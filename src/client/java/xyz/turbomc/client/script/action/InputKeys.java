package xyz.turbomc.client.script.action;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
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
		String key = scratchName.trim().toLowerCase(Locale.ROOT);
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

	// ---- 鼠标：与键盘是两条读取通道，不能混用 ----

	/**
	 * 鼠标按钮名 → GLFW 按钮号；无法识别返回 -1。
	 *
	 * <p>接受 {@code left / right / middle}，也接受 {@code mouse.left} 这类写法——
	 * 与 {@code KeyNames} 的键名习惯保持一致，免得两个体系写法不同。</p>
	 */
	public static int mouseButton(String scratchName) {
		if (scratchName == null) {
			return -1;
		}
		String key = scratchName.trim().toLowerCase(Locale.ROOT);
		if (key.startsWith("mouse.")) {
			key = key.substring("mouse.".length());
		}
		return switch (key) {
			case "left", "lmb" -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
			case "right", "rmb" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
			case "middle", "mmb" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
			default -> -1;
		};
	}

	/**
	 * 任一鼠标键按下。
	 *
	 * <p>这是 Scratch「鼠标按下?」的原义：浏览器里任何按钮的 mousedown 都会让该积木为真，
	 * 它本身不区分按钮。</p>
	 */
	public static boolean anyMouseDown(MinecraftClient client) {
		return isMouseDown(client, GLFW.GLFW_MOUSE_BUTTON_LEFT)
				|| isMouseDown(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
				|| isMouseDown(client, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
	}

	/**
	 * 读取鼠标按钮的物理状态。
	 *
	 * <p>不能复用 {@link InputUtil#isKeyPressed}：它内部只调 {@code glfwGetKey}，
	 * 把 0/1/2 这类按钮码当键盘码查，结果恒为「未按下」。鼠标必须走
	 * {@code glfwGetMouseButton}。物理状态不受界面是否打开影响。</p>
	 */
	public static boolean isMouseDown(MinecraftClient client, int button) {
		if (client == null || client.getWindow() == null || button < 0) {
			return false;
		}
		return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
	}

	/**
	 * 名字入口：只有显式 {@code any} 才表示任一键。
	 *
	 * <p><b>参数为空时返回假，不回退到「任意键」</b>：空值意味着积木的按钮参数没送到
	 * （菜单值丢失、字段名漂移、老工程），此时若乐观放行成「任意键」，
	 * 「等待按下鼠标 [中键]?」会在玩家按住左键挖矿时立刻通过——看起来就是恒真。
	 * 名字无法识别与取值为空同属「参数没传对」，两者都应判假并留痕。</p>
	 */
	public static boolean isMouseDown(MinecraftClient client, String scratchName) {
		if (scratchName != null && "any".equalsIgnoreCase(scratchName.trim())) {
			return anyMouseDown(client);
		}
		if (scratchName == null || scratchName.isBlank()) {
			if (WARNED.add("mouse:<empty>")) {
				LOG.warn("[script] 鼠标积木没拿到按钮名（参数为空），判断恒为假；"
						+ "若本意是「任意键」请显式选 any");
			}
			return false;
		}
		int button = mouseButton(scratchName);
		if (button < 0) {
			if (WARNED.add("mouse:" + scratchName)) {
				LOG.warn("[script] 无法识别的鼠标键名 \"{}\"，鼠标判断将恒为假", scratchName);
			}
			return false;
		}
		return isMouseDown(client, button);
	}
}
