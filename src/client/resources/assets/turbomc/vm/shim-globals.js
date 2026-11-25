/* Rhino 侧的全局垫片：补齐 scratch-vm 依赖但 Rhino 缺失的运行环境。
   必须在 vm.es5.js 之前 load。 */

var global = this;

/* ---- console ---- */
if (typeof console === 'undefined') {
	var console = {
		log: function () { print(Array.prototype.join.call(arguments, ' ')); },
		warn: function () { print('[warn] ' + Array.prototype.join.call(arguments, ' ')); },
		error: function () { print('[error] ' + Array.prototype.join.call(arguments, ' ')); },
		info: function () { print(Array.prototype.join.call(arguments, ' ')); },
		debug: function () {}
	};
}

/* ---- 定时器：单线程同步泵，按到期时间顺序执行 ---- */
var __timers = [];
/* 稳定 id：数组会因执行/取消而 splice 压缩，不能再用下标当句柄 */
var __timerSeq = 0;
var setTimeout = function (fn, ms) {
	__timerSeq++;
	__timers.push({id: __timerSeq, fn: fn, at: Date.now() + (ms || 0)});
	return __timerSeq;
};
var clearTimeout = function (id) {
	for (var i = 0; i < __timers.length; i++) {
		if (__timers[i].id === id) { __timers.splice(i, 1); return; }
	}
};
/* setInterval 不提供：scratch-vm 里唯一的裸用点是 Runtime.start() 的 setInterval 步进，
   而本项目由 Java 侧直接调 runtime._step()，从不调 runtime.start()；
   其余用到 setInterval 的地方都是蓝牙设备（window.setInterval），本 VM 不会走到。
   留一个「只执行一次」的实现反而会让依赖它的代码静默失效。 */

/** 执行最早到期的一个任务；返回是否还有任务。忽略真实时间，避免忙等卡死。 */
function __pumpTimerOnce() {
	var idx = -1;
	for (var i = 0; i < __timers.length; i++) {
		if (__timers[i].fn) {
			if (idx < 0 || __timers[i].at < __timers[idx].at) { idx = i; }
		}
	}
	if (idx < 0) { return false; }
	var task = __timers[idx];
	// splice 而不是置 null：长跑时数组不再单调增长、每次泵也不必扫过一长串空洞
	__timers.splice(idx, 1);
	try {
		task.fn();
	} catch (e) {
		print('[timer] error: ' + e);
	}
	return true;
}

/** 泵到 Promise 落定或轮次耗尽：微任务优先，其次定时器任务。 */
function __waitPromise(promise, maxRounds) {
	var done = false, value, failure;
	promise.then(function (v) { done = true; value = v; }, function (e) { done = true; failure = e; });
	var rounds = 0;
	var limit = maxRounds || 100000;
	while (!done && rounds++ < limit) {
		var progressed = 0;
		if (typeof __flushMicro === 'function') { progressed = __flushMicro(); }
		if (!progressed) {
			if (!__pumpTimerOnce()) { break; }
		}
	}
	if (failure) { throw failure; }
	if (!done) { throw new Error('Promise did not settle within ' + limit + ' rounds'); }
	return value;
}

/* ---- process ---- */
if (typeof process === 'undefined') {
	var process = {
		env: {NODE_ENV: 'production'},
		browser: false,
		nextTick: function (fn) { __timers.push({fn: fn, at: 0}); },
		version: 'rhino'
	};
}

/* ---- 其他杂项 ---- */
if (typeof globalThis === 'undefined') { var globalThis = global; }
if (typeof performance === 'undefined') {
	var performance = {
		now: function () { return Date.now(); },
		mark: function () {},
		measure: function () {},
		clearMarks: function () {},
		clearMeasures: function () {},
		getEntries: function () { return []; },
		getEntriesByName: function () { return []; },
		getEntriesByType: function () { return []; }
	};
}
