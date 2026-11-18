package xyz.turbomc.client.script.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 画框坐标与颜色的纯逻辑测试。
 *
 * <p>这两件事都不会抛异常：颜色写错只是悄悄回退成默认绿，坐标顺序写反只是框跑到别处。
 * 真机上表现为"框颜色不对/位置不对"，很难判断是脚本算错还是宿主解析错，
 * 所以在纯 JVM 层把契约钉死。</p>
 */
class FrameBoxTest {

	@Test
	@DisplayName("颜色：#rrggbb / #rgb / 无 # / 大写都认")
	void parseColorAcceptsCommonForms() {
		assertEquals(0xFF33FF66, FrameBox.parseColor("#33ff66"));
		assertEquals(0xFF33FF66, FrameBox.parseColor("33FF66"));
		assertEquals(0xFF33FF66, FrameBox.parseColor("#3f6"));
		assertEquals(0xFFAA0000, FrameBox.parseColor("0xaa0000"));
		assertEquals(0xFF000000, FrameBox.parseColor("#000000"));
	}

	@Test
	@DisplayName("颜色：非法输入一律回退默认绿，不抛异常")
	void parseColorFallsBack() {
		assertEquals(FrameBox.DEFAULT_COLOR, FrameBox.parseColor(null));
		assertEquals(FrameBox.DEFAULT_COLOR, FrameBox.parseColor(""));
		assertEquals(FrameBox.DEFAULT_COLOR, FrameBox.parseColor("red"));
		assertEquals(FrameBox.DEFAULT_COLOR, FrameBox.parseColor("#12345"));
		assertEquals(FrameBox.DEFAULT_COLOR, FrameBox.parseColor("#gggggg"));
	}

	@Test
	@DisplayName("坐标：两点顺序颠倒时自动摆正")
	void orderDoesNotMatter() {
		FrameBox box = FrameBox.of(10, 70, 10, 0, 64, 0, "#ffffff");
		assertEquals(0, box.minX());
		assertEquals(64, box.minY());
		assertEquals(0, box.minZ());
		assertEquals(10, box.maxX());
		assertEquals(70, box.maxY());
		assertEquals(10, box.maxZ());
	}

	@Test
	@DisplayName("坐标：小数向下取整（-0.5 属于 -1 格，不是 0）")
	void floorsInsteadOfTruncating() {
		FrameBox box = FrameBox.of(1.9, 64.0, -0.5, 1.1, 64.9, 0.5, "#ffffff");
		assertEquals(1, box.minX());
		assertEquals(1, box.maxX());
		assertEquals(-1, box.minZ());
		assertEquals(0, box.maxZ());
	}

	@Test
	@DisplayName("坐标：NaN 归零、超大值夹到世界边界，且不会因 +1 溢出")
	void clampsExtremeValues() {
		FrameBox nan = FrameBox.of(Double.NaN, Double.NaN, Double.NaN, 0, 0, 0, "#ffffff");
		assertEquals(0, nan.minX());
		assertEquals(0, nan.maxX());

		FrameBox huge = FrameBox.of(1e20, 0, 0, -1e20, 0, 0, "#ffffff");
		assertEquals(-30_000_000, huge.minX());
		assertEquals(30_000_000, huge.maxX());
	}

	@Test
	@DisplayName("范围含端点：单格框的尺寸为 1，不是 0")
	void sizeIncludesBothEnds() {
		FrameBox single = FrameBox.of(5, 64, 5, 5, 64, 5, "#ffffff");
		assertEquals(1, single.sizeX());
		assertEquals(1, single.sizeY());
		assertEquals(1, single.sizeZ());
		assertEquals(1L, single.volume());

		FrameBox area = FrameBox.of(0, 60, 0, 9, 69, 9, "#ffffff");
		assertEquals(10, area.sizeX());
		assertEquals(1000L, area.volume());
	}

	@Test
	@DisplayName("相等判定覆盖颜色：同位置不同色算两个框")
	void equalityIncludesColor() {
		FrameBox green = FrameBox.of(0, 64, 0, 1, 65, 1, "#33ff66");
		FrameBox red = FrameBox.of(0, 64, 0, 1, 65, 1, "#ff0000");
		assertEquals(green, FrameBox.of(0, 64, 0, 1, 65, 1, "#33ff66"));
		assertEquals(false, green.equals(red));
	}
}
