package xyz.turbomc.client.script.sb3;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.turbomc.client.script.node.BlockNode;
import xyz.turbomc.client.script.node.Slot;
import xyz.turbomc.client.script.store.ScriptDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * sb3 → 查看器积木树的还原测试。
 *
 * <p>这里守的是三条「看着像面板 bug、实际是导入 bug」的规则，它们都不会报错，
 * 只会让脚本树里冒出原始 opcode 或问号：</p>
 * <ol>
 *   <li>下拉菜单是<b>影子积木</b>（`sensing_keyoptions`、`turbomc_menu_*`），
 *       要取它的字段值当字面量，而不是当成嵌套积木；</li>
 *   <li>`sensing_keypressed` 的键是<b>输入</b>而不是字段，输入名要映射成 `KEY`，
 *       否则模板「按下 {KEY} ?」凑不上；</li>
 *   <li>自定义积木调用的函数名只写在 `mutation.proccode` 里，要读到 `NAME`，
 *       并且 `%s/%b` 占位符要去掉。</li>
 * </ol>
 */
class Sb3RenderingTest {

	/** 一份最小工程：事件 → 按下键? → 按下鼠标按钮? → 调用自定义积木。 */
	private static final String PROJECT = """
			{"targets":[
			  {"isStage":true,"name":"Stage","variables":{},"lists":{},"blocks":{}},
			  {"isStage":false,"name":"Sprite1","variables":{},"lists":{},"blocks":{
			    "a":{"opcode":"event_whenflagclicked","next":"b","topLevel":true,"shadow":false},
			    "b":{"opcode":"sensing_keypressed","next":"c","topLevel":false,"shadow":false,
			         "inputs":{"KEY_OPTION":[1,"k"]}},
			    "k":{"opcode":"sensing_keyoptions","topLevel":false,"shadow":true,
			         "fields":{"KEY_OPTION":["up arrow",null]},"inputs":{}},
			    "c":{"opcode":"turbomc_mouseDown","next":"d","topLevel":false,"shadow":false,
			         "inputs":{"BUTTON":[1,"m"]}},
			    "m":{"opcode":"turbomc_menu_mouseButton","topLevel":false,"shadow":true,
			         "fields":{"mouseButton":["middle",null]},"inputs":{}},
			    "d":{"opcode":"procedures_call","topLevel":false,"shadow":false,
			         "inputs":{"arg0":[1,[4,"5"]]},
			         "mutation":{"tagName":"mutation","proccode":"addscore %s",
			                     "argumentids":"[\\"arg0\\"]","argumentnames":"[\\"p1\\"]"}},
			    "e":{"opcode":"procedures_definition","next":null,"topLevel":true,"shadow":false,
			         "inputs":{"custom_block":[1,"p"]}},
			    "p":{"opcode":"procedures_prototype","topLevel":false,"shadow":true,
			         "inputs":{"arg0":[1,[10,""]]},
			         "mutation":{"tagName":"mutation","proccode":"addscore %s",
			             "argumentids":"[\\"arg0\\"]","argumentnames":"[\\"p1\\"]"}}
			  }}
			]}""";

	private static List<ScriptDefinition> parse() {
		Sb3Importer.Result result = Sb3Importer.parse(JsonParser.parseString(PROJECT));
		assertTrue(result.ok(), () -> "导入失败：" + result.error());
		return result.scripts();
	}

	private static BlockNode body() {
		for (ScriptDefinition definition : parse()) {
			if ("event.tick".equals(definition.hat.type)) {
				return definition.hat.next;
			}
		}
		throw new AssertionError("没找到绿旗脚本");
	}

	@Test
	@DisplayName("下拉菜单影子积木要展开成字面量，而不是留成嵌套积木")
	void menuShadowBecomesLiteral() {
		BlockNode key = body();
		assertEquals("input.key_pressed", key.type);
		Slot slot = key.slot("KEY");
		assertNotNull(slot, "KEY 槽必须存在（KEY_OPTION 输入名要映射成 KEY）");
		assertFalse(slot.hasBlock(), "菜单不该作为嵌套积木挂在槽里");
		assertEquals("up arrow", slot.literal);

		BlockNode mouse = key.next;
		assertEquals("input.mouse_button_down", mouse.type);
		assertEquals("middle", mouse.slot("BUTTON").literal);
	}

	@Test
	@DisplayName("自定义积木调用要读出函数名与参数个数")
	void callReadsProccode() {
		BlockNode call = body().next.next;
		assertEquals("func.call", call.type);
		assertEquals("addscore", call.fields.get("NAME"), "%s 占位符要去掉");
		assertEquals("1", call.fields.get("ARGC"));
		assertEquals("5", call.slot("P1").literal);
	}

	@Test
	@DisplayName("自定义积木定义的名字同样不带 %s")
	void definitionNameStripsPlaceholder() {
		boolean seen = false;
		for (ScriptDefinition definition : parse()) {
			if ("procedures.definition".equals(definition.hat.type)) {
				assertEquals("addscore", definition.hat.fields.get("NAME"));
				seen = true;
			}
		}
		assertTrue(seen, "没找到自定义积木定义");
	}

	/** 带菜单参数的工程：实体类型菜单是影子积木，值应被展开成字面量 id。 */
	private static final String ENTITY_PROJECT = """
			{"targets":[
			  {"isStage":true,"name":"Stage","variables":{},"lists":{},"blocks":{}},
			  {"isStage":false,"name":"Sprite1","variables":{},"lists":{},"blocks":{
			    "a":{"opcode":"event_whenflagclicked","next":"b","topLevel":true,"shadow":false},
			    "b":{"opcode":"turbomc_nearestEntityField","topLevel":false,"shadow":false,
			         "inputs":{"TYPE":[1,"m"]},"fields":{"FIELD":["distance",null]}},
			    "m":{"opcode":"turbomc_menu_entityType","topLevel":false,"shadow":true,
			         "fields":{"entityType":["minecraft:zombie",null]},"inputs":{}}
			  }}
			]}""";

	@Test
	@DisplayName("实体类型菜单同样展开成字面量（查看器才能显示成中文名）")
	void entityTypeMenuBecomesLiteral() {
		Sb3Importer.Result result = Sb3Importer.parse(JsonParser.parseString(ENTITY_PROJECT));
		assertTrue(result.ok(), () -> "导入失败：" + result.error());
		BlockNode node = result.scripts().get(0).hat.next;
		assertEquals("world.nearest_entity_field", node.type);
		assertEquals("minecraft:zombie", node.slot("TYPE").literal);
		assertFalse(node.slot("TYPE").hasBlock(), "菜单不该作为嵌套积木挂在槽里");
	}
}
