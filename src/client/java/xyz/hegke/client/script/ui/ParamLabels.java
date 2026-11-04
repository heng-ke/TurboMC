package xyz.hegke.client.script.ui;

import net.minecraft.client.resource.language.I18n;
import xyz.hegke.client.script.block.BlockRegistry;
import xyz.hegke.client.script.block.BlockType;

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
		String translated = I18n.translate("param.hk-script." + key);
		return translated == null || translated.isBlank() ? key : translated;
	}

	public static String value(String key, String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		// 时间类参数按配置换算成秒或 tick，单位由换算结果自带
		if ("TICKS".equals(key)) {
			return WaitUnit.format(value);
		}
		String translationKey = switch (key) {
			case "SCOPE" -> "value.hk-script.scope." + value;
			case "DIRECTION" -> "value.hk-script.direction." + value;
			case "KEY" -> "value.hk-script.key." + value;
			case "OP" -> "value.hk-script.mathop." + value.replace(' ', '_');
			case "UNIT" -> "value.hk-script.unit." + value.toLowerCase(java.util.Locale.ROOT);
			case "PRESSED" -> "value.hk-script.onoff." + value.toLowerCase(java.util.Locale.ROOT);
			case "BUTTON" -> "value.hk-script.mouse." + value.toLowerCase(java.util.Locale.ROOT);
			default -> null;
		};
		if (translationKey == null) {
			return value;
		}
		String translated = I18n.translate(translationKey);
		return translated == null || translated.isBlank() ? value : translated;
	}
}
