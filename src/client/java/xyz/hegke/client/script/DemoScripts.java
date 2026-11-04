package xyz.hegke.client.script;

import xyz.hegke.client.script.node.BlockNode;

/**
 * 内置演示脚本，用于验证「触发 → 判断 → 查询 → 动作 → 等待」整条链路。
 *
 * <p>语义：瞄准到实体方块就挖掘，否则向前走 10 tick 再停下 10 tick。</p>
 */
public final class DemoScripts {
	private DemoScripts() {
	}

	public static BlockNode mineWalk() {
		BlockNode forever = BlockNode.of("control.forever").branch("SUBSTACK",
				BlockNode.of("control.if")
						.input("CONDITION", BlockNode.of("world.block_in_front"))
						.branch("SUBSTACK", BlockNode.of("action.tap")
								.field("KEY", "mouse.left")
								.input("HOLD", "20")),
				BlockNode.of("action.key").field("KEY", "w").input("PRESSED", "true"),
				BlockNode.of("control.wait").input("TICKS", "10"),
				BlockNode.of("action.key").field("KEY", "w").input("PRESSED", "false"),
				BlockNode.of("control.wait").input("TICKS", "10")
		);

		return BlockNode.of("event.tick").then(forever);
	}
}
