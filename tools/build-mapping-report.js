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
const BLOCKS_JAVA = path.join(ROOT, 'src/client/java/xyz/turbomc/client/script/block/Blocks.java');
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

/**
 * 查看器侧的已知改名：sb3 字段名 → 模板占位符名。
 *
 * <p>目前只有一处：{@code action.key} 的状态字段在 sb3 里叫 {@code STATE}，
 * 导入时由 {@code Sb3Mapping.fieldName} 改成 {@code PRESSED}，查看器模板用后者。
 * 这张表只用于把校验的两边对齐；**真正的改名规则仍在 Sb3Mapping**，
 * 那边新增映射时这里要同步，否则校验会报错提醒（这正是想要的效果）。</p>
 */
const VIEWER_RENAMES = {STATE: 'PRESSED', ONOFF: 'PRESSED'};

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

/**
 * 解析 Blocks.java 的 regT/reg 调用：内部类型 → { 模板占位符, 参数名 }。
 *
 * <p>为什么要解析源码而不是只信参数数量：{@code RhinoVm.argsSelfTest} 只比个数，
 * 把 {@code AXIS} 改成 {@code AXES} 这种改法照样通过，运行时却按位置把错的实参
 * 传进 Java（§6.1 的静默错位）。这里把「名字与顺序」也钉住。</p>
 */
function readBlockRegistry() {
	const text = fs.readFileSync(BLOCKS_JAVA, 'utf8');
	const out = new Map();
	const re = /\breg(T)?\s*\(/g;
	let m;
	while ((m = re.exec(text))) {
		const open = text.indexOf('(', m.index);
		const close = matchParen(text, open);
		if (close < 0) {
			continue;
		}
		const call = text.slice(open + 1, close);
		const first = call.match(/"((?:[^"\\]|\\.)*)"/);
		if (!first) {
			continue;
		}
		// 模板是 BlockShape.XXX 之后的第一个字符串字面量；reg() 版本没有模板
		const shapeAt = call.indexOf('BlockShape.');
		let template = '';
		if (shapeAt >= 0) {
			const found = call.slice(shapeAt).match(/,\s*"((?:[^"\\]|\\.)*)"/);
			if (found) {
				template = found[1];
			}
		}
		out.set(first[1], {
			// 占位符大小写都要认：JSON 组的参数名是小写（{json}、{item}），其余组是大写
			placeholders: [...template.matchAll(/\{([A-Za-z0-9_]+)\}/g)].map((x) => x[1]),
			params: [...call.matchAll(/BlockParam\.\w+\("([^"]+)"/g)].map((x) => x[1])
		});
	}
	return out;
}

/** 从 open 处的左括号找到配对的右括号（跳过字符串内部）。 */
function matchParen(text, open) {
	let depth = 0;
	let inString = false;
	for (let i = open; i < text.length; i += 1) {
		const ch = text[i];
		if (inString) {
			if (ch === '\\') {
				i += 1;
			} else if (ch === '"') {
				inString = false;
			}
			continue;
		}
		if (ch === '"') {
			inString = true;
		} else if (ch === '(') {
			depth += 1;
		} else if (ch === ')') {
			depth -= 1;
			if (depth === 0) {
				return i;
			}
		}
	}
	return -1;
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

	// 参数名与顺序：中文文案是权威顺序（产物里的 argOrder 也由它生成），
	// 数据源、Blocks.java 必须逐字对上。只比个数拦不住改名。
	const registry = readBlockRegistry();
	const grab = (text) => [...String(text || '').matchAll(/\[([A-Za-z0-9_]+)\]/g)].map((x) => x[1]);
	for (const block of blocks) {
		const opcode = PREFIX + block.opcode;
		const zh = grab(block.text && block.text.zh);
		const en = grab(block.text && block.text.en);
		const args = Object.keys(block.arguments || {});
		if (zh.join(',') !== args.join(',')) {
			problems.push('数据源：文案与 arguments 顺序不一致 ' + opcode
				+ '（文案 ' + zh.join(',') + ' / 参数 ' + args.join(',') + '）');
		}
		// 英文顺序允许不同（实参顺序由 argOrder 固定，与界面语言无关），
		// 但占位符名字集合必须一致——少一个就等于该参数在英文界面上填不了
		if ([...en].sort().join(',') !== [...zh].sort().join(',')) {
			problems.push('中英文占位符集合不一致 ' + opcode
				+ '（zh ' + zh.join(',') + ' / en ' + en.join(',') + '）');
		}
		const type = typeOf.get(opcode);
		const spec = registry.get(type);
		if (!spec) {
			problems.push('Blocks.java 未登记：' + type + '（' + opcode + '）');
			continue;
		}
		// 查看器模板里的名字经过 fieldName 改名，比对前先对齐
		const viewerZh = zh.map((name) => VIEWER_RENAMES[name] || name);
		if (spec.placeholders.join(',') !== viewerZh.join(',')) {
			problems.push('Blocks.java 模板占位符不一致 ' + opcode
				+ '（' + spec.placeholders.join(',') + ' vs ' + viewerZh.join(',') + '）');
		}
		if (spec.params.join(',') !== viewerZh.join(',')) {
			problems.push('Blocks.java 参数名不一致 ' + opcode
				+ '（' + spec.params.join(',') + ' vs ' + viewerZh.join(',') + '）');
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
