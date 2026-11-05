package xyz.turbomc.client.script.runtime;

import java.math.BigDecimal;

/**
 * Scratch 的类型转换规则，逐条对齐上游 {@code src/util/cast.js}。
 *
 * <p>与 Java 默认行为的三处关键差异：</p>
 * <ol>
 *   <li>非数字字符串转数字得到 <b>0</b> 而不是抛异常；但"原始解析"（{@link #rawNumber}）
 *       保留 NaN，供比较运算区分"数字"与"文本"。</li>
 *   <li>{@code toBoolean} <b>不 trim</b>：{@code " false "} 在 Scratch 里是<b>真</b>，
 *       只有空串、{@code "0"}、{@code "false"}（忽略大小写）为假。</li>
 *   <li>数字转字符串按 ECMAScript 的规则：整数不带小数点，{@code 1e-7} 级别才用科学计数法。</li>
 * </ol>
 */
public final class ScratchCast {
	private ScratchCast() {
	}

	/**
	 * 对应 JavaScript 的 {@code Number(value)}：无法解析时返回 {@code NaN}（不折成 0）。
	 */
	public static double rawNumber(Object value) {
		if (value == null) {
			return 0d;
		}
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		if (value instanceof Boolean bool) {
			return bool ? 1d : 0d;
		}
		String text = value.toString().trim();
		if (text.isEmpty()) {
			return 0d;
		}
		if (text.equalsIgnoreCase("Infinity") || text.equals("+Infinity")) {
			return Double.POSITIVE_INFINITY;
		}
		if (text.equalsIgnoreCase("-Infinity")) {
			return Double.NEGATIVE_INFINITY;
		}
		if (text.equalsIgnoreCase("NaN")) {
			return Double.NaN;
		}
		if (text.startsWith("0x") || text.startsWith("0X")) {
			try {
				return Long.parseLong(text.substring(2), 16);
			} catch (NumberFormatException e) {
				return Double.NaN;
			}
		}
		try {
			return Double.parseDouble(text);
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/**
	 * 数值转换：NaN 折成 0（Scratch 里 `0 + NaN` 等于 0）。
	 */
	public static double toNumber(Object value) {
		double number = rawNumber(value);
		return Double.isNaN(number) ? 0d : number;
	}

	/**
	 * 布尔转换：与 Scratch 一致，字符串<b>不</b>做 trim。
	 */
	public static boolean toBoolean(Object value) {
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof String text) {
			return !(text.isEmpty() || text.equals("0") || text.equalsIgnoreCase("false"));
		}
		if (value instanceof Number number) {
			double number2 = number.doubleValue();
			return number2 != 0d && !Double.isNaN(number2);
		}
		return value != null;
	}

	public static String toString(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof String text) {
			return text;
		}
		if (value instanceof Boolean bool) {
			return bool ? "true" : "false";
		}
		if (value instanceof Number number) {
			return numberToString(number.doubleValue());
		}
		return value.toString();
	}

	/**
	 * ECMAScript {@code String(number)} 的近似实现。
	 *
	 * <p>整数不带小数点；{@code [1e-6, 1e21)} 用普通十进制（避免 Java 的 {@code 1.0E-4}）；
	 * 其余用科学计数法并采用 JS 的 {@code 1.5e-7} 写法。</p>
	 */
	public static String numberToString(double value) {
		if (Double.isNaN(value)) {
			return "NaN";
		}
		if (Double.isInfinite(value)) {
			return value > 0 ? "Infinity" : "-Infinity";
		}
		if (value == 0d) {
			// -0.0 也输出 "0"，与 JS 一致
			return "0";
		}
		double abs = Math.abs(value);
		if (value == Math.rint(value) && abs < 1e21) {
			return new BigDecimal(value).toBigInteger().toString();
		}
		if (abs >= 1e-6 && abs < 1e21) {
			return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
		}
		String text = Double.toString(value);
		int mark = text.indexOf('E');
		if (mark < 0) {
			return text;
		}
		String mantissa = text.substring(0, mark);
		String exponent = text.substring(mark + 1);
		if (mantissa.endsWith(".0")) {
			mantissa = mantissa.substring(0, mantissa.length() - 2);
		}
		if (!exponent.startsWith("-")) {
			exponent = "+" + exponent;
		}
		return mantissa + "e" + exponent;
	}
}
