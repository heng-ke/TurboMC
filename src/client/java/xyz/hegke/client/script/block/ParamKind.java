package xyz.hegke.client.script.block;

/**
 * 参数种类，决定编辑器为该参数生成何种控件。
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
