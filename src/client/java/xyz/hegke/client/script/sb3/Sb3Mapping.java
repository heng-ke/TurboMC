package xyz.hegke.client.script.sb3;

import java.util.Map;

/**
 * Scratch opcode 与内部积木类型的映射表。
 *
 * <p>能对应原生积木的一律用原生（用户能直接用 Scratch 的语义与文档）；
 * 游戏交互类积木由本模组的 TurboWarp 扩展提供，opcode 形如 {@code hkscript_<name>}。</p>
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
			Map.entry("hkscript_whenDamaged", "event.damage"),
			Map.entry("hkscript_whenChat", "event.chat"),
			Map.entry("hkscript_whenDeath", "event.death"),

			// ---- 控制 ----
			Map.entry("control_if", "control.if"),
			Map.entry("control_if_else", "control.if_else"),
			Map.entry("control_repeat", "control.repeat"),
			Map.entry("control_forever", "control.forever"),
			Map.entry("control_repeat_until", "control.repeat_until"),
			Map.entry("control_wait", "control.wait"),
			Map.entry("control_wait_until", "control.wait_until"),
			Map.entry("control_stop", "control.stop"),

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
			Map.entry("hkscript_gte", "operator.gte"),

			// ---- 变量 ----
			Map.entry("data_setvariableto", "data.set"),
			Map.entry("data_variable", "data.get"),
			Map.entry("data_changevariableby", "data.change"),

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

			// ---- 自定义积木 ----
			Map.entry("procedures_call", "func.call"),

			// ---- 广播 ----
			Map.entry("event_broadcast", "action.broadcast"),
			Map.entry("event_broadcastandwait", "control.broadcast_wait"),

			// ---- 感知 ----
			Map.entry("sensing_keypressed", "input.key_pressed"),
			Map.entry("sensing_timer", "sensing.timer"),
			Map.entry("sensing_resettimer", "sensing.reset_timer"),
			Map.entry("sensing_current", "sensing.current"),
			Map.entry("sensing_dayssince2000", "sensing.days_since_2000"),

			// ---- 输出（说 / 思考 → 动作栏 + 日志）----
			Map.entry("looks_say", "debug.say"),
			Map.entry("looks_think", "debug.think"),
			Map.entry("looks_sayforsecs", "debug.say_for"),
			Map.entry("looks_thinkforsecs", "debug.think_for"),

			// ---- 世界查询（扩展）----
			Map.entry("hkscript_blockInFront", "world.block_in_front"),
			Map.entry("hkscript_health", "world.health"),
			Map.entry("hkscript_food", "world.food"),
			Map.entry("hkscript_posX", "world.pos_x"),
			Map.entry("hkscript_posY", "world.pos_y"),
			Map.entry("hkscript_posZ", "world.pos_z"),
			Map.entry("hkscript_selectedSlot", "world.selected_slot"),
			Map.entry("hkscript_nearestEntity", "world.nearest_entity"),
			Map.entry("hkscript_nearestEntityX", "world.nearest_entity_x"),
			Map.entry("hkscript_nearestEntityY", "world.nearest_entity_y"),
			Map.entry("hkscript_nearestEntityZ", "world.nearest_entity_z"),
			Map.entry("hkscript_nearestHostile", "world.nearest_hostile"),
			Map.entry("hkscript_onGround", "world.on_ground"),
			Map.entry("hkscript_itemCount", "world.item_count"),

			// ---- 动作（扩展）----
			Map.entry("hkscript_key", "action.key"),
			Map.entry("hkscript_chat", "action.chat"),
			Map.entry("hkscript_command", "action.command"),
			Map.entry("hkscript_tap", "action.tap"),
			Map.entry("hkscript_mouse", "action.mouse"),
			Map.entry("hkscript_click", "action.click"),
			Map.entry("hkscript_respawn", "action.respawn"),
			Map.entry("hkscript_attackNearest", "action.attack_nearest"),
			Map.entry("hkscript_attackNearestHostile", "action.attack_nearest_hostile"),
			Map.entry("hkscript_openContainer", "action.open_container"),
			Map.entry("hkscript_moveToward", "action.move_toward"),
			Map.entry("hkscript_look", "action.look"),
			Map.entry("hkscript_lookAt", "action.look_at"),
			Map.entry("hkscript_stop", "action.stop"),

			// ---- 物品与方块查询（扩展）----
			Map.entry("hkscript_heldItem", "world.held_item"),
			Map.entry("hkscript_heldDurability", "world.held_durability"),
			Map.entry("hkscript_frontBlock", "world.front_block"),
			Map.entry("hkscript_blockBelow", "world.block_below"),
			Map.entry("hkscript_blockAt", "world.block_at"),
			Map.entry("hkscript_isDay", "world.is_day"),
			Map.entry("hkscript_dimension", "world.dimension"),
			Map.entry("hkscript_inWater", "world.in_water"),
			Map.entry("hkscript_inLava", "world.in_lava"),
			Map.entry("hkscript_nearestEntityName", "world.nearest_entity_name"),
			Map.entry("hkscript_xpLevel", "world.xp_level"),
			Map.entry("hkscript_inventoryFull", "world.inventory_full"),
			Map.entry("hkscript_entityCount", "world.entity_count"),
			Map.entry("hkscript_biome", "world.biome"),
			Map.entry("hkscript_ping", "world.ping"),
			Map.entry("hkscript_breakWait", "action.break_wait"),
			Map.entry("hkscript_isMining", "world.is_mining"),
			Map.entry("hkscript_miningProgress", "world.mining_progress"),

			// ---- 建造与界面（扩展）----
			Map.entry("hkscript_placeBlock", "action.place_block"),
			Map.entry("hkscript_sleep", "action.sleep"),

			// ---- 调试（扩展）----
			Map.entry("hkscript_log", "debug.log")
	);

	public static String typeOf(String opcode) {
		return TYPES.get(opcode);
	}

	/**
	 * 内部积木类型 → sb3 opcode 的反向查表。
	 *
	 * <p><b>当前没有调用方</b>：脚本只从 sb3 导入到查看器，没有「写回 sb3」的路径。
	 * 保留它是为了映射表成对完整——将来若需要导出工程，可直接复用，不必重做一张表。
	 * 若确认不再需要，可连同 {@code PREFERRED} 与 {@code REVERSE} 一起删除。</p>
	 *
	 * <p>一对多的情况由 {@code PREFERRED} 钉死（如 {@code data.set} 固定反查到
	 * {@code data_setvariableto}），避免依赖 Map 的迭代顺序。</p>
	 */
	public static String opcodeOf(String type) {
		String preferred = PREFERRED.get(type);
		return preferred != null ? preferred : REVERSE.get(type);
	}

	/**
	 * 一个内部类型对应多个 opcode 时的首选。
	 *
	 * <p>{@code Map.ofEntries} 的迭代顺序不保证，取首个会随机落到近义积木上。
	 * 目前映射表已做到一对一（「增加变量」「思考」都有独立类型），这里留作兜底。</p>
	 */
	private static final Map<String, String> PREFERRED = Map.of(
			"data.set", "data_setvariableto",
			"data.change", "data_changevariableby",
			"debug.say", "looks_say",
			"debug.say_for", "looks_sayforsecs",
			"debug.think", "looks_think",
			"debug.think_for", "looks_thinkforsecs");

	private static final Map<String, String> REVERSE = buildReverse();

	private static Map<String, String> buildReverse() {
		Map<String, String> reverse = new java.util.LinkedHashMap<>();
		TYPES.forEach((opcode, type) -> reverse.putIfAbsent(type, opcode));
		PREFERRED.forEach(reverse::put);
		return Map.copyOf(reverse);
	}

	public static boolean isSupported(String opcode) {
		return TYPES.containsKey(opcode);
	}

	/**
	 * Scratch 的帽子积木：出现在工作区顶部的脚本入口。扩展定义的事件积木同样视作帽子。
	 */
	public static boolean isHat(String opcode) {
		if (opcode == null) {
			return false;
		}
		return opcode.startsWith("event_when") || opcode.equals("hkscript_whenDamaged")
				|| opcode.equals("hkscript_whenChat") || opcode.equals("hkscript_whenDeath");
	}

	/**
	 * 输入槽改名。Scratch 各积木的输入名并不统一，这里统一收敛到内部命名。
	 *
	 * <p>这是全项目唯一一份输入名映射，导入器直接调用。注意 ITEM 只在列表积木
	 * （{@code data_*}）里改名为 VALUE；扩展积木 itemCount 的参数本身就叫 ITEM，不能一起改。</p>
	 */
	public static String inputName(String opcode, String scratchName) {
		return switch (scratchName) {
			case "OPERAND1", "FROM", "NUM", "STRING1" -> "A";
			case "OPERAND2", "TO", "STRING2" -> "B";
			// letter_of 的 STRING 是第二个参数，length 的 STRING 是唯一参数
			case "STRING" -> "operator_length".equals(opcode) ? "A" : "B";
			case "LETTER" -> "A";
			case "ITEM" -> opcode != null && opcode.startsWith("data_") ? "VALUE" : "ITEM";
			case "BROADCAST_INPUT" -> "MSG";
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
			default -> scratchName;
		};
	}
}
