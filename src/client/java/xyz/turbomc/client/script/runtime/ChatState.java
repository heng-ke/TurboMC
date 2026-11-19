package xyz.turbomc.client.script.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * 最近若干条聊天的原文。
 *
 * <p>聊天在宿主侧本来是一条「只进不出」的事件：{@code whenChat} 是帽子积木，文本只用来
 * 过滤关键词，命中后就被丢弃。要在脚本里对这句话本身做判断（比如交给 AI 处理），
 * 必须有人把它存下来。</p>
 *
 * <p><b>为什么单独开一个类</b>：写入口在 {@code TurboMCClient}（事件源），读出口在
 * 动作层（{@code NativeRegistry}）。动作层不能反过来依赖 {@code ScriptEngine}——
 * 那是上层门面，会形成环。这个类只做一件事，没有依赖，两边都能安全引用。</p>
 *
 * <p><b>环形缓冲而不是无界队列</b>：容量固定 {@value #CAPACITY} 条，满了覆盖最旧的一条。
 * 理由是聊天由网络线程写入、脚本随时读取，无界增长会在长时间挂机时吃光内存，而
 * 「历史」本身没有上限需求——脚本要的是「刚才几句」，不是全部。超出容量的消息静默丢弃，
 * 与「最近的聊天」的语义一致。</p>
 *
 * <p><b>两条读出口</b>：{@link #last()} 给「最近的聊天」积木（只关心最新一句），
 * {@link #history(int)} 给「最近 [N] 条聊天」（要上下文，例如连续指令或对话恢复）。</p>
 *
 * <p><b>线程</b>：写入来自网络线程（聊天事件），读取来自脚本线程，所有访问都在锁内，
 * 读出去的是一份拷贝，调用方不会被后续写入影响。</p>
 */
public final class ChatState {
	/** 保留条数。32 条足够覆盖「刚才这几句」，且任何时刻只占 32 个引用。 */
	public static final int CAPACITY = 32;

	private static final Object LOCK = new Object();
	private static final String[] BUFFER = new String[CAPACITY];
	/** 下一个写入位置。 */
	private static int next;
	/** 已存条数，最多 {@link #CAPACITY}。 */
	private static int size;

	private ChatState() {
	}

	/** 记录一条聊天。{@code null} 按空串处理——积木不该拿到 null。 */
	public static void record(String message) {
		String text = message == null ? "" : message;
		synchronized (LOCK) {
			BUFFER[next] = text;
			next = (next + 1) % CAPACITY;
			if (size < CAPACITY) {
				size++;
			}
		}
	}

	/** 最近一条聊天；从未收到过则为空串。 */
	public static String last() {
		synchronized (LOCK) {
			return size == 0 ? "" : BUFFER[(next - 1 + CAPACITY) % CAPACITY];
		}
	}

	/**
	 * 最近 {@code count} 条聊天，**旧的在前**（可直接按顺序遍历、拼成对话上下文）。
	 *
	 * <p>{@code count} 夹在 1–{@value #CAPACITY}；实际返回条数取 {@code count} 与已存条数的较小值。
	 * 缓冲没满时返回全部已存消息，不会用空串填充。</p>
	 */
	public static List<String> history(int count) {
		int want = Math.max(1, Math.min(CAPACITY, count));
		synchronized (LOCK) {
			int take = Math.min(want, size);
			List<String> out = new ArrayList<>(take);
			for (int i = take; i > 0; i--) {
				out.add(BUFFER[Math.floorMod(next - i, CAPACITY)]);
			}
			return out;
		}
	}

	/** 换工程或停引擎时清空：上一份工程收过的消息不该被下一份读到。 */
	public static void clear() {
		synchronized (LOCK) {
			for (int i = 0; i < CAPACITY; i++) {
				BUFFER[i] = null;
			}
			next = 0;
			size = 0;
		}
	}
}
