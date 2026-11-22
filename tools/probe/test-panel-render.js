// 面板重建回归测试：快照不得重建 DOM；「断开」必须真的断开且不再重连。
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const FILE = path.resolve(__dirname, '../../turbowarp/turbomc-extension.js');
const code = fs.readFileSync(FILE, 'utf8');

let created = 0;
function makeEl(tag) {
	created++;
	return {
		tagName: tag,
		style: {},
		children: [],
		textContent: '',
		value: '',
		_handlers: {},
		appendChild(c) {
			this.children.push(c);
			c.parentElement = this;
			return c;
		},
		removeChild(c) {
			this.children = this.children.filter((x) => x !== c);
			return c;
		},
		setPointerCapture() {},
		removeEventListener(type, fn) {
			this._handlers[type] = (this._handlers[type] || []).filter((x) => x !== fn);
		},
		dispatch(type, event) {
			(this._handlers[type] || []).forEach((fn) => fn(event));
		},
		offsetWidth: 340,
		offsetHeight: 200,
		addEventListener(type, fn) {
			(this._handlers[type] = this._handlers[type] || []).push(fn);
		},
		click() {
			(this._handlers.click || []).forEach((fn) => fn());
		},
		getBoundingClientRect() {
			return {left: 0, top: 0, width: 480, height: 360};
		}
	};
}

const body = makeEl('body');
const store = {};

class FakeWS {
	constructor(url) {
		this.url = url;
		this.readyState = 1;
		this.sent = [];
		FakeWS.instances.push(this);
	}
	send(data) {
		this.sent.push(data);
	}
	close() {
		this.readyState = 3;
		this.closed = true;
	}
}
FakeWS.instances = [];

const S = {
	extensions: {unsandboxed: true, register(e) { S._ext = e; }},
	BlockType: {BUTTON: 'button', COMMAND: 'command', BOOLEAN: 'Boolean', REPORTER: 'reporter', LABEL: 'label', HAT: 'hat', EVENT: 'event'},
	ArgumentType: {STRING: 'string', NUMBER: 'number', BOOLEAN: 'boolean', ANGLE: 'angle', COLOR: 'color', NOTE: 'note'},
	vm: {runtime: {targets: [], threads: [], startHats() {}, _pushThread() {}}}
};

const ctx = {
	Scratch: S,
	document: {body, createElement: makeEl, getElementById: () => null},
	window: {top: {}, alert() {}, innerWidth: 1200, innerHeight: 800, addEventListener() {}},
	navigator: {language: 'zh-cn', userAgent: 'test'},
	localStorage: {getItem: (k) => (k in store ? store[k] : null), setItem: (k, v) => { store[k] = String(v); }},
	console: {log() {}, warn() {}, info() {}, error: console.error},
	performance: {now: () => Date.now()},
	setTimeout,
	clearTimeout,
	WebSocket: FakeWS,
	isSecureContext: true
};

vm.runInNewContext(code, ctx);

setTimeout(() => {
	const ext = S._ext;
	let failures = 0;
	const check = (ok, label, detail) => {
		console.log((ok ? 'PASS ' : 'FAIL ') + label + (detail === undefined ? '' : '  → ' + detail));
		if (!ok) failures++;
	};

	// 1) 打开面板，找到「连接」按钮并点击
	ext.openRemotePanel();
	const findButton = (el, label) => {
		if (!el) return null;
		if (el.tagName === 'button' && el.textContent === label) return el;
		for (const c of el.children || []) {
			const hit = findButton(c, label);
			if (hit) return hit;
		}
		return null;
	};
	const connectBtn = findButton(body, '连接');
	check(!!connectBtn, '面板出现「连接」按钮');
	connectBtn.click();
	const ws = FakeWS.instances[0];
	check(!!ws, '点击后创建了 WebSocket', ws && ws.url);

	// 2) 握手成功
	ws.onopen();
	ws.onmessage({data: JSON.stringify({
		t: 'welcome', instance: 'run', world: 'minecraft:overworld',
		opcodes: ext.getInfo().blocks.filter((b) => b.opcode).map((b) => b.opcode),
		hats: []
	})});
	check(!!findButton(body, '断开'), '握手后按钮变为「断开」');

	// 3) 快照不得重建 DOM
	const before = created;
	for (let i = 0; i < 20; i++) {
		ws.onmessage({data: JSON.stringify({t: 'snap', tick: i, x: 1, y: 64, z: 2, hp: 20})});
	}
	const grown = created - before;
	check(grown === 0, '收到 20 帧快照后未重建控件', grown === 0 ? '新增 0 个节点' : '新增 ' + grown + ' 个节点');

	// 4) 断开必须真的断开，且不再重连
	const discBtn = findButton(body, '断开');
	discBtn.click();
	check(ws.closed === true, '点「断开」后 WebSocket 已关闭');

	// 5) 面板可拖动：遮住积木区时唯一的挪开办法，位置要记住
	const findText = (el, text) => {
		if (!el) return null;
		if (el.textContent === text) return el;
		for (const c of el.children || []) {
			const hit = findText(c, text);
			if (hit) return hit;
		}
		return null;
	};
	const titleSpan = findText(body, 'TurboMC 远程调试');
	const head = titleSpan && titleSpan.parentElement;
	const panel = head && head.parentElement;
	check(!!head && !!panel, '找到面板标题栏');
	head.dispatch('pointerdown', {target: head, button: 0, clientX: 100, clientY: 100, pointerId: 1});
	head.dispatch('pointermove', {clientX: 160, clientY: 140});
	head.dispatch('pointerup', {});
	check(panel.style.left === '60px' && panel.style.top === '40px',
		'拖动标题栏后面板移动', panel.style.left + ',' + panel.style.top);
	const storedRaw = Object.values(store).find((v) => String(v).indexOf('"address"') >= 0) || '{}';
	const stored = JSON.parse(storedRaw);
	check(stored.panelX === 60 && stored.panelY === 40, '拖动位置写进 localStorage',
		'panelX=' + stored.panelX + ' panelY=' + stored.panelY);

	head.dispatch('dblclick', {});
	check(panel.style.left === '12px' && panel.style.top === 'auto' && panel.style.bottom === '12px',
		'双击标题栏回到左下角', panel.style.left + ',' + panel.style.top);

	setTimeout(() => {
		check(FakeWS.instances.length === 1, '3.5 秒后没有自动重连', 'WS 实例数=' + FakeWS.instances.length);
		check(!!findButton(body, '连接'), '断开后按钮回到「连接」');
		console.log(failures === 0 ? '\n全部通过' : '\n失败 ' + failures + ' 项');
		process.exit(failures === 0 ? 0 : 1);
	}, 3500);
}, 150);
