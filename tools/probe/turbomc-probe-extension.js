/*
 * TurboMC 画面探针（turbomcprobe）
 *
 * 用途：验证「浏览器/桌面版能否把游戏画面显示在 TurboWarp 舞台区域」这条路径是否成立。
 * 与正式扩展无关，测完即可删除。
 *
 * 加载方式（必须 unsandboxed，否则拿不到 DOM / getDisplayMedia）：
 *   编辑器 → 添加扩展 → 加载本地文件 → 选本文件 → 勾选「不使用沙箱 / Load without sandbox」
 *
 * 使用：
 *   1. 积木面板出现「TurboMC 画面探针」
 *   2. 点面板里的按钮「开始捕获（点我）」—— 按钮点击保证有用户手势
 *   3. 在弹出的浏览器窗口里选 Minecraft 窗口
 *   4. 画面应覆盖在舞台区域；左下角浮层显示状态
 *   5. 点「诊断」把完整环境信息打到浮层与控制台
 *
 * 说明：本文件刻意不依赖项目其他代码，可单独复制到任何地方使用。
 */
(function (Scratch) {
	'use strict';

	if (!Scratch.extensions.unsandboxed) {
		throw new Error('TurboMC 画面探针必须以 unsandboxed 方式加载（加载文件时勾选「不使用沙箱」）');
	}

	const PANEL_ID = 'tmc-probe-panel';
	const VIDEO_ID = 'tmc-probe-video';

	const STATE = {
		stream: null,
		video: null,
		host: null,
		timer: null,
		fps: 0,
		lastError: null,
		canvasVia: '',
		ws: null,
		wsStatus: '未测试',
		rtt: null,
		welcome: null,
		lastSnap: null,
		snapCount: 0,
		callSeq: 0,
		pending: {},
		lastCall: null,
		captureInfo: null,
		lastWaitMs: 0,
		panelHidden: false
	};

	// ---- 面板 ----

	function panel() {
		let el = document.getElementById(PANEL_ID);
		if (el) return el;
		el = document.createElement('div');
		el.id = PANEL_ID;
		el.style.cssText = [
			'position:fixed', 'left:12px', 'bottom:12px', 'z-index:1000000',
			'width:420px', 'max-height:46vh', 'display:flex', 'flex-direction:column',
			'border-radius:8px', 'overflow:hidden',
			'background:rgba(18,18,22,0.95)', 'color:#e8e8ea',
			'font:12px/1.55 ui-monospace,Consolas,monospace',
			'box-shadow:0 6px 24px rgba(0,0,0,0.45)',
			'border:1px solid rgba(255,255,255,0.14)'
		].join(';');

		const head = document.createElement('div');
		head.style.cssText = 'display:flex;align-items:center;justify-content:space-between;'
			+ 'padding:6px 10px;background:rgba(255,255,255,0.06);font-weight:600;flex:0 0 auto';
		const title = document.createElement('span');
		title.textContent = 'TurboMC 画面探针';
		const close = document.createElement('button');
		close.textContent = '×';
		close.title = '关闭（点「诊断」可再打开）';
		close.style.cssText = 'background:none;border:none;color:#e8e8ea;font-size:16px;'
			+ 'line-height:1;cursor:pointer;padding:0 2px';
		close.addEventListener('click', () => {
			STATE.panelHidden = true;
			el.style.display = 'none';
		});
		head.appendChild(title);
		head.appendChild(close);

		const body = document.createElement('pre');
		body.id = PANEL_ID + '-body';
		body.style.cssText = 'margin:0;padding:8px 10px;overflow:auto;white-space:pre-wrap;'
			+ 'word-break:break-all;flex:1 1 auto';

		el.appendChild(head);
		el.appendChild(body);
		document.body.appendChild(el);
		return el;
	}

	function setPanelText(text, forceShow) {
		try {
			const el = panel();
			const body = document.getElementById(PANEL_ID + '-body');
			if (body) body.textContent = text;
			if (forceShow) STATE.panelHidden = false;
			el.style.display = STATE.panelHidden ? 'none' : 'flex';
		} catch (e) {
			// 面板失败不影响主流程
		}
	}

	function statusText(hint) {
		return [
			'状态: ' + (hint || '-'),
			'捕获: ' + (STATE.stream ? '是' : '否') + '    帧率: ' + STATE.fps,
			'定位: ' + (STATE.canvasVia || '未定位') + '    未沙箱: ' + (Scratch.extensions.unsandboxed ? '是' : '否'),
			'连接: ' + STATE.wsStatus + (STATE.rtt ? '（' + STATE.rtt + 'ms）' : ''),
			'快照: ' + STATE.snapCount + ' 帧' + (STATE.lastSnap
				? ' · tick ' + STATE.lastSnap.tick
					+ ' · (' + STATE.lastSnap.x + ', ' + STATE.lastSnap.y + ', ' + STATE.lastSnap.z + ')'
					+ ' · HP ' + STATE.lastSnap.hp
				: ''),
			STATE.lastError ? '错误: ' + STATE.lastError : '',
			'按钮: 开始捕获 / 停止 / 诊断'
		].filter(Boolean).join('\n');
	}

	function refreshPanel(hint) {
		setPanelText(statusText(hint), false);
	}

	// ---- 舞台定位 ----

	/**
	 * 找舞台画布。优先走 runtime.renderer.canvas（最可靠）；
	 * 若 VM 在 worker 里导致拿到的是代理对象，回退到「页面上面积最大的 canvas」。
	 */
	function findStageCanvas() {
		try {
			const renderer = Scratch.vm && Scratch.vm.runtime ? Scratch.vm.runtime.renderer : null;
			const c = renderer ? renderer.canvas : null;
			if (c && typeof c.getBoundingClientRect === 'function' && c.width > 0) {
				return {canvas: c, via: 'renderer.canvas'};
			}
			if (c && typeof c.getBoundingClientRect !== 'function') {
				return {canvas: null, via: 'renderer.canvas 是代理对象（VM 在 worker）'};
			}
		} catch (e) {
			// 落到 DOM 兜底
		}
		const list = [...document.querySelectorAll('canvas')]
			.filter((c) => c.width > 100 && c.height > 100);
		list.sort((a, b) => b.width * b.height - a.width * a.height);
		return list[0] ? {canvas: list[0], via: 'dom-largest'} : {canvas: null, via: '未找到画布'};
	}

	/** 把 video 覆盖到舞台画布上面。每 300ms 校正一次，跟随窗口缩放与布局变化。 */
	function placeOverlay() {
		if (!STATE.video || !STATE.stream) return;
		const found = findStageCanvas();
		STATE.canvasVia = found.via;
		const canvas = found.canvas;
		if (!canvas) {
			if (STATE.video.parentElement !== document.body) {
				document.body.appendChild(STATE.video);
			}
			const s0 = STATE.video.style;
			s0.position = 'fixed';
			s0.left = '40px';
			s0.top = '40px';
			s0.width = '640px';
			s0.height = '360px';
			s0.objectFit = 'contain';
			s0.pointerEvents = 'none';
			s0.zIndex = '999';
			return;
		}
		const host = canvas.parentElement || document.body;
		if (STATE.host !== host) {
			if (STATE.video.parentElement) STATE.video.parentElement.removeChild(STATE.video);
			host.appendChild(STATE.video);
			STATE.host = host;
		}
		if (getComputedStyle(host).position === 'static') {
			host.style.position = 'relative';
		}
		const cr = canvas.getBoundingClientRect();
		const pr = host.getBoundingClientRect();
		const s = STATE.video.style;
		s.position = 'absolute';
		s.left = (cr.left - pr.left) + 'px';
		s.top = (cr.top - pr.top) + 'px';
		s.width = cr.width + 'px';
		s.height = cr.height + 'px';
		s.objectFit = 'contain';
		s.background = '#000';
		s.pointerEvents = 'none';
		s.zIndex = '999';
	}

	// ---- 捕获 ----

	function describeError(e) {
		if (!e) return '未知错误';
		return (e.name || 'Error') + ': ' + (e.message || String(e));
	}

	async function doStartCapture() {
		if (STATE.stream) {
			refreshPanel('已在捕获中');
			return 'already';
		}
		STATE.lastError = null;
		refreshPanel('等待选择捕获源…');

		const constraints = {
			video: {
				frameRate: {ideal: 30, max: 60},
				displaySurface: 'window'
			},
			audio: false,
			// 以下为 Chrome 非标准提示项；不认识它们的浏览器会自动忽略
			preferCurrentTab: false,
			selfBrowserSurface: 'exclude',
			surfaceSwitching: 'include',
			systemAudio: 'exclude',
			monitorTypeSurfaces: 'include'
		};

		try {
			STATE.stream = await navigator.mediaDevices.getDisplayMedia(constraints);

			const track = STATE.stream.getVideoTracks()[0];
			const settings = track && track.getSettings ? track.getSettings() : {};
			STATE.captureInfo = {
				label: (track && track.label ? track.label : '').slice(0, 80),
				width: settings.width,
				height: settings.height,
				frameRate: settings.frameRate,
				displaySurface: settings.displaySurface,
				cursor: settings.cursor
			};

			const video = document.createElement('video');
			video.id = VIDEO_ID;
			video.muted = true;
			video.defaultMuted = true;
			video.playsInline = true;
			video.autoplay = true;
			video.srcObject = STATE.stream;
			STATE.video = video;

			placeOverlay();
			await video.play().catch(() => {});

			// 帧率统计
			let frames = 0;
			let t0 = performance.now();
			const tick = () => {
				if (!STATE.stream || !STATE.video) return;
				frames++;
				const now = performance.now();
				if (now - t0 >= 1000) {
					STATE.fps = +(frames / ((now - t0) / 1000)).toFixed(1);
					frames = 0;
					t0 = now;
				}
				if (STATE.video.requestVideoFrameCallback) {
					STATE.video.requestVideoFrameCallback(tick);
				}
			};
			if (video.requestVideoFrameCallback) {
				video.requestVideoFrameCallback(tick);
			}

			// 用户从浏览器 UI 停止共享时同步收尾
			if (track) {
				track.addEventListener('ended', () => doStopCapture('用户停止了共享'), {once: true});
			}

			STATE.timer = setInterval(placeOverlay, 300);
			refreshPanel('捕获中');
			return 'ok';
		} catch (e) {
			STATE.lastError = describeError(e);
			refreshPanel('捕获失败');
			return 'fail';
		}
	}

	function doStopCapture(reason) {
		if (STATE.timer) {
			clearInterval(STATE.timer);
			STATE.timer = null;
		}
		if (STATE.stream) {
			STATE.stream.getTracks().forEach((t) => t.stop());
			STATE.stream = null;
		}
		if (STATE.video) {
			try {
				STATE.video.pause();
				STATE.video.srcObject = null;
				if (STATE.video.parentElement) STATE.video.parentElement.removeChild(STATE.video);
			} catch (e) {
				// 忽略
			}
			STATE.video = null;
		}
		STATE.host = null;
		STATE.fps = 0;
		refreshPanel(reason ? '已停止：' + reason : '已停止');
		return 'ok';
	}

	// ---- 本机连接（顺带验证 LNA） ----

	function doDisconnect() {
		if (STATE.ws) {
			try {
				STATE.ws.onopen = null;
				STATE.ws.onerror = null;
				STATE.ws.onmessage = null;
				STATE.ws.close();
			} catch (e) {
				// 忽略
			}
			STATE.ws = null;
		}
		if (STATE.wsStatus !== '未测试') STATE.wsStatus = '已断开';
		STATE.snapCount = 0;
		STATE.lastSnap = null;
		STATE.welcome = null;
		return 'ok';
	}

	async function doConnect(port) {
		const p = Math.max(1, Math.min(65535, Number(port) || 35671));
		doDisconnect();
		STATE.wsStatus = '连接中…';
		STATE.rtt = null;
		STATE.welcome = null;
		refreshPanel('连接中');

		return await new Promise((resolve) => {
			let settled = false;
			const done = (status) => {
				if (settled) return;
				settled = true;
				STATE.wsStatus = status;
				refreshPanel('连接：' + status);
				resolve(status);
			};
			try {
				const ws = new WebSocket('ws://127.0.0.1:' + p);
				STATE.ws = ws;
				const timeout = setTimeout(() => done('超时'), 6000);
				ws.onopen = async () => {
					clearTimeout(timeout);
					// 先握手：服务端回 welcome（版本、实例名、端口、opcode 清单、帽子表）
					ws.send(JSON.stringify({t: 'hello', v: 1, client: 'turbomc-probe'}));
					const welcome = await new Promise((res) => {
						const t = setTimeout(() => res(null), 4000);
						ws.onmessage = (e) => {
							try {
								const m = JSON.parse(e.data);
								if (m.t === 'welcome') {
									clearTimeout(t);
									res(m);
								}
							} catch (err) {
								// 非 JSON 帧忽略
							}
						};
					});
					if (!welcome) {
						done('通但未收到 welcome（服务端未实现握手？）');
						return;
					}
					STATE.welcome = welcome;
					const ops = Array.isArray(welcome.opcodes) ? welcome.opcodes.length : 0;
					const hats = Array.isArray(welcome.hats) ? welcome.hats.length : 0;
					// 再测 RTT（服务端对 ping 回 pong）
					try {
						const samples = [];
						for (let i = 0; i < 30; i++) {
							const t0 = performance.now();
							const reply = new Promise((r) => { ws.onmessage = () => r(); });
							ws.send('{"t":"ping"}');
							await reply;
							samples.push(performance.now() - t0);
						}
						samples.sort((a, b) => a - b);
						STATE.rtt = +samples[Math.floor(samples.length / 2)].toFixed(3);
					} catch (e) {
						// RTT 失败不影响握手结论
					}
					done('已连接 ' + (welcome.instance || '?')
						+ '（build ' + (welcome.build || '?')
						+ '，' + ops + ' 个 opcode，' + hats + ' 条帽子）');
					// 常驻监听：统计快照帧数与最后一帧，用于确认 20Hz 推送真的在动
					ws.onmessage = (e) => {
						try {
							const m = JSON.parse(e.data);
							if (m.t === 'snap') {
								STATE.lastSnap = m;
								STATE.snapCount++;
								return;
							}
							if (m.t === 'ret' && STATE.pending[m.id]) {
								const resolve = STATE.pending[m.id];
								delete STATE.pending[m.id];
								resolve(m);
							}
						} catch (err) {
							// 非 JSON 忽略
						}
					};
				};
				ws.onerror = () => {
					clearTimeout(timeout);
					done('失败（LNA 未授权 / 端口未监听 / Origin 被拒）');
				};
			} catch (e) {
				STATE.wsStatus = describeError(e);
				refreshPanel('连接：异常');
				resolve('异常');
			}
		});
	}

	// ---- 诊断 ----

	/** 运行时结构：判断 Scratch.vm 是否跨线程代理。 */
	function describeRuntime() {
		try {
			const vm = Scratch.vm;
			const rt = vm && vm.runtime;
			if (!rt) return 'runtime 不可达';
			const parts = [];
			parts.push('vm=' + (vm.constructor ? vm.constructor.name : typeof vm));
			parts.push('targets=' + (Array.isArray(rt.targets)
				? 'array(' + rt.targets.length + ')'
				: typeof rt.targets));
			const first = rt.targets && rt.targets[0];
			parts.push('t0.blocks=' + (first && first.blocks ? 'yes' : 'no'));
			parts.push('threads=' + (Array.isArray(rt.threads)
				? 'array(' + rt.threads.length + ')'
				: typeof rt.threads));
			parts.push('startHats=' + typeof rt.startHats);
			parts.push('_pushThread=' + typeof rt._pushThread);
			return parts.join(' | ');
		} catch (e) {
			return 'err: ' + describeError(e);
		}
	}

	async function queryPermissions() {
		const out = {};
		for (const name of ['loopback-network', 'local-network', 'display-capture']) {
			try {
				out[name] = (await navigator.permissions.query({name})).state;
			} catch (e) {
				out[name] = '不支持';
			}
		}
		return out;
	}

	async function buildReport() {
		const perms = await queryPermissions();
		const found = findStageCanvas();
		return {
			环境: {
				origin: location.origin,
				协议: location.protocol,
				安全上下文: isSecureContext,
				顶层窗口: window.top === window,
				未沙箱: !!Scratch.extensions.unsandboxed,
				用户手势: navigator.userActivation ? navigator.userActivation.isActive : '不支持查询',
				UA: navigator.userAgent.slice(0, 130)
			},
			运行时: {
				结构: describeRuntime(),
				上次等待毫秒: STATE.lastWaitMs
			},
			画面: {
				getDisplayMedia: navigator.mediaDevices ? typeof navigator.mediaDevices.getDisplayMedia : '无 mediaDevices',
				捕获中: !!STATE.stream,
				帧率: STATE.fps,
				源信息: STATE.captureInfo,
				舞台定位: found.via,
				错误: STATE.lastError
			},
			权限: perms,
			本机连接: {
				状态: STATE.wsStatus,
				往返p50: STATE.rtt,
				握手: STATE.welcome
					? {
						实例: STATE.welcome.instance,
						build: STATE.welcome.build,
						端口: STATE.welcome.port,
						世界: STATE.welcome.world,
						opcode数: Array.isArray(STATE.welcome.opcodes) ? STATE.welcome.opcodes.length : 0,
						帽子数: Array.isArray(STATE.welcome.hats) ? STATE.welcome.hats.length : 0,
						帽子表: STATE.welcome.hats
					}
					: null
			}
		};
	}

	function formatReport(report) {
		const lines = [];
		for (const [title, obj] of Object.entries(report)) {
			lines.push('【' + title + '】');
			for (const [k, v] of Object.entries(obj || {})) {
				lines.push('  ' + k + ': ' + (v && typeof v === 'object' ? JSON.stringify(v) : v));
			}
		}
		return lines.join('\n');
	}

	async function doShowDiag() {
		const report = await buildReport();
		const text = formatReport(report);
		try {
			console.log('[turbomc-probe]', report);
		} catch (e) {
			// 忽略
		}
		setPanelText('诊断\n\n' + text, true);
		return text;
	}

	// ---- 积木 ----

	class TurboMCProbe {
		getInfo() {
			return {
				id: 'turbomcprobe',
				name: 'TurboMC 画面探针',
				color1: '#B04C9A',
				color2: '#8E3A7C',
				blocks: [
					{opcode: 'captureButton', blockType: Scratch.BlockType.BUTTON, text: '开始捕获（点我）', func: 'captureButton'},
					{opcode: 'stopButton', blockType: Scratch.BlockType.BUTTON, text: '停止捕获', func: 'stopButton'},
					{opcode: 'diagButton', blockType: Scratch.BlockType.BUTTON, text: '诊断', func: 'diagButton'},
					{opcode: 'startCapture', blockType: Scratch.BlockType.COMMAND, text: '开始捕获画面'},
					{opcode: 'stopCapture', blockType: Scratch.BlockType.COMMAND, text: '停止捕获画面'},
					{opcode: 'isCapturing', blockType: Scratch.BlockType.BOOLEAN, text: '画面捕获中？'},
					{opcode: 'fps', blockType: Scratch.BlockType.REPORTER, text: '画面帧率'},
					{opcode: 'lastError', blockType: Scratch.BlockType.REPORTER, text: '最近错误'},
					{opcode: 'diag', blockType: Scratch.BlockType.REPORTER, text: '诊断报告'},
					{
						opcode: 'waitTest',
						blockType: Scratch.BlockType.COMMAND,
						text: '等待 [SECONDS] 秒（Promise 测试）',
						arguments: {
							SECONDS: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0.5}
						}
					},
					{opcode: 'runtimeShape', blockType: Scratch.BlockType.REPORTER, text: '运行时结构'},
					{opcode: 'threadCount', blockType: Scratch.BlockType.REPORTER, text: '线程数'},
					{opcode: 'snapSummary', blockType: Scratch.BlockType.REPORTER, text: '快照摘要'},
					{
						opcode: 'remoteCall',
						blockType: Scratch.BlockType.COMMAND,
						text: '远程调用 [OPCODE] 参数 [ARGV]',
						arguments: {
							OPCODE: {type: Scratch.ArgumentType.STRING, defaultValue: 'turbomc_key'},
							// 枚举参数要用菜单的 value：按键状态的「按下/松开」实际是 "true"/"false"
							ARGV: {type: Scratch.ArgumentType.STRING, defaultValue: '["w","true"]'}
						}
					},
					{opcode: 'lastCallResult', blockType: Scratch.BlockType.REPORTER, text: '上次调用结果'},
					{
						opcode: 'connect',
						blockType: Scratch.BlockType.COMMAND,
						text: '连接本机端口 [PORT]',
						arguments: {
							PORT: {type: Scratch.ArgumentType.NUMBER, defaultValue: 35671}
						}
					},
					{opcode: 'disconnect', blockType: Scratch.BlockType.COMMAND, text: '断开本机连接'},
					{opcode: 'wsStatus', blockType: Scratch.BlockType.REPORTER, text: '连接状态'},
					{opcode: 'rtt', blockType: Scratch.BlockType.REPORTER, text: '往返毫秒'}
				]
			};
		}

		// 按钮：点击即用户手势，最可靠的捕获触发方式
		async captureButton() {
			await doStartCapture();
		}

		stopButton() {
			doStopCapture();
		}

		async diagButton() {
			await doShowDiag();
		}

		async startCapture() {
			await doStartCapture();
		}

		stopCapture() {
			doStopCapture();
		}

		isCapturing() {
			return !!STATE.stream;
		}

		fps() {
			return STATE.fps;
		}

		lastError() {
			return STATE.lastError || '';
		}

		async diag() {
			return formatReport(await buildReport());
		}

		/**
		 * V-01 验证：返回 Promise 的命令积木，在 TurboWarp 编译模式下是否仍会阻塞线程。
		 *
		 * <p>用法：`重复 5 次 { 等待 0.5 秒; 计数 +1 }`。
		 * 正常等待 → 总耗时约 2.5 秒；只支持解释模式 → 瞬间跑完（Promise 被忽略）。</p>
		 */
		async waitTest(args) {
			const seconds = Math.max(0.05, Math.min(10, Number(args.SECONDS) || 0.5));
			const t0 = performance.now();
			await new Promise((resolve) => setTimeout(resolve, seconds * 1000));
			STATE.lastWaitMs = +(performance.now() - t0).toFixed(1);
			return '';
		}

		/**
		 * 运行时结构：判断 `Scratch.vm` 是不是跨线程代理。
		 *
		 * <p>若是代理，`targets` / `threads` 拿不到真数组，§8.1 的「遍历 targets 读 inputs.MATCH」
		 * 就无法同步实现，聊天帽子的匹配方式要另想办法。</p>
		 */
		runtimeShape() {
			return describeRuntime();
		}

		threadCount() {
			try {
				const rt = Scratch.vm && Scratch.vm.runtime;
				return rt && Array.isArray(rt.threads) ? rt.threads.length : -1;
			} catch (e) {
				return -1;
			}
		}

		/** 最后一帧快照的完整 JSON，用于核对字段是否齐全。 */
		snapSummary() {
			if (!STATE.lastSnap) {
				return '（还没有快照，先连上）';
			}
			return '帧数=' + STATE.snapCount + ' ' + JSON.stringify(STATE.lastSnap);
		}

		/**
		 * 通用远程调用：验证「写」链路。
		 *
		 * <p>示例：{@code turbomc_pressKey} + {@code ["key.jump","down"]} 让角色起跳；
		 * {@code turbomc_chat} + {@code ["hello"]} 发一条聊天。</p>
		 */
		async remoteCall(args) {
			if (!STATE.ws || STATE.ws.readyState !== 1) {
				STATE.lastCall = '未连接';
				return '';
			}
			let argv;
			try {
				argv = JSON.parse(args.ARGV || '[]');
			} catch (e) {
				STATE.lastCall = '参数不是合法 JSON：' + args.ARGV;
				return '';
			}
			if (!Array.isArray(argv)) {
				argv = [argv];
			}
			const id = String(++STATE.callSeq);
			const reply = await new Promise((resolve) => {
				STATE.pending[id] = resolve;
				try {
					STATE.ws.send(JSON.stringify({t: 'call', id, op: args.OPCODE, argv}));
				} catch (e) {
					delete STATE.pending[id];
					resolve({ok: false, err: describeError(e)});
					return;
				}
				setTimeout(() => {
					if (STATE.pending[id]) {
						delete STATE.pending[id];
						resolve(null);
					}
				}, 3000);
			});
			STATE.lastCall = reply || '超时';
			return '';
		}

		lastCallResult() {
			if (!STATE.lastCall) return '（还没有调用）';
			return typeof STATE.lastCall === 'string'
				? STATE.lastCall
				: JSON.stringify(STATE.lastCall);
		}

		async connect(args) {
			await doConnect(args.PORT);
		}

		disconnect() {
			doDisconnect();
		}

		wsStatus() {
			return STATE.wsStatus;
		}

		rtt() {
			return STATE.rtt === null ? 0 : STATE.rtt;
		}
	}

	Scratch.extensions.register(new TurboMCProbe());

	// 加载即显示面板，避免用户找不到入口
	setTimeout(() => refreshPanel('已加载，点「开始捕获」'), 300);
})(Scratch);
