package xyz.turbomc.client.script.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 快照的 JSON 形状：键名、扁平结构、数值格式、转义、缺数据时的降级。
 *
 * <p>这是「键名只增不删」契约的守卫（ARCHITECTURE §26）。快照在真机上才有值，
 * 但拼装层只吃原语，所以字段名写错、数字写成 {@code 20.0} 这类问题必须在这里拦住——
 * 到了游戏里只会表现为「脚本读不到这个键」，没有任何异常。</p>
 */
class SnapshotJsonTest {

	@Test
	@DisplayName("玩家快照：坐标与运动量是扁平键，没有 pos 子对象")
	void playerIsFlat() {
		JsonObject json = parseObject(JsonText.stringify(Snapshots.playerObject(new Snapshots.Player(
				"Steve", "uuid-1", 20f, 20f, 0f, 18, 3.5f, 7, 0.25f, 4, 300,
				"survival", "minecraft:overworld",
				new Snapshots.Vec(10.5, 64, -3), new Snapshots.Vec(0, -0.0784, 0.25),
				90f, -10f, true, false, false, 2,
				List.of(new Snapshots.Effect("minecraft:speed", 1, 400))))));

		assertEquals(10.5, json.get("x").getAsDouble());
		assertEquals(64, json.get("y").getAsDouble());
		assertEquals(-3, json.get("z").getAsDouble());
		// 上一 tick 位移：负值要原样保留
		assertEquals(-0.0784, json.get("motion_y").getAsDouble());
		assertFalse(json.has("pos"), "扁平契约：坐标不能包进 pos 子对象");
		assertFalse(json.has("motion"), "扁平契约：运动量不能包进 motion 子对象");
		// 整数写成 20 而不是 20.0——与 JSON.stringify 一致（ARCHITECTURE §23）
		assertTrue(JsonText.stringify(Snapshots.playerObject(new Snapshots.Player(
				"Steve", "u", 20f, 20f, 0f, 20, 5f, 0, 0f, 0, 300,
				"survival", "minecraft:overworld",
				new Snapshots.Vec(0, 0, 0), new Snapshots.Vec(0, 0, 0), 0f, 0f,
				true, false, false, 1, List.of())))
				.contains("\"health\":20,"), "整数不能写成 20.0");
		assertEquals(18, json.get("food").getAsInt());
		assertEquals(2, json.get("selected_slot").getAsInt());
		assertEquals("survival", json.get("gamemode").getAsString());

		JsonArray effects = json.getAsJsonArray("effects");
		assertEquals(1, effects.size());
		assertEquals("minecraft:speed", effects.get(0).getAsJsonObject().get("id").getAsString());
		assertEquals(400, effects.get(0).getAsJsonObject().get("duration").getAsInt());
	}

	@Test
	@DisplayName("无玩家时给空对象而不是空串：空串会让 json 积木解析失败")
	void playerWithoutPlayer() {
		String text = JsonText.stringify(Snapshots.playerObject(null));
		assertEquals("{}", text);
		assertNotNull(JsonParser.parseString(text), "空对象必须是合法 JSON");
	}

	@Test
	@DisplayName("实体数组：裸数组，按传入顺序，空输入给 []")
	void entitiesArray() {
		String text = Snapshots.entities(List.of(
				new Snapshots.Entity("minecraft:zombie", "僵尸", "", "u1", 2.5,
						new Snapshots.Vec(1, 2, 3), new Snapshots.Vec(0, 0, 0), 20f, 20f, false, true),
				new Snapshots.Entity("minecraft:creeper", "苦力怕", "闪电", "u2", 5,
						new Snapshots.Vec(4, 5, 6), new Snapshots.Vec(0.1, 0, -0.1), 20f, 20f, true, false)));

		JsonArray array = JsonParser.parseString(text).getAsJsonArray();
		assertEquals(2, array.size(), "裸数组才能直接接 json.length / array_map_field");
		JsonObject first = array.get(0).getAsJsonObject();
		assertEquals("minecraft:zombie", first.get("type").getAsString());
		assertEquals("僵尸", first.get("name").getAsString());
		assertEquals(2.5, first.get("distance").getAsDouble());
		// 排序由调用方（WorldQueries）负责，拼装层不得重排
		assertEquals("minecraft:creeper", array.get(1).getAsJsonObject().get("type").getAsString());
		assertTrue(array.get(1).getAsJsonObject().get("is_baby").getAsBoolean());

		assertEquals("[]", Snapshots.entities(List.of()));
		assertEquals("[]", Snapshots.entities(null));
	}

	@Test
	@DisplayName("方块快照：无方块实体时 block_entity 只有空 type，键仍然存在")
	void blockWithoutBlockEntity() {
		JsonObject json = parseObject(Snapshots.block(new Snapshots.Block(
				"minecraft:oak_stairs", 1, 64, 2,
				state("facing", "east"), 2f, false, true, false, 12, "minecraft:plains", null)));

		assertEquals("minecraft:oak_stairs", json.get("id").getAsString());
		assertEquals("east", json.getAsJsonObject("state").get("facing").getAsString());
		assertEquals(2, json.get("hardness").getAsDouble());
		assertTrue(json.get("is_solid").getAsBoolean());
		JsonObject be = json.getAsJsonObject("block_entity");
		assertNotNull(be, "键必须存在，脚本不用先判断");
		assertEquals("", be.get("type").getAsString());
	}

	@Test
	@DisplayName("方块实体：物品、真实槽位数、标量 NBT、告示牌文本")
	void blockWithBlockEntity() {
		JsonObject json = parseObject(Snapshots.block(new Snapshots.Block(
				"minecraft:chest", 0, 64, 0, Map.of(), 2.5f, false, true, false, 0, "minecraft:plains",
				new Snapshots.BlockEntity("minecraft:chest",
						List.of(new Snapshots.Item(0, "minecraft:diamond", 3, 0, 0, "宝贝",
								Map.of("minecraft:sharpness", 5))),
						27,
						nbt("CookTime", 120.0),
						"第一行\n第二行", ""))));

		JsonObject be = json.getAsJsonObject("block_entity");
		assertEquals("minecraft:chest", be.get("type").getAsString());
		assertEquals(27, be.get("items_total").getAsInt(), "截断后仍要给出真实槽位数");
		JsonObject item = be.getAsJsonArray("items").get(0).getAsJsonObject();
		assertEquals(0, item.get("slot").getAsInt());
		assertEquals("minecraft:diamond", item.get("id").getAsString());
		assertEquals("宝贝", item.get("custom_name").getAsString());
		assertEquals(5, item.getAsJsonObject("enchantments").get("minecraft:sharpness").getAsInt());
		// NBT 数字保持数字：熔炉进度要参与比较
		assertEquals(120, be.getAsJsonObject("nbt").get("CookTime").getAsInt());
		assertEquals("第一行\n第二行", be.get("text_front").getAsString());
		assertEquals("", be.get("text_back").getAsString());
	}

	@Test
	@DisplayName("背包快照：裸数组，只列非空槽位，槽位号保留 inventory 索引")
	void inventoryArray() {
		JsonArray array = JsonParser.parseString(JsonText.stringify(Snapshots.inventoryArray(List.of(
				new Snapshots.Item(0, "minecraft:iron_sword", 1, 10, 250, "", Map.of()),
				new Snapshots.Item(36, "minecraft:diamond_helmet", 1, 0, 363, "", Map.of()),
				new Snapshots.Item(40, "minecraft:shield", 1, 0, 336, "", Map.of()))))).getAsJsonArray();

		assertEquals(3, array.size());
		assertEquals(0, array.get(0).getAsJsonObject().get("slot").getAsInt());
		// 盔甲与副手不是 1、2：槽位号必须是 inventory 索引，否则脚本按 36/40 取甲会落空
		assertEquals(36, array.get(1).getAsJsonObject().get("slot").getAsInt());
		assertEquals(40, array.get(2).getAsJsonObject().get("slot").getAsInt());
		assertEquals("[]", JsonText.stringify(Snapshots.inventoryArray(List.of())));
	}

	@Test
	@DisplayName("界面快照：带 type/slots，玩家格有 player_slot 标记")
	void screenShape() {
		JsonObject json = parseObject(JsonText.stringify(Snapshots.screenObject("minecraft:generic_9x3", 63, List.of(
				new Snapshots.ScreenItem(
						new Snapshots.Item(0, "minecraft:gold_ingot", 64, 0, 0, "", Map.of()), false),
				new Snapshots.ScreenItem(
						new Snapshots.Item(27, "minecraft:bread", 3, 0, 0, "", Map.of()), true)))));

		assertEquals("minecraft:generic_9x3", json.get("type").getAsString());
		assertEquals(63, json.get("slots").getAsInt(), "slots 是界面槽位总数，不是非空条目数");
		JsonArray items = json.getAsJsonArray("items");
		assertEquals(2, items.size());
		assertFalse(items.get(0).getAsJsonObject().get("player_slot").getAsBoolean());
		assertTrue(items.get(1).getAsJsonObject().get("player_slot").getAsBoolean());
		assertTrue(JsonParser.parseString(JsonText.stringify(Snapshots.screenObject("", 0, List.of())))
				.getAsJsonObject().getAsJsonArray("items").isEmpty());
	}

	@Test
	@DisplayName("世界快照：时间/天数/月相/天气/光照都在这八个键里")
	void worldShape() {
		JsonObject json = parseObject(JsonText.stringify(Snapshots.worldObject(new Snapshots.WorldFacts(
				13000, 12, 3, true, false, "minecraft:overworld", "minecraft:plains", 7))));

		assertEquals(13000, json.get("time").getAsInt());
		assertEquals(12, json.get("day").getAsInt());
		assertEquals(3, json.get("moon_phase").getAsInt());
		assertTrue(json.get("raining").getAsBoolean());
		assertFalse(json.get("thundering").getAsBoolean());
		assertEquals("minecraft:plains", json.get("biome").getAsString());
		assertEquals(7, json.get("light").getAsInt());
		assertEquals("{}", JsonText.stringify(Snapshots.worldObject(null)));
	}

	@Test
	@DisplayName("在线玩家：只有 uuid/名称/延迟/模式四项，裸数组")
	void playersArray() {
		JsonArray array = JsonParser.parseString(JsonText.stringify(Snapshots.playersArray(List.of(
				new Snapshots.PlayerEntry("uuid-a", "Alice", 35, "survival"),
				new Snapshots.PlayerEntry("", "Bob", 0, ""))))).getAsJsonArray();

		assertEquals(2, array.size());
		JsonObject first = array.get(0).getAsJsonObject();
		assertEquals("uuid-a", first.get("uuid").getAsString());
		assertEquals("Alice", first.get("name").getAsString());
		assertEquals(35, first.get("latency").getAsInt());
		assertEquals("survival", first.get("gamemode").getAsString());
		// 模式缺失时给空串而不是 null，脚本不用判 null
		assertEquals("", array.get(1).getAsJsonObject().get("gamemode").getAsString());
		assertEquals("[]", JsonText.stringify(Snapshots.playersArray(null)));
	}

	@Test
	@DisplayName("聊天历史：字符串裸数组，顺序原样保留（旧的在前由调用方保证）")
	void chatArray() {
		JsonArray array = JsonParser.parseString(Snapshots.stringArray(List.of("第一句", "第二句")))
				.getAsJsonArray();
		assertEquals("第一句", array.get(0).getAsString());
		assertEquals("第二句", array.get(1).getAsString());
		assertEquals("[]", Snapshots.stringArray(null));
	}

	@Test
	@DisplayName("区域扫描：范围回显 + truncated 标记 + 非空气方块列表")
	void scanShape() {
		JsonObject json = parseObject(Snapshots.scan(new Snapshots.Scan(
				-5, 60, -5, 5, 70, 5, true,
				List.of(new Snapshots.BlockEntry(-5, 60, -5, "minecraft:stone")))));

		assertEquals(-5, json.get("x1").getAsInt());
		assertEquals(60, json.get("y1").getAsInt());
		assertEquals(5, json.get("z2").getAsInt());
		// truncated 表示「结果不全」：体积超限或条数超限都会置真
		assertTrue(json.get("truncated").getAsBoolean());
		JsonObject block = json.getAsJsonArray("blocks").get(0).getAsJsonObject();
		assertEquals(-5, block.get("x").getAsInt());
		assertEquals("minecraft:stone", block.get("id").getAsString());
	}

	@Test
	@DisplayName("名称里的双引号与换行必须转义，否则整串 JSON 报废")
	void escaping() {
		String text = Snapshots.entities(List.of(
				new Snapshots.Entity("minecraft:zombie", "僵尸", "他说\"你好\"\n换行", "u", 1,
						new Snapshots.Vec(0, 0, 0), new Snapshots.Vec(0, 0, 0), 1f, 1f, false, true)));

		assertNotNull(JsonParser.parseString(text), "输出必须是合法 JSON");
		assertEquals("他说\"你好\"\n换行",
				JsonParser.parseString(text).getAsJsonArray().get(0).getAsJsonObject().get("custom_name").getAsString());
	}

	@Test
	@DisplayName("每块快照的键顺序必须与 Snapshots 里的清单一致（清单是唯一事实来源）")
	void keysMatchDeclaredLists() {
		JsonObject player = parseObject(JsonText.stringify(Snapshots.playerObject(new Snapshots.Player(
				"n", "u", 1f, 1f, 0f, 1, 0f, 0, 0f, 0, 0, "survival", "minecraft:overworld",
				new Snapshots.Vec(0, 0, 0), new Snapshots.Vec(0, 0, 0), 0f, 0f,
				false, false, false, 1, List.of()))));
		assertEquals(Snapshots.PLAYER_KEYS, List.copyOf(player.keySet()));

		JsonObject entity = JsonParser.parseString(Snapshots.entities(List.of(
				new Snapshots.Entity("t", "n", "", "u", 1,
						new Snapshots.Vec(0, 0, 0), new Snapshots.Vec(0, 0, 0), 1f, 1f, false, true))))
				.getAsJsonArray().get(0).getAsJsonObject();
		assertEquals(Snapshots.ENTITY_KEYS, List.copyOf(entity.keySet()));

		JsonObject item = JsonParser.parseString(JsonText.stringify(Snapshots.inventoryArray(List.of(
				new Snapshots.Item(0, "minecraft:stone", 1, 0, 0, "", Map.of())))))
				.getAsJsonArray().get(0).getAsJsonObject();
		assertEquals(Snapshots.ITEM_KEYS, List.copyOf(item.keySet()));

		JsonObject screen = parseObject(JsonText.stringify(Snapshots.screenObject("minecraft:generic_9x3", 63, List.of(
				new Snapshots.ScreenItem(
						new Snapshots.Item(0, "minecraft:stone", 1, 0, 0, "", Map.of()), true)))));
		assertEquals(Snapshots.SCREEN_KEYS, List.copyOf(screen.keySet()));
		assertEquals(Snapshots.SCREEN_ITEM_KEYS,
				List.copyOf(screen.getAsJsonArray("items").get(0).getAsJsonObject().keySet()));

		JsonObject block = parseObject(Snapshots.block(new Snapshots.Block(
				"minecraft:chest", 0, 0, 0, Map.of(), 1f, false, true, false, 0, "",
				new Snapshots.BlockEntity("minecraft:chest", List.of(), 27, Map.of(), "", ""))));
		assertEquals(Snapshots.BLOCK_KEYS, List.copyOf(block.keySet()));
		assertEquals(Snapshots.BLOCK_ENTITY_KEYS,
				List.copyOf(block.getAsJsonObject("block_entity").keySet()));

		JsonObject world = parseObject(JsonText.stringify(Snapshots.worldObject(new Snapshots.WorldFacts(
				0, 1, 0, false, false, "minecraft:overworld", "", 0))));
		assertEquals(Snapshots.WORLD_KEYS, List.copyOf(world.keySet()));

		JsonObject entry = JsonParser.parseString(JsonText.stringify(Snapshots.playersArray(List.of(
				new Snapshots.PlayerEntry("u", "n", 0, "survival")))))
				.getAsJsonArray().get(0).getAsJsonObject();
		assertEquals(Snapshots.PLAYER_ENTRY_KEYS, List.copyOf(entry.keySet()));

		JsonObject scan = parseObject(Snapshots.scan(new Snapshots.Scan(
				0, 0, 0, 1, 1, 1, false, List.of())));
		assertEquals(Snapshots.SCAN_KEYS, List.copyOf(scan.keySet()));

		// 未知种类给空数组，不抛异常
		assertTrue(Snapshots.keysOf("nope").isEmpty());
	}

	private static Map<String, String> state(String key, String value) {
		Map<String, String> map = new LinkedHashMap<>();
		map.put(key, value);
		return map;
	}

	private static Map<String, Object> nbt(String key, Object value) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put(key, value);
		return map;
	}

	private static JsonObject parseObject(String text) {
		assertNotNull(text, "输出不能为空");
		return JsonParser.parseString(text).getAsJsonObject();
	}
}