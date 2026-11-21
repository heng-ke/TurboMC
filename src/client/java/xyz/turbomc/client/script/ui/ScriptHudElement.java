package xyz.turbomc.client.script.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import xyz.turbomc.client.config.TurboMCConfig;
import xyz.turbomc.client.script.ScriptEngine;
import xyz.turbomc.client.script.action.path.PathFollower;
import xyz.turbomc.client.script.ai.AiService;
import xyz.turbomc.client.script.runtime.ExtensionHealth;
import xyz.turbomc.client.script.runtime.MonitorStore;
import xyz.turbomc.client.script.runtime.PerfProbe;
import xyz.turbomc.client.script.vm.RhinoVm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 脚本 HUD：引擎状态行 + 脚本「说 / 思考」的气泡。
 *
 * <p>之所以要显示气泡，是因为 {@code looks_say} / {@code looks_think} 是 scratch-vm 的原生积木，
 * 游戏内没有任何渲染出口——不在这里补，脚本就没法对外说话。</p>
 *
 * <p>气泡状态从 VM 的每个 target 上读取，用 {@code usageId} 判定是否是新的一句；
 * 每句停留 {@value #HOLD_MS} 毫秒后自动消失，避免一句「你好」永久挡住画面。</p>
 *
 * <p>同时显示几条由配置 {@code hudMaxBubbles} 决定（默认 3，范围 1～6）：气泡是叠在
 * 画面上的，条数必须封顶。被挤掉的不静默丢弃——末尾补一行「…还有 N 条」，
 * 否则四五个角色同时说话时，玩家会以为某个角色根本没开口。</p>
 */
public final class ScriptHudElement implements HudElement {
	/** 一句气泡停留时长。 */
	private static final long HOLD_MS = 5000L;
	/** 单行最大像素宽度，超出按宽度截断。 */
	private static final int MAX_WIDTH = 200;
	private static final int LINE_HEIGHT = 10;
	private static final int PADDING = 3;
	private static final int MARGIN = 4;

	/** 气泡簿记：到期删除并记住 usageId，避免「删掉即复活」（DEF-06）。 */
	private final SpeechBubbleBook bubbles = new SpeechBubbleBook(HOLD_MS);

	/** 寻路两行的缓存：内容没变就不重建 Text，避免每帧几十个临时对象。 */
	private String pathCacheKey;
	private Text pathLine1;
	private Text pathLine2;

	@Override
	public void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.world == null) {
			bubbles.clear();
			return;
		}
		// F1 隐藏 HUD 时跟随隐藏，不去抢玩家的画面
		if (client.options.hudHidden) {
			return;
		}
		// 打开任何界面（背包、F7、聊天）时不再绘制，避免压在 GUI 上
		if (client.currentScreen != null) {
			return;
		}
		TurboMCConfig config = TurboMCConfig.get();
		if (!config.masterEnabled) {
			bubbles.clear();
			return;
		}

		long now = System.currentTimeMillis();
		// 这一段每帧都进 Rhino 读气泡与监视器，是 HUD 侧唯一的可变开销；绘制本身不算在内
		long perf = PerfProbe.begin();
		pollSpeech(now);
		expire(now);

		TextRenderer textRenderer = client.textRenderer;
		List<Row> rows = buildRows(textRenderer);
		PerfProbe.end("hud.read", perf);
		if (rows.isEmpty()) {
			return;
		}
		draw(context, textRenderer, config, rows);
	}

	/** 渲染单位：一行文字 + 颜色，背景按最宽的一行铺。 */
	private record Row(Text text, int color) {
	}

	private List<Row> buildRows(TextRenderer textRenderer) {
		List<Row> rows = new ArrayList<>();

		// 状态行：不再只有「运行中 / 已停用」两态。载入失败、跑崩、停止全部各有各的说法，
		// 而且原因直接跟在后面——HUD 是玩家在游戏内唯一的自查出口。
		ScriptEngine engine = ScriptEngine.get();
		String prefix = switch (engine.status()) {
			case RUNNING -> "\u25CF ";
			case LOAD_FAILED, RUNTIME_ERROR -> "\u2715 ";
			default -> "\u25CB ";
		};
		rows.add(new Row(
				Text.literal(textRenderer.trimToWidth(prefix + engine.hudLine(), MAX_WIDTH)),
				engine.statusColor()));
		// 故障原因单独一行：挤在状态行里会被宽度裁掉，等于没显示
		String detail = engine.statusDetail().getString();
		if (!detail.isEmpty()) {
			rows.add(new Row(Text.literal(textRenderer.trimToWidth(detail, MAX_WIDTH)), engine.statusColor()));
		}

		// 扩展与模组对不上时，症状是「积木拖不出来」或「点了没反应」——都像模组坏了。
		// 常驻一行，玩家才知道该重载扩展还是换 jar（明细在日志里）
		String extensionWarning = ExtensionHealth.warning();
		if (!extensionWarning.isEmpty()) {
			rows.add(new Row(Text.literal(textRenderer.trimToWidth(
					"\u26A0 " + extensionWarning, MAX_WIDTH)), 0xFFFF6B6B));
		}

		TurboMCConfig config = TurboMCConfig.get();
		if (config.showPathHud) {
			addPathRows(rows);
		}
		addMonitorRows(rows, textRenderer);
		addAiRows(rows, textRenderer);

		// 按时间正序：旧消息在上，新消息贴着状态行往下排。
		// 条数上限走配置（默认 3）：气泡是叠在画面上的，必须封顶；
		// 被挤掉的不静默丢弃，补一行「…还有 N 条」，否则会以为某个角色没说话。
		SpeechBubbleBook.View bubbleView = bubbles.view(config.hudBubbleLimit());
		for (SpeechBubbleBook.Bubble bubble : bubbleView.shown()) {
			String line = bubble.name() + "：" + bubble.text();
			String clipped = textRenderer.trimToWidth(line, MAX_WIDTH);
			rows.add(new Row(Text.literal(clipped), bubble.think() ? 0xFFB0B0B0 : 0xFFFFFFFF));
		}
		if (bubbleView.hidden() > 0) {
			rows.add(new Row(Text.translatable("hud.turbomc.bubbles_more", bubbleView.hidden()), 0xFF9E9E9E));
		}
		return rows;
	}

	/**
	 * 变量 / 列表监视器。
	 *
	 * <p>scratch-vm 的监视器本来由舞台 GUI 绘制，游戏内没有那一层，改由 HUD 承担：
	 * 脚本「显示变量」只是登记名字，值在这里现读 VM。Minecraft 的 tick 与渲染同在
	 * 主线程，和 {@link #pollSpeech} 取气泡是同一条路，不存在并发读写。</p>
	 */
	private void addMonitorRows(List<Row> rows, TextRenderer textRenderer) {
		Map<String, MonitorStore.Kind> monitors = MonitorStore.snapshot();
		if (monitors.isEmpty()) {
			return;
		}
		RhinoVm vm = RhinoVm.get();
		int limit = Math.max(1, TurboMCConfig.get().monitorListItems);
		for (Map.Entry<String, MonitorStore.Kind> entry : monitors.entrySet()) {
			String name = entry.getKey();
			if (entry.getValue() == MonitorStore.Kind.LIST) {
				rows.add(new Row(listRow(textRenderer, name, vm, limit), 0xFFFFD54F));
				continue;
			}
			String text = name + " = " + stringify(vm.getVariable(name));
			rows.add(new Row(Text.literal(textRenderer.trimToWidth(text, MAX_WIDTH)), 0xFFFFD54F));
		}
	}

	/**
	 * AI 会话状态。
	 *
	 * <p>三条原则：<b>有活动才出现</b>（空闲时不留一行「AI 空闲」占位）、
	 * <b>回复只给开头若干字</b>（可配，聊天原文可能很长，也可能涉及别人的话，完整内容留给面板）、
	 * <b>失败必须给原因</b>——这是「为什么没回话」在游戏内唯一能自查的出口。</p>
	 *
	 * <p>未配置时只在「用过」之后提示一次，避免从没用过 AI 的人被一行红字长期打扰。</p>
	 */
	private void addAiRows(List<Row> rows, TextRenderer textRenderer) {
		AiService.Snapshot snapshot = AiService.get().snapshot();
		if (!snapshot.configured()) {
			if (snapshot.attempted()) {
				addAiRow(rows, textRenderer,
						Text.translatable("hud.turbomc.ai.unconfigured"), 0xFFFF7F7F);
			}
			return;
		}
		TurboMCConfig config = TurboMCConfig.get();
		int maxRows = config.aiHudMaxRows();
		int replyChars = config.aiHudReplyChars();
		long holdMs = config.aiHudHoldMs();
		long now = System.currentTimeMillis();
		int shown = 0;
		for (AiService.SessionStatus status : snapshot.sessions()) {
			if (shown >= maxRows) {
				break;
			}
			String name = status.name();
			switch (status.phase()) {
				case WAITING -> {
					addAiRow(rows, textRenderer, Text.translatable("hud.turbomc.ai.waiting", name,
							seconds(status.pendingMs())), 0xFFFFD54F);
					shown++;
				}
				case OK -> {
					// 结论只停留几秒：常驻会把屏幕变成日志
					if (now - status.updatedAt() > holdMs) {
						continue;
					}
					// 头部走翻译键，尾巴直接拼字面量：回复内容不该经过格式串，
					// 少一个 %s 就整段消失，这种坑排查成本远大于省下来的几行代码
					String head = Text.translatable("hud.turbomc.ai.ok", name,
							seconds(status.costMs())).getString();
					String tail = status.reply().isEmpty()
							? Text.translatable("hud.turbomc.ai.empty_reply").getString()
							: preview(status.reply(), replyChars);
					addAiRow(rows, textRenderer, Text.literal(head + "  " + tail), 0xFF55FF55);
					shown++;
				}
				case FAILED -> {
					if (now - status.updatedAt() > holdMs) {
						continue;
					}
					addAiRow(rows, textRenderer, Text.translatable("hud.turbomc.ai.failed", name,
							preview(status.error(), 20)), 0xFFFF5555);
					shown++;
				}
				default -> {
					// IDLE：还没发过请求，不占位
				}
			}
		}
	}

	/** 统一按 {@link #MAX_WIDTH} 裁剪：行太宽会把整个 HUD 底板撑出屏幕。 */
	private void addAiRow(List<Row> rows, TextRenderer textRenderer, Text text, int color) {
		rows.add(new Row(Text.literal(textRenderer.trimToWidth(text.getString(), MAX_WIDTH)), color));
	}

	private static String seconds(long millis) {
		return String.format("%.1f", millis / 1000.0);
	}

	/** 按码点截断，避免把 emoji 的代理对切成半个。 */
	private static String preview(String text, int limit) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		if (text.codePointCount(0, text.length()) <= limit) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, limit)) + "…";
	}

	/**
	 * 列表监视器一行：总数必须留在屏上。
	 *
	 * <p>先前把「…(共 N 项)」接在项目后面，再由 {@code trimToWidth} 整体裁剪——被裁掉的
	 * 永远是结尾，也就是总数那一截，玩家只看得到前几项、不知道列表到底多长（DEF-07）。
	 * 这里反过来：先给头（名字）与尾（总数）留出宽度，中间的项目用剩余宽度截断。</p>
	 */
	private Text listRow(TextRenderer textRenderer, String name, RhinoVm vm, int limit) {
		ListSnapshot snapshot = snapshotList(vm, name, limit);
		String head = name + " = [";
		String suffix = snapshot.truncated() ? " …(共 " + snapshot.total() + " 项)]" : "]";
		int room = MAX_WIDTH - textRenderer.getWidth(head) - textRenderer.getWidth(suffix);
		String items = room > 0 ? textRenderer.trimToWidth(snapshot.items(), room) : "";
		// 兜底：名字本身超长时（head + suffix 已经放不下），仍以不越界为先
		return Text.literal(textRenderer.trimToWidth(head + items + suffix, MAX_WIDTH));
	}

	/** 列表快照：前 N 项的文本、总项数、是否被截断。 */
	private record ListSnapshot(String items, int total, boolean truncated) {
	}

	/** 列表前 N 项，整表刷在 HUD 上会挡住半个屏幕。 */
	private ListSnapshot snapshotList(RhinoVm vm, String name, int limit) {
		try {
			JsonElement parsed = JsonParser.parseString(vm.getList(name));
			if (!parsed.isJsonArray()) {
				return new ListSnapshot("", 0, false);
			}
			JsonArray array = parsed.getAsJsonArray();
			int shown = Math.min(limit, array.size());
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < shown; i++) {
				if (i > 0) {
					builder.append(", ");
				}
				JsonElement item = array.get(i);
				builder.append(item.isJsonNull() ? "" : item.getAsString());
			}
			return new ListSnapshot(builder.toString(), array.size(), array.size() > shown);
		} catch (RuntimeException e) {
			return new ListSnapshot("", 0, false);
		}
	}

	/** 值转文本：VM 未就绪时可能拿到 null，别让 HUD 上出现 "null"。 */
	private static String stringify(Object value) {
		if (value == null) {
			return "";
		}
		String text = String.valueOf(value);
		return "null".equals(text) ? "" : text;
	}

	/**
	 * 寻路状态两行。没有会话时什么都不加——终端 HUD 不该常年挂一个「空闲」。
	 *
	 * <p>内容变化才重建 {@link Text}：距离每 tick 都在变，但取到 0.1 格后大约每 5 tick 才变一次，
	 * 中间这些帧直接复用同一对对象。</p>
	 */
	private void addPathRows(List<Row> rows) {
		PathFollower.Status status = PathFollower.status();
		if (status == null) {
			pathCacheKey = null;
			return;
		}
		String key = status.phase() + '|' + status.target() + '|'
				+ status.nodeIndex() + '/' + status.nodeCount() + '|'
				+ status.replans() + '|' + status.partialReplans() + '|' + status.planMs() + '|'
				+ Math.round(status.distance() * 10.0);
		if (!key.equals(pathCacheKey)) {
			pathCacheKey = key;
			pathLine1 = Text.translatable("hud.turbomc.path.distance",
					String.format("%.1f", status.distance()),
					status.target().getX(), status.target().getY(), status.target().getZ());
			Text phase = Text.translatable("hud.turbomc.path.phase." + status.phase());
			pathLine2 = status.nodeCount() == 0
					? Text.translatable("hud.turbomc.path.planning")
					: status.partialReplans() > 0
							// 分段过：多显示「分段 N」，否则看不出「重算 0」其实已经走了好几段
							? Text.translatable("hud.turbomc.path.progress.partial",
									status.nodeIndex(), status.nodeCount(), status.replans(),
									status.partialReplans(), status.planMs(), phase)
							: Text.translatable("hud.turbomc.path.progress",
									status.nodeIndex(), status.nodeCount(), status.replans(),
									status.planMs(), phase);
		}
		int color = switch (status.phase()) {
			case "follow", "turning", "planning" -> 0xFF55FFFF;
			case "arrived" -> 0xFF55FF55;
			default -> 0xFFFFAA55;
		};
		rows.add(new Row(pathLine1, color));
		rows.add(new Row(pathLine2, color));
	}

	private void draw(DrawContext context, TextRenderer textRenderer, TurboMCConfig config, List<Row> rows) {
		int widest = 0;
		for (Row row : rows) {
			widest = Math.max(widest, textRenderer.getWidth(row.text()));
		}
		int boxWidth = widest + PADDING * 2;
		int boxHeight = rows.size() * LINE_HEIGHT + PADDING * 2;

		// 位置由百分比决定，与旧的四角枚举解耦：角落只是「0 / 100」的特例
		int x = axisOffset(context.getScaledWindowWidth(), boxWidth, config.hudPercentX());
		int y = axisOffset(context.getScaledWindowHeight(), boxHeight, config.hudPercentY());

		// 半透明底衬：白字直接压在高对比度场景上会看不清
		context.fill(x, y, x + boxWidth, y + boxHeight, 0x90000000);

		int textY = y + PADDING;
		for (Row row : rows) {
			context.drawTextWithShadow(textRenderer, row.text(), x + PADDING, textY, row.color());
			textY += LINE_HEIGHT;
		}
	}

	/**
	 * 百分比 → 该轴上的像素坐标。
	 *
	 * <p>映射的是「可放置区间」而不是裸屏幕坐标：0% 贴住边距，100% 贴住另一侧边距，
	 * 50% 居中。这样任意百分比都天然落在屏幕内，不需要事后裁剪；代价是面板越宽，
	 * 同一个百分比对应的左边界越靠左——刻意取舍，换来的是「永远不会有一半跑出屏幕」。</p>
	 *
	 * <p>面板比可用区域还宽时（小窗口 + 长监视器名），区间取 0 并贴住起始边距：
	 * 右侧溢出至少保证开头可读，居中会让两侧都被裁掉。</p>
	 */
	private static int axisOffset(int screenSize, int boxSize, double percent) {
		int range = screenSize - boxSize - MARGIN * 2;
		if (range <= 0) {
			return MARGIN;
		}
		return MARGIN + (int) Math.round(range * (percent / 100.0));
	}

	/**
	 * 读取 VM 当前的说话状态，把新出现的句子记下来。
	 *
	 * <p>VM 未就绪或没有工程时直接跳过，不清空已有气泡——重载工程的瞬间不该把话抹掉。</p>
	 */
	private void pollSpeech(long now) {
		RhinoVm vm = RhinoVm.get();
		if (!vm.isReady()) {
			return;
		}
		String json = vm.speechJson();
		if (json == null || json.isEmpty()) {
			return;
		}
		try {
			JsonArray array = JsonParser.parseString(json).getAsJsonArray();
			for (JsonElement element : array) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject item = element.getAsJsonObject();
				boolean think = item.has("think") && item.get("think").getAsBoolean();
				// usageId 每次说话都会变，比比对文本可靠：同一句话连说两次也能识别。
				// 是否该显示、是否已经播完，交给簿记判断（DEF-06 的复活就在那里拦住）
				bubbles.poll(string(item, "name"), string(item, "text"), think, string(item, "id"), now);
			}
		} catch (RuntimeException e) {
			// 解析失败不该影响渲染，最坏情况只是这一帧没有新气泡
		}
	}

	private void expire(long now) {
		bubbles.expire(now);
	}

	private static String string(JsonObject object, String key) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
	}
}
