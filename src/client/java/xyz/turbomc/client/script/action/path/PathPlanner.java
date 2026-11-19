package xyz.turbomc.client.script.action.path;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.config.TurboMCConfig;
import xyz.turbomc.client.script.runtime.PerfProbe;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 用原版僵尸的 A\* 算出一条到目标的可走路线；够不到目标时给出「能走的那一段」。
 *
 * <p><b>为什么手写 navigator 而不是 {@code MobNavigation.findPathTo}</b>：
 * {@code EntityNavigation.findPathToAny} 有三道隐式门槛——{@code isAtValidPosition()} 为假直接返回
 * null（假僵尸永不 tick，{@code onGround} 恒假）、相同目标会复用 {@code currentPath}、
 * 搜索盒半径由 {@code FOLLOW_RANGE + 8} 决定（僵尸 35 → 43 格）。三个都要额外打补丁，
 * 漏一个就是静默返回 null 或旧路径。直接调 {@link PathNodeNavigator} 则参数直给，
 * 没有隐式状态。</p>
 *
 * <p>节点预算按距离给：{@code 距离 × 每格预算}（档位默认 14），clamp 到 [128, 3000]。原版生物用的是
 * {@code FOLLOW_RANGE × 16 ≈ 560}，那是固定值、与目标远近无关；这里按距离缩放，
 * 近距离不至于吃掉整帧，远距离也不会在半途撞顶。参考实现的预算直接等于直线距离，
 * 绕行时容易撞顶并静默返回半截路径。</p>
 */
public final class PathPlanner {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");

	// 目标上限与到达容差已改为可配置项，见 TurboMCConfig#pathMaxDistance() / #pathAccuracy()。
	// 上限仍在配置侧钳制到 32–192：客户端视距 16 区块 = 256 格，再远基本超出服务端视距。

	/**
	 * 覆盖半径 = 目标距离 × 该系数 + 余量，同时作为 A\* 的代价上限。
	 *
	 * <p><b>为什么两者必须用同一个数</b>：{@code findPathToAny} 的第 4 个参数（{@code maxRange}）
	 * 有两处比较——{@code node.getDistance(start) >= maxRange} 不再展开，
	 * {@code successor.pathLength >= maxRange} 不入堆。后者是<b>累计路径长度</b>
	 * （每步欧氏距离相加），下坡、上台阶、跨沟时单步能到 3~4，所以这个上限必须按"路径能有多长"给，
	 * 不能按"目标有多远"给——给成 1.6 倍会让绕行在地形起伏处被剪断，表现为"明明有路却停在中途"。
	 * 而 {@code ChunkCache} 又必须覆盖它，否则缓存之外全是空气，A\* 会从那里"穿墙"过去。</p>
	 *
	 * <p><b>这三个数不对外暴露</b>：单独放宽任何一个都会破坏上面的一致性。</p>
	 */
	private static final double SPAN_PER_BLOCK = 2.5;
	private static final int SPAN_MARGIN = 32;
	private static final int SPAN_MIN = 48;
	private static final int SPAN_MAX = 200;
	/**
	 * 节点预算 = 距离 × {@code pathBudgetPerBlock()}，clamp 到 [128, 3000]。
	 *
	 * <p>每格预算来自配置里的搜索强度档位（默认 14）。上下限保持固定：
	 * 低于 128 时近距离直线都会撞顶，高于 3000 单帧规划耗时不可控。</p>
	 */
	private static final int BUDGET_MIN = 128;
	private static final int BUDGET_MAX = 3000;
	/** 单帧预算告警线：超过它说明这一帧会明显掉帧，需要复核参数。 */
	private static final long SLOW_PLAN_MS = 15L;

	private static ZombieEntity phantom;
	private static World phantomWorld;
	private static LandPathNodeMaker nodeMaker;
	private static PathNodeNavigator navigator;
	private static int navigatorBudget = -1;

	private PathPlanner() {
	}

	/**
	 * 断开静态引用。退出世界、断开连接、停止引擎时调用。
	 *
	 * <p>下面几个字段是 {@code static}，而 {@code ClientWorld} 持着区块、实体、方块实体
	 * 一大堆数据；静态字段属于类加载器级别，只要模组不卸载，旧世界就等不到 GC。
	 * 单人反复换存档时，最多会多留住一整个世界的区块缓存。</p>
	 *
	 * <p>调用时机必须在移动会话 {@code abort} 之后：先断开再 abort，收尾代码会读到 null。</p>
	 */
	public static void reset() {
		if (phantom != null) {
			phantom.discard();
		}
		phantom = null;
		phantomWorld = null;
		nodeMaker = null;
		navigator = null;
		navigatorBudget = -1;
	}

	/**
	 * 一次规划的结果。
	 *
	 * <p>{@code reached=false} 表示 A\* 只给出了半截路（预算或代价上限剪断、或目标暂时不可达）。
	 * 这种路径<b>可以走</b>：走到它的终点后以那里为新起点再算下一段，比一次算完整条路更省——
	 * 每段的搜索半径按剩余距离缩放，A\* 的探索量是分段累加而不是一次性铺开。
	 * 但调用方必须知道它不是终点，否则会把「走到半截的最后一格」误判成到达。</p>
	 */
	public record Plan(List<BlockPos> nodes, boolean reached) {
	}

	/**
	 * 规划一条路线。
	 *
	 * <p>返回 {@code null} 表示这一段完全走不了：目标区块未加载、超过配置的最远目标距离（默认 128 格）、
	 * A\* 无解、半截路只有一个节点（一步都没迈出去），或路径经过未加载区块。
	 * 只是「够不到目标但能往前走」时返回 {@code reached=false} 的 {@link Plan}。</p>
	 */
	public static Plan plan(World world, ClientPlayerEntity player, BlockPos target) {
		TurboMCConfig config = TurboMCConfig.get();
		if (!world.isChunkLoaded(target)) {
			LOG.warn("[path] 目标区块未加载 ({}, {}, {})",
					target.getX(), target.getY(), target.getZ());
			return null;
		}
		Vec3d center = Vec3d.ofCenter(target);
		double distance = Math.sqrt(squaredHorizontal(player, center));
		int maxDistance = config.pathMaxDistance();
		if (distance > maxDistance) {
			LOG.info("[path] 目标太远 {} 格（上限 {}）", (int) distance, maxDistance);
			return null;
		}

		ZombieEntity mob = phantom(world, player);
		if (mob == null) {
			return null;
		}
		explainTarget(world, target);

		int span = MathHelper.clamp(
				(int) Math.ceil(distance * SPAN_PER_BLOCK) + SPAN_MARGIN, SPAN_MIN, SPAN_MAX);
		BlockPos base = player.getBlockPos();
		ChunkCache cache = new ChunkCache(world,
				base.add(-span, -span, -span),
				base.add(span, span, span));

		if (nodeMaker == null) {
			nodeMaker = new LandPathNodeMaker();
			nodeMaker.setCanSwim(true);            // 玩家会游泳；关掉它会把水域判成不可通行
			nodeMaker.setCanWalkOverFences(false); // 玩家翻不过栅栏，与原版一致
		}
		int budget = MathHelper.clamp(
				(int) (distance * config.pathBudgetPerBlock()), BUDGET_MIN, BUDGET_MAX);
		if (navigator == null) {
			navigator = new PathNodeNavigator(nodeMaker, budget);
			navigatorBudget = budget;
		} else if (navigatorBudget != budget) {
			navigator.setRange(budget);
			navigatorBudget = budget;
		}

		long start = System.nanoTime();
		Path path;
		try {
			// span 同时是「节点离起点的欧氏距离上限」与「累计路径代价上限」，
			// 也是 ChunkCache 的覆盖半径——三者必须一致，否则不是剪断路径，就是探出缓存穿墙。
			path = navigator.findPathToAny(cache, mob, Set.of(target),
					(float) span, config.pathAccuracy(), 1.0f);
		} catch (RuntimeException e) {
			LOG.warn("[path] 规划抛异常：{}", e.toString());
			return null;
		}
		long ms = (System.nanoTime() - start) / 1_000_000L;
		PerfProbe.end("path.plan", start);

		if (path == null) {
			LOG.info("[path] 无解：目标 ({}, {}, {})，距离 {} 格，耗时 {} ms",
					target.getX(), target.getY(), target.getZ(), (int) distance, ms);
			return null;
		}
		boolean reached = path.reachesTarget();
		if (!reached) {
			// 半截路照走：它通常是预算/代价上限剪断，或目标暂时不可达（隔着墙、还在更远的一段）。
			// 走到它停下的那一格，起点就前移了，下一段的搜索半径也随之变小。
			PathNode endNode = path.getEnd();
			BlockPos end = endNode == null ? null : new BlockPos(endNode.x, endNode.y, endNode.z);
			LOG.info("[path] 半截路：节点 {}/预算 {}，停在 {}（离目标 {} 格），覆盖/代价上限 {}，耗时 {} ms —— 先走这一段",
					path.getLength(), budget, end, (int) Math.round(distanceTo(end, target)),
					span, ms);
		}

		List<BlockPos> nodes = toNodes(path);
		if (nodes.isEmpty()) {
			return null;
		}
		// 半截且一步都没迈出去：再算一遍还是同一格，直接判无解，别让调用方空转
		if (!reached && nodes.size() <= 1) {
			LOG.info("[path] 半截路只有起点一个节点，视为无解");
			return null;
		}
		if (!chunksLoaded(world, nodes)) {
			return null;
		}
		if (ms >= SLOW_PLAN_MS) {
			LOG.warn("[path] 规划耗时 {} ms（距离 {} 格，覆盖半径 {}，预算 {}），这一帧可能掉帧",
					ms, (int) distance, span, budget);
		}
		LOG.debug("[path] 规划 {} ms / 节点 {} / 预算 {} / 终点 {} / 完整 {}",
				ms, nodes.size(), budget, nodes.get(nodes.size() - 1), reached);
		return new Plan(nodes, reached);
	}

	private static ZombieEntity phantom(World world, ClientPlayerEntity player) {
		if (phantom == null || phantomWorld != world) {
			if (phantom != null) {
				phantom.discard();
			}
			phantom = EntityType.ZOMBIE.create(world, SpawnReason.MOB_SUMMONED);
			if (phantom == null) {
				// create 若返回 null（注册表或客户端世界限制），退回直接构造
				phantom = new ZombieEntity(world);
			}
			phantomWorld = world;
			if (phantom != null) {
				// 不进世界、不 tick：只被 PathNodeMaker 读尺寸与位置
				phantom.setSilent(true);
			}
		}
		if (phantom == null) {
			LOG.warn("[path] 无法创建寻路锚点实体，本次放弃");
			return null;
		}
		phantom.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0f);
		return phantom;
	}

	/**
	 * 目标格明显不是"能站的地方"时给一条日志。
	 *
	 * <p>路线 B 直接调 {@link PathNodeNavigator}，<b>没有</b> {@code MobNavigation.retargetToSolidBlock}
	 * 那一步"目标在空中时向下吸附"。但到达判定用的是曼哈顿距离（配置项 {@code pathAccuracy}，默认 1），
	 * 所以 y 偏 1 格时相邻格仍算命中，偏 2 格以上才真的够不着。
	 * 这里刻意<b>不改目标</b>（y 由脚本作者给定，静默改掉更难查），只在日志里点明位置。</p>
	 */
	private static void explainTarget(World world, BlockPos target) {
		BlockPos up = target.up();
		BlockPos down = target.down();
		boolean feetFree = world.getBlockState(target).getCollisionShape(world, target).isEmpty();
		boolean headFree = world.getBlockState(up).getCollisionShape(world, up).isEmpty();
		boolean floorSolid = !world.getBlockState(down).getCollisionShape(world, down).isEmpty();
		if (feetFree && headFree && floorSolid) {
			return;
		}
		String hint;
		if (!feetFree) {
			// 本格是实心方块：人是站在它「上面」的，站立格在 y+1
			hint = "本格被方块占据，站立格应为 y=" + (target.getY() + 1);
		} else if (!headFree) {
			hint = "上方一格被占据，头顶不够高";
		} else {
			hint = "脚下没有方块，人会直接掉下去";
		}
		LOG.info("[path] 目标格 ({}, {}, {}) 不是可站立位置：{}（本格空={} 上方空={} 脚下实心={}）",
				target.getX(), target.getY(), target.getZ(), hint, feetFree, headFree, floorSolid);
	}

	/** 路径节点转 BlockPos：跳过空节点与相邻重复项（同一格连续出现只保留一个）。 */
	private static List<BlockPos> toNodes(Path path) {
		List<BlockPos> nodes = new ArrayList<>(path.getLength());
		BlockPos last = null;
		for (int i = 0; i < path.getLength(); i++) {
			BlockPos pos = path.getNodePos(i);
			if (pos == null || pos.equals(last)) {
				continue;
			}
			nodes.add(pos);
			last = pos;
		}
		return nodes;
	}

	/** 客户端把未加载区块当空气，路径会直接穿墙，必须逐节点复核。 */
	private static boolean chunksLoaded(World world, List<BlockPos> nodes) {
		for (int i = 0; i < nodes.size(); i++) {
			if (!world.isChunkLoaded(nodes.get(i))) {
				LOG.info("[path] 路径节点 {} 落在未加载区块 {}", i, nodes.get(i));
				return false;
			}
		}
		return true;
	}

	private static double squaredHorizontal(ClientPlayerEntity player, Vec3d target) {
		double dx = player.getX() - target.x;
		double dz = player.getZ() - target.z;
		return dx * dx + dz * dz;
	}

	/** 两个方块中心的 3D 距离，只用于日志；任一为空时返回 -1。 */
	private static double distanceTo(BlockPos a, BlockPos b) {
		if (a == null || b == null) {
			return -1d;
		}
		double dx = a.getX() - b.getX();
		double dy = a.getY() - b.getY();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
