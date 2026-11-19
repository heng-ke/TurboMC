package xyz.turbomc.client.script.vm;

import net.minecraft.client.MinecraftClient;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.script.action.InputKeys;
import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.action.motion.TurnSession;
import xyz.turbomc.client.script.action.path.PathFollower;
import xyz.turbomc.client.script.ai.AiService;
import xyz.turbomc.client.script.runtime.MonitorStore;
import xyz.turbomc.client.script.runtime.ScriptContext;
import xyz.turbomc.client.script.sb3.Sb3Mapping;
import xyz.turbomc.client.script.ui.AskScreen;

import java.util.function.Consumer;

/**
 * JS 运行时与 Java 动作层之间的唯一桥。
 *
 * <p>类型封送规则刻意收得很窄：JS 的 string / number / boolean 直接映射为
 * {@link String} / {@link Double} / {@link Boolean}，其余（对象、数组、undefined）
 * 统一转成字符串。Scratch 的值域本来就只有这三种，桥不需要更复杂的转换。</p>
 *
 * <p>opcode 到动作 id 的映射复用 {@link Sb3Mapping}：sb3 opcode（{@code turbomc_log}）
 * → 积木类型（{@code debug.log}）→ {@link NativeRegistry} 的注册项。这样不引入第四张表。</p>
 */
public final class ScriptBridge {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/vm");
	private static final String[] NO_ARGS = new String[0];

	/** 当前 tick 的宿主上下文；动作在 tick 内同步执行，故用静态字段即可。 */
	private static ScriptContext current;

	private static int calls;
	private static int misses;
	/** 上一次 call 产生的等待请求，由 JS 侧紧接着取走并包装成 Promise。 */
	private static int pendingWait;
	/** 条件型等待：检查键与上限帧数。 */
	private static String pendingCheck;
	private static int pendingLimit;

	private ScriptBridge() {
	}

	static void setContext(ScriptContext context) {
		current = context;
	}

	static void clearContext() {
		current = null;
	}

	public static int callCount() {
		return calls;
	}

	public static int missCount() {
		return misses;
	}

	/**
	 * 扩展产物与宿主的 opcode 集合自检，返回给 HUD 的一句话（一致时为空串）。
	 *
	 * <p>两个方向都要报，因为症状完全不同：</p>
	 * <ul>
	 *   <li>扩展缺块（改了模组没重载扩展）→ 新积木拖不出来；</li>
	 *   <li>扩展多块（换了扩展没换模组）→ 积木点下去没反应（未映射）。</li>
	 * </ul>
	 *
	 * <p>明细写日志，HUD 只放结论——一行放不下清单。</p>
	 */
	public static String checkExtensionCoverage(String declaredCsv) {
		java.util.Set<String> declared = new java.util.HashSet<>();
		if (declaredCsv != null) {
			for (String part : declaredCsv.split(",")) {
				String name = part.trim();
				if (!name.isEmpty()) {
					declared.add(name);
				}
			}
		}
		java.util.Set<String> expected = Sb3Mapping.extensionOpcodes();
		java.util.List<String> missing = new java.util.ArrayList<>();
		for (String opcode : expected) {
			if (!declared.contains(opcode)) {
				missing.add(opcode);
			}
		}
		java.util.List<String> extra = new java.util.ArrayList<>();
		for (String opcode : declared) {
			if (!expected.contains(opcode)) {
				extra.add(opcode);
			}
		}
		if (missing.isEmpty() && extra.isEmpty()) {
			LOG.info("[vm] 扩展自检通过：{} 块积木与映射表一致", expected.size());
			return "";
		}
		if (!missing.isEmpty()) {
			LOG.warn("[vm] 扩展缺少 {} 块积木（多为扩展未重新加载）：{}", missing.size(), missing);
		}
		if (!extra.isEmpty()) {
			LOG.warn("[vm] 扩展多出 {} 块未映射积木（多为模组版本较旧）：{}", extra.size(), extra);
		}
		StringBuilder message = new StringBuilder();
		if (!missing.isEmpty()) {
			message.append("扩展缺少 ").append(missing.size()).append(" 块积木，请重新加载扩展");
		}
		if (!extra.isEmpty()) {
			if (message.length() > 0) {
				message.append("；");
			}
			message.append("扩展多出 ").append(extra.size()).append(" 块未实现积木，请更新模组");
		}
		return message.toString();
	}

	/**
	 * 「用户名」的宿主实现：当前登录账号名。
	 *
	 * <p>离线环境返回离线账号名。游戏未就绪时返回空串，避免积木拿到 null。</p>
	 */
	public static String username() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getSession() == null) {
			return "";
		}
		return client.getSession().getUsername();
	}

	// ---- 询问与监视器 ----

	/** 待交给 VM 的回答；{@code null} 表示没有。空串是合法回答，故不能用空值表示「无」。 */
	private static volatile String pendingAnswer;

	/**
	 * 打开询问界面的方式。
	 *
	 * <p>抽成接口是为了冒烟测试：纯 JVM 里没有 Minecraft 客户端，直接引用
	 * {@code MinecraftClient} 会在类加载阶段就失败，问答链路就一条都测不到。</p>
	 */
	@FunctionalInterface
	public interface AskPresenter {
		void present(String question, Consumer<String> onAnswer);
	}

	private static AskPresenter presenter = ScriptBridge::openAskScreen;

	/** 测试用：替换界面呈现方式；传 {@code null} 恢复默认。 */
	public static void setPresenter(AskPresenter value) {
		presenter = value == null ? ScriptBridge::openAskScreen : value;
	}

	private static void openAskScreen(String question, Consumer<String> onAnswer) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			onAnswer.accept("");
			return;
		}
		client.setScreen(new AskScreen(question, onAnswer));
	}

	/**
	 * 「询问 [问题] 并等待」：弹出输入窗口。
	 *
	 * <p>由 JS 侧收到 {@code QUESTION} 事件后调用，此时仍在 VM 步进内，属于客户端主线程，
	 * {@code setScreen} 合法。</p>
	 */
	public static void askQuestion(String question) {
		String text = question == null ? "" : question;
		try {
			presenter.present(text, ScriptBridge::submitAnswer);
		} catch (Throwable t) {
			// 界面打不开（纯 JVM、渲染异常等）时给空回答：宁可少一次输入，
			// 也不能让询问线程永久挂在 PROMISE_WAIT
			LOG.warn("[vm] 打开询问界面失败，按空回答继续: {}", t.toString());
			submitAnswer("");
		}
	}

	/** 界面回调入口：把回答排进队列，由下一次 VM 步进统一交付。 */
	public static void submitAnswer(String answer) {
		pendingAnswer = answer == null ? "" : answer;
	}

	/**
	 * 取走待交付的回答；没有则返回 {@code null}。
	 *
	 * <p>不在界面回调里直接 emit：emit 会推动 VM 的线程状态，放在一次 step 的边界更安全。</p>
	 */
	public static String takeAnswer() {
		String value = pendingAnswer;
		pendingAnswer = null;
		return value;
	}

	/** 引擎停止或切换工程时丢弃残留回答，避免下一份工程收到上一份的输入。 */
	public static void clearPendingAnswer() {
		pendingAnswer = null;
	}

	/** 「显示 / 隐藏 变量、列表」的宿主入口：登记到 HUD 监视器。 */
	public static void monitor(String kind, String name, boolean visible) {
		MonitorStore.Kind type = "list".equalsIgnoreCase(kind)
				? MonitorStore.Kind.LIST
				: MonitorStore.Kind.VARIABLE;
		MonitorStore.set(type, name, visible);
	}

	/**
	 * 「鼠标按下?」（Scratch 原生积木）的宿主实现，由 JS 侧的鼠标设备转发而来。
	 *
	 * <p>与「按下 [键]?」同理：读物理状态、不做边沿检测。Scratch 该积木不分按钮，
	 * 因此这里同样按「任意键按下」判定（{@code any}）。</p>
	 */
	public static boolean mouseDown(String button) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getWindow() == null) {
			return false;
		}
		return InputKeys.isMouseDown(client, button);
	}

	/**
	 * 「按下 [键]?」的宿主实现，由 JS 侧的键盘设备转发而来。
	 *
	 * <p>与事件侧的 {@code TriggerDispatcher} 共用 {@link InputKeys}，区别在于这里是
	 * <b>电平读取</b>——按住期间持续为真，不做边沿检测。读的是 GLFW 物理键盘状态，
	 * 因此界面打开时同样有效（打开界面不影响按键扫描）。</p>
	 *
	 * <p>{@code any} 与事件侧同义：任意键按下即为真（已排除 ESC / F6 / F7）。</p>
	 */
	public static boolean keyDown(String key) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getWindow() == null) {
			return false;
		}
		if (key == null || "any".equalsIgnoreCase(key.trim())) {
			return InputKeys.anyDown(client);
		}
		return InputKeys.isDown(client, key);
	}

	/**
	 * 由 JS 侧调用：{@code ScriptBridge.call('turbomc_log', 'TEXT', args)}。
	 *
	 * @param opcode sb3 里的扩展 opcode，形如 {@code turbomc_<name>}
	 * @param names  参数名，英文逗号分隔；顺序取自积木显示文本里的 [NAME]
	 * @param args   扩展收到的参数对象
	 * @return 报告类积木的返回值；命令类积木返回 {@code null}
	 */
	public static Object call(String opcode, String names, Scriptable args) {
		calls++;
		pendingWait = 0;
		ScriptContext context = current;
		if (context == null) {
			LOG.warn("[vm] 动作在 tick 之外被调用，已忽略: {}", opcode);
			return null;
		}

		String type = Sb3Mapping.typeOf(opcode);
		if (type == null) {
			misses++;
			LOG.warn("[vm] 未映射的扩展积木: {}", opcode);
			return null;
		}

		Object[] values = extract(names, args);
		Object result = null;
		try {
			NativeRegistry.Action action = NativeRegistry.getAction(type);
			if (action != null) {
				action.run(context, values);
			} else {
				NativeRegistry.Function function = NativeRegistry.getFunction(type);
				if (function != null) {
					result = function.apply(context, values);
				} else {
					misses++;
					LOG.warn("[vm] 积木 {} 已映射为 {}，但动作层没有实现", opcode, type);
				}
			}
		} catch (RuntimeException e) {
			// 单个动作失败不应该让整条脚本链断掉
			LOG.warn("[vm] 执行积木 {} 时出错: {}", opcode, e.toString());
		}
		pendingWait = context.takeWaitTicks();
		pendingCheck = context.takeWaitCheck();
		pendingLimit = context.takeWaitLimit();
		return result;
	}

	/**
	 * 取走上一次调用产生的等待帧数。JS 侧据此把命令积木包装成 Promise，
	 * 使线程进入 {@code PROMISE_WAIT} 而不是立刻执行下一块。
	 */
	public static int takeWaitTicks() {
		int value = pendingWait;
		pendingWait = 0;
		return value;
	}

	/** 取走条件型等待的检查键；为空表示本次是定时等待而非条件等待。 */
	public static String takeWaitCheck() {
		String value = pendingCheck;
		pendingCheck = null;
		return value;
	}

	public static int takeWaitLimit() {
		int value = pendingLimit;
		pendingLimit = 0;
		return value;
	}

	/**
	 * 条件轮询入口，由 JS 每帧调用一次。
	 *
	 * @return {@code true} 表示等待结束（成功、失败或放弃都算结束）
	 */
	public static boolean poll(String key) {
		if ("move".equals(key)) {
			return PathFollower.finished();
		}
		if ("turn".equals(key)) {
			return TurnSession.finished();
		}
		// 必须在下面的「未知 key 一律 true」之前：漏了这段，AI 的等待会被立刻放行，
		// 症状是积木一闪而过、回复永远是空串，而日志里没有任何异常
		if (key != null && key.startsWith("ai:")) {
			return AiService.get().isDone(key.substring("ai:".length()));
		}
		if (!"break".equals(key)) {
			return true;
		}
		return pollBreak();
	}

	// ---- 「等待方块被挖破」的状态 ----

	private static final int BREAK_WAITING = ScriptContext.BreakResult.WAITING;
	private static Object breakState;
	/** 上一轮等待是否已结束；用于在新一轮开始时自动清空锁定的目标。 */
	private static boolean breakDone = true;

	private static boolean pollBreak() {
		ScriptContext context = current;
		if (context == null) {
			return true;
		}
		if (breakDone) {
			breakState = null;
			breakDone = false;
		}
		ScriptContext.BreakResult result = context.probeBreak(breakState);
		breakState = result.state();
		// WAITING 之外（BROKEN / GIVE_UP）都结束等待：放弃时不应把脚本永久挂住
		if (result.status() != BREAK_WAITING) {
			breakDone = true;
			return true;
		}
		return false;
	}

	/** 调试用：把参数按名字取出并转成 Java 值。 */
	private static Object[] extract(String names, Scriptable args) {
		if (names == null || names.isEmpty() || args == null) {
			return NO_ARGS;
		}
		String[] keys = names.split(",");
		Object[] values = new Object[keys.length];
		for (int i = 0; i < keys.length; i++) {
			values[i] = toJava(ScriptableObject.getProperty(args, keys[i].trim()));
		}
		return values;
	}

	private static Object toJava(Object raw) {
		if (raw == null || raw == Scriptable.NOT_FOUND || raw instanceof Undefined) {
			return "";
		}
		if (raw instanceof CharSequence) {
			return raw.toString();
		}
		if (raw instanceof Number number) {
			return number.doubleValue();
		}
		if (raw instanceof Boolean) {
			return raw;
		}
		return Context.toString(raw);
	}
}
