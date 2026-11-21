package xyz.turbomc.client.script.sb3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link ProjectSanity} 的单元测试。
 *
 * <p>钉的是 DEF-03：造型为空的工程交给 VM，会抛 {@code Non-ascii character in
 * FixedAsciiString}，玩家看到的原因与真实缺陷毫无关系。这里保证载入前就能给出
 * 「哪个角色没有造型」或「project.json 哪里不对」。</p>
 *
 * <p>反向也要钉：正常工程必须放行——校验误伤就是「好好的工程突然载不进来」，
 * 比原来的报错更糟。</p>
 */
class ProjectSanityTest {

	@Test
	@DisplayName("角色没有造型：点名到角色，而不是抛出无关的 Rhino 错误")
	void missingCostume() {
		String json = """
				{"targets":[
				  {"name":"Stage","costumes":[{"name":"backdrop"}],"blocks":{}},
				  {"name":"角色1","costumes":[],"blocks":{}}
				]}
				""";
		ProjectSanity.Problem problem = ProjectSanity.check(json);
		assertNotNull(problem);
		assertEquals(ProjectSanity.KEY_MISSING_COSTUME, problem.key());
		assertEquals("角色1", problem.args()[0]);
	}

	@Test
	@DisplayName("costumes 字段整体缺失同样按缺造型处理")
	void absentCostumeField() {
		ProjectSanity.Problem problem = ProjectSanity.check("{\"targets\":[{\"name\":\"Stage\"}]}");
		assertNotNull(problem);
		assertEquals(ProjectSanity.KEY_MISSING_COSTUME, problem.key());
	}

	@Test
	@DisplayName("正常工程放行")
	void healthyProject() {
		String json = """
				{"targets":[
				  {"name":"Stage","costumes":[{"name":"backdrop"}],"blocks":{}},
				  {"name":"角色1","costumes":[{"name":"c1"}],"blocks":{}}
				]}
				""";
		assertNull(ProjectSanity.check(json));
	}

	@Test
	@DisplayName("project.json 语法错误给出可读原因")
	void brokenJson() {
		ProjectSanity.Problem problem = ProjectSanity.check("{\"targets\": [");
		assertNotNull(problem);
		assertEquals(ProjectSanity.KEY_BAD_JSON, problem.key());
		assertNotNull(problem.args()[0]);
	}

	@Test
	@DisplayName("缺少 targets 也算结构不完整")
	void noTargets() {
		ProjectSanity.Problem problem = ProjectSanity.check("{\"targets\":[]}");
		assertNotNull(problem);
		assertEquals(ProjectSanity.KEY_BAD_JSON, problem.key());
	}

	@Test
	@DisplayName("空内容不放行，避免空工程静默变成「0 个脚本」")
	void blank() {
		assertNotNull(ProjectSanity.check("   "));
		assertNotNull(ProjectSanity.check(null));
	}

	@Test
	@DisplayName("next 自环：直接指向自己，必须拦下（DEF-04 的致命形态）")
	void selfCycle() {
		String json = json("""
				"b1": {"opcode":"event_whenflagclicked","next":"b1","parent":null,
				       "inputs":{},"fields":{},"shadow":false,"topLevel":true}
				""");
		ProjectSanity.Problem problem = ProjectSanity.check(json);
		assertNotNull(problem);
		assertEquals(ProjectSanity.KEY_CYCLIC_BLOCKS, problem.key());
		assertEquals("event_whenflagclicked", problem.args()[0]);
	}

	@Test
	@DisplayName("next 互环：两块互指，同样拦下")
	void mutualCycle() {
		String json = json("""
				"b1": {"opcode":"event_whenflagclicked","next":"b2","parent":null,
				       "inputs":{},"fields":{},"shadow":false,"topLevel":true},
				"b2": {"opcode":"turbomc_log","next":"b1","parent":"b1",
				       "inputs":{},"fields":{},"shadow":false,"topLevel":false}
				""");
		ProjectSanity.Problem problem = ProjectSanity.check(json);
		assertNotNull(problem);
		assertEquals(ProjectSanity.KEY_CYCLIC_BLOCKS, problem.key());
	}

	@Test
	@DisplayName("正常链与悬空 next 都放行：只有环才致命")
	void noCycle() {
		String json = json("""
				"b1": {"opcode":"event_whenflagclicked","next":"b2","parent":null,
				       "inputs":{},"fields":{},"shadow":false,"topLevel":true},
				"b2": {"opcode":"turbomc_log","next":null,"parent":"b1",
				       "inputs":{},"fields":{},"shadow":false,"topLevel":false},
				"b3": {"opcode":"turbomc_log","next":"missing","parent":null,
				       "inputs":{},"fields":{},"shadow":false,"topLevel":true}
				""");
		assertNull(ProjectSanity.check(json), "无环工程不该被拦");
	}

	/** 拼一个「Stage + 一个带指定 blocks 的角色」的最小工程。 */
	private static String json(String blocks) {
		return "{\"targets\":["
				+ "{\"name\":\"Stage\",\"isStage\":true,\"costumes\":[{\"name\":\"backdrop\"}],\"blocks\":{}},"
				+ "{\"name\":\"角色1\",\"isStage\":false,\"costumes\":[{\"name\":\"c\"}],\"blocks\":{" + blocks + "}}"
				+ "]}";
	}
}
