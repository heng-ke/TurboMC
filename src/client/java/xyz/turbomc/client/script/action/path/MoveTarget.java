package xyz.turbomc.client.script.action.path;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * 「走向」的目标坐标。
 *
 * <p>三轴全部来自积木参数，宿主不做任何推断——同列可能有地面、地洞、二楼多个可站立面，
 * 猜错了表现为「走向某个坐标却钻进地洞」，比让脚本作者多填一个参数难查得多。</p>
 *
 * @param pos 目标站立格（玩家脚底所在的方块格），不是脚下方块，也不是精确高度
 */
public record MoveTarget(BlockPos pos) {
	/** 水平到达半径：比节点推进阈值宽，避免「差一点点永远判定不到」。 */
	public static final double ARRIVE_HORIZONTAL = 0.6;
	/**
	 * 垂直到达容差。
	 *
	 * <p>取 0.6 而不是 1.0：玩家脚部站在半砖上是 {@code y+0.5}，需要一点余量；
	 * 但给到 1.0 就会把「站在台阶下方同一格」判成已到达——那是整整差一格。</p>
	 */
	public static final double ARRIVE_VERTICAL = 0.6;

	/** 玩家到目标（格中心）的水平距离。 */
	public double horizontalDistanceTo(ClientPlayerEntity player) {
		double dx = pos.getX() + 0.5 - player.getX();
		double dz = pos.getZ() + 0.5 - player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
