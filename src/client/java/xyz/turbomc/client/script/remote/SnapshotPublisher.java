package xyz.turbomc.client.script.remote;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import xyz.turbomc.client.script.action.InputKeys;
import xyz.turbomc.client.script.action.Snapshots;
import xyz.turbomc.client.script.action.WorldQueries;
import xyz.turbomc.client.script.ai.AiService;
import xyz.turbomc.client.script.runtime.ChatState;
import xyz.turbomc.client.script.runtime.TriggerDispatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * 每 tick 采集一次游戏状态，序列化成一个 JSON 对象推给浏览器。
 *
 * <p>为什么必须走快照而不是按需 RPC：Scratch 的 reporter 是同步求值的，
 * 每次读都往返一次的话，一个每秒循环 10 次、每次读 5 个值的脚本需要 1.2–2.5 秒/秒，跑不动。
 * 快照把「读」变成零延迟的本地缓存（见 ARCHITECTURE.md §7.4）。</p>
 *
 * <p><b>{@code tick} 不只是标识，它是浏览器侧的时钟</b>：{@code wait} 按它推进、
 * {@code poll} 由它触发、陈旧检测也读它（§7.3）。</p>
 *
 * <p><b>字段克制</b>：只放「快照能回答、且 reporter 需要」的。不做订阅化；能从现有字段推出的
 * 派生值（例如「前方有方块」直接读 {@code aim.has}）不再单列一份——每包大小是要付 20 次/秒的。</p>
 */
public final class SnapshotPublisher {
	/** 快照序号：只增不减，浏览器侧用它判断陈旧与计时。 */
	private long tick;

	/**
	 * 采集一帧。
	 *
	 * @param client 客户端实例，调用方保证在主线程
	 * @return 快照对象；调用方负责序列化与发送
	 */
	public JsonObject capture(MinecraftClient client) {
		JsonObject snap = new JsonObject();
		snap.addProperty("t", RemoteProtocol.T_SNAP);
		// 快照 schema 版本：扩展侧用它判断「客户端是不是旧的」。
		// 加字段必须 +1——否则旧客户端缺字段时，表现是「积木恒为假/为空」，从日志里看不出来。
		snap.addProperty("schema", RemoteProtocol.SNAPSHOT_SCHEMA);
		snap.addProperty("tick", ++tick);

		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			snap.addProperty("inWorld", false);
			snap.addProperty("paused", false);
			return snap;
		}

		snap.addProperty("inWorld", true);
		// 「暂停」只发生在单机打开暂停菜单时；此时值不变，浏览器侧据此判断脚本该不该推进
		snap.addProperty("paused", client.isPaused());

		// 1.21.11 起 Entity 不再暴露 getPos()，坐标走分量 getter
		snap.addProperty("x", round(player.getX()));
		snap.addProperty("y", round(player.getY()));
		snap.addProperty("z", round(player.getZ()));
		snap.addProperty("yaw", round(player.getYaw()));
		snap.addProperty("pitch", round(player.getPitch()));

		Vec3d velocity = player.getVelocity();
		snap.addProperty("vx", round(velocity.x));
		snap.addProperty("vy", round(velocity.y));
		snap.addProperty("vz", round(velocity.z));

		snap.addProperty("hp", round(player.getHealth()));
		snap.addProperty("maxHp", round(player.getMaxHealth()));
		snap.addProperty("food", player.getHungerManager().getFoodLevel());
		snap.addProperty("air", player.getAir());

		snap.addProperty("xp", round(player.experienceProgress));
		snap.addProperty("xpLevel", player.experienceLevel);

		snap.addProperty("dim", client.world.getRegistryKey().getValue().toString());
		snap.addProperty("dayTime", client.world.getTimeOfDay());
		snap.addProperty("raining", client.world.isRaining());
		snap.addProperty("thundering", client.world.isThundering());

		// 与「当打开界面」帽子共用同一套三档归类，避免两边给出不同的 screen 值
		snap.addProperty("screen", client.currentScreen == null
				? "none"
				: TriggerDispatcher.screenKind(client.currentScreen));

		snap.addProperty("slot", player.getInventory().getSelectedSlot());
		snap.add("hand", hand(player.getMainHandStack()));
		snap.add("aim", aim(client));

		// ---- 状态类读数 ----
		// 都是「此刻的物理状态」，只能每 tick 采；字段名与扩展侧 READERS 一一对应。
		// 注意：能从这里推出的东西不要再单列（例如 frontBlock 直接用 aim.block）
		snap.addProperty("onGround", player.isOnGround());
		snap.addProperty("isDay", client.world.isDay());
		snap.addProperty("inWater", player.isTouchingWater());
		snap.addProperty("inLava", player.isInLava());
		snap.addProperty("biome", client.world.getBiome(player.getBlockPos())
				.getKey().map(key -> key.getValue().toString()).orElse(""));
		snap.addProperty("below", Registries.BLOCK.getId(
				client.world.getBlockState(player.getBlockPos().down()).getBlock()).toString());
		snap.add("mining", mining(client));
		snap.add("mouse", mouse(client));
		snap.addProperty("ping", ping(client));

		// 背包与打开的界面：脚本高频读「有几个 X」「第一格在哪」「这格是什么」。
		// 只列非空槽位——41 格里通常只有十来件东西，空槽既费体积又不好遍历。
		// {@code ui} 为 null 表示没有可装槽位的界面（含「只是打开了背包」之外的一切情况）
		snap.add("inv", WorldQueries.inventoryArray(client));
		JsonObject ui = WorldQueries.screenSnapshot(client);
		snap.add("ui", ui == null ? JsonNull.INSTANCE : ui);

		// 三个「整体快照」积木的数据源。与上面那些扁平字段有重叠，但重叠的是设计：
		// 扁平字段给高频读数用（省一次解析），这三个对象给「整包喂给 JSON 积木」用
		snap.add("me", WorldQueries.playerSnapshot(client));
		snap.add("worldInfo", WorldQueries.worldSnapshot(client));
		snap.add("players", WorldQueries.playersSnapshot(client));

		// 聊天与 AI：reporter 同步求值，只能读快照。
		// history 是环形缓冲的拷贝（最多 ChatState.CAPACITY 条），last 就是最新一条
		JsonObject chat = new JsonObject();
		chat.addProperty("last", ChatState.last());
		JsonArray history = new JsonArray();
		for (String message : ChatState.history(ChatState.CAPACITY)) {
			history.add(message);
		}
		chat.add("history", history);
		snap.add("chat", chat);

		// AI 会话回复：session → 最后一条回复。远程模式下 AI 在游戏侧跑，
		// 浏览器拿不到结果，只能靠这份表同步查
		JsonObject ai = new JsonObject();
		AiService.get().replies().forEach(ai::addProperty);
		snap.add("ai", ai);

		addEntities(client, snap);
		return snap;
	}

	/** 快照里最多带多少个附近实体：再多就是刷怪塔场景，每 tick 十几 KB 不值当。 */
	private static final int ENTITY_LIMIT = 64;
	/** 快照的实体搜索半径（盒范围，与游戏内「附近实体」同一取法）。 */
	private static final double ENTITY_RADIUS = 32;

	/**
	 * 附近实体：按距离升序，最多 {@value #ENTITY_LIMIT} 条。
	 *
	 * <p><b>「是不是主动怪」单独一张 uuid 表</b>，不塞进实体对象：多一个键就会破坏
	 * 「实体快照」与游戏内的键序一致，宁可多一个并行的数组。</p>
	 */
	private static void addEntities(MinecraftClient client, JsonObject snap) {
		JsonArray ents = new JsonArray();
		JsonArray hostile = new JsonArray();
		ClientPlayerEntity player = client.player;
		List<Entity> found = client.world.getOtherEntities(player,
				player.getBoundingBox().expand(ENTITY_RADIUS));
		List<Snapshots.Entity> entries = new ArrayList<>(found.size());
		java.util.Set<String> hostileIds = new java.util.HashSet<>();
		for (Entity entity : found) {
			// 死亡但尚未移除的实体会让结果抖动，游戏内的两个统计都只认存活
			if (!entity.isAlive()) {
				continue;
			}
			entries.add(WorldQueries.entityEntry(player, entity));
			if (entity instanceof net.minecraft.entity.mob.Monster) {
				hostileIds.add(entity.getUuidAsString());
			}
		}
		entries.sort(java.util.Comparator.comparingDouble(Snapshots.Entity::distance));
		int limit = Math.min(entries.size(), ENTITY_LIMIT);
		for (int i = 0; i < limit; i++) {
			Snapshots.Entity entry = entries.get(i);
			ents.add(Snapshots.entityObject(entry));
			if (hostileIds.contains(entry.uuid())) {
				hostile.add(entry.uuid());
			}
		}
		snap.add("ents", ents);
		snap.add("hostile", hostile);
	}

	/** 挖掘状态：{@code progress} 为 -1 表示没在挖（与「挖了 0%」不是一回事）。 */
	private static JsonObject mining(MinecraftClient client) {
		JsonObject mining = new JsonObject();
		boolean breaking = client.interactionManager != null && client.interactionManager.isBreakingBlock();
		mining.addProperty("active", breaking);
		// 底层只有 0–9 十档，与原版渲染裂纹同一精度
		mining.addProperty("progress", breaking
				? Math.min(100L, Math.max(0L, client.interactionManager.getBlockBreakingProgress() * 100L / 9L))
				: -1);
		return mining;
	}

	/**
	 * 三个鼠标键的物理状态。
	 *
	 * <p>只采菜单里存在的三档（左/右/中）；「任意键」由扩展侧三选一合并，
	 * 不在这里多存一份。</p>
	 */
	private static JsonObject mouse(MinecraftClient client) {
		JsonObject mouse = new JsonObject();
		mouse.addProperty("left", InputKeys.isMouseDown(client, "left"));
		mouse.addProperty("right", InputKeys.isMouseDown(client, "right"));
		mouse.addProperty("middle", InputKeys.isMouseDown(client, "middle"));
		return mouse;
	}

	/** 与服务端之间的延迟（毫秒）。单人游戏为 0。 */
	private static double ping(MinecraftClient client) {
		if (client.player == null || client.getNetworkHandler() == null) {
			return 0;
		}
		var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
		return entry == null ? 0 : entry.getLatency();
	}

	/** 手持物品：{@code slot} 只有槽位号，「手持物品是 X 吗」需要 id 与数量。 */
	private static JsonObject hand(ItemStack stack) {
		JsonObject hand = new JsonObject();
		if (stack == null || stack.isEmpty()) {
			hand.addProperty("id", "");
			hand.addProperty("count", 0);
			hand.addProperty("maxDurability", 0);
			hand.addProperty("durability", 0);
			return hand;
		}
		hand.addProperty("id", Registries.ITEM.getId(stack.getItem()).toString());
		hand.addProperty("count", stack.getCount());
		hand.addProperty("maxDurability", stack.getMaxDamage());
		hand.addProperty("durability", durabilityPercent(stack));
		return hand;
	}

	/** 剩余耐久百分比（0–100，一位小数）；不可损坏物品给 100，空手给 0。 */
	private static double durabilityPercent(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		if (!stack.isDamageable()) {
			return 100;
		}
		int max = stack.getMaxDamage();
		if (max <= 0) {
			return 100;
		}
		int remaining = max - stack.getDamage();
		return Math.round(remaining * 1000d / max) / 10d;
	}

	/** 准星指向：方块给坐标与 id，实体给 uuid 与类型。 */
	private static JsonObject aim(MinecraftClient client) {
		JsonObject aim = new JsonObject();
		HitResult target = client.crosshairTarget;
		if (target instanceof BlockHitResult blockHit && target.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			BlockState state = client.world.getBlockState(pos);
			aim.addProperty("x", pos.getX());
			aim.addProperty("y", pos.getY());
			aim.addProperty("z", pos.getZ());
			aim.addProperty("block", Registries.BLOCK.getId(state.getBlock()).toString());
			// 「前方有方块吗」判的是这个位置是不是空气，不是「有没有 block 字段」
			aim.addProperty("has", !state.isAir());
			aim.addProperty("entity", "");
			return aim;
		}
		if (target instanceof EntityHitResult entityHit && target.getType() == HitResult.Type.ENTITY) {
			Entity entity = entityHit.getEntity();
			aim.addProperty("x", round(entity.getX()));
			aim.addProperty("y", round(entity.getY()));
			aim.addProperty("z", round(entity.getZ()));
			aim.addProperty("block", "");
			aim.addProperty("has", false);
			aim.addProperty("entity", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
			aim.addProperty("entityUuid", entity.getUuidAsString());
			return aim;
		}
		aim.addProperty("x", 0);
		aim.addProperty("y", 0);
		aim.addProperty("z", 0);
		aim.addProperty("block", "");
		aim.addProperty("has", false);
		aim.addProperty("entity", "");
		return aim;
	}

	/**
	 * 保留三位小数。
	 *
	 * <p>Scratch 的坐标积木本来就按像素/整数用，多出来的精度只会让每包变大；
	 * 三位小数已足够区分 1/1000 格，远小于一个方块。</p>
	 */
	private static double round(double value) {
		return Math.round(value * 1000d) / 1000d;
	}
}
