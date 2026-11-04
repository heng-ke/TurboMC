package xyz.hegke.client.script;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.hegke.client.script.action.ListFunctions;
import xyz.hegke.client.script.action.NativeRegistry;
import xyz.hegke.client.config.HkScriptConfig;
import xyz.hegke.client.script.block.BlockParam;
import xyz.hegke.client.script.block.BlockRegistry;
import xyz.hegke.client.script.block.BlockType;
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
import xyz.hegke.client.script.store.FunctionDefinition;
import xyz.hegke.client.script.store.ScriptStore;
import xyz.hegke.client.script.ui.NodeChain;
import xyz.hegke.client.script.ui.WaitUnit;

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
 * 启动自检入口：编译器与解释器的基础正确性（跳转、原生引用、循环、列表、spawn、函数、预算）。
 *
 * <p>解析层断言在 {@link SelfCheckSb3}，语义与调度断言在 {@link SelfCheckRuntime}。
 * 三个类都只在启动时跑一次，失败即停用脚本引擎——宁可不能用，也不要带着已知错误跑。</p>
 */
public final class SelfCheck {
	private static final Logger LOG = LoggerFactory.getLogger("hk-script/script");

	/**
	 * 嵌套表达式在编辑器里必须能读出结构，不能折叠成 {@code …}。
	 *
	 * <p>参数进入后能否看懂逻辑，全靠 {@link NodeChain#summary} 把子积木内联开。
	 * 层数被调小或模板写错，表现是「打开参数只看到一个省略号」，不会报任何错。</p>
	 */
	static void checkNestedSummary(List<String> failures) {
		BlockNode variable = BlockNode.of("data.get").field("VAR", "i");
		BlockNode letter = BlockNode.of("operator.letter_of")
				.input("A", variable)
				.input("B", "abc");
		BlockNode length = BlockNode.of("operator.length").input("A", letter);

		String summary = NodeChain.summary(length, "self");
		if (summary.contains("…")) {
			failures.add("三层嵌套被截断成省略号：" + summary);
		}
		if (!summary.contains("abc")) {
			failures.add("嵌套最内层的字面量丢失：" + summary);
		}
		if (!summary.contains("i")) {
			failures.add("嵌套里的变量名丢失：" + summary);
		}
	}

	/**
	 * 等待时长的单位换算：秒 ↔ tick 必须完全可逆。
	 *
	 * <p>不可逆的后果很直接——打开参数屏点一下「确定」，1 秒就变成 0.05 秒（因为写回了 1 tick）。
	 * 这类漂移不会报错，只会让脚本「某天开始变得特别快」。</p>
	 */
	static void checkWaitUnit(List<String> failures) {
		HkScriptConfig config = HkScriptConfig.get();
		Boolean original = config.waitInSeconds;
		try {
			// ---- 秒模式 ----
			config.waitInSeconds = true;
			expectEquals(failures, "秒模式 input(20)", "1", WaitUnit.input("20"));
			expectEquals(failures, "秒模式 input(1)", "0.05", WaitUnit.input("1"));
			expectEquals(failures, "秒模式 input(21)", "1.05", WaitUnit.input("21"));
			expectEquals(failures, "秒模式 parseTicks(1)", "20", WaitUnit.parseTicks("1"));
			expectEquals(failures, "秒模式 parseTicks(0.05)", "1", WaitUnit.parseTicks("0.05"));

			// 往返无损：秒的两位小数足以精确表示任一整数 tick
			for (String ticks : new String[]{"1", "2", "7", "20", "21", "60", "100", "1234"}) {
				String back = WaitUnit.parseTicks(WaitUnit.input(ticks));
				expectEquals(failures, "tick " + ticks + " 往返", ticks, back);
			}

			// ---- tick 模式 ----
			config.waitInSeconds = false;
			expectEquals(failures, "tick 模式 input(20)", "20", WaitUnit.input("20"));
			expectEquals(failures, "tick 模式 input(20.5)", "20.5", WaitUnit.input("20.5"));
			expectEquals(failures, "tick 模式 parseTicks(20)", "20", WaitUnit.parseTicks("20"));

			// ---- 非法输入必须拒绝，由调用方保留原值 ----
			if (WaitUnit.parseTicks("abc") != null || WaitUnit.parseTicks("") != null
					|| WaitUnit.parseTicks(null) != null) {
				failures.add("非法输入应返回 null，否则会把错误的数字写回脚本");
			}
			expectEquals(failures, "负时长钳制", "0", WaitUnit.parseTicks("-5"));

			// 渲染文本必须带上当前单位
			config.waitInSeconds = true;
			if (!WaitUnit.format("20").startsWith("1")) {
				failures.add("秒模式下 20 tick 应显示为 1 秒，实际 " + WaitUnit.format("20"));
			}

			// 整条渲染链路：模板 + 取值 + 换算。模板里若残留硬编码单位，这里会抓到
			BlockNode wait = BlockNode.of("control.wait").input("TICKS", "20");
			String seconds = NodeChain.summary(wait, "self");
			if (seconds.contains("tick")) {
				failures.add("秒模式下编辑器仍渲染出 tick：" + seconds);
			}
			if (!seconds.contains("1")) {
				failures.add("秒模式下应渲染出 1：" + seconds);
			}

			config.waitInSeconds = false;
			String ticks = NodeChain.summary(wait, "self");
			if (!ticks.contains("20")) {
				failures.add("tick 模式下应渲染出 20：" + ticks);
			}

			// 只有时间类积木的 TICKS 参与换算
			if (!WaitUnit.isWaitParam("control.wait", "TICKS")) {
				failures.add("control.wait.TICKS 应识别为时间参数");
			}
			if (WaitUnit.isWaitParam("operator.add", "TICKS")) {
				failures.add("operator.add.TICKS 不应被当作时间参数");
			}
		} finally {
			config.waitInSeconds = original;
		}
	}

	private static void expectEquals(List<String> failures, String what, String expected, String actual) {
		if (!expected.equals(actual)) {
			failures.add(what + " 应为 " + expected + "，实际 " + actual);
		}
	}

	/**
	 * 积木 ↔ 指令区间：报错里的 pc 必须能反查到具体积木。
	 *
	 * <p>没有这条，运行面板显示 {@code pc=16}、日志写 {@code @pc=37} 都无从定位。</p>
	 */
	static void checkPcMapping(List<String> failures) {
		BlockNode wait = BlockNode.of("control.wait").input("TICKS", "1");
		BlockNode hat = BlockNode.of("event.tick").then(wait);
		CompiledScript compiled = Compiler.compile("selfcheck.pc", hat, List.of(), "self");

		int[] range = compiled.rangeOf(wait);
		if (range == null) {
			failures.add("积木未记录指令区间");
			return;
		}
		if (range[0] < 0 || range[1] >= compiled.size() || range[0] > range[1]) {
			failures.add("指令区间越界: " + range[0] + "-" + range[1] + "，共 " + compiled.size() + " 条");
		}
		if (compiled.blockAt(range[0]) == null) {
			failures.add("pc " + range[0] + " 反查不到积木");
		}

		// 嵌套时应取最内层：外层的区间包住内层
		BlockNode inner = BlockNode.of("operator.length").input("A", "abc");
		BlockNode repeat = BlockNode.of("control.repeat")
				.input("TIMES", inner)
				.branch("SUBSTACK", BlockNode.of("control.wait").input("TICKS", "1"));
		CompiledScript nested = Compiler.compile("selfcheck.pc2",
				BlockNode.of("event.tick").then(repeat), List.of(), "self");
		int[] innerRange = nested.rangeOf(inner);
		if (innerRange == null) {
			failures.add("嵌套表达式未记录指令区间");
			return;
		}
		BlockNode resolved = nested.blockAt(innerRange[0]);
		if (resolved != inner) {
			failures.add("嵌套反查应取最内层积木，实际 "
					+ (resolved == null ? "null" : resolved.type));
		}

		// 执行高亮取的是 pc-1（刚执行完的那条），必须落回该积木自己的最后一条指令
		BlockNode next = BlockNode.of("debug.log").input("TEXT", "x");
		BlockNode waitWithNext = BlockNode.of("control.wait").input("TICKS", "1").then(next);
		CompiledScript chain = Compiler.compile("selfcheck.pc3",
				BlockNode.of("event.tick").then(waitWithNext), List.of(), "self");
		int[] waitRange = chain.rangeOf(waitWithNext);
		if (waitRange == null) {
			failures.add("等待积木未记录指令区间");
			return;
		}
		if (chain.blockAt(waitRange[1]) != waitWithNext) {
			failures.add("pc-1 反查应命中刚执行完的积木，实际 "
					+ describe(chain.blockAt(waitRange[1])));
		}
		if (chain.blockAt(waitRange[1] + 1) != next) {
			failures.add("pc 反查应命中下一块积木，实际 "
					+ describe(chain.blockAt(waitRange[1] + 1)));
		}

		// 越界 pc 必须安全返回 null，否则编辑器每帧渲染会抛异常
		if (chain.blockAt(-5) != null || chain.blockAt(chain.size() + 10) != null) {
			failures.add("越界 pc 应反查为 null");
		}

		// 未运行时不得返回任何执行中积木
		if (!ScriptEngine.get().runningBlocks("selfcheck.never").isEmpty()) {
			failures.add("引擎未运行时 runningBlocks 应返回空集");
		}
	}

	private static String describe(BlockNode node) {
		return node == null ? "null" : node.type;
	}

	/**
	 * 参数规格：哪些字段允许被表达式替换（对应 Scratch 的 acceptReporters）。
	 *
	 * <p>标错的后果：该接表达式的参数只读字段 → 表达式被丢弃；不该接的标成可接 →
	 * 编辑器给出无效入口。两种都要靠这条断言挡住。</p>
	 */
	static void checkParamSpecs(List<String> failures) {
		// 扩展积木的文本参数，在 TurboWarp 里可以接任意 reporter
		expectAcceptsReporter(failures, "action.key", "KEY", true);
		expectAcceptsReporter(failures, "event.chat", "MATCH", true);
		// 原生积木的真字段，Scratch 本身也不允许接块
		expectAcceptsReporter(failures, "data.set", "VAR", false);
		expectAcceptsReporter(failures, "sensing.current", "UNIT", false);
		expectAcceptsReporter(failures, "operator.mathop", "OP", false);
	}

	private static void expectAcceptsReporter(List<String> failures, String blockType,
											  String paramName, boolean expected) {
		BlockType type = BlockRegistry.get(blockType);
		if (type == null) {
			failures.add("积木未注册: " + blockType);
			return;
		}
		BlockParam spec = type.param(paramName);
		if (spec == null) {
			failures.add("参数未定义: " + blockType + "." + paramName);
			return;
		}
		if (spec.acceptsReporter() != expected) {
			failures.add(blockType + "." + paramName + " 的 acceptsReporter 应为 " + expected);
		}
	}

	/**
	 * HAT 积木的文本参数：字段 → 字面量输入 → 表达式（返回 null 表示不匹配）。
	 *
	 * <p>表达式不能退化成空串，否则「当聊天包含 [变量]」会变成「任意聊天都触发」。</p>
	 */
	static void checkHatParams(List<String> failures) {
		ScriptEngine engine = ScriptEngine.get();

		BlockNode byField = BlockNode.of("event.chat").field("MATCH", "hello");
		if (!"hello".equals(engine.hatText(byField, "MATCH"))) {
			failures.add("HAT 参数应优先读字段");
		}

		BlockNode byLiteral = BlockNode.of("event.chat").input("MATCH", "字面量");
		if (!"字面量".equals(engine.hatText(byLiteral, "MATCH"))) {
			failures.add("HAT 参数应回退到字面量输入，实际 " + engine.hatText(byLiteral, "MATCH"));
		}

		// 用自检专用类型名，这条告警出现在日志里时能一眼看出是测试触发的
		BlockNode byBlock = BlockNode.of("selfcheck.hat")
				.input("MATCH", BlockNode.of("data.get").field("VAR", "x"));
		String fromBlock = engine.hatText(byBlock, "MATCH");
		if (fromBlock != null) {
			failures.add("表达式形式的 HAT 参数应返回 null（按不匹配处理），实际 \"" + fromBlock + "\"");
		}
	}

	/**
	 * 调用未定义的自定义积木：必须编译失败。
	 *
	 * <p>修复前跳转目标保持默认值 0，脚本会跳回开头重跑，动作被重复执行。</p>
	 */
	static void checkUndefinedFunction(List<String> failures) {
		BlockNode hat = BlockNode.of("event.tick")
				.then(BlockNode.of("func.call")
						.field("NAME", "不存在的积木")
						.field("ARGC", "1")
						.input("P1", "1"));
		try {
			Compiler.compile("selfcheck.undef", hat, List.of(), "self");
			failures.add("调用了未定义的自定义积木，却编译通过了（会跳回开头重跑）");
		} catch (IllegalStateException expected) {
			// 期望：编译期拒绝
		} catch (RuntimeException other) {
			failures.add("未定义函数应抛 IllegalStateException，实际 " + other.getClass().getSimpleName());
		}
	}

	/**
	 * 存储层：显示名归一到文件名后可能撞车，判重必须按文件名而不是显示名。
	 */
	static void checkFileNameCollision(List<String> failures) {
		if (!ScriptStore.sameFileName("挖矿 1", "挖矿_1")) {
			failures.add("文件名判重未覆盖归一化冲突：「挖矿 1」与「挖矿_1」应视为同一文件");
		}
		if (ScriptStore.sameFileName("挖矿", "挖矿2")) {
			failures.add("不同脚本名被误判为文件名冲突");
		}
	}

	/**
	 * 跑完全部断言。任一失败即返回 false，引擎会据此停用。
	 */
	public static boolean run() {
		List<String> failures = new ArrayList<>();

		CompiledScript demo = Compiler.compile("selfcheck.demo", DemoScripts.mineWalk());
		checkJumpTargets(demo, failures);
		checkNativeReferences(demo, failures);
		checkExtendedBlocks(failures);
		checkLoopCounting(failures);
		checkRepeatUntil(failures);
		checkWaitUntil(failures);
		checkLists(failures);
		checkSpawn(failures);
		checkFunctions(failures);
		checkIfElse(failures);
		checkWaitSuspension(failures);
		checkBudget(failures);
		checkFileNameCollision(failures);
		checkUndefinedFunction(failures);
		checkHatParams(failures);
		checkParamSpecs(failures);
		checkPcMapping(failures);
		checkWaitUnit(failures);
		checkNestedSummary(failures);

		SelfCheckSb3.run(failures);
		SelfCheckRuntime.run(failures);

		if (failures.isEmpty()) {
			LOG.info("[script] 自检通过：34 项检查全部正常");
			return true;
		}
		for (String failure : failures) {
			LOG.error("[script] 自检失败：{}", failure);
		}
		return false;
	}

	static void checkJumpTargets(CompiledScript script, List<String> failures) {
		for (int i = 0; i < script.size(); i++) {
			Instr instr = script.code().get(i);
			if (instr.op == Op.JUMP || instr.op == Op.JUMP_IF_FALSE || instr.op == Op.LOOP
					|| instr.op == Op.SPAWN) {
				if (instr.a < 0 || instr.a >= script.size()) {
					failures.add("指令 #" + i + " 的跳转目标越界: " + instr.a);
				}
			}
		}
	}

	static void checkNativeReferences(CompiledScript script, List<String> failures) {
		for (Instr instr : script.code()) {
			if (instr.op == Op.CALL_ACTION && NativeRegistry.getAction(instr.s) == null) {
				failures.add("引用了未注册的动作: " + instr.s);
			}
			if (instr.op == Op.CALL_BUILTIN && NativeRegistry.getFunction(instr.s) == null) {
				failures.add("引用了未注册的函数: " + instr.s);
			}
		}
	}

	/**
	 * 扩展积木的编译冒烟测试：把战斗与视角类积木串成一份脚本，验证编译不抛异常、
	 * 跳转目标合法、引用的动作与函数均已注册。
	 */
	static void checkExtendedBlocks(List<String> failures) {
		BlockNode script = BlockNode.of("event.tick")
				.then(BlockNode.of("control.if")
						.input("CONDITION", BlockNode.of("operator.lt")
								.input("A", BlockNode.of("world.health"))
								.input("B", "10"))
						.branch("SUBSTACK", BlockNode.of("action.click").field("BUTTON", "right")))
				.then(BlockNode.of("action.click").field("BUTTON", "left"))
				.then(BlockNode.of("action.attack_nearest"))
				.then(BlockNode.of("action.attack_nearest_hostile"))
				.then(BlockNode.of("action.open_container"))
				.then(BlockNode.of("action.move_toward").input("X", "0").input("Z", "0"))
				.then(BlockNode.of("debug.log").input("TEXT", BlockNode.of("world.nearest_entity_x")))
				.then(BlockNode.of("debug.log").input("TEXT", BlockNode.of("world.nearest_hostile")))
				.then(BlockNode.of("control.if")
						.input("CONDITION", BlockNode.of("world.on_ground"))
						.branch("SUBSTACK", BlockNode.of("action.stop")))
				.then(BlockNode.of("action.look").input("YAW", "90").input("PITCH", "-10"))
				.then(BlockNode.of("action.look_at").input("X", "0").input("Y", "64").input("Z", "0"))
				.then(BlockNode.of("action.tap").field("KEY", "2").input("HOLD", "1"))
				.then(BlockNode.of("action.stop"))
				.then(BlockNode.of("debug.log")
						.input("TEXT", BlockNode.of("world.item_count").input("ITEM", "minecraft:stone")));

		CompiledScript compiled;
		try {
			compiled = Compiler.compile("selfcheck.extended", script);
		} catch (Exception e) {
			failures.add("扩展积木编译失败: " + e);
			return;
		}
		checkJumpTargets(compiled, failures);
		checkNativeReferences(compiled, failures);
	}

	static void checkLoopCounting(List<String> failures) {		BlockNode repeat = BlockNode.of("control.repeat")
				.input("TIMES", "3")
				.branch("SUBSTACK", BlockNode.of("data.set")
						.field("VAR", "$t")
						.input("VALUE", BlockNode.of("operator.add")
								.input("A", BlockNode.of("data.get").field("VAR", "$t"))
								.input("B", "1")));
		BlockNode body = BlockNode.of("data.set").field("VAR", "$t").input("VALUE", "0");
		body.then(repeat);
		BlockNode hat = BlockNode.of("event.tick").then(body);

		ScriptInstance instance = new ScriptInstance(Compiler.compile("selfcheck.loop", hat));
		ScriptContext context = new ScriptContext(null);
		runToCompletion(instance, context);

		if (instance.state() != ScriptInstance.State.FINISHED) {
			failures.add("循环脚本未正常结束: " + instance.state() + " " + instance.error());
			return;
		}
		double counter = Values.asNumber(instance.local("$t"));
		if (counter != 3d) {
			failures.add("repeat 3 次后计数应为 3，实际为 " + Values.asString(counter));
		}
	}

	/**
	 * repeat until：条件成立时退出。计数到 3 应停止且值为 3。
	 */
	static void checkRepeatUntil(List<String> failures) {
		BlockNode counter = BlockNode.of("data.set").field("VAR", "$u").input("VALUE", "0");
		BlockNode loop = BlockNode.of("control.repeat_until")
				.input("CONDITION", BlockNode.of("operator.gte")
						.input("A", BlockNode.of("data.get").field("VAR", "$u"))
						.input("B", "3"))
				.branch("SUBSTACK", BlockNode.of("data.set")
						.field("VAR", "$u")
						.input("VALUE", BlockNode.of("operator.add")
								.input("A", BlockNode.of("data.get").field("VAR", "$u"))
								.input("B", "1")));
		counter.then(loop);
		BlockNode hat = BlockNode.of("event.tick").then(counter);

		ScriptInstance instance = new ScriptInstance(Compiler.compile("selfcheck.until", hat));
		runToCompletion(instance, new ScriptContext(null));

		if (instance.state() != ScriptInstance.State.FINISHED) {
			failures.add("repeat until 未正常结束: " + instance.state() + " " + instance.error());
			return;
		}
		double value = Values.asNumber(instance.local("$u"));
		if (value != 3d) {
			failures.add("repeat until 应在计数 3 时退出，实际 " + Values.asString(value));
		}
	}

	/**
	 * wait until：条件已满足时应立即继续，不挂起。
	 */
	static void checkWaitUntil(List<String> failures) {
		BlockNode wait = BlockNode.of("control.wait_until")
				.input("CONDITION", BlockNode.of("operator.gt").input("A", "1").input("B", "0"));
		wait.then(BlockNode.of("data.set").field("VAR", "$w").input("VALUE", "1"));
		BlockNode hat = BlockNode.of("event.tick").then(wait);

		ScriptInstance instance = new ScriptInstance(Compiler.compile("selfcheck.until_wait", hat));
		instance.step(100, 0, new ScriptContext(null));

		if (instance.state() != ScriptInstance.State.FINISHED) {
			failures.add("wait until 条件已满足时应立即继续，实际状态 " + instance.state());
		}
		if (Values.asNumber(instance.local("$w")) != 1d) {
			failures.add("wait until 之后的语句未执行");
		}
	}

	/**
	 * 列表：追加三项后长度应为 3，第 2 项应为 20。
	 */
	static void checkLists(List<String> failures) {
		BlockNode script = BlockNode.of("list.clear").input("LIST", "$l");
		script.then(BlockNode.of("list.add").input("LIST", "$l").input("VALUE", "10"));
		script.then(BlockNode.of("list.add").input("LIST", "$l").input("VALUE", "20"));
		script.then(BlockNode.of("list.add").input("LIST", "$l").input("VALUE", "30"));
		script.then(BlockNode.of("data.set").field("VAR", "$ln")
				.input("VALUE", BlockNode.of("list.length").input("LIST", "$l")));
		script.then(BlockNode.of("data.set").field("VAR", "$li")
				.input("VALUE", BlockNode.of("list.item").input("LIST", "$l").input("INDEX", "2")));
		BlockNode hat = BlockNode.of("event.tick").then(script);

		ScriptInstance instance = new ScriptInstance(Compiler.compile("selfcheck.list", hat));
		runToCompletion(instance, new ScriptContext(null));

		if (instance.state() != ScriptInstance.State.FINISHED) {
			failures.add("列表脚本未正常结束: " + instance.state() + " " + instance.error());
			return;
		}
		if (Values.asNumber(instance.local("$ln")) != 3d) {
			failures.add("列表长度应为 3，实际 " + Values.asString(instance.local("$ln")));
		}
		if (Values.asNumber(instance.local("$li")) != 20d) {
			failures.add("列表第 2 项应为 20，实际 " + Values.asString(instance.local("$li")));
		}
	}

	/**
	 * spawn：主流程不阻塞，分支被编译到主流程之后，由子实例执行。
	 */
	static void checkSpawn(List<String> failures) {
		GlobalVariables.clear();
		BlockNode script = BlockNode.of("data.set")
				.field("VAR", "$gs").field("SCOPE", "global").input("VALUE", "0");
		script.then(BlockNode.of("control.spawn").branch("SUBSTACK",
				BlockNode.of("data.set").field("VAR", "$gs").field("SCOPE", "global").input("VALUE", "1")));
		BlockNode hat = BlockNode.of("event.tick").then(script);

		CompiledScript compiled = Compiler.compile("selfcheck.spawn", hat);
		ScriptContext context = new ScriptContext(null);

		ScriptInstance instance = new ScriptInstance(compiled);
		instance.step(100, 0, context);
		List<Integer> spawned = instance.drainSpawns();

		if (spawned.size() != 1) {
			failures.add("spawn 应派生 1 个子实例，实际 " + spawned.size());
			GlobalVariables.clear();
			return;
		}
		if (Values.asNumber(GlobalVariables.get("$gs")) != 0d) {
			failures.add("spawn 不应阻塞主流程，主流程结束时全局值应仍为 0，实际 "
					+ Values.asString(GlobalVariables.get("$gs")) + " 指令=" + compiled.code());
		}
		ScriptInstance child = new ScriptInstance(compiled, spawned.get(0));
		child.step(100, 1, context);
		if (Values.asNumber(GlobalVariables.get("$gs")) != 1d) {
			failures.add("spawn 子实例未执行分支内的全局赋值");
		}
		GlobalVariables.clear();
	}

	/**
	 * 自定义积木：调用时传入 $p1，函数体累加到全局 $sum。
	 */
	static void checkFunctions(List<String> failures) {
		GlobalVariables.clear();

		FunctionDefinition adder = FunctionDefinition.create("adder");
		adder.body = BlockNode.of("data.set")
				.field("VAR", "$sum").field("SCOPE", "global")
				.input("VALUE", BlockNode.of("operator.add")
						.input("A", BlockNode.of("data.get").field("VAR", "$sum").field("SCOPE", "global"))
						.input("B", BlockNode.of("data.get").field("VAR", "$p1")));

		BlockNode script = BlockNode.of("data.set")
				.field("VAR", "$sum").field("SCOPE", "global").input("VALUE", "0");
		script.then(BlockNode.of("func.call")
				.field("NAME", "adder")
				.input("P1", "5")
				.input("P2", "")
				.input("P3", ""));
		BlockNode hat = BlockNode.of("event.tick").then(script);

		CompiledScript compiled = Compiler.compile("selfcheck.func", hat, List.of(adder));
		ScriptInstance instance = new ScriptInstance(compiled);
		runToCompletion(instance, new ScriptContext(null));

		if (instance.state() != ScriptInstance.State.FINISHED) {
			failures.add("自定义积木脚本未正常结束: " + instance.state() + " " + instance.error()
					+ " 指令=" + compiled.code());
			GlobalVariables.clear();
			return;
		}
		double sum = Values.asNumber(GlobalVariables.get("$sum"));
		if (sum != 5d) {
			failures.add("自定义积木未生效，$sum 应为 5，实际 " + Values.asString(sum)
					+ " 指令=" + compiled.code());
		}
		GlobalVariables.clear();
	}

	static Object callFunction(ScriptContext ctx, String id, Object... args) {
		NativeRegistry.Function function = NativeRegistry.getFunction(id);
		return function == null ? null : function.apply(ctx, args);
	}

	/**
	 * if / else 两个方向都要验证：条件真走 then，条件假走 else。
	 */
	static void checkIfElse(List<String> failures) {
		if (!branchResult(1, 0, 1d)) {
			failures.add("if/else 条件为真时应执行 then 分支");
		}
		if (!branchResult(1, 2, 2d)) {
			failures.add("if/else 条件为假时应执行 else 分支");
		}
		GlobalVariables.clear();
	}

	static boolean branchResult(double a, double b, double expected) {
		GlobalVariables.clear();
		BlockNode node = BlockNode.of("control.if_else")
				.input("CONDITION", BlockNode.of("operator.gt").input("A", String.valueOf(a)).input("B", String.valueOf(b)))
				.branch("SUBSTACK", BlockNode.of("data.set")
						.field("VAR", "$r").field("SCOPE", "global").input("VALUE", "1"))
				.branch("SUBSTACK2", BlockNode.of("data.set")
						.field("VAR", "$r").field("SCOPE", "global").input("VALUE", "2"));
		BlockNode hat = BlockNode.of("event.tick").then(node);

		ScriptInstance instance = new ScriptInstance(Compiler.compile("selfcheck.if_else", hat));
		runToCompletion(instance, new ScriptContext(null));
		return Values.asNumber(GlobalVariables.get("$r")) == expected;
	}

	static void checkWaitSuspension(List<String> failures) {		BlockNode hat = BlockNode.of("event.tick")
				.then(BlockNode.of("control.wait").input("TICKS", "5"));
		ScriptInstance instance = new ScriptInstance(Compiler.compile("selfcheck.wait", hat));
		ScriptContext context = new ScriptContext(null);

		instance.step(100, 0, context);
		if (!instance.isWaiting()) {
			failures.add("wait 5 tick 后应进入挂起状态，实际为 " + instance.state());
			return;
		}
		int consumed = instance.step(100, 1, context);
		if (consumed != 0) {
			failures.add("挂起期间不应消耗指令，实际消耗 " + consumed);
		}
		instance.step(100, 5, context);
		if (!instance.isDone()) {
			failures.add("到达唤醒 tick 后应结束，实际为 " + instance.state());
		}
	}

	static void checkBudget(List<String> failures) {
		BlockNode hat = BlockNode.of("event.tick").then(BlockNode.of("control.forever"));
		ScriptInstance instance = new ScriptInstance(Compiler.compile("selfcheck.budget", hat));
		ScriptContext context = new ScriptContext(null);

		int consumed = instance.step(50, 0, context);
		if (consumed != 50) {
			failures.add("空循环应恰好耗尽预算 50，实际消耗 " + consumed);
		}
		if (instance.state() != ScriptInstance.State.RUNNING) {
			failures.add("预算耗尽后应保持运行态，实际为 " + instance.state());
		}
	}

	static BlockNode findBlock(BlockNode node, String type) {
		BlockNode cursor = node;
		while (cursor != null) {
			if (type.equals(cursor.type)) {
				return cursor;
			}
			for (BlockNode branch : cursor.branches.values()) {
				BlockNode found = findBlock(branch, type);
				if (found != null) {
					return found;
				}
			}
			for (Slot slot : cursor.inputs.values()) {
				if (slot.hasBlock()) {
					BlockNode found = findBlock(slot.block, type);
					if (found != null) {
						return found;
					}
				}
			}
			cursor = cursor.next;
		}
		return null;
	}

	static byte[] zipProject(String json) {
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

	static void runToCompletion(ScriptInstance instance, ScriptContext context) {
		int tick = 0;
		while (!instance.isDone() && tick < 200) {
			instance.step(1000, tick++, context);
		}
	}
}
