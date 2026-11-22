#!/usr/bin/env node
/*
 * JSON 积木跨引擎对拍（JS 侧）。
 *
 * 用法：
 *   ./gradlew test --tests '*JsonParityTest*'     # 先产出 Java 侧结果
 *   node tools/check-json-parity.js               # 再跑 JS 侧并比对
 *
 * 比对的是「积木最终显示出来的字符串」：两边都用各自的字符串化（Java 是 ScratchCast.toString，
 * JS 是 String()），这样数值格式（1.0 vs 1）这类差异会被一并抓住——那正是最该抓的一类。
 */

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ROOT = path.resolve(__dirname, '..');
const CASES = path.join(ROOT, 'tools/json-parity-cases.json');
const JAVA_OUT = path.join(ROOT, 'build/json-parity-java.json');
const JSON_LITE = path.join(ROOT, 'turbowarp/src/json-lite.js');

if (!fs.existsSync(JAVA_OUT)) {
	console.error('缺少 ' + JAVA_OUT + '\n先运行：./gradlew test --tests "*JsonParityTest*"');
	process.exit(2);
}

const source = fs.readFileSync(JSON_LITE, 'utf8');
const sandbox = {};
vm.createContext(sandbox);
vm.runInContext(source + '\nthis.createJsonLite = createJsonLite;', sandbox);
const lite = sandbox.createJsonLite();

const cases = JSON.parse(fs.readFileSync(CASES, 'utf8'));
const javaResults = new Map(JSON.parse(fs.readFileSync(JAVA_OUT, 'utf8')).map((r) => [r.id, r.out]));

let failures = 0;
for (const item of cases) {
	const expected = javaResults.get(item.id);
	if (expected === undefined) {
		console.log('MISS ' + item.id + '  Java 侧没有这条结果');
		failures++;
		continue;
	}
	let actual;
	try {
		actual = lite.toScratchString(lite.run(item.op, item.args));
	} catch (e) {
		actual = '<<抛出异常: ' + e.message + '>>';
	}
	if (actual !== expected) {
		console.log('FAIL ' + item.id + '  ' + item.op + '  java=' + JSON.stringify(expected)
			+ '  js=' + JSON.stringify(actual));
		failures++;
	}
}

const total = cases.length;
if (failures === 0) {
	console.log('JSON 对拍通过：' + total + ' 条用例，两侧输出逐字一致');
	process.exit(0);
}
console.log('\nJSON 对拍失败：' + failures + '/' + total + ' 条不一致');
process.exit(1);
