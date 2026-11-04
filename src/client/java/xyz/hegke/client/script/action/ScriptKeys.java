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
 * <p><b>按键按实例归属</b>：脚本结束时由调度器调用 {@link #releaseAll(Object)} 收回自己按下的键。
 * 否则「按下 W」后脚本崩溃或正常结束，角色会一直往前走，直到玩家手动停引擎。</p>
 *
 * <p>同一个键被多个脚本按下时用引用计数，全部释放才真正松开。
 * {@code ticks} 为负表示一直按住，非负表示剩余 tick（用于「吃食物」这类持续动作）。</p>
 */
public final class ScriptKeys {
	private record Hold(int refs, int ticksLeft) {
	}

	private static final Map<KeyBinding, Hold> HELD = new LinkedHashMap<>();
	/** 实例 → 它按下的键，用于实例结束时精确回收。 */
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
	 * 收回某个实例按下的全部键。实例结束或崩溃时调用。
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

	/** 键盘表里已不再持有该键，把它从所有实例的归属列表中摘掉。 */
	private static void forget(KeyBinding key) {
		OWNED.values().forEach(keys -> keys.remove(key));
		OWNED.entrySet().removeIf(entry -> entry.getValue().isEmpty());
	}
}
