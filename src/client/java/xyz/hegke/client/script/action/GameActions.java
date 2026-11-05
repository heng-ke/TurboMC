package xyz.hegke.client.script.action;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.inventory.Inventory;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.hegke.client.config.HkScriptConfig;
import xyz.hegke.client.config.KeyNames;
import xyz.hegke.client.script.ScriptEngine;
import xyz.hegke.client.script.runtime.ScriptContext;
import xyz.hegke.client.script.runtime.Values;

import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 会改变世界状态的动作：挖掘、移动、攻击、使用、放置、界面。
 *
 * <p>动作走「输入模拟 + 交互管理器」两条通道，不发送自定义数据包。
 * 只读的世界状态查询在 {@link WorldQueries}。</p>
 */
public final class GameActions {
	private static final Logger LOG = LoggerFactory.getLogger("hk-script/script");
	private static final double ATTACK_RANGE = 3.5d;
	/**
	 * 某按键名是否被禁止按下。
	 *
	 * <p>判定顺序：黑名单 → 白名单。白名单为空表示不限制；非空时只有列出的键能按。
	 * 名字既可以是物理键（{@code t}、{@code f5}），也可以是动作 id（{@code key.chat}）。</p>
	 */
	public static boolean isBlockedName(String raw) {
		String canonical = KeyNames.canonical(raw);
		if (canonical.isEmpty()) {
			return false;
		}
		HkScriptConfig config = HkScriptConfig.get();
		if (config.blockedKeySet().contains(canonical)) {
			return true;
		}
		Set<String> allowed = config.allowedKeySet();
		return !allowed.isEmpty() && !allowed.contains(canonical);
	}

	/**
	 * 某个按键绑定是否被禁止：物理键或动作 id 任一命中都算。
	 */
	private static boolean isBlocked(KeyBinding binding, String translationKey) {
		return isBlockedName(translationKey) || isBlockedName(binding.getId());
	}

	/**
	 * 无效键名只警告一次，避免每 tick 刷屏。
	 */
	private static void warnKey(String raw, String reason) {
		if (WARNED_KEYS.add(raw)) {
			LOG.warn("[script] 按键 \"{}\" 无效：{}", raw, reason);
		}
	}
	/**
	 * 上一次发送聊天/命令的时间槽（50ms 一个）。同一槽内只放行一条，
	 * 防止「循环节流关闭」时一个 tick 跑上百轮循环把几百条消息砸进服务器（会被踢）。
	 */
	private static long lastChatSlot = Long.MIN_VALUE;

	/** 上次重生请求时间，避免循环里连发。 */
	private static long lastRespawnAt;

	/** 已警告过的未知按键名，避免每 tick 刷屏。 */
	private static final Set<String> WARNED_KEYS = new HashSet<>();

	private GameActions() {
	}

	public static void registerAll() {
		NativeRegistry.action("action.key", GameActions::key);
		NativeRegistry.action("action.mouse", GameActions::mouse);
		NativeRegistry.action("action.attack_nearest", GameActions::attackNearest);
		NativeRegistry.action("action.attack_nearest_hostile", GameActions::attackNearestHostile);
		NativeRegistry.action("action.open_container", GameActions::openContainer);
		NativeRegistry.action("action.move_toward", GameActions::moveToward);
		NativeRegistry.action("action.stop", GameActions::stop);
		NativeRegistry.action("action.look", GameActions::look);
		NativeRegistry.action("action.look_at", GameActions::lookAt);
		NativeRegistry.action("debug.log", GameActions::log);
		NativeRegistry.action("action.broadcast", GameActions::broadcast);
		NativeRegistry.action("action.chat", GameActions::chat);
		NativeRegistry.action("action.command", GameActions::command);
		NativeRegistry.action("action.tap", GameActions::tap);
		NativeRegistry.action("action.click", GameActions::click);
		NativeRegistry.action("action.respawn", GameActions::respawn);
		NativeRegistry.action("action.sleep", GameActions::sleep);
		NativeRegistry.action("action.place_block", GameActions::placeBlock);
		NativeRegistry.action("action.break_wait", GameActions::breakWait);
	}

	/**
	 * 等待准星所指的方块被破坏。
	 *
	 * <p>没有固定时长，因此用条件型等待：JS 侧每帧回调 {@code ScriptBridge.poll("break")}，
	 * 由 {@link WorldQueries#probeBreak} 判定。上限 200 帧（约 10 秒），
	 * 超时或被挡（GIVE_UP）都放行，避免脚本永久挂住。</p>
	 */
	private static void breakWait(ScriptContext ctx, Object[] args) {
		ctx.requestWaitUntil("break", 200);
	}




	/**
	 * 自动索敌：转向最近的存活实体，进入攻击距离后立即攻击。
	 *
	 * <p>超出距离只转向不出手，避免客户端发出必然被服务端拒绝的攻击包。</p>
	 */
	private static void attackNearest(ScriptContext ctx, Object[] args) {
		attackEntity(ctx, entity -> true, "nearest_entity");
	}

	private static void attackNearestHostile(ScriptContext ctx, Object[] args) {
		attackEntity(ctx, entity -> entity instanceof Monster, "nearest_hostile");
	}

	private static void attackEntity(ScriptContext ctx, Predicate<Entity> filter, String cacheKey) {
		MinecraftClient client = client(ctx);
		if (client.interactionManager == null || client.player == null) {
			return;
		}
		Entity target = WorldQueries.nearestEntityOrNull(ctx, filter, cacheKey);
		if (target == null) {
			return;
		}
		client.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target.getEyePos());
		if (client.player.distanceTo(target) <= ATTACK_RANGE) {
			client.interactionManager.attackEntity(client.player, target);
			client.player.swingHand(Hand.MAIN_HAND);
		}
	}

	/**
	 * 打开附近最近的容器（箱子、熔炉等实现了 Inventory 的方块实体）。
	 */
	private static void openContainer(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.interactionManager == null || client.player == null || client.world == null) {
			return;
		}
		ClientPlayerEntity player = client.player;
		BlockPos origin = player.getBlockPos();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockPos pos : BlockPos.iterate(origin.add(-4, -2, -4), origin.add(4, 2, 4))) {
			if (!(client.world.getBlockEntity(pos) instanceof Inventory)) {
				continue;
			}
			double distance = player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = pos.toImmutable();
			}
		}
		if (best == null) {
			return;
		}
		Vec3d center = Vec3d.ofCenter(best);
		player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, center);
		client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
				new BlockHitResult(center, Direction.UP, best, false));
		player.swingHand(Hand.MAIN_HAND);
	}


	/**
	 * 朝坐标走一步：转向目标并按住前进键，进入 0.5 格内自动松手。
	 */
	private static void moveToward(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.player == null) {
			return;
		}
		double targetX = Values.asNumber(args[0]);
		double targetZ = Values.asNumber(args[1]);
		double dx = targetX - client.player.getX();
		double dz = targetZ - client.player.getZ();
		KeyBinding forward = client.options.forwardKey;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 0.5d) {
			ScriptKeys.release(ctx.currentInstance(), forward);
			return;
		}
		client.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
		ScriptKeys.hold(ctx.currentInstance(), forward, -1);
		// 步行约 0.216 格/tick；取 0.15 留出被地形拖慢的余量，上限 200 帧防止卡死。
		// 这是估算而非到达判定：被挡住时会提前放行，需要精确语义时应配合「重复执行直到」使用。
		ctx.requestWait((int) Math.min(200d, Math.ceil(distance / 0.15d)));
	}

	private static void stop(ScriptContext ctx, Object[] args) {
		releaseMovementKeys();
	}




	/**
	 * 在指定坐标放置手持方块：目标必须是空气，从首个相邻固体面出手。
	 *
	 * <p>不做「能否放置」的预判，交给服务端校验——客户端预测放错只会回弹，不会刷物品。</p>
	 */
	private static void placeBlock(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.world == null || client.player == null || client.interactionManager == null) {
			return;
		}
		BlockPos target = new BlockPos(
				clampCoord(Values.asNumber(args[0])),
				clampCoord(Values.asNumber(args[1])),
				clampCoord(Values.asNumber(args[2])));
		if (!client.world.getBlockState(target).isAir()) {
			return;
		}
		for (Direction direction : Direction.values()) {
			BlockPos neighbor = target.offset(direction);
			if (client.world.getBlockState(neighbor).isAir()) {
				continue;
			}
			Vec3d hitPos = Vec3d.ofCenter(neighbor);
			client.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, hitPos);
			client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
					new BlockHitResult(hitPos, direction.getOpposite(), neighbor, false));
			client.player.swingHand(Hand.MAIN_HAND);
			return;
		}
	}

	/**
	 * 右键附近最近的床。
	 */
	private static void sleep(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.world == null || client.player == null || client.interactionManager == null) {
			return;
		}
		BlockPos origin = client.player.getBlockPos();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.iterate(origin.add(-6, -2, -6), origin.add(6, 2, 6))) {
			if (!client.world.getBlockState(pos).isIn(BlockTags.BEDS)) {
				continue;
			}
			double distance = client.player.squaredDistanceTo(Vec3d.ofCenter(pos));
			if (distance < bestDistance) {
				bestDistance = distance;
				best = pos.toImmutable();
			}
		}
		if (best == null) {
			return;
		}
		Vec3d center = Vec3d.ofCenter(best);
		client.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, center);
		client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
				new BlockHitResult(center, Direction.UP, best, false));
		client.player.swingHand(Hand.MAIN_HAND);
	}



	private static void look(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.player == null) {
			return;
		}
		client.player.setYaw((float) Values.asNumber(args[0]));
		client.player.setPitch((float) Values.asNumber(args[1]));
	}

	private static void lookAt(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.player == null) {
			return;
		}
		Vec3d target = new Vec3d(
				Values.asNumber(args[0]),
				Values.asNumber(args[1]),
				Values.asNumber(args[2]));
		client.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target);
	}


	private static void log(ScriptContext ctx, Object[] args) {
		LOG.info("{}", Values.asString(args[0]));
	}

	private static void broadcast(ScriptContext ctx, Object[] args) {
		ScriptEngine.get().broadcast(Values.asString(args[0]));
	}

	/**
	 * 把脚本传入的坐标钳制到世界边界内。
	 *
	 * <p>直接 {@code (int)} 转换会让 {@code 1e20} 这类输入饱和成 {@code Integer.MAX_VALUE}，
	 * 后续 {@code getBlockState} 会去加载极远处的区块。</p>
	 */
	public static int clampCoord(double value) {
		if (Double.isNaN(value)) {
			return 0;
		}
		double clamped = Math.max(-30_000_000d, Math.min(30_000_000d, value));
		return (int) Math.floor(clamped);
	}

	private static MinecraftClient client(ScriptContext ctx) {
		return (MinecraftClient) ctx.handle();
	}

	/**
	 * 同一 50ms 内只允许一条聊天/命令。
	 *
	 * <p>循环节流关闭时一个 tick 能跑很多轮，若脚本里写「在聊天栏说」，
	 * 几百条消息会在同一帧涌出，服务端判定为刷屏直接断开连接。</p>
	 */
	private static boolean chatThrottled() {
		long slot = System.currentTimeMillis() / 50L;
		if (slot == lastChatSlot) {
			return true;
		}
		lastChatSlot = slot;
		return false;
	}

	/**
	 * 在聊天栏发送一条消息。
	 *
	 * <p>以 {@code /} 开头的文本会被服务端当作命令，但走普通聊天通道发不出去，
	 * 所以这里显式拦下并提示改用「执行命令」积木——静默失败比报错更难查。</p>
	 */
	private static void chat(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.getNetworkHandler() == null) {
			return;
		}
		String text = Values.asString(args[0]);
		if (text.isBlank()) {
			return;
		}
		if (text.startsWith("/")) {
			warnKey("chat-slash", "「在聊天栏说」收到以 / 开头的文本，已忽略；执行命令请用「执行命令」积木");
			return;
		}
		if (chatThrottled()) {
			return;
		}
		client.getNetworkHandler().sendChatMessage(text);
	}

	/**
	 * 执行一条命令，自动补前导斜杠。
	 */
	private static void command(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client.getNetworkHandler() == null) {
			return;
		}
		String text = Values.asString(args[0]).trim();
		if (text.isEmpty()) {
			return;
		}
		String body = text.startsWith("/") ? text.substring(1) : text;
		if (body.isBlank() || chatThrottled()) {
			return;
		}
		client.getNetworkHandler().sendChatCommand(body);
	}

	/**
	 * 按住 / 松开任意键盘键或鼠标键。
	 *
	 * <p>键名写法见 {@link KeyNames}：{@code w}、{@code left shift}、{@code mouse.left} 都行。</p>
	 */
	private static void key(ScriptContext ctx, Object[] args) {
		applyKey(ctx, Values.asString(args[0]), Values.asBool(args[1]));
	}

	/**
	 * 鼠标按键的按下 / 松开，参数是 {@code left} / {@code right} / {@code middle}。
	 */
	private static void mouse(ScriptContext ctx, Object[] args) {
		applyKey(ctx, "mouse." + Values.asString(args[0]).toLowerCase(Locale.ROOT),
				Values.asBool(args[1]));
	}

	/**
	 * 点按键盘键：按下后按住若干 tick 自动松开。
	 */
	private static void tap(ScriptContext ctx, Object[] args) {
		int hold = (int) Math.round(Values.asNumber(args[1]));
		applyTap(ctx, Values.asString(args[0]), hold);
		// 按住期间脚本必须挂起，否则后面的积木会在松手之前就执行完
		if (hold > 0) {
			ctx.requestWait(hold);
		}
	}

	/**
	 * 点击鼠标键：按下 1 tick 后自动松开。
	 */
	private static void click(ScriptContext ctx, Object[] args) {
		applyTap(ctx, "mouse." + Values.asString(args[0]).toLowerCase(Locale.ROOT), 1);
	}

	/**
	 * 按键的公共实现：键盘与鼠标共用，区别只在键名怎么拼出来。
	 */
	private static void applyKey(ScriptContext ctx, String raw, boolean pressed) {
		MinecraftClient client = client(ctx);
		String translationKey = KeyNames.toTranslationKey(raw);
		InputUtil.Key physical = InputUtil.fromTranslationKey(translationKey);
		if (physical == InputUtil.UNKNOWN_KEY) {
			warnKey(raw, "不是有效的按键名");
			return;
		}

		int matched = 0;
		int blocked = 0;
		for (KeyBinding binding : client.options.allKeys) {
			if (!translationKey.equals(binding.getBoundKeyTranslationKey())) {
				continue;
			}
			if (isBlocked(binding, translationKey)) {
				blocked++;
				continue;
			}
			matched++;
			if (pressed) {
				// 只在「松开 → 按下」的边沿伪造一次按下事件，否则循环里每 tick
				// 调用都会让「开背包」「切视角」这类动作重复触发
				boolean alreadyHeld = ScriptKeys.isForced(binding);
				ScriptKeys.hold(ctx.currentInstance(), binding, -1);
				if (!alreadyHeld) {
					binding.onKeyPressed(physical);
				}
			} else {
				ScriptKeys.release(ctx.currentInstance(), binding);
			}
		}
		if (matched == 0) {
			warnKey(raw, blocked > 0
					? "该键用于打开游戏界面，脚本不会按下它"
					: "没有任何游戏动作绑定在这个键上（可先在「选项 → 控制」里绑定）");
		}
	}

	/**
	 * 点按的公共实现：按住 {@code hold} tick 后由 {@link ScriptKeys#tick()} 松开。
	 */
	private static void applyTap(ScriptContext ctx, String raw, int hold) {
		MinecraftClient client = client(ctx);
		int ticks = Math.max(1, Math.min(200, hold));

		String translationKey = KeyNames.toTranslationKey(raw);
		InputUtil.Key physical = InputUtil.fromTranslationKey(translationKey);
		if (physical == InputUtil.UNKNOWN_KEY) {
			warnKey(raw, "不是有效的按键名");
			return;
		}

		int matched = 0;
		int blocked = 0;
		for (KeyBinding binding : client.options.allKeys) {
			if (!translationKey.equals(binding.getBoundKeyTranslationKey())) {
				continue;
			}
			if (isBlocked(binding, translationKey)) {
				blocked++;
				continue;
			}
			matched++;
			boolean alreadyHeld = ScriptKeys.isForced(binding);
			ScriptKeys.hold(ctx.currentInstance(), binding, ticks);
			if (!alreadyHeld) {
				binding.onKeyPressed(physical);
			}
		}
		if (matched == 0) {
			warnKey(raw, blocked > 0
					? "该键用于打开游戏界面，脚本不会按下它"
					: "没有任何游戏动作绑定在这个键上（可先在「选项 → 控制」里绑定）");
		}
	}

	/**
	 * 请求重生。
	 *
	 * <p>只发一次客户端状态包，是否真的活过来由服务端决定。用时间戳节流：
	 * 脚本写在循环里时每 tick 调用会连发几十个包，服务端会当成异常客户端。</p>
	 */
	private static void respawn(ScriptContext ctx, Object[] args) {
		requestRespawn(client(ctx));
	}

	/**
	 * 发送重生请求（供动作与「自动重生」开关共用）。
	 */
	public static void requestRespawn(MinecraftClient client) {
		if (client == null || client.getNetworkHandler() == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastRespawnAt < 1000L) {
			return;
		}
		lastRespawnAt = now;
		client.getNetworkHandler().sendPacket(
				new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
	}


	/**
	 * 释放所有被脚本按下的键，避免停止脚本后角色继续移动。
	 *
	 * <p>只松开脚本接管过的键，不会打断玩家自己正在按的方向键。</p>
	 */
	public static void releaseMovementKeys() {
		ScriptKeys.releaseEveryKey();
	}
}
