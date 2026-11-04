package xyz.hegke.client.script.block;

import java.util.List;

/**
 * 积木类型元数据。编译器、编辑器与选择面板共用同一份定义。
 *
 * @param id       唯一标识，例如 {@code control.repeat}
 * @param category 分类，用于界面分组
 * @param shape    外形与连接规则
 * @param params   可编辑参数规格，编辑器据此生成控件
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
