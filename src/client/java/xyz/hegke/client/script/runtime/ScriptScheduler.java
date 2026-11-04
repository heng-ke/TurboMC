package xyz.hegke.client.script.runtime;

import xyz.hegke.client.script.action.ScriptKeys;
import xyz.hegke.client.script.ir.CompiledScript;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 每客户端 tick 驱动的脚本调度器。
 *
 * <p>双层预算：单个实例每 tick 上限防止单脚本霸占时间，全局每 tick 上限防止多脚本叠加掉帧。</p>
 */
public final class ScriptScheduler {
	private static final int MAX_TOTAL_INSTRS_PER_TICK = 4000;
	private static final int MAX_INSTRS_PER_INSTANCE = 400;
	/** 预算耗尽时每实例仍保留的保底配额，避免排在后面的实例永久饥饿。 */
	private static final int MIN_INSTRS_PER_INSTANCE = 32;
	private static final int MAX_TOTAL_INSTANCES = 64;

	private final List<ScriptInstance> instances = new ArrayList<>();
	/** 遍历期间新建的实例，tick 结束后统一并入，避免 ConcurrentModificationException。 */
	private final List<ScriptInstance> pendingAdd = new ArrayList<>();
	private boolean ticking;
	private int tickCounter;

	public ScriptInstance start(CompiledScript script) {
		return start(script, 0);
	}

	public ScriptInstance start(CompiledScript script, int startPc) {
		ScriptInstance instance = new ScriptInstance(script, startPc);
		if (ticking) {
			pendingAdd.add(instance);
		} else {
			instances.add(instance);
		}
		return instance;
	}

	public void tick(ScriptContext ctx) {
		tickCounter++;
		int budget = MAX_TOTAL_INSTRS_PER_TICK;

		// 按"剩余实例数"均分预算：前面的实例多跑，后面的也有保底
		int pending = 0;
		for (ScriptInstance instance : instances) {
			if (!instance.isDone()) {
				pending++;
			}
		}

		ticking = true;
		try {
			Iterator<ScriptInstance> iterator = instances.iterator();
			while (iterator.hasNext()) {
				ScriptInstance instance = iterator.next();
				if (instance.isDone()) {
					ScriptKeys.releaseAll(instance);
					iterator.remove();
					continue;
				}
				pending--;

				int allowance;
				if (budget > 0) {
					int share = budget / Math.max(1, pending + 1);
					allowance = Math.min(MAX_INSTRS_PER_INSTANCE,
							Math.max(MIN_INSTRS_PER_INSTANCE, share));
					allowance = Math.min(allowance, budget);
				} else {
					// 全局预算已耗尽：仍给保底，允许轻微超支，宁可多花一点也不能饿死
					allowance = MIN_INSTRS_PER_INSTANCE;
				}
				budget -= instance.step(allowance, tickCounter, ctx);

				for (int startPc : instance.drainSpawns()) {
					if (instances.size() + pendingAdd.size() >= MAX_TOTAL_INSTANCES) {
						break;
					}
					start(instance.script(), startPc);
				}

				if (instance.isDone()) {
					// 收回该实例按下的键，否则脚本结束后角色会一直移动
					ScriptKeys.releaseAll(instance);
					iterator.remove();
				}
			}
		} finally {
			ticking = false;
			if (!pendingAdd.isEmpty()) {
				instances.addAll(pendingAdd);
				pendingAdd.clear();
			}
		}
	}

	public void clear() {
		instances.clear();
		pendingAdd.clear();
	}

	public int activeCount() {
		int count = 0;
		for (ScriptInstance instance : instances) {
			if (!instance.isDone()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * 按脚本名终止所有实例，用于「广播重启已有线程」的语义。
	 *
	 * <p>先置为完成态再移除：正在等待这些实例的「广播并等待」需要看到它们结束，
	 * 否则会永远挂起。</p>
	 *
	 * @return 被终止的实例数
	 */
	public int killByName(String scriptName) {
		List<ScriptInstance> victims = new ArrayList<>();
		for (ScriptInstance instance : instances) {
			if (!instance.isDone() && instance.script().name().equals(scriptName)) {
				instance.kill();
				victims.add(instance);
			}
		}
		// 遍历期间不能改列表结构，交给下一轮开头的 isDone 检查回收
		if (!ticking) {
			instances.removeAll(victims);
		}
		return victims.size();
	}

	/**
	 * 统计某个脚本当前的活动实例数，用于触发风暴防护。
	 */
	public int countByName(String scriptName) {
		int count = 0;
		for (ScriptInstance instance : instances) {
			if (!instance.isDone() && instance.script().name().equals(scriptName)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * 活动实例快照，供运行面板展示。
	 */
	public List<ScriptInstance> instances() {
		List<ScriptInstance> active = new ArrayList<>();
		for (ScriptInstance instance : instances) {
			if (!instance.isDone()) {
				active.add(instance);
			}
		}
		return List.copyOf(active);
	}

	public int tickCounter() {
		return tickCounter;
	}
}
