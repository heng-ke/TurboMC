package xyz.turbomc.client.script.render;

/**
 * 一个待绘制的方块范围（世界坐标系里的一格到另一格，含两端）。
 *
 * <p>刻意做成不依赖 Minecraft 的纯数据：坐标归一化与颜色解析是这一批最容易悄悄出错的
 * 逻辑（NaN、上下界反了、颜色写成 {@code #ff5}），放在这里就能用普通 JVM 单测覆盖，
 * 不必起客户端。</p>
 *
 * <p>坐标语义：{@code min} 与 {@code max} 都是<b>方块坐标</b>，均含端点。渲染时
 * {@code max} 侧要 +1 才是方块的外沿——这段换算只发生在渲染层，数据层保持"含端点"，
 * 与用户在积木里填的数字一一对应。</p>
 *
 * @param color 不透明 ARGB（{@code 0xFF______}）
 */
public record FrameBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int color) {

	/** 颜色解析失败时的回退色：亮绿，在线框层里对比度最好。 */
	public static final int DEFAULT_COLOR = 0xFF33FF66;

	/**
	 * 世界坐标绝对上限，与 {@code GameActions#clampCoord} 保持一致。
	 *
	 * <p>脚本传入 {@code 1e20} 这类值时，直接取整会饱和到 {@code Integer.MAX_VALUE}，
	 * 之后 {@code max + 1} 还会溢出成负数，画出来的框会翻到世界另一头。</p>
	 */
	private static final double LIMIT = 30_000_000d;

	/**
	 * 由两个角点构造：向下取整、夹到世界边界内、自动把大小关系摆正。
	 *
	 * @param rawColor 积木里的颜色字符串（{@code #rrggbb} / {@code #rgb}）；非法时回退
	 *                 {@link #DEFAULT_COLOR}
	 */
	public static FrameBox of(double x1, double y1, double z1, double x2, double y2, double z2,
			String rawColor) {
		int ax = clamp(x1);
		int ay = clamp(y1);
		int az = clamp(z1);
		int bx = clamp(x2);
		int by = clamp(y2);
		int bz = clamp(z2);
		return new FrameBox(
				Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz),
				Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz),
				parseColor(rawColor));
	}

	/**
	 * 坐标 → 方块坐标：NaN 归零，越界夹紧，其余向下取整。
	 *
	 * <p>用 floor 而不是截断：{@code -0.5} 截断成 0 会跑到格子另一边，
	 * 而玩家/脚本算出来的坐标是连续值，向下取整才对应"所在方块"。</p>
	 */
	static int clamp(double value) {
		if (Double.isNaN(value)) {
			return 0;
		}
		double clamped = Math.max(-LIMIT, Math.min(LIMIT, value));
		return (int) Math.floor(clamped);
	}

	/**
	 * 解析 {@code #rrggbb} / {@code #rgb}（也接受不带 {@code #}、带 {@code 0x} 前缀、
	 * 大小写混写）。
	 *
	 * <p>alpha 固定为 {@code 0xFF}：线框层不做混合，半透明线段在深度测试下会时隐时现，
	 * 看起来像渲染故障。需要"淡一点"请换更暗的 RGB。</p>
	 */
	public static int parseColor(String raw) {
		if (raw == null) {
			return DEFAULT_COLOR;
		}
		String text = raw.trim();
		if (text.startsWith("#")) {
			text = text.substring(1);
		} else if (text.regionMatches(true, 0, "0x", 0, 2)) {
			text = text.substring(2);
		}
		if (text.length() == 3) {
			// #abc → #aabbcc：逐位展开，免得用户写了简写就静默回退成默认色
			StringBuilder expanded = new StringBuilder(6);
			for (int i = 0; i < 3; i++) {
				char c = text.charAt(i);
				expanded.append(c).append(c);
			}
			text = expanded.toString();
		}
		if (text.length() != 6) {
			return DEFAULT_COLOR;
		}
		try {
			return 0xFF000000 | Integer.parseInt(text, 16);
		} catch (NumberFormatException e) {
			return DEFAULT_COLOR;
		}
	}

	public int sizeX() {
		return maxX - minX + 1;
	}

	public int sizeY() {
		return maxY - minY + 1;
	}

	public int sizeZ() {
		return maxZ - minZ + 1;
	}

	/** 含端点计算的方块数；超大范围可能溢出，仅作日志/诊断用途。 */
	public long volume() {
		return (long) sizeX() * sizeY() * sizeZ();
	}
}
