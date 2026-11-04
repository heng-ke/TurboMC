package xyz.hegke.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import xyz.hegke.HkScript;

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
public final class HkScriptConfig {
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private static final Path PATH = FabricLoader.getInstance()
			.getConfigDir()
			.resolve("hk-script.json");

	private static HkScriptConfig instance = new HkScriptConfig();

	// ---- 常规 ----
	public boolean masterEnabled = true;
	public HudCorner hudCorner = HudCorner.TOP_LEFT;

	// ---- 自动化示例项 ----
	public boolean autoToolEnabled = false;
	public int autoToolSearchRadius = 3;

	// ---- 脚本引擎 ----
	public boolean scriptEngineEnabled = false;
	/**
	 * 循环节流：开启后每个循环轮次消耗一个 tick，与 Scratch 的「每帧一轮」一致；
	 * 关闭时在一个 tick 内尽量多跑（默认，适合自动化脚本）。
	 */
	public boolean scratchLoopThrottle = false;
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

	private HkScriptConfig() {
	}

	public static HkScriptConfig get() {
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

	public static void load() {
		if (!Files.exists(PATH)) {
			HkScript.LOGGER.info("未找到配置文件，写入默认配置: {}", PATH);
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
			HkScriptConfig loaded = GSON.fromJson(reader, HkScriptConfig.class);
			if (loaded != null) {
				instance = loaded;
			}
			HkScript.LOGGER.info("配置已加载: {}", PATH);
		} catch (Exception e) {
			HkScript.LOGGER.error("配置读取失败，保持默认值: {}", PATH, e);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			HkScript.LOGGER.error("配置写入失败: {}", PATH, e);
		}
	}
}
