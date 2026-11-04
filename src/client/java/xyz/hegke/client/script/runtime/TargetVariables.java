package xyz.hegke.client.script.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * 角色作用域变量表：对应 Scratch 中「仅适用于当前角色」的变量。
 *
 * <p>与 {@link GlobalVariables}（Stage 变量）的区别是作用域键：同一角色下的所有脚本共享
 * 一份变量，不同角色之间互不可见。引擎每次启动时清空。</p>
 */
public final class TargetVariables {
	private static final Map<String, Map<String, Object>> SCOPES = new HashMap<>();

	private TargetVariables() {
	}

	public static Object get(String scopeId, String name) {
		Map<String, Object> scope = SCOPES.get(scopeId);
		if (scope == null) {
			return 0d;
		}
		return scope.getOrDefault(name, 0d);
	}

	public static void set(String scopeId, String name, Object value) {
		SCOPES.computeIfAbsent(scopeId == null ? "" : scopeId, key -> new HashMap<>())
				.put(name, value == null ? 0d : value);
	}

	/**
	 * 指定角色的变量快照，供运行面板与调试使用。
	 */
	public static Map<String, Object> snapshot(String scopeId) {
		Map<String, Object> scope = SCOPES.get(scopeId);
		return scope == null ? Map.of() : Map.copyOf(scope);
	}

	public static void clear() {
		SCOPES.clear();
	}
}
