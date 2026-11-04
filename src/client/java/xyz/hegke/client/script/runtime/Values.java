package xyz.hegke.client.script.runtime;

/**
 * Scratch 风格弱类型转换。运行期所有值只可能是 Double / String / Boolean。
 *
 * <p>实际规则集中在 {@link ScratchCast}，这里保留历史方法名供各处调用，
 * 避免同一语义出现两套实现。</p>
 */
public final class Values {
	private Values() {
	}

	/**
	 * 解析字面量：布尔与数字会被识别，其余保持字符串。
	 */
	public static Object parseLiteral(String literal) {
		if (literal == null) {
			return 0d;
		}
		String text = literal.trim();
		if (text.equalsIgnoreCase("true")) {
			return Boolean.TRUE;
		}
		if (text.equalsIgnoreCase("false")) {
			return Boolean.FALSE;
		}
		try {
			return Double.parseDouble(text);
		} catch (NumberFormatException ignored) {
			return literal;
		}
	}

	public static double asNumber(Object value) {
		return ScratchCast.toNumber(value);
	}

	public static boolean asBool(Object value) {
		return ScratchCast.toBoolean(value);
	}

	public static String asString(Object value) {
		return ScratchCast.toString(value);
	}
}
