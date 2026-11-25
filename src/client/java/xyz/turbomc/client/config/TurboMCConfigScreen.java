package xyz.turbomc.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cloth Config 配置界面。
 *
 * <p>仅当 Cloth Config 存在时才会被加载（从 {@link ModMenuIntegration} 间接引用），
 * 因此本类可以自由使用 Cloth Config 的 API。</p>
 *
 * <p><b>分类按「作用主题」划分</b>，不按代码里的配置段划分：同一主题的设置项必须
 * 出现在同一个分类里，否则「寻路开关在这一页、寻路参数在那一页」这类分裂会逼迫
 * 使用者在两页之间来回跳。分类顺序即使用频次：常规 → 脚本引擎 → 显示 → 聊天 →
 * 寻路 → AI 对话 → 开发者 → 按键权限。</p>
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
		// 一次性补齐 dev 段：下面四个条目都直接写 dev，逐处判空只会重复四遍
		TurboMCConfig.Dev dev = ensureDev(config);

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

		// ---- 脚本引擎 ----
		// 只放「引擎怎么跑」：启动/停止策略、时间预算、热重载、失焦行为。
		// 展示相关的项全部在「显示」，聊天相关全部在「聊天」。
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
				.startBooleanToggle(Text.translatable("config.turbomc.auto_reload"),
						config.shouldAutoReload())
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.turbomc.auto_reload.tooltip"))
				.setSaveConsumer(value -> config.autoReload = value)
				.build());

		// 间隔与开关同分类：给了「自动重载」却不给「多久查一次」，等于半个开关
		script.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.reload_interval"),
						Math.max(1, Math.round(config.reloadIntervalMsValue() / 1000f)), 1, 30)
				.setDefaultValue(2)
				.setTooltip(Text.translatable("config.turbomc.reload_interval.tooltip"))
				.setSaveConsumer(value -> config.reloadIntervalMs = value * 1000)
				.build());

		// VM 时间预算直接决定脚本跑多快、挤占多少渲染时间，是最该暴露的性能项
		script.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.vm_budget"),
						clampInt(config.scratchVmBudgetMs, 1, 16), 1, 16)
				.setDefaultValue(4)
				.setTooltip(Text.translatable("config.turbomc.vm_budget.tooltip"))
				.setSaveConsumer(value -> config.scratchVmBudgetMs = value)
				.build());

		script.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.keep_running_unfocused"),
						config.shouldKeepRunningWhenUnfocused())
				.setDefaultValue(false)
				.setTooltip(Text.translatable("config.turbomc.keep_running_unfocused.tooltip"))
				.setSaveConsumer(value -> config.keepRunningWhenUnfocused = value)
				.build());

		// 等待积木的单位（秒 / tick）。原先只能改 json，且改完要重启；
		// WaitUnit 每次都读实时配置，所以放在这里改完立即生效。
		script.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.wait_in_seconds"),
						config.showWaitInSeconds())
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.turbomc.wait_in_seconds.tooltip"))
				.setSaveConsumer(value -> config.waitInSeconds = value)
				.build());

		// ---- 显示 ----
		// 所有「画在屏幕上」的项集中在这里：HUD 位置、气泡、列表监视器、画框、
		// AI 结论。改外观不必再翻脚本引擎或 AI 分类。
		ConfigCategory display = builder.getOrCreateCategory(Text.translatable("config.turbomc.category.display"));

		display.addEntry(entries
				.startTextDescription(Text.translatable("config.turbomc.display.hint"))
				.build());

		// HUD 位置：两个百分比替代原来的「四角」下拉。
		// 0 = 贴起始边，100 = 贴另一侧，50 = 居中；角落只是这四个端点的特例，
		// 因此不再需要单独的选择器——两套位置来源并存必然打架。
		display.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.hud_offset_x"),
						(int) Math.round(config.hudPercentX()), 0, 100)
				.setDefaultValue(0)
				.setTooltip(Text.translatable("config.turbomc.hud_offset_x.tooltip"))
				.setSaveConsumer(value -> config.hudOffsetX = (double) value)
				.build());

		display.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.hud_offset_y"),
						(int) Math.round(config.hudPercentY()), 0, 100)
				.setDefaultValue(0)
				.setTooltip(Text.translatable("config.turbomc.hud_offset_y.tooltip"))
				.setSaveConsumer(value -> config.hudOffsetY = (double) value)
				.build());

		display.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.monitor_list_items"),
						config.monitorListItems, 1, 20)
				.setDefaultValue(5)
				.setTooltip(Text.translatable("config.turbomc.monitor_list_items.tooltip"))
				.setSaveConsumer(value -> config.monitorListItems = value)
				.build());

		display.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.hud_max_bubbles"),
						config.hudBubbleLimit(), 1, 6)
				.setDefaultValue(3)
				.setTooltip(Text.translatable("config.turbomc.hud_max_bubbles.tooltip"))
				.setSaveConsumer(value -> config.hudMaxBubbles = value)
				.build());

		// 界面按秒、配置存毫秒：玩家不需要知道 tick/毫秒，夹具与代码那边仍是精确单位
		display.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.hud_bubble_hold"),
						Math.round(config.hudBubbleHoldMsValue() / 1000f), 1, 15)
				.setDefaultValue(5)
				.setTooltip(Text.translatable("config.turbomc.hud_bubble_hold.tooltip"))
				.setSaveConsumer(value -> config.hudBubbleHoldMs = value * 1000)
				.build());

		// 画框上限：128 是「绘制开销可忽略」的经验值，脚本画得多时可以往上开
		display.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.frame_max_boxes"),
						config.frameLimit(), 16, 512)
				.setDefaultValue(128)
				.setTooltip(Text.translatable("config.turbomc.frame_max_boxes.tooltip"))
				.setSaveConsumer(value -> config.frameMaxBoxes = value)
				.build());

		// AI 的 HUD 展示：只影响界面，不影响请求，因此从 AI 分类移到这里
		display.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.ai.hud_hold_seconds"),
						config.aiHudHoldMs() / 1000, 1, 20)
				.setDefaultValue(5)
				.setTooltip(Text.translatable("config.turbomc.ai.hud_hold_seconds.tooltip"))
				.setSaveConsumer(value -> config.ai.hudHoldSeconds = value)
				.build());

		display.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.ai.hud_max_rows"),
						config.aiHudMaxRows(), 1, 5)
				.setDefaultValue(2)
				.setTooltip(Text.translatable("config.turbomc.ai.hud_max_rows.tooltip"))
				.setSaveConsumer(value -> config.ai.hudMaxRows = value)
				.build());

		display.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.ai.hud_reply_chars"),
						config.aiHudReplyChars(), 8, 64)
				.setDefaultValue(24)
				.setTooltip(Text.translatable("config.turbomc.ai.hud_reply_chars.tooltip"))
				.setSaveConsumer(value -> config.ai.hudReplyChars = value)
				.build());

		// ---- 聊天 ----
		// 全部是「脚本与聊天栏如何交互」，与引擎启停无关。
		ConfigCategory chat = builder.getOrCreateCategory(Text.translatable("config.turbomc.category.chat"));

		chat.addEntry(entries
				.startTextDescription(Text.translatable("config.turbomc.chat.hint"))
				.build());

		chat.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.chat_interval"),
						config.chatIntervalTicks == null ? 3 : config.chatIntervalTicks, 1, 20)
				.setDefaultValue(3)
				.setTooltip(Text.translatable("config.turbomc.chat_interval.tooltip"))
				.setSaveConsumer(value -> config.chatIntervalTicks = value)
				.build());

		chat.addEntry(entries
				.startIntSlider(Text.translatable("config.turbomc.chat_max_segments"),
						config.chatMaxSegments == null ? 8 : config.chatMaxSegments, 1, 16)
				.setDefaultValue(8)
				.setTooltip(Text.translatable("config.turbomc.chat_max_segments.tooltip"))
				.setSaveConsumer(value -> config.chatMaxSegments = value)
				.build());

		chat.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.chat_server_messages"),
						config.chatFromServerMessages)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.turbomc.chat_server_messages.tooltip"))
				.setSaveConsumer(value -> config.chatFromServerMessages = value)
				.build());

		chat.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.chat_server_any_match"),
						config.chatServerAnyMatch)
				.setDefaultValue(false)
				.setTooltip(Text.translatable("config.turbomc.chat_server_any_match.tooltip"))
				.setSaveConsumer(value -> config.chatServerAnyMatch = value)
				.build());

		// ---- 寻路 ----
		// 开关和参数在同一页：调「走向」时不需要跨分类找显示开关。
		ConfigCategory pathing = builder.getOrCreateCategory(Text.translatable("config.turbomc.category.pathing"));
		TurboMCConfig.Pathing path = config.pathing;

		pathing.addEntry(entries
				.startTextDescription(Text.translatable("config.turbomc.pathing.hint"))
				.build());

		pathing.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.show_path"), config.showPath)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.turbomc.show_path.tooltip"))
				.setSaveConsumer(value -> config.showPath = value)
				.build());

		pathing.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.show_path_hud"), config.showPathHud)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.turbomc.show_path_hud.tooltip"))
				.setSaveConsumer(value -> config.showPathHud = value)
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
		// 只留请求参数；HUD 三项已移到「显示」。
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
						double parsed = Double.parseDouble(value.trim());
						// "NaN" / "Infinity" 也能被 parseDouble 接受，但 Gson 序列化时会抛异常，
						// 表现为「点保存直接炸」；运行时还会用非法值请求
						if (Double.isFinite(parsed)) {
							section.temperature = Math.max(0.0, Math.min(2.0, parsed));
						}
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
						// 保存时就钳到有效范围：否则界面会显示 600 秒，实际生效仍是 300 秒（B9）
						long ms = (long) Integer.parseInt(value.trim()) * 1000L;
						section.timeoutMs = TurboMCConfig.clampTimeout(
								ms > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ms);
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

		// ---- 开发者 ----
		// 远程桥单独成页：普通玩家不该在「脚本引擎」列表底部撞见 Origin 白名单，
		// 也不该因为顺手滑到底就打开一个本机监听端口。
		ConfigCategory developer =
				builder.getOrCreateCategory(Text.translatable("config.turbomc.category.developer"));

		developer.addEntry(entries
				.startTextDescription(Text.translatable("config.turbomc.developer.hint"))
				.build());

		developer.addEntry(entries
				.startBooleanToggle(Text.translatable("config.turbomc.dev_remote"), config.remoteEnabled())
				.setDefaultValue(false)
				.setTooltip(Text.translatable("config.turbomc.dev_remote.tooltip"))
				.setSaveConsumer(value -> dev.enabled = value)
				.build());

		// 端口用文本框：0 表示自动分配，固定在 HUD 上写着实际端口；
		// 滑块无法表达「0 = 自动」，还会把 0 与 1 混成一项。
		developer.addEntry(entries
				.startStrField(Text.translatable("config.turbomc.dev_port"),
						String.valueOf(dev.port == null ? 0 : dev.port))
				.setDefaultValue("0")
				.setTooltip(Text.translatable("config.turbomc.dev_port.tooltip"))
				.setSaveConsumer(value -> dev.port = parsePort(value, dev.port))
				.build());

		developer.addEntry(entries
				.startStrField(Text.translatable("config.turbomc.dev_instance_name"),
						dev.instanceName == null ? "" : dev.instanceName)
				.setDefaultValue("")
				.setTooltip(Text.translatable("config.turbomc.dev_instance_name.tooltip"))
				.setSaveConsumer(value -> dev.instanceName = value == null ? "" : value.trim())
				.build());

		// 「允许的来源」：远程桥只校验 WebSocket 的 Origin，所以自建 TurboWarp 站点
		// （https://自己的域名、http://localhost:端口）必须能在这里加，否则只能手改 json。
		//
		// 列表里直接显示「当前生效值」：配置为空时就是出厂默认那几条。使用者打开界面看到的是
		// 实际放行什么，而不是一个空列表 + 一句「留空即用默认值」——后者等于把默认值藏起来。
		// 重置按钮（Cloth 的 per-entry reset）指向出厂默认，一键回到初始状态。
		List<String> effectiveOrigins = new ArrayList<>(config.remoteAllowedOrigins());
		developer.addEntry(entries
				.startStrList(Text.translatable("config.turbomc.dev_origins"), effectiveOrigins)
				.setDefaultValue(new ArrayList<>(TurboMCConfig.DEFAULT_DEV_ORIGINS))
				.setExpanded(false)
				.setTooltip(Text.translatable("config.turbomc.dev_origins.tooltip"))
				// 拼错的来源不会报错，只会「连不上」，所以在格子里就地标红：
				// 合法形态是 scheme://host[:*] ，或字面量 null（file:// 页面）
				.setCellErrorSupplier(value -> isOriginPattern(value)
						? java.util.Optional.empty()
						: java.util.Optional.of(Text.translatable("config.turbomc.dev_origins.invalid")))
				.setSaveConsumer(value -> {
					// 整份清空仍按「用出厂默认」处理（见 remoteAllowedOrigins），
					// 所以这里原样保存即可，不需要特判空列表
					dev.allowedOrigins = value == null
							? new ArrayList<>()
							: new ArrayList<>(value);
				})
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

	/** 保证 dev 段存在；Gson 用 Unsafe 建实例，缺该段时字段是 {@code null}。 */
	private static TurboMCConfig.Dev ensureDev(TurboMCConfig config) {
		if (config.dev == null) {
			config.dev = new TurboMCConfig.Dev();
		}
		return config.dev;
	}

	/** 整数钳制，用于滑块初值：越界的旧配置不该让滑块显示成 0。 */
	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	/**
	 * 解析端口：{@code 0} 表示自动；其余钳到 1–65535。
	 *
	 * <p>非法输入保留原值而不是归 0——「我填了 abc」变成「自动分配」会让人误以为配置没保存。
	 * 与 {@code TurboMCConfig.remotePort()} 的语义保持一致。</p>
	 */
	private static int parsePort(String raw, Integer fallback) {
		try {
			int value = Integer.parseInt(raw == null ? "" : raw.trim());
			if (value == 0) {
				return 0;
			}
			return Math.max(1, Math.min(65535, value));
		} catch (RuntimeException ignored) {
			return fallback == null ? 0 : fallback;
		}
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
