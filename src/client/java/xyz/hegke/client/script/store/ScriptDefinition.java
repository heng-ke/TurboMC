package xyz.hegke.client.script.store;

import xyz.hegke.client.script.node.BlockNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 一份用户脚本：名称、启用状态、入口积木链、自定义积木定义。
 */
public final class ScriptDefinition {
	public String name = "untitled";
	public boolean enabled = false;
	public BlockNode hat = BlockNode.of("event.tick");
	public List<FunctionDefinition> functions = new ArrayList<>();
	/**
	 * 角色作用域标识：同一 sb3 角色导出的多个脚本共享它，对应 Scratch 的
	 * 「仅适用于当前角色」变量。编辑器新建的脚本默认用自身名字。
	 */
	public String scopeId = "";

	public ScriptDefinition() {
	}

	public static ScriptDefinition create(String name, BlockNode hat) {
		ScriptDefinition definition = new ScriptDefinition();
		definition.name = name;
		definition.hat = hat;
		definition.scopeId = name;
		return definition;
	}
}
