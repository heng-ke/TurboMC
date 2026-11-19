package xyz.turbomc.client.script.sb3;

import java.util.Map;

/**
 * Scratch opcode 与内部积木类型的映射表。
 *
 * <p>能对应原生积木的一律用原生（用户能直接用 Scratch 的语义与文档）；
 * 游戏交互类积木由本模组的 TurboWarp 扩展提供，opcode 形如 {@code turbomc_<name>}。</p>
 *
 * <p>不映射：运动 / 外观 / 声音 / 画笔（2D 舞台无法对应 3D 世界）、
 * 克隆（语义不等价）、Scratch 自定义积木（参数通道模型不同）。</p>
 */
public final class Sb3Mapping {
	private Sb3Mapping() {
	}

	private static final Map<String, String> TYPES = Map.ofEntries(
			// ---- 事件 ----
			Map.entry("event_whenflagclicked", "event.tick"),
			Map.entry("event_whenkeypressed", "event.key"),
			Map.entry("event_whenbroadcastreceived", "event.broadcast"),
			Map.entry("event_whengreaterthan", "event.when_greater"),
			Map.entry("turbomc_whenDamaged", "event.damage"),
			Map.entry("turbomc_whenChat", "event.chat"),
			Map.entry("turbomc_whenDeath", "event.death"),

			// ---- 控制 ----
			Map.entry("control_if", "control.if"),
			Map.entry("control_if_else", "control.if_else"),
			Map.entry("control_repeat", "control.repeat"),
			Map.entry("control_forever", "control.forever"),
			Map.entry("control_repeat_until", "control.repeat_until"),
			Map.entry("control_wait", "control.wait"),
			Map.entry("control_wait_until", "control.wait_until"),
			Map.entry("control_stop", "control.stop"),
			// while / for_each 是纯 VM 控制流，宿主无需参与
			Map.entry("control_while", "control.while"),
			Map.entry("control_for_each", "control.for_each"),

			// ---- 运算 ----
			Map.entry("operator_lt", "operator.lt"),
			Map.entry("operator_gt", "operator.gt"),
			Map.entry("operator_equals", "operator.eq"),
			Map.entry("operator_add", "operator.add"),
			Map.entry("operator_subtract", "operator.subtract"),
			Map.entry("operator_multiply", "operator.multiply"),
			Map.entry("operator_divide", "operator.divide"),
			Map.entry("operator_mod", "operator.mod"),
			Map.entry("operator_and", "operator.and"),
			Map.entry("operator_or", "operator.or"),
			Map.entry("operator_not", "operator.not"),
			Map.entry("operator_random", "operator.random"),
			Map.entry("operator_round", "operator.round"),
			Map.entry("operator_join", "operator.join"),
			Map.entry("operator_letter_of", "operator.letter_of"),
			Map.entry("operator_length", "operator.length"),
			Map.entry("operator_contains", "operator.contains"),
			Map.entry("operator_mathop", "operator.mathop"),

			// ---- 变量 ----
			Map.entry("data_setvariableto", "data.set"),
			Map.entry("data_variable", "data.get"),
			Map.entry("data_changevariableby", "data.change"),
			Map.entry("data_showvariable", "data.show_var"),
			Map.entry("data_hidevariable", "data.hide_var"),

			// ---- 列表 ----
			Map.entry("data_addtolist", "list.add"),
			Map.entry("data_deleteoflist", "list.delete"),
			Map.entry("data_deletealloflist", "list.clear"),
			Map.entry("data_insertatlist", "list.insert"),
			Map.entry("data_replaceitemoflist", "list.replace"),
			Map.entry("data_itemoflist", "list.item"),
			Map.entry("data_lengthoflist", "list.length"),
			Map.entry("data_listcontainsitem", "list.contains"),
			Map.entry("data_itemnumoflist", "list.item_num"),
			Map.entry("data_listcontents", "list.contents"),
			Map.entry("data_showlist", "data.show_list"),
			Map.entry("data_hidelist", "data.hide_list"),

			// ---- 自定义积木 ----
			Map.entry("procedures_call", "func.call"),
			// 参数由 VM 自身的 procedures 机制求值，这里只需让查看器认得
			Map.entry("argument_reporter_string_number", "func.arg_string"),
			Map.entry("argument_reporter_boolean", "func.arg_boolean"),

			// ---- 广播 ----
			Map.entry("event_broadcast", "action.broadcast"),
			Map.entry("event_broadcastandwait", "control.broadcast_wait"),

			// ---- 感知 ----
			Map.entry("sensing_keypressed", "input.key_pressed"),
			Map.entry("sensing_mousedown", "input.mouse_down"),
			Map.entry("sensing_timer", "sensing.timer"),
			Map.entry("sensing_resettimer", "sensing.reset_timer"),
			Map.entry("sensing_current", "sensing.current"),
			Map.entry("sensing_dayssince2000", "sensing.days_since_2000"),
			Map.entry("sensing_username", "sensing.username"),
			Map.entry("sensing_askandwait", "sensing.ask"),
			Map.entry("sensing_answer", "sensing.answer"),

			// ---- 输出（说 / 思考 → 动作栏 + 日志）----
			Map.entry("looks_say", "debug.say"),
			Map.entry("looks_think", "debug.think"),
			Map.entry("looks_sayforsecs", "debug.say_for"),
			Map.entry("looks_thinkforsecs", "debug.think_for"),

			// ---- 聊天与 AI（扩展）----
			Map.entry("turbomc_lastChat", "query.last_chat"),
			Map.entry("turbomc_triggerChat", "query.trigger_chat"),
			Map.entry("turbomc_aiAsk", "ai.ask"),
			Map.entry("turbomc_aiReply", "ai.reply"),
			Map.entry("turbomc_aiReset", "ai.reset"),
			Map.entry("turbomc_aiSystem", "ai.system"),

			// ---- 世界查询（扩展）----
			Map.entry("turbomc_blockInFront", "world.block_in_front"),
			Map.entry("turbomc_health", "world.health"),
			Map.entry("turbomc_food", "world.food"),
			Map.entry("turbomc_posX", "world.pos_x"),
			Map.entry("turbomc_posY", "world.pos_y"),
			Map.entry("turbomc_posZ", "world.pos_z"),
			Map.entry("turbomc_selectedSlot", "world.selected_slot"),
			Map.entry("turbomc_nearestEntity", "world.nearest_entity"),
			Map.entry("turbomc_nearestEntityX", "world.nearest_entity_x"),
			Map.entry("turbomc_nearestEntityY", "world.nearest_entity_y"),
			Map.entry("turbomc_nearestEntityZ", "world.nearest_entity_z"),
			Map.entry("turbomc_onGround", "world.on_ground"),
			Map.entry("turbomc_itemCount", "world.item_count"),

			// ---- 动作（扩展）----
			Map.entry("turbomc_key", "action.key"),
			Map.entry("turbomc_mouseDown", "input.mouse_button_down"),
			Map.entry("turbomc_chat", "action.chat"),
			Map.entry("turbomc_command", "action.command"),
			Map.entry("turbomc_tap", "action.tap"),
			Map.entry("turbomc_click", "action.click"),
			Map.entry("turbomc_respawn", "action.respawn"),
			Map.entry("turbomc_openContainerAt", "action.open_container_at"),
			Map.entry("turbomc_clickSlot", "action.click_slot"),
			Map.entry("turbomc_quickMoveSlot", "action.quick_move_slot"),
			Map.entry("turbomc_closeContainer", "action.close_container"),
			Map.entry("turbomc_containerSlots", "world.container_slots"),
			Map.entry("turbomc_slotItem", "world.slot_item"),
			Map.entry("turbomc_slotAmount", "world.slot_amount"),
			Map.entry("turbomc_firstSlotOf", "world.first_slot_of"),
			Map.entry("turbomc_moveToward", "action.move_toward"),
			Map.entry("turbomc_look", "action.look"),
			Map.entry("turbomc_lookAt", "action.look_at"),
			Map.entry("turbomc_lookSmooth", "action.look_smooth"),
			Map.entry("turbomc_lookAtSmooth", "action.look_at_smooth"),
			Map.entry("turbomc_stop", "action.stop"),

			// ---- 物品与方块查询（扩展）----
			Map.entry("turbomc_heldItem", "world.held_item"),
			Map.entry("turbomc_heldDurability", "world.held_durability"),
			Map.entry("turbomc_frontBlock", "world.front_block"),
			Map.entry("turbomc_blockBelow", "world.block_below"),
			Map.entry("turbomc_blockAt", "world.block_at"),
			// 准星方块坐标：命中给命中格，未命中给视线尽头那一格（见 WorldQueries.aimBlockPos）
			Map.entry("turbomc_aimX", "world.aim_x"),
			Map.entry("turbomc_aimY", "world.aim_y"),
			Map.entry("turbomc_aimZ", "world.aim_z"),
			Map.entry("turbomc_isDay", "world.is_day"),
			Map.entry("turbomc_dimension", "world.dimension"),
			Map.entry("turbomc_inWater", "world.in_water"),
			Map.entry("turbomc_inLava", "world.in_lava"),
			Map.entry("turbomc_nearestEntityName", "world.nearest_entity_name"),
			Map.entry("turbomc_xpLevel", "world.xp_level"),
			Map.entry("turbomc_inventoryFull", "world.inventory_full"),
			Map.entry("turbomc_entityCount", "world.entity_count"),
			Map.entry("turbomc_biome", "world.biome"),
			Map.entry("turbomc_ping", "world.ping"),
			Map.entry("turbomc_breakWait", "action.break_wait"),
			Map.entry("turbomc_isMining", "world.is_mining"),
			Map.entry("turbomc_miningProgress", "world.mining_progress"),

			// ---- 画框（扩展）----
			Map.entry("turbomc_drawFrame", "region.draw_frame"),
			Map.entry("turbomc_clearFrames", "region.clear_frames"),

			// ---- 调试（扩展）----
			Map.entry("turbomc_log", "debug.log"),

			// ---- JSON（扩展，对齐 TurboWarp 的 skyhigh173JSON）----
			Map.entry("turbomc_jsonNew", "json.new"),
			Map.entry("turbomc_jsonGet", "json.get"),
			Map.entry("turbomc_jsonSet", "json.set"),
			Map.entry("turbomc_jsonDelete", "json.delete"),
			Map.entry("turbomc_jsonLength", "json.length"),
			Map.entry("turbomc_jsonIs", "json.is"),
			Map.entry("turbomc_jsonHasKey", "json.has_key"),
			Map.entry("turbomc_jsonGetPath", "json.get_path"),
			Map.entry("turbomc_jsonSetPath", "json.set_path"),
			Map.entry("turbomc_jsonMinify", "json.minify"),
			Map.entry("turbomc_jsonErrMsg", "json.err_msg"),
			Map.entry("turbomc_jsonGetAll", "json.get_all"),
			Map.entry("turbomc_jsonArrayPush", "json.array_push"),
			Map.entry("turbomc_jsonArraySet", "json.array_set"),
			Map.entry("turbomc_jsonArrayInsert", "json.array_insert"),
			Map.entry("turbomc_jsonArrayDelete", "json.array_delete"),
			Map.entry("turbomc_jsonArrayRemoveAll", "json.array_remove_all"),
			Map.entry("turbomc_jsonArrayItemNum", "json.array_item_num"),
			Map.entry("turbomc_jsonArrayFromTo", "json.array_fromto"),
			Map.entry("turbomc_jsonArrayConcat", "json.array_concat"),
			Map.entry("turbomc_jsonArrayMapField", "json.array_map_field"),
			Map.entry("turbomc_jsonArraySetLen", "json.array_setlen"),
			Map.entry("turbomc_jsonArrayCreate", "json.array_create"),
			Map.entry("turbomc_jsonArrayJoin", "json.array_join"),
			Map.entry("turbomc_jsonArraySort", "json.array_sort"),
			Map.entry("turbomc_jsonArrayTextFilter", "json.array_textfilter"),
			Map.entry("turbomc_jsonArrayAnalysis", "json.array_analysis"),
			Map.entry("turbomc_jsonVmGetList", "json.vm_getlist"),
			Map.entry("turbomc_jsonVmSetList", "json.vm_setlist")
	);

	public static String typeOf(String opcode) {
		return TYPES.get(opcode);
	}

	public static boolean isSupported(String opcode) {
		return TYPES.containsKey(opcode);
	}

	/**
	 * 输入槽改名。Scratch 各积木的输入名并不统一，这里统一收敛到内部命名。
	 *
	 * <p>这是全项目唯一一份输入名映射，导入器直接调用。注意 ITEM 只在列表积木
	 * （{@code data_*}）里改名为 VALUE；扩展积木 itemCount 的参数本身就叫 ITEM，不能一起改。</p>
	 *
	 * <p>算术积木用的是 {@code NUM1/NUM2}（不是 OPERAND1/OPERAND2），漏了这两个
	 * 会让「加 / 减 / 乘 / 除 / 取余」的摘要显示成 {@code ? × ?}——模板里的
	 * {@code {A}}、{@code {B}} 取不到参数。</p>
	 */
	public static String inputName(String opcode, String scratchName) {
		return switch (scratchName) {
			case "OPERAND1", "FROM", "NUM", "NUM1", "STRING1" -> "A";
			case "OPERAND2", "TO", "NUM2", "STRING2" -> "B";
			// letter_of 的 STRING 是第二个参数，length 的 STRING 是唯一参数
			case "STRING" -> "operator_length".equals(opcode) ? "A" : "B";
			case "LETTER" -> "A";
			case "ITEM" -> opcode != null && opcode.startsWith("data_") ? "VALUE" : "ITEM";
			case "BROADCAST_INPUT" -> "MSG";
			// sensing_keypressed 的键是「输入」而不是字段（值装在下拉影子块 sensing_keyoptions 里），
			// 不映射就凑不上模板的 {KEY}，会显示成「按下 ? ?」
			case "KEY_OPTION" -> "KEY";
			case "DURATION", "SECS" -> "TICKS";
			case "MESSAGE" -> "TEXT";
			case "ONOFF", "STATE" -> "PRESSED";
			default -> scratchName;
		};
	}

	/**
	 * 字段改名。Scratch 用 VARIABLE / KEY_OPTION / BROADCAST_OPTION，内部统一。
	 *
	 * <p>这是全项目唯一一份字段名映射，导入器直接调用；不要在别处再写一份，
	 * 两份规则一旦漂移就会表现为「某个积木的参数显示成英文名」。</p>
	 */
	public static String fieldName(String opcode, String scratchName) {
		return switch (scratchName) {
			case "VARIABLE" -> "VAR";
			case "KEY_OPTION" -> "KEY";
			case "BROADCAST_OPTION" -> "MSG";
			case "OPERATOR" -> "OP";
			case "CURRENTMENU" -> "UNIT";
			case "STOP_OPTION" -> "OP";
			case "WHENGREATERTHANMENU" -> "MENU";
			default -> scratchName;
		};
	}
}
