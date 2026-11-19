package xyz.turbomc.client.script.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 参数值本地化的两段式校验：<b>拼出的语言键对不对</b>、<b>语言文件里有没有这个键</b>。
 *
 * <p>这两段必须分别断言。真实事故：「按下 [上箭头]?」在界面上显示英文 {@code up arrow}——
 * 键名归一化只对 {@code OP} 做了空格换下划线，{@code KEY} 没做，于是拼出
 * {@code value.turbomc.key.up arrow}（带空格），查不到就回落成原始值。单字值 {@code space}
 * 恰好不需要归一化，所以只看「空格」是发现不了这个 bug 的。</p>
 *
 * <p>这里不碰 {@code I18n}（纯 JVM 起不来），只校验键名与语言资源本身：
 * 两者都对，运行时 {@code I18n.translate} 就一定能命中。</p>
 */
class ParamLabelsTest {

	@ParameterizedTest(name = "{0} = {1} → {2}")
	@DisplayName("枚举值拼出的语言键：多词值必须归一化成小写 + 下划线")
	@CsvSource({
			"KEY,      space,       value.turbomc.key.space",
			"KEY,      up arrow,    value.turbomc.key.up_arrow",
			"KEY,      down arrow,  value.turbomc.key.down_arrow",
			"KEY,      left arrow,  value.turbomc.key.left_arrow",
			"KEY,      right arrow, value.turbomc.key.right_arrow",
			"KEY,      any,         value.turbomc.key.any",
			"KEY,      a,           value.turbomc.key.a",
			"KEY,      left.shift,  value.turbomc.key.left.shift",
			"KEY,      F12,         value.turbomc.key.f12",
			"BUTTON,   middle,      value.turbomc.mouse.middle",
			"PRESSED,  true,        value.turbomc.onoff.true",
			"UNIT,     dayofweek,   value.turbomc.unit.dayofweek",
			"MENU,     timer,       value.turbomc.menu.timer",
			"SCOPE,    global,      value.turbomc.scope.global",
			"DIRECTION, forward,    value.turbomc.direction.forward",
			"OP,       e ^,         value.turbomc.mathop.e_^",
			"TYPE,     any,         value.turbomc.entity.any",
			"TYPE,     HOSTILE,     value.turbomc.entity.hostile",
			"TYPE,     minecraft:zombie, value.turbomc.entity.minecraft:zombie",
			"AXIS,     x,           value.turbomc.field.x",
			"FIELD,    distance,    value.turbomc.field.distance",
			"FIELD,    durability,  value.turbomc.field.durability",
			"SCREEN,   container,   value.turbomc.screen.container",
			"KIND,     block_entity, value.turbomc.kind.block_entity",
	})
	void buildsNormalizedKey(String param, String value, String expected) {
		assertEquals(expected, ParamLabels.translationKey(param, value));
	}

	@Test
	@DisplayName("不是枚举的参数不构造语言键（按原值显示）")
	void unknownParamHasNoKey() {
		assertNull(ParamLabels.translationKey("TEXT", "hello"));
		assertNull(ParamLabels.translationKey("VAR", "score"));
	}

	@ParameterizedTest(name = "{0} = {1}")
	@DisplayName("中英语言文件都覆盖了这些枚举值")
	@CsvSource({
			"KEY,      space", "KEY,      up arrow", "KEY,      down arrow",
			"KEY,      left arrow", "KEY,      right arrow", "KEY,      any",
			"KEY,      a", "KEY,      left.shift", "KEY,      F12",
			"BUTTON,   left", "BUTTON,   middle", "PRESSED,  true", "PRESSED,  false",
			"UNIT,     dayofweek", "MENU,     timer", "MENU,     loudness",
			"SCOPE,    local", "SCOPE,    global", "DIRECTION, forward", "OP,       e ^",
			"TYPE,     any", "TYPE,     hostile", "TYPE,     player",
			"AXIS,     x", "AXIS,     y", "AXIS,     z",
			"FIELD,    distance", "FIELD,    name", "FIELD,    id",
			"FIELD,    amount", "FIELD,    durability",
			// 快照批次新增的字段：两张语言表都必须有键，否则界面上退回英文原值
			"FIELD,    health", "FIELD,    max_health", "FIELD,    type_id",
			"FIELD,    count", "FIELD,    max_durability", "FIELD,    damage",
			"FIELD,    custom_name",
			// 「当打开 [界面]」的三档
			"SCREEN,   container", "SCREEN,   inventory", "SCREEN,   any",
			// 「[快照] 的字段」的十档
			"KIND,     player", "KIND,     entity", "KIND,     item", "KIND,     screen",
			"KIND,     block", "KIND,     block_entity", "KIND,     world", "KIND,     players",
			"KIND,     scan", "KIND,     chat",
	})
	void languageFilesCoverValue(String param, String value) {
		String key = ParamLabels.translationKey(param, value);
		assertNotNull(key, "未构造出语言键：" + param + "=" + value);
		assertTranslated("zh_cn", key);
		assertTranslated("en_us", key);
	}

	private static void assertTranslated(String locale, String key) {
		JsonObject lang = loadLang(locale);
		String text = lang.has(key) && lang.get(key).isJsonPrimitive() ? lang.get(key).getAsString() : null;
		assertNotNull(text, locale + " 缺少语言键：" + key);
		assertEquals(false, text.isBlank(), locale + " 的语言键是空串：" + key);
	}

	private static JsonObject loadLang(String locale) {
		String path = "/assets/turbomc/lang/" + locale + ".json";
		try (InputStream in = ParamLabelsTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "classpath 上找不到 " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new AssertionError("读取 " + path + " 失败", e);
		}
	}
}
