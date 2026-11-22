#!/usr/bin/env node
/*
 * JSON 积木唯一实现的行为对拍（V8 侧）。
 *
 * 用法：
 *   node tools/check-json-parity.js             # 用 tools/json-parity-cases.json 跑一遍，与 golden 比对
 *   node tools/check-json-parity.js --write     # 有意调整语义后，重新生成 golden
 *
 * golden 的来历：迁移前由 Java 实现产出，是「删掉 Java 副本后行为不变」的权威基线；
 * 现在 JS 是唯一实现，所以只有明确要改语义时才用 --write。
 * Rhino 侧的同一批用例由 JsonCoreParityTest 在 JUnit 里跑。
 */

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ROOT = path.resolve(__dirname, '..');
const CASES = path.join(ROOT, 'tools/json-parity-cases.json');
const GOLDEN = path.join(ROOT, 'tools/json-parity-golden.json');
const JSON_LITE = path.join(ROOT, 'turbowarp/src/json-lite.js');

const write = process.argv.includes('--write');

const source = fs.readFileSync(JSON_LITE, 'utf8');
const sandbox = {};
vm.createContext(sandbox);
vm.runInContext(source + '\nthis.createJsonLite = createJsonLite;', sandbox);
const lite = sandbox.createJsonLite();

const cases = JSON.parse(fs.readFileSync(CASES, 'utf8'));

const results = cases.map((item) => {
	let out;
	try {
		out = lite.toScratchString(lite.run(item.op, item.args));
	} catch (e) {
		out = '<<抛出异常: ' + e.message + '>>';
	}
	return {id: item.id, out: out};
});

if (write) {
	fs.writeFileSync(GOLDEN, JSON.stringify(results, null, 2) + '\n');
	console.log('已写入 ' + path.relative(ROOT, GOLDEN) + '（' + results.length + ' 条）');
	process.exit(0);
}

if (!fs.existsSync(GOLDEN)) {
	console.error('缺少 ' + GOLDEN + '\n首次运行可加 --write 生成');
	process.exit(2);
}

const golden = new Map(JSON.parse(fs.readFileSync(GOLDEN, 'utf8')).map((r) => [r.id, r.out]));

let failures = 0;
for (const result of results) {
	const expected = golden.get(result.id);
	if (expected === undefined) {
		console.log('MISS ' + result.id + '  golden 里没有这条结果');
		failures++;
		continue;
	}
	if (result.out !== expected) {
		console.log('FAIL ' + result.id + '  golden=' + JSON.stringify(expected)
			+ '  js=' + JSON.stringify(result.out));
		failures++;
	}
}

if (failures === 0) {
	console.log('JSON 对拍通过：' + cases.length + ' 条用例，输出与基线逐字一致');
	process.exit(0);
}
console.log('\nJSON 对拍失败：' + failures + '/' + cases.length + ' 条不一致');
process.exit(1);
