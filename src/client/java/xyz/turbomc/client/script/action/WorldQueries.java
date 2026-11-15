package xyz.turbomc.client.script.action;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.script.runtime.ChatState;
import xyz.turbomc.client.script.runtime.ScriptContext;
import xyz.turbomc.client.script.runtime.Values;

import java.util.List;
import java.util.function.Predicate;

/**
 * 世界状态查询：血量、坐标、背包、方块、环境、实体。
 *
 * <p>与 {@link GameActions} 的分工是「只读 vs 改变世界」。这样改动作不会碰到查询，
 * 也便于对查询单独做逻辑审查（它不产生任何副作用）。</p>
 *
 * <p>末尾的 {@code query.last_chat}（最近一条聊天）不属于世界状态，但同样是纯读取，
 * 所以并入本表而不另开注册表。写入方在 {@code TurboMCClient} 的聊天事件里。</p>
 */
public final class WorldQueries {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");
	static final double ENTITY_SEARCH_RANGE = 16d;

	private WorldQueries() {
	}

	public static void registerAll() {
		NativeRegistry.function("world.block_in_front", WorldQueries::blockInFront);
		NativeRegistry.function("world.health", WorldQueries::health);
		NativeRegistry.function("world.food", WorldQueries::food);
		NativeRegistry.function("world.pos_x", WorldQueries::posX);
		NativeRegistry.function("world.pos_y", WorldQueries::posY);
		NativeRegistry.function("world.pos_z", WorldQueries::posZ);
		NativeRegistry.function("world.selected_slot", WorldQueries::selectedSlot);
		NativeRegistry.function("world.nearest_entity", WorldQueries::nearestEntity);
		NativeRegistry.function("world.nearest_entity_x", WorldQueries::nearestEntityX);
		NativeRegistry.function("world.nearest_entity_y", WorldQueries::nearestEntityY);
		NativeRegistry.function("world.nearest_entity_z", WorldQueries::nearestEntityZ);
		NativeRegistry.function("world.on_ground", WorldQueries::onGround);
		NativeRegistry.function("world.item_count", WorldQueries::itemCount);
		NativeRegistry.function("world.held_item", WorldQueries::heldItem);
		NativeRegistry.function("world.held_durability", WorldQueries::heldDurability);
		NativeRegistry.function("world.front_block", WorldQueries::frontBlock);
		NativeRegistry.function("world.block_below", WorldQueries::blockBelow);
		NativeRegistry.function("world.block_at", WorldQueries::blockAt);
		NativeRegistry.function("world.is_day", WorldQueries::isDay);
		NativeRegistry.function("world.dimension", WorldQueries::dimension);
		NativeRegistry.function("world.in_water", WorldQueries::inWater);
		NativeRegistry.function("world.in_lava", WorldQueries::inLava);
		NativeRegistry.function("world.nearest_entity_name", WorldQueries::nearestEntityName);
		NativeRegistry.function("world.xp_level", WorldQueries::xpLevel);
		NativeRegistry.function("world.inventory_full", WorldQueries::inventoryFull);
		NativeRegistry.function("world.entity_count", WorldQueries::entityCount);
		NativeRegistry.function("world.biome", WorldQueries::biome);
		NativeRegistry.function("world.ping", WorldQueries::ping);
		NativeRegistry.function("world.is_mining", WorldQueries::isMining);
		NativeRegistry.function("world.mining_progress", WorldQueries::miningProgress);
		// 聊天不是「世界状态」，但同样是纯读取，放在这里不必单开一张注册表。
		// 写入方在 TurboMCClient 的聊天事件里（ChatGate 通过之后）
		NativeRegistry.function("query.last_chat", (context, args) -> ChatState.last());
		// 「触发我的聊天」由引导脚本在线程上下文里直接返回（见 vm-bootstrap 的 __tmcThreadChat），
		// 这里只是兜底：万一某个调用路径绕过了包装器，拿到空串而不是一次「未映射」警告。
		// 刻意不回落 ChatState.last()——那会让它和「最近的聊天」变成同一个积木。
		NativeRegistry.function("query.trigger_chat", (context, args) -> "");
	}

	// ---- 挖掘 ----

	/** 锁定的挖掘目标：坐标 + 挖掘前的方块，用于区分「被挖掉」和「被流体顶替」。 */
	private record MiningLock(BlockPos pos, Block block) {
	}

	/**
	 * 推进一次「等待挖破」判定。
	 *
	 * <p>不能用准星判断「挖完了没有」：方块一破，射线会立刻穿到后面那块方块上，
	 * 于是「前方有方块」恒为真。所以第一次判定时把准星方块的坐标与方块类型锁定，
	 * 之后只盯这一个坐标，直到它换成了别的东西。</p>
	 *
	 * @param state 上一轮锁定的 {@link MiningLock}，首轮为 {@code null}
	 */
	public static ScriptContext.BreakResult probeBreak(ScriptContext ctx, Object state) {
		MinecraftClient client = client(ctx);
		if (client == null) {
			// 无客户端（无头 VM 冒烟）时无从判定，保持等待交给上限超时
			return new ScriptContext.BreakResult(state, ScriptContext.BreakResult.WAITING);
		}
		if (client.player == null || client.world == null) {
			return new ScriptContext.BreakResult(null, ScriptContext.BreakResult.GIVE_UP);
		}
		if (!(state instanceof MiningLock lock)) {
			BlockPos pos = crosshairBlockPos(client);
			if (pos == null) {
				// 准星还没对着方块（例如还在走近），保持等待而不是直接判失败
				return new ScriptContext.BreakResult(null, ScriptContext.BreakResult.WAITING);
			}
			return new ScriptContext.BreakResult(
					new MiningLock(pos, client.world.getBlockState(pos).getBlock()),
					ScriptContext.BreakResult.WAITING);
		}
		BlockState current = client.world.getBlockState(lock.pos());
		if (current.getBlock() != lock.block()) {
			// 变成空气、或被水/沙砾顶替，都说明这块方块已经不在了
			return new ScriptContext.BreakResult(null, ScriptContext.BreakResult.BROKEN);
		}
		return new ScriptContext.BreakResult(lock, ScriptContext.BreakResult.WAITING);
	}

	private static BlockPos crosshairBlockPos(MinecraftClient client) {
		HitResult hit = client.crosshairTarget;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			return blockHit.getBlockPos().toImmutable();
		}
		return null;
	}

	/**
	 * 是否处于挖掘状态（按住挖掘键且已锁定目标）。
	 */
	private static Object isMining(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.interactionManager != null && client.interactionManager.isBreakingBlock();
	}

	/**
	 * 当前方块的挖掘进度（0–100）。没有在挖掘时返回 -1，
	 * 以便脚本区分「进度 0%」和「压根还没开始挖」。
	 *
	 * <p>底层只有 0–9 的十档（原版渲染裂纹用的就是它），所以精度是 10% 一档。</p>
	 */
	private static Object miningProgress(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.interactionManager == null || !client.interactionManager.isBreakingBlock()) {
			return -1d;
		}
		int stage = client.interactionManager.getBlockBreakingProgress();
		return (double) Math.min(100L, Math.max(0L, stage * 100L / 9L));
	}

	private static Object blockInFront(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.world == null) {
			return false;
		}
		HitResult hit = client.crosshairTarget;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			return !client.world.getBlockState(blockHit.getBlockPos()).isAir();
		}
		return false;
	}

	private static Object health(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player == null ? 0d : (double) client.player.getHealth();
	}

	private static Object food(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player == null ? 0d : (double) client.player.getHungerManager().getFoodLevel();
	}

	private static Object posX(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player == null ? 0d : client.player.getX();
	}

	private static Object posY(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player == null ? 0d : client.player.getY();
	}

	private static Object posZ(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player == null ? 0d : client.player.getZ();
	}

	private static Object selectedSlot(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player == null ? 0d : (double) (client.player.getInventory().getSelectedSlot() + 1);
	}

	private static Object nearestEntity(ScriptContext ctx, Object[] args) {
		Entity entity = nearestEntityOrNull(ctx, args);
		MinecraftClient client = client(ctx);
		if (entity == null || client.player == null) {
			return -1d;
		}
		return (double) client.player.distanceTo(entity);
	}

	private static Object nearestEntityX(ScriptContext ctx, Object[] args) {
		return coordinateOf(nearestEntityOrNull(ctx, args), 0);
	}

	private static Object nearestEntityY(ScriptContext ctx, Object[] args) {
		return coordinateOf(nearestEntityOrNull(ctx, args), 1);
	}

	private static Object nearestEntityZ(ScriptContext ctx, Object[] args) {
		return coordinateOf(nearestEntityOrNull(ctx, args), 2);
	}

	private static Object onGround(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player != null && client.player.isOnGround();
	}

	private static Object itemCount(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.player == null) {
			return 0d;
		}
		Item item = ItemIds.resolve(Values.asString(args[0]));
		if (item == null) {
			return 0d;
		}
		PlayerInventory inventory = client.player.getInventory();
		int total = 0;
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (!stack.isEmpty() && stack.getItem() == item) {
				total += stack.getCount();
			}
		}
		return (double) total;
	}

	private static Object heldItem(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.player == null) {
			return "";
		}
		ItemStack stack = client.player.getMainHandStack();
		return stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).toString();
	}

	/**
	 * 手持物品剩余耐久百分比（0–100，保留一位小数）。
	 * 空手返回 0，不可损坏物品（方块、工具材料等）返回 100。
	 */
	private static Object heldDurability(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.player == null) {
			return 0d;
		}
		ItemStack stack = client.player.getMainHandStack();
		if (stack.isEmpty()) {
			return 0d;
		}
		if (!stack.isDamageable()) {
			return 100d;
		}
		int max = stack.getMaxDamage();
		if (max <= 0) {
			return 100d;
		}
		int remaining = max - stack.getDamage();
		return Math.round(remaining * 1000d / max) / 10d;
	}

	/**
	 * 准星所指方块的 id；没瞄准方块时返回空串。
	 */
	private static Object frontBlock(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.world == null) {
			return "";
		}
		HitResult hit = client.crosshairTarget;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			return Registries.BLOCK.getId(
					client.world.getBlockState(blockHit.getBlockPos()).getBlock()).toString();
		}
		return "";
	}

	private static Object blockBelow(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.world == null || client.player == null) {
			return "";
		}
		BlockPos pos = client.player.getBlockPos().down();
		return Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
	}

	/**
	 * 指定坐标的方块 id（世界坐标，向下取整）。
	 */
	private static Object blockAt(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.world == null) {
			return "";
		}
		BlockPos pos = new BlockPos(
				GameActions.clampCoord(Values.asNumber(args[0])),
				GameActions.clampCoord(Values.asNumber(args[1])),
				GameActions.clampCoord(Values.asNumber(args[2])));
		return Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
	}

	private static Object isDay(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.world != null && client.world.isDay();
	}

	private static Object dimension(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.world == null ? "" : client.world.getRegistryKey().getValue().toString();
	}

	private static Object inWater(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player != null && client.player.isTouchingWater();
	}

	private static Object inLava(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player != null && client.player.isInLava();
	}

	private static Object nearestEntityName(ScriptContext ctx, Object[] args) {
		Entity entity = nearestEntityOrNull(ctx, args);
		return entity == null ? "" : entity.getType().getName().getString();
	}

	private static Object xpLevel(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player == null ? 0d : (double) client.player.experienceLevel;
	}

	/**
	 * 主背包（36 格）是否已满，不计盔甲与副手。
	 */
	private static Object inventoryFull(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.player == null) {
			return false;
		}
		PlayerInventory inventory = client.player.getInventory();
		for (int i = 0; i < 36; i++) {
			if (inventory.getStack(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 附近实体数量。类型支持 {@code any} / {@code hostile} / {@code player} 或实体 id。
	 */
	private static Object entityCount(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.world == null || client.player == null) {
			return 0d;
		}
		EntityFilter.Parsed filter = EntityFilter.parse(Values.asString(args[0]));
		int count = 0;
		for (Entity entity : nearbyEntities(ctx)) {
			if (EntityFilter.matches(entity, filter)) {
				count++;
			}
		}
		return (double) count;
	}

	private static Object biome(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.world == null || client.player == null) {
			return "";
		}
		return client.world.getBiome(client.player.getBlockPos())
				.getKey()
				.map(key -> key.getValue().toString())
				.orElse("");
	}

	/**
	 * 与服务器之间的延迟（毫秒）。单人游戏为 0。
	 */
	private static Object ping(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.player == null || client.getNetworkHandler() == null) {
			return 0d;
		}
		var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
		return entry == null ? 0d : (double) entry.getLatency();
	}

	/**
	 * 无目标时返回 {@link Double#NaN}：脚本中的数值比较对该值恒为假，
	 * 因此「存在目标」的分支不会被误触发。
	 */
	private static Object coordinateOf(Entity entity, int axis) {
		if (entity == null) {
			return Double.NaN;
		}
		return switch (axis) {
			case 0 -> entity.getX();
			case 1 -> entity.getY();
			default -> entity.getZ();
		};
	}

	/**
	 * 单 tick 缓存的「附近实体」列表：最近实体与实体计数共用同一次世界遍历。
	 */
	@SuppressWarnings("unchecked")
	private static List<Entity> nearbyEntities(ScriptContext ctx) {
		return (List<Entity>) ctx.cached("nearby_entities", () -> {
			MinecraftClient client = client(ctx);
			if (client.player == null || client.world == null) {
				return List.of();
			}
			return client.world.getOtherEntities(client.player,
					client.player.getBoundingBox().expand(ENTITY_SEARCH_RANGE));
		});
	}

	/**
	 * 单 tick 缓存最近实体：距离、三轴坐标与名称共五个查询积木共享同一次世界遍历。
	 *
	 * <p>缓存键带上过滤器，否则「任意实体的最近」会被「僵尸的最近」顶替。
	 * 参数缺失（老工程没有这个输入）解析成 {@code any}，行为与加参数之前一致。</p>
	 */
	static Entity nearestEntityOrNull(ScriptContext ctx, Object[] args) {
		EntityFilter.Parsed filter = filterOf(args);
		return nearestEntityOrNull(ctx, entity -> EntityFilter.matches(entity, filter),
				"nearest_entity:" + filter.cacheKey());
	}

	/** 读积木的「实体类型」参数；没有输入时为空串，由 {@link EntityFilter#parse} 归一到 any。 */
	private static EntityFilter.Parsed filterOf(Object[] args) {
		return EntityFilter.parse(args.length > 0 ? Values.asString(args[0]) : "");
	}

	static Entity nearestEntityOrNull(ScriptContext ctx, Predicate<Entity> filter, String cacheKey) {
		return (Entity) ctx.cached(cacheKey, () -> {
			MinecraftClient client = client(ctx);
			if (client.player == null || client.world == null) {
				return null;
			}
			ClientPlayerEntity player = client.player;
			Entity best = null;
			double bestDistance = Double.MAX_VALUE;
			for (Entity entity : nearbyEntities(ctx)) {
				if (!entity.isAlive() || !filter.test(entity)) {
					continue;
				}
				double distance = player.distanceTo(entity);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = entity;
				}
			}
			return best;
		});
	}

	private static MinecraftClient client(ScriptContext ctx) {
		return (MinecraftClient) ctx.handle();
	}
}
