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
