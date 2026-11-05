package xyz.hegke.client.script.block;

/**
 * 参数种类，决定参数值在查看器里的呈现方式（例如枚举值需要本地化）。
 */
public enum ParamKind {
	/** 任意文本。 */
	TEXT,
	/** 数值文本。 */
	NUMBER,
	/** 布尔开关。 */
	BOOLEAN,
	/** 有限选项，点击循环切换。 */
	ENUM
}
