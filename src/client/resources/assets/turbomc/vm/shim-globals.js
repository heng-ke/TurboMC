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
var setTimeout = function (fn, ms) {
	__timers.push({fn: fn, at: Date.now() + (ms || 0)});
	return __timers.length;
};
var clearTimeout = function (id) {
	if (id > 0 && __timers[id - 1]) { __timers[id - 1].fn = null; }
};
var setInterval = function (fn, ms) {
	__timers.push({fn: fn, at: Date.now() + (ms || 0), repeat: ms || 1});
	return __timers.length;
};
var clearInterval = clearTimeout;

/** 执行最早到期的一个任务；返回是否还有任务。忽略真实时间，避免忙等卡死。 */
function __pumpTimerOnce() {
	var idx = -1;
	for (var i = 0; i < __timers.length; i++) {
		if (__timers[i] && __timers[i].fn) {
			if (idx < 0 || __timers[i].at < __timers[idx].at) { idx = i; }
		}
	}
	if (idx < 0) { return false; }
	var task = __timers[idx];
	__timers[idx] = null;
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
