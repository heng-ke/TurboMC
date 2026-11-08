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
	public HudCorner hudCorner = HudCorner.TOP_LEFT;

	// ---- 脚本引擎 ----
	public boolean scriptEngineEnabled = false;
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

	/** 配置界面用：需要写入时确保对象存在。 */
	public Ai aiOrCreate() {
		if (ai == null) {
			ai = new Ai();
		}
		return ai;
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
