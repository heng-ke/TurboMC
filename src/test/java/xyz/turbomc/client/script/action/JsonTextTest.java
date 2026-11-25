package xyz.turbomc.client.script.action;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Java 侧 JSON 序列化（快照拼装）的纯逻辑约束。
 *
 * <p>JSON 积木本身的语义由 {@code turbowarp/src/json-lite.js} 承担，行为回归见
 * {@link JsonCoreParityTest}；这里只守快照仍在用的 {@link JsonText#stringify}。</p>
 */
class JsonTextTest {

	@Test
	void numbersAreWrittenLikeEcmaScript() {
		// JSON.stringify(1.0) === "1"；写成 "1.0" 会让字符串比较永远不成立
		assertEquals("{\"n\":1}", JsonText.stringify(JsonParser.parseString("{\"n\":1.0}")));
		assertEquals("{\"n\":0.5}", JsonText.stringify(JsonParser.parseString("{\"n\":0.50}")));
		assertEquals("[1,2,3]", JsonText.stringify(JsonParser.parseString("[1,2.0,3]")));
	}

	@Test
	void stringifyKeepsStructureAndEscapes() {
		assertEquals("{}", JsonText.stringify(JsonParser.parseString("{}")));
		assertEquals("[]", JsonText.stringify(JsonParser.parseString("[]")));
		assertEquals("{\"a\":{\"b\":[1,null,true]}}",
				JsonText.stringify(JsonParser.parseString("{\"a\":{\"b\":[1,null,true]}}")));
		assertEquals("{\"s\":\"a\\\"b\\nc\"}",
				JsonText.stringify(JsonParser.parseString("{\"s\":\"a\\\"b\\nc\"}")));
	}

	@Test
	void nonFiniteNumbersBecomeNull() {
		// JSON 没有 NaN/Infinity，JSON.stringify 会写成 null
		assertEquals("{\"n\":null}", JsonText.stringify(JsonParser.parseString("{\"n\":1e999}")));
	}
}