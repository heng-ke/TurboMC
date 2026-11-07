package xyz.turbomc.client.script.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChatGate} 的单元测试。
 *
 * <p>这个类管的是「同一条聊天只触发一次」。它在真机上很难验证——要复现
 * 「服务端广播 + 代理转发 + 其它模组重放」并不容易，但一旦错了就是「说一句话
 * 触发三次」这种明显的故障。所以在这里把三条分支都钉住。</p>
 */
class ChatGateTest {

	@AfterEach
	void clear() {
		ChatGate.reset();
	}

	@Test
	@DisplayName("有签名：同一条消息只放行一次")
	void sameSignaturePassesOnce() {
		assertTrue(ChatGate.accept("hello", "sig-1"), "首次应放行");
		assertFalse(ChatGate.accept("hello", "sig-1"), "同签名重复应被拦下");
		assertFalse(ChatGate.accept("hello", "sig-1"), "第三次同样应被拦下");
	}

	@Test
	@DisplayName("有签名：签名不同即视为新消息")
	void differentSignatureIsNewMessage() {
		assertTrue(ChatGate.accept("hello", "sig-1"));
		assertTrue(ChatGate.accept("hello", "sig-2"), "不同签名应视为新消息");
	}

	@Test
	@DisplayName("无签名：1 秒窗口内重复正文被拦下")
	void unsignedWindowBlocksRepeat() {
		assertTrue(ChatGate.accept("hello there", null), "首次应放行");
		assertFalse(ChatGate.accept("hello there", null), "窗口内重复应被拦下");
	}

	@Test
	@DisplayName("无签名：正文不同互不影响")
	void unsignedDifferentTextPasses() {
		assertTrue(ChatGate.accept("first", null));
		assertTrue(ChatGate.accept("second", null));
	}

	@Test
	@DisplayName("空签名按无签名处理，不当成「永不过期」")
	void emptySignatureTreatedAsUnsigned() {
		assertTrue(ChatGate.accept("body", ""), "首次应放行");
		assertFalse(ChatGate.accept("body", ""), "空签名重复应被窗口拦下");
	}

	@Test
	@DisplayName("reset 之后同一签名可以重新触发")
	void resetAllowsRerun() {
		assertTrue(ChatGate.accept("hello", "sig-1"));
		assertFalse(ChatGate.accept("hello", "sig-1"));
		ChatGate.reset();
		assertTrue(ChatGate.accept("hello", "sig-1"), "reset 后应重新放行");
	}

	@Test
	@DisplayName("null 正文不抛异常")
	void nullTextIsSafe() {
		assertTrue(ChatGate.accept(null, null));
		assertFalse(ChatGate.accept(null, null));
	}
}
