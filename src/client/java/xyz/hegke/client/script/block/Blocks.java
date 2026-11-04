package xyz.hegke.client.script.block;

import java.util.List;

/**
 * 核心积木清单，覆盖「事件 / 控制 / 运算 / 变量 / 查询 / 动作 / 调试」七类。
 * 参数规格同时驱动编辑器的控件生成，新增积木时在此声明参数即可。
 */
final class Blocks {
	private Blocks() {
	}

	/** 带显示模板的注册：模板里用 {@code {参数名}} 占位。 */
	private static void regT(String id, String category, BlockShape shape, String template,
							 BlockParam... params) {
		BlockRegistry.register(new BlockType(id, category, shape, List.of(params), template));
	}

	static void registerAll() {
		// 事件
		reg("event.tick", "event", BlockShape.HAT);
		regT("event.key", "event", BlockShape.HAT, "当按下 {KEY}",
				BlockParam.fieldText("KEY", "f"));
		reg("event.damage", "event", BlockShape.HAT);
		regT("event.death", "event", BlockShape.HAT, "当玩家死亡");
		regT("event.chat", "event", BlockShape.HAT, "当聊天包含 {MATCH}",
				BlockParam.fieldOrExpr("MATCH", ""));
		regT("event.broadcast", "event", BlockShape.HAT, "当收到 {MSG}",
				BlockParam.fieldText("MSG", ""));

		// 控制
		regT("control.if", "control", BlockShape.C_SHAPE, "如果 {CONDITION}",
				BlockParam.bool("CONDITION", "false"));
		regT("control.if_else", "control", BlockShape.C_SHAPE, "如果 {CONDITION} 否则",
				BlockParam.bool("CONDITION", "false"));
		regT("control.repeat", "control", BlockShape.C_SHAPE, "重复 {TIMES} 次",
				BlockParam.number("TIMES", "10"));
		reg("control.forever", "control", BlockShape.C_SHAPE);
		regT("control.wait", "control", BlockShape.STACK, "等待 {TICKS}",
				BlockParam.number("TICKS", "20"));
		regT("control.repeat_until", "control", BlockShape.C_SHAPE, "重复直到 {CONDITION}",
				BlockParam.bool("CONDITION", "false"));
		regT("control.wait_until", "control", BlockShape.STACK, "等待直到 {CONDITION}",
				BlockParam.bool("CONDITION", "false"));
		reg("control.stop", "control", BlockShape.STACK);
		reg("control.stop_all", "control", BlockShape.STACK);
		reg("control.spawn", "control", BlockShape.C_SHAPE);
		// 占位积木：sb3 中未映射的语句用它顶上，保证后续积木不被截断
		reg("control.nop", "control", BlockShape.STACK,
				BlockParam.text("SKIPPED", ""));
		reg("control.broadcast_wait", "control", BlockShape.STACK,
				BlockParam.text("MSG", ""));

		// 运算
		regT("operator.lt", "operator", BlockShape.BOOLEAN, "{A} < {B}",
				BlockParam.number("A", "0"),
				BlockParam.number("B", "0"));
		regT("operator.gt", "operator", BlockShape.BOOLEAN, "{A} > {B}",
				BlockParam.number("A", "0"),
				BlockParam.number("B", "0"));
		regT("operator.eq", "operator", BlockShape.BOOLEAN, "{A} = {B}",
				BlockParam.number("A", "0"),
				BlockParam.number("B", "0"));
		regT("operator.gte", "operator", BlockShape.BOOLEAN, "{A} ≥ {B}",
				BlockParam.number("A", "0"),
				BlockParam.number("B", "0"));
		regT("operator.add", "operator", BlockShape.REPORTER, "{A} + {B}",
				BlockParam.number("A", "0"),
				BlockParam.number("B", "0"));
		regT("operator.subtract", "operator", BlockShape.REPORTER, "{A} − {B}",
				BlockParam.number("A", "0"),
				BlockParam.number("B", "0"));
		regT("operator.multiply", "operator", BlockShape.REPORTER, "{A} × {B}",
				BlockParam.number("A", "0"),
				BlockParam.number("B", "0"));
		regT("operator.divide", "operator", BlockShape.REPORTER, "{A} ÷ {B}",
				BlockParam.number("A", "0"),
				BlockParam.number("B", "1"));
		regT("operator.mod", "operator", BlockShape.REPORTER, "{A} mod {B}",
				BlockParam.number("A", "0"),
				BlockParam.number("B", "2"));
		regT("operator.and", "operator", BlockShape.BOOLEAN, "{A} 且 {B}",
				BlockParam.bool("A", "false"),
				BlockParam.bool("B", "false"));
		regT("operator.or", "operator", BlockShape.BOOLEAN, "{A} 或 {B}",
				BlockParam.bool("A", "false"),
				BlockParam.bool("B", "false"));
		regT("operator.not", "operator", BlockShape.BOOLEAN, "非 {A}",
				BlockParam.bool("A", "false"));
		regT("operator.random", "operator", BlockShape.REPORTER, "{A} 到 {B} 的随机数",
				BlockParam.number("A", "1"),
				BlockParam.number("B", "10"));
		regT("operator.round", "operator", BlockShape.REPORTER, "{A} 四舍五入",
				BlockParam.number("A", "0"));
		regT("operator.join", "operator", BlockShape.REPORTER, "连接 {A} 和 {B}",
				BlockParam.text("A", ""),
				BlockParam.text("B", ""));
		regT("operator.letter_of", "operator", BlockShape.REPORTER, "{B} 的第 {A} 个字符",
				BlockParam.number("A", "1"),
				BlockParam.text("B", ""));
		regT("operator.length", "operator", BlockShape.REPORTER, "{A} 的长度",
				BlockParam.text("A", ""));
		regT("operator.contains", "operator", BlockShape.BOOLEAN, "{A} 包含 {B} ?",
				BlockParam.text("A", ""),
				BlockParam.text("B", ""));
		regT("operator.mathop", "operator", BlockShape.REPORTER, "{OP}({A})",
				BlockParam.fieldOption("OP", "abs", "abs", "floor", "ceiling", "sqrt",
						"sin", "cos", "tan", "asin", "acos", "atan", "ln", "log", "e ^", "10 ^"),
				BlockParam.number("A", "0"));

		// 变量
		regT("data.set", "data", BlockShape.STACK, "将 {VAR} 设为 {VALUE}",
				BlockParam.fieldText("VAR", "x"),
				BlockParam.fieldOption("SCOPE", "local", "local", "global"),
				BlockParam.number("VALUE", "0"));
		reg("data.get", "data", BlockShape.REPORTER,
				BlockParam.fieldText("VAR", "x"),
				BlockParam.fieldOption("SCOPE", "local", "local", "global"));

		// 列表
		regT("list.add", "list", BlockShape.STACK, "把 {VALUE} 加入 {LIST}",
				BlockParam.text("LIST", "items"),
				BlockParam.text("VALUE", "0"));
		regT("list.insert", "list", BlockShape.STACK, "在 {LIST} 第 {INDEX} 项插入 {VALUE}",
				BlockParam.text("LIST", "items"),
				BlockParam.number("INDEX", "1"),
				BlockParam.text("VALUE", "0"));
		reg("list.delete", "list", BlockShape.STACK,
				BlockParam.text("LIST", "items"),
				BlockParam.number("INDEX", "1"));
		regT("list.replace", "list", BlockShape.STACK, "把 {LIST} 第 {INDEX} 项设为 {VALUE}",
				BlockParam.text("LIST", "items"),
				BlockParam.number("INDEX", "1"),
				BlockParam.text("VALUE", "0"));
		reg("list.clear", "list", BlockShape.STACK,
				BlockParam.text("LIST", "items"));
		regT("list.item", "list", BlockShape.REPORTER, "{LIST} 的第 {INDEX} 项",
				BlockParam.text("LIST", "items"),
				BlockParam.number("INDEX", "1"));
		regT("list.length", "list", BlockShape.REPORTER, "{LIST} 的长度",
				BlockParam.text("LIST", "items"));
		regT("list.contains", "list", BlockShape.BOOLEAN, "{LIST} 包含 {VALUE}?",
				BlockParam.text("LIST", "items"),
				BlockParam.text("VALUE", "0"));
		reg("list.contents", "list", BlockShape.REPORTER,
				BlockParam.text("LIST", "items"));
		reg("list.item_num", "list", BlockShape.REPORTER,
				BlockParam.text("LIST", "items"),
				BlockParam.text("VALUE", "0"));

		// 世界查询
		reg("world.block_in_front", "world", BlockShape.BOOLEAN);
		reg("world.is_mining", "world", BlockShape.BOOLEAN);
		reg("world.mining_progress", "world", BlockShape.REPORTER);
		reg("world.health", "world", BlockShape.REPORTER);
		reg("world.food", "world", BlockShape.REPORTER);
		reg("world.pos_x", "world", BlockShape.REPORTER);
		reg("world.pos_y", "world", BlockShape.REPORTER);
		reg("world.pos_z", "world", BlockShape.REPORTER);
		reg("world.selected_slot", "world", BlockShape.REPORTER);
		reg("world.nearest_entity", "world", BlockShape.REPORTER);
		reg("world.nearest_entity_x", "world", BlockShape.REPORTER);
		reg("world.nearest_entity_y", "world", BlockShape.REPORTER);
		reg("world.nearest_entity_z", "world", BlockShape.REPORTER);
		regT("world.item_count", "world", BlockShape.REPORTER, "背包里 {ITEM} 的数量",
				BlockParam.text("ITEM", "minecraft:stone"));
		reg("world.nearest_hostile", "world", BlockShape.REPORTER);
		reg("world.on_ground", "world", BlockShape.BOOLEAN);

		// ---- 状态与物品 ----
		reg("world.held_item", "world", BlockShape.REPORTER);
		reg("world.held_durability", "world", BlockShape.REPORTER);
		reg("world.front_block", "world", BlockShape.REPORTER);
		reg("world.block_below", "world", BlockShape.REPORTER);
		regT("world.block_at", "world", BlockShape.REPORTER, "({X}, {Y}, {Z}) 的方块",
				BlockParam.number("X", "0"),
				BlockParam.number("Y", "64"),
				BlockParam.number("Z", "0"));
		reg("world.is_day", "world", BlockShape.BOOLEAN);
		reg("world.dimension", "world", BlockShape.REPORTER);
		reg("world.in_water", "world", BlockShape.BOOLEAN);
		reg("world.in_lava", "world", BlockShape.BOOLEAN);
		reg("world.nearest_entity_name", "world", BlockShape.REPORTER);
		reg("world.xp_level", "world", BlockShape.REPORTER);
		reg("world.inventory_full", "world", BlockShape.BOOLEAN);
		regT("world.entity_count", "world", BlockShape.REPORTER, "附近 {TYPE} 的数量",
				BlockParam.text("TYPE", "minecraft:zombie"));
		reg("world.biome", "world", BlockShape.REPORTER);
		reg("world.ping", "world", BlockShape.REPORTER);

		// 动作
		regT("action.key", "action", BlockShape.STACK, "按键 {KEY} {PRESSED}",
				BlockParam.fieldOrExpr("KEY", "w"),
				BlockParam.bool("PRESSED", "true"));
		regT("action.mouse", "action", BlockShape.STACK, "鼠标 {BUTTON} {PRESSED}",
				BlockParam.fieldOption("BUTTON", "left", "left", "right", "middle"),
				BlockParam.bool("PRESSED", "true"));
		reg("action.attack_nearest", "action", BlockShape.STACK);
		reg("action.attack_nearest_hostile", "action", BlockShape.STACK);
		reg("action.open_container", "action", BlockShape.STACK);
		regT("action.move_toward", "action", BlockShape.STACK, "走向 ({X}, {Z})",
				BlockParam.number("X", "0"),
				BlockParam.number("Z", "0"));
		reg("action.stop", "action", BlockShape.STACK);
		regT("action.chat", "action", BlockShape.STACK, "在聊天栏说 {TEXT}",
				BlockParam.text("TEXT", "大家好"));
		regT("action.command", "action", BlockShape.STACK, "执行命令 {COMMAND}",
				BlockParam.text("COMMAND", "time set day"));
		regT("action.respawn", "action", BlockShape.STACK, "重生");
		regT("action.tap", "action", BlockShape.STACK, "点按 {KEY} 按住 {HOLD} tick",
				BlockParam.fieldOrExpr("KEY", "w"),
				BlockParam.number("HOLD", "1"));
		regT("action.click", "action", BlockShape.STACK, "点击 {BUTTON}",
				BlockParam.fieldOption("BUTTON", "left", "left", "right", "middle"));
		regT("action.break_wait", "action", BlockShape.C_SHAPE, "等待挖破当前方块（最多 {TICKS}）",
				BlockParam.number("TICKS", "200"));
		reg("action.sleep", "action", BlockShape.STACK);
		regT("action.place_block", "action", BlockShape.STACK, "在 ({X}, {Y}, {Z}) 放方块",
				BlockParam.number("X", "0"),
				BlockParam.number("Y", "64"),
				BlockParam.number("Z", "0"));
		regT("action.look", "action", BlockShape.STACK, "视角转到 ({YAW}, {PITCH})",
				BlockParam.number("YAW", "0"),
				BlockParam.number("PITCH", "0"));
		regT("action.look_at", "action", BlockShape.STACK, "看向 ({X}, {Y}, {Z})",
				BlockParam.number("X", "0"),
				BlockParam.number("Y", "64"),
				BlockParam.number("Z", "0"));

		// 调试
		regT("debug.log", "debug", BlockShape.STACK, "输出日志 {TEXT}",
				BlockParam.text("TEXT", ""));

		// 感知
		reg("input.key_pressed", "sensing", BlockShape.BOOLEAN,
				BlockParam.fieldText("KEY", "space"));
		reg("sensing.timer", "sensing", BlockShape.REPORTER);
		reg("sensing.reset_timer", "sensing", BlockShape.STACK);
		reg("sensing.current", "sensing", BlockShape.REPORTER,
				BlockParam.fieldOption("UNIT", "year",
						"year", "month", "date", "dayofweek", "hour", "minute", "second"));
		reg("sensing.days_since_2000", "sensing", BlockShape.REPORTER);

		// 输出（Scratch 的「说 / 思考」映射到动作栏与日志）
		regT("debug.say", "debug", BlockShape.STACK, "说 {TEXT}",
				BlockParam.text("TEXT", ""));
		regT("debug.say_for", "debug", BlockShape.STACK, "说 {TEXT} {TICKS}",
				BlockParam.text("TEXT", ""),
				BlockParam.number("TICKS", "20"));

		// 自定义积木
		reg("func.call", "func", BlockShape.STACK,
				BlockParam.fieldText("NAME", "fn"),
				BlockParam.number("ARGC", "3"),
				BlockParam.text("P1", ""),
				BlockParam.text("P2", ""),
				BlockParam.text("P3", ""),
				BlockParam.text("P4", ""),
				BlockParam.text("P5", ""));
		regT("action.broadcast", "action", BlockShape.STACK, "广播 {MSG}",
				BlockParam.text("MSG", ""));
	}

	private static void reg(String id, String category, BlockShape shape, BlockParam... params) {
		BlockRegistry.register(new BlockType(id, category, shape, List.of(params)));
	}
}
