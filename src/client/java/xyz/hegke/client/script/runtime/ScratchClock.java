package xyz.hegke.client.script.runtime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Scratch 的计时器与时钟。对应 {@code sensing_timer} / {@code sensing_resettimer} /
 * {@code sensing_current} / {@code sensing_dayssince2000}。
 *
 * <p>计时器在引擎启动时归零（相当于点绿旗），停止后重新启动会再次归零。</p>
 */
public final class ScratchClock {
	private static long startMillis = System.currentTimeMillis();

	private ScratchClock() {
	}

	/** 计时器读数，单位秒。 */
	public static double timer() {
		return (System.currentTimeMillis() - startMillis) / 1000d;
	}

	public static void resetTimer() {
		startMillis = System.currentTimeMillis();
	}

	/**
	 * 当前时间的某个分量。单位取值：{@code year month date dayofweek hour minute second}。
	 * 星期按 Scratch 的约定：1 = 周日。
	 */
	public static double current(String unit) {
		LocalDateTime now = LocalDateTime.now();
		String key = unit == null ? "" : unit.trim().toLowerCase();
		return switch (key) {
			case "year" -> now.getYear();
			case "month" -> now.getMonthValue();
			case "date" -> now.getDayOfMonth();
			// DayOfWeek 是 1=周一，Scratch 是 1=周日
			case "dayofweek" -> now.getDayOfWeek().getValue() % 7 + 1;
			case "hour" -> now.getHour();
			case "minute" -> now.getMinute();
			case "second" -> now.getSecond();
			default -> 0d;
		};
	}

	/**
	 * 自 2000-01-01 UTC 起的天数（含小数），与 Scratch 一致。
	 */
	public static double daysSince2000() {
		LocalDate start = LocalDate.of(2000, 1, 1);
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		LocalDateTime origin = start.atStartOfDay();
		long seconds = ChronoUnit.SECONDS.between(origin, now);
		return seconds / 86400d;
	}

	/**
	 * 本地时区偏移，仅用于调试展示。
	 */
	public static ZoneId zone() {
		return ZoneId.systemDefault();
	}
}
