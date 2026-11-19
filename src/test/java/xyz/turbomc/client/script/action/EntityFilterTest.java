package xyz.turbomc.client.script.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 实体类型参数的解析规则。
 *
 * <p>解析必须是纯函数：它决定「脚本里写的东西」怎么变成判定条件，一旦判错，
 * 表现是「积木永远返回无目标」——没有异常、没有日志（改动前），最难排查。
 * 判定本身要真实 {@code Entity}，只能真机验证；解析这一层必须用单测焊死。</p>
 */
class EntityFilterTest {

	@ParameterizedTest(name = "\"{0}\" → any")
	@DisplayName("只有显式 any 表示不限类型")
	@ValueSource(strings = {"any", "ANY", " Any "})
	void anyMeansAny(String raw) {
		assertEquals(EntityFilter.Kind.ANY, EntityFilter.parse(raw).kind());
	}

	@Test
	@DisplayName("空值不再放宽：按「匹配不到」处理，避免参数没送到时安静地匹配全部")
	void blankDoesNotMatchAll() {
		assertEquals(EntityFilter.Kind.TYPE, EntityFilter.parse("").kind());
		assertEquals(EntityFilter.Kind.TYPE, EntityFilter.parse("   ").kind());
		assertEquals(EntityFilter.Kind.TYPE, EntityFilter.parse(null).kind());
	}

	@ParameterizedTest(name = "\"{0}\"")
	@DisplayName("关键字大小写与空白都不敏感")
	@CsvSource({
			"hostile,      HOSTILE",
			"Hostile,      HOSTILE",
			"'  hostile  ', HOSTILE",
			"player,       PLAYER",
			"PLAYER,       PLAYER",
	})
	void keywordsIgnoreCaseAndSpace(String raw, EntityFilter.Kind expected) {
		assertEquals(expected, EntityFilter.parse(raw).kind());
	}

	@ParameterizedTest(name = "\"{0}\" → {1}")
	@DisplayName("其余按实体 id 处理：去空白并小写，命名空间可省略")
	@CsvSource({
			"minecraft:zombie,  minecraft:zombie",
			"ZOMBIE,            zombie",
			"'  minecraft:cow  ', minecraft:cow",
			"minecraft:iron_golem, minecraft:iron_golem",
	})
	void entityIdIsNormalized(String raw, String expectedId) {
		EntityFilter.Parsed parsed = EntityFilter.parse(raw);
		assertEquals(EntityFilter.Kind.TYPE, parsed.kind());
		assertEquals(expectedId, parsed.id());
	}

	@Test
	@DisplayName("缓存键必须区分类型，否则「任意」会顶替「僵尸」")
	void cacheKeySeparatesFilters() {
		assertEquals("any", EntityFilter.parse("any").cacheKey());
		assertEquals("hostile", EntityFilter.parse("hostile").cacheKey());
		assertEquals("player", EntityFilter.parse("player").cacheKey());
		assertEquals("type:minecraft:zombie", EntityFilter.parse("minecraft:zombie").cacheKey());
	}
}
