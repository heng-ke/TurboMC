package xyz.turbomc.client.script.action;

import net.minecraft.client.option.KeyBinding;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * <p>{@code ticks} 为负表示一直按住，非负表示剩余 tick（用于「吃食物」这类持续动作）。</p>
 */
public final class ScriptKeys {
	/** 只记剩余 tick：{@code -1} 表示一直按住。 */
	private record Hold(int ticksLeft) {
	}

	private static final Map<KeyBinding, Hold> HELD = new LinkedHashMap<>();

	private ScriptKeys() {
	}

	/**
	 * 按住某个键。{@code ticks < 0} 表示不自动松开。
	 *
	 * <p><b>幂等</b>：键已在表里时不累加计数，只延长到期时间。早先这里是引用计数
	 * （每次 {@code +1}、每次 {@link #release} 减一），但动作层拿不到稳定的脚本实例标识，
	 * 既分不清是谁按的，也保证不了「N 次按下配 N 次松开」——「重复执行 { 按下 W }」
	 * 会在一秒内累积二十次引用，而「松开 W」只抵消一次，键就再也抬不起来。改成幂等后，
	 * 重复按下等价于「继续保持按下」，一次松开即抬起。</p>
	 *
	 * <p>代价：两个脚本同时按住同一个键时只算一次，其中一个松开就会抬键。旧实现虽然
	 * 计数，但因为归属不明，任意一次 release 同样分不清归属——幂等至少不会无限增长。</p>
	 */
	public static void hold(KeyBinding key, int ticks) {
		if (key == null) {
			return;
		}
		Hold current = HELD.get(key);
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
		HELD.put(key, new Hold(ticksLeft));
		key.setPressed(true);
	}

	/**
	 * 松开某个键。
	 *
	 * <p>因为 {@link #hold} 已幂等，这里直接整条移除，不再做「减少一次引用」。</p>
	 */
	public static void release(KeyBinding key) {
		dropOne(key);
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
				entry.setValue(new Hold(left));
				continue;
			}
			entry.getKey().setPressed(false);
			iterator.remove();
		}
	}

	private static void dropOne(KeyBinding key) {
		if (key == null || HELD.remove(key) == null) {
			return;
		}
		key.setPressed(false);
	}
}