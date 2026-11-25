package xyz.turbomc.client.script.remote;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.action.WorldQueries;
import xyz.turbomc.client.script.runtime.ScriptContext;
import xyz.turbomc.client.script.sb3.Sb3Mapping;
import xyz.turbomc.client.script.vm.ScriptBridge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 远程动作的执行器：把浏览器发来的 {@code call} 在 tick 边界落到 {@link NativeRegistry}。
 *
 * <p><b>刻意不走 {@code ScriptBridge.call}</b>：那个入口是为 Rhino 侧设计的，会读写
 * {@code current} / {@code pendingWait} 等静态字段。远程路径直接调
 * {@code NativeRegistry} + {@code ScriptContext}，静态状态天然不冲突——
 * 加上内 VM 已停（见 ARCHITECTURE.md §7.1），同一 tick 只有一条路径在写。</p>
 *
 * <p><b>执行上下文每 tick 自建</b>：内 VM 处于 {@code REMOTE} 时 {@code RhinoVm.tick}
 * 不会创建 context，不能复用。建的时候必须注入 {@code breakProbe}，
 * 否则「等待方块被挖破」会走默认实现立刻放行。</p>
 *
 * <p>线程模型：{@link #offer} 在 I/O 线程调用，{@link #tick} 在主线程调用；
 * 队列用 synchronized 保护，临界区只有入队/取空两步。</p>
 */
public final class RemoteExecutor {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/remote");
	private static final Gson GSON = new Gson();
	/** 单 tick 的执行预算。单个动作是 Java 侧调用，通常远小于 1ms。 */
	private static final long BUDGET_NANOS = 8_000_000L;
	/** 单 tick 最多执行多少个请求；超出的回 rate，不静默丢。 */
	private static final int MAX_PER_TICK = RemoteProtocol.MAX_CALLS_PER_TICK;

	private final ArrayDeque<Pending> queue = new ArrayDeque<>();
	/** 本 tick 的执行上下文；跨请求复用，每 tick 重建。 */
	private ScriptContext context;

	/**
	 * 队列元素。{@code call} 与 {@code poll} 走同一队列是为了保序：
	 * 浏览器侧先发调用、拿到 {@code check} 后再发轮询，两条消息必须按到达顺序处理。
	 */
	private static final class Pending {
		private final String kind;
		private final String id;
		private final String opcode;
		private final JsonArray argv;
		private final String key;

		private Pending(String kind, String id, String opcode, JsonArray argv, String key) {
			this.kind = kind;
			this.id = id;
			this.opcode = opcode;
			this.argv = argv;
			this.key = key;
		}
	}

	/**
	 * 入队一个调用。队列满时丢掉最旧的并记一次日志——
	 * 丢掉最旧而不是拒绝新的，是因为旧的通常是断线前堆积的过期动作。
	 */
	public void offer(String id, String opcode, JsonArray argv) {
		enqueue(new Pending("call", id, opcode, argv == null ? new JsonArray() : argv, ""));
	}

	/** 入队一个条件轮询：{@code key} 与 {@code ScriptBridge.poll} 的取值一致。 */
	public void offerPoll(String id, String key) {
		enqueue(new Pending("poll", id, "", new JsonArray(), key == null ? "" : key));
	}

	private void enqueue(Pending pending) {
		synchronized (queue) {
			if (queue.size() >= RemoteProtocol.QUEUE_CAPACITY) {
				Pending dropped = queue.pollFirst();
				LOG.warn("[remote] 队列已满，丢弃最旧请求 kind={}", dropped == null ? "?" : dropped.kind);
			}
			queue.addLast(pending);
		}
	}

	/** 丢弃全部待执行请求（收手时用）。 */
	public void clear() {
		synchronized (queue) {
			queue.clear();
		}
	}

	/**
	 * 执行本 tick 的队列。
	 *
	 * @param client 客户端实例，调用方保证在主线程
	 * @param sender 回包出口；接收已序列化的 JSON 文本
	 */
	public void tick(MinecraftClient client, Consumer<String> sender) {
		List<Pending> batch;
		synchronized (queue) {
			if (queue.isEmpty()) {
				return;
			}
			batch = new ArrayList<>(queue);
			queue.clear();
		}

		// 每 tick 重建：与 RhinoVm.tick 同一口径。ScriptContext 很轻，跨 tick 复用反而
		// 会把上一帧的等待状态带过来。
		context = new ScriptContext(client);
		// 不注入的话「等待挖破」会立刻通过——看起来没报错，实际一秒都没等
		context.setBreakProbe(WorldQueries::probeBreak);

		long deadline = System.nanoTime() + BUDGET_NANOS;
		int executed = 0;
		for (Pending pending : batch) {
			if (executed >= MAX_PER_TICK || System.nanoTime() > deadline) {
				sender.accept(error(pending.id, RemoteProtocol.E_RATE, "per-tick budget exceeded"));
				continue;
			}
			sender.accept("poll".equals(pending.kind) ? pollResult(pending) : execute(pending));
			executed++;
		}
		if (executed < batch.size()) {
			LOG.debug("[remote] 本 tick 执行 {}/{}，其余回 rate", executed, batch.size());
		}
	}

	/**
	 * 条件轮询：复用 {@code ScriptBridge.poll(key, context)} 的四条分支。
	 *
	 * <p>异常时回 {@code done:true} 而不是抛给浏览器——等待必须能结束，
	 * 否则脚本会永久挂在 {@code PROMISE_WAIT} 上，表现为"卡死但没有任何报错"。</p>
	 */
	private String pollResult(Pending pending) {
		boolean done;
		try {
			done = ScriptBridge.poll(pending.key, context);
		} catch (RuntimeException e) {
			LOG.warn("[remote] 轮询 {} 时出错: {}", pending.key, e.toString());
			done = true;
		}
		JsonObject ret = new JsonObject();
		ret.addProperty("t", RemoteProtocol.T_RET);
		ret.addProperty("id", pending.id);
		ret.addProperty("ok", true);
		ret.addProperty("done", done);
		return GSON.toJson(ret);
	}

	/** 执行单个调用并构造回包。任何异常都只影响这一条，不中断整批。 */
	private String execute(Pending call) {
		String type = Sb3Mapping.typeOf(call.opcode);
		if (type == null) {
			return error(call.id, RemoteProtocol.E_UNMAPPED, "opcode not mapped: " + call.opcode);
		}

		Object[] values = toValues(call.argv);
		Object result = null;
		try {
			NativeRegistry.Action action = NativeRegistry.getAction(type);
			if (action != null) {
				action.run(context, values);
			} else {
				NativeRegistry.Function function = NativeRegistry.getFunction(type);
				if (function == null) {
					return error(call.id, RemoteProtocol.E_NOTFOUND, "no implementation: " + type);
				}
				result = function.apply(context, values);
			}
			// 先取再回包：等待三元组是「本次调用」的结果，取晚一步就会被下一条覆盖
			int wait = context.takeWaitTicks();
			String check = context.takeWaitCheck();
			int limit = context.takeWaitLimit();
			return success(call.id, result, wait, check, limit);
		} catch (RuntimeException e) {
			// 与 ScriptBridge 同一口径：单个动作失败不停脚本
			LOG.warn("[remote] 执行 {} 时出错: {}", call.opcode, e.toString());
			return error(call.id, RemoteProtocol.E_INTERNAL, e.toString());
		} finally {
			// 异常路径下可能没取干净，兜一次；正常路径此时已经是 0 / null
			context.takeWaitTicks();
			context.takeWaitCheck();
			context.takeWaitLimit();
		}
	}

	private String success(String id, Object value, int wait, String check, int limit) {
		JsonObject ret = new JsonObject();
		ret.addProperty("t", RemoteProtocol.T_RET);
		ret.addProperty("id", id);
		ret.addProperty("ok", true);
		if (value == null) {
			ret.add("v", com.google.gson.JsonNull.INSTANCE);
		} else if (value instanceof Boolean b) {
			ret.addProperty("v", b);
		} else if (value instanceof Number n) {
			ret.addProperty("v", n.doubleValue());
		} else {
			ret.addProperty("v", String.valueOf(value));
		}
		ret.addProperty("wait", wait);
		ret.addProperty("check", check == null ? "" : check);
		ret.addProperty("limit", limit);
		return GSON.toJson(ret);
	}

	private static String error(String id, String code, String message) {
		JsonObject ret = new JsonObject();
		ret.addProperty("t", RemoteProtocol.T_RET);
		ret.addProperty("id", id);
		ret.addProperty("ok", false);
		ret.addProperty("code", code);
		ret.addProperty("err", message);
		return GSON.toJson(ret);
	}

	/**
	 * {@code argv} 有序数组 → 动作层的 {@code Object[]}。
	 *
	 * <p>类型收得很窄，与 {@code ScriptBridge.toJava} 一致：布尔、数字、字符串；
	 * {@code null} 按空串处理（Scratch 的值域里没有 null）。</p>
	 */
	private static Object[] toValues(JsonArray argv) {
		Object[] values = new Object[argv.size()];
		for (int i = 0; i < values.length; i++) {
			JsonElement element = argv.get(i);
			if (element == null || element.isJsonNull()) {
				values[i] = "";
				continue;
			}
			if (!element.isJsonPrimitive()) {
				values[i] = "";
				continue;
			}
			JsonPrimitive primitive = element.getAsJsonPrimitive();
			if (primitive.isBoolean()) {
				values[i] = primitive.getAsBoolean();
			} else if (primitive.isNumber()) {
				values[i] = primitive.getAsDouble();
			} else {
				values[i] = primitive.getAsString();
			}
		}
		return values;
	}

	}
