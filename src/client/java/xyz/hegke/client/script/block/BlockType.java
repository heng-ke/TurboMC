package xyz.hegke.client.script.block;

import java.util.List;

/**
 * 积木类型元数据，用于把 sb3 的积木渲染成一行可读文本（脚本树查看器）。
 *
 * @param id       唯一标识，例如 {@code control.repeat}
 * @param category 分类，仅用于配色等展示信息
 * @param shape    外形，决定渲染时的视觉归类
 * @param params   参数规格，用于把参数值填进模板（不生成输入控件）
 * @param template 显示模板，如 {@code 重复 {TIMES} 次}；空串表示回退到「积木名 参数=值」
 */
public record BlockType(String id, String category, BlockShape shape, List<BlockParam> params,
						String template) {

	public BlockType(String id, String category, BlockShape shape, List<BlockParam> params) {
		this(id, category, shape, params, "");
	}

	public BlockType(String id, String category, BlockShape shape) {
		this(id, category, shape, List.of(), "");
	}

	public boolean hasTemplate() {
		return template != null && !template.isEmpty();
	}

	public BlockParam param(String name) {
		for (BlockParam param : params) {
			if (param.name().equals(name)) {
				return param;
			}
		}
		return null;
	}
}
