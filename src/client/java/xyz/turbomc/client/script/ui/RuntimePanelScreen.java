package xyz.turbomc.client.script.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import xyz.turbomc.client.script.ScriptEngine;
import xyz.turbomc.client.script.ai.AiService;
import xyz.turbomc.client.script.vm.RhinoVm;
import xyz.turbomc.client.script.vm.ScriptBridge;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行状态面板：引擎状态、VM 聚合数据、AI 会话与变量快照。
 *
 * <p>执行内核换成 scratch-vm 之后，不再有「实例 / 程序计数器」这类概念——
 * VM 内部以字节码线程运行，所以这里只呈现可观测的聚合状态。数据每帧现读，无需刷新。</p>
 *
 * <p><b>按行滚动</b>：内容长度取决于变量与 AI 会话数量，早期版本固定从 62px 往下画，
 * 会话一多就溢出到屏幕外（且没有滚动条提示下面还有东西）。现在所有内容先构建成行，
 * 只绘制完整落在可视区内的行。</p>
 *
 * <p>底部提供「重新载入」：工程载入失败或脚本跑崩后 {@code healthy} 会一直是 false，
 * 以前只能重启客户端或按两次 F6，现在这里一键复位。</p>
 */
public class RuntimePanelScreen extends Screen {
	private static final int LINE_HEIGHT = 11;
	private static final int LIST_TOP = 44;
	private static final int BOTTOM_BAR = 36;
	private static final int INDENT = 8;

	private final Screen parent;
	/** 第一行可见行的索引。 */
	private int scroll;

	public RuntimePanelScreen(Screen parent) {
		super(Text.translatable("screen.turbomc.runtime"));
		this.parent = parent;
	}

	/** 一行内容：已本地化的文本 + 颜色 + 缩进。 */
	private record Line(String text, int color, int indent) {
		static Line blank() {
			return new Line("", 0, 0);
		}
	}

	@Override
	protected void init() {
		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.runtime.reload"),
				button -> reload()
		).dimensions(width / 2 - 104, height - 32, 100, 20).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.done"),
				button -> close()
		).dimensions(width / 2 + 4, height - 32, 100, 20).build());
	}

	/** 复位故障标记并重载当前包：下一次 tick 会重新走 start()。 */
	private void reload() {
		ScriptEngine.get().restart();
		scroll = 0;
	}

	private int visibleLines() {
		return Math.max(1, (height - BOTTOM_BAR - LIST_TOP) / LINE_HEIGHT);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int max = Math.max(0, buildLines().size() - visibleLines());
		if (max <= 0) {
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		}
		int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(vertical) * 3));
		if (next != scroll) {
			scroll = next;
		}
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFFFF);

		List<Line> lines = buildLines();
		int left = Math.max(8, width / 2 - 200);
		int visible = visibleLines();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));

		int end = Math.min(lines.size(), scroll + visible);
		for (int index = scroll; index < end; index++) {
			Line line = lines.get(index);
			if (line.text().isEmpty()) {
				continue;
			}
			int y = LIST_TOP + (index - scroll) * LINE_HEIGHT;
			String text = textRenderer.trimToWidth(line.text(), width - left - line.indent() - 16);
			context.drawTextWithShadow(textRenderer, Text.literal(text), left + line.indent(), y, line.color());
		}

		drawScrollBar(context, lines.size(), visible);
	}

	/** 行数超过可视区时给一条细滚动条。 */
	private void drawScrollBar(DrawContext context, int total, int visible) {
		if (total <= visible) {
			return;
		}
		int top = LIST_TOP;
		int bottom = height - BOTTOM_BAR;
		int track = bottom - top;
		int barHeight = Math.max(12, track * visible / total);
		int max = Math.max(1, total - visible);
		int barY = top + (track - barHeight) * scroll / max;
		int x = width - 3;
		context.fill(x, top, x + 2, bottom, 0x40FFFFFF);
		context.fill(x, barY, x + 2, barY + barHeight, 0xC0FFFFFF);
	}

	// ---- 内容构建 ----

	private List<Line> buildLines() {
		List<Line> lines = new ArrayList<>();

		ScriptEngine engine = ScriptEngine.get();
		lines.add(new Line(engine.statusText().getString(), engine.statusColor(), 0));
		if (!engine.statusDetail().getString().isEmpty()) {
			lines.add(new Line(engine.statusDetail().getString(), engine.statusColor(), INDENT));
		}
		lines.add(Line.blank());

		RhinoVm vm = RhinoVm.get();
		boolean ready = vm.isReady();
		lines.add(new Line(Text.translatable("screen.turbomc.runtime.vm",
				Text.translatable(ready ? "screen.turbomc.runtime.vm_ready"
						: "screen.turbomc.runtime.vm_absent").getString()).getString(),
				0xFFA0A0A0, 0));
		lines.add(new Line(Text.translatable("screen.turbomc.runtime.counters",
				engine.activeName().isEmpty() ? "-" : engine.activeName(),
				engine.scriptCount(),
				ready ? vm.threadCount() : 0,
				ScriptBridge.callCount(),
				ScriptBridge.missCount()).getString(),
				0xFFFFFFFF, 0));
		lines.add(Line.blank());

		addAiLines(lines);
		addVariableLines(lines, vm, ready);
		return lines;
	}

	private void addAiLines(List<Line> lines) {
		lines.add(new Line(Text.translatable("screen.turbomc.runtime.ai").getString(), 0xFFFFFF80, 0));

		AiService.Snapshot snapshot = AiService.get().snapshot();
		if (!snapshot.configured()) {
			lines.add(new Line(Text.translatable("screen.turbomc.runtime.ai_unconfigured").getString(),
					0xFFFF5555, INDENT));
			return;
		}

		lines.add(new Line(Text.translatable("screen.turbomc.runtime.ai_model",
				snapshot.model(), snapshot.inFlight(), snapshot.maxConcurrent(), snapshot.sessions().size()).getString(),
				0xFFA0A0A0, INDENT));

		if (snapshot.sessions().isEmpty()) {
			lines.add(new Line(Text.translatable("screen.turbomc.runtime.ai_none").getString(), 0xFF808080, INDENT));
			return;
		}

		for (AiService.SessionStatus status : snapshot.sessions()) {
			int color = switch (status.phase()) {
				case WAITING -> 0xFFFFD54F;
				case OK -> 0xFF55FF55;
				case FAILED -> 0xFFFF5555;
				case IDLE -> 0xFF808080;
			};
			lines.add(new Line(status.name() + "  " + describe(status), color, INDENT));
		}
	}

	private static String describe(AiService.SessionStatus status) {
		return switch (status.phase()) {
			case WAITING -> Text.translatable("screen.turbomc.runtime.ai_waiting", seconds(status.pendingMs()))
					.getString();
			case OK -> Text.translatable("screen.turbomc.runtime.ai_ok",
					seconds(status.costMs()), status.historyTurns()).getString()
					+ (status.reply().isEmpty() ? "" : "  \u300C" + status.reply() + "\u300D");
			case FAILED -> Text.translatable("screen.turbomc.runtime.ai_failed", status.error()).getString();
			case IDLE -> Text.translatable("screen.turbomc.runtime.ai_idle").getString();
		};
	}

	private static String seconds(long millis) {
		return String.format("%.1f", millis / 1000.0);
	}

	private void addVariableLines(List<Line> lines, RhinoVm vm, boolean ready) {
		String dump = ready ? vm.variableDump() : "";
		if (dump == null || dump.isEmpty()) {
			return;
		}
		lines.add(Line.blank());
		lines.add(new Line(Text.translatable("screen.turbomc.runtime.variables").getString(), 0xFFFFFF80, 0));
		// 不再截断到 8 条：面板能滚动，看全比「… 还有 N 条」有用
		for (String part : dump.split(";")) {
			if (!part.isBlank()) {
				lines.add(new Line(part, 0xFFA0A0A0, INDENT));
			}
		}
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
