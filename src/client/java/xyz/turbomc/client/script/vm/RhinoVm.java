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
import org.mozilla.javascript.WrapFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.script.action.WorldQueries;
import xyz.turbomc.client.script.runtime.ExtensionHealth;
import xyz.turbomc.client.script.runtime.PerfProbe;
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

	/**
	 * 最近一次「执行异常导致停止全部」的摘要。
	 *
	 * <p>VM 不反向依赖 {@code ScriptEngine}（那会形成双向依赖），改为与
	 * {@code takeProjectStops()} 同样的拉取式：由引擎每 tick 取走并显示到界面。</p>
	 */
	private String fatalError;

	private static final int LANGUAGE_VERSION_ES6 = 200;
	private static final int DEFAULT_BUDGET_MS = 4;
	private static final int PROMISE_ROUNDS = 200_000;

	private static final String RESOURCE_PREFIX = "/assets/turbomc/vm/";
	/** 预热用的空工程：只有一个没有积木的舞台，用来走一遍首次反序列化。 */
	private static final String PRELOAD_PROJECT = "preload-project.json";
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
	private int budgetMs = DEFAULT_BUDGET_MS;
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

	/**
	 * 设置单帧预算（毫秒）。
	 *
	 * <p>引擎每 tick 都会调一次，但配置极少变。值没变时直接返回：省掉一次
	 * {@code enterContext} 与 JS 调用，也避免在 Rhino 的观察器窗口里插入额外工作。</p>
	 */
	public void setBudgetMs(int ms) {
		int value = Math.max(1, ms);
		if (value == budgetMs) {
			return;
		}
		budgetMs = value;
		budgetNanos = value * 1_000_000L;
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
	 * 加载全部脚本并实例化 VM，约 0.6–0.7 秒（解析 1.5 MB 的 VM 脚本）。
	 *
	 * <p>正常路径由 {@link #preload()} 在标题界面调用；若等到进世界才首次初始化，
	 * 这一下会直接卡在玩家脸上。也不要放在模组初始化阶段——那时做重活会拖慢启动，
	 * 异常还会连累整个客户端起不来。</p>
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
			// 列表读写两块积木的实现在 vm 包（需要 VM 内部状态），注册点放这里，
			// 保证「action → vm」不会多出一条新依赖边。重复注册是幂等的。
			VmListActions.registerAll();
			// 扩展产物与映射表必须一一对应：对不上时积木会静默失效——扩展旧了是新积木
			// 拖不出来，模组旧了是积木点下去没反应。结论交给 HUD 常驻显示（见 §6.3）
			try {
				String declared = String.valueOf(call(cx, "__tmcExtensionOpcodes", new Object[0]));
				ExtensionHealth.report(ScriptBridge.checkExtensionCoverage(declared));
			} catch (RuntimeException e) {
				ExtensionHealth.report("扩展未上报积木清单，无法自检");
			}
		} catch (RuntimeException e) {
			LOG.error("[vm] 初始化失败", e);
			throw e;
		} finally {
			Context.exit();
		}
		LOG.info("[vm] 初始化完成，耗时 {} ms，积木 {} 个",
				(System.nanoTime() - start) / 1_000_000L, blockCount());
		PerfProbe.end("vm.init", start);
	}

	/** VM 认识的积木数量，用于日志与冒烟测试断言。 */
	public int blockCount() {
		return toInt(callQuietly("__tmcBlockCount", new Object[0]), 0);
	}

	/**
	 * 预热：初始化 VM 并载入一个空工程，把「首次反序列化」的冷路径提前走掉。
	 *
	 * <p><b>为什么要连空工程一起 load</b>：单次实测里 {@code init()} 663 ms、
	 * <b>首次</b> {@code loadProject} 307 ms、第二次只有 8–10 ms。那 307 ms 花在
	 * scratch-vm 的首次反序列化与 JIT 预热上，跟工程规模几乎无关——所以先用空工程
	 * 走一遍，玩家真正进世界时载入自己的工程就落到热路径上。</p>
	 *
	 * <p><b>为什么放在标题界面而不是模组初始化</b>：模组初始化阶段做重活会拖慢启动，
	 * 且抛出异常会连累整个客户端起不来；放在标题界面最多让脚本不可用，
	 * 进世界时 {@code start()} 还会再试一次。</p>
	 *
	 * <p>只应由主线程调用。重复调用是安全的：VM 已就绪时直接返回。</p>
	 */
	public void preload() {
		if (ready) {
			return;
		}
		init();
		loadProject(read(PRELOAD_PROJECT));
		// 空工程的载入可能带出一次 stopAll，计数不能留给「进入世界」的第一次判定
		takeProjectStops();
	}

	/**
	 * 「按下 [键]?」的键盘钩子是否已安装到 VM 的键盘设备上。
	 *
	 * <p>供冒烟测试断言。只看有没有警告日志不足以证明覆写生效——原型的
	 * {@code getKeyIsDown} 一直存在，漏装时积木会静默返回 false。</p>
	 */
	public boolean keyboardHooked() {
		return Boolean.TRUE.equals(callQuietly("__tmcKeyboardHooked", new Object[0]));
	}

	/** 「鼠标按下?」的鼠标钩子是否已安装（同上，供冒烟测试断言）。 */
	public boolean mouseHooked() {
		return Boolean.TRUE.equals(callQuietly("__tmcMouseHooked", new Object[0]));
	}

	/** 「用户名」钩子是否已安装（同上）。 */
	public boolean userHooked() {
		return Boolean.TRUE.equals(callQuietly("__tmcUserHooked", new Object[0]));
	}

	/** 「询问并等待」的 QUESTION 监听是否已装上（同上）。 */
	public boolean askHooked() {
		return Boolean.TRUE.equals(callQuietly("__tmcAskHooked", new Object[0]));
	}

	/** 「显示 / 隐藏 变量」的原生积木钩子是否已装上（同上）。 */
	public boolean monitorHooked() {
		return Boolean.TRUE.equals(callQuietly("__tmcMonitorHooked", new Object[0]));
	}

	/** 读取列表内容的 JSON 字符串，供 HUD 监视器显示；VM 未就绪时返回空数组。 */
	public String getList(String name) {
		Object value = callQuietly("__tmcGetList", new Object[]{name});
		return value == null ? "[]" : value.toString();
	}

	/**
	 * 布尔归一化自检。
	 *
	 * <p>桥返回的 {@code Boolean} 会被 Rhino 包成对象，而 scratch 的
	 * {@code Cast.toBoolean} 对对象一律判真——归一化一旦写错，所有「是/否」积木
	 * 都会恒真，且不会有任何报错。</p>
	 */
	public boolean booleanNormalizeOk() {
		return Boolean.TRUE.equals(callQuietly("__tmcBooleanSelfTest", new Object[0]));
	}

	public int threadCount() {
		return toInt(callQuietly("__tmcThreadCount", new Object[0]), 0);
	}

	/**
	 * 取走自上次调用以来「停止全部」发生的次数。
	 *
	 * <p>scratch-vm 的 {@code runtime.stopAll()} 会清空线程并发出 {@code PROJECT_STOP_ALL}，
	 * 但宿主没有订阅点：脚本执行「停止全部」后，Java 侧的「走向」会话与脚本按下的键
	 * 仍在每 tick 驱动。计数搬到 Java 侧之后，由 {@code ScriptEngine} 决定是否收尾。</p>
	 *
	 * <p>注意 {@code greenFlag()} 内部也会 {@code stopAll}，启动流程里必须调用一次把它清掉，
	 * 否则进入世界的第一个 tick 会被误判成「玩家执行了停止全部」。</p>
	 */
	public int takeProjectStops() {
		return toInt(callQuietly("__tmcTakeProjectStops", new Object[0]), 0);
	}

	/**
	 * 取走最近一次执行异常（导致 VM 停止全部）的摘要；没有则为 {@code null}。
	 *
	 * <p>只在 {@link #tick} 的异常分支里写入，读一次即清空——同一个原因不需要在界面上
	 * 反复播报，引擎会把它转成持久的状态行。</p>
	 */
	public String takeFatalError() {
		String value = fatalError;
		fatalError = null;
		return value;
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
	 * 读 Scratch 列表为 JSON 数组文本；列表不存在返回 {@code null}。
	 *
	 * <p>与 {@link #getVariable} 同一层：都是宿主读 VM 内部状态的出口，
	 * 供「JSON」那两块列表积木使用。{@code null} 与 {@code "[]"} 的差别是「找不到」与
	 * 「找到但为空」，积木据此区分空串与空数组。</p>
	 */
	public String readList(String name) {
		Object value = callQuietly("__tmcReadList", new Object[]{name});
		return value == null ? null : value.toString();
	}

	/** 用 JSON 数组覆盖 Scratch 列表；返回是否成功（列表不存在或不是数组时为假）。 */
	public boolean writeList(String name, String json) {
		Object value = callQuietly("__tmcSetList", new Object[]{name, json});
		return Boolean.TRUE.equals(value);
	}

	/**
	 * 所有角色当前的气泡（「说 / 思考」积木的结果），JSON 数组；VM 未就绪返回空串。
	 */
	public String speechJson() {
		Object value = callQuietly("__tmcSpeech", new Object[0]);
		return value == null ? "" : value.toString();
	}

	/**
	 * 载入期（{@code loadProject} / {@code greenFlag}）的时间上限。
	 *
	 * <p>单帧预算只有几毫秒，而载入一次工程要几百毫秒，两者不能共用同一个数。
	 * 这段时间此前<b>完全没有上限</b>：坏工程的 {@code next} 链成环时，VM 在
	 * {@code installTargets} / {@code greenFlag} 里无限循环，堆一路涨到 7.9 GB、
	 * 窗口无响应，只能强杀进程（DEF-04，实测 {@code -Xmx512m} 下 20 秒 OOM）。
	 * 这里给一个宽松但确定的兜底：宁可让这份工程载入失败，也不能把客户端拖死。</p>
	 */
	private static final long LOAD_DEADLINE_MS = 10_000L;

	/**
	 * 载入工程（project.json 字符串）。JS 侧的 Promise 由同步泵推进，因此这里是阻塞的。
	 */
	public void loadProject(String projectJson) {
		requireReady();
		Context cx = factory.enterContext();
		armLoadDeadline();
		try {
			Object promise = call(cx, "__tmcLoadProject", new Object[]{projectJson});
			call(cx, "__waitPromise", new Object[]{promise, PROMISE_ROUNDS});
		} finally {
			factory.disarmDeadline();
			Context.exit();
		}
	}

	public void greenFlag() {
		requireReady();
		Context cx = factory.enterContext();
		armLoadDeadline();
		try {
			call(cx, "__tmcGreenFlag", new Object[0]);
		} finally {
			factory.disarmDeadline();
			Context.exit();
		}
	}

	/** 载入期上锁：进入 VM 之前武装期限，离开时由调用方解除。 */
	private void armLoadDeadline() {
		factory.armDeadline(System.nanoTime() + LOAD_DEADLINE_MS * 1_000_000L);
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
		long perf = PerfProbe.begin();
		try {
			call(cx, "__tmcStep", new Object[0]);
		} catch (EvaluatorException e) {
			// 单帧执行超时：真机上多由 GC 或宿主同步调用（发聊天 / 执行命令）造成抖动。
			// 只跳过本帧，绝不能 stopAll——那会把整个工程的脚本全部停掉，
			// 用户看到的现象是「脚本跑着跑着不动了」，且界面上没有任何提示。
			// 载入期（loadProject / greenFlag）用的是同一个中断，但时限由 LOAD_DEADLINE_MS
			// 单独给出，且在那两个方法里就地抛出，不会落到这个 catch。
			long now = System.nanoTime();
			if (now - lastTimeoutLogNanos > TIMEOUT_LOG_INTERVAL) {
				lastTimeoutLogNanos = now;
				LOG.warn("[vm] 单帧执行超出预算（{} ms × {}），已跳过该帧并继续运行",
						budgetNanos / 1_000_000L, DEADLINE_SLACK);
			}
		} catch (RuntimeException e) {
			// 摘要交给引擎显示：只写日志的话，玩家在游戏里只会看到「脚本已停止」
			fatalError = e.toString();
			LOG.error("[vm] 执行异常，已停止全部脚本", e);
			stopAll(cx);
		} finally {
			// 超时帧同样记一次：它正是「这一帧卡了」的直接证据
			PerfProbe.end("vm.step", perf);
			ScriptBridge.clearContext();
			factory.disarmDeadline();
			Context.exit();
		}
	}

	/**
	 * 扩展积木的参数名自检：占位符数量必须与查看器侧登记的参数数量一致。
	 *
	 * <p><b>要防的是什么</b>：积木方法体转发到 Java 时，实参按<b>位置</b>取
	 * （Java 侧读 {@code args.<名字>}），而位置来自积木显示文本里的 {@code [NAME]}
	 * 占位符。改写文案时若动了占位符的拼写或顺序，实参会静默错位——不报错，
	 * 只是某个积木的参数语义悄悄偏移。</p>
	 *
	 * <p>两边数一遍就能发现：扩展侧的占位符数（JS 侧暴露）与查看器侧登记的
	 * 参数数（{@code BlockRegistry}）必须相等。数量对不上，参数就一定错位。</p>
	 *
	 * @return 不一致时给出说明；一致则返回空串
	 */
	public String argsSelfTest() {
		Object raw = callQuietly("__tmcArgNamesDump", new Object[0]);
		if (raw == null) {
			return "扩展参数清单取不到";
		}
		String dump = String.valueOf(raw);
		if (dump.isEmpty()) {
			return "扩展参数清单取不到";
		}
		xyz.turbomc.client.script.block.BlockRegistry.bootstrapOnce();
		StringBuilder problems = new StringBuilder();
		for (String entry : dump.split(";")) {
			if (entry.isEmpty()) {
				continue;
			}
			int split = entry.indexOf('=');
			if (split < 0) {
				continue;
			}
			String opcode = entry.substring(0, split);
			String names = entry.substring(split + 1);
			int count = names.isEmpty() ? 0 : names.split(",", -1).length;

			// 扩展侧的 opcode 没有前缀，sb3 里是 turbomc_<name>（见 ARCHITECTURE 第 10 条）
			String sb3Opcode = opcode.startsWith("turbomc_") ? opcode : "turbomc_" + opcode;
			String type = xyz.turbomc.client.script.sb3.Sb3Mapping.typeOf(sb3Opcode);
			if (type == null) {
				problems.append(opcode).append("(未映射) ");
				continue;
			}
			xyz.turbomc.client.script.block.BlockType block =
					xyz.turbomc.client.script.block.BlockRegistry.get(type);
			if (block == null) {
				problems.append(opcode).append("(类型未登记) ");
				continue;
			}
			int expected = block.params().size();
			if (count != expected) {
				problems.append(opcode).append("(占位符 ").append(count)
						.append("≠参数 ").append(expected).append(") ");
			}
		}
		return problems.toString();
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
	 * 触发带匹配字段的帽子积木：「当按下 [键]」的 {@code KEY_OPTION}、
	 * 「当打开 [界面]」的 {@code SCREEN}。
	 *
	 * <p>{@code fieldName} 非空时走 JS 侧自建遍历（{@code __tmcStartHatField}），
	 * 不用 {@code runtime.startHats}——实测压缩版 VM 对「带参数的扩展帽子」匹配失败，
	 * 且失败时静默返回 0。详见 ARCHITECTURE §6.1.1。</p>
	 *
	 * <p>聊天帽子不在这里：它要按关键词前缀匹配，走 {@link #startChatHat}。</p>
	 *
	 * @param fieldName 匹配字段名；为空表示不附带匹配条件（「当受伤 / 当死亡」）
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
		return startChatHat(message, true);
	}

	/**
	 * 触发「当聊天包含 [MATCH]」，并指定「关键词读不出来时」是否放行。
	 *
	 * @param message          聊天原文
	 * @param allowAnyKeyword  {@code true} 空关键词/变量/表达式一律放行（玩家聊天的历史行为）；
	 *                         {@code false} 只启动「关键词可确定且命中」的帽子（服务端消息通道）
	 */
	public int startChatHat(String message, boolean allowAnyKeyword) {
		if (!ready) {
			return 0;
		}
		Context cx = factory.enterContext();
		try {
			// 传字符串而不是布尔：Rhino 会把 java.lang.Boolean 包成对象，JS 侧用 === 比较会失真
			return toInt(call(cx, "__tmcStartHatFiltered",
					new Object[]{"turbomc_whenChat", "MATCH", message,
							allowAnyKeyword ? "any" : "keyword"}), 0);
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
			cx.setWrapFactory(new ScalarWrapFactory());
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
				// 文案不写「单帧」：这条中断同时用于逐帧执行与载入期（两者上限不同）
				throw new EvaluatorException("脚本执行超出时间上限，已中断");
			}
		}
	}

	/**
	 * 让 Java 侧的标量以原生 JS 值进入 VM，而不是被包成 {@code NativeJavaObject}。
	 *
	 * <p><b>为什么必须这样</b>：{@link ScriptBridge#call} 的返回类型是 {@code Object}，
	 * Rhino 默认会把 {@code Double} 封装成 {@code NativeJavaObject}。而上游
	 * {@code Cast.toString} 的实现就是 {@code String(e)}——对封装对象取到的是
	 * <b>Java 的 {@code Double.toString}</b>，于是数值积木一旦参与字符串拼接就带上
	 * Java 格式：生命值 20.0 变成 {@code "20.0"}（Scratch 期望 {@code "20"}），
	 * {@code 1e-5} 变成 {@code "1.0E-5"}（期望 {@code "0.00001"}）。
	 * 数值运算本身没错（{@code Number()} 会解包），错的是字符串化。</p>
	 *
	 * <p>覆写后 {@code Double} 直接成为 JS number，{@code String(e)} 走的就是
	 * ECMAScript 的 {@code String(number)}，格式与浏览器里的 Scratch 完全一致。</p>
	 *
	 * <p><b>为什么只放行这三个类型</b>：脚本的��值域本来只有 string / number / boolean
	 * 三种，其余类型（数组、对象）封成 {@code NativeJavaObject} 才是正确行为——
	 * 那样 JS 侧能看见它的方法，语义与浏览器一致。</p>
	 *
	 * <p>与 {@link BridgeOnlyShutter} 互不影响：类可见性由 ClassShutter 决定，
	 * 装箱由 WrapFactory 决定，是两套独立机制。</p>
	 */
	private static final class ScalarWrapFactory extends WrapFactory {
		@Override
		public Object wrap(Context cx, Scriptable scope, Object obj, Class<?> staticType) {
			if (obj instanceof Double value) {
				return value;
			}
			if (obj instanceof Boolean value) {
				return value;
			}
			if (obj instanceof String value) {
				return value;
			}
			return super.wrap(cx, scope, obj, staticType);
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
