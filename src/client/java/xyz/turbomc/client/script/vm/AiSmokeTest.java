package xyz.turbomc.client.script.vm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.ai.AiService;
import xyz.turbomc.client.script.runtime.ChatState;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 对话的冒烟测试：不启动客户端，也不打真实接口。
 *
 * <p>两段：</p>
 * <ol>
 *   <li><b>服务层</b>：本地 {@link HttpServer} 桩，覆盖正常回复、非 2xx、坏 JSON、超时、
 *       会话互斥。这些是「积木返回值对不对」的根基，且不需要 VM。</li>
 *   <li><b>端到端</b>：真 VM 载入含 {@code turbomc_aiAsk / turbomc_aiReply} 的工程，
 *       验证「挂起 → 每帧 poll → 继续执行 → 变量拿到回复」整条链路。
 *       这一段守的是 {@code ScriptBridge.poll} 的 {@code ai:} 分支——漏了它等待会被
 *       立刻放行，变量永远为空，而且不报任何错。</li>
 * </ol>
 *
 * <p>用法：{@code ./gradlew vmAi}</p>
 */
public final class AiSmokeTest {
	private AiSmokeTest() {
	}

	private static final String FIXTURE = "ai-project.json";
	private static final String MARKER = "MARKER";

	public static void main(String[] args) throws Exception {
		NativeRegistry.bootstrapOnce();

		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/completions", AiSmokeTest::handle);
		server.setExecutor(Executors.newFixedThreadPool(4, runnable -> {
			Thread thread = new Thread(runnable, "ai-stub");
			thread.setDaemon(true);
			return thread;
		}));
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

		AiService ai = AiService.get();
		// 先走热一次再开始断言。
		//
		// 首个请求要付 HttpClient 初始化 + JVM 冷启动 + JIT 的成本，偶发超过后面的 3 秒硬超时，
		// 表现是第一条断言「正常回复」直接 ✗（整批跑 11 个冒烟任务时实测约一半概率，
		// 单独跑永远通过）。用一个一次性会话、宽超时预热，既不打乱断言，
		// 也不放松任何正式用例的超时阈值——那些阈值本身就是被测语义的一部分。
		ai.settingsProvider(() -> new AiService.Settings(base, "test-key", "stub-model", 0.7, 64, 15000, 4, 2));
		await(ai, "s-warmup", "hello", 15000);

		// 超时设 3000 ms：桩对 "slow" 睡 4000 ms，正好越过这条线。
		// 不用 1200/1500 那种紧贴的余量：并行跑多个冒烟任务时 CPU 一忙就会误判超时（实测过）
		ai.settingsProvider(() -> new AiService.Settings(base, "test-key", "stub-model", 0.7, 64, 3000, 4, 2));

		boolean ok = true;

		// ---- 段 1：服务层 ----
		String normal = await(ai, "s-normal", "hello", 5000);
		boolean normalOk = "pong:hello".equals(normal);
		ok &= report("正常回复", normalOk, normal);

		String missing = await(ai, "s-500", "boom", 5000);
		boolean httpOk = "".equals(missing);
		ok &= report("非 2xx 返回空串", httpOk, missing);

		// 同一会话先成功后失败：失败必须把上一轮的回复清掉。
		// 否则脚本的「让会话回应 X」失败后，「说 会话的最后回复」会把旧答案当新答案发出去。
		String previous = await(ai, "s-stale", "hello", 5000);
		String stale = await(ai, "s-stale", "boom", 5000);
		boolean staleOk = "pong:hello".equals(previous) && "".equals(stale);
		ok &= report("失败后不留上一轮回复", staleOk, "上次=" + previous + " 本次=" + stale);

		String broken = await(ai, "s-json", "badjson", 5000);
		boolean jsonOk = "".equals(broken);
		ok &= report("坏 JSON 返回空串", jsonOk, broken);

		long slowStart = System.currentTimeMillis();
		String slow = await(ai, "s-slow", "slow", 8000);
		long slowCost = System.currentTimeMillis() - slowStart;
		boolean slowOk = "".equals(slow) && slowCost < 8000;
		ok &= report("超时返回空串（" + slowCost + " ms）", slowOk, slow);

		// 会话互斥：同一个会话同时两条，第二条必须被拒绝（返回 null 而不是排队）
		String first = ai.ask("s-busy", "slow");
		String second = ai.ask("s-busy", "hello");
		boolean busyOk = first != null && second == null;
		awaitId(ai, first, 8000);
		ok &= report("同会话不许并排", busyOk, "first=" + first + " second=" + second);

		// 并发上限必须按配置生效：maxConcurrent=4 时四个不同会话应同时受理，第五个被拒。
		// 这条守住的是「配置写了 4 却仍然只跑 2」那类假配置。
		ai.settingsProvider(() -> new AiService.Settings(base, "test-key", "stub-model", 0.7, 64, 10000, 4, 4));
		String[] parallel = new String[4];
		for (int i = 0; i < parallel.length; i++) {
			parallel[i] = ai.ask("s-par" + i, "slow");
		}
		String overflow = ai.ask("s-par-overflow", "slow");
		boolean admitted = true;
		for (String id : parallel) {
			admitted &= id != null;
		}
		boolean inFlightOk = ai.snapshot().inFlight() == 4;
		boolean concurrencyOk = admitted && overflow == null && inFlightOk;
		ok &= report("并发上限按配置生效", concurrencyOk,
				"受理=" + admitted + " 在途=" + ai.snapshot().inFlight() + " 溢出=" + overflow);
		long parStart = System.currentTimeMillis();
		for (String id : parallel) {
			awaitId(ai, id, 12000);
		}
		long parCost = System.currentTimeMillis() - parStart;
		// 断言「桩侧同时处理过 4 个请求」，而不是比耗时：耗时在高负载机器上会飘，
		// 而同时处理数只取决于线程池大小——这才是「maxConcurrent 真的生效」的直接证据。
		int peak = MAX_HANDLERS.get();
		boolean parallelOk = peak >= 4;
		ok &= report("四个请求真并行", parallelOk, "同时处理峰值 " + peak + "，总耗时 " + parCost + " ms");
		ai.settingsProvider(() -> new AiService.Settings(base, "test-key", "stub-model", 0.7, 64, 3000, 4, 2));

		// 未配置：baseUrl / model 为空时必须立刻失败，不发请求
		ai.settingsProvider(() -> AiService.Settings.DISABLED);
		boolean disabledOk = ai.ask("s-off", "hello") == null;
		ok &= report("未配置直接失败", disabledOk, "");

		// 未配置必须能从快照看出来：HUD 与面板都靠 snapshot，不能只写日志
		AiService.Snapshot offView = ai.snapshot();
		AiService.SessionStatus offSession = find(offView, "s-off");
		boolean offReported = !offView.configured() && offView.attempted()
				&& offSession != null && offSession.phase() == AiService.Phase.FAILED
				&& offSession.error().contains("未配置");
		ok &= report("快照暴露未配置", offReported, offSession == null ? "无会话" : offSession.error());

		ai.settingsProvider(() -> new AiService.Settings(base, "test-key", "stub-model", 0.7, 64, 3000, 4, 2));

		// ---- 状态快照：HUD / 面板的唯一数据源 ----
		// 超时故意放宽到 10 s：默认 3000 ms 下，机器一忙就可能「还没来得及取快照就已经超时」，
		// WAITING 断言会偶发失败（并行跑多个冒烟任务时实测到过一次）
		ai.settingsProvider(() -> new AiService.Settings(base, "test-key", "stub-model", 0.7, 64, 10000, 4, 2));
		String slowId = ai.ask("s-view", "slow");
		AiService.SessionStatus waitingView = find(ai.snapshot(), "s-view");
		boolean waitingOk = slowId != null && waitingView != null
				&& waitingView.phase() == AiService.Phase.WAITING
				&& waitingView.pendingMs() >= 0
				&& "slow".equals(waitingView.prompt());
		ok &= report("快照=等待中", waitingOk,
				waitingView == null ? "无会话" : waitingView.phase() + " " + waitingView.prompt());
		awaitId(ai, slowId, 8000);

		// 桩睡 4000 ms < 10 s 超时，所以这一轮应当成功，且能读到回复与耗时
		AiService.SessionStatus okView = find(ai.snapshot(), "s-view");
		boolean okViewOk = okView != null && okView.phase() == AiService.Phase.OK
				&& "pong:slow".equals(okView.reply())
				&& okView.error().isEmpty()
				&& okView.costMs() >= 1000;
		ok &= report("快照=成功", okViewOk,
				okView == null ? "" : okView.phase() + " " + okView.reply() + " " + okView.costMs() + "ms");

		// 失败路径：短原因（HUD 一行显示的就是它），而不是整条异常链
		await(ai, "s-fail", "boom", 5000);
		AiService.SessionStatus failView = find(ai.snapshot(), "s-fail");
		boolean failViewOk = failView != null && failView.phase() == AiService.Phase.FAILED
				&& "HTTP 500".equals(failView.error());
		ok &= report("失败留下短原因", failViewOk, failView == null ? "" : failView.error());

		// 恢复默认超时：后续用例按 3000 ms 走（超时那份断言依赖它）
		ai.settingsProvider(() -> new AiService.Settings(base, "test-key", "stub-model", 0.7, 64, 3000, 4, 2));

		// 2xx + 空 content：算失败而不是成功（否则 HUD 是「✓ + 空白」）
		String noContent = await(ai, "s-empty", "noContent", 5000);
		AiService.SessionStatus emptyView = find(ai.snapshot(), "s-empty");
		boolean emptyOk = "".equals(noContent) && emptyView != null
				&& emptyView.phase() == AiService.Phase.FAILED
				&& "空回复".equals(emptyView.error());
		ok &= report("空回复按失败处理", emptyOk,
				emptyView == null ? "" : emptyView.phase() + " " + emptyView.error());

		// 「最近的聊天」：注册表里能拿到，且读到的是最近记录的那条。
		// 写入方在 TurboMCClient 的客户端事件里，纯 JVM 测不到；这里守的是注册与读取这一半。
		ChatState.record("hello from chat");
		NativeRegistry.Function lastChat = NativeRegistry.getFunction("query.last_chat");
		boolean chatOk = lastChat != null
				&& "hello from chat".equals(String.valueOf(lastChat.apply(null, new Object[0])));
		ok &= report("最近的聊天可读", chatOk, "");

		// ---- 段 2：端到端 ----
		String json = VmSmokeTest.readFixture(FIXTURE);
		if (json == null) {
			System.out.println("[ai] 找不到夹具 " + FIXTURE);
			System.exit(2);
		}
		RhinoVm vm = RhinoVm.get();
		vm.setBudgetMs(4);
		vm.init();
		vm.loadProject(json);
		vm.greenFlag();

		String result = "";
		int waited = 0;
		for (int i = 0; i < 400; i++) {
			vm.tick(null);
			// HTTP 在真实线程里跑：不睡的话 400 帧几十毫秒就烧完了，请求还没回来
			Thread.sleep(10);
			waited = i;
			result = String.valueOf(vm.getVariable("result"));
			if ("pong:hello".equals(result)) {
				break;
			}
		}
		boolean endToEndOk = "pong:hello".equals(result);
		ok &= report("端到端（第 " + waited + " 帧）", endToEndOk, result);
		boolean mapped = ScriptBridge.missCount() == 0;
		ok &= report("所有 opcode 都已映射", mapped, "未映射 " + ScriptBridge.missCount() + " 次");

		server.stop(0);
		ai.cancelAll();
		System.out.println(ok ? "AI SMOKE PASS" : "AI SMOKE FAIL");
		System.exit(ok ? 0 : 1);
	}

	/** 提交并等到有结论，然后返回会话的最近回复。 */
	private static String await(AiService ai, String session, String text, int maxMs) throws Exception {
		String id = ai.ask(session, text);
		if (id == null) {
			return "";
		}
		awaitId(ai, id, maxMs);
		return ai.reply(session);
	}

	private static void awaitId(AiService ai, String id, int maxMs) throws Exception {
		long deadline = System.currentTimeMillis() + maxMs;
		while (id != null && !ai.isDone(id) && System.currentTimeMillis() < deadline) {
			Thread.sleep(10);
		}
	}

	private static AiService.SessionStatus find(AiService.Snapshot snapshot, String name) {
		for (AiService.SessionStatus status : snapshot.sessions()) {
			if (status.name().equals(name)) {
				return status;
			}
		}
		return null;
	}

	private static boolean report(String name, boolean pass, String detail) {
		System.out.println("[ai] " + (pass ? "✓ " : "✗ ") + name
				+ (detail == null || detail.isEmpty() ? "" : "：" + detail));
		return pass;
	}

	// ---- 本地桩 ----

	private static final AtomicInteger HANDLERS = new AtomicInteger();
	private static final AtomicInteger MAX_HANDLERS = new AtomicInteger();

	private static void handle(HttpExchange exchange) throws IOException {
		int now = HANDLERS.incrementAndGet();
		MAX_HANDLERS.accumulateAndGet(now, Math::max);
		try {
			handleInner(exchange);
		} finally {
			HANDLERS.decrementAndGet();
		}
	}

	private static void handleInner(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		String user = lastUserContent(body);

		if (body.contains("slow")) {
			sleep(4000);
		}
		if (body.contains("boom")) {
			respond(exchange, 500, "{\"error\":{\"message\":\"boom\"}}");
			return;
		}
		if (body.contains("badjson")) {
			respond(exchange, 200, "not json at all");
			return;
		}
		// 2xx 但 content 为空：必须归为失败，不能显示成绿色的成功
		if (body.contains("noContent")) {
			respond(exchange, 200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}]}");
			return;
		}
		JsonObject message = new JsonObject();
		message.addProperty("role", "assistant");
		message.addProperty("content", "pong:" + user);
		JsonObject choice = new JsonObject();
		choice.add("message", message);
		JsonArray choices = new JsonArray();
		choices.add(choice);
		JsonObject root = new JsonObject();
		root.add("choices", choices);
		respond(exchange, 200, root.toString());
	}

	/** 取最后一条 user 消息的 content；解析失败返回 {@link #MARKER}。 */
	private static String lastUserContent(String body) {
		try {
			JsonArray messages = JsonParser.parseString(body).getAsJsonObject()
					.getAsJsonArray("messages");
			for (int i = messages.size() - 1; i >= 0; i--) {
				JsonObject message = messages.get(i).getAsJsonObject();
				if ("user".equals(message.get("role").getAsString())) {
					return message.get("content").getAsString();
				}
			}
		} catch (RuntimeException ignored) {
			// 落到下面的标记值，让断言失败时能看出是解析问题
		}
		return MARKER;
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		try {
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		} catch (IOException ignored) {
			// 超时用例里客户端先断开，写不进去是预期行为
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
