package xyz.hegke.client.config;

import java.util.Locale;

/**
 * 按键名的规范化与换算。
 *
 * <p>配置里让人手写的键名、脚本里写的键名、Minecraft 内部的翻译键，三者形式不同：</p>
 *
 * <pre>
 *   用户写   t / T / left shift / left.shift / esc / mouse.left
 *   翻译键   key.keyboard.t / key.keyboard.left.shift / key.keyboard.escape
 *   动作 id  key.chat（这类不是物理键，按名字原样比较）
 * </pre>
 *
 * <p>所有比较都先过 {@link #canonical(String)}，避免「Left Shift」和「left.shift」被当成两个键。</p>
 */
public final class KeyNames {
	private static final String KEYBOARD_PREFIX = "key.keyboard.";
	private static final String MOUSE_PREFIX = "key.mouse.";
	private static final String KEY_PREFIX = "key.";

	private KeyNames() {
	}

	/**
	 * 物理键名 → 翻译键，如 {@code t} → {@code key.keyboard.t}。
	 *
	 * <p>同时接受几个常用别名，省得用户去查 {@code left.shift} 这种全名。</p>
	 */
	public static String toTranslationKey(String raw) {
		String id = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '.');
		if (id.isEmpty()) {
			return "";
		}
		if (id.startsWith(KEY_PREFIX)) {
			return id;
		}
		// 鼠标键：mouse.left → key.mouse.left
		if (id.startsWith("mouse.")) {
			return MOUSE_PREFIX + id.substring("mouse.".length());
		}
		return switch (id) {
			case "shift" -> "key.keyboard.left.shift";
			case "ctrl", "control" -> "key.keyboard.left.control";
			case "alt" -> "key.keyboard.left.alt";
			case "esc" -> "key.keyboard.escape";
			case "lmb", "leftclick" -> MOUSE_PREFIX + "left";
			case "rmb", "rightclick" -> MOUSE_PREFIX + "right";
			case "mmb", "middleclick" -> MOUSE_PREFIX + "middle";
			default -> KEYBOARD_PREFIX + id;
		};
	}

	/**
	 * 归一化后的名字，用于配置比较。
	 *
	 * <p>物理键统一成 {@code left.shift} / {@code t} / {@code f5} 这种形式；
	 * 动作 id（以 {@code key.} 开头且不是 {@code key.keyboard.}）原样返回。</p>
	 */
	public static String canonical(String raw) {
		String trimmed = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		if (trimmed.isEmpty()) {
			return "";
		}
		if (trimmed.startsWith(KEY_PREFIX) && !trimmed.startsWith(KEYBOARD_PREFIX)
				&& !trimmed.startsWith(MOUSE_PREFIX)) {
			return trimmed;
		}
		String translationKey = toTranslationKey(trimmed);
		if (translationKey.startsWith(KEYBOARD_PREFIX)) {
			return translationKey.substring(KEYBOARD_PREFIX.length());
		}
		if (translationKey.startsWith(MOUSE_PREFIX)) {
			// 鼠标键统一成 mouse.left 形式，和用户书写习惯一致
			return translationKey.substring(KEY_PREFIX.length());
		}
		return translationKey;
	}

}
