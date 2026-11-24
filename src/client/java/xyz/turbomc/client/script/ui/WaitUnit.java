package xyz.turbomc.client.script.ui;

import net.minecraft.client.resource.language.I18n;
import xyz.turbomc.client.config.TurboMCConfig;

import java.math.BigDecimal;

/**
 * 等待时长的显示单位换算：内部一律存 tick，显示时可按配置换算成秒。
 *
 * <p>脚本里 {@code control.wait} 的 {@code TICKS} 是 tick 数（Scratch 的「等待 1 秒」导入后是 20）。
 * 直接显示 {@code 20 tick} 会让照着 Scratch 写脚本的人以为换算错了，所以默认按秒显示。</p>
 *
 * <p>单位由配置项 {@code waitInSeconds} 决定，入口在模组菜单的「脚本引擎」分类
 * （「等待积木按秒显示」）。{@link TurboMCConfig#get()} 返回的是活实例，开关改完
 * 立即影响后续绘制，不需要重启。</p>
 *
 * <p><b>换算用 {@link BigDecimal} 而不是 {@code double}</b>：除以 20 在十进制下总是有限小数
 * （20 = 2² × 5），所以「tick → 秒」可以做到完全无损，不会出现
 * {@code 0.05 × 20 = 1.0000000000000002} 这类尾巴。</p>
 */
public final class WaitUnit {
	/** 20 tick = 1 秒，Minecraft 的固定换算，不随服务器 TPS 变化。 */
	private static final BigDecimal TICKS_PER_SECOND = BigDecimal.valueOf(20);

	private WaitUnit() {
	}

	/**
	 * 展示文本，带单位，如 {@code 1 秒} 或 {@code 20 tick}。
	 */
	public static String format(String ticksLiteral) {
		BigDecimal ticks = parseDecimal(ticksLiteral);
		if (ticks == null) {
			return ticksLiteral == null ? "" : ticksLiteral;
		}
		BigDecimal shown = showInSeconds() ? ticks.divide(TICKS_PER_SECOND) : ticks;
		return plain(shown) + " " + unitLabel();
	}

	private static boolean showInSeconds() {
		return TurboMCConfig.get().showWaitInSeconds();
	}

	private static String unitLabel() {
		return I18n.translate(showInSeconds() ? "unit.turbomc.second" : "unit.turbomc.tick");
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
