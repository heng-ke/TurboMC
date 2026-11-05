package xyz.turbomc.client.script.store;

import xyz.turbomc.client.script.node.BlockNode;

/**
 * 从 sb3 解析出的一份脚本：名称、所属角色、入口积木链。
 *
 * <p>不再持久化，也不再带启用状态——启停是包级的事。它只是导入结果的视图模型，
 * 供列表与只读查看器使用。</p>
 */
public final class ScriptDefinition {
	public String name = "untitled";
	public BlockNode hat = BlockNode.of("event.tick");
	/**
	 * 角色作用域标识：同一 sb3 角色下的多个脚本共享它，对应 Scratch 的
	 * 「仅适用于当前角色」变量。导入时取角色名。
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
