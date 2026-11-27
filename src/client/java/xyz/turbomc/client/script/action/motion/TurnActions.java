package xyz.turbomc.client.script.action.motion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.runtime.ScriptContext;
import xyz.turbomc.client.script.runtime.ScratchCast;

/**
 * 「平滑转到视角」与「平滑看向」两个积木的入口。
 *
 * <p>与瞬时的「设置视角 / 看向」并存：需要立刻对准（比如出手前）用瞬时版，
 * 需要看起来像人操作、或者不想被视角突变暴露时用平滑版。</p>
 */
public final class TurnActions {
	private TurnActions() {
	}

	public static void registerAll() {
		NativeRegistry.action("action.turn_view_smooth", TurnActions::turnViewSmooth);
		NativeRegistry.action("action.look_at_smooth", TurnActions::lookAtSmooth);
	}

	/**
	 * 「平滑转动视角」：与瞬时版共用 MODE 语义，区别只在「直接写值」与「逐帧收敛」。
	 *
	 * <p>增量模式下把增量叠到<b>当前角度</b>上再交给 {@link TurnSession}；它内部会做
	 * 最短路径折算与俯仰钳制，所以传 200° 的增量等价于转 -160°，不会绕远路。</p>
	 */
	private static void turnViewSmooth(ScriptContext ctx, Object[] args) {
		MinecraftClient client = (MinecraftClient) ctx.handle();
		if (client.player == null) {
			return;
		}
		float yaw = (float) ScratchCast.toNumber(args[1]);
		float pitch = (float) ScratchCast.toNumber(args[2]);
		if (args != null && args.length > 0
				&& "relative".equalsIgnoreCase(ScratchCast.toString(args[0]).trim())) {
			yaw += client.player.getYaw();
			pitch += client.player.getPitch();
		}
		if (!TurnSession.start(client, yaw, pitch)) {
			return;   // 已经在目标角上：不挂起
		}
		ctx.requestWaitUntil("turn", TurnSession.waitLimitTicks());
	}

	private static void lookAtSmooth(ScriptContext ctx, Object[] args) {
		MinecraftClient client = (MinecraftClient) ctx.handle();
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		double x = ScratchCast.toNumber(args[0]);
		double y = ScratchCast.toNumber(args[1]);
		double z = ScratchCast.toNumber(args[2]);
		Vec3d eye = player.getEyePos();
		if (TurnSession.isTooClose(eye, x, y, z)) {
			return;   // 目标就在眼睛里，算出来的角度没有意义
		}
		float yaw = TurnSession.yawTo(eye, x, y, z, player.getYaw());
		float pitch = TurnSession.pitchTo(eye, x, y, z);
		if (!TurnSession.start(client, yaw, pitch)) {
			return;
		}
		ctx.requestWaitUntil("turn", TurnSession.waitLimitTicks());
	}
}
