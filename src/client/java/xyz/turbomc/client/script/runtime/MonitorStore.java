package xyz.turbomc.client.script.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD 监视器登记表：脚本执行「显示 / 隐藏 变量、列表」后记录在这里。
 *
 * <p>scratch-vm 的监视器属于舞台 GUI，游戏内没有那一层，因此改由 HUD 承担：
 * 本表只记「哪些名字要显示」与「是变量还是列表」，值在渲染时现读 VM。</p>
 *
 * <p>用 {@link LinkedHashMap} 而不是 {@code HashMap}：显示顺序应当稳定，
 * 否则每帧重排会让画面抖动。</p>
 */
public final class MonitorStore {
	public enum Kind {
		VARIABLE, LIST
	}

	private static final Map<String, Kind> VISIBLE = new LinkedHashMap<>();

	private MonitorStore() {
	}

	public static synchronized void set(Kind kind, String name, boolean visible) {
		if (name == null || name.isBlank()) {
			return;
		}
		if (visible) {
			VISIBLE.put(name, kind);
		} else {
			VISIBLE.remove(name);
		}
	}

	/**
	 * 快照：渲染时遍历，不与脚本线程的登记动作互相影响。
	 */
	public static synchronized Map<String, Kind> snapshot() {
		return new LinkedHashMap<>(VISIBLE);
	}

	/** 引擎停止或切换工程时清空——上一份工程的监视器不该留在画面上。 */
	public static synchronized void clear() {
		VISIBLE.clear();
	}
}
