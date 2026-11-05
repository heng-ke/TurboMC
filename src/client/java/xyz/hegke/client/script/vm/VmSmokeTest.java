package xyz.hegke.client.script.vm;

import xyz.hegke.client.script.action.NativeRegistry;

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

	public static void main(String[] args) throws Exception {
		Path project = Path.of(args.length > 0 ? args[0] : "run/hkscript-vm/project.json");
		if (!Files.isRegularFile(project)) {
			System.err.println("找不到工程文件: " + project.toAbsolutePath());
			System.exit(2);
		}

		NativeRegistry.bootstrapOnce();
		System.out.println("[smoke] 动作已注册");

		RhinoVm vm = RhinoVm.get();
		vm.setBudgetMs(4);
		long t0 = System.nanoTime();
		vm.init();
		System.out.printf("[smoke] VM 就绪 %d ms，积木 %d 个%n",
				(System.nanoTime() - t0) / 1_000_000L, vm.blockCount());

		String json = Files.readString(project, StandardCharsets.UTF_8);
		vm.loadProject(json);
		System.out.println("[smoke] 工程已载入，线程 " + vm.threadCount());

		boolean hatMode = args.length > 1 && "hat".equals(args[1]);
		boolean blockMode = args.length > 1 && "block".equals(args[1]);
		if (blockMode) {
			// 阻塞动作：sleep 应让 score 在约 40 帧后才被置 1
			vm.greenFlag();
			int hitFrame = -1;
			int frames = 120;
			for (int i = 0; i < frames; i++) {
				vm.tick(null);
				if (hitFrame < 0 && "1".equals(String.valueOf(vm.getVariable("score")))) {
					hitFrame = i;
				}
			}
			System.out.println("[smoke] score 在第 " + hitFrame + " 帧变为 1（期望约 40）");
			boolean blockOk = hitFrame >= 30 && hitFrame <= 50;
			System.out.println(blockOk ? "BLOCK SMOKE PASS" : "BLOCK SMOKE FAIL");
			System.exit(blockOk ? 0 : 1);
			return;
		}

		if (hatMode) {
			// 模拟宿主事件：聊天含 hello → 触发「当聊天包含 [MATCH]」帽子。
			// 不传匹配字段：扩展的 MATCH 声明为 input，而 startHats 的字段匹配只认 field，
			// 传了会在 hatFields[matchField].value 处崩（见阶段 2 报告已知限制）。
			int started = vm.startHat("hkscript_whenChat", (String) null);
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
		for (int i = 0; i < 10; i++) {
			vm.tick(null);
		}
		System.out.println("[smoke] 步进 10 帧完成，线程 " + vm.threadCount()
				+ "，桥调用 " + ScriptBridge.callCount() + " 次，未映射 " + ScriptBridge.missCount() + " 次");

		boolean ok = ScriptBridge.callCount() > 0 && ScriptBridge.missCount() == 0;
		System.out.println(ok ? "SMOKE PASS" : "SMOKE FAIL");
		System.exit(ok ? 0 : 1);
	}
}
