package xyz.turbomc.client.script.action;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.config.TurboMCConfig;
import xyz.turbomc.client.config.KeyNames;
import xyz.turbomc.client.script.action.motion.TurnSession;
import xyz.turbomc.client.script.action.path.PathFollower;
import xyz.turbomc.client.script.runtime.ScriptContext;
import xyz.turbomc.client.script.runtime.SelfChatEcho;
import xyz.turbomc.client.script.runtime.Values;

import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 会改变世界状态的动作：移动、挖掘、使用、放置、聊天与命令、界面操作。
 *
 * <p>动作走「输入模拟 + 交互管理器」两条通道，不发送自定义数据包。
 * 只读的世界状态查询在 {@link WorldQueries}。</p>
 *
 * <p>聊天是唯一的例外：它不是「调用即发出」，而是先清洗、按单条上限切段，
 * 再交给 {@link ChatQueue} 按配置的间隔逐条发送（见 {@link ChatText}）。</p>
 */
public final class GameActions {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");
	/**
	 * 某按键名是否被禁止按下。
	 *
	 * <p>判定顺序：黑名单 → 白名单。白名单为空表示不限制；非空时只有列出的键能按。
	 * 名字既可以是物理键（{@code t}、{@code f5}），也可以是动作 id（{@code key.chat}）。</p>
	 */
	public static boolean isBlockedName(String raw) {
		String canonical = KeyNames.canonical(raw);
		if (canonical.isEmpty()) {
			return false;
		}
		TurboMCConfig config = TurboMCConfig.get();
		if (config.blockedKeySet().contains(canonical)) {
			return true;
		}
		Set<String> allowed = config.allowedKeySet();
		return !allowed.isEmpty() && !allowed.contains(canonical);
	}

	/**
	 * 某个按键绑定是否被禁止：物理键或动作 id 任一命中都算。
	 */
	private static boolean isBlocked(KeyBinding binding, String translationKey) {
		return isBlockedName(translationKey) || isBlockedName(binding.getId());
	}

	/**
	 * 无效键名只警告一次，避免每 tick 刷屏。
	 */
	private static void warnKey(String raw, String reason) {
		if (WARNED_KEYS.add(raw)) {
			LOG.warn("[script] 按键 \"{}\" 无效：{}", raw, reason);
		}
	}
	/**
	 * 上一次发送命令的时间槽（50 ms 一个）。同一槽内只放行一条，
	 * 防止「循环节流关闭」时一个 tick 跑上百轮循环把几百条命令砸进服务器。
	 *
	 * <p>聊天不走这里了：它由 {@link ChatQueue} 按 tick 排队，间隔可配置，
	 * 且同帧的第二次调用不再被静默丢弃。</p>
	 */
	private static long lastChatSlot = Long.MIN_VALUE;

	/** 聊天清洗的提示只打一次：模型每轮都可能带换行，逐条刷日志只会淹掉真正的问题。 */
	private static boolean warnedChatSanitize;
	/** 分段发送的提示同样只打一次。 */
	private static boolean warnedChatSegmented;

	/** 上次重生请求时间，避免循环里连发。 */
	private static long lastRespawnAt;

	/** 已警告过的未知按键名，避免每 tick 刷屏。 */
	private static final Set<String> WARNED_KEYS = new HashSet<>();

	private GameActions() {
	}

	public static void registerAll() {
		// 聊天走队列：超长要分段、同帧多次调用不能静默丢弃。间隔由配置决定
		ChatQueue.install(GameActions::sendChatNow, GameActions::chatIntervalTicks);
		NativeRegistry.action("action.key", GameActions::key);
		NativeRegistry.action("action.stop", GameActions::stop);
		NativeRegistry.action("action.look", GameActions::look);
		NativeRegistry.action("action.look_at", GameActions::lookAt);
		NativeRegistry.action("debug.log", GameActions::log);
		NativeRegistry.action("action.chat", GameActions::chat);
		NativeRegistry.action("action.command", GameActions::command);
		NativeRegistry.action("action.tap", GameActions::tap);
		NativeRegistry.action("action.click", GameActions::click);
		NativeRegistry.action("action.respawn", GameActions::respawn);
		NativeRegistry.action("action.break_wait", GameActions::breakWait);
		// 读状态而非改状态，但和「按键」同属输入层，放这里与 action.key 共用一套键名解析
		NativeRegistry.function("input.mouse_button_down", GameActions::mouseButtonDown);
	}

	/**
	 * 「按下鼠标 [按钮]?」：读物理鼠标状态。
	 *
	 * <p>不做边沿检测——按住期间持续为真；也不受界面是否打开影响。</p>
	 */
	private static Object mouseButtonDown(ScriptContext ctx, Object[] args) {
		String raw = args.length > 0 ? Values.asString(args[0]) : "";
		boolean down = InputKeys.isMouseDown(client(ctx), raw);
		// 状态翻转时留一行日志：鼠标判断出问题时，这行能区分「参数没传进来」
		// 「键名没解析」「GLFW 没读到」三种情况，比逐层加打印快
		Boolean previous = LAST_MOUSE_STATE.put(raw, down);
		if (previous == null || previous != down) {
			LOG.info("[script] 鼠标「{}」{}（glfw button={}）",
					raw, down ? "按下" : "松开", InputKeys.mouseButton(raw));
		}
		return down;
	}

	/** 上一次各按钮的读数，仅用于状态翻转时打日志。 */
	private static final Map<String, Boolean> LAST_MOUSE_STATE = new HashMap<>();

	/**
	 * 等待准星所指的方块被破坏。
	 *
	 * <p>没有固定时长，因此用条件型等待：JS 侧每帧回调 {@code ScriptBridge.poll("break")}，
	 * 由 {@link WorldQueries#probeBreak} 判定。上限 200 帧（约 10 秒），
	 * 超时或被挡（GIVE_UP）都放行，避免脚本永久挂住。</p>
	 */
	private static void breakWait(ScriptContext ctx, Object[] args) {
		ctx.requestWaitUntil("break", 200);
	}

	/**
	 * 停止移动：中止移动会话并松开所有脚本按键。
	 *
	 * <p>只松键是不够的——跟随器下一 tick 会把前进键按回去，所以必须先让它停下。</p>
	 */
	private static void stop(ScriptContext ctx, Object[] args) {
		PathFollower.abort("停止移动");
		TurnSession.abort("停止移动");
		releaseMovementKeys();
	}

	private static void look(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.player == null) {
			return;
		}
		client.player.setYaw((float) Values.asNumber(args[0]));
		client.player.setPitch((float) Values.asNumber(args[1]));
	}

	private static void lookAt(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.player == null) {
			return;
		}
		Vec3d target = new Vec3d(
				Values.asNumber(args[0]),
				Values.asNumber(args[1]),
				Values.asNumber(args[2]));
		// 目标与眼睛重合时方向向量为零，归一化得到 NaN，而 setYaw 遇到 NaN 会走
		// Util.logErrorOrPause（开发环境直接抛异常）。这种输入直接忽略。
		if (target.squaredDistanceTo(client.player.getEyePos()) < 1e-8) {
			return;
		}
		client.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target);
	}


	private static void log(ScriptContext ctx, Object[] args) {
		LOG.info("{}", Values.asString(args[0]));
	}

	/**
	 * 把脚本传入的坐标钳制到世界边界内。
	 *
	 * <p>直接 {@code (int)} 转换会让 {@code 1e20} 这类输入饱和成 {@code Integer.MAX_VALUE}，
	 * 后续 {@code getBlockState} 会去加载极远处的区块。</p>
	 */
	public static int clampCoord(double value) {
		if (Double.isNaN(value)) {
			return 0;
		}
		double clamped = Math.max(-30_000_000d, Math.min(30_000_000d, value));
		return (int) Math.floor(clamped);
	}

	private static MinecraftClient client(ScriptContext ctx) {
		return (MinecraftClient) ctx.handle();
	}

	/**
	 * 同一 50ms 内只允许一条聊天/命令。
	 *
	 * <p>循环节流关闭时一个 tick 能跑很多轮，若脚本里写「在聊天栏说」，
	 * 几百条消息会在同一帧涌出，服务端判定为刷屏直接断开连接。</p>
	 */
	private static boolean chatThrottled() {
		long slot = System.currentTimeMillis() / 50L;
		if (slot == lastChatSlot) {
			return true;
		}
		lastChatSlot = slot;
		return false;
	}

	/**
	 * 在聊天栏发送一条消息。
	 *
	 * <p>以 {@code /} 开头的文本会被服务端当作命令，但走普通聊天通道发不出去，
	 * 所以这里显式拦下并提示改用「执行命令」积木——静默失败比报错更难查。</p>
	 *
	 * <p><b>发送前必须清洗</b>：模型回复与脚本拼出来的文本可能带换行、`§` 或超长，
	 * 原版服务端不会截断而是直接 {@code disconnect}，报「非法聊天字符」，玩家被踢出存档。
	 * 详见 {@link ChatText}。</p>
	 *
	 * <p><b>超长要分段</b>：单条上限 256 字符 / 768 字节，切开的段落交给
	 * {@link ChatQueue} 按配置的间隔逐条发出。</p>
	 */
	private static void chat(ScriptContext ctx, Object[] args) {
		if (client(ctx).getNetworkHandler() == null) {
			return;
		}
		String raw = Values.asString(args[0]);
		if (raw.isBlank()) {
			return;
		}
		if (raw.startsWith("/")) {
			warnKey("chat-slash", "「在聊天栏说」收到以 / 开头的文本，已忽略；执行命令请用「执行命令」积木");
			return;
		}
		// 注意用 clean 而不是 sanitize：后者会按单条上限截断，
		// 长文本在分段之前就被砍掉，结果永远只有一段（真机事故）
		String text = ChatText.clean(raw);
		if (text.isBlank()) {
			warnKey("chat-empty", "「在聊天栏说」的内容去掉换行与控制字符后为空，已忽略");
			return;
		}
		if (!text.equals(raw) && !warnedChatSanitize) {
			warnedChatSanitize = true;
			// 只提示一次：模型每轮都可能带换行，逐条刷日志没有意义。
			// 记长度不记内容——聊天内容不该进日志。
			LOG.warn("[script] 「在聊天栏说」的内容含换行/控制字符，已清洗后发送："
					+ "原始 {} 字 → 发送 {} 字（单条上限 {} 字符 / {} 字节；本提示只出现一次）",
					raw.length(), text.length(), ChatText.MAX_LENGTH, ChatText.MAX_BYTES);
		}
		int maxSegments = chatMaxSegments();
		List<String> segments = ChatText.split(text, maxSegments);
		if (segments.size() > 1 && !warnedChatSegmented) {
			warnedChatSegmented = true;
			// 分段是正常行为，但用户会看到聊天栏连着冒出好几条，提示一次免得以为重复了
			LOG.info("[script] 「在聊天栏说」的内容超过单条上限，已分成 {} 段按间隔发送"
					+ "（本提示只出现一次）", segments.size());
		}
		ChatQueue.offer(segments, maxSegments * 2);
	}

	/**
	 * 真正把一段文本发出去：队列的出队回调。
	 *
	 * <p>回声登记放在这里而不是入队时：{@link SelfChatEcho} 的匹配窗口是 3 秒，
	 * 入队时登记的话，排到第 4 段时第一条早过期了。必须是「实际发出的那一刻」。</p>
	 */
	private static void sendChatNow(String text) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getNetworkHandler() == null) {
			return;
		}
		client.getNetworkHandler().sendChatMessage(text);
		// 服务端会把这条广播回来，同样触发一次聊天事件。登记一下，
		// 免得「当聊天包含」被自己的回复再次拉起，形成无限回路。
		SelfChatEcho.sentByScript(text);
	}

	/** 每 tick 由引擎调用：冷却结束后发下一段。 */
	public static void tickChat() {
		ChatQueue.tick();
	}

	/** 切包、停引擎、停止全部时清空待发内容。 */
	public static void clearChatQueue() {
		ChatQueue.clear();
	}

	private static int chatIntervalTicks() {
		TurboMCConfig config = TurboMCConfig.get();
		Integer value = config.chatIntervalTicks;
		return value == null ? 3 : Math.max(1, Math.min(20, value));
	}

	private static int chatMaxSegments() {
		TurboMCConfig config = TurboMCConfig.get();
		Integer value = config.chatMaxSegments;
		return value == null ? 8 : Math.max(1, Math.min(16, value));
	}

	/**
	 * 执行一条命令，自动补前导斜杠。
	 */
	private static void command(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.getNetworkHandler() == null) {
			return;
		}
		String text = Values.asString(args[0]).trim();
		if (text.isEmpty()) {
			return;
		}
		String body = text.startsWith("/") ? text.substring(1) : text;
		if (body.isBlank() || chatThrottled()) {
			return;
		}
		client.getNetworkHandler().sendChatCommand(body);
	}

	/**
	 * 按住 / 松开任意键盘键或鼠标键。
	 *
	 * <p>键名写法见 {@link KeyNames}：{@code w}、{@code left shift}、{@code mouse.left} 都行。</p>
	 */
	private static void key(ScriptContext ctx, Object[] args) {
		applyKey(ctx, Values.asString(args[0]), Values.asBool(args[1]));
	}

	/**
	 * 点按键盘键：按下后按住若干 tick 自动松开。
	 */
	private static void tap(ScriptContext ctx, Object[] args) {
		int hold = (int) Math.round(Values.asNumber(args[1]));
		applyTap(ctx, Values.asString(args[0]), hold);
		// 按住期间脚本必须挂起，否则后面的积木会在松手之前就执行完
		if (hold > 0) {
			ctx.requestWait(hold);
		}
	}

	/**
	 * 点击鼠标键：按下 1 tick 后自动松开。
	 */
	private static void click(ScriptContext ctx, Object[] args) {
		applyTap(ctx, "mouse." + Values.asString(args[0]).toLowerCase(Locale.ROOT), 1);
	}

	/**
	 * 按键的公共实现：键盘与鼠标共用，区别只在键名怎么拼出来。
	 */
	private static void applyKey(ScriptContext ctx, String raw, boolean pressed) {
		MinecraftClient client = client(ctx);
		String translationKey = KeyNames.toTranslationKey(raw);
		InputUtil.Key physical = InputUtil.fromTranslationKey(translationKey);
		if (physical == InputUtil.UNKNOWN_KEY) {
			warnKey(raw, "不是有效的按键名");
			return;
		}

		int matched = 0;
		int blocked = 0;
		for (KeyBinding binding : client.options.allKeys) {
			if (!translationKey.equals(binding.getBoundKeyTranslationKey())) {
				continue;
			}
			if (isBlocked(binding, translationKey)) {
				blocked++;
				continue;
			}
			matched++;
			if (pressed) {
				// 只在「松开 → 按下」的边沿伪造一次按下事件，否则循环里每 tick
				// 调用都会让「开背包」「切视角」这类动作重复触发
				boolean alreadyHeld = ScriptKeys.isForced(binding);
				ScriptKeys.hold(ctx.currentInstance(), binding, -1);
				if (!alreadyHeld) {
					binding.onKeyPressed(physical);
				}
			} else {
				ScriptKeys.release(ctx.currentInstance(), binding);
			}
		}
		if (matched == 0) {
			warnKey(raw, blocked > 0
					? "该键用于打开游戏界面，脚本不会按下它"
					: "没有任何游戏动作绑定在这个键上（可先在「选项 → 控制」里绑定）");
		}
	}

	/**
	 * 点按的公共实现：按住 {@code hold} tick 后由 {@link ScriptKeys#tick()} 松开。
	 */
	private static void applyTap(ScriptContext ctx, String raw, int hold) {
		MinecraftClient client = client(ctx);
		int ticks = Math.max(1, Math.min(200, hold));

		String translationKey = KeyNames.toTranslationKey(raw);
		InputUtil.Key physical = InputUtil.fromTranslationKey(translationKey);
		if (physical == InputUtil.UNKNOWN_KEY) {
			warnKey(raw, "不是有效的按键名");
			return;
		}

		int matched = 0;
		int blocked = 0;
		for (KeyBinding binding : client.options.allKeys) {
			if (!translationKey.equals(binding.getBoundKeyTranslationKey())) {
				continue;
			}
			if (isBlocked(binding, translationKey)) {
				blocked++;
				continue;
			}
			matched++;
			boolean alreadyHeld = ScriptKeys.isForced(binding);
			ScriptKeys.hold(ctx.currentInstance(), binding, ticks);
			if (!alreadyHeld) {
				binding.onKeyPressed(physical);
			}
		}
		if (matched == 0) {
			warnKey(raw, blocked > 0
					? "该键用于打开游戏界面，脚本不会按下它"
					: "没有任何游戏动作绑定在这个键上（可先在「选项 → 控制」里绑定）");
		}
	}

	/**
	 * 请求重生。
	 *
	 * <p>只发一次客户端状态包，是否真的活过来由服务端决定。用时间戳节流：
	 * 脚本写在循环里时每 tick 调用会连发几十个包，服务端会当成异常客户端。</p>
	 */
	private static void respawn(ScriptContext ctx, Object[] args) {
		requestRespawn(client(ctx));
	}

	/**
	 * 发送重生请求（供动作与「自动重生」开关共用）。
	 */
	public static void requestRespawn(MinecraftClient client) {
		if (client == null || client.getNetworkHandler() == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastRespawnAt < 1000L) {
			return;
		}
		lastRespawnAt = now;
		client.getNetworkHandler().sendPacket(
				new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
	}


	/**
	 * 释放所有被脚本按下的键，避免停止脚本后角色继续移动。
	 *
	 * <p>只松开脚本接管过的键，不会打断玩家自己正在按的方向键。</p>
	 */
	public static void releaseMovementKeys() {
		ScriptKeys.releaseEveryKey();
	}
}
