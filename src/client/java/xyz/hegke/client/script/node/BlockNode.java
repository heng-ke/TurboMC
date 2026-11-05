package xyz.hegke.client.script.node;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积木实例树，用于把 sb3 里的积木还原成可浏览的结构。
 *
 * <p>结构对应 Scratch 的三类连接：{@code next} 为顺序连接，{@code branches} 为 C 形块的内部序列，
 * {@code inputs} 为参数槽。它只在内存中存在——脚本的持久化格式就是 sb3 本身，
 * 不再有中间序列化层。</p>
 */
public final class BlockNode {
	@SerializedName("type")
	public String type;

	@SerializedName("fields")
	public Map<String, String> fields = new LinkedHashMap<>();

	@SerializedName("inputs")
	public Map<String, Slot> inputs = new LinkedHashMap<>();

	@SerializedName("branches")
	public Map<String, BlockNode> branches = new LinkedHashMap<>();

	@SerializedName("next")
	public BlockNode next;

	public BlockNode() {
	}

	public BlockNode(String type) {
		this.type = type;
	}

	public static BlockNode of(String type) {
		return new BlockNode(type);
	}

	public BlockNode field(String name, String value) {
		fields.put(name, value);
		return this;
	}

	public BlockNode input(String name, String literal) {
		inputs.put(name, Slot.literal(literal));
		return this;
	}

	public BlockNode input(String name, BlockNode block) {
		inputs.put(name, Slot.of(block));
		return this;
	}

	public BlockNode branch(String name, BlockNode... nodes) {
		branches.put(name, chain(nodes));
		return this;
	}

	/**
	 * 把节点接在本链尾部，返回本链头，便于继续链式书写。
	 */
	public BlockNode then(BlockNode node) {
		BlockNode tail = this;
		while (tail.next != null) {
			tail = tail.next;
		}
		tail.next = node;
		return this;
	}

	/**
	 * 将若干个节点串成一条链，返回链头。
	 */
	public static BlockNode chain(BlockNode... nodes) {
		List<BlockNode> list = new ArrayList<>();
		for (BlockNode node : nodes) {
			if (node != null) {
				list.add(node);
			}
		}
		if (list.isEmpty()) {
			return null;
		}
		for (int i = 0; i + 1 < list.size(); i++) {
			list.get(i).next = list.get(i + 1);
		}
		list.get(list.size() - 1).next = null;
		return list.get(0);
	}

	public String fieldOr(String name, String fallback) {
		String value = fields.get(name);
		return value != null ? value : fallback;
	}

	public Slot slot(String name) {
		return inputs.get(name);
	}

	public BlockNode branchOrNull(String name) {
		return branches.get(name);
	}
}
