package xyz.turbomc.client.script.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 大模型请求与会话管理。
 *
 * <p><b>为什么网络只能在 Java 侧</b>：游戏内跑的是 Rhino 里的 scratch-vm，没有
 * {@code fetch} / {@code XMLHttpRequest}，也没有 {@code Worker}——混合 TurboWarp 扩展时
 * 已经验证过，扩展加载器的第一句 {@code new Worker} 就会 {@code ReferenceError}。
 * 顺带的好处是 API Key 留在本地配置文件，导出给别人玩的 sb3 里不会带出密钥。</p>
 *
 * <p><b>响应式而不是阻塞</b>：{@code HttpClient.sendAsync} 提交后立刻返回，脚本线程由
 * {@code ScriptContext.requestWaitUntil} 挂起，每帧 {@code ScriptBridge.poll} 查一次
 * {@link #isDone(String)}。主线程上没有 {@code get} / {@code join}，单帧预算不受影响。</p>
 *
 * <p>本类刻意不引用任何 Minecraft 类型，也不引用 {@code TurboMCConfig}：配置由调用方
 * 通过 {@link #settingsProvider} 注入。这样纯 JVM 冒烟测试可以直接给它一份假配置，
 * 打本地桩，不必启动客户端。</p>
 */
public final class AiService {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/ai");
	private static final AiService INSTANCE = new AiService();

	/**
	 * 单次请求的输入上限（字符）。
	 *
	 * <p>不是为了省 token，而是防止脚本把整张列表或长段日志拼进去：超出模型上下文时
	 * 接口返回 400，整轮失败，原因还埋在响应体里。8000 字符对聊天场景绰绰有余。</p>
	 */
	private static final int MAX_PROMPT_CHARS = 8000;

	/**
	 * 一次请求的配置快照。
	 *
	 * @param baseUrl     不含 {@code /chat/completions}；宿主自己拼（填全了也认）
	 * @param apiKey      可为空（本地 Ollama / LM Studio 通常不需要）
	 * @param model       模型名，空则视为未配置
	 * @param temperature 采样温度
	 * @param maxTokens   回复长度上限
	 * @param timeoutMs   HTTP 超时；脚本侧等待上限由它换算
	 * @param historyTurns 带多少轮历史；{@code <= 0} 表示不带
	 * @param maxConcurrent 全局并发上限
	 */
	public record Settings(String baseUrl, String apiKey, String model, double temperature,
						   int maxTokens, int timeoutMs, int historyTurns, int maxConcurrent) {

		/** 未配置时的占位值：任何积木调用都会立刻失败，不产生网络请求。 */
		public static final Settings DISABLED =
				new Settings("", "", "", 0.7, 300, 30000, 8, 2);

		/** 只要有地址和模型就能用：本地部署通常不校验 API Key。 */
		public boolean configured() {
			return baseUrl != null && !baseUrl.isBlank()
					&& model != null && !model.isBlank();
		}
	}

	/** 一条对话消息。只区分 user / assistant 两种角色，system 单独存在会话上。 */
	private record Message(String role, String content) {
	}

	/** 一个会话：system 提示 + 历史 + 在途请求 + 最近回复。 */
	private static final class Session {
		String system = "";
		String last = "";
		/** 在途请求 id；非 null 表示该会话忙，新的请求直接失败而不是排队。 */
		String pending;
		/** 只存 user / assistant，system 单独存，不参与裁剪。 */
		final Deque<Message> history = new ArrayDeque<>();
	}

	private final Map<String, Session> sessions = new ConcurrentHashMap<>();
	/** key 为请求 id；完成或取消后移除。{@link #isDone(String)} 就是查这里。 */
	private final Map<String, CompletableFuture<HttpResponse<String>>> inFlight = new ConcurrentHashMap<>();

	private volatile Supplier<Settings> settingsProvider = () -> Settings.DISABLED;
	private HttpClient http;
	private ExecutorService executor;
	private int counter;

	private AiService() {
	}

	public static AiService get() {
		return INSTANCE;
	}

	/**
	 * 注入配置来源。
	 *
	 * <p>用 {@link Supplier} 而不是在这里直接读配置文件：配置可能在模组菜单里被改，
	 * 每次请求都取一次最新值，比缓存一份再想办法失效简单。</p>
	 */
	public void settingsProvider(Supplier<Settings> provider) {
		this.settingsProvider = provider == null ? () -> Settings.DISABLED : provider;
	}

	/**
	 * 提交一次请求。
	 *
	 * @return 请求 id，供脚本侧 {@code requestWaitUntil("ai:" + id)} 挂起；<b>失败返回 null</b>
	 *         （未配置、会话忙、并发满、文本为空）
	 */
	public String ask(String sessionKey, String text) {
		String key = normalize(sessionKey);
		Settings settings = settingsProvider.get();
		if (settings == null || !settings.configured()) {
			invalidate(key);
			LOG.warn("[ai] 未配置：请在 config/turbomc.json 里填写 ai.baseUrl 与 ai.model");
			return null;
		}
		String prompt = capPrompt(text == null ? "" : text.trim());
		if (prompt.isEmpty()) {
			invalidate(key);
			return null;
		}
		Session session = sessions.computeIfAbsent(key, k -> new Session());

		synchronized (session) {
			// 新一轮开始，上一轮的回复立刻失效：
			// 否则本次失败时，脚本里的「会话 X 的最后回复」会把上一次的答案当成这次的
			session.last = "";
			if (session.pending != null) {
				// 排队会让历史顺序与服务端实际处理顺序错开，这里选择直接失败
				LOG.warn("[ai] 会话「{}」已有在途请求，本次忽略", key);
				return null;
			}
			if (inFlight.size() >= Math.max(1, settings.maxConcurrent())) {
				LOG.warn("[ai] 并发已达上限 {}，本次忽略", settings.maxConcurrent());
				return null;
			}
			String id;
			synchronized (this) {
				id = "r" + (++counter);
			}
			session.pending = id;
			HttpRequest request;
			try {
				request = buildRequest(settings, session, prompt);
			} catch (RuntimeException e) {
				session.pending = null;
				LOG.warn("[ai] 请求构造失败：{}", e.toString());
				return null;
			}
			CompletableFuture<HttpResponse<String>> future;
			try {
				future = client(settings).sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			} catch (RuntimeException e) {
				session.pending = null;
				LOG.warn("[ai] 请求提交失败：{}", e.toString());
				return null;
			}
			inFlight.put(id, future);
			future.whenComplete((response, error) -> complete(id, key, session, prompt, response, error));
			return id;
		}
	}

	/** 请求是否已经有结论（成功、失败、超时、被取消都算）。 */
	public boolean isDone(String requestId) {
		return requestId == null || !inFlight.containsKey(requestId);
	}

	/** 会话的最近一次成功回复；没有或上一次失败则为空串。 */
	public String reply(String sessionKey) {
		Session session = sessions.get(normalize(sessionKey));
		if (session == null) {
			return "";
		}
		synchronized (session) {
			return session.last == null ? "" : session.last;
		}
	}

	/** 清空历史与最近回复，保留 system 提示。 */
	public void reset(String sessionKey) {
		String key = normalize(sessionKey);
		Session session = sessions.get(key);
		if (session == null) {
			return;
		}
		synchronized (session) {
			session.history.clear();
			session.last = "";
		}
	}

	/**
	 * 设置 system 提示。
	 *
	 * <p><b>顺带清空历史</b>：换了人格之后旧对话与新人格互相矛盾，留着只会让模型前后不一。
	 * 这不是顺手做的优化，是刻意的设计——所以单独写在这里，别被"只改一个字段"重构掉。</p>
	 */
	public void system(String sessionKey, String text) {
		Session session = sessions.computeIfAbsent(normalize(sessionKey), k -> new Session());
		synchronized (session) {
			session.system = text == null ? "" : text;
			session.history.clear();
			session.last = "";
		}
	}

	/**
	 * 取消所有在途请求并清空会话。
	 *
	 * <p>切包、停引擎、工程启动失败都要调：回调落在已停止的 VM 上没有意义，而线程池
	 * 里的连接不取消就是泄漏。</p>
	 */
	public void cancelAll() {
		for (Map.Entry<String, CompletableFuture<HttpResponse<String>>> entry : inFlight.entrySet()) {
			CompletableFuture<HttpResponse<String>> future = entry.getValue();
			if (future != null) {
				future.cancel(true);
			}
		}
		inFlight.clear();
		sessions.clear();
	}

	/**
	 * 脚本侧等待的帧数上限。
	 *
	 * <p>必须比 HTTP 超时<b>大</b>：{@code __tmcWaitUntil} 到点会自行 resolve、不通知 Java，
	 * 两边相等时脚本会先往下跑，而请求还在飞（架构文档里「走向」踩过同一个坑）。
	 * 多给 40 tick（2 秒）留出解析与线程调度的时间。</p>
	 */
	public int waitLimitTicks() {
		Settings settings = settingsProvider.get();
		int timeout = settings == null ? Settings.DISABLED.timeoutMs() : settings.timeoutMs();
		return Math.max(40, timeout / 50 + 40);
	}

	// ---- 内部 ----

	private void complete(String id, String key, Session session, String prompt,
						  HttpResponse<String> response, Throwable error) {
		String reply = null;
		String failure;
		if (error != null) {
			failure = describe(error);
		} else if (response == null) {
			failure = "没有响应";
		} else if (response.statusCode() / 100 != 2) {
			failure = "HTTP " + response.statusCode() + " " + truncate(response.body(), 200);
		} else {
			try {
				reply = parseContent(response.body());
				failure = null;
			} catch (RuntimeException e) {
				failure = "响应解析失败：" + e.getMessage();
			}
		}

		synchronized (session) {
			session.pending = null;
			if (reply != null) {
				session.last = reply;
				appendHistory(session, prompt, reply);
			} else {
				// 失败时清空：宁可让脚本拿到空串，也不要它把上一次的旧回复当成这一次的
				session.last = "";
				// 取消是切包/停引擎的预期行为，不是故障——照着打 warn 会把真错误淹掉
				if (!isCancellation(error)) {
					LOG.warn("[ai] 会话「{}」请求失败：{}", key, failure);
				}
			}
		}
		inFlight.remove(id);
	}

	/** 取消会被包装成 {@code CompletionException}，所以要顺着 cause 链找。 */
	private static boolean isCancellation(Throwable error) {
		Throwable current = error;
		for (int depth = 0; current != null && depth < 8; depth++) {
			if (current instanceof java.util.concurrent.CancellationException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private void appendHistory(Session session, String prompt, String reply) {
		Settings settings = settingsProvider.get();
		int turns = settings == null ? Settings.DISABLED.historyTurns() : settings.historyTurns();
		if (turns <= 0) {
			// 明确要求「不带历史」：不存，也就没有裁剪问题
			session.history.clear();
			return;
		}
		session.history.addLast(new Message("user", prompt));
		session.history.addLast(new Message("assistant", reply));
		int limit = turns * 2;
		while (session.history.size() > limit) {
			session.history.removeFirst();
		}
	}

	private HttpRequest buildRequest(Settings settings, Session session, String prompt) {
		JsonObject body = new JsonObject();
		body.addProperty("model", settings.model());
		body.addProperty("temperature", settings.temperature());
		body.addProperty("max_tokens", settings.maxTokens());
		// 流式需要逐帧读 buffer 与增量渲染，一期不做；显式写 false 免得网关按默认值行为不一
		body.addProperty("stream", false);

		JsonArray messages = new JsonArray();
		if (!session.system.isBlank()) {
			messages.add(message("system", session.system));
		}
		for (Message m : session.history) {
			messages.add(message(m.role(), m.content()));
		}
		messages.add(message("user", prompt));
		body.add("messages", messages);

		HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(settings.baseUrl()))
				.timeout(Duration.ofMillis(Math.max(1000, settings.timeoutMs())))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
		if (settings.apiKey() != null && !settings.apiKey().isBlank()) {
			builder.header("Authorization", "Bearer " + settings.apiKey().trim());
		}
		return builder.build();
	}

	private static JsonObject message(String role, String content) {
		JsonObject object = new JsonObject();
		object.addProperty("role", role);
		object.addProperty("content", content);
		return object;
	}

	/** 接受 {@code https://host/v1} 与 {@code https://host/v1/chat/completions} 两种写法。 */
	static URI endpoint(String baseUrl) {
		String base = baseUrl == null ? "" : baseUrl.trim();
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		if (!base.endsWith("/chat/completions")) {
			base = base + "/chat/completions";
		}
		return URI.create(base);
	}

	/** 从 OpenAI 兼容响应里取第一段回复。 */
	static String parseContent(String body) {
		JsonElement parsed = JsonParser.parseString(body);
		if (!parsed.isJsonObject()) {
			throw new IllegalStateException("响应不是 JSON 对象");
		}
		JsonObject root = parsed.getAsJsonObject();
		JsonArray choices = root.getAsJsonArray("choices");
		if (choices == null || choices.isEmpty()) {
			throw new IllegalStateException("没有 choices");
		}
		JsonObject first = choices.get(0).getAsJsonObject();
		JsonObject message = first.getAsJsonObject("message");
		if (message == null) {
			throw new IllegalStateException("choices[0] 没有 message");
		}
		JsonElement content = message.get("content");
		if (content == null || content.isJsonNull()) {
			return "";
		}
		return content.getAsString();
	}

	private HttpClient client(Settings settings) {
		HttpClient current = http;
		if (current != null) {
			return current;
		}
		synchronized (this) {
			if (http == null) {
				executor = Executors.newFixedThreadPool(2, runnable -> {
					Thread thread = new Thread(runnable, "turbomc-ai");
					// 守护线程：冒烟测试结束时不该被它挂住
					thread.setDaemon(true);
					return thread;
				});
				http = HttpClient.newBuilder()
						.connectTimeout(Duration.ofMillis(Math.max(1000, settings.timeoutMs())))
						.executor(executor)
						.build();
			}
			return http;
		}
	}

	private static String normalize(String sessionKey) {
		String key = sessionKey == null ? "" : sessionKey.trim();
		return key.isEmpty() ? "main" : key;
	}

	/**
	 * 让某个会话的「最后回复」失效。
	 *
	 * <p>失败路径统一走这里。脚本的常见写法是「让会话回应 X」紧接着「说 会话的最后回复」，
	 * 若失败时留着上一轮的答案，玩家会看到答非所问——问新问题，回上一次的话。</p>
	 */
	private void invalidate(String key) {
		Session session = sessions.get(key);
		if (session == null) {
			return;
		}
		synchronized (session) {
			session.last = "";
		}
	}

	/** 截断过长的输入；按码点边界切，不留半个代理对。 */
	private static String capPrompt(String prompt) {
		if (prompt.length() <= MAX_PROMPT_CHARS) {
			return prompt;
		}
		int end = MAX_PROMPT_CHARS;
		if (Character.isHighSurrogate(prompt.charAt(end - 1))) {
			end--;
		}
		LOG.warn("[ai] 输入过长（{} 字），已截断到 {} 字", prompt.length(), end);
		return prompt.substring(0, end);
	}

	private static String describe(Throwable error) {
		if (error instanceof java.util.concurrent.CancellationException) {
			return "已取消";
		}
		String message = error.getMessage();
		return error.getClass().getSimpleName() + (message == null ? "" : "：" + message);
	}

	private static String truncate(String text, int limit) {
		if (text == null) {
			return "";
		}
		String flat = text.replace('\n', ' ').trim();
		return flat.length() <= limit ? flat : flat.substring(0, limit) + "…";
	}
}
