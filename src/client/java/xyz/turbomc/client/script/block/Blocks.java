package xyz.turbomc.client.script.block;

import java.util.List;

/**
 * 核心积木清单，覆盖「事件 / 控制 / 运算 / 变量 / 列表 / 感知 / 聊天与 AI / 动作 / 调试」等类别。
 *
 * <p>这份清单只服务于「把 sb3 积木渲染成文本」。脚本由 TurboWarp 编写，
 * 游戏内不提供拼接，因此这里不生成任何控件；新增积木时若忘了在此声明，
 * 查看器会把它显示成未注册的原始 opcode（不影响运行）。</p>
 *
 * <p><b>参数登记里真正被消费的只有两样</b>：模板里的 {@code {NAME}}（值从 sb3 的
 * fields / inputs 现取，先 fields 后 inputs）与参数<b>个数</b>（{@code RhinoVm.argsSelfTest}
 * 拿它和扩展的占位符数比对）。{@link BlockParam} 的 {@code kind} / {@code options} /
 * {@code defaultValue} 目前不参与渲染——所以登记成 field 还是 input 不影响显示，
 * 别把它当渲染依据。名字与顺序的一致性由 {@code tools/build-mapping-report.js} 在
 * 构建期校验（见 ARCHITECTURE §6.1）。</p>
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
		regT("event.screen_open", "event", BlockShape.HAT, "当打开 {SCREEN}",
				BlockParam.fieldOption("SCREEN", "container", "container", "inventory", "any"));
		regT("event.chat", "event", BlockShape.HAT, "当聊天包含 {MATCH}",
				BlockParam.fieldOrExpr("MATCH", ""));
		regT("event.broadcast", "event", BlockShape.HAT, "当收到 {MSG}",
				BlockParam.fieldText("MSG", ""));
		// 谓词由 VM 自己每帧比较，宿主不参与；「响度」在游戏内没有音源，恒不成立
		regT("event.when_greater", "event", BlockShape.HAT, "当 {MENU} > {VALUE}",
				BlockParam.fieldOption("MENU", "timer", "timer", "loudness"),
				BlockParam.number("VALUE", "10"));

		// 自定义积木：游戏内不能定义，但导入的工程要能看见定义与函数体
		regT("procedures.definition", "control", BlockShape.HAT, "定义 {NAME}",
				BlockParam.text("NAME", ""));

		// 控制
		regT("control.if", "control", BlockShape.C_SHAPE, "如果 {CONDITION}",
				BlockParam.bool("CONDITION", "false"));		regT("control.if_else", "control", BlockShape.C_SHAPE, "如果 {CONDITION} 否则",
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
		regT("control.while", "control", BlockShape.C_SHAPE, "当 {CONDITION} 重复执行",
				BlockParam.bool("CONDITION", "false"));
		regT("control.for_each", "control", BlockShape.C_SHAPE, "对于 {VAR} 在 {VALUE} 中每一项",
				BlockParam.fieldText("VAR", "item"),
				BlockParam.text("VALUE", ""));
		reg("control.stop_all", "control", BlockShape.STACK);
		regT("control.broadcast_wait", "control", BlockShape.STACK, "广播 {MSG} 并等待",
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
		regT("operator.contains", "operator", BlockShape.BOOLEAN, "{A} 包含 {B}?",
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
		// 只读查看需要它：sb3 里的「将变量增加」与「设为」是两块积木，
		// 挤到同一个类型上会把「增加 1」显示成「设为 1」
		regT("data.change", "data", BlockShape.STACK, "将 {VAR} 增加 {VALUE}",
				BlockParam.fieldText("VAR", "x"),
				BlockParam.fieldOption("SCOPE", "local", "local", "global"),
				BlockParam.number("VALUE", "1"));
		reg("data.get", "data", BlockShape.REPORTER,
				BlockParam.fieldText("VAR", "x"),
				BlockParam.fieldOption("SCOPE", "local", "local", "global"));
		// 监视器：舞台没有 GUI，显示/隐藏改由 HUD 承担
		regT("data.show_var", "data", BlockShape.STACK, "显示变量 {VAR}",
				BlockParam.fieldText("VAR", "x"));
		regT("data.hide_var", "data", BlockShape.STACK, "隐藏变量 {VAR}",
				BlockParam.fieldText("VAR", "x"));

		// 列表
		regT("list.add", "list", BlockShape.STACK, "把 {VALUE} 加入 {LIST}",
				BlockParam.text("LIST", "items"),
				BlockParam.text("VALUE", "0"));
		regT("list.insert", "list", BlockShape.STACK, "在 {LIST} 第 {INDEX} 项插入 {VALUE}",
				BlockParam.text("LIST", "items"),
				BlockParam.number("INDEX", "1"),
				BlockParam.text("VALUE", "0"));
		regT("list.delete", "list", BlockShape.STACK, "删除 {LIST} 第 {INDEX} 项",
				BlockParam.text("LIST", "items"),
				BlockParam.number("INDEX", "1"));
		regT("list.replace", "list", BlockShape.STACK, "把 {LIST} 第 {INDEX} 项设为 {VALUE}",
				BlockParam.text("LIST", "items"),
				BlockParam.number("INDEX", "1"),
				BlockParam.text("VALUE", "0"));
		regT("list.clear", "list", BlockShape.STACK, "清空列表 {LIST}",
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
		regT("data.show_list", "list", BlockShape.STACK, "显示列表 {LIST}",
				BlockParam.fieldText("LIST", "items"));
		regT("data.hide_list", "list", BlockShape.STACK, "隐藏列表 {LIST}",
				BlockParam.fieldText("LIST", "items"));
		regT("list.item_num", "list", BlockShape.REPORTER, "{LIST} 中第一个 {VALUE} 的编号",
				BlockParam.text("LIST", "items"),
				BlockParam.text("VALUE", "0"));

		// 世界查询
		reg("world.block_in_front", "world", BlockShape.BOOLEAN);
		reg("world.is_mining", "world", BlockShape.BOOLEAN);
		reg("world.mining_progress", "world", BlockShape.REPORTER);
		reg("world.health", "world", BlockShape.REPORTER);
		reg("world.food", "world", BlockShape.REPORTER);
		// 三轴合并成一块下拉（旧的 world.pos_x/y/z 已删除，见 ARCHITECTURE §6.2）
		regT("world.pos", "world", BlockShape.REPORTER, "玩家 {AXIS} 坐标",
				BlockParam.fieldOption("AXIS", "x", "x", "y", "z"));
		reg("world.selected_slot", "world", BlockShape.REPORTER);
		reg("world.container_slots", "world", BlockShape.REPORTER);
		regT("world.slot_field", "world", BlockShape.REPORTER, "槽位 {INDEX} 的 {FIELD}",
				BlockParam.number("INDEX", "0"),
				BlockParam.fieldOption("FIELD", "id", "id", "amount",
						"max_durability", "damage", "custom_name"));
		// 物品参数是下拉菜单（扩展的 itemId 菜单，1.21.11 全部 1505 个物品）；
		// 查看器把 id 显示成物品中文名（见 ParamLabels.itemName）
		regT("world.first_slot_of", "world", BlockShape.REPORTER, "第一个装着 {ITEM} 的槽位",
				BlockParam.text("ITEM", "minecraft:iron_ore"));
		// 实体类型在编辑器里是下拉菜单（扩展的 entityType 菜单，1.21.11 全部 157 个实体）；
		// 查看器只拿到最终值：关键字走语言文件，具体 id 走注册表查中文名（见 ParamLabels）
		regT("world.nearest_entity_field", "world", BlockShape.REPORTER, "最近 {TYPE} 的 {FIELD}",
				BlockParam.text("TYPE", "minecraft:zombie"),
				BlockParam.fieldOption("FIELD", "distance", "distance", "x", "y", "z", "name",
						"health", "max_health", "type_id"));
		// 物品菜单同上（itemId）
		regT("world.item_count", "world", BlockShape.REPORTER, "背包里 {ITEM} 的数量",
				BlockParam.text("ITEM", "minecraft:stone"));
		reg("world.on_ground", "world", BlockShape.BOOLEAN);

		// ---- 状态与物品 ----
		regT("world.held_item_field", "world", BlockShape.REPORTER, "手中的 {FIELD}",
				BlockParam.fieldOption("FIELD", "id", "id", "durability", "count", "max_durability"));
		reg("world.front_block", "world", BlockShape.REPORTER);
		reg("world.block_below", "world", BlockShape.REPORTER);
		regT("world.block_at", "world", BlockShape.REPORTER, "({X}, {Y}, {Z}) 的方块",
				BlockParam.number("X", "0"),
				BlockParam.number("Y", "64"),
				BlockParam.number("Z", "0"));
		// 准星方块坐标：命中就是命中格，未命中取视线尽头那一格（见 WorldQueries.aimBlockPos）
		regT("world.aim_pos", "world", BlockShape.REPORTER, "准星方块的 {AXIS} 坐标",
				BlockParam.fieldOption("AXIS", "x", "x", "y", "z"));
		reg("world.is_day", "world", BlockShape.BOOLEAN);
		reg("world.dimension", "world", BlockShape.REPORTER);
		reg("world.in_water", "world", BlockShape.BOOLEAN);
		reg("world.in_lava", "world", BlockShape.BOOLEAN);
		reg("world.xp_level", "world", BlockShape.REPORTER);
		reg("world.inventory_full", "world", BlockShape.BOOLEAN);
		regT("world.entity_count", "world", BlockShape.REPORTER, "附近 {TYPE} 的数量",
				BlockParam.text("TYPE", "minecraft:zombie"));
		reg("world.biome", "world", BlockShape.REPORTER);
		reg("world.ping", "world", BlockShape.REPORTER);

		// ---- 快照（游戏状态 → JSON 文本，只读，见 ARCHITECTURE §26）----
		// 返回的是字符串，可直接接 JSON 那批积木；字段名是契约，只增不删
		regT("world.player_json", "world", BlockShape.REPORTER, "玩家快照");
		regT("world.block_json", "world", BlockShape.REPORTER, "方块 x:{X} y:{Y} z:{Z} 的快照",
				BlockParam.number("X", "0"),
				BlockParam.number("Y", "64"),
				BlockParam.number("Z", "0"));
		regT("world.entities_json", "world", BlockShape.REPORTER,
				"附近 {TYPE} 的快照 半径 {RADIUS} 最多 {LIMIT} 个",
				BlockParam.text("TYPE", "minecraft:zombie"),
				BlockParam.number("RADIUS", "16"),
				BlockParam.number("LIMIT", "32"));
		regT("world.inventory_json", "world", BlockShape.REPORTER, "背包快照");
		regT("world.screen_json", "world", BlockShape.REPORTER, "界面快照");
		regT("world.world_json", "world", BlockShape.REPORTER, "世界快照");
		regT("world.players_json", "world", BlockShape.REPORTER, "在线玩家快照");
		regT("world.snapshot_keys", "world", BlockShape.REPORTER, "{KIND} 的字段",
				BlockParam.fieldOption("KIND", "player", "player", "entity", "item", "screen",
						"block", "block_entity", "world", "players", "scan", "chat"));
		regT("world.scan_json", "world", BlockShape.REPORTER,
				"从 ({X1}, {Y1}, {Z1}) 到 ({X2}, {Y2}, {Z2}) 扫描方块 最多 {LIMIT} 个",
				BlockParam.number("X1", "0"),
				BlockParam.number("Y1", "64"),
				BlockParam.number("Z1", "0"),
				BlockParam.number("X2", "15"),
				BlockParam.number("Y2", "79"),
				BlockParam.number("Z2", "15"),
				BlockParam.number("LIMIT", "4096"));

		// 画框：只影响画面，不改世界状态。category 只决定查看器里的配色（见 ParamLabels），
		// 与积木面板的分组标题无关——面板分组由扩展 js 里的 LABEL 决定。
		regT("region.draw_frame", "region", BlockShape.STACK,
				"从 x:{X1} y:{Y1} z:{Z1} 到 x:{X2} y:{Y2} z:{Z2} 画框 颜色:{COLOR}",
				BlockParam.number("X1", "0"),
				BlockParam.number("Y1", "64"),
				BlockParam.number("Z1", "0"),
				BlockParam.number("X2", "10"),
				BlockParam.number("Y2", "70"),
				BlockParam.number("Z2", "10"),
				BlockParam.text("COLOR", "#33ff66"));
		regT("region.clear_frames", "region", BlockShape.STACK, "清除所有画框");

		// 动作
		regT("action.key", "action", BlockShape.STACK, "按键 {KEY} {PRESSED}",
				BlockParam.fieldOrExpr("KEY", "w"),
				BlockParam.bool("PRESSED", "true"));
		regT("action.open_container_at", "action", BlockShape.STACK, "打开容器 x:{X} y:{Y} z:{Z}",
				BlockParam.number("X", "0"),
				BlockParam.number("Y", "64"),
				BlockParam.number("Z", "0"));
		regT("action.click_slot", "action", BlockShape.STACK, "点击槽位 {INDEX} 用 {BUTTON}",
				BlockParam.number("INDEX", "0"),
				BlockParam.text("BUTTON", "left"));
		regT("action.quick_move_slot", "action", BlockShape.STACK, "快速移动槽位 {INDEX}",
				BlockParam.number("INDEX", "0"));
		regT("action.close_container", "action", BlockShape.STACK, "关闭界面");
		regT("action.move_toward", "action", BlockShape.STACK, "走向 ({X}, {Y}, {Z})",
				BlockParam.number("X", "0"),
				BlockParam.number("Y", "64"),
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
		// 无参数：等待上限由宿主侧固定（走 requestWaitUntil 的条件等待），
		// 与扩展里 breakWait 的「等待方块被挖破」一致。
		regT("action.break_wait", "action", BlockShape.C_SHAPE, "等待方块被挖破");
		regT("action.look", "action", BlockShape.STACK, "视角转到 ({YAW}, {PITCH})",
				BlockParam.number("YAW", "0"),
				BlockParam.number("PITCH", "0"));
		regT("action.look_at", "action", BlockShape.STACK, "看向 ({X}, {Y}, {Z})",
				BlockParam.number("X", "0"),
				BlockParam.number("Y", "64"),
				BlockParam.number("Z", "0"));
		regT("action.look_smooth", "action", BlockShape.STACK, "平滑转到视角 ({YAW}, {PITCH})",
				BlockParam.number("YAW", "0"),
				BlockParam.number("PITCH", "0"));
		regT("action.look_at_smooth", "action", BlockShape.STACK, "平滑看向 ({X}, {Y}, {Z})",
				BlockParam.number("X", "0"),
				BlockParam.number("Y", "64"),
				BlockParam.number("Z", "0"));

		// 聊天与 AI
		// 「最近的聊天」是唯一能把聊天原文交给脚本的出口：whenChat 帽子只做关键词过滤，
		// 文本不进线程。AI 对话靠它拿到玩家说的话，否则只能对着写死的文本自问自答。
		regT("query.last_chat", "sensing", BlockShape.REPORTER, "最近的聊天");
		// 与上一块同一族：前者是「全局最新」，后者是「触发我的那条」（跟着线程走，不会被覆盖）
		regT("query.trigger_chat", "sensing", BlockShape.REPORTER, "触发我的聊天");
		regT("query.chat_history", "sensing", BlockShape.REPORTER, "最近 {COUNT} 条聊天",
				BlockParam.number("COUNT", "10"));
		regT("ai.ask", "action", BlockShape.STACK, "让会话 {SESSION} 回应 {TEXT}",
				BlockParam.text("SESSION", "main"),
				BlockParam.text("TEXT", ""));
		regT("ai.reply", "sensing", BlockShape.REPORTER, "会话 {SESSION} 的最后回复",
				BlockParam.text("SESSION", "main"));
		regT("ai.reset", "action", BlockShape.STACK, "清空会话 {SESSION} 的对话记录",
				BlockParam.text("SESSION", "main"));
		regT("ai.system", "action", BlockShape.STACK, "设置会话 {SESSION} 的系统提示为 {TEXT}",
				BlockParam.text("SESSION", "main"),
				BlockParam.text("TEXT", ""));

		// 调试
		regT("debug.log", "debug", BlockShape.STACK, "输出日志 {TEXT}",
				BlockParam.text("TEXT", ""));

		// 感知
		regT("input.key_pressed", "sensing", BlockShape.BOOLEAN, "按下 {KEY}?",
				BlockParam.fieldText("KEY", "space"));
		// Scratch 的「鼠标按下?」不分按钮，与「按下鼠标 [按钮]?」是两块积木
		regT("input.mouse_down", "sensing", BlockShape.BOOLEAN, "鼠标按下?");
		regT("input.mouse_button_down", "sensing", BlockShape.BOOLEAN, "按下鼠标 {BUTTON}?",
				BlockParam.fieldOption("BUTTON", "left", "left", "right", "middle"));
		reg("sensing.timer", "sensing", BlockShape.REPORTER);
		reg("sensing.reset_timer", "sensing", BlockShape.STACK);
		regT("sensing.current", "sensing", BlockShape.REPORTER, "当前时间的 {UNIT}",
				BlockParam.fieldOption("UNIT", "year",
						"year", "month", "date", "dayofweek", "hour", "minute", "second"));
		reg("sensing.days_since_2000", "sensing", BlockShape.REPORTER);
		regT("sensing.username", "sensing", BlockShape.REPORTER, "用户名");
		regT("sensing.ask", "sensing", BlockShape.STACK, "询问 {QUESTION} 并等待",
				BlockParam.text("QUESTION", ""));
		regT("sensing.answer", "sensing", BlockShape.REPORTER, "回答");

		// 输出（Scratch 的「说 / 思考」映射到动作栏与日志）
		regT("debug.say", "debug", BlockShape.STACK, "说 {TEXT}",
				BlockParam.text("TEXT", ""));
		regT("debug.say_for", "debug", BlockShape.STACK, "说 {TEXT} {TICKS}",
				BlockParam.text("TEXT", ""),
				BlockParam.number("TICKS", "20"));
		// 思考与说是两块不同积木，同样的道理不能合并
		regT("debug.think", "debug", BlockShape.STACK, "思考 {TEXT}",
				BlockParam.text("TEXT", ""));
		regT("debug.think_for", "debug", BlockShape.STACK, "思考 {TEXT} {TICKS}",
				BlockParam.text("TEXT", ""),
				BlockParam.number("TICKS", "20"));

		// 自定义积木
		// 调用行的文本由 NodeChain 现拼（参数个数可变，模板占位符表达不了），
		// 这里登记参数只为说明 sb3 的输入位名，不参与显示
		reg("func.call", "func", BlockShape.STACK,
				BlockParam.fieldText("NAME", "fn"),
				BlockParam.number("ARGC", "3"),
				BlockParam.text("P1", ""),
				BlockParam.text("P2", ""),
				BlockParam.text("P3", ""),
				BlockParam.text("P4", ""),
				BlockParam.text("P5", ""));
		// 参数积木：sb3 把参数名存在 VALUE 字段里（VM 用 {@code getParam(e.VALUE)} 求值），
		// 因此模板直接用 {VALUE}，不要给 VALUE 加全局改名规则
		regT("func.arg_string", "func", BlockShape.REPORTER, "{VALUE}",
				BlockParam.fieldText("VALUE", ""));
		regT("func.arg_boolean", "func", BlockShape.BOOLEAN, "{VALUE}",
				BlockParam.fieldText("VALUE", ""));
		regT("action.broadcast", "action", BlockShape.STACK, "广播 {MSG}",
				BlockParam.text("MSG", ""));

		// ---- JSON ----
		// 对齐 TurboWarp 扩展 skyhigh173JSON（MIT）。参数名必须与扩展文案里的 [占位符] 完全一致：
		// 运行期实参按占位符出现顺序传给 Java，改名字或改顺序都会静默错位（ARCHITECTURE §6.1）。
		regT("json.new", "json", BlockShape.REPORTER, "新建 {json}",
				BlockParam.text("json", "Object"));
		regT("json.get", "json", BlockShape.REPORTER, "{json} 中 {item} 的值",
				BlockParam.text("json", "{\"key\":\"value\"}"),
				BlockParam.text("item", "key"));
		regT("json.set", "json", BlockShape.REPORTER, "把 {json} 中 {item} 设为 {value}",
				BlockParam.text("json", "{\"key\":\"value\"}"),
				BlockParam.text("item", "key"),
				BlockParam.text("value", "new value"));
		regT("json.delete", "json", BlockShape.REPORTER, "删除 {json} 中的 {item}",
				BlockParam.text("json", "{\"key\":\"value\",\"key2\":\"value2\"}"),
				BlockParam.text("item", "key2"));
		// 「键数量」与「数组长度」原本是同一实现的两种叫法，合并成一块：对对象给键数、对数组给元素数
		regT("json.length", "json", BlockShape.REPORTER, "{json} 的长度",
				BlockParam.text("json", "{\"key\":\"value\",\"key2\":\"value2\"}"));
		regT("json.is", "json", BlockShape.BOOLEAN, "{json} 是 {check} 吗?",
				BlockParam.text("json", "{\"key\":\"value\"}"),
				BlockParam.text("check", "valid"));
		regT("json.has_key", "json", BlockShape.BOOLEAN, "{json} 包含键 {key} 吗?",
				BlockParam.text("json", "{\"key\":\"value\"}"),
				BlockParam.text("key", "key2"));
		regT("json.get_path", "json", BlockShape.REPORTER, "{json} 中路径 {path} 的值",
				BlockParam.text("json", "{\"key\":{\"inner\":[true,false]}}"),
				BlockParam.text("path", "[\"key\",\"inner\",1]"));
		regT("json.set_path", "json", BlockShape.REPORTER, "把 {json} 中路径 {path} 的值设为 {data}",
				BlockParam.text("json", "{\"key\":{\"inner\":[true,false]}}"),
				BlockParam.text("path", "[\"key\",\"inner\",1]"),
				BlockParam.text("data", "value"));
		regT("json.minify", "json", BlockShape.REPORTER, "压缩 {json}",
				BlockParam.text("json", " {\"key\" : \"value\" } "));
		regT("json.err_msg", "json", BlockShape.REPORTER, "{json} 的解析错误",
				BlockParam.text("json", "{\"key\":\"value\",}"));
		regT("json.get_all", "json", BlockShape.REPORTER, "{json} 的全部 {Stype}",
				BlockParam.text("json", "{\"key\":\"value\",\"key2\":\"value2\"}"),
				BlockParam.text("Stype", "keys"));
		regT("json.array_push", "json", BlockShape.REPORTER, "向数组 {json} 添加 {item}",
				BlockParam.text("json", "[\"scratch\"]"),
				BlockParam.text("item", "TurboWarp"));
		regT("json.array_set", "json", BlockShape.REPORTER, "把数组 {json} 的第 {pos} 项换成 {item}",
				BlockParam.text("json", "[\"apple\",\"banana\",\"orange\"]"),
				BlockParam.number("pos", "2"),
				BlockParam.text("item", "fav"));
		regT("json.array_insert", "json", BlockShape.REPORTER, "在数组 {json} 的第 {pos} 项插入 {item}",
				BlockParam.text("json", "[\"apple\",\"banana\",\"orange\"]"),
				BlockParam.number("pos", "2"),
				BlockParam.text("item", "pear"));
		regT("json.array_delete", "json", BlockShape.REPORTER, "删除数组 {json} 的第 {item} 项",
				BlockParam.text("json", "[\"apple\",\"banana\",\"orange\"]"),
				BlockParam.number("item", "2"));
		regT("json.array_remove_all", "json", BlockShape.REPORTER, "删除数组 {json} 中所有 {item}",
				BlockParam.text("json", "[\"scratch\",\"a\",\"TurboWarp\",\"a\"]"),
				BlockParam.text("item", "a"));
		regT("json.array_item_num", "json", BlockShape.REPORTER, "{item} 在数组 {json} 中的项号",
				BlockParam.text("item", "scratch"),
				BlockParam.text("json", "[\"scratch\",\"TurboWarp\"]"));
		regT("json.array_fromto", "json", BlockShape.REPORTER, "数组 {json} 的第 {item} 到第 {item2} 项",
				BlockParam.text("json", "[1,2,3,4]"),
				BlockParam.number("item", "2"),
				BlockParam.number("item2", "3"));
		regT("json.array_concat", "json", BlockShape.REPORTER, "拼接数组 {json} 和 {json2}",
				BlockParam.text("json", "[\"scratch\"]"),
				BlockParam.text("json2", "[\"TurboWarp\"]"));
		regT("json.array_map_field", "json", BlockShape.REPORTER, "数组 {json} 中所有对象的 {key} 值",
				BlockParam.text("json", "[{\"id\":12},{\"id\":24}]"),
				BlockParam.text("key", "id"));
		regT("json.array_setlen", "json", BlockShape.REPORTER, "把数组 {json} 的长度设为 {len}",
				BlockParam.text("json", "[\"scratch\",\"TurboWarp\"]"),
				BlockParam.number("len", "1"));
		regT("json.array_create", "json", BlockShape.REPORTER, "按分隔符 {d} 把文本 {text} 拆成数组",
				BlockParam.text("d", ","),
				BlockParam.text("text", "a,b,c"));
		regT("json.array_join", "json", BlockShape.REPORTER, "用分隔符 {d} 把数组 {json} 拼成文本",
				BlockParam.text("d", ","),
				BlockParam.text("json", "[\"a\",\"b\",\"c\"]"));
		regT("json.array_sort", "json", BlockShape.REPORTER, "数组 {list} 按 {order} 排序",
				BlockParam.text("list", "[5.23,214,522,61,1,0]"),
				BlockParam.text("order", "ascending"));
		regT("json.array_textfilter", "json", BlockShape.REPORTER,
				"筛选数组 {list}：文本 {options} {text}，返回 {type}",
				BlockParam.text("list", "[\"apple\",\"banana\",\"orange\"]"),
				BlockParam.text("options", "includes"),
				BlockParam.text("text", "e"),
				BlockParam.text("type", "Array"));
		regT("json.array_analysis", "json", BlockShape.REPORTER, "数组 {list} 的 {analysis}",
				BlockParam.text("list", "[17,1,2017,0,120,14]"),
				BlockParam.text("analysis", "average"));
		// 列表名是普通文本：项目里变量与列表都按名字对应（ARCHITECTURE §17），
		// 用下拉菜单存 id 反而会在改名后失效
		regT("json.vm_getlist", "json", BlockShape.REPORTER, "把列表 {list} 读成数组",
				BlockParam.text("list", "mylist"));
		regT("json.vm_setlist", "json", BlockShape.STACK, "把列表 {list} 设为 {json}",
				BlockParam.text("list", "mylist"),
				BlockParam.text("json", "[\"apple\",\"banana\"]"));
	}

	private static void reg(String id, String category, BlockShape shape, BlockParam... params) {
		BlockRegistry.register(new BlockType(id, category, shape, List.of(params)));
	}
}
