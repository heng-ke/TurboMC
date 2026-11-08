package xyz.turbomc.client.script.action;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChatQueue} 的单元测试。
 *
 * <p>守两件事：**同帧多次调用不再丢消息**，以及**分段之间有间隔**（间隔为 1 时
 * 每秒 20 条，正好触发原版刷屏判定，默认值是 3）。</p>
 *
 * <p>发送出口是注入的，所以这里不碰网络也不碰客户端。</p>
 */
class ChatQueueTest {
	private final List<String> sent = new ArrayList<>();

	@AfterEach
	void tearDown() {
		ChatQueue.resetForTest();
	}

	private void install(int intervalTicks) {
		ChatQueue.install(sent::add, () -> intervalTicks);
	}

	@Test
	@DisplayName("空闲时立刻发第一段：单条消息不该多等一个 tick")
	void sendsFirstSegmentImmediately() {
		install(3);
		ChatQueue.offer(List.of("a"), 8);
		assertEquals(List.of("a"), sent);
		assertEquals(0, ChatQueue.pending());
	}

	@Test
	@DisplayName("后续段落按配置的间隔逐条发出")
	void respectsInterval() {
		install(3);
		ChatQueue.offer(List.of("a", "b", "c"), 8);
		assertEquals(1, sent.size(), "第一段应立即发出");

		ChatQueue.tick();
		ChatQueue.tick();
		assertEquals(1, sent.size(), "冷却未结束不应发第二段");

		ChatQueue.tick();
		assertEquals(2, sent.size(), "第 3 个 tick 应发出第二段");

		ChatQueue.tick();
		ChatQueue.tick();
		ChatQueue.tick();
		assertEquals(List.of("a", "b", "c"), sent);
	}

	@Test
	@DisplayName("队列满时丢弃新来的段，不打乱已排队的顺序")
	void dropsOverflow() {
		install(3);
		ChatQueue.offer(List.of("a", "b", "c", "d"), 2);
		// a 立即发出，队列剩一个位置给 b；c、d 因超限被丢弃
		assertEquals(List.of("a"), sent);
		assertEquals(1, ChatQueue.pending());
	}

	@Test
	@DisplayName("clear 之后不再发任何段")
	void clearStopsSending() {
		install(1);
		ChatQueue.offer(List.of("a", "b", "c"), 8);
		ChatQueue.clear();
		ChatQueue.tick();
		ChatQueue.tick();
		assertEquals(List.of("a"), sent);
		assertEquals(0, ChatQueue.pending());
	}

	@Test
	@DisplayName("没装发送出口时静默忽略，不抛异常")
	void withoutSenderDoesNothing() {
		ChatQueue.offer(List.of("a"), 8);
		ChatQueue.tick();
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("空白段被跳过")
	void skipsBlankSegments() {
		install(3);
		ChatQueue.offer(java.util.Arrays.asList("", "  ", "有效"), 8);
		assertEquals(List.of("有效"), sent);
	}
}
