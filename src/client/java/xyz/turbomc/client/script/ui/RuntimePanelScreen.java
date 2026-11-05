package xyz.turbomc.client.script.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import xyz.turbomc.client.script.ScriptEngine;
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

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
