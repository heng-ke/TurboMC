/*
 * JSON 积木的浏览器端实现（远程模式用）。
 *
 * 对拍目标：Java 侧 JsonActions / JsonText。两份实现必须逐条等价——
 * reporter 在 Scratch 里是同步求值，浏览器不可能为了一次读数去等 RPC，
 * 所以这批纯计算积木只能在浏览器里再实现一份。
 *
 * 防漂移靠对拍：tools/json-parity-cases.json 同一批用例分别喂给两边，
 * Java 侧输出 build/json-parity-java.json，tools/check-json-parity.js 逐条比对。
 *
 * 由 tools/build-extension.js 拼进编辑器产物；运行时副本（游戏内）不包含本文件。
 */
function createJsonLite() {
	'use strict';

	/** 与 Java 侧 MAX_DEPTH 一致：递归下降不设上限会被深层输入打穿栈。 */
	const MAX_DEPTH = 256;

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
		const text = String(value).trim();
		if (text === '') {
			return 0;
		}
		const lower = text.toLowerCase();
		if (lower === 'infinity' || text === '+Infinity') {
			return Infinity;
		}
		if (lower === '-infinity') {
			return -Infinity;
		}
		if (lower === 'nan') {
			return NaN;
		}
		if (/^0[xX]/.test(text)) {
			const parsed = Number.parseInt(text.slice(2), 16);
			return Number.isNaN(parsed) ? NaN : parsed;
		}
		const parsed = Number(text);
		return Number.isNaN(parsed) ? NaN : parsed;
	}

	/** 对齐 ScratchCast.toNumber：NaN 归 0。 */
	function toNum(value) {
		const number = rawNumber(value);
		return Number.isNaN(number) ? 0 : number;
	}

	function isBlankText(value) {
		if (typeof value === 'number') {
			return false;
		}
		return toStr(value).trim() === '';
	}

	/** 对齐 ScratchCast.compare：两边都能当数字比数值，否则不分大小写比字典序。 */
	function castCompare(left, right) {
		let a = rawNumber(left);
		let b = rawNumber(right);
		if (a === 0 && isBlankText(left)) {
			a = NaN;
		} else if (b === 0 && isBlankText(right)) {
			b = NaN;
		}
		if (Number.isNaN(a) || Number.isNaN(b)) {
			const la = toStr(left).toLowerCase();
			const lb = toStr(right).toLowerCase();
			return la < lb ? -1 : (la > lb ? 1 : 0);
		}
		if ((a === Infinity && b === Infinity) || (a === -Infinity && b === -Infinity)) {
			return 0;
		}
		const diff = a - b;
		return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
	}

	// ---- 严格 JSON 解析（逐条对齐 JsonText.Parser） ----
	//
	// 不用 JSON.parse 的原因只有一个：错误信息要逐字一致。
	// 「是不是合法 JSON」这块积木的返回值是给人看的，两边不同就等于两块不同的积木。

	class ParserError {
		constructor(message, position) {
			this.message = message;
			this.position = position;
		}
	}

	class Parser {
		constructor(text) {
			this.text = text;
			this.pos = 0;
		}

		error(message) {
			return new ParserError(message, this.pos);
		}

		atEnd() {
			return this.pos >= this.text.length;
		}

		skipWhitespace() {
			while (this.pos < this.text.length) {
				const c = this.text[this.pos];
				if (c === ' ' || c === '\t' || c === '\n' || c === '\r') {
					this.pos++;
				} else {
					break;
				}
			}
		}

		readValue(depth) {
			if (depth > MAX_DEPTH) {
				throw this.error('JSON 嵌套过深');
			}
			this.skipWhitespace();
			if (this.atEnd()) {
				throw this.error('JSON 提前结束');
			}
			const c = this.text[this.pos];
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
		}

		readLiteral(literal, value) {
			if (this.text.substr(this.pos, literal.length) !== literal) {
				throw this.error('非法字面量');
			}
			this.pos += literal.length;
			return value;
		}

		readObject(depth) {
			const object = {};
			this.pos++;
			this.skipWhitespace();
			if (!this.atEnd() && this.text[this.pos] === '}') {
				this.pos++;
				return object;
			}
			while (true) {
				this.skipWhitespace();
				if (this.atEnd() || this.text[this.pos] !== '"') {
					throw this.error('键必须是双引号字符串');
				}
				const key = this.readString();
				this.skipWhitespace();
				if (this.atEnd() || this.text[this.pos] !== ':') {
					throw this.error('缺少冒号');
				}
				this.pos++;
				// 重复键以最后一次为准，与 JSON.parse 一致
				object[key] = this.readValue(depth + 1);
				this.skipWhitespace();
				if (this.atEnd()) {
					throw this.error('对象未闭合');
				}
				const c = this.text[this.pos];
				if (c === ',') {
					this.pos++;
					this.skipWhitespace();
					if (!this.atEnd() && this.text[this.pos] === '}') {
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
		}

		readArray(depth) {
			const array = [];
			this.pos++;
			this.skipWhitespace();
			if (!this.atEnd() && this.text[this.pos] === ']') {
				this.pos++;
				return array;
			}
			while (true) {
				array.push(this.readValue(depth + 1));
				this.skipWhitespace();
				if (this.atEnd()) {
					throw this.error('数组未闭合');
				}
				const c = this.text[this.pos];
				if (c === ',') {
					this.pos++;
					this.skipWhitespace();
					if (!this.atEnd() && this.text[this.pos] === ']') {
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
		}

		readString() {
			this.pos++;
			let out = '';
			while (true) {
				if (this.atEnd()) {
					throw this.error('字符串未闭合');
				}
				const c = this.text[this.pos++];
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
				const escape = this.text[this.pos++];
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
					case 'u': {
						if (this.pos + 4 > this.text.length) {
							throw this.error('\\u 转义不完整');
						}
						const hex = this.text.substr(this.pos, 4);
						this.pos += 4;
						if (!/^[0-9a-fA-F]{4}$/.test(hex)) {
							throw this.error('\\u 转义非法');
						}
						out += String.fromCharCode(Number.parseInt(hex, 16));
						break;
					}
					default:
						throw this.error('非法转义');
				}
			}
		}

		readNumber() {
			const start = this.pos;
			if (!this.atEnd() && this.text[this.pos] === '-') {
				this.pos++;
			}
			if (this.atEnd()) {
				throw this.error('数字不完整');
			}
			const first = this.text[this.pos];
			if (first === '0') {
				this.pos++;
			} else if (first >= '1' && first <= '9') {
				while (!this.atEnd() && this.text[this.pos] >= '0' && this.text[this.pos] <= '9') {
					this.pos++;
				}
			} else {
				throw this.error('非法数字');
			}
			if (!this.atEnd() && this.text[this.pos] === '.') {
				this.pos++;
				if (this.atEnd() || !(this.text[this.pos] >= '0' && this.text[this.pos] <= '9')) {
					throw this.error('小数点后必须有数字');
				}
				while (!this.atEnd() && this.text[this.pos] >= '0' && this.text[this.pos] <= '9') {
					this.pos++;
				}
			}
			if (!this.atEnd() && (this.text[this.pos] === 'e' || this.text[this.pos] === 'E')) {
				this.pos++;
				if (!this.atEnd() && (this.text[this.pos] === '+' || this.text[this.pos] === '-')) {
					this.pos++;
				}
				if (this.atEnd() || !(this.text[this.pos] >= '0' && this.text[this.pos] <= '9')) {
					throw this.error('指数必须有数字');
				}
				while (!this.atEnd() && this.text[this.pos] >= '0' && this.text[this.pos] <= '9') {
					this.pos++;
				}
			}
			return Number(this.text.slice(start, this.pos));
		}
	}

	/** 解析结果：{ok, value}。失败与 JSON 字面量 null 都算「没有值」，与 Java 一致。 */
	function parse(text) {
		if (text === null || text === undefined) {
			return {ok: false, value: undefined};
		}
		try {
			const parser = new Parser(text);
			const value = parser.readValue(0);
			parser.skipWhitespace();
			if (!parser.atEnd()) {
				return {ok: false, value: undefined};
			}
			if (value === null) {
				return {ok: false, value: undefined};
			}
			return {ok: true, value};
		} catch (e) {
			return {ok: false, value: undefined};
		}
	}

	function errorMessage(text) {
		if (text === null || text === undefined) {
			return '';
		}
		try {
			const parser = new Parser(text);
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
		const body = String(text).trim();
		if (body.length < 2) {
			return false;
		}
		const first = body[0];
		const last = body[body.length - 1];
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
		const text = toStr(raw);
		if (looksLikeContainer(text)) {
			const parsed = parse(text);
			if (parsed.ok) {
				return parsed.value;
			}
		}
		return text;
	}

	/** Scratch 风格下标 → 0 基：1 是首项，-1 是末项，0 与越界给 -1。 */
	function arrayIndex(raw, size) {
		const number = toNum(raw);
		if (!Number.isFinite(number) || number !== Math.round(number) || number === 0) {
			return -1;
		}
		let index = number;
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
		let index = Math.trunc(raw);
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
		const number = toNum(raw);
		if (!Number.isFinite(number) || number !== Math.round(number) || number < 1) {
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
		const parsed = parse(text);
		return parsed.ok && Array.isArray(parsed.value) ? parsed.value : null;
	}

	function safeEntryValue(value) {
		return value === null || value === undefined ? '' : value;
	}

	// ---- 积木实现 ----

	const BLOCKS = {};

	BLOCKS.jsonNew = (a) => {
		const kind = toStr(a[0]);
		if (kind === 'Object') {
			return '{}';
		}
		if (kind === 'Array') {
			return '[]';
		}
		return '';
	};

	BLOCKS.jsonLength = (a) => {
		const parsed = parse(toStr(a[0]));
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

	BLOCKS.jsonGet = (a) => {
		const parsed = parse(toStr(a[0]));
		const item = toStr(a[1]);
		if (!parsed.ok) {
			return '';
		}
		if (Array.isArray(parsed.value)) {
			const index = arrayIndex(item, parsed.value.length);
			return index < 0 ? '' : toScratch(parsed.value[index]);
		}
		if (typeof parsed.value === 'object') {
			return Object.prototype.hasOwnProperty.call(parsed.value, item)
				? toScratch(parsed.value[item]) : '';
		}
		return '';
	};

	BLOCKS.jsonSet = (a) => {
		const parsed = parse(toStr(a[0]));
		const item = toStr(a[1]);
		const value = toWriteValue(a[2]);
		if (!parsed.ok) {
			return '';
		}
		if (Array.isArray(parsed.value)) {
			const slot = writeSlot(item);
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

	BLOCKS.jsonDelete = (a) => {
		const parsed = parse(toStr(a[0]));
		const item = toStr(a[1]);
		if (!parsed.ok) {
			return '';
		}
		if (Array.isArray(parsed.value)) {
			const index = arrayIndex(item, parsed.value.length);
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

	BLOCKS.jsonIs = (a) => {
		// 与 Java 一致：只有字符串才可能「是合法 JSON」——数字 1 不是 JSON
		if (typeof a[0] !== 'string') {
			return false;
		}
		const text = a[0];
		const check = toStr(a[1]);
		if (check === 'valid') {
			return looksLikeContainer(text) && parse(text).ok;
		}
		if (!looksLikeContainer(text)) {
			return false;
		}
		const parsed = parse(text);
		if (!parsed.ok) {
			return false;
		}
		const array = Array.isArray(parsed.value);
		if (check === 'Object') {
			return !array;
		}
		if (check === 'Array') {
			return array;
		}
		return false;
	};

	BLOCKS.jsonHasKey = (a) => {
		const parsed = parse(toStr(a[0]));
		if (!parsed.ok || !isContainer(parsed.value)) {
			return false;
		}
		const key = keyText(toWriteValue(a[1]));
		if (Array.isArray(parsed.value)) {
			if (key === 'length') {
				return false;
			}
			return arrayIndex(key, parsed.value.length) >= 0;
		}
		return Object.prototype.hasOwnProperty.call(parsed.value, key);
	};

	BLOCKS.jsonGetPath = (a) => {
		const root = parse(toStr(a[0]));
		const path = parse(toStr(a[1]));
		if (!root.ok || !path.ok || !Array.isArray(path.value)) {
			return '';
		}
		let current = root.value;
		for (const segment of path.value) {
			if (Array.isArray(current)) {
				const index = arrayIndex(toScratch(segment), current.length);
				if (index < 0) {
					return '';
				}
				current = current[index];
			} else if (isContainer(current)) {
				const key = keyText(segment);
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

	BLOCKS.jsonSetPath = (a) => {
		const root = parse(toStr(a[0]));
		const path = parse(toStr(a[1]));
		if (!root.ok || !path.ok || !Array.isArray(path.value)) {
			return '';
		}
		const segments = path.value;
		if (segments.length === 0) {
			return stringify(root.value);
		}
		const data = toWriteValue(a[2]);
		let current = root.value;
		for (let i = 0; i < segments.length; i++) {
			const segment = segments[i];
			const last = i === segments.length - 1;
			if (Array.isArray(current)) {
				const index = arrayIndex(toScratch(segment), current.length);
				if (index < 0) {
					return '';
				}
				if (last) {
					current[index] = data;
				} else {
					current = current[index];
				}
			} else if (isContainer(current)) {
				const key = keyText(segment);
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

	BLOCKS.jsonMinify = (a) => {
		const parsed = parse(toStr(a[0]));
		return parsed.ok ? stringify(parsed.value) : '';
	};

	BLOCKS.jsonErrMsg = (a) => errorMessage(toStr(a[0]));

	BLOCKS.jsonGetAll = (a) => {
		const parsed = parse(toStr(a[0]));
		if (!parsed.ok || !isContainer(parsed.value)) {
			return '';
		}
		const kind = toStr(a[1]);
		if (kind !== 'keys' && kind !== 'values' && kind !== 'datas') {
			return '';
		}
		const out = [];
		const add = (key, value) => {
			const safe = value === null || value === undefined ? '' : value;
			if (kind === 'keys') {
				out.push(key);
			} else if (kind === 'values') {
				out.push(safe);
			} else {
				out.push([key, safe]);
			}
		};
		if (Array.isArray(parsed.value)) {
			parsed.value.forEach((value, index) => add(String(index), value));
		} else {
			Object.keys(parsed.value).forEach((key) => add(key, parsed.value[key]));
		}
		return stringify(out);
	};

	BLOCKS.jsonArrayPush = (a) => {
		const array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		array.push(toWriteValue(a[1]));
		return stringify(array);
	};

	BLOCKS.jsonArraySet = (a) => {
		const array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		const index = toNum(a[1]) - 1;
		if (Number.isInteger(index) && index >= 0) {
			setArraySlot(array, index, toWriteValue(a[2]));
		}
		return stringify(array);
	};

	BLOCKS.jsonArrayInsert = (a) => {
		const array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		const at = jsSliceIndex(toNum(a[1]) - 1, array.length);
		array.splice(at, 0, toWriteValue(a[2]));
		return stringify(array);
	};

	BLOCKS.jsonArrayDelete = (a) => {
		const array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		const at = jsSliceIndex(toNum(a[1]) - 1, array.length);
		if (at < array.length) {
			array.splice(at, 1);
		}
		return stringify(array);
	};

	BLOCKS.jsonArrayRemoveAll = (a) => {
		const array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		const target = toWriteValue(a[1]);
		for (let i = array.length - 1; i >= 0; i--) {
			if (strictEquals(array[i], target)) {
				array.splice(i, 1);
			}
		}
		return stringify(array);
	};

	BLOCKS.jsonArrayItemNum = (a) => {
		const array = parseArray(toStr(a[1]));
		if (array === null) {
			return '';
		}
		const target = toWriteValue(a[0]);
		for (let i = 0; i < array.length; i++) {
			if (strictEquals(array[i], target)) {
				return i + 1;
			}
		}
		return 0;
	};

	BLOCKS.jsonArrayFromTo = (a) => {
		const array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		const start = jsSliceIndex(toNum(a[1]) - 1, array.length);
		let end = jsSliceIndex(toNum(a[2]), array.length);
		if (end < start) {
			end = start;
		}
		return stringify(array.slice(start, end));
	};

	BLOCKS.jsonArrayConcat = (a) => {
		const left = parseArray(toStr(a[0]));
		const right = parseArray(toStr(a[1]));
		if (left === null || right === null) {
			return '';
		}
		return stringify(left.concat(right));
	};

	BLOCKS.jsonArrayMapField = (a) => {
		const array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		const key = toStr(a[1]);
		return stringify(array.map((element) => (
			isContainer(element) && !Array.isArray(element)
				&& Object.prototype.hasOwnProperty.call(element, key)
				? element[key] : null
		)));
	};

	BLOCKS.jsonArraySetLen = (a) => {
		const array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		const raw = toNum(a[1]);
		if (!Number.isInteger(raw) || raw < 0) {
			return '';
		}
		array.length = raw;
		// 变大时 JS 留下空洞，序列化后是 null——这里显式补 null，避免空洞让 stringify 写成 null 之外的东西
		for (let i = 0; i < raw; i++) {
			if (array[i] === undefined) {
				array[i] = null;
			}
		}
		return stringify(array);
	};

	BLOCKS.jsonArrayCreate = (a) => {
		const delimiter = toStr(a[0]);
		const text = toStr(a[1]);
		if (delimiter === '') {
			return stringify(Array.from(text));
		}
		return stringify(text.split(delimiter));
	};

	BLOCKS.jsonArrayJoin = (a) => {
		const delimiter = toStr(a[0]);
		const array = parseArray(toStr(a[1]));
		if (array === null) {
			return '';
		}
		return array.map((element) => (element === null || element === undefined ? '' : keyText(element)))
			.join(delimiter);
	};

	BLOCKS.jsonArraySort = (a) => {
		const array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		const items = array.slice();
		// 参与比较的取值：对象/数组退化为文本，null 当 0
		const compareValue = (element) => {
			if (element === null || element === undefined) {
				return 0;
			}
			if (isContainer(element)) {
				return keyText(element);
			}
			return element;
		};
		items.sort((left, right) => castCompare(compareValue(left), compareValue(right)));
		if (toStr(a[1]) === 'descending') {
			items.reverse();
		}
		return stringify(items);
	};

	BLOCKS.jsonArrayTextFilter = (a) => {
		const root = parse(toStr(a[0]));
		if (!root.ok || !isContainer(root.value)) {
			return '';
		}
		const option = toStr(a[1]);
		const needle = toStr(a[2]);
		const shape = toStr(a[3]);
		const asObject = shape === 'Object';
		if (!asObject && shape !== 'Array') {
			return '';
		}
		const values = [];
		const mapped = {};
		const matches = (element) => {
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
			root.value.forEach((element, index) => {
				if (matches(element)) {
					values.push(element);
					mapped[String(index + 1)] = element;
				}
			});
		} else {
			Object.keys(root.value).forEach((key) => {
				const element = root.value[key];
				if (matches(element)) {
					values.push(element);
					mapped[key] = element;
				}
			});
		}
		return stringify(asObject ? mapped : values);
	};

	BLOCKS.jsonArrayAnalysis = (a) => {
		const array = parseArray(toStr(a[0]));
		if (array === null) {
			return 0;
		}
		const kind = toStr(a[1]);
		const values = array.map((element) => toNum(toScratch(element)));
		const sum = values.reduce((total, value) => total + value, 0);
		switch (kind) {
			case 'maximum': {
				let max = -Infinity;
				for (const value of values) {
					if (value > max) {
						max = value;
					}
				}
				return max;
			}
			case 'minimum': {
				let min = Infinity;
				for (const value of values) {
					if (value < min) {
						min = value;
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
				const sorted = values.slice().sort((x, y) => (x < y ? -1 : (x > y ? 1 : 0)));
				const middle = Math.floor(sorted.length / 2);
				return sorted.length % 2 === 0
					? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
			}
			case 'mode': {
				const counts = new Map();
				let mode = 0;
				let best = 0;
				for (const value of values) {
					// -0 与 0 算同一个键（SameValueZero）；NaN 也归一到同一个键
					const key = value === 0 ? 0 : value;
					const count = (counts.get(key) || 0) + 1;
					counts.set(key, count);
					if (count > best) {
						best = count;
						mode = value;
					}
				}
				return mode;
			}
			case 'variance': {
				const average = sum / values.length;
				let total = 0;
				for (const value of values) {
					const delta = value - average;
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
		run: (opcode, args) => {
			const fn = BLOCKS[opcode];
			return fn ? fn(args) : '';
		},
		toScratchString: toStr
	};
}
