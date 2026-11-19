package xyz.turbomc.client.script.vm;

import xyz.turbomc.client.script.action.NativeRegistry;

/**
 * 「当打开 [界面]」帽子的派发冒烟。
 *
 * <p>这块帽子和按键帽子一样靠<b>字段匹配</b>决定起哪些线程，匹配错的表现同样是
 * 「打开了界面但脚本没反应」——没有异常、没有日志。所以两个方向都要钉住：</p>
 * <ul>
 *   <li>类型对得上 → 起 1 个线程，并且真的跑到积木体（桥被调用）；</li>
 *   <li>类型对不上（含 {@code any} 缺位）→ 起 0 个，防止「开什么界面都触发」。</li>
 * </ul>
 *
 * <p>夹具里有 container 与 inventory 两块帽子，用来验证两者互不串台。</p>
 */
public final class ScreenHatSmokeTest {
	private static final String FIXTURE = "screenhat-project.json";

	public static void main(String[] args) throws Exception {
		String json = VmSmokeTest.readFixture(FIXTURE);
		if (json == null) {
			System.err.println("找不到工程夹具: " + FIXTURE);
			System.exit(2);
			return;
		}

		NativeRegistry.bootstrapOnce();
		RhinoVm vm = RhinoVm.get();
		vm.setBudgetMs(4);
		vm.init();
		vm.loadProject(json);

		long before = ScriptBridge.callCount();
		int container = vm.startHat("turbomc_whenScreenOpen", "SCREEN", "container");
		pump(vm, 5);
		int inventory = vm.startHat("turbomc_whenScreenOpen", "SCREEN", "inventory");
		pump(vm, 5);
		// 夹具里没有「任意界面」的帽子：这两次都必须起 0 个
		int any = vm.startHat("turbomc_whenScreenOpen", "SCREEN", "any");
		int other = vm.startHat("turbomc_whenScreenOpen", "SCREEN", "other");
		long executed = ScriptBridge.callCount() - before;

		boolean ok = container == 1 && inventory == 1 && any == 0 && other == 0 && executed >= 2;
		System.out.println("[screenhat] 容器命中 " + container + " 个线程（期望 1），背包命中 "
				+ inventory + " 个（期望 1），any 命中 " + any + "（期望 0），other 命中 "
				+ other + "（期望 0），桥调用 " + executed + " 次（期望 ≥2）");
		if (!ok) {
			System.err.println("[screenhat] FAILED: screen hat field matching");
			System.exit(1);
		}
		System.out.println("[screenhat] 通过");
	}

	private static void pump(RhinoVm vm, int frames) {
		for (int i = 0; i < frames; i++) {
			vm.tick(null);
		}
	}

	private ScreenHatSmokeTest() {
	}
}
