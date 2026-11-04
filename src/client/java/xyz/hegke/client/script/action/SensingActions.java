package xyz.hegke.client.script.action;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.hegke.client.script.runtime.ScratchCast;
import xyz.hegke.client.script.runtime.ScratchClock;
import xyz.hegke.client.script.runtime.ScriptContext;
import xyz.hegke.client.script.runtime.Values;

/**
 * 感知与输出积木：按键查询、计时器、当前时间、以及「说 / 思考」。
 *
 * <p>「说」在 Scratch 里是角色气泡，在 MC 里映射为动作栏消息 + 日志。
 * 这样脚本里的调试输出不再被静默丢弃。</p>
 */
public final class SensingActions {
	private static final Logger LOG = LoggerFactory.getLogger("hk-script/script");

	private SensingActions() {
	}

	public static void registerAll() {
		NativeRegistry.function("input.key_pressed", SensingActions::keyPressed);
		NativeRegistry.function("sensing.timer", (ctx, args) -> ScratchClock.timer());
		NativeRegistry.action("sensing.reset_timer", (ctx, args) -> ScratchClock.resetTimer());
		NativeRegistry.function("sensing.current", SensingActions::current);
		NativeRegistry.function("sensing.days_since_2000", (ctx, args) -> ScratchClock.daysSince2000());
		NativeRegistry.action("debug.say", SensingActions::say);
	}

	private static Object keyPressed(ScriptContext ctx, Object[] args) {
		MinecraftClient client = client(ctx);
		if (client == null) {
			return false;
		}
		String key = ScratchCast.toString(args[0]);
		if ("any".equalsIgnoreCase(key.trim())) {
			return InputKeys.anyDown(client);
		}
		return InputKeys.isDown(client, key);
	}

	private static Object current(ScriptContext ctx, Object[] args) {
		return ScratchClock.current(ScratchCast.toString(args[0]));
	}

	private static void say(ScriptContext ctx, Object[] args) {
		String text = Values.asString(args[0]);
		MinecraftClient client = client(ctx);
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal(text), true);
		}
		LOG.info("[say] {}", text);
	}

	private static MinecraftClient client(ScriptContext ctx) {
		return ctx.handle() instanceof MinecraftClient client ? client : null;
	}
}
