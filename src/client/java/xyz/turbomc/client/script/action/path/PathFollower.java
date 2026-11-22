package xyz.turbomc.client.script.action.path;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.config.TurboMCConfig;
import xyz.turbomc.client.script.action.ScriptKeys;
import xyz.turbomc.client.script.action.motion.TurnMath;
import xyz.turbomc.client.script.action.motion.TurnSession;
import xyz.turbomc.client.script.view.ViewSmoother;

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

	// 会话超时、到达容差判定、卡住窗口与氧气余量的常量与算术都在 {@link PathMath} 里：
	// 那四段是纯算术且不会抛异常，抽出去才能用单测钉住（见 PathMathTest）。
	/** 结束后保留约 3 秒，便于回看走了哪条路。 */
	private static final int FADE_TICKS = 60;

	// 节点推进阈值与站定转向阈值已改为可配置项，见 TurboMCConfig#pathNodeReach() 与
	// #pathStandTurnDeg()。两者的取舍说明（几何耦合：转弯半径 ≈ 1.03 格）留在配置字段的 javadoc 里。
	/**
	 * 半截路的分段上限。被剪断时每段可能只有十几格，128 格最多也就十几段；
	 * 给 16 是留余量，真正的总闸仍是会话超时（见 {@link PathMath#sessionTicks}）。
	 */
	private static final int MAX_PARTIAL_REPLANS = 16;
	/** 连续几次半截路停在同一格：说明再算也一样，别空转。 */
	private static final int MAX_SAME_END = 3;
	/** 位置突变（传送 / 服务端回拉）阈值。 */
	private static final double TELEPORT_SQUARED = 64;

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
		session.maxTicks = PathMath.sessionTicks(session.distance);
		lastState = State.FOLLOWING;
		return true;
	}

	/**
	 * 脚本侧等待上限（tick）。比会话上限多留 40 tick，让 Java 侧的超时先结束。
	 *
	 * <p>必须传玩家：上限按起始距离缩放，128 格时仍是 1200 + 40。</p>
	 */
	public static int waitLimitTicks(MoveTarget target, ClientPlayerEntity player) {
		return PathMath.waitTicks(target.horizontalDistanceTo(player));
	}

	/** poll 的返回值：终态为真，且一直保持到下一次 start()。 */
	public static boolean finished() {
		return lastState != State.IDLE && lastState != State.FOLLOWING;
	}

	/** 中止当前会话（停止移动、关引擎、切工程、停止全部）。 */
	public static void abort(String reason) {
		// 视角控制一并交还：会话已经不在了，再按旧目标转没有意义
		ViewSmoother.clear("走向中止");
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
		if (s.ticks > s.maxTicks) {
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
				// 才好吃到 {@link PathMath#REPLAN_INTERVAL} 的节流、也才好统计耗时。
				s.wantReplan = true;
			}
			return;
		}

		// 精度参数每 tick 现读配置：菜单里改完立刻生效，不需要重开会话。
		TurboMCConfig config = TurboMCConfig.get();
		double nodeReach = config.pathNodeReach();
		double standTurnDeg = config.pathStandTurnDeg();

		// 节点推进：一次可以跨多个（贴地小步常常一 tick 跨 2~3 个）
		while (s.index < lastIndex && horizontalTo(player, s.nodes.get(s.index)) < nodeReach) {
			s.index++;
			viewIndex = s.index;
		}
		BlockPos node = s.nodes.get(s.index);

		// 水平已经贴住这个节点、但高度还没对上（典型是差 1 格台阶）：此时转向没有意义。
		// 只有节点在上方才跳；节点在下方时松键让它自己落下，否则会原地一直往上蹦。
		if (horizontalTo(player, node) < nodeReach) {
			s.turning = false;
			setForward(client, s, false);
			setJump(client, s, node.getY() > player.getBlockPos().getY());
			checkStuck(client, s, player);
			s.lastPos = posOf(player);
			return;
		}

		// 朝向：大角度先站定，小角度边走边转。
		// 真正的 yaw 写入交给 ViewSmoother 在渲染帧上按 dt 收敛：原版第一人称的
		// getYaw(float) 不插值，在 tick 里写一次就是 20 Hz 台阶（每 3 帧跳一格）。
		float desiredYaw = yawTo(player, node);
		float diff = TurnMath.yawDiff(player.getYaw(), desiredYaw);
		// 转向会话接管时让出：两套目标不能同时生效
		if (!TurnSession.isActive()) {
			ViewSmoother.setWalkTarget(desiredYaw);
		}
		if (Math.abs(diff) > standTurnDeg) {
			s.turning = true;
			setForward(client, s, false);
			setJump(client, s, false);
		} else {
			s.turning = false;
			setForward(client, s, true);
			setJump(client, s, shouldJump(player, node));
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
	 * <p>判的是路径终点而不是用户给的 target：A\* 会把终点吸到它认为可站的相邻格。
	 * 水平/垂直分开判的理由与两个容差的来历见 {@link PathMath#arrived}。</p>
	 */
	private static boolean isArrived(ClientPlayerEntity player, BlockPos end) {
		TurboMCConfig config = TurboMCConfig.get();
		return PathMath.arrived(player.getX(), player.getY(), player.getZ(),
				end.getX(), end.getY(), end.getZ(),
				config.pathArriveHorizontal(), config.pathArriveVertical());
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
		return PathMath.airReserveForDepth(depth);
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
	 * 窗口式卡住判定：锚点累计位移够半格就重置窗口，真卡住 10 tick 后重算，
	 * 累计 3 次仍卡就放弃。两个阈值各自管什么、以及「蠕动」在哪个区间才不会误判，
	 * 见 {@link PathMath#stuckWindow}——那里有单测，别只按这里的直觉改。
	 */
	private static void checkStuck(MinecraftClient client, Session s, ClientPlayerEntity player) {
		Vec3d now = posOf(player);
		PathMath.Window window = PathMath.stuckWindow(
				s.anchor.x, s.anchor.z,
				s.lastPos.x, s.lastPos.y, s.lastPos.z,
				now.x, now.y, now.z, s.stuckTicks);
		if (window.progressed()) {
			s.anchor = now;
			s.stuckTicks = 0;
			return;
		}
		s.stuckTicks = window.stuckTicks();
		if (s.stuckTicks < PathMath.STUCK_TICKS) {
			return;
		}
		s.anchor = now;
		s.stuckTicks = 0;
		switch (PathMath.replanStep(s.replans, s.ticks - s.lastReplan)) {
			case GIVE_UP -> finish(s, State.GAVE_UP, client, "卡住，重算 " + s.replans + " 次");
			// 节流：这一轮先不重算，下个窗口再试
			case WAIT -> {
			}
			case REPLAN -> {
				s.replans++;
				s.wantReplan = true;
			}
		}
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
		// 会话结束就不再接管视角；旧目标留在那里会让镜头继续转
		ViewSmoother.clear("走向结束");
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
		// 被新会话取代：先交还视角，新会话下一次 tick 再登记自己的目标
		ViewSmoother.clear("走向会话被替换");
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

	// ---- 会话数据 ----

	private static final class Session {
		final MoveTarget target;
		final World world;
		List<BlockPos> nodes;
		int index;
		int ticks;
		/** 本会话的 tick 上限，由起始距离算出（见 {@link PathMath#sessionTicks}）。 */
		int maxTicks = PathMath.TICKS_MIN;
		int fadeTicks = -1;
		Vec3d anchor;
		Vec3d lastPos;
		int stuckTicks;
		double distance;
		long planMs;
		int replans;
		int lastReplan = -PathMath.REPLAN_INTERVAL;
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
