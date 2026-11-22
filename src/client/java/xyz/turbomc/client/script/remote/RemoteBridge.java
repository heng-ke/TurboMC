package xyz.turbomc.client.script.remote;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.config.TurboMCConfig;
import xyz.turbomc.client.script.ScriptEngine;
import xyz.turbomc.client.script.action.Snapshots;
import xyz.turbomc.client.script.sb3.Sb3Mapping;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 远程开发桥的 WebSocket 服务端，也是协议的唯一入口。
 *
 * <p>职责边界：端口绑定、Origin 校验、握手、心跳、单控制者；把三类消息分发给协作对象
 * ——动作交给 {@link RemoteExecutor}（FIFO + 每 tick 预算），状态读交给
 * {@link SnapshotPublisher}（每 tick 一帧）。本类不直接碰 VM，也不做动作的业务语义。</p>
 *
 * <p><b>线程模型</b>：所有回调都在 Java-WebSocket 的 I/O 线程上。因此这里只处理
 * 无状态消息（hello / ping / 未知类型）；任何要碰客户端状态的动作，一律投递回主线程，
 * 与项目其它跨线程边界（如聊天回调）保持同一约定。</p>
 *
 * <p><b>默认不监听</b>：只有 {@code dev.enabled} 为真时才 {@link #start()}。
 * 开发环境与正式实例都默认关闭，玩家不受影响。</p>
 */
public final class RemoteBridge {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/remote");
	private static final Gson GSON = new Gson();
	private static final RemoteBridge INSTANCE = new RemoteBridge();
	/** 启动失败后的重试间隔。取 5 秒：够躲开「端口刚被释放」这类瞬时冲突，又不会让人等太久。 */
	private static final long RETRY_MS = 5_000L;

	private Server server;
	private int port;
	private volatile WebSocket controller;
	private String lastRejectedOrigin = "";
	/**
	 * 启动失败后的冷却截止时刻（{@link System#currentTimeMillis()}）。
	 *
	 * <p>{@code TurboMCClient} 每 tick 都会问一次「该起没起」，而 {@link #bind} 失败时要
	 * 阻塞等 latch（最长 2 秒）。没有冷却的话，端口被占的情况下每 tick 都白等一次，
	 * 主线程直接卡死——症状是「一开远程就掉帧到 0」。</p>
	 */
	private long retryAfter;
	/** 上一次启动失败的原因，供 HUD 显示。成功或停止后清空。 */
	private volatile String lastError = "";
	private final SnapshotPublisher snapshotPublisher = new SnapshotPublisher();
	private final RemoteExecutor executor = new RemoteExecutor();

	private RemoteBridge() {
	}

	public static RemoteBridge get() {
		return INSTANCE;
	}

	/** 是否已在监听。 */
	public boolean running() {
		return server != null;
	}

	/** 实际监听的端口；未监听时为 0。HUD 用它显示「远程调试 127.0.0.1:端口」。 */
	public int port() {
		return port;
	}

	/** 最近一次启动失败的原因；正常监听时为空串。 */
	public String lastError() {
		return lastError;
	}

	/**
	 * 按配置启动监听。
	 *
	 * <p>自动分配时从 {@link RemoteProtocol#BASE_PORT} 起逐个尝试；固定端口被占则失败并写日志，
	 * 不静默改成自动——用户填了具体端口就是要求那个端口。</p>
	 *
	 * @return 是否成功监听
	 */
	public boolean start() {
		if (running()) {
			return true;
		}
		long now = System.currentTimeMillis();
		if (now < retryAfter) {
			return false;
		}
		TurboMCConfig config = TurboMCConfig.get();
		int wanted = config.remotePort();
		List<String> origins = config.remoteAllowedOrigins();

		if (wanted > 0) {
			if (bind(wanted, origins)) {
				return started(wanted, "固定端口");
			}
			return failed("端口 " + wanted + " 被占用或无法绑定");
		}

		for (int i = 0; i < RemoteProtocol.MAX_PORT_TRIES; i++) {
			int candidate = RemoteProtocol.BASE_PORT + i;
			if (bind(candidate, origins)) {
				return started(candidate, "自动分配");
			}
		}
		return failed(RemoteProtocol.BASE_PORT + " 起的 " + RemoteProtocol.MAX_PORT_TRIES + " 个端口都不可用");
	}

	private boolean started(int boundPort, String how) {
		port = boundPort;
		retryAfter = 0L;
		lastError = "";
		LOG.info("[remote] 监听 127.0.0.1:{}（{}）", boundPort, how);
		return true;
	}

	/** 启动失败：记原因并进入冷却，避免每 tick 重试把主线程卡在绑定等待上。 */
	private boolean failed(String reason) {
		lastError = reason;
		retryAfter = System.currentTimeMillis() + RETRY_MS;
		LOG.error("[remote] 远程桥未启动：{}（{} 秒后重试）", reason, RETRY_MS / 1000);
		return false;
	}

	/** 停止监听并断开当前控制者。幂等。 */
	public void stop() {
		Server current = server;
		server = null;
		port = 0;
		controller = null;
		// 关掉再开（或改了端口）应当立刻重试，不继承上一次失败的冷却
		retryAfter = 0L;
		lastError = "";
		if (current == null) {
			return;
		}
		try {
			current.stop(1000);
		} catch (Exception e) {
			LOG.warn("[remote] 停止服务端时出错: {}", e.toString());
		}
		LOG.info("[remote] 已停止监听");
	}

	/**
	 * 绑定一个端口并等待结果。
	 *
	 * <p>Java-WebSocket 的 {@code start()} 是异步的：绑定失败走 {@code onError} 而不是抛异常。
	 * 所以这里用 latch 等一小会儿，否则「端口被占」会被当成启动成功，
	 * 后续所有连接都石沉大海。</p>
	 */
	private boolean bind(int candidate, List<String> origins) {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicBoolean ok = new AtomicBoolean(false);
		Server instance = new Server(new InetSocketAddress("127.0.0.1", candidate), origins, latch, ok);
		try {
			instance.start();
			if (!latch.await(2, TimeUnit.SECONDS) || !ok.get()) {
				instance.stop(0);
				return false;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (RuntimeException e) {
			LOG.warn("[remote] 端口 {} 绑定异常: {}", candidate, e.toString());
			return false;
		}
		server = instance;
		return true;
	}

	/** 当前是否已有控制者。 */
	public boolean hasController() {
		return controller != null && controller.isOpen();
	}

	/**
	 * 每 tick 调用（**必须在主线程**）：有控制者时推一帧快照。
	 *
	 * <p>序列化放在主线程是刻意的：采集必须读客户端状态，若先投递到 I/O 线程再读，
	 * 会与 tick 并发。而 {@code send()} 本身是异步入队的，主线程调用不会阻塞网络。</p>
	 */
	public void tick(MinecraftClient client) {
		WebSocket socket = controller;
		if (socket == null || !socket.isOpen()) {
			return;
		}
		// 先执行动作、再采快照：本 tick 的动作效果要能出现在同一帧快照里，
		// 否则浏览器侧会看到「动作已返回、坐标还没变」，读与写错开一帧
		executor.tick(client, this::reply);
		try {
			JsonObject snapshot = snapshotPublisher.capture(client);
			socket.send(GSON.toJson(snapshot));
		} catch (RuntimeException e) {
			// 快照异常不得影响游戏：记一次日志，下一 tick 继续
			LOG.warn("[remote] 快照推送失败: {}", e.toString());
		}
	}

	/**
	 * 把游戏事件推给浏览器（§7.5）。
	 *
	 * <p>由 {@code ScriptEngine.fire*} 在各触发点调用——**同一处**同时喂给游戏内帽子与
	 * 远程会话，语义不会分叉。没有控制者时直接丢弃，不排队：事件是「此刻发生」，
	 * 重连后补发一批过期事件只会让脚本乱跑。</p>
	 */
	public void publishEvent(String name, JsonObject data) {
		WebSocket socket = controller;
		if (socket == null || !socket.isOpen()) {
			return;
		}
		JsonObject message = new JsonObject();
		message.addProperty("t", RemoteProtocol.T_EVENT);
		message.addProperty("name", name);
		message.add("data", data == null ? new JsonObject() : data);
		try {
			socket.send(GSON.toJson(message));
		} catch (RuntimeException e) {
			LOG.debug("[remote] 事件推送失败：{}", e.toString());
		}
	}

	/**
	 * 结束当前会话但保持监听。
	 *
	 * <p>用在退出世界时：脚本已经停了，会话没有继续的意义；但监听要留着，
	 * 因为玩家可能马上又进世界，扩展侧的重连会自己接上。</p>
	 */
	public void endSession() {
		WebSocket socket = controller;
		controller = null;
		// 会话没了，内 VM 的让位也要撤：否则引擎永远停在 REMOTE，玩家按 F6 也没反应
		onMainThread(() -> ScriptEngine.get().endRemoteSession());
		if (socket == null) {
			return;
		}
		try {
			if (socket.isOpen()) {
				socket.send("{\"t\":\"" + RemoteProtocol.T_STOPPED + "\"}");
				socket.close(1000, "session ended");
			}
		} catch (RuntimeException e) {
			LOG.warn("[remote] 结束会话失败: {}", e.toString());
		}
		LOG.info("[remote] 会话已结束，继续监听 127.0.0.1:{}", port);
	}

	/**
	 * 每分钟的心跳检查由 Java-WebSocket 内置的 connection-lost 机制承担：
	 * 服务端发 WS ping 帧，浏览器自动回 pong 帧。浏览器无法主动发 ping 帧，
	 * 所以不走「客户端定时 ping」那条路。快照本身也在每 tick 出数据，断线一目了然。
	 */
	private void onText(String text) {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(text);
		} catch (RuntimeException e) {
			LOG.warn("[remote] 收到非 JSON 文本，已忽略");
			return;
		}
		if (!parsed.isJsonObject()) {
			return;
		}
		JsonObject message = parsed.getAsJsonObject();
		String type = optString(message, "t");

		switch (type) {
			case RemoteProtocol.T_HELLO -> handleHello();
			case RemoteProtocol.T_PING -> reply("{\"t\":\"" + RemoteProtocol.T_PONG + "\"}");
			case RemoteProtocol.T_STOP -> {
				// 浏览器点了红停止：线程在那边清了，游戏侧的动作（按住的键、寻路、待发聊天）
				// 只有这里能收。先回包再收手，扩展侧才不会等
				reply("{\"t\":\"" + RemoteProtocol.T_STOPPED + "\"}");
				onMainThread(() -> ScriptEngine.get().abortForRemote("浏览器停止全部"));
			}
			case RemoteProtocol.T_CALL -> {
				// 入队后立刻返回，真正执行在主线程的 tick 边界；结果走异步回包
				JsonElement argvElement = message.get("argv");
				JsonArray argv = argvElement != null && argvElement.isJsonArray()
						? argvElement.getAsJsonArray()
						: new JsonArray();
				executor.offer(optString(message, "id"), optString(message, "op"), argv);
			}
			case RemoteProtocol.T_POLL -> executor.offerPoll(optString(message, "id"), optString(message, "key"));
			case RemoteProtocol.T_CANCEL -> {
				// 取消是浏览器侧不再等：轮询由它发起，它不再发就自然停了。
				// 这里只回确认，避免扩展侧一直挂着等回包。
				String id = optString(message, "id");
				JsonObject ret = new JsonObject();
				ret.addProperty("t", RemoteProtocol.T_RET);
				ret.addProperty("id", id);
				ret.addProperty("ok", true);
				ret.addProperty("done", true);
				reply(GSON.toJson(ret));
			}
			default -> LOG.debug("[remote] 未知消息类型: {}", type);
		}
	}

	private void handleHello() {
		JsonObject welcome = new JsonObject();
		welcome.addProperty("t", RemoteProtocol.T_WELCOME);
		welcome.addProperty("v", RemoteProtocol.VERSION);
		welcome.addProperty("build", modVersion());
		welcome.addProperty("instance", instanceName());
		welcome.addProperty("world", currentWorld());
		welcome.addProperty("port", port);
		welcome.addProperty("pid", ProcessHandle.current().pid());
		welcome.addProperty("tickRate", 20);
		welcome.addProperty("snapshotHz", RemoteProtocol.SNAPSHOT_HZ);
		welcome.addProperty("maxPerTick", RemoteProtocol.MAX_CALLS_PER_TICK);
		welcome.addProperty("queue", RemoteProtocol.QUEUE_CAPACITY);
		welcome.add("opcodes", opcodes());
		welcome.add("hats", hats());
		welcome.add("snapshotKeys", snapshotKeys());
		reply(GSON.toJson(welcome));
		LOG.info("[remote] 握手完成");
		// 会话建立即让位：本方法跑在 WebSocket I/O 线程上，收尾动作必须回主线程
		onMainThread(() -> ScriptEngine.get()
				.suspendForRemote(instanceName() + " · 端口 " + port));
	}

	/** 把手上的活丢回客户端主线程；已经在线程上就直接执行。 */
	private static void onMainThread(Runnable task) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return;
		}
		if (client.isOnThread()) {
			task.run();
			return;
		}
		client.execute(task);
	}

	/** 版本 / opcode 自检用的清单：与扩展产物比对，不一致时两侧同时提示。 */	private static JsonArray opcodes() {
		JsonArray array = new JsonArray();
		for (String opcode : Sb3Mapping.extensionOpcodes()) {
			array.add(opcode);
		}
		return array;
	}

	/**
	 * 事件到帽子的映射表（§7.5）。
	 *
	 * <p>放在服务端而不是扩展里：这张表是唯一会随需求漂移的知识，下发之后客户端
	 * 只需实现三种通用匹配器，改语义不必重新发布扩展产物。</p>
	 */
	private static JsonArray hats() {
		JsonArray array = new JsonArray();
		array.add(hat("damage", "turbomc_whenDamaged", null, null, false));
		array.add(hat("death", "turbomc_whenDeath", null, null, false));
		array.add(hat("screen_open", "turbomc_whenScreenOpen", "field:SCREEN", null, true));
		array.add(hat("key", "event_whenkeypressed", "field:KEY_OPTION", null, false));
		array.add(hat("chat", "turbomc_whenChat", "input:MATCH", "contains", false));
		return array;
	}

	/**
	 * 各快照的键清单，随握手下发。
	 *
	 * <p>「快照字段」是同步 reporter，客户端必须能立刻答出来；把清单放进握手，
	 * 就不用扩展里再抄一份、也不会与 `Snapshots` 的常量漂移。</p>
	 */
	private static JsonObject snapshotKeys() {
		JsonObject keys = new JsonObject();
		keys.add("player", stringArray(Snapshots.PLAYER_KEYS));
		keys.add("entity", stringArray(Snapshots.ENTITY_KEYS));
		keys.add("item", stringArray(Snapshots.ITEM_KEYS));
		keys.add("screen", stringArray(Snapshots.SCREEN_KEYS));
		keys.add("block", stringArray(Snapshots.BLOCK_KEYS));
		keys.add("block_entity", stringArray(Snapshots.BLOCK_ENTITY_KEYS));
		keys.add("world", stringArray(Snapshots.WORLD_KEYS));
		keys.add("players", stringArray(Snapshots.PLAYER_ENTRY_KEYS));
		keys.add("scan", stringArray(Snapshots.SCAN_KEYS));
		return keys;
	}

	private static JsonArray stringArray(java.util.List<String> values) {
		JsonArray array = new JsonArray();
		for (String value : values) {
			array.add(value);
		}
		return array;
	}

	private static JsonObject hat(String event, String opcode, String match, String kind, boolean alsoAny) {		JsonObject entry = new JsonObject();
		entry.addProperty("ev", event);
		entry.addProperty("op", opcode);
		if (match != null) {
			entry.addProperty("match", match);
		}
		if (kind != null) {
			entry.addProperty("contains", true);
		}
		if (alsoAny) {
			entry.addProperty("alsoAny", true);
		}
		return entry;
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer("turbomc")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
	}

	private String instanceName() {
		String configured = TurboMCConfig.get().remoteInstanceName();
		if (!configured.isEmpty()) {
			return configured;
		}
		// gameDir 的目录名：多开时通常是不同实例目录，足够区分
		return FabricLoader.getInstance().getGameDir().getFileName() == null
				? "minecraft"
				: FabricLoader.getInstance().getGameDir().getFileName().toString();
	}

	private static String currentWorld() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) {
			return "";
		}
		return client.world.getRegistryKey().getValue().toString();
	}

	private void reply(String text) {
		WebSocket socket = controller;
		if (socket != null && socket.isOpen()) {
			try {
				socket.send(text);
			} catch (RuntimeException e) {
				LOG.warn("[remote] 发送失败: {}", e.toString());
			}
		}
	}

	private static String optString(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? "" : element.getAsString();
	}

	/** 单连接的服务端；每次换端口都会新建一个实例。 */
	private final class Server extends WebSocketServer {
		private final List<String> origins;
		private final CountDownLatch startLatch;
		private final AtomicBoolean startOk;

		private Server(InetSocketAddress address, List<String> origins,
					   CountDownLatch startLatch, AtomicBoolean startOk) {
			super(address);
			this.origins = origins;
			this.startLatch = startLatch;
			this.startOk = startOk;
			// 浏览器无法主动发 WS ping 帧，由服务端探活；3 秒收不到 pong 即断开
			setConnectionLostTimeout((int) (RemoteProtocol.HEARTBEAT_TIMEOUT_MS / 1000));
		}

		@Override
		public void onStart() {
			startOk.set(true);
			startLatch.countDown();
		}

		@Override
		public void onError(WebSocket conn, Exception ex) {
			// 启动阶段的错误要唤醒等待者，否则 start() 会一直等满超时
			startLatch.countDown();
			LOG.warn("[remote] 服务端异常: {}", ex.toString());
		}

		@Override
		public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(
				WebSocket conn, Draft draft, ClientHandshake request) throws InvalidDataException {
			String origin = request.getFieldValue("origin");
			if (!RemoteProtocol.originAllowed(origin, origins)) {
				lastRejectedOrigin = origin == null ? "null" : origin;
				LOG.warn("[remote] 拒绝握手：Origin 不在白名单（{}）", lastRejectedOrigin);
				throw new InvalidDataException(1008, "origin not allowed");
			}
			return super.onWebsocketHandshakeReceivedAsServer(conn, draft, request);
		}

		@Override
		public void onOpen(WebSocket conn, ClientHandshake handshake) {
			WebSocket previous = controller;
			controller = conn;
			if (previous != null && previous != conn && previous.isOpen()) {
				// 单控制者：旧连接先收手再断开，避免两个标签页来回抢占
				try {
					previous.send("{\"t\":\"" + RemoteProtocol.T_EVICTED + "\"}");
					previous.close(1000, "evicted");
				} catch (RuntimeException e) {
					LOG.warn("[remote] 踢掉旧连接失败: {}", e.toString());
				}
				LOG.info("[remote] 新连接接管，旧连接已断开");
			}
			LOG.info("[remote] 已连接：{}", conn.getRemoteSocketAddress());
		}

		@Override
		public void onClose(WebSocket conn, int code, String reason, boolean remote) {
			if (controller == conn) {
				controller = null;
				// 断线同样要让位撤销：否则 HUD 一直写「远程调试中」，而浏览器已经走了
				onMainThread(() -> ScriptEngine.get().endRemoteSession());
			}
			LOG.info("[remote] 连接关闭（code={}, reason={}）", code, reason);
		}

		@Override
		public void onMessage(WebSocket conn, String message) {
			onText(message);
		}
	}
}
