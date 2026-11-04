package xyz.hegke.client.script.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import xyz.hegke.client.script.block.BlockParam;
import xyz.hegke.client.script.block.BlockRegistry;
import xyz.hegke.client.script.block.BlockShape;
import xyz.hegke.client.script.block.BlockType;
import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.node.Slot;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 积木选择面板：按分类列出可插入的积木。
 *
 * <p>支持按外形过滤——编辑语句序列时列出全部非事件积木，填充表达式槽时只列返回值积木。</p>
 */
public class BlockPickerScreen extends Screen {
	private static final int PAGE_SIZE = 12;
	private static final int COLUMNS = 2;

	private final Screen parent;
	private final Predicate<BlockType> filter;
	private final Consumer<BlockNode> consumer;
	private int page;
	private List<BlockType> types = List.of();

	public BlockPickerScreen(Screen parent, Consumer<BlockNode> consumer) {
		this(parent, type -> true, consumer);
	}

	public BlockPickerScreen(Screen parent, Predicate<BlockType> filter, Consumer<BlockNode> consumer) {
		super(Text.translatable("screen.hk-script.block_title"));
		this.parent = parent;
		this.filter = filter;
		this.consumer = consumer;
	}

	/**
	 * 仅返回值的积木，用于填充表达式槽。
	 */
	public static boolean isExpression(BlockType type) {
		return type.shape() == BlockShape.REPORTER || type.shape() == BlockShape.BOOLEAN;
	}

	/**
	 * 可放入语句序列的积木（排除事件头与表达式）。
	 */
	public static boolean isStatement(BlockType type) {
		// control.nop 是导入时给未支持积木用的占位符，不该出现在选择器里
		if ("control.nop".equals(type.id())) {
			return false;
		}
		return type.shape() != BlockShape.HAT && !isExpression(type);
	}

	@Override
	protected void init() {
		types = BlockRegistry.all().stream()
				.filter(filter)
				.sorted(Comparator.comparing(BlockType::category).thenComparing(BlockType::id))
				.toList();

		int totalPages = Math.max(1, (types.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, totalPages - 1));

		int left = width / 2 - 205;
		int startY = 46;
		int from = page * PAGE_SIZE;
		int to = Math.min(types.size(), from + PAGE_SIZE);

		for (int index = from; index < to; index++) {
			BlockType type = types.get(index);
			int slot = index - from;
			int column = slot % COLUMNS;
			int row = slot / COLUMNS;
			int x = left + column * 210;
			int y = startY + row * 26;
			addDrawableChild(ButtonWidget.builder(
					Text.literal(NodeChain.label(type.id())),
					button -> {
						if (consumer != null) {
							consumer.accept(createNode(type));
						}
					}
			).dimensions(x, y, 200, 20).build());
		}

		int bottom = height - 32;
		if (page > 0) {
			addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> {
				page--;
				refresh();
			}).dimensions(left, bottom, 30, 20).build());
		}
		if (page + 1 < totalPages) {
			addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> {
				page++;
				refresh();
			}).dimensions(left + 40, bottom, 30, 20).build());
		}
		addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.cancel"),
				button -> close()
		).dimensions(left + 310, bottom, 100, 20).build());
	}

	/**
	 * 新建积木时按规格填充默认参数，保证 JSON 中参数完整、不依赖编译期兜底。
	 */
	private static BlockNode createNode(BlockType type) {
		BlockNode node = new BlockNode(type.id());
		for (BlockParam param : type.params()) {
			if (param.field()) {
				node.fields.put(param.name(), param.defaultValue());
			} else {
				node.inputs.put(param.name(), Slot.literal(param.defaultValue()));
			}
		}
		return node;
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
				Text.literal((page + 1) + " / " + Math.max(1, (types.size() + PAGE_SIZE - 1) / PAGE_SIZE)),
				width / 2, height - 44, 0xFFA0A0A0);
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
