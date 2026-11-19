package xyz.turbomc.client.script.action;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 「键的可见性」三处一致性守卫：代码清单 / 编辑器骨架 / 文档表格。
 *
 * <p>键名只存在于代码里，用户看不到——所以既有文档表格，也有「[快照] 的字段」积木。
 * 只要三者中的任何一处被改动而另两处没跟上，用户拿到的就是错的键名，
 * 而这类错误在游戏里表现为「取到空值」，没有任何异常。这个测试就是防这个。</p>
 */
class SnapshotKeysDocTest {
	private static final Path README = Path.of("turbowarp/README.md");
	private static final Path DATA = Path.of("turbowarp/src/blocks.data.js");
	/** 文档里每种快照的键行格式：{@code - `kind` —— 说明} 换行后 {@code 键：a, b, c}。 */
	private static final Pattern DOC_LINE =
			Pattern.compile("(?m)^- `([a-z_]+)`[^\\n]*\\n\\s*键：(.*)$");

	private static final List<String> KINDS = List.of(
			"player", "entity", "item", "screen", "block", "block_entity", "world",
			"players", "scan", "chat");

	@Test
	@DisplayName("文档里的键行必须与 Snapshots 的清单逐项一致")
	void docMatchesCode() throws IOException {
		assumeTrue(Files.isRegularFile(README), "找不到 turbowarp/README.md，跳过文档校验");
		Map<String, List<String>> doc = parseDoc(Files.readString(README, StandardCharsets.UTF_8));
		for (String kind : KINDS) {
			assertTrue(doc.containsKey(kind), "文档缺少 `" + kind + "` 的「键：」行");
			assertEquals(Snapshots.keysOf(kind), doc.get(kind),
					"`" + kind + "` 的文档键与代码不一致（改键时三处要一起改）");
		}
	}

	@Test
	@DisplayName("编辑器骨架必须覆盖全部键，否则浏览器里看不到真实键名")
	void stubCoversKeys() throws IOException {
		assumeTrue(Files.isRegularFile(DATA), "找不到 blocks.data.js，跳过骨架校验");
		String js = Files.readString(DATA, StandardCharsets.UTF_8);

		assertEquals(Snapshots.PLAYER_KEYS,
				keysOf(stubOf(js, "playerJson")), "玩家骨架的键与代码不一致");
		assertEquals(Snapshots.WORLD_KEYS,
				keysOf(stubOf(js, "worldJson")), "世界骨架的键与代码不一致");

		JsonObject block = JsonText.parse(stubOf(js, "blockJson")).getAsJsonObject();
		assertEquals(Snapshots.BLOCK_KEYS, List.copyOf(block.keySet()));
		assertEquals(Snapshots.BLOCK_ENTITY_KEYS,
				List.copyOf(block.getAsJsonObject("block_entity").keySet()));
	}

	private static List<String> keysOf(String json) {
		JsonObject object = JsonText.parse(json).getAsJsonObject();
		return List.copyOf(object.keySet());
	}

	/**
	 * 取出某块积木的骨架 JSON 文本。
	 *
	 * <p>数据源里写作 {@code stub: 'return \'{...}\';'}，需要先还原转义的单引号。</p>
	 */
	private static String stubOf(String js, String opcode) {
		int at = js.indexOf("opcode: '" + opcode + "'");
		assertTrue(at > 0, "数据源里找不到积木 " + opcode);
		int start = js.indexOf("stub: '", at);
		assertTrue(start > 0, opcode + " 没有 stub");
		int end = js.indexOf("';'", start);
		assertTrue(end > start, opcode + " 的 stub 结尾不合法");
		// 数据源用单引号包裹并转义了内部引号，直接按花括号取对象字面量最稳：
		// 这一段的边界（尾部的 \';' ）在两种写法下位置不同，按结构取反而不受其影响
		String raw = js.substring(start + "stub: '".length(), end);
		int open = raw.indexOf('{');
		int close = raw.lastIndexOf('}');
		assertTrue(open >= 0 && close > open, opcode + " 的骨架不是对象字面量：" + raw);
		String json = raw.substring(open, close + 1);
		assertNotNull(JsonText.parse(json), opcode + " 的骨架不是合法 JSON：" + json);
		return json;
	}

	private static Map<String, List<String>> parseDoc(String text) {
		Map<String, List<String>> out = new LinkedHashMap<>();
		Matcher matcher = DOC_LINE.matcher(text);
		while (matcher.find()) {
			String kind = matcher.group(1);
			String values = matcher.group(2);
			// 只取「；」之前的部分：后半句是补充说明
			int semicolon = values.indexOf('；');
			if (semicolon >= 0) {
				values = values.substring(0, semicolon);
			}
			List<String> keys = new ArrayList<>();
			// 「（无键…）」表示这块返回字符串数组，没有键
			if (!values.trim().startsWith("（")) {
				for (String part : values.split(",")) {
					String key = part.trim();
					if (!key.isEmpty()) {
						keys.add(key);
					}
				}
			}
			out.put(kind, keys);
		}
		return out;
	}
}
