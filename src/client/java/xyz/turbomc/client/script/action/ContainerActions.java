package xyz.turbomc.client.script.action;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
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
import xyz.turbomc.client.script.runtime.ScriptContext;
import xyz.turbomc.client.script.runtime.ScratchCast;

import java.util.Locale;

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
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");
	/** 服务端的交互距离上限约 4.5 格，超出会被拒绝且不会有任何提示。 */
	private static final double REACH_LIMIT = 5.0d;
	/** 距离超限的告警节流，避免每 tick 刷屏。 */
	private static final long REACH_WARN_INTERVAL = 5_000_000_000L;
	private static long lastReachWarn;
	/** 已提示过的非法按钮名，避免每 tick 刷屏。 */
	private static final java.util.Set<String> WARNED_BUTTONS = new java.util.HashSet<>();

	private ContainerActions() {
	}

	public static void registerAll() {
		NativeRegistry.action("action.open_container_at", ContainerActions::openAt);
		NativeRegistry.action("action.click_slot", ContainerActions::clickSlot);
		NativeRegistry.action("action.quick_move_slot", ContainerActions::quickMove);
		NativeRegistry.action("action.close_container", ContainerActions::close);
		NativeRegistry.function("world.container_slots", ContainerActions::containerSlots);
		NativeRegistry.function("world.slot_field", ContainerActions::slotField);
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
				GameActions.clampCoord(ScratchCast.toNumber(args[0])),
				GameActions.clampCoord(ScratchCast.toNumber(args[1])),
				GameActions.clampCoord(ScratchCast.toNumber(args[2])));
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

		// 刻意不转视角。服务端 onPlayerInteractBlock 只校验 canInteractWithBlockAt 的距离，
		// 不看玩家朝向，命中点由下面的 BlockHitResult 带过去；写 yaw/pitch 反而会把
		// 「走向」的节奏打乱——PathFollower 每 tick 比较角度差，超过 standTurnDeg（默认 30°）
		// 就松前进键站定；ViewSmoother 又会把这次写入当成外部改动，让位一帧再平滑转回来，
		// 表现为「走过去开箱子，人会顿一下再回弹」。想看向容器就自己加「看向坐标」。
		client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
				new BlockHitResult(center, Direction.UP, pos, false));
		client.player.swingHand(Hand.MAIN_HAND);
	}

	/**
	 * 点击槽位：{@code left} 举起 / 放下整叠，{@code right} 分半堆放，
	 * {@code middle} 在创造模式下把该格物品复制到光标。
	 *
	 * <p><b>中键不是「按钮 2 的 PICKUP」</b>：原版 {@code ScreenHandler.internalOnSlotClick}
	 * 的 PICKUP 分支只接受 button 0 / 1，其余会落到后面 SWAP、CLONE、THROW 的判定，
	 * 全部不匹配后直接返回——也就是什么都不做。真正的中键语义是
	 * {@code SlotActionType.CLONE} + button 2，且服务端还要求创造模式、光标为空，
	 * 所以生存模式点中键是安全的空操作。</p>
	 *
	 * <p>需要界面已打开，否则什么都不会发生。</p>
	 */
	private static void clickSlot(ScriptContext ctx, Object[] args) {
		// 菜单值是 left / right / middle，不是数字
		String raw = args.length > 1 ? ScratchCast.toString(args[1]).trim() : "";
		if ("middle".equalsIgnoreCase(raw)) {
			perform(ctx, args, SlotActionType.CLONE, 2);
			return;
		}
		if (!raw.isEmpty() && !"left".equalsIgnoreCase(raw) && !"right".equalsIgnoreCase(raw)) {
			// 拼错的按钮名过去会被静默当成左键（拿起整叠），后果是把容器搞乱，必须说清楚
			warnUnknownButton(raw);
		}
		perform(ctx, args, SlotActionType.PICKUP, "right".equalsIgnoreCase(raw) ? 1 : 0);
	}

	/** 未知按钮名只提示一次，避免循环脚本把日志刷爆。 */
	private static void warnUnknownButton(String raw) {
		if (WARNED_BUTTONS.add(raw.toLowerCase(Locale.ROOT))) {
			LOG.warn("[script] 「点击槽位」的按钮 \"{}\" 无法识别，按 left 处理；可选 left / right / middle", raw);
		}
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
		int index = (int) Math.round(ScratchCast.toNumber(args[0]));
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
	 * 「槽位 [INDEX] 的 [字段]」合并块：注册名、数量、耐久上限、已损耗、自定义名称。
	 *
	 * <p>只做字段分派，取栈走 {@link #stackAt}：未开界面或越界都是 {@code null}，
	 * 于是文本字段给空串、数值字段给 0，与旧的 slotItem / slotAmount 完全一致。</p>
	 *
	 * <p>「已损耗」与「耐久上限」分开给而不直接给百分比：两者相除才是有意义的进度，
	 * 由脚本自己决定怎么算（百分比在「耐久百分比」那块里已有一份），这里不做第二次约定。</p>
	 */
	private static Object slotField(ScriptContext ctx, Object[] args) {
		String field = args.length > 1
				? ScratchCast.toString(args[1]).trim().toLowerCase(Locale.ROOT) : "id";
		ItemStack stack = stackAt(ctx, args);
		boolean empty = stack == null || stack.isEmpty();
		switch (field) {
			case "amount" -> {
				return empty ? 0d : (double) stack.getCount();
			}
			case "max_durability" -> {
				return empty ? 0d : (double) stack.getMaxDamage();
			}
			case "damage" -> {
				return empty ? 0d : (double) stack.getDamage();
			}
			case "custom_name" -> {
				if (empty || stack.getCustomName() == null) {
					return "";
				}
				return stack.getCustomName().getString();
			}
			default -> {
				return empty ? "" : Registries.ITEM.getId(stack.getItem()).toString();
			}
		}
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
		Item wanted = ItemIds.resolve(ScratchCast.toString(args.length > 0 ? args[0] : ""));
		if (wanted == null) {
			return -1d;
		}
		for (int index = 0; index < handler.slots.size(); index++) {
			ItemStack stack = handler.getSlot(index).getStack();
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			if (stack.getItem() == wanted) {
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
		int index = (int) Math.round(ScratchCast.toNumber(args.length > 0 ? args[0] : 0));
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
