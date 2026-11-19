#!/usr/bin/env node
/*
 * 从单一数据源渲染 TurboMC 扩展的两份产物：
 *
 *   turbowarp/src/blocks.data.js  +  turbowarp/src/items.data.js
 *            │
 *            ├─▶ turbowarp/turbomc-extension.js                    （编辑器副本，ES6，带占位方法与分组标题）
 *            └─▶ src/client/resources/assets/turbomc/vm/turbomc-extension.es5.js （运行时副本，ES5，无占位方法与标题）
 *
 * 两份产物的差异只有四处，其它内容必须逐字一致：
 *   1. 语法：编辑器是 class，运行时是普通对象（Rhino 里不需要 class，也就省掉 Babel 中间层）；
 *   2. 分组标题：只有编辑器渲染（内嵌 vm.es5.js 不支持 label）；
 *   3. 菜单 items：编辑器内联数组（Worker 要结构化克隆），运行时用函数引用（省 1.6 秒初始化）；
 *   4. 占位方法：只有编辑器生成（游戏内由 vm-bootstrap 注入真实调用）。
 *
 * 用法：
 *   node tools/build-extension.js           写入两份产物
 *   node tools/build-extension.js --check   只校验产物是否过期（CI 用，过期退出非零）
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const DATA = require(path.join(ROOT, 'turbowarp/src/blocks.data.js'));
const ITEMS = require(path.join(ROOT, 'turbowarp/src/items.data.js'));

const OUTPUTS = {
	editor: path.join(ROOT, 'turbowarp/turbomc-extension.js'),
	runtime: path.join(ROOT, 'src/client/resources/assets/turbomc/vm/turbomc-extension.es5.js'),
};

const ARG_TYPES = {
	string: 'STRING',
	number: 'NUMBER',
	boolean: 'BOOLEAN',
	angle: 'ANGLE',
	color: 'COLOR',
	note: 'NOTE',
};

const q = (value) => "'" + String(value)
	.replace(/\\/g, '\\\\')
	.replace(/'/g, "\\'")
	.replace(/\n/g, '\\n')
	.replace(/\r/g, '\\r')
	.replace(/\t/g, '\\t') + "'";

/** 默认值：数字/布尔裸写，其余按字符串。 */
const literal = (value) => {
	if (typeof value === 'number' || typeof value === 'boolean') {
		return String(value);
	}
	return q(value);
};

const oneLine = (text) => String(text).replace(/\s*\n\s*/g, ' ');

function emitArgument(arg) {
	const parts = ['type: Scratch.ArgumentType.' + (ARG_TYPES[arg.type] || 'STRING')];
	if (arg.defaultValue !== undefined) {
		parts.push('defaultValue: ' + literal(arg.defaultValue));
	}
	if (arg.menu) {
		parts.push('menu: ' + q(arg.menu));
	}
	return '{' + parts.join(', ') + '}';
}

function emitBlocks(target) {
	const blocks = [];
	for (const block of DATA.blocks) {
		if (block.label) {
			if (target === 'runtime') {
				continue;
			}
			blocks.push('\t\t\t\t\t{blockType: Scratch.BlockType.LABEL, text: T('
				+ q(block.label.en) + ', ' + q(block.label.zh) + ')}');
			continue;
		}
		const lines = ['\t\t\t\t\t{'];
		lines.push('\t\t\t\t\t\topcode: ' + q(block.opcode) + ',');
		lines.push('\t\t\t\t\t\tblockType: Scratch.BlockType.' + block.blockType.toUpperCase() + ',');
		lines.push('\t\t\t\t\t\ttext: T(' + q(block.text.en) + ', ' + q(block.text.zh) + '),');
		if (block.arguments && Object.keys(block.arguments).length) {
			const args = Object.entries(block.arguments)
				.map(([name, arg]) => '\t\t\t\t\t\t\t' + name + ': ' + emitArgument(arg));
			lines.push('\t\t\t\t\t\targuments: {');
			lines.push(args.join(',\n'));
			lines.push('\t\t\t\t\t\t},');
		}
		if (block.props) {
			for (const [key, value] of Object.entries(block.props)) {
				lines.push('\t\t\t\t\t\t' + key + ': ' + literal(value) + ',');
			}
		}
		lines[lines.length - 1] = lines[lines.length - 1].replace(/,$/, '');
		lines.push('\t\t\t\t\t}');
		blocks.push(lines.join('\n'));
	}
	return blocks.join(',\n');
}

function emitMenus(target) {
	const menus = [];
	for (const [name, menu] of Object.entries(DATA.menus)) {
		if (menu.dynamic) {
			// 编辑器要数组（结构化克隆），运行时要函数引用（避免注册期展开 1505 项）
			const items = target === 'editor' ? 'itemMenuItems()' : 'itemMenuItems';
			menus.push('\t\t\t\t\t' + name + ': {acceptReporters: ' + !!menu.acceptReporters
				+ ', items: ' + items + '}');
			continue;
		}
		const items = menu.items.map((item) => '\t\t\t\t\t\t\t{text: T('
			+ q(item.text.en) + ', ' + q(item.text.zh) + '), value: ' + q(item.value) + '}');
		menus.push('\t\t\t\t\t' + name + ': {acceptReporters: ' + !!menu.acceptReporters
			+ ', items: [\n' + items.join(',\n') + '\n\t\t\t\t\t]}');
	}
	return menus.join(',\n');
}

function emitStubs() {
	const stubs = [];
	for (const block of DATA.blocks) {
		if (!block.opcode || !block.stub) {
			continue;
		}
		const params = /args\./.test(block.stub) ? 'args' : '';
		stubs.push('\t\t' + block.opcode + '(' + params + ') { ' + oneLine(block.stub) + ' }');
	}
	return stubs.join('\n');
}

const ITEM_TABLE_TEXT = '[\n' + ITEMS.map((row) => '\t' + q(row)).join(',\n') + '\n]';

function renderEditor() {
	return `(function (Scratch) {
	'use strict';

	/*
	 * 本文件由 tools/build-extension.js 生成，不要手改。
	 * 要改积木或菜单，请改 turbowarp/src/blocks.data.js（物品表在 items.data.js），然后重新生成。
	 *
	 * 编辑器副本：给 TurboWarp 用。浏览器里没有 Java，所有积木都是占位实现，
	 * 真正的执行发生在游戏内——由 vm-bootstrap 把每个 opcode 换成对宿主的调用。
	 */

	let lang = 'en';
	try {
		lang = (navigator.language || 'en').toLowerCase();
	} catch (e) {
		lang = 'en';
	}
	const ZH = lang.indexOf('zh') === 0;

	/** 中英切换 */
	const T = (en, zh) => (ZH ? zh : en);

const ITEM_TABLE = ${ITEM_TABLE_TEXT};

	/**
	 * 物品菜单项（惰性构建，构建一次后缓存）。
	 *
	 * 必须返回**数组**：编辑器侧 getInfo() 要经 postMessage 结构化克隆，函数不能克隆。
	 */
	function itemMenuItems() {
		if (!itemMenuItems.cache) {
			itemMenuItems.cache = ITEM_TABLE.map((row) => {
				const parts = row.split('|');
				return {text: T(parts[2], parts[1]), value: 'minecraft:' + parts[0]};
			});
		}
		return itemMenuItems.cache;
	}

	class TurboMC {
		getInfo() {
			return {
				// 扩展 id 决定 opcode 前缀：注册为 turbomc，积木在 project.json 里就是
				// turbomc_<opcode>。改这里等于让所有已导出的工程失效。
				id: ${q(DATA.id)},
				name: T(${q(DATA.name.en)}, ${q(DATA.name.zh)}),
				color1: ${q(DATA.color1)},
				blocks: [
${emitBlocks('editor')}
				],
				menus: {
${emitMenus('editor')}
				}
			};
		}

		// ---- 浏览器占位实现：只打印，不做任何事；游戏内会被整体替换 ----
${emitStubs()}

		preview(name) {
			console.log('[turbomc] 预览模式（浏览器内无实际效果）:', name);
		}
	}

	Scratch.extensions.register(new TurboMC());
})(Scratch);
`;
}

function renderRuntime() {
	return `/*
 * 本文件由 tools/build-extension.js 生成，不要手改。
 * 要改积木或菜单，请改 turbowarp/src/blocks.data.js（物品表在 items.data.js），然后重新生成。
 *
 * 运行时副本：给内嵌 Rhino 用。刻意与编辑器副本有四处不同——
 *   1. 纯 ES5（无 class / const / 箭头函数），不经过 Babel；
 *   2. 不带分组标题（内嵌 vm.es5.js 不支持 label，带上会在注册阶段出问题）；
 *   3. 大菜单的 items 写成**函数引用**（注册期不展开 1505 项，初始化快约 1.6 秒）；
 *   4. 不带占位方法：vm-bootstrap 在注册扩展之前就把每个 opcode 的实现装到本对象上。
 */
(function (Scratch) {
	'use strict';

	var lang = 'en';
	try {
		lang = (navigator.language || 'en').toLowerCase();
	} catch (e) {
		lang = 'en';
	}
	var ZH = lang.indexOf('zh') === 0;

	function T(en, zh) {
		return ZH ? zh : en;
	}

var ITEM_TABLE = ${ITEM_TABLE_TEXT};

	function itemMenuItems() {
		if (!itemMenuItems.cache) {
			itemMenuItems.cache = ITEM_TABLE.map(function (row) {
				var parts = row.split('|');
				return {text: T(parts[2], parts[1]), value: 'minecraft:' + parts[0]};
			});
		}
		return itemMenuItems.cache;
	}

	var extension = {
		getInfo: function () {
			return {
				id: ${q(DATA.id)},
				name: T(${q(DATA.name.en)}, ${q(DATA.name.zh)}),
				color1: ${q(DATA.color1)},
				blocks: [
${emitBlocks('runtime')}
				],
				menus: {
${emitMenus('runtime')}
				}
			};
		}
	};

	Scratch.extensions.register(extension);
})(Scratch);
`;
}

function main() {
	const check = process.argv.includes('--check');
	const rendered = {editor: renderEditor(), runtime: renderRuntime()};
	let stale = 0;

	for (const [target, file] of Object.entries(OUTPUTS)) {
		const next = rendered[target];
		const exists = fs.existsSync(file);
		const current = exists ? fs.readFileSync(file, 'utf8') : '';
		if (current === next) {
			console.log('[build-extension] ' + target + ' 已是最新');
			continue;
		}
		stale++;
		if (check) {
			const currentLines = current.split('\n').length;
			const nextLines = next.split('\n').length;
			console.error('[build-extension] ✗ ' + path.relative(ROOT, file) + ' 与数据源不一致（'
				+ currentLines + ' → ' + nextLines + ' 行），运行 node tools/build-extension.js 重新生成');
			continue;
		}
		fs.writeFileSync(file, next);
		console.log('[build-extension] 写入 ' + path.relative(ROOT, file) + '（' + next.split('\n').length + ' 行）');
	}

	if (check && stale > 0) {
		process.exit(1);
	}
}

main();
