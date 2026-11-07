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

/**
 * 把桥的返回值归一化成 JS 原始布尔。
 *
 * <p>桥的方法签名返回 {@code Object}，Rhino 的 {@code WrapFactory} 已把
 * {@code Boolean} 直接转成 JS 原始布尔，所以第一分支就是常态。</p>
 *
 * <p>保留字符串兜底是为了防御：万一某个值仍以包装对象形式到达
 * （例如将来改回默认 {@code WrapFactory}），{@code Boolean(value)} 对包装对象
 * 同样返回 true，只能按字符串比对。</p>
 */
function __tmcAsBoolean(value) {
	if (value === true || value === false) {
		return value;
	}
	return String(value) === 'true';
}

/**
 * 布尔归一化自检（冒烟测试用）。
 *
 * <p>先断言<b>归一化真的装到了积木上</b>（{@code __tmcBooleanBlocks} 是安装期统计的
 * 「被识别为布尔」的积木数）。少了这一条，判定常量写错时本函数依旧全绿——
 * 那是上一版的真实事故：辅助函数没问题，接线断了，所有「是/否」积木恒真。</p>
 *
 * <p>其余两项覆盖常态与兜底：原生布尔直接返回；再人为造一个
 * {@code java.lang.Boolean} 包装对象，模拟 {@code WrapFactory} 未生效时的形态。</p>
 */
function __tmcBooleanSelfTest() {
	var wrappedFalse = new Packages.java.lang.Boolean(false);
	return __tmcBooleanBlocks > 0
		&& __tmcAsBoolean(true) === true
		&& __tmcAsBoolean(false) === false
		&& __tmcAsBoolean(null) === false
		&& __tmcAsBoolean(undefined) === false
		&& __tmcAsBoolean(wrappedFalse) === false;
}

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
 * - 布尔积木的返回值必须归一化成 JS 原始布尔，见下方注释；
 * - 若动作声明了等待帧数，则返回 Promise 挂起线程。
 */

/**
 * 已识别为布尔的积木数，供 {@code __tmcBooleanSelfTest} 断言「归一化真的装上了」。
 *
 * <p>这个计数存在的理由：上一版用 {@code block.blockType === 'Boolean'} 判定，
 * 而本仓库的 {@code scratch-globals.js} 垫片给的是 {@code 'boolean'}（上游 scratch-vm
 * 才是大写的 {@code 'Boolean'}）。结果每个布尔积木都走了「不归一化」分支，
 * 归一化形同虚设，自检却只测辅助函数、照样通过——假绿。</p>
 */
var __tmcBooleanBlocks = 0;

for (var __j = 0; __j < __tmcInfo.blocks.length; __j++) {
	(function (block) {
		// 形状判断一律大小写无关：垫片与上游常量不一致，这里不能依赖其中一个具体写法
		var shape = String(block.blockType || '').toLowerCase();
		if (shape === 'hat') {
			Scratch.__instance[block.opcode] = function () {
				return true;
			};
			return;
		}
		var names = __tmcOrder[block.opcode] || [];
		// 布尔积木必须归一化：桥的返回值若不是 JS 原始布尔，scratch 的 Cast.toBoolean
		// 会把 false 也判真（历史 bug：所有「是/否」积木恒真）
		var isBoolean = shape === 'boolean';
		if (isBoolean) {
			__tmcBooleanBlocks++;
		}
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
			return isBoolean ? __tmcAsBoolean(result) : result;
		};
	})(__tmcInfo.blocks[__j]);
}

var __tmcVm = null;

/** 取 VM 的输入设备容器；未就绪返回 null。 */
function __tmcDevices(virtualMachine) {
	var runtime = virtualMachine && virtualMachine.runtime;
	return (runtime && runtime.ioDevices) || null;
}

/**
 * 把「按下 [键]?」的数据源接到宿主。
 *
 * scratch-vm 的 Keyboard 只在浏览器里由 keydown / keyup 写入 _keysPressed，
 * 游戏内没有任何 DOM 事件，不接管的话该积木恒为 false 且不报错。
 * {@code ioQuery} 取的是设备实例上的方法（{@code device[query].apply(device, args)}），
 * 因此覆写实例属性即可生效，不必改动 vm.es5.js。
 */
function __tmcInstallKeyboard(virtualMachine) {
	var devices = __tmcDevices(virtualMachine);
	var keyboard = devices && devices.keyboard;
	if (!keyboard) {
		print('[turbomc] 键盘设备缺失，「按下 [键]?」将恒为假');
		return;
	}
	keyboard.getKeyIsDown = function (key) {
		return __tmcAsBoolean(__tmcApi().keyDown(key === undefined || key === null ? '' : String(key)));
	};
	// 标记：供冒烟测试断言钩子确实装到了设备实例上（原型方法本身仍在，容易误判）
	keyboard.__tmcHooked = true;
}

/**
 * 把「鼠标按下?」的数据源接到宿主。
 *
 * <p>同理：Mouse 设备的 {@code _isDown} 只由浏览器 mousedown / mouseup 写入，游戏内恒为
 * false。Scratch 该积木不区分按钮，这里按「任意键按下」判定，与浏览器语义一致；
 * 需要区分左右中键请用扩展积木「按下鼠标 [按钮]?」。</p>
 */
function __tmcInstallMouse(virtualMachine) {
	var devices = __tmcDevices(virtualMachine);
	var mouse = devices && devices.mouse;
	if (!mouse) {
		print('[turbomc] 鼠标设备缺失，「鼠标按下?」将恒为假');
		return;
	}
	mouse.getIsDown = function () {
		return __tmcAsBoolean(__tmcApi().mouseDown('any'));
	};
	mouse.__tmcHooked = true;
}

/** 键盘钩子是否已安装（冒烟测试用，正常运行无调用方）。 */
function __tmcKeyboardHooked() {
	var devices = __tmcDevices(__tmcVm);
	return !!(devices && devices.keyboard && devices.keyboard.__tmcHooked === true);
}

/**
 * 把「用户名」接到宿主。
 *
 * <p>userData 设备存在，但 {@code _username} 默认是空串——浏览器里由 GUI 注入，
 * 游戏内没有这一层，不接管就会得到空字符串（积木看起来"能用"，值永远为空）。</p>
 */
function __tmcInstallUserData(virtualMachine) {
	var devices = __tmcDevices(virtualMachine);
	var userData = devices && devices.userData;
	if (!userData) {
		print('[turbomc] userData 设备缺失，「用户名」将返回空');
		return;
	}
	userData.getUsername = function () {
		return __tmcApi().username();
	};
	userData.__tmcHooked = true;
}

/** 用户名钩子是否已安装（冒烟测试用）。 */
function __tmcUserHooked() {
	var devices = __tmcDevices(__tmcVm);
	return !!(devices && devices.userData && devices.userData.__tmcHooked === true);
}

/* ---- 询问并等待 ---- */

var __tmcAskInstalled = false;

/**
 * 把「询问 [问题] 并等待」接到宿主。
 *
 * <p>VM 提问时会 {@code emit('QUESTION', 问题)}，宿主弹窗后用 {@code emit('ANSWER', 文本)}
 * 回填；「回答」积木由 VM 自己维护。</p>
 *
 * <p>两种取值要分清楚：<b>null 表示「没有待提问」</b>（停止工程、队列清空时发出），
 * 收到就忽略；<b>空串表示角色可见</b>——此时问题已经通过 {@code SAY} 事件显示在气泡上，
 * 但仍需要输入框，所以照常弹窗。把两者混为一谈会导致「角色可见时提问永远等不到回答」。</p>
 */
function __tmcInstallAsk(virtualMachine) {
	var runtime = virtualMachine && virtualMachine.runtime;
	if (!runtime || typeof runtime.on !== 'function') {
		print('[turbomc] runtime 不支持事件监听，「询问并等待」不可用');
		return;
	}
	runtime.on('QUESTION', function (question) {
		if (question === null || question === undefined) {
			return;
		}
		__tmcApi().askQuestion(String(question));
	});
	__tmcAskInstalled = true;
}

function __tmcAskHooked() {
	return __tmcAskInstalled === true;
}

/**
 * 把界面提交的回答交给 VM。
 *
 * <p>必须在这里 emit（而不是界面回调里）：emit 会推动线程状态，放在 step 开头执行，
 * 保证它总在 VM 的运行边界上。</p>
 */
function __tmcFlushAnswer() {
	var answer = __tmcApi().takeAnswer();
	if (answer !== null && answer !== undefined) {
		__tmcVm.runtime.emit('ANSWER', String(answer));
	}
}

/* ---- HUD 监视器（显示 / 隐藏 变量与列表） ---- */

var __tmcMonitorInstalled = false;

/**
 * 从积木参数里取名字。
 *
 * <p>「显示变量 / 显示列表」的参数不是字符串，而是 VM 内部的变量 / 列表对象
 * （原生实现直接用 {@code args.VARIABLE.id}）。所以这里按对象取 {@code name}，
 * 只在退化情况下才当字符串处理。</p>
 */
function __tmcParamName(raw) {
	if (raw === undefined || raw === null) {
		return '';
	}
	if (typeof raw === 'object') {
		if (raw.name !== undefined && raw.name !== null) {
			return String(raw.name);
		}
		if (raw.id !== undefined && raw.id !== null) {
			return String(raw.id);
		}
		return '';
	}
	return String(raw);
}

/**
 * 接管 4 个「显示 / 隐藏」积木。
 *
 * <p>它们是原生 opcode，执行时不会通知宿主，而游戏内没有舞台监视器。只能覆写原生积木表
 * {@code runtime._primitives}——这是压缩后的私有名，升级 vm.es5.js 时可能改名，
 * 因此缺失时明确打印，不静默失效。</p>
 */
function __tmcInstallMonitors(virtualMachine) {
	var runtime = virtualMachine && virtualMachine.runtime;
	var primitives = runtime && runtime._primitives;
	if (!primitives) {
		print('[turbomc] 原生积木表缺失，「显示 / 隐藏 变量」不会有反应');
		return;
	}
	var hook = function (kind, fieldName, visible) {
		return function (args) {
			var name = __tmcParamName(args ? args[fieldName] : null);
			if (name !== '') {
				__tmcApi().monitor(kind, name, visible);
			}
		};
	};
	primitives['data_showvariable'] = hook('var', 'VARIABLE', true);
	primitives['data_hidevariable'] = hook('var', 'VARIABLE', false);
	primitives['data_showlist'] = hook('list', 'LIST', true);
	primitives['data_hidelist'] = hook('list', 'LIST', false);
	__tmcMonitorInstalled = true;
}

function __tmcMonitorHooked() {
	return __tmcMonitorInstalled === true;
}

/** 读取列表内容（JSON 数组字符串），供 HUD 监视器显示。 */
/**
 * 读取列表内容（JSON 数组），供 HUD 监视器显示。
 *
 * <p><b>为什么要看两个地方</b>：这套 scratch-vm 把变量与列表<b>统一存在
 * {@code target.variables} 里</b>，用 {@code type} 区分（{@code "list"} / 空串）。
 * 只遍历 {@code target.lists} 会永远拿到空——那是旧数据模型的位置，
 * 表现为「HUD 上的列表一直是空的」，而列表里其实早有数据。</p>
 */
function __tmcGetList(name) {
	if (!__tmcVm) {
		return '[]';
	}
	var targets = __tmcVm.runtime.targets;
	for (var t = 0; t < targets.length; t++) {
		var target = targets[t];
		var vars = target.variables || {};
		for (var id in vars) {
			var item = vars[id];
			if (item && item.name === name && item.type === 'list') {
				return JSON.stringify(item.value || []);
			}
		}
		// 兼容旧数据模型：列表单独挂在 lists 上
		var lists = target.lists || {};
		for (var listId in lists) {
			var legacy = lists[listId];
			if (legacy && legacy.name === name) {
				return JSON.stringify(legacy.value || []);
			}
		}
	}
	return '[]';
}

/** 鼠标钩子是否已安装（冒烟测试用）。 */
function __tmcMouseHooked() {
	var devices = __tmcDevices(__tmcVm);
	return !!(devices && devices.mouse && devices.mouse.__tmcHooked === true);
}

/**
 * 「停止全部」的累计次数。
 *
 * <p>脚本执行「停止全部」时，scratch-vm 会清空 {@code runtime.threads} 并发出
 * {@code PROJECT_STOP_ALL}，但宿主侧没有任何订阅点——线程没了，「走向」会话与
 * 脚本按下的前进键却还在每 tick 驱动，表现为「脚本已停，人还在走」。
 * 这里只做计数，由 Java 侧每 tick 取走并决定收尾。</p>
 */
var __tmcProjectStops = 0;

/**
 * 订阅 {@code PROJECT_STOP_ALL}。
 *
 * <p>要防重入：{@code __tmcSetVm} 在冒烟测试里会被多次调用，重复 {@code on} 会让
 * 同一个事件被计多次数。</p>
 */
function __tmcHookProjectStop(virtualMachine) {
	var runtime = virtualMachine && virtualMachine.runtime;
	if (!runtime || runtime.__tmcStopHooked) {
		return;
	}
	runtime.__tmcStopHooked = true;
	runtime.on('PROJECT_STOP_ALL', function () {
		__tmcProjectStops++;
	});
}

/** 取走自上次调用以来「停止全部」发生的次数。 */
function __tmcTakeProjectStops() {
	var count = __tmcProjectStops;
	__tmcProjectStops = 0;
	return count;
}

function __tmcSetVm(virtualMachine) {
	__tmcVm = virtualMachine;
	// 先挂钩再装键盘/鼠标：greenFlag 内部会 stopAll，计数必须在宿主清零前就位
	__tmcHookProjectStop(virtualMachine);
	__tmcInstallKeyboard(virtualMachine);
	__tmcInstallMouse(virtualMachine);
	__tmcInstallUserData(virtualMachine);
	__tmcInstallAsk(virtualMachine);
	__tmcInstallMonitors(virtualMachine);
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
	__tmcFlushAnswer();
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
 * 各扩展积木的参数名清单（按占位符出现顺序）。
 *
 * <p>积木方法体转发到 Java 时，实参的<b>位置</b>由这里决定：Java 侧按位置取
 * {@code args.<名字>}。参数名来自积木显示文本里的 {@code [NAME]} 占位符——
 * 这是唯一与 {@code arguments} 一一对应且顺序可靠的来源（对象 key 顺序在 ES5 里不保证）。</p>
 *
 * <p>代价是：改写显示文本时若动了占位符的拼写或顺序，实参会静默错位，
 * 不报错、只是语义偏移。宿主侧用本函数做交叉校验（见 {@code RhinoVm.argsSelfTest}），
 * 占位符被破坏时能立刻发现。</p>
 */
function __tmcArgNamesDump() {
	var list = [];
	for (var k = 0; k < __tmcInfo.blocks.length; k++) {
		var b = __tmcInfo.blocks[k];
		list.push(b.opcode + '=' + (__tmcOrder[b.opcode] || []).join(','));
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
