package xyz.turbomc.client.script.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 轻量性能打点：统计「宿主侧同步占住主线程」的几个调用。
 *
 * <p><b>为什么需要它</b>：脚本逻辑本身有单帧预算和指令观察器兜底，但 A*、VM 初始化、
 * 工程解析、zip 解压这些<b>宿主调用</b>不在那个预算里——卡顿时日志中一片安静，
 * 只能靠猜。这里只在入口/出口各取一次 {@link System#nanoTime()}，把耗时聚合进
 * 30 秒窗口输一行汇总；单次超过两帧还会立刻告警，卡顿发生的那一刻就留痕。</p>
 *
 * <p><b>默认开启</b>：30 秒最多一行，不刷屏。不想看时加 JVM 参数
 * {@code -Dturbomc.perf=false} 关掉。只有主线程（tick / 渲染 / GUI 回调）调用，
 * 因此不做同步。</p>
 */
public final class PerfProbe {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/perf");
	private static final boolean ENABLED =
			!"false".equalsIgnoreCase(System.getProperty("turbomc.perf", "true"));
	/** 汇总窗口长度（毫秒）；{@code -Dturbomc.perf.windowMs=} 可缩短，便于验证打点本身。 */
	private static final long WINDOW_NANOS = Math.max(1L,
			Long.getLong("turbomc.perf.windowMs", 30_000L)) * 1_000_000L;
	/** 单次超过这个值（约两帧）立即告警。 */
	private static final long SPIKE_MS = 33L;
	/** 一帧 16.7 ms 的取整，用于日志里换算。 */
	private static final long FRAME_MS = 17L;

	private static final Map<String, Stats> STATS = new LinkedHashMap<>();
	private static long windowStart = System.nanoTime();

	private PerfProbe() {
	}

	public static boolean enabled() {
		return ENABLED;
	}

	/** 开始计时。返回 0 表示打点关闭，{@link #end} 会直接忽略。 */
	public static long begin() {
		return ENABLED ? System.nanoTime() : 0L;
	}

	/** 记录一次耗时；{@code start} 为 0 表示未开启。 */
	public static void end(String label, long start) {
		if (!ENABLED || start == 0L) {
			return;
		}
		long now = System.nanoTime();
		long ms = (now - start) / 1_000_000L;
		STATS.computeIfAbsent(label, key -> new Stats()).add(ms);
		if (ms >= SPIKE_MS) {
			LOG.warn("[perf] {} 单次 {} ms（≥{} ms，约 {} 帧），这一下会看得见掉帧",
					label, ms, SPIKE_MS, Math.max(2, ms / FRAME_MS));
		}
		if (now - windowStart >= WINDOW_NANOS) {
			flush();
			windowStart = now;
		}
	}

	/**
	 * 立即输出并清零当前窗口。退出世界（一局结束）时调用，免得等下一个 30 秒窗口。
	 */
	public static void flush() {
		if (!ENABLED || STATS.isEmpty()) {
			STATS.clear();
			return;
		}
		StringBuilder builder = new StringBuilder();
		STATS.forEach((label, stats) -> {
			if (builder.length() > 0) {
				builder.append(" | ");
			}
			builder.append(label).append(' ').append(stats.describe());
		});
		LOG.info("[perf] 最近一窗（{} ms）：{}", WINDOW_NANOS / 1_000_000L, builder);
		STATS.clear();
	}

	private static final class Stats {
		private int count;
		private long total;
		private long max;
		private int overFrame;
		private int overThreeFrames;

		void add(long ms) {
			count++;
			total += ms;
			max = Math.max(max, ms);
			if (ms > 16) {
				overFrame++;
			}
			if (ms > 50) {
				overThreeFrames++;
			}
		}

		String describe() {
			return "n=" + count
					+ " avg=" + (total / count) + "ms"
					+ " max=" + max + "ms"
					+ " >16ms=" + overFrame
					+ " >50ms=" + overThreeFrames;
		}
	}
}
