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
	// 到达容差（水平 0.6 / 垂直 0.6）已改为可配置项，见 TurboMCConfig#pathArriveHorizontal()
	// 与 #pathArriveVertical()。默认值与改造前的常量一致，所以行为不变。

	/** 玩家到目标（格中心）的水平距离。 */
	public double horizontalDistanceTo(ClientPlayerEntity player) {
		double dx = pos.getX() + 0.5 - player.getX();
		double dz = pos.getZ() + 0.5 - player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
