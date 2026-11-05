package xyz.turbomc.client.script.ui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import xyz.turbomc.client.script.node.BlockNode;
import xyz.turbomc.client.script.store.ScriptDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 脚本树查看器（只读）。
 *
 * <p>脚本的编写与修改由 TurboWarp 完成，游戏内只保留「看一眼」的能力，因此这里没有任何
 * 写操作：不能添加、删除、移动或修改积木。保留的操作只有选中、滚动、折叠与返回。</p>
 *
 * <p>整棵树一次性展开（C 形块内联缩进），行由本类直接绘制，折叠只触发重绘、不重建界面。</p>
 */
public class ScriptEditScreen extends Screen {
	private static final int LIST_TOP = 42;
	private static final int ROW_HEIGHT = 14;
	private static final int INDENT = 10;
	private static final int BOTTOM_BAR = 34;

	private final Screen parent;
	private final ScriptDefinition definition;

	private final List<Row> rows = new ArrayList<>();
	private final Set<BlockNode> collapsed = new HashSet<>();
	private int scroll;
	private int selected = -1;

	public ScriptEditScreen(Screen parent, ScriptDefinition definition) {
		super(Text.literal(definition.name));
		this.parent = parent;
		this.definition = definition;
	}

	@Override
	protected void init() {
		buildTree();

		int left = width / 2 - 210;
		int row = height - 26;

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.expand_all"),
				button -> {
					collapsed.clear();
					refreshTree();
				}
		).dimensions(left, row, 70, 18).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.collapse_all"),
				button -> {
					for (Row item : rows) {
						if (item.node != null && hasChildren(item.node)) {
							collapsed.add(item.node);
						}
					}
					refreshTree();
				}
		).dimensions(left + 76, row, 70, 18).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.back"),
				button -> close()
		).dimensions(left + 360, row, 60, 18).build());
	}

	// ---- 树构建 ----

	private void buildTree() {
		rows.clear();
		collect(definition.hat, 0);
	}

	private void collect(BlockNode head, int depth) {
		BlockNode current = head;
		while (current != null) {
			rows.add(new Row(current, depth, false));
			if (!collapsed.contains(current)) {
				BlockNode first = current.branchOrNull("SUBSTACK");
				if (first != null) {
					collect(first, depth + 1);
				}
				BlockNode second = current.branchOrNull("SUBSTACK2");
				if (second != null) {
					// 「否则」分隔行，让两段分支在视觉上分开
					rows.add(new Row(null, depth + 1, true));
					collect(second, depth + 1);
				}
			}
			current = current.next;
		}
	}

	private void refreshTree() {
		BlockNode keep = selected >= 0 && selected < rows.size() ? rows.get(selected).node : null;
		buildTree();
		selected = -1;
		if (keep != null) {
			for (int index = 0; index < rows.size(); index++) {
				if (rows.get(index).node == keep) {
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

	// ---- 渲染 ----

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);

		context.fill(0, LIST_TOP - 4, width, listBottom(), 0xF0141414);

		int end = Math.min(rows.size(), scroll + visibleRows());
		for (int index = scroll; index < end; index++) {
			Row row = rows.get(index);
			int y = LIST_TOP + (index - scroll) * ROW_HEIGHT;

			if (row.virtual) {
				context.drawTextWithShadow(textRenderer,
						Text.translatable("screen.turbomc.else"),
						12 + row.depth * INDENT, y + 2, 0xFF909090);
				continue;
			}

			if (index == selected) {
				context.fill(0, y - 1, width, y + ROW_HEIGHT - 1, 0x40FFFFFF);
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

		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("screen.turbomc.view_hint"), width / 2, 26, 0xFF909090);

		if (rows.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("screen.turbomc.empty_view"), width / 2, LIST_TOP + 24, 0xFF808080);
		}
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
			if (!collapsed.remove(row.node)) {
				collapsed.add(row.node);
			}
			refreshTree();
			return true;
		}

		selected = index;
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
			Row row = current();
			if (row != null && hasChildren(row.node) && !collapsed.contains(row.node)) {
				collapsed.add(row.node);
				refreshTree();
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_RIGHT) {
			Row row = current();
			if (row != null && collapsed.remove(row.node)) {
				refreshTree();
			}
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
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
		if (selected < scroll) {
			scroll = selected;
		} else if (selected >= scroll + visibleRows()) {
			scroll = selected - visibleRows() + 1;
		}
		clampScroll();
	}

	private Row current() {
		return selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
	}

	/** 一行展示位；{@code node} 为 {@code null} 时是「否则」分隔行。 */
	private record Row(BlockNode node, int depth, boolean virtual) {
	}
}
