package xyz.turbomc.client.script.action;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体过滤器：把积木上的「实体类型」文本参数解析成可复用判定。
 *
 * <p>取值四类：{@code any}（不限）/ {@code hostile}（{@code instanceof Monster}）/
 * {@code player} / 具体实体 id（{@code minecraft:zombie}，或不带命名空间的 {@code zombie}）。
 * 解析与判定拆开是为了可测：{@link #parse(String)} 不碰任何 Minecraft 类型，
 * 纯 JVM 单测就能覆盖大小写、空格、空值这些边界。</p>
 *
 * <p><b>非法 id 不再静默</b>：拼错的名字以前会让查询永远返回「无目标」，
 * 界面上没有任何线索。现在每个非法名只警告一次。</p>
 */
final class EntityFilter {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");
	/** 已警告过的实体名，避免每 tick 刷屏。 */
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	/** 过滤器种类。 */
	enum Kind {
		ANY, HOSTILE, PLAYER, TYPE
	}

	/**
	 * 解析结果。{@code id} 仅 {@link Kind#TYPE} 时有意义，且已小写去空白。
	 */
	record Parsed(Kind kind, String id) {
		/** 缓存键后缀：同键复用同一次遍历结果。 */
		String cacheKey() {
			return kind == Kind.TYPE ? "type:" + id : kind.name().toLowerCase(Locale.ROOT);
		}
	}

	private static final Parsed ANY = new Parsed(Kind.ANY, "");

	private EntityFilter() {
	}

	/**
	 * 解析积木参数。
	 *
	 * <p>只有显式 {@code any} 才表示不限类型。空值按「匹配不到」处理——
	 * 与 {@code InputKeys} 的取舍一致：参数没送到时宁可判否，也不要安静地匹配全部。</p>
	 */
	static Parsed parse(String raw) {
		String text = raw == null ? "" : raw.trim();
		if ("any".equalsIgnoreCase(text)) {
			return ANY;
		}
		if ("hostile".equalsIgnoreCase(text)) {
			return new Parsed(Kind.HOSTILE, "");
		}
		if ("player".equalsIgnoreCase(text)) {
			return new Parsed(Kind.PLAYER, "");
		}
		return new Parsed(Kind.TYPE, text.toLowerCase(Locale.ROOT));
	}

	/** 判定单个实体是否命中。 */
	static boolean matches(Entity entity, Parsed filter) {
		return switch (filter.kind()) {
			case ANY -> true;
			case HOSTILE -> entity instanceof Monster;
			case PLAYER -> entity instanceof PlayerEntity;
			case TYPE -> matchesType(entity, filter.id());
		};
	}

	private static boolean matchesType(Entity entity, String name) {
		Identifier id = Identifier.tryParse(name);
		if (id == null) {
			warnOnce(name, "无法解析的实体类型");
			return false;
		}
		// id 语法正确但注册表里没有（拼错、来自别的版本、模组已卸载）：
		// 同样要留痕，否则表现为「积木永远无目标」，没有异常也没有日志
		if (Registries.ENTITY_TYPE.get(id) == null) {
			warnOnce(name, "注册表里没有这个实体类型");
			return false;
		}
		return Registries.ENTITY_TYPE.getId(entity.getType()).equals(id);
	}

	private static void warnOnce(String name, String reason) {
		if (WARNED.add(name)) {
			LOG.warn("[script] {} \"{}\"；可用 any / hostile / player / 实体 id（如 minecraft:zombie）",
					reason, name);
		}
	}
}
