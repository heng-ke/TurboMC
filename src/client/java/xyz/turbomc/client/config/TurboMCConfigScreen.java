package xyz.turbomc.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Cloth Config 配置界面。
 *
 * <p>仅当 Cloth Config 存在时才会被加载（从 {@link ModMenuIntegration} 间接引用），
 * 因此本类可以自由使用 Cloth Config 的 API。</p>
 */
public final class TurboMCConfigScreen {
	private TurboMCConfigScreen() {
	}

	public static Screen create(Screen parent) {
		TurboMCConfig config = TurboMCConfig.get();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.translatable("config.turbomc.title"))
				.setSavingRunnable(TurboMCConfig::save);

		ConfigEntryBuilder entries = builder.entryBuilder();

		// ---- 常规 ----
		ConfigCategory general = builder.getOrCreateCategory(Text.translatable("config.turbomc.category.general"));

		general.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.master_enabled"), config.masterEnabled)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.turbomc.master_enabled.tooltip"))
				.setSaveConsumer(value -> config.masterEnabled = value)
				.build());

		general.addEntry(entries
				.startEnumSelector(Text.translatable("config.turbomc.hud_corner"), HudCorner.class, config.hudCorner)
				.setDefaultValue(HudCorner.TOP_LEFT)
				.setEnumNameProvider(corner -> Text.translatable(
						"config.turbomc.hud_corner." + corner.name().toLowerCase(Locale.ROOT)))
				.setTooltip(Text.translatable("config.turbomc.hud_corner.tooltip"))
				.setSaveConsumer(value -> config.hudCorner = value)
				.build());

		// ---- 脚本引擎 ----
		ConfigCategory script = builder.getOrCreateCategory(Text.translatable("config.turbomc.category.script"));

		script.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.auto_respawn"), config.autoRespawn)
				.setDefaultValue(false)
				.setTooltip(Text.translatable("config.turbomc.auto_respawn.tooltip"))
				.setSaveConsumer(value -> config.autoRespawn = value)
				.build());

		script.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.show_path"), config.showPath)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.turbomc.show_path.tooltip"))
				.setSaveConsumer(value -> config.showPath = value)
				.build());

		script.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.show_path_hud"), config.showPathHud)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.turbomc.show_path_hud.tooltip"))
				.setSaveConsumer(value -> config.showPathHud = value)
				.build());

		script.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.monitor_list_items"),
						config.monitorListItems, 1, 20)
				.setDefaultValue(5)
				.setTooltip(Text.translatable("config.turbomc.monitor_list_items.tooltip"))
				.setSaveConsumer(value -> config.monitorListItems = value)
				.build());

		// ---- 按键权限 ----
		ConfigCategory keys = builder.getOrCreateCategory(Text.translatable("config.turbomc.category.keys"));

		keys.addEntry(entries
				.startTextDescription(Text.translatable("config.turbomc.keys.intro"))
				.build());

		keys.addEntry(entries
				.startStrList(Text.translatable("config.turbomc.blocked_keys"),
						new ArrayList<>(config.blockedKeysOrDefault()))
				.setDefaultValue(TurboMCConfig.DEFAULT_BLOCKED_KEYS)
				.setTooltip(Text.translatable("config.turbomc.blocked_keys.tooltip"))
				.setSaveConsumer(value -> config.blockedKeys = new ArrayList<>(value))
				.build());

		keys.addEntry(entries
				.startStrList(Text.translatable("config.turbomc.allowed_keys"),
						config.allowedKeys == null ? new ArrayList<>() : new ArrayList<>(config.allowedKeys))
				.setDefaultValue(new ArrayList<>())
				.setTooltip(Text.translatable("config.turbomc.allowed_keys.tooltip"))
				.setSaveConsumer(value -> config.allowedKeys = new ArrayList<>(value))
				.build());

		// 速查做成可展开子分类：点开就是全部写法，比悬停 tooltip 好读
		var help = entries.startSubCategory(Text.translatable("config.turbomc.key_help"));
		help.setExpanded(false);
		for (String line : new String[]{
				"config.turbomc.key_help.letters",
				"config.turbomc.key_help.function",
				"config.turbomc.key_help.common",
				"config.turbomc.key_help.arrows",
				"config.turbomc.key_help.modifiers",
				"config.turbomc.key_help.action"}) {
			help.add(entries.startTextDescription(Text.translatable(line)).build());
		}
		keys.addEntry(help.build());

		return builder.build();
	}
}
