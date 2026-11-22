package xyz.turbomc.client.script.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import xyz.turbomc.client.script.runtime.ScratchCast;
import xyz.turbomc.client.script.sb3.Sb3Mapping;import xyz.turbomc.client.script.runtime.ScriptContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON 积木的跨引擎对拍（Java 侧）。
 *
 * <p>远程模式下 JSON 一族在浏览器里另有一份 JS 实现（{@code turbowarp/src/json-lite.js}）：
 * reporter 是同步求值，等不了 RPC。两份实现一旦分叉，就会出现「同样的积木、开发时对、
 * 发布后错」——所以用同一批用例两边各跑一遍，逐条比对字符串化结果。</p>
 *
 * <p>本测试只产出 Java 侧结果，真正的比对由 {@code tools/check-json-parity.js} 完成
 * （它跑 JS 实现并与 {@code build/json-parity-java.json} 对照）。测试自身校验
 * 「用例全部跑出了结果」，用例清单缺失或结果为空都算失败。</p>
 */
class JsonParityTest {
	private static final Path CASES = Path.of("tools", "json-parity-cases.json");
	private static final Path OUTPUT = Path.of("build", "json-parity-java.json");

	@Test
	void writeJavaSideResults() throws IOException {
		assertTrue(Files.isRegularFile(CASES), "缺少用例清单：" + CASES.toAbsolutePath());
		JsonActions.registerAll();

		JsonArray cases = JsonParser.parseString(Files.readString(CASES, StandardCharsets.UTF_8))
				.getAsJsonArray();
		assertFalse(cases.isEmpty(), "用例清单为空");

		List<JsonObject> results = new ArrayList<>();
		ScriptContext context = new ScriptContext(null);
		for (JsonElement element : cases) {
			JsonObject item = element.getAsJsonObject();
			String id = item.get("id").getAsString();
			String opcode = item.get("op").getAsString();
			// 走生产同一条映射：opcode → 动作 id，避免对拍用一个"测试专用"入口
			String actionId = Sb3Mapping.typeOf("turbomc_" + opcode);
			assertNotNull(actionId, "opcode 未映射：" + opcode);
			NativeRegistry.Function function = NativeRegistry.getFunction(actionId);
			assertNotNull(function, "动作未注册：" + actionId);
			Object[] args = toArgs(item.getAsJsonArray("args"));
			Object value = function.apply(context, args);

			JsonObject out = new JsonObject();
			out.addProperty("id", id);
			// 与 JS 侧比较的是「积木最终显示出来的东西」，所以统一走字符串化
			out.addProperty("out", ScratchCast.toString(value));
			results.add(out);
		}

		JsonArray array = new JsonArray();
		results.forEach(array::add);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Files.createDirectories(OUTPUT.getParent());
		Files.writeString(OUTPUT, gson.toJson(array), StandardCharsets.UTF_8);
		assertTrue(results.size() >= 100, "用例太少，覆盖不足：" + results.size());
	}

	private static Object[] toArgs(JsonArray array) {
		Object[] args = new Object[array.size()];
		for (int i = 0; i < array.size(); i++) {
			JsonElement element = array.get(i);
			if (element.isJsonPrimitive()) {
				var primitive = element.getAsJsonPrimitive();
				if (primitive.isBoolean()) {
					args[i] = primitive.getAsBoolean();
				} else if (primitive.isNumber()) {
					args[i] = primitive.getAsDouble();
				} else {
					args[i] = primitive.getAsString();
				}
			} else {
				args[i] = element.toString();
			}
		}
		return args;
	}
}
