package xyz.turbomc.client.script.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 「画框」积木的显示层：保存最近若干次调用的范围，并在每帧把它们画成线框。
 *
 * <p>这一层的语义刻意做得很薄——它不认识"选区"，也不记得是谁画的。积木每调用一次就
 * 往队列里放一个 {@link FrameBox}，画不画、画几个由脚本自己决定。因此脚本里
 * 「重复遍历每个矿点 → 画框」能同屏留下 128 个框（{@link #MAX_FRAMES}），
 * 超出后挤掉最旧的那个。</p>
 *
 * <p><b>线程约定</b>：写入只发生在客户端主线程（积木经 VM 在主线程执行，引擎停止/断线
 * 收尾也在主线程）；渲染线程只读一次 {@link #snapshot()} 拿到的不可变数组，不遍历可变状态。
 * 数组本身用 copy-on-write 重建，{@code volatile} 保证发布可见——与 {@link PathRenderer}
 * 的 {@code NodeView} 同一套约定。</p>
 */
public final class FrameOverlay {
	/** 同时显示的框上限。128 个框 = 1536 条线段，绘制开销可忽略。 */
	public static final int MAX_FRAMES = 128;

	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");
	private static final FrameBox[] EMPTY = new FrameBox[0];
	private static final Object LOCK = new Object();

	/** 当前要画的框，最新在末尾。只整体替换，不原地改。 */
	private static volatile FrameBox[] frames = EMPTY;

	/**
	 * 超过这个距离的框不画。
	 *
	 * <p>判据是<b>框的最近点</b>到相机的距离，不是中心点：一个 200 格跨度的大框，
	 * 中心可能在 256 格外，但人明明站在框边上看得见。按中心点裁剪会让整框凭空消失。</p>
	 */
	private static final double RENDER_DISTANCE = 256.0;
	private static final double RENDER_DISTANCE_SQ = RENDER_DISTANCE * RENDER_DISTANCE;
	private static final float LINE_WIDTH = 2.0f;

	/** 渲染失败后的冷却，理由同 {@link PathRenderer}：一帧抛一次会把日志刷爆。 */
	private static final long RETRY_COOLDOWN_NANOS = 5_000_000_000L;
	private static final int MAX_LOGGED_FAILURES = 3;
	private static long retryAfterNanos;
	private static int consecutiveFailures;

	private FrameOverlay() {
	}

	/**
	 * 加一个框。
	 *
	 * <p>与末尾完全相同的框不再入队：脚本常把「画框」放在 {@code 一直重复} 里，
	 * 每 tick 重画同一个框，若不判重，128 个位置会被同一个框占满，别的框全被挤掉。</p>
	 */
	public static void add(FrameBox box) {
		if (box == null) {
			return;
		}
		synchronized (LOCK) {
			FrameBox[] current = frames;
			if (current.length > 0 && current[current.length - 1].equals(box)) {
				return;
			}
			// 只保留最新的一批，再追加：长度到顶时等价于「挤掉最旧的一个」
			int keep = Math.min(current.length, MAX_FRAMES - 1);
			FrameBox[] next = new FrameBox[keep + 1];
			System.arraycopy(current, current.length - keep, next, 0, keep);
			next[keep] = box;
			frames = next;
		}
	}

	/** 清空全部框：积木「清除所有画框」、引擎停止、退出世界都会走到这里。 */
	public static void clear() {
		synchronized (LOCK) {
			frames = EMPTY;
		}
	}

	/** 当前框数量，供自检与日志使用。 */
	public static int size() {
		return frames.length;
	}

	static FrameBox[] snapshot() {
		return frames;
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
				LOG.error("[frame] 画框渲染失败（连续第 {} 次），{} 秒后重试: {}",
						consecutiveFailures, RETRY_COOLDOWN_NANOS / 1_000_000_000L, e.toString());
			}
		}
	}

	private static void renderSafely(WorldRenderContext context) {
		// 只取一次引用：渲染期间脚本线程可能替换数组，两次读会画出半新半旧的两批框
		FrameBox[] boxes = frames;
		if (boxes.length == 0 || context.consumers() == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}
		Vec3d camera = client.gameRenderer.getCamera().getCameraPos();

		MatrixStack matrices = context.matrices();
		matrices.push();
		try {
			matrices.translate(-camera.x, -camera.y, -camera.z);
			VertexConsumer vertices = context.consumers().getBuffer(RenderLayers.lines());
			MatrixStack.Entry entry = matrices.peek();
			for (FrameBox box : boxes) {
				if (!inRange(box, camera)) {
					continue;
				}
				drawEdges(vertices, entry, box);
			}
		} finally {
			// 抛异常也必须弹栈：矩阵栈每帧涨一层，几十秒后画面就整片跑飞
			matrices.pop();
		}
	}

	/**
	 * 框是否在可见距离内。
	 *
	 * <p>每个轴把相机坐标夹进框的范围再算距离，等价于"框上离相机最近的点"。
	 * 相机在框内部时距离为 0，永远可见。</p>
	 */
	private static boolean inRange(FrameBox box, Vec3d camera) {
		// max 侧 +1：方块坐标含端点，外沿在 max+1
		double x = Math.max(box.minX(), Math.min(box.maxX() + 1.0, camera.x));
		double y = Math.max(box.minY(), Math.min(box.maxY() + 1.0, camera.y));
		double z = Math.max(box.minZ(), Math.min(box.maxZ() + 1.0, camera.z));
		double dx = camera.x - x;
		double dy = camera.y - y;
		double dz = camera.z - z;
		return dx * dx + dy * dy + dz * dz <= RENDER_DISTANCE_SQ;
	}

	/**
	 * 画 12 条棱。
	 *
	 * <p>不用 {@code VertexRendering.drawOutline}：那个入口要把范围包成 {@code VoxelShape}，
	 * 每帧每框一次装箱分配；128 个框在 60 帧下是每秒上万次临时对象，只为画 12 条线。</p>
	 *
	 * <p>顶点格式必须写全 POSITION_COLOR_NORMAL_LINE_WIDTH 四项。少写 {@code lineWidth}
	 * 时 BufferBuilder 会在写下一个顶点时校验上一个顶点并抛 Missing elements，
	 * 而那条半成品顶点会留在共用缓冲里，把原版画准星线框的代码一起带崩。</p>
	 */
	private static void drawEdges(VertexConsumer vertices, MatrixStack.Entry entry, FrameBox box) {
		double x0 = box.minX();
		double y0 = box.minY();
		double z0 = box.minZ();
		// 方块坐标含端点 → 外沿在 max + 1
		double x1 = box.maxX() + 1.0;
		double y1 = box.maxY() + 1.0;
		double z1 = box.maxZ() + 1.0;
		int color = box.color();

		// 底面 4 条
		edge(vertices, entry, x0, y0, z0, x1, y0, z0, color);
		edge(vertices, entry, x1, y0, z0, x1, y0, z1, color);
		edge(vertices, entry, x1, y0, z1, x0, y0, z1, color);
		edge(vertices, entry, x0, y0, z1, x0, y0, z0, color);
		// 顶面 4 条
		edge(vertices, entry, x0, y1, z0, x1, y1, z0, color);
		edge(vertices, entry, x1, y1, z0, x1, y1, z1, color);
		edge(vertices, entry, x1, y1, z1, x0, y1, z1, color);
		edge(vertices, entry, x0, y1, z1, x0, y1, z0, color);
		// 立柱 4 条
		edge(vertices, entry, x0, y0, z0, x0, y1, z0, color);
		edge(vertices, entry, x1, y0, z0, x1, y1, z0, color);
		edge(vertices, entry, x1, y0, z1, x1, y1, z1, color);
		edge(vertices, entry, x0, y0, z1, x0, y1, z1, color);
	}

	private static void edge(VertexConsumer vertices, MatrixStack.Entry entry,
			double ax, double ay, double az, double bx, double by, double bz, int color) {
		vertices.vertex(entry, (float) ax, (float) ay, (float) az)
				.color(color).normal(entry, 0f, 1f, 0f).lineWidth(LINE_WIDTH);
		vertices.vertex(entry, (float) bx, (float) by, (float) bz)
				.color(color).normal(entry, 0f, 1f, 0f).lineWidth(LINE_WIDTH);
	}
}
