/*
 * TurboMC 远程开发桥 —— 浏览器端实现。
 *
 * 本文件由 tools/build-extension.js 拼进编辑器产物（与积木声明同处一个 IIFE 内），
 * 不在游戏内运行，也不进入运行时副本。
 *
 * 职责：
 *   1. 连接面板：地址 / 端口 / 连接 / 断开 / 自动重连
 *   2. __tmc：所有积木的统一入口（未连接时执行 fallback，连接后走 RPC 或快照）
 *   3. 快照缓存与 tick 时钟：wait 与 poll 都由快照驱动（见 ARCHITECTURE.md §7.3）
 *   4. 帽子派发：按服务端下发的 hats 表，实现三种通用匹配器（ARCHITECTURE.md §7.5）
 *
 * 只做这三件事之外的事都是错的方向——积木语义留在 Java，这里不做第二份判断。
 */
function installRemote(Scratch, ext, meta) {
	'use strict';

	if (!Scratch.extensions.unsandboxed) {
		throw new Error('TurboMC 远程模式必须以 unsandboxed 方式加载扩展');
	}

	const STORAGE_KEY = 'turbomc.remote.v1';
	const RECONNECT_MS = 3000;
	/** 单个调用最长等待；动作自身的上限更短，这里是「绝不永挂」的兜底。 */
	const CALL_TIMEOUT_MS = 60000;
	/** 扩展期望的快照字段版本；与客户端 RemoteProtocol.SNAPSHOT_SCHEMA 必须一致。 */
	const SNAP_SCHEMA = 6;
	/**
	 * 画面：浏览器原生捕获（stage-view.js，构建时拼在同一作用域里）。
	 * 缺失时面板不显示画面按钮，其余功能不受影响。
	 */
	const STAGE_VIEW = typeof createStageView === 'function' ? createStageView(Scratch) : null;

	const S = {
		ws: null,
		connected: false,
		welcome: null,
		tick: 0,
		seq: 0,
		/** id → { resolve, value, tick0, wait, check, settled, pollTick } */
		pending: new Map(),
		snapshot: null,
		lastSnapAt: 0,
		reconnectTimer: null,
		reconnectWanted: false,
		panel: null,
		panelBody: null,
		/** 最近一次帽子派发的结论，显示在状态行最后一行（见 noteHat）。 */
		lastHat: '',
		/** 状态文字节点：快照只改这里的 textContent，不重建整个面板（见 refreshStatus）。 */
		panelStatus: null,
		/** 上一次渲染面板时的连接状态，用来判断要不要重建控件。 */
		panelConnected: null,
		missingReporters: new Set(),
		/** 画面捕获的失败原因（面板上显示一行） */
		viewError: '',
		config: loadConfig()
	};

	// ---- 配置 ----

	function loadConfig() {
		const fallback = {
			address: '127.0.0.1', port: 35671, name: '', autoReconnect: true,
			panelX: null, panelY: null
		};
		try {
			const raw = localStorage.getItem(STORAGE_KEY);
			if (!raw) {
				return fallback;
			}
			const parsed = JSON.parse(raw);
			return {
				address: parsed.address || fallback.address,
				port: Number(parsed.port) || fallback.port,
				name: parsed.name || '',
				autoReconnect: parsed.autoReconnect !== false,
				// 面板位置：拖过一次才记，没记过就按默认的左下角（CSS 的 bottom:12px）
				panelX: Number.isFinite(parsed.panelX) ? parsed.panelX : null,
				panelY: Number.isFinite(parsed.panelY) ? parsed.panelY : null
			};
		} catch (e) {
			return fallback;
		}
	}

	function saveConfig() {
		try {
			localStorage.setItem(STORAGE_KEY, JSON.stringify(S.config));
		} catch (e) {
			// 隐私模式下写不进去，不影响本次会话
		}
	}

	// ---- 面板 ----

	function ensurePanel() {
		if (S.panel) {
			return S.panel;
		}
		const root = document.createElement('div');
		root.style.cssText = [
			'position:fixed', 'left:12px', 'bottom:12px', 'z-index:1000000',
			'width:340px', 'border-radius:8px', 'overflow:hidden',
			'background:rgba(18,18,22,0.96)', 'color:#e8e8ea',
			'font:12px/1.5 ui-monospace,Consolas,monospace',
			'box-shadow:0 6px 24px rgba(0,0,0,0.45)',
			'border:1px solid rgba(255,255,255,0.14)'
		].join(';');
		root.style.display = 'none';

		const head = document.createElement('div');
		head.style.cssText = 'display:flex;align-items:center;justify-content:space-between;'
			+ 'padding:6px 10px;background:rgba(255,255,255,0.06);font-weight:600';
		const title = document.createElement('span');
		title.textContent = 'TurboMC 远程调试';
		const close = document.createElement('button');
		close.textContent = '×';
		close.style.cssText = 'background:none;border:none;color:#e8e8ea;font-size:16px;'
			+ 'line-height:1;cursor:pointer;padding:0 2px';
		close.addEventListener('click', () => { root.style.display = 'none'; });
		head.appendChild(title);
		head.appendChild(close);

		const body = document.createElement('div');
		body.style.padding = '8px 10px';

		root.appendChild(head);
		root.appendChild(body);
		document.body.appendChild(root);
		S.panel = root;
		S.panelBody = body;
		makeDraggable(root, head);
		applyPanelPosition(root);
		// 窗口变小后把面板拉回可视区，否则标题栏可能被推到屏幕外、再也抓不回来。
		// 探针沙箱（tools/probe）的 window 是精简桩，没有 addEventListener，故按能力探测
		if (typeof window.addEventListener === 'function') {
			window.addEventListener('resize', () => clampPanel(root));
		}
		renderPanel();
		return root;
	}

	/**
	 * 让面板可拖动。
	 *
	 * <p>面板固定 340px 宽、压在编辑器左下角，遮住积木区时没有任何办法挪开——这是
	 * 「远程调试按下去就挡住工作区」的直接原因。改成拖标题栏移动，位置记进 localStorage，
	 * 下次打开还在原处；双击标题栏回到默认位置。</p>
	 *
	 * <p>用 Pointer Events：鼠标 / 触屏 / 触控笔一套逻辑，不需要分别监听 mouse 与 touch。
	 * 坐标一律钳在视口内——标题栏是唯一的抓手，它跑到屏幕外就等于面板丢了。</p>
	 */
	function makeDraggable(root, head) {
		head.style.cursor = 'move';
		head.style.userSelect = 'none';
		head.title = '拖动可移动，双击回到左下角';

		head.addEventListener('pointerdown', (e) => {
			// 关闭按钮上的按下不算拖动（按钮自己还要收 click）
			if (e.target !== head && e.target.tagName !== 'SPAN') {
				return;
			}
			if (typeof e.button === 'number' && e.button !== 0) {
				return;
			}
			const rect = root.getBoundingClientRect();
			const offsetX = e.clientX - rect.left;
			const offsetY = e.clientY - rect.top;
			// 先把「左下角锚定」换成「左上角坐标」，否则拖动会从 12px 处跳一下
			root.style.left = rect.left + 'px';
			root.style.top = rect.top + 'px';
			root.style.bottom = 'auto';
			if (typeof head.setPointerCapture === 'function') {
				head.setPointerCapture(e.pointerId);
			}

			// 用 AbortController 统一收回监听：比 removeEventListener 少写三行、
			// 也不会漏掉 pointercancel 这一路
			const controller = typeof AbortController === 'function' ? new AbortController() : null;
			const opts = controller ? {signal: controller.signal} : undefined;

			const move = (ev) => {
				const maxX = Math.max(0, window.innerWidth - root.offsetWidth);
				const maxY = Math.max(0, window.innerHeight - root.offsetHeight);
				const x = Math.min(Math.max(0, ev.clientX - offsetX), maxX);
				const y = Math.min(Math.max(0, ev.clientY - offsetY), maxY);
				root.style.left = x + 'px';
				root.style.top = y + 'px';
				S.config.panelX = x;
				S.config.panelY = y;
			};
			const up = () => {
				if (controller) {
					controller.abort();
				} else {
					head.removeEventListener('pointermove', move);
					head.removeEventListener('pointerup', up);
				}
				// 拖完才写盘：拖动过程每帧都写 localStorage 会卡顿
				saveConfig();
			};
			head.addEventListener('pointermove', move, opts);
			head.addEventListener('pointerup', up, opts);
			if (controller) {
				head.addEventListener('pointercancel', up, opts);
			}
		});

		head.addEventListener('dblclick', () => resetPanelPosition(root));
	}

	/** 恢复默认位置（左下角），并把记录清掉。 */
	function resetPanelPosition(root) {
		S.config.panelX = null;
		S.config.panelY = null;
		saveConfig();
		root.style.left = '12px';
		root.style.top = 'auto';
		root.style.bottom = '12px';
	}

	/** 应用上次记录的位置；没记录就保持 CSS 里的左下角默认值。 */
	function applyPanelPosition(root) {
		if (!Number.isFinite(S.config.panelX) || !Number.isFinite(S.config.panelY)) {
			return;
		}
		root.style.left = S.config.panelX + 'px';
		root.style.top = S.config.panelY + 'px';
		root.style.bottom = 'auto';
		clampPanel(root);
	}

	/** 把面板钳回视口内（窗口缩小、换了显示器、上次保存时窗口更大）。 */
	function clampPanel(root) {
		if (!root || !Number.isFinite(S.config.panelX) || !Number.isFinite(S.config.panelY)) {
			return;
		}
		const width = root.offsetWidth || 340;
		const height = root.offsetHeight || 0;
		const maxX = Math.max(0, window.innerWidth - width);
		const maxY = Math.max(0, window.innerHeight - height);
		const x = Math.min(Math.max(0, S.config.panelX), maxX);
		const y = Math.min(Math.max(0, S.config.panelY), maxY);
		S.config.panelX = x;
		S.config.panelY = y;
		root.style.left = x + 'px';
		root.style.top = y + 'px';
		root.style.bottom = 'auto';
	}

	function field(label, value, onChange, type) {
		const row = document.createElement('div');
		row.style.cssText = 'display:flex;gap:6px;align-items:center;margin-bottom:6px';
		const text = document.createElement('span');
		text.textContent = label;
		text.style.cssText = 'flex:0 0 48px;opacity:0.75';
		const input = document.createElement('input');
		input.type = type || 'text';
		input.value = value;
		input.style.cssText = 'flex:1 1 auto;min-width:0;background:rgba(255,255,255,0.08);'
			+ 'border:1px solid rgba(255,255,255,0.15);border-radius:4px;color:#e8e8ea;'
			+ 'padding:2px 6px;font:inherit';
		input.addEventListener('change', () => onChange(input.value));
		row.appendChild(text);
		row.appendChild(input);
		return row;
	}

	function button(label, onClick) {
		const b = document.createElement('button');
		b.textContent = label;
		b.style.cssText = 'background:rgba(255,255,255,0.10);border:1px solid rgba(255,255,255,0.18);'
			+ 'color:#e8e8ea;border-radius:4px;padding:3px 10px;cursor:pointer;font:inherit';
		b.addEventListener('click', onClick);
		return b;
	}

	function renderPanel() {
		if (!S.panelBody) {
			return;
		}
		const body = S.panelBody;
		body.textContent = '';

		body.appendChild(field('地址', S.config.address, (v) => {
			S.config.address = v.trim() || '127.0.0.1';
			saveConfig();
		}));
		body.appendChild(field('端口', String(S.config.port), (v) => {
			const n = Number(v);
			S.config.port = Number.isFinite(n) && n > 0 && n < 65536 ? Math.trunc(n) : 35671;
			saveConfig();
			renderPanel();
		}, 'number'));

		const row = document.createElement('div');
		row.style.cssText = 'display:flex;gap:6px;margin:8px 0';
		if (S.connected) {
			row.appendChild(button('断开', () => disconnect(true)));
		} else {
			row.appendChild(button('连接', () => {
				// 首次连接必须由用户手势触发：LNA 的权限请求要求这样
				connect();
			}));
		}
		row.appendChild(button('打开目录说明', () => {
			window.alert('端口在游戏的 HUD 或 logs/latest.log 里（搜索「远程调试」）。\n'
				+ '首次连接浏览器会询问是否允许访问本地网络，选「允许」。');
		}));
		body.appendChild(row);

		// 画面：捕获的是系统窗口，与连接状态无关——断线后画面仍在，用户自己决定何时停
		if (STAGE_VIEW) {
			const viewRow = document.createElement('div');
			viewRow.style.cssText = 'display:flex;gap:6px;margin:8px 0';
			viewRow.appendChild(button(STAGE_VIEW.isCapturing() ? '停止捕获' : '开始捕获', async () => {
				if (STAGE_VIEW.isCapturing()) {
					STAGE_VIEW.stop();
					S.viewError = '';
				} else {
					// 必须由用户手势触发：页面加载时自动调用会被浏览器拒绝
					const result = await STAGE_VIEW.start();
					S.viewError = result.ok ? '' : result.error;
					if (!result.ok) {
						console.warn('[turbomc] 画面捕获失败：' + result.error);
					}
				}
				renderPanel();
			}));
			viewRow.appendChild(button(STAGE_VIEW.getMode() === 'video' ? '切到舞台' : '切到画面', () => {
				STAGE_VIEW.toggleMode();
				renderPanel();
			}));
			body.appendChild(viewRow);
		}

		const status = document.createElement('div');
		status.style.cssText = 'white-space:pre-wrap;word-break:break-all;opacity:0.9';
		status.textContent = statusText();
		S.panelStatus = status;
		S.panelConnected = S.connected;
		body.appendChild(status);
	}

	function statusText() {
		if (S.connected && S.welcome) {
			return '● 已连接 ' + (S.welcome.instance || '?')
				+ '\n  世界 ' + (S.welcome.world || '—')
				+ ' · ' + S.tick + ' tick'
				+ (S.snapshot ? '\n  (' + S.snapshot.x + ', ' + S.snapshot.y + ', ' + S.snapshot.z + ')' : '')
				+ (S.schemaSeen !== undefined && S.schemaSeen !== SNAP_SCHEMA
					? '\n  ⚠ 客户端快照版本旧（' + (S.schemaSeen || 0) + ' < ' + SNAP_SCHEMA
						+ '）：请更新模组，否则新积木恒为空'
					: '')
				+ (S.lastHat ? '\n  ' + S.lastHat : '')
				+ viewLine();
		}
		if (S.ws) {
			return '○ 连接中…';
		}
		return '○ 未连接\n  端口见游戏 HUD 或 logs/latest.log';
	}

	/** 画面那一行：失败原因优先，其次显示捕获源分辨率。 */
	function viewLine() {
		if (!STAGE_VIEW) {
			return '';
		}
		if (S.viewError) {
			return '\n  画面：' + S.viewError;
		}
		if (!STAGE_VIEW.isCapturing()) {
			return '';
		}
		const source = STAGE_VIEW.source();
		const size = source && source.width
			? source.width + '×' + source.height + '@' + Math.round(source.frameRate || 0)
			: '已捕获';
		return '\n  画面：' + size + (STAGE_VIEW.getMode() === 'stage' ? '（已切到舞台）' : '');
	}

	function refreshStatus() {		if (!S.panelBody || !S.panel || S.panel.style.display === 'none') {
			return;
		}
		// 只有「连接状态变化」才重建控件。快照每秒到这里 20 次，若每次都重建，
		// 按钮会在 mousedown 与 mouseup 之间被换掉——click 不触发，点「断开」就像没反应；
		// 更糟的是按下时是「断开」、抬起时那个位置已经变成「连接」，于是反而连了回去。
		// 输入框同理：每帧重建会让地址/端口根本敲不进去。
		if (!S.panelStatus || S.panelConnected !== S.connected) {
			renderPanel();
			return;
		}
		S.panelStatus.textContent = statusText();
	}

	function togglePanel() {
		const root = ensurePanel();
		if (root.style.display === 'none') {
			root.style.display = 'block';
			renderPanel();
		} else {
			root.style.display = 'none';
		}
	}

	// ---- 连接 ----

	function connect() {
		disconnect(false);
		const url = 'ws://' + S.config.address + ':' + S.config.port;
		let ws;
		try {
			ws = new WebSocket(url);
		} catch (e) {
			console.warn('[turbomc] 地址无效：' + url);
			return;
		}
		S.ws = ws;
		S.reconnectWanted = true;

		ws.onopen = () => {
			ws.send(JSON.stringify({t: 'hello', v: 1, client: 'turbowarp'}));
		};
		ws.onmessage = (event) => {
			let message;
			try {
				message = JSON.parse(event.data);
			} catch (e) {
				return;
			}
			handleMessage(message);
		};
		ws.onerror = () => {
			// 具体原因（LNA 未授权 / 端口未监听 / Origin 被拒）由浏览器控制台给出
			console.warn('[turbomc] 连接失败：' + url + '（常见原因：本地网络未授权、端口不对、Origin 被拒）');
		};
		ws.onclose = () => {
			const wasConnected = S.connected;
			S.connected = false;
			S.welcome = null;
			S.ws = null;
			rejectAllPending();
			refreshStatus();
			if (wasConnected) {
				console.info('[turbomc] 连接已断开');
			}
			scheduleReconnect();
		};
		refreshStatus();
	}

	/**
	 * @param {boolean} byUser 用户主动断开时为 true：不再自动重连
	 */
	function disconnect(byUser) {
		if (byUser) {
			S.reconnectWanted = false;
		}
		if (S.reconnectTimer) {
			clearTimeout(S.reconnectTimer);
			S.reconnectTimer = null;
		}
		const ws = S.ws;
		S.ws = null;
		S.connected = false;
		S.welcome = null;
		if (ws) {
			try {
				ws.onclose = null;
				ws.close();
			} catch (e) {
				// 忽略
			}
		}
		rejectAllPending();
		refreshStatus();
	}

	// ---- 停止链路 ----

	/**
	 * 订阅 VM 的「停止全部」。
	 *
	 * <p>点红方块后，线程在浏览器侧清空；但游戏侧按住的键、寻路会话、待发聊天没人收，
	 * 人物会继续往前走。必须主动通知服务端收手（ARCHITECTURE.md §7.1）。</p>
	 *
	 * <p>绿旗启动前 VM 自己也会 `stopAll`，这里同样上送——语义上没错：上一次运行的
	 * 动作不该留到新一次运行里。</p>
	 */
	function hookStopAll() {
		const runtime = Scratch.vm && Scratch.vm.runtime;
		if (!runtime || typeof runtime.on !== 'function' || runtime.__tmcStopHooked) {
			return;
		}
		runtime.__tmcStopHooked = true;
		runtime.on('PROJECT_STOP_ALL', () => {
			// 先清本地：挂起的 Promise 不结束，线程会永远停在 PROMISE_WAIT，
			// 表现是「点了红停止，脚本还在转」
			rejectAllPending();
			if (!S.connected || !S.ws) {
				return;
			}
			try {
				S.ws.send(JSON.stringify({t: 'stop', reason: 'user'}));
			} catch (e) {
				// 连接恰好断了，忽略
			}
		});
	}

	function scheduleReconnect() {
		if (!S.reconnectWanted || !S.config.autoReconnect || S.reconnectTimer) {
			return;
		}
		S.reconnectTimer = setTimeout(() => {
			S.reconnectTimer = null;
			if (S.reconnectWanted && !S.connected) {
				connect();
			}
		}, RECONNECT_MS);
	}

	// ---- 消息 ----

	function handleMessage(message) {
		switch (message.t) {
			case 'welcome':
				S.welcome = message;
				S.connected = true;
				// VM 可能比扩展晚就绪（沙箱/加载顺序），每次连上都补挂一次，函数内部幂等
				hookStopAll();
				checkCoverage(message);
				refreshStatus();
				return;
			case 'snap':
				S.snapshot = message;
				S.tick = typeof message.tick === 'number' ? message.tick : S.tick + 1;
				S.lastSnapAt = performance.now();
				// 版本不一致 = 客户端是旧的。字段只增不减，所以不会报错，
				// 只会让新积木恒为假/为空——不显式提示的话，看起来就是「积木坏了」
				const schema = typeof message.schema === 'number' ? message.schema : 0;
				if (schema !== SNAP_SCHEMA && S.schemaSeen !== schema) {
					S.schemaSeen = schema;
					console.warn('[turbomc] 快照版本不一致：客户端 schema=' + schema
						+ '，扩展期望 ' + SNAP_SCHEMA + '——请更新模组客户端');
				}
				advancePending();
				refreshStatus();
				return;
			case 'ret':
				handleReturn(message);
				return;
			case 'event':
				dispatchHat(message);
				return;
			case 'evicted':
				// 被另一个标签页接管：停止重连，避免两边来回抢占
				S.reconnectWanted = false;
				console.warn('[turbomc] 已被其他标签页接管，停止自动重连');
				return;
			case 'stopped':
				rejectAllPending();
				return;
			default:
				return;
		}
	}

	/** 版本自检：opcode 集合与服务端不一致时提示（与游戏内 HUD 同一套判断）。 */
	function checkCoverage(welcome) {
		if (!Array.isArray(welcome.opcodes)) {
			return;
		}
		const expected = new Set(welcome.opcodes);
		const actual = new Set(ext.getInfo().blocks
			.filter((b) => b.opcode && b.blockType !== 'button')
			.map((b) => meta.id + '_' + b.opcode));
		const missing = [...expected].filter((op) => !actual.has(op));
		const extra = [...actual].filter((op) => !expected.has(op));
		if (missing.length || extra.length) {
			console.warn('[turbomc] 版本不一致：缺少 ' + missing.length
				+ ' 块（模组较新），多出 ' + extra.length + ' 块（扩展较新）');
			if (missing.length) console.warn('[turbomc] 缺少的 opcode：', missing);
			if (extra.length) console.warn('[turbomc] 多出的 opcode：', extra);
		}
	}

	function handleReturn(message) {
		const p = S.pending.get(message.id);
		if (!p) {
			return;
		}
		// poll 的回包只有 done
		if (typeof message.done === 'boolean') {
			if (message.done) {
				finish(p, p.value);
			}
			return;
		}
		if (!message.ok) {
			console.warn('[turbomc] 调用失败 ' + (message.code || '') + '：' + (message.err || ''));
			finish(p, undefined);
			return;
		}
		p.value = message.v === undefined ? null : message.v;
		p.tick0 = S.tick;
		p.wait = Number(message.wait) || 0;
		p.check = message.check || '';
		if (!p.wait && !p.check) {
			finish(p, p.value);
		}
	}

	/**
	 * 每收到一帧快照推进一步等待。
	 *
	 * <p>这里就是 §7.3 的时钟：{@code wait} 按 tick 号推进，{@code check} 每帧发一次 poll。
	 * 不用 setTimeout——它的实际间隔是 52–56ms，跑几分钟会明显漂移。</p>
	 */
	function advancePending() {
		if (!S.pending.size || !S.ws) {
			return;
		}
		for (const [id, p] of S.pending) {
			if (p.wait > 0 && S.tick >= p.tick0 + p.wait) {
				finish(p, p.value);
				continue;
			}
			if (p.check && p.pollTick !== S.tick) {
				p.pollTick = S.tick;
				try {
					S.ws.send(JSON.stringify({t: 'poll', id: id, key: p.check}));
				} catch (e) {
					finish(p, undefined);
				}
			}
		}
	}

	function finish(p, value) {
		if (p.settled) {
			return;
		}
		p.settled = true;
		S.pending.delete(p.id);
		p.resolve(value);
	}

	function rejectAllPending() {
		for (const [, p] of S.pending) {
			if (!p.settled) {
				p.settled = true;
				p.resolve(undefined);
			}
		}
		S.pending.clear();
	}

	// ---- 帽子派发 ----

	/** 帽子字段名 → 事件里的数据字段。名字不同，必须显式映射，不能靠大小写猜。 */
	const FIELD_SOURCE = {SCREEN: 'screen', KEY_OPTION: 'key', MATCH: 'text'};

	function dispatchHat(event) {
		const hats = S.welcome && S.welcome.hats;
		const runtime = Scratch.vm && Scratch.vm.runtime;
		if (!Array.isArray(hats) || !runtime) {
			return;
		}
		const data = event.data || {};
		for (const rule of hats) {
			if (rule.ev !== event.name) {
				continue;
			}
			if (!rule.match) {
				pushHatsMatching(runtime, rule.op, null);
				continue;
			}
			const parts = String(rule.match).split(':');
			const kind = parts[0];
			const name = parts[1];
			const value = data[FIELD_SOURCE[name] || name.toLowerCase()];
			if (kind === 'field') {
				pushHatsMatching(runtime, rule.op, makeFields(name, value));
				if (rule.alsoAny) {
					pushHatsMatching(runtime, rule.op, makeFields(name, 'any'));
				}
			} else if (kind === 'input' && rule.contains) {
				startInputMatches(runtime, rule.op, name, value);
			}
		}
	}

	function makeFields(name, value) {
		const fields = {};
		fields[name] = value === undefined || value === null ? '' : String(value);
		return fields;
	}

	/**
	 * 按字段匹配启动帽子。
	 *
	 * <p><b>为什么不直接用 {@code runtime.startHats}</b>：它会先 {@code stepThread} 再
	 * {@code goToNextBlock}，而第一步就把帽子块当作普通积木执行了一次。扩展帽子没有实现体，
	 * 返回 {@code undefined}，于是走 {@code n || retireThread(t)} 分支——线程当场退役。
	 * 症状是「帽子亮了，后面的积木一块都不跑」，而且内置键帽（{@code event_whenkeypressed}
	 * 是 edge-activated）不受影响，更加让人以为是自己的脚本写错了。</p>
	 *
	 * <p>所以这里自己遍历顶层块、匹配字段、{@code _pushThread} 之后立刻
	 * {@code goToNextBlock()}，让线程第一步就落在帽子的下一块上。与游戏内
	 * {@code __tmcStartHatField} 同一套做法。</p>
	 */
	function pushHatsMatching(runtime, opcode, fields) {
		const started = [];
		let matched = 0;
		for (const target of runtime.targets) {
			const blocks = target.blocks;
			if (!blocks || !blocks._blocks) {
				continue;
			}
			for (const id in blocks._blocks) {
				const block = blocks._blocks[id];
				if (!block || block.opcode !== opcode || !block.topLevel) {
					continue;
				}
				if (fields && !fieldsMatch(block, fields)) {
					continue;
				}
				matched++;
				if (isBusy(runtime, target, id)) {
					continue;
				}
				pushHatThread(runtime, target, id, started);
			}
		}
		// 面板上留一行：命中几顶、起了几个线程。分不清「没匹配上」与「起了但没跑」时，
		// 这行是唯一能在游戏画面外看到的信息
		noteHat('帽子 ' + opcode.replace(/^turbomc_/, '') + '：命中 ' + matched
			+ ' · 起线程 ' + started.length
			+ ' · 当前线程 ' + (Array.isArray(runtime.threads) ? runtime.threads.length : '?')
			+ (matched > started.length ? '（其余在跑或帽子是空的）' : ''));
		return started;
	}

	/** 把最近一次帽子派发结果写进面板状态行。 */
	function noteHat(text) {
		S.lastHat = text;
		refreshStatus();
	}

	/** 字段比较：沿用 {@code startHats} 的规则——两边都转大写再比。 */
	function fieldsMatch(block, fields) {
		for (const name in fields) {
			const actual = blockField(block, name);
			if (actual === null) {
				return false;
			}
			if (actual.toUpperCase() !== String(fields[name]).toUpperCase()) {
				return false;
			}
		}
		return true;
	}

	/** 读帽子字段：兼容运行期 {@code {name,value,id}} 与原始 JSON {@code [value, id]}。 */
	function blockField(block, name) {
		const field = block && block.fields ? block.fields[name] : null;
		if (!field) {
			return null;
		}
		if (typeof field === 'object' && field.value !== undefined) {
			return String(field.value);
		}
		if (Array.isArray(field) && field.length >= 1) {
			return String(field[0]);
		}
		return null;
	}

	/**
	 * 起一个帽子线程，**直接把栈顶放在帽子的下一块**。
	 *
	 * <p>踩过两次的坑：用 {@code runtime.startHats} 或在帽子块上 {@code _pushThread}
	 * 再 {@code goToNextBlock}，这份 VM 里都会先把帽子当普通积木执行一次。扩展帽子没有实现体，
	 * 返回 {@code undefined}，于是 {@code retireThread}——现象是「帽子高亮了，后面的积木不动」。</p>
	 *
	 * <p>所以这里绕过帽子本身：找到它的 {@code next}，从那一块起线程。空帽子（只有帽子、
	 * 下面没有积木）本来就没有可执行的东西，直接跳过。</p>
	 */
	function pushHatThread(runtime, target, blockId, out, chatText) {
		const nextId = nextBlockId(target, blockId);
		if (!nextId) {
			return null;
		}
		const ret = runtime._pushThread(nextId, target);
		// 兼容被包过一层的 _pushThread：可能不返回线程，取 threads 末尾那个
		const thread = ret && typeof ret === 'object'
			? ret
			: (Array.isArray(runtime.threads) ? runtime.threads[runtime.threads.length - 1] : null);
		// 聊天触发的线程带一份自己的消息副本：全局槽可能已经被后来的消息改写，
		// 「触发我的聊天」读的必须是这一条（与游戏内 vm-bootstrap 同一做法）
		if (thread && chatText !== undefined && chatText !== null) {
			thread.tmcChat = chatText;
		}
		if (out) {
			out.push(thread || true);
		}
		return thread;
	}

	/** 取帽子下面挂的第一块；没有则返回 null。 */
	function nextBlockId(target, blockId) {
		const blocks = target && target.blocks;
		if (!blocks) {
			return null;
		}
		if (typeof blocks.getNextBlock === 'function') {
			return blocks.getNextBlock(blockId) || null;
		}
		const block = blocks._blocks ? blocks._blocks[blockId] : null;
		return block && block.next ? block.next : null;
	}

	/**
	 * 按输入内容过滤后启动帽子。
	 *
	 * <p>{@code startHats} 的字段匹配只认 field，而「当聊天包含」的关键词是 input，
	 * 传 matchFields 会抛错。因此自行遍历顶层块、读输入、命中后 {@code _pushThread}。
	 * 这段与游戏内 {@code vm-bootstrap.js} 的 {@code __tmcStartHatFiltered} 同一套做法。</p>
	 */
	function startInputMatches(runtime, opcode, inputName, text) {
		const needle = text === undefined || text === null ? '' : String(text);
		let matched = 0;
		let started = 0;
		for (const target of runtime.targets) {
			const blocks = target.blocks;
			if (!blocks || !blocks._blocks) {
				continue;
			}
			for (const id in blocks._blocks) {
				const block = blocks._blocks[id];
				if (!block || block.opcode !== opcode || !block.topLevel) {
					continue;
				}
				// 读不出关键词（接了表达式）或关键词为空：按「匹配全部」处理，
				// 与玩家聊天通道的历史行为一致
				const keyword = resolveKeyword(runtime, target, blocks, block, inputName);
				if (keyword.known && keyword.value !== '' && needle.indexOf(keyword.value) < 0) {
					continue;
				}
				matched++;
				if (isBusy(runtime, target, id)) {
					continue;
				}
				// 线程起在帽子下一块：留在帽子上会执行帽子本身并立刻退役
				if (pushHatThread(runtime, target, id, null, needle)) {
					started++;
				}
			}
		}
		noteHat('帽子 ' + opcode.replace(/^turbomc_/, '') + '：命中 ' + matched
			+ ' · 起线程 ' + started
			+ ' · 当前线程 ' + (Array.isArray(runtime.threads) ? runtime.threads.length : '?'));
	}

	/**
	 * 读输入槽的字面量：读不出（接了变量/表达式）返回 {@code null}，空槽返回空串。
	 *
	 * <p><b>这是踩过的坑</b>：sb3 的字面量是嵌套形态 {@code [1, [10, "文字"]]}，
	 * 只看 {@code input[1]} 是不是字符串，会把**所有字面量都判成"读不出来"**，
	 * 于是关键词过滤形同虚设——「当聊天包含 你好」在任何一条聊天上都会触发。
	 * 这里的三种形态与游戏内 {@code __tmcReadInput} 一一对应。</p>
	 */
	function readInput(blocks, block, name) {
		const direct = fieldText(block, name);
		if (direct !== null) {
			return direct;
		}
		const input = block.inputs && block.inputs[name];
		if (!input) {
			return '';
		}
		// 运行期形态 {name, block, shadow}：block 与 shadow 不同说明槽里接了积木
		if (input.block !== undefined) {
			if (input.block !== input.shadow) {
				return null;
			}
			return shadowText(blocks, input.block);
		}
		// 原始 JSON 形态 [1, [10, "文字"]] 或 [1, "blockId"]
		const value = input[1];
		if (typeof value === 'string') {
			return shadowText(blocks, value);
		}
		if (value && value.length >= 2) {
			return String(value[1] === null || value[1] === undefined ? '' : value[1]);
		}
		return null;
	}

	/** 字段文本：兼容运行期的 {name,value,id} 与原始 JSON 的 [value, id]。 */
	function fieldText(block, name) {
		const field = block && block.fields ? block.fields[name] : null;
		if (!field) {
			return null;
		}
		if (typeof field === 'object' && field.value !== undefined) {
			return String(field.value);
		}
		if (Array.isArray(field) && field.length >= 1) {
			return String(field[0]);
		}
		return null;
	}

	/** 替身积木（只有字段没有操作）里取第一段文本。 */
	function shadowText(blocks, id) {
		const shadow = id && blocks && blocks._blocks ? blocks._blocks[id] : null;
		if (!shadow || !shadow.fields) {
			return '';
		}
		for (const key in shadow.fields) {
			const value = fieldText(shadow, key);
			if (value !== null) {
				return value;
			}
		}
		return '';
	}

	/** 槽里接的那块积木（可能是变量/表达式 reporter）；只有替身时返回 null。 */
	function reporterBlock(blocks, block, inputName) {
		const input = block.inputs && block.inputs[inputName];
		if (!input) {
			return null;
		}
		let id = null;
		if (input.block !== undefined) {
			id = input.block;
		} else if (typeof input[1] === 'string') {
			id = input[1];
		}
		if (!id || !blocks || !blocks._blocks) {
			return null;
		}
		return blocks._blocks[id] || null;
	}

	/** 读变量当前值；作用域先本角色再舞台（sb3 里变量通常定义在舞台上）。 */
	function variableText(runtime, target, variableField) {
		let id = null;
		if (variableField && typeof variableField === 'object' && variableField.id !== undefined) {
			id = variableField.id;
		} else if (Array.isArray(variableField) && variableField.length >= 2) {
			id = variableField[1];
		}
		if (!id) {
			return null;
		}
		const lookup = (scope) => {
			const bag = scope && scope.variables;
			return bag && bag[id] !== undefined ? bag[id] : null;
		};
		let value = lookup(target);
		if (value === null) {
			const stage = (runtime.targets || []).find((t) => t.isStage);
			value = lookup(stage);
		}
		return value === null ? null : String(value);
	}

	/** 关键词：字面量优先，其次变量；都读不出时 known=false（由调用方按「匹配全部」处理）。 */
	function resolveKeyword(runtime, target, blocks, block, inputName) {
		const literal = readInput(blocks, block, inputName);
		if (literal !== null) {
			return {known: true, value: literal};
		}
		const reporter = reporterBlock(blocks, block, inputName);
		if (reporter && reporter.opcode === 'data_variable') {
			const value = variableText(runtime, target, reporter.fields && reporter.fields.VARIABLE);
			if (value !== null) {
				return {known: true, value: value};
			}
		}
		return {known: false, value: ''};
	}

	function isBusy(runtime, target, blockId) {
		// 线程是从帽子的下一块起的，topBlock 记的是那一块——两个都要认，
		// 否则同一顶帽子会被连发的聊天叠出好几份线程
		const bodyId = nextBlockId(target, blockId);
		for (const thread of runtime.threads) {
			if (thread.target !== target) {
				continue;
			}
			if (thread.topBlock !== blockId && thread.topBlock !== bodyId) {
				continue;
			}
			if (thread.status !== 4) {
				return true;
			}
		}
		return false;
	}

	// ---- 积木入口 ----

	/**
	 * 快照读数表：由 tools/build-extension.js 从 blocks.data.js 的 read 声明生成，勿手改。
	 * 键是带前缀的 opcode，值是 (snapshot, argv, util) => value。
	 * 新增读数积木只在 blocks.data.js 写一处；这里缺一条，构建会直接失败。
	 */
	/* @tmc-readers */

	/** 与游戏内 {@code ENTITY_SEARCH_RANGE} 一致：两个统计积木的默认搜索半径。 */
	const ENTITY_RANGE = 16;
	/** 快照只带这么远的实体（服务端 {@code SnapshotPublisher.ENTITY_RADIUS}）。 */
	const ENTITY_SNAPSHOT_RADIUS = 32;

	/**
	 * 实体过滤：{@code any} / {@code hostile} / {@code player} / 具体实体 id。
	 *
	 * <p>与游戏内 {@code EntityFilter.parse} 同一套判据；空串按「匹配不到」处理，
	 * 不回退成 any——参数没送到时宁可判否，也不要安静地匹配全部。</p>
	 */
	function entityFilter(raw) {
		const text = String(raw === undefined || raw === null ? '' : raw).trim().toLowerCase();
		if (text === 'any') {
			return {kind: 'any'};
		}
		if (text === 'hostile') {
			return {kind: 'hostile'};
		}
		if (text === 'player') {
			return {kind: 'player'};
		}
		return {kind: 'type', id: text.indexOf(':') >= 0 ? text : (text ? 'minecraft:' + text : '')};
	}

	function entityEntries(s) {
		return s && Array.isArray(s.ents) ? s.ents : [];
	}

	/**
	 * 是否在「盒范围」内。
	 *
	 * <p>游戏内是 {@code boundingBox.expand(r)} 的盒判定，不是球；这里用三轴分量还原，
	 * 差别只出现在大体型实体贴边时，比按 {@code distance} 判球更接近原语义。</p>
	 */
	function inEntityBox(entry, s, range) {
		const px = s && typeof s.x === 'number' ? s.x : 0;
		const py = s && typeof s.y === 'number' ? s.y : 0;
		const pz = s && typeof s.z === 'number' ? s.z : 0;
		return Math.abs(entry.x - px) <= range
			&& Math.abs(entry.y - py) <= range
			&& Math.abs(entry.z - pz) <= range;
	}

	function entityMatches(entry, filter, s) {
		if (filter.kind === 'any') {
			return true;
		}
		if (filter.kind === 'player') {
			return entry.type === 'minecraft:player';
		}
		if (filter.kind === 'hostile') {
			// 「是不是主动怪」在快照里是单独的 uuid 表，不污染实体对象的键序
			return Array.isArray(s && s.hostile) && s.hostile.indexOf(entry.uuid) >= 0;
		}
		return filter.id !== '' && entry.type === filter.id;
	}

	function countEntities(s, filter) {
		let count = 0;
		for (const entry of entityEntries(s)) {
			if (inEntityBox(entry, s, ENTITY_RANGE) && entityMatches(entry, filter, s)) {
				count++;
			}
		}
		return count;
	}

	/** 最近实体：快照已按距离升序，第一个命中的就是答案。 */
	function nearestEntityField(s, a) {
		const filter = entityFilter(a && a[0]);
		const field = String((a && a[1]) || 'distance').trim().toLowerCase();
		const hit = entityEntries(s).find((entry) => (
			inEntityBox(entry, s, ENTITY_RANGE) && entityMatches(entry, filter, s)));
		switch (field) {
			case 'x':
				return hit ? hit.x : NaN;
			case 'y':
				return hit ? hit.y : NaN;
			case 'z':
				return hit ? hit.z : NaN;
			case 'name':
				return hit ? hit.name : '';
			case 'health':
				return hit ? hit.health : 0;
			case 'max_health':
				return hit ? hit.max_health : 0;
			case 'type_id':
				return hit ? hit.type : '';
			default:
				return hit ? hit.distance : -1;
		}
	}

	/**
	 * 附近实体快照。半径与数量在远程模式有上限：快照只带 {@link ENTITY_SNAPSHOT_RADIUS} 格内的实体，
	 * 超出部分会被静默夹到上限（有效上限：半径 32 格、64 个，见 ARCHITECTURE.md §7.6）。
	 */
	function entitiesJson(s, a) {
		const filter = entityFilter(a && a[0]);
		const radius = Math.max(1, Math.min(64, Number((a && a[1]) === undefined ? ENTITY_RANGE : a[1]) || 0));
		const limit = Math.max(1, Math.min(256, Number((a && a[2]) === undefined ? 32 : a[2]) || 0));
		const effective = Math.min(radius, ENTITY_SNAPSHOT_RADIUS);
		const out = [];
		for (const entry of entityEntries(s)) {
			if (!inEntityBox(entry, s, effective) || !entityMatches(entry, filter, s)) {
				continue;
			}
			out.push(entry);
			if (out.length >= limit) {
				break;
			}
		}
		// 条目字段顺序与游戏内 {@code Snapshots.entityObject} 完全一致，stringify 即逐字相同
		return JSON.stringify(out);
	}

	/**
	 * 物品名归一成注册名：{@code stone} 与 {@code minecraft:stone} 视为同一个。
	 *
	 * <p>只做前缀补全，不查中文名——游戏内 {@code ItemIds.resolve} 认中文，
	 * 但浏览器侧没有那份表；用中文名写法时这里会匹配不到（见 ARCHITECTURE.md §7.6 已知限制）。</p>
	 */
	function itemKey(raw) {
		const text = String(raw === undefined || raw === null ? '' : raw).trim().toLowerCase();
		if (!text) {
			return '';
		}
		return text.indexOf(':') >= 0 ? text : 'minecraft:' + text;
	}

	function defaultValue(kind) {
		if (kind === 'boolean') {
			return false;
		}
		if (kind === 'reporter') {
			return '';
		}
		// command：返回 undefined，scratch-vm 不会把 undefined 当成 Promise 等待
		return undefined;
	}

	/**
	 * 所有积木的统一入口。
	 *
	 * @param {string} opcode 完整 opcode（{@code turbomc_xxx}）
	 * @param {Array} argv 有序参数
	 * @param {string} kind command / reporter / boolean
	 * @param {Function} fallback 未连接时的占位行为（原来的预览）
	 */
	ext.__tmc = function (opcode, argv, kind, fallback, util) {
		// 读写当前 VM 列表的两块：碰的是浏览器自己的列表对象，与连接状态无关。
		//
		// 必须排在「未连接 → fallback」之前。放在后面时，导入扩展、尚未连接游戏
		// 的这段时间里，读列表恒为 '[]'、写列表什么都不做——与 JSON 纯计算积木
		// 之前踩的是同一个坑（它们在构建期已改成直调，不再经过本函数）。
		if (opcode === 'turbomc_jsonVmGetList') {
			// 列表不存在给空串，存在但为空给 "[]"——两者语义不同，不能合并
			const found = findVmList(String((argv || [])[0] === undefined ? '' : argv[0]));
			return found ? JSON.stringify(found.value || []) : '';
		}
		if (opcode === 'turbomc_jsonVmSetList') {
			writeVmList(String((argv || [])[0] === undefined ? '' : argv[0]),
				String((argv || [])[1] === undefined ? '' : argv[1]));
			// 命令积木返回 undefined：VM 不会把它当 Promise 去等
			return undefined;
		}
		if (!S.connected) {
			if (typeof fallback === 'function') {
				return fallback.call(this);
			}
			return defaultValue(kind);
		}
		if (kind === 'reporter' || kind === 'boolean') {
			const reader = READERS[opcode];
			if (!reader) {
				if (!S.missingReporters.has(opcode)) {
					S.missingReporters.add(opcode);
					console.warn('[turbomc] 远程模式暂不支持该读数积木：' + opcode + '（返回空值）');
				}
				return defaultValue(kind);
			}
			try {
				const value = reader(S.snapshot, argv, util);
				if (value === undefined || value === null) {
					return defaultValue(kind);
				}
				return kind === 'boolean' ? !!value : value;
			} catch (e) {
				return defaultValue(kind);
			}
		}
		return callRemote(opcode, argv);
	};

	/**
	 * 按名字找列表：先各角色，再兼容老的 {@code target.lists}。
	 *
	 * <p>与游戏内 {@code __tmcFindList} 同一套查找顺序——两边对「哪个列表」的判断必须一致，
	 * 否则同名列表在两份工程里指向不同对象。</p>
	 */
	function findVmList(name) {
		const runtime = Scratch.vm && Scratch.vm.runtime;
		if (!runtime || !Array.isArray(runtime.targets)) {
			return null;
		}
		for (const target of runtime.targets) {
			const vars = target.variables || {};
			for (const id in vars) {
				const item = vars[id];
				if (item && item.name === name && item.type === 'list') {
					return item;
				}
			}
			const lists = target.lists || {};
			for (const listId in lists) {
				const legacy = lists[listId];
				if (legacy && legacy.name === name) {
					return legacy;
				}
			}
		}
		return null;
	}

	/**
	 * 用 JSON 数组覆盖列表。
	 *
	 * <p>列表项只能是标量：对象/数组转成 JSON 文本、{@code null} 变空串。不这么转，
	 * 塞进去的对象只会变成 {@code [object Object]}，一进一出结构就没了。</p>
	 *
	 * <p>读写两块（{@code jsonVmGetList} / {@code jsonVmSetList}）都在本地做：它们操作的是
	 * 当前 VM 自己的列表，发到对面写没有意义。所以游戏内与浏览器走同一条路径，
	 * 转换规则只此一份（见 {@code json-lite.js}）。</p>
	 */
	function writeVmList(name, json) {
		const found = findVmList(name);
		if (!found) {
			return;
		}
		let value;
		try {
			value = JSON.parse(json);
		} catch (e) {
			return;
		}
		if (!Array.isArray(value)) {
			return;
		}
		found.value = value.map((item) => {
			if (item === null || item === undefined) {
				return '';
			}
			if (typeof item === 'object') {
				return JSON.stringify(item);
			}
			return item;
		});
	}

	function callRemote(opcode, argv) {
		const ws = S.ws;
		if (!ws || ws.readyState !== 1) {
			return Promise.resolve(undefined);
		}
		const id = String(++S.seq);
		return new Promise((resolve) => {
			const entry = {id: id, resolve: resolve, value: null, tick0: S.tick, wait: 0, check: '', settled: false, pollTick: -1};
			S.pending.set(id, entry);
			try {
				ws.send(JSON.stringify({t: 'call', id: id, op: opcode, argv: argv || []}));
			} catch (e) {
				finish(entry, undefined);
				return;
			}
			setTimeout(() => finish(entry, undefined), CALL_TIMEOUT_MS);
		});
	}

	/** 供积木按钮调用：打开/关闭连接面板。 */
	ext.openRemotePanel = function () {
		togglePanel();
		return '';
	};

	// 安装即挂上停止订阅：漏了它，「点红停止后人物还在走」会是最难查的一类现象
	hookStopAll();

	return {
		togglePanel: togglePanel,
		connect: connect,
		disconnect: disconnect,
		state: S
	};
}
