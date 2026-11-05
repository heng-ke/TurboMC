package xyz.hegke.client.script.block;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 积木类型注册表。
 *
 * <p>这些定义现在只有一个用途：把 sb3 里的积木渲染成可读的一行文本。脚本本身由
 * TurboWarp 编写与执行，游戏内不再拼接积木，所以这里的参数不会生成任何控件。</p>
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
