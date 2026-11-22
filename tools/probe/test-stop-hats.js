// 批次 7 回归：事件帽子派发 + 停止链路。
// 覆盖三件事：① 关键词字面量必须能读出来（否则帽子全匹配）
// ② screen_open 要发两次（具体类别 + any）③ 点红停止要把 stop 上送并清掉挂起的 Promise
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
		appendChild(c) { this.children.push(c); c.parentElement = this; return c; },
		removeChild(c) { this.children = this.children.filter((x) => x !== c); return c; },
		addEventListener(type, fn) { (this._handlers[type] = this._handlers[type] || []).push(fn); },
		click() { (this._handlers.click || []).forEach((fn) => fn()); }
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
	send(data) { this.sent.push(data); }
	close() { this.readyState = 3; this.closed = true; }
}
FakeWS.instances = [];

// 一个带聊天帽子与界面帽子的角色。每顶帽子后面都挂了一块 body：
// 线程必须起在 body 上，绝不能起在帽子块上（否则会执行帽子并退役）
const chatHat = {
	opcode: 'turbomc_whenChat',
	topLevel: true,
	next: 'bodyChat',
	// 运行期形态：block 与 shadow 指向同一块替身，才是字面量
	inputs: {MATCH: {block: 'shadow1', shadow: 'shadow1'}}
};
const screenHat = {opcode: 'turbomc_whenScreenOpen', topLevel: true, next: 'bodyScreen', fields: {SCREEN: {value: 'container'}}};
const screenAnyHat = {opcode: 'turbomc_whenScreenOpen', topLevel: true, next: 'bodyAny', fields: {SCREEN: {value: 'any'}}};
const damageHat = {opcode: 'turbomc_whenDamaged', topLevel: true, next: 'bodyDamage', fields: {}};

const startedHats = [];
const pushed = [];
const advanced = [];
const stopHandlers = [];

const runtime = {
	targets: [
		{
			isStage: true,
			variables: {},
			blocks: {_blocks: {}},
			comments: {}
		},
		{
			isStage: false,
			variables: {list1: {name: '我的列表', type: 'list', value: ['a', 'b']}},
			blocks: {
				_blocks: {
					hat1: chatHat,
					shadow1: {opcode: 'text', fields: {TEXT: {value: '你好'}}},
					hat2: screenHat,
					hat4: screenAnyHat,
					hat3: damageHat,
					bodyChat: {opcode: 'looks_say'},
					bodyScreen: {opcode: 'looks_say'},
					bodyAny: {opcode: 'looks_say'},
					bodyDamage: {opcode: 'looks_say'}
				}
			}
		}
	],
	threads: [],
	on(event, fn) { if (event === 'PROJECT_STOP_ALL') stopHandlers.push(fn); },
	startHats(opcode, fields) { startedHats.push({opcode, fields}); return []; },
	_pushThread(blockId, target) {
		pushed.push(blockId);
		// 真实 VM 的 Thread 有 goToNextBlock；扩展必须调用它，否则线程停在帽子上退役
		return {blockId, target, goToNextBlock() { advanced.push(blockId); }};
	}
};

const S = {
	extensions: {unsandboxed: true, register(e) { S._ext = e; }},
	BlockType: {BUTTON: 'button', COMMAND: 'command', BOOLEAN: 'Boolean', REPORTER: 'reporter', LABEL: 'label', HAT: 'hat', EVENT: 'event'},
	ArgumentType: {STRING: 'string', NUMBER: 'number', BOOLEAN: 'boolean', ANGLE: 'angle', COLOR: 'color', NOTE: 'note'},
	vm: {runtime}
};

const ctx = {
	Scratch: S,
	document: {body, createElement: makeEl, getElementById: () => null},
	window: {top: {}, alert() {}},
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

	// 连接
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
	findButton(body, '连接').click();
	const ws = FakeWS.instances[0];
	ws.onopen();
	ws.onmessage({
		data: JSON.stringify({
			t: 'welcome', instance: 'run', world: 'minecraft:overworld',
			opcodes: [],
			hats: [
				{ev: 'chat', op: 'turbomc_whenChat', match: 'input:MATCH', contains: true},
				{ev: 'screen_open', op: 'turbomc_whenScreenOpen', match: 'field:SCREEN', alsoAny: true},
				{ev: 'damage', op: 'turbomc_whenDamaged'}
			],
			snapshotKeys: {player: ['name', 'uuid'], world: ['time'], item: ['slot', 'id']}
		})
	});

	// ① 关键词「你好」应当只命中含「你好」的聊天，且线程起在 body 上而非帽子块
	ws.onmessage({data: JSON.stringify({t: 'event', name: 'chat', data: {text: '你好呀'}})});
	check(pushed.length === 1, '聊天含关键词触发一次', 'pushThread=' + pushed.length);
	check(pushed[0] === 'bodyChat', '线程起在帽子下一块（不碰帽子本体）', JSON.stringify(pushed));

	pushed.length = 0;
	ws.onmessage({data: JSON.stringify({t: 'event', name: 'chat', data: {text: '随便说说'}})});
	check(pushed.length === 0, '不含关键词不触发', 'pushThread=' + pushed.length);

	// ② screen_open：具体类别命中「container」与「any」两顶帽子
	pushed.length = 0;
	ws.onmessage({data: JSON.stringify({t: 'event', name: 'screen_open', data: {screen: 'container'}})});
	check(pushed.length === 2 && pushed.indexOf('hat2') < 0 && pushed.indexOf('hat4') < 0,
		'界面事件命中两顶帽子且都不碰帽子块', JSON.stringify(pushed));
	check(startedHats.length === 0, '不再走 runtime.startHats（会先执行帽子并退役）', 'startHats=' + startedHats.length);

	// ③ 无参数帽子
	pushed.length = 0;
	ws.onmessage({data: JSON.stringify({t: 'event', name: 'damage', data: {hp: 12}})});
	check(pushed.length === 1 && pushed[0] === 'bodyDamage', '受伤帽子触发', JSON.stringify(pushed));

	// ⑤ VM 列表互通：读 / 写都走浏览器本地
	const listJson = ext.__tmc('turbomc_jsonVmGetList', ['我的列表'], 'reporter');
	check(listJson === '["a","b"]', '读列表得到 JSON 数组', listJson);
	const missing = ext.__tmc('turbomc_jsonVmGetList', ['不存在'], 'reporter');
	check(missing === '', '列表不存在给空串（区别于存在但为空）', JSON.stringify(missing));
	ext.__tmc('turbomc_jsonVmSetList', ['我的列表', '[1,{"k":2}]'], 'command');
	check(JSON.stringify(runtime.targets[1].variables.list1.value) === '[1,"{\\"k\\":2}"]',
		'写列表：对象转成 JSON 文本', JSON.stringify(runtime.targets[1].variables.list1.value));

	// ⑥ JSON 一族走本地计算，不应进入 RPC
	const sentBeforeJson = ws.sent.length;
	const len = ext.__tmc('turbomc_jsonLength', ['[1,2,3]'], 'reporter');
	check(len === 3, 'JSON 长度在浏览器本地算出', 'len=' + len);
	const got = ext.__tmc('turbomc_jsonGet', ['{"a":{"b":7}}', 'a'], 'reporter');
	check(got === '{"b":7}', 'JSON 取值走本地实现', 'v=' + got);
	check(ws.sent.length === sentBeforeJson, 'JSON 读数没有产生 RPC', 'sent+' + (ws.sent.length - sentBeforeJson));

	// ④ 状态类读数：全部来自快照，不得产生 RPC
	const snapMsg = {
		t: 'snap', schema: 3, tick: 5, x: 1, y: 2, z: 3, hp: 20, food: 18, xpLevel: 3,
		dim: 'minecraft:overworld', slot: 1,
		hand: {id: 'minecraft:iron_pickaxe', count: 1, maxDurability: 250, durability: 80},
		aim: {x: 1, y: 2, z: 3, block: 'minecraft:stone', has: true},
		onGround: true, isDay: false, biome: 'minecraft:plains', inWater: false, inLava: true,
		below: 'minecraft:dirt', mining: {active: true, progress: 33},
		mouse: {left: true, right: false, middle: false}, ping: 42,
		inv: [
			{slot: 0, id: 'minecraft:stone', count: 64, damage: 0, max_damage: 0, custom_name: '', enchantments: {}},
			{slot: 1, id: 'minecraft:diamond_pickaxe', count: 1, damage: 10, max_damage: 1561, custom_name: '', enchantments: {}}
		],
		ui: {
			type: 'minecraft:chest', slots: 63, items: [
				{slot: 0, id: 'minecraft:stone', count: 32, damage: 0, max_damage: 0, custom_name: '', enchantments: {}, player_slot: false},
				{slot: 27, id: 'minecraft:apple', count: 3, damage: 0, max_damage: 0, custom_name: '', enchantments: {}, player_slot: true}
			]
		},
		me: {name: 'Steve', uuid: 'u-1', health: 20, max_health: 20},
		worldInfo: {time: 1000, day: 3, moon_phase: 0, raining: false, thundering: false, dimension: 'minecraft:overworld', biome: 'minecraft:plains', light: 15},
		players: [
			{uuid: 'u-1', name: 'Steve', latency: 42, gamemode: 'survival'},
			{uuid: 'u-2', name: 'Alex', latency: 30, gamemode: 'creative'}
		],
		chat: {last: '你好呀', history: ['随便说说', '你好呀']},
		ai: {default: 'AI 回复内容'},
		ents: [
			{type: 'minecraft:zombie', name: 'Zombie', custom_name: '', uuid: 'e-1', distance: 2,
				x: 3, y: 2, z: 3, motion_x: 0, motion_y: 0, motion_z: 0,
				health: 20, max_health: 20, is_baby: false, on_ground: true},
			{type: 'minecraft:pig', name: 'Pig', custom_name: '', uuid: 'e-2', distance: 7.07,
				x: 8, y: 3, z: 3, motion_x: 0, motion_y: 0, motion_z: 0,
				health: 10, max_health: 10, is_baby: false, on_ground: true}
		],
		hostile: ['e-1']
	};
	ws.onmessage({data: JSON.stringify(snapMsg)});
	const sentBeforeSnapReaders = ws.sent.length;
	const read = (op, args) => ext.__tmc(op, args || [], 'reporter');
	check(read('turbomc_onGround') === true, 'onGround 读快照');
	check(read('turbomc_isDay') === false, 'isDay 读快照');
	check(read('turbomc_biome') === 'minecraft:plains', 'biome 读快照');
	check(read('turbomc_inLava') === true && read('turbomc_inWater') === false, '液体状态读快照');
	check(read('turbomc_isMining') === true && read('turbomc_miningProgress') === 33, '挖掘状态与进度');
	check(read('turbomc_blockInFront') === true && read('turbomc_frontBlock') === 'minecraft:stone', '准星方块');
	check(read('turbomc_blockBelow') === 'minecraft:dirt', '脚下方块');
	check(read('turbomc_ping') === 42, '延迟读快照');
	check(read('turbomc_mouseDown', ['left']) === true && read('turbomc_mouseDown', ['middle']) === false,
		'鼠标按键');
	check(read('turbomc_mouseDown', ['any']) === true, '鼠标 any 合并三键');
	check(read('turbomc_heldItemField', ['durability']) === 80
		&& read('turbomc_heldItemField', ['count']) === 1
		&& read('turbomc_heldItemField', ['max_durability']) === 250
		&& read('turbomc_heldItemField', ['id']) === 'minecraft:iron_pickaxe', '手持字段四档');

	// 背包与容器：快照里的 inv / ui 就是游戏内那两个快照积木的原样形态
	check(read('turbomc_inventoryJson') === JSON.stringify(snapMsg.inv), '背包快照逐字一致');
	check(read('turbomc_screenJson') === JSON.stringify(snapMsg.ui), '界面快照逐字一致');
	check(read('turbomc_itemCount', ['stone']) === 64, '物品计数（短名）');
	check(read('turbomc_itemCount', ['minecraft:diamond_pickaxe']) === 1, '物品计数（注册名）');
	check(read('turbomc_inventoryFull') === false, '背包未满');
	check(read('turbomc_containerSlots') === 63, '容器槽位数');
	check(read('turbomc_slotField', [0, 'amount']) === 32
		&& read('turbomc_slotField', [27, 'id']) === 'minecraft:apple'
		&& read('turbomc_slotField', [5, 'amount']) === 0, '槽位字段（含空格）');
	check(read('turbomc_firstSlotOf', ['minecraft:apple']) === 27
		&& read('turbomc_firstSlotOf', ['dirt']) === -1, '首个槽位');

	// 整体快照与键清单
	check(read('turbomc_playerJson') === JSON.stringify(snapMsg.me), '玩家快照逐字一致');
	check(read('turbomc_worldJson') === JSON.stringify(snapMsg.worldInfo), '世界快照逐字一致');
	check(read('turbomc_playersJson') === JSON.stringify(snapMsg.players), '在线玩家快照逐字一致');
	check(read('turbomc_snapshotKeys', ['player']) === '["name","uuid"]', '键清单来自握手');
	check(read('turbomc_snapshotKeys', ['不存在']) === '[]', '未知类型给空数组');

	// 聊天与 AI
	check(read('turbomc_lastChat') === '你好呀', '最近一条聊天');
	check(read('turbomc_chatHistory', [1]) === '["你好呀"]', '聊天历史取最近 N 条');
	check(read('turbomc_chatHistory', [10]) === JSON.stringify(snapMsg.chat.history), '历史条数不足时全给');
	check(read('turbomc_aiReply', ['default']) === 'AI 回复内容', 'AI 回复按会话查');
	check(read('turbomc_aiReply', ['nope']) === '', '不存在的会话给空串');
	// 「触发我的聊天」读的是 util.thread 上的私有副本
	check(ext.__tmc('turbomc_triggerChat', [], 'reporter', null, {thread: {tmcChat: '这条是我的'}}) === '这条是我的',
		'触发我的聊天读线程副本');
	check(ext.__tmc('turbomc_triggerChat', [], 'reporter', null, {thread: {}} ) === '',
		'非聊天线程给空串');

	// 附近实体：快照按距离升序，三类过滤与半径/数量都在客户端筛
	check(read('turbomc_entityCount', ['any']) === 2, '附近实体计数 any');
	check(read('turbomc_entityCount', ['hostile']) === 1, '附近实体计数 hostile');
	check(read('turbomc_entityCount', ['zombie']) === 1, '附近实体计数 短名');
	check(read('turbomc_entityCount', ['minecraft:pig']) === 1, '附近实体计数 注册名');
	check(read('turbomc_nearestEntityField', ['any', 'name']) === 'Zombie', '最近实体名称');
	check(read('turbomc_nearestEntityField', ['any', 'distance']) === 2, '最近实体距离');
	check(read('turbomc_nearestEntityField', ['player', 'distance']) === -1, '无匹配给 -1');
	check(read('turbomc_entitiesJson', ['any', 16, 1]) === JSON.stringify([snapMsg.ents[0]]),
		'实体快照数量截断');
	check(read('turbomc_entitiesJson', ['any', 16, 10]) === JSON.stringify(snapMsg.ents),
		'实体快照逐字一致');
	check(read('turbomc_entitiesJson', ['hostile', 16, 10]) === JSON.stringify([snapMsg.ents[0]]),
		'实体快照按 hostile 过滤');

	// 画面：stage-view 已注入，面板上应有两个按钮（真实捕获要人工点，自动化测不了）
	check(!!findButton(body, '开始捕获'), '面板出现「开始捕获」按钮');
	check(!!findButton(body, '切到舞台'), '面板出现「切到舞台」按钮');

	check(ws.sent.length === sentBeforeSnapReaders, '状态读数没有产生 RPC', 'sent+' + (ws.sent.length - sentBeforeSnapReaders));

	// ⑤ 手动点帽子：帽子方法必须返回 true，否则 VM 会 retireThread	check(ext.whenChat() === true, '帽子方法返回 true（点帽子能继续往下跑）');
	check(ext.whenDamaged() === true, '无参数帽子同样返回 true');

	// ⑤ 停止链路：挂起一个命令，然后点红
	const pending = ext.__tmc('turbomc_chat', ['x'], 'command');
	check(typeof pending === 'object' && typeof pending.then === 'function', '命令积木返回 Promise');
	const sentBefore = ws.sent.length;
	check(stopHandlers.length === 1, '已订阅 PROJECT_STOP_ALL', 'handlers=' + stopHandlers.length);
	stopHandlers.forEach((fn) => fn());

	const lastSent = ws.sent.slice(sentBefore).map((s) => { try { return JSON.parse(s); } catch (e) { return null; } });
	check(lastSent.some((m) => m && m.t === 'stop'), '点红后上送 stop', JSON.stringify(lastSent));

	Promise.race([
		pending.then((v) => ({resolved: true, v})),
		new Promise((r) => setTimeout(() => r({resolved: false}), 300))
	]).then((state) => {
		check(state.resolved === true, '挂起的 Promise 在停止时被结束');
		console.log(failures === 0 ? '\n全部通过' : '\n失败 ' + failures + ' 项');
		process.exit(failures === 0 ? 0 : 1);
	});
}, 150);
