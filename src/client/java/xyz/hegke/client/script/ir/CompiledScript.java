package xyz.hegke.client.script.ir;

import xyz.hegke.client.script.node.BlockNode;

import java.util.List;
import java.util.Map;

/**
 * 编译产物：一份脚本对应一条扁平指令序列。
 *
 * @param name    脚本名
 * @param hatType 入口积木类型，决定触发方式
 * @param code    指令序列
 * @param scopeId 角色作用域标识；{@code data.get/set} 作用域为 {@code target} 的变量
 *                存放在这个命名空间下。为空表示该脚本没有角色作用域（编辑器新建脚本）。
 * @param ranges  积木 → 指令区间（含首尾）。用于把报错里的 pc 反查到具体积木，
 *                也让编辑器能显示"这块积木编译成了哪几条指令"。
 */
public record CompiledScript(String name, String hatType, List<Instr> code, String scopeId,
							 Map<BlockNode, int[]> ranges) {

	public CompiledScript(String name, String hatType, List<Instr> code) {
		this(name, hatType, code, "", Map.of());
	}

	public CompiledScript(String name, String hatType, List<Instr> code, String scopeId) {
		this(name, hatType, code, scopeId, Map.of());
	}

	public int size() {
		return code.size();
	}

	/**
	 * 某块积木编译成的指令区间；没有记录时返回 {@code null}。
	 */
	public int[] rangeOf(BlockNode node) {
		return node == null ? null : ranges.get(node);
	}

	/**
	 * 反查：包含该 pc 的**最内层**积木。
	 *
	 * <p>外层积木的区间包含内层，所以取范围最小的那个，才对应"当前正在执行的积木"。</p>
	 */
	public BlockNode blockAt(int pc) {
		BlockNode best = null;
		int bestSpan = Integer.MAX_VALUE;
		for (Map.Entry<BlockNode, int[]> entry : ranges.entrySet()) {
			int[] range = entry.getValue();
			if (range == null || pc < range[0] || pc > range[1]) {
				continue;
			}
			int span = range[1] - range[0];
			if (span < bestSpan) {
				bestSpan = span;
				best = entry.getKey();
			}
		}
		return best;
	}
}
