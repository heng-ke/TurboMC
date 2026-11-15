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

	// ---- 渲染帧级参数（见 ViewSmoother）----
	//
	// 上面那组是「每 tick 步进」，用于 tick 内必须当场写视角的场合。但原版第一人称
	// 的 getYaw(float) 不插值，每 tick 写一次就是 20 Hz 台阶；改为每渲染帧写一次，
	// 步长按帧间隔算，台阶才会消失。两组参数单位不同（度/tick 与 度/秒），不要混用。

	/** 帧级角速度上限（度/秒）。与 {@link #MAX_YAW_STEP} 等价：12°/tick × 20 tick/s。 */
	public static final float MAX_YAW_SPEED = 240f;
	/** 帧级俯仰速度上限（度/秒）。 */
	public static final float MAX_PITCH_SPEED = 300f;
	/** 帧级收敛系数：走路稳一点。 */
	public static final float GAIN_WALK = 10f;
	/** 帧级收敛系数：显式转向快一点。 */
	public static final float GAIN_TURN = 14f;


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

	/**
	 * 渲染帧级的步进：一阶指数收敛 + 按秒限速，返回本帧应加在角度上的增量。
	 *
	 * <p><b>为什么用指数而不是线性</b>：{@code 1 - exp(-k·dt)} 让收敛速度与帧率无关——
	 * 60 fps 与 144 fps 下走完同样角度用同样的时间。线性「每帧固定角度」在高帧率下会变慢、
	 * 低帧率下会变快，正是要避免的。</p>
	 *
	 * <p><b>限速为什么按秒</b>：{@code maxSpeed * dt} 才是「这一帧最多能转多少度」。
	 * 直接给每帧上限，等于把角速度绑死在帧率上。</p>
	 *
	 * <p>不越过目标：步长大于剩余角度时直接走完，否则会在目标附近来回摆。</p>
	 *
	 * @param diff     剩余角度（度，带符号，yaw 需先用 {@link #yawDiff} 折到 [-180,180]）
	 * @param dt       帧间隔（秒）；非有限或非正时返回 0
	 * @param gain     收敛系数，越大越快
	 * @param maxSpeed 角速度上限（度/秒）
	 * @return 本帧增量（度，带符号）；在死区内或参数非法时为 0
	 */
	public static float frameStep(float diff, float dt, float gain, float maxSpeed) {
		if (!Float.isFinite(diff) || !Float.isFinite(dt) || !Float.isFinite(gain)
				|| !Float.isFinite(maxSpeed) || dt <= 0f || gain <= 0f || maxSpeed <= 0f) {
			return 0f;
		}
		float magnitude = Math.abs(diff);
		if (magnitude <= DEAD_ZONE) {
			return 0f;
		}
		float wanted = magnitude * (1f - (float) Math.exp(-gain * dt));
		float limit = maxSpeed * dt;
		if (wanted > limit) {
			wanted = limit;
		}
		if (wanted > magnitude) {
			wanted = magnitude;
		}
		return diff > 0f ? wanted : -wanted;
	}
}
