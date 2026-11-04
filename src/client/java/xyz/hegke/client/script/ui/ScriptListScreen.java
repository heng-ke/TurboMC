package xyz.hegke.client.script.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import xyz.hegke.client.script.ScriptEngine;
import xyz.hegke.client.script.store.ScriptDefinition;
import xyz.hegke.client.script.store.ScriptStore;

import java.util.List;

/**
 * 脚本列表：新建、启用/停用、删除、进入编辑。
 */
public class ScriptListScreen extends Screen {
	private static final int PAGE_SIZE = 8;
	private static final int ROW_HEIGHT = 26;

	private final Screen parent;
	private int page;
	private int totalPages = 1;

	public ScriptListScreen(Screen parent) {
		super(Text.translatable("screen.hk-script.scripts"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		List<ScriptDefinition> scripts = ScriptStore.get().sorted();
		totalPages = Math.max(1, (scripts.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, totalPages - 1));

		int left = width / 2 - 210;
		int startY = 48;
		int from = page * PAGE_SIZE;
		int to = Math.min(scripts.size(), from + PAGE_SIZE);

		for (int index = from; index < to; index++) {
			ScriptDefinition definition = scripts.get(index);
			int rowY = startY + (index - from) * ROW_HEIGHT;
			String marker = definition.enabled ? "\u25CF " : "\u25CB ";

			addDrawableChild(ButtonWidget.builder(
					Text.literal(marker + definition.name),
					button -> {
						if (client != null) {
							client.setScreen(new ScriptEditScreen(this, definition));
						}
					}
			).dimensions(left, rowY, 200, 20).build());

			addDrawableChild(ButtonWidget.builder(
					Text.translatable(definition.enabled ? "screen.hk-script.on" : "screen.hk-script.off"),
					button -> {
						definition.enabled = !definition.enabled;
						ScriptStore.get().save(definition);
						ScriptEngine.get().restart();
						refresh();
					}
			).dimensions(left + 204, rowY, 40, 20).build());

			addDrawableChild(ButtonWidget.builder(
					Text.translatable("screen.hk-script.duplicate"),
					button -> {
						ScriptStore.get().duplicate(definition);
						ScriptEngine.get().restart();
						refresh();
					}
			).dimensions(left + 248, rowY, 40, 20).build());

			addDrawableChild(ButtonWidget.builder(
					Text.translatable("screen.hk-script.rename"),
					button -> {
						if (client != null) {
							client.setScreen(new TextInputScreen(this,
									Text.translatable("screen.hk-script.rename"),
									definition.name,
									name -> {
										ScriptStore.get().rename(definition, name);
										ScriptEngine.get().restart();
										if (client != null) {
											client.setScreen(new ScriptListScreen(parent));
										}
									}));
						}
					}
			).dimensions(left + 292, rowY, 52, 20).build());

			addDrawableChild(ButtonWidget.builder(
					Text.translatable("screen.hk-script.delete"),
					button -> confirmDelete(definition)
			).dimensions(left + 348, rowY, 46, 20).build());
		}

		int bottom = height - 32;
		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.new"),
				button -> {
					ScriptDefinition created = ScriptStore.get().create("script");
					if (client != null) {
						client.setScreen(new ScriptEditScreen(this, created));
					}
				}
		).dimensions(left, bottom, 70, 20).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.import"),
				button -> {
					ScriptStore.get().importAll();
					ScriptEngine.get().restart();
					refresh();
				}
		).dimensions(left + 74, bottom, 60, 20).build());

		if (page > 0) {
			addDrawableChild(ButtonWidget.builder(
					Text.literal("<"),
					button -> {
						page--;
						refresh();
					}
			).dimensions(left + 140, bottom, 28, 20).build());
		}
		if (page + 1 < totalPages) {
			addDrawableChild(ButtonWidget.builder(
					Text.literal(">"),
					button -> {
						page++;
						refresh();
					}
			).dimensions(left + 172, bottom, 28, 20).build());
		}

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.runtime"),
				button -> {
					if (client != null) {
						client.setScreen(new RuntimePanelScreen(this));
					}
				}
		).dimensions(left + 230, bottom, 90, 20).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.done"),
				button -> close()
		).dimensions(left + 330, bottom, 90, 20).build());
	}

	private void confirmDelete(ScriptDefinition definition) {
		if (client == null) {
			return;
		}
		client.setScreen(new ConfirmScreen(
				confirmed -> {
					if (confirmed) {
						ScriptStore.get().delete(definition);
						ScriptEngine.get().restart();
					}
					if (client != null) {
						client.setScreen(new ScriptListScreen(parent));
					}
				},
				Text.translatable("screen.hk-script.delete.title"),
				Text.translatable("screen.hk-script.delete.message", definition.name)
		));
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
				Text.literal((page + 1) + " / " + totalPages), width / 2, height - 46, 0xFFA0A0A0);

		String message = ScriptStore.get().lastMessage();
		if (!message.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.literal(message),
					width / 2, height - 62, 0xFF55FF55);
		}
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
