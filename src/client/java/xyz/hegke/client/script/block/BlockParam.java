package xyz.hegke.client.script.block;

import java.util.List;

/**
 * 积木参数规格。
 *
 * @param name         参数名，须与编译器读取的字段/输入名一致
 * @param field           {@code true} 表示积木字段（默认值存于 fields），否则为输入（存于 inputs）
 * @param kind            控件种类
 * @param defaultValue    新建积木时的默认值
 * @param options         枚举选项，仅 {@link ParamKind#ENUM} 使用
 * @param acceptsReporter 字段是否允许被表达式替换，对应 Scratch 的 {@code acceptReporters}。
 *                        为 {@code false} 时，若导入的 sb3 在该位置放了积木，会被记为「丢弃」而不是静默塞进 inputs
 */
public record BlockParam(String name, boolean field, ParamKind kind, String defaultValue,
						 List<String> options, boolean acceptsReporter) {

	public static BlockParam text(String name, String defaultValue) {
		return new BlockParam(name, false, ParamKind.TEXT, defaultValue, List.of(), false);
	}

	public static BlockParam fieldText(String name, String defaultValue) {
		return new BlockParam(name, true, ParamKind.TEXT, defaultValue, List.of(), false);
	}

	/**
	 * 文本字段，但允许被表达式替换（等价于 Scratch 里 {@code acceptReporters: true} 的菜单/文本）。
	 *
	 * <p>导入时字面量仍落 {@code fields}，积木则落 {@code inputs}；编译期由
	 * {@code Compiler.pushParam} 统一读取，两种写法都能生效。</p>
	 */
	public static BlockParam fieldOrExpr(String name, String defaultValue) {
		return new BlockParam(name, true, ParamKind.TEXT, defaultValue, List.of(), true);
	}

	public static BlockParam number(String name, String defaultValue) {
		return new BlockParam(name, false, ParamKind.NUMBER, defaultValue, List.of(), false);
	}

	public static BlockParam bool(String name, String defaultValue) {
		return new BlockParam(name, false, ParamKind.BOOLEAN, defaultValue, List.of(), false);
	}

	public static BlockParam option(String name, String defaultValue, String... options) {
		return new BlockParam(name, false, ParamKind.ENUM, defaultValue, List.of(options), false);
	}

	public static BlockParam fieldOption(String name, String defaultValue, String... options) {
		return new BlockParam(name, true, ParamKind.ENUM, defaultValue, List.of(options), false);
	}
}
