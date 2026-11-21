package xyz.turbomc.client.script.sb3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 载入前的工程结构校验。
 *
 * <p>存在的理由只有一个：scratch-vm 在 Rhino 里遇到结构性缺陷时不报真实原因。
 * 最典型的是「某个 target 的 {@code costumes} 为空」——VM 会在资源加载里抛
 * {@code Error: Non-ascii character in FixedAsciiString}，玩家看到的是
 * 「工程载入失败」加一段和造型毫无关系的英文（DEF-03）。同一条路径上，
 * {@code project.json} 语法错误也会被包成这个错误。</p>
 *
 * <p>第三种情况更糟：{@code next} 链成环时 VM 不会报错，而是顺着链无限执行，
 * 堆一路涨到数 GB、客户端无响应（DEF-04）。环只能来自手改 JSON、第三方生成器或
 * 文件损坏——编辑器画不出来（连接类型 + 祖先检查拦住了）。</p>
 *
 * <p>因此这里只做「能在载入前说清楚的检查」，把原因换成玩家能行动的话；
 * 检查不出来的一律放行，交给 VM 报错——宁可漏报，不要误伤正常工程。</p>
 *
 * <p>返回的是「语言键 + 参数」而不是 {@code Text}：本类不进 Minecraft 运行时，
 * 单测里可以直接断言，不必初始化客户端。</p>
 */
public final class ProjectSanity {
	/** project.json 不是合法 JSON（含缺少 targets）。 */
	public static final String KEY_BAD_JSON = "status.turbomc.detail.bad_project_json";
	/** 某个 target 没有任何造型。 */
	public static final String KEY_MISSING_COSTUME = "status.turbomc.detail.missing_costume";
	/** 积木链里存在环：next 顺着走会回到自己。 */
	public static final String KEY_CYCLIC_BLOCKS = "status.turbomc.detail.cyclic_blocks";

	/** 校验结论：{@code key} 是语言键，{@code args} 是插值参数。 */
	public record Problem(String key, Object... args) {
	}

	private ProjectSanity() {
	}

	/**
	 * 检查工程能否安全交给 VM。
	 *
	 * @return {@code null} 表示没发现问题；否则返回可直接展示的原因
	 */
	public static Problem check(String projectJson) {
		if (projectJson == null || projectJson.isBlank()) {
			return new Problem(KEY_BAD_JSON, "内容为空");
		}
		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(projectJson);
			if (!parsed.isJsonObject()) {
				return new Problem(KEY_BAD_JSON, "根节点不是对象");
			}
			root = parsed.getAsJsonObject();
		} catch (RuntimeException e) {
			return new Problem(KEY_BAD_JSON, brief(e.getMessage()));
		}

		JsonElement targets = root.get("targets");
		if (targets == null || !targets.isJsonArray() || targets.getAsJsonArray().isEmpty()) {
			return new Problem(KEY_BAD_JSON, "缺少 targets");
		}
		JsonArray list = targets.getAsJsonArray();
		for (JsonElement element : list) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject target = element.getAsJsonObject();
			JsonElement costumes = target.get("costumes");
			boolean empty = costumes == null || !costumes.isJsonArray()
					|| costumes.getAsJsonArray().isEmpty();
			if (empty) {
				return new Problem(KEY_MISSING_COSTUME, nameOf(target));
			}
			String cycle = firstCycle(target.get("blocks"));
			if (cycle != null) {
				return new Problem(KEY_CYCLIC_BLOCKS, cycle);
			}
		}
		return null;
	}

	/**
	 * 找 {@code next} 链里的环。
	 *
	 * <p>环是 DEF-04 的唯一致命点：VM 顺着 {@code next} 不断执行同一批积木，
	 * 每一圈都在分配对象，实测 {@code -Xmx512m} 下 20 秒 OOM，默认堆会涨到 7.9 GB。
	 * 编辑器画不出这种图（Blockly 有连接类型与祖先检查），它只可能来自手改 JSON、
	 * 第三方生成器或文件损坏——所以这里按「数据是否自相矛盾」判断，不看积木形状。</p>
	 *
	 * <p>用三色标记做一次线性遍历（0 未访问 / 1 在栈上 / 2 已确认无环），
	 * 已经走过的尾巴不会重复走，几千块工程也是一次 O(n)。</p>
	 *
	 * @return 有环时返回环上某一块的 opcode（给玩家看得懂的名字），无环返回 {@code null}
	 */
	private static String firstCycle(JsonElement blocksElement) {
		if (blocksElement == null || !blocksElement.isJsonObject()) {
			return null;
		}
		JsonObject blocks = blocksElement.getAsJsonObject();
		Map<String, Integer> state = new HashMap<>();
		for (String start : blocks.keySet()) {
			String current = start;
			List<String> path = new ArrayList<>();
			while (current != null) {
				Integer seen = state.get(current);
				if (seen != null) {
					if (seen == 1) {
						return opcodeOf(blocks, current);
					}
					break;
				}
				state.put(current, 1);
				path.add(current);
				JsonElement block = blocks.get(current);
				if (block == null || !block.isJsonObject()) {
					break;
				}
				JsonElement next = block.getAsJsonObject().get("next");
				current = next == null || next.isJsonNull() ? null : next.getAsString();
			}
			for (String done : path) {
				state.put(done, 2);
			}
		}
		return null;
	}

	/** 环上报的是 opcode 而不是块 id：id 是随机串，对玩家没有意义。 */
	private static String opcodeOf(JsonObject blocks, String id) {
		JsonElement block = blocks.get(id);
		if (block != null && block.isJsonObject()) {
			JsonElement opcode = block.getAsJsonObject().get("opcode");
			if (opcode != null && !opcode.isJsonNull() && !opcode.getAsString().isBlank()) {
				return opcode.getAsString();
			}
		}
		return id;
	}

	/** target 的显示名：角色名对玩家最有意义，缺失时退回「某个角色」。 */
	private static String nameOf(JsonObject target) {
		JsonElement name = target.get("name");
		if (name != null && !name.isJsonNull() && !name.getAsString().isBlank()) {
			return name.getAsString();
		}
		return "（未命名角色）";
	}

	/** 异常信息可能很长，塞进 HUD 会把状态行挤爆。 */
	private static String brief(String message) {
		if (message == null || message.isBlank()) {
			return "格式错误";
		}
		String flat = message.replace('\n', ' ').strip();
		return flat.length() <= 60 ? flat : flat.substring(0, 60) + "…";
	}
}
