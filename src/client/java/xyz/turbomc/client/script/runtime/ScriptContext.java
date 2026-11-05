package xyz.turbomc.client.script.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 脚本可见的宿主环境。
 *
 * <p>刻意用 {@link Object} 持有宿主句柄、不直接引用任何 Minecraft 类型，使动作层
 * 与测试可以在没有客户端实例的情况下加载——冒烟测试正是靠这一点在纯 JVM 里跑通。</p>
 *
 * <p>实例每 tick 重建一次，因此内部的 {@link #cached} 天然是「单 tick 缓存」：
 * 多个查询积木读取同一份世界数据时只遍历一次。</p>
 */
public final class ScriptContext {
	private final Object handle;
	private final Map<String, Object> cache = new HashMap<>();
	/**
	 * 当前脚本的归属标识，动作层用它给按键等资源记归属。
	 *
	 * <p><b>目前恒为 {@code null}</b>：scratch-vm 里没有稳定的「脚本实例」句柄可以传出来
	 * （线程 id 会复用），所以所有按键都记在 null 这一位上。后果是「两个脚本按同一个键、
	 * 一个松手会连带松开另一个」，单脚本场景无影响。要做精确归属，得从 JS 侧把线程 id 带进桥。</p>
	 */
	private Object currentInstance;
	/** 挖掘等待：由引擎注入，内核只透传宿主目标，不依赖任何 Minecraft 类型。 */
	private BreakProbe breakProbe = (context, state) -> new BreakResult(state, BreakResult.GIVE_UP);

	public ScriptContext(Object handle) {
		this.handle = handle;
	}

	public Object handle() {
		return handle;
	}

	public Object currentInstance() {
		return currentInstance;
	}

	public Object cached(String key, Supplier<Object> supplier) {
		if (cache.containsKey(key)) {
			return cache.get(key);
		}
		Object value = supplier.get();
		cache.put(key, value);
		return value;
	}

	// ---- 阻塞动作的等待请求 ----

	/**
	 * 动作请求脚本暂停的帧数，供 JS 运行时包装成 Promise。
	 *
	 * <p>命令类积木原本同步返回即可，但「按住 N tick」「等待方块挖破」这类动作
	 * 在 Scratch 语义里必须挂起线程，否则后续积木会立刻执行。</p>
	 */
	private int waitTicks;

	/** 动作层声明需要等待的帧数；取较大值，避免同一次调用里被较小的值覆盖。 */
	public void requestWait(int ticks) {
		if (ticks > waitTicks) {
			waitTicks = ticks;
		}
	}

	/** 取走并清空等待请求。 */
	public int takeWaitTicks() {
		int value = waitTicks;
		waitTicks = 0;
		return value;
	}

	/**
	 * 条件型等待：挂起直到宿主侧的检查通过，或超过上限帧数。
	 *
	 * <p>用于「等待方块被挖破」这类没有固定时长的动作。检查键由 JS 运行时每帧
	 * 回调 {@code ScriptBridge.poll(key)}，返回 {@code true} 即结束等待。</p>
	 */
	private String waitCheck;
	private int waitLimit;

	public void requestWaitUntil(String checkKey, int maxTicks) {
		waitCheck = checkKey;
		waitLimit = Math.max(1, maxTicks);
	}

	public String takeWaitCheck() {
		String value = waitCheck;
		waitCheck = null;
		return value;
	}

	public int takeWaitLimit() {
		int value = waitLimit;
		waitLimit = 0;
		return value;
	}

	// ---- 挖掘等待 ----

	/**
	 * 一次挖掘判定的结果。
	 *
	 * @param state  下一次判定要继续透传的目标（宿主自定义，内核不解释）
	 * @param status {@link #WAITING} / {@link #BROKEN} / {@link #GIVE_UP}
	 */
	public record BreakResult(Object state, int status) {
		/** 还没有结论，下一 tick 继续问。 */
		public static final int WAITING = 0;
		/** 目标方块已经不在了。 */
		public static final int BROKEN = 1;
		/** 放弃：没进世界、目标丢失等，按「没挖破」处理，避免脚本永久挂起。 */
		public static final int GIVE_UP = 2;
	}

	/**
	 * 挖掘判定钩子。实现方负责「锁定哪一块、怎么算破了」，内核只保存它返回的 {@code state}。
	 */
	@FunctionalInterface
	public interface BreakProbe {
		BreakResult probe(ScriptContext context, Object state);
	}

	public void setBreakProbe(BreakProbe probe) {
		this.breakProbe = probe == null
				? (context, state) -> new BreakResult(state, BreakResult.GIVE_UP)
				: probe;
	}

	public BreakResult probeBreak(Object state) {
		return breakProbe.probe(this, state);
	}
}
