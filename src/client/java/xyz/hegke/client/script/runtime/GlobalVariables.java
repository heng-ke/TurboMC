package xyz.hegke.client.script.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局变量表：跨脚本、跨实例共享。
 *
 * <p>引擎每次启动时清空，避免上一次运行的残留值影响脚本判断。</p>
 */
public final class GlobalVariables {
	private static final Map<String, Object> VALUES = new HashMap<>();

	private GlobalVariables() {
	}

	public static Object get(String name) {
		return VALUES.getOrDefault(name, 0d);
	}

	public static void set(String name, Object value) {
		VALUES.put(name, value == null ? 0d : value);
	}

	public static Map<String, Object> snapshot() {
		return Map.copyOf(VALUES);
	}

	public static void clear() {
		VALUES.clear();
	}
}
