package xyz.turbomc.client.script.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * 聊天发送队列。
 *
 * <p>存在的理由有两个，都不在「立刻发」这一侧：</p>
 * <ol>
 *   <li><b>超长文本要分段</b>：单条上限 256 字符 / 768 字节，模型回复常常超。切开的段落
 *       必须拉开间隔发，连着两条会被服务端当成刷屏。</li>
 *   <li><b>同一帧的多次调用不能静默丢掉</b>：以前 50 ms 内只放行一条，脚本连调两次
 *       `在聊天栏说` 第二条直接消失，没有任何提示。</li>
 * </ol>
 *
 * <p>间隔由配置决定（默认 3 tick = 150 ms，约 6.7 条/秒）。原版 1.19+ 的
 * {@code ChatSpamTracker} 阈值是每秒 20 条，每 tick 一条正好贴着上限，所以默认不取 1。</p>
 *
 * <p><b>线程</b>：只在客户端主线程使用——入队来自脚本执行（VM 的 tick），出队来自
 * {@code ScriptEngine.tick}，两者同线程，因此不做同步。</p>
 */
public final class ChatQueue {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");

	/** 真正把一段文本发出去。抽出来是为了让队列能在纯 JVM 里测。 */
	@FunctionalInterface
	public interface Sender {
		void send(String text);
	}

	private static final Deque<String> QUEUE = new ArrayDeque<>();
	private static Sender sender;
	private static IntSupplier intervalProvider = () -> 3;
	/** 还差几次 tick 才能发下一条；0 表示空闲。 */
	private static int cooldown;
	/** 队列溢出的提示只打一次：脚本写错时会持续溢出，逐条刷日志没有意义。 */
	private static boolean warnedOverflow;

	private ChatQueue() {
	}

	/**
	 * 装上发送出口与间隔来源。
	 *
	 * <p>两者都用方法引用注入，不在这里读配置：纯 JVM 冒烟会调
	 * {@code NativeRegistry.bootstrapOnce()}，那时没有配置文件。</p>
	 */
	public static void install(Sender newSender, IntSupplier interval) {
		sender = newSender;
		intervalProvider = interval == null ? () -> 3 : interval;
	}

	/**
	 * 排队若干段；空闲时立刻发出第一段。
	 *
	 * @param maxQueue 队列上限，满了就丢弃<b>新</b>来的段——已排队的顺序不能被后来者打乱
	 */
	public static void offer(List<String> segments, int maxQueue) {
		if (segments == null || segments.isEmpty() || sender == null) {
			return;
		}
		int limit = Math.max(1, maxQueue);
		for (String segment : segments) {
			if (segment == null || segment.isBlank()) {
				continue;
			}
			if (QUEUE.size() >= limit) {
				// 丢弃是静默的：玩家只会看到回复变短，不知道为什么
				if (!warnedOverflow) {
					warnedOverflow = true;
					LOG.warn("[script] 聊天待发队列已满（上限 {} 段），多出的段落被丢弃"
							+ "（本提示只出现一次；脚本连续大量发送时会触发）", limit);
				}
				break;
			}
			QUEUE.addLast(segment);
		}
		drain();
	}

	/** 每 tick 调一次：冷却结束后发一段。 */
	public static void tick() {
		if (cooldown > 0) {
			cooldown--;
		}
		drain();
	}

	/** 清空待发内容。切包、停引擎、停止全部都要调，否则上一份工程的话还会继续冒出来。 */
	public static void clear() {
		QUEUE.clear();
		cooldown = 0;
		// 「队列已满」只提示一次；不清掉的话换工程后这个诊断就永久沉默
		warnedOverflow = false;
	}

	public static int pending() {
		return QUEUE.size();
	}

	/** 测试用：清空队列并摘掉发送出口。 */
	static void resetForTest() {
		QUEUE.clear();
		cooldown = 0;
		sender = null;
		intervalProvider = () -> 3;
		warnedOverflow = false;
	}

	private static void drain() {
		if (sender == null || cooldown > 0 || QUEUE.isEmpty()) {
			return;
		}
		String text = QUEUE.pollFirst();
		sender.send(text);
		cooldown = Math.max(1, intervalProvider.getAsInt());
	}
}
