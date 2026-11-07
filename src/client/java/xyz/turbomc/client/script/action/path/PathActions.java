package xyz.turbomc.client.script.action.path;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.script.action.GameActions;
import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.runtime.ScriptContext;
import xyz.turbomc.client.script.runtime.Values;

/**
 * 「走向 x y z」的入口：解析三个参数，然后交给 {@link PathFollower} 挂起执行。
 *
 * <p>参数是按积木文本 {@code 走向 x:[X] y:[Y] z:[Z]} 的顺序从桥里取出来的，
 * 所以<b>顺序变了就等于把 Z 读到 Y 上</b>——改积木文本时必须同步这里。</p>
 */
public final class PathActions {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");
	/** Y 只做合理性钳制，避免 {@code 1e20} 这类输入造出荒谬的 BlockPos。 */
	private static final double Y_LIMIT = 1024d;

	private static boolean warnedMalformed;

	private PathActions() {
	}

	public static void registerAll() {
		NativeRegistry.action("action.move_toward", PathActions::moveToward);
	}

	private static void moveToward(ScriptContext ctx, Object[] args) {
		MinecraftClient client = (MinecraftClient) ctx.handle();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		// 参数个数由积木签名固定为 3，长度检查几乎不会触发；真正要防的是「input 存在但为空」：
		// 桥对缺失值给空串，而 toNumber("") = 0——直接放行会让角色朝 y=0 走，且不报错。
		if (args.length < 3 || isBlank(args[0]) || isBlank(args[1]) || isBlank(args[2])) {
			warnMalformed();
			return;
		}

		BlockPos pos = new BlockPos(
				GameActions.clampCoord(Values.asNumber(args[0])),
				clampY(Values.asNumber(args[1])),
				GameActions.clampCoord(Values.asNumber(args[2])));
		MoveTarget target = new MoveTarget(pos);

		if (!PathFollower.start(client, target)) {
			return;   // 已在目标内 / 目标在未加载区：不挂起，立刻返回
		}
		ctx.requestWaitUntil("move", PathFollower.waitLimitTicks());
	}

	private static int clampY(double value) {
		double clamped = Math.max(-Y_LIMIT, Math.min(Y_LIMIT, value));
		return (int) Math.floor(clamped);
	}

	/** 缺失的 input 在桥里是 null 或空串。 */
	private static boolean isBlank(Object value) {
		return value == null || (value instanceof String text && text.isBlank());
	}

	private static void warnMalformed() {
		if (warnedMalformed) {
			return;
		}
		warnedMalformed = true;
		LOG.warn("[path] 「走向」的参数缺失或为空（x/y/z），本次已忽略；"
				+ "请用当前版本的扩展重新导出工程");
	}
}
