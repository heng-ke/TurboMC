package xyz.turbomc.client.script.vm;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.runtime.ScriptContext;
import xyz.turbomc.client.script.sb3.Sb3Mapping;

/**
 * JS 运行时与 Java 动作层之间的唯一桥。
 *
 * <p>类型封送规则刻意收得很窄：JS 的 string / number / boolean 直接映射为
 * {@link String} / {@link Double} / {@link Boolean}，其余（对象、数组、undefined）
 * 统一转成字符串。Scratch 的值域本来就只有这三种，桥不需要更复杂的转换。</p>
 *
 * <p>opcode 到动作 id 的映射复用 {@link Sb3Mapping}：sb3 opcode（{@code hkscript_log}）
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
	 * 由 JS 侧调用：{@code ScriptBridge.call('hkscript_log', 'TEXT', args)}。
	 *
	 * @param opcode sb3 里的扩展 opcode，形如 {@code hkscript_<name>}
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
