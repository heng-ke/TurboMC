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
	/**
	 * 已保存密钥的占位显示。
	 *
	 * <p>固定 10 个圆点、与真实长度无关：不给「从字符数反推密钥长度」留口子。
	 * 这是 Cloth Config 没有密码框时的折衷——已存的值不再回显，新输入的内容才会显示。</p>
	 */
	private static final String API_KEY_MASK = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

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

		// HUD 位置：两个百分比替代原来的「四角」下拉。
		// 0 = 贴起始边，100 = 贴另一侧，50 = 居中；角落只是这四个端点的特例，
		// 因此不再需要单独的选择器——两套位置来源并存必然打架。
		general.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.hud_offset_x"),
						(int) Math.round(config.hudPercentX()), 0, 100)
				.setDefaultValue(0)
				.setTooltip(Text.translatable("config.turbomc.hud_offset_x.tooltip"))
				.setSaveConsumer(value -> config.hudOffsetX = (double) value)
				.build());

		general.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.hud_offset_y"),
						(int) Math.round(config.hudPercentY()), 0, 100)
				.setDefaultValue(0)
				.setTooltip(Text.translatable("config.turbomc.hud_offset_y.tooltip"))
				.setSaveConsumer(value -> config.hudOffsetY = (double) value)
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
				.startBooleanToggle(Text.translatable("config.turbomc.auto_reload"),
						config.shouldAutoReload())
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.turbomc.auto_reload.tooltip"))
				.setSaveConsumer(value -> config.autoReload = value)
				.build());

		script.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.keep_running_unfocused"),
						config.shouldKeepRunningWhenUnfocused())
				.setDefaultValue(false)
				.setTooltip(Text.translatable("config.turbomc.keep_running_unfocused.tooltip"))
				.setSaveConsumer(value -> config.keepRunningWhenUnfocused = value)
				.build());

		script.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.monitor_list_items"),
						config.monitorListItems, 1, 20)
				.setDefaultValue(5)
				.setTooltip(Text.translatable("config.turbomc.monitor_list_items.tooltip"))
				.setSaveConsumer(value -> config.monitorListItems = value)
				.build());

		script.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.hud_max_bubbles"),
						config.hudBubbleLimit(), 1, 6)
				.setDefaultValue(3)
				.setTooltip(Text.translatable("config.turbomc.hud_max_bubbles.tooltip"))
				.setSaveConsumer(value -> config.hudMaxBubbles = value)
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

		// 开发调试：远程桥。默认关，普通玩家保持关闭即可。
		script.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.dev_remote"),
						config.remoteEnabled())
				.setDefaultValue(false)
				.setTooltip(Text.translatable("config.turbomc.dev_remote.tooltip"))
				.setSaveConsumer(value -> {
					if (config.dev == null) {
						config.dev = new TurboMCConfig.Dev();
					}
					config.dev.enabled = value;
				})
				.build());

		// 「允许的来源」：远程桥只校验 WebSocket 的 Origin，所以自建 TurboWarp 站点
		// （https://自己的域名、http://localhost:端口）必须能在这里加，否则只能手改 json。
		// 留空 = 用内置默认值；手填的列表会整体替换默认值，不是追加。
		script.addEntry(entries
				.startStrList(Text.translatable("config.turbomc.dev_origins"),
						config.dev == null || config.dev.allowedOrigins == null
								? new ArrayList<>()
								: new ArrayList<>(config.dev.allowedOrigins))
				.setDefaultValue(new ArrayList<>())
				.setExpanded(false)
				.setTooltip(Text.translatable("config.turbomc.dev_origins.tooltip"))
				// 拼错的来源不会报错，只会「连不上」，所以在格子里就地标红：
				// 合法形态是 scheme://host[:*] ，或字面量 null（file:// 页面）
				.setCellErrorSupplier(value -> isOriginPattern(value)
						? java.util.Optional.empty()
						: java.util.Optional.of(Text.translatable("config.turbomc.dev_origins.invalid")))
				.setSaveConsumer(value -> {
					if (config.dev == null) {
						config.dev = new TurboMCConfig.Dev();
					}
					config.dev.allowedOrigins = value == null
							? new ArrayList<>()
							: new ArrayList<>(value);
				})
				.build());

		// ---- 寻路 ----
		// 放在脚本引擎之后：这些是「走向」积木的精度参数，改完下一 tick 即生效，无需重启。
		ConfigCategory pathing = builder.getOrCreateCategory(Text.translatable("config.turbomc.category.pathing"));
		TurboMCConfig.Pathing path = config.pathing;

		pathing.addEntry(entries
				.startTextDescription(Text.translatable("config.turbomc.pathing.hint"))
				.build());

		// 小数项用文本框：Cloth 21.11 只有 intSlider，0.6 这类值要么步进太粗，
		// 要么得让用户自己换算。解析失败或越界按钳制后的值回写，不保留非法输入。
		pathing.addEntry(entries
				.startStrField(Text.translatable("config.turbomc.pathing.arrive_horizontal"),
						num(path.arriveHorizontal, 0.6))
				.setDefaultValue("0.6")
				.setTooltip(Text.translatable("config.turbomc.pathing.arrive_horizontal.tooltip"))
				.setSaveConsumer(value -> path.arriveHorizontal =
						parseClamped(value, config.pathArriveHorizontal(), 0.30, 1.20))
				.build());

		pathing.addEntry(entries
				.startStrField(Text.translatable("config.turbomc.pathing.arrive_vertical"),
						num(path.arriveVertical, 0.6))
				.setDefaultValue("0.6")
				.setTooltip(Text.translatable("config.turbomc.pathing.arrive_vertical.tooltip"))
				.setSaveConsumer(value -> path.arriveVertical =
						parseClamped(value, config.pathArriveVertical(), 0.50, 0.90))
				.build());

		pathing.addEntry(entries
				.startStrField(Text.translatable("config.turbomc.pathing.node_reach"),
						num(path.nodeReach, 0.5))
				.setDefaultValue("0.5")
				.setTooltip(Text.translatable("config.turbomc.pathing.node_reach.tooltip"))
				.setSaveConsumer(value -> path.nodeReach =
						parseClamped(value, config.pathNodeReach(), 0.35, 0.60))
				.build());

		pathing.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.pathing.accuracy"),
						config.pathAccuracy(), 0, 2)
				.setDefaultValue(1)
				.setTooltip(Text.translatable("config.turbomc.pathing.accuracy.tooltip"))
				.setSaveConsumer(value -> path.accuracy = value)
				.build());

		pathing.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.pathing.stand_turn_deg"),
						(int) Math.round(config.pathStandTurnDeg()), 20, 60)
				.setDefaultValue(30)
				.setTooltip(Text.translatable("config.turbomc.pathing.stand_turn_deg.tooltip"))
				.setSaveConsumer(value -> path.standTurnDeg = (double) value)
				.build());

		pathing.addEntry(entries
				.startEnumSelector(Text.translatable("config.turbomc.pathing.effort"),
						PathEffort.class, config.pathEffort())
				.setDefaultValue(PathEffort.NORMAL)
				.setEnumNameProvider(effort -> Text.translatable(
						"config.turbomc.pathing.effort." + effort.name().toLowerCase(Locale.ROOT)))
				.setTooltip(Text.translatable("config.turbomc.pathing.effort.tooltip"))
				.setSaveConsumer(value -> path.effort = value)
				.build());

		pathing.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.pathing.max_distance"),
						config.pathMaxDistance(), 32, 192)
				.setDefaultValue(128)
				.setTooltip(Text.translatable("config.turbomc.pathing.max_distance.tooltip"))
				.setSaveConsumer(value -> path.maxDistance = value)
				.build());

		// ---- AI 对话 ----
		ConfigCategory ai = builder.getOrCreateCategory(Text.translatable("config.turbomc.category.ai"));
		TurboMCConfig.Ai section = config.ai;

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
				.startTextDescription(Text.translatable("config.turbomc.ai.api_key.hint"))
				.build());

		// 遮码：已保存的密钥不回显。字段显示掩码串，用户输入新值才覆盖；
		// 保持掩码或留空都视为「不改」，避免误触把密钥清空。
		ai.addEntry(entries
				.startStrField(Text.translatable("config.turbomc.ai.api_key"),
						section.apiKey == null || section.apiKey.isBlank() ? "" : API_KEY_MASK)
				.setDefaultValue("")
				.setTooltip(Text.translatable("config.turbomc.ai.api_key.tooltip"))
				.setSaveConsumer(value -> {
					String text = value == null ? "" : value.trim();
					if (text.isEmpty() || text.equals(API_KEY_MASK)) {
						return;
					}
					section.apiKey = "-".equals(text) ? "" : text;
				})
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
						String.valueOf(section.maxTokens == null
								? TurboMCConfig.DEFAULT_MAX_TOKENS : section.maxTokens))
				.setDefaultValue(String.valueOf(TurboMCConfig.DEFAULT_MAX_TOKENS))
				.setTooltip(Text.translatable("config.turbomc.ai.max_tokens.tooltip"))
				.setSaveConsumer(value -> {
					try {
						// 保存时就钳到有效范围：否则界面会留住一个运行时永不生效的数字（DEF-05）
						section.maxTokens = TurboMCConfig.clampMaxTokens(Integer.parseInt(value.trim()));
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
						section.maxConcurrent == null ? 2 : section.maxConcurrent, 1, 16)
				.setDefaultValue(2)
				.setTooltip(Text.translatable("config.turbomc.ai.max_concurrent.tooltip"))
				.setSaveConsumer(value -> section.maxConcurrent = value)
				.build());

		// HUD 展示：只影响界面，不影响请求
		ai.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.ai.hud_hold_seconds"),
						section.hudHoldSeconds == null ? 5 : section.hudHoldSeconds, 1, 20)
				.setDefaultValue(5)
				.setTooltip(Text.translatable("config.turbomc.ai.hud_hold_seconds.tooltip"))
				.setSaveConsumer(value -> section.hudHoldSeconds = value)
				.build());

		ai.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.ai.hud_max_rows"),
						section.hudMaxRows == null ? 2 : section.hudMaxRows, 1, 5)
				.setDefaultValue(2)
				.setTooltip(Text.translatable("config.turbomc.ai.hud_max_rows.tooltip"))
				.setSaveConsumer(value -> section.hudMaxRows = value)
				.build());

		ai.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.ai.hud_reply_chars"),
						section.hudReplyChars == null ? 24 : section.hudReplyChars, 8, 64)
				.setDefaultValue(24)
				.setTooltip(Text.translatable("config.turbomc.ai.hud_reply_chars.tooltip"))
				.setSaveConsumer(value -> section.hudReplyChars = value)
				.build());

		// ---- 按键权限 ----
		ConfigCategory keys = builder.getOrCreateCategory(Text.translatable("config.turbomc.category.keys"));

		keys.addEntry(entries
				.startTextDescription(Text.translatable("config.turbomc.keys.intro"))
				.build());

		keys.addEntry(entries
				.startStrList(Text.translatable("config.turbomc.blocked_keys"),
						new ArrayList<>(config.blockedKeys))
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

	/** 小数项的显示文本：整数省略小数点后的零，其余原样。 */
	private static String num(Double value, double fallback) {
		double number = value == null ? fallback : value;
		return number == Math.rint(number) ? String.valueOf((long) number) : String.valueOf(number);
	}

	/**
	 * 解析小数并钳制到 [{@code min}, {@code max}]。
	 *
	 * <p>解析失败返回 {@code fallback}（调用方传当前生效值），不写 0——
	 * 这些参数都容不下 0，静默归零会让寻路直接失效。</p>
	 */
	private static double parseClamped(String raw, double fallback, double min, double max) {
		try {
			double value = Double.parseDouble(raw == null ? "" : raw.trim());
			return value < min ? min : Math.min(value, max);
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	/**
	 * 一行白名单是否写成了合法形态。
	 *
	 * <p>只做形态检查，不联网也不解析端口：{@code scheme://host[:port|:*]} 或字面量
	 * {@code null}（{@code file://} 页面）。空行交给 Cloth 自己处理——它在保存时就会忽略，
	 * 这里报红只会让人困惑。</p>
	 */
	private static boolean isOriginPattern(String raw) {
		if (raw == null) {
			return true;
		}
		String value = raw.trim();
		if (value.isEmpty() || "null".equals(value)) {
			return true;
		}
		if (value.indexOf(' ') >= 0) {
			return false;
		}
		int separator = value.indexOf("://");
		if (separator <= 0 || separator + 3 >= value.length()) {
			return false;
		}
		for (int i = 0; i < separator; i++) {
			char c = value.charAt(i);
			boolean schemeChar = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
					|| c == '+' || c == '-' || c == '.';
			if (!schemeChar) {
				return false;
			}
		}
		return true;
	}
}
