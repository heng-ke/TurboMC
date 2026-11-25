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
		NativeRegistry.action("action.look_smooth", TurnActions::lookSmooth);
		NativeRegistry.action("action.look_at_smooth", TurnActions::lookAtSmooth);
	}

	private static void lookSmooth(ScriptContext ctx, Object[] args) {
		MinecraftClient client = (MinecraftClient) ctx.handle();
		if (client.player == null) {
			return;
		}
		float yaw = (float) ScratchCast.toNumber(args[0]);
		float pitch = (float) ScratchCast.toNumber(args[1]);
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
