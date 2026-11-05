package xyz.hegke.client.script.ui;

import net.minecraft.client.resource.language.I18n;
import xyz.hegke.client.script.block.BlockRegistry;
import xyz.hegke.client.script.block.BlockType;
import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.node.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 积木链与列表之间的转换工具，以及界面上使用的摘要文案。
 */
public final class NodeChain {
	private static final int GUARD = 10_000;

	private NodeChain() {
	}

	public static List<BlockNode> toList(BlockNode head) {
		List<BlockNode> nodes = new ArrayList<>();
		BlockNode node = head;
		int guard = 0;
		while (node != null && guard++ < GUARD) {
			nodes.add(node);
			node = node.next;
		}
		return nodes;
	}

	public static String label(String type) {
		return I18n.translate("block.hk-script." + type);
	}

	/** 摘要里嵌套表达式的最大展开层数。 */
	private static final int INLINE_DEPTH = 3;

	/**
	 * 单行摘要：积木名 + 字段 + 参数，参数名与枚举值均本地化。
	 */
	public static String summary(BlockNode node) {
		return summary(node, "", INLINE_DEPTH);
	}

	/**
	 * 带角色作用域的摘要：变量与列表名会省略当前角色的前缀。
	 */
	public static String summary(BlockNode node, String scopeId) {
		return summary(node, scopeId, INLINE_DEPTH);
	}

	/**
	 * 带展开层数的摘要。
	 *
	 * <p>嵌套的表达式会内联成 {@code (列表长度 (方块列表))} 这样的文本，而不是 {@code [...]}——
	 * 查看时最需要看懂的正是"这里到底算的是什么"。层数用尽后退化为 {@code …}，避免一行过长。</p>
	 */
	private static String summary(BlockNode node, String scopeId, int depth) {
		// 有显示模板的积木直接套模板，参数嵌在句子里（如「{B} 的第 {A} 个字符」）
		BlockType spec = BlockRegistry.get(node.type);
		if (spec != null && spec.hasTemplate()) {
			return renderTemplate(node, spec.template(), scopeId, depth);
		}

		// 变量/列表读取直接显示名字，Scratch 里也是这么画的
		String variable = node.fields.get("VAR");
		if (variable != null && "data.get".equals(node.type)) {
			return "(" + ParamLabels.variableName(variable, scopeId) + ")";
		}
		String list = node.fields.get("LIST");
		if (list != null && "list.contents".equals(node.type)) {
			return "(" + ParamLabels.variableName(list, scopeId) + ")";
		}

		StringBuilder builder = new StringBuilder(label(node.type));
		for (Map.Entry<String, String> entry : node.fields.entrySet()) {
			if ("SCOPE".equals(entry.getKey())) {
				continue;   // 内部作用域标记，不是用户参数
			}
			builder.append(' ').append(ParamLabels.label(entry.getKey()))
					.append('=').append(valueOf(entry.getKey(), entry.getValue(), scopeId));
		}
		for (Map.Entry<String, Slot> entry : node.inputs.entrySet()) {
			Slot slot = entry.getValue();
			builder.append(' ').append(ParamLabels.label(entry.getKey())).append('=');
			if (slot.hasBlock()) {
				builder.append(depth > 1 ? summary(slot.block, scopeId, depth - 1) : "…");
			} else if (slot.literal != null) {
				builder.append(valueOf(entry.getKey(), slot.literal, scopeId));
			} else {
				builder.append("?");
			}
		}
		return builder.toString();
	}

	/** 模板里的 {@code {参数名}} 占位。 */
	private static final Pattern HOLE = Pattern.compile("\\{([A-Z0-9_]+)}");

	/**
	 * 按模板渲染：把 {@code {NAME}} 换成该参数的实际值。
	 *
	 * <p>参数值优先取字段，其次取输入（字面量或嵌套积木）。</p>
	 */
	private static String renderTemplate(BlockNode node, String template, String scopeId, int depth) {
		Matcher matcher = HOLE.matcher(template);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			matcher.appendReplacement(out,
					Matcher.quoteReplacement(paramText(node, matcher.group(1), scopeId, depth)));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	private static String paramText(BlockNode node, String name, String scopeId, int depth) {
		String field = node.fields.get(name);
		if (field != null) {
			return valueOf(name, field, scopeId);
		}
		Slot slot = node.inputs.get(name);
		if (slot == null) {
			return "?";
		}
		if (slot.hasBlock()) {
			return depth > 1 ? summary(slot.block, scopeId, depth - 1) : "…";
		}
		return valueOf(name, slot.literal, scopeId);
	}

	private static String valueOf(String key, String value, String scopeId) {
		if ("VAR".equals(key) || "LIST".equals(key)) {
			return ParamLabels.variableName(value, scopeId);
		}
		return ParamLabels.value(key, value);
	}
}
