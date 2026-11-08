package xyz.turbomc.client.script.ai;

import xyz.turbomc.client.config.TurboMCConfig;
import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.runtime.ScriptContext;
import xyz.turbomc.client.script.runtime.Values;

/**
 * AI 对话积木的动作层。
 *
 * <p>四个动作对应扩展里的 {@code aiAsk / aiReply / aiReset / aiSystem}，桥按
 * {@code Sb3Mapping} 把 opcode 翻成这里的 id，所以这张表里不需要再写一遍 opcode。</p>
 *
 * <p><b>挂起而不是阻塞</b>：{@code ask} 提交请求后调 {@link ScriptContext#requestWaitUntil}，
 * 由 JS 侧包装成 Promise 让线程进入 {@code PROMISE_WAIT}；每帧 {@code ScriptBridge.poll}
 * 查一次结果。这一层没有 {@code join} / {@code get}，主线程不会被网络拖住。</p>
 *
 * <p>失败一律不抛异常，也不挂起：脚本继续往下走，{@code aiReply} 得到空串。
 * 抛异常会被桥吞成 null，脚本静默乱走，比返回空串更难查。</p>
 */
public final class AiActions {
	private AiActions() {
	}

	public static void registerAll() {
		// 只是存方法引用，不在这里读配置：纯 JVM 冒烟会调 bootstrapOnce，
		// 而 TurboMCConfig 依赖 FabricLoader，那时读不到
		AiService.get().settingsProvider(AiActions::settings);

		NativeRegistry.action("ai.ask", AiActions::ask);
		NativeRegistry.function("ai.reply", AiActions::reply);
		NativeRegistry.action("ai.reset", AiActions::reset);
		NativeRegistry.action("ai.system", AiActions::system);
	}

	private static AiService.Settings settings() {
		return TurboMCConfig.get().aiSettings();
	}

	/**
	 * 「让会话 [SESSION] 回应 [TEXT]」。
	 *
	 * <p>{@code SESSION} 是普通文本，脚本自己定名字；宿主不知道也不该知道有哪些会话。</p>
	 */
	private static void ask(ScriptContext ctx, Object[] args) {
		String session = arg(args, 0);
		String text = arg(args, 1);
		String id = AiService.get().ask(session, text);
		if (id == null) {
			return;
		}
		// 上限比 HTTP 超时大 40 tick：__tmcWaitUntil 到点会自行 resolve 且不通知 Java
		ctx.requestWaitUntil("ai:" + id, AiService.get().waitLimitTicks());
	}

	private static Object reply(ScriptContext ctx, Object[] args) {
		return AiService.get().reply(arg(args, 0));
	}

	private static void reset(ScriptContext ctx, Object[] args) {
		AiService.get().reset(arg(args, 0));
	}

	private static void system(ScriptContext ctx, Object[] args) {
		AiService.get().system(arg(args, 0), arg(args, 1));
	}

	private static String arg(Object[] args, int index) {
		return args != null && index < args.length ? Values.asString(args[index]) : "";
	}
}
