package xyz.hegke.client.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.hegke.client.script.action.GameActions;
import xyz.hegke.client.script.action.ListFunctions;
import xyz.hegke.client.script.action.ScriptKeys;
import xyz.hegke.client.script.action.NativeRegistry;
import xyz.hegke.client.config.KeyNames;
import xyz.hegke.client.script.block.BlockRegistry;
import xyz.hegke.client.script.block.BlockShape;
import xyz.hegke.client.script.block.BlockType;
import xyz.hegke.client.script.ir.CompiledScript;
import xyz.hegke.client.script.ir.Compiler;
import xyz.hegke.client.script.ir.Instr;
import xyz.hegke.client.script.ir.Op;
import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.node.Slot;
import xyz.hegke.client.script.runtime.GlobalVariables;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
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
 * 运行时语义自检：类型转换与比较、调度与预算、循环节流、感知、广播等待、世界查询。
 */
public final class SelfCheckRuntime {
	/**
	 * 按键归属：实例结束时必须收回自己按下的键，多个实例按同一个键时用引用计数。
	 *
	 * <p>修复前实例结束只被移出调度器，按键仍处在按下状态，角色会一直移动。</p>
	 */
	static void checkKeyOwnership(List<String> failures) {
		KeyBinding key = new KeyBinding("key.hk-script.selfcheck",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, KeyBinding.Category.MISC);

		Object owner = new Object();
		ScriptKeys.hold(owner, key, -1);
		if (!ScriptKeys.isForced(key)) {
			failures.add("按下后按键未被脚本接管");
		}
		ScriptKeys.releaseAll(owner);
		if (ScriptKeys.isForced(key) || key.isPressed()) {
			failures.add("实例结束后按键未被收回（会导致角色一直移动）");
		}

		// 两个实例按同一个键：释放其中一个不应松开
		Object first = new Object();
		Object second = new Object();
		ScriptKeys.hold(first, key, -1);
		ScriptKeys.hold(second, key, -1);
		ScriptKeys.releaseAll(first);
		if (!ScriptKeys.isForced(key)) {
			failures.add("引用计数错误：仍有实例按着却被松开");
		}
		ScriptKeys.releaseAll(second);
		if (ScriptKeys.isForced(key)) {
			failures.add("全部释放后按键仍未收回");
		}

		// 限时按键到点自动松开
		Object timed = new Object();
		ScriptKeys.hold(timed, key, 1);
		ScriptKeys.tick();
		if (ScriptKeys.isForced(key)) {
			failures.add("限时按键到期后未自动松开");
		}
		ScriptKeys.releaseAll(timed);
	}

	/**
	 * 语义、调度与游戏查询相关的全部断言。
	 */
	static void run(List<String> failures) {
		checkKeyOwnership(failures);
		checkScratchSemantics(failures);
		checkScheduling(failures);
		checkLoopThrottle(failures);
		checkSensing(failures);
		checkBroadcastWait(failures);
		checkWorldQueries(failures);
		checkBuildActions(failures);
		checkMiningWait(failures);
	}

	/**
	 * 挖破等待：没有宿主时必须立刻放弃，否则离开世界时脚本会永久挂起；
	 * 判定为真时必须执行内部代码，且等待期间要逐 tick 重新询问。
	 */
	static void checkMiningWait(List<String> failures) {
		BlockNode hat = BlockNode.of("event.tick")
				.then(BlockNode.of("action.break_wait").input("TICKS", "20")
						.branch("SUBSTACK", BlockNode.of("data.set")
								.field("VAR", "$broke").input("VALUE", "1")));
		CompiledScript compiled = Compiler.compile("selfcheck.breakwait", hat);
		if (compiled.code().stream().noneMatch(instr -> instr.op == Op.WAIT_BREAK)) {
			failures.add("等待挖破未编译出 WAIT_BREAK，指令=" + compiled.code());
			return;
		}

		// 没有宿主（未进世界）时，默认探针返回「放弃」，脚本应直接跳过内部代码
		ScriptInstance noHost = new ScriptInstance(compiled);
		SelfCheck.runToCompletion(noHost, new ScriptContext(null));
		if (noHost.state() != ScriptInstance.State.FINISHED) {
			failures.add("无宿主时等待挖破应立即放弃，实际 " + noHost.state() + " " + noHost.error());
		} else if (Values.asNumber(noHost.local("$broke")) != 0d) {
			failures.add("未判定为挖破时不应执行内部代码");
		}

		// 前两次返回等待、第三次返回已破：既验证逐 tick 重试，也验证成功分支
		ScriptContext slow = new ScriptContext(null);
		int[] probes = {0};
		slow.setBreakProbe((context, state) -> new ScriptContext.BreakResult(state,
				++probes[0] < 3 ? ScriptContext.BreakResult.WAITING : ScriptContext.BreakResult.BROKEN));
		ScriptInstance hit = new ScriptInstance(compiled);
		SelfCheck.runToCompletion(hit, slow);
		if (hit.state() != ScriptInstance.State.FINISHED) {
			failures.add("判定为挖破后脚本未结束: " + hit.state() + " " + hit.error());
		} else if (Values.asNumber(hit.local("$broke")) != 1d) {
			failures.add("判定为挖破后内部代码应执行一次");
		}
		if (probes[0] < 3) {
			failures.add("等待期间应逐 tick 重新询问，实际只询问了 " + probes[0] + " 次");
		}
	}

	/**
	 * 语义层：比较、取模、随机、数学函数、布尔转换、列表下标与 repeat 取整。
	 *
	 * <p>这一组断言直接对应审计报告里的 P0-3 与 P1-1/2/3/4/5/8。</p>
	 */
	static void checkScratchSemantics(List<String> failures) {
		// 比较：文本按字典序，数字串按数值
		if (ScratchCompare.compare("apple", "banana") >= 0) {
			failures.add("compare 应认为 apple < banana");
		}
		if (ScratchCompare.compare("A", "a") != 0) {
			failures.add("compare 的文本比较应忽略大小写");
		}
		if (ScratchCompare.compare("石头", "泥土") == 0) {
			failures.add("compare 对两个无关文本不应返回相等");
		}
		if (ScratchCompare.compare("10", "9") <= 0) {
			failures.add("compare 对两个数字串应按数值比较（10 > 9）");
		}

		// 取模：Scratch 用向下取整
		if (ScratchMath.mod(-3d, 5d) != 2d) {
			failures.add("mod(-3, 5) 应为 2，实际 " + ScratchMath.mod(-3d, 5d));
		}
		if (ScratchMath.mod(7d, 5d) != 2d) {
			failures.add("mod(7, 5) 应为 2");
		}

		// 随机：浮点区间必须产生非端点值，且不越界
		boolean fractional = false;
		for (int i = 0; i < 200; i++) {
			double value = ScratchMath.random(0.5d, 1.5d);
			if (value < 0.5d || value >= 1.5d) {
				failures.add("random(0.5, 1.5) 越界: " + value);
				break;
			}
			if (value != 0.5d && value != 1.5d) {
				fractional = true;
			}
		}
		if (!fractional) {
			failures.add("random 在浮点区间应产生非端点值");
		}

		// 数学函数
		if (ScratchMath.mathop("abs", -3d) != 3d || ScratchMath.mathop("sqrt", 9d) != 3d) {
			failures.add("mathop 的 abs/sqrt 结果不正确");
		}
		if (Math.abs(ScratchMath.mathop("sin", 180d)) > 1e-9) {
			failures.add("mathop 的 sin(180) 应为 0，实际 " + ScratchMath.mathop("sin", 180d));
		}

		// 布尔转换不 trim：带空格的 " false " 在 Scratch 里为真
		if (!ScratchCast.toBoolean(" false ")) {
			failures.add("toBoolean 不应 trim，\" false \" 应为真");
		}
		if (ScratchCast.toBoolean("") || ScratchCast.toBoolean("0")) {
			failures.add("toBoolean 对空串与 \"0\" 应为假");
		}

		// 列表下标
		if (ScratchMath.listIndex("last", 3, false) != 3) {
			failures.add("listIndex(\"last\") 应取末项");
		}
		if (ScratchMath.listIndex(2.9d, 3, false) != 2) {
			failures.add("listIndex 应向下取整");
		}
		if (ScratchMath.listIndex(0d, 3, false) != ScratchMath.LIST_INVALID) {
			failures.add("listIndex(0) 应判为非法");
		}
		if (ScratchMath.listIndex("all", 3, true) != ScratchMath.LIST_ALL) {
			failures.add("listIndex(\"all\") 在允许时应返回 LIST_ALL");
		}
		if (ScratchMath.listIndex("all", 3, false) != ScratchMath.LIST_INVALID) {
			failures.add("listIndex(\"all\") 在不允许时应判为非法");
		}

		// 字符串积木
		ScriptContext ctx = new ScriptContext(null);
		if (!"b".equals(SelfCheck.callFunction(ctx, "operator.letter_of", 2d, "abc"))) {
			failures.add("letter_of(2, abc) 应为 b");
		}
		if (Values.asNumber(SelfCheck.callFunction(ctx, "operator.length", "abc")) != 3d) {
			failures.add("length(abc) 应为 3");
		}
		if (!Boolean.TRUE.equals(SelfCheck.callFunction(ctx, "operator.contains", "ABC", "bc"))) {
			failures.add("contains 应忽略大小写");
		}

		// repeat 取整：2.1 次只跑 2 轮
		BlockNode repeat = BlockNode.of("control.repeat")
				.input("TIMES", "2.1")
				.branch("SUBSTACK", BlockNode.of("data.set")
						.field("VAR", "$r2")
						.input("VALUE", BlockNode.of("operator.add")
								.input("A", BlockNode.of("data.get").field("VAR", "$r2"))
								.input("B", "1")));
		BlockNode body = BlockNode.of("data.set").field("VAR", "$r2").input("VALUE", "0");
		body.then(repeat);
		ScriptInstance instance = new ScriptInstance(
				Compiler.compile("selfcheck.round", BlockNode.of("event.tick").then(body)));
		SelfCheck.runToCompletion(instance, ctx);
		double rounds = Values.asNumber(instance.local("$r2"));
		if (rounds != 2d) {
			failures.add("repeat 2.1 次应跑 2 轮，实际 " + Values.asString(rounds));
		}
	}

	/**
	 * 调度层：触发策略、按名终止、公平预算（防止排在后面的实例被饿死）。
	 */
	static void checkScheduling(List<String> failures) {
		if (TriggerPolicy.of("event.broadcast") != TriggerPolicy.RESTART) {
			failures.add("广播触发应为重启策略");
		}
		if (TriggerPolicy.of("event.key") != TriggerPolicy.ABANDON) {
			failures.add("按键触发应为放弃策略");
		}
		if (TriggerPolicy.of("event.damage") != TriggerPolicy.CONCURRENT) {
			failures.add("受伤触发应允许并发");
		}

		// 同名实例可被一次性终止
		BlockNode spin = BlockNode.of("event.tick").then(
				BlockNode.of("control.forever"));
		CompiledScript script = Compiler.compile("selfcheck.kill", spin);
		ScriptScheduler scheduler = new ScriptScheduler();
		for (int i = 0; i < 3; i++) {
			scheduler.start(script);
		}
		if (scheduler.countByName("selfcheck.kill") != 3) {
			failures.add("按名计数应为 3，实际 " + scheduler.countByName("selfcheck.kill"));
		}
		scheduler.killByName("selfcheck.kill");
		if (scheduler.countByName("selfcheck.kill") != 0) {
			failures.add("killByName 后仍有实例存活");
		}

		// 在 tick 遍历期间新建实例（spawn / 广播都会走这条路径）不得抛并发修改异常
		BlockNode spawnHat = BlockNode.of("event.tick").then(
				BlockNode.of("control.spawn").branch("SUBSTACK", BlockNode.of("control.forever")));
		ScriptScheduler spawnScheduler = new ScriptScheduler();
		spawnScheduler.start(Compiler.compile("selfcheck.spawn_sched", spawnHat));
		spawnScheduler.tick(new ScriptContext(null));
		if (spawnScheduler.activeCount() < 1) {
			failures.add("spawn 派生实例未加入调度（可能触发并发修改异常），活动数="
					+ spawnScheduler.activeCount());
		}

		// 公平预算：12 个实例同时跑长循环，末位实例也必须在本 tick 有推进
		BlockNode counterLoop = BlockNode.of("control.repeat")
				.input("TIMES", "400")
				.branch("SUBSTACK", BlockNode.of("data.set")
						.field("VAR", "$c")
						.input("VALUE", BlockNode.of("operator.add")
								.input("A", BlockNode.of("data.get").field("VAR", "$c"))
								.input("B", "1")));
		BlockNode seed = BlockNode.of("data.set").field("VAR", "$c").input("VALUE", "0");
		seed.then(counterLoop);
		CompiledScript heavy = Compiler.compile("selfcheck.fair",
				BlockNode.of("event.tick").then(seed));

		ScriptScheduler fair = new ScriptScheduler();
		for (int i = 0; i < 12; i++) {
			fair.start(heavy);
		}
		fair.tick(new ScriptContext(null));
		List<ScriptInstance> running = fair.instances();
		if (running.isEmpty()) {
			failures.add("公平预算：12 个实例不应在单 tick 内全部结束");
		} else {
			ScriptInstance last = running.get(running.size() - 1);
			if (Values.asNumber(last.local("$c")) <= 0d) {
				failures.add("公平预算：排在末位的实例本 tick 未获得任何配额（饥饿）");
			}
		}
	}

	/**
	 * 循环节流：关闭时一个 tick 内跑完，开启时每轮消耗一个 tick。
	 */
	static void checkLoopThrottle(List<String> failures) {
		BlockNode repeat = BlockNode.of("control.repeat")
				.input("TIMES", "3")
				.branch("SUBSTACK", BlockNode.of("data.set")
						.field("VAR", "$t3")
						.input("VALUE", BlockNode.of("operator.add")
								.input("A", BlockNode.of("data.get").field("VAR", "$t3"))
								.input("B", "1")));
		BlockNode body = BlockNode.of("data.set").field("VAR", "$t3").input("VALUE", "0");
		body.then(repeat);
		BlockNode hat = BlockNode.of("event.tick").then(body);

		int fast = ticksToFinish(Compiler.compile("selfcheck.fast", hat, List.of(), "", false));
		int slow = ticksToFinish(Compiler.compile("selfcheck.slow", hat, List.of(), "", true));

		if (fast != 1) {
			failures.add("未节流时 repeat 3 应在 1 个 tick 内完成，实际 " + fast);
		}
		if (slow < 3) {
			failures.add("节流后 repeat 3 至少需要 3 个 tick，实际 " + slow);
		}
	}

	static int ticksToFinish(CompiledScript script) {
		ScriptInstance instance = new ScriptInstance(script);
		ScriptContext ctx = new ScriptContext(null);
		int tick = 0;
		while (!instance.isDone() && tick < 50) {
			tick++;
			instance.step(2000, tick, ctx);
		}
		return instance.isDone() ? tick : -1;
	}

	/**
	 * 感知与输出：按键查询、计时器、当前时间、「说」以及 say-for-secs 的秒换算。
	 */
	static void checkSensing(List<String> failures) {
		CompiledScript script = Compiler.compile("selfcheck.sensing",
				BlockNode.of("event.tick")
						.then(BlockNode.of("debug.say").input("TEXT", "hi"))
						.then(BlockNode.of("debug.say_for").input("TEXT", "hi").input("TICKS", "20"))
						.then(BlockNode.of("input.key_pressed").field("KEY", "space"))
						.then(BlockNode.of("sensing.current").field("UNIT", "year")));

		if (script.code().stream().noneMatch(instr -> instr.op == Op.CALL_ACTION
				&& "debug.say".equals(instr.s))) {
			failures.add("「说」未编译出 debug.say 调用");
		}
		if (script.code().stream().noneMatch(instr -> instr.op == Op.CALL_BUILTIN
				&& "input.key_pressed".equals(instr.s))) {
			failures.add("按键查询未编译出 input.key_pressed");
		}
		if (script.code().stream().noneMatch(instr -> instr.op == Op.CALL_BUILTIN
				&& "sensing.current".equals(instr.s))) {
			failures.add("当前时间项未编译出 sensing.current");
		}

		// 时钟：计时器非负、年份合理、2000 年至今的天数合理
		if (ScratchClock.timer() < 0d) {
			failures.add("计时器读数不应为负");
		}
		if (ScratchClock.current("year") < 2024d) {
			failures.add("当前年份异常: " + ScratchClock.current("year"));
		}
		if (ScratchClock.daysSince2000() < 9000d) {
			failures.add("2000 年至今的天数异常: " + ScratchClock.daysSince2000());
		}

		// 「说 2 秒」必须换算成 40 tick
		try {
			List<ScriptDefinition> parsed = Sb3Parser.parseScripts(SelfCheckSb3.buildSaySb3());
			if (parsed.isEmpty()) {
				failures.add("sb3 「说 n 秒」样例未解析出脚本");
				return;
			}
			BlockNode say = SelfCheck.findBlock(parsed.get(0).hat, "debug.say_for");
			if (say == null) {
				failures.add("looks_sayforsecs 未映射为 debug.say_for");
				return;
			}
			Slot ticks = say.inputs.get("TICKS");
			if (ticks == null || !"40.0".equals(ticks.literal)) {
				failures.add("「说 2 秒」应换算成 40 tick，实际 "
						+ (ticks == null ? "缺失" : ticks.literal));
			}
		} catch (Exception e) {
			failures.add("sb3 「说 n 秒」样例解析失败: " + e);
		}
	}

	/**
	 * 广播并等待：无接收者直接继续；有接收者则挂起，直到对方结束（或被强制终止）。
	 */
	static void checkBroadcastWait(List<String> failures) {
		CompiledScript waiterScript = Compiler.compile("selfcheck.bw",
				BlockNode.of("event.tick").then(
						BlockNode.of("control.broadcast_wait").input("MSG", "go")));
		if (waiterScript.code().stream().noneMatch(instr -> instr.op == Op.BROADCAST_WAIT)) {
			failures.add("广播并等待未编译出 BROADCAST_WAIT");
			return;
		}
		// 消息名以输入槽形式压栈，必须紧挨在 BROADCAST_WAIT 之前
		int waitIndex = -1;
		for (int i = 0; i < waiterScript.code().size(); i++) {
			if (waiterScript.code().get(i).op == Op.BROADCAST_WAIT) {
				waitIndex = i;
				break;
			}
		}
		if (waitIndex <= 0 || !"go".equals(waiterScript.code().get(waitIndex - 1).value)) {
			failures.add("广播并等待的消息名未正确压栈，指令=" + waiterScript.code());
		}

		CompiledScript receiverScript = Compiler.compile("selfcheck.bw.recv",
				BlockNode.of("event.tick").then(
						BlockNode.of("control.wait").input("TICKS", "100")));
		ScriptInstance waiter = new ScriptInstance(waiterScript);
		ScriptInstance receiver = new ScriptInstance(receiverScript);
		ScriptContext ctx = new ScriptContext(null);
		ctx.setBroadcaster(message -> "go".equals(message) ? List.of(receiver) : List.of());

		for (int tick = 1; tick <= 3; tick++) {
			waiter.step(200, tick, ctx);
			receiver.step(200, tick, ctx);
		}
		if (waiter.isDone() || waiter.state() != ScriptInstance.State.WAITING) {
			failures.add("广播并等待应在接收者结束前保持挂起，实际 " + waiter.state());
		}

		// 被等待的实例被强制终止后必须能唤醒等待方，否则会死锁
		receiver.kill();
		for (int tick = 4; tick <= 12 && !waiter.isDone(); tick++) {
			waiter.step(200, tick, ctx);
		}
		if (!waiter.isDone()) {
			failures.add("接收者结束后「广播并等待」未恢复执行");
		}
	}

	/**
	 * 列表编号查询与物品/方块查询积木的注册、编译与执行。
	 */
	static void checkWorldQueries(List<String> failures) {
		String[] functions = {
				"world.held_item", "world.held_durability", "world.front_block",
				"world.block_below", "world.block_at", "world.is_day", "world.dimension",
				"world.in_water", "world.in_lava", "world.nearest_entity_name",
				"world.xp_level", "world.inventory_full", "list.item_num"
		};
		for (String id : functions) {
			if (NativeRegistry.getFunction(id) == null) {
				failures.add("查询积木未注册: " + id);
			}
		}

		// 坐标查询必须压入三个参数
		CompiledScript compiled = Compiler.compile("selfcheck.world",
				BlockNode.of("event.tick")
						.then(BlockNode.of("world.block_at")
								.input("X", "1").input("Y", "2").input("Z", "3"))
						.then(BlockNode.of("action.tap").field("KEY", "q").input("HOLD", "1")));
		boolean blockAt = compiled.code().stream().anyMatch(instr ->
				instr.op == Op.CALL_BUILTIN && "world.block_at".equals(instr.s) && instr.a == 3);
		if (!blockAt) {
			failures.add("坐标处方块未按 3 个参数编译，指令=" + compiled.code());
		}
		if (compiled.code().stream().noneMatch(instr ->
				instr.op == Op.CALL_ACTION && "action.tap".equals(instr.s) && instr.a == 2)) {
			failures.add("点按未编译出 action.tap 调用");
		}

		// 列表编号：命中返回 1 起算的位置，未命中返回 0
		BlockNode hat = BlockNode.of("event.tick")
				.then(BlockNode.of("list.add").input("LIST", "selfcheck_list").input("VALUE", "alpha"))
				.then(BlockNode.of("list.add").input("LIST", "selfcheck_list").input("VALUE", "beta"))
				.then(BlockNode.of("data.set").field("VAR", "$pos").input("VALUE",
						BlockNode.of("list.item_num")
								.input("LIST", "selfcheck_list").input("VALUE", "beta")))
				.then(BlockNode.of("data.set").field("VAR", "$miss").input("VALUE",
						BlockNode.of("list.item_num")
								.input("LIST", "selfcheck_list").input("VALUE", "zzz")));
		ScriptInstance instance = new ScriptInstance(Compiler.compile("selfcheck.listnum", hat));
		SelfCheck.runToCompletion(instance, new ScriptContext(null));
		ListFunctions.clearAll();

		if (instance.state() != ScriptInstance.State.FINISHED) {
			failures.add("列表编号脚本未正常结束: " + instance.error());
			return;
		}
		if (Values.asNumber(instance.local("$pos")) != 2d) {
			failures.add("列表中第一个 beta 的编号应为 2，实际 "
					+ Values.asString(instance.local("$pos")));
		}
		if (Values.asNumber(instance.local("$miss")) != 0d) {
			failures.add("查不到时应返回 0，实际 " + Values.asString(instance.local("$miss")));
		}
	}

	/**
	 * 建造与界面类积木：注册完整性 + 参数个数。
	 */
	static void checkBuildActions(List<String> failures) {
		String[] actions = {"action.sleep", "action.place_block", "action.key",
				"action.tap", "action.chat", "action.command", "action.respawn",
				"action.mouse", "action.click"};
		for (String id : actions) {
			if (NativeRegistry.getAction(id) == null) {
				failures.add("建造/界面动作未注册: " + id);
			}
		}
		// 打开背包只能靠「点按 e」，所以 E 必须放行；聊天/命令仍要拦（会抢走键盘焦点）
		if (GameActions.isBlockedName("e")) {
			failures.add("E 键（打开背包）不应被屏蔽，否则脚本无法开背包");
		}
		if (!GameActions.isBlockedName("t")) {
			failures.add("T 键应被屏蔽，否则脚本会弹出聊天输入框");
		}
		// 键名归一化：大小写、空格、全名与别名都要认成同一个键
		if (!GameActions.isBlockedName("ESC")) {
			failures.add("ESC 应归一化为 escape 后命中黑名单");
		}
		if (GameActions.isBlockedName("left shift")) {
			failures.add("left shift 不在默认黑名单里，不应被拦截");
		}

		String[] functions = {"world.entity_count", "world.biome", "world.ping"};
		for (String id : functions) {
			if (NativeRegistry.getFunction(id) == null) {
				failures.add("扩展查询未注册: " + id);
			}
		}

		CompiledScript compiled = Compiler.compile("selfcheck.build",
				BlockNode.of("event.tick")
						.then(BlockNode.of("action.place_block")
								.input("X", "1").input("Y", "2").input("Z", "3"))
						.then(BlockNode.of("world.entity_count").input("TYPE", "hostile"))
						.then(BlockNode.of("action.key").field("KEY", "attack").input("PRESSED", "true"))
						.then(BlockNode.of("action.tap").field("KEY", "w").input("HOLD", "1"))
						.then(BlockNode.of("action.chat").input("TEXT", "hi"))
						.then(BlockNode.of("action.command").input("COMMAND", "time set day"))
						.then(BlockNode.of("action.respawn"))
						.then(BlockNode.of("action.mouse").field("BUTTON", "left").input("PRESSED", "true"))
						.then(BlockNode.of("action.click").field("BUTTON", "right")));

		if (compiled.code().stream().noneMatch(instr ->
				instr.op == Op.CALL_ACTION && "action.place_block".equals(instr.s) && instr.a == 3)) {
			failures.add("坐标放方块未按 3 个参数编译，指令=" + compiled.code());
		}
		if (compiled.code().stream().noneMatch(instr ->
				instr.op == Op.CALL_ACTION && "action.key".equals(instr.s) && instr.a == 2)) {
			failures.add("按键积木未按 2 个参数编译，指令=" + compiled.code());
		}
		if (compiled.code().stream().noneMatch(instr ->
				instr.op == Op.CALL_BUILTIN && "world.entity_count".equals(instr.s) && instr.a == 1)) {
			failures.add("实体计数未按 1 个参数编译，指令=" + compiled.code());
		}
		if (compiled.code().stream().noneMatch(instr ->
				instr.op == Op.CALL_ACTION && "action.tap".equals(instr.s) && instr.a == 2)) {
			failures.add("点按未按 2 个参数编译（键 + 时长），指令=" + compiled.code());
		}
		if (compiled.code().stream().noneMatch(instr ->
				instr.op == Op.CALL_ACTION && "action.chat".equals(instr.s) && instr.a == 1)) {
			failures.add("聊天未按 1 个参数编译，指令=" + compiled.code());
		}
		if (compiled.code().stream().noneMatch(instr ->
				instr.op == Op.CALL_ACTION && "action.respawn".equals(instr.s) && instr.a == 0)) {
			failures.add("重生未按 0 个参数编译，指令=" + compiled.code());
		}
		if (compiled.code().stream().noneMatch(instr ->
				instr.op == Op.CALL_ACTION && "action.mouse".equals(instr.s) && instr.a == 2)) {
			failures.add("鼠标未按 2 个参数编译（键 + 状态），指令=" + compiled.code());
		}
		if (compiled.code().stream().noneMatch(instr ->
				instr.op == Op.CALL_ACTION && "action.click".equals(instr.s) && instr.a == 1)) {
			failures.add("点击未按 1 个参数编译，指令=" + compiled.code());
		}

		// 鼠标键名归一化：三种写法必须落到同一个名字
		if (!"mouse.left".equals(KeyNames.canonical("mouse.left"))
				|| !"mouse.left".equals(KeyNames.canonical("key.mouse.left"))
				|| !"mouse.left".equals(KeyNames.canonical("lmb"))) {
			failures.add("鼠标键名未归一化: mouse.left/key.mouse.left/lmb 应等价");
		}
		if (!"mouse.right".equals(KeyNames.canonical("RMB"))) {
			failures.add("rmb 应归一化为 mouse.right");
		}
		// 键盘键不能因为加了鼠标而被误判
		if (!"left.shift".equals(KeyNames.canonical("shift"))
				|| !"t".equals(KeyNames.canonical("T"))) {
			failures.add("键盘键名归一化被鼠标规则影响");
		}

		// 死亡事件必须是帽子积木，否则它会被当成语句挂在链中间
		BlockType death = BlockRegistry.get("event.death");
		if (death == null || death.shape() != BlockShape.HAT) {
			failures.add("event.death 应为帽子积木");
		}

		// 坐标钳制：超大值不能饱和成 Integer.MAX_VALUE，NaN 归零
		if (GameActions.clampCoord(1e20) != 30_000_000) {
			failures.add("坐标上界未钳制到 30000000，实际 " + GameActions.clampCoord(1e20));
		}
		if (GameActions.clampCoord(-1e20) != -30_000_000) {
			failures.add("坐标下界未钳制到 -30000000，实际 " + GameActions.clampCoord(-1e20));
		}
		if (GameActions.clampCoord(Double.NaN) != 0) {
			failures.add("NaN 坐标应归零，实际 " + GameActions.clampCoord(Double.NaN));
		}
	}
}
