package xyz.turbomc.client.script.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 视角归属的转移规则。
 *
 * <p>守的是一条容易回归、且症状很隐蔽的规则：<b>「走向」必须能从 MANUAL 手里拿回视角，
 * 但不能抢走「平滑转向」的</b>。之前 {@code setWalkTarget} 把 MANUAL 一起排除在外，
 * 结果是「走向途中执行『看向』→ 帧层再也不写 yaw → PathFollower 每 tick 判定角度差
 * 超过 standTurnDeg 就松开前进键 → 角色站在原地直到走向会话超时」。</p>
 *
 * <p>只测状态，不碰客户端实例：写角度那半需要 {@code MinecraftClient}，
 * 由 {@code ViewSmootherTest} 这种纯 JVM 用例照不到，交给冒烟/真机。</p>
 */
class ViewSmootherTest {
	@BeforeEach
	void reset() {
		ViewSmoother.clear("测试开始");
	}

	@Test
	void 空闲时走向接管视角() {
		assertEquals(ViewSmoother.Mode.NONE, ViewSmoother.mode());

		ViewSmoother.setWalkTarget(10f);

		assertEquals(ViewSmoother.Mode.WALK, ViewSmoother.mode());
	}

	@Test
	void 走向必须能从看向手里拿回视角() {
		ViewSmoother.adoptManual(90f, 30f);
		assertEquals(ViewSmoother.Mode.MANUAL, ViewSmoother.mode());

		ViewSmoother.setWalkTarget(-90f);

		assertEquals(ViewSmoother.Mode.WALK, ViewSmoother.mode());
		assertEquals(-90f, ViewSmoother.targetYaw(), 1e-4);
	}

	@Test
	void 走向不抢平滑转向的视角() {
		ViewSmoother.setTurnTarget(45f, 20f);

		ViewSmoother.setWalkTarget(0f);

		// 转向会话持有期间，走向每 tick 都会登记目标，但归属不能让出去
		assertEquals(ViewSmoother.Mode.TURN, ViewSmoother.mode());
	}

	@Test
	void 清理后回到不控制() {
		ViewSmoother.adoptManual(90f, 30f);

		ViewSmoother.clear("测试结束");

		assertEquals(ViewSmoother.Mode.NONE, ViewSmoother.mode());
	}
}
