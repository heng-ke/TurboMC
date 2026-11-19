package xyz.turbomc.client.script.vm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import xyz.turbomc.client.script.action.JsonText;
import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.runtime.Values;

/**
 * 「JSON」里直接读写 Scratch 列表的两块积木。
 *
 * <p><b>为什么放在 vm 包、由 RhinoVm 注册</b>：这两块的实现必须访问 VM 内部的
 * {@code target.variables}，而那个出口是 {@link RhinoVm}（它已经有 {@code getVariable} /
 * {@code variableDump} 这类只读出口）。放进 action 包会让 {@code action → vm} 多一条新依赖边，
 * 而 {@code vm → action}（ScriptBridge 找 NativeRegistry）本来就有。注册点放在
 * {@link RhinoVm#init()} 里，依赖方向就与现状一致。</p>
 *
 * <p>积木本身仍然走「桥 → NativeRegistry → 功能函数」这条唯一路径，没有在积木包装器里
 * 开第二个分支——包装器是每 tick 最热的代码，多一个判断就多一份维护成本。</p>
 */
public final class VmListActions {
	private VmListActions() {
	}

	public static void registerAll() {
		NativeRegistry.function("json.vm_getlist", (ctx, args) -> {
			String name = Values.asString(args[0]);
			String json = RhinoVm.get().readList(name);
			// 列表不存在给空串；存在但为空给 "[]"——两者语义不同，不能合并
			return json == null ? "" : json;
		});
		NativeRegistry.function("json.vm_setlist", (ctx, args) -> {
			String name = Values.asString(args[0]);
			JsonElement root = JsonText.parse(Values.asString(args[1]));
			if (root == null || !root.isJsonArray()) {
				return null;
			}
			RhinoVm.get().writeList(name, toScratchList(root.getAsJsonArray()));
			return null;
		});
	}

	/**
	 * 列表项只能是标量：对象与数组转成 JSON 文本，{@code null} 变空串。
	 *
	 * <p>Scratch 列表存不下结构，直接塞进去只会得到 {@code [object Object]}。
	 * 转成 JSON 文本后，再经「把列表读成数组」取回来仍是可解析的一串，
	 * 结构在这一进一出之间没有丢失。</p>
	 */
	private static String toScratchList(JsonArray array) {
		JsonArray safe = new JsonArray();
		for (JsonElement element : array) {
			if (element == null || element.isJsonNull()) {
				safe.add("");
			} else if (element.isJsonObject() || element.isJsonArray()) {
				safe.add(JsonText.stringify(element));
			} else {
				safe.add(element);
			}
		}
		return JsonText.stringify(safe);
	}
}
