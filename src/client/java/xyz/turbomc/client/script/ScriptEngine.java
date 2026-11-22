package xyz.turbomc.client.script;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import com.google.gson.JsonObject;
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
import xyz.turbomc.client.script.sb3.ProjectSanity;
import xyz.turbomc.client.script.sb3.Sb3Importer;
import xyz.turbomc.client.script.render.FrameOverlay;
import xyz.turbomc.client.script.remote.RemoteBridge;
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
	/** 引擎处于哪个阶段。把「没开」「包坏了」「脚本跑崩了」在界面上区分开。 */
	private Status status = Status.OFF;
	/** 状态的具体原因（异常摘要等），没有则为空。 */
	private Text detail = Text.empty();
	/** 预热只尝试一次：失败就交给进世界后的 start() 再 init。 */
	private boolean warmUpTried;
	private int scriptCount;
	/** 当前运行的包文件名，供面板显示。 */
	private String activeName = "";
	/** 热重载提示：下一次 {@link #start()} 成功后并进状态原因，只提示一次。 */
	private Text reloadNotice;
	/** 上一次检查 imports/ 的时刻（毫秒）。 */
	private long lastReloadCheckMs;

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
		// 聊天队列必须最先推进，且不受任何分支影响。
		// 它曾经只在 vm.tick 之后调一次，于是「引擎没开」「远程会话中」「停止全部」三种情况下
		// 冷却永远不减：第一条消息发出去了，后续全部卡在队列里，表现为「说了一次就再也不说」。
		GameActions.tickChat();
		if (!healthy) {
			// 工程读不出来 / 脚本跑崩时 VM 不再推进：先把会话与按键收干净，否则角色会带着
			// 上一份工程按下的前进键一直往前走。状态与原因保留，交给界面显示。
			if (running) {
				teardown();
			}
			ScriptKeys.tick();
			return;
		}
		if (client.player == null || client.world == null) {
			halted = false;
			if (running) {
				stop(Status.NO_WORLD, null);
			} else if (status != Status.OFF_WORLD_EXIT && status != Status.REMOTE) {
				// 远程会话可能是在标题界面建立的（面板先连、后进世界），
				// 这里若写成 NO_WORLD，进世界后就会把内 VM 拉起来跟浏览器抢同一份工程
				setStatus(Status.NO_WORLD, null);
			}
			// 标题界面/加载界面期间把 VM 备好，进世界就不用再付那 0.6 秒
			tryWarmUp();
			return;
		}
		TurboMCConfig config = TurboMCConfig.get();
		// 脚本按下的键需要按 tick 计数松手，与引擎是否运行无关
		ScriptKeys.tick();
		// 放在引擎开关判断之前：用户可能先丢文件再按 F6，包列表要能先更新
		pollAutoReload();
		// 远程会话期间内 VM 一律不推进：浏览器那侧是主 VM，同一份工程跑两遍会互相踩坐标。
		// 本分支必须在开关判断之前——否则玩家一按 F6 就会把 REMOTE 改写成 OFF，
		// HUD 上「远程调试中」自己消失，而浏览器那侧还在驱动。
		if (status == Status.REMOTE) {
			return;
		}
		if (!config.masterEnabled || !config.scriptEngineEnabled) {
			halted = false;
			if (running || status == Status.RUNNING || status == Status.HALTED) {
				stop(Status.OFF, null);
			} else if (status.staleWhenEngineOff()) {
				// 人已经站在世界里，这两个状态都是过期的现场：NO_WORLD 是标题界面留下的
				// （或引擎关闭时 restart() 写的），OFF_WORLD_EXIT 是上一局退出时留下的。
				// 留着不动就是「人在世界里，HUD 说还没进入世界」，要按一次 F6 才纠正（DEF-01）。
				// 故障态（LOAD_FAILED / RUNTIME_ERROR）不在此列，它们要保留给玩家看。
				setStatus(Status.OFF, null);
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

		// 运行期异常被 VM 内部 catch 掉了，不主动拉取的话界面上只会显示「已停止」，
		// 玩家无从知道是脚本自己的 bug。拉取即视为引擎不可用，等 F6 / 重新载入复位。
		String fatal = vm.takeFatalError();
		if (fatal != null) {
			healthy = false;
			setStatus(Status.RUNTIME_ERROR, Text.literal(shorten(fatal)));
			teardown();
			LOG.error("[script] 运行期异常，脚本已停用：{}", fatal);
			return;
		}

		// 「停止全部」是 VM 内部事件，Java 侧只有主动查询才看得见。
		// 不查的话线程已经清空，「走向」会话却还会按着前进键再走满 MAX_TICKS。
		if (vm.takeProjectStops() > 0) {
			halted = true;
			setStatus(Status.HALTED, null);
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
	 * 放标题界面最多让脚本暂时不可用，进世界时 {@code start()} 还会再走一次 init。</p>
	 *
	 * <p><b>只看总开关，不看引擎开关</b>——这条是踩过坑的。{@code disableEngineOnExit}
	 * 默认开，退出世界会把 {@code scriptEngineEnabled} 复位成 false 并写盘；如果预热也跟着
	 * 这个开关走，下次启动在标题界面就不预热，玩家进世界后按 F6 才现付
	 * {@code vm.init} 的 0.7 秒 + 工程冷载 0.4 秒，全部落在渲染线程上，表现为一次 1.1 秒的卡死。
	 * 预热成本换到标题界面（游戏本来就在加载资源）几乎无感，这个交换是划算的。</p>
	 *
	 * <p><b>只在没有玩家时调用</b>，所以不会和 {@code start()} 抢同一帧。</p>
	 */
	private void tryWarmUp() {
		if (warmUpTried) {
			return;
		}
		TurboMCConfig config = TurboMCConfig.get();
		if (!config.masterEnabled) {
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
			setStatus(Status.NO_PACK, null);
			LOG.info("[script] 没有启用的脚本包，等待导入");
			return;
		}

		String json = PackStore.get().projectJson(pack);
		if (json == null) {
			healthy = false;
			setStatus(Status.LOAD_FAILED, Text.translatable("status.turbomc.detail.project_unreadable"));
			LOG.error("[script] 无法读取工程 {}，脚本已停用", pack.fileName);
			return;
		}

		try {
			// 先做一次结构校验：VM 在 Rhino 里遇到「造型为空」这类缺陷时，
			// 抛的是 Non-ascii character in FixedAsciiString，跟真实原因完全无关（DEF-03）
			ProjectSanity.Problem problem = ProjectSanity.check(json);
			if (problem != null) {
				healthy = false;
				setStatus(Status.LOAD_FAILED, Text.translatable(problem.key(), problem.args()));
				LOG.error("[script] 工程 {} 未通过载入前校验：{} {}",
						pack.fileName, problem.key(), java.util.Arrays.toString(problem.args()));
				return;
			}
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
			// 热重载提示只显示一次：取走即清，避免每次普通启动都挂着「已自动重载」
			Text notice = reloadNotice;
			reloadNotice = null;
			setStatus(Status.RUNNING, notice);
			LOG.info("[script] 已启动 {}：{} 个脚本，线程 {} 个，监听按键 {} 个",
					pack.fileName, scriptCount, vm.threadCount(), cachedTriggerKeys.size());
		} catch (RuntimeException e) {
			healthy = false;
			setStatus(Status.LOAD_FAILED, Text.literal(shorten(e.toString())));
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

	/** 停止并记录原因：界面靠 {@link #statusText()} 区分「没开」「包坏了」「跑崩了」。 */
	/**
	 * 远程会话建立：内 VM 让位给浏览器那侧。
	 *
	 * <p>做法是复用 {@link #stop}——一次 {@code teardown} 把键表、寻路、转向、聊天队列、
	 * 监视器、画框全部收干净，与玩家手动关引擎走同一条路，不新增一套收尾逻辑。</p>
	 *
	 * <p><b>必须由主线程调用</b>：握手回调在 WebSocket I/O 线程上。</p>
	 */
	public void suspendForRemote(String detail) {
		if (status == Status.REMOTE) {
			return;
		}
		stop(Status.REMOTE, detail == null || detail.isBlank() ? Text.empty() : Text.literal(detail));
		// teardown 会把键表清空，而远程会话恰恰要靠它做按键边沿检测
		refreshRemoteContext();
		LOG.info("[script] 远程会话建立，内 VM 让位");
	}

	/**
	 * 远程会话内的「停止全部」：只收手，不结束会话。
	 *
	 * <p>浏览器点红停止时线程在浏览器侧已经清空，但游戏侧的动作没人收——按住的键、
	 * 寻路会话、待发的聊天段落、脚本画的框都还在，人物会一直往前走。</p>
	 */
	public void abortForRemote(String reason) {
		if (status != Status.REMOTE) {
			return;
		}
		teardown();
		// 收手同样会清键表，不清回来后续的「当按下 W」就再也不触发了
		refreshRemoteContext();
		LOG.info("[script] 远程会话内停止全部：{}", reason == null ? "" : reason);
	}

	/**
	 * 只算「这个工程有哪些触发键」，不载入工程、不跑绿旗。
	 *
	 * <p>远程会话里内 VM 是停的，走不了 {@link #start()}；但按键边沿检测需要键表，
	 * 否则「当按下 W」在远程模式下永远不会触发。</p>
	 */
	private void refreshRemoteContext() {
		ScriptPack pack = PackStore.get().activePack();
		if (pack == null) {
			activeName = "";
			scriptCount = 0;
			cachedTriggerKeys = Set.of();
			return;
		}
		try {
			activeName = pack.fileName;
			scriptCount = countScripts(pack);
			cachedTriggerKeys = collectKeys(pack);
			// 按键帽子在远程模式下靠这份键表做边沿检测：为 0 就说明「按了没反应」不是 VM 的问题
			LOG.info("[script] 远程会话：监听按键 {} 个{}", cachedTriggerKeys.size(),
					cachedTriggerKeys.isEmpty() ? "（工程里没有「当按下」帽子）" : "：" + cachedTriggerKeys);
		} catch (RuntimeException e) {
			// 工程本身有问题时不要连累远程会话：没有键表就只是按键帽子不触发
			cachedTriggerKeys = Set.of();
			LOG.warn("[script] 远程会话读取触发键失败：{}", e.toString());
		}
	}

	/**
	 * 远程会话结束：内 VM 保持关闭，等玩家按 F6 重新开启。
	 *
	 * <p>会把配置里的引擎开关一起关掉。不关的话下一 tick 的 {@code if (!running) start()}
	 * 会自己把脚本拉起来——玩家刚在浏览器里调完，游戏里突然开始跑，是最坏的那种意外。</p>
	 */
	public void endRemoteSession() {
		if (status != Status.REMOTE) {
			return;
		}
		TurboMCConfig config = TurboMCConfig.get();
		if (config.scriptEngineEnabled) {
			config.scriptEngineEnabled = false;
			TurboMCConfig.save();
		}
		stop(Status.OFF, null);
		LOG.info("[script] 远程会话结束，脚本引擎保持关闭");
	}

	private void stop(Status status, Text detail) {
		teardown();
		setStatus(status, detail);
		LOG.info("[script] 已停止：{}", statusText().getString());
	}

	/**
	 * 只做收尾，不改状态。
	 *
	 * <p>故障分支（载入失败、运行期异常）在别处已经写好了状态，这里若再写一次就会把
	 * 具体原因覆盖成「已停止」，等于把唯一的诊断信息抹掉。</p>
	 */
	private void teardown() {
		running = false;
		scriptCount = 0;
		// 键表与包名一起清：分发器只看 triggerKeys()，留着旧键表就等于「引擎关了
		// 但按下 W 仍会被派发成一次帽子」，线程会堆进一个不再推进的 VM。
		activeName = "";
		cachedTriggerKeys = Set.of();
		// 移动会话要先停：它下一 tick 会把前进键按回去，光松键没用
		PathFollower.abort("引擎停止");
		TurnSession.abort("引擎停止");
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
		// 引擎停了就没人能再清除这些框：留着等于世界里多出一批谁也消不掉的线框，
		// 只能退出存档。与「停止移动」同理，脚本画的东西随脚本一起收走。
		FrameOverlay.clear();
	}

	/**
	 * 退出存档/服务器时的收尾。
	 *
	 * <p>总开关是持久化的：上一局按过 F6，配置里就留着 {@code true}，下一局进世界
	 * 的第一帧会自动跑起来，玩家没有机会选择。是否在这里复位由配置
	 * {@code disableEngineOnExit} 决定（默认开）。</p>
	 *
	 * <p>调用方必须保证在主线程执行：本方法会写配置文件，并与 tick 争用会话状态。</p>
	 */
	public void onWorldExit() {
		TurboMCConfig config = TurboMCConfig.get();
		if (!config.shouldDisableEngineOnExit() || !config.scriptEngineEnabled) {
			return;
		}
		config.scriptEngineEnabled = false;
		TurboMCConfig.save();
		// 不等下一 tick 的 player==null 分支：断线回调这一帧世界引用可能还在，
		// 立刻收尾才能保证按键、会话与聊天队列在同一帧内释放干净。
		stop(Status.OFF_WORLD_EXIT, null);
		LOG.info("[script] 已按配置关闭引擎，下次进入世界需按 F6 开启");
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
	 * 触发是否应当派发：游戏内引擎在跑，或远程会话接管中。
	 *
	 * <p>远程会话的内 VM 是停的，{@link #active()} 恒为 false；若触发源仍只看
	 * {@code active()}，「当按下 W」「当受伤」在远程模式下永远不触发——帽子表下发得再全
	 * 也没人喂它。</p>
	 */
	public boolean acceptsTriggers() {
		return active() || status == Status.REMOTE;
	}

	/**
	 * 分发一次触发事件。
	 *
	 * <p>{@code matcher} 参数已不参与过滤——过滤发生在 VM 内部（字段匹配）或由宿主
	 * 直接调用 {@link #fireKey}。保留该参数是为了不改动既有调用点。</p>
	 */
	public void fire(String trigger, Predicate<BlockNode> matcher) {
		if (status == Status.REMOTE) {
			// 远程会话：内 VM 是停的，帽子由浏览器那侧按 §7.5 的帽子表起
			switch (trigger) {
				case "event.damage" -> RemoteBridge.get().publishEvent("damage", null);
				case "event.death" -> RemoteBridge.get().publishEvent("death", null);
				// event.chat 走 fireChat（要带文本），event.start / event.tick 由绿旗与 VM 自己负责
				default -> {
				}
			}
			return;
		}
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
	/**
	 * 界面打开触发：按 {@code SCREEN} 字段匹配。
	 *
	 * <p>宿主把界面归成三类：{@code inventory}（玩家背包）、{@code container}（箱子/熔炉等
	 * 带容器的界面）、{@code other}（聊天、暂停、书、告示牌等）。派发时先发具体类别，
	 * 再发一次 {@code any}——所以「当打开（任意）」的脚本只会被第二发命中一次，
	 * 不会因为两类都发而重复启动。</p>
	 *
	 * <p>为什么要有这块积木：界面打开时按键是被拦截的（见 {@code TriggerDispatcher}），
	 * 「打开箱子就执行」这类脚本原本只能靠轮询，而它本质上是事件。</p>
	 */
	public void fireScreen(String kind) {
		if (kind == null || kind.isBlank()) {
			return;
		}
		if (status == Status.REMOTE) {
			JsonObject data = new JsonObject();
			data.addProperty("screen", kind);
			RemoteBridge.get().publishEvent("screen_open", data);
			return;
		}
		if (!active()) {
			return;
		}
		int started = RhinoVm.get().startHat("turbomc_whenScreenOpen", "SCREEN", kind);
		if (!"any".equals(kind)) {
			started += RhinoVm.get().startHat("turbomc_whenScreenOpen", "SCREEN", "any");
		}
		if (started == 0) {
			LOG.debug("[script] 界面「{}」派发帽子但没起线程（工程里没有这块帽子？）", kind);
		} else {
			LOG.debug("[script] 界面「{}」派发帽子，起线程 {}", kind, started);
		}
	}

	public void fireKey(String key) {
		if (key == null || key.isBlank()) {
			return;
		}
		if (status == Status.REMOTE) {
			JsonObject data = new JsonObject();
			data.addProperty("key", key);
			RemoteBridge.get().publishEvent("key", data);
			return;
		}
		if (!active()) {
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
		if (message == null) {
			return;
		}
		if (status == Status.REMOTE) {
			// 回声过滤沿用游戏内规则：脚本自己发的消息不能被自己再触发一次
			if (!SelfChatEcho.isEcho(message)) {
				JsonObject data = new JsonObject();
				data.addProperty("text", message);
				RemoteBridge.get().publishEvent("chat", data);
			}
			return;
		}
		if (!active()) {
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
		if (message == null || message.isEmpty()) {
			return;
		}
		if (status == Status.REMOTE) {
			// 远程侧只有一种聊天匹配器（空关键词即全匹配），表达不了 chatServerAnyMatch=false
			// 那套「只起有关键词的帽子」的规则，这里按普通聊天转发，差异记在 §7.5
			if (!SelfChatEcho.isEcho(message)) {
				JsonObject data = new JsonObject();
				data.addProperty("text", message);
				data.addProperty("server", true);
				RemoteBridge.get().publishEvent("chat", data);
			}
			return;
		}
		if (!active()) {
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

	/**
	 * 引擎阶段。
	 *
	 * <p>与「是否运行」分开：{@code running} 只说明 VM 在推进，状态还要说明<b>为什么</b>。
	 * 之前这四种情况在界面上都是同一句「脚本已停用」，玩家无法判断是自己没开、
	 * 包坏了，还是脚本把自己跑崩了。</p>
	 */
	public enum Status {
		/** 总开关或引擎开关关闭。 */
		OFF,
		/** 退出世界时按配置自动关闭。 */
		OFF_WORLD_EXIT,
		/** 没有启用任何脚本包。 */
		NO_PACK,
		/** 还没有进入世界。 */
		NO_WORLD,
		/** 正在运行。 */
		RUNNING,
		/** 停止全部，需按 F6 重开。 */
		HALTED,
		/** 浏览器远程会话进行中：内 VM 让位，由浏览器那侧驱动。 */
		REMOTE,
		/** 工程读取 / 启动失败。 */
		LOAD_FAILED,
		/** 运行期异常，VM 已停止。 */
		RUNTIME_ERROR;

		/**
		 * 引擎关闭时，该状态是否属于「过期现场」。
		 *
		 * <p>只认这两个：{@link #NO_WORLD} 是标题界面（或引擎关闭时 {@code restart()}）写的，
		 * {@link #OFF_WORLD_EXIT} 是上一局退出时写的。人已经在世界里时它们都是假话。
		 * 故障态必须排除在外——把 {@code LOAD_FAILED} 改成「已关闭」等于抹掉唯一的诊断信息。</p>
		 */
		public boolean staleWhenEngineOff() {
			return this == NO_WORLD || this == OFF_WORLD_EXIT;
		}
	}

	public Status status() {
		return status;
	}

	/** 状态短语（不含具体原因）。 */
	public Text statusText() {
		return switch (status) {
			case OFF -> Text.translatable("status.turbomc.off");
			case OFF_WORLD_EXIT -> Text.translatable("status.turbomc.off_world_exit");
			case NO_PACK -> Text.translatable("status.turbomc.no_pack");
			case NO_WORLD -> Text.translatable("status.turbomc.no_world");
			case RUNNING -> activeName.isEmpty()
					? Text.translatable("status.turbomc.no_pack")
					: Text.translatable("status.turbomc.running", activeName);
			case HALTED -> Text.translatable("status.turbomc.halted");
			case REMOTE -> Text.translatable("status.turbomc.remote");
			case LOAD_FAILED -> Text.translatable("status.turbomc.load_failed");
			case RUNTIME_ERROR -> Text.translatable("status.turbomc.runtime_error");
		};
	}

	/** 具体原因：载入失败 / 运行期异常的摘要，正常时为空。 */
	public Text statusDetail() {
		return detail;
	}

	/** 拼成一行的状态（含原因），HUD 与列表页用。 */
	public String hudLine() {
		String base = statusText().getString();
		String extra = detail.getString();
		return extra.isEmpty() ? base : base + "：" + extra;
	}

	/** 状态颜色：绿=运行，黄=停止全部，红=故障，灰=未运行。 */
	public int statusColor() {
		return switch (status) {
			case RUNNING -> 0xFF55FF55;
			case HALTED -> 0xFFFFD54F;
			case REMOTE -> 0xFF55DDFF;
			case LOAD_FAILED, RUNTIME_ERROR -> 0xFFFF5555;
			default -> 0xFF9A9A9A;
		};
	}

	private void setStatus(Status status, Text detail) {
		this.status = status;
		this.detail = detail == null ? Text.empty() : detail;
	}

	/** 异常摘要：HUD 一行只放得下几十个字。 */
	private static String shorten(String text) {
		if (text == null) {
			return "";
		}
		int limit = 60;
		if (text.codePointCount(0, text.length()) <= limit) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, limit)) + "…";
	}

	/**
	 * 热重载：按配置间隔检查 {@code imports/}，变化则同步；启用中的包被更新时重启引擎。
	 *
	 * <p>这是「Ctrl+S 就生效」的实现。三个刻意的取舍：</p>
	 *
	 * <ul>
	 *   <li><b>按间隔轮询，不注册文件监听</b>：WatchService 在 Windows 上对「整体覆盖」
	 *       常报 ENTRY_MODIFY 多次、偶尔漏报，且要额外线程；每 2 秒扫一个只有几个文件的
	 *       目录，成本远低于维护一个监听器的坑。</li>
	 *   <li><b>指纹不取目录 mtime</b>：Windows 上覆盖同名文件不更新父目录时间，会永远不触发。</li>
	 *   <li><b>只在启用中的包被更新时重启</b>：导入一个新包不该打断正在跑的脚本。</li>
	 * </ul>
	 */
	private void pollAutoReload() {
		TurboMCConfig config = TurboMCConfig.get();
		if (!config.shouldAutoReload()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastReloadCheckMs < config.reloadIntervalMsValue()) {
			return;
		}
		lastReloadCheckMs = now;

		PackStore.SyncResult result = PackStore.get().pollImports();
		if (result == null || (result.added() == 0 && result.updated() == 0)) {
			return;
		}
		if (!result.activeUpdated()) {
			LOG.info("[script] 热重载：新增 {}，更新 {}", result.added(), result.updated());
			return;
		}
		if (!running) {
			// 引擎没在跑：包已更新，但没人会看到提示。下次按 F6 自然加载新版本。
			LOG.info("[script] 热重载：启用中的包已更新（引擎未运行）");
			return;
		}
		// 提示留到下一次 start() 成功后再显示：此刻 restart 会把状态重置成 NO_WORLD
		reloadNotice = Text.translatable("status.turbomc.reloaded");
		LOG.info("[script] 热重载：启用中的包已更新，重启引擎");
		restart();
	}

	/** 重启：下次 tick 重新载入当前启用的包。 */
	public void restart() {
		running = false;
		halted = false;
		healthy = true;
		// 故障标志复位后，状态也要跟着复位：下一次 tick 会重新判定成运行中/无包
		setStatus(Status.NO_WORLD, null);
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
		// 旧工程画的框不该留到新工程里：坐标可能来自另一套脚本、甚至另一个维度
		FrameOverlay.clear();
	}

	/**
	 * 「停止全部」后按 F6：直接重开一次，不经过「先关再开」。
	 *
	 * <p>HALTED 时开关本来就是 true（否则 tick 早就走到关闭分支了），所以按键那侧
	 * 再反转一次只会先把引擎关掉，玩家要按两下才回来，而 HUD 明明写着「按 F6 重新开启」
	 * （DEF-02）。这里把这一下直接消费成重启。</p>
	 *
	 * @return {@code true} 表示确实处于「停止全部」并已重启，调用方不要再反转开关
	 */
	public boolean resumeFromHalt() {
		if (!halted) {
			return false;
		}
		TurboMCConfig config = TurboMCConfig.get();
		if (!config.scriptEngineEnabled) {
			// halted 只可能在开关为真时产生；这里兜一手，避免留下「开了却不跑」的组合
			config.scriptEngineEnabled = true;
			TurboMCConfig.save();
		}
		restart();
		LOG.info("[script] 从「停止全部」直接重开");
		return true;
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
