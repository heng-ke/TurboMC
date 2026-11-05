package xyz.hegke.client.script.store;

/**
 * 一个脚本包：一份 sb3 工程文件。
 *
 * <p>在方案甲下，开关只作用在包这一层——整包加载或整包不加载。包内的脚本仅用于查看，
 * 不单独启停，因此不需要任何脚本级状态。</p>
 */
public final class ScriptPack {
	/** 目录下的实际文件名，例如 {@code 作品.sb3}。 */
	public final String fileName;
	/** 是否处于启用状态（同时只允许一个包启用）。 */
	public boolean enabled;

	public ScriptPack(String fileName, boolean enabled) {
		this.fileName = fileName;
		this.enabled = enabled;
	}

	/** 去掉扩展名的显示名。 */
	public String displayName() {
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

	@Override
	public String toString() {
		return fileName;
	}
}
