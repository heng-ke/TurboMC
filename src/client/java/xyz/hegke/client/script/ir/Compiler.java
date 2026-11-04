package xyz.hegke.client.script.ir;

import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.node.Slot;
import xyz.hegke.client.script.runtime.Values;
import xyz.hegke.client.script.store.FunctionDefinition;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积木树 → 扁平指令。
 *
 * <p>所有控制流都编译成显式跳转，运行时不需要递归解释，因此可以在任意指令处挂起与恢复。</p>
 */
public final class Compiler {
	/** 自定义积木最多支持的参数个数（受积木参数面板限制）。 */
	private static final int MAX_CALL_ARGS = 5;

	private final String name;
	private final boolean throttle;
	private final List<Instr> code = new ArrayList<>();
	private int tempIndex;

	/** 当前输出目标：主流程为 {@link #code}，编译 spawn 体时切换到临时列表。 */
	private List<Instr> sink = code;

	/** 所有 spawn 体，按下标即 bodyId 索引；拼接顺序保证父体先于子体。 */
	private final List<List<Instr>> spawnBodies = new ArrayList<>();
	private final List<PendingSpawn> spawnPatches = new ArrayList<>();
	private final List<PendingCall> callPatches = new ArrayList<>();
	/** 积木 → 它编译出的指令区间，供编辑器显示与报错反查。 */
	private final Map<BlockNode, int[]> blockRanges = new IdentityHashMap<>();

	/** 自定义积木名 → bodyId。 */
	private final Map<String, Integer> functionBodies = new LinkedHashMap<>();

	private Compiler(String name, boolean throttle) {
		this.name = name;
		this.throttle = throttle;
	}

	/**
	 * 循环回边：节流开启时让出一 tick（Scratch 的「每帧一轮」），关闭时在一个 tick 内尽量多跑。
	 */
	private void emitLoopBack(int target) {
		Instr back = Instr.jump(Op.LOOP, target);
		if (throttle) {
			back.b = 1;
		}
		add(back);
	}

	public static CompiledScript compile(String name, BlockNode hat) {
		return compile(name, hat, List.of());
	}

	public static CompiledScript compile(String name, BlockNode hat, List<FunctionDefinition> functions) {
		return compile(name, hat, functions, "");
	}

	public static CompiledScript compile(String name, BlockNode hat, List<FunctionDefinition> functions,
										 String scopeId) {
		return compile(name, hat, functions, scopeId, false);
	}

	/**
	 * @param throttle 是否让每个循环轮次消耗一个 tick（对齐 Scratch 的「每帧一轮」）
	 */
	public static CompiledScript compile(String name, BlockNode hat, List<FunctionDefinition> functions,
										 String scopeId, boolean throttle) {
		Compiler compiler = new Compiler(name, throttle);
		String hatType = hat != null ? hat.type : "event.tick";

		if (functions != null) {
			for (FunctionDefinition function : functions) {
				compiler.reserveFunction(function);
			}
		}

		if (hat != null) {
			compiler.emitChain(hat.next);
		}
		compiler.add(new Instr(Op.HALT));

		if (functions != null) {
			for (FunctionDefinition function : functions) {
				compiler.compileFunction(function);
			}
		}

		compiler.flushSpawnBodies();
		return new CompiledScript(name, hatType, List.copyOf(compiler.code), scopeId,
				Map.copyOf(compiler.blockRanges));
	}

	/**
	 * 先为每个函数占位一个 bodyId，使函数之间可以互相调用（含前向引用）。
	 */
	private void reserveFunction(FunctionDefinition function) {
		if (function == null || function.name == null || function.name.isBlank()
				|| functionBodies.containsKey(function.name)) {
			return;
		}
		int bodyId = spawnBodies.size();
		spawnBodies.add(new ArrayList<>());
		functionBodies.put(function.name, bodyId);
	}

	private void compileFunction(FunctionDefinition function) {
		if (function == null) {
			return;
		}
		Integer bodyId = functionBodies.get(function.name);
		if (bodyId == null) {
			return;
		}
		List<Instr> body = spawnBodies.get(bodyId);
		if (!body.isEmpty()) {
			return;
		}
		List<Instr> saved = sink;
		sink = body;
		emitChain(function.body);
		add(new Instr(Op.RETURN));
		sink = saved;
	}

	/**
	 * 把 spawn 体追加到主流程之后，并按各自的基址修正内部跳转。
	 *
	 * <p>主流程以 HALT 收尾，因此追加段不会被主流程顺序执行到。</p>
	 */
	private void flushSpawnBodies() {
		int base = code.size();
		int[] bodyBase = new int[spawnBodies.size()];

		for (int index = 0; index < spawnBodies.size(); index++) {
			bodyBase[index] = base;
			List<Instr> body = spawnBodies.get(index);
			for (Instr instr : body) {
				if (instr.op == Op.JUMP || instr.op == Op.JUMP_IF_FALSE || instr.op == Op.LOOP) {
					instr.a += base;
				}
			}
			code.addAll(body);
			base += body.size();
		}

		for (PendingSpawn pending : spawnPatches) {
			pending.instr().a = bodyBase[pending.bodyId()];
		}
		List<String> missing = new ArrayList<>();
		for (PendingCall pending : callPatches) {
			Integer bodyId = functionBodies.get(pending.name());
			if (bodyId == null) {
				// 目标保持默认值 0 会让脚本跳回开头重跑，宁可编译失败
				missing.add(pending.name());
				continue;
			}
			pending.instr().a = bodyBase[bodyId];
		}
		if (!missing.isEmpty()) {
			throw new IllegalStateException("调用了未定义的自定义积木: " + String.join(", ", missing));
		}
	}

	/**
	 * 把 spawn 体编译到独立列表，主流程只留下一条跳转指令。
	 */
	private void emitSpawn(BlockNode node) {
		Instr spawn = new Instr(Op.SPAWN);
		add(spawn);

		int bodyId = spawnBodies.size();
		List<Instr> body = new ArrayList<>();
		spawnBodies.add(body);

		List<Instr> saved = sink;
		sink = body;
		emitChain(node.branchOrNull("SUBSTACK"));
		add(new Instr(Op.HALT));
		sink = saved;

		spawnPatches.add(new PendingSpawn(spawn, bodyId));
	}

	private record PendingSpawn(Instr instr, int bodyId) {
	}

	private record PendingCall(Instr instr, String name) {
	}

	// ---- 控制流 ----

	/**
	 * 压入「字段或表达式」参数：有 inputs 就用表达式，否则压字段里的常量。
	 *
	 * <p>对应 {@code BlockParam.acceptsReporter}：这样的参数在 sb3 里可能是字面量（落 fields），
	 * 也可能被子积木替换（落 inputs）。只读一边都会丢值。</p>
	 */
	private void pushParam(BlockNode node, String name, String fallback) {
		if (node.inputs.containsKey(name)) {
			pushInput(node, name, fallback);
			return;
		}
		add(new Instr(Op.PUSH_CONST, node.fieldOr(name, fallback)));
	}

	private void emitChain(BlockNode first) {
		BlockNode node = first;
		int guard = 0;
		while (node != null) {
			if (++guard > 10_000) {
				throw new IllegalStateException("积木链过长或存在环: " + node.type);
			}
			emitBlock(node);
			node = node.next;
		}
	}

	private void emitBlock(BlockNode node) {
		int start = code.size();
		try {
		switch (node.type) {
			case "control.if" -> emitIf(node);
			case "control.if_else" -> emitIfElse(node);
			case "control.repeat" -> emitRepeat(node);
			case "control.forever" -> emitForever(node);
			case "control.wait" -> {
				pushInput(node, "TICKS", 0d);
				add(new Instr(Op.YIELD));
			}
			case "control.repeat_until" -> emitRepeatUntil(node);
			case "control.wait_until" -> emitWaitUntil(node);
			case "control.stop" -> add(new Instr(Op.HALT));
			case "control.stop_all" -> add(new Instr(Op.STOP_ALL));
			case "control.nop" -> {
				// 占位积木：sb3 中无法映射的语句用它顶上，保证后续积木不被截断
			}
			case "control.broadcast_wait" -> {
				pushInput(node, "MSG", "");
				add(new Instr(Op.BROADCAST_WAIT));
			}
			case "control.spawn" -> emitSpawn(node);
			case "operator.lt" -> emitCompare(node, "<");
			case "operator.gt" -> emitCompare(node, ">");
			case "operator.eq" -> emitCompare(node, "=");
			case "operator.gte" -> emitCompare(node, ">=");
			case "operator.add" -> emitBinary(node, "+");
			case "operator.subtract" -> emitBinary(node, "-");
			case "operator.multiply" -> emitBinary(node, "*");
			case "operator.divide" -> emitBinary(node, "/");
			case "operator.mod" -> emitBinary(node, "%");
			case "operator.and" -> emitLogic(node, Op.AND);
			case "operator.or" -> emitLogic(node, Op.OR);
			case "operator.not" -> {
				pushInput(node, "A", Boolean.FALSE);
				add(new Instr(Op.NOT));
			}
			case "operator.random" -> {
				pushInput(node, "A", 0d);
				pushInput(node, "B", 10d);
				add(Instr.builtin("operator.random", 2));
			}
			case "operator.round" -> {
				pushInput(node, "A", 0d);
				add(Instr.builtin("operator.round", 1));
			}
			case "operator.join" -> {
				pushInput(node, "A", "");
				pushInput(node, "B", "");
				add(Instr.builtin("operator.join", 2));
			}
			case "operator.letter_of" -> {
				pushInput(node, "A", 1d);
				pushInput(node, "B", "");
				add(Instr.builtin("operator.letter_of", 2));
			}
			case "operator.length" -> {
				pushInput(node, "A", "");
				add(Instr.builtin("operator.length", 1));
			}
			case "operator.contains" -> {
				pushInput(node, "A", "");
				pushInput(node, "B", "");
				add(Instr.builtin("operator.contains", 2));
			}
			case "operator.mathop" -> {
				add(new Instr(Op.PUSH_CONST, node.fieldOr("OP", "abs")));
				pushInput(node, "A", 0d);
				add(Instr.builtin("operator.mathop", 2));
			}
			case "data.set" -> {
				pushInput(node, "VALUE", 0d);
				Instr store = Instr.var(Op.STORE_VAR, node.fieldOr("VAR", "x"));
				store.a = scopeOf(node);
				add(store);
			}
			case "data.get" -> {
				Instr load = Instr.var(Op.LOAD_VAR, node.fieldOr("VAR", "x"));
				load.a = scopeOf(node);
				add(load);
			}
			case "list.add" -> {
				pushInput(node, "LIST", "items");
				pushInput(node, "VALUE", 0d);
				add(Instr.action("list.add", 2));
			}
			case "list.insert" -> {
				pushInput(node, "LIST", "items");
				pushInput(node, "INDEX", 1d);
				pushInput(node, "VALUE", 0d);
				add(Instr.action("list.insert", 3));
			}
			case "list.delete" -> {
				pushInput(node, "LIST", "items");
				pushInput(node, "INDEX", 1d);
				add(Instr.action("list.delete", 2));
			}
			case "list.replace" -> {
				pushInput(node, "LIST", "items");
				pushInput(node, "INDEX", 1d);
				pushInput(node, "VALUE", 0d);
				add(Instr.action("list.replace", 3));
			}
			case "list.clear" -> {
				pushInput(node, "LIST", "items");
				add(Instr.action("list.clear", 1));
			}
			case "list.item" -> {
				pushInput(node, "LIST", "items");
				pushInput(node, "INDEX", 1d);
				add(Instr.builtin("list.item", 2));
			}
			case "list.length" -> {
				pushInput(node, "LIST", "items");
				add(Instr.builtin("list.length", 1));
			}
			case "list.contains" -> {
				pushInput(node, "LIST", "items");
				pushInput(node, "VALUE", 0d);
				add(Instr.builtin("list.contains", 2));
			}
			case "list.contents" -> {
				pushInput(node, "LIST", "items");
				add(Instr.builtin("list.contents", 1));
			}
			case "world.block_in_front" -> add(Instr.builtin("world.block_in_front", 0));
			case "world.is_mining" -> add(Instr.builtin("world.is_mining", 0));
			case "world.mining_progress" -> add(Instr.builtin("world.mining_progress", 0));
			case "world.health" -> add(Instr.builtin("world.health", 0));
			case "world.food" -> add(Instr.builtin("world.food", 0));
			case "world.pos_x" -> add(Instr.builtin("world.pos_x", 0));
			case "world.pos_y" -> add(Instr.builtin("world.pos_y", 0));
			case "world.pos_z" -> add(Instr.builtin("world.pos_z", 0));
			case "world.selected_slot" -> add(Instr.builtin("world.selected_slot", 0));
			case "world.nearest_entity" -> add(Instr.builtin("world.nearest_entity", 0));
			case "world.nearest_entity_x" -> add(Instr.builtin("world.nearest_entity_x", 0));
			case "world.nearest_entity_y" -> add(Instr.builtin("world.nearest_entity_y", 0));
			case "world.nearest_entity_z" -> add(Instr.builtin("world.nearest_entity_z", 0));
			case "world.nearest_hostile" -> add(Instr.builtin("world.nearest_hostile", 0));
			case "world.on_ground" -> add(Instr.builtin("world.on_ground", 0));
			case "world.held_item" -> add(Instr.builtin("world.held_item", 0));
			case "world.held_durability" -> add(Instr.builtin("world.held_durability", 0));
			case "world.front_block" -> add(Instr.builtin("world.front_block", 0));
			case "world.block_below" -> add(Instr.builtin("world.block_below", 0));
			case "world.block_at" -> {
				pushInput(node, "X", 0d);
				pushInput(node, "Y", 64d);
				pushInput(node, "Z", 0d);
				add(Instr.builtin("world.block_at", 3));
			}
			case "world.is_day" -> add(Instr.builtin("world.is_day", 0));
			case "world.dimension" -> add(Instr.builtin("world.dimension", 0));
			case "world.in_water" -> add(Instr.builtin("world.in_water", 0));
			case "world.in_lava" -> add(Instr.builtin("world.in_lava", 0));
			case "world.nearest_entity_name" -> add(Instr.builtin("world.nearest_entity_name", 0));
			case "world.xp_level" -> add(Instr.builtin("world.xp_level", 0));
			case "world.inventory_full" -> add(Instr.builtin("world.inventory_full", 0));
			case "action.respawn" -> add(Instr.action("action.respawn", 0));
			case "action.chat" -> {
				pushInput(node, "TEXT", "");
				add(Instr.action("action.chat", 1));
			}
			case "action.command" -> {
				pushInput(node, "COMMAND", "");
				add(Instr.action("action.command", 1));
			}
			case "action.tap" -> {
				pushParam(node, "KEY", "w");
				pushInput(node, "HOLD", 1d);
				add(Instr.action("action.tap", 2));
			}
			case "action.sleep" -> add(Instr.action("action.sleep", 0));
			case "action.place_block" -> {
				pushInput(node, "X", 0d);
				pushInput(node, "Y", 64d);
				pushInput(node, "Z", 0d);
				add(Instr.action("action.place_block", 3));
			}
			case "world.entity_count" -> {
				pushInput(node, "TYPE", "any");
				add(Instr.builtin("world.entity_count", 1));
			}
			case "world.biome" -> add(Instr.builtin("world.biome", 0));
			case "world.ping" -> add(Instr.builtin("world.ping", 0));
			case "list.item_num" -> {
				pushInput(node, "LIST", "items");
				pushInput(node, "VALUE", 0d);
				add(Instr.builtin("list.item_num", 2));
			}
			case "world.item_count" -> {
				pushInput(node, "ITEM", "minecraft:stone");
				add(Instr.builtin("world.item_count", 1));
			}
			case "action.key" -> {
				// 按键名可以是常量，也可以是表达式（如「第 i 个字符 of 指令」）
				pushParam(node, "KEY", "w");
				pushInput(node, "PRESSED", Boolean.TRUE);
				add(Instr.action("action.key", 2));
			}
			case "action.mouse" -> {
				// 鼠标键只可能是三个常量之一，不需要表达式支持
				add(new Instr(Op.PUSH_CONST, node.fieldOr("BUTTON", "left")));
				pushInput(node, "PRESSED", Boolean.TRUE);
				add(Instr.action("action.mouse", 2));
			}
			case "action.click" -> {
				add(new Instr(Op.PUSH_CONST, node.fieldOr("BUTTON", "left")));
				add(Instr.action("action.click", 1));
			}
			case "action.break_wait" -> emitBreakWait(node);
			case "action.attack_nearest" -> add(Instr.action("action.attack_nearest", 0));
			case "action.attack_nearest_hostile" -> add(Instr.action("action.attack_nearest_hostile", 0));
			case "action.open_container" -> add(Instr.action("action.open_container", 0));
			case "action.move_toward" -> {
				pushInput(node, "X", 0d);
				pushInput(node, "Z", 0d);
				add(Instr.action("action.move_toward", 2));
			}
			case "action.stop" -> add(Instr.action("action.stop", 0));
			case "action.look" -> {
				pushInput(node, "YAW", 0d);
				pushInput(node, "PITCH", 0d);
				add(Instr.action("action.look", 2));
			}
			case "action.look_at" -> {
				pushInput(node, "X", 0d);
				pushInput(node, "Y", 64d);
				pushInput(node, "Z", 0d);
				add(Instr.action("action.look_at", 3));
			}
			case "debug.log" -> {
				pushInput(node, "TEXT", "");
				add(Instr.action("debug.log", 1));
			}
			case "debug.say" -> {
				pushInput(node, "TEXT", "");
				add(Instr.action("debug.say", 1));
			}
			case "debug.say_for" -> {
				pushInput(node, "TEXT", "");
				add(Instr.action("debug.say", 1));
				pushInput(node, "TICKS", 20d);
				add(new Instr(Op.YIELD));
			}
			case "input.key_pressed" -> {
				add(new Instr(Op.PUSH_CONST, node.fieldOr("KEY", "space")));
				add(Instr.builtin("input.key_pressed", 1));
			}
			case "sensing.timer" -> add(Instr.builtin("sensing.timer", 0));
			case "sensing.reset_timer" -> add(Instr.action("sensing.reset_timer", 0));
			case "sensing.current" -> {
				add(new Instr(Op.PUSH_CONST, node.fieldOr("UNIT", "year")));
				add(Instr.builtin("sensing.current", 1));
			}
			case "sensing.days_since_2000" -> add(Instr.builtin("sensing.days_since_2000", 0));
			case "action.broadcast" -> {
				pushInput(node, "MSG", "");
				add(Instr.action("action.broadcast", 1));
			}
			case "func.call" -> {
				int argc = Math.min(MAX_CALL_ARGS, Math.max(1, (int) Values.asNumber(node.fieldOr("ARGC", "3"))));
				for (int i = 1; i <= argc; i++) {
					pushInput(node, "P" + i, "");
				}
				Instr call = new Instr(Op.CALL_FUNC);
				call.s = node.fieldOr("NAME", "");
				call.b = argc;
				add(call);
				callPatches.add(new PendingCall(call, call.s));
			}
			default -> throw new IllegalArgumentException("未实现的积木: " + node.type);
		}
		} finally {
			if (code.size() > start) {
				blockRanges.put(node, new int[]{start, code.size() - 1});
			}
		}
	}

	private void emitIf(BlockNode node) {
		pushInput(node, "CONDITION", Boolean.FALSE);
		int jmp = add(new Instr(Op.JUMP_IF_FALSE));
		emitChain(node.branchOrNull("SUBSTACK"));
		sink.get(jmp).a = sink.size();
	}

	/**
	 * if / else：条件为假跳到 else 分支，then 分支结束后跳过 else。
	 */
	private void emitIfElse(BlockNode node) {
		pushInput(node, "CONDITION", Boolean.FALSE);
		int jumpIfFalse = add(new Instr(Op.JUMP_IF_FALSE));
		emitChain(node.branchOrNull("SUBSTACK"));
		int jumpToEnd = add(new Instr(Op.JUMP));
		sink.get(jumpIfFalse).a = sink.size();
		emitChain(node.branchOrNull("SUBSTACK2"));
		sink.get(jumpToEnd).a = sink.size();
	}

	private void emitRepeat(BlockNode node) {
		String counter = "$repeat" + (tempIndex++);
		pushInput(node, "TIMES", 0d);
		// Scratch 用 Math.round 取整：重复 2.1 次只跑 2 轮
		add(Instr.builtin("operator.round", 1));
		add(Instr.var(Op.STORE_VAR, counter));

		int start = sink.size();
		add(Instr.var(Op.LOAD_VAR, counter));
		add(new Instr(Op.PUSH_CONST, 0d));
		add(Instr.operator(Op.COMPARE, ">"));
		int exit = add(new Instr(Op.JUMP_IF_FALSE));

		emitChain(node.branchOrNull("SUBSTACK"));

		add(Instr.var(Op.LOAD_VAR, counter));
		add(new Instr(Op.PUSH_CONST, 1d));
		add(Instr.operator(Op.BIN_OP, "-"));
		add(Instr.var(Op.STORE_VAR, counter));
		emitLoopBack(start);

		sink.get(exit).a = sink.size();
	}

	private void emitForever(BlockNode node) {
		int start = sink.size();
		emitChain(node.branchOrNull("SUBSTACK"));
		emitLoopBack(start);
	}

	/**
	 * repeat until：条件为真时退出，为假时执行一次本体。
	 */
	private void emitRepeatUntil(BlockNode node) {
		int start = sink.size();
		pushInput(node, "CONDITION", Boolean.FALSE);
		int jumpIfFalse = add(new Instr(Op.JUMP_IF_FALSE));
		int jumpToEnd = add(new Instr(Op.JUMP));
		sink.get(jumpIfFalse).a = sink.size();
		emitChain(node.branchOrNull("SUBSTACK"));
		emitLoopBack(start);
		sink.get(jumpToEnd).a = sink.size();
	}

	/**
	 * wait until：条件未满足时每 tick 让出一次并重新求值。
	 */
	private void emitWaitUntil(BlockNode node) {
		int start = sink.size();
		pushInput(node, "CONDITION", Boolean.FALSE);
		int jumpIfFalse = add(new Instr(Op.JUMP_IF_FALSE));
		int jumpToEnd = add(new Instr(Op.JUMP));
		sink.get(jumpIfFalse).a = sink.size();
		add(new Instr(Op.PUSH_CONST, 1d));
		add(new Instr(Op.YIELD));
		add(Instr.jump(Op.LOOP, start));
		sink.get(jumpToEnd).a = sink.size();
	}

	/**
	 * 等待挖破：挂起直到准星方块被挖破或超时，结果（是否真挖破）压栈。
	 *
	 * <p>因此它天然是 C 形积木：里面的代码只在真正挖破时执行，超时则整段跳过，
	 * 不需要额外一个变量去接返回值。</p>
	 */
	private void emitBreakWait(BlockNode node) {
		// 先记下当前输出列表：SUBSTACK 里若有 spawn，sink 会被临时切走
		List<Instr> here = sink;
		pushInput(node, "TICKS", 200d);
		add(new Instr(Op.WAIT_BREAK));
		int jumpIfFalse = add(new Instr(Op.JUMP_IF_FALSE));
		emitChain(node.branchOrNull("SUBSTACK"));
		here.get(jumpIfFalse).a = here.size();
	}

	private void emitLogic(BlockNode node, Op op) {
		pushInput(node, "A", Boolean.FALSE);
		pushInput(node, "B", Boolean.FALSE);
		add(new Instr(op));
	}

	/**
	 * 变量作用域：{@code a = 1} 表示全局，0 表示脚本局部。
	 */
	private static int scopeOf(BlockNode node) {
		String scope = node.fieldOr("SCOPE", "local");
		if ("global".equals(scope)) {
			return 1;
		}
		if ("target".equals(scope)) {
			return 2;
		}
		return 0;
	}

	private void emitCompare(BlockNode node, String operator) {
		pushInput(node, "A", 0d);
		pushInput(node, "B", 0d);
		add(Instr.operator(Op.COMPARE, operator));
	}

	private void emitBinary(BlockNode node, String operator) {
		pushInput(node, "A", 0d);
		pushInput(node, "B", 0d);
		add(Instr.operator(Op.BIN_OP, operator));
	}

	// ---- 工具 ----

	private void pushInput(BlockNode node, String inputName, Object fallback) {
		Slot slot = node.slot(inputName);
		if (slot == null || (slot.literal == null && !slot.hasBlock())) {
			add(new Instr(Op.PUSH_CONST, fallback));
			return;
		}
		if (slot.hasBlock()) {
			emitBlock(slot.block);
			return;
		}
		add(new Instr(Op.PUSH_CONST, Values.parseLiteral(slot.literal)));
	}

	private int add(Instr instr) {
		sink.add(instr);
		return sink.size() - 1;
	}
}
