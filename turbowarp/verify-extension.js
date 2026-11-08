/*
 * 编辑器侧扩展自检：在 Node 里加载 turbomc-extension.js，模拟 TurboWarp 的加载契约。
 *
 * 为什么需要它：扩展在 TurboWarp 里跑在 Worker 中，`getInfo()` 的返回值要经
 * `postMessage` 做结构化克隆。只要里面混进函数（例如菜单 items 写成函数引用），
 * 编辑器就会抛 `DataCloneError: function ... could not be cloned`；
 * 而菜单写成「方法名字符串」又会因为服务对象上没有实例方法而抛
 * `Cannot read properties of undefined (reading 'call')`。
 * 这两类错误在模组侧冒烟测试里都发现不了，只能靠这个脚本兜住。
 *
 * 用法：node verify-extension.js [路径，默认同目录的 turbomc-extension.js]
 * 退出码 0 表示通过。
 */

const path = require('path');

const target = process.argv[2] || path.join(__dirname, 'turbomc-extension.js');

global.navigator = {language: 'zh-CN'};
let instance = null;
global.Scratch = {
	BlockType: {HAT: 'hat', COMMAND: 'command', REPORTER: 'reporter', BOOLEAN: 'boolean',
		BUTTON: 'button', LABEL: 'label'},
	ArgumentType: {STRING: 'string', NUMBER: 'number', BOOLEAN: 'boolean',
		ANGLE: 'angle', COLOR: 'color', NOTE: 'note'},
	TargetType: {SPRITE: 'sprite', STAGE: 'stage'},
	extensions: {register: (i) => { instance = i; }}
};

require(path.resolve(target));

const problems = [];
const info = instance.getInfo();
if (!info || !Array.isArray(info.blocks)) {
	console.error('getInfo() 没有返回 blocks');
	process.exit(1);
}

const menus = info.menus || {};
for (const [name, menu] of Object.entries(menus)) {
	const items = menu && menu.items;
	if (typeof items === 'function') {
		problems.push(`菜单 ${name} 的 items 是函数：编辑器 Worker 无法克隆`);
		continue;
	}
	if (!Array.isArray(items)) {
		problems.push(`菜单 ${name} 的 items 既不是数组也不是函数（${typeof items}）`);
		continue;
	}
	const bad = items.filter((it) => !it || typeof it.text !== 'string' || typeof it.value !== 'string');
	if (bad.length) {
		problems.push(`菜单 ${name} 有 ${bad.length} 项缺 text/value`);
	}
	if (new Set(items.map((it) => it.value)).size !== items.length) {
		problems.push(`菜单 ${name} 有重复的 value`);
	}
}

for (const block of info.blocks) {
	const args = block.arguments || {};
	for (const [argName, arg] of Object.entries(args)) {
		if (arg && arg.menu && !menus[arg.menu]) {
			problems.push(`积木 ${block.opcode} 的参数 ${argName} 引用了不存在的菜单 ${arg.menu}`);
		}
	}
}

try {
	structuredClone(info);
} catch (e) {
	problems.push('getInfo() 结果无法结构化克隆：' + e.message);
}

const total = Object.values(menus).reduce((n, m) => n + (Array.isArray(m.items) ? m.items.length : 0), 0);
console.log(`[verify] 积木 ${info.blocks.length} 个，菜单 ${Object.keys(menus).length} 个，菜单项 ${total} 条`);
if (problems.length) {
	problems.forEach((p) => console.error('[verify] ✗ ' + p));
	process.exit(1);
}
console.log('[verify] 通过');
