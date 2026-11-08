package xyz.turbomc.client.script.action;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 物品 id 的规范化规则。
 *
 * <p>菜单给的是规范 id，但手填仍然可能：这里保证 {@code iron_ore}、{@code IRON_ORE}、
 * 前后带空格都能落到 {@code minecraft:iron_ore}。规则错了的表现是「积木永远 0 / -1」，
 * 不会报错，所以用单测焊死。</p>
 */
class ItemIdsTest {

	@ParameterizedTest(name = "\"{0}\" → {1}")
	@DisplayName("补命名空间、小写、去首尾空格")
	@CsvSource({
			"minecraft:iron_ore,  minecraft:iron_ore",
			"iron_ore,            minecraft:iron_ore",
			"'  IRON_ORE  ',      minecraft:iron_ore",
			"Diamond,             minecraft:diamond",
			"minecraft:oak_log,   minecraft:oak_log",
			"oak_log,             minecraft:oak_log",
	})
	void normalizes(String raw, String expected) {
		Identifier id = ItemIds.parse(raw);
		assertEquals(expected, id == null ? null : id.toString());
	}

	@ParameterizedTest(name = "\"{0}\" 应判非法")
	@DisplayName("空值与非法写法返回 null，不抛异常")
	@ValueSource(strings = {"", "   ", "bad id", "minecraft:", "!!!", "a:b:c"})
	void rejectsInvalid(String raw) {
		assertNull(ItemIds.parse(raw));
	}

	@Test
	@DisplayName("null 不抛异常")
	void nullIsSafe() {
		assertNull(ItemIds.parse(null));
	}
}
