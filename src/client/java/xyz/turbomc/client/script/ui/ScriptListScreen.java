package xyz.turbomc.client.script.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import xyz.turbomc.client.script.ScriptEngine;
import xyz.turbomc.client.script.sb3.Sb3Importer;
import xyz.turbomc.client.script.store.PackStore;
import xyz.turbomc.client.script.store.ScriptDefinition;
import xyz.turbomc.client.script.store.ScriptPack;

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
 */
public class ScriptListScreen extends Screen {
	private static final int ROW_HEIGHT = 20;

	private final Screen parent;
	/** 展开的包（文件名）。 */
	private final Set<String> expanded = new HashSet<>();
	/** 选中的包，重命名/删除作用在它身上。 */
	private String selected;

	public ScriptListScreen(Screen parent) {
		super(Text.translatable("screen.turbomc.packs"));
		this.parent = parent;
	}

	public ScriptListScreen(Screen parent, String selectAfterOpen) {
		this(parent);
		this.selected = selectAfterOpen;
	}

	@Override
	protected void init() {
		PackStore store = PackStore.get();
		store.ensureDirectories();
		List<ScriptPack> packs = store.packs();

		int left = width / 2 - 210;
		int y = 48;

		// 每次开界面算一次差异。sb3 只有 KB 量级，换来「哪个包有新版本」一眼可见。
		Map<String, Boolean> pending = new HashMap<>();
		for (ScriptPack pack : packs) {
			pending.put(pack.fileName, store.pendingUpdate(pack) != null);
		}

		for (ScriptPack pack : packs) {
			addPackRow(store, pack, left, y, pending.getOrDefault(pack.fileName, false));
			y += ROW_HEIGHT;

			if (expanded.contains(pack.fileName)) {
				Sb3Importer.Result result = store.scripts(pack);
				if (!result.ok()) {
					y += ROW_HEIGHT;
					continue;
				}
				for (ScriptDefinition definition : result.scripts()) {
					addScriptRow(pack, definition, left, y);
					y += ROW_HEIGHT;
				}
			}
		}

		int bottom = height - 30;
		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.import"),
				button -> {
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
		).dimensions(left, bottom, 60, 20).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.rename"),
				button -> renameSelected()
		).dimensions(left + 64, bottom, 60, 20).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.delete"),
				button -> confirmDeleteSelected()
		).dimensions(left + 128, bottom, 60, 20).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("screen.turbomc.runtime"),
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

	private void addPackRow(PackStore store, ScriptPack pack, int left, int y, boolean hasUpdate) {
		boolean isSelected = pack.fileName.equals(selected);

		// 状态圆点：点击即切换启用（互斥，其余包自动关闭）
		addDrawableChild(ButtonWidget.builder(
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
		).dimensions(left, y, 20, 20).build());

		ButtonWidget nameButton = ButtonWidget.builder(
				// ↑ 表示 imports 里有内容不同的同名文件，点「导入」即会同步
				Text.literal((isSelected ? "\u00BB " : "") + pack.displayName() + (hasUpdate ? " \u2191" : "")),
				button -> {
					selected = pack.fileName;
					refresh();
				}
		).dimensions(left + 22, y, 190, 20).build();
		if (hasUpdate) {
			nameButton.setTooltip(Tooltip.of(Text.translatable("screen.turbomc.update_pending")));
		}
		addDrawableChild(nameButton);

		Sb3Importer.Result result = store.scripts(pack);
		String count = result.ok()
				? Text.translatable("screen.turbomc.script_count", result.scripts().size()).getString()
				: Text.translatable("screen.turbomc.broken").getString();
		// 数量是只读信息，用按钮只是因为与行对齐
		addDrawableChild(ButtonWidget.builder(
				Text.literal(count),
				button -> {
					selected = pack.fileName;
					refresh();
				}
		).dimensions(left + 214, y, 100, 20).build());

		boolean open = expanded.contains(pack.fileName);
		addDrawableChild(ButtonWidget.builder(
				Text.translatable(open ? "screen.turbomc.collapse" : "screen.turbomc.expand"),
				button -> {
					if (!expanded.remove(pack.fileName)) {
						expanded.add(pack.fileName);
					}
					refresh();
				}
		).dimensions(left + 318, y, 76, 20).build());
	}

	private void addScriptRow(ScriptPack pack, ScriptDefinition definition, int left, int y) {
		String trigger = definition.hat == null ? "" : NodeChain.label(definition.hat.type);
		addDrawableChild(ButtonWidget.builder(
				Text.literal("  \u2514 " + definition.name + "   " + trigger),
				button -> {
					if (client != null) {
						client.setScreen(new ScriptEditScreen(this, definition));
					}
				}
		).dimensions(left + 22, y, 372, 20).build());
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
		int left = width / 2 - 210;
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFFFF);

		if (PackStore.get().packs().isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("screen.turbomc.no_packs"), width / 2, 60, 0xFFA0A0A0);
		}

		context.drawTextWithShadow(textRenderer,
				Text.translatable("screen.turbomc.pack_hint"),
				left, height - 44, 0xFF808080);

		ScriptPack active = PackStore.get().activePack();
		if (active != null) {
			context.drawTextWithShadow(textRenderer,
					Text.translatable("screen.turbomc.running_pack", active.fileName),
					left, height - 60, 0xFF55FF55);
		}

		String message = PackStore.get().lastMessage();
		if (!message.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.literal(message),
					width / 2, height - 58, 0xFF55FF55);
		}
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
