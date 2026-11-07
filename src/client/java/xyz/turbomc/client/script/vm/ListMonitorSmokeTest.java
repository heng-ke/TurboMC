package xyz.turbomc.client.script.vm;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.node.BlockNode;
import xyz.turbomc.client.script.runtime.MonitorStore;
import xyz.turbomc.client.script.sb3.Sb3Importer;
import xyz.turbomc.client.script.store.ScriptDefinition;

/**
 * 两件事：列表能被 HUD 读到，以及算术积木的参数名映射正确。
 *
 * <p><b>列表</b>：这套 scratch-vm 把变量与列表统一存在 {@code target.variables} 里、用
 * {@code type} 区分。早先 {@code __tmcGetList} 只遍历旧的 {@code target.lists}，于是
 * HUD 上的「显示列表」永远是空的——列表里其实早有数据。这里跑真实工程把它钉住。</p>
 *
 * <p><b>算术参数</b>：Scratch 的加/减/乘/除/取余用 {@code NUM1/NUM2}，查看器模板用
 * {@code {A}/{B}}；映射漏了这两个，摘要就会显示成 {@code ? × ?}。</p>
 */
public final class ListMonitorSmokeTest {
	private static final String FIXTURE = "list-project.json";
	private static final String LIST_NAME = "数据";
	private static final int EXPECTED_ITEMS = 2;

	public static void main(String[] args) throws Exception {
		// 允许传任意工程（.json 或 sb3 解出来的 project.json），用来验证玩家自己那份
		String source = args.length > 0 ? args[0] : FIXTURE;
		// 外部工程有自己的节奏（可能先等待再写列表），多跑几帧再断言
		boolean custom = args.length > 0;
		String json = VmSmokeTest.readFixture(source);
		if (json == null) {
			System.err.println("找不到工程夹具: " + source);
			System.exit(2);
			return;
		}

		NativeRegistry.bootstrapOnce();
		RhinoVm vm = RhinoVm.get();
		vm.setBudgetMs(4);
		vm.init();
		vm.loadProject(json);
		vm.greenFlag();
		vm.takeProjectStops();
		int frames = custom ? 120 : 5;
		for (int i = 0; i < frames; i++) {
			vm.tick(null);
		}

		MonitorStore.Kind kind = MonitorStore.snapshot().get(LIST_NAME);
		int items = countItems(vm.getList(LIST_NAME));
		// 传了外部工程时不假定条目数（工程的产出量由它自己决定），只要求「非空且读得到」
		int expected = custom ? -1 : EXPECTED_ITEMS;
		boolean countOk = custom ? items > 0 : items == expected;
		System.out.println("[list] 监视器登记 " + kind + "（期望 LIST），读到 " + items
				+ " 项（期望 " + (custom ? ">0" : String.valueOf(expected)) + "）：" + vm.getList(LIST_NAME));

		boolean mappingOk = arithmeticMappingOk();
		System.out.println("[list] 算术积木参数名映射 " + (mappingOk ? "正确（A/B）" : "仍是 NUM1/NUM2"));

		boolean ok = kind == MonitorStore.Kind.LIST && countOk && mappingOk;
		System.out.println(ok ? "LIST SMOKE PASS" : "LIST SMOKE FAIL");
		System.exit(ok ? 0 : 1);
	}

	private static int countItems(String listJson) {
		if (listJson == null || listJson.isEmpty()) {
			return -1;
		}
		try {
			JsonElement parsed = JsonParser.parseString(listJson);
			return parsed.isJsonArray() ? parsed.getAsJsonArray().size() : -1;
		} catch (RuntimeException e) {
			return -1;
		}
	}

	/**
	 * 构造一个「设置变量为 [2 × 3]」的最小工程，检查解析后乘法积木的输入键是不是 A/B。
	 *
	 * <p>不落盘：映射是纯函数，内嵌字符串足够，也避免为一个两行映射再加一个夹具。</p>
	 */
	private static boolean arithmeticMappingOk() {
		String probe = "{\"targets\":["
				+ "{\"isStage\":true,\"name\":\"Stage\",\"variables\":{},\"lists\":{},\"blocks\":{}},"
				+ "{\"isStage\":false,\"name\":\"S\",\"variables\":{},\"lists\":{},\"blocks\":{"
				+ "\"hat\":{\"opcode\":\"event_whenflagclicked\",\"next\":\"setv\",\"parent\":null,"
				+ "\"inputs\":{},\"fields\":{},\"topLevel\":true},"
				+ "\"setv\":{\"opcode\":\"data_setvariableto\",\"next\":null,\"parent\":\"hat\","
				+ "\"inputs\":{\"VALUE\":[3,\"mul\",[10,\"\"]]},"
				+ "\"fields\":{\"VARIABLE\":[\"x\",\"var-x\"]},\"topLevel\":false},"
				+ "\"mul\":{\"opcode\":\"operator_multiply\",\"next\":null,\"parent\":\"setv\","
				+ "\"inputs\":{\"NUM1\":[1,[4,\"2\"]],\"NUM2\":[1,[4,\"3\"]]},\"fields\":{},\"topLevel\":false}"
				+ "}}]}";

		Sb3Importer.Result result = Sb3Importer.parse(JsonParser.parseString(probe));
		if (!result.ok() || result.scripts().isEmpty()) {
			return false;
		}
		ScriptDefinition definition = result.scripts().get(0);
		for (BlockNode node = definition.hat; node != null; node = node.next) {
			var slot = node.inputs.get("VALUE");
			if (slot != null && slot.hasBlock() && "operator.multiply".equals(slot.block.type)) {
				return slot.block.inputs.containsKey("A") && slot.block.inputs.containsKey("B");
			}
		}
		return false;
	}
}
