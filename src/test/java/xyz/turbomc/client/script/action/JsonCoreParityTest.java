package xyz.turbomc.client.script.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON 积木唯一实现的回归测试。
 *
 * <p>JSON 一族现在只有一份实现：{@code turbowarp/src/json-lite.js}。编辑器直接内联，
 * 游戏内由 {@code tools/build-extension.js} 原样复制为 {@code json-core.es5.js}，
 * 两者必须是同一段文本——本测试先守住这条。</p>
 *
 * <p>然后在内嵌 Rhino 1.7.15（与生产同一版本、同样 {@code optimizationLevel=-1}）里
 * 跑一遍 {@code tools/json-parity-cases.json}，与 {@code tools/json-parity-golden.json}
 * 逐条比对。golden 是迁移前由 Java 实现产出的权威结果，用来证明「删掉 Java 副本后行为没变」。
 * 以后若有意调整语义，用 {@code node tools/check-json-parity.js --write} 重新生成 golden。</p>
 */
class JsonCoreParityTest {
	private static final Path CASES = Path.of("tools", "json-parity-cases.json");
	private static final Path GOLDEN = Path.of("tools", "json-parity-golden.json");
	private static final Path SOURCE = Path.of("turbowarp", "src", "json-lite.js");
	private static final String RESOURCE = "/assets/turbomc/vm/json-core.es5.js";

	@Test
	void runtimeCopyMatchesSource() throws IOException {
		assertTrue(Files.isRegularFile(SOURCE), "缺少源码：" + SOURCE.toAbsolutePath());
		String source = Files.readString(SOURCE, StandardCharsets.UTF_8).trim();
		String runtime = readResource().trim();
		assertEquals(source, runtime,
				"运行时 json-core.es5.js 与 turbowarp/src/json-lite.js 不一致，运行 node tools/build-extension.js");
	}

	@Test
	void matchesGoldenUnderRhino() throws IOException {
		assertTrue(Files.isRegularFile(CASES), "缺少用例清单：" + CASES.toAbsolutePath());
		assertTrue(Files.isRegularFile(GOLDEN), "缺少基线：" + GOLDEN.toAbsolutePath());

		JsonArray cases = JsonParser.parseString(Files.readString(CASES, StandardCharsets.UTF_8))
				.getAsJsonArray();
		assertTrue(cases.size() >= 100, "用例太少，覆盖不足：" + cases.size());

		Map<String, String> golden = new HashMap<>();
		JsonArray baseline = JsonParser.parseString(Files.readString(GOLDEN, StandardCharsets.UTF_8))
				.getAsJsonArray();
		for (JsonElement element : baseline) {
			JsonObject row = element.getAsJsonObject();
			golden.put(row.get("id").getAsString(), row.get("out").getAsString());
		}

		String source = readResource();
		int checked = 0;
		Context cx = Context.enter();
		try {
			// 与 RhinoVm 完全一致的执行配置：纯解释 + ES6 语言级
			cx.setOptimizationLevel(-1);
			cx.setLanguageVersion(Context.VERSION_ES6);
			Scriptable scope = cx.initStandardObjects();
			cx.evaluateString(scope, source, "json-core.es5.js", 1, null);

			Object factoryValue = scope.get("createJsonLite", scope);
			assertTrue(factoryValue instanceof Function, "json-core.es5.js 没有暴露 createJsonLite");
			Function factory = (Function) factoryValue;
			Scriptable lite = (Scriptable) factory.call(cx, scope, scope, new Object[0]);
			Function run = (Function) lite.get("run", lite);
			Function toScratchString = (Function) lite.get("toScratchString", lite);
			assertNotNull(run, "本地实现缺少 run()");
			assertNotNull(toScratchString, "本地实现缺少 toScratchString()");

			for (JsonElement element : cases) {
				JsonObject item = element.getAsJsonObject();
				String id = item.get("id").getAsString();
				String expected = golden.get(id);
				assertNotNull(expected, "基线缺少用例：" + id);

				Scriptable args = cx.newArray(scope, toArgs(item.getAsJsonArray("args")));
				Object value = run.call(cx, lite, lite,
						new Object[]{item.get("op").getAsString(), args});
				Object text = toScratchString.call(cx, lite, lite, new Object[]{value});
				assertEquals(expected, Context.toString(text),
						"用例不一致：" + id + "（" + item.get("op").getAsString() + "）");
				checked++;
			}
		} finally {
			Context.exit();
		}
		assertEquals(cases.size(), checked, "有用例没跑到");
	}

	private static String readResource() throws IOException {
		try (InputStream stream = JsonCoreParityTest.class.getResourceAsStream(RESOURCE)) {
			assertNotNull(stream, "缺少资源：" + RESOURCE);
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
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
