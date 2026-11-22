/*
 * JSON 积木的唯一实现（浏览器编辑器与游戏内 Rhino 共用同一份源码）。
 *
 * <p><b>为什么是 ES5</b>：这份文件有两个宿主——TurboWarp 浏览器（V8，什么语法都吃）
 * 与内嵌 Rhino 1.7.15（{@code optimizationLevel=-1} 纯解释执行）。Rhino 1.7.15 不支持
 * {@code class}，{@code const} 在同一函数内重复声明还会直接报错，所以这里只写 ES5：
 * 浏览器跑 ES5 没有任何问题，反过来 Rhino 跑 ES6 会直接起不来。</p>
 *
 * <p><b>为什么不再有 Java 副本</b>：reporter 在 Scratch 里是同步求值，浏览器不可能为了一次读数
 * 去等 RPC，所以这批纯计算积木只能在 JS 里实现；两处各写一份就是两份实现、两份维护。
 * 现在只保留这一份：编辑器直接调用，游戏内由 vm-bootstrap 装到扩展对象上。
 * 与 Java 的历史基线仍可用 tools/json-parity-cases.json 逐条核对（{@code build/json-parity-java.json}
 * 是迁移前生成的 golden，用来证明改写没有改变行为）。</p>
 *
 * <p>由 tools/build-extension.js 拼进编辑器产物，并原样复制为运行时副本
 * {@code assets/turbomc/vm/json-core.es5.js}。</p>
 */
function createJsonLite() {
	'use strict';

	/** 与历史 Java 侧 MAX_DEPTH 一致：递归下降不设上限会被深层输入打穿栈。 */
	var MAX_DEPTH = 256;

	// ES5 没有 Number.isNaN / isFinite / isInteger / Math.trunc，统一在这里补。
	// 入参都已是 number，所以全局 isNaN / isFinite 的隐式转换不影响语义。
	function isNaNValue(value) {
		return value !== value;
	}

	function isFiniteValue(value) {
		return typeof value === 'number' && isFinite(value);
	}

	function isIntegerValue(value) {
		return isFinite(value) && Math.floor(value) === value;
	}

	/** Math.trunc 的 ES5 等价；只在这里用于切片下标，入参已是 number。 */
	function truncate(value) {
		return value < 0 ? Math.ceil(value) : Math.floor(value);
	}

	// ---- Scratch 值转换（对齐 ScratchCast） ----

	function toStr(value) {
		if (value === null || value === undefined) {
			return '';
		}
		if (typeof value === 'boolean') {
			return value ? 'true' : 'false';
		}
		return String(value);
	}

	/** 对齐 ScratchCast.rawNumber：解析不出给 NaN，不在这里归一成 0。 */
	function rawNumber(value) {
		if (typeof value === 'number') {
			return value;
		}
		if (typeof value === 'boolean') {
			return value ? 1 : 0;
		}
		if (value === null || value === undefined) {
			return 0;
		}
		var text = String(value).trim();
		if (text === '') {
			return 0;
		}
		var lower = text.toLowerCase();
		if (lower === 'infinity' || text === '+Infinity') {
			return Infinity;
		}
		if (lower === '-infinity') {
			return -Infinity;
		}
		if (lower === 'nan') {
			return NaN;
		}
		var hexadecimal;
		if (/^0[xX]/.test(text)) {
			hexadecimal = parseInt(text.slice(2), 16);
			return isNaNValue(hexadecimal) ? NaN : hexadecimal;
		}
		var parsed = Number(text);
		return isNaNValue(parsed) ? NaN : parsed;
	}

	/** 对齐 ScratchCast.toNumber：NaN 归 0。 */
	function toNum(value) {
		var number = rawNumber(value);
		return isNaNValue(number) ? 0 : number;
	}

	function isBlankText(value) {
		if (typeof value === 'number') {
			return false;
		}
		return toStr(value).trim() === '';
	}

	/** 对齐 ScratchCast.compare：两边都能当数字比数值，否则不分大小写比字典序。 */
	function castCompare(left, right) {
		var a = rawNumber(left);
		var b = rawNumber(right);
		if (a === 0 && isBlankText(left)) {
			a = NaN;
		} else if (b === 0 && isBlankText(right)) {
			b = NaN;
		}
		if (isNaNValue(a) || isNaNValue(b)) {
			var la = toStr(left).toLowerCase();
			var lb = toStr(right).toLowerCase();
			return la < lb ? -1 : (la > lb ? 1 : 0);
		}
		if ((a === Infinity && b === Infinity) || (a === -Infinity && b === -Infinity)) {
			return 0;
		}
		var diff = a - b;
		return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
	}

	// ---- 严格 JSON 解析（逐条对齐原 JsonText.Parser 的行为） ----
	//
	// 不用 JSON.parse 的原因只有一个：错误信息要逐字一致。
	// 「是不是合法 JSON」这块积木的返回值是给人看的，报错文案必须固定。

	function ParserError(message, position) {
		this.message = message;
		this.position = position;
	}

	function Parser(text) {
		this.text = text;
		this.pos = 0;
	}

	Parser.prototype.error = function (message) {
		return new ParserError(message, this.pos);
	};

	Parser.prototype.atEnd = function () {
		return this.pos >= this.text.length;
	};

	Parser.prototype.skipWhitespace = function () {
		while (this.pos < this.text.length) {
			var c = this.text.charAt(this.pos);
			if (c === ' ' || c === '\t' || c === '\n' || c === '\r') {
				this.pos++;
			} else {
				break;
			}
		}
	};

	Parser.prototype.readValue = function (depth) {
		if (depth > MAX_DEPTH) {
			throw this.error('JSON 嵌套过深');
		}
		this.skipWhitespace();
		if (this.atEnd()) {
			throw this.error('JSON 提前结束');
		}
		var c = this.text.charAt(this.pos);
		switch (c) {
			case '{':
				return this.readObject(depth);
			case '[':
				return this.readArray(depth);
			case '"':
				return this.readString();
			case 't':
				return this.readLiteral('true', true);
			case 'f':
				return this.readLiteral('false', false);
			case 'n':
				return this.readLiteral('null', null);
			default:
				return this.readNumber();
		}
	};

	Parser.prototype.readLiteral = function (literal, value) {
		if (this.text.substr(this.pos, literal.length) !== literal) {
			throw this.error('非法字面量');
		}
		this.pos += literal.length;
		return value;
	};

	Parser.prototype.readObject = function (depth) {
		var object = {};
		this.pos++;
		this.skipWhitespace();
		if (!this.atEnd() && this.text.charAt(this.pos) === '}') {
			this.pos++;
			return object;
		}
		while (true) {
			this.skipWhitespace();
			if (this.atEnd() || this.text.charAt(this.pos) !== '"') {
				throw this.error('键必须是双引号字符串');
			}
			var key = this.readString();
			this.skipWhitespace();
			if (this.atEnd() || this.text.charAt(this.pos) !== ':') {
				throw this.error('缺少冒号');
			}
			this.pos++;
			// 重复键以最后一次为准，与 JSON.parse 一致
			object[key] = this.readValue(depth + 1);
			this.skipWhitespace();
			if (this.atEnd()) {
				throw this.error('对象未闭合');
			}
			var c = this.text.charAt(this.pos);
			if (c === ',') {
				this.pos++;
				this.skipWhitespace();
				if (!this.atEnd() && this.text.charAt(this.pos) === '}') {
					throw this.error('不允许尾随逗号');
				}
				continue;
			}
			if (c === '}') {
				this.pos++;
				return object;
			}
			throw this.error('对象中出现非法字符');
		}
	};

	Parser.prototype.readArray = function (depth) {
		var array = [];
		this.pos++;
		this.skipWhitespace();
		if (!this.atEnd() && this.text.charAt(this.pos) === ']') {
			this.pos++;
			return array;
		}
		while (true) {
			array.push(this.readValue(depth + 1));
			this.skipWhitespace();
			if (this.atEnd()) {
				throw this.error('数组未闭合');
			}
			var c = this.text.charAt(this.pos);
			if (c === ',') {
				this.pos++;
				this.skipWhitespace();
				if (!this.atEnd() && this.text.charAt(this.pos) === ']') {
					throw this.error('不允许尾随逗号');
				}
				continue;
			}
			if (c === ']') {
				this.pos++;
				return array;
			}
			throw this.error('数组中出现非法字符');
		}
	};

	Parser.prototype.readString = function () {
		this.pos++;
		var out = '';
		while (true) {
			if (this.atEnd()) {
				throw this.error('字符串未闭合');
			}
			var c = this.text.charAt(this.pos++);
			if (c === '"') {
				return out;
			}
			if (c.charCodeAt(0) < 0x20) {
				throw this.error('字符串里有未转义的控制字符');
			}
			if (c !== '\\') {
				out += c;
				continue;
			}
			if (this.atEnd()) {
				throw this.error('转义未完成');
			}
			var escape = this.text.charAt(this.pos++);
			switch (escape) {
				case '"':
					out += '"';
					break;
				case '\\':
					out += '\\';
					break;
				case '/':
					out += '/';
					break;
				case 'b':
					out += '\b';
					break;
				case 'f':
					out += '\f';
					break;
				case 'n':
					out += '\n';
					break;
				case 'r':
					out += '\r';
					break;
				case 't':
					out += '\t';
					break;
				case 'u':
					if (this.pos + 4 > this.text.length) {
						throw this.error('\\u 转义不完整');
					}
					var hex = this.text.substr(this.pos, 4);
					this.pos += 4;
					if (!/^[0-9a-fA-F]{4}$/.test(hex)) {
						throw this.error('\\u 转义非法');
					}
					out += String.fromCharCode(parseInt(hex, 16));
					break;
				default:
					throw this.error('非法转义');
			}
		}
	};

	Parser.prototype.readNumber = function () {
		var start = this.pos;
		if (!this.atEnd() && this.text.charAt(this.pos) === '-') {
			this.pos++;
		}
		if (this.atEnd()) {
			throw this.error('数字不完整');
		}
		var first = this.text.charAt(this.pos);
		if (first === '0') {
			this.pos++;
		} else if (first >= '1' && first <= '9') {
			while (!this.atEnd() && this.text.charAt(this.pos) >= '0' && this.text.charAt(this.pos) <= '9') {
				this.pos++;
			}
		} else {
			throw this.error('非法数字');
		}
		if (!this.atEnd() && this.text.charAt(this.pos) === '.') {
			this.pos++;
			if (this.atEnd() || !(this.text.charAt(this.pos) >= '0' && this.text.charAt(this.pos) <= '9')) {
				throw this.error('小数点后必须有数字');
			}
			while (!this.atEnd() && this.text.charAt(this.pos) >= '0' && this.text.charAt(this.pos) <= '9') {
				this.pos++;
			}
		}
		if (!this.atEnd() && (this.text.charAt(this.pos) === 'e' || this.text.charAt(this.pos) === 'E')) {
			this.pos++;
			if (!this.atEnd() && (this.text.charAt(this.pos) === '+' || this.text.charAt(this.pos) === '-')) {
				this.pos++;
			}
			if (this.atEnd() || !(this.text.charAt(this.pos) >= '0' && this.text.charAt(this.pos) <= '9')) {
				throw this.error('指数必须有数字');
			}
			while (!this.atEnd() && this.text.charAt(this.pos) >= '0' && this.text.charAt(this.pos) <= '9') {
				this.pos++;
			}
		}
		return Number(this.text.slice(start, this.pos));
	};

	/** 解析结果：{ok, value}。失败与 JSON 字面量 null 都算「没有值」。 */
	function parse(text) {
		if (text === null || text === undefined) {
			return {ok: false, value: undefined};
		}
		try {
			var parser = new Parser(text);
			var value = parser.readValue(0);
			parser.skipWhitespace();
			if (!parser.atEnd()) {
				return {ok: false, value: undefined};
			}
			if (value === null) {
				return {ok: false, value: undefined};
			}
			return {ok: true, value: value};
		} catch (e) {
			return {ok: false, value: undefined};
		}
	}

	function errorMessage(text) {
		if (text === null || text === undefined) {
			return '';
		}
		try {
			var parser = new Parser(text);
			parser.readValue(0);
			parser.skipWhitespace();
			if (!parser.atEnd()) {
				return 'JSON 解析失败：值后面还有内容（位置 ' + parser.pos + '）';
			}
		} catch (e) {
			if (e instanceof ParserError) {
				return 'JSON 解析失败：' + e.message + '（位置 ' + e.position + '）';
			}
			return 'JSON 解析失败';
		}
		if (!looksLikeContainer(text)) {
			return 'JSON 必须是对象或数组';
		}
		return '';
	}

	function looksLikeContainer(text) {
		if (text === null || text === undefined) {
			return false;
		}
		var body = String(text).trim();
		if (body.length < 2) {
			return false;
		}
		var first = body.charAt(0);
		var last = body.charAt(body.length - 1);
		return (first === '[' && last === ']') || (first === '{' && last === '}');
	}

	/** 等价 JSON.stringify（不缩进）；非有限数写成 null，与 JSON.stringify 一致。 */
	function stringify(value) {
		return JSON.stringify(value === undefined ? null : value);
	}

	function isContainer(value) {
		return value !== null && typeof value === 'object';
	}

	function toScratch(value) {
		if (value === null || value === undefined) {
			return '';
		}
		if (isContainer(value)) {
			return stringify(value);
		}
		return value;
	}

	function toWriteValue(raw) {
		var text = toStr(raw);
		if (looksLikeContainer(text)) {
			var parsed = parse(text);
			if (parsed.ok) {
				return parsed.value;
			}
		}
		return text;
	}

	/** Scratch 风格下标 → 0 基：1 是首项，-1 是末项，0 与越界给 -1。 */
	function arrayIndex(raw, size) {
		var number = toNum(raw);
		if (!isFiniteValue(number) || number !== Math.round(number) || number === 0) {
			return -1;
		}
		var index = number;
		if (index > 0) {
			index--;
		} else {
			index += size;
		}
		return index >= 0 && index < size ? index : -1;
	}

	/** JS 的 arr[i]=x：越界会把中间填成空洞，序列化后就是 null。 */
	function setArraySlot(array, index, value) {
		while (array.length <= index) {
			array.push(null);
		}
		array[index] = value;
	}

	function jsSliceIndex(raw, size) {
		var index = truncate(raw);
		if (index < 0) {
			index = Math.max(size + index, 0);
		}
		return Math.min(index, size);
	}

	/** 对齐 keyText：数字按 ECMAScript 格式化，对象退化 [object Object]。 */
	function keyText(value) {
		if (value === null || value === undefined) {
			return 'null';
		}
		if (Array.isArray(value)) {
			return value.map(keyText).join(',');
		}
		if (typeof value === 'object') {
			return '[object Object]';
		}
		if (typeof value === 'boolean') {
			return value ? 'true' : 'false';
		}
		return String(value);
	}

	/** 写入用的数组位置：1 基，允许越界；非整数或小于 1 给 -1。 */
	function writeSlot(raw) {
		var number = toNum(raw);
		if (!isFiniteValue(number) || number !== Math.round(number) || number < 1) {
			return -1;
		}
		return number - 1;
	}

	/** JS ===：只有同类型标量相等；对象/数组按引用比，永远不相等。 */
	function strictEquals(left, right) {
		if (isContainer(left) || isContainer(right)) {
			return false;
		}
		if (left === null || right === null || left === undefined || right === undefined) {
			return false;
		}
		return typeof left === typeof right && left === right;
	}

	function parseArray(text) {
		var parsed = parse(text);
		return parsed.ok && Array.isArray(parsed.value) ? parsed.value : null;
	}

	// ---- 积木实现 ----

	var BLOCKS = {};

	BLOCKS.jsonNew = function (a) {
		var kind = toStr(a[0]);
		if (kind === 'Object') {
			return '{}';
		}
		if (kind === 'Array') {
			return '[]';
		}
		return '';
	};

	BLOCKS.jsonLength = function (a) {
		var parsed = parse(toStr(a[0]));
		if (!parsed.ok) {
			return '';
		}
		if (Array.isArray(parsed.value)) {
			return parsed.value.length;
		}
		if (typeof parsed.value === 'object') {
			return Object.keys(parsed.value).length;
		}
		return '';
	};

	BLOCKS.jsonGet = function (a) {
		var parsed = parse(toStr(a[0]));
		var item = toStr(a[1]);
		if (!parsed.ok) {
			return '';
		}
		if (Array.isArray(parsed.value)) {
			var index = arrayIndex(item, parsed.value.length);
			return index < 0 ? '' : toScratch(parsed.value[index]);
		}
		if (typeof parsed.value === 'object') {
			return Object.prototype.hasOwnProperty.call(parsed.value, item)
				? toScratch(parsed.value[item]) : '';
		}
		return '';
	};

	BLOCKS.jsonSet = function (a) {
		var parsed = parse(toStr(a[0]));
		var item = toStr(a[1]);
		var value = toWriteValue(a[2]);
		if (!parsed.ok) {
			return '';
		}
		if (Array.isArray(parsed.value)) {
			var slot = writeSlot(item);
			if (slot >= 0) {
				setArraySlot(parsed.value, slot, value);
			}
			return stringify(parsed.value);
		}
		if (typeof parsed.value === 'object') {
			parsed.value[item] = value;
			return stringify(parsed.value);
		}
		return '';
	};

	BLOCKS.jsonDelete = function (a) {
		var parsed = parse(toStr(a[0]));
		var item = toStr(a[1]);
		if (!parsed.ok) {
			return '';
		}
		if (Array.isArray(parsed.value)) {
			var index = arrayIndex(item, parsed.value.length);
			if (index >= 0) {
				// 照搬 delete arr[i]：长度不变，该位置变 null
				parsed.value[index] = null;
			}
			return stringify(parsed.value);
		}
		if (typeof parsed.value === 'object') {
			delete parsed.value[item];
			return stringify(parsed.value);
		}
		return '';
	};

	BLOCKS.jsonIs = function (a) {
		// 只有字符串才可能「是合法 JSON」——数字 1 不是 JSON
		if (typeof a[0] !== 'string') {
			return false;
		}
		var text = a[0];
		var check = toStr(a[1]);
		if (check === 'valid') {
			return looksLikeContainer(text) && parse(text).ok;
		}
		if (!looksLikeContainer(text)) {
			return false;
		}
		var parsed = parse(text);
		if (!parsed.ok) {
			return false;
		}
		var array = Array.isArray(parsed.value);
		if (check === 'Object') {
			return !array;
		}
		if (check === 'Array') {
			return array;
		}
		return false;
	};

	BLOCKS.jsonHasKey = function (a) {
		var parsed = parse(toStr(a[0]));
		if (!parsed.ok || !isContainer(parsed.value)) {
			return false;
		}
		var key = keyText(toWriteValue(a[1]));
		if (Array.isArray(parsed.value)) {
			if (key === 'length') {
				return false;
			}
			return arrayIndex(key, parsed.value.length) >= 0;
		}
		return Object.prototype.hasOwnProperty.call(parsed.value, key);
	};

	BLOCKS.jsonGetPath = function (a) {
		var root = parse(toStr(a[0]));
		var path = parse(toStr(a[1]));
		if (!root.ok || !path.ok || !Array.isArray(path.value)) {
			return '';
		}
		var current = root.value;
		for (var i = 0; i < path.value.length; i++) {
			var segment = path.value[i];
			if (Array.isArray(current)) {
				var index = arrayIndex(toScratch(segment), current.length);
				if (index < 0) {
					return '';
				}
				current = current[index];
			} else if (isContainer(current)) {
				var key = keyText(segment);
				if (!Object.prototype.hasOwnProperty.call(current, key)) {
					return '';
				}
				current = current[key];
			} else {
				return '';
			}
		}
		return toScratch(current);
	};

	BLOCKS.jsonSetPath = function (a) {
		var root = parse(toStr(a[0]));
		var path = parse(toStr(a[1]));
		if (!root.ok || !path.ok || !Array.isArray(path.value)) {
			return '';
		}
		var segments = path.value;
		if (segments.length === 0) {
			return stringify(root.value);
		}
		var data = toWriteValue(a[2]);
		var current = root.value;
		for (var i = 0; i < segments.length; i++) {
			var segment = segments[i];
			var last = i === segments.length - 1;
			if (Array.isArray(current)) {
				var index = arrayIndex(toScratch(segment), current.length);
				if (index < 0) {
					return '';
				}
				if (last) {
					current[index] = data;
				} else {
					current = current[index];
				}
			} else if (isContainer(current)) {
				var key = keyText(segment);
				if (last) {
					current[key] = data;
				} else {
					if (!Object.prototype.hasOwnProperty.call(current, key)) {
						return '';
					}
					current = current[key];
				}
			} else {
				return '';
			}
		}
		return stringify(root.value);
	};

	BLOCKS.jsonMinify = function (a) {
		var parsed = parse(toStr(a[0]));
		return parsed.ok ? stringify(parsed.value) : '';
	};

	BLOCKS.jsonErrMsg = function (a) {
		return errorMessage(toStr(a[0]));
	};

	BLOCKS.jsonGetAll = function (a) {
		var parsed = parse(toStr(a[0]));
		if (!parsed.ok || !isContainer(parsed.value)) {
			return '';
		}
		var kind = toStr(a[1]);
		if (kind !== 'keys' && kind !== 'values' && kind !== 'datas') {
			return '';
		}
		var out = [];
		var add = function (key, value) {
			var safe = value === null || value === undefined ? '' : value;
			if (kind === 'keys') {
				out.push(key);
			} else if (kind === 'values') {
				out.push(safe);
			} else {
				out.push([key, safe]);
			}
		};
		if (Array.isArray(parsed.value)) {
			parsed.value.forEach(function (value, index) {
				add(String(index), value);
			});
		} else {
			Object.keys(parsed.value).forEach(function (key) {
				add(key, parsed.value[key]);
			});
		}
		return stringify(out);
	};

	BLOCKS.jsonArrayPush = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		array.push(toWriteValue(a[1]));
		return stringify(array);
	};

	BLOCKS.jsonArraySet = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var index = toNum(a[1]) - 1;
		if (isIntegerValue(index) && index >= 0) {
			setArraySlot(array, index, toWriteValue(a[2]));
		}
		return stringify(array);
	};

	BLOCKS.jsonArrayInsert = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var at = jsSliceIndex(toNum(a[1]) - 1, array.length);
		array.splice(at, 0, toWriteValue(a[2]));
		return stringify(array);
	};

	BLOCKS.jsonArrayDelete = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var at = jsSliceIndex(toNum(a[1]) - 1, array.length);
		if (at < array.length) {
			array.splice(at, 1);
		}
		return stringify(array);
	};

	BLOCKS.jsonArrayRemoveAll = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var target = toWriteValue(a[1]);
		for (var i = array.length - 1; i >= 0; i--) {
			if (strictEquals(array[i], target)) {
				array.splice(i, 1);
			}
		}
		return stringify(array);
	};

	BLOCKS.jsonArrayItemNum = function (a) {
		var array = parseArray(toStr(a[1]));
		if (array === null) {
			return '';
		}
		var target = toWriteValue(a[0]);
		for (var i = 0; i < array.length; i++) {
			if (strictEquals(array[i], target)) {
				return i + 1;
			}
		}
		return 0;
	};

	BLOCKS.jsonArrayFromTo = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var start = jsSliceIndex(toNum(a[1]) - 1, array.length);
		var end = jsSliceIndex(toNum(a[2]), array.length);
		if (end < start) {
			end = start;
		}
		return stringify(array.slice(start, end));
	};

	BLOCKS.jsonArrayConcat = function (a) {
		var left = parseArray(toStr(a[0]));
		var right = parseArray(toStr(a[1]));
		if (left === null || right === null) {
			return '';
		}
		return stringify(left.concat(right));
	};

	BLOCKS.jsonArrayMapField = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var key = toStr(a[1]);
		return stringify(array.map(function (element) {
			return isContainer(element) && !Array.isArray(element)
				&& Object.prototype.hasOwnProperty.call(element, key)
				? element[key] : null;
		}));
	};

	BLOCKS.jsonArraySetLen = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var raw = toNum(a[1]);
		if (!isIntegerValue(raw) || raw < 0) {
			return '';
		}
		array.length = raw;
		// 变大时 JS 留下空洞，序列化后是 null——这里显式补 null，避免空洞让 stringify 写成 null 之外的东西
		for (var i = 0; i < raw; i++) {
			if (array[i] === undefined) {
				array[i] = null;
			}
		}
		return stringify(array);
	};

	BLOCKS.jsonArrayCreate = function (a) {
		var delimiter = toStr(a[0]);
		var text = toStr(a[1]);
		if (delimiter === '') {
			// 按 UTF-16 代码单元拆，与原 Java 的 charAt 循环一致（Array.from 会按码点拆，反而不一致）
			return stringify(text.split(''));
		}
		return stringify(text.split(delimiter));
	};

	BLOCKS.jsonArrayJoin = function (a) {
		var delimiter = toStr(a[0]);
		var array = parseArray(toStr(a[1]));
		if (array === null) {
			return '';
		}
		return array.map(function (element) {
			return element === null || element === undefined ? '' : keyText(element);
		}).join(delimiter);
	};

	BLOCKS.jsonArraySort = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var items = array.slice();
		// 参与比较的取值：对象/数组退化为文本，null 当 0
		var compareValue = function (element) {
			if (element === null || element === undefined) {
				return 0;
			}
			if (isContainer(element)) {
				return keyText(element);
			}
			return element;
		};
		items.sort(function (left, right) {
			return castCompare(compareValue(left), compareValue(right));
		});
		if (toStr(a[1]) === 'descending') {
			items.reverse();
		}
		return stringify(items);
	};

	BLOCKS.jsonArrayTextFilter = function (a) {
		var root = parse(toStr(a[0]));
		if (!root.ok || !isContainer(root.value)) {
			return '';
		}
		var option = toStr(a[1]);
		var needle = toStr(a[2]);
		var shape = toStr(a[3]);
		var asObject = shape === 'Object';
		if (!asObject && shape !== 'Array') {
			return '';
		}
		var values = [];
		var mapped = {};
		var matches = function (element) {
			if (typeof element !== 'string') {
				return false;
			}
			if (option === 'includes') {
				return element.indexOf(needle) >= 0;
			}
			if (option === 'starts with') {
				return element.indexOf(needle) === 0;
			}
			if (option === 'ends with') {
				return needle.length <= element.length
					&& element.lastIndexOf(needle) === element.length - needle.length;
			}
			return false;
		};
		if (Array.isArray(root.value)) {
			root.value.forEach(function (element, index) {
				if (matches(element)) {
					values.push(element);
					mapped[String(index + 1)] = element;
				}
			});
		} else {
			Object.keys(root.value).forEach(function (key) {
				var element = root.value[key];
				if (matches(element)) {
					values.push(element);
					mapped[key] = element;
				}
			});
		}
		return stringify(asObject ? mapped : values);
	};

	BLOCKS.jsonArrayAnalysis = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return 0;
		}
		var kind = toStr(a[1]);
		var values = array.map(function (element) {
			return toNum(toScratch(element));
		});
		var sum = values.reduce(function (total, value) {
			return total + value;
		}, 0);
		var i;
		switch (kind) {
			case 'maximum': {
				var max = -Infinity;
				for (i = 0; i < values.length; i++) {
					if (values[i] > max) {
						max = values[i];
					}
				}
				return max;
			}
			case 'minimum': {
				var min = Infinity;
				for (i = 0; i < values.length; i++) {
					if (values[i] < min) {
						min = values[i];
					}
				}
				return min;
			}
			case 'sum':
				return sum;
			case 'average':
				return sum / values.length;
			case 'median': {
				if (values.length === 0) {
					return NaN;
				}
				var sorted = values.slice().sort(function (x, y) {
					return x < y ? -1 : (x > y ? 1 : 0);
				});
				var middle = Math.floor(sorted.length / 2);
				return sorted.length % 2 === 0
					? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
			}
			case 'mode': {
				// 键统一加前缀：数字转字符串后不会撞上 Object 原型上的名字。
				// -0 与 0 算同一个键（SameValueZero）；NaN 也归一到同一个键。
				var counts = {};
				var mode = 0;
				var best = 0;
				for (i = 0; i < values.length; i++) {
					var value = values[i];
					var key = '#' + String(value === 0 ? 0 : value);
					var count = (counts[key] || 0) + 1;
					counts[key] = count;
					if (count > best) {
						best = count;
						mode = value;
					}
				}
				return mode;
			}
			case 'variance': {
				var average = sum / values.length;
				var total = 0;
				for (i = 0; i < values.length; i++) {
					var delta = values[i] - average;
					total += delta * delta;
				}
				return total / values.length;
			}
			default:
				return 0;
		}
	};

	return {
		blocks: BLOCKS,
		/** 供对拍脚本直接调用：返回与 Java 侧同级的字符串化结果。 */
		run: function (opcode, args) {
			var fn = BLOCKS[opcode];
			return fn ? fn(args) : '';
		},
		toScratchString: toStr
	};
}
