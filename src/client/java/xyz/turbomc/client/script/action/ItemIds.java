package xyz.turbomc.client.script.action;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品 id 的解析与校验。
 *
 * <p>积木上的物品参数在编辑器里是下拉菜单（扩展的 {@code itemId} 菜单，1.21.11 全部 1505 个物品），
 * 但用户仍可能手填：这里统一收口，允许省略命名空间（{@code iron_ore} = {@code minecraft:iron_ore}）、
 * 忽略大小写与首尾空格。</p>
 *
 * <p><b>两种失败都要留痕</b>：语法不认识、注册表里没有。改动前这两类错误都表现为
 * 「积木永远返回 0 / -1」，没有异常也没有日志，只能靠猜。</p>
 */
final class ItemIds {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private ItemIds() {
	}

	/**
	 * 规范化物品 id：去空白、小写、补 {@code minecraft:} 命名空间。
	 *
	 * <p>纯函数，不碰注册表，便于单测覆盖。</p>
	 *
	 * @return 规范 id；语法非法时返回 {@code null}
	 */
	static Identifier parse(String raw) {
		if (raw == null) {
			return null;
		}
		String text = raw.trim().toLowerCase(Locale.ROOT);
		if (text.isEmpty()) {
			return null;
		}
		try {
			Identifier id = Identifier.tryParse(text);
			// "minecraft:" 会被解析成路径为空的 id，这种写法永远匹配不到物品，按语法错误处理
			return id == null || id.getPath().isEmpty() ? null : id;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** 解析成物品；语法错或注册表没有时返回 {@code null}，并各自警告一次。 */
	static Item resolve(String raw) {
		Identifier id = parse(raw);
		if (id == null) {
			warnOnce(raw, "无法解析的物品 id");
			return null;
		}
		if (!Registries.ITEM.containsId(id)) {
			warnOnce(raw, "注册表里没有这个物品");
			return null;
		}
		return Registries.ITEM.get(id);
	}

	private static void warnOnce(String raw, String reason) {
		String key = String.valueOf(raw);
		if (WARNED.add(key)) {
			LOG.warn("[script] {} \"{}\"；可用菜单选，或填物品 id（如 minecraft:iron_ore 或 iron_ore）", reason, key);
		}
	}
}
