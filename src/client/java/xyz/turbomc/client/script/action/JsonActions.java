package xyz.turbomc.client.script.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import xyz.turbomc.client.script.runtime.ScriptContext;
import xyz.turbomc.client.script.runtime.Values;

/**
 * JSON 积木的 Java 实现（对齐 {@code skyhigh173JSON}，MIT，Skyhigh173 &amp; Mio）。
 *
 * <p>这批积木<b>不碰游戏状态</b>：输入是 JSON 文本，输出是文本/标量，全部在动作层内完成。
 * 因此它们能在纯 JVM 里跑，不需要启动客户端（{@code ./gradlew test} 可覆盖）。</p>
 *
 * <p>失败一律返回空串，不抛异常——异常会被桥吞成 {@code null}，脚本接着往下跑，
 * 比空串更难查（与 AI 积木同一条约定）。</p>
 */
public final class JsonActions {
	private JsonActions() {
	}

	public static void registerAll() {
		NativeRegistry.function("json.new", JsonActions::create);
		NativeRegistry.function("json.length", JsonActions::length);
		NativeRegistry.function("json.get", JsonActions::get);
		NativeRegistry.function("json.set", JsonActions::set);
		NativeRegistry.function("json.delete", JsonActions::delete);
		NativeRegistry.function("json.is", JsonActions::is);
		NativeRegistry.function("json.has_key", JsonActions::hasKey);
		NativeRegistry.function("json.get_path", JsonActions::getPath);
		NativeRegistry.function("json.set_path", JsonActions::setPath);
		NativeRegistry.function("json.minify", JsonActions::minify);
		NativeRegistry.function("json.err_msg", JsonActions::errMsg);
		NativeRegistry.function("json.get_all", JsonActions::getAll);
		NativeRegistry.function("json.array_push", JsonActions::arrayPush);
		NativeRegistry.function("json.array_set", JsonActions::arraySet);
		NativeRegistry.function("json.array_insert", JsonActions::arrayInsert);
		NativeRegistry.function("json.array_delete", JsonActions::arrayDelete);
		NativeRegistry.function("json.array_remove_all", JsonActions::arrayRemoveAll);
		NativeRegistry.function("json.array_item_num", JsonActions::arrayItemNum);
		NativeRegistry.function("json.array_fromto", JsonActions::arrayFromTo);
		NativeRegistry.function("json.array_concat", JsonActions::arrayConcat);
		NativeRegistry.function("json.array_map_field", JsonActions::arrayMapField);
		NativeRegistry.function("json.array_setlen", JsonActions::arraySetLen);
		NativeRegistry.function("json.array_create", JsonActions::arrayCreate);
		NativeRegistry.function("json.array_join", JsonActions::arrayJoin);
		NativeRegistry.function("json.array_sort", JsonActions::arraySort);
		NativeRegistry.function("json.array_textfilter", JsonActions::arrayTextFilter);
		NativeRegistry.function("json.array_analysis", JsonActions::arrayAnalysis);
	}

	/** {@code 新建 [json]}：菜单 Object / Array，等价于 {@code {}} 与 {@code []}。 */
	private static Object create(ScriptContext ctx, Object[] args) {
		String kind = Values.asString(args[0]);
		if ("Object".equals(kind)) {
			return "{}";
		}
		if ("Array".equals(kind)) {
			return "[]";
		}
		return "";
	}

	/**
	 * {@code [json] 的长度}：对象给键数、数组给元素数。
	 *
	 * <p>原扩展把它拆成「键数量」与「数组长度」两块，但两者是同一份实现
	 * （{@code Object.keys(x).length}），合并后对两种容器都成立。</p>
	 *
	 * <p>与原扩展的唯一偏差：解析失败时它返回一个<b>空格</b>（明显是笔误），这里返回空串。
	 * 空格会让「= 空」这类判断失效，属于原实现的 bug，不做兼容。</p>
	 */
	private static Object length(ScriptContext ctx, Object[] args) {
		JsonElement root = JsonText.parse(Values.asString(args[0]));
		if (root == null) {
			return "";
		}
		if (root.isJsonArray()) {
			return (double) root.getAsJsonArray().size();
		}
		if (root.isJsonObject()) {
			return (double) root.getAsJsonObject().size();
		}
		return "";
	}

	/**
	 * {@code [json] 中 [item] 的值}：对象按键、数组按位置（<b>1 基</b>，负数从末尾数）。
	 *
	 * <p>数组这里与「数组第 n 项」原本是 0 基与 1 基两套语义，合并时统一成 1 基——
	 * 同一批积木里出现两种下标基准，只会让脚本作者写错。</p>
	 */
	private static Object get(ScriptContext ctx, Object[] args) {
		JsonElement root = JsonText.parse(Values.asString(args[0]));
		String item = Values.asString(args[1]);
		if (root == null) {
			return "";
		}
		if (root.isJsonObject()) {
			JsonElement value = root.getAsJsonObject().get(item);
			return value == null ? "" : JsonText.toScratch(value);
		}
		if (root.isJsonArray()) {
			JsonArray array = root.getAsJsonArray();
			int index = JsonText.arrayIndex(item, array.size());
			if (index < 0) {
				return "";
			}
			return JsonText.toScratch(array.get(index));
		}
		return "";
	}

	/**
	 * {@code 把 [json] 中 [item] 设为 [value]}。
	 *
	 * <p>值先按原扩展的规则处理：看起来像 {@code [...]}/{@code {...}} 的文本会解析成结构，
	 * 否则当字符串写入。所以「设成一个 JSON 片段」和「设成一段文字」都靠这一块完成。</p>
	 */
	private static Object set(ScriptContext ctx, Object[] args) {
		JsonElement root = JsonText.parse(Values.asString(args[0]));
		String item = Values.asString(args[1]);
		JsonElement value = JsonText.toWriteValue(args[2]);
		if (root == null) {
			return "";
		}
		if (root.isJsonObject()) {
			JsonObject object = root.getAsJsonObject();
			object.add(item, value);
			return JsonText.stringify(object);
		}
		if (root.isJsonArray()) {
			JsonArray array = root.getAsJsonArray();
			int slot = writeSlot(item);
			// 非整数或小于 1 的位置在数组上没有意义，等价于没改
			if (slot >= 0) {
				JsonText.setArraySlot(array, slot, value);
			}
			return JsonText.stringify(array);
		}
		return "";
	}

	/**
	 * {@code 删除 [json] 中的 [item]}。
	 *
	 * <p>数组走 {@code delete arr[i]}：JS 会留下空洞，序列化后是 {@code null}。
	 * 所以删除后长度不变、该位置变 null——照搬，不改成「挤出」。</p>
	 */
	private static Object delete(ScriptContext ctx, Object[] args) {
		JsonElement root = JsonText.parse(Values.asString(args[0]));
		String item = Values.asString(args[1]);
		if (root == null) {
			return "";
		}
		if (root.isJsonObject()) {
			JsonObject object = root.getAsJsonObject();
			object.remove(item);
			return JsonText.stringify(object);
		}
		if (root.isJsonArray()) {
			JsonArray array = root.getAsJsonArray();
			int index = JsonText.arrayIndex(item, array.size());
			if (index >= 0) {
				array.set(index, JsonNull.INSTANCE);
			}
			return JsonText.stringify(array);
		}
		return "";
	}

	/**
	 * {@code [json] 是 [合法 JSON/对象/数组] 吗?}
	 *
	 * <p>三选一合并自原来的「是合法 JSON 吗」与「是 [对象/数组] 吗」两块：两者共用同一道门，
	 * 分开只让面板更长。{@code valid} 只要求能解析；{@code Object}/{@code Array} 还要求属于容器，
	 * 因此 {@code "1"}、{@code "true"} 一律为假——与原扩展一致。</p>
	 */
	private static Object is(ScriptContext ctx, Object[] args) {
		if (!(args[0] instanceof String text)) {
			return Boolean.FALSE;
		}
		String check = Values.asString(args[1]);
		if ("valid".equals(check)) {
			return JsonText.looksLikeContainer(text) && JsonText.parse(text) != null;
		}
		if (!JsonText.looksLikeContainer(text)) {
			return Boolean.FALSE;
		}
		JsonElement root = JsonText.parse(text);
		if (root == null) {
			return Boolean.FALSE;
		}
		boolean array = root.isJsonArray();
		if ("Object".equals(check)) {
			return !array;
		}
		if ("Array".equals(check)) {
			return array;
		}
		return Boolean.FALSE;
	}

	/**
	 * {@code [json] 包含键 [key] 吗?}
	 *
	 * <p>数组的 {@code length} 被显式排除：它是 JS 的原生属性，不是内容里的键。</p>
	 */
	private static Object hasKey(ScriptContext ctx, Object[] args) {
		JsonElement root = JsonText.parse(Values.asString(args[0]));
		if (root == null || root.isJsonPrimitive()) {
			return Boolean.FALSE;
		}
		String key = keyText(JsonText.toWriteValue(args[1]));
		if (root.isJsonArray()) {
			if ("length".equals(key)) {
				return Boolean.FALSE;
			}
			return JsonText.arrayIndex(key, root.getAsJsonArray().size()) >= 0;
		}
		return root.getAsJsonObject().has(key);
	}

	/**
	 * {@code [json] 中路径 [path] 的值}。
	 *
	 * <p>路径是 <b>JSON 数组文本</b>，如 {@code ["key","inner",1]}——不是点号表达式。
	 * 遇到数组时下标按 Scratch 规则（1 基、负数从末尾）。</p>
	 */
	private static Object getPath(ScriptContext ctx, Object[] args) {
		JsonElement current = JsonText.parse(Values.asString(args[0]));
		JsonElement path = JsonText.parse(Values.asString(args[1]));
		if (current == null || path == null || !path.isJsonArray()) {
			return "";
		}
		for (JsonElement segment : path.getAsJsonArray()) {
			if (current.isJsonObject()) {
				String key = keyText(segment);
				JsonObject object = current.getAsJsonObject();
				if (!object.has(key)) {
					return "";
				}
				current = object.get(key);
			} else if (current.isJsonArray()) {
				JsonArray array = current.getAsJsonArray();
				int index = JsonText.arrayIndex(JsonText.toScratch(segment), array.size());
				if (index < 0) {
					return "";
				}
				current = array.get(index);
			} else {
				return "";
			}
		}
		return JsonText.toScratch(current);
	}

	/**
	 * {@code 把 [json] 中路径 [path] 的值设为 [data]}。
	 *
	 * <p>只替换已存在的位置：中间层缺键直接失败（返回空串），与原扩展一致。
	 * 想新建键用「设为」那块积木。</p>
	 */
	private static Object setPath(ScriptContext ctx, Object[] args) {
		JsonElement root = JsonText.parse(Values.asString(args[0]));
		JsonElement path = JsonText.parse(Values.asString(args[1]));
		if (root == null || path == null || !path.isJsonArray()) {
			return "";
		}
		JsonArray segments = path.getAsJsonArray();
		if (segments.size() == 0) {
			return JsonText.stringify(root);
		}
		JsonElement data = JsonText.toWriteValue(args[2]);
		JsonElement current = root;
		for (int i = 0; i < segments.size(); i++) {
			JsonElement segment = segments.get(i);
			boolean last = i == segments.size() - 1;
			if (current.isJsonArray()) {
				JsonArray array = current.getAsJsonArray();
				int index = JsonText.arrayIndex(JsonText.toScratch(segment), array.size());
				if (index < 0) {
					return "";
				}
				if (last) {
					array.set(index, data);
				} else {
					current = array.get(index);
				}
			} else if (current.isJsonObject()) {
				String key = keyText(segment);
				JsonObject object = current.getAsJsonObject();
				if (last) {
					object.add(key, data);
				} else {
					if (!object.has(key)) {
						return "";
					}
					current = object.get(key);
				}
			} else {
				return "";
			}
		}
		return JsonText.stringify(root);
	}

	/** {@code 压缩 [json]}：解析后重新序列化，顺带去掉空白。 */
	private static Object minify(ScriptContext ctx, Object[] args) {
		JsonElement root = JsonText.parse(Values.asString(args[0]));
		return root == null ? "" : JsonText.stringify(root);
	}

	/** {@code [json] 的解析错误}：没问题给空串，有问题给原因与位置。 */
	private static Object errMsg(ScriptContext ctx, Object[] args) {
		return JsonText.errorMessage(Values.asString(args[0]));
	}

	/**
	 * {@code [json] 的全部 [Stype]}：键 / 值 / 键值对，返回的都是 JSON 数组文本。
	 *
	 * <p>数组进这一块时「键」是下标字符串 {@code "0"}、{@code "1"}……，与 JS
	 * {@code Object.keys} 的行为一致。</p>
	 */
	private static Object getAll(ScriptContext ctx, Object[] args) {
		JsonElement root = JsonText.parse(Values.asString(args[0]));
		if (root == null || root.isJsonPrimitive()) {
			return "";
		}
		String kind = Values.asString(args[1]);
		if (!"keys".equals(kind) && !"values".equals(kind) && !"datas".equals(kind)) {
			return "";
		}
		JsonArray out = new JsonArray();
		if (root.isJsonObject()) {
			for (var entry : root.getAsJsonObject().entrySet()) {
				addEntry(out, kind, entry.getKey(), entry.getValue());
			}
		} else {
			JsonArray array = root.getAsJsonArray();
			for (int i = 0; i < array.size(); i++) {
				addEntry(out, kind, Integer.toString(i), array.get(i));
			}
		}
		return JsonText.stringify(out);
	}

	private static void addEntry(JsonArray out, String kind, String key, JsonElement value) {
		JsonElement safe = value == null || value.isJsonNull() ? new JsonPrimitive("") : value;
		switch (kind) {
			case "keys" -> out.add(key);
			case "values" -> out.add(safe);
			default -> {
				JsonArray pair = new JsonArray();
				pair.add(key);
				pair.add(safe);
				out.add(pair);
			}
		}
	}

	/** {@code 向数组 [json] 添加 [item]}：追加到末尾。 */
	private static Object arrayPush(ScriptContext ctx, Object[] args) {
		JsonArray array = parseArray(Values.asString(args[0]));
		if (array == null) {
			return "";
		}
		array.add(JsonText.toWriteValue(args[1]));
		return JsonText.stringify(array);
	}

	/**
	 * {@code 把数组 [json] 的第 [pos] 项换成 [item]}：1 基。
	 *
	 * <p>超出长度会补 {@code null}（JS 对越界的下标赋值就是延长数组）；
	 * 非整数或负数下标在 JS 里只是挂了个属性、序列化时看不见，等价于没改。</p>
	 */
	private static Object arraySet(ScriptContext ctx, Object[] args) {
		JsonArray array = parseArray(Values.asString(args[0]));
		if (array == null) {
			return "";
		}
		double index = Values.asNumber(args[1]) - 1;
		JsonElement value = JsonText.toWriteValue(args[2]);
		if (index == Math.rint(index) && index >= 0) {
			JsonText.setArraySlot(array, (int) index, value);
		}
		return JsonText.stringify(array);
	}

	/** {@code 在数组 [json] 的第 [pos] 项插入 [item]}：1 基，等价 {@code splice(pos-1, 0, item)}。 */
	private static Object arrayInsert(ScriptContext ctx, Object[] args) {
		JsonArray array = parseArray(Values.asString(args[0]));
		if (array == null) {
			return "";
		}
		int at = jsSliceIndex(Values.asNumber(args[1]) - 1, array.size());
		insertAt(array, at, JsonText.toWriteValue(args[2]));
		return JsonText.stringify(array);
	}

	/** {@code 删除数组 [json] 的第 [item] 项}：1 基，等价 {@code splice(item-1, 1)}。 */
	private static Object arrayDelete(ScriptContext ctx, Object[] args) {
		JsonArray array = parseArray(Values.asString(args[0]));
		if (array == null) {
			return "";
		}
		int at = jsSliceIndex(Values.asNumber(args[1]) - 1, array.size());
		if (at < array.size()) {
			array.remove(at);
		}
		return JsonText.stringify(array);
	}

	/**
	 * {@code 删除数组 [json] 中所有 [item]}。
	 *
	 * <p>按 JS 严格相等比较：{@code 1} 与 {@code "1"} 不相等；对象/数组按引用比较，
	 * 永远不相等——所以「删除所有对象项」在原扩展里也是无效操作，这里保持一致。</p>
	 */
	private static Object arrayRemoveAll(ScriptContext ctx, Object[] args) {
		JsonArray array = parseArray(Values.asString(args[0]));
		if (array == null) {
			return "";
		}
		JsonElement target = JsonText.toWriteValue(args[1]);
		for (int i = array.size() - 1; i >= 0; i--) {
			if (jsStrictEquals(array.get(i), target)) {
				array.remove(i);
			}
		}
		return JsonText.stringify(array);
	}

	/** {@code [item] 在数组 [json] 中的项号}：1 基；找不到给 0。 */
	private static Object arrayItemNum(ScriptContext ctx, Object[] args) {
		JsonArray array = parseArray(Values.asString(args[1]));
		if (array == null) {
			return "";
		}
		JsonElement target = JsonText.toWriteValue(args[0]);
		for (int i = 0; i < array.size(); i++) {
			if (jsStrictEquals(array.get(i), target)) {
				return (double) (i + 1);
			}
		}
		return 0d;
	}

	/** {@code 数组 [json] 的第 [item] 到第 [item2] 项}：首尾都是 1 基，含两端。 */
	private static Object arrayFromTo(ScriptContext ctx, Object[] args) {
		JsonArray array = parseArray(Values.asString(args[0]));
		if (array == null) {
			return "";
		}
		int start = jsSliceIndex(Values.asNumber(args[1]) - 1, array.size());
		int end = jsSliceIndex(Values.asNumber(args[2]), array.size());
		if (end < start) {
			end = start;
		}
		JsonArray out = new JsonArray();
		for (int i = start; i < end; i++) {
			out.add(array.get(i));
		}
		return JsonText.stringify(out);
	}

	/**
	 * {@code 数组 [list] 的 [analysis]}：最小值 / 最大值 / 总和 / 平均值 / 中位数 / 众数 / 方差。
	 *
	 * <p>全部先过 {@code Cast.toNumber}，所以字符串元素也能参与统计。空数组的结果与 JS 一致：
	 * 平均值得 {@code NaN}（0/0）、最大值 {@code -Infinity}、众数 0。</p>
	 *
	 * <p>众数取<b>最早达到最高频次</b>的那个值（原实现只在 {@code count > best} 时更新），
	 * 所以并列时结果依赖元素顺序，不是"随便挑一个"。</p>
	 */
	private static Object arrayAnalysis(ScriptContext ctx, Object[] args) {
		JsonArray array = parseArray(Values.asString(args[0]));
		if (array == null) {
			return 0d;
		}
		String kind = Values.asString(args[1]);
		int length = array.size();
		double[] values = new double[length];
		for (int i = 0; i < length; i++) {
			values[i] = xyz.turbomc.client.script.runtime.ScratchCast
					.toNumber(JsonText.toScratch(array.get(i)));
		}
		switch (kind) {
			case "maximum": {
				double max = Double.NEGATIVE_INFINITY;
				for (double value : values) {
					if (value > max) {
						max = value;
					}
				}
				return max;
			}
			case "minimum": {
				double min = Double.POSITIVE_INFINITY;
				for (double value : values) {
					if (value < min) {
						min = value;
					}
				}
				return min;
			}
			case "sum":
				return sum(values);
			case "average":
				return sum(values) / length;
			case "median": {
				double[] sorted = values.clone();
				java.util.Arrays.sort(sorted);
				int middle = sorted.length / 2;
				if (sorted.length % 2 == 0) {
					return (sorted[middle - 1] + sorted[middle]) / 2;
				}
				return sorted[middle];
			}
			case "mode": {
				// JS Map 的键比较是 SameValueZero：NaN 与 NaN 算同一个键，
				// -0 与 0 也算同一个键——所以入表前先把 -0 归一
				java.util.HashMap<Double, Integer> counts = new java.util.HashMap<>();
				double mode = 0;
				int best = 0;
				for (double value : values) {
					Double key = value + 0d;
					int count = counts.getOrDefault(key, 0) + 1;
					counts.put(key, count);
					if (count > best) {
						best = count;
						mode = value;
					}
				}
				return mode;
			}
			case "variance": {
				double average = sum(values) / length;
				double total = 0;
				for (double value : values) {
					double delta = value - average;
					total += delta * delta;
				}
				return total / length;
			}
			default:
				return 0d;
		}
	}

	private static double sum(double[] values) {
		double total = 0;
		for (double value : values) {
			total += value;
		}
		return total;
	}

	private static JsonArray parseArray(String text) {
		JsonElement root = JsonText.parse(text);
		return root != null && root.isJsonArray() ? root.getAsJsonArray() : null;
	}

	/** {@code 拼接数组 [json] 和 [json2]}：两边都必须是数组。 */
	private static Object arrayConcat(ScriptContext ctx, Object[] args) {
		JsonArray left = parseArray(Values.asString(args[0]));
		JsonArray right = parseArray(Values.asString(args[1]));
		if (left == null || right == null) {
			return "";
		}
		JsonArray out = new JsonArray();
		for (JsonElement element : left) {
			out.add(element);
		}
		for (JsonElement element : right) {
			out.add(element);
		}
		return JsonText.stringify(out);
	}

	/**
	 * {@code 数组 [json] 中所有对象的 [key] 值}。
	 *
	 * <p>原文叫 filter，实际是「逐个对象抽同一字段」（map）。缺该字段或元素不是对象时补
	 * {@code null}，所以结果长度永远与原数组一致。</p>
	 */
	private static Object arrayMapField(ScriptContext ctx, Object[] args) {
		JsonArray array = parseArray(Values.asString(args[0]));
		if (array == null) {
			return "";
		}
		String key = Values.asString(args[1]);
		JsonArray out = new JsonArray();
		for (JsonElement element : array) {
			if (element.isJsonObject() && element.getAsJsonObject().has(key)) {
				out.add(element.getAsJsonObject().get(key));
			} else {
				out.add(JsonNull.INSTANCE);
			}
		}
		return JsonText.stringify(out);
	}

	/**
	 * {@code 把数组 [json] 的长度设为 [len]}。
	 *
	 * <p>变小截断、变大补 {@code null}（JS 里是空洞，序列化后就是 null）。
	 * 非整数或负数在 JS 里会抛 {@code RangeError}，这里同样返回空串。</p>
	 */
	private static Object arraySetLen(ScriptContext ctx, Object[] args) {
		JsonArray array = parseArray(Values.asString(args[0]));
		if (array == null) {
			return "";
		}
		double raw = Values.asNumber(args[1]);
		if (raw != Math.rint(raw) || raw < 0 || raw > Integer.MAX_VALUE) {
			return "";
		}
		int target = (int) raw;
		while (array.size() > target) {
			array.remove(array.size() - 1);
		}
		while (array.size() < target) {
			array.add(JsonNull.INSTANCE);
		}
		return JsonText.stringify(array);
	}

	/** {@code 按分隔符 [d] 把文本 [text] 拆成数组}：分隔符为空时按字符拆。 */
	private static Object arrayCreate(ScriptContext ctx, Object[] args) {
		String delimiter = Values.asString(args[0]);
		String text = Values.asString(args[1]);
		JsonArray out = new JsonArray();
		if (delimiter.isEmpty()) {
			for (int i = 0; i < text.length(); i++) {
				out.add(String.valueOf(text.charAt(i)));
			}
			return JsonText.stringify(out);
		}
		int from = 0;
		while (true) {
			int at = text.indexOf(delimiter, from);
			if (at < 0) {
				out.add(text.substring(from));
				break;
			}
			out.add(text.substring(from, at));
			from = at + delimiter.length();
		}
		return JsonText.stringify(out);
	}

	/** {@code 用分隔符 [d] 把数组 [json] 拼成文本}。 */
	private static Object arrayJoin(ScriptContext ctx, Object[] args) {
		String delimiter = Values.asString(args[0]);
		JsonArray array = parseArray(Values.asString(args[1]));
		if (array == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < array.size(); i++) {
			if (i > 0) {
				out.append(delimiter);
			}
			out.append(joinElement(array.get(i)));
		}
		return out.toString();
	}

	/**
	 * {@code 数组 [list] 按 [order] 排序}：用 Scratch 的弱类型比较器。
	 *
	 * <p>不是单纯的数值排序：两边都能当数字才比数值，否则按不分大小写的字典序
	 * （见 {@code ScratchCast.compare}）。这与「排序」在 Scratch 列表里的直觉一致。</p>
	 */
	private static Object arraySort(ScriptContext ctx, Object[] args) {
		JsonArray array = parseArray(Values.asString(args[0]));
		if (array == null) {
			return "";
		}
		var items = new java.util.ArrayList<JsonElement>(array.asList());
		items.sort((left, right) -> xyz.turbomc.client.script.runtime.ScratchCast
				.compare(compareValue(left), compareValue(right)));
		if ("descending".equals(Values.asString(args[1]))) {
			java.util.Collections.reverse(items);
		}
		JsonArray out = new JsonArray();
		for (JsonElement element : items) {
			out.add(element);
		}
		return JsonText.stringify(out);
	}

	/**
	 * {@code 筛选数组 [list]：文本 [options] [text]，返回 [type]}。
	 *
	 * <p>只筛<b>字符串元素</b>；返回「对象」时给 {@code {项号: 值}}（数组场景项号是 1 基），
	 * 返回「数组」时只给值。原扩展用 {@code Object.entries} 统一处理数组与对象，这里照搬。</p>
	 */
	private static Object arrayTextFilter(ScriptContext ctx, Object[] args) {
		JsonElement root = JsonText.parse(Values.asString(args[0]));
		if (root == null || !(root.isJsonArray() || root.isJsonObject())) {
			return "";
		}
		String option = Values.asString(args[1]);
		String needle = Values.asString(args[2]);
		String shape = Values.asString(args[3]);
		boolean asObject = "Object".equals(shape);
		if (!asObject && !"Array".equals(shape)) {
			return "";
		}
		JsonArray values = new JsonArray();
		JsonObject mapped = new JsonObject();
		boolean isArray = root.isJsonArray();
		if (isArray) {
			JsonArray source = root.getAsJsonArray();
			for (int i = 0; i < source.size(); i++) {
				JsonElement element = source.get(i);
				if (!matchesText(element, option, needle)) {
					continue;
				}
				values.add(element);
				mapped.add(Integer.toString(i + 1), element);
			}
		} else {
			for (var entry : root.getAsJsonObject().entrySet()) {
				JsonElement element = entry.getValue();
				if (!matchesText(element, option, needle)) {
					continue;
				}
				values.add(element);
				mapped.add(entry.getKey(), element);
			}
		}
		return JsonText.stringify(asObject ? mapped : values);
	}

	private static boolean matchesText(JsonElement element, String option, String needle) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return false;
		}
		String value = element.getAsString();
		return switch (option) {
			case "includes" -> value.contains(needle);
			case "starts with" -> value.startsWith(needle);
			case "ends with" -> value.endsWith(needle);
			default -> false;
		};
	}

	/** 数组 join 时单个元素的表现：null 为空串，对象变 {@code [object Object]}。 */
	private static String joinElement(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return "";
		}
		return keyText(element);
	}

	/** 参与 Scratch 比较的取值：对象/数组退化为文本，null 当 0（JS 的 {@code Number(null)}）。 */
	private static Object compareValue(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return 0d;
		}
		if (element.isJsonObject() || element.isJsonArray()) {
			// JS 里对象比较走 String(obj) → "[object Object]"，全部相等；数组是 "1,2"
			return keyText(element);
		}
		return JsonText.toScratch(element);
	}

	/** JS {@code slice/splice} 的下标归一：负数从末尾数，越界夹到 {@code [0, size]}。 */
	private static int jsSliceIndex(double raw, int size) {
		int index = (int) raw;
		if (index < 0) {
			index = Math.max(size + index, 0);
		}
		return Math.min(index, size);
	}

	private static void insertAt(JsonArray array, int index, JsonElement value) {
		JsonArray rebuilt = new JsonArray();
		for (int i = 0; i < index && i < array.size(); i++) {
			rebuilt.add(array.get(i));
		}
		rebuilt.add(value);
		for (int i = index; i < array.size(); i++) {
			rebuilt.add(array.get(i));
		}
		while (array.size() > 0) {
			array.remove(0);
		}
		for (JsonElement element : rebuilt) {
			array.add(element);
		}
	}

	/**
	 * JS 的 {@code ===}：只有同类型标量会相等。
	 *
	 * <p>对象与数组按引用比较，而每次解析都会得到新对象，所以它们永远不相等——
	 * 这不是实现缺陷，是原扩展的实际行为。</p>
	 */
	private static boolean jsStrictEquals(JsonElement left, JsonElement right) {
		if (left == null || right == null) {
			return false;
		}
		if (left.isJsonObject() || right.isJsonObject() || left.isJsonArray() || right.isJsonArray()) {
			return false;
		}
		if (left.isJsonNull() || right.isJsonNull()) {
			return false;
		}
		JsonPrimitive a = left.getAsJsonPrimitive();
		JsonPrimitive b = right.getAsJsonPrimitive();
		if (a.isBoolean() != b.isBoolean() || a.isString() != b.isString() || a.isNumber() != b.isNumber()) {
			return false;
		}
		return a.equals(b);
	}

	/** 路径段转成 JS 的属性键：数字按 ECMAScript 格式化，对象退化为 {@code [object Object]}。 */
	private static String keyText(JsonElement element) {		if (element == null || element.isJsonNull()) {
			return "null";
		}
		if (element.isJsonObject()) {
			return "[object Object]";
		}
		if (element.isJsonArray()) {
			StringBuilder joined = new StringBuilder();
			JsonArray array = element.getAsJsonArray();
			for (int i = 0; i < array.size(); i++) {
				if (i > 0) {
					joined.append(',');
				}
				joined.append(keyText(array.get(i)));
			}
			return joined.toString();
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (primitive.isBoolean()) {
			return primitive.getAsBoolean() ? "true" : "false";
		}
		if (primitive.isNumber()) {
			return xyz.turbomc.client.script.runtime.ScratchCast.numberToString(primitive.getAsDouble());
		}
		return primitive.getAsString();
	}

	/**
	 * 写入用的数组位置：1 基（与读取一致），允许越界以便像 JS 那样延长数组。
	 *
	 * <p>非整数或小于 1 返回 -1——在 JS 里那只是给数组挂了个属性，序列化时看不见。</p>
	 */
	private static int writeSlot(String raw) {
		double number = xyz.turbomc.client.script.runtime.ScratchCast.toNumber(raw);
		if (Double.isNaN(number) || Double.isInfinite(number) || number != Math.rint(number) || number < 1d) {
			return -1;
		}
		return (int) number - 1;
	}
}
