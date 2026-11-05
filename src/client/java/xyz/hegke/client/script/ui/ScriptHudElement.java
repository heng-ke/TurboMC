package xyz.hegke.client.script.ui;

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
import xyz.hegke.client.config.HkScriptConfig;
import xyz.hegke.client.config.HudCorner;
import xyz.hegke.client.script.ScriptEngine;
import xyz.hegke.client.script.vm.RhinoVm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
 */
public final class ScriptHudElement implements HudElement {
	/** 一句气泡停留时长。 */
	private static final long HOLD_MS = 5000L;
	/** 同时最多显示几句。 */
	private static final int MAX_BUBBLES = 3;
	/** 单行最大像素宽度，超出按宽度截断。 */
	private static final int MAX_WIDTH = 200;
	private static final int LINE_HEIGHT = 10;
	private static final int PADDING = 3;
	private static final int MARGIN = 4;

	/** 角色名 → 当前气泡。scratch-vm 里气泡是按 target 存的，同一角色只会有一条。 */
	private final Map<String, Bubble> bubbles = new LinkedHashMap<>();

	private record Bubble(String name, String text, boolean think, String usageId, long updatedAt) {
	}

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
		HkScriptConfig config = HkScriptConfig.get();
		if (!config.masterEnabled) {
			bubbles.clear();
			return;
		}

		long now = System.currentTimeMillis();
		pollSpeech(now);
		expire(now);

		boolean running = ScriptEngine.get().isRunning();
		String packName = ScriptEngine.get().activeName();

		TextRenderer textRenderer = client.textRenderer;
		List<Row> rows = buildRows(textRenderer, running, packName);
		if (rows.isEmpty()) {
			return;
		}
		draw(context, textRenderer, config.hudCorner, rows);
	}

	/** 渲染单位：一行文字 + 颜色，背景按最宽的一行铺。 */
	private record Row(Text text, int color) {
	}

	private List<Row> buildRows(TextRenderer textRenderer, boolean running, String packName) {
		List<Row> rows = new ArrayList<>();
		if (running) {
			String name = packName.isEmpty() ? Text.translatable("hud.hk-script.no_pack").getString() : packName;
			rows.add(new Row(Text.literal("\u25CF " + name), 0xFF55FF55));
		} else {
			rows.add(new Row(Text.translatable("hud.hk-script.disabled"), 0xFF9A9A9A));
		}

		// 按时间正序：旧消息在上，新消息贴着状态行往下排
		List<Bubble> visible = bubbles.values().stream()
				.sorted(Comparator.comparingLong(Bubble::updatedAt))
				.toList();
		List<Bubble> tail = visible.size() <= MAX_BUBBLES
				? visible
				: visible.subList(visible.size() - MAX_BUBBLES, visible.size());
		for (Bubble bubble : tail) {
			String line = bubble.name() + "：" + bubble.text();
			String clipped = textRenderer.trimToWidth(line, MAX_WIDTH);
			rows.add(new Row(Text.literal(clipped), bubble.think() ? 0xFFB0B0B0 : 0xFFFFFFFF));
		}
		return rows;
	}

	private void draw(DrawContext context, TextRenderer textRenderer, HudCorner corner, List<Row> rows) {
		int widest = 0;
		for (Row row : rows) {
			widest = Math.max(widest, textRenderer.getWidth(row.text()));
		}
		int boxWidth = widest + PADDING * 2;
		int boxHeight = rows.size() * LINE_HEIGHT + PADDING * 2;

		boolean right = corner == HudCorner.TOP_RIGHT || corner == HudCorner.BOTTOM_RIGHT;
		boolean bottom = corner == HudCorner.BOTTOM_LEFT || corner == HudCorner.BOTTOM_RIGHT;

		int x = right ? context.getScaledWindowWidth() - MARGIN - boxWidth : MARGIN;
		int y = bottom ? context.getScaledWindowHeight() - MARGIN - boxHeight : MARGIN;

		// 半透明底衬：白字直接压在高对比度场景上会看不清
		context.fill(x, y, x + boxWidth, y + boxHeight, 0x90000000);

		int textY = y + PADDING;
		for (Row row : rows) {
			context.drawTextWithShadow(textRenderer, row.text(), x + PADDING, textY, row.color());
			textY += LINE_HEIGHT;
		}
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
				String name = string(item, "name");
				String text = string(item, "text");
				String id = string(item, "id");
				if (name.isEmpty() || text.isEmpty()) {
					continue;
				}
				Bubble existing = bubbles.get(name);
				// usageId 每次说话都会变，比比对文本可靠：同一句话连说两次也能识别
				if (existing == null || !existing.usageId().equals(id)) {
					boolean think = item.has("think") && item.get("think").getAsBoolean();
					bubbles.put(name, new Bubble(name, text, think, id, now));
				}
			}
		} catch (RuntimeException e) {
			// 解析失败不该影响渲染，最坏情况只是这一帧没有新气泡
		}
	}

	private void expire(long now) {
		bubbles.values().removeIf(bubble -> now - bubble.updatedAt() > HOLD_MS);
	}

	private static String string(JsonObject object, String key) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
	}
}
