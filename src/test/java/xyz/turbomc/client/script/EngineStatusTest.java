package xyz.turbomc.client.script;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScriptEngine.Status} 的两条状态语义。
 *
 * <p>这里钉的是两个真机上很贵、单测里很便宜的东西：</p>
 * <ol>
 *   <li><b>DEF-01</b>——引擎关闭时哪些状态算「过期现场」，必须纠正成「已关闭」。
 *       先前关闭分支只在 running/RUNNING/HALTED 时收尾，NO_WORLD 会永久残留，
 *       表现为「人已经在世界里，HUD 说还没有进入世界」。</li>
 *   <li><b>DEF-02</b>——「停止全部」后按一次 F6 就该重开（{@code resumeFromHalt}），
 *       不再要求按两次。</li>
 * </ol>
 *
 * <p>注意只断言枚举与纯逻辑：{@code ScriptEngine} 本体要 MinecraftClient，
 * 不进单测。</p>
 */
class EngineStatusTest {

	@Test
	@DisplayName("引擎关闭时，过期的现场状态要纠正为已关闭")
	void staleStates() {
		assertTrue(ScriptEngine.Status.NO_WORLD.staleWhenEngineOff(),
				"标题界面留下的「还没有进入世界」是 DEF-01 的直接成因");
		assertTrue(ScriptEngine.Status.OFF_WORLD_EXIT.staleWhenEngineOff(),
				"上一局退出留下的状态，人回到世界后同样过期");
	}

	@Test
	@DisplayName("故障态与正常态不能被「已关闭」覆盖")
	void keptStates() {
		assertFalse(ScriptEngine.Status.LOAD_FAILED.staleWhenEngineOff(),
				"覆盖成「已关闭」等于抹掉唯一的诊断信息");
		assertFalse(ScriptEngine.Status.RUNTIME_ERROR.staleWhenEngineOff());
		assertFalse(ScriptEngine.Status.NO_PACK.staleWhenEngineOff(),
				"没有启用脚本包是仍然成立的现状");
		assertFalse(ScriptEngine.Status.OFF.staleWhenEngineOff());
		assertFalse(ScriptEngine.Status.RUNNING.staleWhenEngineOff(),
				"运行中由 stop() 收尾，不走这条分支");
		assertFalse(ScriptEngine.Status.HALTED.staleWhenEngineOff(),
				"停止全部同样走 stop()，且要保持可读的提示");
	}
}
