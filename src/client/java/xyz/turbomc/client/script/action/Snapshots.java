package xyz.turbomc.client.script.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * 快照的 JSON 拼装：**只收原语，不碰任何 Minecraft 类型**。
 *
 * <p>取值在 {@code WorldQueries} 里读 {@code MinecraftClient}，值搬进下面的 record 之后交给这里
 * 拼字符串。这样字段名、顺序、数值格式都能在纯 JVM 里断言（同 {@code EntityFilter} 把解析与
 * 判定拆开的手法），不必开客户端——见 {@code ARCHITECTURE.md} §26 第 4 条。</p>
 *
 * <p><b>键名是契约</b>：脚本会硬编码 {@code health}、{@code x} 这些名字，所以只增不删。
 * 固定字段一律扁平（{@code x} / {@code motion_x}），理由是可组合：{@code json.array_map_field}
 * 只能取顶层键，嵌套的 {@code pos.x} 在数组上没法批量映射。随方块属性、NBT、物品条目变化的
 * 部分只能是子对象，那些键本来就由游戏决定。</p>
 */
public final class Snapshots {
	private Snapshots() {
	}

	/** 三维量。JSON 里展开成 {@code x/y/z} 或 {@code motion_x/motion_y/motion_z}。 */
	public record Vec(double x, double y, double z) {
	}

	/** 状态效果。{@code id} 是效果注册名。 */
	public record Effect(String id, int amplifier, int duration) {
	}

	/**
	 * 玩家快照的原料。
	 *
	 * @param pos    当前坐标
	 * @param motion 上一 tick 的位移（格/tick），不是服务端速度字段——客户端自己的速度基本恒为 0
	 */
	public record Player(String name, String uuid, double health, double maxHealth, double absorption,
						 int food, double saturation, int xpLevel, double xpProgress, int armor, int air,
						 String gamemode, String dimension, Vec pos, Vec motion, double yaw, double pitch,
						 boolean onGround, boolean inWater, boolean inLava, int selectedSlot,
						 List<Effect> effects) {
	}

	/**
	 * 单个实体的快照原料。
	 *
	 * @param type       实体注册名（{@code minecraft:zombie}），用于精确匹配
	 * @param name       本地化名称（「僵尸」），用于显示
	 * @param customName 命名牌名称，没有时为空串
	 * @param distance   与玩家的距离
	 * @param motion     上一 tick 的位移（格/tick）
	 */
	public record Entity(String type, String name, String customName, String uuid, double distance,
						 Vec pos, Vec motion, double health, double maxHealth, boolean baby,
						 boolean onGround) {
	}

	/**
	 * 物品条目。
	 *
	 * @param slot         槽位号：背包是 0–40 的 inventory 索引，界面是 ScreenHandler 的槽位号
	 *                     （与「点击槽位」同一套编号，可直接拿去点）
	 * @param enchantments 附魔：附魔 id → 等级；没有附魔时为空表
	 */
	public record Item(int slot, String id, int count, int damage, int maxDamage, String customName,
					   Map<String, Integer> enchantments) {
	}

	/** 界面快照的条目：多一个「这格是不是玩家自己的背包」。 */
	public record ScreenItem(Item item, boolean playerSlot) {
	}

	/**
	 * 在线玩家的一项。
	 *
	 * <p>只有这四项可靠：坐标、血量、背包对**其他**玩家是服务端不下发的数据，
	 * 客户端无论如何都读不到。</p>
	 */
	public record PlayerEntry(String uuid, String name, int latency, String gamemode) {
	}

	/** 区域扫描里的一个方块。{@code id} 是方块注册名。 */
	public record BlockEntry(int x, int y, int z, String id) {
	}

	/**
	 * 区域扫描的结果。
	 *
	 * @param truncated 是否被截断：体积超上限或非空气方块超过 {@code limit} 时为真，
	 *                  表示「没扫完/没列全」，脚本应缩小范围或提高上限
	 */
	public record Scan(int x1, int y1, int z1, int x2, int y2, int z2, boolean truncated,
					   List<BlockEntry> blocks) {
	}

	/**
	 * 世界快照的原料。
	 *
	 * @param time       当天已过 tick（0–23999），对应 F3 的时间
	 * @param day        第几天，1 基（与 F3 的 Day 一致）
	 * @param moonPhase  月相 0–7，0 是满月
	 * @param light      玩家所在格的光照等级（取方块光与天空光的较大值）
	 */
	public record WorldFacts(double time, int day, int moonPhase, boolean raining, boolean thundering,
							 String dimension, String biome, int light) {
	}

	/**
	 * 方块实体（箱子、告示牌、熔炉…）的内容。
	 *
	 * @param type       方块实体类型 id
	 * @param items      容器槽位内容，最多 {@link #ITEM_LIMIT} 条
	 * @param itemsTotal 容器的真实槽位数，用于判断是否被截断
	 * @param nbt        标量 NBT 字段（数字/字符串），嵌套与列表一律跳过；熔炉的 {@code CookTime} 走这里
	 * @param textFront  告示牌正面文本，行间以 {@code \n} 连接；非告示牌为空串
	 * @param textBack   告示牌背面文本
	 */
	public record BlockEntity(String type, List<Item> items, int itemsTotal, Map<String, Object> nbt,
							  String textFront, String textBack) {
	}

	/** 方块快照的原料。{@code state} 是方块状态的属性表（朝向、含水…）。 */
	public record Block(String id, int x, int y, int z, Map<String, String> state, double hardness,
						boolean air, boolean solid, boolean fluid, int light, String biome,
						BlockEntity blockEntity) {
	}

	// ---- 键清单：唯一事实来源 ----
	// 写入顺序与下面这些清单必须一致，由 SnapshotJsonTest 断言；README 的「键：」行也由
	// SnapshotKeysDocTest 与这里比对。改动键时三处会同时失败——这是刻意的，避免各说各话。
	// 「快照字段」积木（world.snapshot_keys）直接返回这些清单，用户可以自助查询。

	public static final List<String> PLAYER_KEYS = List.of(
			"name", "uuid", "health", "max_health", "absorption", "food", "saturation",
			"xp_level", "xp_progress", "armor", "air", "gamemode", "dimension",
			"x", "y", "z", "motion_x", "motion_y", "motion_z", "yaw", "pitch",
			"on_ground", "in_water", "in_lava", "selected_slot", "effects");

	public static final List<String> ENTITY_KEYS = List.of(
			"type", "name", "custom_name", "uuid", "distance",
			"x", "y", "z", "motion_x", "motion_y", "motion_z",
			"health", "max_health", "is_baby", "on_ground");

	public static final List<String> ITEM_KEYS = List.of(
			"slot", "id", "count", "damage", "max_damage", "custom_name", "enchantments");

	/** 界面里的物品条目比背包条目多一个 `player_slot`。 */
	public static final List<String> SCREEN_ITEM_KEYS = List.of(
			"slot", "id", "count", "damage", "max_damage", "custom_name", "enchantments",
			"player_slot");

	public static final List<String> SCREEN_KEYS = List.of("type", "slots", "items");

	public static final List<String> BLOCK_KEYS = List.of(
			"id", "x", "y", "z", "state", "hardness", "is_air", "is_solid", "is_fluid",
			"light", "biome", "block_entity");

	public static final List<String> BLOCK_ENTITY_KEYS = List.of(
			"type", "items", "items_total", "nbt", "text_front", "text_back");

	public static final List<String> WORLD_KEYS = List.of(
			"time", "day", "moon_phase", "raining", "thundering", "dimension", "biome", "light");

	public static final List<String> PLAYER_ENTRY_KEYS = List.of(
			"uuid", "name", "latency", "gamemode");

	public static final List<String> SCAN_KEYS = List.of(
			"x1", "y1", "z1", "x2", "y2", "z2", "truncated", "blocks");

	/**
	 * 菜单值 → 键清单。「快照字段」积木用它，返回值是字符串数组。
	 *
	 * <p>{@code chat} 没有键（它是字符串数组），给空数组而不是报错：脚本拿它做「有没有键」
	 * 的判断时，空数组是最容易处理的结果。</p>
	 */
	public static List<String> keysOf(String kind) {
		return switch (kind == null ? "" : kind.trim().toLowerCase(java.util.Locale.ROOT)) {
			case "player" -> PLAYER_KEYS;
			case "entity" -> ENTITY_KEYS;
			case "item" -> ITEM_KEYS;
			case "screen" -> SCREEN_KEYS;
			case "block" -> BLOCK_KEYS;
			case "block_entity" -> BLOCK_ENTITY_KEYS;
			case "world" -> WORLD_KEYS;
			case "players" -> PLAYER_ENTRY_KEYS;
			case "scan" -> SCAN_KEYS;
			default -> List.of();
		};
	}

	/** 单次快照最多带出的容器槽位数：大箱子 27 格、潜影盒 27 格，64 足够覆盖常见容器。 */
	public static final int ITEM_LIMIT = 64;
	/** 标量 NBT 的条数上限，防止模组方块实体带出上百个键。 */
	public static final int NBT_LIMIT = 32;
	/**
	 * 界面快照的槽位上限定为 128：原版最大界面是「大箱子 + 玩家背包」90 格，
	 * 128 留出模组容器的余量；超出时 {@code slots} 仍给真实总数，脚本能看出被截断。
	 */
	public static final int SCREEN_LIMIT = 128;

	// ---- 玩家 ----

	/** 玩家快照；{@code null} 表示当前没有玩家（无客户端），返回空对象而不是空串——空串解析会失败。 */
	public static String player(Player p) {
		return JsonText.stringify(playerObject(p));
	}

	/** 玩家快照对象；远程桥直接挂进快照，游戏内再序列化。 */
	public static JsonObject playerObject(Player p) {
		if (p == null) {
			return new JsonObject();
		}
		JsonObject o = new JsonObject();
		o.addProperty("name", p.name());
		o.addProperty("uuid", p.uuid());
		o.addProperty("health", p.health());
		o.addProperty("max_health", p.maxHealth());
		o.addProperty("absorption", p.absorption());
		o.addProperty("food", p.food());
		o.addProperty("saturation", p.saturation());
		o.addProperty("xp_level", p.xpLevel());
		o.addProperty("xp_progress", p.xpProgress());
		o.addProperty("armor", p.armor());
		o.addProperty("air", p.air());
		o.addProperty("gamemode", p.gamemode());
		o.addProperty("dimension", p.dimension());
		o.addProperty("x", p.pos().x());
		o.addProperty("y", p.pos().y());
		o.addProperty("z", p.pos().z());
		o.addProperty("motion_x", p.motion().x());
		o.addProperty("motion_y", p.motion().y());
		o.addProperty("motion_z", p.motion().z());
		o.addProperty("yaw", p.yaw());
		o.addProperty("pitch", p.pitch());
		o.addProperty("on_ground", p.onGround());
		o.addProperty("in_water", p.inWater());
		o.addProperty("in_lava", p.inLava());
		o.addProperty("selected_slot", p.selectedSlot());
		JsonArray effects = new JsonArray();
		for (Effect effect : p.effects()) {
			JsonObject e = new JsonObject();
			e.addProperty("id", effect.id());
			e.addProperty("amplifier", effect.amplifier());
			e.addProperty("duration", effect.duration());
			effects.add(e);
		}
		o.add("effects", effects);
		return o;
	}

	// ---- 实体 ----

	/** 实体快照（单个）。 */
	public static String entity(Entity entity) {
		return JsonText.stringify(entityObject(entity));
	}

	/**
	 * 实体快照（数组）。
	 *
	 * <p>刻意返回裸数组而不是 {@code {count:.., list:[..]}}：裸数组能直接喂 {@code json.length}、
	 * {@code json.array_map_field}、{@code 把列表设为}，包一层就全都用不了。截断由调用方按
	 * {@code LIMIT} 参数控制，数组长度等于 LIMIT 即为「可能还有更多」。</p>
	 */
	public static String entities(List<Entity> list) {
		JsonArray array = new JsonArray();
		if (list != null) {
			for (Entity entity : list) {
				array.add(entityObject(entity));
			}
		}
		return JsonText.stringify(array);
	}

	/** 单个实体的对象形态；远程桥直接挂进快照，与游戏内 `实体快照` 键序一致。 */
	public static JsonObject entityObject(Entity e) {
		JsonObject o = new JsonObject();
		o.addProperty("type", e.type());
		o.addProperty("name", e.name());
		o.addProperty("custom_name", e.customName());
		o.addProperty("uuid", e.uuid());
		o.addProperty("distance", e.distance());
		o.addProperty("x", e.pos().x());
		o.addProperty("y", e.pos().y());
		o.addProperty("z", e.pos().z());
		o.addProperty("motion_x", e.motion().x());
		o.addProperty("motion_y", e.motion().y());
		o.addProperty("motion_z", e.motion().z());
		o.addProperty("health", e.health());
		o.addProperty("max_health", e.maxHealth());
		o.addProperty("is_baby", e.baby());
		o.addProperty("on_ground", e.onGround());
		return o;
	}

	// ---- 背包与界面 ----

	/**
	 * 背包快照：裸数组，只列**非空**槽位，按槽位号升序。
	 *
	 * <p>空槽不占条目：41 格里通常只有十来件东西，列空槽既费体积又不好遍历；
	 * 「哪些格是空的」用 41 减去数组长度即可。槽位号 0–35 主背包、36–39 盔甲、40 副手，
	 * 与游戏内 inventory 索引一致。</p>
	 */
	public static String inventory(List<Item> items) {
		return JsonText.stringify(inventoryArray(items));
	}

	/**
	 * 背包快照的数组形态。
	 *
	 * <p>远程桥直接把它挂进每 tick 的快照里（而不是再套一层字符串），
	 * 避免「JSON 里的 JSON」带来的转义开销与二次解析。</p>
	 */
	public static JsonArray inventoryArray(List<Item> items) {
		JsonArray array = new JsonArray();
		if (items != null) {
			for (Item item : items) {
				array.add(itemObject(item));
			}
		}
		return array;
	}

	/**
	 * 界面快照：当前打开界面的全部槽位。
	 *
	 * <p>槽位号取自 {@code ScreenHandler}，与「点击槽位」完全同一套编号——这是它单独成块、
	 * 而不复用方块实体内容的原因（方块实体是容器内部索引，且同步有延迟）。
	 * {@code slots} 是界面槽位总数，{@code items} 只列非空格，两者相减得空格数。</p>
	 */
	public static String screen(String type, int slots, List<ScreenItem> items) {
		return JsonText.stringify(screenObject(type, slots, items));
	}

	/** 界面快照的对象形态；远程桥直接挂进快照，游戏内再序列化成字符串。 */
	public static JsonObject screenObject(String type, int slots, List<ScreenItem> items) {
		JsonObject o = new JsonObject();
		o.addProperty("type", type);
		o.addProperty("slots", slots);
		JsonArray array = new JsonArray();
		if (items != null) {
			for (ScreenItem entry : items) {
				JsonObject item = itemObject(entry.item());
				item.addProperty("player_slot", entry.playerSlot());
				array.add(item);
			}
		}
		o.add("items", array);
		return o;
	}

	// ---- 世界 ----

	/** 世界快照；{@code null} 表示没有世界，返回空对象。 */
	public static String world(WorldFacts facts) {
		return JsonText.stringify(worldObject(facts));
	}

	/** 世界快照对象；{@code null} 表示没有世界，返回空对象。 */
	public static JsonObject worldObject(WorldFacts facts) {
		if (facts == null) {
			return new JsonObject();
		}
		JsonObject o = new JsonObject();
		o.addProperty("time", facts.time());
		o.addProperty("day", facts.day());
		o.addProperty("moon_phase", facts.moonPhase());
		o.addProperty("raining", facts.raining());
		o.addProperty("thundering", facts.thundering());
		o.addProperty("dimension", facts.dimension());
		o.addProperty("biome", facts.biome());
		o.addProperty("light", facts.light());
		return o;
	}

	/** 物品条目的共用形状：背包、界面、方块实体三处一致，脚本写一次取值逻辑够用。 */
	private static JsonObject itemObject(Item item) {
		JsonObject i = new JsonObject();
		i.addProperty("slot", item.slot());
		i.addProperty("id", item.id());
		i.addProperty("count", item.count());
		i.addProperty("damage", item.damage());
		i.addProperty("max_damage", item.maxDamage());
		i.addProperty("custom_name", item.customName());
		JsonObject enchantments = new JsonObject();
		if (item.enchantments() != null) {
			for (Map.Entry<String, Integer> entry : item.enchantments().entrySet()) {
				enchantments.addProperty(entry.getKey(), entry.getValue());
			}
		}
		i.add("enchantments", enchantments);
		return i;
	}

	// ---- 在线玩家 / 聊天 ----

	/** 在线玩家快照：裸数组，顺序由服务端给出（即 tab 列表顺序），不做重排。 */
	public static String players(List<PlayerEntry> list) {
		return JsonText.stringify(playersArray(list));
	}

	/** 在线玩家快照的数组形态；远程桥直接挂进快照。 */
	public static JsonArray playersArray(List<PlayerEntry> list) {
		JsonArray array = new JsonArray();
		if (list != null) {
			for (PlayerEntry entry : list) {
				JsonObject o = new JsonObject();
				o.addProperty("uuid", entry.uuid());
				o.addProperty("name", entry.name());
				o.addProperty("latency", entry.latency());
				o.addProperty("gamemode", entry.gamemode());
				array.add(o);
			}
		}
		return array;
	}

	/** 字符串裸数组：聊天历史与「快照字段」共用。 */
	public static String stringArray(List<String> values) {
		JsonArray array = new JsonArray();
		if (values != null) {
			for (String value : values) {
				array.add(value == null ? "" : value);
			}
		}
		return JsonText.stringify(array);
	}

	/** 聊天历史：字符串裸数组，旧的在前，可直接喂 {@code json.array_join} 拼上下文。 */
	public static String chat(List<String> messages) {
		JsonArray array = new JsonArray();
		if (messages != null) {
			for (String message : messages) {
				array.add(message == null ? "" : message);
			}
		}
		return JsonText.stringify(array);
	}

	// ---- 区域扫描 ----

	/** 区域扫描结果：范围 + 截断标记 + 非空气方块列表。 */
	public static String scan(Scan scan) {
		JsonObject o = new JsonObject();
		o.addProperty("x1", scan.x1());
		o.addProperty("y1", scan.y1());
		o.addProperty("z1", scan.z1());
		o.addProperty("x2", scan.x2());
		o.addProperty("y2", scan.y2());
		o.addProperty("z2", scan.z2());
		o.addProperty("truncated", scan.truncated());
		JsonArray blocks = new JsonArray();
		if (scan.blocks() != null) {
			for (BlockEntry entry : scan.blocks()) {
				JsonObject block = new JsonObject();
				block.addProperty("x", entry.x());
				block.addProperty("y", entry.y());
				block.addProperty("z", entry.z());
				block.addProperty("id", entry.id());
				blocks.add(block);
			}
		}
		o.add("blocks", blocks);
		return JsonText.stringify(o);
	}

	// ---- 方块 ----

	/** 方块快照；{@code null} 表示拿不到（无世界），返回空对象。 */
	public static String block(Block b) {
		if (b == null) {
			return "{}";
		}
		JsonObject o = new JsonObject();
		o.addProperty("id", b.id());
		o.addProperty("x", b.x());
		o.addProperty("y", b.y());
		o.addProperty("z", b.z());
		JsonObject state = new JsonObject();
		// 属性名与顺序由游戏决定，无法扁平化：属性名是开放的（模组方块会加自己的），
		// 扁平后会与 id / x / light 这些固定键撞名
		for (Map.Entry<String, String> entry : b.state().entrySet()) {
			state.addProperty(entry.getKey(), entry.getValue());
		}
		o.add("state", state);
		o.addProperty("hardness", b.hardness());
		o.addProperty("is_air", b.air());
		o.addProperty("is_solid", b.solid());
		o.addProperty("is_fluid", b.fluid());
		o.addProperty("light", b.light());
		o.addProperty("biome", b.biome());
		BlockEntity be = b.blockEntity();
		JsonObject beObject = new JsonObject();
		if (be == null) {
			// 没有方块实体时给空对象而不是省略键：脚本不用先判断键在不在
			beObject.addProperty("type", "");
		} else {
			beObject.addProperty("type", be.type());
			JsonArray items = new JsonArray();
			for (Item item : be.items()) {
				items.add(itemObject(item));
			}
			beObject.add("items", items);
			beObject.addProperty("items_total", be.itemsTotal());
			JsonObject nbt = new JsonObject();
			// 数字保持数字：熔炉进度这类值要参与大小比较，变成字符串后虽然能强制转换，
			// 但「= 200」这类等值判断会依赖转换规则，不如直接给数值
			for (Map.Entry<String, Object> entry : be.nbt().entrySet()) {
				if (entry.getValue() instanceof Number number) {
					nbt.addProperty(entry.getKey(), number.doubleValue());
				} else {
					nbt.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
				}
			}
			beObject.add("nbt", nbt);
			beObject.addProperty("text_front", be.textFront());
			beObject.addProperty("text_back", be.textBack());
		}
		o.add("block_entity", beObject);
		return JsonText.stringify(o);
	}
}
