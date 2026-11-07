package xyz.turbomc.client.script.action.motion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TurnMath} 的单元测试。
 *
 * <p>这个类被「走向」与「平滑转向」两处共用，一份实现走偏两处都会坏。
 * 它的三条性质是刻意设计的，回归时要能立刻发现：</p>
 * <ol>
 *   <li><b>限幅</b>：单 tick 最多转 12°（pitch 10°），不能因为差得多就甩过头；</li>
 *   <li><b>死区</b>：剩余角度 ≤1.5° 就不转，避免噪声导致左右微摆；</li>
 *   <li><b>最短路径</b>：yaw 差值折回 [-180, 180]，从 179° 转到 -179° 只走 2°。</li>
 * </ol>
 */
class TurnMathTest {

	@Test
	@DisplayName("死区内不转，避免微摆")
	void deadZoneStops() {
		assertEquals(0f, TurnMath.yawStep(0f));
		assertEquals(0f, TurnMath.yawStep(1.5f));
		assertEquals(0f, TurnMath.yawStep(-1.5f));
		assertEquals(0f, TurnMath.yawStep(0.5f));
	}

	@Test
	@DisplayName("超过死区就转")
	void beyondDeadZoneMoves() {
		assertTrue(TurnMath.yawStep(2f) > 0f);
		assertTrue(TurnMath.yawStep(-2f) < 0f);
	}

	@Test
	@DisplayName("单 tick 步长不超过 yaw 上限 12°")
	void yawStepRespectsLimit() {
		assertEquals(TurnMath.MAX_YAW_STEP, TurnMath.yawStep(180f));
		assertEquals(TurnMath.MAX_YAW_STEP, TurnMath.yawStep(90f));
		assertEquals(TurnMath.MAX_YAW_STEP, TurnMath.yawStep(30f));
	}

	@Test
	@DisplayName("单 tick 步长不超过 pitch 上限 10°")
	void pitchStepRespectsLimit() {
		assertEquals(TurnMath.MAX_PITCH_STEP, TurnMath.pitchStep(180f));
		// 步长带符号：负差得到负步长，幅度仍是 10
		assertEquals(-TurnMath.MAX_PITCH_STEP, TurnMath.pitchStep(-45f));
	}

	@Test
	@DisplayName("步长带符号：正差为正、负差为负")
	void stepKeepsSign() {
		assertTrue(TurnMath.yawStep(30f) > 0f);
		assertTrue(TurnMath.yawStep(-30f) < 0f);
	}

	@Test
	@DisplayName("比例收敛：差得越多转得越多，但不超过限幅")
	void proportionalConvergence() {
		float small = TurnMath.yawStep(10f);
		float large = TurnMath.yawStep(60f);
		assertTrue(large > small, "大角度应转得更多");
		assertTrue(large <= TurnMath.MAX_YAW_STEP, "但仍受限幅约束");
	}

	@Test
	@DisplayName("NaN 与无穷输入得 0，不把 NaN 传进 setYaw")
	void nonFiniteIsZero() {
		// setYaw 遇 NaN 会走 Util.logErrorOrPause，开发环境直接抛异常
		assertEquals(0f, TurnMath.yawStep(Float.NaN));
		assertEquals(0f, TurnMath.yawStep(Float.POSITIVE_INFINITY));
		assertEquals(0f, TurnMath.yawStep(Float.NEGATIVE_INFINITY));
		assertEquals(0f, TurnMath.pitchStep(Float.NaN));
	}

	@Test
	@DisplayName("yawDiff 走最短路径：179° 到 -179° 只差 2°")
	void yawDiffUsesShortestPath() {
		assertEquals(2f, TurnMath.yawDiff(179f, -179f), 1e-4f);
		assertEquals(-2f, TurnMath.yawDiff(-179f, 179f), 1e-4f);
	}

	@ParameterizedTest
	@DisplayName("yawDiff 的常见取值")
	@CsvSource({
			"0, 90, 90",
			"90, 0, -90",
			"0, 0, 0",
			// wrapDegrees 折回 [-180, 180)：正 180 落成 -180，两边等价
			"0, 180, -180",
			"0, -180, -180"
	})
	void yawDiffCases(float current, float want, float expected) {
		assertEquals(expected, TurnMath.yawDiff(current, want), 1e-4f);
	}

	@ParameterizedTest
	@DisplayName("步长绝不超过对应上限")
	@ValueSource(floats = {1f, 5f, 20f, 90f, 180f, 359f})
	void stepNeverExceedsLimit(float diff) {
		assertTrue(Math.abs(TurnMath.yawStep(diff)) <= TurnMath.MAX_YAW_STEP);
		assertTrue(Math.abs(TurnMath.pitchStep(diff)) <= TurnMath.MAX_PITCH_STEP);
	}
}
