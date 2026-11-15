package xyz.turbomc.client.script.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.config.TurboMCConfig;
import xyz.turbomc.client.script.action.path.PathFollower;

/**
 * 把「走向」的路线画到世界里：折线 + 当前节点框 + 终点框。
 *
 * <p>参考项目是逐节点画 1×1×1 的框（{@code RegionRenderer.renderPath}）。能看清节点，
 * 但 30 个节点就是 30 个方框，密的路径会糊成一片；这里改成折线为主、框为辅。</p>
 *
 * <p>折线按「是否走过」分色：已走过的段是金黄，未走的是青色；当前节点白框、终点黄框。
 * 走过与否读的是 {@link PathFollower#viewIndex()}，即脚本线程每 tick 推进入快照的下标，
 * 渲染线程只读，不反推。</p>
 *
 * <p>线层带深度测试，被墙挡住的段落看不见——这是有意的，能直接暴露"路径穿墙"。</p>
 *
 * <p>渲染线程只读 {@link PathFollower#nodeView()} 的不可变快照，不遍历可变的 Path，
 * 也不在这里分配集合。</p>
 */
public final class PathRenderer {
	private static final int LINE_COLOR = 0xFF55FFFF;
	/**
	 * 已经走过的段：金黄。
	 *
	 * <p>用颜色而不是删掉线段：玩家需要一眼分清「这段已经走过、不用回头」和「前面还要走」。
	 * 与终点框的纯黄（{@link #END_COLOR}）刻意不同——一个偏琥珀，一个是亮黄，同屏不会混。</p>
	 */
	private static final int WALKED_COLOR = 0xFFFFC24D;
	private static final int CURRENT_COLOR = 0xFFFFFFFF;
	private static final int END_COLOR = 0xFFFFFF00;
	private static final double RENDER_DISTANCE = 128.0;
	private static final float LINE_WIDTH = 2.0f;

	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");
	/**
	 * 渲染出错后的冷却时间。
	 *
	 * <p>渲染事件没有兜底，一帧抛一次会把日志刷爆；但「出错一次就永久停画」更糟——
	 * 偶发的异常（区块切换、矩阵栈状态）会让整局都看不到路线，且没有任何提示。
	 * 折中：失败后冷却重试，连续失败只记前几次日志。</p>
	 */
	private static final long RETRY_COOLDOWN_NANOS = 5_000_000_000L;
	private static final int MAX_LOGGED_FAILURES = 3;
	private static long retryAfterNanos;
	private static int consecutiveFailures;

	private PathRenderer() {
	}

	public static void render(WorldRenderContext context) {
		long now = System.nanoTime();
		if (now < retryAfterNanos) {
			return;
		}
		try {
			renderSafely(context);
			consecutiveFailures = 0;
		} catch (RuntimeException e) {
			consecutiveFailures++;
			retryAfterNanos = now + RETRY_COOLDOWN_NANOS;
			if (consecutiveFailures <= MAX_LOGGED_FAILURES) {
				LOG.error("[path] 路线渲染失败（连续第 {} 次），{} 秒后重试: {}",
						consecutiveFailures, RETRY_COOLDOWN_NANOS / 1_000_000_000L, e.toString());
			}
		}
	}

	private static void renderSafely(WorldRenderContext context) {
		if (!TurboMCConfig.get().showPath) {
			return;
		}
		PathFollower.NodeView view = PathFollower.nodeView();
		if (view == null || context.consumers() == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.world != view.world()) {
			return;
		}
		Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
		if (camera.squaredDistanceTo(view.center()) > RENDER_DISTANCE * RENDER_DISTANCE) {
			return;
		}

		MatrixStack matrices = context.matrices();
		matrices.push();
		try {
			matrices.translate(-camera.x, -camera.y, -camera.z);
			// 只取一次索引：渲染期间 tick 可能推进它，两次读会画出「线还没变色、框已经跳了」的错帧
			int current = PathFollower.viewIndex();
			drawPolyline(context, matrices, view, current);
			drawNodeBox(context, matrices, view, current, CURRENT_COLOR);
			drawNodeBox(context, matrices, view, view.count() - 1, END_COLOR);
		} finally {
			// 抛异常也必须把栈弹回来，否则矩阵栈会一帧一帧地涨
			matrices.pop();
		}
	}

	/**
	 * 画折线，按「是否走过」分段上色。
	 *
	 * @param walkedUpTo 已走过的最后一个节点下标（含）；小于等于它的段用
	 *                   {@link #WALKED_COLOR}，其余用 {@link #LINE_COLOR}
	 */
	private static void drawPolyline(WorldRenderContext context, MatrixStack matrices,
			PathFollower.NodeView view, int walkedUpTo) {
		float[] points = view.points();
		if (points.length < 6) {
			return;
		}
		VertexConsumer vertices = context.consumers().getBuffer(RenderLayers.lines());
		MatrixStack.Entry entry = matrices.peek();
		for (int i = 3; i + 2 < points.length; i += 3) {
			// 段 (i-3 → i) 的终点节点下标就是 i / 3
			int color = (i / 3) <= walkedUpTo ? WALKED_COLOR : LINE_COLOR;
			// RenderLayers.lines() 的顶点格式是 POSITION_COLOR_NORMAL_LINE_WIDTH：
			// 位置、颜色、法线、线宽四个元素缺一不可。少写 lineWidth 时 BufferBuilder
			// 会在「写下一个顶点」时校验上一个顶点并抛 Missing elements in vertex，
			// 而那条半成品顶点会留在共用的缓冲里，把原版画准星线框的代码一起带崩。
			vertices.vertex(entry, points[i - 3], points[i - 2], points[i - 1])
					.color(color).normal(entry, 0f, 1f, 0f).lineWidth(LINE_WIDTH);
			vertices.vertex(entry, points[i], points[i + 1], points[i + 2])
					.color(color).normal(entry, 0f, 1f, 0f).lineWidth(LINE_WIDTH);
		}
	}

	private static void drawNodeBox(WorldRenderContext context, MatrixStack matrices,
			PathFollower.NodeView view, int index, int color) {
		float[] points = view.points();
		int base = index * 3;
		if (index < 0 || base + 2 >= points.length) {
			return;
		}
		// points 存的是格中心与脚部高度，框要覆盖整格
		Box box = new Box(
				points[base] - 0.5, points[base + 1], points[base + 2] - 0.5,
				points[base] + 0.5, points[base + 1] + 1.0, points[base + 2] + 0.5);
		VertexConsumer vertices = context.consumers().getBuffer(RenderLayers.lines());
		VertexRendering.drawOutline(matrices, vertices, VoxelShapes.cuboid(box), 0, 0, 0, color, LINE_WIDTH);
	}
}
