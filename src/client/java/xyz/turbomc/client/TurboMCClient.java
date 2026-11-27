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
import net.minecraft.client.gui.screen.GameMenuScreen;
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
import xyz.turbomc.client.script.remote.RemoteBridge;
import xyz.turbomc.client.script.render.FrameOverlay;
import xyz.turbomc.client.script.render.PathRenderer;
import xyz.turbomc.client.script.ui.ScriptHudElement;
import xyz.turbomc.client.script.ui.ScriptListScreen;
import xyz.turbomc.client.script.view.ViewSmoother;
import xyz.turbomc.client.script.vm.ExtensionExporter;
import xyz.turbomc.client.ui.EnchantPenaltyTooltip;

public class TurboMCClient implements ClientModInitializer {
	private static KeyBinding toggleScriptKey;
	private static KeyBinding openPacksKey;

	private final TriggerDispatcher triggerDispatcher = new TriggerDispatcher();

	/** 聊天回调的线程断言只提示一次：正常情况永远不触发，触发一次就够了。 */
	private static final java.util.concurrent.atomic.AtomicBoolean WARNED_OFF_THREAD_CHAT =
			new java.util.concurrent.atomic.AtomicBoolean();

	/** 远程会话期间被临时改成 false 的「失焦暂停」原值；null 表示当前没改过。 */
	private Boolean pauseOnLostFocusBackup;
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

		// F3+H 高级浮窗里的铁砧惩罚：只在 advanced 模式追加，F3+H 本身就是开关，
		// 不额外占配置项；普通浮窗一行都不加。
		EnchantPenaltyTooltip.register();

		ClientReceiveMessageEvents.CHAT.register(
				(message, signedMessage, sender, params, receptionTimestamp) -> {
					assertMainThread("CHAT");
					// 引擎没在跑就不必算签名、也不必入队：入队的线程永远不会被 step。
					// 远程会话例外：那时内 VM 是停的，但帽子要由浏览器那侧起
					if (!ScriptEngine.get().acceptsTriggers()) {
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
		// 线程：与上面的 CHAT 回调一样在主线程。1.21.11 的 ClientPlayNetworkHandler
		// 两个入口（onChatMessage / onGameMessage）第一句就是 NetworkThreadUtils.forceMainThread：
		// 不在客户端线程时把包塞进 PacketApplyBatcher 并抛 OffThreadException，
		// 随后由 MinecraftClient.tick() 里的 apply() 执行。所以这两个回调与 vm.tick 天然串行，
		// 不需要「入队 + 主线程派发」。真正在 Netty 上的是 DISCONNECT（见下方注册处，已投递回主线程）。
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			assertMainThread("GAME");
			// 动作栏（血量、计时、经验提示）不是聊天，混进来会让帽子疯狂启动
			if (overlay) {
				return;
			}
			if (!TurboMCConfig.get().chatFromServerMessages) {
				return;
			}
			if (!ScriptEngine.get().acceptsTriggers()) {
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

		// 「画框」的线框：同一个事件、独立监听器。分开注册是为了互不牵连——
		// 任一方渲染抛异常时，另一方的显示不受影响。
		WorldRenderEvents.AFTER_ENTITIES.register(FrameOverlay::render);

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
					// 框画在旧世界的坐标上，换存档后不该跟过来
					FrameOverlay.clear();
					// 视角控制也要收：引擎可能没在跑，光 abort 会话不覆盖 MANUAL 那一路
					ViewSmoother.clear("断开连接");
					ScriptKeys.releaseEveryKey();
					PathPlanner.reset();
					// 一局结束就把性能汇总刷出来，不用等下一个 30 秒窗口
					PerfProbe.flush();
					// 退出世界时结束远程会话但保持监听：玩家可能马上又进来，扩展侧会自己重连
					RemoteBridge.get().endSession();
					// 总开关是持久化的，不在这里复位，下一局进世界会自动开跑。
					// 内部会再走一次 stop()：上面的 abort / reset 都幂等，重复调用无副作用。
					ScriptEngine.get().onWorldExit();
				}));
	}

	/**
	 * 远程桥的开关跟随配置：ModMenu 里改完不必重启游戏。
	 *
	 * <p>每 tick 只比两个布尔，成本可忽略；比注册配置变更回调少一层跨模块耦合。
	 * 默认关闭，所以对普通玩家这条路径永远只是两次布尔比较。</p>
	 */
	private void syncRemoteBridge() {
		boolean wanted = TurboMCConfig.get().remoteEnabled();
		boolean running = RemoteBridge.get().running();
		if (wanted && !running) {
			// 动作表与积木注册都在 init 里：远程会话可能先于引擎启动（玩家没按 F6），
			// 不补这一行，浏览器发来的 opcode 会一律回 notfound，看起来像「积木不支持」
			ScriptEngine.get().init();
			RemoteBridge.get().start();
		} else if (!wanted && running) {
			RemoteBridge.get().stop();
		}
	}

	/**
	 * 压住「失焦暂停」。
	 *
	 * <p>浏览器或编辑器在前台时游戏窗口必然失去焦点，原版会因此弹出暂停界面、单机世界
	 * 随之停住：脚本读到的坐标不再变化、按键被 GUI 吞掉、动作积木看着成功但世界不动。
	 * 这不是脚本的 bug，是「同机多窗口」这种用法的固有冲突。</p>
	 *
	 * <p>两种触发条件，任一成立即生效：</p>
	 * <ul>
	 *   <li><b>远程会话中</b>——必须生效，否则开发者一操作浏览器脚本就冻住；</li>
	 *   <li><b>配置项 {@code keepRunningWhenUnfocused}</b>——用户希望平时也不暂停。</li>
	 * </ul>
	 *
	 * <p>两层处理：把 {@code pauseOnLostFocus} 置假；再兜底关掉「因失焦弹出来的暂停界面」。
	 * 只认 `失焦 + GameMenuScreen` 这一种组合——玩家自己按 ESC 的暂停不动它。
	 * 两个条件都撤掉时恢复原值，不改用户的持久化设置。</p>
	 */
	private void guardFocusPause(MinecraftClient client) {
		boolean remote = ScriptEngine.get().status() == ScriptEngine.Status.REMOTE;
		boolean wanted = remote || TurboMCConfig.get().shouldKeepRunningWhenUnfocused();
		if (wanted) {
			if (pauseOnLostFocusBackup == null && client.options != null) {
				pauseOnLostFocusBackup = client.options.pauseOnLostFocus;
				client.options.pauseOnLostFocus = false;
				TurboMC.LOGGER.info("[focus] 已临时关闭「失焦暂停」（{}）",
						remote ? "远程会话" : "配置项");
			}
			// 只有一个备份字段，所以两条件并存时不会互相覆盖；都撤掉才恢复
			if (!client.isWindowFocused() && client.currentScreen instanceof GameMenuScreen) {
				client.setScreen(null);
			}
			return;
		}
		if (pauseOnLostFocusBackup != null && client.options != null) {
			client.options.pauseOnLostFocus = pauseOnLostFocusBackup;
			pauseOnLostFocusBackup = null;
			TurboMC.LOGGER.info("[focus] 已恢复「失焦暂停」设置");
		}
	}

	private void onClientTick(MinecraftClient client) {
		syncRemoteBridge();

		while (toggleScriptKey.wasPressed()) {
			// 远程会话期间内 VM 已让位：此时翻开关不会真的启动，反而让 HUD 与浏览器侧对不上
			if (ScriptEngine.get().status() == ScriptEngine.Status.REMOTE) {
				if (client.player != null) {
					client.player.sendMessage(
							Text.translatable("message.turbomc.remote_busy"), true);
				}
				continue;
			}
			// 「停止全部」后 HUD 写的是「按 F6 重新开启」，那这一下就必须直接重开：
			// 此时开关本来就是 true，走下面的反转只会先把引擎关掉，玩家要按两次（DEF-02）
			if (ScriptEngine.get().resumeFromHalt()) {
				if (client.player != null) {
					client.player.sendMessage(
							Text.translatable("message.turbomc.script_on"), true);
				}
				continue;
			}
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
		// 放在引擎 tick 之后：status 已经更新，才知道这一帧是不是处于远程会话。
		// 也在 triggerDispatcher 之前：失焦弹出的暂停界面会挡住按键派发（要求 currentScreen 为 null）
		guardFocusPause(client);
		triggerDispatcher.tick(client);
		// 快照放在最后：本 tick 的界面/血量/坐标变化都已落定，推出去的就是这一帧的最终值。
		// 同时它也承担浏览器侧的时钟（wait 与 poll 都按快照 tick 推进）。
		RemoteBridge.get().tick(client);
	}

	/**
	 * 确认聊天回调落在客户端主线程。
	 *
	 * <p>1.21.11 的 {@code ClientPlayNetworkHandler.onChatMessage} / {@code onGameMessage}
	 * 第一句是 {@code NetworkThreadUtils.forceMainThread(packet, this, client.getPacketApplyBatcher())}：
	 * 不在 batcher 线程时把包塞进队列并抛 {@code OffThreadException}，包随后在
	 * {@code MinecraftClient.tick()} 的 {@code packetApplyBatcher.apply()} 里执行。
	 * 而 batcher 是用 {@code MinecraftClient.thread} 构造的，也就是渲染线程——
	 * 所以这两个事件本来就与 {@code vm.tick} 同线程，不存在与 VM 的并发。</p>
	 *
	 * <p>这里只留一道便宜的保险：将来若上游改了这条路径、或别的模组手工重放事件，
	 * 会先在日志里露头，而不是变成「帽子偶尔丢一次」这种查不出来的现象。</p>
	 */
	private static void assertMainThread(String channel) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && !client.isOnThread()
				&& WARNED_OFF_THREAD_CHAT.compareAndSet(false, true)) {
			TurboMC.LOGGER.warn("[chat] {} 回调不在主线程：引擎状态与 VM 访问需要重新评估（本警告只出现一次）",
					channel);
		}
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
