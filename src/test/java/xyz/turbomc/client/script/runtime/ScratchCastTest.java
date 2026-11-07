package xyz.turbomc.client.script.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScratchCast} 的单元测试。
 *
 * <p>Scratch 的弱类型转换规则很反直觉：非数字字符串转数字得 0 而不是报错、
 * {@code " false "} 是<b>真</b>、数字转字符串按 ECMAScript 的规则（整数不带小数点）。
 * 这些规则是脚本作者依赖的语义，改错一条就会让一批脚本行为漂移，而且不会有任何报错。</p>
 *
 * <p>这里逐条对齐上游 {@code src/util/cast.js}。</p>
 */
class ScratchCastTest {

	@Test
	@DisplayName("toNumber：非数字字符串得 0，不是 NaN")
	void nonNumericStringBecomesZero() {
		assertEquals(0d, ScratchCast.toNumber("abc"));
		assertEquals(0d, ScratchCast.toNumber(""));
		assertEquals(0d, ScratchCast.toNumber("  "));
	}

	@Test
	@DisplayName("toNumber：能解析的数字字符串正常转换")
	void numericStringParses() {
		assertEquals(42d, ScratchCast.toNumber("42"));
		assertEquals(-3.5d, ScratchCast.toNumber("-3.5"));
		assertEquals(42d, ScratchCast.toNumber("  42  "));
	}

	@Test
	@DisplayName("toNumber：rawNumber 保留 NaN，用于区分数字与文本")
	void rawNumberKeepsNaN() {
		assertTrue(Double.isNaN(ScratchCast.rawNumber("abc")));
		assertEquals(42d, ScratchCast.rawNumber("42"));
	}

	@Test
	@DisplayName("toNumber：十六进制按 ECMAScript 规则解析")
	void hexStringParses() {
		assertEquals(255d, ScratchCast.rawNumber("0xFF"));
		assertEquals(255d, ScratchCast.rawNumber("0xff"));
	}

	@Test
	@DisplayName("toNumber：Infinity 与 NaN 字面量")
	void specialLiteralsParse() {
		assertEquals(Double.POSITIVE_INFINITY, ScratchCast.rawNumber("Infinity"));
		assertEquals(Double.NEGATIVE_INFINITY, ScratchCast.rawNumber("-Infinity"));
		assertTrue(Double.isNaN(ScratchCast.rawNumber("NaN")));
	}

	@Test
	@DisplayName("toNumber：布尔按 1 / 0 计入")
	void booleanToNumber() {
		assertEquals(1d, ScratchCast.toNumber(Boolean.TRUE));
		assertEquals(0d, ScratchCast.toNumber(Boolean.FALSE));
	}

	@Test
	@DisplayName("toBoolean：字符串不做 trim，「 false 」是真")
	void stringBooleanDoesNotTrim() {
		assertTrue(ScratchCast.toBoolean(" false "), "带空格的 false 在 Scratch 里是真");
		assertTrue(ScratchCast.toBoolean("false "));
	}

	@Test
	@DisplayName("toBoolean：空串、\"0\"、\"false\" 为假")
	void falsyStrings() {
		assertFalse(ScratchCast.toBoolean(""));
		assertFalse(ScratchCast.toBoolean("0"));
		assertFalse(ScratchCast.toBoolean("false"));
		assertFalse(ScratchCast.toBoolean("FALSE"));
	}

	@Test
	@DisplayName("toBoolean：数字 0 与 NaN 为假")
	void falsyNumbers() {
		assertFalse(ScratchCast.toBoolean(0d));
		assertFalse(ScratchCast.toBoolean(Double.NaN));
		assertTrue(ScratchCast.toBoolean(1d));
		assertTrue(ScratchCast.toBoolean(-1d));
	}

	@Test
	@DisplayName("toString：数字按 ECMAScript 规则，整数不带小数点")
	void numberToStringFollowsEcmaScript() {
		assertEquals("20", ScratchCast.toString(20d));
		assertEquals("0", ScratchCast.toString(0d));
		assertEquals("2.5", ScratchCast.toString(2.5d));
		assertEquals("-7", ScratchCast.toString(-7d));
	}

	@Test
	@DisplayName("toString：小数用普通十进制，不用 Java 的科学计数法")
	void smallNumberAvoidsScientificNotation() {
		assertEquals("0.00001", ScratchCast.toString(0.00001d));
		assertEquals("1.5", ScratchCast.toString(1.5d));
	}

	@Test
	@DisplayName("toString：null 与 undefined 得空串")
	void nullToEmptyString() {
		assertEquals("", ScratchCast.toString(null));
	}

	@ParameterizedTest
	@DisplayName("numberToString：整数值不带小数点")
	@CsvSource({
			"0, 0",
			"20, 20",
			"-7, -7",
			"2.5, 2.5",
			"100.25, 100.25"
	})
	void numberToStringCases(double input, String expected) {
		assertEquals(expected, ScratchCast.numberToString(input));
	}

	@ParameterizedTest
	@DisplayName("toNumber：NaN 一律折成 0")
	@ValueSource(strings = {"abc", "NaN", "", "   "})
	void nanFoldsToZero(String input) {
		assertEquals(0d, ScratchCast.toNumber(input));
	}
}
