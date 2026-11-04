package xyz.hegke.client.script.sb3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.hegke.client.script.block.BlockParam;
import xyz.hegke.client.script.block.BlockRegistry;
import xyz.hegke.client.script.block.BlockType;
import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.node.Slot;
import xyz.hegke.client.script.runtime.Values;
import xyz.hegke.client.script.store.FunctionDefinition;
import xyz.hegke.client.script.store.ScriptDefinition;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Scratch 3 项目（.sb3）解析器：把 {@code project.json} 中的积木图还原成 {@link BlockNode} 树。
 *
 * <p>sb3 里的 blocks 是 {@code id → block} 的扁平字典，通过 {@code next} 与 {@code inputs}
 * 互相引用；这里从每个帽子积木出发做一遍深度优先还原。</p>
 *
 * <p>三条关键约定：</p>
 * <ol>
 *   <li><b>不断链</b>：无法映射的语句积木用 {@code control.nop} 占位并继续链接，
 *       避免"一个不支持的积木吞掉后面整段代码"；表达式位置的占位积木则退化为空值。</li>
 *   <li><b>变量按 ID 定位</b>：Scratch 的变量字段是 {@code [名字, ID]}，同一 ID 才视为同一个变量。
 *       内部名按作用域加前缀（{@code score} 为 Stage 全局，{@code 猪/score} 为角色局部）。</li>
 *   <li><b>内联原语</b>：输入槽里的变量/列表会被序列化成 {@code [12, 名字, ID]} / {@code [13, ...]}，
 *       需要还原成积木而不是字面量。</li>
 * </ol>
 */
public final class Sb3Parser {
	private static final Logger LOG = LoggerFactory.getLogger("hk-script/sb3");
	private static final int MAX_DEPTH = 200;
	/**
	 * 单条积木链的最大长度。与嵌套深度分开计数：链是线性的，
	 * 上百块很常见，不能和嵌套共用同一个上限。
	 */
	private static final int MAX_CHAIN = 10_000;
	/** project.json 解压后的字节上限，防止压缩炸弹把内存打爆。 */
	private static final int MAX_PROJECT_BYTES = 32 * 1024 * 1024;
	/** 自定义积木最多支持的参数个数，与积木面板的参数槽数量一致。 */
	private static final int MAX_CALL_PARAMS = 5;

	private Sb3Parser() {
	}

	/**
	 * 解析结果：脚本 + 被跳过内容的统计，供导入界面汇总展示。
	 */
	public record ParseResult(List<ScriptDefinition> scripts, List<String> skippedOpcodes,
							  List<String> skippedHats, int skippedBlocks,
							  List<String> droppedExpressions) {
		public String summary() {
			StringBuilder builder = new StringBuilder("导入 ").append(scripts.size()).append(" 个脚本");
			if (skippedBlocks > 0) {
				builder.append("，跳过 ").append(skippedBlocks).append(" 块（")
						.append(String.join(", ", skippedOpcodes)).append("）");
			}
			if (!skippedHats.isEmpty()) {
				builder.append("，未支持的事件（").append(String.join(", ", skippedHats)).append("）");
			}
			if (!droppedExpressions.isEmpty()) {
				builder.append("，丢弃 ").append(droppedExpressions.size())
						.append(" 处表达式参数（该位置只接受常量：")
						.append(String.join(", ", droppedExpressions)).append("）");
			}
			return builder.toString();
		}
	}

	/**
	 * 变量在引擎里的落位：内部名 + 作用域（{@code global} 或 {@code target}）。
	 */
	private record VarRef(String name, String scope) {
	}

	/**
	 * 单个 target 的解析上下文。
	 */
	private static final class Ctx {
		final JsonObject blocks;
		final String targetName;
		final boolean stage;
		final Map<String, VarRef> variables = new HashMap<>();
		final Map<String, VarRef> lists = new HashMap<>();
		final Set<String> skippedOpcodes = new LinkedHashSet<>();
		final Set<String> skippedHats = new LinkedHashSet<>();
		/** 「纯字段位置上出现了表达式」的记录，导入报告会列出。 */
		final Set<String> droppedExpressions = new LinkedHashSet<>();
		/** 当前正在解析的函数体的「参数名 → 序号」，用于 argument_reporter。 */
		Map<String, Integer> paramIndex = Map.of();
		int skippedBlocks;

		Ctx(JsonObject blocks, String targetName, boolean stage) {
			this.blocks = blocks;
			this.targetName = targetName;
			this.stage = stage;
		}

		VarRef variable(String id, String fallbackName) {
			VarRef ref = id == null ? null : variables.get(id);
			if (ref != null) {
				return ref;
			}
			// 找不到 ID（旧格式或字段缺 ID）时退回按名字处理，与历史行为一致
			return new VarRef(fallbackName, "global");
		}

		VarRef list(String id, String fallbackName) {
			VarRef ref = id == null ? null : lists.get(id);
			if (ref != null) {
				return ref;
			}
			return new VarRef(fallbackName, "global");
		}
	}

	/**
	 * 自定义积木的元数据（尚未解析函数体）。
	 */
	private record FunctionMeta(String proccode, String bodyId, Map<String, Integer> paramIndex) {
	}

	public static ParseResult parse(byte[] data) throws IOException {
		String json = readProjectJson(data);
		if (json == null) {
			throw new IOException("sb3 中缺少 project.json");
		}
		return parseProject(JsonParser.parseString(json).getAsJsonObject());
	}

	/**
	 * 只要脚本列表的便捷入口。
	 */
	public static List<ScriptDefinition> parseScripts(byte[] data) throws IOException {
		return parse(data).scripts();
	}

	private static String readProjectJson(byte[] data) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (!entry.getName().endsWith("project.json")) {
					continue;
				}
				return new String(readLimited(zip, MAX_PROJECT_BYTES), StandardCharsets.UTF_8);
			}
		}
		return null;
	}

	/**
	 * 限长读取：边读边计数，超过上限立刻中断。
	 *
	 * <p>不能先 {@code readAllBytes()} 再检查长度——那时内存已经吃完了。</p>
	 */
	private static byte[] readLimited(InputStream input, int limit) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int total = 0;
		int read;
		while ((read = input.read(buffer)) != -1) {
			total += read;
			if (total > limit) {
				throw new IOException("project.json 解压后超过 " + limit + " 字节，已拒绝导入");
			}
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	public static ParseResult parseProject(JsonObject project) {
		List<ScriptDefinition> scripts = new ArrayList<>();
		Set<String> skippedOpcodes = new LinkedHashSet<>();
		Set<String> droppedExpressions = new LinkedHashSet<>();
		Set<String> skippedHats = new LinkedHashSet<>();
		int[] skippedBlocks = {0};

		JsonArray targets = project.getAsJsonArray("targets");
		if (targets == null) {
			return new ParseResult(scripts, List.of(), List.of(), 0, List.of());
		}

		for (JsonElement element : targets) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject target = element.getAsJsonObject();
			JsonObject blocks = target.getAsJsonObject("blocks");
			if (blocks == null) {
				continue;
			}
			String targetName = string(target, "name");
			if (targetName == null || targetName.isBlank()) {
				targetName = "sprite";
			}
			boolean stage = target.has("isStage") && target.get("isStage").getAsBoolean();

			Ctx ctx = new Ctx(blocks, targetName, stage);
			registerScope(ctx, target);
			// 先扫描自定义积木的定义（只读 prototype，函数体稍后解析）
			List<FunctionMeta> functionMetas = scanFunctions(ctx);

			int scriptStart = scripts.size();
			int index = 0;
			for (Map.Entry<String, JsonElement> entry : blocks.entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject block = entry.getValue().getAsJsonObject();
				if (!block.has("topLevel") || !block.get("topLevel").getAsBoolean()) {
					continue;
				}
				String opcode = string(block, "opcode");
				if (!Sb3Mapping.isHat(opcode)) {
					if (opcode != null && isScriptRoot(opcode)) {
						ctx.skippedHats.add(opcode);
					}
					continue;
				}
				BlockNode hat = buildBlock(entry.getKey(), ctx, 0, 0);
				if (hat == null) {
					continue;
				}
				ScriptDefinition definition = ScriptDefinition.create(
						targetName + "#" + (++index), hat);
				definition.enabled = false;
				// 同一角色的所有脚本共享一个作用域，对应 Scratch 的「仅适用于当前角色」
				definition.scopeId = targetName;
				scripts.add(definition);
			}

			// 解析函数体（此时才有参数名 → 序号的映射）
			if (!functionMetas.isEmpty()) {
				List<FunctionDefinition> functions = new ArrayList<>();
				for (FunctionMeta meta : functionMetas) {
					ctx.paramIndex = meta.paramIndex();
					FunctionDefinition function = FunctionDefinition.create(meta.proccode());
					function.body = buildBlock(meta.bodyId(), ctx, 0, 0);
					functions.add(function);
				}
				ctx.paramIndex = Map.of();
				for (int i = scriptStart; i < scripts.size(); i++) {
					scripts.get(i).functions = functions;
				}
			}

			skippedOpcodes.addAll(ctx.skippedOpcodes);
			skippedHats.addAll(ctx.skippedHats);
			droppedExpressions.addAll(ctx.droppedExpressions);
			skippedBlocks[0] += ctx.skippedBlocks;
		}

		ParseResult result = new ParseResult(scripts, List.copyOf(skippedOpcodes),
				List.copyOf(skippedHats), skippedBlocks[0], List.copyOf(droppedExpressions));
		LOG.info("[sb3] {}", result.summary());
		return result;
	}

	/**
	 * 是否是"脚本入口"但它不是我们支持的帽子：用于报告里列出"哪些事件没能导入"。
	 */
	private static boolean isScriptRoot(String opcode) {
		return opcode.startsWith("event_") || opcode.startsWith("control_start_as_clone");
	}

	/**
	 * 扫描 {@code procedures_definition}，读取 proccode 与参数表；函数体稍后解析。
	 */
	private static List<FunctionMeta> scanFunctions(Ctx ctx) {
		List<FunctionMeta> result = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : ctx.blocks.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject definition = entry.getValue().getAsJsonObject();
			if (!definition.has("topLevel") || !definition.get("topLevel").getAsBoolean()) {
				continue;
			}
			if (!"procedures_definition".equals(string(definition, "opcode"))) {
				continue;
			}
			String prototypeId = firstInputId(definition, "custom_block");
			JsonElement prototypeElement = prototypeId == null ? null : ctx.blocks.get(prototypeId);
			if (prototypeElement == null || !prototypeElement.isJsonObject()) {
				continue;
			}
			JsonObject mutation = prototypeElement.getAsJsonObject().getAsJsonObject("mutation");
			if (mutation == null) {
				continue;
			}
			String proccode = string(mutation, "proccode");
			if (proccode == null || proccode.isBlank()) {
				continue;
			}
			List<String> names = jsonStringArray(mutation.get("argumentnames"));
			Map<String, Integer> paramIndex = new LinkedHashMap<>();
			for (int i = 0; i < names.size(); i++) {
				paramIndex.putIfAbsent(names.get(i), i);
			}
			result.add(new FunctionMeta(proccode, string(definition, "next"), Map.copyOf(paramIndex)));
		}
		return result;
	}

	/**
	 * 取输入槽指向的第一个积木 id（{@code [1, "blockId"]} 或 {@code [3, "blockId", ...]}）。
	 */
	private static String firstInputId(JsonObject block, String name) {
		JsonObject inputs = block.getAsJsonObject("inputs");
		if (inputs == null) {
			return null;
		}
		JsonElement element = inputs.get(name);
		if (element == null || !element.isJsonArray()) {
			return null;
		}
		JsonArray array = element.getAsJsonArray();
		if (array.size() < 2) {
			return null;
		}
		JsonElement second = array.get(1);
		return second.isJsonPrimitive() ? second.getAsString() : null;
	}

	/**
	 * mutation 里的数组字段可能是 JSON 数组，也可能是被字符串化过的 JSON 数组。
	 */
	private static List<String> jsonStringArray(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		JsonElement array = element;
		if (!element.isJsonArray()) {
			try {
				array = JsonParser.parseString(element.getAsString());
			} catch (RuntimeException e) {
				return List.of();
			}
		}
		if (!array.isJsonArray()) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		for (JsonElement item : array.getAsJsonArray()) {
			if (!item.isJsonNull()) {
				result.add(item.getAsString());
			}
		}
		return result;
	}

	/**
	 * 读取 target 的 variables / lists 字典，建立 ID → 内部名 的映射。
	 */
	private static void registerScope(Ctx ctx, JsonObject target) {
		readScopeDict(target.getAsJsonObject("variables"))
				.forEach((id, name) -> ctx.variables.put(id, ref(ctx, name)));
		readScopeDict(target.getAsJsonObject("lists"))
				.forEach((id, name) -> ctx.lists.put(id, ref(ctx, name)));
	}

	private static Map<String, String> readScopeDict(JsonObject dict) {
		Map<String, String> result = new LinkedHashMap<>();
		if (dict == null) {
			return result;
		}
		for (Map.Entry<String, JsonElement> entry : dict.entrySet()) {
			if (!entry.getValue().isJsonArray()) {
				continue;
			}
			JsonArray array = entry.getValue().getAsJsonArray();
			if (array.isEmpty() || array.get(0).isJsonNull()) {
				continue;
			}
			result.put(entry.getKey(), array.get(0).getAsString());
		}
		return result;
	}

	private static VarRef ref(Ctx ctx, String name) {
		if (ctx.stage || name == null || name.isBlank()) {
			return new VarRef(name == null ? "" : name, "global");
		}
		return new VarRef(ctx.targetName + "/" + name, "target");
	}

	private static BlockNode buildBlock(String id, Ctx ctx, int depth, int chain) {
		if (id == null || depth > MAX_DEPTH) {
			return null;
		}
		if (chain > MAX_CHAIN) {
			throw new IllegalStateException("积木链过长（超过 " + MAX_CHAIN + " 块），疑似数据损坏");
		}
		JsonElement element = ctx.blocks.get(id);
		if (element == null || !element.isJsonObject()) {
			return null;
		}
		JsonObject block = element.getAsJsonObject();

		String opcode = string(block, "opcode");

		// 函数参数读取：按当前函数体的参数表映射到 $pN（$p1 起算）
		if ("argument_reporter_string_number".equals(opcode)
				|| "argument_reporter_boolean".equals(opcode)) {
			String name = rawField(block, "VALUE");
			Integer index = name == null ? null : ctx.paramIndex.get(name);
			if (index == null) {
				ctx.skippedBlocks++;
				ctx.skippedOpcodes.add(opcode);
			}
			BlockNode get = BlockNode.of("data.get");
			get.fields.put("VAR", "$p" + ((index == null ? 0 : index) + 1));
			get.fields.put("SCOPE", "local");
			return get;
		}

		String type = Sb3Mapping.typeOf(opcode);
		if (type == null) {
			// 不断链：占位并继续解析 next，后续积木照常导入
			ctx.skippedBlocks++;
			ctx.skippedOpcodes.add(opcode == null ? "(unknown)" : opcode);
			BlockNode placeholder = new BlockNode("control.nop");
			placeholder.fields.put("SKIPPED", opcode == null ? "(unknown)" : opcode);
			String next = string(block, "next");
			if (next != null) {
				placeholder.next = buildBlock(next, ctx, depth, chain + 1);
			}
			return placeholder;
		}

		// Scratch 的「停止」是单条积木带下拉选项，内部拆成「停止脚本」与「停止全部」两个积木
		if ("control_stop".equals(opcode)) {
			String option = rawField(block, "STOP_OPTION");
			if ("all".equals(option)) {
				type = "control.stop_all";
			} else if (option != null && !"this script".equals(option)) {
				LOG.warn("[sb3] 「停止 {}」暂不支持，已按「停止当前脚本」处理", option);
			}
		}

		// 自定义积木调用：参数按 argumentids 的顺序落到 P1..Pn
		if ("procedures_call".equals(opcode)) {
			return buildProcedureCall(block, ctx, depth, chain);
		}

		BlockNode node = new BlockNode(type);

		JsonObject fields = block.getAsJsonObject("fields");
		if (fields != null) {
			for (Map.Entry<String, JsonElement> entry : fields.entrySet()) {
				JsonElement value = entry.getValue();
				if (!value.isJsonArray()) {
					continue;
				}
				JsonArray array = value.getAsJsonArray();
				if (array.isEmpty()) {
					continue;
				}
				// 下拉选项已被转换成不同的积木类型，不再作为参数保留
				if ("control_stop".equals(opcode) && "STOP_OPTION".equals(entry.getKey())) {
					continue;
				}
				applyField(node, ctx, opcode, entry.getKey(), array);
			}
		}

		JsonObject inputs = block.getAsJsonObject("inputs");
		if (inputs != null) {
			for (Map.Entry<String, JsonElement> entry : inputs.entrySet()) {
				if (!entry.getValue().isJsonArray()) {
					continue;
				}
				applyInput(node, ctx, opcode, entry.getKey(), entry.getValue().getAsJsonArray(), depth, chain);
			}
		}

		String next = string(block, "next");
		if (next != null) {
			node.next = buildBlock(next, ctx, depth, chain + 1);
		}

		// 「将变量增加 n」在 Scratch 里是单条积木，内部需要展开成 读取 → 相加 → 写回
		if ("data_changevariableby".equals(opcode)) {
			rewriteChangeBy(node);
		}
		// Scratch 的「等待 n 秒」与「说 n 秒」都以秒为单位，内部以游戏 tick 计
		if ("control_wait".equals(opcode) || "looks_sayforsecs".equals(opcode)
				|| "looks_thinkforsecs".equals(opcode)) {
			rewriteWait(node);
		}
		return node;
	}

	/**
	 * 把 {@code procedures_call} 还原为 {@code func.call}：函数名取 proccode，
	 * 实参按 argumentids 的顺序写入 P1..Pn。
	 */
	private static BlockNode buildProcedureCall(JsonObject block, Ctx ctx, int depth, int chain) {
		JsonObject mutation = block.getAsJsonObject("mutation");
		String proccode = mutation == null ? "" : string(mutation, "proccode");
		List<String> argIds = mutation == null
				? List.of()
				: jsonStringArray(mutation.get("argumentids"));

		BlockNode node = new BlockNode("func.call");
		node.fields.put("NAME", proccode == null ? "" : proccode);
		int argc = Math.min(argIds.size(), MAX_CALL_PARAMS);
		node.fields.put("ARGC", String.valueOf(Math.max(1, argc)));
		if (argIds.size() > MAX_CALL_PARAMS) {
			ctx.skippedOpcodes.add("procedures_call(参数超过 " + MAX_CALL_PARAMS + " 个)");
		}

		JsonObject inputs = block.getAsJsonObject("inputs");
		for (int i = 0; i < argc; i++) {
			JsonElement input = inputs == null ? null : inputs.get(argIds.get(i));
			if (input != null && input.isJsonArray()) {
				applyInput(node, ctx, "procedures_call", "P" + (i + 1),
						input.getAsJsonArray(), depth, chain);
			}
		}

		String next = string(block, "next");
		if (next != null) {
			node.next = buildBlock(next, ctx, depth, chain + 1);
		}
		return node;
	}

	/**
	 * 字段落位。变量与列表字段的第二元素是 ID，必须按 ID 查作用域表，
	 * 否则不同角色的同名变量会被错误合并。
	 */
	private static void applyField(BlockNode node, Ctx ctx, String opcode, String key, JsonArray array) {
		String value = array.get(0).getAsString();
		String id = array.size() > 1 && !array.get(1).isJsonNull() ? array.get(1).getAsString() : null;

		if ("VARIABLE".equals(key)) {
			VarRef ref = ctx.variable(id, value);
			node.fields.put("VAR", ref.name());
			node.fields.put("SCOPE", ref.scope());
			return;
		}
		if ("LIST".equals(key)) {
			VarRef ref = ctx.list(id, value);
			node.fields.put("LIST", ref.name());
			return;
		}
		node.fields.put(Sb3Mapping.fieldName(opcode, key), value);
	}

	/**
	 * 把 {@code data_changevariableby} 展开为 {@code data.set(VAR, add(get(VAR), delta))}。
	 */
	private static void rewriteChangeBy(BlockNode node) {
		String variable = node.fields.getOrDefault("VAR", "x");
		String scope = node.fields.getOrDefault("SCOPE", "global");
		Slot delta = node.inputs.remove("VALUE");

		BlockNode add = BlockNode.of("operator.add");
		add.inputs.put("A", Slot.of(BlockNode.of("data.get")
				.field("VAR", variable).field("SCOPE", scope)));
		add.inputs.put("B", delta != null ? delta : Slot.literal("1"));

		node.inputs.put("VALUE", Slot.of(add));
	}

	/**
	 * 秒 → tick：字面量直接乘 20，表达式则包一层乘法积木。
	 */
	private static void rewriteWait(BlockNode node) {
		Slot duration = node.inputs.get("TICKS");
		if (duration == null) {
			return;
		}
		if (duration.hasBlock()) {
			BlockNode multiply = BlockNode.of("operator.multiply");
			multiply.inputs.put("A", duration);
			multiply.inputs.put("B", Slot.literal("20"));
			node.inputs.put("TICKS", Slot.of(multiply));
			return;
		}
		double seconds = Values.asNumber(Values.parseLiteral(duration.literal));
		node.inputs.put("TICKS", Slot.literal(String.valueOf(seconds * 20d)));
	}

	private static void applyInput(BlockNode node, Ctx ctx, String opcode, String scratchName,
								   JsonArray input, int depth, int chain) {
		if (input.isEmpty()) {
			return;
		}
		String name = Sb3Mapping.inputName(opcode, scratchName);
		int kind = input.get(0).getAsInt();

		switch (kind) {
			case 1 -> {
				JsonElement second = input.get(1);
				if (second.isJsonArray()) {
					putLiteral(node, name, shadowValue(second.getAsJsonArray(), ctx));
				} else if (second.isJsonPrimitive()) {
					Slot slot = resolveSlot(ctx, second.getAsString(), depth, chain);
					if (slot != null) {
						putSlot(node, name, slot, ctx);
					}
				}
			}
			case 2 -> {
				if (input.size() > 1 && input.get(1).isJsonPrimitive()) {
					Slot slot = resolveSlot(ctx, input.get(1).getAsString(), depth, chain);
					if (slot != null) {
						putSlot(node, name, slot, ctx);
					}
				}
			}
			default -> {
				JsonElement second = input.get(1);
				JsonElement third = input.size() > 2 ? input.get(2) : null;

				// 标准格式是 [3, shadow, blockId]，但有的导出工具写成 [3, blockId, shadow]。
				// 块 id 只可能出现在这两个位置之一，所以两处都找：
				//   - 第 2 位是字符串 → 反向格式
				//   - 第 3 位是字符串 → 标准格式（修复前这里被完全忽略，标准脚本会丢块）
				JsonElement blockId = null;
				if (second.isJsonPrimitive() && second.getAsJsonPrimitive().isString()) {
					blockId = second;
				} else if (third != null && third.isJsonPrimitive()
						&& third.getAsJsonPrimitive().isString()) {
					blockId = third;
				}
				if (blockId != null) {
					Slot slot = resolveSlot(ctx, blockId.getAsString(), depth, chain);
					if (slot != null) {
						putSlot(node, name, slot, ctx);
						return;
					}
				}
				// 没有块，或块无法识别：退回字面量
				if (second.isJsonArray()) {
					putLiteral(node, name, shadowValue(second.getAsJsonArray(), ctx));
				} else if (third != null && third.isJsonArray()) {
					putLiteral(node, name, shadowValue(third.getAsJsonArray(), ctx));
				}
			}
		}
	}

	/**
	 * 解析输入槽指向的内容：下拉菜单取字面量，其余按积木递归解析。
	 *
	 * <p>表达式位置无法用占位积木顶上（占位积木是语句），因此遇到占位返回 null，
	 * 由调用方回退到 shadow 默认值。</p>
	 */
	private static Slot resolveSlot(Ctx ctx, String id, int depth, int chain) {
		Slot menu = resolveMenu(ctx.blocks, id);
		if (menu != null) {
			return menu;
		}
		BlockNode block = buildBlock(id, ctx, depth + 1, chain);
		if (block == null || "control.nop".equals(block.type)) {
			return null;
		}
		return Slot.of(block);
	}

	/**
	 * 扩展定义的菜单参数会被序列化成一个 {@code hkscript_menu_*} 影子积木，取其字段值即可。
	 */
	private static Slot resolveMenu(JsonObject blocks, String id) {
		JsonElement element = blocks.get(id);
		if (element == null || !element.isJsonObject()) {
			return null;
		}
		JsonObject block = element.getAsJsonObject();
		String opcode = string(block, "opcode");
		if (opcode == null || !opcode.startsWith("hkscript_menu_")) {
			return null;
		}
		JsonObject fields = block.getAsJsonObject("fields");
		if (fields == null) {
			return null;
		}
		for (Map.Entry<String, JsonElement> entry : fields.entrySet()) {
			JsonElement value = entry.getValue();
			if (value.isJsonArray() && !value.getAsJsonArray().isEmpty()) {
				return Slot.literal(value.getAsJsonArray().get(0).getAsString());
			}
		}
		return null;
	}

	private static void putSlot(BlockNode node, String name, Slot slot, Ctx ctx) {
		if ("SUBSTACK".equals(name) || "SUBSTACK2".equals(name)) {
			if (slot.hasBlock()) {
				node.branches.put(name, slot.block);
			}
			return;
		}
		BlockParam param = paramOf(node.type, name);
		if (param != null && param.field()) {
			if (!slot.hasBlock()) {
				node.fields.put(name, slot.literal == null ? "" : slot.literal);
				return;
			}
			if (!param.acceptsReporter()) {
				// 纯字段位置放了积木：Scratch 允许多种写法，我们无法求值，只能丢弃并记录
				ctx.droppedExpressions.add(node.type + "." + name);
				return;
			}
		}
		node.inputs.put(name, slot);
	}

	private static BlockParam paramOf(String blockType, String name) {
		BlockType type = BlockRegistry.get(blockType);
		return type == null ? null : type.param(name);
	}

	private static void putLiteral(BlockNode node, String name, Slot slot) {
		if ("SUBSTACK".equals(name)) {
			return;
		}
		// 内部声明为字段的参数，即使是字面量也要落进 fields，否则编译器读不到
		// （TurboWarp 扩展的文本参数导出为 input 字面量，不是 fields）
		BlockParam param = paramOf(node.type, name);
		if (param != null && param.field() && !slot.hasBlock()) {
			node.fields.put(name, slot.literal == null ? "" : slot.literal);
			return;
		}
		node.inputs.put(name, slot);
	}

	/**
	 * shadow 数组：{@code [4, "10"]} 数字 / {@code [12, 名字, ID]} 变量 /
	 * {@code [13, 名字, ID]} 列表 / {@code [11, 名字, ID]} 广播。
	 */
	private static Slot shadowValue(JsonArray shadow, Ctx ctx) {
		int type = shadow.get(0).getAsInt();
		String text = shadow.size() > 1 && !shadow.get(1).isJsonNull() ? shadow.get(1).getAsString() : "";
		String id = shadow.size() > 2 && !shadow.get(2).isJsonNull() ? shadow.get(2).getAsString() : null;

		if (type == 12) {
			VarRef ref = ctx.variable(id, text);
			return Slot.of(BlockNode.of("data.get")
					.field("VAR", ref.name()).field("SCOPE", ref.scope()));
		}
		if (type == 13) {
			VarRef ref = ctx.list(id, text);
			return Slot.of(BlockNode.of("list.contents").field("LIST", ref.name()));
		}
		return Slot.literal(text);
	}

	private static String string(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? null : element.getAsString();
	}

	/**
	 * 读取字段的原始值（{@code "NAME": ["value", null]} 的第一个元素）。
	 */
	private static String rawField(JsonObject block, String key) {
		JsonObject fields = block.getAsJsonObject("fields");
		if (fields == null) {
			return null;
		}
		JsonElement element = fields.get(key);
		if (element == null || !element.isJsonArray()) {
			return null;
		}
		JsonArray array = element.getAsJsonArray();
		return array.isEmpty() ? null : array.get(0).getAsString();
	}
}
