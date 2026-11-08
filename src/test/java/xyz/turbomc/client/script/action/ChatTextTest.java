package xyz.turbomc.client.script.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChatText} 的单元测试。
 *
 * <p>这个类守的是「不把玩家踢出存档」。真机上验证的代价很高（每次都是断线重连），
 * 而失败模式非常明确：服务端 {@code multiplayer.disconnect.illegal_characters}。
 * 所以在这里把每一种会被拒的字符都钉住。</p>
 */
class ChatTextTest {

	@Test
	@DisplayName("换行折成空格：模型回复最主要的多段来源")
	void newlineBecomesSpace() {
		assertEquals("第一段 第二段", ChatText.sanitize("第一段\n第二段"));
		assertEquals("a b c", ChatText.sanitize("a\r\nb\nc"));
	}

	@Test
	@DisplayName("删掉 § 与颜色码")
	void stripsFormatting() {
		assertEquals("红色字", ChatText.sanitize("§c红色§r字"));
	}

	@Test
	@DisplayName("删掉控制字符，保留正常文本")
	void stripsControlCharacters() {
		assertEquals("abc", ChatText.sanitize("a\u0000b\u0007c"));
	}

	@Test
	@DisplayName("折叠换行带来的连续空格，并去首尾空白")
	void collapsesSpaces() {
		assertEquals("一句话 结束", ChatText.sanitize("  一句话 \n\n 结束  "));
	}

	@Test
	@DisplayName("超过 256 字符按字符截断，并以省略号标记")
	void truncatesLongText() {
		String longText = "字".repeat(ChatText.MAX_LENGTH + 50);
		String clean = ChatText.sanitize(longText);
		assertEquals(ChatText.MAX_LENGTH, clean.length());
		assertTrue(clean.endsWith("…"));
	}

	@Test
	@DisplayName("字节上限：emoji 是 4 字节，不能只看字符数")
	void respectsByteLimit() {
		// 250 个汉字（750 字节）+ 20 个 emoji（80 字节）→ 字符数才 270，字节数已 830
		String text = "字".repeat(250) + "😀".repeat(20);
		String clean = ChatText.sanitize(text);
		assertTrue(utf8Bytes(clean) <= ChatText.MAX_BYTES,
				"字节数 " + utf8Bytes(clean) + " 超过上限 " + ChatText.MAX_BYTES);
		assertTrue(clean.length() <= ChatText.MAX_LENGTH);
		assertTrue(clean.endsWith("…"));
	}

	@Test
	@DisplayName("恰好 256 个汉字不截断（正好 768 字节，卡在边界）")
	void exactLimitUntouched() {
		String text = "字".repeat(ChatText.MAX_LENGTH);
		assertEquals(ChatText.MAX_BYTES, utf8Bytes(text));
		assertEquals(text, ChatText.sanitize(text));
	}

	@Test
	@DisplayName("截断不会切掉代理对的一半")
	void truncationKeepsSurrogatePairs() {
		// 截断点落在 emoji 中间时，整个 emoji 应当丢掉而不是留半截
		String text = "a".repeat(ChatText.MAX_LENGTH - 2) + "😀😀尾巴";
		String clean = ChatText.sanitize(text);
		assertTrue(clean.length() <= ChatText.MAX_LENGTH);
		for (int i = 0; i < clean.length(); i++) {
			char c = clean.charAt(i);
			if (Character.isHighSurrogate(c)) {
				assertTrue(i + 1 < clean.length() && Character.isLowSurrogate(clean.charAt(i + 1)),
						"第 " + i + " 位是孤立的高代理");
				i++;
			} else {
				assertFalse(Character.isLowSurrogate(c), "第 " + i + " 位是孤立的低代理");
			}
		}
	}

	@Test
	@DisplayName("孤立代理项被丢弃：半截 emoji 服务端判非法")
	void dropsLoneSurrogates() {
		assertEquals("ab", ChatText.sanitize("a\uD83Db"));
	}

	@Test
	@DisplayName("全是非法字符时返回空串，调用方据此放弃发送")
	void allIllegalBecomesEmpty() {
		assertEquals("", ChatText.sanitize("\n\n\u0000  "));
	}

	@Test
	@DisplayName("干净文本原样返回，不误报需要清洗")
	void cleanTextUntouched() {
		String clean = "钻石斧可以砍树、挖矿、打怪。";
		assertEquals(clean, ChatText.sanitize(clean));
		assertFalse(ChatText.needsSanitize(clean));
		assertTrue(ChatText.needsSanitize("多行\n回复"));
	}

	/** 与 ChatText 内部同一算法，但在测试里独立实现：不能拿被测代码去验证被测代码。 */
	private static int utf8Bytes(String text) {
		int total = 0;
		for (int i = 0; i < text.length(); ) {
			int codePoint = text.codePointAt(i);
			total += codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
			i += Character.charCount(codePoint);
		}
		return total;
	}

	// ---- 分段 ----

	@Test
	@DisplayName("短文本只有一段，内容不变")
	void splitKeepsShortText() {
		assertEquals(List.of("你好。"), ChatText.split("你好。", 8));
	}

	@Test
	@DisplayName("超长切成多段，每段都满足双上限，合起来一字不少")
	void splitRespectsLimits() {
		String text = "字".repeat(600);
		List<String> segments = ChatText.split(text, 8);
		assertEquals(3, segments.size());
		assertEquals(600, String.join("", segments).length());
		for (String segment : segments) {
			assertTrue(segment.length() <= ChatText.MAX_LENGTH, "字符超限：" + segment.length());
			assertTrue(utf8Bytes(segment) <= ChatText.MAX_BYTES, "字节超限：" + utf8Bytes(segment));
		}
	}

	@Test
	@DisplayName("优先在标点处切，不把句子拦腰截断")
	void splitPrefersPunctuation() {
		String text = "第一句话。".repeat(80);
		List<String> segments = ChatText.split(text, 8);
		assertTrue(segments.size() > 1);
		assertTrue(segments.get(0).endsWith("。"), "第一段结尾是：" + segments.get(0));
	}

	@Test
	@DisplayName("超过段数上限时末段补省略号")
	void splitMarksTruncation() {
		List<String> segments = ChatText.split("字".repeat(2000), 2);
		assertEquals(2, segments.size());
		assertTrue(segments.get(1).endsWith("…"));
		assertTrue(segments.get(1).length() <= ChatText.MAX_LENGTH);
	}

	@Test
	@DisplayName("空文本没有分段")
	void splitEmpty() {
		assertTrue(ChatText.split("", 8).isEmpty());
		assertTrue(ChatText.split("   ", 8).isEmpty());
	}

	@Test
	@DisplayName("clean 不截断，sanitize 才截断：分段前绝不能先砍")
	void cleanKeepsLongText() {
		String text = "字".repeat(1000);
		assertEquals(1000, ChatText.clean(text).length());
		assertEquals(ChatText.MAX_LENGTH, ChatText.sanitize(text).length());
	}

	@Test
	@DisplayName("回归：1000 字经 clean + split 必须切成多段，而不是一段 256 字")
	void cleanThenSplitProducesMultipleSegments() {
		// 真机事故：chat() 先调 sanitize 再 split，长文本在分段前就被截到 256，
		// 结果只发出一条 256 字的消息，日志里也没有「已分成 N 段」
		String text = ChatText.clean("字".repeat(1000));
		List<String> segments = ChatText.split(text, 8);
		assertTrue(segments.size() >= 4, "实际段数：" + segments.size());
		assertEquals(1000, String.join("", segments).length(), "分段后不应丢失内容");
	}
}
