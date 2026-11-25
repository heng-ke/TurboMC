package xyz.turbomc.client.script.action.motion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.script.view.ViewSmoother;

/**
 * 一次「平滑转向」的会话：每 tick 把 yaw / pitch 朝目标收敛一段。
 *
 * <p>为什么必须跨 tick：{@code setYaw} 是瞬时写值，一次写到目标就是「瞬移转头」。
 * 脚本语义上这类积木要阻塞到「转到位」，所以和「走向」一样用
 * {@code requestWaitUntil} + {@code poll} 挂起线程。</p>
 *
 * <p>与寻路的关系：{@link xyz.turbomc.client.script.action.path.PathFollower} 每 tick 也会写 yaw。
 * 转向会话在跑时寻路会主动让出 yaw（否则两边互相抵消）。</p>
 */
public final class TurnSession {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");

	/** 40 tick = 2 秒。180° 按 12°/tick 是 15 tick，90° 俯仰是 9 tick，够用。 */
	private static final int MAX_TICKS = 40;
	private static final int WAIT_SLACK = 20;
	private static final float PITCH_LIMIT = 90f;
	/** 目标点与眼睛重合时角度无意义，按「不动」处理。 */
	private static final double MIN_AIM_DISTANCE = 1e-4;

	public enum State {
		IDLE, TURNING, DONE, TIMEOUT, ABORTED
	}

	private static Session session;
	/** 终态保持到下一次 start()：poll 读它，读后不复位。 */
	private static State lastState = State.IDLE;

	private TurnSession() {
	}

	/**
	 * 开始转向。返回 {@code false} 表示不需要挂起（已经在目标角上）。
	 */
	public static boolean start(MinecraftClient client, float yaw, float pitch) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return false;
		}
		if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
			LOG.warn("[turn] 目标角度不是有限值（yaw={}, pitch={}），已忽略", yaw, pitch);
			return false;
		}
		// 折成最短路径，并且不归一化：yaw 保持连续累加，服务端看到的就是小增量。
		// 若在这里 wrap 成 [-180,180]，yaw 从 370 变 10 会被反作弊看成一次 360° 瞬转。
		float yawDiff = TurnMath.yawDiff(player.getYaw(), yaw);
		float targetYaw = player.getYaw() + yawDiff;
		float targetPitch = MathHelper.clamp(pitch, -PITCH_LIMIT, PITCH_LIMIT);

		if (Math.abs(yawDiff) <= TurnMath.DEAD_ZONE
				&& Math.abs(targetPitch - player.getPitch()) <= TurnMath.DEAD_ZONE) {
			// 只在没有会话在转时才改 lastState：否则会把另一个脚本正在等的 turn 提前放行
			if (session == null) {
				lastState = State.DONE;
			}
			return false;
		}
		if (session != null && session.state == State.TURNING
				&& session.yaw == targetYaw && session.pitch == targetPitch) {
			return true;   // 同一目标已在转：沿用
		}
		session = new Session(targetYaw, targetPitch);
		lastState = State.TURNING;
		return true;
	}

	public static void tick(MinecraftClient client) {
		Session s = session;
		if (s == null || s.state != State.TURNING) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null) {
			finish(s, State.ABORTED, "离开世界");
			return;
		}

		s.ticks++;
		float yawDiff = TurnMath.yawDiff(player.getYaw(), s.yaw);
		float pitchDiff = s.pitch - player.getPitch();

		if (Math.abs(yawDiff) <= TurnMath.DEAD_ZONE && Math.abs(pitchDiff) <= TurnMath.DEAD_ZONE) {
			finish(s, State.DONE, null);
			return;
		}
		if (s.ticks > MAX_TICKS) {
			// 超时也放行（否则阻塞积木挂死），但状态与日志必须如实说「超时」
			finish(s, State.TIMEOUT, "剩 yaw " + Math.round(yawDiff) + "°, pitch " + Math.round(pitchDiff) + "°");
			return;
		}

		// 写入交给 ViewSmoother 在渲染帧上按 dt 收敛：tick 里每 50 ms 写一次，
		// 在第一人称下就是每 3 帧跳一格的台阶（getYaw(float) 不做 tick 内插值）
		ViewSmoother.setTurnTarget(s.yaw, s.pitch);
	}

	/** poll 的返回值：终态为真，保持到下一次 start()。 */
	public static boolean finished() {
		return lastState == State.DONE || lastState == State.TIMEOUT || lastState == State.ABORTED;
	}

	/** 是否正在转——寻路用它决定要不要让出 yaw。 */
	public static boolean isActive() {
		return session != null && session.state == State.TURNING;
	}

	public static void abort(String reason) {
		Session s = session;
		if (s != null && s.state == State.TURNING) {
			finish(s, State.ABORTED, reason);
		}
		session = null;
	}

	public static int waitLimitTicks() {
		return MAX_TICKS + WAIT_SLACK;
	}

	/** 从眼睛到目标点的 yaw（度）。水平距离过小时返回 {@code currentYaw}。 */
	public static float yawTo(net.minecraft.util.math.Vec3d eye, double x, double y, double z, float currentYaw) {
		double dx = x - eye.x;
		double dz = z - eye.z;
		if (Math.sqrt(dx * dx + dz * dz) < MIN_AIM_DISTANCE) {
			return currentYaw;
		}
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** 从眼睛到目标点的 pitch（度，正数向下）。 */
	public static float pitchTo(net.minecraft.util.math.Vec3d eye, double x, double y, double z) {
		double dx = x - eye.x;
		double dy = y - eye.y;
		double dz = z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
	}

	/** 目标点与眼睛是否几乎重合。重合时角度无意义。 */
	public static boolean isTooClose(net.minecraft.util.math.Vec3d eye, double x, double y, double z) {
		double dx = x - eye.x;
		double dy = y - eye.y;
		double dz = z - eye.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz) < MIN_AIM_DISTANCE;
	}

	private static void finish(Session s, State state, String note) {
		if (s.state != State.TURNING) {
			return;
		}
		s.state = state;
		lastState = state;
		// 转向结束就不再接管视角：旧目标留着会让镜头继续往那个方向偏
		ViewSmoother.clear("转向结束");
		if (state == State.DONE) {
			LOG.info("[turn] 到达目标视角：{} tick{}", s.ticks, note == null ? "" : "（" + note + "）");
		} else if (state == State.TIMEOUT) {
			LOG.info("[turn] 超时：{} tick（{}）", s.ticks, note == null ? "" : note);
		} else {
			LOG.info("[turn] 中止：{}", note == null ? "未知" : note);
		}
	}

	private static final class Session {
		final float yaw;
		final float pitch;
		int ticks;
		State state = State.TURNING;

		Session(float yaw, float pitch) {
			this.yaw = yaw;
			this.pitch = pitch;
		}
	}
}
