package xyz.hegke.client.script.action;

import xyz.hegke.client.script.runtime.ScratchCast;
import xyz.hegke.client.script.runtime.ScratchMath;
import xyz.hegke.client.script.runtime.ScriptContext;
import xyz.hegke.client.script.runtime.Values;

import java.util.Locale;

/**
 * 纯数值与字符串函数，不依赖 Minecraft，单独注册以保持 GameActions 只处理游戏交互。
 *
 * <p>具体规则在 {@code runtime/ScratchMath} 与 {@code runtime/ScratchCast}，
 * 这里只做参数解包，保证与上游 Scratch 语义一致。</p>
 */
public final class MathFunctions {
	private MathFunctions() {
	}

	public static void registerAll() {
		NativeRegistry.function("operator.random", MathFunctions::random);
		NativeRegistry.function("operator.round", MathFunctions::round);
		NativeRegistry.function("operator.join", MathFunctions::join);
		NativeRegistry.function("operator.letter_of", MathFunctions::letterOf);
		NativeRegistry.function("operator.length", MathFunctions::length);
		NativeRegistry.function("operator.contains", MathFunctions::contains);
		NativeRegistry.function("operator.mathop", MathFunctions::mathop);
	}

	private static Object random(ScriptContext ctx, Object[] args) {
		return ScratchMath.random(Values.asNumber(args[0]), Values.asNumber(args[1]));
	}

	private static Object round(ScriptContext ctx, Object[] args) {
		return (double) Math.round(Values.asNumber(args[0]));
	}

	private static Object join(ScriptContext ctx, Object[] args) {
		return Values.asString(args[0]) + Values.asString(args[1]);
	}

	/**
	 * 第 n 个字符：1 起算，越界返回空串。
	 */
	private static Object letterOf(ScriptContext ctx, Object[] args) {
		int index = (int) Values.asNumber(args[0]) - 1;
		String text = Values.asString(args[1]);
		if (index < 0 || index >= text.length()) {
			return "";
		}
		return String.valueOf(text.charAt(index));
	}

	private static Object length(ScriptContext ctx, Object[] args) {
		return (double) Values.asString(args[0]).length();
	}

	/**
	 * 包含判断：大小写不敏感，与 Scratch 一致。
	 */
	private static Object contains(ScriptContext ctx, Object[] args) {
		String text = Values.asString(args[0]).toLowerCase(Locale.ROOT);
		String part = Values.asString(args[1]).toLowerCase(Locale.ROOT);
		return text.contains(part);
	}

	private static Object mathop(ScriptContext ctx, Object[] args) {
		return ScratchMath.mathop(ScratchCast.toString(args[0]), Values.asNumber(args[1]));
	}
}
