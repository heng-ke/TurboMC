package xyz.hegke.client.script.sb3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xyz.hegke.client.script.block.BlockRegistry;
import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.node.Slot;
import xyz.hegke.client.script.store.ScriptDefinition;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * sb3 → 编辑器积木树。
 *
 * <p>导入后每个顶层积木链变成一份 {@link ScriptDefinition}，供列表展开与只读查看。
 * 脚本的编写与修改由 TurboWarp 负责，这里只做「看得见」。</p>
 *
 * <p>只还原「积木」这一层：造型、声音、角色属性不进入编辑器（编辑器也不维护它们），
 * 原始 sb3 会另行备份，需要时仍可整体运行。</p>
 */
public final class Sb3Importer {
	/** 导入结果。{@code error} 为 {@code null} 表示成功。 */
	public record Result(List<ScriptDefinition> scripts, List<String> unsupported, String error) {
		public boolean ok() {
			return error == null;
		}
	}

	private Sb3Importer() {
	}

	/** 读取 sb3 压缩包并解析其中的 project.json。 */
	public static Result read(Path file) {
		try (ZipFile zip = new ZipFile(file.toFile())) {
			ZipEntry entry = zip.getEntry("project.json");
			if (entry == null) {
				return new Result(List.of(), List.of(), "sb3 里没有 project.json");
			}
			try (InputStreamReader reader = new InputStreamReader(
					zip.getInputStream(entry), StandardCharsets.UTF_8)) {
				return parse(JsonParser.parseReader(reader));
			}
		} catch (IOException e) {
			return new Result(List.of(), List.of(), "读取失败：" + e.getMessage());
		} catch (RuntimeException e) {
			return new Result(List.of(), List.of(), "解析失败：" + e.getMessage());
		}
	}

	/** 解析 project.json 的根对象。 */
	public static Result parse(JsonElement root) {
		// 判断「积木是否有编辑器形态」要查注册表，先确保已装载
		BlockRegistry.bootstrapOnce();
		if (root == null || !root.isJsonObject()) {
			return new Result(List.of(), List.of(), "project.json 不是对象");
		}
		JsonArray targets = root.getAsJsonObject().getAsJsonArray("targets");
		if (targets == null) {
			return new Result(List.of(), List.of(), "project.json 缺少 targets");
		}

		// 舞台变量对所有角色可见，先收集，供各角色判断作用域
		Map<String, String> stageVariables = new LinkedHashMap<>();
		Map<String, String> stageLists = new LinkedHashMap<>();
		for (JsonElement element : targets) {
			if (element.isJsonObject() && bool(element.getAsJsonObject(), "isStage")) {
				stageVariables.putAll(collectNames(element.getAsJsonObject(), "variables"));
				stageLists.putAll(collectNames(element.getAsJsonObject(), "lists"));
			}
		}

		List<ScriptDefinition> scripts = new ArrayList<>();
		List<String> unsupported = new ArrayList<>();

		for (JsonElement element : targets) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject target = element.getAsJsonObject();
			boolean stage = bool(target, "isStage");
			String targetName = string(target, "name", stage ? "Stage" : "Sprite");
			JsonObject blocks = target.has("blocks") && target.get("blocks").isJsonObject()
					? target.getAsJsonObject("blocks")
					: new JsonObject();

			// 舞台自己的变量就是全局变量；角色自己的算局部，舞台的算全局
			Scope scope = stage
					? new Scope(Map.of(), stageVariables, Map.of(), stageLists)
					: new Scope(collectNames(target, "variables"), stageVariables,
							collectNames(target, "lists"), stageLists);

			List<String> topLevel = new ArrayList<>();
			for (Map.Entry<String, JsonElement> entry : blocks.entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject block = entry.getValue().getAsJsonObject();
				if (bool(block, "topLevel") && !bool(block, "shadow")) {
					topLevel.add(entry.getKey());
				}
			}

			int index = 0;
			for (String id : topLevel) {
				BlockNode hat = convert(blocks, id, scope, unsupported, new HashSet<>());
				if (hat == null) {
					continue;
				}
				index++;
				String name = topLevel.size() == 1 ? targetName : targetName + "-" + index;
				ScriptDefinition definition = ScriptDefinition.create(name, hat);
				// 同一角色的多个脚本共享局部变量，正是 Scratch 的语义
				definition.scopeId = targetName;
				scripts.add(definition);
			}
		}

		if (scripts.isEmpty()) {
			return new Result(List.of(), unsupported, "没有找到可导入的积木链（工程可能是空的）");
		}
		return new Result(scripts, unsupported, null);
	}

	/** 一个角色的变量作用域：自己的变量算局部，舞台的算全局。 */
	private record Scope(
			Map<String, String> localVariables, Map<String, String> globalVariables,
			Map<String, String> localLists, Map<String, String> globalLists) {
		/** 变量名；{@code null} 表示找不到（工程数据损坏）。 */
		String variableName(String id) {
			return localVariables.getOrDefault(id, globalVariables.get(id));
		}

		boolean isGlobal(String id) {
			return !localVariables.containsKey(id) && globalVariables.containsKey(id);
		}

		String listName(String id) {
			return localLists.getOrDefault(id, globalLists.get(id));
		}
	}

	private static Map<String, String> collectNames(JsonObject target, String field) {
		Map<String, String> names = new LinkedHashMap<>();
		if (!target.has(field) || !target.get(field).isJsonObject()) {
			return names;
		}
		for (Map.Entry<String, JsonElement> entry : target.getAsJsonObject(field).entrySet()) {
			JsonElement value = entry.getValue();
			if (value.isJsonArray() && !value.getAsJsonArray().isEmpty()) {
				names.put(entry.getKey(), value.getAsJsonArray().get(0).getAsString());
			}
		}
		return names;
	}

	/**
	 * 把 sb3 的一个积木（含 next 链、分支、输入）还原成编辑器节点。
	 */
	private static BlockNode convert(JsonObject blocks, String id, Scope scope,
			List<String> unsupported, Set<String> visiting) {
		if (id == null || id.isEmpty() || !blocks.has(id)) {
			return null;
		}
		// 防御工程文件里的环，否则会栈溢出
		if (!visiting.add(id)) {
			return null;
		}
		JsonElement element = blocks.get(id);
		if (!element.isJsonObject()) {
			return null;
		}
		JsonObject block = element.getAsJsonObject();
		String opcode = string(block, "opcode", "");

		// 原型块只是定义的影子输入，不单独展示；定义块本身要留，否则函数体没有归属
		if (opcode.startsWith("procedures_prototype")) {
			return convert(blocks, nextId(block), scope, unsupported, visiting);
		}
		if (opcode.startsWith("procedures_definition")) {
			BlockNode define = new BlockNode("procedures.definition");
			define.fields.put("NAME", procedureCode(blocks, block));
			define.next = convert(blocks, nextId(block), scope, unsupported, visiting);
			visiting.remove(id);
			return define;
		}

		BlockNode node = new BlockNode(editorType(opcode, block));
		if (!BlockRegistry.exists(node.type)) {
			unsupported.add(opcode + " → " + node.type);
		}

		// 字段：变量/列表保存的是名字，其余取首元素
		if (block.has("fields") && block.get("fields").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : block.getAsJsonObject("fields").entrySet()) {
				String name = reverseFieldName(opcode, entry.getKey());
				String value = firstOf(entry.getValue());
				if (value == null) {
					continue;
				}
				node.fields.put(name, value);
				// 变量/列表字段的第二项是 ID，据此还原「仅适用于当前角色 / 所有角色」
				if ("VAR".equals(name) || "LIST".equals(name)) {
					String varId = secondOf(entry.getValue());
					if (varId != null) {
						node.fields.put("SCOPE", scope.isGlobal(varId) ? "global" : "local");
					}
				}
			}
		}

		// 输入：字面量、变量引用或嵌套积木
		if (block.has("inputs") && block.get("inputs").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : block.getAsJsonObject("inputs").entrySet()) {
				String scratchName = entry.getKey();
				String internal = reverseInputName(opcode, scratchName);
				Slot slot = readSlot(blocks, entry.getValue(), scope, unsupported, visiting);
				if (slot == null) {
					continue;
				}
				if ("SUBSTACK".equals(scratchName) || "SUBSTACK2".equals(scratchName)) {
					// 分支不是输入槽，改放 branches
					if (slot.hasBlock()) {
						node.branches.put(scratchName, slot.block);
					}
					continue;
				}
				// sb3 用秒，编辑器用 tick
				if ("TICKS".equals(internal) && !slot.hasBlock()) {
					slot = Slot.literal(scaleToTicks(slot.literal));
				}
				node.inputs.put(internal, slot);
			}
		}

		node.next = convert(blocks, nextId(block), scope, unsupported, visiting);
		visiting.remove(id);
		return node;
	}

	private static Slot readSlot(JsonObject blocks, JsonElement value, Scope scope,
			List<String> unsupported, Set<String> visiting) {
		if (value == null || !value.isJsonArray()) {
			return null;
		}
		JsonArray array = value.getAsJsonArray();
		if (array.isEmpty()) {
			return null;
		}
		// 形态一：[1, [10, "文本"]] —— 纯字面量（[类型码, 值] 都写在内层）
		// 形态二：[3, [12, "i", "id"], [10, "w"]] —— 变量引用 + shadow 字面量
		// 形态三：[3, "blockId", [10, ""]] —— 嵌套积木（带影子）
		// 形态四：[2, "blockId"] —— 嵌套积木（无影子），分支也用这种
		JsonElement second = array.size() > 1 ? array.get(1) : null;
		if (second != null && second.isJsonPrimitive() && second.getAsJsonPrimitive().isString()) {
			String blockId = second.getAsString();
			if (blocks.has(blockId)) {
				BlockNode child = convert(blocks, blockId, scope, unsupported, visiting);
				return child == null ? null : Slot.of(child);
			}
			return Slot.literal("");
		}
		Slot reference = variableSlot(second, scope);
		if (reference != null) {
			return reference;
		}
		String literal = literalValue(second);
		if (literal == null && array.size() > 2) {
			literal = literalValue(array.get(2));
		}
		return literal == null ? null : Slot.literal(literal);
	}

	/**
	 * sb3 里变量/列表作为输入时是 {@code [12, 名字, ID]}（列表为 13），
	 * 必须还原成「读取变量」积木，否则编辑器里会把变量名当成一段文字。
	 */
	private static Slot variableSlot(JsonElement element, Scope scope) {
		if (element == null || !element.isJsonArray()) {
			return null;
		}
		JsonArray array = element.getAsJsonArray();
		if (array.size() < 2 || !array.get(0).isJsonPrimitive()) {
			return null;
		}
		int kind;
		try {
			kind = array.get(0).getAsInt();
		} catch (RuntimeException e) {
			return null;
		}
		if (kind != 12 && kind != 13) {
			return null;
		}
		String id = array.size() > 2 && array.get(2).isJsonPrimitive() ? array.get(2).getAsString() : null;
		String name = id == null ? null : (kind == 12 ? scope.variableName(id) : scope.listName(id));
		if (name == null) {
			name = array.get(1).getAsString();
		}
		if (kind == 13) {
			// 列表整体作为值：编辑器用「列表内容」读取（LIST 是文本参数，不是字段）
			BlockNode contents = BlockNode.of("list.contents").input("LIST", name);
			return Slot.of(contents);
		}
		BlockNode get = BlockNode.of("data.get")
				.field("VAR", name)
				.field("SCOPE", id != null && scope.isGlobal(id) ? "global" : "local");
		return Slot.of(get);
	}

	/** opcode → 编辑器积木类型，处理少数一对多的情形。 */
	private static String editorType(String opcode, JsonObject block) {
		if ("control_stop".equals(opcode)) {
			String option = fieldValue(block, "STOP_OPTION");
			return "all".equals(option) ? "control.stop_all" : "control.stop";
		}
		String mapped = Sb3Mapping.typeOf(opcode);
		return mapped != null ? mapped : opcode;
	}

	/** 复用 {@link Sb3Mapping} 的字段映射，避免两处规则漂移。 */
	private static String reverseFieldName(String opcode, String scratchName) {
		return Sb3Mapping.fieldName(opcode, scratchName);
	}

	/**
	 * 复用 {@link Sb3Mapping} 的输入名映射；自定义积木的 {@code arg0..argN} 额外还原成
	 * 编辑器习惯的 {@code P1..Pn}。
	 */
	private static String reverseInputName(String opcode, String scratchName) {
		if (scratchName.startsWith("arg")) {
			return "P" + (parseIndex(scratchName) + 1);
		}
		return Sb3Mapping.inputName(opcode, scratchName);
	}

	private static int parseIndex(String arg) {
		try {
			return Integer.parseInt(arg.substring(3));
		} catch (RuntimeException e) {
			return 0;
		}
	}

	/** 秒 → tick：Scratch 的等待按秒计，界面显示按 tick。 */
	private static String scaleToTicks(String seconds) {
		if (seconds == null || seconds.isBlank()) {
			return "0";
		}
		try {
			double value = Double.parseDouble(seconds.trim()) * 20d;
			return Math.abs(value - Math.rint(value)) < 1e-9
					? String.valueOf((long) Math.rint(value))
					: String.valueOf(value);
		} catch (RuntimeException e) {
			return seconds;
		}
	}

	/**
	 * 取 sb3 输入里的字面量值。
	 *
	 * <p>内层是 {@code [类型码, 值]}（10=文本、4=数字、11=广播…），所以值在第二项；
	 * 直接取第一项会拿到类型码，表现为所有文本参数都变成 10/12 这类数字。</p>
	 */
	private static String literalValue(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			for (int i = 1; i < array.size(); i++) {
				JsonElement value = array.get(i);
				if (value.isJsonPrimitive()) {
					return value.getAsString();
				}
			}
			return null;
		}
		return element.isJsonPrimitive() ? element.getAsString() : null;
	}

	/**
	 * 自定义积木的名字：定义块本身不带它，要顺着 {@code custom_block} 输入找到原型块，
	 * 再读 {@code mutation.proccode}（形如 {@code addscore %s}）。
	 */
	private static String procedureCode(JsonObject blocks, JsonObject definition) {
		if (!definition.has("inputs") || !definition.get("inputs").isJsonObject()) {
			return "";
		}
		for (Map.Entry<String, JsonElement> entry : definition.getAsJsonObject("inputs").entrySet()) {
			JsonElement value = entry.getValue();
			if (!value.isJsonArray()) {
				continue;
			}
			JsonArray array = value.getAsJsonArray();
			if (array.size() < 2 || !array.get(1).isJsonPrimitive()) {
				continue;
			}
			String protoId = array.get(1).getAsString();
			if (!blocks.has(protoId) || !blocks.get(protoId).isJsonObject()) {
				continue;
			}
			JsonObject proto = blocks.getAsJsonObject(protoId);
			if (proto.has("mutation") && proto.getAsJsonObject("mutation").has("proccode")) {
				return proto.getAsJsonObject("mutation").get("proccode").getAsString();
			}
		}
		return "";
	}

	private static String nextId(JsonObject block) {
		if (!block.has("next")) {
			return null;
		}
		JsonElement next = block.get("next");
		return next.isJsonPrimitive() && next.getAsJsonPrimitive().isString() ? next.getAsString() : null;
	}

	private static String fieldValue(JsonObject block, String name) {
		if (!block.has("fields") || !block.getAsJsonObject("fields").has(name)) {
			return null;
		}
		return firstOf(block.getAsJsonObject("fields").get(name));
	}

	/** 取 {@code [值, id]} 或直接的值。 */
	private static String firstOf(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			if (array.isEmpty()) {
				return null;
			}
			JsonElement first = array.get(0);
			return first.isJsonPrimitive() ? first.getAsString() : null;
		}
		return element.isJsonPrimitive() ? element.getAsString() : null;
	}

	/** 取 {@code [值, id]} 的第二项（变量 ID），没有则返回 {@code null}。 */
	private static String secondOf(JsonElement element) {
		if (element == null || !element.isJsonArray()) {
			return null;
		}
		JsonArray array = element.getAsJsonArray();
		if (array.size() < 2 || !array.get(1).isJsonPrimitive()) {
			return null;
		}
		return array.get(1).getAsString();
	}

	private static String string(JsonObject object, String name, String fallback) {
		if (!object.has(name) || object.get(name).isJsonNull()) {
			return fallback;
		}
		return object.get(name).getAsString();
	}

	private static boolean bool(JsonObject object, String name) {
		return object.has(name) && !object.get(name).isJsonNull() && object.get(name).getAsBoolean();
	}

	/** 供测试：把解析结果写成可读摘要。 */
	public static String describe(List<ScriptDefinition> scripts) {
		StringBuilder builder = new StringBuilder();
		for (ScriptDefinition definition : scripts) {
			builder.append(definition.name).append(" (scope=").append(definition.scopeId).append(")\n");
			describe(definition.hat, builder, 1);
		}
		return builder.toString();
	}

	private static void describe(BlockNode node, StringBuilder builder, int depth) {
		BlockNode current = node;
		while (current != null) {
			builder.append("  ".repeat(depth)).append(current.type);
			if (!current.fields.isEmpty()) {
				builder.append(" fields=").append(current.fields);
			}
			if (!current.inputs.isEmpty()) {
				Map<String, String> simple = new LinkedHashMap<>();
				current.inputs.forEach((key, slot) -> simple.put(key,
						slot.hasBlock() ? "<" + slot.block.type + ">" : String.valueOf(slot.literal)));
				builder.append(" inputs=").append(simple);
			}
			builder.append('\n');
			current.branches.forEach((name, child) -> {
				builder.append("  ".repeat(depth + 1)).append('[').append(name).append("]\n");
				describe(child, builder, depth + 2);
			});
			current = current.next;
		}
	}

	/** 供测试：从文件路径读并解析。 */
	public static Result readFile(Path file) {
		if (!Files.isRegularFile(file)) {
			return new Result(List.of(), List.of(), "文件不存在：" + file);
		}
		return read(file);
	}
}
