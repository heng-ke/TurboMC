package xyz.turbomc.client.ui;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * F3+H 高级物品浮窗里的铁砧「先前工作惩罚」提示。
 *
 * <p>原版高级浮窗只补物品 ID、耐久分数等，不显示 {@code minecraft:repair_cost}——
 * 而这个组件正是「越合越贵、最后 Too Expensive」的根因。只在
 * {@link TooltipType#isAdvanced()} 为真时追加一行，普通浮窗完全不受影响，
 * 因此不需要独立配置开关：F3+H 本身就是开关。</p>
 *
 * <p>显示当前惩罚值与下一次加工后的值。1.21 的铁砧把结果惩罚设为
 * {@code max(双方惩罚) * 2 + 1}，服务端对单次花费 ≥ 40 的操作拒绝执行；
 * 单次花费还含附魔基础值与另一件物品的惩罚，所以这里不承诺「还能合几次」，
 * 只把两个确定值与 40 上限摆出来，由玩家自己判断。</p>
 */
public final class EnchantPenaltyTooltip {
	private static final int ANVIL_LEVEL_LIMIT = 40;

	private EnchantPenaltyTooltip() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register(EnchantPenaltyTooltip::append);
	}

	/** 一次铁砧加工后的惩罚：结果取双方最大值，再 ×2+1。 */
	public static int nextCost(int current) {
		return current * 2 + 1;
	}

	private static void append(ItemStack stack, Item.TooltipContext context,
							   TooltipType type, List<Text> lines) {
		if (!type.isAdvanced()) {
			return;
		}
		// 组件缺省即惩罚 0（未加工过的物品）。老大要求零值也显示，便于确认功能在工作。
		Integer stored = stack.get(DataComponentTypes.REPAIR_COST);
		int cost = stored == null ? 0 : stored;
		int next = nextCost(cost);
		Text line = cost == 0
				? Text.translatable("tooltip.turbomc.repair_cost.none", next)
				: Text.translatable("tooltip.turbomc.repair_cost", cost, next);
		lines.add(line.copy()
				.formatted(next >= ANVIL_LEVEL_LIMIT ? Formatting.RED : Formatting.GRAY));
	}
}
