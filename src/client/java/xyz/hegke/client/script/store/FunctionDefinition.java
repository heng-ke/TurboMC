package xyz.hegke.client.script.store;

import xyz.hegke.client.script.node.BlockNode;

/**
 * 用户自定义积木（无返回值命令块）。
 *
 * <p>参数固定为 {@code $p1} ~ {@code $p3} 三个槽，函数体内用「读取变量（局部）」访问。</p>
 */
public final class FunctionDefinition {
	public String name = "fn";
	public BlockNode body;

	public FunctionDefinition() {
	}

	public static FunctionDefinition create(String name) {
		FunctionDefinition definition = new FunctionDefinition();
		definition.name = name;
		return definition;
	}
}
