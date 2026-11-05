/* 最小 Promise 实现：专供 Rhino 单线程环境。
   与 es6-promise 的区别：微任务不依赖 setTimeout / MutationObserver / vertx，
   而是进 __micro 队列，由 __flushMicro() 在同步循环里清空。
   必须在 shim-globals.js 之后、vm.es5.js 之前 load。 */

var __micro = [];

function __enqueueMicro(fn) {
	__micro.push(fn);
}

/** 清空微任务队列；返回本轮执行的任务数。 */
function __flushMicro() {
	var count = 0;
	while (__micro.length > 0 && count < 1000000) {
		var fn = __micro.shift();
		count++;
		try {
			fn();
		} catch (e) {
			print('[micro] error: ' + e);
		}
	}
	return count;
}

(function () {
	function isFunction(x) { return typeof x === 'function'; }

	function MPromise(executor) {
		var self = this;
		self._state = 0;
		self._value = undefined;
		self._handlers = [];
		var settled = false;

		function settle(state, value) {
			if (settled) { return; }
			// 采纳 thenable
			if (state === 1 && value && (typeof value === 'object' || isFunction(value))) {
				var then;
				try {
					then = value.then;
				} catch (e) {
					settled = true;
					self._state = 2;
					self._value = e;
					flushHandlers(self);
					return;
				}
				if (isFunction(then)) {
					settled = true;
					try {
						then.call(value,
							function (v) { self._state = 0; self._value = undefined; settled = false; settle(1, v); },
							function (e) { self._state = 2; self._value = e; settled = true; flushHandlers(self); });
					} catch (e2) {
						self._state = 2;
						self._value = e2;
						flushHandlers(self);
					}
					return;
				}
			}
			settled = true;
			self._state = state;
			self._value = value;
			flushHandlers(self);
		}

		try {
			executor(function (v) { settle(1, v); }, function (e) { settle(2, e); });
		} catch (e) {
			settle(2, e);
		}
	}

	function flushHandlers(promise) {
		var handlers = promise._handlers;
		if (!handlers.length) { return; }
		promise._handlers = [];
		for (var i = 0; i < handlers.length; i++) {
			(function (h) {
				__enqueueMicro(function () { runHandler(promise, h); });
			})(handlers[i]);
		}
	}

	function runHandler(promise, handler) {
		var cb = promise._state === 1 ? handler.onFulfilled : handler.onRejected;
		if (!isFunction(cb)) {
			if (promise._state === 1) { handler.resolve(promise._value); }
			else { handler.reject(promise._value); }
			return;
		}
		try {
			handler.resolve(cb(promise._value));
		} catch (e) {
			handler.reject(e);
		}
	}

	MPromise.prototype.then = function (onFulfilled, onRejected) {
		var self = this;
		return new MPromise(function (resolve, reject) {
			var handler = {onFulfilled: onFulfilled, onRejected: onRejected, resolve: resolve, reject: reject};
			if (self._state === 0) {
				self._handlers.push(handler);
			} else {
				__enqueueMicro(function () { runHandler(self, handler); });
			}
		});
	};

	MPromise.prototype['catch'] = function (onRejected) {
		return this.then(null, onRejected);
	};

	MPromise.prototype['finally'] = function (fn) {
		return this.then(function (v) { fn(); return v; }, function (e) { fn(); throw e; });
	};

	MPromise.resolve = function (v) {
		if (v instanceof MPromise) { return v; }
		return new MPromise(function (resolve) { resolve(v); });
	};

	MPromise.reject = function (e) {
		return new MPromise(function (_, reject) { reject(e); });
	};

	MPromise.all = function (list) {
		return new MPromise(function (resolve, reject) {
			var arr = [];
			var remaining = list.length;
			if (remaining === 0) { resolve(arr); return; }
			for (var i = 0; i < list.length; i++) {
				(function (idx) {
					MPromise.resolve(list[idx]).then(function (v) {
						arr[idx] = v;
						remaining--;
						if (remaining === 0) { resolve(arr); }
					}, reject);
				})(i);
			}
		});
	};

	MPromise.race = function (list) {
		return new MPromise(function (resolve, reject) {
			for (var i = 0; i < list.length; i++) {
				MPromise.resolve(list[i]).then(resolve, reject);
			}
		});
	};

	Promise = MPromise;
	if (typeof global !== 'undefined') { global.Promise = MPromise; }
})();

if (typeof Promise === 'undefined') {
	print('[shim] Promise polyfill 未挂载到全局');
}
