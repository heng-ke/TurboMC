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

	private static TurboMCConfig instance = new TurboMCConfig();

	// ---- 常规 ----
	public boolean masterEnabled = true;

	/**
	 * 旧版 HUD 停靠角落。仅用于读取旧配置时推导初始百分比，不再由界面写入。
	 *
	 * <p>保留字段而不是删掉，是为了让老 json 里的 {@code hudCorner} 继续有意义：
	 * 它决定 {@link #hudOffsetX} / {@link #hudOffsetY} 未设置时的回退位置。
	 * 新的唯一定位真源是下面两个百分比。</p>
	 */
	public HudCorner hudCorner = HudCorner.TOP_LEFT;

	/**
	 * HUD 左上角相对屏幕「可放置区域」的水平百分比，0–100；{@code null} = 未设置。
	 *
	 * <p>用包装类型而不是 {@code double}：缺字段与「显式设成 0」必须能区分，
	 * 否则旧配置里的「右上」会被一个默认 0 直接抹成左上。</p>
	 */
	public Double hudOffsetX;

	/** 垂直百分比，语义同 {@link #hudOffsetX}。 */
	public Double hudOffsetY;

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
	 * <p>与 {@link Ai} 同样的问题：旧配置文件里没有这一段，Gson 用 Unsafe 建实例、
	 * 不执行字段初始化器，所以字段可能整体为 {@code null}。读取一律走
	 * {@link #pathingSection()} 系列 getter（缺段/缺字段回退默认值并钳制），
	 * 写入走 {@link #pathingOrCreate()}。</p>
	 */
	public Pathing pathing = new Pathing();

	/**
	 * 脚本禁止按下的键。为空列表表示不拦截任何键（不建议）。
	 *
	 * <p>{@code null} 表示「从未配置过」，读取时回退到 {@link #DEFAULT_BLOCKED_KEYS}——
	 * 旧配置文件没有这个字段时，Gson 会留 null，用空列表兜底会让所有键突然都能按了。</p>
	 */
	public List<String> blockedKeys;

	/**
	 * 脚本允许按下的键。**空表示不限制**（除黑名单外全部放行）。
	 */
	public List<String> allowedKeys;

	/**
	 * 大模型对话配置。
	 *
	 * <p><b>从旧配置文件加载时这一段是 {@code null}</b>：Gson 用 Unsafe 建实例，字段初始化
	 * 不执行。所以读取一律走 {@link #aiOrCreate()} 或 {@link #aiSettings()}，不要直接读字段。</p>
	 */
	public Ai ai = new Ai();

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

	/**
	 * HUD 水平位置百分比（0–100）。
	 *
	 * <p>未配置时按 {@link #hudCorner} 推导，于是旧配置的四角落点原样落到
	 * 0 / 100 上，行为与改造前一致；枚举本身也可能是 {@code null}（手改过的 json），
	 * 一并按左上处理，不抛空指针。</p>
	 */
	public double hudPercentX() {
		if (hudOffsetX != null) {
			return clamp(hudOffsetX, 0.0, 100.0);
		}
		return hudCorner == HudCorner.TOP_RIGHT || hudCorner == HudCorner.BOTTOM_RIGHT ? 100.0 : 0.0;
	}

	/** HUD 垂直位置百分比（0–100），推导规则同 {@link #hudPercentX()}。 */
	public double hudPercentY() {
		if (hudOffsetY != null) {
			return clamp(hudOffsetY, 0.0, 100.0);
		}
		return hudCorner == HudCorner.BOTTOM_LEFT || hudCorner == HudCorner.BOTTOM_RIGHT ? 100.0 : 0.0;
	}


	/**
	 * 默认黑名单：聊天、命令、截图、全屏、暂停、进度、社交。
	 *
	 * <p>这几个键一按就会抢走键盘焦点或影响游戏外行为，脚本按下后后续按键会打进输入框。</p>
	 */
	public static final List<String> DEFAULT_BLOCKED_KEYS =
			List.of("t", "slash", "f2", "f11", "escape", "l", "p");

	public List<String> blockedKeysOrDefault() {
		return blockedKeys == null ? DEFAULT_BLOCKED_KEYS : blockedKeys;
	}

	/**
	 * 归一化后的黑名单集合。
	 */
	public Set<String> blockedKeySet() {
		return canonicalSet(blockedKeysOrDefault());
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

	/**
	 * 归一化后的大模型配置。
	 *
	 * <p>缺失字段一律回退到安全默认值：旧版本的配置文件里没有 {@code ai} 段，
	 * 直接读字段会得到一串 {@code null}。</p>
	 */
	public AiService.Settings aiSettings() {
		Ai section = ai == null ? new Ai() : ai;
		return new AiService.Settings(
				trim(section.baseUrl),
				trim(section.apiKey),
				trim(section.model),
				section.temperature == null ? 0.7 : Math.max(0.0, Math.min(2.0, section.temperature)),
				section.maxTokens == null ? 300 : Math.max(1, Math.min(8192, section.maxTokens)),
				section.timeoutMs == null ? 30000 : Math.max(1000, Math.min(300000, section.timeoutMs)),
				section.historyTurns == null ? 8 : Math.max(0, Math.min(50, section.historyTurns)),
				section.maxConcurrent == null ? 2 : Math.max(1, Math.min(16, section.maxConcurrent)));
	}

	/** HUD 结论停留时长（毫秒）。缺字段按 5 秒——旧配置文件没有这一段。 */
	public int aiHudHoldMs() {
		Ai section = ai;
		Integer value = section == null ? null : section.hudHoldSeconds;
		return (value == null ? 5 : Math.max(1, Math.min(20, value))) * 1000;
	}

	/** HUD 上同时显示几个会话。 */
	public int aiHudMaxRows() {
		Ai section = ai;
		Integer value = section == null ? null : section.hudMaxRows;
		return value == null ? 2 : Math.max(1, Math.min(5, value));
	}

	/** HUD 上回复预览的字数上限。 */
	public int aiHudReplyChars() {
		Ai section = ai;
		Integer value = section == null ? null : section.hudReplyChars;
		return value == null ? 24 : Math.max(8, Math.min(64, value));
	}

	/** 配置界面用：需要写入时确保对象存在。 */
	public Ai aiOrCreate() {
		if (ai == null) {
			ai = new Ai();
		}
		return ai;
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

	/** 缺段时的只读兜底。绝不写回：写入一律走 {@link #pathingOrCreate()}。 */
	private static final Pathing PATHING_DEFAULTS = new Pathing();

	private Pathing pathingSection() {
		return pathing == null ? PATHING_DEFAULTS : pathing;
	}

	/** 配置界面用：需要写入时确保对象存在。 */
	public Pathing pathingOrCreate() {
		if (pathing == null) {
			pathing = new Pathing();
		}
		return pathing;
	}

	/** 水平到达半径（格），钳制到 0.30–1.20。 */
	public double pathArriveHorizontal() {
		Double value = pathingSection().arriveHorizontal;
		return value == null ? 0.6 : clamp(value, 0.30, 1.20);
	}

	/** 垂直到达容差（格），钳制到 0.50–0.90。 */
	public double pathArriveVertical() {
		Double value = pathingSection().arriveVertical;
		return value == null ? 0.6 : clamp(value, 0.50, 0.90);
	}

	/**
	 * 节点推进阈值（格），钳制到 0.35–0.60。
	 *
	 * <p>上限 0.60 是刻意的：再大就会提前切角，贴着拐角内壁走。</p>
	 */
	public double pathNodeReach() {
		Double value = pathingSection().nodeReach;
		return value == null ? 0.5 : clamp(value, 0.35, 0.60);
	}

	/** A\* 到达容差（格），钳制到 0–2。 */
	public int pathAccuracy() {
		Integer value = pathingSection().accuracy;
		return value == null ? 1 : Math.max(0, Math.min(2, value));
	}

	/** 站定转向阈值（度），钳制到 20–60。 */
	public double pathStandTurnDeg() {
		Double value = pathingSection().standTurnDeg;
		return value == null ? 30.0 : clamp(value, 20.0, 60.0);
	}

	/** 搜索强度档位；缺字段按 {@link PathEffort#NORMAL}。 */
	public PathEffort pathEffort() {
		PathEffort value = pathingSection().effort;
		return value == null ? PathEffort.NORMAL : value;
	}

	/** 每格节点预算，由 {@link #pathEffort()} 映射。 */
	public int pathBudgetPerBlock() {
		return pathEffort().budgetPerBlock();
	}

	/** 最远目标距离（格），钳制到 32–192。 */
	public int pathMaxDistance() {
		Integer value = pathingSection().maxDistance;
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
				instance = loaded;
			}
			TurboMC.LOGGER.info("配置已加载: {}", PATH);
		} catch (Exception e) {
			TurboMC.LOGGER.error("配置读取失败，保持默认值: {}", PATH, e);
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
