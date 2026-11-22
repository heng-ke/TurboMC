package xyz.turbomc.client.script.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java 侧 JSON 工具（快照序列化）的纯逻辑约束。
 *
 * <p>JSON 积木本身的语义已由 {@code turbowarp/src/json-lite.js} 承担，
 * 行为回归见 {@link JsonCoreParityTest}；这里守的是快照仍在用的解析与序列化。</p>
 */
class JsonTextTest {

	@Test
	void parserRejectsWhatJsonParseRejects() {
		// 合法
		assertEquals("{\"a\":1}", JsonText.stringify(JsonText.parse("{\"a\":1}")));
		assertEquals("[1,2]", JsonText.stringify(JsonText.parse(" [1,2] ")));
		// Gson 默认宽松：裸键名、单引号都必须被拒，否则「是不是合法 JSON」这块积木失真
		assertNull(JsonText.parse("{a:1}"));
		assertNull(JsonText.parse("{'a':1}"));
		// JSON.parse 不接受尾部多余内容
		assertNull(JsonText.parse("{\"a\":1} trailing"));
		// 注释同理
		assertNull(JsonText.parse("{\"a\":1} // c"));
	}

	@Test
	void onlyContainerLooksLikeJson() {
		assertTrue(JsonText.looksLikeContainer("{\"a\":1}"));
		assertTrue(JsonText.looksLikeContainer("[1]"));
		// 原扩展只接受对象与数组：标量文本不算合法 JSON
		assertFalse(JsonText.looksLikeContainer("1"));
		assertFalse(JsonText.looksLikeContainer("true"));
		assertFalse(JsonText.looksLikeContainer("{\"a\":1]"));
		assertFalse(JsonText.looksLikeContainer(""));
	}

	@Test
	void numbersAreWrittenLikeEcmaScript() {
		// JSON.stringify(1.0) === "1"；写成 "1.0" 会让字符串比较永远不成立
		assertEquals("{\"n\":1}", JsonText.stringify(JsonText.parse("{\"n\":1.0}")));
		assertEquals("{\"n\":0.5}", JsonText.stringify(JsonText.parse("{\"n\":0.50}")));
		assertEquals("[1,2,3]", JsonText.stringify(JsonText.parse("[1,2.0,3]")));
	}

	@Test
	void stringifyKeepsStructureAndEscapes() {
		assertEquals("{}", JsonText.stringify(JsonText.parse("{}")));
		assertEquals("[]", JsonText.stringify(JsonText.parse("[]")));
		assertEquals("{\"a\":{\"b\":[1,null,true]}}",
				JsonText.stringify(JsonText.parse("{\"a\":{\"b\":[1,null,true]}}")));
		assertEquals("{\"s\":\"a\\\"b\\nc\"}",
				JsonText.stringify(JsonText.parse("{\"s\":\"a\\\"b\\nc\"}")));
	}

	@Test
	void scratchIndexIsOneBasedAndCountsBackwards() {
		assertEquals(0, JsonText.arrayIndex("1", 3));
		assertEquals(2, JsonText.arrayIndex("3", 3));
		assertEquals(2, JsonText.arrayIndex("-1", 3));
		// 0 与越界都给 -1，对应原扩展返回空串
		assertEquals(-1, JsonText.arrayIndex("0", 3));
		assertEquals(-1, JsonText.arrayIndex("4", 3));
		assertEquals(-1, JsonText.arrayIndex("1.5", 3));
		assertEquals(-1, JsonText.arrayIndex("", 3));
	}

	@Test
	void valuesComeBackAsScalarsOrJsonText() {
		assertEquals(1.0, JsonText.toScratch(JsonText.parse("[1]").getAsJsonArray().get(0)));
		assertEquals(true, JsonText.toScratch(JsonText.parse("[true]").getAsJsonArray().get(0)));
		assertEquals("x", JsonText.toScratch(JsonText.parse("[\"x\"]").getAsJsonArray().get(0)));
		assertEquals("{\"a\":1}",
				JsonText.toScratch(JsonText.parse("[{\"a\":1}]").getAsJsonArray().get(0)));
		// null 在积木里是空串（原扩展 result ?? ""）
		assertEquals("", JsonText.toScratch(JsonText.parse("[null]").getAsJsonArray().get(0)));
	}

	@Test
	void writeValueParsesOnlyContainerShapedText() {
		// 看起来像容器的文本会被解析成结构
		assertEquals("[1,2]", JsonText.stringify(JsonText.toWriteValue("[1,2]")));
		// 普通文字保持字符串，不会被当成数字或布尔
		assertEquals("\"42\"", JsonText.stringify(JsonText.toWriteValue("42")));
		assertEquals("\"{oops}\"", JsonText.stringify(JsonText.toWriteValue("{oops}")));
	}
}
