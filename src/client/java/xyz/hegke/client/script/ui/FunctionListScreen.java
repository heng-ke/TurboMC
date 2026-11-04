package xyz.hegke.client.script.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import xyz.hegke.client.script.ScriptEngine;
import xyz.hegke.client.script.store.FunctionDefinition;
import xyz.hegke.client.script.store.ScriptDefinition;
import xyz.hegke.client.script.store.ScriptStore;

import java.util.List;

/**
 * 自定义积木（函数）列表：新建、删除、进入编辑函数体。
 *
 * <p>函数无返回值，参数槽固定为 {@code $p1} ~ {@code $p3}，在函数体内用「读取变量（局部）」访问。</p>
 */
public class FunctionListScreen extends Screen {
	private static final int PAGE_SIZE = 8;
	private static final int ROW_HEIGHT = 26;

	private final Screen parent;
	private final ScriptDefinition definition;
	private int page;
	private int totalPages = 1;

	public FunctionListScreen(Screen parent, ScriptDefinition definition) {
		super(Text.translatable("screen.hk-script.functions"));
		this.parent = parent;
		this.definition = definition;
	}

	@Override
	protected void init() {
		List<FunctionDefinition> functions = definition.functions;
		totalPages = Math.max(1, (functions.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, totalPages - 1));

		int left = width / 2 - 210;
		int startY = 48;
		int from = page * PAGE_SIZE;
		int to = Math.min(functions.size(), from + PAGE_SIZE);

		for (int index = from; index < to; index++) {
			FunctionDefinition function = functions.get(index);
			int rowY = startY + (index - from) * ROW_HEIGHT;

			addDrawableChild(ButtonWidget.builder(
					Text.literal(function.name),
					button -> {
						if (client != null) {
							client.setScreen(new ScriptEditScreen(this, definition, function));
						}
					}
			).dimensions(left, rowY, 300, 20).build());

			addDrawableChild(ButtonWidget.builder(
					Text.translatable("screen.hk-script.delete"),
					button -> {
						definition.functions.remove(function);
						persist();
						refresh();
					}
			).dimensions(left + 306, rowY, 62, 20).build());
		}

		int bottom = height - 32;
		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.new"),
				button -> {
					definition.functions.add(FunctionDefinition.create(nextName()));
					persist();
					refresh();
				}
		).dimensions(left, bottom, 90, 20).build());

		if (page > 0) {
			addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> {
				page--;
				refresh();
			}).dimensions(left + 200, bottom, 30, 20).build());
		}
		if (page + 1 < totalPages) {
			addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> {
				page++;
				refresh();
			}).dimensions(left + 260, bottom, 30, 20).build());
		}

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.done"),
				button -> close()
		).dimensions(left + 330, bottom, 90, 20).build());
	}

	private String nextName() {
		int index = definition.functions.size() + 1;
		while (hasName("fn" + index)) {
			index++;
		}
		return "fn" + index;
	}

	private boolean hasName(String name) {
		for (FunctionDefinition function : definition.functions) {
			if (function.name.equals(name)) {
				return true;
			}
		}
		return false;
	}

	private void persist() {
		ScriptStore.get().save(definition);
		ScriptEngine.get().restart();
	}

	/**
	 * 重建控件但保留本屏状态（页码、选中项）。
	 *
	 * <p>以前这里 {@code new} 一个新实例：页码随之归零，翻页按钮点了等于原地打转。</p>
	 */
	private void refresh() {
		clearAndInit();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("screen.hk-script.functions_hint"), width / 2, 36, 0xFFA0A0A0);

		if (definition.functions.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("screen.hk-script.functions_empty"), width / 2, 96, 0xFF808080);
		}
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
