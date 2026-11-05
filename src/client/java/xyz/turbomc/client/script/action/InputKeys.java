package xyz.turbomc.client.script.action;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Scratch 按键名 ↔ Minecraft 按键的翻译与按下检测。
 *
 * <p>Scratch 用 {@code "space"} / {@code "a"} / {@code "up arrow"} 这类名字，
 * 其中 {@code "any"} 表示"任意键"。触发分发与「按下 [键]?」积木共用这一套解析，
 * 避免两处各写一份、慢慢跑偏。</p>
 */
public final class InputKeys {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");
	/** 已警告过的键名，避免每 tick 刷屏。 */
	private static final Set<String> WARNED = new HashSet<>();
	/** 「任意键」的短时缓存：同一次 tick 内的多次查询不必重复扫 300 多个键。 */
	private static final long ANY_CACHE_NANOS = 8_000_000L;
	private static long anyCachedAt;
	private static boolean anyCached;

	private InputKeys() {
	}

	/**
	 * 解析成 Minecraft 按键；不支持的名字返回 {@link InputUtil#UNKNOWN_KEY}。
	 */
	public static InputUtil.Key resolve(String scratchName) {
		if (scratchName == null) {
			return InputUtil.UNKNOWN_KEY;
		}
		String key = scratchName.trim().toLowerCase();
		if (key.isEmpty() || "any".equals(key)) {
			return InputUtil.UNKNOWN_KEY;
		}
		String translationKey = switch (key) {
			case "space" -> "key.keyboard.space";
			case "enter", "return" -> "key.keyboard.enter";
			case "shift" -> "key.keyboard.left.shift";
			case "up arrow", "up" -> "key.keyboard.up";
			case "down arrow", "down" -> "key.keyboard.down";
			case "left arrow", "left" -> "key.keyboard.left";
			case "right arrow", "right" -> "key.keyboard.right";
			default -> key.length() == 1 ? "key.keyboard." + key : null;
		};
		return translationKey == null ? InputUtil.UNKNOWN_KEY : InputUtil.fromTranslationKey(translationKey);
	}

	public static boolean isDown(MinecraftClient client, String scratchName) {
		InputUtil.Key key = resolve(scratchName);
		if (key == InputUtil.UNKNOWN_KEY) {
			if (scratchName != null && !"any".equalsIgnoreCase(scratchName.trim())
					&& WARNED.add(scratchName)) {
				LOG.warn("[script] 无法识别的按键名 \"{}\"，「按下 [键]?」将恒为假", scratchName);
			}
			return false;
		}
		return InputUtil.isKeyPressed(client.getWindow(), key.getCode());
	}

	/**
	 * 「任意键」：遍历可打印键与常见功能键。ESC 与 F6/F7 排除，
	 * 避免脚本自己把引擎关掉。
	 */
	public static boolean anyDown(MinecraftClient client) {
		long now = System.nanoTime();
		if (now - anyCachedAt < ANY_CACHE_NANOS) {
			return anyCached;
		}
		anyCached = scanAnyKey(client);
		anyCachedAt = now;
		return anyCached;
	}

	private static boolean scanAnyKey(MinecraftClient client) {
		for (int code = GLFW.GLFW_KEY_SPACE; code <= GLFW.GLFW_KEY_LAST; code++) {
			if (code == GLFW.GLFW_KEY_ESCAPE || code == GLFW.GLFW_KEY_F6 || code == GLFW.GLFW_KEY_F7) {
				continue;
			}
			if (InputUtil.isKeyPressed(client.getWindow(), code)) {
				return true;
			}
		}
		return false;
	}
}
