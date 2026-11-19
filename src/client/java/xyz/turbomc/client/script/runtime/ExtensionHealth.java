package xyz.turbomc.client.script.runtime;

/**
 * 启动期自检的结果存放处：扩展产物与宿主的 opcode 集合对不上时，留一句给 HUD。
 *
 * <p>为什么不直接打日志：这两种不一致的症状都很像「模组坏了」——
 * 扩展旧了是「新积木拖不出来」，模组旧了是「积木点下去没反应」。
 * 日志在 F7 面板之外看不到，所以必须常驻在 HUD 上，玩家才知道该重载扩展还是换 jar。</p>
 *
 * <p>只存一句人类可读的话，不存集合：明细走日志，HUD 只放得下一行。</p>
 */
public final class ExtensionHealth {
	private static volatile String warning = "";

	private ExtensionHealth() {
	}

	/** 记录自检结论；空串表示一致（正常状态）。 */
	public static void report(String message) {
		warning = message == null ? "" : message;
	}

	public static String warning() {
		return warning;
	}
}
