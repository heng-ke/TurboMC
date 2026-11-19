package xyz.turbomc.client.script.vm;

import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.runtime.ExtensionHealth;
import xyz.turbomc.client.script.runtime.MonitorStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 冒烟测试：在纯 JVM（不启动客户端）里验证 VM 后端。
 *
 * <p>覆盖链路：资源加载 → VM 实例化 → 扩展注册 → loadProject → greenFlag → step
 * → JS 积木经 {@link ScriptBridge} 调用 Java 动作。</p>
 *
 * <p>仅手动运行：{@code ./gradlew vmSmoke}。tick 传 null 是因为所选动作（debug.log）
 * 不接触 Minecraft 客户端；换成涉及世界的动作时必须传真实实例。</p>
 */
public final class VmSmokeTest {
	private VmSmokeTest() {
	}

	/** 夹具在 classpath 中的位置；与 src/test/resources/turbomc-vm/ 一一对应。 */
	private static final String FIXTURE_ROOT = "/turbomc-vm/";

	public static void main(String[] args) throws Exception {
		String source = args.length > 0 ? args[0] : "project.json";
		String json = readFixture(source);
		if (json == null) {
			System.err.println("找不到工程夹具: " + source);
			System.err.println("可用的 classpath 夹具: project.json / hat-project.json /"
					+ " block-project.json / ask-project.json / wait-mouse-project.json /"
					+ " merged-project.json");
			System.exit(2);
		}

		NativeRegistry.bootstrapOnce();
		System.out.println("[smoke] 动作已注册");

		// 纯 JVM 没有客户端窗口：用同步作答替换界面，才能验证 QUESTION → 桥 → ANSWER 整条链
		ScriptBridge.setPresenter((question, onAnswer) -> onAnswer.accept("smoke"));

		RhinoVm vm = RhinoVm.get();
		vm.setBudgetMs(4);
		long t0 = System.nanoTime();
		vm.init();
		System.out.printf("[smoke] VM 就绪 %d ms，积木 %d 个%n",
				(System.nanoTime() - t0) / 1_000_000L, vm.blockCount());

		vm.loadProject(json);
		System.out.println("[smoke] 工程已载入，线程 " + vm.threadCount());

		// 两个判断积木都依赖 VM 输入设备被宿主接管；漏装时积木静默返回 false，必须显式断言
		boolean keyboardOk = vm.keyboardHooked();
		boolean mouseOk = vm.mouseHooked();
		boolean userOk = vm.userHooked();
		boolean askOk = vm.askHooked();
		boolean monitorOk = vm.monitorHooked();
		// 映射了但动作层没实现时，桥只会记一次 miss 并返回 null，积木看起来"永远为假"
		boolean mouseBlockOk = NativeRegistry.getFunction("input.mouse_button_down") != null;
		// 合并块：注册表里缺一个，面板上就有、游戏里就没反应，
		// 而夹具只能端到端跑 slotField（其余读世界状态，纯 JVM 无客户端会 NPE），
		// 所以这里直接查注册表，覆盖「漏实现」这一层
		String[] merged = {"world.pos", "world.aim_pos", "world.nearest_entity_field",
				"world.held_item_field", "world.slot_field"};
		boolean mergedOk = allImplemented(merged);
		// 快照三块同理：漏实现时积木照常能拖，只是永远返回空对象/空数组
		boolean snapshotOk = allImplemented(new String[]{
				"world.player_json", "world.entities_json", "world.block_json",
				"world.inventory_json", "world.screen_json", "world.world_json",
				"world.players_json", "world.scan_json", "query.chat_history",
				"world.snapshot_keys"});
		boolean boolOk = vm.booleanNormalizeOk();
		// 扩展积木的占位符数必须与查看器侧登记的参数数一致：不等就说明参数会错位
		String argProblems = vm.argsSelfTest();
		boolean argsOk = argProblems.isEmpty();
		// 扩展产物的 opcode 集合必须与 Sb3Mapping 完全一致。对不上时游戏里只会表现为
		// 「新积木拖不出来」或「点了没反应」，HUD 虽会警告，但 CI 上先失败更省事
		String extensionWarning = ExtensionHealth.warning();
		boolean extensionOk = extensionWarning.isEmpty();
		System.out.println("[smoke] 钩子：键盘 " + (keyboardOk ? "已装" : "缺失")
				+ "，鼠标 " + (mouseOk ? "已装" : "缺失")
				+ "，用户名 " + (userOk ? "已装" : "缺失")
				+ "，询问 " + (askOk ? "已装" : "缺失")
				+ "，监视器 " + (monitorOk ? "已装" : "缺失")
				+ "，鼠标积木 " + (mouseBlockOk ? "已注册" : "未实现")
				+ "，合并块 " + (mergedOk ? "已实现" : "有缺失")
				+ "，快照 " + (snapshotOk ? "已实现" : "有缺失")
				+ "，布尔归一化 " + (boolOk ? "正常" : "异常"));
		System.out.println("[smoke] 扩展参数名 " + (argsOk ? "一致" : "不一致：" + argProblems));
		System.out.println("[smoke] 扩展自检 " + (extensionOk ? "一致" : "不一致：" + extensionWarning));

		boolean hatMode = args.length > 1 && "hat".equals(args[1]);
		boolean blockMode = args.length > 1 && "block".equals(args[1]);
		boolean waitMode = args.length > 1 && "wait".equals(args[1]);
		if (waitMode) {
			// 「等待直到 <按下鼠标 [中键]?>」在没有任何鼠标输入时必须一直挂住。
			// 这条守住的是「布尔归一化接线断了 → 所有是/否积木恒真」那次事故：
			// 那时唯一的现象就是等待瞬间通过，脚本一路跑到结尾。
			vm.greenFlag();
			int frames = 40;
			for (int i = 0; i < frames; i++) {
				vm.tick(null);
			}
			String score = String.valueOf(vm.getVariable("score"));
			// 探针要能区分「挂住」与「条件根本没被求值」：后者同样不会通过，但是假绿
			long probes = ScriptBridge.callCount();
			boolean held = "0".equals(score);
			boolean probed = probes > 0;
			System.out.println("[smoke] 等待中键 " + frames + " 帧：未通过=" + held
					+ "，条件求值 " + probes + " 次");
			boolean waitOk = held && probed;
			System.out.println(waitOk ? "WAIT SMOKE PASS" : "WAIT SMOKE FAIL");
			System.exit(waitOk ? 0 : 1);
			return;
		}
		if (blockMode) {
			// 条件等待：「等待方块被挖破」在无客户端时无法判定，应挂起到 200 帧上限才放行
			vm.greenFlag();
			int hitFrame = -1;
			int frames = 260;
			for (int i = 0; i < frames; i++) {
				vm.tick(null);
				if (hitFrame < 0 && "1".equals(String.valueOf(vm.getVariable("score")))) {
					hitFrame = i;
				}
			}
			System.out.println("[smoke] score 在第 " + hitFrame + " 帧变为 1（期望约 200）");
			boolean blockOk = hitFrame >= 190 && hitFrame <= 215;
			System.out.println(blockOk ? "BLOCK SMOKE PASS" : "BLOCK SMOKE FAIL");
			System.exit(blockOk ? 0 : 1);
			return;
		}

		if (hatMode) {
			// 模拟宿主事件：聊天含 hello → 触发「当聊天包含 [MATCH]」帽子。
			// 不传匹配字段：扩展的 MATCH 声明为 input，而 startHats 的字段匹配只认 field，
			// 传了会在 hatFields[matchField].value 处崩（见阶段 2 报告已知限制）。
			int started = vm.startHat("turbomc_whenChat", (String) null);
			System.out.println("[smoke] startHat 起了 " + started + " 个线程");
			for (int i = 0; i < 5; i++) {
				vm.tick(null);
			}
			boolean hatOk = started > 0 && ScriptBridge.callCount() > 0;
			System.out.println("[smoke] 帽子触发完成，桥调用 " + ScriptBridge.callCount() + " 次");
			System.out.println(hatOk ? "HAT SMOKE PASS" : "HAT SMOKE FAIL");
			System.exit(hatOk ? 0 : 1);
			return;
		}

		vm.greenFlag();
		// 「停止全部」的联动靠 PROJECT_STOP_ALL 计数，这里验两件事：
		// 1) greenFlag 内部确实会 stopAll，钩子能收到（否则「停止全部」永远不生效）；
		// 2) takeProjectStops 取走即清零（否则会每 tick 重复收尾，一启动就被判成停止）。
		int stopsAfterFlag = vm.takeProjectStops();
		int stopsAfterTake = vm.takeProjectStops();
		System.out.println("[smoke] greenFlag 后停止计数 " + stopsAfterFlag + "（期望 ≥1），"
				+ "再取一次 " + stopsAfterTake + "（期望 0）");
		boolean stopHookOk = stopsAfterFlag >= 1 && stopsAfterTake == 0;
		for (int i = 0; i < 10; i++) {
			vm.tick(null);
		}
		System.out.println("[smoke] 步进 10 帧完成，线程 " + vm.threadCount()
				+ "，桥调用 " + ScriptBridge.callCount() + " 次，未映射 " + ScriptBridge.missCount() + " 次");
		// 「显示变量」由原生积木钩子登记，这里顺便核对是否真的落到监视器表里
		System.out.println("[smoke] 监视器 " + MonitorStore.snapshot());
		System.out.println("[smoke] 变量 " + vm.variableDump());

		boolean ok = ScriptBridge.callCount() > 0 && ScriptBridge.missCount() == 0
				&& keyboardOk && mouseOk && userOk && askOk && monitorOk && mouseBlockOk && boolOk
				&& argsOk && stopHookOk && mergedOk && extensionOk;
		System.out.println(ok ? "SMOKE PASS" : "SMOKE FAIL");
		System.exit(ok ? 0 : 1);
	}

	/** 逐个查动作层的实现；缺哪个就打印出来，不靠一次性的布尔值埋线索。 */
	private static boolean allImplemented(String[] types) {
		for (String type : types) {
			if (NativeRegistry.getFunction(type) == null) {
				System.out.println("[smoke] 缺少实现: " + type);
				return false;
			}
		}
		return true;
	}

	/**
	 * 读夹具：先当路径找，再当 classpath 资源找。
	 *
	 * <p>classpath 里的是版本控制内的副本（src/test/resources/turbomc-vm/），CI 上只有这一份；
	 * 路径分支留给本地临时夹具。</p>
	 */
	static String readFixture(String source) throws IOException {
		Path path = Path.of(source);
		if (Files.isRegularFile(path)) {
			return Files.readString(path, StandardCharsets.UTF_8);
		}
		String resource = source.startsWith("/") ? source : FIXTURE_ROOT + source;
		try (InputStream in = VmSmokeTest.class.getResourceAsStream(resource)) {
			if (in == null) {
				return null;
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
