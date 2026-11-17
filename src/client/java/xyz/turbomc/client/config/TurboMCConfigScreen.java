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

		// 放分类第一项：它决定「进世界后要不要手动按 F6」，是其它行为的前提
		script.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.disable_on_exit"),
						config.shouldDisableEngineOnExit())
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.turbomc.disable_on_exit.tooltip"))
				.setSaveConsumer(value -> config.disableEngineOnExit = value)
				.build());

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

		script.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.chat_interval"),
						config.chatIntervalTicks == null ? 3 : config.chatIntervalTicks, 1, 20)
				.setDefaultValue(3)
				.setTooltip(Text.translatable("config.turbomc.chat_interval.tooltip"))
				.setSaveConsumer(value -> config.chatIntervalTicks = value)
				.build());

		script.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.chat_server_messages"),
						config.chatFromServerMessages)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.turbomc.chat_server_messages.tooltip"))
				.setSaveConsumer(value -> config.chatFromServerMessages = value)
				.build());

		script.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.chat_server_any_match"),
						config.chatServerAnyMatch)
				.setDefaultValue(false)
				.setTooltip(Text.translatable("config.turbomc.chat_server_any_match.tooltip"))
				.setSaveConsumer(value -> config.chatServerAnyMatch = value)
				.build());

		script.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.chat_max_segments"),
						config.chatMaxSegments == null ? 8 : config.chatMaxSegments, 1, 16)
				.setDefaultValue(8)
				.setTooltip(Text.translatable("config.turbomc.chat_max_segments.tooltip"))
				.setSaveConsumer(value -> config.chatMaxSegments = value)
				.build());

		// ---- AI 对话 ----
		ConfigCategory ai = builder.getOrCreateCategory(Text.translatable("config.turbomc.category.ai"));
		TurboMCConfig.Ai section = config.aiOrCreate();

		ai.addEntry(entries
				.startTextDescription(Text.translatable("config.turbomc.ai.hint"))
				.build());

		ai.addEntry(entries
				.startStrField(Text.translatable("config.turbomc.ai.base_url"),
						section.baseUrl == null ? "" : section.baseUrl)
				.setDefaultValue("")
				.setTooltip(Text.translatable("config.turbomc.ai.base_url.tooltip"))
				.setSaveConsumer(value -> section.baseUrl = value == null ? "" : value.trim())
				.build());

		ai.addEntry(entries
				.startStrField(Text.translatable("config.turbomc.ai.api_key"),
						section.apiKey == null ? "" : section.apiKey)
				.setDefaultValue("")
				.setTooltip(Text.translatable("config.turbomc.ai.api_key.tooltip"))
				.setSaveConsumer(value -> section.apiKey = value == null ? "" : value.trim())
				.build());

		ai.addEntry(entries
				.startStrField(Text.translatable("config.turbomc.ai.model"),
						section.model == null ? "" : section.model)
				.setDefaultValue("")
				.setTooltip(Text.translatable("config.turbomc.ai.model.tooltip"))
				.setSaveConsumer(value -> section.model = value == null ? "" : value.trim())
				.build());

		// 温度用文本框：Cloth 21.11 只有 intSlider，用滑块表达 0.0–2.0 要么步进太粗，
		// 要么得让用户自己换算除以 100。文本框直接写 0.7，解析失败就保留原值。
		ai.addEntry(entries
				.startStrField(Text.translatable("config.turbomc.ai.temperature"),
						String.valueOf(section.temperature == null ? 0.7 : section.temperature))
				.setDefaultValue("0.7")
				.setTooltip(Text.translatable("config.turbomc.ai.temperature.tooltip"))
				.setSaveConsumer(value -> {
					try {
						section.temperature = Double.parseDouble(value.trim());
					} catch (RuntimeException ignored) {
						// 输入不是数字：保留原值，不回写 0（0 是合法温度，会把输出变成死板复读）
					}
				})
				.build());

		ai.addEntry(entries
				.startStrField(Text.translatable("config.turbomc.ai.max_tokens"),
						String.valueOf(section.maxTokens == null ? 300 : section.maxTokens))
				.setDefaultValue("300")
				.setTooltip(Text.translatable("config.turbomc.ai.max_tokens.tooltip"))
				.setSaveConsumer(value -> {
					try {
						section.maxTokens = Integer.parseInt(value.trim());
					} catch (RuntimeException ignored) {
						// 同上：非法输入不覆盖
					}
				})
				.build());

		ai.addEntry(entries
				.startStrField(Text.translatable("config.turbomc.ai.timeout"),
						String.valueOf((section.timeoutMs == null ? 30000 : section.timeoutMs) / 1000))
				.setDefaultValue("30")
				.setTooltip(Text.translatable("config.turbomc.ai.timeout.tooltip"))
				.setSaveConsumer(value -> {
					try {
						section.timeoutMs = Integer.parseInt(value.trim()) * 1000;
					} catch (RuntimeException ignored) {
						// 同上
					}
				})
				.build());

		ai.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.ai.history_turns"),
						section.historyTurns == null ? 8 : section.historyTurns, 0, 50)
				.setDefaultValue(8)
				.setTooltip(Text.translatable("config.turbomc.ai.history_turns.tooltip"))
				.setSaveConsumer(value -> section.historyTurns = value)
				.build());

		ai.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.ai.max_concurrent"),
						section.maxConcurrent == null ? 2 : section.maxConcurrent, 1, 8)
				.setDefaultValue(2)
				.setTooltip(Text.translatable("config.turbomc.ai.max_concurrent.tooltip"))
				.setSaveConsumer(value -> section.maxConcurrent = value)
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
