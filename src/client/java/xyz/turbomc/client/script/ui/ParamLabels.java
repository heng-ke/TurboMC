package xyz.turbomc.client.script.ui;

import net.minecraft.client.resource.language.I18n;
import xyz.turbomc.client.script.block.BlockRegistry;
import xyz.turbomc.client.script.block.BlockType;

/**
 * 显示层的文本工具：积木名、参数名、参数值的中文化与剪裁，以及积木配色。
 *
 * <p>这三件事都只服务于「把内部数据画到屏幕上」，放在一处比拆成多个 20 行的工具类更好找。</p>
 */
public final class ParamLabels {
	private ParamLabels() {
	}

	/**
	 * 积木配色：按分类着色，深色背景下保证文字对比度。
	 */
	public static int blockColor(String blockType) {
		BlockType type = BlockRegistry.get(blockType);
		String category = type != null ? type.category() : "misc";
		return switch (category) {
			case "event" -> 0xFFC1782B;
			case "control" -> 0xFFBE9B2E;
			case "operator" -> 0xFF3F8F3A;
			case "data" -> 0xFFB85A2C;
			case "list" -> 0xFF2E8B8B;
			case "world" -> 0xFF2E64B0;
			case "sensing" -> 0xFF2E8FB0;
			case "action" -> 0xFF6F45B0;
			case "debug" -> 0xFF5A5A5A;
			case "func" -> 0xFF8B3A62;
			default -> 0xFF4A4A4A;
		};
	}

	/**
	 * 变量名显示：内部名带角色前缀（{@code Sprite1/score}），
	 * 在该角色的脚本里显示时省略前缀。存储与执行始终使用完整名。
	 */
	public static String variableName(String internalName, String scopeId) {
		if (internalName == null) {
			return "";
		}
		if (scopeId == null || scopeId.isEmpty()) {
			return internalName;
		}
		String prefix = scopeId + "/";
		return internalName.startsWith(prefix) ? internalName.substring(prefix.length()) : internalName;
	}

	public static String label(String key) {
		String full = "param.turbomc." + key;
		String translated = I18n.translate(full);
		// 缺键时 I18n.translate 原样返回键名（非 null、非空白），只看 isBlank 会把
		// "param.turbomc.xxx" 直接摆到界面上；这里比对键名，缺键就退回参数名。
		return translated == null || translated.isBlank() || full.equals(translated) ? key : translated;
	}

	public static String value(String key, String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		// 时间类参数按配置换算成秒或 tick，单位由换算结果自带
		if ("TICKS".equals(key)) {
			return WaitUnit.format(value);
		}
		String translationKey = translationKey(key, value);
		if (translationKey == null) {
			return value;
		}
		String translated = I18n.translate(translationKey);
		// 同上：缺键时 translate 返回键名本身，必须比对才能退回原始值
		return translated == null || translated.isBlank() || translationKey.equals(translated)
				? value
				: translated;
	}

	/**
	 * 参数值 → 语言键。查不到对应键时返回 {@code null}（调用方回落成原始值）。
	 *
	 * <p><b>键名归一化规则必须与取值表一致</b>：枚举值里有多词的（Scratch 的方向键是
	 * {@code up arrow}，快捷键是 {@code left shift}），语言键统一写成小写 + 下划线。
	 * 少了这一步，{@code up arrow} 会拼成 {@code value.turbomc.key.up arrow} 查不到，
	 * 屏幕上就退回英文原值——「按下 up arrow?」。单字值（{@code space}）看不出问题，
	 * 所以这类缺陷特别容易漏。</p>
	 */
	static String translationKey(String key, String value) {
		return switch (key) {
			case "SCOPE" -> "value.turbomc.scope." + normalize(value);
			case "DIRECTION" -> "value.turbomc.direction." + normalize(value);
			case "KEY" -> "value.turbomc.key." + normalize(value);
			case "OP" -> "value.turbomc.mathop." + normalize(value);
			case "UNIT" -> "value.turbomc.unit." + normalize(value);
			// 「当 [计时器/响度] > …」的菜单项
			case "MENU" -> "value.turbomc.menu." + normalize(value);
			case "PRESSED" -> "value.turbomc.onoff." + normalize(value);
			case "BUTTON" -> "value.turbomc.mouse." + normalize(value);
			default -> null;
		};
	}

	/** 枚举值 → 语言键后缀：小写 + 空格换下划线（{@code up arrow} → {@code up_arrow}）。 */
	private static String normalize(String value) {
		return value.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
	}
}
