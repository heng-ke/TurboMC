package xyz.hegke.client.script.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * 通用单行文本输入屏（重命名、命名新脚本等）。
 */
public class TextInputScreen extends Screen {
	private final Screen parent;
	private final String initial;
	private final Consumer<String> onConfirm;
	private TextFieldWidget field;
	private boolean confirmed;

	public TextInputScreen(Screen parent, Text title, String initial, Consumer<String> onConfirm) {
		super(title);
		this.parent = parent;
		this.initial = initial;
		this.onConfirm = onConfirm;
	}

	@Override
	protected void init() {
		int left = width / 2 - 150;
		field = new TextFieldWidget(textRenderer, left, 60, 300, 20, Text.translatable("screen.hk-script.name"));
		field.setMaxLength(48);
		field.setText(initial == null ? "" : initial);
		addDrawableChild(field);
		setInitialFocus(field);

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.confirm"),
				button -> apply()
		).dimensions(left + 60, 92, 90, 20).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.cancel"),
				button -> close()
		).dimensions(left + 160, 92, 90, 20).build());
	}

	private void apply() {
		String value = field.getText().trim();
		if (value.isEmpty() || onConfirm == null) {
			close();
			return;
		}
		// 回调负责跳转到新界面，因此置位后 close 不再回退到 parent
		confirmed = true;
		onConfirm.accept(value);
		close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 32, 0xFFFFFFFF);
	}

	@Override
	public void close() {
		if (client != null && !confirmed) {
			client.setScreen(parent);
		}
	}
}
