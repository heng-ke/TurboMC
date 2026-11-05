package xyz.hegke.client.script.vm;

import xyz.hegke.client.script.action.NativeRegistry;
import xyz.hegke.client.script.block.BlockRegistry;
import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.node.Slot;
import xyz.hegke.client.script.sb3.Sb3Importer;
import xyz.hegke.client.script.store.ScriptDefinition;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 导入冒烟：sb3 → 可查看的积木树，以及 sb3 → VM 执行。
 *
 * <p>新架构下脚本由 TurboWarp 产出，游戏只做两件事：把工程交给 VM、把积木树画出来。
 * 因此验证也拆成两条：</p>
 * <ol>
 *   <li><b>可查看</b>——解析后每块积木都能在积木定义表里找到，否则界面会显示成未知块；</li>
 *   <li><b>可执行</b>——原始 project.json 直接交给 VM 能跑到动作层。</li>
 * </ol>
 *
 * <p>仅手动运行：{@code ./gradlew vmImport -Psb3=路径}。</p>
 */
public final class ImportSmokeTest {
	private ImportSmokeTest() {
	}

	public static void main(String[] args) throws Exception {
		NativeRegistry.bootstrapOnce();
		BlockRegistry.bootstrapOnce();
		Path source = Path.of(args.length > 0 ? args[0]
				: "run/config/hk-script/imports/imported/作品.sb3");

		Sb3Importer.Result parsed = Sb3Importer.readFile(source);
		if (!parsed.ok()) {
			System.out.println("[import] 解析失败: " + parsed.error());
			System.out.println("IMPORT SMOKE FAIL");
			System.exit(1);
		}

		System.out.println("[import] 文件: " + source.getFileName()
				+ "，脚本 " + parsed.scripts().size() + " 份");
		System.out.print(Sb3Importer.describe(parsed.scripts()));
		if (!parsed.unsupported().isEmpty()) {
			System.out.println("[import] 查看器不认识的积木: " + parsed.unsupported());
		}
		boolean viewable = parsed.unsupported().isEmpty();
		boolean hasCustom = parsed.scripts().stream().anyMatch(item -> contains(item.hat, "procedures.definition"));
		System.out.println("[import] 可查看=" + viewable + " 含自定义积木=" + hasCustom);

		String json = readProjectJson(source);
		boolean canRead = json != null;

		RhinoVm vm = RhinoVm.get();
		vm.setBudgetMs(4);
		vm.init();
		vm.loadProject(json);
		vm.greenFlag();
		for (int i = 0; i < 10; i++) {
			vm.tick(null);
		}
		int calls = ScriptBridge.callCount();
		System.out.println("[import] 步进 10 帧，线程 " + vm.threadCount()
				+ "，桥调用 " + calls + "，未映射 " + ScriptBridge.missCount());
		// 「说 / 思考」走的是 VM 原生积木，只能从气泡状态里读——HUD 就靠这条通道
		String speech = vm.speechJson();
		System.out.println("[import] 气泡: " + (speech == null || speech.isEmpty() ? "(无)" : speech));

		// JVM 里没有 MinecraftClient，动作会抛 NPE 被桥吞掉，因此只要求「调用到动作层」
		boolean ran = calls > 0 || parsed.scripts().stream().noneMatch(ImportSmokeTest::hasAction);
		boolean ok = viewable && canRead && ran;
		System.out.println("[import] 结果: viewable=" + viewable + " readable=" + canRead + " ran=" + ran);
		System.out.println(ok ? "IMPORT SMOKE PASS" : "IMPORT SMOKE FAIL");
		System.exit(ok ? 0 : 1);
	}

	/** 直接取 sb3 里的 project.json —— 运行时走的就是这份原文。 */
	private static String readProjectJson(Path sb3) throws Exception {
		if (!Files.isRegularFile(sb3)) {
			return null;
		}
		try (ZipFile zip = new ZipFile(sb3.toFile())) {
			ZipEntry entry = zip.getEntry("project.json");
			if (entry == null) {
				return null;
			}
			try (InputStream in = zip.getInputStream(entry)) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		}
	}

	private static boolean hasAction(ScriptDefinition definition) {
		return containsAction(definition.hat);
	}

	private static boolean containsAction(BlockNode node) {
		BlockNode current = node;
		while (current != null) {
			if (current.type.startsWith("action.") || current.type.startsWith("world.")) {
				return true;
			}
			for (Slot slot : current.inputs.values()) {
				if (slot.hasBlock() && containsAction(slot.block)) {
					return true;
				}
			}
			for (BlockNode branch : current.branches.values()) {
				if (containsAction(branch)) {
					return true;
				}
			}
			current = current.next;
		}
		return false;
	}

	private static boolean contains(BlockNode node, String type) {
		BlockNode current = node;
		while (current != null) {
			if (type.equals(current.type)) {
				return true;
			}
			for (Slot slot : current.inputs.values()) {
				if (slot.hasBlock() && contains(slot.block, type)) {
					return true;
				}
			}
			for (BlockNode branch : current.branches.values()) {
				if (contains(branch, type)) {
					return true;
				}
			}
			current = current.next;
		}
		return false;
	}

	/** 供后续扩展：统计脚本树里的积木块数。 */
	static int blockCount(List<ScriptDefinition> scripts) {
		int total = 0;
		for (ScriptDefinition definition : scripts) {
			BlockNode current = definition.hat;
			while (current != null) {
				total++;
				current = current.next;
			}
		}
		return total;
	}
}
