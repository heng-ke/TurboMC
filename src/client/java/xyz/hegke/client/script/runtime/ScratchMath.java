package xyz.hegke.client.script.runtime;

import java.util.Random;

/**
 * Scratch 的数值规则，对齐上游 {@code scratch3_operators.js} 与 {@code Cast.toListIndex}。
 */
public final class ScratchMath {
	/** 非法列表下标。 */
	public static final int LIST_INVALID = Integer.MIN_VALUE;
	/** 「全部」：仅删除类积木接受。 */
	public static final int LIST_ALL = Integer.MAX_VALUE - 1;

	private static final Random RANDOM = new Random();

	private ScratchMath() {
	}

	/**
	 * 取余：Scratch 用<b>向下取整</b>的除法，{@code -3 mod 5 = 2}；
	 * Java 的 {@code %} 是截断除法，会得到 -3。
	 */
	public static double mod(double number, double modulus) {
		if (modulus == 0d) {
			return 0d;
		}
		double result = number % modulus;
		return result / modulus < 0 ? result + modulus : result;
	}

	/**
	 * 随机数：两端都是整数时返回整数，否则返回浮点。
	 *
	 * <p>此前一律按整数处理，导致 {@code 0.5~1.5} 只会得到端点值。</p>
	 */
	public static double random(double from, double to) {
		double low = Math.min(from, to);
		double high = Math.max(from, to);
		if (low == high) {
			return low;
		}
		if (isIntLike(from) && isIntLike(to)) {
			return low + Math.floor(RANDOM.nextDouble() * ((high + 1) - low));
		}
		return RANDOM.nextDouble() * (high - low) + low;
	}

	private static boolean isIntLike(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value) && value == Math.rint(value);
	}

	/**
	 * 数学函数：{@code abs floor ceiling sqrt sin cos tan asin acos atan ln log e^ 10^}。
	 * 三角函数以角度为单位，与 Scratch 一致。
	 */
	public static double mathop(String operator, double number) {
		String op = operator == null ? "" : operator.toLowerCase().trim();
		return switch (op) {
			case "abs" -> Math.abs(number);
			case "floor" -> Math.floor(number);
			case "ceiling" -> Math.ceil(number);
			case "sqrt" -> Math.sqrt(number);
			case "sin" -> roundTo10(Math.sin(Math.toRadians(number)));
			case "cos" -> roundTo10(Math.cos(Math.toRadians(number)));
			case "tan" -> {
				double radians = Math.toRadians(number);
				// 90° + k·180° 处余弦为 0，Scratch 返回 Infinity
				yield Math.abs(Math.cos(radians)) < 1e-12
						? Double.POSITIVE_INFINITY
						: roundTo10(Math.tan(radians));
			}
			case "asin" -> Math.toDegrees(Math.asin(number));
			case "acos" -> Math.toDegrees(Math.acos(number));
			case "atan" -> Math.toDegrees(Math.atan(number));
			case "ln" -> Math.log(number);
			case "log" -> Math.log10(number);
			case "e ^" -> Math.exp(number);
			case "10 ^" -> Math.pow(10, number);
			default -> 0d;
		};
	}

	/**
	 * 上游用 {@code toFixed(10)} 抹掉浮点噪声，使 {@code sin(180) = 0}。
	 */
	private static double roundTo10(double value) {
		return Math.rint(value * 1e10) / 1e10;
	}

	/**
	 * 列表下标：1 起算、按 {@code floor} 取整；支持 {@code last} / {@code random} / {@code any} /
	 * {@code all}（仅删除类积木）。返回 {@link #LIST_INVALID} 或 {@link #LIST_ALL} 表示特殊情况。
	 */
	public static int listIndex(Object index, int length, boolean acceptAll) {
		if (!(index instanceof Number)) {
			String text = ScratchCast.toString(index);
			if ("all".equals(text)) {
				return acceptAll ? LIST_ALL : LIST_INVALID;
			}
			if ("last".equals(text)) {
				return length > 0 ? length : LIST_INVALID;
			}
			if ("random".equals(text) || "any".equals(text)) {
				return length > 0 ? 1 + (int) Math.floor(RANDOM.nextDouble() * length) : LIST_INVALID;
			}
		}
		int result = (int) Math.floor(ScratchCast.toNumber(index));
		if (result < 1 || result > length) {
			return LIST_INVALID;
		}
		return result;
	}
}
