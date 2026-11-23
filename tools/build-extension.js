#!/usr/bin/env node
/*
 * 从单一数据源渲染 TurboMC 扩展的产物：
 *
 *   turbowarp/src/blocks.data.js  +  turbowarp/src/items.data.js
 *            │
 *            ├─▶ turbowarp/turbomc-extension.js                    （编辑器副本，ES6，带占位方法与分组标题）
 *            └─▶ src/client/resources/assets/turbomc/vm/turbomc-extension.es5.js （运行时副本，ES5，无占位方法与标题）
 *
 *   turbowarp/src/json-lite.js
 *            ├─▶ 内联进编辑器副本（JSON 积木在浏览器本地求值）
 *            └─▶ 原样复制为 src/client/resources/assets/turbomc/vm/json-core.es5.js（游戏内同一份实现）
 *
 *   编辑器副本的读数表（READERS）同样从这里生成：blocks.data.js 每个 reporter/boolean 的
 *   `read` 字段序列化进 remote-client.js 的 `/* @tmc-readers *\/` 注入点。
 *   投影逻辑只允许写一处——以前它是一份手写的 39 条表，漏了不报错，浏览器里恒返回空值。
 *
 * 两份扩展产物的差异只有四处，其它内容必须逐字一致：
 *   1. 语法：编辑器是 class，运行时是普通对象（Rhino 里不需要 class，也就省掉 Babel 中间层）；
 *   2. 分组标题：只有编辑器渲染（内嵌 vm.es5.js 不支持 label）；
 *   3. 菜单 items：编辑器内联数组（Worker 要结构化克隆），运行时用函数引用（省 1.6 秒初始化）；
 *   4. 占位方法：只有编辑器生成（游戏内由 vm-bootstrap 注入真实调用）。
 *      例外：JSON 一族是纯计算，编辑器也不生成 __tmc 占位，直接调本地实现——
 *      否则「未连接 → fallback」会把它们短路成假值。
 *
 * 用法：
 *   node tools/build-extension.js           写入产物
 *   node tools/build-extension.js --check   只校验产物是否过期（CI 用，过期退出非零）
 */

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ROOT = path.resolve(__dirname, '..');
const DATA = require(path.join(ROOT, 'turbowarp/src/blocks.data.js'));
const ITEMS = require(path.join(ROOT, 'turbowarp/src/items.data.js'));
/** 远程客户端：真实源文件，构建时拼进编辑器产物（见下方 installRemote 调用）。 */
const REMOTE_CLIENT = fs.readFileSync(path.join(ROOT, 'turbowarp/src/remote-client.js'), 'utf8').trim();
/**
 * JSON 积木的唯一实现（ES5，浏览器与 Rhino 共用）。
 *
 * <p>reporter 是同步求值，浏览器不可能为了一次读数等 RPC；游戏内则直接在本机解释执行。
 * 两个宿主用同一份源码：编辑器产物内联，运行时原样复制为 json-core.es5.js。
 * 行为基线由 tools/json-parity-golden.json 锁住（见 check-json-parity.js）。</p>
 */
const JSON_LITE = fs.readFileSync(path.join(ROOT, 'turbowarp/src/json-lite.js'), 'utf8').trim();

/**
 * JSON 积木的键集合（裸 opcode），构建期执行源码取得。
 *
 * <p>不读文本猜键：这份实现是唯一事实源，生成器必须与它逐键对齐。
 * 只在生成器里硬编码一份清单的话，新增一块积木时产物会有积木、本地却没实现，
 * 表现是「未连接返回空、连接后仍返回空」——既不报错也不好查。</p>
 */
const JSON_BLOCK_KEYS = (() => {
	const sandbox = {};
	vm.createContext(sandbox);
	vm.runInContext(JSON_LITE + '\nthis.createJsonLite = createJsonLite;', sandbox);
	return Object.keys(sandbox.createJsonLite().blocks);
})();

/**
 * 读写浏览器 VM 列表的两块：它们要碰 runtime 里的列表对象，不属于「纯计算」。
 *
 * <p>这两块仍走 {@code __tmc} 的本地分支（见 remote-client.js），
 * 其余 27 块在构建期就生成直调，运行期不经过 {@code __tmc}，与连接状态无关。</p>
 */
const VM_LIST_OPS = new Set(['jsonVmGetList', 'jsonVmSetList']);

/**
 * 断言数据源与 json-lite.js 的 JSON 积木集合一致。
 *
 * <p>两个方向都要查：数据源多出一块（产物有积木、本地没实现）会静默返回空值；
 * 实现多出一块（本地有实现、产物没积木）是死代码，通常是改了名没同步。</p>
 */
function assertJsonCoverage() {
	const fromData = DATA.blocks
		.filter((block) => block.opcode && block.opcode.indexOf('json') === 0 && !VM_LIST_OPS.has(block.opcode))
		.map((block) => block.opcode);
	const fromLite = JSON_BLOCK_KEYS.filter((key) => !VM_LIST_OPS.has(key));
	const missing = fromData.filter((key) => fromLite.indexOf(key) < 0);
	const extra = fromLite.filter((key) => fromData.indexOf(key) < 0);
	if (missing.length || extra.length) {
		throw new Error('[build-extension] JSON 积木与 json-lite.js 不一致'
			+ (missing.length ? '；数据源缺实现：' + missing.join(', ') : '')
			+ (extra.length ? '；实现无人引用：' + extra.join(', ') : ''));
	}
}
/**
 * 快照顶层字段集合：从 SnapshotPublisher.java 的 {@code snap.addProperty("x", …)} /
 * {@code snap.add("x", …)} 提取。
 *
 * <p>只取顶层：`aim.block` 这种二级路径由快照对象自己保证，构建期不追进去——
 * 追进去就要解析 Java 的嵌套方法，容易假失败。一级已经能挡住「改了快照字段名忘了跟」。</p>
 */
function snapshotTopLevelKeys() {
	const file = path.join(ROOT,
		'src/client/java/xyz/turbomc/client/script/remote/SnapshotPublisher.java');
	const java = fs.readFileSync(file, 'utf8');
	const keys = new Set();
	for (const m of java.matchAll(/snap\.add(?:Property)?\("(\w+)"/g)) {
		keys.add(m[1]);
	}
	if (keys.size === 0) {
		throw new Error('[build-extension] 从 SnapshotPublisher.java 解析不出快照字段：'
			+ '提取规则失效，校验会静默变成空转');
	}
	return keys;
}

/** read 函数源码里对 {@code s.<key>} 的引用；read 的第一参数固定叫 s。 */
function readFieldRefs(source) {
	const out = new Set();
	for (const m of String(source).matchAll(/\bs\.(\w+)/g)) {
		out.add(m[1]);
	}
	return out;
}

/**
 * 生成浏览器侧的读数表。
 *
 * <p>投影逻辑写在 blocks.data.js 的 {@code read} 字段里，这里只做序列化——
 * remote-client.js 不再维护第二份手写表。迁移前那份表有 39 条、153 行，
 * 且没有任何校验：漏一条表现为「浏览器里恒返回空值」，构建和 CI 都是绿的。</p>
 */
function emitReaders() {
	const rows = [];
	for (const block of DATA.blocks) {
		if (!block.opcode || typeof block.read !== 'function') {
			continue;
		}
		rows.push('\t\t' + q(DATA.id + '_' + block.opcode) + ': ' + block.read.toString() + ',');
	}
	return 'const READERS = {\n' + rows.join('\n') + '\n\t};';
}

/** 把生成的读数表填进 remote-client.js 的注入点。 */
function withReaders(source) {
	const marker = '/* @tmc-readers */';
	if (source.indexOf(marker) < 0) {
		throw new Error('[build-extension] remote-client.js 缺少 ' + marker + ' 注入点');
	}
	return source.replace(marker, emitReaders());
}

/**
 * 读数声明的双向校验。
 *
 * <p>三条规则：</p>
 * <ol>
 *   <li>每个 reporter / boolean 必须能回答——有 {@code read}、标了 {@code unsupported}，
 *       或者由本地实现兜底（JSON 纯计算与读写 VM 列表）。漏了直接失败；</li>
 *   <li>{@code read} 里引用的 {@code s.<字段>} 必须真实存在于快照；</li>
 *   <li>{@code unsupported} 不能同时带 {@code read}——那种自相矛盾会让构建期跳过、
 *       运行期又有实现，两个信号互相掩盖。</li>
 * </ol>
 */
function assertReads() {
	const keys = snapshotTopLevelKeys();
	const missing = [];
	const badRefs = [];
	const contradictory = [];
	for (const block of DATA.blocks) {
		if (!block.opcode) {
			continue;
		}
		const kind = String(block.blockType || '').toLowerCase();
		const hasRead = typeof block.read === 'function';
		if (hasRead && block.unsupported) {
			contradictory.push(block.opcode);
		}
		if (kind !== 'reporter' && kind !== 'boolean') {
			continue;
		}
		if (!hasRead && !block.unsupported
			&& JSON_BLOCK_KEYS.indexOf(block.opcode) < 0
			&& !VM_LIST_OPS.has(block.opcode)) {
			missing.push(block.opcode);
		}
		if (hasRead) {
			for (const field of readFieldRefs(block.read.toString())) {
				if (!keys.has(field)) {
					badRefs.push(block.opcode + ' → s.' + field);
				}
			}
		}
	}
	const problems = [];
	if (missing.length) {
		problems.push('缺少 read 声明（浏览器侧会恒返回空值）：' + missing.join(', '));
	}
	if (badRefs.length) {
		problems.push('read 引用了快照里不存在的字段：' + badRefs.join('; '));
	}
	if (contradictory.length) {
		problems.push('同时标了 unsupported 与 read：' + contradictory.join(', '));
	}
	if (problems.length) {
		throw new Error('[build-extension] 读数声明不一致\n  - ' + problems.join('\n  - '));
	}
}

/**
 * 画面：浏览器原生 getDisplayMedia + `<video>` 覆盖舞台（§10）。
 * 与 JSON 一样只进编辑器产物——游戏端不参与画面，这是设计而非省略。
 */
const STAGE_VIEW = fs.readFileSync(path.join(ROOT, 'turbowarp/src/stage-view.js'), 'utf8').trim();

const OUTPUTS = {
	editor: path.join(ROOT, 'turbowarp/turbomc-extension.js'),
	runtime: path.join(ROOT, 'src/client/resources/assets/turbomc/vm/turbomc-extension.es5.js'),
};

/**
 * JSON 核心的运行时副本。源码本身就是 ES5，原样复制即可；
 * 单独成文件是因为它先于 vm-bootstrap.js 加载，且浏览器产物里是内联的同一段文本。
 */
const JSON_RUNTIME = path.join(ROOT, 'src/client/resources/assets/turbomc/vm/json-core.es5.js');

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

/**
 * 参数顺序以**中文文案**为准，写进产物的 {@code argOrder}。
 *
 * <p>为什么不能直接让 vm-bootstrap 从 text 里提：运行时 text 是 {@code T(en, zh)}，
 * 喊什么语言就按什么语言的占位符顺序传参。JSON 那批积木的中英文顺序本来就不一致
 * （如 {@code set [item] in [json]} 对 {@code 把 [json] 中 [item] 设为}），
 * 英文环境下实参会整体错位，而 Java 侧是按中文顺序读的。</p>
 */
function argOrder(block) {
	if (!block.text || !block.text.zh) {
		return [];
	}
	return [...block.text.zh.matchAll(/\[([A-Za-z0-9_]+)\]/g)].map((m) => m[1]);
}

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
		const order = argOrder(block);
		if (order.length) {
			lines.push('\t\t\t\t\t\targOrder: [' + order.map(q).join(', ') + '],');
		}
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

/**
 * 生成每个积木的方法体：统一转发到 {@code __tmc}。
 *
 * <p>以前的产物是「一块积木一个 preview 占位」，未连接时只能打印；
 * 现在所有积木都走同一个入口，由 {@code remote-client.js} 决定：
 * 未连接执行 fallback（即原来的预览），连接后 reporter 读快照缓存、command 走 RPC。</p>
 *
 * <p>参数顺序用 {@link #argOrder}（中文文案的占位符顺序），与远程协议的 {@code argv}
 * 直接对应；hat 积木不生成方法——它们不会被调用，是线程入口。</p>
 */
function emitStubs() {
	const stubs = [];
	for (const block of DATA.blocks) {
		if (!block.opcode) {
			continue;
		}
		if (block.blockType === 'hat') {
			// 帽子的方法体只有一件事：返回 true。
			// TurboWarp 里非 edge-activated 的扩展帽子，执行结果为空会被 retireThread——
			// 事件派发我们绕开了帽子本身，但「手动点帽子」走的是 VM 的 toggleScript，
			// 线程就停在帽子上，只有返回真值才会继续往下执行。
			stubs.push('\t\t' + block.opcode + '() { return true; }');
			continue;
		}
		const order = argOrder(block);
		const opcode = DATA.id + '_' + block.opcode;
		const argv = order.map((name) => 'args.' + name).join(', ');
		// 纯计算积木（JSON 一族）：构建期就生成直调，运行期不进 __tmc。
		// 它们不读写游戏状态，未连接时也必须算出正确结果——若走 __tmc，
		// 会被「未连接 → fallback」短路成 blocks.data.js 里的占位假值。
		// 读写 VM 列表的两块（jsonVmGetList / jsonVmSetList）需要 runtime，不在此列。
		if (!VM_LIST_OPS.has(block.opcode) && JSON_BLOCK_KEYS.indexOf(block.opcode) >= 0) {
			const isBoolean = String(block.blockType || '').toLowerCase() === 'boolean';
			stubs.push('\t\t' + block.opcode + '(args, util) { return __tmcJson('
				+ q(block.opcode) + ', [' + argv + '], ' + isBoolean + '); }');
			continue;
		}
		// 第二个参数 util 必须一并转交：`触发我的聊天` 要读 `util.thread.tmcChat`，
		// 那是 per-thread 的私有副本，全局槽读到的是「最后一条」（可能已经是别人的话）
		const params = 'args, util';
		let fallback;
		if (block.stub) {
			// 用普通 function 包住原文：stub 里的 args 由外层方法参数闭包提供
			fallback = 'function () { ' + oneLine(block.stub) + ' }';
		} else if (block.blockType === 'boolean') {
			fallback = "function () { return false; }";
		} else if (block.blockType === 'reporter') {
			fallback = "function () { return ''; }";
		} else {
			// command：未连接时什么都不做（原来的占位行为）
			fallback = 'null';
		}
		stubs.push('\t\t' + block.opcode + '(' + params + ') { return this.__tmc('
			+ q(opcode) + ', [' + argv + '], ' + q(block.blockType) + ', ' + fallback + ', util); }');
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
					// 远程调试入口：点一下打开连接面板（地址 / 端口 / 连接 / 断开）
					{blockType: Scratch.BlockType.BUTTON, text: T('Remote debug', '远程调试'), func: 'openRemotePanel'},
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

${JSON_LITE}

	/*
	 * 纯计算积木（JSON 一族）的本地入口：构建期为这 27 块生成直调，
	 * 运行期完全不经过 __tmc，因此与「是否已连接游戏」无关。
	 *
	 * <p>以前它们和其他积木共用 __tmc，而 __tmc 的第一句就是「未连接 → 返回 fallback」，
	 * 于是「导入扩展后、连接游戏前」JSON 全部返回占位假值（长度恒 0、取键恒空串、
	 * 新建数组得到 {}）。这里把它们挪到连接判断之外，未连接也能算出真实结果。</p>
	 *
	 * <p>查表只做一次：createJsonLite() 每次调用都会重建整张积木表，热路径上不能重复付这个代价。</p>
	 */
	let __tmcJsonBlocks = null;
	function __tmcJson(opcode, argv, isBoolean) {
		if (!__tmcJsonBlocks) {
			__tmcJsonBlocks = createJsonLite().blocks;
		}
		const fn = __tmcJsonBlocks[opcode];
		if (!fn) {
			return isBoolean ? false : '';
		}
		try {
			const out = fn(Array.isArray(argv) ? argv : []);
			// 布尔积木必须归一化成 JS 原始布尔：scratch 的 Cast.toBoolean 会把任意对象判真
			return isBoolean ? !!out : out;
		} catch (e) {
			return isBoolean ? false : '';
		}
	}

${STAGE_VIEW}

${withReaders(REMOTE_CLIENT)}

	// 注入远程能力后再注册：installRemote 会给实例挂上 __tmc 与 openRemotePanel。
	// 未连接时 __tmc 一律走 fallback，所以这份产物同时是「普通编辑器」与「远程调试器」。
	const extension = new TurboMC();
	installRemote(Scratch, extension, {id: ${q(DATA.id)}});
	Scratch.extensions.register(extension);
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
	assertJsonCoverage();
	assertReads();
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

	// JSON 核心：单一源码，运行时原样复制。
	{
		const next = JSON_LITE + '\n';
		const exists = fs.existsSync(JSON_RUNTIME);
		const current = exists ? fs.readFileSync(JSON_RUNTIME, 'utf8') : '';
		if (current === next) {
			console.log('[build-extension] json-core 已是最新');
		} else {
			stale++;
			if (check) {
				console.error('[build-extension] ✗ ' + path.relative(ROOT, JSON_RUNTIME)
					+ ' 与 turbowarp/src/json-lite.js 不一致，运行 node tools/build-extension.js 重新生成');
			} else {
				fs.writeFileSync(JSON_RUNTIME, next);
				console.log('[build-extension] 写入 ' + path.relative(ROOT, JSON_RUNTIME)
					+ '（' + next.split('\n').length + ' 行）');
			}
		}
	}

	if (check && stale > 0) {
		process.exit(1);
	}
}

main();
