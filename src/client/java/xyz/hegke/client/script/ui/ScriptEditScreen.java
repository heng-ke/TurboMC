package xyz.hegke.client.script.ui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import xyz.hegke.client.script.ScriptEngine;
import xyz.hegke.client.script.ir.CompiledScript;
import xyz.hegke.client.script.block.BlockRegistry;
import xyz.hegke.client.script.block.BlockShape;
import xyz.hegke.client.script.block.BlockType;
import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.store.FunctionDefinition;
import xyz.hegke.client.script.store.ScriptDefinition;
import xyz.hegke.client.script.store.ScriptStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 树形脚本编辑器。
 *
 * <p>整棵树一次性展开（C 形块内联缩进），不在层级之间来回跳转；行由本类直接绘制，
 * 选中与折叠只触发重绘、不重建界面，因此点击不会闪烁。</p>
 *
 * <p>快捷键：↑↓ 移动选中 · Enter 改参数 · Delete 删除 · ←→ 折叠/展开 · Esc 返回。</p>
 */
public class ScriptEditScreen extends Screen {
	private static final int LIST_TOP = 42;
	private static final int ROW_HEIGHT = 14;
	private static final int INDENT = 10;
	private static final int BOTTOM_BAR = 62;

	private final Screen parent;
	private final ScriptDefinition definition;
	private final FunctionDefinition function;

	private final List<Row> rows = new ArrayList<>();
	private final Set<BlockNode> collapsed = new HashSet<>();
	private int scroll;
	private int selected = -1;
	/** 是否有未应用的结构改动；关闭编辑器时统一重启一次引擎。 */
	private boolean dirty;
	/** 依赖「已选中积木」的按钮，未选中时置灰而不是静默无反应。 */
	private ButtonWidget editButton;
	private ButtonWidget removeButton;
	private ButtonWidget upButton;
	private ButtonWidget downButton;

	public ScriptEditScreen(Screen parent, ScriptDefinition definition) {
		this(parent, definition, null);
	}

	public ScriptEditScreen(Screen parent, ScriptDefinition definition, FunctionDefinition function) {
		super(Text.literal(function == null ? definition.name : definition.name + " / " + function.name));
		this.parent = parent;
		this.definition = definition;
		this.function = function;
	}

	@Override
	protected void init() {
		buildTree();
		scrollToRunning();

		int left = width / 2 - 210;
		int row1 = height - 54;

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.add"),
				button -> openPicker()
		).dimensions(left, row1, 62, 18).build());

		editButton = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.edit"),
				button -> openParams()
		).dimensions(left + 66, row1, 54, 18).build());

		removeButton = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.remove"),
				button -> deleteSelected()
		).dimensions(left + 124, row1, 54, 18).build());

		upButton = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.up"),
				button -> moveBlock(-1)
		).dimensions(left + 182, row1, 48, 18).build());

		downButton = addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.down"),
				button -> moveBlock(1)
		).dimensions(left + 234, row1, 48, 18).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.trigger"),
				button -> openTriggerPicker()
		).dimensions(left + 286, row1, 54, 18).build());

		int row2 = height - 30;
		if (function == null) {
			addDrawableChild(ButtonWidget.builder(
					Text.translatable("screen.hk-script.functions"),
					button -> {
						if (client != null) {
							client.setScreen(new FunctionListScreen(this, definition));
						}
					}
			).dimensions(left, row2, 62, 18).build());
		}

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.export"),
				button -> ScriptStore.get().exportTo(definition)
		).dimensions(left + 124, row2, 54, 18).build());

		// 等待时长的显示单位：只影响显示与输入框，脚本里始终存 tick
		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.hk-script.wait_unit",
						Text.translatable(WaitUnit.inSeconds()
								? "unit.hk-script.second" : "unit.hk-script.tick")),
				button -> {
					WaitUnit.toggle();
					clearAndInit();
				}
		).dimensions(left + 182, row2, 90, 18).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.done"),
				button -> close()
		).dimensions(left + 340, row2, 80, 18).build());
	}

	/**
	 * 按选中状态与位置刷新按钮可用性。
	 *
	 * <p>以前这些按钮在无选中时只是 {@code return}，按下去毫无反馈，
	 * 用户会以为"按钮坏了"。置灰能直接说明原因。</p>
	 */
	private void updateButtonStates() {
		Row row = current();
		// 占位行只用于「往空分支里加积木」，没有可编辑/删除的实体
		boolean hasSelection = row != null && row.node != null;
		int index = hasSelection ? row.sequence.indexOf(row.node) : -1;

		if (editButton != null) {
			editButton.active = hasSelection;
		}
		if (removeButton != null) {
			removeButton.active = hasSelection;
		}
		if (upButton != null) {
			upButton.active = hasSelection && index > 0;
		}
		if (downButton != null) {
			downButton.active = hasSelection && index >= 0 && index < row.sequence.size() - 1;
		}
	}

	// ---- 树构建 ----

	private void buildTree() {
		rows.clear();
		collect(rootSequence(), null, null, 0);
	}

	private List<BlockNode> rootSequence() {
		return new ArrayList<>(NodeChain.toList(function == null ? definition.hat.next : function.body));
	}

	private void collect(List<BlockNode> sequence, BlockNode parentBlock, String branch, int depth) {
		for (BlockNode node : sequence) {
			rows.add(new Row(node, sequence, parentBlock, branch, depth, false));
			if (collapsed.contains(node)) {
				continue;
			}
			BlockNode first = node.branchOrNull("SUBSTACK");
			if (first != null) {
				collect(new ArrayList<>(NodeChain.toList(first)), node, "SUBSTACK", depth + 1);
			} else if (canHoldBranch(node)) {
				// 空的 C 形积木必须留一行占位，否则界面上没有任何入口能往它里面放积木
				rows.add(Row.placeholder(node, "SUBSTACK", depth + 1));
			}
			BlockNode second = node.branchOrNull("SUBSTACK2");
			if (second != null) {
				rows.add(Row.virtual(depth + 1));
				collect(new ArrayList<>(NodeChain.toList(second)), node, "SUBSTACK2", depth + 1);
			}
		}
	}

	private void refreshTree(BlockNode keepSelected) {
		buildTree();
		selected = -1;
		if (keepSelected != null) {
			for (int index = 0; index < rows.size(); index++) {
				if (rows.get(index).node == keepSelected) {
					selected = index;
					break;
				}
			}
		}
		clampScroll();
	}

	private void clampScroll() {
		int max = Math.max(0, rows.size() - visibleRows());
		scroll = Math.max(0, Math.min(scroll, max));
	}

	/**
	 * 打开编辑器时把运行中的行滚入视野——调试时最想先看到的是「脚本现在停在哪」。
	 *
	 * <p>只在运行行不在当前视野内时滚动，并且留出上方三分之一的上下文，
	 * 不把它顶到第一行。</p>
	 */
	private void scrollToRunning() {
		Set<BlockNode> running = ScriptEngine.get().runningBlocks(definition.name);
		if (running.isEmpty()) {
			return;
		}
		int visible = visibleRows();
		for (int index = 0; index < rows.size(); index++) {
			Row row = rows.get(index);
			if (row.virtual || !running.contains(row.node)) {
				continue;
			}
			if (index < scroll || index >= scroll + visible) {
				scroll = Math.max(0, index - visible / 3);
			}
			return;
		}
	}

	private int visibleRows() {
		return Math.max(1, (listBottom() - LIST_TOP) / ROW_HEIGHT);
	}

	private int listBottom() {
		return height - BOTTOM_BAR;
	}

	private static boolean hasChildren(BlockNode node) {
		return node != null
				&& (node.branchOrNull("SUBSTACK") != null || node.branchOrNull("SUBSTACK2") != null);
	}

	/**
	 * 该积木是否是能容纳子积木的 C 形块——用它决定空分支要不要留占位行。
	 */
	private static boolean canHoldBranch(BlockNode node) {
		BlockType type = BlockRegistry.get(node.type);
		return type != null && type.shape() == BlockShape.C_SHAPE;
	}

	// ---- 渲染 ----

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		updateButtonStates();
		super.render(context, mouseX, mouseY, deltaTicks);

		context.fill(0, LIST_TOP - 4, width, listBottom(), 0xF0141414);

		Set<BlockNode> running = ScriptEngine.get().runningBlocks(definition.name);
		boolean runningVisible = false;
		int end = Math.min(rows.size(), scroll + visibleRows());
		for (int index = scroll; index < end; index++) {
			Row row = rows.get(index);
			int y = LIST_TOP + (index - scroll) * ROW_HEIGHT;

			if (row.virtual) {
				context.drawTextWithShadow(textRenderer,
						Text.translatable("screen.hk-script.else"),
						12 + row.depth * INDENT, y + 2, 0xFF909090);
				continue;
			}

			if (row.node == null) {
				// 空分支占位行：选中它再点「添加」，新积木会落进这一层
				if (index == selected) {
					context.fill(0, y - 1, width, y + ROW_HEIGHT - 1, 0x40FFFFFF);
				}
				context.drawTextWithShadow(textRenderer,
						Text.translatable("screen.hk-script.empty_branch"),
						8 + row.depth * INDENT, y + 2, 0xFF707070);
				continue;
			}

			if (index == selected) {
				context.fill(0, y - 1, width, y + ROW_HEIGHT - 1, 0x40FFFFFF);
			}
			if (running.contains(row.node)) {
				// 正在执行：淡金背景 + 左侧竖条，与「选中」的白底可叠加辨认
				runningVisible = true;
				context.fill(0, y - 1, width, y + ROW_HEIGHT - 1, 0x30FFC24D);
				context.fill(2, y - 1, 5, y + ROW_HEIGHT - 1, 0xFFFFC24D);
			}

			int indentX = 8 + row.depth * INDENT;
			if (hasChildren(row.node)) {
				boolean isCollapsed = collapsed.contains(row.node);
				context.drawTextWithShadow(textRenderer, Text.literal(isCollapsed ? "+" : "-"),
						indentX, y + 2, 0xFFC0C0C0);
			}

			int color = ParamLabels.blockColor(row.node.type);
			context.fill(indentX + 8, y + 2, indentX + 10, y + ROW_HEIGHT - 4, color);
			// 内联展开可能超出屏幕宽度，按可用宽度裁剪并补省略号
			String summary = NodeChain.summary(row.node, definition.scopeId);
			int available = Math.max(40, width - indentX - 24);
			context.drawTextWithShadow(textRenderer,
					Text.literal(textRenderer.trimToWidth(summary, available)),
					indentX + 14, y + 2, 0xFFFFFFFF);
		}

		if (!running.isEmpty()) {
			// 三种状态分开说：正常定位、看不到（折叠/滚出）、改动尚未生效
			String key = dirty ? "screen.hk-script.running_stale"
					: runningVisible ? "screen.hk-script.running"
					: "screen.hk-script.running_hidden";
			context.drawTextWithShadow(textRenderer, Text.translatable(key), 6, 14, 0xFFFFC24D);
		}
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("screen.hk-script.tree_hint"), width / 2, 26, 0xFF909090);
		drawSelectedRange(context);

		if (rows.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("screen.hk-script.empty"), width / 2, LIST_TOP + 24, 0xFF808080);
		}
	}

	/**
	 * 右上角显示选中积木编译成的指令区间。
	 *
	 * <p>运行面板与报错日志说的是 pc，这里给出 pc ↔ 积木的对应关系，两边才能对上。</p>
	 */
	private void drawSelectedRange(DrawContext context) {
		Row row = current();
		if (row == null || row.node == null) {
			return;
		}
		CompiledScript compiled = ScriptEngine.get().compiledOrNull(definition);
		if (compiled == null) {
			return;
		}
		int[] range = compiled.rangeOf(row.node);
		if (range == null) {
			return;
		}
		String text = "指令 " + range[0] + "-" + range[1];
		context.drawTextWithShadow(textRenderer, Text.literal(text),
				width - textRenderer.getWidth(text) - 8, 26, 0xFFA0A0A0);
	}

	// ---- 交互 ----

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true;
		}
		if (click.button() != 0 || click.y() < LIST_TOP || click.y() > listBottom()) {
			return false;
		}
		int index = scroll + (int) ((click.y() - LIST_TOP) / ROW_HEIGHT);
		if (index < 0 || index >= rows.size()) {
			return false;
		}
		Row row = rows.get(index);
		if (row.virtual) {
			return true;
		}

		int markerX = 8 + row.depth * INDENT;
		if (hasChildren(row.node) && click.x() >= markerX - 2 && click.x() <= markerX + 10) {
			toggleCollapse(row.node);
			return true;
		}

		selected = index;
		if (doubled) {
			openParams();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int max = Math.max(0, rows.size() - visibleRows());
		scroll = (int) Math.max(0, Math.min(max, scroll - vertical * 3));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int key = input.key();
		if (key == GLFW.GLFW_KEY_UP) {
			moveSelection(-1);
			return true;
		}
		if (key == GLFW.GLFW_KEY_DOWN) {
			moveSelection(1);
			return true;
		}
		if (key == GLFW.GLFW_KEY_LEFT) {
			collapseSelected();
			return true;
		}
		if (key == GLFW.GLFW_KEY_RIGHT) {
			expandSelected();
			return true;
		}
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			openParams();
			return true;
		}
		if (key == GLFW.GLFW_KEY_DELETE) {
			deleteSelected();
			return true;
		}
		return super.keyPressed(input);
	}

	private void moveSelection(int delta) {
		if (rows.isEmpty()) {
			return;
		}
		int index = selected < 0 ? (delta > 0 ? -1 : rows.size()) : selected;
		int next = index + delta;
		while (next >= 0 && next < rows.size() && rows.get(next).virtual) {
			next += delta;
		}
		if (next < 0 || next >= rows.size()) {
			return;
		}
		selected = next;
		ensureVisible();
	}

	private void ensureVisible() {
		if (selected < scroll) {
			scroll = selected;
		} else if (selected >= scroll + visibleRows()) {
			scroll = selected - visibleRows() + 1;
		}
		clampScroll();
	}

	private void toggleCollapse(BlockNode node) {
		if (!collapsed.remove(node)) {
			collapsed.add(node);
		}
		refreshTree(node);
	}

	private void collapseSelected() {
		Row row = current();
		if (row != null && hasChildren(row.node) && !collapsed.contains(row.node)) {
			collapsed.add(row.node);
			refreshTree(row.node);
		}
	}

	private void expandSelected() {
		Row row = current();
		if (row != null && collapsed.remove(row.node)) {
			refreshTree(row.node);
		}
	}

	private Row current() {
		return selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
	}

	// ---- 编辑操作 ----

	private void moveBlock(int delta) {
		Row row = current();
		if (row == null) {
			return;
		}
		int index = row.sequence.indexOf(row.node);
		int target = index + delta;
		if (index < 0 || target < 0 || target >= row.sequence.size()) {
			return;
		}
		row.sequence.set(index, row.sequence.get(target));
		row.sequence.set(target, row.node);
		commit(row);
		refreshTree(row.node);
	}

	private void deleteSelected() {
		Row row = current();
		if (row == null || row.node == null) {
			return;
		}
		row.sequence.remove(row.node);
		commit(row);
		refreshTree(null);
	}

	private void commit(Row row) {
		BlockNode head = NodeChain.relink(row.sequence);
		if (row.parentBlock == null) {
			if (function == null) {
				definition.hat.next = head;
			} else {
				function.body = head;
			}
		} else if (head == null) {
			row.parentBlock.branches.remove(row.branch);
		} else {
			row.parentBlock.branches.put(row.branch, head);
		}
		markDirty();
	}

	private void openPicker() {
		if (client == null) {
			return;
		}
		client.setScreen(new BlockPickerScreen(this, BlockPickerScreen::isStatement, node -> {
			Row row = current();
			List<BlockNode> sequence = row == null ? rootSequence() : row.sequence;
			int insertAt = row == null ? sequence.size() : sequence.indexOf(row.node) + 1;
			sequence.add(insertAt, node);

			BlockNode head = NodeChain.relink(sequence);
			if (row == null || row.parentBlock == null) {
				if (function == null) {
					definition.hat.next = head;
				} else {
					function.body = head;
				}
			} else {
				row.parentBlock.branches.put(row.branch, head);
			}
			ScriptStore.get().save(definition);
			ScriptEngine.get().restart();

			client.setScreen(this);
		}));
	}

	private void openParams() {
		Row row = current();
		if (client == null || row == null || row.node == null) {
			return;
		}
		// 参数改动同样只落盘不重启，统一在关闭编辑器时生效（与 U2 的取舍一致）
		client.setScreen(new ParamEditScreen(this, row.node, this::markDirty,
				definition.scopeId, ""));
	}

	private void openTriggerPicker() {
		if (client == null || function != null) {
			return;
		}
		client.setScreen(new BlockPickerScreen(this, type -> type.shape() == BlockShape.HAT, hat -> {
			hat.next = definition.hat.next;
			definition.hat = hat;
			ScriptStore.get().save(definition);
			ScriptEngine.get().restart();
			client.setScreen(this);
		}));
	}

	/**
	 * 只落盘并标记改动，不重启引擎。
	 *
	 * <p>以前每改一个参数就 {@code restart()}：清空全局变量、重置计时器、打断所有正在跑的任务。
	 * 现在改成关闭编辑器时统一重启一次。</p>
	 */
	private void markDirty() {
		ScriptStore.get().save(definition);
		dirty = true;
	}

	@Override
	public void close() {
		if (dirty) {
			dirty = false;
			ScriptEngine.get().restart();
		}
		if (client != null) {
			client.setScreen(parent);
		}
	}

	/**
	 * 一行：一个积木节点 + 它所属的可变序列 + 父块与分支名。
	 */
	private static final class Row {
		final BlockNode node;
		final List<BlockNode> sequence;
		final BlockNode parentBlock;
		final String branch;
		final int depth;
		final boolean virtual;

		Row(BlockNode node, List<BlockNode> sequence, BlockNode parentBlock, String branch,
			int depth, boolean virtual) {
			this.node = node;
			this.sequence = sequence;
			this.parentBlock = parentBlock;
			this.branch = branch;
			this.depth = depth;
			this.virtual = virtual;
		}

		static Row virtual(int depth) {
			return new Row(null, List.of(), null, null, depth, true);
		}

		/**
		 * 空分支占位行：绑定父块与分支名，选中后点「添加」即可把积木放进该分支。
		 * 序列必须是可变的，添加时会被直接写入。
		 */
		static Row placeholder(BlockNode parent, String branch, int depth) {
			return new Row(null, new ArrayList<>(), parent, branch, depth, false);
		}
	}
}
