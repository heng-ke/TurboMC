package xyz.turbomc.client.script.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.script.runtime.ChatState;
import xyz.turbomc.client.script.runtime.ScriptContext;
import xyz.turbomc.client.script.action.path.PathMath;
import xyz.turbomc.client.script.runtime.ScratchCast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 世界状态查询：血量、坐标、背包、方块、环境、实体，以及九块「快照」（游戏状态 → JSON 文本）。
 *
 * <p>与 {@link GameActions} 的分工是「只读 vs 改变世界」。这样改动作不会碰到查询，
 * 也便于对查询单独做逻辑审查（它不产生任何副作用）。</p>
 *
 * <p>末尾的 {@code query.last_chat} / {@code query.chat_history} 不属于世界状态，但同样是纯读取，
 * 所以并入本表而不另开注册表。写入方在 {@code TurboMCClient} 的聊天事件里，存储在
 * {@link ChatState}。</p>
 *
 * <p>快照部分的分工：这里只负责把 Minecraft 对象拆成原语，字段名与 JSON 形状全在
 * {@link Snapshots}（只收原语、纯 JVM 可测）。方向固定只读，见 ARCHITECTURE §26。</p>
 */
public final class WorldQueries {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");
	static final double ENTITY_SEARCH_RANGE = 16d;
	/**
	 * 区域扫描的体积上限（格数）。{@code getBlockState} 是主线程调用，
	 * 8192 格约等于 16×16×32，一次扫描的量级安全；超出的部分不扫，用 {@code truncated} 标记。
	 */
	static final int SCAN_VOLUME_LIMIT = 8192;

	private WorldQueries() {
	}

	public static void registerAll() {
		// 合并块：一个 opcode + 下拉菜单，取代原先的三轴/五字段细粒度积木。
		// 被取代的旧 opcode（world.pos_x、world.aim_x、world.nearest_entity* 等）已删除，
		// 含它们的旧工程会出现未知积木——见 ARCHITECTURE §6.2
		NativeRegistry.function("world.player_field", WorldQueries::playerField);
		NativeRegistry.function("world.aim_pos", WorldQueries::aimPos);
		NativeRegistry.function("world.nearest_entity_field", WorldQueries::nearestEntityField);
		NativeRegistry.function("world.held_item_field", WorldQueries::heldItemField);
		NativeRegistry.function("world.block_in_front", WorldQueries::blockInFront);
		NativeRegistry.function("world.on_ground", WorldQueries::onGround);
		NativeRegistry.function("world.front_block", WorldQueries::frontBlock);
		NativeRegistry.function("world.block_below", WorldQueries::blockBelow);
		NativeRegistry.function("world.block_at", WorldQueries::blockAt);
		NativeRegistry.function("world.is_day", WorldQueries::isDay);
		NativeRegistry.function("world.in_water", WorldQueries::inWater);
		NativeRegistry.function("world.in_lava", WorldQueries::inLava);
		NativeRegistry.function("world.inventory_full", WorldQueries::inventoryFull);
		NativeRegistry.function("world.entity_count", WorldQueries::entityCount);
		NativeRegistry.function("world.biome", WorldQueries::biome);
		// 快照：把游戏状态读成 JSON 文本，补上 JSON 积木缺的输入源。
		// 单向只读，回归写必须走动作积木——见 ARCHITECTURE §26
		NativeRegistry.function("world.player_json", WorldQueries::playerJson);
		NativeRegistry.function("world.entities_json", WorldQueries::entitiesJson);
		NativeRegistry.function("world.block_json", WorldQueries::blockJson);
		NativeRegistry.function("world.inventory_json", WorldQueries::inventoryJson);
		NativeRegistry.function("world.screen_json", WorldQueries::screenJson);
		NativeRegistry.function("world.world_json", WorldQueries::worldJson);
		NativeRegistry.function("world.players_json", WorldQueries::playersJson);
		NativeRegistry.function("world.scan_json", WorldQueries::scanJson);
		NativeRegistry.function("world.snapshot_keys", WorldQueries::snapshotKeys);
		NativeRegistry.function("world.is_mining", WorldQueries::isMining);
		NativeRegistry.function("world.mining_progress", WorldQueries::miningProgress);
		// 聊天不是「世界状态」，但同样是纯读取，放在这里不必单开一张注册表。
		// 写入方在 TurboMCClient 的聊天事件里（ChatGate 通过之后）。
		// 「触发我的」那一路由引导脚本在线程上下文里直接返回（见 vm-bootstrap 的
		// __tmcThreadChat），这里只服务「最近」并给 trigger 兜底空串——刻意不回落
		// ChatState.last()，否则两种来源就变成同一个积木了。
		NativeRegistry.function("query.chat_source", (context, args) -> {
			if (args != null && args.length > 0
					&& "trigger".equalsIgnoreCase(ScratchCast.toString(args[0]).trim())) {
				return "";
			}
			return ChatState.last();
		});
		NativeRegistry.function("query.chat_history", WorldQueries::chatHistory);
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
	 * 准星指向的方块坐标（积木「准星方块 X/Y/Z」的数据源）。
	 *
	 * <p><b>为什么要有兜底</b>：空气没有碰撞箱，射线不会「命中空气」，所以朝天空看时
	 * 游戏给的是 {@link HitResult.Type#MISS}，压根没有方块坐标——这不是「瞄准了一个空气格」。
	 * 若此时返回 {@code 0,0,0}，脚本会把框画到世界原点，比没有值更难排查。
	 * 因此这里退化为「眼睛沿视线走到触及距离终点所在的那一格」：永远有坐标，
	 * 且与玩家看到的准星方向一致。注意该格可能位于空中，要判断「是否真的瞄到方块」
	 * 请另用「前方有方块？」。</p>
	 */
	private static BlockPos aimBlockPos(MinecraftClient client) {
		BlockPos hit = crosshairBlockPos(client);
		if (hit != null) {
			return hit;
		}
		if (client.player == null) {
			return BlockPos.ORIGIN;
		}
		// 触及距离取自玩家属性（生存 4.5、创造 5，可被属性修饰符改动），不要写死常量
		double reach = client.player.getBlockInteractionRange();
		Vec3d eye = client.player.getEyePos();
		Vec3d direction = client.player.getRotationVec(1.0f);
		return BlockPos.ofFloored(eye.add(direction.multiply(reach)));
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

	// ---- 合并块实现（一块 + 下拉选择轴/字段）----
	// 被它取代的细粒度旧 opcode 已删除；这些方法只服务面板上仍存在的合并块。

	/**
	 * 「玩家的 [字段]」：所有数值/文本类的玩家读数走这一个入口。
	 *
	 * <p>字段名与玩家 JSON 快照的键一致（{@code Snapshots.PLAYER_KEYS}），作者在
	 * 「快照里有什么」与「这个下拉里有什么」之间不必再做换算。布尔类（站在地面 /
	 * 在水中 / 在岩浆中 / 背包已满）不在这里——六边形是条件槽的语法要求，
	 * 合并成 reporter 就再也放不进「如果」。</p>
	 *
	 * <p><b>槽位从 1 开始</b>：与原「当前槽位」积木一致，也与快捷键 1–9 的读法一致；
	 * 快照里的 {@code selected_slot} 是 0 基，两者刻意不同。</p>
	 */
	private static Object playerField(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client == null || client.player == null) {
			return 0d;
		}
		var player = client.player;
		String field = args == null || args.length == 0 ? "health" : ScratchCast.toString(args[0]).trim();
		return switch (field) {
			case "health" -> (double) player.getHealth();
			// 最大血量 / 护甲 / 氧气：判断「还能扛几下」「要不要浮上水面」都比拿血量猜准
			case "max_health" -> (double) player.getMaxHealth();
			case "armor" -> (double) player.getArmor();
			case "air" -> (double) player.getAir();
			case "food" -> (double) player.getHungerManager().getFoodLevel();
			case "xp_level" -> (double) player.experienceLevel;
			case "selected_slot" -> (double) (player.getInventory().getSelectedSlot() + 1);
			case "x" -> player.getX();
			case "y" -> player.getY();
			case "z" -> player.getZ();
			case "yaw" -> (double) player.getYaw();
			case "pitch" -> (double) player.getPitch();
			case "dimension" -> client.world == null ? ""
					: client.world.getRegistryKey().getValue().toString();
			case "ping" -> latency(client);
			default -> 0d;
		};
	}

	/** 与服务端之间的延迟（毫秒）。单人游戏为 0；玩家未就绪时也是 0。 */
	private static double latency(MinecraftClient client) {
		if (client.player == null || client.getNetworkHandler() == null) {
			return 0d;
		}
		var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
		return entry == null ? 0d : (double) entry.getLatency();
	}

	private static Object aimPos(ScriptContext ctx, Object[] args) {
		BlockPos pos = aimBlockPos(client(ctx));
		return switch (axisOf(args)) {
			case 1 -> (double) pos.getY();
			case 2 -> (double) pos.getZ();
			default -> (double) pos.getX();
		};
	}

	/**
	 * 最近实体的某个字段：距离 / 三轴坐标 / 名称。
	 *
	 * <p>无目标时坐标给 {@code NaN}（数值比较恒假）、距离给 {@code -1}、名称给空串，
	 * 与它取代的五个旧积木逐一对齐——合并只减少面板条目，不改变任何语义。</p>
	 */
	private static Object nearestEntityField(ScriptContext ctx, Object[] args) {
		Entity entity = nearestEntityOrNull(ctx, args);
		String field = args.length > 1
				? ScratchCast.toString(args[1]).trim().toLowerCase(Locale.ROOT) : "distance";
		MinecraftClient client = client(ctx);
		return switch (field) {
			case "x" -> coordinateOf(entity, 0);
			case "y" -> coordinateOf(entity, 1);
			case "z" -> coordinateOf(entity, 2);
			case "name" -> entity == null ? "" : entity.getType().getName().getString();
			// 血量与注册名：打怪脚本要判断「还剩多少」，抓特定实体要按注册名精确匹配。
			// 无目标时分别给 0 与空串，与距离 -1、名称空串的取舍一致（不返回 NaN，避免显示成「非数字」）
			case "health" -> entity == null ? 0d : (double) healthOf(entity);
			case "max_health" -> entity == null ? 0d : (double) maxHealthOf(entity);
			case "type_id" -> entity == null
					? "" : Registries.ENTITY_TYPE.getId(entity.getType()).toString();
			// 幼年 / 着地：直接读实体本身，不额外遍历。无目标时给 false，
			// 与「血量 0 / 名称空串」的取舍一致（不让脚本拿到 NaN）
			case "is_baby" -> entity instanceof LivingEntity living && living.isBaby();
			case "on_ground" -> entity != null && entity.isOnGround();
			default -> entity == null || client.player == null ? -1d : (double) client.player.distanceTo(entity);
		};
	}

	private static Object heldItemField(ScriptContext ctx, Object[] args) {
		String field = args.length > 0
				? ScratchCast.toString(args[0]).trim().toLowerCase(Locale.ROOT) : "id";
		MinecraftClient client = client(ctx);
		// 数量与最大耐久：判断「这一组还剩几个」「这工具还能修几次」，都是背包类脚本的常用判断。
		// 空手时数量给 0、最大耐久给 0（不可损坏物品的耐久百分比另由 durability 给 100）
		if ("count".equals(field) || "max_durability".equals(field)) {
			ItemStack stack = client.player == null ? null : client.player.getMainHandStack();
			if (stack == null || stack.isEmpty()) {
				return 0d;
			}
			return "count".equals(field) ? (double) stack.getCount() : (double) stack.getMaxDamage();
		}
		if ("durability".equals(field)) {
			return heldDurabilityPercent(client);
		}
		return heldItemId(client);
	}

	/** 菜单值 x/y/z → 轴序号；未知值按 x 处理。 */
	private static int axisOf(Object[] args) {
		String axis = args.length > 0 ? ScratchCast.toString(args[0]).trim().toLowerCase(Locale.ROOT) : "x";
		return switch (axis) {
			case "y" -> 1;
			case "z" -> 2;
			default -> 0;
		};
	}

	private static Object onGround(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player != null && client.player.isOnGround();
	}

	/** 主手物品的注册名；空手返回空串。 */
	private static String heldItemId(MinecraftClient client) {
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
	private static double heldDurabilityPercent(MinecraftClient client) {
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
				GameActions.clampCoord(ScratchCast.toNumber(args[0])),
				GameActions.clampCoord(ScratchCast.toNumber(args[1])),
				GameActions.clampCoord(ScratchCast.toNumber(args[2])));
		return Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
	}

	private static Object isDay(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.world != null && client.world.isDay();
	}

	private static Object inWater(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player != null && client.player.isTouchingWater();
	}

	private static Object inLava(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		return client.player != null && client.player.isInLava();
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
		EntityFilter.Parsed filter = EntityFilter.parse(ScratchCast.toString(args[0]));
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
	private static List<Entity> nearbyEntities(ScriptContext ctx) {
		return nearbyEntities(ctx, ENTITY_SEARCH_RANGE);
	}

	/**
	 * 同上，半径可调（实体快照用）。缓存键带半径：不同半径是同一次遍历的不同结果，
	 * 共用一个键会让 {@code 16} 格的结果顶替 {@code 32} 格。
	 */
	@SuppressWarnings("unchecked")
	private static List<Entity> nearbyEntities(ScriptContext ctx, double radius) {
		return (List<Entity>) ctx.cached("nearby_entities:" + radius, () -> {
			MinecraftClient client = client(ctx);
			if (client.player == null || client.world == null) {
				return List.of();
			}
			return client.world.getOtherEntities(client.player,
							client.player.getBoundingBox().expand(radius)).stream()
					// expand 出来的是方盒，对角可达 1.73r；这里再按真实距离过一遍，
					// 否则积木上写的「半径」与实际范围不符（B15）
					.filter(entity -> client.player.distanceTo(entity) <= radius)
					.toList();
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

	/** 读积木的「实体类型」参数；菜单必有值，缺失时按空串进解析（匹配不到）。 */
	private static EntityFilter.Parsed filterOf(Object[] args) {
		return EntityFilter.parse(ScratchCast.toString(args[0]));
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

	// ---- 快照（JSON 文本出口）----
	// 只读：返回普通字符串，脚本改它不会回到世界（ARCHITECTURE §26）。
	// 这里只负责把 Minecraft 对象拆成原语，字段名与格式全在 Snapshots 里，便于纯 JVM 单测。

	/** 玩家快照。无玩家时给 {@code {}}：空串会让下游 {@code json.get_path} 直接失败。 */
	private static Object playerJson(ScriptContext ctx, Object[] args) {
		return JsonText.stringify(playerSnapshot(client(ctx)));
	}

	/**
	 * 玩家快照对象；无客户端/无玩家时给空对象。
	 *
	 * <p>{@code public} 是给远程桥的快照用的：远程与游戏内共用同一份拼装，
	 * 免得两边的键名或 1 基/0 基约定悄悄分叉。</p>
	 */
	public static JsonObject playerSnapshot(MinecraftClient client) {
		if (client == null || client.player == null) {
			return new JsonObject();
		}
		ClientPlayerEntity player = client.player;
		List<Snapshots.Effect> effects = new ArrayList<>();
		for (StatusEffectInstance instance : player.getStatusEffects()) {
			effects.add(new Snapshots.Effect(
					instance.getEffectType().getKey().map(key -> key.getValue().toString()).orElse(""),
					instance.getAmplifier(),
					instance.getDuration()));
		}
		String gamemode = client.interactionManager == null
				? "" : client.interactionManager.getCurrentGameMode().getId();
		String dimension = client.world == null
				? "" : client.world.getRegistryKey().getValue().toString();
		return Snapshots.playerObject(new Snapshots.Player(
				player.getGameProfile().name(),
				player.getUuidAsString(),
				player.getHealth(),
				player.getMaxHealth(),
				player.getAbsorptionAmount(),
				player.getHungerManager().getFoodLevel(),
				player.getHungerManager().getSaturationLevel(),
				player.experienceLevel,
				player.experienceProgress,
				player.getArmor(),
				player.getAir(),
				gamemode,
				dimension,
				new Snapshots.Vec(player.getX(), player.getY(), player.getZ()),
				motion(player),
				player.getYaw(),
				player.getPitch(),
				player.isOnGround(),
				player.isTouchingWater(),
				player.isInLava(),
				// 与「当前槽位」积木同为 1 基：两处读数不一致比少一个字段更难查
				player.getInventory().getSelectedSlot() + 1,
				effects));
	}

	/**
	 * 附近实体快照（数组），按距离升序。
	 *
	 * <p>半径钳到 1–64 格、数量钳到 1–256：世界遍历在主线程跑，误填 {@code 1000} 会直接卡帧。
	 * 同一 tick 内重复调用只遍历一次（缓存键带过滤器与两个参数）。</p>
	 */
	private static Object entitiesJson(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client == null || client.player == null || client.world == null) {
			return "[]";
		}
		EntityFilter.Parsed filter = EntityFilter.parse(args.length > 0 ? ScratchCast.toString(args[0]) : "");
		double radius = args.length > 1 ? PathMath.clamp(ScratchCast.toNumber(args[1]), 1d, 64d) : ENTITY_SEARCH_RANGE;
		int limit = args.length > 2 ? (int) PathMath.clamp(ScratchCast.toNumber(args[2]), 1d, 256d) : 32;
		String key = "entities_json:" + filter.cacheKey() + ':' + radius + ':' + limit;
		return ctx.cached(key, () -> buildEntitiesJson(ctx, filter, radius, limit));
	}

	private static String buildEntitiesJson(ScriptContext ctx, EntityFilter.Parsed filter,
											double radius, int limit) {
		MinecraftClient client = client(ctx);
		if (client == null || client.player == null) {
			return "[]";
		}
		ClientPlayerEntity player = client.player;
		List<Snapshots.Entity> out = new ArrayList<>();
		for (Entity entity : nearbyEntities(ctx, radius)) {
			if (!entity.isAlive() || !EntityFilter.matches(entity, filter)) {
				continue;
			}
			out.add(entityEntry(player, entity));
		}
		out.sort(Comparator.comparingDouble(Snapshots.Entity::distance));
		if (out.size() > limit) {
			out = new ArrayList<>(out.subList(0, limit));
		}
		return Snapshots.entities(out);
	}

	/** 方块快照：id + 方块状态 + 硬度/光照/群系 + 方块实体内容。 */
	private static Object blockJson(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client == null || client.world == null) {
			return "{}";
		}
		BlockPos pos = new BlockPos(
				GameActions.clampCoord(ScratchCast.toNumber(args.length > 0 ? args[0] : 0)),
				GameActions.clampCoord(ScratchCast.toNumber(args.length > 1 ? args[1] : 0)),
				GameActions.clampCoord(ScratchCast.toNumber(args.length > 2 ? args[2] : 0)));
		BlockState state = client.world.getBlockState(pos);
		Map<String, String> properties = new LinkedHashMap<>();
		for (var entry : state.getEntries().entrySet()) {
			properties.put(entry.getKey().getName(), String.valueOf(entry.getValue()));
		}
		return Snapshots.block(new Snapshots.Block(
				Registries.BLOCK.getId(state.getBlock()).toString(),
				pos.getX(),
				pos.getY(),
				pos.getZ(),
				properties,
				state.getHardness(client.world, pos),
				state.isAir(),
				state.isSolidBlock(client.world, pos),
				!state.getFluidState().isEmpty(),
				client.world.getLightLevel(pos),
				biomeAt(client, pos),
				blockEntity(client, pos)));
	}

	/**
	 * 背包快照：41 格（0–35 主背包、36–39 盔甲、40 副手），只列非空槽位。
	 *
	 * <p>不走 {@code currentScreenHandler}：那个界面里的玩家格编号会随打开的容器整体后移
	 * （大箱子里是 27–62），拿它当背包编号会随场景变化。这里固定用 inventory 索引。</p>
	 */
	private static Object inventoryJson(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client == null || client.player == null) {
			return "[]";
		}
		return JsonText.stringify(inventoryArray(client));
	}

	/**
	 * 背包条目：只列非空槽位，槽位号是 inventory 索引 0–40。
	 *
	 * <p>{@code public} 是给远程桥的快照用的：同一份取值给游戏内与远程两条链路，
	 * 免得「背包里有什么」在两处各自实现、各自算错边界。</p>
	 */
	public static java.util.List<Snapshots.Item> inventoryItems(MinecraftClient client) {
		java.util.List<Snapshots.Item> items = new ArrayList<>();
		if (client == null || client.player == null) {
			return items;
		}
		PlayerInventory inventory = client.player.getInventory();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (!stack.isEmpty()) {
				items.add(itemOf(i, stack));
			}
		}
		return items;
	}

	/** 背包快照的 JSON 数组形态（远程桥直接塞进快照，不经过字符串）。 */
	public static JsonArray inventoryArray(MinecraftClient client) {
		return Snapshots.inventoryArray(inventoryItems(client));
	}

	/**
	 * 界面快照：当前打开界面的槽位，编号与「点击槽位」同一套。
	 *
	 * <p>没有界面时给 {@code {}}：此时 {@code currentScreenHandler} 仍指向玩家自己的
	 * 46 格合成界面，那不是「打开了一个界面」，照搬会让脚本以为有东西可点。</p>
	 */
	private static Object screenJson(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		JsonObject snapshot = screenSnapshot(client);
		return snapshot == null ? "{}" : JsonText.stringify(snapshot);
	}

	/**
	 * 界面快照对象；没有可装槽位的界面时返回 {@code null}。
	 *
	 * <p>{@code public} 同样是给远程桥用的：远程与游戏内必须给出**同一份**槽位编号，
	 * 否则「点击槽位 [n]」在两处指的不是同一个格子。</p>
	 */
	public static JsonObject screenSnapshot(MinecraftClient client) {
		if (client == null || client.player == null || client.currentScreen == null) {
			return null;
		}
		// 必须与 ContainerActions.handler() 同一判据：只有带槽位的界面才算「打开了容器」。
		// 用 player.currentScreenHandler 顶替的话，暂停菜单/聊天界面也会被当成 46 格的背包界面，
		// 「界面槽位数」与「点击槽位」的编号就全错了
		if (!(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider<?> provider)) {
			return null;
		}
		ScreenHandler handler = provider.getScreenHandler();
		if (handler == null) {
			return null;
		}
		String type = screenType(client.currentScreen, handler);
		int total = handler.slots.size();
		List<Snapshots.ScreenItem> items = new ArrayList<>();
		for (int i = 0; i < Math.min(total, Snapshots.SCREEN_LIMIT); i++) {
			Slot slot = handler.slots.get(i);
			ItemStack stack = slot.getStack();
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			// player_slot：点之前要能判断这格是自己的背包还是容器，避免把自己的东西搬进箱子
			items.add(new Snapshots.ScreenItem(itemOf(i, stack), slot.inventory instanceof PlayerInventory));
		}
		return Snapshots.screenObject(type, total, items);
	}

	/** 世界快照：时间、天数、月相、天气、维度、群系、光照。 */
	private static Object worldJson(ScriptContext ctx, Object[] args) {
		return JsonText.stringify(worldSnapshot(client(ctx)));
	}

	/** 世界快照对象；没有世界时给空对象。 */
	public static JsonObject worldSnapshot(MinecraftClient client) {
		if (client == null || client.world == null) {
			return new JsonObject();
		}
		long timeOfDay = client.world.getTimeOfDay();
		long days = Math.floorDiv(timeOfDay, 24000L);
		BlockPos pos = client.player == null ? BlockPos.ORIGIN : client.player.getBlockPos();
		return Snapshots.worldObject(new Snapshots.WorldFacts(
				Math.floorMod(timeOfDay, 24000L),
				// 1 基天数与 F3 的 Day 一致：第 0 天在界面上显示为第 1 天
				(int) (days + 1),
				// 月相 0–7，0 是满月；每 8 天一轮
				(int) Math.floorMod(days, 8L),
				client.world.isRaining(),
				client.world.isThundering(),
				client.world.getRegistryKey().getValue().toString(),
				biomeAt(client, pos),
				client.world.getLightLevel(pos)));
	}

	/**
	 * 「快照字段」：返回该快照的键名数组，供作者自助查询。
	 *
	 * <p>键名只存在于代码里，用户在 TurboWarp 里看不到；这块积木把清单直接交给脚本，
	 * 接到「说」或「日志」上就能一次看全，不必翻文档。</p>
	 */
	private static Object snapshotKeys(ScriptContext ctx, Object[] args) {
		return Snapshots.stringArray(Snapshots.keysOf(args.length > 0 ? ScratchCast.toString(args[0]) : ""));
	}

	/** 在线玩家快照：uuid / 名称 / 延迟 / 游戏模式。顺序由服务端给出（tab 列表顺序）。 */
	private static Object playersJson(ScriptContext ctx, Object[] args) {
		return JsonText.stringify(playersSnapshot(client(ctx)));
	}

	/** 在线玩家快照的数组形态；顺序即 tab 列表顺序，不重排。 */
	public static JsonArray playersSnapshot(MinecraftClient client) {
		if (client == null || client.getNetworkHandler() == null) {
			return new JsonArray();
		}
		List<Snapshots.PlayerEntry> out = new ArrayList<>();
		for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
			var profile = entry.getProfile();
			out.add(new Snapshots.PlayerEntry(
					profile.id() == null ? "" : profile.id().toString(),
					profile.name() == null ? "" : profile.name(),
					entry.getLatency(),
					// 单人/旁观等场景可能没有模式信息，给空串而不是 null
					entry.getGameMode() == null ? "" : entry.getGameMode().getId()));
		}
		return Snapshots.playersArray(out);
	}

	/**
	 * 最近 N 条聊天（旧的在前）。
	 *
	 * <p>N 夹在 1–{@link ChatState#CAPACITY}：缓冲容量固定，要更多得先改 ChatState，
	 * 不能靠参数绕过（否则等于开了个无界队列）。</p>
	 */
	private static Object chatHistory(ScriptContext ctx, Object[] args) {
		int count = args.length > 0
				? (int) PathMath.clamp(ScratchCast.toNumber(args[0]), 1d, ChatState.CAPACITY) : 10;
		return Snapshots.stringArray(ChatState.history(count));
	}

	/**
	 * 区域扫描：两角之间的**非空气**方块。
	 *
	 * <p>只返回非空气：完整体积的 JSON 轻易上百 KB，每帧一次就能拖垮帧率；
	 * 「哪里有方块」是绝大多数脚本要的，「哪格是空的」用坐标反推即可。</p>
	 *
	 * <p>两道闸：体积不超过 {@link #SCAN_VOLUME_LIMIT}（超出部分不扫），
	 * 列出的方块不超过 {@code LIMIT} 个。任一触发都置 {@code truncated=true}——
	 * 它表示「结果不全」，脚本应缩小范围或提高上限。遍历顺序 y → x → z，
	 * 因此截断先丢高层。</p>
	 */
	private static Object scanJson(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client == null || client.world == null) {
			return "{}";
		}
		int x1 = GameActions.clampCoord(ScratchCast.toNumber(args.length > 0 ? args[0] : 0));
		int y1 = GameActions.clampCoord(ScratchCast.toNumber(args.length > 1 ? args[1] : 0));
		int z1 = GameActions.clampCoord(ScratchCast.toNumber(args.length > 2 ? args[2] : 0));
		int x2 = GameActions.clampCoord(ScratchCast.toNumber(args.length > 3 ? args[3] : 0));
		int y2 = GameActions.clampCoord(ScratchCast.toNumber(args.length > 4 ? args[4] : 0));
		int z2 = GameActions.clampCoord(ScratchCast.toNumber(args.length > 5 ? args[5] : 0));
		int limit = args.length > 6 ? (int) PathMath.clamp(ScratchCast.toNumber(args[6]), 1d, 16384d) : 4096;
		int minX = Math.min(x1, x2);
		int minY = Math.min(y1, y2);
		int minZ = Math.min(z1, z2);
		int maxX = Math.max(x1, x2);
		int maxY = Math.max(y1, y2);
		int maxZ = Math.max(z1, z2);
		// 同一 tick 内重复调用只扫一次：扫描是这里最贵的操作，脚本常在循环里反复取
		String key = "scan_json:" + minX + ',' + minY + ',' + minZ + ','
				+ maxX + ',' + maxY + ',' + maxZ + ',' + limit;
		return ctx.cached(key, () -> buildScan(client, minX, minY, minZ, maxX, maxY, maxZ, limit));
	}

	private static String buildScan(MinecraftClient client, int minX, int minY, int minZ,
									int maxX, int maxY, int maxZ, int limit) {
		long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
		boolean truncated = volume > SCAN_VOLUME_LIMIT;
		List<Snapshots.BlockEntry> blocks = new ArrayList<>();
		int visited = 0;
		outer:
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					if (visited++ >= SCAN_VOLUME_LIMIT) {
						truncated = true;
						break outer;
					}
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = client.world.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					if (blocks.size() >= limit) {
						truncated = true;
						break outer;
					}
					blocks.add(new Snapshots.BlockEntry(x, y, z,
							Registries.BLOCK.getId(state.getBlock()).toString()));
				}
			}
		}
		return Snapshots.scan(new Snapshots.Scan(minX, minY, minZ, maxX, maxY, maxZ, truncated, blocks));
	}

	/**
	 * 界面的类型 id。
	 *
	 * <p><b>玩家背包与创造背包会抛异常</b>：{@code PlayerScreenHandler} 与
	 * {@code CreativeScreenHandler} 构造时传的类型都是 {@code null}（它们不是网络菜单），
	 * {@code getType()} 直接抛 {@code UnsupportedOperationException: Unable to construct this menu
	 * by type}。这不是异常状态，是原版的设计——所以这里兜住并给固定值，而不是让整块积木失败。</p>
	 *
	 * <p>两者**分开命名**：创造背包是 45 格物品栏 + 9 格快捷栏（实测 `slots=54`），
	 * 生存背包 46 格，脚本按 `type` 分流时不能混为一谈。</p>
	 */
	private static String screenType(Screen screen, ScreenHandler handler) {
		try {
			return Registries.SCREEN_HANDLER.getId(handler.getType()).toString();
		} catch (UnsupportedOperationException e) {
			return screen instanceof CreativeInventoryScreen
					? "minecraft:creative_inventory" : "minecraft:player";
		}
	}

	/** 物品条目：背包、界面、方块实体共用一份，附魔也在这里取。 */
	private static Snapshots.Item itemOf(int slot, ItemStack stack) {
		java.util.Map<String, Integer> enchantments = new LinkedHashMap<>();
		var component = stack.getEnchantments();
		if (component != null && !component.isEmpty()) {
			for (var entry : component.getEnchantments()) {
				String id = entry.getKey().map(key -> key.getValue().toString()).orElse("");
				if (!id.isEmpty()) {
					enchantments.put(id, component.getLevel(entry));
				}
			}
		}
		return new Snapshots.Item(slot,
				Registries.ITEM.getId(stack.getItem()).toString(),
				stack.getCount(),
				stack.getDamage(),
				stack.getMaxDamage(),
				stack.getCustomName() == null ? "" : stack.getCustomName().getString(),
				enchantments);
	}

	/**
	 * 方块实体的内容：容器槽位、告示牌文本、标量 NBT。
	 *
	 * <p>不做整棵 NBT 的 JSON 化：箱子的 {@code Items} 是列表，原样倒出来既大又难用，
	 * 而列表内容已由 {@code items} 结构化给出。标量仍然有用——熔炉的 {@code CookTime} /
	 * {@code BurnTime} 只存在于 NBT 里，客户端没有对应的 getter。</p>
	 */
	private static Snapshots.BlockEntity blockEntity(MinecraftClient client, BlockPos pos) {
		BlockEntity be = client.world.getBlockEntity(pos);
		if (be == null) {
			return null;
		}
		String type = Registries.BLOCK_ENTITY_TYPE.getId(be.getType()).toString();
		List<Snapshots.Item> items = new ArrayList<>();
		int total = 0;
		if (be instanceof Inventory inventory) {
			total = inventory.size();
			// 截断到 ITEM_LIMIT 条；items_total 给出真实槽位数，脚本据此判断有没有漏
			for (int i = 0; i < Math.min(total, Snapshots.ITEM_LIMIT); i++) {
				ItemStack stack = inventory.getStack(i);
				if (!stack.isEmpty()) {
					items.add(itemOf(i, stack));
				}
			}
		}
		Map<String, Object> nbt = new LinkedHashMap<>();
		try {
			NbtCompound compound = be.createNbtWithIdentifyingData(client.world.getRegistryManager());
			for (String key : compound.getKeys()) {
				if (nbt.size() >= Snapshots.NBT_LIMIT) {
					break;
				}
				// x / y / z 与 id 已在顶层给过，且 id 在 1.21.11 里序列化成
				// "Optional[minecraft:furnace]" 这种噪声，不进 nbt
				if ("x".equals(key) || "y".equals(key) || "z".equals(key) || "id".equals(key)) {
					continue;
				}
				NbtElement element = compound.get(key);
				if (element instanceof AbstractNbtNumber number) {
					nbt.put(key, number.doubleValue());
				} else if (element instanceof NbtString string) {
					nbt.put(key, string.asString());
				}
			}
		} catch (RuntimeException e) {
			// 模组方块实体可能拒绝客户端取 NBT；只丢 nbt 段，id/items/文本照常给
			LOG.debug("[script] 方块实体 NBT 读取失败 {}", pos, e);
		}
		String front = "";
		String back = "";
		if (be instanceof SignBlockEntity sign) {
			front = signText(sign.getFrontText());
			back = signText(sign.getBackText());
		}
		return new Snapshots.BlockEntity(type, items, total, nbt, front, back);
	}

	/** 告示牌四行文本，行间保留 {@code \n}（空行有版式意义），整体去掉首尾空白。 */
	private static String signText(SignText text) {
		if (text == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < 4; i++) {
			if (i > 0) {
				out.append('\n');
			}
			out.append(text.getMessage(i, false).getString());
		}
		return out.toString().strip();
	}

	private static Snapshots.Vec position(Entity entity) {
		return new Snapshots.Vec(entity.getX(), entity.getY(), entity.getZ());
	}

	/**
	 * 远程快照用的实体条目（含到玩家的距离）。
	 *
	 * <p>与 {@code buildEntitiesJson} 用同一份字段拼装，只是把「遍历 + 排序 + 截断」
	 * 交给快照侧统一做——远程每秒 20 份，重复遍历没有意义。</p>
	 */
	public static Snapshots.Entity entityEntry(ClientPlayerEntity player, Entity entity) {
		return new Snapshots.Entity(
				Registries.ENTITY_TYPE.getId(entity.getType()).toString(),
				entity.getType().getName().getString(),
				entity.getCustomName() == null ? "" : entity.getCustomName().getString(),
				entity.getUuidAsString(),
				(double) player.distanceTo(entity),
				position(entity),
				motion(entity),
				(double) healthOf(entity),
				(double) maxHealthOf(entity),
				entity instanceof LivingEntity living && living.isBaby(),
				entity.isOnGround());
	}

	/**
	 * 上一 tick 的位移（格/tick）。
	 *
	 * <p>不用 {@code getVelocity()}：客户端自己的速度基本恒为 0（移动由客户端权威），
	 * 用它算「移动速度」会永远得到 0。位移差分对玩家与实体都成立。</p>
	 */
	private static Snapshots.Vec motion(Entity entity) {
		return new Snapshots.Vec(
				entity.getX() - entity.lastX,
				entity.getY() - entity.lastY,
				entity.getZ() - entity.lastZ);
	}

	/** 血量：箭、掉落物这类非生物实体没有血量，给 0 而不是让整个快照失败。 */
	private static float healthOf(Entity entity) {
		return entity instanceof LivingEntity living ? living.getHealth() : 0f;
	}

	private static float maxHealthOf(Entity entity) {
		return entity instanceof LivingEntity living ? living.getMaxHealth() : 0f;
	}

	private static String biomeAt(MinecraftClient client, BlockPos pos) {
		return client.world == null
				? "" : client.world.getBiome(pos).getKey().map(key -> key.getValue().toString()).orElse("");
	}

	private static MinecraftClient client(ScriptContext ctx) {
		return (MinecraftClient) ctx.handle();
	}
}
