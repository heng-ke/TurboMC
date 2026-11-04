package xyz.hegke.client.script.ui;

import net.minecraft.client.resource.language.I18n;
import xyz.hegke.client.config.HkScriptConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * 等待时长的显示单位换算：内部一律存 tick，界面可切换成秒。
 *
 * <p>脚本里 {@code control.wait} 的 {@code TICKS} 是 tick 数（Scratch 的「等待 1 秒」导入后是 20）。
 * 直接显示 {@code 20 tick} 会让照着 Scratch 写脚本的人以为换算错了，所以给一个显示开关。</p>
 *
 * <p><b>换算用 {@link BigDecimal} 而不是 {@code double}</b>：除以 20 在十进制下总是有限小数
 * （20 = 2² × 5），所以「tick → 秒 → tick」可以做到完全无损。用 double 则可能出现
 * {@code 0.05 × 20 = 1.0000000000000002}，经 {@code Math.round} 虽然也能回到 1，
 * 但一旦有人改成截断就会悄悄少 1 tick。</p>
 */
public final class WaitUnit {
	/** 20 tick = 1 秒，Minecraft 的固定换算，不随服务器 TPS 变化。 */
	private static final BigDecimal TICKS_PER_SECOND = BigDecimal.valueOf(20);

	/** 参数名是 TICKS 且语义为「时间」的积木。其余带 TICKS 的积木若出现，需要一并加进来。 */
	private static final Set<String> TIMED_BLOCKS = Set.of("control.wait", "debug.say_for", "action.break_wait");

	private WaitUnit() {
	}

	public static boolean inSeconds() {
		return HkScriptConfig.get().showWaitInSeconds();
	}

	/**
	 * 切换显示单位并立即落盘。
	 */
	public static void toggle() {
		HkScriptConfig config = HkScriptConfig.get();
		config.waitInSeconds = !config.showWaitInSeconds();
		HkScriptConfig.save();
	}

	/**
	 * 该参数是否按时间单位显示。
	 */
	public static boolean isWaitParam(String blockType, String paramName) {
		return "TICKS".equals(paramName) && blockType != null && TIMED_BLOCKS.contains(blockType);
	}

	/**
	 * 展示文本，带单位，如 {@code 1 秒} 或 {@code 20 tick}。
	 */
	public static String format(String ticksLiteral) {
		BigDecimal ticks = parseDecimal(ticksLiteral);
		if (ticks == null) {
			return ticksLiteral == null ? "" : ticksLiteral;
		}
		BigDecimal shown = inSeconds() ? ticks.divide(TICKS_PER_SECOND) : ticks;
		return plain(shown) + " " + unitLabel();
	}

	/**
	 * 输入框初值：只有数字，不带单位。
	 */
	public static String input(String ticksLiteral) {
		BigDecimal ticks = parseDecimal(ticksLiteral);
		if (ticks == null) {
			return ticksLiteral == null ? "" : ticksLiteral;
		}
		return plain(inSeconds() ? ticks.divide(TICKS_PER_SECOND) : ticks);
	}

	/**
	 * 输入框回写：把用户输入的数字换算回 tick 字面量。
	 *
	 * <p>无法解析时返回 {@code null}——调用方必须保留原值，绝不能把「秒」当成 tick 写回去，
	 * 那会让 1 秒变成 1 tick，快 20 倍。</p>
	 */
	public static String parseTicks(String text) {
		BigDecimal value = parseDecimal(text);
		if (value == null) {
			return null;
		}
		BigDecimal ticks = inSeconds() ? value.multiply(TICKS_PER_SECOND) : value;
		return plain(ticks.setScale(0, RoundingMode.HALF_UP).max(BigDecimal.ZERO));
	}

	public static String unitLabel() {
		return I18n.translate(inSeconds() ? "unit.hk-script.second" : "unit.hk-script.tick");
	}

	private static BigDecimal parseDecimal(String text) {
		if (text == null) {
			return null;
		}
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		try {
			return new BigDecimal(trimmed);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 去掉多余的零：{@code 1.00} → {@code 1}、{@code 0.05} → {@code 0.05}。
	 *
	 * <p>用 {@code toPlainString} 而不是 {@code toString}，否则 100 会显示成 {@code 1E+2}。</p>
	 */
	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}
}
