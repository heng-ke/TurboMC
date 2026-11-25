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
 * 回归时重点盯三件事：死区、最短路径、帧率无关性。</p>
 */
class TurnMathTest {

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

	// ---- 帧级步进（ViewSmoother 用）----

	@Test
	@DisplayName("帧级步进：死区内不转")
	void frameStepDeadZone() {
		assertEquals(0f, TurnMath.frameStep(1.5f, 1f / 60f, TurnMath.GAIN_WALK, TurnMath.MAX_YAW_SPEED));
		assertEquals(0f, TurnMath.frameStep(-1.4f, 1f / 60f, TurnMath.GAIN_WALK, TurnMath.MAX_YAW_SPEED));
	}

	@ParameterizedTest
	@DisplayName("帧级步长不超过「按秒限速 × dt」")
	@ValueSource(floats = {1f / 144f, 1f / 60f, 1f / 20f, 0.05f})
	void frameStepRespectsSpeedLimit(float dt) {
		float step = TurnMath.frameStep(180f, dt, TurnMath.GAIN_TURN, TurnMath.MAX_YAW_SPEED);
		assertTrue(Math.abs(step) <= TurnMath.MAX_YAW_SPEED * dt + 1e-4f);
	}

	@Test
	@DisplayName("帧级步进不越过目标，否则会在目标附近来回摆")
	void frameStepNeverOvershoots() {
		float diff = 3f;
		float step = TurnMath.frameStep(diff, 0.05f, TurnMath.GAIN_TURN, 1_000f);
		assertTrue(step <= diff);
		assertTrue(step > 0f);
	}

	@Test
	@DisplayName("帧级步进与帧率无关：60 fps 与 144 fps 走完 90° 的时间接近")
	void frameStepIsFrameRateIndependent() {
		float at60 = convergeSeconds(TurnMath.GAIN_WALK, 60);
		float at144 = convergeSeconds(TurnMath.GAIN_WALK, 144);
		// 指数收敛 + 按秒限速的联合效果：帧率只影响步长粒度，不影响总时长
		assertEquals(at60, at144, 0.06f, "两种帧率下收敛时间应接近（60fps=" + at60 + "s, 144fps=" + at144 + "s）");
	}

	@Test
	@DisplayName("帧级步进：dt 非法或非正时为 0")
	void frameStepRejectsInvalidDt() {
		assertEquals(0f, TurnMath.frameStep(30f, 0f, TurnMath.GAIN_WALK, TurnMath.MAX_YAW_SPEED));
		assertEquals(0f, TurnMath.frameStep(30f, -0.016f, TurnMath.GAIN_WALK, TurnMath.MAX_YAW_SPEED));
		assertEquals(0f, TurnMath.frameStep(Float.NaN, 1f / 60f, TurnMath.GAIN_WALK, TurnMath.MAX_YAW_SPEED));
		assertEquals(0f, TurnMath.frameStep(30f, Float.POSITIVE_INFINITY,
				TurnMath.GAIN_WALK, TurnMath.MAX_YAW_SPEED));
	}

	/** 从 90° 差开始，按给定帧率迭代到死区以内，返回耗时（秒）。 */
	private static float convergeSeconds(float gain, int fps) {
		float dt = 1f / fps;
		float diff = 90f;
		float elapsed = 0f;
		int guard = fps * 10;
		while (Math.abs(diff) > TurnMath.DEAD_ZONE && guard-- > 0) {
			diff -= TurnMath.frameStep(diff, dt, gain, TurnMath.MAX_YAW_SPEED);
			elapsed += dt;
		}
		return elapsed;
	}
}