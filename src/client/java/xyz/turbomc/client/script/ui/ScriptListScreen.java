package xyz.turbomc.client.script.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import xyz.turbomc.client.script.ScriptEngine;
import xyz.turbomc.client.script.sb3.Sb3Importer;
import xyz.turbomc.client.script.store.PackStore;
import xyz.turbomc.client.script.store.ScriptDefinition;
import xyz.turbomc.client.script.store.ScriptPack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 脚本包列表。
 *
 * <p>脚本在 TurboWarp 里编写、导出成 sb3，这里只负责：导入、启用哪一个包、重命名、删除，
 * 以及展开查看包里的脚本树。</p>
 *
 * <p>开关是互斥的——同时只运行一个工程。这样才能把 sb3 原样交给 VM，
 * 不必合并多个工程的角色与舞台变量（那会引入静默的变量错乱）。</p>
 *
 * <p><b>列表按行滚动</b>：包与脚本行都是 20px 高，行数一旦超过可视区，早期版本会让行画到
 * 底部按钮上并变得无法点击。这里改为「首行索引 + 逐行重排」，只显示完整落在可视区内的行——
 * 不用裁剪区（{@code enableScissor}）包住 {@code super.render}，避免连带裁掉背景与 tooltip。</p>
 */
public class ScriptListScreen extends Screen {
	private static final int ROW_HEIGHT = 20;
	/** 列表内容顶（标题与提示之下）。 */
	private static final int LIST_TOP = 46;
	/** 底部按钮区高度：按钮 20 + 下边距 10。 */
	private static final int BOTTOM_BAR = 30;
	/** 信息区高度：状态 / 同步消息 / 失败详情 / 操作提示 四行。 */
	private static final int INFO_AREA = 70;

	private final Screen parent;
	/** 展开的包（文件名）。 */
	private final Set<String> expanded = new HashSet<>();
	/** 列表内容行：每行的控件，y 由 {@link #layoutRows()} 统一算。 */
	private final List<Row> rows = new ArrayList<>();
	/** 第一行可见行的索引。按行滚动，不会出现半行。 */
	private int scrollIndex;
	/** 选中的包，重命名/删除作用在它身上。 */
	private String selected;
	/** 界面级提示（例如打开文件夹失败），优先于 PackStore 的同步消息。 */
	private Text localMessage = Text.empty();

	public ScriptListScreen(Screen parent) {
		super(Text.translatable("screen.turbomc.packs"));
		this.parent = parent;
	}

	public ScriptListScreen(Screen parent, String selectAfterOpen) {
		this(parent);
		this.selected = selectAfterOpen;
	}

	/** 一行列表内容：行高固定 {@link #ROW_HEIGHT}。 */
	private record Row(List<ClickableWidget> widgets) {
	}

	@Override
	protected void init() {
		PackStore store = PackStore.get();
		store.ensureDirectories();
		List<ScriptPack> packs = store.packs();
		int left = left();

		// 每次开界面算一次差异。sb3 只有 KB 量级，换来「哪个包有新版本」一眼可见。
		Map<String, Boolean> pending = new HashMap<>();
		for (ScriptPack pack : packs) {
			pending.put(pack.fileName, store.pendingUpdate(pack) != null);
		}

		rows.clear();
		for (ScriptPack pack : packs) {
			rows.add(addPackRow(store, pack, left, pending.getOrDefault(pack.fileName, false)));

			if (expanded.contains(pack.fileName)) {
				Sb3Importer.Result result = store.scripts(pack);
				if (!result.ok()) {
					// 坏包也占一行：否则「展开」点了没反应，看起来像按钮坏了
					rows.add(addBrokenRow(left, result.error()));
					continue;
				}
				for (ScriptDefinition definition : result.scripts()) {
					rows.add(addScriptRow(definition, left));
				}
			}
		}

		scrollIndex = clampScroll(scrollIndex);

		int bottom = height - BOTTOM_BAR;
		int buttonWidth = 66;
		int gap = 4;
		int bx = left;

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.import"),
				button -> doImport(store)
		).dimensions(bx, bottom, buttonWidth, ROW_HEIGHT).build());
		bx += buttonWidth + gap;

		ButtonWidget folder = ButtonWidget.builder(
				Text.translatable("screen.turbomc.open_folder"),
				button -> openImportsFolder()
		).dimensions(bx, bottom, buttonWidth, ROW_HEIGHT).build();
		folder.setTooltip(Tooltip.of(Text.literal(store.importsDir().toString())));
		addDrawableChild(folder);
		bx += buttonWidth + gap;

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.rename"),
				button -> renameSelected()
		).dimensions(bx, bottom, buttonWidth, ROW_HEIGHT).build());
		bx += buttonWidth + gap;

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.delete"),
				button -> confirmDeleteSelected()
		).dimensions(bx, bottom, buttonWidth, ROW_HEIGHT).build());
		bx += buttonWidth + gap;

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.runtime"),
				button -> {
					if (client != null) {
						client.setScreen(new RuntimePanelScreen(this));
					}
				}
		).dimensions(bx, bottom, buttonWidth, ROW_HEIGHT).build());
		bx += buttonWidth + gap;

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.done"),
				button -> close()
		).dimensions(bx, bottom, buttonWidth, ROW_HEIGHT).build());

		layoutRows();
	}

	private int left() {
		// 6 个按钮 + 5 个间隙 = 416px
		return Math.max(4, width / 2 - 208);
	}

	private int listBottom() {
		return height - BOTTOM_BAR - INFO_AREA;
	}

	private int visibleRows() {
		return Math.max(1, (listBottom() - LIST_TOP) / ROW_HEIGHT);
	}

	private int maxScrollIndex() {
		return Math.max(0, rows.size() - visibleRows());
	}

	private int clampScroll(int value) {
		return Math.max(0, Math.min(value, maxScrollIndex()));
	}

	/**
	 * 按首行索引重排全部行控件。
	 *
	 * <p>整行可见才显示：半行会被列表边界切掉，而且 {@code setVisible(false)} 让不可见行
	 * 自动退出 Tab 焦点链，键盘导航不会跳到看不见的按钮上。</p>
	 */
	private void layoutRows() {
		int bottom = listBottom();
		int visible = visibleRows();
		for (int index = 0; index < rows.size(); index++) {
			int y = LIST_TOP + (index - scrollIndex) * ROW_HEIGHT;
			boolean shown = index >= scrollIndex && index < scrollIndex + visible && y + ROW_HEIGHT <= bottom;
			for (ClickableWidget widget : rows.get(index).widgets()) {
				widget.setY(y);
				// 1.21.11 的可见性是 public 字段，不是 setter
				widget.visible = shown;
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int max = maxScrollIndex();
		if (max <= 0) {
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		}
		int next = clampScroll(scrollIndex - (int) Math.signum(vertical));
		if (next != scrollIndex) {
			scrollIndex = next;
			layoutRows();
		}
		return true;
	}

	private ClickableWidget add(ClickableWidget widget) {
		addDrawableChild(widget);
		return widget;
	}

	private Row addPackRow(PackStore store, ScriptPack pack, int left, boolean hasUpdate) {
		List<ClickableWidget> widgets = new ArrayList<>(4);
		boolean isSelected = pack.fileName.equals(selected);
		int y = LIST_TOP;

		// 状态圆点：点击即切换启用（互斥，其余包自动关闭）
		widgets.add(add(ButtonWidget.builder(
				Text.literal(pack.enabled ? "\u25CF" : "\u25CB"),
				button -> {
					// 点已启用的包不做事：全部关掉等于让引擎空转，用户容易以为坏了。
					// 要停止运行请用 F6 总开关。
					if (!pack.enabled) {
						store.setActive(pack);
						ScriptEngine.get().restart();
						refresh();
					}
				}
		).dimensions(left, y, 20, ROW_HEIGHT).build()));

		ButtonWidget nameButton = ButtonWidget.builder(
				// ↑ 表示 imports 里有内容不同的同名文件，点「导入」即会同步
				Text.literal((isSelected ? "\u00BB " : "") + pack.displayName() + (hasUpdate ? " \u2191" : "")),
				button -> {
					selected = pack.fileName;
					refresh();
				}
		).dimensions(left + 22, y, 190, ROW_HEIGHT).build();
		if (hasUpdate) {
			nameButton.setTooltip(Tooltip.of(Text.translatable("screen.turbomc.update_pending")));
		}
		widgets.add(add(nameButton));

		Sb3Importer.Result result = store.scripts(pack);
		// 解析失败时直接把原因摆出来（截断到按钮宽度），悬停可看全文：
		// 只写「无法解析」等于把唯一有用的信息留在日志里
		String count = result.ok()
				? Text.translatable("screen.turbomc.script_count", result.scripts().size()).getString()
				: textRenderer.trimToWidth(result.error(), 96);
		// 数量是只读信息，用按钮只是因为与行对齐
		ButtonWidget countButton = ButtonWidget.builder(
				Text.literal(count),
				button -> {
					selected = pack.fileName;
					refresh();
				}
		).dimensions(left + 214, y, 100, ROW_HEIGHT).build();
		if (!result.ok()) {
			countButton.setTooltip(Tooltip.of(Text.literal(result.error())));
		}
		widgets.add(add(countButton));

		boolean open = expanded.contains(pack.fileName);
		widgets.add(add(ButtonWidget.builder(
				Text.translatable(open ? "screen.turbomc.collapse" : "screen.turbomc.expand"),
				button -> {
					if (!expanded.remove(pack.fileName)) {
						expanded.add(pack.fileName);
					}
					refresh();
				}
		).dimensions(left + 318, y, 76, ROW_HEIGHT).build()));

		return new Row(widgets);
	}

	/** 解析失败的包展开后的占位行：把原因带出来，而不是静默少一行。 */
	private Row addBrokenRow(int left, String error) {
		ButtonWidget line = ButtonWidget.builder(
				Text.literal("  \u2514 " + textRenderer.trimToWidth(error == null ? "" : error, 340)),
				button -> {
				}
		).dimensions(left + 22, LIST_TOP, 372, ROW_HEIGHT).build();
		line.setTooltip(Tooltip.of(Text.literal(error == null ? "" : error)));
		return new Row(List.of(add(line)));
	}

	private Row addScriptRow(ScriptDefinition definition, int left) {
		String trigger = definition.hat == null ? "" : NodeChain.label(definition.hat.type);
		ButtonWidget line = ButtonWidget.builder(
				Text.literal("  \u2514 " + definition.name + "   " + trigger),
				button -> {
					if (client != null) {
						client.setScreen(new ScriptEditScreen(this, definition));
					}
				}
		).dimensions(left + 22, LIST_TOP, 372, ROW_HEIGHT).build();
		return new Row(List.of(add(line)));
	}

	private ScriptPack selectedPack() {
		if (selected == null) {
			return null;
		}
		for (ScriptPack pack : PackStore.get().packs()) {
			if (pack.fileName.equals(selected)) {
				return pack;
			}
		}
		return null;
	}

	/** 导入/更新：新增与覆盖是同一个动作，具体结果（含失败原因）由 PackStore 汇总。 */
	private void doImport(PackStore store) {
		localMessage = Text.empty();
		boolean hadActive = store.activePack() != null;
		PackStore.SyncResult result = store.importAll();
		// 只有首次导入、还没有启用包时才接手第一个；否则保持玩家原有的选择
		if (!hadActive && result.added() > 0) {
			store.setActive(store.packs().stream().findFirst().orElse(null));
		}
		// 新增或覆盖都要求立刻生效：点这个按钮就是想看到新版本
		if (result.added() > 0 || result.activeUpdated()) {
			ScriptEngine.get().restart();
		}
		refresh();
	}

	/** 打开 imports 目录：玩家不用自己去 .minecraft 里翻路径。 */
	private void openImportsFolder() {
		PackStore store = PackStore.get();
		store.ensureDirectories();
		try {
			Util.getOperatingSystem().open(store.importsDir());
			localMessage = Text.empty();
		} catch (RuntimeException e) {
			// 无桌面环境/权限不足时不能静默：按钮点了没反应最难排查
			localMessage = Text.translatable("message.turbomc.pack.open_failed",
					e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
		}
	}

	private void renameSelected() {
		ScriptPack pack = selectedPack();
		if (pack == null || client == null) {
			return;
		}
		client.setScreen(new TextInputScreen(this,
				Text.translatable("screen.turbomc.rename"),
				pack.displayName(),
				name -> {
					PackStore.get().rename(pack, name);
					ScriptEngine.get().restart();
					if (client != null) {
						client.setScreen(new ScriptListScreen(parent));
					}
				}));
	}

	private void confirmDeleteSelected() {
		ScriptPack pack = selectedPack();
		if (pack == null || client == null) {
			return;
		}
		client.setScreen(new ConfirmScreen(
				confirmed -> {
					if (confirmed) {
						PackStore.get().delete(pack);
						ScriptEngine.get().restart();
					}
					if (client != null) {
						client.setScreen(new ScriptListScreen(parent));
					}
				},
				Text.translatable("screen.turbomc.delete.title"),
				Text.translatable("screen.turbomc.delete.message", pack.fileName)
		));
	}

	private void refresh() {
		clearAndInit();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
		int left = left();
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFFFF);

		if (PackStore.get().packs().isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("screen.turbomc.no_packs"), width / 2, 60, 0xFFA0A0A0);
		}

		drawScrollBar(context);

		// 状态行统一由引擎给出：运行中/未开/包坏了/跑崩了各有各的说法，
		// 不再用「运行中：xxx」+ 别处一句「已停用」拼出互相矛盾的信息
		ScriptEngine engine = ScriptEngine.get();
		context.drawTextWithShadow(textRenderer,
				Text.literal(textRenderer.trimToWidth(engine.hudLine(), width - left * 2)),
				left, height - 92, engine.statusColor());

		Text message = localMessage.getString().isEmpty() ? PackStore.get().lastMessage() : localMessage;
		if (!message.getString().isEmpty()) {
			// 有失败明细时消息转橙：绿字配一行红字原因会让人以为整体成功
			boolean failed = !PackStore.get().lastDetail().getString().isEmpty();
			context.drawCenteredTextWithShadow(textRenderer, message, width / 2, height - 80,
					failed ? 0xFFFFAA55 : 0xFF55FF55);
		}

		// 失败明细单独一行：一行放不下「新增 1 更新 0 失败 1」+ 具体是哪个文件为什么失败
		Text detail = PackStore.get().lastDetail();
		if (!detail.getString().isEmpty()) {
			context.drawTextWithShadow(textRenderer,
					Text.literal(textRenderer.trimToWidth(detail.getString(), width - left * 2)),
					left, height - 68, 0xFFFFAA55);
		}

		context.drawTextWithShadow(textRenderer,
				Text.translatable("screen.turbomc.pack_hint"),
				left, height - 52, 0xFF808080);
	}

	/** 行数超过可视区时给一条细滚动条：没有它，玩家看不出下面还有内容。 */
	private void drawScrollBar(DrawContext context) {
		int total = rows.size();
		int visible = visibleRows();
		if (total <= visible) {
			return;
		}
		int top = LIST_TOP;
		int bottom = listBottom();
		int track = bottom - top;
		int barHeight = Math.max(12, track * visible / total);
		int max = maxScrollIndex();
		int barY = top + (track - barHeight) * scrollIndex / Math.max(1, max);
		int x = width - 3;
		context.fill(x, top, x + 2, bottom, 0x40FFFFFF);
		context.fill(x, barY, x + 2, barY + barHeight, 0xC0FFFFFF);
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
