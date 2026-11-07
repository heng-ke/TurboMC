package xyz.turbomc.client.script.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import xyz.turbomc.client.script.vm.RhinoVm;

import java.util.function.Consumer;

/**
 * 「询问 [问题] 并等待」的输入窗口。
 *
 * <p>Scratch 在浏览器里把提问框画在舞台上，游戏内没有那一层，改成弹一个输入界面。</p>
 *
 * <p><b>确认与取消都要给出回答</b>：取消视为空字符串。这不是随手定的——VM 的询问
 * 线程会一直挂在 {@code PROMISE_WAIT}，少一次回答它就永远醒不过来。界面被外部
 * 关闭（退出世界、切屏）时同样补一次，见 {@link #removed()}。</p>
 *
 * <p>不暂停游戏：多人模式下暂停没有意义，且脚本本来就该继续跑。</p>
 */
public final class AskScreen extends Screen {
	private final Consumer<String> onAnswer;
	private TextFieldWidget field;
	private boolean answered;

	public AskScreen(String question, Consumer<String> onAnswer) {
		super(Text.literal(titleFor(question)));
		this.onAnswer = onAnswer;
	}

	/**
	 * 标题取问题原文；为空时改读角色气泡。
	 *
	 * <p>角色可见时，VM 只把问题通过 {@code SAY} 事件画在气泡上，发给宿主的是空串。
	 * 而弹窗期间 HUD 不绘制，气泡看不到——不补这一步，玩家只会看到一个没有题目的输入框。</p>
	 */
	private static String titleFor(String question) {
		if (question != null && !question.isBlank()) {
			return question;
		}
		String bubble = latestBubble();
		return bubble.isEmpty() ? Text.translatable("screen.turbomc.ask").getString() : bubble;
	}

	/** 取最后一条气泡文本；VM 未就绪或没有气泡时返回空串。 */
	private static String latestBubble() {
		try {
			JsonElement parsed = JsonParser.parseString(RhinoVm.get().speechJson());
			if (!parsed.isJsonArray()) {
				return "";
			}
			JsonArray array = parsed.getAsJsonArray();
			if (array.isEmpty()) {
				return "";
			}
			JsonObject last = array.get(array.size() - 1).getAsJsonObject();
			JsonElement text = last.get("text");
			return text == null || text.isJsonNull() ? "" : text.getAsString();
		} catch (RuntimeException e) {
			return "";
		}
	}

	@Override
	protected void init() {
		int left = width / 2 - 150;
		field = new TextFieldWidget(textRenderer, left, 70, 300, 20,
				Text.translatable("screen.turbomc.answer"));
		field.setMaxLength(256);
		addDrawableChild(field);
		setInitialFocus(field);

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.confirm"),
				button -> submit(field.getText())
		).dimensions(left + 55, 102, 90, 20).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.cancel"),
				button -> close()
		).dimensions(left + 155, 102, 90, 20).build());
	}

	/** 只回调一次：重复提交会让 VM 收到两次 ANSWER。 */
	private void submit(String value) {
		if (answered) {
			return;
		}
		answered = true;
		if (onAnswer != null) {
			onAnswer.accept(value == null ? "" : value);
		}
		if (client != null) {
			client.setScreen(null);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
		// 问题可能很长，按宽度截成一行，避免顶出屏幕
		String line = textRenderer.trimToWidth(title.getString(), Math.max(60, width - 40));
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(line), width / 2, 40, 0xFFFFFFFF);
	}

	@Override
	public void close() {
		submit("");
	}

	@Override
	public void removed() {
		// 被外部替换（退出世界等）时的兜底，防止询问线程永久挂起
		if (!answered) {
			submit("");
		}
		super.removed();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
