package xyz.turbomc.client.script.action.path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PathMath} 的单元测试。
 *
 * <p>这四段逻辑错了都不会抛异常，真机上只表现为「走不到」「半路超时」「明明在走却被重算」
 * 「水下提前中止」，复现一次要几十秒。这里把算术与阈值钉死，改常量时能立刻看出影响面。</p>
 *
 * <p>{@link PathMath} 刻意不引任何 Minecraft 类型，所以本测试在纯 JVM 里就能跑，
 * 不需要客户端。</p>
 */
class PathMathTest {

	// ---- 会话超时 ----

	@Test
	@DisplayName("128 格 → 1200 tick：与改造前写死的固定值一致，改公式不能破这条")
	void timeoutKeepsLegacyValueAt128() {
		assertEquals(1200, PathMath.sessionTicks(128));
	}

	@Test
	@DisplayName("短距离被下限 600 接住，远距离被上限 3600 截断")
	void timeoutClampsBothEnds() {
		assertEquals(PathMath.TICKS_MIN, PathMath.sessionTicks(0));
		assertEquals(PathMath.TICKS_MIN, PathMath.sessionTicks(5));
		// 53 格刚好算到 600，54 格开始超出下限
		assertEquals(600, PathMath.sessionTicks(53));
		assertEquals(608, PathMath.sessionTicks(54));
		assertEquals(PathMath.TICKS_MAX, PathMath.sessionTicks(1000));
	}

	@Test
	@DisplayName("距离越大上限不减少（单调），否则远处目标会先被判超时")
	void timeoutIsMonotonic() {
		int previous = 0;
		for (int distance = 0; distance <= 600; distance++) {
			int ticks = PathMath.sessionTicks(distance);
			assertTrue(ticks >= previous, "距离 " + distance + " 的上限比前一个距离还小");
			previous = ticks;
		}
	}

	@Test
	@DisplayName("脚本侧等待上限 = 会话上限 + 40，保证 Java 侧的超时先发生")
	void waitTicksLeavesSlack() {
		assertEquals(PathMath.sessionTicks(128) + 40, PathMath.waitTicks(128));
		assertEquals(1240, PathMath.waitTicks(128));
	}

	// ---- 到达判定 ----

	@Test
	@DisplayName("站位与目标格中心重合时判到达")
	void arrivedAtCenter() {
		assertTrue(PathMath.arrived(0.5, 64, 0.5, 0, 64, 0, 0.6, 0.6));
	}

	@Test
	@DisplayName("同一 x/z 但高 4 格不算到达：只判水平会当场放行，这是本块的存在理由")
	void notArrivedWhenOnlyVerticallyFar() {
		assertFalse(PathMath.arrived(0.5, 68, 0.5, 0, 64, 0, 0.6, 0.6));
	}

	@Test
	@DisplayName("垂直容差容得下半砖（0.5）、容不下整格（1.0）")
	void verticalToleranceBoundary() {
		assertTrue(PathMath.arrived(0.5, 64.5, 0.5, 0, 64, 0, 0.6, 0.6));
		assertFalse(PathMath.arrived(0.5, 65.0, 0.5, 0, 64, 0, 0.6, 0.6));
	}

	@Test
	@DisplayName("水平容差是「≤」而不是「<」：正好压在边界上仍算到达")
	void horizontalToleranceIsInclusive() {
		// 故意用二进制可精确表示的数：容差 0.5、偏移正好 0.5。
		// 换成默认的 0.6 时，`0.5 - 1.1` 得到 -0.6000000000000001，平方后比 0.36 大一丝，
		// 边界值会判 false——那是浮点表示，不是容差方向的问题，别在这里误改实现。
		assertTrue(PathMath.arrived(1.0, 64, 0.5, 0, 64, 0, 0.5, 0.6));
		assertFalse(PathMath.arrived(1.0001, 64, 0.5, 0, 64, 0, 0.5, 0.6));
	}

	@Test
	@DisplayName("斜向按平方和判：两个方向各偏 0.45 时距离 0.636 > 0.6，不算到达")
	void horizontalIsEuclidean() {
		assertFalse(PathMath.arrived(0.95, 64, 0.95, 0, 64, 0, 0.6, 0.6));
		assertTrue(PathMath.arrived(0.9, 64, 0.9, 0, 64, 0, 0.6, 0.6));
	}

	// ---- 卡住窗口 ----

	@Test
	@DisplayName("完全静止：每 tick 计数 +1，第 10 tick 达到判卡线")
	void stillPlayerCountsUp() {
		int stuckTicks = 0;
		for (int tick = 1; tick <= PathMath.STUCK_TICKS; tick++) {
			stuckTicks = PathMath.stuckWindow(0, 0, 0, 0, 0, 0, 0, 0, stuckTicks).stuckTicks();
			assertEquals(tick, stuckTicks);
		}
		assertEquals(PathMath.STUCK_TICKS, stuckTicks);
	}

	@Test
	@DisplayName("相对锚点走出半格：窗口重置，计数清零")
	void anchorProgressResetsWindow() {
		PathMath.Window window = PathMath.stuckWindow(0, 0, 0, 0, 0, 0.6, 0, 0, 7);
		assertTrue(window.progressed());
		assertEquals(0, window.stuckTicks());
	}

	@Test
	@DisplayName("每 tick 位移够 0.02 就不计数：贴墙滑行不会被误判成卡住")
	void smallButRealMovementDoesNotCount() {
		int stuckTicks = 4;
		// 0.03 < 0.5（锚点没走出半格），但 ≥ 0.02，所以这一 tick 算动过
		PathMath.Window window = PathMath.stuckWindow(0, 0, 0, 0, 0, 0.03, 0, 0, stuckTicks);
		assertFalse(window.progressed());
		assertEquals(stuckTicks, window.stuckTicks());
	}

	@Test
	@DisplayName("每 tick 位移不足 0.02 仍然计数：阈值 0.02 才是那道闸，锚点窗口救不了")
	void creepBelowMinMoveStillCounts() {
		// 从锚点起每 tick 只挪 0.01：10 tick 才 0.1 格，够不到 0.5，
		// 所以「贴墙蠕动不会被误判」只在每 tick ≥ 0.02 时成立——这条把边界钉住。
		double x = 0;
		int stuckTicks = 0;
		for (int tick = 1; tick <= PathMath.STUCK_TICKS; tick++) {
			PathMath.Window window = PathMath.stuckWindow(0, 0, x, 0, 0, x + 0.01, 0, 0, stuckTicks);
			assertFalse(window.progressed(), "第 " + tick + " tick 不该被当成有进展");
			stuckTicks = window.stuckTicks();
			x += 0.01;
		}
		assertEquals(PathMath.STUCK_TICKS, stuckTicks);
	}

	@Test
	@DisplayName("小位移里夹一次大位移（被顶开/传送）：计数清零，不判卡")
	void singleBigJumpRescues() {
		// 先攒到 9，再一次性走 0.8 格：锚点分支先于计数触发
		PathMath.Window window = PathMath.stuckWindow(0, 0, 0.09, 0, 0, 0.8, 0, 0, 9);
		assertTrue(window.progressed());
		assertEquals(0, window.stuckTicks());
	}

	@Test
	@DisplayName("锚点的签名里没有 y：垂直位移不可能让窗口判定「有进展」")
	void anchorIgnoresVertical() {
		// 同一 (x,z)、y 差 5 格：锚点只有两维，所以必然无进展，计数照常累加
		PathMath.Window window = PathMath.stuckWindow(0, 0, 0, 0, 0, 0, 5, 0, 3);
		assertFalse(window.progressed());
		assertEquals(3, window.stuckTicks(), "3D 位移 5 格 ≥ 0.02，这一 tick 算动过，计数不增");
	}

	@Test
	@DisplayName("「这一 tick 有没有动」按三维判：从台阶上掉下去只是 y 在变，不该算卡住")
	void verticalDropCountsAsMovement() {
		// 锚点没走出半格（水平 0），但 3D 位移 1.0 格 → 不累加静止计数
		PathMath.Window window = PathMath.stuckWindow(0, 0, 0, 5, 0, 0, 6, 0, 4);
		assertFalse(window.progressed());
		assertEquals(4, window.stuckTicks());
	}

	// ---- 重算决策 ----

	@Test
	@DisplayName("重算次数用尽先于节流判定：否则第 4 次卡住会被节流吞掉，永远不放弃")
	void giveUpOutranksThrottle() {
		assertEquals(PathMath.Replan.GIVE_UP, PathMath.replanStep(PathMath.MAX_REPLANS, 0));
		assertEquals(PathMath.Replan.GIVE_UP, PathMath.replanStep(PathMath.MAX_REPLANS + 5, 999));
	}

	@Test
	@DisplayName("距上次重算不足 20 tick 时节流，够了就重算")
	void throttleWindow() {
		assertEquals(PathMath.Replan.WAIT, PathMath.replanStep(0, 0));
		assertEquals(PathMath.Replan.WAIT, PathMath.replanStep(0, PathMath.REPLAN_INTERVAL - 1));
		assertEquals(PathMath.Replan.REPLAN, PathMath.replanStep(0, PathMath.REPLAN_INTERVAL));
	}

	// ---- 氧气余量 ----

	@Test
	@DisplayName("浅水被下限 80 接住：1 格深只要 50 tick，但留 80 更安全")
	void airReserveFloor() {
		assertEquals(80, PathMath.airReserveForDepth(1));
		assertEquals(80, PathMath.airReserveForDepth(4));
		assertEquals(90, PathMath.airReserveForDepth(5));
	}

	@Test
	@DisplayName("深度线性增长，24 格起被上限 280 截断（满氧 300）")
	void airReserveCeiling() {
		assertEquals(140, PathMath.airReserveForDepth(10));
		assertEquals(280, PathMath.airReserveForDepth(24));
		assertEquals(280, PathMath.airReserveForDepth(25));
		assertEquals(280, PathMath.airReserveForDepth(200));
	}

	@Test
	@DisplayName("深度 0 按 1 格兜底，不返回低于下限的值")
	void airReserveHandlesNonPositiveDepth() {
		assertEquals(80, PathMath.airReserveForDepth(0));
		assertEquals(80, PathMath.airReserveForDepth(-5));
	}
}
