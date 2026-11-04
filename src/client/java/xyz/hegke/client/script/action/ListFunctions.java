package xyz.hegke.client.script.action;

import xyz.hegke.client.script.runtime.ScratchCompare;
import xyz.hegke.client.script.runtime.ScratchMath;
import xyz.hegke.client.script.runtime.ScriptContext;
import xyz.hegke.client.script.runtime.Values;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 列表操作。列表按名称存放在全局命名空间，索引从 1 开始（与 Scratch 一致）。
 *
 * <p>下标越界时读项返回空字符串、删除与替换静默忽略，避免脚本因边界问题整体失败。</p>
 */
public final class ListFunctions {
	private static final Map<String, List<Object>> LISTS = new HashMap<>();

	private ListFunctions() {
	}

	public static void registerAll() {
		NativeRegistry.action("list.add", ListFunctions::add);
		NativeRegistry.action("list.insert", ListFunctions::insert);
		NativeRegistry.action("list.delete", ListFunctions::delete);
		NativeRegistry.action("list.replace", ListFunctions::replace);
		NativeRegistry.action("list.clear", ListFunctions::clear);
		NativeRegistry.function("list.item", ListFunctions::item);
		NativeRegistry.function("list.length", ListFunctions::length);
		NativeRegistry.function("list.contains", ListFunctions::contains);
		NativeRegistry.function("list.contents", ListFunctions::contents);
		NativeRegistry.function("list.item_num", ListFunctions::itemNum);
	}

	/**
	 * 「列表中第一个 [项] 的编号」：1 起算，找不到返回 0，比较沿用 Scratch 的宽松相等。
	 */
	private static Object itemNum(ScriptContext ctx, Object[] args) {
		List<Object> list = listOf(args[0]);
		Object target = args[1];
		for (int i = 0; i < list.size(); i++) {
			if (looseEquals(list.get(i), target)) {
				return (double) (i + 1);
			}
		}
		return 0d;
	}

	/**
	 * 「列表内容」：与 Scratch 一致——全是单字符则无分隔拼接，否则用空格拼接。
	 */
	private static Object contents(ScriptContext ctx, Object[] args) {
		List<Object> list = listOf(args[0]);
		boolean allSingleLetters = true;
		for (Object item : list) {
			if (!(item instanceof String text) || text.length() != 1) {
				allSingleLetters = false;
				break;
			}
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			if (i > 0 && !allSingleLetters) {
				builder.append(' ');
			}
			builder.append(Values.asString(list.get(i)));
		}
		return builder.toString();
	}

	/**
	 * 引擎启动时清空全部列表，与全局变量保持一致的生命周期。
	 */
	public static void clearAll() {
		LISTS.clear();
	}

	public static Map<String, Integer> sizes() {
		Map<String, Integer> result = new HashMap<>();
		LISTS.forEach((name, list) -> result.put(name, list.size()));
		return result;
	}

	private static List<Object> listOf(Object name) {
		return LISTS.computeIfAbsent(Values.asString(name), key -> new ArrayList<>());
	}

	private static void add(ScriptContext ctx, Object[] args) {
		listOf(args[0]).add(args[1]);
	}

	private static void insert(ScriptContext ctx, Object[] args) {
		List<Object> list = listOf(args[0]);
		int index = ScratchMath.listIndex(args[1], list.size() + 1, false);
		if (index == ScratchMath.LIST_INVALID) {
			return;
		}
		list.add(index - 1, args[2]);
	}

	private static void delete(ScriptContext ctx, Object[] args) {
		List<Object> list = listOf(args[0]);
		int index = ScratchMath.listIndex(args[1], list.size(), true);
		if (index == ScratchMath.LIST_ALL) {
			list.clear();
			return;
		}
		if (index == ScratchMath.LIST_INVALID) {
			return;
		}
		list.remove(index - 1);
	}

	private static void replace(ScriptContext ctx, Object[] args) {
		List<Object> list = listOf(args[0]);
		int index = ScratchMath.listIndex(args[1], list.size(), false);
		if (index == ScratchMath.LIST_INVALID) {
			return;
		}
		list.set(index - 1, args[2]);
	}

	private static void clear(ScriptContext ctx, Object[] args) {
		listOf(args[0]).clear();
	}

	private static Object item(ScriptContext ctx, Object[] args) {
		List<Object> list = listOf(args[0]);
		int index = ScratchMath.listIndex(args[1], list.size(), false);
		if (index == ScratchMath.LIST_INVALID) {
			return "";
		}
		return list.get(index - 1);
	}

	private static Object length(ScriptContext ctx, Object[] args) {
		return (double) listOf(args[0]).size();
	}

	private static Object contains(ScriptContext ctx, Object[] args) {
		List<Object> list = listOf(args[0]);
		Object target = args[1];
		for (Object candidate : list) {
			if (looseEquals(candidate, target)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 与 Scratch 的宽松相等一致：数字优先，文本按大小写不敏感比较。
	 */
	private static boolean looseEquals(Object left, Object right) {
		return ScratchCompare.compare(left, right) == 0;
	}
}
