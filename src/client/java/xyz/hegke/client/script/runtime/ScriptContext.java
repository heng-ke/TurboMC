package xyz.hegke.client.script.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 脚本可见的宿主环境。
 *
 * <p>刻意用 {@link Object} 持有宿主句柄、不直接引用任何 Minecraft 类型，使执行内核
 * （编译器 / 解释器 / 调度器）可以脱离游戏运行，便于自检与后续单元测试。</p>
 *
 * <p>实例每 tick 重建一次，因此内部的 {@link #cached} 天然是「单 tick 缓存」：
 * 多个查询积木读取同一份世界数据时只遍历一次。</p>
 */
public final class ScriptContext {
	private final Object handle;
	private final Map<String, Object> cache = new HashMap<>();
	private boolean stopAllRequested;
	/** 当前正在执行 step 的实例，供动作层归属按键等资源。 */
	private Object currentInstance;
	/** 广播并等待：由引擎注入「发出广播并返回被唤醒实例」的实现。 */
	private Function<String, List<ScriptInstance>> broadcaster = message -> List.of();
	/** 挖掘等待：由引擎注入，内核只透传宿主目标，不依赖任何 Minecraft 类型。 */
	private BreakProbe breakProbe = (context, state) -> new BreakResult(state, BreakResult.GIVE_UP);

	public ScriptContext(Object handle) {
		this.handle = handle;
	}

	public Object handle() {
		return handle;
	}

	/**
	 * 请求停止全部脚本。标志位由引擎在本次 tick 的调度结束后统一处理，
	 * 避免在遍历实例时直接清空集合。
	 */
	public void requestStopAll() {
		stopAllRequested = true;
	}

	public boolean stopAllRequested() {
		return stopAllRequested;
	}

	public void setCurrentInstance(Object instance) {
		this.currentInstance = instance;
	}

	public Object currentInstance() {
		return currentInstance;
	}

	public void setBroadcaster(Function<String, List<ScriptInstance>> broadcaster) {
		this.broadcaster = broadcaster == null ? message -> List.of() : broadcaster;
	}

	/**
	 * 发出广播，返回本次被唤醒的实例（可能为空）。
	 */
	public List<ScriptInstance> broadcast(String message) {
		List<ScriptInstance> started = broadcaster.apply(message);
		return started == null ? List.of() : started;
	}

	public Object cached(String key, Supplier<Object> supplier) {
		if (cache.containsKey(key)) {
			return cache.get(key);
		}
		Object value = supplier.get();
		cache.put(key, value);
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
