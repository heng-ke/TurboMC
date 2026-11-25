package xyz.turbomc.client.script.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

	/** 一条可见监视器：名字 + 类型。变量与列表分开登记，同名不会互相顶掉。 */
	public record Entry(String name, Kind kind) {
	}

	/** 键是「类型 + 分隔符 + 名字」：同名变量与列表各占一条（P3）。 */
	private static final Map<String, Entry> VISIBLE = new LinkedHashMap<>();

	private MonitorStore() {
	}

	public static synchronized void set(Kind kind, String name, boolean visible) {
		if (name == null || name.isBlank()) {
			return;
		}
		String key = kind.name() + "|" + name;
		if (visible) {
			VISIBLE.put(key, new Entry(name, kind));
		} else {
			VISIBLE.remove(key);
		}
	}

	/**
	 * 快照：渲染时遍历，不与脚本线程的登记动作互相影响。
	 */
	public static synchronized List<Entry> snapshot() {
		return new ArrayList<>(VISIBLE.values());
	}

	/** 按名字查类型；同名变量与列表都存在时返回先登记的那条。 */
	public static synchronized Kind kindOf(String name) {
		for (Entry entry : VISIBLE.values()) {
			if (entry.name().equals(name)) {
				return entry.kind();
			}
		}
		return null;
	}

	/** 引擎停止或切换工程时清空——上一份工程的监视器不该留在画面上。 */
	public static synchronized void clear() {
		VISIBLE.clear();
	}
}
