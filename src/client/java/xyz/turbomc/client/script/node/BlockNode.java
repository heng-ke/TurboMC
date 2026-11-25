package xyz.turbomc.client.script.node;

import com.google.gson.annotations.SerializedName;

import java.util.LinkedHashMap;
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
