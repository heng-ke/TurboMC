package xyz.hegke.client.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Cloth Config 缺失时的降级提示界面，避免点击配置按钮后陷入空白屏。
 */
public class MissingClothConfigScreen extends Screen {
	private final Screen parent;

	public MissingClothConfigScreen(Screen parent) {
		super(Text.translatable("config.hk-script.missing_cloth_config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> this.close())
				.dimensions(this.width / 2 - 100, this.height / 2 + 24, 200, 20)
				.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title,
				this.width / 2, this.height / 2 - 40, 0xFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("config.hk-script.missing_cloth_config.body"),
				this.width / 2, this.height / 2 - 16, 0xA0A0A0);
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}
}
