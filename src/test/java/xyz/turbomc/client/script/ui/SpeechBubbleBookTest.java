package xyz.turbomc.client.script.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SpeechBubbleBook} 的单元测试。
 *
 * <p>钉的是 DEF-06：气泡到期被移除后，VM 仍会一直上报同一句，如果只做「到期就删」，
 * 下一帧会把它当新气泡重新登记，气泡就永久挡在 HUD 上。真机上这条要等 6 秒、
 * 21 秒分别截图才能看出，单测里一秒就能钉住。</p>
 */
class SpeechBubbleBookTest {
	private static final long HOLD_MS = 5000L;

	@Test
	@DisplayName("到期移除后，同一条消息不会被重新登记")
	void expiredDoesNotComeBack() {
		SpeechBubbleBook book = new SpeechBubbleBook(HOLD_MS);
		book.poll("甲", "你好", false, "u1", 0L);
		assertEquals(1, book.size());

		book.expire(HOLD_MS + 1);
		assertEquals(0, book.size(), "超过停留时长应当消失");

		// VM 还在报同一句（usageId 不变），这里必须继续不显示
		book.poll("甲", "你好", false, "u1", HOLD_MS + 2);
		assertEquals(0, book.size(), "同一条已播完的消息不该复活");
	}

	@Test
	@DisplayName("再说一句（usageId 变了）仍然能显示")
	void newUtteranceShows() {
		SpeechBubbleBook book = new SpeechBubbleBook(HOLD_MS);
		book.poll("甲", "你好", false, "u1", 0L);
		book.expire(HOLD_MS + 1);

		book.poll("甲", "你好", false, "u2", HOLD_MS + 2);
		assertEquals(1, book.size(), "每次说话都会换 usageId，不能被上一条记录挡住");
		assertEquals("u2", book.snapshot().get(0).usageId());
	}

	@Test
	@DisplayName("没到点的气泡不动")
	void notExpiredYet() {
		SpeechBubbleBook book = new SpeechBubbleBook(HOLD_MS);
		book.poll("甲", "你好", false, "u1", 0L);
		book.expire(HOLD_MS);
		assertEquals(1, book.size());
	}

	@Test
	@DisplayName("空名字或空内容不登记：VM 停止说话时会把 text 置空")
	void ignoresEmpty() {
		SpeechBubbleBook book = new SpeechBubbleBook(HOLD_MS);
		book.poll("", "你好", false, "u1", 0L);
		book.poll("甲", "", false, "u2", 0L);
		assertEquals(0, book.size());
	}

	@Test
	@DisplayName("被新的一句顶掉时，旧 usageId 同样不再复活")
	void replacedIsRetired() {
		SpeechBubbleBook book = new SpeechBubbleBook(HOLD_MS);
		book.poll("甲", "第一句", false, "u1", 0L);
		book.poll("甲", "第二句", false, "u2", 100L);
		assertEquals(1, book.size());

		// 模拟 VM 回退上报旧的一句：不该把第二句顶掉
		book.poll("甲", "第一句", false, "u1", 200L);
		assertEquals("u2", book.snapshot().get(0).usageId());
	}

	@Test
	@DisplayName("clear 之后一切重来：换世界不该带着上一局的记忆")
	void clearForgets() {
		SpeechBubbleBook book = new SpeechBubbleBook(HOLD_MS);
		book.poll("甲", "你好", false, "u1", 0L);
		book.expire(HOLD_MS + 1);
		book.clear();
		book.poll("甲", "你好", false, "u1", HOLD_MS + 2);
		assertEquals(1, book.size());
	}

	@Test
	@DisplayName("记住的 usageId 有上限，不会随对局无限增长")
	void retiredIsBounded() {
		SpeechBubbleBook book = new SpeechBubbleBook(HOLD_MS);
		for (int i = 0; i < 500; i++) {
			book.poll("甲", "第 " + i + " 句", false, "u" + i, i * 1000L);
			book.expire(i * 1000L + HOLD_MS + 1);
		}
		assertEquals(0, book.size(), "每句都应按时消失");
		// 只验证行为，不暴露内部集合大小：最老的一条被淘汰后仍不影响新说话
		book.poll("甲", "最后一句", false, "u-last", 10_000_000L);
		assertEquals(1, book.size());
	}

	@Test
	@DisplayName("四条气泡、上限 3：最早登记的被挤掉，并报出剩余条数")
	void viewDropsOldest() {
		SpeechBubbleBook book = new SpeechBubbleBook(HOLD_MS);
		// 同一时刻登记：模拟四个角色在同一 tick 说话
		book.poll("甲", "甲说话", false, "u1", 1_000L);
		book.poll("乙", "乙说话", false, "u2", 1_000L);
		book.poll("丙", "丙思考", true, "u3", 1_000L);
		book.poll("丁", "丁说话", false, "u4", 1_000L);

		SpeechBubbleBook.View view = book.view(3);
		assertEquals(3, view.shown().size());
		assertEquals(1, view.hidden(), "被挤掉的那条要有提示，不能静默");
		assertEquals("乙", view.shown().get(0).name(), "时间戳相同按登记顺序，最早登记的甲先被挤掉");
		assertEquals("丁", view.shown().get(2).name());
	}

	@Test
	@DisplayName("上限够大时全部显示，不报剩余")
	void viewShowsAll() {
		SpeechBubbleBook book = new SpeechBubbleBook(HOLD_MS);
		book.poll("甲", "甲说话", false, "u1", 1_000L);
		book.poll("乙", "乙说话", false, "u2", 1_000L);
		SpeechBubbleBook.View view = book.view(6);
		assertEquals(2, view.shown().size());
		assertEquals(0, view.hidden());
	}

	@Test
	@DisplayName("上限为 1 或非法值时按 1 处理，只留最新一条")
	void viewLimitFloor() {
		SpeechBubbleBook book = new SpeechBubbleBook(HOLD_MS);
		book.poll("甲", "旧", false, "u1", 1_000L);
		book.poll("乙", "新", false, "u2", 2_000L);
		assertEquals("乙", book.view(1).shown().get(0).name());
		assertEquals(1, book.view(0).shown().size(), "0 或负数按 1 处理，不能画出空视图");
	}
}
