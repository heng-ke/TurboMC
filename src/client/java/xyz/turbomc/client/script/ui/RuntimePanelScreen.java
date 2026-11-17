package xyz.turbomc.client.script.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import xyz.turbomc.client.script.ScriptEngine;
import xyz.turbomc.client.script.ai.AiService;
import xyz.turbomc.client.script.vm.RhinoVm;
import xyz.turbomc.client.script.vm.ScriptBridge;

/**
 * 运行状态面板：展示 VM 的线程数、脚本数与变量快照。
 *
 * <p>执行内核换成 scratch-vm 之后，不再有「实例 / 程序计数器」这类概念——
 * VM 内部以字节码线程运行，所以这里只呈现可观测的聚合状态。数据每帧现读，无需刷新。</p>
 */
public class RuntimePanelScreen extends Screen {
	private static final int MAX_VARS = 8;

	private final Screen parent;

	public RuntimePanelScreen(Screen parent) {
		super(Text.translatable("screen.turbomc.runtime"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.done"),
				button -> close()
		).dimensions(width / 2 - 50, height - 32, 100, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFFFF);

		boolean running = ScriptEngine.get().isRunning();
		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable(running ? "screen.turbomc.runtime_on" : "screen.turbomc.runtime_off"),
				width / 2, 38, running ? 0xFF55FF55 : 0xFFFF5555);

		int left = width / 2 - 200;
		int y = 62;

		RhinoVm vm = RhinoVm.get();
		boolean ready = vm.isReady();
		context.drawTextWithShadow(textRenderer,
				"runtime: " + (ready ? "rhino + scratch-vm" : "not loaded"), left, y, 0xFFA0A0A0);
		y += 13;

		context.drawTextWithShadow(textRenderer,
				"pack=" + (ScriptEngine.get().activeName().isEmpty() ? "none" : ScriptEngine.get().activeName())
						+ "  scripts=" + ScriptEngine.get().scriptCount()
						+ "  threads=" + (ready ? vm.threadCount() : 0)
						+ "  bridge=" + ScriptBridge.callCount()
						+ "  unmapped=" + ScriptBridge.missCount(),
				left, y, 0xFFFFFFFF);
		y += 16;

		y = drawAiSection(context, left, y);

		String dump = ready ? vm.variableDump() : "";
		if (dump == null || dump.isEmpty()) {
			context.drawTextWithShadow(textRenderer,
					Text.translatable("screen.turbomc.runtime_empty"), left, y, 0xFF808080);
			return;
		}
		context.drawTextWithShadow(textRenderer, "[variables]", left, y, 0xFFFFFF80);
		y += 12;
		String[] parts = dump.split(";");
		int shown = 0;
		for (String part : parts) {
			if (shown++ >= MAX_VARS) {
				context.drawTextWithShadow(textRenderer,
						"... " + (parts.length - MAX_VARS) + " more", left + 8, y, 0xFF808080);
				break;
			}
			context.drawTextWithShadow(textRenderer,
					textRenderer.trimToWidth(part, width - left - 24), left + 8, y, 0xFFA0A0A0);
			y += 11;
		}
	}

	/**
	 * AI 会话明细。
	 *
	 * <p>HUD 只够显示一行结论，这里补上「谁在等、等了多久、回的是什么、错在哪」。
	 * 数据来自 {@link AiService#snapshot()}，每帧现读，无需刷新按钮。</p>
	 */
	private int drawAiSection(DrawContext context, int left, int y) {
		context.drawTextWithShadow(textRenderer, "[ai]", left, y, 0xFFFFFF80);
		y += 12;

		AiService.Snapshot snapshot = AiService.get().snapshot();
		if (!snapshot.configured()) {
			context.drawTextWithShadow(textRenderer,
					textRenderer.trimToWidth(
							"未配置：config/turbomc.json 的 ai.baseUrl 与 ai.model", width - left - 16),
					left + 8, y, 0xFFFF5555);
			return y + 13;
		}

		context.drawTextWithShadow(textRenderer,
				textRenderer.trimToWidth("model=" + snapshot.model()
						+ "  在途 " + snapshot.inFlight() + "/" + snapshot.maxConcurrent()
						+ "  会话 " + snapshot.sessions().size(), width - left - 16),
				left + 8, y, 0xFFA0A0A0);
		y += 12;

		if (snapshot.sessions().isEmpty()) {
			context.drawTextWithShadow(textRenderer, "还没有请求", left + 8, y, 0xFF808080);
			return y + 13;
		}

		for (AiService.SessionStatus status : snapshot.sessions()) {
			int color = switch (status.phase()) {
				case WAITING -> 0xFFFFD54F;
				case OK -> 0xFF55FF55;
				case FAILED -> 0xFFFF5555;
				case IDLE -> 0xFF808080;
			};
			context.drawTextWithShadow(textRenderer,
					textRenderer.trimToWidth(status.name() + "  " + describe(status), width - left - 24),
					left + 8, y, color);
			y += 11;
		}
		return y + 4;
	}

	private static String describe(AiService.SessionStatus status) {
		return switch (status.phase()) {
			case WAITING -> "等待 " + seconds(status.pendingMs()) + "s";
			case OK -> "成功 " + seconds(status.costMs()) + "s"
					+ "  历史 " + status.historyTurns() + " 轮"
					+ (status.reply().isEmpty() ? "" : "  「" + status.reply() + "」");
			case FAILED -> "失败 " + status.error();
			case IDLE -> "空闲";
		};
	}

	private static String seconds(long millis) {
		return String.format("%.1f", millis / 1000.0);
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
