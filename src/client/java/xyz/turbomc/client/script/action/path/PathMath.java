package xyz.turbomc.client.script.action.path;

/**
 * 「走向」里可以脱离客户端验算的四段算术：到达判定、会话超时、卡住窗口、氧气余量。
 *
 * <p>抽出来的理由不是好看，而是这四段错了都<b>不会抛异常</b>：找到达容差判反只是「永远到不了」，
 * 超时公式算错只是「远距离目标半路被判超时」，卡住窗口判错只是「明明在走却被重算」，
 * 氧气余量算小只是「水下提前中止」。真机上要复现得跑几十秒并盯日志，而它们全是纯算术——
 * 放这里用单测钉住，比在 {@link PathFollower} 里靠肉眼读快一个数量级。</p>
 *
 * <p><b>本类不许引用任何 Minecraft 类型</b>（含 {@code Vec3d} / {@code BlockPos} /
 * {@code MathHelper}）：一旦引用，单测就得起客户端，这层隔离立刻失效。所以坐标一律拆成
 * 裸 {@code double}，{@code clamp} 也自己写一份（语义与 {@code MathHelper.clamp} 一致）。</p>
 *
 * <p>这里只放「怎么判」，不放「判完做什么」——按键、重算、收尾仍在 {@link PathFollower} 里，
 * 因为它们要碰客户端状态。</p>
 */
public final class PathMath {
	private PathMath() {
	}

	// ---- 会话超时 ----

	/**
	 * 每格给多少 tick。128 格 × 0.216 格/tick ≈ 592 tick，绕路按 1.6 倍算 ≈ 950，
	 * 再加分段停顿的余量，由此定出下面的基数。
	 */
	public static final double TICKS_PER_BLOCK = 8.0;
	public static final int TICKS_BASE = 176;
	public static final int TICKS_MIN = 600;
	public static final int TICKS_MAX = 3600;
	/** 脚本侧等待上限比会话上限多留 40 tick，让 Java 侧的超时先发生。 */
	public static final int WAIT_SLACK = 40;

	/**
	 * 会话 tick 上限：{@code round(距离 × 8) + 176}，钳到 [600, 3600]。
	 *
	 * <p>128 格 → 1200，与改造前的固定值一致；距离越远上限越高。按距离算是因为
	 * 「最远目标距离」已可配置，只放大距离而不动超时，远目标会在走完前被判超时。</p>
	 */
	public static int sessionTicks(double distance) {
		return clamp((int) Math.round(distance * TICKS_PER_BLOCK) + TICKS_BASE, TICKS_MIN, TICKS_MAX);
	}

	/** 脚本侧等待上限：会话上限 + 40。多出来的 40 是让 Java 侧的超时先结束。 */
	public static int waitTicks(double distance) {
		return sessionTicks(distance) + WAIT_SLACK;
	}

	// ---- 到达判定 ----

	/**
	 * 是否已经站在 {@code (endX, endY, endZ)} 这一格上。
	 *
	 * <p>水平与垂直分开判，<b>不能用 3D 距离</b>：目标正上方 4 格的同一 (x,z) 水平距离是 0，
	 * 只判水平会立刻宣布到达。垂直容差要容得下半砖的 0.5、容不下整整一格。</p>
	 *
	 * <p>判的是路径终点而不是用户给的 target：A\* 会把终点吸到它认为可站的相邻格。</p>
	 */
	public static boolean arrived(double x, double y, double z,
			int endX, int endY, int endZ,
			double toleranceHorizontal, double toleranceVertical) {
		double dx = endX + 0.5 - x;
		double dz = endZ + 0.5 - z;
		return dx * dx + dz * dz <= toleranceHorizontal * toleranceHorizontal
				&& Math.abs(y - endY) <= toleranceVertical;
	}

	// ---- 卡住窗口 ----

	/** 真卡住多少 tick 后重算。 */
	public static final int STUCK_TICKS = 10;
	/** 锚点位移超过半格即认为有进展。 */
	public static final double ANCHOR_PROGRESS = 0.5;
	/** 单 tick 位移低于它才算「这一 tick 没动」。 */
	public static final double STUCK_MIN_MOVE = 0.02;
	/** 卡住重算上限。与半截路的分段次数分开计：这是「原地打转」，那是「够不到目标」。 */
	public static final int MAX_REPLANS = 3;
	/** 两次重算之间的节流。 */
	public static final int REPLAN_INTERVAL = 20;

	/**
	 * 一 tick 的窗口判定结果。
	 *
	 * @param progressed 相对锚点已经走出半格，窗口重置
	 * @param stuckTicks 更新后的静止计数
	 */
	public record Window(boolean progressed, int stuckTicks) {
	}

	/**
	 * 窗口式卡住判定的一步。
	 *
	 * <p><b>两个阈值管的是不同的事</b>，改动前先看清这一点：</p>
	 * <ul>
	 *   <li>{@link #STUCK_MIN_MOVE}（0.02）决定「这一 tick 算不算动过」——动过就不累加。
	 *       判的是<b>三维</b>位移：从台阶上掉下去只有 y 在变，那是真的在动，不该算卡住；</li>
	 *   <li>{@link #ANCHOR_PROGRESS}（0.5）决定「相对锚点走出半格就重置」——只看<b>水平</b>。
	 *       用三维的话，垂直下坠会被当成前进，人就绕着原地往下掉。</li>
	 * </ul>
	 *
	 * <p>由此可推出一个容易被注释误导的结论：每 tick 位移在
	 * {@code [0.02, 0.05)} 之间时计数器根本不动（永不判卡），而低于 0.02 时
	 * 10 tick 最多攒 0.2 格、够不到 0.5，锚点分支救不回来。也就是说「贴墙蠕动不会被误判」
	 * 只在每 tick ≥ 0.02 时成立。</p>
	 */
	public static Window stuckWindow(double anchorX, double anchorZ,
			double lastX, double lastY, double lastZ,
			double nowX, double nowY, double nowZ, int stuckTicks) {
		if (horizontal(anchorX, anchorZ, nowX, nowZ) >= ANCHOR_PROGRESS) {
			return new Window(true, 0);
		}
		boolean moved = distance(lastX, lastY, lastZ, nowX, nowY, nowZ) >= STUCK_MIN_MOVE;
		return new Window(false, moved ? stuckTicks : stuckTicks + 1);
	}

	/** 卡住之后怎么处置。 */
	public enum Replan {
		/** 重算次数用尽，本次会话放弃。 */
		GIVE_UP,
		/** 距上次重算不足节流间隔，本轮先跳过（计数已清零，下个窗口再试）。 */
		WAIT,
		/** 允许重算。 */
		REPLAN
	}

	/**
	 * 卡住后的重算决策。顺序是固定的：先看次数上限，再看节流——反过来的话，
	 * 第 4 次卡住会先被节流吞掉，一直不放弃。
	 */
	public static Replan replanStep(int replans, int ticksSinceLastReplan) {
		if (replans >= MAX_REPLANS) {
			return Replan.GIVE_UP;
		}
		if (ticksSinceLastReplan < REPLAN_INTERVAL) {
			return Replan.WAIT;
		}
		return Replan.REPLAN;
	}

	// ---- 氧气余量 ----

	/** 每格深度给多少 tick（水下上浮约 0.1 格/tick，保守取值）。 */
	public static final int AIR_PER_BLOCK = 10;
	/** 反应时间。 */
	public static final int AIR_BASE = 40;
	public static final int AIR_MIN = 80;
	/** 满氧 300 tick，上限压到 280：更深的水本来就浮不上来，早点中止比硬走好。 */
	public static final int AIR_MAX = 280;

	/**
	 * 按深度算需要的氧气余量：{@code 深度 × 10 + 40}，钳到 [80, 280]。
	 *
	 * @param depth 水面到所在方块层的格数；调用方已保证 ≥ 1，这里再兜一次
	 */
	public static int airReserveForDepth(int depth) {
		return clamp(Math.max(1, depth) * AIR_PER_BLOCK + AIR_BASE, AIR_MIN, AIR_MAX);
	}

	// ---- 内部 ----

	/** 语义与 {@code MathHelper.clamp(int,int,int)} 一致；自己写一份是为了不引 MC 类型。 */
	public static int clamp(int value, int min, int max) {
		return value < min ? min : Math.min(value, max);
	}

	/** 坐标/参数钳制：NaN（参数缺失或填了非数字）落到下限，其余夹在区间内。 */
	public static double clamp(double value, double min, double max) {
		if (Double.isNaN(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}

	/** 两点间的水平距离（忽略 y）。 */
	public static double horizontal(double ax, double az, double bx, double bz) {
		double dx = ax - bx;
		double dz = az - bz;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * 从 (ax, az) 看向 (bx, bz) 的 yaw（度）。
	 *
	 * <p>两点重合时返回 0；需要「保持当前朝向」的调用方自己先判距离
	 * （见 {@code TurnSession.yawTo} 的 {@code MIN_AIM_DISTANCE}）。</p>
	 */
	public static float yawTo(double ax, double az, double bx, double bz) {
		return (float) Math.toDegrees(Math.atan2(-(bx - ax), bz - az));
	}

	private static double distance(double ax, double ay, double az,
			double bx, double by, double bz) {
		double dx = ax - bx;
		double dy = ay - by;
		double dz = az - bz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
