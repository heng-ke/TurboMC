/*
 * turbomc 的 VM 引导脚本（Rhino 侧），在 scratch-globals.js 与扩展之后执行。
 *
 * 职责：
 *  1. 把扩展里所有积木方法替换成「转发到 Java 动作层」的实现，
 *     扩展文件本身保持原样（浏览器里仍然可用）；
 *  2. 暴露 __tmc* 入口给 Java 侧调用。
 *
 * 参数顺序取自积木显示文本里的 [NAME] 占位符——这是唯一与 arguments 一一对应
 * 且顺序可靠的来源（对象 key 顺序在 ES5 里不保证）。
 */

if (!Scratch || !Scratch.__instance) {
	throw new Error('TurboMC 扩展未注册：检查 scratch-globals.js 与扩展的加载顺序');
}

var __tmcInfo = Scratch.__instance.getInfo();

function __tmcArgNames(text) {
	var names = [];
	var re = /\[([A-Za-z0-9_]+)\]/g;
	var m;
	while ((m = re.exec(text)) !== null) {
		names.push(m[1]);
	}
	return names;
}

var __tmcOrder = {};
for (var __i = 0; __i < __tmcInfo.blocks.length; __i++) {
	var __b = __tmcInfo.blocks[__i];
	__tmcOrder[__b.opcode] = __tmcArgNames(__b.text || '');
}

var __tmcBridge = null;
function __tmcApi() {
	if (__tmcBridge === null) {
		__tmcBridge = Packages.xyz.turbomc.client.script.vm.ScriptBridge;
	}
	return __tmcBridge;
}

/* ---- 阻塞动作：按帧兑现的 Promise ---- */

var __tmcTick = 0;
var __tmcPending = [];

/**
 * 让当前脚本挂起 ticks 帧，返回 Promise。
 * scratch-vm 见到 thenable 会把线程置为 PROMISE_WAIT，兑现后才继续下一块。
 */
function __tmcWaitTicks(ticks) {
	var frames = Math.max(1, Math.floor(ticks));
	return new Promise(function (resolve) {
		__tmcPending.push({resolve: resolve, until: __tmcTick + frames});
	});
}

/** 每帧推进：兑现到期的等待，并清空微任务队列。 */
function __tmcPumpPromises() {
	__tmcTick++;
	if (__tmcPending.length > 0) {
		var remain = [];
		for (var i = 0; i < __tmcPending.length; i++) {
			var item = __tmcPending[i];
			var finished = __tmcTick >= item.until;
			// 条件型等待：每帧问一次宿主，满足即提前结束
			if (!finished && item.check) {
				finished = __tmcApi().poll(item.check) === true;
			}
			if (finished) {
				item.resolve();
			} else {
				remain.push(item);
			}
		}
		__tmcPending = remain;
	}
	if (typeof __flushMicro === 'function') {
		__flushMicro();
	}
	return __tmcPending.length;
}

/**
 * 条件型等待：每帧回调 {@code ScriptBridge.poll(checkKey)}，返回 true 或超时才继续。
 * 用于「等待方块被挖破」这类没有固定时长的动作。
 */
function __tmcWaitUntil(checkKey, maxTicks) {
	var limit = Math.max(1, Math.floor(maxTicks || 200));
	return new Promise(function (resolve) {
		__tmcPending.push({resolve: resolve, until: __tmcTick + limit, check: checkKey});
	});
}

function __tmcPendingCount() {
	return __tmcPending.length;
}

/*
 * 覆盖方法体。
 * - 帽子积木补一个恒真实现：startHats 会调用其方法判断条件，
 *   缺失时会因读到 undefined 而崩（扩展原本假定浏览器里由 UI 触发）；
 * - 其余积木统一转发，参数按 __tmcOrder 的顺序取出；
 * - 若动作声明了等待帧数，则返回 Promise 挂起线程。
 */
for (var __j = 0; __j < __tmcInfo.blocks.length; __j++) {
	(function (block) {
		if (block.blockType === 'hat') {
			Scratch.__instance[block.opcode] = function () {
				return true;
			};
			return;
		}
		var names = __tmcOrder[block.opcode] || [];
		Scratch.__instance[block.opcode] = function (args) {
			var result = __tmcApi().call('turbomc_' + block.opcode, names, args);
			// 条件型等待优先：它表达的是「等到某事发生」，不能用固定帧数代替
			var check = __tmcApi().takeWaitCheck();
			if (check) {
				return __tmcWaitUntil(check, __tmcApi().takeWaitLimit());
			}
			var wait = __tmcApi().takeWaitTicks();
			if (wait > 0) {
				return __tmcWaitTicks(wait);
			}
			if (block.opcode === 'sleep') {
				// 睡觉本身是一次长动作，Java 侧不声明等待时按 2 秒兜底
				return __tmcWaitTicks(40);
			}
			return result;
		};
	})(__tmcInfo.blocks[__j]);
}

var __tmcVm = null;

function __tmcSetVm(virtualMachine) {
	__tmcVm = virtualMachine;
}

/** 预先登记扩展，避免 loadProject 走浏览器 Worker 路径。 */
function __tmcRegisterExtension() {
	var manager = __tmcVm.extensionManager;
	var serviceName = manager._registerInternalExtension(Scratch.__instance);
	manager._loadedExtensions.set(__tmcInfo.id, serviceName);
	manager._registerExtensionInfo(serviceName, __tmcInfo);
}

function __tmcLoadProject(json) {
	return __tmcVm.loadProject(json);
}

function __tmcStep() {
	__tmcPumpPromises();
	__tmcVm.runtime._step();
}

function __tmcSetBudget(ms) {
	__tmcVm.runtime.currentStepTime = ms;
}

function __tmcGreenFlag() {
	__tmcVm.greenFlag();
}

function __tmcStartHat(opcode, matchValue) {
	return __tmcVm.runtime.startHats(opcode, matchValue || null);
}

/** 带单个匹配字段的触发：{@code fieldName} 为空表示不带匹配条件。 */
function __tmcStartHatField(opcode, fieldName, value) {
	var match = null;
	if (fieldName) {
		match = {};
		match[fieldName] = value;
	}
	return __tmcVm.runtime.startHats(opcode, match);
}

/** 取字段文本：兼容 {name,value,id} 对象与 [value, id] 数组两种形态。 */
function __tmcFieldText(block, name) {
	var field = block.fields && block.fields[name];
	if (!field) {
		return null;
	}
	if (typeof field === 'object' && field.value !== undefined) {
		return String(field.value);
	}
	if (field.length !== undefined && field.length >= 1) {
		return String(field[0]);
	}
	return String(field);
}

/** 读取积木某个参数槽的文本：优先 fields，其次 [1,[10,"x"]] 与 [1,"blockId"] 两种输入形态。 */
/**
 * 读取积木某个参数槽的文本。
 *
 * <p>要同时兼容两种形态：</p>
 * <ul>
 *   <li><b>原始 JSON</b>：{@code [1, [10, "文字"]]} 或 {@code [1, "blockId"]}；</li>
 *   <li><b>VM 运行期</b>：{@code {name, block, shadow}}——sb3 载入后输入会被改写成这个对象，
 *       字面量本身则变成一个独立的替身积木，值放在它的 {@code fields.VALUE} 里。</li>
 * </ul>
 *
 * <p>只认字面量。变量或表达式无法静态求值，返回 {@code null} 表示「不知道」，
 * 由调用方决定不要拿它做过滤——返回空串会被当成「关键词为空 ⇒ 匹配全部」，
 * 那正是「说一次却什么都触发」的来源。</p>
 */
function __tmcReadInput(blocks, block, name) {
	var direct = __tmcFieldText(block, name);
	if (direct !== null) {
		return direct;
	}
	var input = block.inputs && block.inputs[name];
	if (!input) {
		return '';
	}

	// 运行期对象形态 {name, block, shadow}：block 与 shadow 不同，说明槽里接了
	// 变量或表达式，静态读不出值——返回 null 让调用方别拿它当关键词。
	if (input.block !== undefined) {
		if (input.block !== input.shadow) {
			return null;
		}
		return __tmcShadowText(blocks, input.block);
	}

	// 原始 JSON 形态 [1, [10, "文字"]] 或 [1, "blockId"]
	var value = input[1];
	if (typeof value === 'string') {
		return __tmcShadowText(blocks, value);
	}
	if (value && value.length >= 2) {
		return String(value[1] === null || value[1] === undefined ? '' : value[1]);
	}
	return '';
}

/**
 * 读替身积木里的字面量。
 *
 * <p>替身按类型把值放在不同字段上：文本是 {@code TEXT}、数字是 {@code NUM}，
 * 变量是 {@code VARIABLE}（存的是名字，不是值——所以变量永远不会走到这里）。</p>
 */
function __tmcShadowText(blocks, id) {
	var inner = blocks._blocks && blocks._blocks[id];
	if (!inner) {
		return '';
	}
	var byValue = __tmcFieldText(inner, 'VALUE');
	if (byValue !== null) {
		return byValue;
	}
	var byText = __tmcFieldText(inner, 'TEXT');
	if (byText !== null) {
		return byText;
	}
	var byNumber = __tmcFieldText(inner, 'NUM');
	if (byNumber !== null) {
		return byNumber;
	}
	if (inner.fields) {
		for (var key in inner.fields) {
			var text = __tmcFieldText(inner, key);
			if (text !== null) {
				return text;
			}
		}
	}
	return '';
}

/**
 * 按输入内容过滤后再触发帽子。
 *
 * <p>{@code runtime.startHats} 的字段匹配只认 field，而扩展把「当聊天包含」的关键词
 * 声明成 input，因此不能用它做过滤。这里自己遍历顶层块、读输入值、按需 pushThread。</p>
 */
function __tmcStartHatFiltered(opcode, inputName, matchText) {
	var started = 0;
	var runtime = __tmcVm.runtime;
	var targets = runtime.targets;
	var needle = matchText === null || matchText === undefined ? null : String(matchText);
	for (var t = 0; t < targets.length; t++) {
		var target = targets[t];
		var blocks = target.blocks;
		if (!blocks || !blocks._blocks) {
			continue;
		}
		var all = blocks._blocks;
		for (var id in all) {
			var block = all[id];
			if (!block || block.opcode !== opcode || !block.topLevel) {
				continue;
			}
			if (inputName && needle !== null) {
				var pattern = __tmcReadInput(blocks, block, inputName);
				if (pattern === null) {
					// 关键词不是字面量（表达式之类）：静态读不出值，退化为「不按关键词过滤」
					pattern = '';
				}
				if (pattern !== '' && needle.indexOf(pattern) < 0) {
					continue;
				}
			}
			// 同一顶块已有线程在跑则跳过，避免聊天连发时叠加
			var busy = false;
			for (var i = 0; i < runtime.threads.length; i++) {
				var thread = runtime.threads[i];
				if (thread.target === target && thread.topBlock === id && thread.status !== 4) {
					busy = true;
					break;
				}
			}
			if (busy) {
				continue;
			}
			runtime._pushThread(id, target);
			started++;
		}
	}
	return started;
}

function __tmcBlockCount() {
	return __tmcInfo.blocks.length;
}

function __tmcThreadCount() {
	return __tmcVm.runtime.threads.length;
}

/** 读取变量值：全局在 Stage 上，局部在各自的 target 上，这里全部遍历一遍。 */
function __tmcGetVar(name) {
	var targets = __tmcVm.runtime.targets;
	for (var t = 0; t < targets.length; t++) {
		var vars = targets[t].variables;
		for (var id in vars) {
			if (vars[id].name === name) {
				return vars[id].value;
			}
		}
	}
	return null;
}

/** 变量快照，格式 {@code 名字=值} 用分号分隔，供运行时面板显示。 */
function __tmcDumpVars() {
	var parts = [];
	var targets = __tmcVm.runtime.targets;
	for (var t = 0; t < targets.length; t++) {
		var vars = targets[t].variables;
		for (var id in vars) {
			parts.push(vars[id].name + '=' + vars[id].value);
		}
	}
	return parts.join(';');
}

function __tmcDescribe() {
	var list = [];
	for (var k = 0; k < __tmcInfo.blocks.length; k++) {
		var b = __tmcInfo.blocks[k];
		list.push(b.opcode + ':' + b.blockType + ':' + (__tmcOrder[b.opcode] || []).join('|'));
	}
	return list.join(';');
}

/**
 * 读取所有角色的当前气泡（「说 / 思考」积木的结果），返回 JSON 数组。
 *
 * <p>气泡状态由 scratch-vm 存在每个 target 的 customState['Scratch.looks'] 里，
 * 其中 usageId 每次说话都会变——用它判断「是不是新的一句」，比比对文本可靠
 * （同一句话连说两次也能识别）。</p>
 */
function __tmcSpeech() {
	var out = [];
	var targets = __tmcVm.runtime.targets;
	for (var t = 0; t < targets.length; t++) {
		var target = targets[t];
		if (!target || typeof target.getCustomState !== 'function') {
			continue;
		}
		var state = target.getCustomState('Scratch.looks');
		if (!state || !state.text) {
			continue;
		}
		out.push({
			name: target.getName(),
			text: String(state.text),
			think: state.type === 'think',
			id: String(state.usageId || '')
		});
	}
	return JSON.stringify(out);
}
