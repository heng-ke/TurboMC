package xyz.turbomc.client.script.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「说 / 思考」气泡的簿记。
 *
 * <p>气泡状态来自 scratch-vm 的 target：{@code looks_say} 把 {@code (type, text, usageId)}
 * 写在角色身上，下一次说话或「停止全部」才覆盖。这里的问题在于<b>过期</b>——
 * 如果只按「气泡到期就删」实现，删掉之后 VM 仍然在报同一句，下一帧又会被当成新气泡
 * 重新登记，形成「删掉即复活」，气泡就再也不会消失（DEF-06）。</p>
 *
 * <p>因此过期时除了删显示，还要把这个 {@code usageId} 记进 {@link #retired}：
 * VM 每次说话都会换一个 usageId（{@code _updateBubble} 里现生成），所以记住旧 id
 * 不会挡住玩家下一次说话，只会挡住「同一条已播完的消息被重新登记」。</p>
 *
 * <p>本类不依赖任何 Minecraft 类型，纯状态机，可直接单测。</p>
 */
final class SpeechBubbleBook {
	/** 记住多少个已播完的 usageId。VM 只会报当前这一句，留几十个足够，且不会无限涨。 */
	private static final int MAX_RETIRED = 64;

	/** 一句停留多久。 */
	private final long holdMs;
	/** 角色名 → 当前气泡。scratch-vm 里气泡按 target 存，同一角色只会有一条。 */
	private final Map<String, Bubble> current = new LinkedHashMap<>();
	/** 已经播完（或已被新的覆盖）的 usageId，用于判定「不该复活」。 */
	private final Set<String> retired = new LinkedHashSet<>();

	SpeechBubbleBook(long holdMs) {
		this.holdMs = holdMs;
	}

	/** 显示单位：一行「角色名：内容」。 */
	record Bubble(String name, String text, boolean think, String usageId, long updatedAt) {
	}

	/** 整块清空：离开世界 / 总开关关闭时用，连记忆一起丢。 */
	void clear() {
		current.clear();
		retired.clear();
	}

	/**
	 * 登记 VM 上报的一句中。
	 *
	 * <p>三种情况什么都不做：名字或内容为空（VM 停止说话时会把 text 置空）、
	 * 这个 usageId 已经播完、以及该角色当前显示的就是这一句（每帧都会重复上报）。</p>
	 */
	void poll(String name, String text, boolean think, String usageId, long now) {
		if (name == null || name.isEmpty() || text == null || text.isEmpty()) {
			return;
		}
		if (usageId != null && retired.contains(usageId)) {
			return;
		}
		Bubble existing = current.get(name);
		if (existing != null && existing.usageId().equals(usageId)) {
			return;
		}
		if (existing != null) {
			// 被新的一句顶掉：旧 id 同样不该再回来
			retire(existing.usageId());
		}
		current.put(name, new Bubble(name, text, think, usageId, now));
	}

	/** 到点的气泡移除，并记住它的 usageId，避免下一帧被重新登记。 */
	void expire(long now) {
		Iterator<Map.Entry<String, Bubble>> it = current.entrySet().iterator();
		while (it.hasNext()) {
			Bubble bubble = it.next().getValue();
			if (now - bubble.updatedAt() > holdMs) {
				it.remove();
				retire(bubble.usageId());
			}
		}
	}

	/** 当前气泡快照，按登记顺序返回。 */
	List<Bubble> snapshot() {
		return new ArrayList<>(current.values());
	}

	/** 渲染视图：显示哪几条（旧的在前面）、还有几条被挤掉。 */
	record View(List<Bubble> shown, int hidden) {
	}

	/**
	 * 按「新的优先」挑出最多 {@code limit} 条，并报出被挤掉的数量。
	 *
	 * <p>时间戳相同的按登记顺序排（稳定排序），所以同一 tick 内说话的多个角色里，
	 * <b>最早登记的会先被挤掉</b>。这不是缺陷：HUD 是叠在画面上的，条数必须封顶；
	 * 但挤掉之后要能用 {@code hidden} 告诉玩家「还有人说过话」，
	 * 否则四个角色同时说话只看到三条，会以为第四个没说话。</p>
	 */
	View view(int limit) {
		List<Bubble> ordered = snapshot();
		ordered.sort(Comparator.comparingLong(Bubble::updatedAt));
		int cap = Math.max(1, limit);
		if (ordered.size() <= cap) {
			return new View(List.copyOf(ordered), 0);
		}
		List<Bubble> shown = new ArrayList<>(ordered.subList(ordered.size() - cap, ordered.size()));
		return new View(List.copyOf(shown), ordered.size() - cap);
	}

	int size() {
		return current.size();
	}

	private void retire(String usageId) {
		if (usageId == null || usageId.isEmpty()) {
			return;
		}
		if (retired.add(usageId) && retired.size() > MAX_RETIRED) {
			Iterator<String> it = retired.iterator();
			it.next();
			it.remove();
		}
	}
}
