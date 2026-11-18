package xyz.turbomc.client.script.action;

import xyz.turbomc.client.script.action.motion.TurnActions;
import xyz.turbomc.client.script.action.path.PathActions;
import xyz.turbomc.client.script.ai.AiActions;
import xyz.turbomc.client.script.runtime.ScriptContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 游戏侧动作与求值函数的注册表。积木只引用 id，实现集中在此，便于版本升级时单独修复。
 */
public final class NativeRegistry {
	@FunctionalInterface
	public interface Action {
		void run(ScriptContext ctx, Object[] args);
	}

	@FunctionalInterface
	public interface Function {
		Object apply(ScriptContext ctx, Object[] args);
	}

	private static final Map<String, Action> ACTIONS = new HashMap<>();
	private static final Map<String, Function> FUNCTIONS = new HashMap<>();
	private static boolean bootstrapped;

	private NativeRegistry() {
	}

	public static void action(String id, Action action) {
		ACTIONS.put(id, action);
	}

	public static void function(String id, Function function) {
		FUNCTIONS.put(id, function);
	}

	public static Action getAction(String id) {
		return ACTIONS.get(id);
	}

	public static Function getFunction(String id) {
		return FUNCTIONS.get(id);
	}

	public static void bootstrapOnce() {
		if (bootstrapped) {
			return;
		}
		bootstrapped = true;
		GameActions.registerAll();
		FrameActions.registerAll();
		PathActions.registerAll();
		TurnActions.registerAll();
		ContainerActions.registerAll();
		WorldQueries.registerAll();
		AiActions.registerAll();
	}
}
