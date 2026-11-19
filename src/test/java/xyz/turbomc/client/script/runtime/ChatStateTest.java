package xyz.turbomc.client.script.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 聊天环形缓冲的语义：顺序、环绕、条数钳制、清空。
 *
 * <p>这层必须是纯函数式的（除静态状态外无依赖），因为它是「最近 [N] 条聊天」积木的唯一数据源。
 * 顺序搞反（新的在前）会让脚本把对话上下文拼反，而界面上看不出任何异常。</p>
 */
class ChatStateTest {

	@BeforeEach
	void reset() {
		ChatState.clear();
	}

	@Test
	@DisplayName("没收到消息时：最后一条是空串，历史为空数组")
	void emptyState() {
		assertEquals("", ChatState.last());
		assertTrue(ChatState.history(5).isEmpty());
	}

	@Test
	@DisplayName("历史是旧的在前，方便直接拼上下文")
	void historyIsOldestFirst() {
		ChatState.record("a");
		ChatState.record("b");
		ChatState.record("c");

		assertEquals("c", ChatState.last());
		assertEquals(List.of("a", "b", "c"), ChatState.history(3));
		// 只要最近两条时，取尾部
		assertEquals(List.of("b", "c"), ChatState.history(2));
	}

	@Test
	@DisplayName("缓冲没满时不补空串，返回条数等于已存条数")
	void notPadded() {
		ChatState.record("only");
		assertEquals(List.of("only"), ChatState.history(32));
	}

	@Test
	@DisplayName("超过容量后环绕：保留的是最新的 32 条，最旧的被丢弃")
	void wrapsAround() {
		for (int i = 1; i <= ChatState.CAPACITY + 3; i++) {
			ChatState.record("m" + i);
		}
		List<String> history = ChatState.history(ChatState.CAPACITY);
		assertEquals(ChatState.CAPACITY, history.size());
		// 35 条里保留第 4…35 条
		assertEquals("m4", history.get(0));
		assertEquals("m" + (ChatState.CAPACITY + 3), history.get(history.size() - 1));
		assertEquals("m" + (ChatState.CAPACITY + 3), ChatState.last());
	}

	@Test
	@DisplayName("条数参数钳在 1–容量：0 取 1 条，超大值取满容量")
	void countIsClamped() {
		for (int i = 1; i <= 5; i++) {
			ChatState.record("m" + i);
		}
		assertEquals(List.of("m5"), ChatState.history(0));
		assertEquals(5, ChatState.history(999).size());
	}

	@Test
	@DisplayName("清空后历史与最后一条都归零（换工程不能读到上一份的消息）")
	void clearResets() {
		ChatState.record("a");
		ChatState.clear();
		assertEquals("", ChatState.last());
		assertTrue(ChatState.history(10).isEmpty());
	}

	@Test
	@DisplayName("null 按空串处理，不会把 null 传进积木")
	void nullBecomesEmpty() {
		ChatState.record(null);
		assertEquals("", ChatState.last());
		assertEquals(List.of(""), ChatState.history(1));
	}
}
