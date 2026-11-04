package xyz.hegke.client.script.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import xyz.hegke.client.script.ScriptEngine;
import xyz.hegke.client.script.action.ListFunctions;
import xyz.hegke.client.script.runtime.GlobalVariables;
import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.runtime.ScriptInstance;
import xyz.hegke.client.script.runtime.Values;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 运行状态面板：实时展示活动脚本实例的程序计数器与局部变量。
 *
 * <p>数据在每帧渲染时直接读取调度器，因此不需要刷新按钮。</p>
 */
public class RuntimePanelScreen extends Screen {
	private static final int MAX_ROWS = 8;

	private final Screen parent;

	public RuntimePanelScreen(Screen parent) {
		super(Text.translatable("screen.hk-script.runtime"));
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
				Text.translatable(running ? "screen.hk-script.runtime_on" : "screen.hk-script.runtime_off"),
				width / 2, 38, running ? 0xFF55FF55 : 0xFFFF5555);

		List<ScriptInstance> instances = ScriptEngine.get().instances();
		int left = width / 2 - 200;
		int y = 58;

		if (instances.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("screen.hk-script.runtime_empty"), width / 2, 88, 0xFF808080);
			return;
		}

		int shown = 0;
		for (ScriptInstance instance : instances) {
			if (shown++ >= MAX_ROWS) {
				break;
			}
			String header = instance.script().name()
					+ "  [" + stateLabel(instance) + "]"
					+ "  " + instance.pc() + "/" + instance.script().size();
			context.drawTextWithShadow(textRenderer, header, left, y, 0xFFFFFFFF);
			y += 11;

			// 当前积木：把 pc 翻译成编辑器里能看到的那一行，否则只有数字无从定位
			BlockNode current = instance.script().blockAt(Math.max(0, instance.pc() - 1));
			if (current != null) {
				String summary = "▶ " + NodeChain.summary(current, instance.script().scopeId());
				context.drawTextWithShadow(textRenderer,
						Text.literal(textRenderer.trimToWidth(summary, width - left - 24)),
						left + 12, y, 0xFFFFC24D);
				y += 11;
			}

			Map<String, Object> locals = instance.locals();
			String variables = locals.entrySet().stream()
					.limit(4)
					.map(entry -> entry.getKey() + "=" + Values.asString(entry.getValue()))
					.collect(Collectors.joining("  "));
			if (!variables.isEmpty()) {
				context.drawTextWithShadow(textRenderer, variables, left + 12, y, 0xFFA0A0A0);
				y += 11;
			}
			if (instance.state() == ScriptInstance.State.ERROR) {
				context.drawTextWithShadow(textRenderer,
						Text.literal(instance.error()), left + 12, y, 0xFFFF5555);
				y += 11;
			}
			y += 6;
		}

		Map<String, Object> globals = GlobalVariables.snapshot();
		if (!globals.isEmpty()) {
			String text = globals.entrySet().stream()
					.limit(8)
					.map(entry -> entry.getKey() + "=" + Values.asString(entry.getValue()))
					.collect(Collectors.joining("  "));
			context.drawTextWithShadow(textRenderer, "[global] " + text, left, y + 6, 0xFFFFFF80);
			y += 12;
		}

		Map<String, Integer> lists = ListFunctions.sizes();
		if (!lists.isEmpty()) {
			String text = lists.entrySet().stream()
					.limit(8)
					.map(entry -> entry.getKey() + ":" + entry.getValue())
					.collect(Collectors.joining("  "));
			context.drawTextWithShadow(textRenderer, "[list] " + text, left, y + 6, 0xFF80E0E0);
		}
	}

	private String stateLabel(ScriptInstance instance) {
		return switch (instance.state()) {
			case RUNNING -> "run";
			case WAITING -> "wait";
			case FINISHED -> "done";
			case ERROR -> "error";
		};
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
