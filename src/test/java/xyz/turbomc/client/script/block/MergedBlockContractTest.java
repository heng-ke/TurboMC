package xyz.turbomc.client.script.block;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.turbomc.client.script.sb3.Sb3Mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 合并积木的契约：面板用「一块 + 下拉」替代若干细粒度积木，三处登记必须齐全。
 *
 * <p>守的是扩展声明、{@link Sb3Mapping}、{@link BlockRegistry} 的一致性。
 * 少任何一处都不会报错：删映射是 {@code argsSelfTest} 报「未映射」，
 * 删登记是老工程在查看器里显示成原始 opcode，而运行路径仍然正常。</p>
 *
 * <p>被合并的旧 opcode 已按 2026-11-19 的决定删除（见 ARCHITECTURE §6.2），
 * 所以这里不再断言它们的别名可用——如果将来又要兼容旧工程，
 * 正确做法是加载入期迁移层，而不是恢复这些条目。</p>
 */
class MergedBlockContractTest {

	@BeforeAll
	static void bootstrap() {
		BlockRegistry.bootstrapOnce();
	}

	@Test
	@DisplayName("合并块映射到新类型，参数数与扩展文案的占位符数一致")
	void mergedBlocksAreMapped() {
		// 玩家的七个读数合并成一块：health / food / xpLevel / selectedSlot / dimension /
		// ping / pos 的旧 opcode 已删（2026-11-27），这里只断言新块
		assertMapping("turbomc_playerField", "world.player_field", 1);
		// 2026-11-27 第二批：视角带模式、聊天来源、背包查询（范围 + 物品 + 要什么）
		assertMapping("turbomc_turnView", "action.turn_view", 3);
		assertMapping("turbomc_turnViewSmooth", "action.turn_view_smooth", 3);
		assertMapping("turbomc_chatSource", "query.chat_source", 1);
		assertMapping("turbomc_inventoryQuery", "world.inventory_query", 3);
		assertMapping("turbomc_aimPos", "world.aim_pos", 1);
		assertMapping("turbomc_nearestEntityField", "world.nearest_entity_field", 2);
		assertMapping("turbomc_heldItemField", "world.held_item_field", 1);
		assertMapping("turbomc_slotField", "world.slot_field", 2);
		// 快照三块：漏一处就是「面板有块、游戏里没反应」。玩家快照无参数，
		// 方块快照 3 个坐标，实体列表 3 个参数（类型 / 半径 / 上限）
		assertMapping("turbomc_playerJson", "world.player_json", 0);
		assertMapping("turbomc_blockJson", "world.block_json", 3);
		assertMapping("turbomc_entitiesJson", "world.entities_json", 3);
		// 批次 2：背包 / 界面 / 世界快照，均无参数
		assertMapping("turbomc_inventoryJson", "world.inventory_json", 0);
		assertMapping("turbomc_screenJson", "world.screen_json", 0);
		assertMapping("turbomc_worldJson", "world.world_json", 0);
		// 批次 3：在线玩家 / 区域扫描 / 聊天历史（扫描 7 个参数，聊天 1 个）
		assertMapping("turbomc_playersJson", "world.players_json", 0);
		assertMapping("turbomc_scanJson", "world.scan_json", 7);
		assertMapping("turbomc_chatHistory", "query.chat_history", 1);
		// 界面帽子：靠 SCREEN 字段匹配，漏登记就是「打开界面没反应」
		assertMapping("turbomc_whenScreenOpen", "event.screen_open", 1);
		// 「[快照] 的字段」：键清单的运行时出口
		assertMapping("turbomc_snapshotKeys", "world.snapshot_keys", 1);
	}

	private static void assertMapping(String opcode, String type, int params) {
		assertEquals(type, Sb3Mapping.typeOf(opcode), opcode + " 的映射不对");
		BlockType block = BlockRegistry.get(type);
		assertNotNull(block, "类型未登记：" + type);
		assertEquals(params, block.params().size(),
				type + " 的参数数与扩展文案的占位符数不一致（ARCHITECTURE §6.1）");
	}
}
