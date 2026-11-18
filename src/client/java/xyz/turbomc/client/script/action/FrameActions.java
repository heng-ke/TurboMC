package xyz.turbomc.client.script.action;

import xyz.turbomc.client.script.render.FrameBox;
import xyz.turbomc.client.script.render.FrameOverlay;
import xyz.turbomc.client.script.runtime.ScriptContext;
import xyz.turbomc.client.script.runtime.Values;

/**
 * 画框类动作：把脚本算出来的范围交给显示层。
 *
 * <p>这两个动作<b>不改世界状态</b>，因此没有放进 {@link GameActions}（那里是挖掘、放置、
 * 聊天这类真动作），也不产生等待——调用即返回，框由渲染层每帧重画。</p>
 *
 * <p>坐标与颜色都不做业务解释：脚本给什么就画什么。要"框选"语义、要读取选区尺寸，
 * 都由脚本自己用变量算——本层刻意不引入选区对象。</p>
 *
 * <p>内部类型叫 {@code region.*} 而不是 {@code display.*}：它描述的是"画一块区域"这个语义，
 * 与积木面板里的分组标题（显示与日志）没有耦合——面板顺序随时可调，类型名一旦进 sb3
 * 映射就不该再动。</p>
 */
public final class FrameActions {
	private FrameActions() {
	}

	public static void registerAll() {
		NativeRegistry.action("region.draw_frame", FrameActions::drawFrame);
		NativeRegistry.action("region.clear_frames", FrameActions::clearFrames);
	}

	/**
	 * 「从 x1,y1,z1 到 x2,y2,z2 画框 颜色 …」。
	 *
	 * <p>参数按积木文本里的占位符顺序进来：X1 Y1 Z1 X2 Y2 Z2 COLOR。少给参数就整条忽略——
	 * 用默认值补零会画出一个莫名其妙的框，比不画更难查。</p>
	 */
	private static void drawFrame(ScriptContext ctx, Object[] args) {
		if (args.length < 7) {
			return;
		}
		FrameOverlay.add(FrameBox.of(
				Values.asNumber(args[0]), Values.asNumber(args[1]), Values.asNumber(args[2]),
				Values.asNumber(args[3]), Values.asNumber(args[4]), Values.asNumber(args[5]),
				Values.asString(args[6])));
	}

	private static void clearFrames(ScriptContext ctx, Object[] args) {
		FrameOverlay.clear();
	}
}
