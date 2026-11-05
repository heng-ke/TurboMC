package xyz.turbomc.client.script.runtime;

import net.minecraft.client.MinecraftClient;
import xyz.turbomc.client.config.TurboMCConfig;
import xyz.turbomc.client.script.ScriptEngine;
import xyz.turbomc.client.script.action.GameActions;
import xyz.turbomc.client.script.action.InputKeys;

import java.util.HashMap;
import java.util.Map;

/**
 * 事件源分发器：把每 tick 可检测的触发条件转成引擎事件。
 *
 * <p>按键用边沿检测（上一 tick 未按下 → 本 tick 按下）；受伤用血量下降判断。
 * 聊天事件由 {@code TurboMCClient} 直接注册回调，不经过本类。</p>
 */
public final class TriggerDispatcher {
	private final Map<String, Boolean> keyStates = new HashMap<>();
	private float lastHealth = -1f;
	private boolean wasDead;

	public void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			wasDead = false;
			reset();
			return;
		}
		// 死亡时客户端会弹出死亡界面，而「有界面就跳过」正是会让死亡事件永远不触发的那条判断，
		// 所以死亡检测必须放在它前面。
		dispatchDeath(client);
		if (client.currentScreen != null) {
			reset();
			return;
		}
		dispatchKeys(client);
		dispatchDamage(client);
	}

	/**
	 * 死亡边沿：活着 → 死亡的那一 tick 触发一次。
	 */
	private void dispatchDeath(MinecraftClient client) {
		boolean dead = client.player.isDead();
		if (dead && !wasDead) {
			ScriptEngine.get().fire("event.death", hat -> true);
			if (TurboMCConfig.get().autoRespawn) {
				GameActions.requestRespawn(client);
			}
		}
		wasDead = dead;
	}

	private void dispatchKeys(MinecraftClient client) {
		for (String key : ScriptEngine.get().triggerKeys()) {
			boolean down = "any".equalsIgnoreCase(key)
					? InputKeys.anyDown(client)
					: InputKeys.isDown(client, key);
			boolean wasDown = keyStates.getOrDefault(key, false);
			if (down && !wasDown) {
				// 按键过滤交给 VM：startHats 按 KEY_OPTION 字段精确匹配，
				// 只有绑定该键的脚本会启动
				ScriptEngine.get().fireKey(key);
			}
			keyStates.put(key, down);
		}
	}

	private void dispatchDamage(MinecraftClient client) {
		float health = client.player.getHealth();
		if (lastHealth >= 0f && health < lastHealth - 0.01f) {
			ScriptEngine.get().fire("event.damage", hat -> true);
		}
		lastHealth = health;
	}

	private void reset() {
		keyStates.clear();
		lastHealth = -1f;
	}
}
