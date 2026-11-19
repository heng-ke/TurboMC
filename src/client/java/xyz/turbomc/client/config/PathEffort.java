package xyz.turbomc.client.config;

/**
 * 「走向」的 A\* 搜索强度档位，映射到「每格节点预算」。
 *
 * <p>为什么做成档位而不是直接暴露数字：{@code PathNodeNavigator} 的预算决定
 * 「一次搜索最多展开多少节点」。调小会在绕行时撞顶并静默返回半截路，
 * 调大则单帧规划耗时上升；这个取舍对使用者是抽象的，给三档即可，不必让人记住 14 这种魔数。</p>
 *
 * <p>档位与 {@code PathPlanner} 里的 {@code BUDGET_MIN}/{@code BUDGET_MAX} 一起生效：
 * 实际预算 = {@code clamp(距离 × 系数, 128, 3000)}。</p>
 */
public enum PathEffort {
	/** 轻量：近距离够用，绕行地形容易撞顶转成分段接近。 */
	LIGHT(8),
	/** 默认，与原实现一致。 */
	NORMAL(14),
	/** 重量：复杂地形一次算通，代价是单帧规划耗时上升。 */
	HEAVY(22);

	private final int budgetPerBlock;

	PathEffort(int budgetPerBlock) {
		this.budgetPerBlock = budgetPerBlock;
	}

	/** 每格目标距离分配多少个 A\* 节点预算。 */
	public int budgetPerBlock() {
		return budgetPerBlock;
	}
}
