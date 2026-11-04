package xyz.hegke.client.script.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import xyz.hegke.client.script.block.BlockParam;
import xyz.hegke.client.script.block.BlockRegistry;
import xyz.hegke.client.script.block.BlockType;
import xyz.hegke.client.script.block.ParamKind;
import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.node.Slot;
import xyz.hegke.client.script.runtime.Values;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积木参数编辑：按 {@link BlockParam} 规格生成对应控件。
 *
 * <p>非字段参数同时支持两种形态：字面量（由控件直接编辑）与嵌套表达式积木。
 * 嵌套块显示为可读摘要（如「列表 的第 (我的变量) 项」），点它进入下一层继续查看与编辑，
 * 右侧 {@code ×} 才是清除——把「查看」和「删除」拆成两个按钮，避免误点一次就丢掉整棵子树。</p>
 */
public class ParamEditScreen extends Screen {
	private static final int MAX_ROWS = 10;
	private static final int ROW_HEIGHT = 26;

	private final Screen parent;
	private final BlockNode node;
	private final Runnable onSaved;
	/** 角色作用域，用于摘要里的变量名裁剪。 */
	private final String scopeId;
	/** 面包屑：从这个积木逐层进入的路径，如「说 › TEXT」。 */
	private final String path;

	private final List<ParamRow> rows = new ArrayList<>();
	private final Map<String, TextFieldWidget> textWidgets = new LinkedHashMap<>();

	public ParamEditScreen(Screen parent, BlockNode node, Runnable onSaved) {
		this(parent, node, onSaved, "", "");
	}

	public ParamEditScreen(Screen parent, BlockNode node, Runnable onSaved, String scopeId, String path) {
		super(path == null || path.isBlank()
				? Text.translatable("screen.hk-script.param_title")
				: Text.literal(path));
		this.parent = parent;
		this.node = node;
		this.onSaved = onSaved;
		this.scopeId = scopeId == null ? "" : scopeId;
		this.path = path == null ? "" : path;
	}

	@Override
	protected void init() {
		rows.clear();
		textWidgets.clear();
		collectRows();

		int left = width / 2 - 165;
		int top = 46;
		int count = Math.min(rows.size(), MAX_ROWS);

		for (int index = 0; index < count; index++) {
			ParamRow row = rows.get(index);
			int y = top + index * ROW_HEIGHT;
			int controlX = left + 95;
			String current = currentValue(row);

			if (current == null) {
				Slot slot = node.inputs.get(row.name());
				BlockNode inner = slot == null ? null : slot.block;
				if (inner == null) {
					addDrawableChild(ButtonWidget.builder(
							Text.literal("[" + ParamLabels.label(row.name()) + "]"),
							button -> {
								node.inputs.put(row.name(), Slot.literal(row.defaultValue()));
								persistAndRebuild();
							}
					).dimensions(controlX, y, 150, 18).build());
					continue;
				}

				// 按钮文字是这块嵌套积木的完整摘要，点它进入下一层——「查看」不再等于「删除」
				String summary = NodeChain.summary(inner, scopeId);
				String shown = textRenderer.trimToWidth(summary, 142);
				addDrawableChild(ButtonWidget.builder(
						Text.literal(shown),
						button -> {
							if (client != null) {
								client.setScreen(new ParamEditScreen(this, inner, onSaved, scopeId,
										childPath(row.name(), inner)));
							}
						}
				).dimensions(controlX, y, 150, 18).build());

				addDrawableChild(ButtonWidget.builder(
						Text.literal("×"),
						button -> {
							node.inputs.put(row.name(), Slot.literal(row.defaultValue()));
							persistAndRebuild();
						}
				).dimensions(controlX + 155, y, 20, 18).build());
				continue;
			}

			switch (row.kind()) {
				case BOOLEAN -> addDrawableChild(ButtonWidget.builder(
						Text.literal(ParamLabels.value(row.name(), current)),
						button -> {
							writeValue(row, Values.asBool(current) ? "false" : "true");
							persistAndRebuild();
						}
				).dimensions(controlX, y, 150, 18).build());

				case ENUM -> addDrawableChild(ButtonWidget.builder(
						Text.literal(ParamLabels.value(row.name(), current)),
						button -> {
							writeValue(row, nextOption(row, current));
							persistAndRebuild();
						}
				).dimensions(controlX, y, 150, 18).build());

				default -> {
					TextFieldWidget widget = new TextFieldWidget(textRenderer, controlX, y, 150, 18,
							Text.literal(row.name()));
					widget.setMaxLength(256);
					// 时间类参数在输入框里是「秒」，写回时再换算成 tick
					widget.setText(WaitUnit.isWaitParam(node.type, row.name())
							? WaitUnit.input(current)
							: current);
					if (row.kind() == ParamKind.NUMBER) {
						widget.setTextPredicate(text -> text.isEmpty() || text.matches("-?\\d*\\.?\\d*"));
					}
					textWidgets.put(row.name(), widget);
					addDrawableChild(widget);
				}
			}

			// 可接表达式的字段（acceptsReporter）也要能切换到积木
			if (!row.field() || row.acceptsReporter()) {
				addDrawableChild(ButtonWidget.builder(
						Text.literal("fx"),
						button -> openExpressionPicker(row)
				).dimensions(controlX + 155, y, 60, 18).build());
			}
		}

		int bottom = height - 32;
		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.confirm"),
				button -> {
					applyTextWidgets();
					if (onSaved != null) {
						onSaved.run();
					}
					close();
				}
		).dimensions(left + 140, bottom, 90, 20).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.cancel"),
				button -> close()
		).dimensions(left + 240, bottom, 90, 20).build());
	}

	private void collectRows() {
		BlockType type = BlockRegistry.get(node.type);
		List<BlockParam> specs = type != null ? type.params() : List.of();

		for (BlockParam spec : specs) {
			rows.add(new ParamRow(spec.name(), spec.field(), spec.acceptsReporter(),
					spec.kind(), spec.options(), spec.defaultValue()));
		}
		// 兼容旧脚本：节点中存在但规格未声明的参数，按文本处理
		for (String key : node.fields.keySet()) {
			if (findRow(key) == null) {
				rows.add(new ParamRow(key, true, false, ParamKind.TEXT, List.of(), ""));
			}
		}
		for (Map.Entry<String, Slot> entry : node.inputs.entrySet()) {
			if (findRow(entry.getKey()) == null) {
				rows.add(new ParamRow(entry.getKey(), false, false, ParamKind.TEXT, List.of(), ""));
			}
		}
	}

	private ParamRow findRow(String name) {
		for (ParamRow row : rows) {
			if (row.name().equals(name)) {
				return row;
			}
		}
		return null;
	}

	/**
	 * @return 当前字面量；嵌套表达式返回 {@code null}
	 */
	private String currentValue(ParamRow row) {
		if (row.field()) {
			return node.fields.getOrDefault(row.name(), row.defaultValue());
		}
		Slot slot = node.inputs.get(row.name());
		if (slot == null) {
			return row.defaultValue();
		}
		return slot.hasBlock() ? null : slot.literal;
	}

	private void writeValue(ParamRow row, String value) {
		if (row.field()) {
			node.fields.put(row.name(), value);
		} else {
			node.inputs.put(row.name(), Slot.literal(value));
		}
	}

	private String nextOption(ParamRow row, String current) {
		List<String> options = row.options();
		if (options.isEmpty()) {
			return current;
		}
		int index = options.indexOf(current);
		return options.get((index + 1) % options.size());
	}

	private void applyTextWidgets() {
		for (ParamRow row : rows) {
			TextFieldWidget widget = textWidgets.get(row.name());
			if (widget == null) {
				continue;
			}
			if (WaitUnit.isWaitParam(node.type, row.name())) {
				// 解析失败就保留原值：绝不把「秒」当成 tick 写回，那会让 1 秒变 1 tick
				String ticks = WaitUnit.parseTicks(widget.getText());
				if (ticks != null) {
					writeValue(row, ticks);
				}
				continue;
			}
			writeValue(row, widget.getText());
		}
	}

	private void openExpressionPicker(ParamRow row) {
		if (client == null) {
			return;
		}
		client.setScreen(new BlockPickerScreen(this, BlockPickerScreen::isExpression, picked -> {
			node.inputs.put(row.name(), Slot.of(picked));
			if (onSaved != null) {
				onSaved.run();
			}
			if (client != null) {
				client.setScreen(new ParamEditScreen(parent, node, onSaved, scopeId, path));
			}
		}));
	}

	private void persistAndRebuild() {
		if (onSaved != null) {
			onSaved.run();
		}
		if (client != null) {
			client.setScreen(new ParamEditScreen(parent, node, onSaved, scopeId, path));
		}
	}

	/**
	 * 下一层的标题：沿用上一层的路径再补「参数名 › 积木名」。
	 */
	private String childPath(String paramName, BlockNode child) {
		String step = ParamLabels.label(paramName) + " › " + NodeChain.label(child.type);
		String combined = path.isBlank() ? step : path + " › " + NodeChain.label(child.type);
		return textRenderer.trimToWidth(combined, Math.max(60, width - 20));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 22, 0xFFFFFFFF);
		// 副标题放整块积木的当前摘要，进来就能读出「这一层到底在算什么」
		String summary = NodeChain.summary(node, scopeId);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal(textRenderer.trimToWidth(summary, Math.max(60, width - 20))),
				width / 2, 36, 0xFFA0A0A0);

		int left = width / 2 - 165;
		int top = 46;
		int count = Math.min(rows.size(), MAX_ROWS);
		for (int index = 0; index < count; index++) {
			ParamRow row = rows.get(index);
			context.drawTextWithShadow(textRenderer, ParamLabels.label(row.name()), left, top + index * ROW_HEIGHT + 5, 0xFFE0E0E0);
		}
		if (rows.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("screen.hk-script.no_params"), width / 2, 96, 0xFF808080);
		} else if (rows.size() > MAX_ROWS) {
			context.drawTextWithShadow(textRenderer, Text.literal("..."),
					left, top + MAX_ROWS * ROW_HEIGHT, 0xFF808080);
		}
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	private record ParamRow(String name, boolean field, boolean acceptsReporter,
						   ParamKind kind, List<String> options, String defaultValue) {
	}
}
