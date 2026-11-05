package xyz.hegke.client.script.action;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.hegke.client.script.runtime.ScriptContext;
import xyz.hegke.client.script.runtime.Values;

/**
 * 容器交互：打开指定容器、点击槽位、快速转移、关闭界面。
 *
 * <p>为什么必须走界面而不是直接改方块实体：客户端的 {@code world} 只是服务端状态的副本，
 * 本地改动不会同步，下次同步就被覆盖。{@code clickSlot} 会发出与玩家手动点击完全相同的
 * {@code ClickSlotC2SPacket}，由服务端校验后执行——这是唯一合法路径，也意味着它受正常的
 * 距离、权限与反作弊约束。</p>
 *
 * <p><b>点击是异步的</b>：发包后要等服务端回发槽位更新，同一 tick 内读到的仍是旧值。
 * 脚本在每次点击后应至少等待 1 tick 再读状态。</p>
 */
public final class ContainerActions {
	private static final Logger LOG = LoggerFactory.getLogger("hk-script/script");
	/** 服务端的交互距离上限约 4.5 格，超出会被拒绝且不会有任何提示。 */
	private static final double REACH_LIMIT = 5.0d;
	/** 距离超限的告警节流，避免每 tick 刷屏。 */
	private static final long REACH_WARN_INTERVAL = 5_000_000_000L;
	private static long lastReachWarn;

	private ContainerActions() {
	}

	public static void registerAll() {
		NativeRegistry.action("action.open_container_at", ContainerActions::openAt);
		NativeRegistry.action("action.click_slot", ContainerActions::clickSlot);
		NativeRegistry.action("action.quick_move_slot", ContainerActions::quickMove);
		NativeRegistry.action("action.close_container", ContainerActions::close);
		NativeRegistry.function("world.container_slots", ContainerActions::containerSlots);
		NativeRegistry.function("world.slot_item", ContainerActions::slotItem);
		NativeRegistry.function("world.slot_amount", ContainerActions::slotAmount);
		NativeRegistry.function("world.first_slot_of", ContainerActions::firstSlotOf);
	}

	/**
	 * 打开指定坐标的容器。
	 *
	 * <p>已经有界面打开时直接返回——在界面里再发右键会点到被 GUI 遮住的画面上，
	 * 很容易误操作到别的东西。</p>
	 */
	private static void openAt(ScriptContext ctx, Object[] args) {
		MinecraftClient client = (MinecraftClient) ctx.handle();
		if (client == null || client.interactionManager == null
				|| client.player == null || client.world == null) {
			return;
		}
		if (client.currentScreen != null) {
			return;
		}
		BlockPos pos = new BlockPos(
				GameActions.clampCoord(Values.asNumber(args[0])),
				GameActions.clampCoord(Values.asNumber(args[1])),
				GameActions.clampCoord(Values.asNumber(args[2])));
		if (!(client.world.getBlockEntity(pos) instanceof Inventory)) {
			return;
		}

		Vec3d center = Vec3d.ofCenter(pos);
		double distance = client.player.getEyePos().distanceTo(center);
		if (distance > REACH_LIMIT) {
			// 服务端会静默拒绝，这里提前说清楚，否则用户只看到「界面没打开」
			long now = System.nanoTime();
			if (now - lastReachWarn > REACH_WARN_INTERVAL) {
				lastReachWarn = now;
				LOG.warn("[script] 容器距离 {} 格超过交互上限，无法打开", String.format("%.1f", distance));
			}
			return;
		}

		client.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, center);
		client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
				new BlockHitResult(center, Direction.UP, pos, false));
		client.player.swingHand(Hand.MAIN_HAND);
	}

	/**
	 * 点击槽位：左键拿起或放下（button 0=左，1=右）。
	 *
	 * <p>需要界面已打开，否则什么都不会发生。</p>
	 */
	private static void clickSlot(ScriptContext ctx, Object[] args) {
		// 菜单值是 left / right，不是数字；右键用于分半堆放
		int button = args.length > 1 && "right".equalsIgnoreCase(Values.asString(args[1]).trim()) ? 1 : 0;
		perform(ctx, args, SlotActionType.PICKUP, button);
	}

	/**
	 * 快速移动槽位（相当于 Shift+点击）：容器与背包之间自动转移。
	 *
	 * <p>不需要知道目标格在哪里——服务端按该容器的规则分配，这是搬东西最省的一种方式。</p>
	 */
	private static void quickMove(ScriptContext ctx, Object[] args) {
		perform(ctx, args, SlotActionType.QUICK_MOVE, 0);
	}

	private static void perform(ScriptContext ctx, Object[] args, SlotActionType type, int button) {
		MinecraftClient client = (MinecraftClient) ctx.handle();
		if (client == null || client.interactionManager == null || client.player == null) {
			return;
		}
		ScreenHandler handler = handler(client);
		if (handler == null) {
			return;
		}
		int index = (int) Math.round(Values.asNumber(args[0]));
		if (index < 0 || index >= handler.slots.size()) {
			return;
		}
		client.interactionManager.clickSlot(handler.syncId, index, Math.max(0, button), type, client.player);
	}

	/** 关闭当前界面；没有界面时什么也不做。 */
	private static void close(ScriptContext ctx, Object[] args) {
		MinecraftClient client = (MinecraftClient) ctx.handle();
		if (client == null || client.player == null) {
			return;
		}
		if (client.currentScreen != null) {
			client.player.closeHandledScreen();
		}
	}

	/** 当前界面的槽位总数；没有界面时为 0——脚本据此判断「到底打开了没有」。 */
	private static Object containerSlots(ScriptContext ctx, Object[] args) {
		MinecraftClient client = (MinecraftClient) ctx.handle();
		if (client == null) {
			return 0d;
		}
		ScreenHandler handler = handler(client);
		return handler == null ? 0d : (double) handler.slots.size();
	}

	/**
	 * 某个槽位里的物品 ID，例如 {@code minecraft:iron_ore}；空槽返回空串。
	 *
	 * <p>返回的是注册名而不是显示名：显示名会随语言变化，脚本判断会跟着坏掉。</p>
	 */
	private static Object slotItem(ScriptContext ctx, Object[] args) {
		ItemStack stack = stackAt(ctx, args);
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		return Registries.ITEM.getId(stack.getItem()).toString();
	}

	/** 某个槽位里的物品数量；空槽返回 0。 */
	private static Object slotAmount(ScriptContext ctx, Object[] args) {
		ItemStack stack = stackAt(ctx, args);
		return stack == null || stack.isEmpty() ? 0d : (double) stack.getCount();
	}

	/**
	 * 第一个装着指定物品的槽位号；找不到返回 -1。
	 *
	 * <p>比让脚本自己循环 36 格好：一次桥调用就能定位，也不会把「背包格」和
	 * 「容器格」的索引边界写错。</p>
	 */
	private static Object firstSlotOf(ScriptContext ctx, Object[] args) {
		MinecraftClient client = (MinecraftClient) ctx.handle();
		if (client == null) {
			return -1d;
		}
		ScreenHandler handler = handler(client);
		if (handler == null) {
			return -1d;
		}
		String wanted = Values.asString(args.length > 0 ? args[0] : "").trim();
		if (wanted.isEmpty()) {
			return -1d;
		}
		for (int index = 0; index < handler.slots.size(); index++) {
			ItemStack stack = handler.getSlot(index).getStack();
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			if (Registries.ITEM.getId(stack.getItem()).toString().equals(wanted)) {
				return (double) index;
			}
		}
		return -1d;
	}

	/**
	 * 取指定槽位的物品栈；界面未打开或索引越界时返回 {@code null}。
	 *
	 * <p>刻意用 {@code null} 而不是 {@code ItemStack.EMPTY}：后者是静态常量，
	 * 类加载时会触发 Minecraft 的 Bootstrap，在纯 JVM 冒烟测试里会直接崩。</p>
	 */
	private static ItemStack stackAt(ScriptContext ctx, Object[] args) {
		MinecraftClient client = (MinecraftClient) ctx.handle();
		if (client == null) {
			return null;
		}
		ScreenHandler handler = handler(client);
		if (handler == null) {
			return null;
		}
		int index = (int) Math.round(Values.asNumber(args.length > 0 ? args[0] : 0));
		if (index < 0 || index >= handler.slots.size()) {
			return null;
		}
		return handler.getSlot(index).getStack();
	}

	private static ScreenHandler handler(MinecraftClient client) {
		if (client.currentScreen instanceof ScreenHandlerProvider<?> provider) {
			return provider.getScreenHandler();
		}
		return null;
	}
}
