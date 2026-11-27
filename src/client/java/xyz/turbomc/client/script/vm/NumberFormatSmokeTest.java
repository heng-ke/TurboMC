package xyz.turbomc.client.script.vm;

import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.block.BlockRegistry;
import xyz.turbomc.client.script.runtime.ScriptContext;

/**
 * 数值字符串化冒烟：在<b>真实 VM</b>里确认数值积木的字符串化结果。
 *
 * <p><b>要防的是什么</b>：{@code ScriptBridge.call} 的返回类型是 {@code Object}，
 * Rhino 默认把 {@code Double} 封装成 {@code NativeJavaObject}，而上游
 * {@code Cast.toString} 的实现就是 {@code String(e)}——对封装对象取到的是
 * <b>Java 的 {@code Double.toString}</b>。于是生命值 20.0 拼进字符串会得到
 * {@code "20.0"}，而 Scratch 期望 {@code "20"}；{@code 1e-5} 会得到
 * {@code "1.0E-5"}，期望 {@code "0.00001"}。</p>
 *
 * <p>修法是 {@code RhinoVm.ScalarWrapFactory}：让标量以原生 JS 值进入 VM，
 * {@code String(e)} 于是走 ECMAScript 的 {@code String(number)}。本用例守住这条。</p>
 *
 * <p>验收走的是「注册探针实现 → 让扩展积木调用它 → 在脚本里拼接」这条完整链路。
 * 不能直接调 {@code ScriptBridge.call}：那样测的是桥，不是 VM 的字符串化。</p>
 *
 * <p>仅手动运行：{@code ./gradlew vmNumberFormat}。</p>
 */
public final class NumberFormatSmokeTest {
	private NumberFormatSmokeTest() {
	}

	public static void main(String[] args) throws Exception {
		NativeRegistry.bootstrapOnce();
		BlockRegistry.bootstrapOnce();

		// 探针：玩家的读数已合并成 world.player_field（旧的 world.health / food / xp_level
		// 三块已删除），这里按 FIELD 参数给固定值——真实实现要读客户端，纯 JVM 拿不到。
		// 三个值刻意不同：正常数、极小浮点、布尔，用来测封送与字符串化。
		NativeRegistry.function("world.player_field", (ctx, values) -> {
			String field = values.length > 0 ? String.valueOf(values[0]) : "health";
			return switch (field) {
				case "food" -> 0.00001d;
				case "xp_level" -> Boolean.FALSE;
				default -> 20.0d;
			};
		});

		RhinoVm vm = RhinoVm.get();
		vm.setBudgetMs(4);
		vm.init();
		vm.loadProject(project());
		vm.greenFlag();
		for (int i = 0; i < 10; i++) {
			vm.tick(null);
		}

		Object joinedRaw = vm.getVariable("joined");
		Object numericRaw = vm.getVariable("numeric");
		Object tinyRaw = vm.getVariable("tiny");
		Object boolRaw = vm.getVariable("bool");

		String joined = joinedRaw == null ? "(null)" : String.valueOf(joinedRaw);
		System.out.println("[e2e] operator_join 拼出的字符串 = " + joined);
		System.out.println("[e2e] 数值积木运行时类型 = " + describe(numericRaw)
				+ "，值 = " + numericRaw);
		System.out.println("[e2e] 小数值积木 = " + describe(tinyRaw) + "，值 = " + tinyRaw);
		System.out.println("[e2e] 布尔积木 = " + describe(boolRaw) + "，值 = " + boolRaw);

		boolean joinOk = "20!".equals(joined);
		boolean numberOk = numericRaw instanceof Number;
		boolean tinyOk = tinyRaw instanceof Number;
		boolean boolOk = boolRaw instanceof Boolean && Boolean.FALSE.equals(boolRaw);
		System.out.println("[e2e] 字符串化是否为 ECMAScript 格式 = " + joinOk
				+ "（期望 \"20!\"，非 \"20.0!\"）");
		System.out.println("[e2e] 数值积木仍是数值类型 = " + numberOk);
		System.out.println("[e2e] 小数值仍是数值类型 = " + tinyOk);
		System.out.println("[e2e] 布尔积木仍是布尔类型且为 false = " + boolOk);

		boolean ok = joinOk && numberOk && tinyOk && boolOk;
		System.out.println(ok ? "NUMBER FORMAT PASS" : "NUMBER FORMAT FAIL");
		System.exit(ok ? 0 : 1);
	}

	private static String describe(Object value) {
		if (value == null) {
			return "null";
		}
		return value.getClass().getSimpleName();
	}

	/**
	 * 工程：数值积木 → 变量；数值积木 → operator_join → 字符串变量；布尔积木 → 变量。
	 *
	 * <p>输入用三元素形式 {@code [3, "块ID", [10, ""]]}：第三段是 shadow 的默认值，
	 * 缺了它 VM 不会把输入解析成块引用。</p>
	 */
	private static String project() {
		return """
				{
				  "targets": [
				    {
				      "isStage": true, "name": "Stage",
				      "variables": {"jid":["joined",""], "nid":["numeric",0],
				                    "tid":["tiny",0], "bid":["bool",true]},
				      "lists": {}, "broadcasts": {}, "blocks": {}, "comments": {},
				      "currentCostume": 0,
				      "costumes": [{"name":"c","dataFormat":"svg","assetId":"cd21514d0531fdffb22204e0ec5ed84a",
				        "md5ext":"cd21514d0531fdffb22204e0ec5ed84a.svg","rotationCenterX":0,"rotationCenterY":0}],
				      "sounds": [], "volume": 100, "layerOrder": 0, "tempo": 60,
				      "videoTransparency": 50, "videoState": "on", "textToSpeechLanguage": null
				    },
				    {
				      "isStage": false, "name": "角色1", "variables": {}, "lists": {}, "broadcasts": {},
				      "blocks": {
				        "b0": {"opcode":"event_whenflagclicked","next":"b1","parent":null,
				          "inputs":{},"fields":{},"shadow":false,"topLevel":true,"x":0,"y":0},
				        "b1": {"opcode":"data_setvariableto","next":"b2","parent":"b0",
				          "inputs":{"VALUE":[3,"v_num",[10,""]]},"fields":{"VARIABLE":["numeric","nid"]},
				          "shadow":false,"topLevel":false},
				        "b2": {"opcode":"data_setvariableto","next":"b3","parent":"b1",
				          "inputs":{"VALUE":[3,"v_tiny",[10,"0"]]},"fields":{"VARIABLE":["tiny","tid"]},
				          "shadow":false,"topLevel":false},
				        "b3": {"opcode":"data_setvariableto","next":"b4","parent":"b2",
				          "inputs":{"VALUE":[3,"v_bool",[10,""]]},"fields":{"VARIABLE":["bool","bid"]},
				          "shadow":false,"topLevel":false},
				        "b4": {"opcode":"data_setvariableto","next":null,"parent":"b3",
				          "inputs":{"VALUE":[3,"v_join",[10,""]]},"fields":{"VARIABLE":["joined","jid"]},
				          "shadow":false,"topLevel":false},
				        "v_num": {"opcode":"turbomc_playerField","next":null,"parent":"b1",
				          "inputs":{"FIELD":[1,[10,"health"]]},"fields":{},"shadow":true,"topLevel":false},
				        "v_tiny": {"opcode":"turbomc_playerField","next":null,"parent":"b2",
				          "inputs":{"FIELD":[1,[10,"food"]]},"fields":{},"shadow":true,"topLevel":false},
				        "v_bool": {"opcode":"turbomc_playerField","next":null,"parent":"b3",
				          "inputs":{"FIELD":[1,[10,"xp_level"]]},"fields":{},"shadow":true,"topLevel":false},
				        "v_join": {"opcode":"operator_join","next":null,"parent":"b4",
				          "inputs":{"STRING1":[3,"v_join2",[10,""]],"STRING2":[1,[10,"!"]]},
				          "fields":{},"shadow":true,"topLevel":false},
				        "v_join2": {"opcode":"turbomc_playerField","next":null,"parent":"v_join",
				          "inputs":{"FIELD":[1,[10,"health"]]},"fields":{},"shadow":true,"topLevel":false}
				      },
				      "comments": {},
				      "currentCostume": 0,
				      "costumes": [{"name":"c","dataFormat":"svg","assetId":"cd21514d0531fdffb22204e0ec5ed84a",
				        "md5ext":"cd21514d0531fdffb22204e0ec5ed84a.svg","rotationCenterX":0,"rotationCenterY":0}],
				      "sounds": [], "volume": 100, "layerOrder": 1, "visible": true,
				      "x": 0, "y": 0, "size": 100, "direction": 90, "draggable": false, "rotationStyle": "all around"
				    }
				  ],
				  "monitors": [],
				  "extensions": ["turbomc"],
				  "meta": {"semver": "3.0.0", "vm": "0.2.0", "agent": "number-format"}
				}
				""";
	}
}
