package xyz.turbomc.client.script.runtime;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 脚本自己发出的聊天，回声抑制。
 *
 * <p>客户端发出去的聊天会由服务端广播回来，再触发一次 {@code CHAT} 事件。
 * 如果脚本在「当聊天包含 X」里回复一条同样含 X 的聊天，就会形成
 * 「消息 → 帽子 → 再发消息 → 帽子」的回路，表现为「我只说了一句，它一直在跑」。</p>
 *
 * <p>做法很直接：发送时登记一条，收到消息时若包含登记的文本就抵消一条并判定为回声。
 * 只按文本匹配，因为收到的消息带 {@code <玩家名>} 前缀，做不到整串相等。</p>
 *
 * <p>登记有寿命（{@link #WINDOW_MS}）：留太久会把玩家手打的相同内容也吞掉。</p>
 */
public final class SelfChatEcho {
	/** 超过这个时长还没被回收就丢弃。 */
	private static final long WINDOW_MS = 3000L;
	/** 未回收登记的上限，防止脚本高频刷屏时无限堆积。 */
	private static final int MAX_PENDING = 16;

	private record Sent(String text, long at) {
	}

	private static final Deque<Sent> PENDING = new ArrayDeque<>();

	private SelfChatEcho() {
	}

	/** 脚本发出了一条聊天。 */
	public static synchronized void sentByScript(String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		prune();
		if (PENDING.size() >= MAX_PENDING) {
			PENDING.removeFirst();
		}
		PENDING.addLast(new Sent(text.trim(), System.currentTimeMillis()));
	}

	/**
	 * 这条收到的消息是不是脚本自己刚发的。
	 *
	 * <p>是则抵消一条登记并返回 {@code true}——同一条消息只会被吞一次，
	 * 玩家随后手打相同内容仍然会正常触发。</p>
	 */
	public static synchronized boolean isEcho(String incoming) {
		if (incoming == null || incoming.isEmpty() || PENDING.isEmpty()) {
			return false;
		}
		prune();
		for (var iterator = PENDING.iterator(); iterator.hasNext(); ) {
			Sent sent = iterator.next();
			if (incoming.contains(sent.text())) {
				iterator.remove();
				return true;
			}
		}
		return false;
	}

	private static void prune() {
		long now = System.currentTimeMillis();
		while (!PENDING.isEmpty() && now - PENDING.peekFirst().at() > WINDOW_MS) {
			PENDING.removeFirst();
		}
	}
}
