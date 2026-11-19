#!/usr/bin/env node
/*
 * 生成 turbowarp/mapping-report.md。
 *
 * 数据来源只有两处，都是真正的事实源：
 *   - src/client/java/xyz/turbomc/client/script/sb3/Sb3Mapping.java  （opcode ↔ 内部类型）
 *   - turbowarp/src/blocks.data.js                                   （面板顺序、分组标题、外形）
 *
 * 用法：
 *   node tools/build-mapping-report.js          写入文件
 *   node tools/build-mapping-report.js --check  只比对，不一致时退出非零（CI 用）
 *
 * 顺带做一项一致性校验：扩展的数据源里有哪块积木，映射表里就必须有对应条目，反之亦然。
 * 这两边漂移的症状是「面板里有积木但游戏里没反应」，比文档过期严重得多。
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const MAPPING_JAVA = path.join(ROOT, 'src/client/java/xyz/turbomc/client/script/sb3/Sb3Mapping.java');
const BLOCKS_DATA = path.join(ROOT, 'turbowarp/src/blocks.data.js');
const OUTPUT = path.join(ROOT, 'turbowarp/mapping-report.md');

const PREFIX = 'turbomc_';

/** 外形：数据源里的 blockType（小写）→ 表里的大写写法。 */
const SHAPE = {
	hat: 'HAT',
	command: 'COMMAND',
	reporter: 'REPORTER',
	boolean: 'BOOLEAN',
	button: 'BUTTON'
};

/** 第四节是纯文字维护提示，与数据无关，原样保留在这里。 */
const MAINTENANCE = `## 四、维护提示

- **面板分类靠 \`Scratch.BlockType.LABEL\`**：标题块只有 \`blockType\` 与 \`text\`，没有 opcode，
  不进本表、不进 \`.sb3\`、不参与执行。数组顺序就是面板顺序，改顺序不影响任何已导出工程。
  运行时副本（\`assets/turbomc/vm/turbomc-extension.es5.js\`）刻意不带标题——内嵌的
  \`vm.es5.js\` 不支持 label；\`vm-bootstrap.js\` 也对无 opcode 的条目直接跳过。
- 一个内部类型只能对应一个 opcode。曾把「将变量增加」与「将变量设为」映射到同一类型，
  结果查看器把「增加 1」显示成「设为 1」——这类错误不报错，只是语义偏移。
- 扩展 id 是 \`turbomc\`，所以 opcode 前缀是 \`turbomc_\`；改 id 等于让所有已导出工程失效。
- 扩展里每个非帽子积木都必须在 \`NativeRegistry\` 有对应实现，否则运行时静默返回空值。
  改动后的核对方式：比对 \`turbomc_*\` 映射到的类型与 action 包里的注册名。
- **参数的 \`menu\` 必须写菜单名（字符串），不能内联 items 对象**。scratch-vm 用
  \`menuInfo[argInfo.menu]\` 查表，传对象会得到 undefined 并抛错——积木在 TurboWarp 里
  直接拖不出来，日志只有一行 \`Error parsing block\`。
- 不提供攻击积木：自动选目标会打掉落物/经验球，服务端判定无效实体直接断开连接。
  攻击用「点击左键」，走原版射线。`;

/** 按出现顺序读出 Sb3Mapping 里的 Map.entry("a", "b")。 */
function readMapping() {
	const text = fs.readFileSync(MAPPING_JAVA, 'utf8');
	const re = /Map\.entry\("([^"]+)",\s*"([^"]+)"\)/g;
	const entries = [];
	let m;
	while ((m = re.exec(text))) {
		entries.push({ opcode: m[1], type: m[2] });
	}
	return entries;
}

function build() {
	const entries = readMapping();
	const data = require(BLOCKS_DATA);

	const extEntries = entries.filter((e) => e.opcode.startsWith(PREFIX));
	const nativeEntries = entries.filter((e) => !e.opcode.startsWith(PREFIX));

	const typeOf = new Map(extEntries.map((e) => [e.opcode, e.type]));
	const blocks = data.blocks.filter((b) => b.opcode);
	const labels = data.blocks.filter((b) => b.label);

	// 一致性校验：数据源与映射表必须一一对应，缺一边都是「积木看得见却没反应」。
	const problems = [];
	const declared = new Set(blocks.map((b) => PREFIX + b.opcode));
	for (const op of declared) {
		if (!typeOf.has(op)) {
			problems.push('数据源有、映射表缺：' + op);
		}
	}
	for (const op of typeOf.keys()) {
		if (!declared.has(op)) {
			problems.push('映射表有、数据源缺：' + op);
		}
	}
	if (problems.length) {
		throw new Error('扩展数据源与 Sb3Mapping 不一致：\n  ' + problems.join('\n  '));
	}

	const hatCount = blocks.filter((b) => b.blockType === 'hat').length;

	const lines = [];
	lines.push('# 积木与 opcode 对应表');
	lines.push('');
	lines.push('> 由 `node tools/build-mapping-report.js` 从 `Sb3Mapping.java` 与 `turbowarp/src/blocks.data.js`');
	lines.push('> 生成，**不要手改**（CI 的 `--check` 会拦下过期版本）。表格顺序与 TurboWarp 积木面板一致。');
	lines.push('');
	lines.push('## 一、统计');
	lines.push('');
	lines.push('| 项 | 数量 |');
	lines.push('|---|---|');
	lines.push('| 扩展积木 | ' + blocks.length + ' |');
	lines.push('| 其中事件帽子（宿主触发） | ' + hatCount + ' |');
	lines.push('| 需要动作实现 | ' + (blocks.length - hatCount) + ' |');
	lines.push('| 映射表条目 | ' + entries.length + ' |');
	lines.push('| 其中原生积木 | ' + nativeEntries.length + ' |');
	lines.push('');
	lines.push('## 二、扩展积木');
	lines.push('');
	lines.push('| # | opcode（带前缀） | 内部类型 | 外形 |');
	lines.push('|---|---|---|---|');

	let index = 0;
	for (const item of data.blocks) {
		if (item.label) {
			// 标题行不计编号，与面板里的分组一一对应
			lines.push('| | **' + item.label.zh + '** | | |');
			continue;
		}
		if (!item.opcode) {
			continue;
		}
		const opcode = PREFIX + item.opcode;
		const type = typeOf.get(opcode);
		const shape = SHAPE[item.blockType] || item.blockType.toUpperCase();
		index += 1;
		lines.push('| ' + index + ' | `' + opcode + '` | `' + type + '` | ' + shape + ' |');
	}

	lines.push('');
	lines.push('## 三、原生积木');
	lines.push('');
	lines.push('这些是 Scratch 自带积木，导入后由 scratch-vm 直接执行，不经过 Java 动作层。');
	lines.push('');
	lines.push('| 内部类型 | sb3 opcode |');
	lines.push('|---|---|');
	for (const e of nativeEntries) {
		lines.push('| `' + e.type + '` | `' + e.opcode + '` |');
	}
	lines.push('');
	lines.push(MAINTENANCE);
	lines.push('');

	return { text: lines.join('\n'), blocks: blocks.length, labels: labels.length, entries: entries.length };
}

const result = build();
const want = result.text;

if (process.argv.includes('--check')) {
	const have = fs.existsSync(OUTPUT) ? fs.readFileSync(OUTPUT, 'utf8') : '';
	if (have !== want) {
		console.error('[mapping] 产物过期：请运行 node tools/build-mapping-report.js');
		process.exit(1);
	}
	console.log('[mapping] 最新（' + result.blocks + ' 块 / ' + result.labels + ' 标题 / ' + result.entries + ' 映射）');
} else {
	fs.writeFileSync(OUTPUT, want, 'utf8');
	console.log('[mapping] 已写入 ' + path.relative(ROOT, OUTPUT) +
		'（' + result.blocks + ' 块 / ' + result.labels + ' 标题 / ' + result.entries + ' 映射）');
}
