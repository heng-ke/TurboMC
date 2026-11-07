package xyz.turbomc.client.script.action.motion;

import net.minecraft.util.math.MathHelper;

/**
 * 朝向的每 tick 步进：比例收敛 + 限幅 + 死区。
 *
 * <p>「按固定角速度转」看起来就是机器：每 tick 精确转 12.000°，反作弊里是一条直线。
 * 这里用一阶比例控制——差得越多转得越快、接近时自动减速，天然是非线性的，
 * 也符合人手「快速甩过来再微调」的形状。</p>
 *
 * <p>死区不只是为了省事：节点方向在贴近时会有噪声，没有死区就会左右微摆。</p>
 */
public final class TurnMath {
	/** 比例系数：每 tick 走掉剩余角度的 45%。 */
	public static final float GAIN = 0.45f;
	/** 一 tick 最多转多少度。12°/tick = 240°/s，接近正常人快速回头。 */
	public static final float MAX_YAW_STEP = 12f;
	/** 俯仰变化视觉上更敏感，限得更低。 */
	public static final float MAX_PITCH_STEP = 10f;
	/** 保底步长：大角度时别因为 gain 小而拖尾。 */
	public static final float MIN_STEP = 1.2f;
	/** 死区：剩余角度小于它就不再转，避免噪声导致的微摆。 */
	public static final float DEAD_ZONE = 1.5f;

	private TurnMath() {
	}

	/** 这一 tick 应该加在 yaw 上的增量（度，带符号）。 */
	public static float yawStep(float diffDegrees) {
		return step(diffDegrees, MAX_YAW_STEP);
	}

	/** 这一 tick 应该加在 pitch 上的增量（度，带符号）。 */
	public static float pitchStep(float diffDegrees) {
		return step(diffDegrees, MAX_PITCH_STEP);
	}

	/** 目标角度与当前角度之差，yaw 需要按最短路径折回 [-180, 180]。 */
	public static float yawDiff(float current, float want) {
		return MathHelper.wrapDegrees(want - current);
	}

	private static float step(float diff, float maxStep) {
		// setYaw 遇到 NaN 会走 Util.logErrorOrPause（开发环境直接抛异常），必须先挡掉
		if (!Float.isFinite(diff)) {
			return 0f;
		}
		float magnitude = Math.abs(diff);
		if (magnitude <= DEAD_ZONE) {
			return 0f;
		}
		float wanted = MathHelper.clamp(magnitude * GAIN, Math.min(MIN_STEP, magnitude), maxStep);
		return diff > 0 ? wanted : -wanted;
	}
}
