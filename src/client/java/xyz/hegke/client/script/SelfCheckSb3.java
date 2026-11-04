package xyz.hegke.client.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.hegke.client.script.action.ListFunctions;
import xyz.hegke.client.script.action.NativeRegistry;
import xyz.hegke.client.script.ir.CompiledScript;
import xyz.hegke.client.script.ir.Compiler;
import xyz.hegke.client.script.ir.Instr;
import xyz.hegke.client.script.ir.Op;
import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.node.Slot;
import xyz.hegke.client.script.runtime.GlobalVariables;
import xyz.hegke.client.script.runtime.ScratchCast;
import xyz.hegke.client.script.runtime.ScratchClock;
import xyz.hegke.client.script.runtime.ScratchCompare;
import xyz.hegke.client.script.runtime.ScratchMath;
import xyz.hegke.client.script.runtime.ScriptContext;
import xyz.hegke.client.script.runtime.ScriptInstance;
import xyz.hegke.client.script.runtime.ScriptScheduler;
import xyz.hegke.client.script.runtime.TriggerPolicy;
import xyz.hegke.client.script.runtime.Values;
import xyz.hegke.client.script.sb3.Sb3Mapping;
import xyz.hegke.client.script.sb3.Sb3Parser;
import xyz.hegke.client.script.store.FunctionDefinition;
import xyz.hegke.client.script.store.ScriptDefinition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 启动自检：在进入游戏前验证编译器与解释器的核心语义。
 *
 * <p>只使用纯逻辑脚本与空上下文，不触碰任何 Minecraft 类型，因此可在初始化阶段安全执行。
 * 自检失败时引擎会被停用，避免带着错误语义去驱动真实操作。</p>
 */

/**
 * sb3 解析层自检：切片映射、菜单参数、停止全部、变量作用域、自定义积木。
 *
 * <p>与 {@link SelfCheck}（基础编译与运行时）和 {@link SelfCheckRuntime}（语义与调度）分开，
 * 便于单独定位「导入环节」的问题。</p>
 */
public final class SelfCheckSb3 {
	/**
	 * 长积木链：250 块的链必须完整导入。
	 *
	 * <p>历史上"链长度"与"嵌套深度"共用了一个 200 的上限，导致长脚本从第 201 块起被静默丢弃。</p>
	 */
	static void checkLongChain(List<String> failures) {
		int count = 250;
		StringBuilder json = new StringBuilder();
		json.append("{\"targets\":[{\"isStage\":true,\"name\":\"Stage\",\"blocks\":{}},");
		json.append("{\"isStage\":false,\"name\":\"S\",\"blocks\":{");
		json.append("\"b0\":{\"opcode\":\"event_whenflagclicked\",\"next\":\"b1\",\"parent\":null,")
				.append("\"inputs\":{},\"fields\":{},\"topLevel\":true,\"shadow\":false,\"x\":0,\"y\":0},");
		for (int i = 1; i <= count; i++) {
			String next = i < count ? "\"b" + (i + 1) + "\"" : "\"last\"";
			json.append("\"b").append(i).append("\":{\"opcode\":\"data_setvariableto\",\"next\":").append(next)
					.append(",\"parent\":\"b").append(i - 1)
					.append("\",\"inputs\":{\"VALUE\":[1,[10,\"v").append(i).append("\"]]},")
					.append("\"fields\":{\"VARIABLE\":[\"t\",\"vid\"]}},");
		}
		json.append("\"last\":{\"opcode\":\"hkscript_log\",\"next\":null,\"parent\":\"b").append(count)
				.append("\",\"inputs\":{\"TEXT\":[1,[10,\"end\"]]},\"fields\":{}}");
		json.append("}}]}");

		List<ScriptDefinition> parsed;
		try {
			parsed = Sb3Parser.parseScripts(SelfCheck.zipProject(json.toString()));
		} catch (Exception e) {
			failures.add("长积木链样例解析失败: " + e);
			return;
		}
		if (parsed.isEmpty()) {
			failures.add("长积木链样例未解析出脚本");
			return;
		}
		BlockNode tail = SelfCheck.findBlock(parsed.get(0).hat, "debug.log");
		if (tail == null) {
			failures.add("250 块的积木链被截断（末尾积木丢失）");
		}
	}

	/**
	 * sb3 解析相关的全部断言。
	 */
	static void run(List<String> failures) {
		checkLongChain(failures);
		checkInputOrder(failures);
		checkSb3Slice(failures);
		checkSb3Menu(failures);
		checkStopAll(failures);
		checkSb3Scope(failures);
		checkProcedures(failures);
	}

	/**
	 * sb3 垂直切片：内存里构造一个最小 Scratch 项目，验证「zip → 积木树 → 编译 → 动作调用」。
	 *
	 * <p>样例脚本：绿旗 → 重复 2 次 → 如果 (1 &lt; 2) → 挖掘。</p>
	 */
	/**
	 * 输入槽的三种写法都必须解析成同一块积木。
	 *
	 * <p>Scratch 的标准格式是 {@code [3, shadow, blockId]}，但实际遇到过
	 * {@code [3, blockId, shadow]} 和 {@code [3, 内联shadow, 内联块]} 两种变体。
	 * 修复前只认第 2 位的块 id：标准格式的第三个元素被完全忽略，脚本里的表达式会静默变成默认值。</p>
	 */
	static void checkInputOrder(List<String> failures) {
		List<ScriptDefinition> parsed;
		try {
			parsed = Sb3Parser.parseScripts(buildInputOrderSb3());
		} catch (Exception e) {
			failures.add("输入槽样例解析失败: " + e);
			return;
		}
		if (parsed.isEmpty()) {
			failures.add("输入槽样例未解析出脚本");
			return;
		}
		BlockNode hat = parsed.get(0).hat;

		// 1) 标准格式：[3, shadow, blockId]
		BlockNode standard = SelfCheck.findBlock(hat, "control.repeat");
		if (standard == null) {
			failures.add("输入槽样例缺少 control.repeat");
			return;
		}
		Slot standardTimes = standard.inputs.get("TIMES");
		if (standardTimes == null || !standardTimes.hasBlock()
				|| !"list.length".equals(standardTimes.block.type)) {
			failures.add("标准顺序 [3, shadow, blockId] 的块被丢弃，实际 "
					+ describeSlot(standardTimes));
		}

		// 2) 反向格式：[3, blockId, shadow]
		BlockNode reversed = SelfCheck.findBlock(hat, "operator.join");
		if (reversed == null) {
			failures.add("输入槽样例缺少 operator.join");
			return;
		}
		Slot joinA = reversed.inputs.get("A");
		if (joinA == null || !joinA.hasBlock() || !"list.length".equals(joinA.block.type)) {
			failures.add("反向顺序 [3, blockId, shadow] 的块被丢弃，实际 " + describeSlot(joinA));
		}

		// 3) 纯内联 shadow（无块）：字面量照常落位
		BlockNode wait = SelfCheck.findBlock(hat, "control.wait");
		if (wait == null) {
			failures.add("输入槽样例缺少 control.wait");
			return;
		}
		Slot ticks = wait.inputs.get("TICKS");
		double tickValue = ticks == null || ticks.hasBlock() || ticks.literal == null
				? -1d
				: Values.asNumber(Values.parseLiteral(ticks.literal));
		if (Math.abs(tickValue - 30d) > 0.001) {
			failures.add("内联 shadow 的 1.5 秒应换算成 30 tick，实际 " + describeSlot(ticks));
		}
	}

	private static String describeSlot(Slot slot) {
		if (slot == null) {
			return "缺失";
		}
		return slot.hasBlock() ? "block:" + slot.block.type : "literal:" + slot.literal;
	}


	private static void collectByType(BlockNode node, String type, List<BlockNode> out) {
		if (node == null) {
			return;
		}
		if (type.equals(node.type)) {
			out.add(node);
		}
		collectByType(node.next, type, out);
		for (BlockNode branch : node.branches.values()) {
			collectByType(branch, type, out);
		}
		for (Slot slot : node.inputs.values()) {
			if (slot.hasBlock()) {
				collectByType(slot.block, type, out);
			}
		}
	}

	/**
	 * 一个脚本里塞进三种输入槽写法，用来验证解析器不依赖元素顺序。
	 */
	static byte[] buildInputOrderSb3() {
		return SelfCheck.zipProject("""
				{
				  "targets": [
				    { "isStage": true, "name": "Stage", "blocks": {} },
				    {
				      "isStage": false,
				      "name": "S",
				      "blocks": {
				        "hat": { "opcode": "event_whenflagclicked", "next": "r1", "parent": null,
				                 "inputs": {}, "fields": {}, "topLevel": true, "shadow": false, "x": 0, "y": 0 },
				        "r1": { "opcode": "control_repeat", "next": "j1", "parent": "hat",
				                "inputs": { "TIMES": [3, [6, "10"], "len1"], "SUBSTACK": [2, "w1"] },
				                "fields": {} },
				        "len1": { "opcode": "data_lengthoflist", "next": null, "parent": "r1",
				                  "inputs": {}, "fields": { "LIST": ["列表", "listid"] } },
				        "w1": { "opcode": "control_wait", "next": null, "parent": "r1",
				                "inputs": { "DURATION": [1, [5, "1.5"]] }, "fields": {} },
				        "j1": { "opcode": "operator_join", "next": "w2", "parent": "r1",
				                "inputs": { "STRING1": [3, "len2", [10, "x"]], "STRING2": [1, [10, "y"]] },
				                "fields": {} },
				        "len2": { "opcode": "data_lengthoflist", "next": null, "parent": "j1",
				                  "inputs": {}, "fields": { "LIST": ["列表", "listid"] } },
				        "w2": { "opcode": "control_wait", "next": null, "parent": "j1",
				                "inputs": { "DURATION": [1, [5, "1.5"]] }, "fields": {} }
				      }
				    }
				  ]
				}
				""");
	}

	static void checkSb3Slice(List<String> failures) {
		byte[] sb3 = buildSampleSb3();
		List<ScriptDefinition> parsed;
		try {
			parsed = Sb3Parser.parseScripts(sb3);
		} catch (Exception e) {
			failures.add("sb3 解析失败: " + e);
			return;
		}

		if (parsed.size() != 1) {
			failures.add("sb3 应解析出 1 个脚本，实际 " + parsed.size());
			return;
		}
		ScriptDefinition definition = parsed.get(0);
		if (!"event.tick".equals(definition.hat.type)) {
			failures.add("sb3 帽子积木应映射为 event.tick，实际 " + definition.hat.type);
			return;
		}

		CompiledScript compiled;
		try {
			compiled = Compiler.compile(definition.name, definition.hat, definition.functions);
		} catch (Exception e) {
			failures.add("sb3 脚本编译失败: " + e);
			return;
		}

		boolean hasClick = compiled.code().stream()
				.anyMatch(instr -> instr.op == Op.CALL_ACTION && "action.click".equals(instr.s));
		if (!hasClick) {
			failures.add("sb3 脚本未编译出 action.click 调用，指令=" + compiled.code());
		}

		// Scratch 的「等待 2 秒」必须换算成 40 tick
		boolean hasWaitTicks = compiled.code().stream()
				.anyMatch(instr -> instr.op == Op.PUSH_CONST
						&& instr.value instanceof Number number && number.doubleValue() == 40d);
		if (!hasWaitTicks) {
			failures.add("sb3 的 wait 2 秒应换算为 40 tick，指令=" + compiled.code());
		}
	}

	/**
	 * 扩展积木的参数落位：内部声明为字段的参数（如按键名）应落到 fields，
	 * 声明为输入的参数（如按下/松开）应落到 inputs。
	 */
	static void checkSb3Menu(List<String> failures) {
		List<ScriptDefinition> parsed;
		try {
			parsed = Sb3Parser.parseScripts(buildMenuSb3());
		} catch (Exception e) {
			failures.add("sb3 菜单解析失败: " + e);
			return;
		}
		if (parsed.isEmpty()) {
			failures.add("sb3 菜单样例未解析出脚本");
			return;
		}

		BlockNode key = SelfCheck.findBlock(parsed.get(0).hat, "action.key");
		if (key == null) {
			failures.add("sb3 参数样例中未找到 action.key");
			return;
		}
		// 按键名是表达式时必须留在 inputs 里，否则编译期会读字段拿到默认值
		Slot name = key.inputs.get("KEY");
		if (name == null || name.block == null) {
			failures.add("表达式形式的按键名未落到 inputs.KEY，fields=" + key.fields
					+ " inputs=" + key.inputs.keySet());
		}
		// Scratch 叫 STATE，内部叫 PRESSED，必须改名
		Slot pressed = key.inputs.get("PRESSED");
		if (pressed == null || !"true".equals(pressed.literal)) {
			failures.add("STATE 应改名为 PRESSED=true，实际 "
					+ (pressed == null ? "缺失，inputs=" + key.inputs.keySet() : pressed.literal));
		}
		if (key.inputs.containsKey("STATE")) {
			failures.add("STATE 未改名，仍是 STATE");
		}

		// 编译后必须真的调用 operator.length，而不是用丢弃后的默认键名
		CompiledScript compiled = Compiler.compile("selfcheck.keyexpr", parsed.get(0).hat,
				parsed.get(0).functions, parsed.get(0).scopeId);
		boolean evaluated = compiled.code().stream().anyMatch(instr ->
				instr.op == Op.CALL_BUILTIN && "operator.length".equals(instr.s));
		if (!evaluated) {
			failures.add("表达式形式的按键名未被编译，指令=" + compiled.code());
		}

		// ITEM 只对列表积木改名为 VALUE；扩展的 itemCount 参数本身就叫 ITEM
		if (!"VALUE".equals(Sb3Mapping.inputName("data_addtolist", "ITEM"))) {
			failures.add("列表积木的 ITEM 应改名为 VALUE");
		}
		if (!"ITEM".equals(Sb3Mapping.inputName("hkscript_itemCount", "ITEM"))) {
			failures.add("itemCount 的 ITEM 不应被改名");
		}
	}

	/**
	 * 「停止全部」：Scratch 用一条「停止」积木加下拉选项表达三种语义，
	 * 内部拆成「停止脚本」与「停止全部」。这条检查同时覆盖编译、执行与解析三段。
	 */
	static void checkStopAll(List<String> failures) {
		CompiledScript compiled = Compiler.compile("selfcheck.stopall",
				BlockNode.of("event.tick").then(BlockNode.of("control.stop_all")));
		if (compiled.code().stream().noneMatch(instr -> instr.op == Op.STOP_ALL)) {
			failures.add("control.stop_all 未编译出 STOP_ALL，指令=" + compiled.code());
		}

		ScriptContext ctx = new ScriptContext(new Object());
		ScriptInstance instance = new ScriptInstance(compiled);
		for (int i = 0; i < 8 && !instance.isDone(); i++) {
			instance.step(50, 0, ctx);
		}
		if (!ctx.stopAllRequested()) {
			failures.add("执行「停止全部」后未向引擎发出停机请求");
		}

		try {
			List<ScriptDefinition> parsed = Sb3Parser.parseScripts(buildStopAllSb3());
			if (parsed.isEmpty()) {
				failures.add("sb3 停止样例未解析出脚本");
				return;
			}
			BlockNode stop = SelfCheck.findBlock(parsed.get(0).hat, "control.stop_all");
			if (stop == null) {
				failures.add("sb3 的 STOP_OPTION=all 未映射为 control.stop_all");
			} else if (!stop.fields.isEmpty()) {
				failures.add("停止积木不应保留参数，实际 " + stop.fields);
			}
		} catch (Exception e) {
			failures.add("sb3 停止积木解析失败: " + e);
		}
	}

	static byte[] buildStopAllSb3() {
		return SelfCheck.zipProject("""
				{
				  "targets": [
				    { "isStage": true, "name": "Stage", "blocks": {} },
				    {
				      "isStage": false,
				      "name": "S",
				      "blocks": {
				        "hat": { "opcode": "event_whenflagclicked", "next": "stop", "parent": null,
				                 "inputs": {}, "fields": {}, "topLevel": true, "shadow": false, "x": 0, "y": 0 },
				        "stop": { "opcode": "control_stop", "next": null, "parent": "hat",
				                  "inputs": {}, "fields": { "STOP_OPTION": ["all", null] },
				                  "mutation": { "tagName": "mutation", "children": [], "hasnext": "false" } }
				      }
				    }
				  ]
				}
				""");
	}

	/**
	 * 变量作用域与不断链：同一角色内同名变量、不同角色同名变量、未映射积木、内联原语。
	 */
	static void checkSb3Scope(List<String> failures) {
		List<ScriptDefinition> parsed;
		try {
			parsed = Sb3Parser.parseScripts(buildScopeSb3());
		} catch (Exception e) {
			failures.add("sb3 作用域样例解析失败: " + e);
			return;
		}
		if (parsed.size() != 3) {
			failures.add("sb3 作用域样例应解析出 3 个脚本，实际 " + parsed.size());
			return;
		}

		ScriptDefinition stage = byName(parsed, "Stage#1");
		ScriptDefinition sprite1 = byName(parsed, "Sprite1#1");
		ScriptDefinition sprite2 = byName(parsed, "Sprite2#1");
		if (stage == null || sprite1 == null || sprite2 == null) {
			failures.add("sb3 作用域样例的脚本名不符：" + parsed.stream()
					.map(definition -> definition.name).toList());
			return;
		}

		// Stage 上的变量是全局
		checkVariable(failures, stage, "score", "global", "Stage 变量");
		// 角色变量用「角色名/变量名」，不同角色的同名变量互不干扰
		checkVariable(failures, sprite1, "Sprite1/score", "target", "Sprite1 变量");
		checkVariable(failures, sprite2, "Sprite2/score", "target", "Sprite2 变量");

		if (!"Sprite1".equals(sprite1.scopeId)) {
			failures.add("角色作用域标识应为 Sprite1，实际 " + sprite1.scopeId);
		}

		// 内联变量 [12, 名字, ID] 必须与字段走同一套作用域
		BlockNode inline = SelfCheck.findBlock(sprite1.hat, "data.get");
		if (inline == null) {
			failures.add("内联变量未被还原成 data.get");
		} else if (!"Sprite1/score".equals(inline.fields.get("VAR"))
				|| !"target".equals(inline.fields.get("SCOPE"))) {
			failures.add("内联变量作用域错：VAR=" + inline.fields.get("VAR")
					+ " SCOPE=" + inline.fields.get("SCOPE"));
		}

		// 内联列表 [13, 名字, ID] 应还原成「列表内容」
		BlockNode contents = SelfCheck.findBlock(sprite1.hat, "list.contents");
		if (contents == null) {
			failures.add("内联列表未被还原成 list.contents");
		} else if (!"Sprite1/items".equals(contents.fields.get("LIST"))) {
			failures.add("内联列表名应带角色前缀，实际 " + contents.fields.get("LIST"));
		}

		// 未映射积木不得截断后续链
		BlockNode placeholder = SelfCheck.findBlock(sprite1.hat, "control.nop");
		if (placeholder == null) {
			failures.add("未映射积木未生成占位（control.nop）");
		} else if (!"motion_movesteps".equals(placeholder.fields.get("SKIPPED"))) {
			failures.add("占位积木应记录原 opcode，实际 " + placeholder.fields.get("SKIPPED"));
		}
		if (SelfCheck.findBlock(sprite1.hat, "action.click") == null) {
			failures.add("未映射积木之后的积木被截断（找不到 action.click）");
		}
	}

	static void checkVariable(List<String> failures, ScriptDefinition definition,
									  String expectedName, String expectedScope, String label) {
		BlockNode set = SelfCheck.findBlock(definition.hat, "data.set");
		if (set == null) {
			failures.add(label + "：未找到变量赋值积木");
			return;
		}
		if (!expectedName.equals(set.fields.get("VAR"))) {
			failures.add(label + "：VAR 应为 " + expectedName + "，实际 " + set.fields.get("VAR"));
		}
		if (!expectedScope.equals(set.fields.get("SCOPE"))) {
			failures.add(label + "：SCOPE 应为 " + expectedScope + "，实际 " + set.fields.get("SCOPE"));
		}
	}

	static ScriptDefinition byName(List<ScriptDefinition> scripts, String name) {
		for (ScriptDefinition definition : scripts) {
			if (name.equals(definition.name)) {
				return definition;
			}
		}
		return null;
	}

	/**
	 * 三个 target 的样例：Stage 一个全局变量、Sprite1 一个同名角色变量 + 未映射积木 + 内联原语、
	 * Sprite2 一个同名角色变量。
	 */
	static byte[] buildScopeSb3() {
		return SelfCheck.zipProject("""
				{
				  "targets": [
				    { "isStage": true, "name": "Stage",
				      "variables": { "vid-stage": ["score", 0] },
				      "blocks": {
				        "hat": { "opcode": "event_whenflagclicked", "next": "set", "parent": null,
				                 "inputs": {}, "fields": {}, "topLevel": true, "shadow": false, "x": 0, "y": 0 },
				        "set": { "opcode": "data_setvariableto", "next": null, "parent": "hat",
				                 "inputs": { "VALUE": [1, [4, "1"]] },
				                 "fields": { "VARIABLE": ["score", "vid-stage"] } }
				      } },
				    { "isStage": false, "name": "Sprite1",
				      "variables": { "vid-s1": ["score", 0] },
				      "lists": { "vid-l1": ["items", []] },
				      "blocks": {
				        "hat": { "opcode": "event_whenflagclicked", "next": "set1", "parent": null,
				                 "inputs": {}, "fields": {}, "topLevel": true, "shadow": false, "x": 0, "y": 0 },
				        "set1": { "opcode": "data_setvariableto", "next": "say", "parent": "hat",
				                  "inputs": { "VALUE": [1, [12, "score", "vid-s1"]] },
				                  "fields": { "VARIABLE": ["score", "vid-s1"] } },
				        "say": { "opcode": "motion_movesteps", "next": "set2", "parent": "set1",
				                 "inputs": { "STEPS": [1, [4, "10"]] }, "fields": {} },
				        "set2": { "opcode": "data_setvariableto", "next": "mine", "parent": "say",
				                  "inputs": { "VALUE": [1, [13, "items", "vid-l1"]] },
				                  "fields": { "VARIABLE": ["score", "vid-s1"] } },
				        "mine": { "opcode": "hkscript_click", "next": null, "parent": "set2",
				                  "inputs": {}, "fields": {} }
				      } },
				    { "isStage": false, "name": "Sprite2",
				      "variables": { "vid-s2": ["score", 0] },
				      "blocks": {
				        "hat": { "opcode": "event_whenflagclicked", "next": "set2", "parent": null,
				                 "inputs": {}, "fields": {}, "topLevel": true, "shadow": false, "x": 0, "y": 0 },
				        "set2": { "opcode": "data_setvariableto", "next": null, "parent": "hat",
				                  "inputs": { "VALUE": [1, [4, "2"]] },
				                  "fields": { "VARIABLE": ["score", "vid-s2"] } }
				      } }
				  ]
				}
				""");
	}

	static byte[] buildSaySb3() {
		return SelfCheck.zipProject("""
				{
				  "targets": [
				    { "isStage": true, "name": "Stage", "blocks": {} },
				    {
				      "isStage": false,
				      "name": "S",
				      "blocks": {
				        "hat": { "opcode": "event_whenflagclicked", "next": "say", "parent": null,
				                 "inputs": {}, "fields": {}, "topLevel": true, "shadow": false, "x": 0, "y": 0 },
				        "say": { "opcode": "looks_sayforsecs", "next": null, "parent": "hat",
				                 "inputs": { "MESSAGE": [1, [10, "hi"]], "SECS": [1, [4, "2"]] },
				                 "fields": {} }
				      }
				    }
				  ]
				}
				""");
	}

	static byte[] buildMenuSb3() {
		return SelfCheck.zipProject("""
				{
				  "targets": [
				    { "isStage": true, "name": "Stage", "blocks": {} },
				    {
				      "isStage": false,
				      "name": "S",
				      "blocks": {
				        "hat": { "opcode": "event_whenflagclicked", "next": "mv", "parent": null,
				                 "inputs": {}, "fields": {}, "topLevel": true, "shadow": false, "x": 0, "y": 0 },
				        "mv": { "opcode": "hkscript_key", "next": null, "parent": "hat",
				                "inputs": { "KEY": [3, "kb", [10, "w"]], "STATE": [1, [10, "true"]] },
				                "fields": {} },
				        "kb": { "opcode": "operator_length", "next": null, "parent": "mv",
				                "inputs": { "STRING": [1, [10, "苹果"]] }, "fields": {} }
				      }
				    }
				  ]
				}
				""");
	}

	/**
	 * 手工构造最小 sb3（zip 内含 project.json），结构与 Scratch 导出一致。
	 */
	static byte[] buildSampleSb3() {
		String json = """
				{
				  "targets": [
				    { "isStage": true, "name": "Stage", "blocks": {} },
				    {
				      "isStage": false,
				      "name": "Sprite1",
				      "blocks": {
				        "hat": { "opcode": "event_whenflagclicked", "next": "rep", "parent": null,
				                 "inputs": {}, "fields": {}, "topLevel": true, "shadow": false, "x": 0, "y": 0 },
				        "rep": { "opcode": "control_repeat", "next": "wait", "parent": "hat",
				                 "inputs": { "TIMES": [1, [6, "2"]], "SUBSTACK": [2, "cond"] }, "fields": {} },
				        "cond": { "opcode": "control_if", "next": null, "parent": "rep",
				                  "inputs": { "CONDITION": [2, "lt"], "SUBSTACK": [2, "mine"] }, "fields": {} },
				        "wait": { "opcode": "control_wait", "next": null, "parent": "rep",
				                  "inputs": { "DURATION": [1, [5, "2"]] }, "fields": {} },
				        "lt": { "opcode": "operator_lt", "next": null, "parent": "cond",
				                "inputs": { "OPERAND1": [1, [4, "1"]], "OPERAND2": [1, [4, "2"]] }, "fields": {} },
				        "mine": { "opcode": "hkscript_click", "next": null, "parent": "cond",
				                  "inputs": {}, "fields": {} }
				      }
				    }
				  ]
				}
				""";

		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
			 ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("project.json"));
			zip.write(json.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.finish();
			return out.toByteArray();
		} catch (IOException e) {
			return new byte[0];
		}
	}

	/**
	 * 自定义积木：定义、参数读取、调用与编译。
	 */
	static void checkProcedures(List<String> failures) {
		List<ScriptDefinition> parsed;
		try {
			parsed = Sb3Parser.parseScripts(buildProcedureSb3());
		} catch (Exception e) {
			failures.add("sb3 自定义积木样例解析失败: " + e);
			return;
		}
		if (parsed.isEmpty()) {
			failures.add("sb3 自定义积木样例未解析出脚本");
			return;
		}
		ScriptDefinition script = parsed.get(0);
		if (script.functions.isEmpty()) {
			failures.add("自定义积木定义未被导入（procedures_definition 被丢弃）");
			return;
		}
		String proccode = script.functions.get(0).name;
		if (proccode == null || !proccode.startsWith("挖")) {
			failures.add("自定义积木名应取 proccode，实际 " + proccode);
		}

		BlockNode param = SelfCheck.findBlock(script.functions.get(0).body, "data.get");
		if (param == null) {
			failures.add("函数体内的参数读取未还原成 data.get");
		} else if (!"$p1".equals(param.fields.get("VAR"))) {
			failures.add("参数读取应映射为 $p1，实际 " + param.fields.get("VAR"));
		}

		BlockNode call = SelfCheck.findBlock(script.hat, "func.call");
		if (call == null) {
			failures.add("procedures_call 未还原成 func.call");
		} else {
			if (proccode != null && !proccode.equals(call.fields.get("NAME"))) {
				failures.add("调用名与定义名不一致: " + call.fields.get("NAME"));
			}
			if (!"1".equals(call.fields.get("ARGC"))) {
				failures.add("调用参数个数应为 1，实际 " + call.fields.get("ARGC"));
			}
		}

		CompiledScript compiled = Compiler.compile(script.name, script.hat,
				script.functions, script.scopeId);
		if (compiled.code().stream().noneMatch(instr -> instr.op == Op.CALL_FUNC)) {
			failures.add("自定义积木调用未编译出 CALL_FUNC，指令=" + compiled.code());
		}
	}

	static byte[] buildProcedureSb3() {
		return SelfCheck.zipProject("""
				{
				  "targets": [
				    { "isStage": true, "name": "Stage", "blocks": {} },
				    {
				      "isStage": false,
				      "name": "S",
				      "blocks": {
				        "hat": { "opcode": "event_whenflagclicked", "next": "call", "parent": null,
				                 "inputs": {}, "fields": {}, "topLevel": true, "shadow": false, "x": 0, "y": 0 },
				        "call": { "opcode": "procedures_call", "next": null, "parent": "hat",
				                  "inputs": { "arg1": [1, [4, "2"]] }, "fields": {},
				                  "mutation": { "tagName": "mutation", "children": [],
				                                "proccode": "挖 %s 下",
				                                "argumentids": "[\\\"arg1\\\"]",
				                                "warp": "false" } },
				        "def": { "opcode": "procedures_definition", "next": "body", "parent": null,
				                 "inputs": { "custom_block": [1, "proto"] }, "fields": {},
				                 "topLevel": true, "shadow": false, "x": 0, "y": 400 },
				        "proto": { "opcode": "procedures_prototype", "next": null, "parent": "def",
				                   "inputs": { "arg1": [1, "argProto"] }, "fields": {}, "shadow": true,
				                   "mutation": { "tagName": "mutation", "children": [],
				                                 "proccode": "挖 %s 下",
				                                 "argumentids": "[\\\"arg1\\\"]",
				                                 "argumentnames": "[\\\"次数\\\"]",
				                                 "argumentdefaults": "[\\\"\\\"]",
				                                 "warp": "false" } },
				        "argProto": { "opcode": "argument_reporter_string_number",
				                      "inputs": {}, "fields": { "VALUE": ["次数", null] } },
				        "body": { "opcode": "hkscript_log", "next": null, "parent": "def",
				                  "inputs": { "TEXT": [2, "argUse"] }, "fields": {} },
				        "argUse": { "opcode": "argument_reporter_string_number", "next": null,
				                    "parent": "body", "inputs": {},
				                    "fields": { "VALUE": ["次数", null] } }
				      }
				    }
				  ]
				}
				""");
	}
}
