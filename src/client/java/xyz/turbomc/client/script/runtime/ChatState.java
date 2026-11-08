package xyz.turbomc.client.script.runtime;

/**
 * 最近一条聊天的原文。
 *
 * <p>聊天在宿主侧本来是一条「只进不出」的事件：{@code whenChat} 是帽子积木，文本只用来
 * 过滤关键词，命中后就被丢弃。要在脚本里对这句话本身做判断（比如交给 AI 处理），
 * 必须有人把它存下来。</p>
 *
 * <p><b>为什么单独开一个类</b>：写入口在 {@code TurboMCClient}（事件源），读出口在
 * 动作层（{@code NativeRegistry}）。动作层不能反过来依赖 {@code ScriptEngine}——
 * 那是上层门面，会形成环。这个类只做一件事，没有依赖，两边都能安全引用。</p>
 *
 * <p><b>只保留最后一条</b>：脚本正在等待（寻路、HTTP）时连来三条，前两条会被覆盖。
 * 这是「最近的聊天」这个名字承诺的语义；要完整队列是另一件事，别在这里偷偷扩。</p>
 */
public final class ChatState {
	private static volatile String lastChat = "";

	private ChatState() {
	}

	/** 记录一条聊天。{@code null} 按空串处理——积木不该拿到 null。 */
	public static void record(String message) {
		lastChat = message == null ? "" : message;
	}

	/** 最近一条聊天；从未收到过则为空串。 */
	public static String last() {
		return lastChat;
	}

	/** 换工程或停引擎时清空：上一份工程收过的消息不该被下一份读到。 */
	public static void clear() {
		lastChat = "";
	}
}
