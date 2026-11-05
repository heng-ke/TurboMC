package xyz.turbomc.client.script.block;

/**
 * 积木外形。仅用于查看时的视觉归类，不参与任何拼接规则。
 */
public enum BlockShape {
	/** 事件积木，只能位于脚本顶部。 */
	HAT,
	/** 普通语句积木，可上下堆叠。 */
	STACK,
	/** C 形语句积木，内部可容纳子序列。 */
	C_SHAPE,
	/** 表达式积木，返回任意值。 */
	REPORTER,
	/** 表达式积木，返回布尔值。 */
	BOOLEAN
}
