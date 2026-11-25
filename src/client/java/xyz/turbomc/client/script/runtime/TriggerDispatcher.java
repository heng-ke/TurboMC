package xyz.turbomc.client.script.runtime;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
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
 *
 * <p>界面打开、或引擎没在跑时，只更新边沿与血量基线、不派发：否则关掉界面那一下会把
 * 仍按着的键当成「新按下」，死亡那一 tick 会把「受伤 + 死亡」叠在一起，
 * 引擎停着还会往一个不再推进的 VM 里塞线程。</p>
 */
public final class TriggerDispatcher {
	private final Map<String, Boolean> keyStates = new HashMap<>();
	/** 上一 tick 的界面实例，用于「打开界面」的边沿判定。 */
	private Screen lastScreen;
	private float lastHealth = -1f;
	private boolean wasDead;

	public void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			wasDead = false;
			reset();
			return;
		}
		// 死亡时客户端会弹出死亡界面，而「有界面就跳过」正是会让死亡事件永远不触发的那条判断，
		// 所以死亡检测必须放在它前面。「死亡后自动重生」是独立配置（不需要写脚本），
		// 不能跟着引擎开关失效；它派发的「当死亡」事件本身由 active() 拦住。
		dispatchDeath(client);
		// 受伤比较的是「上一 tick 的血量」，属于物理世界状态，与界面无关：
		// 打开背包时被怪打，这一击不能因为界面上盖了一层就丢掉。
		dispatchDamage(client);
		// 界面只决定「能不能触发帽子」，不决定「要不要记录按键状态」。
		// 界面里按下的键照样更新边沿状态，否则关掉界面时仍按着的 W 会被当成新按下，
		// 重复触发一次「当按下 W」。引擎没在跑时同理：只记账，不派发。
		dispatchKeys(client, ScriptEngine.get().acceptsTriggers() && client.currentScreen == null);
		// 界面打开是独立事件：它恰好发生在按键被拦截的时候，所以不能挂在上面的判断里
		dispatchScreen(client);
	}

	/**
	 * 死亡边沿：活着 → 死亡的那一 tick 触发一次。
	 */
	private void dispatchDeath(MinecraftClient client) {
		boolean dead = client.player.isDead();
		if (dead && !wasDead) {
			ScriptEngine.get().fire("event.death");
			if (TurboMCConfig.get().autoRespawn) {
				GameActions.requestRespawn(client);
			}
		}
		wasDead = dead;
	}

	private void dispatchKeys(MinecraftClient client, boolean allowFire) {
		for (String key : ScriptEngine.get().triggerKeys()) {
			boolean down = "any".equalsIgnoreCase(key)
					? InputKeys.anyDown(client)
					: InputKeys.isDown(client, key);
			boolean wasDown = keyStates.getOrDefault(key, false);
			if (allowFire && down && !wasDown) {
				// 按键过滤交给 VM：startHats 按 KEY_OPTION 字段精确匹配，
				// 只有绑定该键的脚本会启动
				ScriptEngine.get().fireKey(key);
			}
			keyStates.put(key, down);
		}
	}

	/**
	 * 界面打开边沿：{@code 无界面 → 有界面}，或换成另一个界面实例时触发一次。
	 *
	 * <p>只认实例变化，不认「界面类型」：连续开两个箱子是两次打开，应当触发两次；
	 * 而同一个界面里点来点去不触发。</p>
	 */
	private void dispatchScreen(MinecraftClient client) {
		Screen screen = client.currentScreen;
		if (screen == lastScreen) {
			return;
		}
		lastScreen = screen;
		if (screen == null) {
			return;
		}
		ScriptEngine.get().fireScreen(screenKind(screen));
	}

	/**
	 * 界面归类。{@code ScreenHandlerProvider} 表示「带容器槽位的界面」，
	 * 其中玩家自己的背包单独算一类：整理背包与整理箱子是两种脚本。
	 *
	 * <p>{@code public} 是为了让远程桥的快照复用同一套判定——两边各写一份的话，
	 * 「当打开界面」在游戏内与远程模式会给出不同的 {@code screen} 值。</p>
	 */
	public static String screenKind(Screen screen) {
		if (!(screen instanceof ScreenHandlerProvider<?>)) {
			return "other";
		}
		return screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen
				? "inventory" : "container";
	}

	private void dispatchDamage(MinecraftClient client) {
		// 死亡那一 tick 血量必然是 0，先报「受伤」会和「死亡」叠在一起；
		// 死亡语义由 dispatchDeath 单独负责，这里只把基线清掉，复活后重新建立。
		if (client.player.isDead()) {
			lastHealth = -1f;
			return;
		}
		float health = client.player.getHealth();
		if (lastHealth >= 0f && health < lastHealth - 0.01f) {
			ScriptEngine.get().fire("event.damage");
		}
		lastHealth = health;
	}

	private void reset() {
		keyStates.clear();
		lastScreen = null;
		lastHealth = -1f;
		wasDead = false;
	}
}
