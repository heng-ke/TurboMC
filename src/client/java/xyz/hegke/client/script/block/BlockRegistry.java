package xyz.hegke.client.script.block;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 积木类型注册表。编译期与编辑器共用同一份定义，避免两处语义漂移。
 */
public final class BlockRegistry {
	private static final Map<String, BlockType> TYPES = new LinkedHashMap<>();
	private static boolean bootstrapped;

	private BlockRegistry() {
	}

	public static void register(BlockType type) {
		TYPES.put(type.id(), type);
	}

	public static BlockType get(String id) {
		return TYPES.get(id);
	}

	public static boolean exists(String id) {
		return TYPES.containsKey(id);
	}

	public static Collection<BlockType> all() {
		return TYPES.values();
	}

	public static void bootstrapOnce() {
		if (bootstrapped) {
			return;
		}
		bootstrapped = true;
		Blocks.registerAll();
	}
}
