package xyz.turbomc.client.script;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.config.TurboMCConfig;
import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.action.ScriptKeys;
import xyz.turbomc.client.script.block.BlockRegistry;
import xyz.turbomc.client.script.node.BlockNode;
import xyz.turbomc.client.script.sb3.Sb3Importer;
import xyz.turbomc.client.script.store.PackStore;
import xyz.turbomc.client.script.store.ScriptDefinition;
import xyz.turbomc.client.script.store.ScriptPack;
import xyz.turbomc.client.script.vm.RhinoVm;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 脚本引擎门面。
 *
 * <p>脚本的编写与修改由 TurboWarp 完成，游戏内只负责「把 sb3 载入 VM 并运行」。
 * 因此这里没有任何序列化逻辑：启用的包原样交给 scratch-vm，不存在中间格式，
 * 也就不会出现「编辑器写的和实际跑的不一致」这类问题。</p>
 *
 * <p>包级开关是互斥的——同时只运行一个工程。事件模型与 Scratch 一致：
 * {@code whenflagclicked} 随工程启动常驻，其余帽子由宿主事件转成 {@code startHats}。</p>
 */
public final class ScriptEngine {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");
	private static final ScriptEngine INSTANCE = new ScriptEngine();

	/** 当前工程关心的按键名，启动时从 sb3 里收集一次。 */
	private Set<String> cachedTriggerKeys = Set.of();

	private boolean initialized;
	private boolean running;
	private boolean halted;
	private boolean healthy = true;
	private int scriptCount;
	/** 当前运行的包文件名，供面板显示。 */
	private String activeName = "";

	private ScriptEngine() {
	}

	public static ScriptEngine get() {
		return INSTANCE;
	}

	public void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		BlockRegistry.bootstrapOnce();
		NativeRegistry.bootstrapOnce();
		PackStore.get().load();
		LOG.info("[script] 引擎就绪：积木定义 {} 个，脚本包 {} 个",
				BlockRegistry.all().size(), PackStore.get().packs().size());
	}

	public void tick(MinecraftClient client) {
		if (!healthy) {
			return;
		}
		if (client.player == null || client.world == null) {
			halted = false;
			if (running) {
				stop("离开世界");
			}
			return;
		}
		TurboMCConfig config = TurboMCConfig.get();
		// 脚本按下的键需要按 tick 计数松手，与引擎是否运行无关
		ScriptKeys.tick();
		if (!config.masterEnabled || !config.scriptEngineEnabled) {
			halted = false;
			if (running) {
				stop("已关闭");
			}
			return;
		}
		// 执行过「停止全部」后不自动恢复，需玩家重新按 F6 开启
		if (halted) {
			return;
		}
		if (!initialized) {
			init();
		}
		if (!running) {
			start();
		}
		RhinoVm vm = RhinoVm.get();
		vm.setBudgetMs(config.scratchVmBudgetMs);
		vm.tick(client);
	}

	private void start() {
		running = true;

		ScriptPack pack = PackStore.get().activePack();
		if (pack == null) {
			scriptCount = 0;
			activeName = "";
			cachedTriggerKeys = Set.of();
			LOG.info("[script] 没有启用的脚本包，等待导入");
			return;
		}

		String json = PackStore.get().projectJson(pack);
		if (json == null) {
			healthy = false;
			LOG.error("[script] 无法读取工程 {}，脚本已停用", pack.fileName);
			return;
		}

		try {
			RhinoVm vm = RhinoVm.get();
			if (!vm.isReady()) {
				// 首次启动约 0.6–1.2 秒：解析 1.5 MB 的 VM 脚本
				vm.init();
			}
			vm.loadProject(json);
			vm.greenFlag();
			activeName = pack.fileName;
			scriptCount = countScripts(pack);
			cachedTriggerKeys = collectKeys(pack);
			LOG.info("[script] 已启动 {}：{} 个脚本，线程 {} 个，监听按键 {} 个",
					pack.fileName, scriptCount, vm.threadCount(), cachedTriggerKeys.size());
		} catch (RuntimeException e) {
			healthy = false;
			LOG.error("[script] 工程启动失败，脚本已停用", e);
		}
	}

	private static int countScripts(ScriptPack pack) {
		Sb3Importer.Result result = PackStore.get().scripts(pack);
		return result.ok() ? result.scripts().size() : 0;
	}

	/**
	 * 收集工程里「当按下 [键]」用到的键名。
	 *
	 * <p>之前是从编辑器脚本里扫的，编辑层移除后改为扫 sb3 的解析结果——两者用的是同一份
	 * 帽子积木，只是来源从内存树换成了工程文件。</p>
	 */
	private static Set<String> collectKeys(ScriptPack pack) {
		Set<String> keys = new HashSet<>();
		Sb3Importer.Result result = PackStore.get().scripts(pack);
		if (!result.ok()) {
			return keys;
		}
		for (ScriptDefinition definition : result.scripts()) {
			BlockNode hat = definition.hat;
			if (hat != null && "event.key".equals(hat.type)) {
				keys.add(hat.fieldOr("KEY", ""));
			}
		}
		keys.remove("");
		return Set.copyOf(keys);
	}

	private void stop(String reason) {
		running = false;
		scriptCount = 0;
		// 必须在这里松手：脚本按住的键由 KeyBindingMixin 从读取端接管，只要表里还登记着
		// 就恒返回 true。引擎停掉后没人再来松，角色会一直往前走。
		ScriptKeys.releaseEveryKey();
		LOG.info("[script] 已停止：{}", reason);
	}

	/**
	 * 分发一次触发事件。
	 *
	 * <p>{@code matcher} 参数已不参与过滤——过滤发生在 VM 内部（字段匹配）或由宿主
	 * 直接调用 {@link #fireKey}。保留该参数是为了不改动既有调用点。</p>
	 */
	public void fire(String trigger, Predicate<BlockNode> matcher) {
		String opcode = switch (trigger) {
			case "event.chat" -> "turbomc_whenChat";
			case "event.damage" -> "turbomc_whenDamaged";
			case "event.death" -> "turbomc_whenDeath";
			case "event.tick", "event.start" -> "event_whenflagclicked";
			default -> null;
		};
		if (opcode != null) {
			RhinoVm.get().startHat(opcode, (String) null);
		}
	}

	/** 按键触发：按 field 精确匹配，只有对应键的脚本会启动。 */
	public void fireKey(String key) {
		if (key == null || key.isBlank()) {
			return;
		}
		RhinoVm.get().startHat("event_whenkeypressed", "KEY_OPTION", key);
	}

	/**
	 * 聊天触发：按每个「当聊天包含」积木的关键词过滤，只有命中的脚本才启动。
	 *
	 * <p>关键词为空视为无条件匹配；消息为 null 时同样全部触发。</p>
	 */
	public void fireChat(String message) {
		RhinoVm.get().startChatHat(message);
	}

	public Set<String> triggerKeys() {
		return cachedTriggerKeys;
	}

	/** 重启：下次 tick 重新载入当前启用的包。 */
	public void restart() {
		running = false;
		halted = false;
		healthy = true;
		// 换工程等于上一批线程全部消失，它们按下的键同样没人来松
		ScriptKeys.releaseEveryKey();
	}

	public boolean isRunning() {
		return running;
	}

	public int scriptCount() {
		return scriptCount;
	}

	/** 当前运行的包名，没有则为空串。 */
	public String activeName() {
		return activeName;
	}
}
