package xyz.hegke.client.script;

import net.minecraft.client.MinecraftClient;
import xyz.hegke.HkScript;
import xyz.hegke.client.config.HkScriptConfig;
import xyz.hegke.client.script.action.GameActions;
import xyz.hegke.client.script.action.ScriptKeys;
import xyz.hegke.client.script.action.ListFunctions;
import xyz.hegke.client.script.action.NativeRegistry;
import xyz.hegke.client.script.action.WorldQueries;
import xyz.hegke.client.script.block.BlockRegistry;
import xyz.hegke.client.script.ir.CompiledScript;
import xyz.hegke.client.script.ir.Compiler;
import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.node.Slot;
import xyz.hegke.client.script.runtime.GlobalVariables;
import xyz.hegke.client.script.runtime.ScratchClock;
import xyz.hegke.client.script.runtime.ScriptContext;
import xyz.hegke.client.script.runtime.ScriptInstance;
import xyz.hegke.client.script.runtime.ScriptScheduler;
import xyz.hegke.client.script.runtime.TargetVariables;
import xyz.hegke.client.script.runtime.TriggerDispatcher;
import xyz.hegke.client.script.runtime.TriggerPolicy;
import xyz.hegke.client.script.store.ScriptDefinition;
import xyz.hegke.client.script.store.ScriptStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 脚本引擎门面：初始化注册表、按触发类型装载脚本、分发事件、驱动 tick。
 *
 * <p>事件模型：带 {@code event.tick} 的脚本在启动时各创建一个常驻实例；
 * 其余触发类型（按键 / 受伤 / 聊天 / 广播）登记在案，由 {@link TriggerDispatcher}
 * 与聊天监听回调 {@link #fire}，每次触发新建一个实例。</p>
 */
public final class ScriptEngine {
	private static final ScriptEngine INSTANCE = new ScriptEngine();
	private static final int MAX_INSTANCES_PER_SCRIPT = 4;

	private final ScriptScheduler scheduler = new ScriptScheduler();
	private final Map<String, List<ScriptDefinition>> byTrigger = new HashMap<>();
	private final Map<ScriptDefinition, CompiledScript> compiledCache = new HashMap<>();

	/** 已警告过的 HAT 参数，避免每次聊天/按键都刷屏。 */
	private final Set<String> warnedHatParams = new HashSet<>();

	/** 按键触发脚本关心的键名，启动时算一次；每 tick 重建没有必要。 */
	private Set<String> cachedTriggerKeys = Set.of();

	private boolean initialized;
	private boolean running;
	private boolean halted;
	private boolean healthy = true;

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
		ScriptStore.get().load();
		healthy = SelfCheck.run();
		HkScript.LOGGER.info("[script] 引擎就绪：注册积木 {} 个，脚本 {} 个",
				BlockRegistry.all().size(), ScriptStore.get().all().size());
		if (!healthy) {
			HkScript.LOGGER.error("[script] 自检未通过，脚本引擎已停用");
		}
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
		HkScriptConfig config = HkScriptConfig.get();
		// 脚本按下的使用键需要按 tick 计数松手，与引擎是否运行无关，因此先处理
		ScriptKeys.tick();
		boolean shouldRun = config.masterEnabled && config.scriptEngineEnabled;
		if (!shouldRun) {
			halted = false;
			if (running) {
				stop("已关闭");
			}
			return;
		}
		// 脚本执行过「停止全部」后不自动恢复，需玩家重新按 F6 开启
		if (halted) {
			return;
		}
		if (!initialized) {
			init();
		}
		if (!running) {
			start();
		}
		ScriptContext context = new ScriptContext(client);
		context.setBroadcaster(this::broadcastAndCollect);
		// 挖掘等待需要读准星与世界，交给查询层实现，执行内核保持无 Minecraft 依赖
		context.setBreakProbe(WorldQueries::probeBreak);
		scheduler.tick(context);
		// 「停止全部」只在本次调度结束后处理，避免边遍历实例边清空集合
		if (context.stopAllRequested()) {
			stop("脚本执行了「停止全部」");
			halted = true;
			// 同步关闭开关，使配置状态与运行状态一致：玩家按一次 F6 即可重新启动
			HkScriptConfig.get().scriptEngineEnabled = false;
			HkScriptConfig.save();
			if (client.player != null) {
				client.player.sendMessage(
						net.minecraft.text.Text.translatable("message.hk-script.stopped_all"), true);
			}
		}
	}

	private void start() {
		running = true;
		scheduler.clear();
		byTrigger.clear();
		compiledCache.clear();
		GlobalVariables.clear();
		TargetVariables.clear();
		ListFunctions.clearAll();
		// 计时器随引擎启动归零，相当于 Scratch 点绿旗
		ScratchClock.resetTimer();

		int resident = 0;
		int waiting = 0;
		for (ScriptDefinition definition : ScriptStore.get().all()) {
			if (!definition.enabled || definition.hat == null) {
				continue;
			}
			String trigger = definition.hat.type;
			byTrigger.computeIfAbsent(trigger, key -> new ArrayList<>()).add(definition);
			if ("event.tick".equals(trigger) || "event.start".equals(trigger)) {
				if (startInstance(definition) != null) {
					resident++;
				}
			} else {
				waiting++;
			}
		}
		Set<String> keys = new HashSet<>();
		for (ScriptDefinition definition : byTrigger.getOrDefault("event.key", List.of())) {
			keys.add(definition.hat.fieldOr("KEY", "f"));
		}
		cachedTriggerKeys = Set.copyOf(keys);
		HkScript.LOGGER.info("[script] 已启动 {} 个常驻脚本，{} 个等待触发", resident, waiting);
	}

	private ScriptInstance startInstance(ScriptDefinition definition) {
		try {
			CompiledScript script = compiledCache.get(definition);
			if (script == null) {
				script = Compiler.compile(definition.name, definition.hat, definition.functions,
						definition.scopeId, HkScriptConfig.get().scratchLoopThrottle);
				compiledCache.put(definition, script);
			}
			return scheduler.start(script);
		} catch (StackOverflowError e) {
			// 嵌套过深的脚本会递归编译到栈溢出；Error 不在 Exception 分支里
			HkScript.LOGGER.error("[script] 脚本嵌套过深，已跳过: {}", definition.name);
			return null;
		} catch (Exception e) {
			HkScript.LOGGER.error("[script] 编译失败: {}", definition.name, e);
			return null;
		}
	}

	/**
	 * 分发一次触发事件，为所有匹配的脚本各创建一个实例。
	 *
	 * @param trigger 触发类型，如 {@code event.key}
	 * @param matcher 对 hat 积木的过滤条件（按键名、消息内容等）
	 */
	public void fire(String trigger, Predicate<BlockNode> matcher) {
		dispatch(trigger, matcher, null);
	}

	/**
	 * 发出广播并返回本次被唤醒的实例，供「广播并等待」使用。
	 */
	public List<ScriptInstance> broadcastAndCollect(String message) {
		List<ScriptInstance> started = new ArrayList<>();
		dispatch("event.broadcast", hat -> {
			String target = hat.fieldOr("MSG", "");
			return target.isBlank() || target.equals(message);
		}, started);
		return started;
	}

	private void dispatch(String trigger, Predicate<BlockNode> matcher,
						  List<ScriptInstance> collector) {
		if (!running) {
			return;
		}
		List<ScriptDefinition> candidates = byTrigger.get(trigger);
		if (candidates == null) {
			return;
		}
		TriggerPolicy policy = TriggerPolicy.of(trigger);
		for (ScriptDefinition definition : candidates) {
			if (!matcher.test(definition.hat)) {
				continue;
			}
			int active = scheduler.countByName(definition.name);
			switch (policy) {
				case ABANDON -> {
					// 按键：已在跑就放弃本次触发（Scratch 的 restartExistingThreads=false）
					if (active > 0) {
						continue;
					}
				}
				case RESTART -> {
					// 广播：终止已有线程后重新开始，保证同一时刻只有一份
					if (active > 0) {
						scheduler.killByName(definition.name);
					}
				}
				case CONCURRENT -> {
					if (active >= MAX_INSTANCES_PER_SCRIPT) {
						continue;
					}
				}
			}
			ScriptInstance instance = startInstance(definition);
			if (instance != null && collector != null) {
				collector.add(instance);
			}
		}
	}

	/**
	 * 脚本间广播。空目标表示任意广播脚本都接收。
	 */
	public void broadcast(String message) {
		fire("event.broadcast", hat -> {
			String target = hat.fieldOr("MSG", "");
			return target.isBlank() || target.equals(message);
		});
	}

	/**
	 * 读取 HAT 积木的文本参数。
	 *
	 * <p>字段优先，其次字面量输入。返回 {@code null} 表示该参数是表达式——
	 * 触发时没有执行上下文，无法求值，调用方应按「不匹配」处理，
	 * 绝不能退化成空串（空串会让"任意聊天都触发"这类误判发生）。</p>
	 */
	public String hatText(BlockNode hat, String name) {
		String field = hat.fields.get(name);
		if (field != null) {
			return field;
		}
		Slot slot = hat.inputs.get(name);
		if (slot == null) {
			return "";
		}
		if (slot.hasBlock()) {
			if (warnedHatParams.add(hat.type + "." + name)) {
				HkScript.LOGGER.warn("[script] {} 的 {} 是表达式，触发时无法求值，该条件按不匹配处理",
						hat.type, name);
			}
			return null;
		}
		return slot.literal == null ? "" : slot.literal;
	}

	/**
	 * 当前所有按键触发脚本关心的按键名集合。
	 */
	public Set<String> triggerKeys() {
		return cachedTriggerKeys;
	}

	private void stop(String reason) {
		running = false;
		scheduler.clear();
		byTrigger.clear();
		compiledCache.clear();
		cachedTriggerKeys = Set.of();
		GameActions.releaseMovementKeys();
		HkScript.LOGGER.info("[script] 已停止：{}", reason);
	}

	/**
	 * 编辑器保存后调用：重新装载全部脚本，使改动立即生效。
	 */
	public void restart() {
		if (!running) {
			return;
		}
		stop("重新加载脚本");
		start();
	}

	/**
	 * 取某脚本的编译产物（必要时现场编译并缓存）。编辑器用它显示积木对应的指令区间。
	 */
	public CompiledScript compiledOrNull(ScriptDefinition definition) {
		if (definition == null || definition.hat == null) {
			return null;
		}
		CompiledScript cached = compiledCache.get(definition);
		if (cached != null) {
			return cached;
		}
		try {
			CompiledScript compiled = Compiler.compile(definition.name, definition.hat,
					definition.functions, definition.scopeId,
					HkScriptConfig.get().scratchLoopThrottle);
			compiledCache.put(definition, compiled);
			return compiled;
		} catch (Exception e) {
			return null;
		}
	}

	public boolean isRunning() {
		return running;
	}

	public List<ScriptInstance> instances() {
		return scheduler.instances();
	}

	/**
	 * 当前正在执行的积木（按脚本名筛选），供编辑器做执行高亮。
	 *
	 * <p>同名脚本可能同时有多个实例（广播、{@code spawn}），所以返回集合而不是单块。
	 * 用 {@link IdentityHashMap} 建集合是因为 {@link BlockNode} 没有重写 {@code equals}，
	 * 高亮必须按「同一个对象」判断，而不是按内容相等。</p>
	 *
	 * <p>取 {@code pc - 1} 而不是 {@code pc}：{@code pc} 指向下一条待执行指令，
	 * 而 {@code pc - 1} 落在刚刚执行完的那条上——等待 500 tick 时它会一直停在「等待」那一块，
	 * 用 {@code pc} 则会提前跳到下一行，看起来像执行错了位置。</p>
	 */
	public Set<BlockNode> runningBlocks(String scriptName) {
		if (scriptName == null || !running) {
			return Set.of();
		}
		Set<BlockNode> blocks = Collections.newSetFromMap(new IdentityHashMap<>());
		for (ScriptInstance instance : scheduler.instances()) {
			if (!scriptName.equals(instance.script().name())) {
				continue;
			}
			BlockNode block = instance.script().blockAt(Math.max(0, instance.pc() - 1));
			if (block != null) {
				blocks.add(block);
			}
		}
		return blocks;
	}

	public int activeCount() {
		return scheduler.activeCount();
	}
}
