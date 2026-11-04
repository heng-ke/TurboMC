package xyz.hegke.client.config;

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
public final class HkScriptConfigScreen {
	private HkScriptConfigScreen() {
	}

	public static Screen create(Screen parent) {
		HkScriptConfig config = HkScriptConfig.get();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.translatable("config.hk-script.title"))
				.setSavingRunnable(HkScriptConfig::save);

		ConfigEntryBuilder entries = builder.entryBuilder();

		// ---- 常规 ----
		ConfigCategory general = builder.getOrCreateCategory(Text.translatable("config.hk-script.category.general"));

		general.addEntry(entries
				.startBooleanToggle(Text.translatable("config.hk-script.master_enabled"), config.masterEnabled)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.hk-script.master_enabled.tooltip"))
				.setSaveConsumer(value -> config.masterEnabled = value)
				.build());

		general.addEntry(entries
				.startEnumSelector(Text.translatable("config.hk-script.hud_corner"), HudCorner.class, config.hudCorner)
				.setDefaultValue(HudCorner.TOP_LEFT)
				.setEnumNameProvider(corner -> Text.translatable(
						"config.hk-script.hud_corner." + corner.name().toLowerCase(Locale.ROOT)))
				.setTooltip(Text.translatable("config.hk-script.hud_corner.tooltip"))
				.setSaveConsumer(value -> config.hudCorner = value)
				.build());

		// ---- 自动化 ----
		ConfigCategory automation = builder.getOrCreateCategory(Text.translatable("config.hk-script.category.automation"));

		automation.addEntry(entries
				.startBooleanToggle(Text.translatable("config.hk-script.auto_tool"), config.autoToolEnabled)
				.setDefaultValue(false)
				.setTooltip(Text.translatable("config.hk-script.auto_tool.tooltip"))
				.setSaveConsumer(value -> config.autoToolEnabled = value)
				.build());

		automation.addEntry(entries
				.startIntSlider(Text.translatable("config.hk-script.auto_tool_radius"), config.autoToolSearchRadius, 1, 8)
				.setDefaultValue(3)
				.setTooltip(Text.translatable("config.hk-script.auto_tool_radius.tooltip"))
				.setSaveConsumer(value -> config.autoToolSearchRadius = value)
				.build());

		// ---- 脚本引擎 ----
		ConfigCategory script = builder.getOrCreateCategory(Text.translatable("config.hk-script.category.script"));

		script.addEntry(entries
				.startBooleanToggle(Text.translatable("config.hk-script.loop_throttle"), config.scratchLoopThrottle)
				.setDefaultValue(false)
				.setTooltip(Text.translatable("config.hk-script.loop_throttle.tooltip"))
				.setSaveConsumer(value -> {
					config.scratchLoopThrottle = value;
					// 循环是否让出是在编译期决定的，改动后需要重新编译脚本
					xyz.hegke.client.script.ScriptEngine.get().restart();
				})
				.build());

		script.addEntry(entries
				.startBooleanToggle(Text.translatable("config.hk-script.auto_respawn"), config.autoRespawn)
				.setDefaultValue(false)
				.setTooltip(Text.translatable("config.hk-script.auto_respawn.tooltip"))
				.setSaveConsumer(value -> config.autoRespawn = value)
				.build());

		// ---- 按键权限 ----
		ConfigCategory keys = builder.getOrCreateCategory(Text.translatable("config.hk-script.category.keys"));

		keys.addEntry(entries
				.startTextDescription(Text.translatable("config.hk-script.keys.intro"))
				.build());

		keys.addEntry(entries
				.startStrList(Text.translatable("config.hk-script.blocked_keys"),
						new ArrayList<>(config.blockedKeysOrDefault()))
				.setDefaultValue(HkScriptConfig.DEFAULT_BLOCKED_KEYS)
				.setTooltip(Text.translatable("config.hk-script.blocked_keys.tooltip"))
				.setSaveConsumer(value -> config.blockedKeys = new ArrayList<>(value))
				.build());

		keys.addEntry(entries
				.startStrList(Text.translatable("config.hk-script.allowed_keys"),
						config.allowedKeys == null ? new ArrayList<>() : new ArrayList<>(config.allowedKeys))
				.setDefaultValue(new ArrayList<>())
				.setTooltip(Text.translatable("config.hk-script.allowed_keys.tooltip"))
				.setSaveConsumer(value -> config.allowedKeys = new ArrayList<>(value))
				.build());

		// 速查做成可展开子分类：点开就是全部写法，比悬停 tooltip 好读
		var help = entries.startSubCategory(Text.translatable("config.hk-script.key_help"));
		help.setExpanded(false);
		for (String line : new String[]{
				"config.hk-script.key_help.letters",
				"config.hk-script.key_help.function",
				"config.hk-script.key_help.common",
				"config.hk-script.key_help.arrows",
				"config.hk-script.key_help.modifiers",
				"config.hk-script.key_help.action"}) {
			help.add(entries.startTextDescription(Text.translatable(line)).build());
		}
		keys.addEntry(help.build());

		return builder.build();
	}
}
