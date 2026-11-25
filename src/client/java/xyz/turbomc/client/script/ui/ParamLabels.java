package xyz.turbomc.client.script.ui;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
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
			// JSON 用玫红：既有分类里没有同色系（fn 的紫红更深），白字对比度也够
			case "json" -> 0xFFC25A85;
			case "world" -> 0xFF2E64B0;
			case "sensing" -> 0xFF2E8FB0;
			case "action" -> 0xFF6F45B0;
			case "region" -> 0xFF2E8B57;
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
		// 实体类型：关键字取语言文件，具体 id 查注册表的实体名（模组实体同样有名字）
		if ("TYPE".equals(key)) {
			return entityTypeName(value);
		}
		// 物品同理：查注册表拿物品名，方块类物品会自动落到方块名（石头、铁矿石…）
		if ("ITEM".equals(key)) {
			return itemName(value);
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
	 * 实体类型的显示名：{@code any/hostile/player} 走语言文件，其余按实体 id 查注册表。
	 *
	 * <p>不逐个实体列语言键：实体类型近百个，且随游戏版本与模组增减，列既不全也维护不动。
	 * 查不到（拼错、模组已卸载）就回落成原始 id，保持可诊断。</p>
	 */
	private static String entityTypeName(String value) {
		String key = translationKey("TYPE", value);
		String translated = key == null ? null : I18n.translate(key);
		if (translated != null && !translated.isBlank() && !key.equals(translated)) {
			return translated;
		}
		Identifier id = Identifier.tryParse(value.trim());
		if (id != null) {
			EntityType<?> type = Registries.ENTITY_TYPE.get(id);
			if (type != null) {
				String name = type.getName().getString();
				if (name != null && !name.isBlank()) {
					return name;
				}
			}
		}
		return value;
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
			case "KEY" -> "value.turbomc.key." + normalize(value);
			// 实体类型：any / hostile / player 有中文名，具体 id（minecraft:zombie）回落原值
			case "TYPE" -> "value.turbomc.entity." + normalize(value);
			case "OP" -> "value.turbomc.mathop." + normalize(value);
			case "UNIT" -> "value.turbomc.unit." + normalize(value);
			// 「当 [计时器/响度] > …」的菜单项
			case "MENU" -> "value.turbomc.menu." + normalize(value);
			case "PRESSED" -> "value.turbomc.onoff." + normalize(value);
			case "BUTTON" -> "value.turbomc.mouse." + normalize(value);
			// 「当打开 [界面]」的菜单
			case "SCREEN" -> "value.turbomc.screen." + normalize(value);
			// 「[快照] 的字段」的菜单
			case "KIND" -> "value.turbomc.kind." + normalize(value);
			// 合并块的下拉：AXIS 与 FIELD 共用一张表（x/y/z 两种菜单都有，且显示相同）
			case "AXIS", "FIELD" -> "value.turbomc.field." + normalize(value);
			default -> null;
		};
	}

	/** 枚举值 → 语言键后缀：小写 + 空格换下划线（{@code up arrow} → {@code up_arrow}）。 */
	private static String normalize(String value) {
		return value.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
	}

	/**
	 * 物品 id 的显示名，例如 {@code minecraft:iron_ore} → 铁矿石。
	 *
	 * <p>用 {@code containsId} 而不是 {@code get != AIR} 判存在：未知 id 时
	 * {@code Registries.ITEM.get} 会回落成 AIR，那样拼错的 id 会被显示成「空气」。</p>
	 */
	private static String itemName(String value) {
		Identifier id = Identifier.tryParse(value.trim().toLowerCase(java.util.Locale.ROOT));
		if (id != null && Registries.ITEM.containsId(id)) {
			String name = Registries.ITEM.get(id).getName().getString();
			if (name != null && !name.isBlank()) {
				return name;
			}
		}
		return value;
	}
}
