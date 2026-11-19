#!/usr/bin/env node
/*
 * 生成产物的语义等价闸门：把「基线」与「新产物」分别加载，取 getInfo() 做规范化后深比较。
 *
 * 为什么要它：产物从「手写 + Babel」换成「生成 + ES5」，文本 diff 无法判断是否等价。
 * 只有把 getInfo() 的结构（积木顺序、文案、参数、菜单取值、帽子属性）逐项比出来，
 * 才敢说「换生成器没有改变行为」。
 *
 * 比较时抹平三处**设计上的**差异：
 *   1. 分组标题：运行时不渲染，跳过；
 *   2. 菜单 items：编辑器是数组、运行时可换成函数引用，统一展开成 value 列表；
 *   3. 语言：分别在中英两种语言下各比一遍。
 *
 * 用法：node tools/compare-getinfo.js <基线编辑器> <基线运行时>
 *       新产物固定读仓库里的两处路径。
 */

const fs = require('fs');
const vm = require('vm');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');

function load(file, lang) {
	let instance = null;
	const Scratch = {
		BlockType: {HAT: 'hat', COMMAND: 'command', REPORTER: 'reporter', BOOLEAN: 'boolean',
			BUTTON: 'button', LABEL: 'label'},
		ArgumentType: {STRING: 'string', NUMBER: 'number', BOOLEAN: 'boolean',
			ANGLE: 'angle', COLOR: 'color', NOTE: 'note'},
		TargetType: {SPRITE: 'sprite', STAGE: 'stage'},
		extensions: {register: (i) => { instance = i; }}
	};
	const sandbox = {
		Scratch,
		navigator: {language: lang},
		console,
		module: {exports: {}},
		require,
		Math,
		JSON,
		Object,
		Array,
		String,
		Number,
		Boolean,
		isNaN,
		Infinity,
		NaN,
	};
	sandbox.globalThis = sandbox;
	vm.runInContext(fs.readFileSync(file, 'utf8'), vm.createContext(sandbox), {filename: file});
	if (!instance) {
		throw new Error('未注册扩展：' + file);
	}
	return instance.getInfo();
}

function normalize(info, target) {
	const blocks = [];
	for (const block of info.blocks) {
		if (!block.opcode) {
			if (target === 'editor') {
				blocks.push({label: block.text});
			}
			continue;
		}
		const item = {
			opcode: block.opcode,
			blockType: String(block.blockType || '').toLowerCase(),
			text: block.text,
		};
		if (block.arguments && Object.keys(block.arguments).length) {
			item.arguments = {};
			for (const [name, arg] of Object.entries(block.arguments)) {
				item.arguments[name] = {
					type: arg.type,
					defaultValue: arg.defaultValue,
					menu: arg.menu,
				};
			}
		}
		if (block.isEdgeActivated !== undefined) {
			item.isEdgeActivated = block.isEdgeActivated;
		}
		blocks.push(item);
	}

	const menus = {};
	for (const [name, menu] of Object.entries(info.menus || {})) {
		let items = menu.items;
		if (typeof items === 'function') {
			items = items();
		}
		menus[name] = {
			acceptReporters: !!menu.acceptReporters,
			items: (items || []).map((item) => (typeof item === 'string' ? item : item.value)),
		};
	}

	return {id: info.id, name: info.name, color1: info.color1, blocks, menus};
}

function diff(label, a, b, problems) {
	if (typeof a !== typeof b || a === null || b === null) {
		if (a !== b) {
			problems.push(label + '：' + JSON.stringify(a) + ' ≠ ' + JSON.stringify(b));
		}
		return;
	}
	if (typeof a !== 'object') {
		if (a !== b) {
			problems.push(label + '：' + JSON.stringify(a) + ' ≠ ' + JSON.stringify(b));
		}
		return;
	}
	const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
	for (const key of keys) {
		diff(label + '.' + key, a[key], b[key], problems);
	}
}

function comparePair(name, baselineFile, newFile, lang) {
	const target = name;
	const before = normalize(load(baselineFile, lang), target);
	const after = normalize(load(newFile, lang), target);
	const problems = [];

	// 运行时侧的顺序没有语义：scratch-vm 按 opcode 建 primitive，面板顺序是编辑器的事。
	// 旧运行时是手工排的，与数据源顺序不同——这里按 opcode 排序后比较，只验证集合与内容。
	if (target === 'runtime') {
		before.blocks.sort((a, b) => String(a.opcode).localeCompare(String(b.opcode)));
		after.blocks.sort((a, b) => String(a.opcode).localeCompare(String(b.opcode)));
	}

	if (before.blocks.length !== after.blocks.length) {
		problems.push('积木条数 ' + before.blocks.length + ' → ' + after.blocks.length);
	}
	const count = Math.max(before.blocks.length, after.blocks.length);
	for (let i = 0; i < count; i++) {
		const label = 'blocks[' + i + ']';
		if (!before.blocks[i] || !after.blocks[i]) {
			problems.push(label + '：一侧缺失 ' + JSON.stringify(before.blocks[i] || after.blocks[i]));
			continue;
		}
		diff(label + (after.blocks[i].opcode || ''), before.blocks[i], after.blocks[i], problems);
		if (problems.length > 40) {
			break;
		}
	}
	diff('menus', before.menus, after.menus, problems);
	if (before.id !== after.id) {
		problems.push('id：' + before.id + ' ≠ ' + after.id);
	}

	const tag = '[compare] ' + name + ' · ' + lang + '：';
	if (problems.length === 0) {
		console.log(tag + '一致（' + after.blocks.length + ' 条积木/标题，菜单 ' + Object.keys(after.menus).length + ' 个）');
		return 0;
	}
	console.error(tag + problems.length + ' 处差异');
	problems.slice(0, 25).forEach((p) => console.error('  ✗ ' + p));
	return 1;
}

function main() {
	const baselineEditor = process.argv[2];
	const baselineRuntime = process.argv[3];
	if (!baselineEditor || !baselineRuntime) {
		console.error('用法：node tools/compare-getinfo.js <基线编辑器> <基线运行时>');
		process.exit(2);
	}
	const newEditor = path.join(ROOT, 'turbowarp/turbomc-extension.js');
	const newRuntime = path.join(ROOT, 'src/client/resources/assets/turbomc/vm/turbomc-extension.es5.js');

	let failed = 0;
	for (const lang of ['zh-CN', 'en-US']) {
		failed += comparePair('editor', baselineEditor, newEditor, lang);
		failed += comparePair('runtime', baselineRuntime, newRuntime, lang);
	}
	if (failed) {
		console.error('[compare] 失败：生成的产物与基线语义不一致');
		process.exit(1);
	}
	console.log('[compare] 通过：两份产物与基线语义完全一致');
}

main();
