package xyz.turbomc.client;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Base64;
import java.util.UUID;
import xyz.turbomc.TurboMC;
import xyz.turbomc.client.config.TurboMCConfig;
import xyz.turbomc.client.script.ScriptEngine;
import xyz.turbomc.client.script.action.ScriptKeys;
import xyz.turbomc.client.script.action.motion.TurnSession;
import xyz.turbomc.client.script.action.path.PathFollower;
import xyz.turbomc.client.script.action.path.PathPlanner;
import xyz.turbomc.client.script.runtime.ChatGate;
import xyz.turbomc.client.script.runtime.ChatState;
import xyz.turbomc.client.script.runtime.PerfProbe;
import xyz.turbomc.client.script.runtime.SelfChatEcho;
import xyz.turbomc.client.script.runtime.TriggerDispatcher;
import xyz.turbomc.client.script.render.PathRenderer;
import xyz.turbomc.client.script.ui.ScriptHudElement;
import xyz.turbomc.client.script.ui.ScriptListScreen;
import xyz.turbomc.client.script.vm.ExtensionExporter;

public class TurboMCClient implements ClientModInitializer {
	private static KeyBinding toggleScriptKey;
	private static KeyBinding openPacksKey;

	private final TriggerDispatcher triggerDispatcher = new TriggerDispatcher();

	@Override
	public void onInitializeClient() {
		// 客户端配置在初始化阶段加载，保证后续所有客户端功能都读到同一份数据。
		TurboMCConfig.load();

		// 把打包进 jar 的 TurboWarp 扩展释放到 config/turbomc/，分发出去的 jar 才自带可导入的 js
		ExtensionExporter.exportToConfig();

		// Category.create 会向全局注册表登记，同一个 id 只能创建一次，因此两个按键共享同一实例。
		KeyBinding.Category category = KeyBinding.Category.create(TurboMC.id("general"));

		toggleScriptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.turbomc.toggle_script",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F6,
				category
		));

		openPacksKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				// 翻译键沿用旧名（改 key binding id 会让玩家自定义的按键失效），显示文本已改为「打开脚本包」
				"key.turbomc.open_editor",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F7,
				category
		));

		ScriptEngine.get().init();

		// 脚本 HUD：引擎状态 + 「说 / 思考」气泡。scratch-vm 的 say/think 在游戏内
		// 没有任何渲染出口，不在这里补，脚本的话就没人看得见。
		HudElementRegistry.addLast(TurboMC.id("script_hud"), new ScriptHudElement());

		ClientReceiveMessageEvents.CHAT.register(
				(message, signedMessage, sender, params, receptionTimestamp) -> {
					// 引擎没在跑就不必算签名、也不必入队：入队的线程永远不会被 step
					if (!ScriptEngine.get().active()) {
						return;
					}
					String text = message.getString();
					// 同一条消息只触发一次：回调可能重复，用签名当身份；无签名时按短窗口兜底
					String signature = signedMessage != null && signedMessage.hasSignature()
							? Base64.getEncoder().encodeToString(signedMessage.signature().data())
							: null;
					if (!ChatGate.accept(text, signature)) {
						return;
					}
					// 回声只可能来自自己：别人的消息哪怕包含刚发出的子串，也该照常触发。
					// sender 为 null（服务端未带档案）时无从判断，退回旧行为以免形成回路。
					if ((sender == null || isSelf(sender)) && SelfChatEcho.isEcho(text)) {
						return;
					}
					// 在关键词过滤之前记录：脚本要能「按内容自己判断要不要理」，
					// 放进 fireChat 里就只剩命中关键词的那几条了
					ChatState.record(text);
					// 关键词过滤在 VM 侧按每个「当聊天包含」积木的输入值完成
					ScriptEngine.get().fireChat(text);
				});

		// 脚本在 START_CLIENT_TICK 推进，使本 tick 设置的动作能赶在实体移动逻辑之前生效。
		ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);

		// 「走向」的路线显示：实体之后、半透明之前，线与方块遮挡关系正常
		WorldRenderEvents.AFTER_ENTITIES.register(PathRenderer::render);

		// 退出世界/断开连接：PathPlanner 的静态字段抓着旧世界的 ClientWorld，
		// 不在这里断开，换存档时会一直多留一整个世界的区块缓存。
		// 顺序不能颠倒：先让会话收尾（它会读规划状态），再断开静态引用。
		//
		// 这个回调走的是 Netty 线程（实测名字为 "Netty Local IO #1"），而会话、按键表、
		// 规划器全是主线程状态——必须投递回主线程再动，否则与 tick 并发读写。
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				client.execute(() -> {
					PathFollower.abort("断开连接");
					TurnSession.abort("断开连接");
					ScriptKeys.releaseEveryKey();
					PathPlanner.reset();
					// 一局结束就把性能汇总刷出来，不用等下一个 30 秒窗口
					PerfProbe.flush();
				}));
	}

	private void onClientTick(MinecraftClient client) {
		while (toggleScriptKey.wasPressed()) {
			boolean enabled = !TurboMCConfig.get().scriptEngineEnabled;
			TurboMCConfig.get().scriptEngineEnabled = enabled;
			TurboMCConfig.save();
			TurboMC.LOGGER.info("[script] 开关切换为 {}", enabled);
			if (enabled) {
				// 只改配置复位不了两个闩锁：工程读取失败留下的 healthy=false，
				// 以及「停止全部」留下的 halted=true。后者会让 tick 直接返回，
				// 表现为「F6 打开了，但脚本一动不动」。
				ScriptEngine.get().restart();
			}
			if (client.player != null) {
				client.player.sendMessage(
						Text.translatable(enabled ? "message.turbomc.script_on" : "message.turbomc.script_off"),
						true);
			}
		}

		while (openPacksKey.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new ScriptListScreen(null));
			}
		}

		ScriptEngine.get().tick(client);
		triggerDispatcher.tick(client);
	}

	/**
	 * 这条聊天是不是本地玩家自己发的。
	 *
	 * <p>服务端会把客户端发出的聊天原样广播回来，那是回声；别人的消息再像也不是。
	 * 用 UUID 而非名字比较：改名、带前缀的昵称都不影响判断。</p>
	 */
	private static boolean isSelf(GameProfile sender) {
		MinecraftClient client = MinecraftClient.getInstance();
		UUID id = sender == null ? null : sender.id();
		return id != null && client.player != null && client.player.getUuid().equals(id);
	}
}
