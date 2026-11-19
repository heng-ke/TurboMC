package xyz.turbomc.client.script.vm;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import xyz.turbomc.client.script.action.JsonText;
import xyz.turbomc.client.script.action.NativeRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * JSON 积木放在哪一侧更快：一套可重复的基准，不改任何生产代码。
 *
 * <p>比较对象是「同一套算法在两种宿主里跑」：</p>
 * <ul>
 *   <li><b>JS（Rhino）</b>：{@code json-bench.js}，用 {@code JSON.parse} / {@code JSON.stringify}
 *       与 JS 循环，跑在与游戏完全相同的配置下——{@code optimizationLevel = -1}（纯解释执行）、
 *       ES6 语言版本。这是关键：默认的编译模式会给出虚假的乐观数字。</li>
 *   <li><b>Java</b>：直接调 {@link JsonText} 与 {@link NativeRegistry} 里已注册的功能函数，
 *       也就是桥最终落到的那一层。</li>
 * </ul>
 *
 * <p>另外单独测「Rhino 调一次 Java 方法」的成本，对应每次扩展积木的桥开销。</p>
 *
 * <p>仅手动运行：{@code ./gradlew jsonBench}。</p>
 */
public final class JsonBench {
	private static final int OBJECTS = 12_000;
	private static final int NUMBERS = 1_000;
	private static final int PARSE_RUNS = 15;
	private static final int GET_RUNS = 200;
	private static final int SORT_RUNS = 15;
	private static final int NOOP_CALLS = 100_000;

	private JsonBench() {
	}

	public static void main(String[] args) throws Exception {
		NativeRegistry.bootstrapOnce();
		String big = bigJson();
		String numbers = numberArray();
		System.out.printf("[bench] 数据：对象数组 %d KB（%d 条）、数值数组 %d 项%n",
				big.length() / 1024, OBJECTS, NUMBERS);
		System.out.println("[bench] 配置：Rhino optimizationLevel=-1（解释执行，与游戏一致）");

		Context cx = Context.enter();
		try {
			cx.setLanguageVersion(Context.VERSION_ES6);
			cx.setOptimizationLevel(-1);
			Scriptable scope = cx.initStandardObjects();
			// 模拟一次「积木调用桥」：JS 里调一个 Java 方法
			ScriptableObject.putProperty(scope, "__benchNoop", new BaseFunction() {
				@Override
				public Object call(Context context, Scriptable callScope, Scriptable thisObj, Object[] callArgs) {
					return "";
				}
			});
			cx.evaluateString(scope, readBenchScript(), "json-bench.js", 1, null);
			Scriptable bench = (Scriptable) scope.get("__jsonBench", scope);

			// 预热：Rhino 的解释器与 Java 的 JIT 都需要
			for (int i = 0; i < 3; i++) {
				js(cx, scope, bench, "parse", big);
				js(cx, scope, bench, "minify", big);
				js(cx, scope, bench, "get", big, "k1");
				js(cx, scope, bench, "sort", numbers);
				java("json.minify", big);
				java("json.get", big, "k1");
				java("json.array_sort", numbers, "ascending");
			}

			double jsParse = average(PARSE_RUNS, () -> js(cx, scope, bench, "parse", big));
			double javaParse = average(PARSE_RUNS, () -> JsonText.parse(big));

			double jsMinify = average(PARSE_RUNS, () -> js(cx, scope, bench, "minify", big));
			double javaMinify = average(PARSE_RUNS, () -> java("json.minify", big));

			double jsGet = average(GET_RUNS, () -> js(cx, scope, bench, "get", big, "k1"));
			double javaGet = average(GET_RUNS, () -> java("json.get", big, "k1"));

			double jsSort = average(SORT_RUNS, () -> js(cx, scope, bench, "sort", numbers));
			double javaSort = average(SORT_RUNS, () -> java("json.array_sort", numbers, "ascending"));

			double jsNoop = average(3, () -> js(cx, scope, bench, "noopLoop", NOOP_CALLS)) * 1_000_000 / NOOP_CALLS;

			String small = smallNumberArray(32);
			for (int i = 0; i < 20; i++) {
				js(cx, scope, bench, "sort", small);
				java("json.array_sort", small, "ascending");
			}
			double jsSortSmall = average(200, () -> js(cx, scope, bench, "sort", small));
			double javaSortSmall = average(200, () -> java("json.array_sort", small, "ascending"));

			System.out.println();
			String sizeLabel = (big.length() / 1024) + " KB";
			System.out.println("[bench] 场景                          JS(Rhino)        Java            JS/Java");
			row("解析 " + sizeLabel + "（单次）", jsParse, javaParse, "ms");
			row("解析+序列化 " + sizeLabel + "（单次）", jsMinify, javaMinify, "ms");
			row("按键取值（单次，含解析）", jsGet, javaGet, "ms");
			row("排序 32 项（单次，含解析+序列化）", jsSortSmall, javaSortSmall, "ms");
			row("排序 1000 项（单次，含解析+序列化）", jsSort, javaSort, "ms");
			System.out.println();
			System.out.printf("[bench] Rhino → Java 单次调用：%.0f ns（%d 次平均）%n", jsNoop, NOOP_CALLS);
			System.out.println("[bench] 说明：Java 一列不含桥的参数抽取与 dispatch，那部分只增加微秒级常数");
		} finally {
			Context.exit();
		}
	}

	private static void row(String label, double js, double java, String unit) {
		System.out.printf("[bench] %-30s %8.2f %s %8.2f %s %8.1fx%n",
				label, js, unit, java, unit, java == 0 ? 0 : js / java);
	}

	private static double average(int runs, Runnable action) {
		long start = System.nanoTime();
		for (int i = 0; i < runs; i++) {
			action.run();
		}
		return (System.nanoTime() - start) / 1_000_000d / runs;
	}

	private static Object js(Context cx, Scriptable scope, Scriptable bench, String name, Object... args) {
		Function function = (Function) ScriptableObject.getProperty(bench, name);
		return function.call(cx, scope, bench, args);
	}

	private static Object java(String id, Object... args) {
		NativeRegistry.Function function = NativeRegistry.getFunction(id);
		if (function == null) {
			throw new IllegalStateException("动作未注册：" + id);
		}
		return function.apply(null, args);
	}

	private static String readBenchScript() throws IOException {
		try (InputStream in = JsonBench.class.getResourceAsStream("/turbomc-vm/json-bench.js")) {
			if (in == null) {
				throw new IOException("找不到 json-bench.js");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** 约 1 MB 的对象数组：字段名与取值都贴近实际脚本会处理的数据。 */
	private static String bigJson() {
		StringBuilder sb = new StringBuilder(1_100_000);
		sb.append('[');
		for (int i = 0; i < OBJECTS; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append("{\"id\":").append(i)
					.append(",\"name\":\"entity_").append(i).append('"')
					.append(",\"x\":").append(i % 300).append(".5")
					.append(",\"y\":").append(64 + i % 20)
					.append(",\"tag\":\"t").append(i % 7).append("\"}");
		}
		return sb.append(']').toString();
	}

	/** 小规模数组：脚本里真实会遇到的量级（背包 36 格、附近实体几十个）。 */
	private static String smallNumberArray(int size) {
		StringBuilder sb = new StringBuilder(size * 10);
		sb.append('[');
		long seed = 20261119L;
		for (int i = 0; i < size; i++) {
			if (i > 0) {
				sb.append(',');
			}
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			sb.append((seed >>> 11) % 1_000_000 / 1000d);
		}
		return sb.append(']').toString();
	}

	/** 1000 个混合量级的数值，避免排序退化成本已有序。 */
	private static String numberArray() {
		StringBuilder sb = new StringBuilder(NUMBERS * 12);
		sb.append('[');
		long seed = 20261119L;
		for (int i = 0; i < NUMBERS; i++) {
			if (i > 0) {
				sb.append(',');
			}
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			double value = (seed >>> 11) % 1_000_000 / 1000d;
			sb.append(value);
		}
		return sb.append(']').toString();
	}
}
