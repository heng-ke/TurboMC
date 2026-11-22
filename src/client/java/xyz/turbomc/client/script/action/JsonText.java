package xyz.turbomc.client.script.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import xyz.turbomc.client.script.runtime.ScratchCast;

/**
 * JSON 文本的解析与序列化，逐条对齐 TurboWarp 扩展 {@code skyhigh173JSON}（Skyhigh173 &amp; Mio，MIT）。
 *
 * <p><b>现在的用途</b>：JSON 积木本身已经迁到 JS（{@code turbowarp/src/json-lite.js}，
 * 浏览器与游戏内共用同一份实现），不再有任何积木走这里。本类只剩两项职责：
 * {@link Snapshots} 用 {@link #stringify} 拼快照文本，以及测试用它校验快照输出。
 * 保留的原因是快照在 Java 侧拼 JSON，与积木语义无关。</p>
 * <p><b>为什么自己写解析器</b>：Gson 的 {@code JsonParser} 在入口处会把 {@code JsonReader}
 * 临时切成宽松模式（即使外面 setLenient(false) 也会被它覆盖），于是 {@code {a:1}}、单引号、
 * 注释统统被当成合法 JSON。原扩展走的是浏览器 {@code JSON.parse}，严格得多——
 * 两者一旦分叉，「是不是合法 JSON」这块积木就与 TurboWarp 里的结果不一致。
 * 这里只借用 Gson 的数据结构（{@code JsonObject}/{@code JsonArray}），解析按 RFC 8259 自己走。</p>
 *
 * <p>另两条必须守住的语义：</p>
 * <ol>
 *   <li><b>数值按 ECMAScript 规则输出</b>：{@code 1.0} 必须写成 {@code 1}，否则回写的 JSON
 *       与浏览器里存的不是同一串，<b>字符串比较</b>类脚本会莫名其妙不成立。
 *       复用 {@link ScratchCast#numberToString(double)}，与 ARCHITECTURE §3.1 同一套规则。</li>
 *   <li><b>取值为对象/数组时返回 JSON 文本，标量按原生类型返回</b>：返回 {@code Double} 而不是
 *       {@code String}，让 VM 用同一套字符串化规则处理。</li>
 * </ol>
 */
public final class JsonText {
	/** 嵌套上限。原实现没有，但 Java 是递归下降，不设上限会被深层输入打穿线程栈。 */
	private static final int MAX_DEPTH = 256;

	private JsonText() {
	}

	/**
	 * 严格解析（等价 {@code JSON.parse}）。失败返回 {@code null}——调用方据此返回空串。
	 *
	 * <p>{@code null} 字面量也返回 {@code null}：本扩展所有积木都把它当「没有值」。</p>
	 */
	public static JsonElement parse(String text) {
		if (text == null) {
			return null;
		}
		try {
			Parser parser = new Parser(text);
			JsonElement element = parser.readValue(0);
			parser.skipWhitespace();
			if (!parser.atEnd()) {
				// JSON.parse 不接受「值后面还有内容」
				return null;
			}
			return element == null || element.isJsonNull() ? null : element;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * 解析失败的原因，带上出错位置。
	 *
	 * <p>原扩展直接把浏览器的 {@code SyntaxError.toString()} 返回给脚本，跨引擎不可能逐字一致；
	 * 这里给结构相同的中文说明，位置用于自查。</p>
	 */
	public static String errorMessage(String text) {
		if (text == null) {
			return "";
		}
		try {
			Parser parser = new Parser(text);
			parser.readValue(0);
			parser.skipWhitespace();
			if (!parser.atEnd()) {
				return "JSON 解析失败：值后面还有内容（位置 " + parser.pos + "）";
			}
		} catch (ParserError e) {
			return "JSON 解析失败：" + e.getMessage() + "（位置 " + e.position() + "）";
		} catch (RuntimeException e) {
			return "JSON 解析失败";
		}
		if (!looksLikeContainer(text)) {
			// 与原扩展同一句提示：解析没问题，但其它积木只接受对象与数组
			return "JSON 必须是对象或数组";
		}
		return "";
	}

	/**
	 * 与原扩展一致：只有以 {@code [}/{@code {} 开头并配对结尾的文本才可能被当成 JSON。
	 *
	 * <p>这条前置判断决定了 {@code "1"}、{@code "true"} 都不算合法 JSON——原扩展如此，
	 * 照搬以免「是不是合法」这块积木与浏览器里的行为分叉。</p>
	 */
	public static boolean looksLikeContainer(String text) {
		if (text == null) {
			return false;
		}
		String body = text.trim();
		if (body.length() < 2) {
			return false;
		}
		char first = body.charAt(0);
		char last = body.charAt(body.length() - 1);
		return (first == '[' && last == ']') || (first == '{' && last == '}');
	}

	/** 序列化：等价于 {@code JSON.stringify}（不缩进）。 */
	public static String stringify(JsonElement element) {
		StringBuilder out = new StringBuilder();
		write(element, out);
		return out.toString();
	}

	private static void write(JsonElement element, StringBuilder out) {
		if (element == null || element.isJsonNull()) {
			out.append("null");
			return;
		}
		if (element.isJsonPrimitive()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();
			if (primitive.isBoolean()) {
				out.append(primitive.getAsBoolean() ? "true" : "false");
			} else if (primitive.isNumber()) {
				double number = primitive.getAsDouble();
				// JSON 没有 NaN/Infinity，JSON.stringify 会写成 null
				out.append(Double.isFinite(number) ? ScratchCast.numberToString(number) : "null");
			} else {
				writeString(primitive.getAsString(), out);
			}
			return;
		}
		if (element.isJsonArray()) {
			out.append('[');
			JsonArray array = element.getAsJsonArray();
			for (int i = 0; i < array.size(); i++) {
				if (i > 0) {
					out.append(',');
				}
				write(array.get(i), out);
			}
			out.append(']');
			return;
		}
		out.append('{');
		boolean first = true;
		for (var entry : element.getAsJsonObject().entrySet()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			writeString(entry.getKey(), out);
			out.append(':');
			write(entry.getValue(), out);
		}
		out.append('}');
	}

	/** 与 {@code JSON.stringify} 相同的转义集合：引号、反斜杠、控制字符。 */
	private static void writeString(String value, StringBuilder out) {
		out.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				case '\b' -> out.append("\\b");
				case '\f' -> out.append("\\f");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					} else {
						out.append(c);
					}
				}
			}
		}
		out.append('"');
	}

	/**
	 * 把 JSON 值转成积木的返回值。
	 *
	 * <p>标量走原生类型（{@code Double}/{@code Boolean}/{@code String}），对象与数组给 JSON 文本，
	 * {@code null} 给空串——与原扩展 {@code result ?? ""} 一致。</p>
	 */
	public static Object toScratch(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return "";
		}
		if (element.isJsonObject() || element.isJsonArray()) {
			return stringify(element);
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (primitive.isBoolean()) {
			return primitive.getAsBoolean();
		}
		if (primitive.isNumber()) {
			return primitive.getAsDouble();
		}
		return primitive.getAsString();
	}

	/**
	 * 写入前的值处理，等价于原扩展的 {@code json_valid_return} + {@code _fixInvalidJSONValues}：
	 * 看起来像容器的文本尝试解析，失败就当普通字符串；{@code null} 变空串。
	 */
	public static JsonElement toWriteValue(Object raw) {
		String text = ScratchCast.toString(raw);
		if (looksLikeContainer(text)) {
			JsonElement parsed = parse(text);
			if (parsed != null) {
				return parsed;
			}
		}
		return new JsonPrimitive(text);
	}

	/**
	 * Scratch 风格下标 → 0 基数组下标：1 是首项，-1 是末项，0 与越界返回 -1。
	 */
	public static int arrayIndex(Object raw, int size) {
		double number = ScratchCast.toNumber(raw);
		if (Double.isNaN(number) || Double.isInfinite(number) || number != Math.rint(number) || number == 0d) {
			return -1;
		}
		int index = (int) number;
		if (index > 0) {
			index--;
		} else {
			index += size;
		}
		return index >= 0 && index < size ? index : -1;
	}

	/** 数组越界补 null：JS 里 {@code arr[5]=x} 会把中间填成空洞，序列化后就是 null。 */
	public static void setArraySlot(JsonArray array, int index, JsonElement value) {
		while (array.size() <= index) {
			array.add(JsonNull.INSTANCE);
		}
		array.set(index, value);
	}

	/** 解析错误：带上出错位置，供「解析错误」积木给出可自查的提示。 */
	private static final class ParserError extends RuntimeException {
		private final int position;

		ParserError(String message, int position) {
			super(message);
			this.position = position;
		}

		int position() {
			return position;
		}
	}

	/**
	 * RFC 8259 的递归下降解析器。
	 *
	 * <p>比 Gson 的入口严格的地方：键必须双引号、字符串只能双引号、不允许注释与尾逗号、
	 * 数字必须符合 JSON 数字文法（{@code 01}、{@code .5}、{@code 1.} 全部非法）、
	 * 控制字符必须转义。</p>
	 */
	private static final class Parser {
		private final String text;
		private int pos;

		Parser(String text) {
			this.text = text;
		}

		private ParserError error(String message) {
			return new ParserError(message, pos);
		}

		boolean atEnd() {
			return pos >= text.length();
		}

		void skipWhitespace() {
			while (pos < text.length()) {
				char c = text.charAt(pos);
				if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
					pos++;
				} else {
					break;
				}
			}
		}

		JsonElement readValue(int depth) {
			if (depth > MAX_DEPTH) {
				throw error("JSON 嵌套过深");
			}
			skipWhitespace();
			if (atEnd()) {
				throw error("JSON 提前结束");
			}
			char c = text.charAt(pos);
			return switch (c) {
				case '{' -> readObject(depth);
				case '[' -> readArray(depth);
				case '"' -> new JsonPrimitive(readString());
				case 't' -> readLiteral("true", new JsonPrimitive(true));
				case 'f' -> readLiteral("false", new JsonPrimitive(false));
				case 'n' -> readLiteral("null", JsonNull.INSTANCE);
				default -> readNumber();
			};
		}

		private JsonElement readLiteral(String literal, JsonElement value) {
			if (!text.startsWith(literal, pos)) {
				throw error("非法字面量");
			}
			pos += literal.length();
			return value;
		}

		private JsonObject readObject(int depth) {
			JsonObject object = new JsonObject();
			pos++; // {
			skipWhitespace();
			if (!atEnd() && text.charAt(pos) == '}') {
				pos++;
				return object;
			}
			while (true) {
				skipWhitespace();
				if (atEnd() || text.charAt(pos) != '"') {
					throw error("键必须是双引号字符串");
				}
				String key = readString();
				skipWhitespace();
				if (atEnd() || text.charAt(pos) != ':') {
					throw error("缺少冒号");
				}
				pos++;
				JsonElement value = readValue(depth + 1);
				// 重复键以最后一次为准，与 JSON.parse 一致
				object.add(key, value);
				skipWhitespace();
				if (atEnd()) {
					throw error("对象未闭合");
				}
				char c = text.charAt(pos);
				if (c == ',') {
					pos++;
					skipWhitespace();
					if (!atEnd() && text.charAt(pos) == '}') {
						throw error("不允许尾随逗号");
					}
					continue;
				}
				if (c == '}') {
					pos++;
					return object;
				}
				throw error("对象中出现非法字符");
			}
		}

		private JsonArray readArray(int depth) {
			JsonArray array = new JsonArray();
			pos++; // [
			skipWhitespace();
			if (!atEnd() && text.charAt(pos) == ']') {
				pos++;
				return array;
			}
			while (true) {
				array.add(readValue(depth + 1));
				skipWhitespace();
				if (atEnd()) {
					throw error("数组未闭合");
				}
				char c = text.charAt(pos);
				if (c == ',') {
					pos++;
					skipWhitespace();
					if (!atEnd() && text.charAt(pos) == ']') {
						throw error("不允许尾随逗号");
					}
					continue;
				}
				if (c == ']') {
					pos++;
					return array;
				}
				throw error("数组中出现非法字符");
			}
		}

		private String readString() {
			pos++; // "
			StringBuilder out = new StringBuilder();
			while (true) {
				if (atEnd()) {
					throw error("字符串未闭合");
				}
				char c = text.charAt(pos++);
				if (c == '"') {
					return out.toString();
				}
				if (c < 0x20) {
					throw error("字符串里有未转义的控制字符");
				}
				if (c != '\\') {
					out.append(c);
					continue;
				}
				if (atEnd()) {
					throw error("转义未完成");
				}
				char escape = text.charAt(pos++);
				switch (escape) {
					case '"' -> out.append('"');
					case '\\' -> out.append('\\');
					case '/' -> out.append('/');
					case 'b' -> out.append('\b');
					case 'f' -> out.append('\f');
					case 'n' -> out.append('\n');
					case 'r' -> out.append('\r');
					case 't' -> out.append('\t');
					case 'u' -> out.append(readHex4());
					default -> throw error("非法转义");
				}
			}
		}

		private char readHex4() {
			if (pos + 4 > text.length()) {
				throw error("\\u 转义不完整");
			}
			String hex = text.substring(pos, pos + 4);
			pos += 4;
			try {
				return (char) Integer.parseInt(hex, 16);
			} catch (NumberFormatException e) {
				throw error("\\u 转义非法");
			}
		}

		private JsonPrimitive readNumber() {
			int start = pos;
			if (!atEnd() && text.charAt(pos) == '-') {
				pos++;
			}
			// 整数部分：0 或 [1-9][0-9]*，不接受前导 0（JSON 不允许 01）
			if (atEnd()) {
				throw error("数字不完整");
			}
			char first = text.charAt(pos);
			if (first == '0') {
				pos++;
			} else if (first >= '1' && first <= '9') {
				while (!atEnd() && Character.isDigit(text.charAt(pos))) {
					pos++;
				}
			} else {
				throw error("非法数字");
			}
			if (!atEnd() && text.charAt(pos) == '.') {
				pos++;
				if (atEnd() || !Character.isDigit(text.charAt(pos))) {
					throw error("小数点后必须有数字");
				}
				while (!atEnd() && Character.isDigit(text.charAt(pos))) {
					pos++;
				}
			}
			if (!atEnd() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
				pos++;
				if (!atEnd() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
					pos++;
				}
				if (atEnd() || !Character.isDigit(text.charAt(pos))) {
					throw error("指数必须有数字");
				}
				while (!atEnd() && Character.isDigit(text.charAt(pos))) {
					pos++;
				}
			}
			return new JsonPrimitive(Double.parseDouble(text.substring(start, pos)));
		}
	}
}
