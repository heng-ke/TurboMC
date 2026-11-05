package xyz.turbomc.client.script.runtime;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同一条消息只触发一次。
 *
 * <p>聊天事件可能对同一条消息回调多次（服务端广播、代理转发、其它模组重放），
 * 而脚本关心的是「这条消息处理过了没有」，不是「收到几次回调」。这里做去重：</p>
 *
 * <ul>
 *   <li><b>有签名</b>：用消息签名当身份。签名由服务端为每条消息生成，天然唯一，
 *       同一条消息无论回调几次都只放行一次，而玩家再打一遍相同文字属于新消息，照常触发。</li>
 *   <li><b>无签名</b>（单机、离线、无签名模式）：退化为「正文 + 时间窗」。
 *       重复投递都在毫秒级，{@link #UNSIGNED_WINDOW_MS} 足够覆盖；
 *       而人重新打一遍同一句话通常要一两秒，不会误吞。</li>
 * </ul>
 *
 * <p>刻意<b>不</b>按正文永久去重：那会让「再说一次同样的暗号」永远失效，
 * 玩家只能重开引擎，是把一个 bug 换成一个更难理解的行为。</p>
 */
public final class ChatGate {
	/** 无签名时的去重窗口。 */
	private static final long UNSIGNED_WINDOW_MS = 1000L;
	/** 记录上限，防止长时间挂机积累。 */
	private static final int MAX_ENTRIES = 128;

	/** 键 → 过期时间；有签名的记为永不过期。 */
	private static final Map<String, Long> SEEN = new LinkedHashMap<>();

	private ChatGate() {
	}

	/**
	 * 这条消息是否还没处理过。
	 *
	 * @param text      消息正文（含 {@code <玩家名>} 前缀，原样传入即可）
	 * @param signature 消息签名摘要；无签名传 {@code null}
	 * @return {@code true} 表示首次收到，应当触发
	 */
	public static synchronized boolean accept(String text, String signature) {
		long now = System.currentTimeMillis();
		prune(now);

		String key;
		long deadline;
		if (signature != null && !signature.isEmpty()) {
			key = "s:" + signature;
			deadline = Long.MAX_VALUE;
		} else {
			key = "t:" + (text == null ? "" : text);
			deadline = now + UNSIGNED_WINDOW_MS;
		}

		if (SEEN.containsKey(key)) {
			return false;
		}
		SEEN.put(key, deadline);
		if (SEEN.size() > MAX_ENTRIES) {
			Iterator<String> oldest = SEEN.keySet().iterator();
			if (oldest.hasNext()) {
				oldest.next();
				oldest.remove();
			}
		}
		return true;
	}

	/** 清空记录。切换工程或重开引擎时调用，避免旧消息挡住新工程。 */
	public static synchronized void reset() {
		SEEN.clear();
	}

	private static void prune(long now) {
		SEEN.entrySet().removeIf(entry -> entry.getValue() != Long.MAX_VALUE && entry.getValue() < now);
	}
}
