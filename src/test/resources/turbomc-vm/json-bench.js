/*
 * jsonBench 专用的 VM 侧实现：与 Java 版同一套算法，用来回答
 * 「JSON 积木如果放在 VM 侧，Rhino 解释执行有多快」。
 * 不参与游戏运行，只有 JsonBench 会加载它。
 */
var __jsonBench = (function () {
	function isBlank(value) {
		return typeof value !== 'number' && String(value).trim().length === 0;
	}

	function compare(a, b) {
		var n1 = Number(a);
		var n2 = Number(b);
		if (n1 === 0 && isBlank(a)) {
			n1 = NaN;
		} else if (n2 === 0 && isBlank(b)) {
			n2 = NaN;
		}
		if (isNaN(n1) || isNaN(n2)) {
			var s1 = String(a).toLowerCase();
			var s2 = String(b).toLowerCase();
			return s1 < s2 ? -1 : (s1 > s2 ? 1 : 0);
		}
		if ((n1 === Infinity && n2 === Infinity) || (n1 === -Infinity && n2 === -Infinity)) {
			return 0;
		}
		var difference = n1 - n2;
		return difference < 0 ? -1 : (difference > 0 ? 1 : 0);
	}

	return {
		compare: compare,
		parse: function (text) {
			return JSON.parse(text);
		},
		minify: function (text) {
			return JSON.stringify(JSON.parse(text));
		},
		get: function (text, key) {
			var root = JSON.parse(text);
			var value = root[key];
			if (value === undefined || value === null) {
				return '';
			}
			return typeof value === 'object' ? JSON.stringify(value) : value;
		},
		sort: function (text) {
			var list = JSON.parse(text);
			list.sort(compare);
			return JSON.stringify(list);
		},
		noopLoop: function (times) {
			var last = '';
			for (var i = 0; i < times; i++) {
				last = __benchNoop();
			}
			return last;
		}
	};
})();
