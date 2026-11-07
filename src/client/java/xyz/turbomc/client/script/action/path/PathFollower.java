package xyz.turbomc.client.script.action.path;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.script.action.ScriptKeys;
import xyz.turbomc.client.script.action.motion.TurnMath;
import xyz.turbomc.client.script.action.motion.TurnSession;

import java.util.List;

/**
 * 一次「走向」的移动会话：每 tick 把路径节点翻译成「朝向 + 前进键 + 跳跃键」。
 *
 * <p>为什么不能算完就完：玩家没有 {@code MoveControl}（那是给 MobEntity 直接写移动向量的），
 * 客户端玩家的位移来自 {@code KeyBinding} → {@code tickMovement}，只能逐 tick 按键，
 * 并在整个过程中挂起脚本线程。</p>
 *
 * <p><b>按键按状态变化收发</b>：{@link ScriptKeys#hold} 是<b>幂等</b>的——重复按下只延长
 * 到期时间，不累加计数，所以即便每 tick 都调也不会把键卡住。本类仍用 {@link #setForward} /
 * {@link #setJump} 只在状态真的变化时才动：少做无谓的 {@code setPressed}，也让
 * {@code forwardHeld} 这类状态位始终等于「我们以为的物理状态」，排查时不用再猜。</p>
 *
 * <p>注意 {@code ticks = -1} 的长按键不会自动到期，收尾只能靠 {@link #abort} 或引擎停止时的
 * {@code ScriptKeys.releaseEveryKey()}。</p>
 *
 * <p>本类不直接依赖 {@code Path}：节点统一是 {@code BlockPos} 列表，由 {@link #computePlan} 提供。
 *
 * <p><b>半截路先走</b>：A\* 只给出半截时（预算/代价上限剪断，或目标暂时不可达），不去重新规划整条，
 * 而是走完这一段、以当前位置为新起点再算下一段。每段的搜索半径按剩余距离缩放，
 * 探索量是分段累加而不是一次性铺开。区分完整与半截靠 {@link PathPlanner.Plan#reached()}——
 * 少了这个标志，走到半截的最后一格会被误判成到达。</p>
 */
public final class PathFollower {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");

	/** 会话上限 60 秒：128 格 × 0.216 格/tick ≈ 592 tick，绕路按 1.6 倍算 ≈ 950，再留分段停顿的余量。 */
	private static final int MAX_TICKS = 1200;
	/** 脚本侧等待上限比会话上限多留 40 tick，让 Java 的超时先发生。 */
	private static final int WAIT_SLACK = 40;
	/** 结束后保留约 3 秒，便于回看走了哪条路。 */
	private static final int FADE_TICKS = 60;

	/**
	 * 节点推进阈值。参考实现用 1.0，角色会提前切角、贴着拐角内壁走；再小（0.35 以下）
	 * 又会在拐角来回摆。0.5 是「贴得够近但不切角」的折中。
	 */
	private static final double NODE_REACH = 0.5;
	/** 超过这个角度先站定再转：边走边转 90° 以上会横着蹭墙。 */
	private static final float STAND_TURN_DEG = 75f;

	private static final int STUCK_TICKS = 10;
	/** 卡住重算上限。与半截路的分段次数分开计：这是「原地打转」，那是「够不到目标」。 */
	private static final int MAX_REPLANS = 3;
	private static final int REPLAN_INTERVAL = 20;
	/**
	 * 半截路的分段上限。被剪断时每段可能只有十几格，128 格最多也就十几段；
	 * 给 16 是留余量，真正的总闸仍是 {@link #MAX_TICKS}。
	 */
	private static final int MAX_PARTIAL_REPLANS = 16;
	/** 连续几次半截路停在同一格：说明再算也一样，别空转。 */
	private static final int MAX_SAME_END = 3;
	/** 窗口式卡住判定：锚点位移超过半格即认为有进展（抗贴墙蠕动）。 */
	private static final double ANCHOR_PROGRESS = 0.5;
	private static final double STUCK_MIN_MOVE = 0.02;
	/** 位置突变（传送 / 服务端回拉）阈值。 */
	private static final double TELEPORT_SQUARED = 64;

	/**
	 * 氧气余量 = 深度 × 10 + 40 tick，clamp 到 [80, 280]。
	 *
	 * <p>依据：水下上浮约 0.1 格/tick（保守估计），每格留 10 tick，另加 40 tick 反应时间。
	 * 满氧 300 tick，所以上限压到 280——比这更深的水本来就浮不上来，早点中止比硬走好。</p>
	 */
	private static final int AIR_PER_BLOCK = 10;
	private static final int AIR_BASE = 40;
	private static final int AIR_MIN = 80;
	private static final int AIR_MAX = 280;
	/** 水面高度每 10 tick 重采一次：上浮过程中水面不变，没必要每 tick 向上扫。 */
	private static final int SURFACE_SAMPLE_INTERVAL = 10;
	/** 紧急上浮上限：水下没有可上的路时，不能一直按着跳跃键。 */
	private static final int SURFACE_TIMEOUT = 200;

	public enum State {
		IDLE, FOLLOWING, ARRIVED, GAVE_UP, TIMEOUT, CANCELLED
	}

	/** 供渲染线程读取的节点快照：不可变对象整体替换，不做字段级共享。 */
	public record NodeView(World world, float[] points, int count, Vec3d center) {
	}

	/**
	 * 给 HUD 读的状态快照。字段都是值，不做跨线程共享；{@code phase} 由跟随器自己判定，
	 * HUD 不解释内部状态机。
	 *
	 * @param replans 卡住重算次数（原地打转的那种）
	 * @param partialReplans 半截路的分段次数（够不到目标、分段靠近的那种）
	 */
	public record Status(String phase, BlockPos target, double distance,
			int nodeIndex, int nodeCount, int replans, int partialReplans, long planMs, int ticks) {
	}

	private static Session session;
	/** 终态保持到下一次 start()：poll 读它，读后不复位。 */
	private static State lastState = State.IDLE;
	private static volatile NodeView view;
	private static volatile int viewIndex;
	/** 紧急上浮：-1 表示未激活，>=0 是已经按住的 tick 数。与会话解耦，中止后仍要继续。 */
	private static int surfaceTicks = -1;
	private static boolean surfaceHeld;

	private PathFollower() {
	}

	// ---- 对外接口 ----

	/**
	 * 启动一次会话。返回 {@code false} 表示不需要挂起：玩家或世界不存在、目标在未加载区、
	 * 或者已经在到达范围内。
	 */
	public static boolean start(MinecraftClient client, MoveTarget target) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return false;
		}
		if (!client.world.isChunkLoaded(target.pos())) {
			LOG.warn("[path] 目标区块未加载 ({}, {}, {})",
					target.pos().getX(), target.pos().getY(), target.pos().getZ());
			return false;
		}
		// 幂等：已在到达范围内就立刻返回，不重建会话（支持「重复执行直到」的写法）
		if (isArrived(player, target.pos())) {
			// 只在没有会话在跑时才改 lastState：否则会把另一个脚本正在等的
			// move 等待提前放行，而那个会话还在走。
			if (session == null) {
				lastState = State.ARRIVED;
			}
			return false;
		}
		// 同一目标已在路上：沿用，不重置路径
		if (session != null && session.state == State.FOLLOWING && session.target.equals(target)) {
			return true;
		}
		closeSession(client);

		session = new Session(target, client.world, posOf(player));
		session.distance = target.horizontalDistanceTo(player);
		lastState = State.FOLLOWING;
		return true;
	}

	/** 脚本侧等待上限（tick）。比 {@link #MAX_TICKS} 多 40，让 Java 侧超时先结束。 */
	public static int waitLimitTicks() {
		return MAX_TICKS + WAIT_SLACK;
	}

	/** poll 的返回值：终态为真，且一直保持到下一次 start()。 */
	public static boolean finished() {
		return lastState != State.IDLE && lastState != State.FOLLOWING;
	}

	/** 中止当前会话（停止移动、关引擎、切工程、停止全部）。 */
	public static void abort(String reason) {
		// 上浮状态一并收掉，否则中止后跳跃键还按着、玩家会一直往上飘
		stopSurface();
		Session current = session;
		if (current == null) {
			return;
		}
		if (current.state == State.FOLLOWING) {
			finish(current, State.CANCELLED, MinecraftClient.getInstance(), reason);
		}
		view = null;
		viewIndex = 0;
		session = null;
	}

	/** 当前会话的可显示状态；没有会话时返回 {@code null}（HUD 据此不画）。 */
	public static Status status() {
		Session s = session;
		if (s == null) {
			return null;
		}
		return new Status(
				phase(s),
				s.target.pos(),
				s.distance,
				s.nodes == null ? 0 : s.index + 1,
				s.nodes == null ? 0 : s.nodes.size(),
				s.replans,
				s.partialReplans,
				s.planMs,
				s.ticks);
	}

	/** 阶段标识：给 HUD 拼翻译键用，不在这里写人类可读文本。 */
	private static String phase(Session s) {
		if (s.state == State.FOLLOWING) {
			if (s.nodes == null) {
				return "planning";
			}
			return s.turning ? "turning" : "follow";
		}
		return switch (s.state) {
			case ARRIVED -> "arrived";
			case GAVE_UP -> "gave_up";
			case TIMEOUT -> "timeout";
			case CANCELLED -> "cancelled";
			default -> "idle";
		};
	}

	public static NodeView nodeView() {
		return view;
	}

	public static int viewIndex() {
		return viewIndex;
	}

	// ---- 每 tick ----

	public static void tick(MinecraftClient client) {
		// 上浮与会话解耦：会话结束后它仍要把玩家顶出水面，否则「中止」等于「等死」
		if (surfaceTicks >= 0) {
			tickSurface(client);
		}
		Session s = session;
		if (s == null) {
			return;
		}
		if (s.state != State.FOLLOWING) {
			if (s.fadeTicks > 0) {
				s.fadeTicks--;
			}
			if (s.fadeTicks <= 0) {
				view = null;
				viewIndex = 0;
				session = null;
			}
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.world != s.world) {
			finish(s, State.CANCELLED, client, "离开世界");
			return;
		}

		s.ticks++;
		if (s.ticks > MAX_TICKS) {
			finish(s, State.TIMEOUT, client, null);
			return;
		}
		// 传送 / 服务端回拉：路径前提已经不成立
		if (posOf(player).squaredDistanceTo(s.lastPos) > TELEPORT_SQUARED) {
			finish(s, State.CANCELLED, client, "位置突变");
			return;
		}

		// 水下目标允许去，但不能拿命换：余量不够上浮就中止，并立刻开始上浮
		int reserve = airReserve(s, player);
		if (reserve > 0 && player.getAir() < reserve) {
			int depth = Math.max(1, s.waterSurfaceY - player.getBlockY());
			finish(s, State.GAVE_UP, client,
					"氧气不足（水下 " + depth + " 格，余 " + player.getAir() + " tick）");
			requestSurface();
			return;
		}

		if (s.nodes == null || s.wantReplan) {
			if (!replan(client, s, player)) {
				return;
			}
		}

		s.distance = horizontalTo(player, s.target.pos());
		int lastIndex = s.nodes.size() - 1;
		BlockPos end = s.nodes.get(lastIndex);
		if (s.index >= lastIndex && isArrived(player, end)) {
			if (s.pathReached) {
				finish(s, State.ARRIVED, client, null);
			} else {
				// 这一段走完了，目标还没到：留下重算标记，下一 tick 以当前位置为起点算下一段。
				// 不在这一 tick 直接算——重算要几毫秒，统一放在 tick 开头的 replan 分支里，
				// 才好吃到 REPLAN_INTERVAL 的节流、也才好统计耗时。
				s.wantReplan = true;
			}
			return;
		}

		// 节点推进：一次可以跨多个（贴地小步常常一 tick 跨 2~3 个）
		while (s.index < lastIndex && horizontalTo(player, s.nodes.get(s.index)) < NODE_REACH) {
			s.index++;
			viewIndex = s.index;
		}
		BlockPos node = s.nodes.get(s.index);

		// 水平已经贴住这个节点、但高度还没对上（典型是差 1 格台阶）：此时转向没有意义。
		// 只有节点在上方才跳；节点在下方时松键让它自己落下，否则会原地一直往上蹦。
		if (horizontalTo(player, node) < NODE_REACH) {
			s.turning = false;
			setForward(client, s, false);
			setJump(client, s, node.getY() > player.getBlockPos().getY());
			checkStuck(client, s, player);
			s.lastPos = posOf(player);
			return;
		}

		// 朝向：大角度先站定，小角度边走边转；转多少由比例收敛决定，不是固定 30°
		float diff = TurnMath.yawDiff(player.getYaw(), yawTo(player, node));
		if (Math.abs(diff) > STAND_TURN_DEG) {
			s.turning = true;
			setForward(client, s, false);
			setJump(client, s, false);
		} else {
			s.turning = false;
			setForward(client, s, true);
			setJump(client, s, shouldJump(player, node));
		}

		// 转向会话正在接管视角时让出 yaw，否则两边每 tick 各写一次、互相抵消
		if (!TurnSession.isActive()) {
			player.setYaw(player.getYaw() + TurnMath.yawStep(diff));
		}

		if (s.turning) {
			// 站定转身不算卡住，否则 10 tick 后会被误判
			s.anchor = posOf(player);
			s.stuckTicks = 0;
			s.lastPos = posOf(player);
			return;
		}
		checkStuck(client, s, player);
		s.lastPos = posOf(player);
	}

	// ---- 路径来源 ----

	/**
	 * 取得本次移动的节点序列。返回 {@code null} 表示这一段完全走不了。
	 * 半截路也会返回，由 {@link #replan} 记下来：走到头要再算下一段，不能当成到达。
	 */
	private static PathPlanner.Plan computePlan(MinecraftClient client, ClientPlayerEntity player, MoveTarget target) {
		return PathPlanner.plan(client.world, player, target.pos());
	}

	private static boolean replan(MinecraftClient client, Session s, ClientPlayerEntity player) {
		long started = System.nanoTime();
		PathPlanner.Plan plan = computePlan(client, player, s.target);
		s.planMs = (System.nanoTime() - started) / 1_000_000L;
		if (plan == null || plan.nodes().isEmpty()) {
			finish(s, State.GAVE_UP, client, "无解");
			return false;
		}
		if (!plan.reached() && !acceptPartial(s, plan.nodes())) {
			finish(s, State.GAVE_UP, client, "半截路不再前进");
			return false;
		}
		s.pathReached = plan.reached();
		s.nodes = plan.nodes();
		s.index = 0;
		s.wantReplan = false;
		s.lastReplan = s.ticks;
		buildView(s, player);
		return true;
	}

	/**
	 * 半截路的进度闸。
	 *
	 * <p>走一段再算一段能收敛，前提是每段的终点比上一段更靠前。若连续几次终点落在同一格，
	 * 说明 A\* 已经卡在那面墙前，再走也是原地打转，这时才放弃。</p>
	 */
	private static boolean acceptPartial(Session s, List<BlockPos> nodes) {
		if (s.partialReplans >= MAX_PARTIAL_REPLANS) {
			return false;
		}
		s.partialReplans++;
		BlockPos end = nodes.get(nodes.size() - 1);
		if (end.equals(s.lastPartialEnd)) {
			s.sameEndCount++;
		} else {
			s.sameEndCount = 0;
			s.lastPartialEnd = end;
		}
		return s.sameEndCount < MAX_SAME_END;
	}

	// ---- 判定 ----

	/**
	 * 是否已经站在 {@code end} 这一格上。
	 *
	 * <p>水平与垂直分开判，不能用 3D 距离：目标正上方 4 格的同一 (x,z) 水平距离是 0，
	 * 只判水平会立刻宣布到达。垂直卡 0.6 格——容得下半砖的 0.5，容不下整整一格。</p>
	 *
	 * <p>判的是路径终点而不是用户给的 target：A\* 会把终点吸到它认为可站的相邻格。</p>
	 */
	private static boolean isArrived(ClientPlayerEntity player, BlockPos end) {
		double dx = end.getX() + 0.5 - player.getX();
		double dz = end.getZ() + 0.5 - player.getZ();
		return dx * dx + dz * dz <= MoveTarget.ARRIVE_HORIZONTAL * MoveTarget.ARRIVE_HORIZONTAL
				&& Math.abs(player.getY() - end.getY()) <= MoveTarget.ARRIVE_VERTICAL;
	}

	private static boolean shouldJump(ClientPlayerEntity player, BlockPos node) {
		// 水下先判，且不受 3 格限制：
		// 不低于节点就按跳——比节点高是上浮，与节点同高是「按住抵消下沉」，
		// 否则沿水面水平游会一路沉底。节点明显更低（差 0.2 以上）才松手让它沉下去。
		if (player.isTouchingWater()) {
			return node.getY() >= player.getY() - 0.2;
		}
		// 陆地上只对眼前的节点跳：目标在几十格外又高一格时，不该一路蹦过去。
		// 阈值给到 3 格：A\* 会生成跨格的跳跃节点（水平 2 格以上），卡在 2.0 会把它们全抑制掉。
		if (horizontalTo(player, node) > 3.0) {
			return false;
		}
		return node.getY() > player.getBlockPos().getY() && player.isOnGround();
	}

	// ---- 水下 ----

	/**
	 * 按当前深度算需要的氧气余量；不在水下返回 -1。
	 *
	 * <p>水面高度每 {@link #SURFACE_SAMPLE_INTERVAL} tick 重采一次，不是每 tick 都向上扫——
	 * 上浮过程中水面不变，逐 tick 扫描纯属浪费。</p>
	 */
	private static int airReserve(Session s, ClientPlayerEntity player) {
		if (!player.isSubmergedInWater()) {
			return -1;
		}
		if (s.ticks - s.surfaceSampleTick >= SURFACE_SAMPLE_INTERVAL) {
			s.waterSurfaceY = waterSurfaceY(player);
			s.surfaceSampleTick = s.ticks;
		}
		int depth = Math.max(1, s.waterSurfaceY - player.getBlockY());
		return MathHelper.clamp(depth * AIR_PER_BLOCK + AIR_BASE, AIR_MIN, AIR_MAX);
	}

	/** 从玩家所在列向上找第一个非液体方块，作为水面高度。 */
	private static int waterSurfaceY(ClientPlayerEntity player) {
		World world = player.getEntityWorld();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int x = MathHelper.floor(player.getX());
		int z = MathHelper.floor(player.getZ());
		int top = world.getBottomY() + world.getHeight();
		for (int y = MathHelper.floor(player.getY()) + 1; y < top; y++) {
			pos.set(x, y, z);
			if (world.getFluidState(pos).isEmpty()) {
				return y;
			}
		}
		return top;
	}

	/** 中止后开始上浮。与会话无关，由 tick 持续驱动到出水或超时。 */
	private static void requestSurface() {
		surfaceTicks = 0;
	}

	private static void tickSurface(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || !player.isSubmergedInWater() || surfaceTicks >= SURFACE_TIMEOUT) {
			stopSurface();
			return;
		}
		if (!surfaceHeld) {
			ScriptKeys.hold(null, client.options.jumpKey, -1);
			surfaceHeld = true;
		}
		surfaceTicks++;
	}

	/** 收掉上浮状态。必须与 {@link #surfaceHeld} 成对释放，否则跳跃键会一直按着。 */
	private static void stopSurface() {
		if (surfaceHeld) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client != null) {
				ScriptKeys.release(null, client.options.jumpKey);
			}
			surfaceHeld = false;
		}
		surfaceTicks = -1;
	}

	/**
	 * 窗口式卡住判定：锚点累计位移够 {@link #ANCHOR_PROGRESS} 就重置窗口，所以贴墙蠕动
	 * （每 tick 动一点点）不会被误判。真卡住 {@link #STUCK_TICKS} tick 后重算，
	 * 累计 {@link #MAX_REPLANS} 次仍卡就放弃。
	 */
	private static void checkStuck(MinecraftClient client, Session s, ClientPlayerEntity player) {
		if (horizontalFrom(s.anchor, posOf(player)) >= ANCHOR_PROGRESS) {
			s.anchor = posOf(player);
			s.stuckTicks = 0;
			return;
		}
		if (s.lastPos.distanceTo(posOf(player)) < STUCK_MIN_MOVE) {
			s.stuckTicks++;
		}
		if (s.stuckTicks < STUCK_TICKS) {
			return;
		}
		s.anchor = posOf(player);
		s.stuckTicks = 0;
		if (s.replans >= MAX_REPLANS) {
			finish(s, State.GAVE_UP, client, "卡住，重算 " + s.replans + " 次");
			return;
		}
		if (s.ticks - s.lastReplan < REPLAN_INTERVAL) {
			return;   // 节流：这一轮先不重算，10 tick 后再试
		}
		s.replans++;
		s.wantReplan = true;
	}

	// ---- 按键 ----

	private static void setForward(MinecraftClient client, Session s, boolean want) {
		if (want == s.forwardHeld) {
			return;
		}
		if (want) {
			ScriptKeys.hold(null, client.options.forwardKey, -1);
		} else {
			ScriptKeys.release(null, client.options.forwardKey);
		}
		s.forwardHeld = want;
	}

	private static void setJump(MinecraftClient client, Session s, boolean want) {
		if (want == s.jumpHeld) {
			return;
		}
		if (want) {
			ScriptKeys.hold(null, client.options.jumpKey, -1);
		} else {
			ScriptKeys.release(null, client.options.jumpKey);
		}
		s.jumpHeld = want;
	}

	// ---- 会话生命周期 ----

	private static void finish(Session s, State state, MinecraftClient client, String reason) {
		if (s.state != State.FOLLOWING) {
			return;
		}
		s.state = state;
		s.fadeTicks = state == State.CANCELLED ? 0 : FADE_TICKS;
		if (client != null) {
			setForward(client, s, false);
			setJump(client, s, false);
		}
		lastState = state;
		if (state == State.ARRIVED) {
			LOG.info("[path] 到达：{} tick，节点 {}，重算 {} 次，分段 {} 段",
					s.ticks, s.nodes == null ? 0 : s.nodes.size(), s.replans, s.partialReplans);
		} else if (state == State.TIMEOUT) {
			LOG.info("[path] 超时：{} tick，节点 {}，重算 {} 次，分段 {} 段",
					s.ticks, s.nodes == null ? 0 : s.nodes.size(), s.replans, s.partialReplans);
		} else if (state == State.GAVE_UP) {
			LOG.info("[path] 放弃：重算 {} 次，分段 {} 段{}",
					s.replans, s.partialReplans, reason == null ? "" : "，原因：" + reason);
		} else {
			LOG.info("[path] 中止：{}", reason == null ? "未知" : reason);
		}
	}

	private static void closeSession(MinecraftClient client) {
		Session s = session;
		if (s != null) {
			setForward(client, s, false);
			setJump(client, s, false);
			if (s.state == State.FOLLOWING) {
				s.state = State.CANCELLED;
				lastState = State.CANCELLED;
			}
		}
		session = null;
		view = null;
		viewIndex = 0;
	}

	private static void buildView(Session s, ClientPlayerEntity player) {
		List<BlockPos> nodes = s.nodes;
		float[] points = new float[nodes.size() * 3];
		for (int i = 0; i < nodes.size(); i++) {
			BlockPos pos = nodes.get(i);
			points[i * 3] = pos.getX() + 0.5f;
			points[i * 3 + 1] = pos.getY();
			points[i * 3 + 2] = pos.getZ() + 0.5f;
		}
		view = new NodeView(s.world, points, nodes.size(), posOf(player));
		viewIndex = s.index;
	}

	// ---- 数学 ----

	/** 1.21.11 的 Entity 没有再暴露 Vec3d 形式的坐标，只能自己拼。 */
	private static Vec3d posOf(ClientPlayerEntity player) {
		return new Vec3d(player.getX(), player.getY(), player.getZ());
	}

	private static double horizontalTo(ClientPlayerEntity player, BlockPos pos) {
		double dx = pos.getX() + 0.5 - player.getX();
		double dz = pos.getZ() + 0.5 - player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static float yawTo(ClientPlayerEntity player, BlockPos pos) {
		double dx = pos.getX() + 0.5 - player.getX();
		double dz = pos.getZ() + 0.5 - player.getZ();
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	private static double horizontalFrom(Vec3d a, Vec3d b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ---- 会话数据 ----

	private static final class Session {
		final MoveTarget target;
		final World world;
		List<BlockPos> nodes;
		int index;
		int ticks;
		int fadeTicks = -1;
		Vec3d anchor;
		Vec3d lastPos;
		int stuckTicks;
		double distance;
		long planMs;
		int replans;
		int lastReplan = -REPLAN_INTERVAL;
		boolean wantReplan = true;
		boolean turning;
		boolean forwardHeld;
		boolean jumpHeld;
		/** 当前这条路径是否真的到了目标；半截路走到头要接着算。 */
		boolean pathReached = true;
		/** 半截路的分段次数，与卡住重算的 {@link #replans} 分开计数。 */
		int partialReplans;
		BlockPos lastPartialEnd;
		int sameEndCount;
		int waterSurfaceY;
		int surfaceSampleTick = -100;
		State state = State.FOLLOWING;

		Session(MoveTarget target, World world, Vec3d startPos) {
			this.target = target;
			this.world = world;
			this.anchor = startPos;
			this.lastPos = startPos;
		}
	}
}
