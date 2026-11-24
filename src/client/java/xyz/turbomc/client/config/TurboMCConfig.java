package xyz.turbomc.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import xyz.turbomc.TurboMC;
import xyz.turbomc.client.script.ai.AiService;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户端配置数据与持久化。
 *
 * <p>该类刻意不引用 Cloth Config / Mod Menu 的任何类型，保证在未安装这两个模组时
 * 依然可以被加载，从而让自动化核心功能在零前置依赖的情况下运行。</p>
 */
public final class TurboMCConfig {
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private static final Path PATH = FabricLoader.getInstance()
			.getConfigDir()
			.resolve("turbomc.json");

	/**
	 * 默认黑名单：聊天、命令、截图、全屏、暂停、进度、社交。
	 *
	 * <p>这几个键一按就会抢走键盘焦点或影响游戏外行为，脚本按下后后续按键会打进输入框。</p>
	 *
	 * <p><b>必须声明在 {@link #instance} 之前</b>：构造器里的
	 * {@code blockedKeys = new ArrayList<>(DEFAULT_BLOCKED_KEYS)} 会读这个常量，
	 * 而静态初始化按源码顺序执行——放到后面时它还是 {@code null}，
	 * {@code new ArrayList<>(null)} 会让模组在客户端启动阶段直接崩掉。</p>
	 */
	public static final List<String> DEFAULT_BLOCKED_KEYS =
			List.of("t", "slash", "f2", "f11", "escape", "l", "p");

	private static TurboMCConfig instance = new TurboMCConfig();

	// ---- 常规 ----
	public boolean masterEnabled = true;

	/** HUD 左上角相对屏幕「可放置区域」的水平百分比，0–100。 */
	public double hudOffsetX;

	/** 垂直百分比，语义同 {@link #hudOffsetX}。 */
	public double hudOffsetY;

	// ---- 脚本引擎 ----
	/**
	 * 脚本引擎总开关，由 F6 翻转。
	 *
	 * <p><b>这是持久值，不是会话值</b>：为 {@code true} 时，进入世界的第一帧
	 * {@code ScriptEngine.tick()} 就会 {@code start()} + {@code greenFlag()}，与玩家是否
	 * 按过 F6 无关。是否在退出世界时复位见 {@link #disableEngineOnExit}。</p>
	 */
	public boolean scriptEngineEnabled = false;

	/**
	 * 退出存档或服务器时是否自动关闭脚本引擎。
	 *
	 * <p>开关本身是持久化的：玩家上一局按过 F6，配置里就留着 {@code true}，
	 * 下一局进世界的第一帧会自动跑起来——这不是玩家按了 F6，是他们没机会选择。
	 * 打开本项后，退出世界时自动复位为 {@code false}，每局都要显式按 F6 才运行。</p>
	 *
	 * <p>用包装类型：旧配置文件没有这个字段时 Gson 会留 {@code null}，原始类型会
	 * 静默变成 {@code false}，等于把「不自动关闭」强加给升级用户。{@code null}
	 * 按「开」处理，与默认值一致。</p>
	 */
	public Boolean disableEngineOnExit = true;

	/**
	 * VM 每 tick 的脚本时间预算（毫秒）。
	 *
	 * <p>scratch-vm 按 {@code 0.75 × 该值} 分配执行时间，默认 4 ms 约等于 20 tps
	 * 的 50 ms 预算的 7%。调大跑得更快但会挤占渲染时间。</p>
	 */
	public int scratchVmBudgetMs = 4;
	/**
	 * HUD 列表监视器最多显示几项。
	 *
	 * <p>整张列表刷在屏幕上会挡住画面，超出部分只显示总数。</p>
	 */
	public int monitorListItems = 5;

	/**
	 * 热重载：{@code imports/} 里的 sb3 一有变化就自动同步，启用中的包再自动重启。
	 *
	 * <p>把「导出 → 放进 imports → 点导入 → 按 F6」压成一次 Ctrl+S。
	 * 默认开；关掉后仍走手动「导入」。{@code null} 按开处理，与默认值一致。</p>
	 */
	public Boolean autoReload = true;

	/** 热重载的检查间隔（毫秒）。越界由 {@link #reloadIntervalMsValue()} 钳到 500–30000。 */
	public int reloadIntervalMs = 2000;

	/**
	 * 平时（不连远程时）窗口失焦也不暂停单人世界。
	 *
	 * <p>原版在窗口失去焦点时会弹出暂停菜单、把单人世界冻住。开发脚本时经常要切到
	 * 浏览器或编辑器，一失焦脚本就停在原地，看起来像「积木没生效」。打开这项后，
	 * 只在「失焦 + 因失焦弹出的暂停界面」这一种组合下自动关掉菜单，玩家自己按 ESC
	 * 的暂停不受影响。</p>
	 *
	 * <p>默认关：它改了原版行为，不该在玩家没要求时生效。远程会话期间无论这项
	 * 开不开，都会临时压住失焦暂停（见 {@code TurboMCClient}）。</p>
	 */
	public Boolean keepRunningWhenUnfocused = false;

	/**
	 * HUD 上同时显示几句「说 / 思考」气泡（1～6）。空值按 3 处理。
	 *
	 * <p>气泡是叠在画面上的，条数越多挡得越多。超出这个数的气泡不逐个画，
	 * 但也不会静默消失——HUD 会补一行「…还有 N 条」（见 {@code ScriptHudElement}），
	 * 否则四五个角色同时说话时，玩家会以为某个角色根本没说话。</p>
	 */
	public Integer hudMaxBubbles = 3;

	/**
	 * 气泡显示条数的有效值。
	 *
	 * <p>名字不带 {@code max} 前缀是为了和字段 {@link #hudMaxBubbles} 区分开：
	 * 这里返回的是钳制后的值，直接给渲染用。</p>
	 */
	public int hudBubbleLimit() {
		Integer value = hudMaxBubbles;
		return value == null ? 3 : Math.max(1, Math.min(6, value));
	}

	/**
	 * 每句「说 / 思考」气泡停留多久（毫秒，1～15 秒）。空值按 5000 处理。
	 *
	 * <p>原先写死 5 秒：需要看住的提示（坐标、状态播报）不够用，短循环脚本又嫌它占画面。
	 * 默认值与旧行为一致，升级后观感不变。</p>
	 */
	public Integer hudBubbleHoldMs = 5000;

	/** 气泡停留时长的有效值：钳到 1～15 秒，避免配成 0 时一句话都看不见。 */
	public int hudBubbleHoldMsValue() {
		Integer value = hudBubbleHoldMs;
		return value == null ? 5000 : Math.max(1000, Math.min(15_000, value));
	}

	/**
	 * 同屏最多保留多少个画框（16～512）。空值按 128 处理。
	 *
	 * <p>128 是「绘制开销可忽略」的经验值（1536 条线段）。脚本遍历大片矿点时容易触顶，
	 * 触顶后最早的框被挤掉；原先上限写死在代码里，画得多的作者只能改源码。</p>
	 */
	public Integer frameMaxBoxes = 128;

	/** 画框上限的有效值：钳到 16～512，避免配成 0 时一个框都画不出。 */
	public int frameLimit() {
		Integer value = frameMaxBoxes;
		return value == null ? 128 : Math.max(16, Math.min(512, value));
	}

	/** 热重载开关的有效值（空值按开）。 */
	public boolean shouldAutoReload() {
		return autoReload == null || autoReload;
	}

	/** 失焦不暂停开关的有效值（空值按关）。 */
	public boolean shouldKeepRunningWhenUnfocused() {
		return Boolean.TRUE.equals(keepRunningWhenUnfocused);
	}

	/** 热重载间隔的有效值：钳到 0.5–30 秒，避免配成 0 时每 tick 扫目录。 */
	public int reloadIntervalMsValue() {
		return Math.max(500, Math.min(30_000, reloadIntervalMs));
	}

	// ---- 远程开发桥（dev 段） ----

	/** 是否监听远程桥。默认关。 */
	public boolean remoteEnabled() {
		return dev != null && Boolean.TRUE.equals(dev.enabled);
	}

	/**
	 * 端口：{@code 0} 表示自动；其余钳到 1–65535。
	 *
	 * <p>越界值不静默改成 0——那会让「我填了 70000」变成"自动分配"，
	 * 用户从 HUD 看到一个陌生端口会以为配置没生效。</p>
	 */
	public int remotePort() {
		Integer value = dev == null ? null : dev.port;
		if (value == null || value == 0) {
			return 0;
		}
		return Math.max(1, Math.min(65535, value));
	}

	/** 实例名；空则返回空串，由调用方回退到 gameDir 目录名。 */
	public String remoteInstanceName() {
		String value = dev == null ? null : dev.instanceName;
		return value == null ? "" : value.trim();
	}

	/**
	 * 生效的 Origin 白名单：配置非空就用配置，否则用内置默认值。
	 *
	 * <p><b>必须返回快照</b>：握手在 WebSocket I/O 线程上遍历这份列表，而模组菜单会在
	 * 主线程增删同一个 {@code ArrayList}——直接返回本体，遍历到一半被改就是
	 * {@code ConcurrentModificationException}（表现为握手静默失败）。{@code DEFAULT_DEV_ORIGINS}
	 * 本身是 {@code List.of}，不可变，不必再拷。</p>
	 */
	public List<String> remoteAllowedOrigins() {
		List<String> configured = dev == null ? null : dev.allowedOrigins;
		if (configured == null || configured.isEmpty()) {
			return DEFAULT_DEV_ORIGINS;
		}
		return List.copyOf(configured);
	}

	/**
	 * 连续发送聊天的间隔（tick，20 tick = 1 秒）。
	 *
	 * <p>超长回复会被切成多段逐条发出，段与段之间就是这个间隔。默认 3 tick ≈ 150 ms
	 * （约 6.7 条/秒）。**不要轻易改成 1**：原版 1.19+ 的 {@code ChatSpamTracker}
	 * 每秒上限是 20 条，每 tick 一条正好贴着红线，服务端会判定刷屏并断开。</p>
	 */
	public Integer chatIntervalTicks = 3;

	/**
	 * 单次「在聊天栏说」最多切成几段。
	 *
	 * <p>每段上限 256 字符 / 768 字节，8 段约等于 2000 字。超出部分不再发送，
	 * 末段以省略号结尾。调大只会让一次输出持续更久，触发反刷屏插件的风险也随之上升。</p>
	 */
	public Integer chatMaxSegments = 8;

	/**
	 * 把「服务端消息」也当作聊天来源。
	 *
	 * <p>代理服与子服常把玩家聊天包成 system 消息下发（日志表现为
	 * {@code [System] [CHAT] 名字: 内容}），聊天栏里与真玩家聊天看不出区别，
	 * 但 Fabric 的 {@code ClientReceiveMessageEvents.CHAT} 不会触发。关掉这一项后，
	 * 这类服务器上「当聊天包含」就完全不工作——与 1.3.0 的行为一致。</p>
	 *
	 * <p>默认开：只订阅玩家聊天会让「单人正常、进服失效」被当成模组坏了。</p>
	 */
	public boolean chatFromServerMessages = true;

	/**
	 * 服务端消息通道上，关键词读不出来的帽子是否也触发。
	 *
	 * <p>「读不出来」指关键词为空，或槽里接的是复合表达式（直接接变量会被求值）。
	 * 默认关：这个通道混着插件广告、加入离开、抢购倒计时，放行等于每条广告都把脚本拉起来。
	 * 需要「任意匹配」语义时再打开。</p>
	 */
	public boolean chatServerAnyMatch = false;
	/**
	 * 死亡后自动重生（不需要写脚本）。
	 *
	 * <p>只发一次重生请求，不做「复活后自动回到死亡点」之类的额外动作——
	 * 那需要传送，属于脚本该做的事。</p>
	 */
	public boolean autoRespawn = false;
	/**
	 * 等待时长是否按「秒」显示（关掉则显示 tick）。
	 *
	 * <p>用包装类型而不是 {@code boolean}：旧配置文件里没有这个字段时 Gson 会留 {@code null}，
	 * 而 {@code showWaitInSeconds()} 把 {@code null} 当作「开」——保持默认显示秒。
	 * 用原始类型则会静默变成 {@code false}，老用户升级后显示方式莫名其妙地变了。</p>
	 */
	public Boolean waitInSeconds = true;

	/** 「走向」执行时是否把路线画到世界里。 */
	public boolean showPath = true;

	/** 是否在 HUD 显示寻路状态（目标、剩余距离、节点进度、规划耗时）。 */
	public boolean showPathHud = true;

	/**
	 * 「走向」的精度与搜索参数。
	 *
	 * <p>字段全用包装类型：缺字段与「配成 0」必须能区分。读取走下面的系列 getter
	 * （缺字段回退默认值并钳制）。</p>
	 */
	public Pathing pathing = new Pathing();

	/**
	 * 脚本禁止按下的键。为空列表表示不拦截任何键（不建议）。
	 */
	public List<String> blockedKeys = new ArrayList<>(DEFAULT_BLOCKED_KEYS);

	/**
	 * 脚本允许按下的键。**空表示不限制**（除黑名单外全部放行）。
	 */
	public List<String> allowedKeys;

	/**
	 * 大模型对话配置。段内字段用包装类型，读取走 {@link #aiSettings()} 等 getter。
	 */
	public Ai ai = new Ai();

	/**
	 * 配置段 {@code dev}：远程开发桥（见 {@code script/remote}）。
	 *
	 * <p>默认关闭。开发环境与正式实例都不监听，普通玩家完全不受影响。</p>
	 */
	public Dev dev = new Dev();

	/**
	 * 配置段 {@code ai}。
	 *
	 * <p>字段全用包装类型：缺失与 0 必须能区分——{@code temperature = 0} 是合法值
	 * （完全确定性输出），用原始类型会把「没配过」和「配成 0」混成一件事。</p>
	 */
	public static final class Ai {
		/** 不含 {@code /chat/completions}；填全了也认。 */
		public String baseUrl = "";
		/** 明文存放，本地 Ollama 之类可以留空。 */
		public String apiKey = "";
		public String model = "";
		public Double temperature = 0.7;
		public Integer maxTokens = 300;
		public Integer timeoutMs = 30000;
		public Integer historyTurns = 8;
		public Integer maxConcurrent = 2;

		// ---- HUD 展示（只影响界面，不参与请求） ----

		/** 成功 / 失败结论在 HUD 上停留几秒。等待中的状态不受影响，一直显示到有结论。 */
		public Integer hudHoldSeconds = 5;
		/** HUD 上同时显示几个会话；多会话时按会话名排序取前几个。 */
		public Integer hudMaxRows = 2;
		/** HUD 上回复预览的字数上限；完整内容在运行面板里看。 */
		public Integer hudReplyChars = 24;
	}

	/**
	 * 配置段 {@code dev}。
	 *
	 * <p>字段用包装类型：缺字段时能分辨「没配过」与「配成默认值」，并在 getter 里回退。</p>
	 */
	public static final class Dev {
		/** 是否监听远程桥。默认关：正式实例不该开一个本机端口。 */
		public Boolean enabled = false;
		/** 端口；{@code 0} 表示从 35671 起顺序找空闲，非 0 表示固定端口（被占则报错不监听）。 */
		public Integer port = 0;
		/** 实例名，多开窗口时用于区分；空表示取 gameDir 的目录名。 */
		public String instanceName = "";
		/** Origin 白名单；空表示用 {@link TurboMCConfig#DEFAULT_DEV_ORIGINS}。 */
		public List<String> allowedOrigins = new ArrayList<>();
	}

	/**
	 * 远程桥默认放行的 Origin。
	 *
	 * <p>只放两类：<b>TurboWarp 官方站与桌面版</b>（扩展本来就跑在那里），
	 * <b>本机开发服务器</b>（{@code http://localhost:*} / {@code http://127.0.0.1:*}）。
	 * 不写任何个人站点——这是要发布出去的模组，别人的域名不该出现在默认值里；
	 * 自建站由使用者自己在模组菜单的「允许的来源」里加（列表里已经把这几条列好了，直接改）。</p>
	 *
	 * <p><b>刻意不含 {@code "null"}</b>：那是 {@code file://} 页面（也就是「从网上下载一个
	 * HTML、双击打开」）的 Origin，是唯一能被「顺手打开一个文件」就接管游戏的入口。
	 * 真要从本地文件打开，在列表里手动加一行 {@code null}。</p>
	 */
	public static final List<String> DEFAULT_DEV_ORIGINS = List.of(
			"https://turbowarp.org",
			"http://localhost:*",
			"http://127.0.0.1:*",
			"tw-editor://.",
			"tw-editor://./"
	);

	private TurboMCConfig() {
	}

	public static TurboMCConfig get() {
		return instance;
	}

	/**
	 * 等待时长是否按秒显示；配置里缺该字段时按「是」处理。
	 */
	public boolean showWaitInSeconds() {
		return waitInSeconds == null || waitInSeconds;
	}

	/**
	 * 退出世界时是否自动关闭脚本引擎；配置里缺该字段时按「是」处理。
	 */
	public boolean shouldDisableEngineOnExit() {
		return disableEngineOnExit == null || disableEngineOnExit;
	}

	/** HUD 水平位置百分比（0–100）。 */
	public double hudPercentX() {
		return clamp(hudOffsetX, 0.0, 100.0);
	}

	/** HUD 垂直位置百分比（0–100）。 */
	public double hudPercentY() {
		return clamp(hudOffsetY, 0.0, 100.0);
	}


	/**
	 * 归一化后的黑名单集合。
	 */
	public Set<String> blockedKeySet() {
		return canonicalSet(blockedKeys);
	}

	/**
	 * 归一化后的白名单集合；空集表示不限制。
	 */
	public Set<String> allowedKeySet() {
		return canonicalSet(allowedKeys);
	}

	private static Set<String> canonicalSet(List<String> raw) {
		if (raw == null || raw.isEmpty()) {
			return Set.of();
		}
		Set<String> result = new HashSet<>();
		for (String item : raw) {
			String canonical = KeyNames.canonical(item);
			if (!canonical.isEmpty()) {
				result.add(canonical);
			}
		}
		return result;
	}

	/** maxTokens 的默认值与有效范围。输入框与运行时共用这一份，避免「显示 99999、实际 8192」（DEF-05）。 */
	public static final int DEFAULT_MAX_TOKENS = 300;
	public static final int MIN_MAX_TOKENS = 1;
	public static final int MAX_MAX_TOKENS = 8192;

	/**
	 * 把 maxTokens 钳到有效范围。
	 *
	 * <p>输入框保存与运行时归一化都必须走这里：只在运行时钳制，界面上就会留下一个
	 * 永远不会生效的数字，玩家无从知道真实上限（DEF-05）。</p>
	 */
	public static int clampMaxTokens(int value) {
		return Math.max(MIN_MAX_TOKENS, Math.min(MAX_MAX_TOKENS, value));
	}

	/**
	 * 归一化后的大模型配置。
	 *
	 * <p>段内字段仍用包装类型：缺失与「配成 0」必须能区分。</p>
	 */
	public AiService.Settings aiSettings() {
		Ai section = ai;
		return new AiService.Settings(
				trim(section.baseUrl),
				trim(section.apiKey),
				trim(section.model),
				section.temperature == null ? 0.7 : Math.max(0.0, Math.min(2.0, section.temperature)),
				section.maxTokens == null ? DEFAULT_MAX_TOKENS : clampMaxTokens(section.maxTokens),
				section.timeoutMs == null ? 30000 : Math.max(1000, Math.min(300000, section.timeoutMs)),
				section.historyTurns == null ? 8 : Math.max(0, Math.min(50, section.historyTurns)),
				section.maxConcurrent == null ? 2 : Math.max(1, Math.min(16, section.maxConcurrent)));
	}

	/** HUD 结论停留时长（毫秒）。 */
	public int aiHudHoldMs() {
		Integer value = ai.hudHoldSeconds;
		return (value == null ? 5 : Math.max(1, Math.min(20, value))) * 1000;
	}

	/** HUD 上同时显示几个会话。 */
	public int aiHudMaxRows() {
		Integer value = ai.hudMaxRows;
		return value == null ? 2 : Math.max(1, Math.min(5, value));
	}

	/** HUD 上回复预览的字数上限。 */
	public int aiHudReplyChars() {
		Integer value = ai.hudReplyChars;
		return value == null ? 24 : Math.max(8, Math.min(64, value));
	}

	/**
	 * 配置段 {@code pathing}：「走向」的精度、搜索强度与放弃策略。
	 *
	 * <p>字段全用包装类型，缺字段与「配成 0」必须能区分——{@code accuracy = 0}
	 * 是合法值（要求终点落在目标格本身），{@code arriveHorizontal} 极小值虽然没意义
	 * 但也不该被当成「没配过」。默认值即改造前写死在
	 * {@code MoveTarget}/{@code PathFollower}/{@code PathPlanner} 里的常量。</p>
	 */
	public static final class Pathing {
		/**
		 * 水平到达半径（格）。
		 *
		 * <p>玩家与路径终点格中心的水平距离小于它即判到达。调大提前停步，
		 * 调小容易出现「差一点点永远判不到」。默认 0.6 比节点推进阈值宽，
		 * 是为了避免两个判定打架。</p>
		 */
		public Double arriveHorizontal = 0.6;

		/**
		 * 垂直到达容差（格）。
		 *
		 * <p>0.6 容得下半砖的 0.5，容不下整整一格；给到 1.0 会把「站在台阶下方
		 * 同一格」判成已到达。</p>
		 */
		public Double arriveVertical = 0.6;

		/**
		 * 节点推进阈值（格）：走到离当前节点多近就切下一个节点。
		 *
		 * <p><b>上限 0.6 是硬约束</b>：参考实现用 1.0 会提前切角、贴着拐角内壁走。
		 * 而它又与 {@link #standTurnDeg} 存在几何耦合——边走边转的转弯半径
		 * R = 速度 ÷ 角速度 ≈ 4.317 ÷ 4.19 ≈ 1.03 格，目标落在 1 格附近且需要大角度时
		 * 角色会绕着目标转圈，永远进不了这个阈值。所以这两个值不要各调各的。</p>
		 */
		public Double nodeReach = 0.5;

		/**
		 * A\* 到达容差（曼哈顿距离，格）。
		 *
		 * <p><b>不是节点粒度，也不减少搜索量</b>：只决定 A\* 在哪里停。
		 * 1 表示终点必须落在目标格六个紧邻面之一；0 表示必须正好是目标格；
		 * 2 会让终点停在离目标两格处，与「走向精确坐标」的语义冲突。</p>
		 */
		public Integer accuracy = 1;

		/**
		 * 站定转向阈值（度）：朝向偏差大于它就先站定转身，否则边走边转。
		 *
		 * <p>站定把速度降为 0，转弯半径随之为 0，绕圈在几何上就不成立。
		 * 调得过大会缩小「站定」的适用范围，把 {@link #nodeReach} 那段的绕圈区间放回来。</p>
		 */
		public Double standTurnDeg = 30.0;

		/** A\* 搜索强度档位，映射到每格节点预算。 */
		public PathEffort effort = PathEffort.NORMAL;

		/**
		 * 最远目标距离（格）。
		 *
		 * <p>会话超时按这个距离缩放（见 {@code PathFollower.maxTicksFor}），改大不会
		 * 让远距离目标被判超时早退。但超过约 67 格起会被覆盖半径上限削成半截路，
		 * 转为「走一段算一段」的分段接近，实际耗时与卡顿都会上升。</p>
		 */
		public Integer maxDistance = 128;
	}

	/** 水平到达半径（格），钳制到 0.30–1.20。 */
	public double pathArriveHorizontal() {
		Double value = pathing.arriveHorizontal;
		return value == null ? 0.6 : clamp(value, 0.30, 1.20);
	}

	/** 垂直到达容差（格），钳制到 0.50–0.90。 */
	public double pathArriveVertical() {
		Double value = pathing.arriveVertical;
		return value == null ? 0.6 : clamp(value, 0.50, 0.90);
	}

	/**
	 * 节点推进阈值（格），钳制到 0.35–0.60。
	 *
	 * <p>上限 0.60 是刻意的：再大就会提前切角，贴着拐角内壁走。</p>
	 */
	public double pathNodeReach() {
		Double value = pathing.nodeReach;
		return value == null ? 0.5 : clamp(value, 0.35, 0.60);
	}

	/** A\* 到达容差（格），钳制到 0–2。 */
	public int pathAccuracy() {
		Integer value = pathing.accuracy;
		return value == null ? 1 : Math.max(0, Math.min(2, value));
	}

	/** 站定转向阈值（度），钳制到 20–60。 */
	public double pathStandTurnDeg() {
		Double value = pathing.standTurnDeg;
		return value == null ? 30.0 : clamp(value, 20.0, 60.0);
	}

	/** 搜索强度档位；缺字段按 {@link PathEffort#NORMAL}。 */
	public PathEffort pathEffort() {
		PathEffort value = pathing.effort;
		return value == null ? PathEffort.NORMAL : value;
	}

	/** 每格节点预算，由 {@link #pathEffort()} 映射。 */
	public int pathBudgetPerBlock() {
		return pathEffort().budgetPerBlock();
	}

	/** 最远目标距离（格），钳制到 32–192。 */
	public int pathMaxDistance() {
		Integer value = pathing.maxDistance;
		return value == null ? 128 : Math.max(32, Math.min(192, value));
	}

	private static double clamp(double value, double min, double max) {
		return value < min ? min : Math.min(value, max);
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	public static void load() {
		if (!Files.exists(PATH)) {
			TurboMC.LOGGER.info("未找到配置文件，写入默认配置: {}", PATH);
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
			TurboMCConfig loaded = GSON.fromJson(reader, TurboMCConfig.class);
			if (loaded != null) {
				loaded.normalizeSections();
				instance = loaded;
			}
			TurboMC.LOGGER.info("配置已加载: {}", PATH);
		} catch (Exception e) {
			TurboMC.LOGGER.error("配置读取失败，保持默认值: {}", PATH, e);
		}
	}

	/**
	 * 补齐缺失的段。
	 *
	 * <p>不是版本兼容，而是 Gson 的固有行为：它用 Unsafe 建实例、不执行字段初始化器，
	 * 所以 json 里缺 {@code ai} / {@code pathing} / {@code blockedKeys} 时这几个字段是
	 * {@code null}。集中在加载后补一次，比让 20 多个 getter 各写一遍 null 判断更省。</p>
	 *
	 * <p>段内字段仍可能为 null（缺字段与「配成 0」要能区分），由各自的 getter 兜底。</p>
	 */
	private void normalizeSections() {
		if (ai == null) {
			ai = new Ai();
		}
		if (pathing == null) {
			pathing = new Pathing();
		}
		if (blockedKeys == null) {
			blockedKeys = new ArrayList<>(DEFAULT_BLOCKED_KEYS);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			TurboMC.LOGGER.error("配置写入失败: {}", PATH, e);
		}
	}
}
