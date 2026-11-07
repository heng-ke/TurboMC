package xyz.turbomc.client.script.vm;

import xyz.turbomc.client.script.action.NativeRegistry;

/**
 * 「当按下 [键]」帽子的派发冒烟：{@code startHats} 的字段匹配只认 field，
 * 匹配不上的时候<b>不报错、返回 0 个线程</b>，脚本看起来就是「按了没反应」。
 *
 * <p>所以这里把两个方向都钉住：</p>
 * <ul>
 *   <li>键名对得上 → 必须起 1 个线程，并且真的跑到了积木体（桥被调用）；</li>
 *   <li>键名对不上 → 必须起 0 个，防止「随便按什么键都触发」。</li>
 * </ul>
 *
 * <p>2026-11-07 的真机回归里，玩家按住空格没有任何反应，而日志里「监听按键 1 个」是正常的，
 * 说明收集这一步没问题、问题在派发这一步——就需要这样一条不依赖窗口的用例来定位。</p>
 */
public final class KeyHatSmokeTest {
	private static final String FIXTURE = "keyhat-project.json";

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

		// 1) 对得上：空格帽应起 1 个线程
		int hit = vm.startHat("event_whenkeypressed", "KEY_OPTION", "space");
		// 2) 对不上：按 a 不该启动任何脚本
		int miss = vm.startHat("event_whenkeypressed", "KEY_OPTION", "a");
		System.out.println("[keyhat] 空格命中 " + hit + " 个线程，a 命中 " + miss + " 个（期望 1 / 0）");

		long callsBefore = ScriptBridge.callCount();
		for (int i = 0; i < 5; i++) {
			vm.tick(null);
		}
		long executed = ScriptBridge.callCount() - callsBefore;
		System.out.println("[keyhat] 帽体执行 " + executed + " 次桥调用（期望 ≥1）");

		boolean ok = hit == 1 && miss == 0 && executed >= 1;
		System.out.println(ok ? "KEY HAT PASS" : "KEY HAT FAIL");
		System.exit(ok ? 0 : 1);
	}
}
