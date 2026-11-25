package xyz.turbomc.client.script.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import xyz.turbomc.client.script.runtime.ScratchCast;

/**
 * 快照 JSON 的序列化（等价 {@code JSON.stringify}，不缩进）。
 *
 * <p>JSON 积木本身已迁到 JS（{@code turbowarp/src/json-lite.js}，浏览器与游戏内共用
 * 同一份实现），Java 侧只保留快照拼装需要的序列化。</p>
 *
 * <p><b>数值必须按 ECMAScript 规则输出</b>：{@code 1.0} 要写成 {@code 1}，
 * 否则回写的 JSON 与浏览器里存的不是同一串，字符串比较类脚本会莫名其妙不成立。
 * 复用 {@link ScratchCast#numberToString(double)}，与 ARCHITECTURE §3.1 同一套规则。</p>
 */
public final class JsonText {
	private JsonText() {
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
}