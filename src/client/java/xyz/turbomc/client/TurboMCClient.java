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
import xyz.turbomc.client.script.view.ViewSmoother;
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

		// 「服务端消息」通道。
		//
		// 代理服/子服常把玩家聊天包成 system 消息下发（客户端日志表现为
		// "[System] [CHAT] 名字: 内容"，而真玩家聊天是 "[CHAT] <名字> 内容"），
		// 聊天栏里看着一模一样，但协议层是不同的包，Fabric 的 CHAT 事件不会触发。
		// 只订阅 CHAT 的后果是「单人/局域网正常、进服务器后当聊天包含永不触发」。
		//
		// 这个通道要额外挡两类东西：动作栏消息（overlay）、插件广告。
		// 前者靠 overlay 标记，后者靠 ChatServerAnyMatch 默认为 false——只放行
		// 「关键词可确定且命中」的帽子，空关键词/复合表达式一律不启动。
		//
		// 已知隐患（本批次不处理）：与上面的 CHAT 回调一样，这里运行在网络线程，
		// 会直接操作 Rhino 里的 runtime.threads。改主线程投递要重排时序与去重，
		// 属独立批次，见 docs/ARCHITECTURE.md「双通道：玩家聊天 ≠ 服务器系统消息」。
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			// 动作栏（血量、计时、经验提示）不是聊天，混进来会让帽子疯狂启动
			if (overlay) {
				return;
			}
			if (!TurboMCConfig.get().chatFromServerMessages) {
				return;
			}
			if (!ScriptEngine.get().active()) {
				return;
			}
			String text = message.getString();
			// 系统消息没有签名：走「正文 + 短窗口」去重，与单机路径同一套语义
			if (!ChatGate.accept(text, null)) {
				return;
			}
			// 脚本自己发的聊天在服务端也会变成 system 消息回来，认出来就别触发自己
			if (SelfChatEcho.isEcho(text)) {
				return;
			}
			// 与玩家聊天通道同序：先记录再派发，脚本读到的就是触发它的那句话。
			// 这里是无条件覆盖（含插件广告），由使用者自行在脚本里按关键词判断。
			ChatState.record(text);
			ScriptEngine.get().fireServerChat(text);
		});

		// 脚本在 START_CLIENT_TICK 推进，使本 tick 设置的动作能赶在实体移动逻辑之前生效。
		ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);

		// 「走向」的路线显示：实体之后、半透明之前，线与方块遮挡关系正常
		WorldRenderEvents.AFTER_ENTITIES.register(PathRenderer::render);

		// 视角平滑：写入必须发生在渲染帧上。原版第一人称的 getYaw(float) 不做 tick 内插值，
		// 在 tick 里每 50 ms 写一次就是每 3 帧跳一格的台阶（见 ViewSmoother 的类注释）。
		// START_MAIN 是每帧能拿到的最早时机；写入下一帧才被相机读到，固定 1 帧延迟。
		WorldRenderEvents.START_MAIN.register(ctx ->
				ViewSmoother.onRenderFrame(MinecraftClient.getInstance()));

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
					// 视角控制也要收：引擎可能没在跑，光 abort 会话不覆盖 MANUAL 那一路
					ViewSmoother.clear("断开连接");
					ScriptKeys.releaseEveryKey();
					PathPlanner.reset();
					// 一局结束就把性能汇总刷出来，不用等下一个 30 秒窗口
					PerfProbe.flush();
					// 总开关是持久化的，不在这里复位，下一局进世界会自动开跑。
					// 内部会再走一次 stop()：上面的 abort / reset 都幂等，重复调用无副作用。
					ScriptEngine.get().onWorldExit();
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
