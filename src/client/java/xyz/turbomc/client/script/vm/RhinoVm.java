package xyz.turbomc.client.script.vm;

import net.minecraft.client.MinecraftClient;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.script.action.WorldQueries;
import xyz.turbomc.client.script.runtime.ScriptContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Rhino 宿主：承载 scratch-vm，把每个客户端 tick 变成一次 VM 步进。
 *
 * <p>三个刻意的选择：</p>
 * <ol>
 *   <li><b>优化级别固定为 -1（解释模式）</b>：Rhino 的指令观察器只在解释模式下生效，
 *       而这是中断失控脚本的唯一手段。实测 opt 9 与 opt -1 的吞吐几乎一致，代价可忽略。</li>
 *   <li><b>语言版本必须为 200（ES6）</b>：否则 Map / Set 不存在，VM 实例化直接失败。</li>
 *   <li><b>类访问白名单</b>：只允许脚本看到 {@code ScriptBridge}，避免 JS 触达 Runtime 之类的类。</li>
 * </ol>
 */
public final class RhinoVm {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/vm");
	private static final RhinoVm INSTANCE = new RhinoVm();

	private static final int LANGUAGE_VERSION_ES6 = 200;
	private static final int DEFAULT_BUDGET_MS = 4;
	private static final int PROMISE_ROUNDS = 200_000;

	private static final String RESOURCE_PREFIX = "/assets/turbomc/vm/";
	/** 加载顺序不可调换：shim 提供 Promise 与定时器，全局对象必须先于扩展，bootstrap 最后覆盖方法体。 */
	private static final String[] SCRIPTS = {
			"shim-globals.js",
			"sync-promise.js",
			"vm.es5.js",
			"scratch-globals.js",
			"turbomc-extension.es5.js",
			"vm-bootstrap.js"
	};

	private final VmContextFactory factory = new VmContextFactory();
	private Scriptable scope;
	private boolean ready;
	private long budgetNanos = DEFAULT_BUDGET_MS * 1_000_000L;
	/**
	 * 冷启动帧豁免计数。
	 *
	 * <p>头几帧要跑 dispatch 队列与首次 JIT，耗时会明显高于稳态；用同一预算判定会误杀。</p>
	 */
	private int warmupFrames = 3;
	/**
	 * 观察器阈值的容错倍数。
	 *
	 * <p>VM 自身按 0.75×预算分配时间片，但宿主侧还有上下文切换、ScriptContext 构造，
	 * 一次 minor GC 也能轻松吃掉 10 ms。取 4 倍（默认预算下 16 ms）只为兜住真正
	 * 失控的死循环，正常抖动不应触发。</p>
	 */
	private static final long DEADLINE_SLACK = 4L;
	/** 超时日志的最小间隔，避免持续抖动时刷屏。 */
	private static final long TIMEOUT_LOG_INTERVAL = 5_000_000_000L;
	private long lastTimeoutLogNanos;

	private RhinoVm() {
	}

	public static RhinoVm get() {
		return INSTANCE;
	}

	public boolean isReady() {
		return ready;
	}

	public void setBudgetMs(int ms) {
		budgetNanos = Math.max(1, ms) * 1_000_000L;
		if (ready) {
			Context cx = factory.enterContext();
			try {
				call(cx, "__tmcSetBudget", new Object[]{budgetNanos / 1_000_000d});
			} finally {
				Context.exit();
			}
		}
	}

	/**
	 * 加载全部脚本并实例化 VM。首次调用约需 1–2 秒，应在进入世界后触发，不要放在模组初始化里。
	 */
	public synchronized void init() {
		if (ready) {
			return;
		}
		long start = System.nanoTime();
		Context cx = factory.enterContext();
		try {
			scope = cx.initStandardObjects();
			installPrint(cx, scope);
			for (String script : SCRIPTS) {
				cx.evaluateString(scope, read(script), script, 1, null);
			}
			// 实例化 VM 与登记扩展（等价于阶段 0 在 Node 里验证过的内部注册路径）
			cx.evaluateString(scope,
					"var __tmcVmInstance = new ScratchVM();"
							+ "__tmcSetVm(__tmcVmInstance);"
							+ "__tmcRegisterExtension();",
					"vm-init", 1, null);
			// 必须显式设定步进间隔：currentStepTime 为 null 时 stepThreads 的
			// WORK_TIME 计算为 0，整帧静默空转且不报错（阶段 0 踩过的坑）。
			call(cx, "__tmcSetBudget", new Object[]{budgetNanos / 1_000_000d});
			ready = true;
		} catch (RuntimeException e) {
			LOG.error("[vm] 初始化失败", e);
			throw e;
		} finally {
			Context.exit();
		}
		LOG.info("[vm] 初始化完成，耗时 {} ms，积木 {} 个",
				(System.nanoTime() - start) / 1_000_000L, blockCount());
	}

	/** VM 认识的积木数量，用于日志与冒烟测试断言。 */
	public int blockCount() {
		return toInt(callQuietly("__tmcBlockCount", new Object[0]), 0);
	}

	public int threadCount() {
		return toInt(callQuietly("__tmcThreadCount", new Object[0]), 0);
	}

	/** 读取 Stage 上的全局变量，供宿主/测试观测脚本状态。 */
	public Object getVariable(String name) {
		return callQuietly("__tmcGetVar", new Object[]{name});
	}

	/** 全部变量快照，格式 {@code 名字=值} 用分号分隔。 */
	public String variableDump() {
		Object value = callQuietly("__tmcDumpVars", new Object[0]);
		return value == null ? "" : value.toString();
	}

	/**
	 * 所有角色当前的气泡（「说 / 思考」积木的结果），JSON 数组；VM 未就绪返回空串。
	 */
	public String speechJson() {
		Object value = callQuietly("__tmcSpeech", new Object[0]);
		return value == null ? "" : value.toString();
	}

	/**
	 * 载入工程（project.json 字符串）。JS 侧的 Promise 由同步泵推进，因此这里是阻塞的。
	 */
	public void loadProject(String projectJson) {
		requireReady();
		Context cx = factory.enterContext();
		try {
			Object promise = call(cx, "__tmcLoadProject", new Object[]{projectJson});
			call(cx, "__waitPromise", new Object[]{promise, PROMISE_ROUNDS});
		} finally {
			Context.exit();
		}
	}

	public void greenFlag() {
		requireReady();
		Context cx = factory.enterContext();
		try {
			call(cx, "__tmcGreenFlag", new Object[0]);
		} finally {
			Context.exit();
		}
	}

	/**
	 * 驱动一帧。
	 *
	 * <p>单帧超时由指令观察器抛出，此时<b>只跳过该帧</b>，不停任何线程——停全部会让
	 * 用户看到「脚本跑着跑着不动了」，且界面上没有任何报错。</p>
	 */
	public void tick(MinecraftClient client) {
		if (!ready) {
			return;
		}
		ScriptContext context = new ScriptContext(client);
		// 「等待方块被挖破」靠这个探针判定。不注入的话内核会走默认实现（直接放弃），
		// 积木会变成立刻通过——看起来没报错，实际一秒都没等。
		context.setBreakProbe(WorldQueries::probeBreak);
		ScriptBridge.setContext(context);
		if (warmupFrames > 0) {
			warmupFrames--;
			factory.disarmDeadline();
		} else {
			factory.armDeadline(System.nanoTime() + budgetNanos * DEADLINE_SLACK);
		}
		Context cx = factory.enterContext();
		try {
			call(cx, "__tmcStep", new Object[0]);
		} catch (EvaluatorException e) {
			// 单帧执行超时：真机上多由 GC 或宿主同步调用（发聊天 / 执行命令）造成抖动。
			// 只跳过本帧，绝不能 stopAll——那会把整个工程的脚本全部停掉，
			// 用户看到的现象是「脚本跑着跑着不动了」，且界面上没有任何提示。
			long now = System.nanoTime();
			if (now - lastTimeoutLogNanos > TIMEOUT_LOG_INTERVAL) {
				lastTimeoutLogNanos = now;
				LOG.warn("[vm] 单帧执行超出预算（{} ms × {}），已跳过该帧并继续运行",
						budgetNanos / 1_000_000L, DEADLINE_SLACK);
			}
		} catch (RuntimeException e) {
			LOG.error("[vm] 执行异常，已停止全部脚本", e);
			stopAll(cx);
		} finally {
			ScriptBridge.clearContext();
			factory.disarmDeadline();
			Context.exit();
		}
	}

	/** 触发帽子积木，返回新起的线程数。 */
	public int startHat(String opcode, String match) {
		if (!ready) {
			return 0;
		}
		Context cx = factory.enterContext();
		try {
			return threadCountOf(call(cx, "__tmcStartHat", new Object[]{opcode, match}));
		} finally {
			Context.exit();
		}
	}

	/**
	 * 触发带匹配字段的帽子积木（如「当聊天包含 [MATCH]」）。
	 *
	 * @param fieldName 匹配字段名；为空表示不附带匹配条件
	 */
	public int startHat(String opcode, String fieldName, String value) {
		if (!ready) {
			return 0;
		}
		Context cx = factory.enterContext();
		try {
			return threadCountOf(call(cx, "__tmcStartHatField", new Object[]{opcode, fieldName, value}));
		} finally {
			Context.exit();
		}
	}

	/**
	 * 触发「当聊天包含 [MATCH]」。
	 *
	 * <p>不走 {@code startHats}：它的字段匹配只认 field，而扩展把关键词声明成 input，
	 * 传 matchFields 会在 {@code hatFields[..].value} 处抛错。改为自行遍历顶层块、
	 * 读入关键词、命中后才 pushThread。</p>
	 *
	 * @param message 聊天原文；关键词为空表示无条件触发
	 */
	public int startChatHat(String message) {
		if (!ready) {
			return 0;
		}
		Context cx = factory.enterContext();
		try {
			return toInt(call(cx, "__tmcStartHatFiltered",
					new Object[]{"turbomc_whenChat", "MATCH", message}), 0);
		} finally {
			Context.exit();
		}
	}

	/** startHats 返回线程数组，取其长度。 */
	private static int threadCountOf(Object result) {
		if (result instanceof Scriptable scriptable) {
			Object length = ScriptableObject.getProperty(scriptable, "length");
			if (length instanceof Number number) {
				return number.intValue();
			}
		}
		return 0;
	}

	// ---- 内部 ----

	private void stopAll(Context cx) {
		try {
			Object vm = ScriptableObject.getProperty(scope, "__tmcVmInstance");
			if (vm instanceof Scriptable scriptable) {
				Object runtime = ScriptableObject.getProperty(scriptable, "runtime");
				if (runtime instanceof Scriptable runtimeScriptable) {
					Object stopAll = ScriptableObject.getProperty(runtimeScriptable, "stopAll");
					if (stopAll instanceof Function function) {
						function.call(cx, scope, runtimeScriptable, new Object[0]);
					}
				}
			}
		} catch (RuntimeException e) {
			LOG.warn("[vm] 停止全部脚本时出错: {}", e.getMessage());
		}
	}

	private void requireReady() {
		if (!ready) {
			throw new IllegalStateException("VM 尚未初始化");
		}
	}

	private Object call(Context cx, String functionName, Object[] args) {
		Object target = ScriptableObject.getProperty(scope, functionName);
		if (!(target instanceof Function function)) {
			throw new IllegalStateException("缺少 JS 入口函数: " + functionName);
		}
		return function.call(cx, scope, scope, args);
	}

	/** 静默调用：初始化中途属性可能还不存在，失败返回 null。 */
	private Object callQuietly(String functionName, Object[] args) {
		if (!ready) {
			return null;
		}
		Context cx = factory.enterContext();
		try {
			return call(cx, functionName, args);
		} catch (RuntimeException e) {
			return null;
		} finally {
			Context.exit();
		}
	}

	private static int toInt(Object value, int fallback) {
		return value instanceof Number number ? number.intValue() : fallback;
	}

	/** Rhino 的 shell 环境才有 print，嵌入时必须自己装一个。 */
	private static void installPrint(Context cx, Scriptable scope) {
		BaseFunction print = new BaseFunction() {
			@Override
			public Object call(Context context, Scriptable s, Scriptable thisObj, Object[] args) {
				if (args.length > 0) {
					LOG.info("[vm/js] {}", Context.toString(args[0]));
				}
				return Undefined.instance;
			}
		};
		scope.put("print", scope, print);
	}

	private static String read(String name) {
		String path = RESOURCE_PREFIX + name;
		try (InputStream in = RhinoVm.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("缺少 VM 资源: " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("读取 VM 资源失败: " + path, e);
		}
	}

	/**
	 * 定制工厂：语言版本、解释模式、类白名单，以及按时间预算的指令级中断。
	 */
	private static final class VmContextFactory extends ContextFactory {
		private volatile long deadline;

		@Override
		protected Context makeContext() {
			Context cx = super.makeContext();
			cx.setLanguageVersion(LANGUAGE_VERSION_ES6);
			// 观察器只在解释模式下生效；opt 9 与 -1 的实测吞吐几乎一致，故选可中断的那个。
			cx.setOptimizationLevel(-1);
			cx.setInstructionObserverThreshold(10_000);
			cx.setClassShutter(new BridgeOnlyShutter());
			return cx;
		}

		void armDeadline(long value) {
			deadline = value;
		}

		void disarmDeadline() {
			deadline = 0L;
		}

		@Override
		protected void observeInstructionCount(Context cx, int instructionCount) {
			long limit = deadline;
			if (limit != 0L && System.nanoTime() > limit) {
				deadline = 0L;
				throw new EvaluatorException("脚本执行超出单帧时间预算，已中断");
			}
		}
	}

	/** 只允许脚本看到桥本身，其余 Java 类一律不可见。 */
	private static final class BridgeOnlyShutter implements ClassShutter {
		private static final String ALLOWED = "xyz.turbomc.client.script.vm.";
		/**
		 * 方法签名里用到的装箱类型必须放行，否则 JS 传参时 Rhino 包装
		 * {@code java.lang.String} 就会被拦下（报 "Access to Java class is prohibited"）。
		 * 只列这几个，不放开整个 java.lang——否则 Runtime 之类也会暴露。
		 */
		private static final Set<String> BOXED = Set.of(
				"java.lang.String", "java.lang.Double", "java.lang.Boolean",
				"java.lang.Integer", "java.lang.Long", "java.lang.Object");

		@Override
		public boolean visibleToScripts(String fullClassName) {
			return fullClassName.startsWith(ALLOWED) || BOXED.contains(fullClassName);
		}
	}
}
