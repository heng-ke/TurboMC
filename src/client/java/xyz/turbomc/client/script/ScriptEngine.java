package xyz.turbomc.client.script;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.config.TurboMCConfig;
import xyz.turbomc.client.script.action.GameActions;
import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.action.ScriptKeys;
import xyz.turbomc.client.script.action.motion.TurnSession;
import xyz.turbomc.client.script.action.path.PathFollower;
import xyz.turbomc.client.script.action.path.PathPlanner;
import xyz.turbomc.client.script.ai.AiService;
import xyz.turbomc.client.script.block.BlockRegistry;
import xyz.turbomc.client.script.node.BlockNode;
import xyz.turbomc.client.script.runtime.ChatGate;
import xyz.turbomc.client.script.runtime.ChatState;
import xyz.turbomc.client.script.runtime.MonitorStore;
import xyz.turbomc.client.script.runtime.PerfProbe;
import xyz.turbomc.client.script.runtime.SelfChatEcho;
import xyz.turbomc.client.script.sb3.Sb3Importer;
import xyz.turbomc.client.script.store.PackStore;
import xyz.turbomc.client.script.store.ScriptDefinition;
import xyz.turbomc.client.script.store.ScriptPack;
import xyz.turbomc.client.script.vm.RhinoVm;
import xyz.turbomc.client.script.vm.ScriptBridge;

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
	/** 预热只尝试一次：失败就交给进世界后的 start() 再 init。 */
	private boolean warmUpTried;
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
			// 工程读不出来时 VM 不再推进：先把会话与按键收干净，否则角色会带着
			// 上一份工程按下的前进键一直往前走；按键到期也要照常推进。
			if (running) {
				stop("工程不可用");
			}
			ScriptKeys.tick();
			return;
		}
		if (client.player == null || client.world == null) {
			halted = false;
			if (running) {
				stop("离开世界");
			}
			// 标题界面/加载界面期间把 VM 备好，进世界就不用再付那 0.6 秒
			tryWarmUp();
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
			// 这个分支直接 return，跟随器不会再被驱动——不在这里收尾，
			// 脚本按下的前进键就永远松不开。
			PathFollower.abort("停止全部");
			TurnSession.abort("停止全部");
			ScriptKeys.releaseEveryKey();
			// 已排队的聊天段落不再发：脚本都停了，队列还在往外冒话很奇怪
			GameActions.clearChatQueue();
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
		// 聊天队列按 tick 推进：超长回复被切成多段，段间要拉开间隔
		GameActions.tickChat();
		// 「停止全部」是 VM 内部事件，Java 侧只有主动查询才看得见。
		// 不查的话线程已经清空，「走向」会话却还会按着前进键再走满 MAX_TICKS。
		if (vm.takeProjectStops() > 0) {
			halted = true;
			PathFollower.abort("停止全部");
			TurnSession.abort("停止全部");
			ScriptKeys.releaseEveryKey();
			MonitorStore.clear();
			LOG.info("[script] 检测到「停止全部」，已收尾，等待玩家按 F6 重新开启");
			return;
		}
		// 放在 vm.tick 之后：会话是在 vm.tick 里启动的，放前面会白等一 tick。
		PathFollower.tick(client);
		TurnSession.tick(client);
	}

	/**
	 * 在标题界面/加载界面把 VM 备好，把「首次反序列化」的冷路径提前走掉。
	 *
	 * <p><b>为什么放在这里</b>：模组初始化阶段做重活会拖慢启动，异常还会连累整个客户端；
	 * 放标题界面最多让脚本暂时不可用，进世界时 {@code start()} 还会再走一次 init。
	 * 引擎关闭时不预热——不为用不上的 VM 付 0.6 秒。</p>
	 *
	 * <p><b>只在没有玩家时调用</b>，所以不会和 {@code start()} 抢同一帧。</p>
	 */
	private void tryWarmUp() {
		if (warmUpTried) {
			return;
		}
		TurboMCConfig config = TurboMCConfig.get();
		if (!config.masterEnabled || !config.scriptEngineEnabled) {
			return;
		}
		warmUpTried = true;
		RhinoVm vm = RhinoVm.get();
		if (vm.isReady()) {
			return;
		}
		long perf = PerfProbe.begin();
		try {
			vm.preload();
			PerfProbe.end("vm.preload", perf);
			LOG.info("[script] VM 预热完成，进世界只需载入工程");
		} catch (RuntimeException e) {
			PerfProbe.end("vm.preload", perf);
			LOG.warn("[script] VM 预热失败，进世界后再试：{}", e.toString());
		}
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
			long perf = PerfProbe.begin();
			if (!vm.isReady()) {
				// 正常路径已在标题界面预热（见 tryWarmUp）；走到这里是预热没跑成，
				// 兜底付一次 0.6–0.7 秒的 VM 脚本解析
				vm.init();
			}
			long load = PerfProbe.begin();
			vm.loadProject(json);
			PerfProbe.end("project.load", load);
			long flag = PerfProbe.begin();
			vm.greenFlag();
			PerfProbe.end("project.flag", flag);
			// greenFlag 内部会 stopAll，这次计数属于「启动流程」而非玩家操作，必须丢掉，
			// 否则进入世界的第一帧就会被判成「停止全部」，脚本一启动就趴下。
			vm.takeProjectStops();
			activeName = pack.fileName;
			scriptCount = countScripts(pack);
			cachedTriggerKeys = collectKeys(pack);
			PerfProbe.end("project.start", perf);
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
		// 键表与包名一起清：分发器只看 triggerKeys()，留着旧键表就等于「引擎关了
		// 但按下 W 仍会被派发成一次帽子」，线程会堆进一个不再推进的 VM。
		activeName = "";
		cachedTriggerKeys = Set.of();
		// 移动会话要先停：它下一 tick 会把前进键按回去，光松键没用
		PathFollower.abort(reason);
		TurnSession.abort(reason);
		// 规划器的静态字段抓着当前 ClientWorld；停止后不再规划，就该放开它
		PathPlanner.reset();
		// 必须在这里松手：脚本按住的键由 KeyBindingMixin 从读取端接管，只要表里还登记着
		// 就恒返回 true。引擎停掉后没人再来松，角色会一直往前走。
		ScriptKeys.releaseEveryKey();
		// 上一份工程登记的监视器与未交付的回答不该留给下一份
		MonitorStore.clear();
		ScriptBridge.clearPendingAnswer();
		// 在途的 AI 请求同样属于上一份工程：回调落到已停止的 VM 上只会白跑，连接还得占着
		AiService.get().cancelAll();
		ChatState.clear();
		GameActions.clearChatQueue();
		LOG.info("[script] 已停止：{}", reason);
	}

	/**
	 * 只有「运行中 + 未被停止全部 + VM 健康」才接受外部触发。
	 *
	 * <p>缺了这道判断，引擎关闭后按键与聊天仍会 {@code startHat}：线程被压进
	 * {@code runtime.threads} 但 {@code _step} 不再调用，表现为只进不出的堆积。</p>
	 */
	public boolean active() {
		return running && !halted && healthy;
	}

	/**
	 * 分发一次触发事件。
	 *
	 * <p>{@code matcher} 参数已不参与过滤——过滤发生在 VM 内部（字段匹配）或由宿主
	 * 直接调用 {@link #fireKey}。保留该参数是为了不改动既有调用点。</p>
	 */
	public void fire(String trigger, Predicate<BlockNode> matcher) {
		if (!active()) {
			return;
		}
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

	/**
	 * 按键触发：按 field 精确匹配，只有对应键的脚本会启动。
	 *
	 * <p>链路出问题时现象是「按了没反应」，而三种原因（键名没收集到 / 物理键没读到 /
	 * startHats 匹配不上）在日志里看起来一模一样：没有这行是前两步的问题，有这行但
	 * 起 0 个线程是 VM 匹配的问题。所以正常派发走 debug，只有「起了 0 个线程」才告警。</p>
	 */
	public void fireKey(String key) {
		if (!active() || key == null || key.isBlank()) {
			return;
		}
		int started = RhinoVm.get().startHat("event_whenkeypressed", "KEY_OPTION", key);
		if (started == 0) {
			LOG.warn("[script] 按键「{}」派发帽子但没起线程（工程里没有这个键的帽子？）", key);
		} else {
			LOG.debug("[script] 按键「{}」派发帽子，起线程 {}", key, started);
		}
	}

	/**
	 * 聊天触发：按每个「当聊天包含」积木的关键词过滤，只有命中的脚本才启动。
	 *
	 * <p>关键词为空视为无条件匹配；消息为 null 时同样全部触发。</p>
	 */
	public void fireChat(String message) {
		if (!active() || message == null) {
			return;
		}
		// 脚本自己发的聊天会被服务端回传，若照常触发就会「自己触发自己」
		if (SelfChatEcho.isEcho(message)) {
			return;
		}
		RhinoVm.get().startChatHat(message);
	}

	/**
	 * 服务端消息通道的触发：代理服把玩家聊天包成 system 消息下发时走这里。
	 *
	 * <p>与 {@link #fireChat} 有两处刻意的差别：</p>
	 *
	 * <ol>
	 *   <li><b>只启动关键词可确定的帽子</b>（{@code chatServerAnyMatch} 为 {@code false} 时）。
	 *       这个通道里混着插件广告、加入离开提示、抢购倒计时，无关键词的帽子跟着走会被刷爆。
	 *       关键词是变量的仍会被精确求值——见 {@code __tmcResolveKeyword}。</li>
	 *   <li><b>命中才打日志</b>：这个通道的消息量在每分钟几十条，正常派发打 info 会把日志淹掉；
	 *       而「一条都不打」又让线上排查无从下手，所以只在真的起了线程时留一行。</li>
	 * </ol>
	 *
	 * <p>{@code ChatState} 的写入由调用方（事件源）负责，与玩家聊天通道保持同序：
	 * 先记录、再派发，脚本读到的就是触发它的那句话。</p>
	 */
	public void fireServerChat(String message) {
		if (!active() || message == null || message.isEmpty()) {
			return;
		}
		if (SelfChatEcho.isEcho(message)) {
			return;
		}
		int started = RhinoVm.get().startChatHat(message,
				TurboMCConfig.get().chatServerAnyMatch);
		if (started > 0) {
			LOG.info("[chat] 服务端消息命中帽子 {} 个", started);
		} else {
			LOG.debug("[chat] 服务端消息未命中帽子");
		}
	}

	public Set<String> triggerKeys() {
		return cachedTriggerKeys;
	}

	/** 重启：下次 tick 重新载入当前启用的包。 */
	public void restart() {
		running = false;
		halted = false;
		healthy = true;
		// 换工程等于上一批线程全部消失，它们的移动会话与按键同样没人来收
		PathFollower.abort("切换工程");
		TurnSession.abort("切换工程");
		ScriptKeys.releaseEveryKey();
		// 旧工程收过的消息不该挡住新工程
		ChatGate.reset();
		// 监视器与未交付的回答同属「上一份工程的状态」，一并清掉
		MonitorStore.clear();
		ScriptBridge.clearPendingAnswer();
		// AI 会话与在途请求同理：换工程后接着上一份的对话只会让模型前后矛盾
		AiService.get().cancelAll();
		ChatState.clear();
		GameActions.clearChatQueue();
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
