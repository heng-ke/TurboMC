package xyz.hegke.client.script.runtime;

import java.util.Locale;

/**
 * Scratch 的比较规则，对齐上游 {@code Cast.compare}。
 *
 * <p>要点：先按数字比，任一侧无法转成数字才按<b>小写字符串</b>比。
 * 这一条决定了 {@code "石头" = "泥土"} 必须是<b>假</b>——此前用数值比较，
 * 两侧都被折成 0，导致任意两个文本恒等。</p>
 */
public final class ScratchCompare {
	private ScratchCompare() {
	}

	public static int compare(Object left, Object right) {
		double n1 = ScratchCast.rawNumber(left);
		double n2 = ScratchCast.rawNumber(right);

		// 空串/全空白在 Scratch 里不当作 0，而是走字符串比较
		if (n1 == 0d && isWhiteSpace(left)) {
			n1 = Double.NaN;
		} else if (n2 == 0d && isWhiteSpace(right)) {
			n2 = Double.NaN;
		}

		if (Double.isNaN(n1) || Double.isNaN(n2)) {
			String s1 = ScratchCast.toString(left).toLowerCase(Locale.ROOT);
			String s2 = ScratchCast.toString(right).toLowerCase(Locale.ROOT);
			return Integer.signum(s1.compareTo(s2));
		}
		if (n1 < n2) {
			return -1;
		}
		if (n1 > n2) {
			return 1;
		}
		return 0;
	}

	public static boolean isWhiteSpace(Object value) {
		return value == null || (value instanceof String text && text.trim().isEmpty());
	}
}
