package xyz.turbomc.client.script.vm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import xyz.turbomc.client.script.sb3.Sb3Importer;

/**
 * 深度保护冒烟：一条上万积木的 {@code next} 链必须被截断成可读提示，而不是打穿线程栈。
 *
 * <p><b>为什么值得单独守</b>：{@code Sb3Importer.convert} 沿 {@code next} 链递归，而 Scratch
 * 对链长没有任何限制；{@code StackOverflowError} 又是 {@code Error} 而不是
 * {@code RuntimeException}，早先的兜底拦不住它——它会一路冒到 F7 列表的点击回调里，
 * 表现为「导入一个大工程，客户端直接崩了」，且没有任何提示。</p>
 *
 * <p>本用例直接调 {@link Sb3Importer#parse}（不经过 {@code read}），所以断言的是
 * 「深度限制本身生效」，而不是「最后一道 SOE 兜底生效」。两者互为保险。</p>
 */
public final class DepthSmokeTest {
	/** 远超默认线程栈能承受的递归深度。 */
	private static final int CHAIN = 20_000;
	/** 已映射、无输入的积木，链里用它只是为了构造长 next 链。 */
	private static final String OPCODE = "event_whenflagclicked";
	private static final String TRUNCATED = "积木嵌套超过";

	public static void main(String[] args) {
		Sb3Importer.Result result;
		try {
			result = Sb3Importer.parse(project(CHAIN));
		} catch (StackOverflowError e) {
			System.out.println("DEPTH SMOKE FAIL：栈溢出未被拦截");
			System.exit(1);
			return;
		} catch (RuntimeException e) {
			System.out.println("DEPTH SMOKE FAIL：解析抛异常 " + e);
			System.exit(1);
			return;
		}

		boolean truncated = result.unsupported().stream().anyMatch(text -> text.startsWith(TRUNCATED));
		System.out.println("[depth] 积木 " + CHAIN + "，脚本 " + result.scripts().size()
				+ "，截断提示 " + truncated + "，不支持项 " + result.unsupported().size());
		boolean ok = truncated && result.scripts().size() == 1;
		System.out.println(ok ? "DEPTH SMOKE PASS" : "DEPTH SMOKE FAIL");
		System.exit(ok ? 0 : 1);
	}

	/** 造一个只有一条超长链的 project.json 结构。 */
	private static JsonObject project(int chain) {
		JsonObject blocks = new JsonObject();
		for (int i = 0; i < chain; i++) {
			JsonObject block = new JsonObject();
			block.addProperty("opcode", OPCODE);
			// 末块没有 next；addProperty(String, null) 写的是 JsonNull，nextId 会判成"没有"
			block.addProperty("next", i + 1 < chain ? "b" + (i + 1) : null);
			block.addProperty("topLevel", i == 0);
			blocks.add("b" + i, block);
		}
		JsonObject target = new JsonObject();
		target.addProperty("isStage", true);
		target.addProperty("name", "Stage");
		target.add("blocks", blocks);

		JsonArray targets = new JsonArray();
		targets.add(target);
		JsonObject root = new JsonObject();
		root.add("targets", targets);
		return root;
	}
}
