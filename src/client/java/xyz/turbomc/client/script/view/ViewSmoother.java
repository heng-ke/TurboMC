package xyz.turbomc.client.script.view;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import xyz.turbomc.client.script.action.motion.TurnMath;

/**
 * 视角平滑器：把「目标角度」在<b>渲染帧</b>上逐步兑现，而不是每 tick 写一次。
 *
 * <p><b>为什么必须放在渲染帧</b>：原版第一人称的 {@code ClientPlayerEntity#getYaw(float)}
 * 在非骑乘时直接返回原始 yaw（相机不做 tick 内插值，见 ARCHITECTURE §13），
 * 所以 tick 里每 50 ms 写一次，画面就是每 3 帧（60 fps）跳一格。改成每帧写一次、
 * 步长按帧间隔算，相机每帧都能读到新值，台阶自然消失。整条路径不需要 mixin。</p>
 *
 * <p><b>分工</b>：tick 层只产出目标（{@link #setWalkTarget} / {@link #setTurnTarget}），
 * 本类负责「怎么过去」。目标在两个 tick 之间保持不变，帧层持续收敛，
 * 因此节点切换造成的目标跳变也会被抹平。</p>
 *
 * <p><b>四种归属</b>：</p>
 * <ul>
 *   <li>{@link Mode#WALK}：寻路会话持有，只控 yaw；</li>
 *   <li>{@link Mode#TURN}：平滑转向会话持有，yaw + pitch；</li>
 *   <li>{@link Mode#MANUAL}：「看向 / 看向坐标」积木直接写值后占用，<b>一直保持到下一次
 *       走向或转向开始</b>（对应 Scratch 里 point in direction 的持久语义）；</li>
 *   <li>{@link Mode#NONE}：不控制，视角完全归玩家与原版。</li>
 * </ul>
 *
 * <p><b>玩家接管</b>：每帧比对「上一帧我们写下的 yaw」与「当前 yaw」，不一致就说明
 * 有别人（鼠标、其他模组）改过，本帧让位、下一帧继续。不用 {@code mouse.getX()}：
 * 锁光标（FPS 模式）下光标坐标不一定变化，yaw 的变化才是可靠信号。</p>
 */
public final class ViewSmoother {
	public enum Mode {
		/** 不控制视角。 */
		NONE,
		/** 寻路会话持有。 */
		WALK,
		/** 平滑转向会话持有。 */
		TURN,
		/** 积木直接写入后占用，保持到下一次走向 / 转向开始。 */
		MANUAL
	}

	/** 单帧 dt 上限：卡顿帧不产生一次大跳。 */
	private static final float MAX_DT = 0.05f;
	/** 首帧没有上一帧时间戳时按 60 fps 估。 */
	private static final float DEFAULT_DT = 1f / 60f;
	private static final float PITCH_LIMIT = 90f;
	/**
	 * 判定「有别人改过 yaw」的阈值。
	 *
	 * <p>太小会被浮点噪声误触，太大则玩家轻推鼠标抢不回来。0.05° 对应灵敏度 0.5 下
	 * 不到 1 px 的鼠标位移，足够灵敏。</p>
	 */
	private static final float EXTERNAL_EPSILON = 0.05f;

	private static Mode mode = Mode.NONE;
	private static float targetYaw;
	private static float targetPitch;
	/** 只有 TURN / MANUAL 才控 pitch；走路保持原版俯仰。 */
	private static boolean pitchActive;

	private static long lastFrameNanos;
	/** 上一帧我们写下的 yaw；NaN 表示还没有可比对的基准。 */
	private static float lastAppliedYaw = Float.NaN;

	private ViewSmoother() {
	}

	// ---- 目标登记 ----

	/**
	 * 登记走路时的目标朝向（只控 yaw）。
	 *
	 * <p>MANUAL / TURN 期间只更新目标、不夺取归属：显式转向优先级最高，
	 * 「看向」按 D5-B 保持到下一次会话开始。</p>
	 */
	public static void setWalkTarget(float yaw) {
		targetYaw = yaw;
		pitchActive = false;
		if (mode != Mode.TURN && mode != Mode.MANUAL) {
			mode = Mode.WALK;
		}
	}

	/** 登记显式转向的目标（yaw + pitch），并夺取归属。 */
	public static void setTurnTarget(float yaw, float pitch) {
		targetYaw = yaw;
		targetPitch = pitch;
		pitchActive = true;
		mode = Mode.TURN;
	}

	/**
	 * 积木「看向 / 看向坐标」：立即写值，然后以 MANUAL 占用，保持到下一次会话开始。
	 *
	 * <p>取舍（D5-B）：脚本可以自由改视角，但移动方向由 yaw 派生，所以走向会把角色
	 * 带向新的朝向——这是选择「看向接管视角」的既定代价，不是缺陷。</p>
	 */
	public static void applyManual(MinecraftClient client, float yaw, float pitch) {
		if (client == null || client.player == null) {
			return;
		}
		// setYaw 遇到 NaN 会走 Util.logErrorOrPause（开发环境直接抛异常）
		if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
			return;
		}
		float clampedPitch = MathHelper.clamp(pitch, -PITCH_LIMIT, PITCH_LIMIT);
		client.player.setYaw(yaw);
		client.player.setPitch(clampedPitch);
		targetYaw = yaw;
		targetPitch = clampedPitch;
		pitchActive = true;
		mode = Mode.MANUAL;
		lastAppliedYaw = yaw;
	}

	/**
	 * 丢弃目标、停止控制。
	 *
	 * <p>引擎停止、切工程、断开连接、会话结束时调用。不这么做的话脚本停了，
	 * 镜头还会继续朝旧目标转。</p>
	 */
	public static void clear(String reason) {
		if (mode != Mode.NONE) {
			mode = Mode.NONE;
		}
		pitchActive = false;
		lastFrameNanos = 0L;
		lastAppliedYaw = Float.NaN;
	}

	// ---- 帧回调 ----

	/**
	 * 每渲染帧调用一次（{@code WorldRenderEvents.START_MAIN}）。
	 *
	 * <p>与 tick 同为主线程，无并发；不做加锁。</p>
	 */
	public static void onRenderFrame(MinecraftClient client) {
		if (client == null || client.player == null || client.isPaused()) {
			// 暂停期间 dt 会累积成一个大值，清零避免恢复瞬间的一次大跳
			lastFrameNanos = 0L;
			return;
		}
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0L
				? DEFAULT_DT
				: (now - lastFrameNanos) / 1_000_000_000f;
		lastFrameNanos = now;
		dt = MathHelper.clamp(dt, 0f, MAX_DT);
		if (dt <= 0f) {
			return;
		}

		if (mode == Mode.NONE) {
			return;
		}

		float currentYaw = client.player.getYaw();
		// 有人在我们之外改过 yaw（鼠标 / 其它模组）：本帧让位，基准跟过去，下一帧继续
		if (!Float.isNaN(lastAppliedYaw)
				&& Math.abs(TurnMath.yawDiff(currentYaw, lastAppliedYaw)) > EXTERNAL_EPSILON) {
			lastAppliedYaw = currentYaw;
			return;
		}
		if (mode == Mode.MANUAL) {
			// 直接写入的语义已经生效，不需要每帧再推
			lastAppliedYaw = currentYaw;
			return;
		}

		float gain = mode == Mode.TURN ? TurnMath.GAIN_TURN : TurnMath.GAIN_WALK;
		float yawStep = TurnMath.frameStep(
				TurnMath.yawDiff(currentYaw, targetYaw), dt, gain, TurnMath.MAX_YAW_SPEED);
		if (yawStep != 0f) {
			currentYaw += yawStep;
			client.player.setYaw(currentYaw);
		}

		if (pitchActive) {
			float currentPitch = client.player.getPitch();
			float pitchStep = TurnMath.frameStep(
					targetPitch - currentPitch, dt, gain, TurnMath.MAX_PITCH_SPEED);
			if (pitchStep != 0f) {
				client.player.setPitch(MathHelper.clamp(currentPitch + pitchStep, -PITCH_LIMIT, PITCH_LIMIT));
			}
		}
		lastAppliedYaw = client.player.getYaw();
	}

	// ---- 观测（HUD / 冒烟测试）----

	public static Mode mode() {
		return mode;
	}

	public static float targetYaw() {
		return targetYaw;
	}

	/** 是否正在接管视角。 */
	public static boolean active() {
		return mode != Mode.NONE;
	}
}
