package xyz.turbomc.client.script.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		assertEquals("第一段 第二段", ChatText.clean("第一段\n第二段"));
		assertEquals("a b c", ChatText.clean("a\r\nb\nc"));
	}

	@Test
	@DisplayName("删掉 § 与颜色码")
	void stripsFormatting() {
		assertEquals("红色字", ChatText.clean("§c红色§r字"));
	}

	@Test
	@DisplayName("删掉控制字符，保留正常文本")
	void stripsControlCharacters() {
		assertEquals("abc", ChatText.clean("a\u0000b\u0007c"));
	}

	@Test
	@DisplayName("折叠换行带来的连续空格，并去首尾空白")
	void collapsesSpaces() {
		assertEquals("一句话 结束", ChatText.clean("  一句话 \n\n 结束  "));
	}

	@Test
	@DisplayName("孤立代理项被丢弃：半截 emoji 服务端判非法")
	void dropsLoneSurrogates() {
		assertEquals("ab", ChatText.clean("a\uD83Db"));
	}

	@Test
	@DisplayName("全是非法字符时返回空串，调用方据此放弃发送")
	void allIllegalBecomesEmpty() {
		assertEquals("", ChatText.clean("\n\n\u0000  "));
	}

	@Test
	@DisplayName("干净文本原样返回")
	void cleanTextUntouched() {
		String clean = "钻石斧可以砍树、挖矿、打怪。";
		assertEquals(clean, ChatText.clean(clean));
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
	@DisplayName("clean 不截断：1000 字原样保留，交给 split 分段")
	void cleanKeepsLongText() {
		String text = "字".repeat(1000);
		assertEquals(1000, ChatText.clean(text).length());
	}

	@Test
	@DisplayName("回归：1000 字经 clean + split 必须切成多段，而不是一段 256 字")
	void cleanThenSplitProducesMultipleSegments() {
		String text = ChatText.clean("字".repeat(1000));
		List<String> segments = ChatText.split(text, 8);
		assertTrue(segments.size() >= 4, "实际段数：" + segments.size());
		assertEquals(1000, String.join("", segments).length(), "分段后不应丢失内容");
	}
}