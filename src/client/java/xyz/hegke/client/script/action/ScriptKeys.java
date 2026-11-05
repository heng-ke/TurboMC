package xyz.hegke.client.script.action;

import net.minecraft.client.option.KeyBinding;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 脚本持有的按键状态：统一管理「强制按下」与「到期自动松开」。
 *
 * <p>原版每 tick 会用物理按键状态刷新 {@link KeyBinding}，单纯 {@code setPressed} 会被覆盖，
 * 因此由 {@code KeyBindingMixin} 从读取端接管：只要本表登记了某个键，就按本表的值返回。</p>
 *
 * <p><b>松手有两条路径</b>：限时按键（{@code ticks >= 0}）由每 tick 的 {@link #tick()} 到期松开；
 * 「一直按住」的键只能靠 {@link #releaseEveryKey()}，由引擎停止与切换工程时调用。
 * 少了后者，「按下 W」之后关掉引擎，角色会一直往前走——因为 {@code KeyBindingMixin} 只看本表，
 * 不看引擎是否在运行。</p>
 *
 * <p>同一个键被多个脚本按下时用引用计数，全部释放才真正松开。
 * {@code ticks} 为负表示一直按住，非负表示剩余 tick（用于「吃食物」这类持续动作）。</p>
 */
public final class ScriptKeys {
	private record Hold(int refs, int ticksLeft) {
	}

	private static final Map<KeyBinding, Hold> HELD = new LinkedHashMap<>();
	/** owner → 它按下的键。**当前恒为空**：动作层传进来的归属标识一直是 {@code null}。 */
	private static final Map<Object, Set<KeyBinding>> OWNED = new HashMap<>();

	private ScriptKeys() {
	}

	/**
	 * 按住某个键。{@code ticks < 0} 表示不自动松开。
	 */
	public static void hold(Object owner, KeyBinding key, int ticks) {
		Hold current = HELD.get(key);
		int refs = current == null ? 1 : current.refs() + 1;
		int ticksLeft;
		if (ticks < 0) {
			ticksLeft = -1;
		} else if (current == null) {
			ticksLeft = ticks;
		} else if (current.ticksLeft() < 0) {
			// 已经有一个「一直按住」：点按不能把它降级成限时，否则长按会被提前松开
			ticksLeft = -1;
		} else {
			ticksLeft = Math.max(current.ticksLeft(), ticks);
		}
		HELD.put(key, new Hold(refs, ticksLeft));
		if (owner != null) {
			OWNED.computeIfAbsent(owner, ignored -> new HashSet<>()).add(key);
		}
		key.setPressed(true);
	}

	/**
	 * 松开某个键（仅减少一次引用）。
	 */
	public static void release(Object owner, KeyBinding key) {
		if (owner != null) {
			Set<KeyBinding> keys = OWNED.get(owner);
			if (keys != null) {
				keys.remove(key);
				if (keys.isEmpty()) {
					OWNED.remove(owner);
				}
			}
		}
		dropOne(key);
	}

	/**
	 * 收回某个 owner 按下的全部键。
	 *
	 * <p>当前没有调用方，也命中不了任何条目——动作层传的归属标识恒为 {@code null}
	 * （见 {@code ScriptContext#currentInstance()}），而 {@link #hold} 对 null 不登记归属。
	 * 保留它是为了将来能从 JS 侧带线程 id 进来，实现「单个脚本结束时只松自己按的键」。</p>
	 *
	 * <p>现在真正起作用的是 {@link #releaseEveryKey()}。</p>
	 */
	public static void releaseAll(Object owner) {
		Set<KeyBinding> keys = OWNED.remove(owner);
		if (keys == null) {
			return;
		}
		for (KeyBinding key : keys) {
			dropOne(key);
		}
	}

	/**
	 * 供 mixin 查询：该键是否被脚本接管。
	 */
	public static boolean isForced(KeyBinding key) {
		return HELD.containsKey(key);
	}

	/**
	 * 松开所有脚本按键。引擎停止时调用。
	 */
	public static void releaseEveryKey() {
		for (KeyBinding key : HELD.keySet()) {
			key.setPressed(false);
		}
		HELD.clear();
		OWNED.clear();
	}

	/**
	 * 每 tick 递减限时按键，归零的自动松开。
	 */
	public static void tick() {
		Iterator<Map.Entry<KeyBinding, Hold>> iterator = HELD.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<KeyBinding, Hold> entry = iterator.next();
			Hold hold = entry.getValue();
			if (hold.ticksLeft() < 0) {
				continue;
			}
			int left = hold.ticksLeft() - 1;
			if (left > 0) {
				entry.setValue(new Hold(hold.refs(), left));
				continue;
			}
			KeyBinding key = entry.getKey();
			key.setPressed(false);
			iterator.remove();
			forget(key);
		}
	}

	private static void dropOne(KeyBinding key) {
		Hold current = HELD.get(key);
		if (current == null) {
			return;
		}
		if (current.refs() <= 1) {
			HELD.remove(key);
			key.setPressed(false);
			forget(key);
		} else {
			HELD.put(key, new Hold(current.refs() - 1, current.ticksLeft()));
		}
	}

	/** 键盘表里已不再持有该键，把它从各 owner 的归属列表中摘掉（归属表当前为空，等于空转）。 */
	private static void forget(KeyBinding key) {
		OWNED.values().forEach(keys -> keys.remove(key));
		OWNED.entrySet().removeIf(entry -> entry.getValue().isEmpty());
	}
}
