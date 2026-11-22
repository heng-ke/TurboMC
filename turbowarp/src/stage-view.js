/*
 * 远程调试的「画面」：浏览器原生屏幕/窗口捕获，覆盖在 TurboWarp 舞台上方。
 *
 * 为什么不用游戏端推流（FBO → JPEG → WebSocket）：同机开发时游戏窗口就在旁边，
 * 推流是把眼前的东西搬进浏览器，却要付掉帧、带宽与一整套 1.21.11 渲染 API 的风险。
 * `getDisplayMedia` 由系统合成器直接交给浏览器，游戏端零代码、零带宽（原方案见 ARCHITECTURE.md §7.8）。
 *
 * 由 tools/build-extension.js 拼进编辑器产物；运行时副本（游戏内）不包含本文件。
 */
function createStageView(Scratch) {
	'use strict';

	const STORAGE_KEY = 'turbomc.remote.view';
	const MODE_VIDEO = 'video';
	const MODE_STAGE = 'stage';

	const state = {
		/** @type {HTMLVideoElement|null} */
		video: null,
		/** @type {MediaStream|null} */
		stream: null,
		/** 覆盖层当前挂在哪个宿主元素下 */
		host: null,
		mode: loadMode(),
		lastError: '',
		/** 捕获源的原始分辨率，仅用于面板显示 */
		source: null,
		observer: null,
		timer: null
	};

	function loadMode() {
		try {
			return localStorage.getItem(STORAGE_KEY) === MODE_STAGE ? MODE_STAGE : MODE_VIDEO;
		} catch (e) {
			return MODE_VIDEO;
		}
	}

	function saveMode(mode) {
		try {
			localStorage.setItem(STORAGE_KEY, mode);
		} catch (e) {
			// 隐私模式写不进去：本次会话内仍然生效
		}
	}

	// ---- 舞台定位 ----
	// 与探针同一套：优先 runtime.renderer.canvas，拿不到再退回「页面最大画布」。
	// 实测自建站与官方站都能拿到真实 DOM（renderer 不在 worker 里）。

	function findStageCanvas() {
		try {
			const renderer = Scratch.vm && Scratch.vm.runtime ? Scratch.vm.runtime.renderer : null;
			const canvas = renderer ? renderer.canvas : null;
			if (canvas && typeof canvas.getBoundingClientRect === 'function' && canvas.width > 0) {
				return canvas;
			}
		} catch (e) {
			// 落到 DOM 兜底
		}
		const list = Array.prototype.slice.call(document.querySelectorAll('canvas'))
			.filter((c) => c.width > 100 && c.height > 100);
		list.sort((a, b) => (b.width * b.height) - (a.width * a.height));
		return list[0] || null;
	}

	/** 把 video 对齐到舞台画布；舞台找不到时固定一块区域，至少能看到画面。 */
	function place() {
		const video = state.video;
		if (!video || !state.stream) {
			return;
		}
		const canvas = findStageCanvas();
		const style = video.style;
		style.objectFit = 'contain';
		style.background = '#000';
		style.pointerEvents = 'none';
		style.zIndex = '999';
		style.display = state.mode === MODE_STAGE ? 'none' : 'block';

		if (!canvas) {
			if (video.parentElement !== document.body) {
				document.body.appendChild(video);
			}
			state.host = document.body;
			style.position = 'fixed';
			style.left = '40px';
			style.top = '40px';
			style.width = '640px';
			style.height = '360px';
			return;
		}

		const host = canvas.parentElement || document.body;
		if (state.host !== host) {
			if (video.parentElement) {
				video.parentElement.removeChild(video);
			}
			host.appendChild(video);
			state.host = host;
			// 绝对定位需要一个定位上下文；宿主是 static 时补上
			if (getComputedStyle(host).position === 'static') {
				host.style.position = 'relative';
			}
			watchStage(canvas);
		}
		const cr = canvas.getBoundingClientRect();
		const pr = host.getBoundingClientRect();
		style.position = 'absolute';
		style.left = (cr.left - pr.left) + 'px';
		style.top = (cr.top - pr.top) + 'px';
		style.width = cr.width + 'px';
		style.height = cr.height + 'px';
	}

	/**
	 * 跟随布局变化。`ResizeObserver` 管画布尺寸，窗口 resize 管滚动与缩放；
	 * 再加一个低频兜底，处理「面板开合导致舞台位移」这类观察不到的情况。
	 */
	function watchStage(canvas) {
		if (state.observer) {
			state.observer.disconnect();
		}
		if (typeof ResizeObserver === 'function') {
			state.observer = new ResizeObserver(() => place());
			state.observer.observe(canvas);
		}
		if (!state.timer) {
			state.timer = setInterval(() => {
				if (state.stream) {
					place();
				}
			}, 500);
		}
	}

	// ---- 捕获 ----

	function describeError(error) {
		if (!error) {
			return '未知错误';
		}
		if (error.name === 'NotAllowedError') {
			return '被拒绝或没有用户手势：再点一次按钮并允许共享';
		}
		if (error.name === 'NotSupportedError') {
			return '此环境不支持屏幕捕获（Desktop 版没有实现 setDisplayMediaRequestHandler）';
		}
		return (error.name || 'Error') + ': ' + (error.message || String(error));
	}

	/**
	 * 开始捕获。**必须由用户手势调用**（按钮点击），页面加载时自动调用会被浏览器拒绝。
	 */
	async function start() {
		if (state.stream) {
			return {ok: true, already: true};
		}
		if (!navigator.mediaDevices || typeof navigator.mediaDevices.getDisplayMedia !== 'function') {
			state.lastError = '当前环境没有 getDisplayMedia';
			return {ok: false, error: state.lastError};
		}
		state.lastError = '';
		try {
			// displaySurface: 'window' 只是偏好；用户仍可选整个屏幕
			const stream = await navigator.mediaDevices.getDisplayMedia({
				video: {frameRate: {ideal: 30, max: 60}, displaySurface: 'window'},
				audio: false
			});
			state.stream = stream;

			const video = document.createElement('video');
			video.autoplay = true;
			video.playsInline = true;
			video.muted = true;
			video.srcObject = stream;
			state.video = video;

			const track = stream.getVideoTracks()[0];
			const settings = track && track.getSettings ? track.getSettings() : {};
			state.source = {
				width: settings.width || 0,
				height: settings.height || 0,
				frameRate: settings.frameRate || 0,
				label: track && track.label ? track.label : ''
			};

			// 用户在浏览器原生「停止共享」里点了结束：跟着收尾
			if (track) {
				track.addEventListener('ended', () => stop());
			}

			state.mode = loadMode();
			place();
			try {
				await video.play();
			} catch (e) {
				// 自动播放被拦：无声音的 video 一般在用户手势后可以播放，失败也不致命
			}
			return {ok: true};
		} catch (error) {
			state.lastError = describeError(error);
			state.stream = null;
			return {ok: false, error: state.lastError};
		}
	}

	function stop() {
		const stream = state.stream;
		state.stream = null;
		if (stream) {
			stream.getTracks().forEach((track) => {
				try {
					track.stop();
				} catch (e) {
					// 已停止
				}
			});
		}
		if (state.video && state.video.parentElement) {
			state.video.parentElement.removeChild(state.video);
		}
		if (state.observer) {
			state.observer.disconnect();
			state.observer = null;
		}
		if (state.timer) {
			clearInterval(state.timer);
			state.timer = null;
		}
		state.video = null;
		state.host = null;
		state.source = null;
	}

	/** 画面 ↔ 舞台。舞台模式下隐藏覆盖层，原生舞台与变量监视器完全可见。 */
	function setMode(mode) {
		state.mode = mode === MODE_STAGE ? MODE_STAGE : MODE_VIDEO;
		saveMode(state.mode);
		if (state.video) {
			state.video.style.display = state.mode === MODE_STAGE ? 'none' : 'block';
			if (state.mode === MODE_VIDEO) {
				place();
			}
		}
		return state.mode;
	}

	function toggleMode() {
		return setMode(state.mode === MODE_STAGE ? MODE_VIDEO : MODE_STAGE);
	}

	return {
		start: start,
		stop: stop,
		setMode: setMode,
		toggleMode: toggleMode,
		isCapturing: () => !!state.stream,
		getMode: () => state.mode,
		lastError: () => state.lastError,
		source: () => state.source,
		place: place
	};
}
