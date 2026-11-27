/*
 * TurboMC 扩展的唯一事实源：积木、菜单、分组标题、占位返回值。
 * 产物（编辑器副本 / 运行时副本）由 tools/build-extension.js 渲染，不要直接改产物。
 *
 * 字段：
 *   {label}                    面板分组标题（只在编辑器显示，运行时不渲染）
 *   {opcode, blockType, text}  积木；text 必须中英各一份
 *                              宽度按文本动态渲染，写太长会横向撑出编辑器工作区；
 *                              build-extension 的 assertBlockWidth 会在超过 420px 时失败
 *                              （菜单项也会撑宽所有引用它的积木，缩短菜单同样能收窄）
 *   arguments                  参数名/类型/默认值/菜单；名称与顺序必须与 text 的 [占位符] 一致
 *   props                      额外属性：目前只有帽子的 isEdgeActivated（hideFromPalette 仍被 VM 支持，按需使用）
 *   stub                       编辑器里的占位方法体（浏览器内没有 Java，只能返回假值）。
 *                              JSON 一族没有 stub：那批是纯计算，构建期直接生成本地调用，
 *                              未连接游戏也能算出真实结果（见 tools/build-extension.js）。
 *   read                       浏览器侧的读数投影：(snapshot, argv, util) => value。
 *                              reporter / boolean 必须有它（或 unsupported / JSON 本地实现），
 *                              构建期据此生成 remote-client.js 的读数表——那边不再手写第二份。
 *                              read 里引用的 s.<键> 会在构建期与快照字段比对，改名会直接失败。
 *   unsupported                true 表示该读数在浏览器侧永久不可用（同步求值等不了 RPC），
 *                              构建期跳过生成，运行期返回空值。
 */
module.exports = {
	id: 'turbomc',
	name: {en: 'TurboMC', zh: 'TurboMC'},
	color1: '#6F45B0',

	blocks: [
		{label: {en: 'Events', zh: '事件'}},
		{opcode: 'whenDamaged', blockType: 'hat', text: {en: 'when damaged', zh: '当受伤时'}, props: {isEdgeActivated: false}},
		{opcode: 'whenDeath', blockType: 'hat', text: {en: 'when player dies', zh: '当玩家死亡'}, props: {isEdgeActivated: false}},
		// 界面打开：界面里按键是被拦截的（TriggerDispatcher），「打开箱子就执行」只能靠这个事件
		{opcode: 'whenScreenOpen', blockType: 'hat', text: {en: 'when [SCREEN] opens', zh: '当打开 [SCREEN]'}, arguments: {SCREEN: {type: 'string', defaultValue: "container", menu: 'screenKind'}}, props: {isEdgeActivated: false}},
		{opcode: 'whenChat', blockType: 'hat', text: {en: 'when chat contains [MATCH]', zh: '当聊天包含 [MATCH]'}, arguments: {MATCH: {type: 'string', defaultValue: ""}}, props: {isEdgeActivated: false}},
		{label: {en: 'Movement & view', zh: '移动与视角'}},
		{opcode: 'moveToward', blockType: 'command', text: {en: 'go to x[X] y[Y] z[Z]', zh: '走向 x[X] y[Y] z[Z]'}, arguments: {X: {type: 'number', defaultValue: 0}, Y: {type: 'number', defaultValue: 64}, Z: {type: 'number', defaultValue: 0}}, stub: 'this.preview(\'moveToward \' + args.X + \',\' + args.Y + \',\' + args.Z);'},
		// 移动 → 视角（各自的瞬时/平滑相邻）→ 停止。
		// 不把瞬时与平滑合成一块：平滑是阻塞的，把「会不会卡住」藏进参数就没法一眼看出。
		{opcode: 'turnView', blockType: 'command', text: {en: 'turn view [MODE] yaw[YAW] pitch[PITCH]', zh: '转动视角 [MODE] 水平[YAW] 俯仰[PITCH]'}, arguments: {MODE: {type: 'string', defaultValue: "absolute", menu: 'viewMode'}, YAW: {type: 'number', defaultValue: 0}, PITCH: {type: 'number', defaultValue: 0}}, stub: 'this.preview(\'turnView \' + args.MODE + \' \' + args.YAW + \',\' + args.PITCH);'},
		{opcode: 'turnViewSmooth', blockType: 'command', text: {en: 'turn smoothly [MODE] yaw[YAW] pitch[PITCH]', zh: '平滑转动视角 [MODE] 水平[YAW] 俯仰[PITCH]'}, arguments: {MODE: {type: 'string', defaultValue: "absolute", menu: 'viewMode'}, YAW: {type: 'number', defaultValue: 0}, PITCH: {type: 'number', defaultValue: 0}}, stub: 'this.preview(\'turnViewSmooth \' + args.MODE + \' \' + args.YAW + \',\' + args.PITCH);'},
		{opcode: 'lookAt', blockType: 'command', text: {en: 'look at x[X] y[Y] z[Z]', zh: '看向 x[X] y[Y] z[Z]'}, arguments: {X: {type: 'number', defaultValue: 0}, Y: {type: 'number', defaultValue: 64}, Z: {type: 'number', defaultValue: 0}}, stub: 'this.preview(\'lookAt \' + args.X + \',\' + args.Y + \',\' + args.Z);'},
		{opcode: 'lookAtSmooth', blockType: 'command', text: {en: 'look smoothly at x[X] y[Y] z[Z]', zh: '平滑看向 x[X] y[Y] z[Z]'}, arguments: {X: {type: 'number', defaultValue: 0}, Y: {type: 'number', defaultValue: 64}, Z: {type: 'number', defaultValue: 0}}, stub: 'this.preview(\'lookAtSmooth \' + args.X + \',\' + args.Y + \',\' + args.Z);'},
		{opcode: 'stop', blockType: 'command', text: {en: 'stop moving', zh: '停止移动'}, stub: 'this.preview(\'stop\');'},
		{label: {en: 'Input', zh: '输入'}},
		{opcode: 'key', blockType: 'command', text: {en: 'key [KEY] [STATE]', zh: '按键 [KEY] [STATE]'}, arguments: {KEY: {type: 'string', defaultValue: "w", menu: 'keyName'}, STATE: {type: 'string', defaultValue: "down", menu: 'keyState'}}, stub: 'this.preview(\'key \' + args.KEY + \' \' + args.STATE);'},
		{opcode: 'click', blockType: 'command', text: {en: 'click [BUTTON]', zh: '点击 [BUTTON]'}, arguments: {BUTTON: {type: 'string', defaultValue: "left", menu: 'mouseButton'}}, stub: 'this.preview(\'click \' + args.BUTTON);'},
		{opcode: 'tap', blockType: 'command', text: {en: 'tap [KEY] for [HOLD] ticks', zh: '点按 [KEY] 按住 [HOLD] tick'}, arguments: {KEY: {type: 'string', defaultValue: "w", menu: 'keyName'}, HOLD: {type: 'number', defaultValue: 1}}, stub: 'this.preview(\'tap \' + args.KEY + \' x\' + args.HOLD);'},
		{opcode: 'mouseDown', blockType: 'boolean', text: {en: 'mouse [BUTTON] pressed?', zh: '按下鼠标 [BUTTON]?'}, arguments: {BUTTON: {type: 'string', defaultValue: "left", menu: 'mouseButton'}}, stub: 'return false;',
			read: (s, a) => {
				const button = String((a && a[0]) || 'left').trim().toLowerCase();
				if (!s || !s.mouse) {
					return false;
				}
				if (button === 'any') {
					return !!(s.mouse.left || s.mouse.right || s.mouse.middle);
				}
				return !!s.mouse[button];
			}
		},
		{label: {en: 'Mining', zh: '挖掘'}},
		// 顺序即决策路径：先判断「在不在挖、挖了多少」，再决定「等它被挖破」
		{opcode: 'isMining', blockType: 'boolean', text: {en: 'is mining', zh: '正在挖掘'}, stub: 'return false;', read: (s) => !!(s && s.mining && s.mining.active)},
		{opcode: 'miningProgress', blockType: 'reporter', text: {en: 'mining progress', zh: '挖掘进度'}, stub: 'return 0;', read: (s) => (s && s.mining ? s.mining.progress : -1)},
		{opcode: 'breakWait', blockType: 'command', text: {en: 'wait until block broken', zh: '等待方块被挖破'}, stub: 'this.preview(\'breakWait\');'},
		{label: {en: 'Containers', zh: '容器'}},
		{opcode: 'openContainerAt', blockType: 'command', text: {en: 'open container at x[X] y[Y] z[Z]', zh: '打开坐标处的容器 x[X] y[Y] z[Z]'}, arguments: {X: {type: 'number', defaultValue: 0}, Y: {type: 'number', defaultValue: 64}, Z: {type: 'number', defaultValue: 0}}, stub: 'this.preview(\'openContainerAt\');'},
		{opcode: 'containerSlots', blockType: 'reporter', text: {en: 'container slots', zh: '界面槽位数'}, stub: 'return 0;', read: (s) => (s && s.ui ? s.ui.slots : 0)},
		{opcode: 'slotField', blockType: 'reporter', text: {en: 'slot [INDEX] [FIELD]', zh: '槽位 [INDEX] 的 [FIELD]'}, arguments: {INDEX: {type: 'number', defaultValue: 0}, FIELD: {type: 'string', defaultValue: "id", menu: 'slotField'}}, stub: 'return \'\';',
			read: (s, a) => {
				const index = Number((a && a[0]) === undefined ? 0 : a[0]);
				const field = String((a && a[1]) || 'id').trim().toLowerCase();
				const items = s && s.ui && Array.isArray(s.ui.items) ? s.ui.items : [];
				const item = items.find((entry) => entry.slot === index) || null;
				if (field === 'amount') {
					return item ? item.count : 0;
				}
				if (field === 'max_durability') {
					return item ? item.max_damage : 0;
				}
				if (field === 'damage') {
					return item ? item.damage : 0;
				}
				if (field === 'custom_name') {
					return item ? item.custom_name : '';
				}
				return item ? item.id : '';
			}
		},
		// 界面快照：槽位号与「点击槽位」同一套（都来自 ScreenHandler），没开界面时返回 {}
		{opcode: 'screenJson', blockType: 'reporter', text: {en: 'open screen snapshot', zh: '界面快照'}, read: (s) => (s && s.ui ? JSON.stringify(s.ui) : '{}'), stub: 'return \'{}\';'},
		{opcode: 'clickSlot', blockType: 'command', text: {en: 'click slot [INDEX] with [BUTTON]', zh: '点击槽位 [INDEX] 用 [BUTTON]'}, arguments: {INDEX: {type: 'number', defaultValue: 0}, BUTTON: {type: 'string', defaultValue: "left", menu: 'slotButton'}}, stub: 'this.preview(\'clickSlot\');'},
		{opcode: 'quickMoveSlot', blockType: 'command', text: {en: 'quick move slot [INDEX]', zh: '快速移动槽位 [INDEX]'}, arguments: {INDEX: {type: 'number', defaultValue: 0}}, stub: 'this.preview(\'quickMoveSlot\');'},
		{opcode: 'closeContainer', blockType: 'command', text: {en: 'close container', zh: '关闭界面'}, stub: 'this.preview(\'closeContainer\');'},
		{label: {en: 'Player', zh: '玩家状态'}},
		// 玩家的数值/文本字段全部走这一块下拉。旧的 health / food / xpLevel / selectedSlot /
		// dimension / ping / pos 七块已删除，sb3 里存的是 opcode，含它们的旧工程会出现未知积木
		// （见 ARCHITECTURE §6.2，与 posX/posY/posZ 那次同一处理）。
		//
		// 布尔类不并进来：六边形是条件槽的语法要求，塞进 reporter 就再也放不进「如果」。
		{opcode: 'playerField', blockType: 'reporter', text: {en: 'player [FIELD]', zh: '玩家的 [FIELD]'},
			arguments: {FIELD: {type: 'string', defaultValue: "health", menu: 'playerField'}},
			stub: 'return 0;',
			// 浏览器侧读的是远程快照（SnapshotPublisher），那边用短名（hp / dim / slot / xpLevel），
			// 与玩家 JSON 快照的长名（health / dimension / selected_slot / xp_level）不是同一套。
			// 别名表只能写在这里：read 会被 toString 序列化进产物，引用外部常量会直接失效。
			read: (s, a) => {
				if (!s) {
					return 0;
				}
				const alias = {health: 'hp', max_health: 'maxHp', xp_level: 'xpLevel', selected_slot: 'slot', dimension: 'dim'};
				const key = (a && alias[a[0]]) || (a && a[0]);
				const value = s[key];
				return value === undefined || value === null ? 0 : value;
			}},
		{opcode: 'onGround', blockType: 'boolean', text: {en: 'on ground?', zh: '站在地面？'}, stub: 'this.preview(\'onGround\'); return true;', read: (s) => !!(s && s.onGround)},
		{opcode: 'inWater', blockType: 'boolean', text: {en: 'in water?', zh: '在水中?'}, read: (s) => !!(s && s.inWater), stub: 'return false;'},
		{opcode: 'inLava', blockType: 'boolean', text: {en: 'in lava?', zh: '在岩浆中?'}, read: (s) => !!(s && s.inLava), stub: 'return false;'},
		// 背包与界面是两套槽位，不能合并成一句「背包中 [ITEM] 的槽位」——那会把界面
		// 槽位号当成背包槽位号。所以用 RANGE 显式区分，ASK 决定要数量还是首个槽位。
		// 参数名用 ASK 而非 KIND：KIND 已被「[快照] 的字段」占用，同名会让参数标签表撞键。
		// 原 firstSlotOf（只查界面）与 itemCount（只查背包）已删除。
		{opcode: 'inventoryQuery', blockType: 'reporter', text: {en: '[ASK] of [ITEM] in [RANGE]', zh: '[RANGE] 中 [ITEM] 的 [ASK]'},
			arguments: {
				RANGE: {type: 'string', defaultValue: "backpack", menu: 'inventoryRange'},
				ITEM: {type: 'string', defaultValue: "minecraft:stone", menu: 'itemId'},
				ASK: {type: 'string', defaultValue: "count", menu: 'inventoryKind'}
			},
			stub: 'return 0;',
			read: (s, a) => {
				const range = (a && a[0]) === 'screen' ? 'screen' : 'backpack';
				const want = itemKey(a && a[1]);
				const firstSet = (a && a[2]) === 'first_slot';
				if (!want || !s) {
					return firstSet ? -1 : 0;
				}
				const entries = range === 'screen'
					? (s.ui && Array.isArray(s.ui.items) ? s.ui.items : [])
					: (Array.isArray(s.inv) ? s.inv : []);
				if (firstSet) {
					const hit = entries.find((entry) => entry.id === want);
					return hit && typeof hit.slot === 'number' ? hit.slot : -1;
				}
				let total = 0;
				for (const entry of entries) {
					if (entry.id === want) {
						total += entry.count || 0;
					}
				}
				return total;
			}},
		{opcode: 'inventoryFull', blockType: 'boolean', text: {en: 'inventory full?', zh: '背包已满?'}, stub: 'return false;',
			read: (s) => {
				if (!s || !Array.isArray(s.inv)) {
					return false;
				}
				const filled = new Set(s.inv.map((item) => item.slot));
				for (let i = 0; i < 36; i++) {
					if (!filled.has(i)) {
						return false;
					}
				}
				return true;
			}
		},
		// 背包快照：41 格（0–35 主背包、36–39 盔甲、40 副手），只列非空槽位，裸数组
		{opcode: 'inventoryJson', blockType: 'reporter', text: {en: 'inventory snapshot', zh: '背包快照'}, read: (s) => JSON.stringify(s && Array.isArray(s.inv) ? s.inv : []), stub: 'return \'[]\';'},
		{opcode: 'heldItemField', blockType: 'reporter', text: {en: 'held [FIELD]', zh: '手中的 [FIELD]'}, arguments: {FIELD: {type: 'string', defaultValue: "id", menu: 'heldField'}}, stub: 'return \'\';',
			read: (s, a) => {
				const field = String((a && a[0]) || 'id').trim().toLowerCase();
				const hand = s && s.hand ? s.hand : {id: '', count: 0, maxDurability: 0, durability: 0};
				if (field === 'count') {
					return hand.count;
				}
				if (field === 'max_durability') {
					return hand.maxDurability;
				}
				if (field === 'durability') {
					return hand.durability;
				}
				return hand.id;
			}
		},
		// 快照：游戏状态 → JSON 文本，只读（ARCHITECTURE §26）。占位方法返回带真实键名的骨架，
		// 这样作者在浏览器里也能用 json.get_path 把取值路径调通，只是数值是假的
		{opcode: 'playerJson', blockType: 'reporter', text: {en: 'player snapshot', zh: '玩家快照'}, read: (s) => JSON.stringify(s && s.me ? s.me : {}), stub: 'return \'{"name":"","uuid":"","health":0,"max_health":20,"absorption":0,"food":20,"saturation":5,"xp_level":0,"xp_progress":0,"armor":0,"air":300,"gamemode":"","dimension":"","x":0,"y":0,"z":0,"motion_x":0,"motion_y":0,"motion_z":0,"yaw":0,"pitch":0,"on_ground":false,"in_water":false,"in_lava":false,"selected_slot":1,"effects":[]}\';'},
		{opcode: 'respawn', blockType: 'command', text: {en: 'respawn', zh: '重生'}, stub: 'this.preview(\'respawn\');'},
		{label: {en: 'World', zh: '世界查询'}},
		{opcode: 'isDay', blockType: 'boolean', text: {en: 'is daytime?', zh: '是白天?'}, read: (s) => !!(s && s.isDay), stub: 'return true;'},
		{opcode: 'biome', blockType: 'reporter', text: {en: 'biome', zh: '当前生物群系'}, read: (s) => (s ? s.biome : ''), stub: 'return \'minecraft:plains\';'},
		{opcode: 'blockBelow', blockType: 'reporter', text: {en: 'block below', zh: '脚下方块'}, read: (s) => (s ? s.below : ''), stub: 'return \'minecraft:grass_block\';'},
		{opcode: 'blockInFront', blockType: 'boolean', text: {en: 'block in front?', zh: '前方有方块？'}, read: (s) => !!(s && s.aim && s.aim.has), stub: 'this.preview(\'blockInFront\'); return false;'},
		{opcode: 'blockAt', blockType: 'reporter', text: {en: 'block at x[X] y[Y] z[Z]', zh: '坐标处方块 x[X] y[Y] z[Z]'}, arguments: {X: {type: 'number', defaultValue: 0}, Y: {type: 'number', defaultValue: 64}, Z: {type: 'number', defaultValue: 0}}, stub: 'this.preview(\'blockAt \' + args.X + \',\' + args.Y + \',\' + args.Z); return \'minecraft:air\';', unsupported: true},
		{opcode: 'blockJson', blockType: 'reporter', text: {en: 'snapshot of block x[X] y[Y] z[Z]', zh: '方块 x[X] y[Y] z[Z] 的快照'}, arguments: {X: {type: 'number', defaultValue: 0}, Y: {type: 'number', defaultValue: 64}, Z: {type: 'number', defaultValue: 0}}, stub: 'return \'{"id":"","x":0,"y":0,"z":0,"state":{},"hardness":0,"is_air":false,"is_solid":false,"is_fluid":false,"light":0,"biome":"","block_entity":{"type":"","items":[],"items_total":0,"nbt":{},"text_front":"","text_back":""}}\';', unsupported: true},
		{opcode: 'frontBlock', blockType: 'reporter', text: {en: 'block at crosshair', zh: '准星所指方块'}, read: (s) => (s && s.aim ? s.aim.block : ''), stub: 'return \'minecraft:air\';'},
		{opcode: 'aimPos', blockType: 'reporter', text: {en: 'aimed block [AXIS] position', zh: '准星方块的 [AXIS] 坐标'}, arguments: {AXIS: {type: 'string', defaultValue: "x", menu: 'axis'}}, read: (s, a) => (s && s.aim ? s.aim[a[0]] : 0), stub: 'return 0;'},
		// 在线玩家：只有 uuid/名称/延迟/游戏模式可靠，坐标血量背包服务端不下发
		{opcode: 'playersJson', blockType: 'reporter', text: {en: 'online players snapshot', zh: '在线玩家快照'}, read: (s) => JSON.stringify(s && Array.isArray(s.players) ? s.players : []), stub: 'return \'[]\';'},
		{opcode: 'worldJson', blockType: 'reporter', text: {en: 'world snapshot', zh: '世界快照'}, read: (s) => JSON.stringify(s && s.worldInfo ? s.worldInfo : {}), stub: 'return \'{"time":0,"day":1,"moon_phase":0,"raining":false,"thundering":false,"dimension":"","biome":"","light":0}\';'},
		// 区域扫描：只给非空气方块；体积上限 8192 格、条数上限 [LIMIT]，超出置 truncated
		{opcode: 'scanJson', blockType: 'reporter', text: {en: 'scan x[X1] y[Y1] z[Z1] to x[X2] y[Y2] z[Z2] max [LIMIT]', zh: '扫描 x[X1] y[Y1] z[Z1] 到 x[X2] y[Y2] z[Z2] 限 [LIMIT]'}, arguments: {X1: {type: 'number', defaultValue: 0}, Y1: {type: 'number', defaultValue: 64}, Z1: {type: 'number', defaultValue: 0}, X2: {type: 'number', defaultValue: 15}, Y2: {type: 'number', defaultValue: 79}, Z2: {type: 'number', defaultValue: 15}, LIMIT: {type: 'number', defaultValue: 4096}}, stub: 'return \'{"x1":0,"y1":0,"z1":0,"x2":0,"y2":0,"z2":0,"truncated":false,"blocks":[]}\';', unsupported: true},
		// 快照字段：键清单的运行时出口，接到「说」上就能看全（键盘名不必翻文档）
		{opcode: 'snapshotKeys', blockType: 'reporter', text: {en: 'fields of [KIND]', zh: '[KIND] 的字段'}, arguments: {KIND: {type: 'string', defaultValue: "player", menu: 'snapshotKind'}}, stub: 'return \'[]\';',
			read: (s, a) => {
				const kind = String((a && a[0]) || 'player').trim().toLowerCase();
				const table = S.welcome && S.welcome.snapshotKeys ? S.welcome.snapshotKeys : null;
				if (!table) {
					return '[]';
				}
				return JSON.stringify(Array.isArray(table[kind]) ? table[kind] : []);
			}
		},
		{label: {en: 'Entities', zh: '实体查询'}},
		// 距离 / 三轴 / 名称合成一块：共享同一次世界遍历（WorldQueries 的单 tick 缓存按类型键控）
		{opcode: 'nearestEntityField', blockType: 'reporter', text: {en: 'nearest [TYPE] [FIELD]', zh: '最近 [TYPE] 的 [FIELD]'}, arguments: {TYPE: {type: 'string', defaultValue: "minecraft:zombie", menu: 'entityType'}, FIELD: {type: 'string', defaultValue: "distance", menu: 'entityField'}}, read: (s, a) => nearestEntityField(s, a), stub: 'return 0;'},
		{opcode: 'entityCount', blockType: 'reporter', text: {en: 'count of [TYPE] nearby', zh: '附近 [TYPE] 数量'}, arguments: {TYPE: {type: 'string', defaultValue: "minecraft:zombie", menu: 'entityType'}}, read: (s, a) => countEntities(s, entityFilter(a && a[0])), stub: 'return 0;'},
		// 半径与上限都可调：半径钳 1–64 格、上限钳 1–256 个（宿主侧做，防止误填卡主线程）
		{opcode: 'entitiesJson', blockType: 'reporter', text: {en: 'snapshot [TYPE] r[RADIUS] max[LIMIT]', zh: '附近 [TYPE] 快照 半径[RADIUS] 最多[LIMIT]'}, arguments: {TYPE: {type: 'string', defaultValue: "minecraft:zombie", menu: 'entityType'}, RADIUS: {type: 'number', defaultValue: 16}, LIMIT: {type: 'number', defaultValue: 32}}, read: (s, a) => entitiesJson(s, a), stub: 'return \'[]\';'},
		{label: {en: 'Display', zh: '显示与日志'}},
		{opcode: 'drawFrame', blockType: 'command', text: {en: 'frame x[X1] y[Y1] z[Z1] to x[X2] y[Y2] z[Z2] [COLOR]', zh: '画框 x[X1] y[Y1] z[Z1] 到 x[X2] y[Y2] z[Z2] [COLOR]'}, arguments: {X1: {type: 'number', defaultValue: 0}, Y1: {type: 'number', defaultValue: 64}, Z1: {type: 'number', defaultValue: 0}, X2: {type: 'number', defaultValue: 10}, Y2: {type: 'number', defaultValue: 70}, Z2: {type: 'number', defaultValue: 10}, COLOR: {type: 'color', defaultValue: "#33ff66"}}, stub: 'this.preview(\'drawFrame \' + args.X1 + \',\' + args.Y1 + \',\' + args.Z1\r\n\t\t\t\t+ \' -> \' + args.X2 + \',\' + args.Y2 + \',\' + args.Z2 + \' \' + args.COLOR);'},
		{opcode: 'clearFrames', blockType: 'command', text: {en: 'clear all frames', zh: '清除所有画框'}, stub: 'this.preview(\'clearFrames\');'},
		{opcode: 'log', blockType: 'command', text: {en: 'log [TEXT]', zh: '输出日志 [TEXT]'}, arguments: {TEXT: {type: 'string', defaultValue: "hello"}}, stub: 'console.log(\'[turbomc]\', args.TEXT);'},
		{label: {en: 'Chat & AI', zh: '聊天与 AI'}},
		{opcode: 'chat', blockType: 'command', text: {en: 'say [TEXT] in chat', zh: '在聊天栏说 [TEXT]'}, arguments: {TEXT: {type: 'string', defaultValue: "hello"}}, stub: 'this.preview(\'chat \' + args.TEXT);'},
		{opcode: 'command', blockType: 'command', text: {en: 'run command [COMMAND]', zh: '执行命令 [COMMAND]'}, arguments: {COMMAND: {type: 'string', defaultValue: "time set day"}}, stub: 'this.preview(\'command \' + args.COMMAND);'},
		// 聊天来源合并成一块：last 与 trigger 只差「取哪一条」，都是无参读取。
		// trigger 只认线程副本（只有被聊天触发的脚本才有），last 优先线程副本、再回落全局最近。
		{opcode: 'chatSource', blockType: 'reporter', text: {en: 'chat [SOURCE]', zh: '聊天 [SOURCE]'},
			arguments: {SOURCE: {type: 'string', defaultValue: "last", menu: 'chatSource'}},
			stub: 'this.preview(\'chatSource\'); return \'\';',
			read: (s, a, util) => {
				const tagged = util && util.thread ? util.thread.tmcChat : undefined;
				const hasTagged = tagged !== undefined && tagged !== null;
				if ((a && a[0]) === 'trigger') {
					return hasTagged ? String(tagged) : '';
				}
				if (hasTagged) {
					return String(tagged);
				}
				return s && s.chat ? s.chat.last : '';
			}},
		// 聊天历史：环形缓冲 32 条，这里按「最近 N 条」取，旧的在前
		{opcode: 'chatHistory', blockType: 'reporter', text: {en: 'last [COUNT] chat messages', zh: '最近 [COUNT] 条聊天'}, arguments: {COUNT: {type: 'number', defaultValue: 10}}, stub: 'return \'[]\';',
			read: (s, a) => {
				const count = Math.max(1, Math.min(32, Number((a && a[0]) === undefined ? 10 : a[0]) || 0));
				const history = s && s.chat && Array.isArray(s.chat.history) ? s.chat.history : [];
				return JSON.stringify(history.slice(Math.max(0, history.length - count)));
			}
		},
		{opcode: 'aiAsk', blockType: 'command', text: {en: 'ask session [SESSION] to reply to [TEXT]', zh: '让会话 [SESSION] 回应 [TEXT]'}, arguments: {SESSION: {type: 'string', defaultValue: "main"}, TEXT: {type: 'string', defaultValue: "hello"}}, stub: 'this.preview(\'aiAsk \' + args.SESSION + \' \' + args.TEXT);'},
		{opcode: 'aiReply', blockType: 'reporter', text: {en: 'last reply of session [SESSION]', zh: '会话 [SESSION] 的最后回复'}, arguments: {SESSION: {type: 'string', defaultValue: "main"}}, stub: 'this.preview(\'aiReply\'); return \'\';',
			read: (s, a) => {
				const session = String((a && a[0]) || '').trim();
				const table = s && s.ai ? s.ai : null;
				if (!table) {
					return '';
				}
				const key = session || 'default';
				return table[key] === undefined || table[key] === null ? '' : String(table[key]);
			}
		},
		{opcode: 'aiSystem', blockType: 'command', text: {en: 'set system prompt of session [SESSION] to [TEXT]', zh: '设置会话 [SESSION] 的系统提示为 [TEXT]'}, arguments: {SESSION: {type: 'string', defaultValue: "main"}, TEXT: {type: 'string', defaultValue: "you are a Minecraft helper"}}, stub: 'this.preview(\'aiSystem\');'},
		{opcode: 'aiReset', blockType: 'command', text: {en: 'clear conversation of session [SESSION]', zh: '清空会话 [SESSION] 的对话记录'}, arguments: {SESSION: {type: 'string', defaultValue: "main"}}, stub: 'this.preview(\'aiReset\');'},
		{label: {en: 'JSON · Check & create', zh: 'JSON · 校验与新建'}},
		{opcode: 'jsonIs', blockType: 'boolean', text: {en: 'is [json] [check]?', zh: '[json] 是 [check] 吗?'}, arguments: {json: {type: 'string', defaultValue: "{\"a\":1}"}, check: {type: 'string', defaultValue: "valid", menu: 'jsonCheck'}}},
		{opcode: 'jsonNew', blockType: 'reporter', text: {en: 'new [json]', zh: '新建 [json]'}, arguments: {json: {type: 'string', defaultValue: "Object", menu: 'jsonKind'}}},
		{opcode: 'jsonLength', blockType: 'reporter', text: {en: 'length of [json]', zh: '[json] 的长度'}, arguments: {json: {type: 'string', defaultValue: "{\"a\":1,\"b\":2}"}}},
		{opcode: 'jsonHasKey', blockType: 'boolean', text: {en: '[json] contains key [key]?', zh: '[json] 包含键 [key] 吗?'}, arguments: {json: {type: 'string', defaultValue: "{\"a\":1}"}, key: {type: 'string', defaultValue: "a"}}},
		{opcode: 'jsonGetAll', blockType: 'reporter', text: {en: 'all [Stype] of [json]', zh: '[json] 的全部 [Stype]'}, arguments: {json: {type: 'string', defaultValue: "{\"a\":1,\"b\":2}"}, Stype: {type: 'string', menu: 'jsonAll'}}},
		{opcode: 'jsonErrMsg', blockType: 'reporter', text: {en: 'parse error from [json]', zh: '[json] 的解析错误'}, arguments: {json: {type: 'string', defaultValue: "{\"a\":1,}"}}},
		{opcode: 'jsonMinify', blockType: 'reporter', text: {en: 'minify [json]', zh: '压缩 [json]'}, arguments: {json: {type: 'string', defaultValue: " { \"a\" : 1 } "}}},
		{label: {en: 'JSON · Objects & paths', zh: 'JSON · 对象与路径'}},
		{opcode: 'jsonGet', blockType: 'reporter', text: {en: 'value of [item] in [json]', zh: '[json] 中 [item] 的值'}, arguments: {json: {type: 'string', defaultValue: "{\"a\":1}"}, item: {type: 'string', defaultValue: "a"}}},
		{opcode: 'jsonSet', blockType: 'reporter', text: {en: 'set [item] in [json] to [value]', zh: '把 [json] 中 [item] 设为 [value]'}, arguments: {json: {type: 'string', defaultValue: "{\"a\":1}"}, item: {type: 'string', defaultValue: "a"}, value: {type: 'string', defaultValue: "2"}}},
		{opcode: 'jsonDelete', blockType: 'reporter', text: {en: 'delete [item] in [json]', zh: '删除 [json] 中的 [item]'}, arguments: {json: {type: 'string', defaultValue: "{\"a\":1,\"b\":2}"}, item: {type: 'string', defaultValue: "b"}}},
		{opcode: 'jsonGetPath', blockType: 'reporter', text: {en: 'value by path [path] in [json]', zh: '[json] 中路径 [path] 的值'}, arguments: {json: {type: 'string', defaultValue: "{\"a\":{\"b\":[1,0]}}"}, path: {type: 'string', defaultValue: "[\"a\",\"b\",1]"}}},
		{opcode: 'jsonSetPath', blockType: 'reporter', text: {en: 'set value by path [path] in [json] to [data]', zh: '把 [json] 中路径 [path] 的值设为 [data]'}, arguments: {json: {type: 'string', defaultValue: "{\"a\":{\"b\":[1,0]}}"}, path: {type: 'string', defaultValue: "[\"a\",\"b\",1]"}, data: {type: 'string', defaultValue: "2"}}},
		{label: {en: 'JSON · Array access', zh: 'JSON · 数组读写'}},
		{opcode: 'jsonArrayPush', blockType: 'reporter', text: {en: 'add [item] to array [json]', zh: '向数组 [json] 添加 [item]'}, arguments: {json: {type: 'string', defaultValue: "[\"a\"]"}, item: {type: 'string', defaultValue: "b"}}},
		{opcode: 'jsonArraySet', blockType: 'reporter', text: {en: 'replace item [pos] of [json] with [item]', zh: '把数组 [json] 的第 [pos] 项换成 [item]'}, arguments: {json: {type: 'string', defaultValue: "[\"a\",\"b\"]"}, pos: {type: 'number', defaultValue: 2}, item: {type: 'string', defaultValue: "c"}}},
		{opcode: 'jsonArrayInsert', blockType: 'reporter', text: {en: 'insert [item] at [pos] of array [json]', zh: '在数组 [json] 的第 [pos] 项插入 [item]'}, arguments: {json: {type: 'string', defaultValue: "[\"a\",\"b\"]"}, pos: {type: 'number', defaultValue: 2}, item: {type: 'string', defaultValue: "c"}}},
		{opcode: 'jsonArrayDelete', blockType: 'reporter', text: {en: 'delete item [item] of array [json]', zh: '删除数组 [json] 的第 [item] 项'}, arguments: {json: {type: 'string', defaultValue: "[\"a\",\"b\"]"}, item: {type: 'number', defaultValue: 2}}},
		{opcode: 'jsonArrayRemoveAll', blockType: 'reporter', text: {en: 'delete all [item] in array [json]', zh: '删除数组 [json] 中所有 [item]'}, arguments: {json: {type: 'string', defaultValue: "[\"a\",\"b\",\"a\"]"}, item: {type: 'string', defaultValue: "a"}}},
		{opcode: 'jsonArrayItemNum', blockType: 'reporter', text: {en: 'item # of [item] in array [json]', zh: '[item] 在数组 [json] 中的项号'}, arguments: {item: {type: 'string', defaultValue: "a"}, json: {type: 'string', defaultValue: "[\"a\",\"b\"]"}}},
		{opcode: 'jsonArrayFromTo', blockType: 'reporter', text: {en: 'items [item] to [item2] of array [json]', zh: '数组 [json] 的第 [item] 到第 [item2] 项'}, arguments: {json: {type: 'string', defaultValue: "[1,2,3]"}, item: {type: 'number', defaultValue: 2}, item2: {type: 'number', defaultValue: 3}}},
		{opcode: 'jsonArraySetLen', blockType: 'reporter', text: {en: 'set length of array [json] to [len]', zh: '把数组 [json] 的长度设为 [len]'}, arguments: {json: {type: 'string', defaultValue: "[\"a\",\"b\"]"}, len: {type: 'number', defaultValue: 1}}},
		{label: {en: 'JSON · Array transform', zh: 'JSON · 数组变换'}},
		{opcode: 'jsonArrayConcat', blockType: 'reporter', text: {en: 'concatenate array [json] [json2]', zh: '拼接数组 [json] 和 [json2]'}, arguments: {json: {type: 'string', defaultValue: "[\"a\"]"}, json2: {type: 'string', defaultValue: "[\"b\"]"}}},
		{opcode: 'jsonArrayMapField', blockType: 'reporter', text: {en: 'get all values with key [key] in array [json]', zh: '数组 [json] 中所有对象的 [key] 值'}, arguments: {json: {type: 'string', defaultValue: "[{\"a\":1},{\"a\":2}]"}, key: {type: 'string', defaultValue: "a"}}},
		{opcode: 'jsonArrayCreate', blockType: 'reporter', text: {en: 'create array by [text] with delimiter [d]', zh: '按分隔符 [d] 把文本 [text] 拆成数组'}, arguments: {d: {type: 'string', defaultValue: ","}, text: {type: 'string', defaultValue: "a,b"}}},
		{opcode: 'jsonArrayJoin', blockType: 'reporter', text: {en: 'join string by array [json] with delimiter [d]', zh: '用分隔符 [d] 把数组 [json] 拼成文本'}, arguments: {d: {type: 'string', defaultValue: ","}, json: {type: 'string', defaultValue: "[\"a\",\"b\"]"}}},
		{opcode: 'jsonArraySort', blockType: 'reporter', text: {en: 'sort array [list] in [order] order', zh: '数组 [list] 按 [order] 排序'}, arguments: {list: {type: 'string', defaultValue: "[3,1,2]"}, order: {type: 'string', menu: 'jsonSort'}}},
		{opcode: 'jsonArrayTextFilter', blockType: 'reporter', text: {en: 'filter [list] text [options] [text] return [type]', zh: '筛选 [list] 文本 [options] [text] 返回 [type]'}, arguments: {list: {type: 'string', defaultValue: "[\"a\",\"b\"]"}, options: {type: 'string', menu: 'jsonFilter'}, text: {type: 'string', defaultValue: "a"}, type: {type: 'string', menu: 'jsonKind'}}},
		{opcode: 'jsonArrayAnalysis', blockType: 'reporter', text: {en: '[analysis] of array [list]', zh: '数组 [list] 的 [analysis]'}, arguments: {list: {type: 'string', defaultValue: "[1,2,3]"}, analysis: {type: 'string', menu: 'jsonAnalysis'}}},
		{label: {en: 'JSON · List bridge', zh: 'JSON · 与列表互通'}},
		{opcode: 'jsonVmGetList', blockType: 'reporter', text: {en: 'get list [list] as array', zh: '把列表 [list] 读成数组'}, arguments: {list: {type: 'string', defaultValue: "mylist"}}, stub: 'this.preview(\'jsonVmGetList \' + args.list); return \'[]\';'},
		{opcode: 'jsonVmSetList', blockType: 'command', text: {en: 'set list [list] to [json]', zh: '把列表 [list] 设为 [json]'}, arguments: {list: {type: 'string', defaultValue: "mylist"}, json: {type: 'string', defaultValue: "[\"a\",\"b\"]"}}, stub: 'this.preview(\'jsonVmSetList \' + args.list);'}
	],

	menus: {
		jsonKind: {acceptReporters: true, items: [
			{text: {en: 'object', zh: '对象'}, value: 'Object'},
			{text: {en: 'array', zh: '数组'}, value: 'Array'}
		]},
		jsonCheck: {acceptReporters: true, items: [
			{text: {en: 'valid JSON', zh: '合法 JSON'}, value: 'valid'},
			{text: {en: 'object', zh: '对象'}, value: 'Object'},
			{text: {en: 'array', zh: '数组'}, value: 'Array'}
		]},
		jsonAll: {acceptReporters: false, items: [
			{text: {en: 'keys', zh: '键'}, value: 'keys'},
			{text: {en: 'values', zh: '值'}, value: 'values'},
			{text: {en: 'key-value pairs', zh: '键值对'}, value: 'datas'}
		]},
		jsonSort: {acceptReporters: true, items: [
			{text: {en: 'ascending', zh: '升序'}, value: 'ascending'},
			{text: {en: 'descending', zh: '降序'}, value: 'descending'}
		]},
		jsonFilter: {acceptReporters: false, items: [
			{text: {en: 'includes', zh: '包含'}, value: 'includes'},
			{text: {en: 'starts with', zh: '开头是'}, value: 'starts with'},
			{text: {en: 'ends with', zh: '结尾是'}, value: 'ends with'}
		]},
		jsonAnalysis: {acceptReporters: true, items: [
			{text: {en: 'minimum', zh: '最小值'}, value: 'minimum'},
			{text: {en: 'maximum', zh: '最大值'}, value: 'maximum'},
			{text: {en: 'sum', zh: '总和'}, value: 'sum'},
			{text: {en: 'average', zh: '平均值'}, value: 'average'},
			{text: {en: 'median', zh: '中位数'}, value: 'median'},
			{text: {en: 'mode', zh: '众数'}, value: 'mode'},
			{text: {en: 'variance', zh: '方差'}, value: 'variance'}
		]},
		// ---- 合并积木用的下拉菜单 ----
		// acceptReporters: false ⇒ 渲染成字段下拉（不能塞报告器），Blocks.java 用 fieldOption 登记。
		// value 一律 ASCII 且与语言无关：工程文件里存的是 value，切语言不能变。
		axis: {acceptReporters: false, items: [
			{text: {en: 'X', zh: 'X'}, value: 'x'},
			{text: {en: 'Y', zh: 'Y'}, value: 'y'},
			{text: {en: 'Z', zh: 'Z'}, value: 'z'}
		]},
		// 视角命令的「绝对 / 增量」。瞬时与平滑两块共用同一份菜单：两者只差
		// 「直接写值」与「逐帧收敛」，角度语义必须一致，否则同一份脚本换一块积木结果就变。
		viewMode: {acceptReporters: false, items: [
			{text: {en: 'absolute', zh: '绝对'}, value: 'absolute'},
			{text: {en: 'relative', zh: '增量'}, value: 'relative'}
		]},
		// 背包 / 界面是两套槽位：背包是 36+4 的 PlayerInventory，界面是当前 ScreenHandler。
		// 槽位号在两边含义不同，所以范围必须由使用者显式选，不能默认某一个。
		inventoryRange: {acceptReporters: false, items: [
			{text: {en: 'backpack', zh: '背包'}, value: 'backpack'},
			{text: {en: 'open container', zh: '当前界面'}, value: 'screen'}
		]},
		inventoryKind: {acceptReporters: false, items: [
			{text: {en: 'count', zh: '数量'}, value: 'count'},
			{text: {en: 'first slot', zh: '首个槽位'}, value: 'first_slot'}
		]},
		chatSource: {acceptReporters: false, items: [
			{text: {en: 'last message', zh: '最近'}, value: 'last'},
			{text: {en: 'that triggered me', zh: '触发我的'}, value: 'trigger'}
		]},
		// 「玩家的 [字段]」的字段表。value 是**长名**（与玩家 JSON 快照一致），
		// 远程快照那套短名（hp / dim / slot / xpLevel）在积木的 read 里换算。
		// 顺序按常用度排：读数类 → 坐标 → 视角 → 维度 → 延迟。
		playerField: {acceptReporters: false, items: [
			{text: {en: 'health', zh: '血量'}, value: 'health'},
			{text: {en: 'max health', zh: '最大血量'}, value: 'max_health'},
			{text: {en: 'armor', zh: '护甲'}, value: 'armor'},
			{text: {en: 'air', zh: '氧气'}, value: 'air'},
			{text: {en: 'food level', zh: '饱食度'}, value: 'food'},
			{text: {en: 'xp level', zh: '经验等级'}, value: 'xp_level'},
			{text: {en: 'selected slot', zh: '当前槽位'}, value: 'selected_slot'},
			{text: {en: 'x position', zh: 'X 坐标'}, value: 'x'},
			{text: {en: 'y position', zh: 'Y 坐标'}, value: 'y'},
			{text: {en: 'z position', zh: 'Z 坐标'}, value: 'z'},
			{text: {en: 'yaw', zh: '水平视角'}, value: 'yaw'},
			{text: {en: 'pitch', zh: '俯仰视角'}, value: 'pitch'},
			{text: {en: 'dimension', zh: '当前维度'}, value: 'dimension'},
			{text: {en: 'server ping (ms)', zh: '服务端延迟(ms)'}, value: 'ping'}
		]},
		entityField: {acceptReporters: false, items: [
			{text: {en: 'distance', zh: '距离'}, value: 'distance'},
			{text: {en: 'X', zh: 'X'}, value: 'x'},
			{text: {en: 'Y', zh: 'Y'}, value: 'y'},
			{text: {en: 'Z', zh: 'Z'}, value: 'z'},
			{text: {en: 'name', zh: '名称'}, value: 'name'},
			{text: {en: 'health', zh: '血量'}, value: 'health'},
			{text: {en: 'max health', zh: '最大血量'}, value: 'max_health'},
			// 注册名与「名称」不同：name 是本地化显示名（僵尸），type_id 是 minecraft:zombie
			{text: {en: 'registry id', zh: '注册名'}, value: 'type_id'},
			// 幼年与着地：抓特定实体（幼年僵尸、落地判断）时常用；都是从实体本身读，不额外遍历
			{text: {en: 'baby?', zh: '幼年?'}, value: 'is_baby'},
			{text: {en: 'on ground?', zh: '着地?'}, value: 'on_ground'}
		]},
		heldField: {acceptReporters: false, items: [
			{text: {en: 'item', zh: '物品'}, value: 'id'},
			{text: {en: 'durability %', zh: '耐久百分比'}, value: 'durability'},
			{text: {en: 'count', zh: '数量'}, value: 'count'},
			{text: {en: 'max durability', zh: '最大耐久'}, value: 'max_durability'}
		]},
		slotField: {acceptReporters: false, items: [
			{text: {en: 'item', zh: '物品'}, value: 'id'},
			{text: {en: 'amount', zh: '数量'}, value: 'amount'},
			{text: {en: 'max durability', zh: '最大耐久'}, value: 'max_durability'},
			{text: {en: 'damage', zh: '已损耗'}, value: 'damage'},
			{text: {en: 'custom name', zh: '自定义名称'}, value: 'custom_name'}
		]},
		itemId: {dynamic: 'items', acceptReporters: true},
		entityType: {acceptReporters: true, items: [
			{text: {en: 'any entity', zh: '任意实体'}, value: 'any'},
			{text: {en: 'hostile mob', zh: '敌对生物'}, value: 'hostile'},
			{text: {en: 'player', zh: '玩家'}, value: 'player'},
			{text: {en: 'Zombie', zh: '僵尸'}, value: 'minecraft:zombie'},
			{text: {en: 'Skeleton', zh: '骷髅'}, value: 'minecraft:skeleton'},
			{text: {en: 'Creeper', zh: '苦力怕'}, value: 'minecraft:creeper'},
			{text: {en: 'Spider', zh: '蜘蛛'}, value: 'minecraft:spider'},
			{text: {en: 'Enderman', zh: '末影人'}, value: 'minecraft:enderman'},
			{text: {en: 'Witch', zh: '女巫'}, value: 'minecraft:witch'},
			{text: {en: 'Slime', zh: '史莱姆'}, value: 'minecraft:slime'},
			{text: {en: 'Drowned', zh: '溺尸'}, value: 'minecraft:drowned'},
			{text: {en: 'Husk', zh: '尸壳'}, value: 'minecraft:husk'},
			{text: {en: 'Zombie Villager', zh: '僵尸村民'}, value: 'minecraft:zombie_villager'},
			{text: {en: 'Stray', zh: '流浪者'}, value: 'minecraft:stray'},
			{text: {en: 'Bogged', zh: '沼骸'}, value: 'minecraft:bogged'},
			{text: {en: 'Parched', zh: '焦骸'}, value: 'minecraft:parched'},
			{text: {en: 'Wither Skeleton', zh: '凋灵骷髅'}, value: 'minecraft:wither_skeleton'},
			{text: {en: 'Cave Spider', zh: '洞穴蜘蛛'}, value: 'minecraft:cave_spider'},
			{text: {en: 'Silverfish', zh: '蠹虫'}, value: 'minecraft:silverfish'},
			{text: {en: 'Endermite', zh: '末影螨'}, value: 'minecraft:endermite'},
			{text: {en: 'Magma Cube', zh: '岩浆怪'}, value: 'minecraft:magma_cube'},
			{text: {en: 'Blaze', zh: '烈焰人'}, value: 'minecraft:blaze'},
			{text: {en: 'Ghast', zh: '恶魂'}, value: 'minecraft:ghast'},
			{text: {en: 'Happy Ghast', zh: '快乐恶魂'}, value: 'minecraft:happy_ghast'},
			{text: {en: 'Phantom', zh: '幻翼'}, value: 'minecraft:phantom'},
			{text: {en: 'Pillager', zh: '掠夺者'}, value: 'minecraft:pillager'},
			{text: {en: 'Vindicator', zh: '卫道士'}, value: 'minecraft:vindicator'},
			{text: {en: 'Evoker', zh: '唤魔者'}, value: 'minecraft:evoker'},
			{text: {en: 'Illusioner', zh: '幻术师'}, value: 'minecraft:illusioner'},
			{text: {en: 'Ravager', zh: '劫掠兽'}, value: 'minecraft:ravager'},
			{text: {en: 'Vex', zh: '恼鬼'}, value: 'minecraft:vex'},
			{text: {en: 'Guardian', zh: '守卫者'}, value: 'minecraft:guardian'},
			{text: {en: 'Elder Guardian', zh: '远古守卫者'}, value: 'minecraft:elder_guardian'},
			{text: {en: 'Shulker', zh: '潜影贝'}, value: 'minecraft:shulker'},
			{text: {en: 'Warden', zh: '监守者'}, value: 'minecraft:warden'},
			{text: {en: 'Breeze', zh: '旋风人'}, value: 'minecraft:breeze'},
			{text: {en: 'Creaking', zh: '嘎枝'}, value: 'minecraft:creaking'},
			{text: {en: 'Giant', zh: '巨人'}, value: 'minecraft:giant'},
			{text: {en: 'Wither', zh: '凋灵'}, value: 'minecraft:wither'},
			{text: {en: 'Ender Dragon', zh: '末影龙'}, value: 'minecraft:ender_dragon'},
			{text: {en: 'Piglin', zh: '猪灵'}, value: 'minecraft:piglin'},
			{text: {en: 'Piglin Brute', zh: '猪灵蛮兵'}, value: 'minecraft:piglin_brute'},
			{text: {en: 'Zombified Piglin', zh: '僵尸猪灵'}, value: 'minecraft:zombified_piglin'},
			{text: {en: 'Hoglin', zh: '疣猪兽'}, value: 'minecraft:hoglin'},
			{text: {en: 'Zoglin', zh: '僵尸疣猪兽'}, value: 'minecraft:zoglin'},
			{text: {en: 'Villager', zh: '村民'}, value: 'minecraft:villager'},
			{text: {en: 'Wandering Trader', zh: '流浪商人'}, value: 'minecraft:wandering_trader'},
			{text: {en: 'Iron Golem', zh: '铁傀儡'}, value: 'minecraft:iron_golem'},
			{text: {en: 'Snow Golem', zh: '雪傀儡'}, value: 'minecraft:snow_golem'},
			{text: {en: 'Cow', zh: '牛'}, value: 'minecraft:cow'},
			{text: {en: 'Pig', zh: '猪'}, value: 'minecraft:pig'},
			{text: {en: 'Sheep', zh: '绵羊'}, value: 'minecraft:sheep'},
			{text: {en: 'Chicken', zh: '鸡'}, value: 'minecraft:chicken'},
			{text: {en: 'Rabbit', zh: '兔子'}, value: 'minecraft:rabbit'},
			{text: {en: 'Horse', zh: '马'}, value: 'minecraft:horse'},
			{text: {en: 'Donkey', zh: '驴'}, value: 'minecraft:donkey'},
			{text: {en: 'Mule', zh: '骡'}, value: 'minecraft:mule'},
			{text: {en: 'Camel', zh: '骆驼'}, value: 'minecraft:camel'},
			{text: {en: 'Camel Husk', zh: '骆驼尸壳'}, value: 'minecraft:camel_husk'},
			{text: {en: 'Wolf', zh: '狼'}, value: 'minecraft:wolf'},
			{text: {en: 'Cat', zh: '猫'}, value: 'minecraft:cat'},
			{text: {en: 'Ocelot', zh: '豹猫'}, value: 'minecraft:ocelot'},
			{text: {en: 'Fox', zh: '狐狸'}, value: 'minecraft:fox'},
			{text: {en: 'Panda', zh: '熊猫'}, value: 'minecraft:panda'},
			{text: {en: 'Polar Bear', zh: '北极熊'}, value: 'minecraft:polar_bear'},
			{text: {en: 'Bee', zh: '蜜蜂'}, value: 'minecraft:bee'},
			{text: {en: 'Parrot', zh: '鹦鹉'}, value: 'minecraft:parrot'},
			{text: {en: 'Bat', zh: '蝙蝠'}, value: 'minecraft:bat'},
			{text: {en: 'Squid', zh: '鱿鱼'}, value: 'minecraft:squid'},
			{text: {en: 'Glow Squid', zh: '发光鱿鱼'}, value: 'minecraft:glow_squid'},
			{text: {en: 'Dolphin', zh: '海豚'}, value: 'minecraft:dolphin'},
			{text: {en: 'Cod', zh: '鳕鱼'}, value: 'minecraft:cod'},
			{text: {en: 'Salmon', zh: '鲑鱼'}, value: 'minecraft:salmon'},
			{text: {en: 'Tropical Fish', zh: '热带鱼'}, value: 'minecraft:tropical_fish'},
			{text: {en: 'Pufferfish', zh: '河豚'}, value: 'minecraft:pufferfish'},
			{text: {en: 'Tadpole', zh: '蝌蚪'}, value: 'minecraft:tadpole'},
			{text: {en: 'Frog', zh: '青蛙'}, value: 'minecraft:frog'},
			{text: {en: 'Turtle', zh: '海龟'}, value: 'minecraft:turtle'},
			{text: {en: 'Axolotl', zh: '美西螈'}, value: 'minecraft:axolotl'},
			{text: {en: 'Mooshroom', zh: '哞菇'}, value: 'minecraft:mooshroom'},
			{text: {en: 'Sniffer', zh: '嗅探兽'}, value: 'minecraft:sniffer'},
			{text: {en: 'Armadillo', zh: '犰狳'}, value: 'minecraft:armadillo'},
			{text: {en: 'Allay', zh: '悦灵'}, value: 'minecraft:allay'},
			{text: {en: 'Goat', zh: '山羊'}, value: 'minecraft:goat'},
			{text: {en: 'Llama', zh: '羊驼'}, value: 'minecraft:llama'},
			{text: {en: 'Trader Llama', zh: '行商羊驼'}, value: 'minecraft:trader_llama'},
			{text: {en: 'Strider', zh: '炽足兽'}, value: 'minecraft:strider'},
			{text: {en: 'Copper Golem', zh: '铜傀儡'}, value: 'minecraft:copper_golem'},
			{text: {en: 'Nautilus', zh: '鹦鹉螺'}, value: 'minecraft:nautilus'},
			{text: {en: 'Zombie Nautilus', zh: '僵尸鹦鹉螺'}, value: 'minecraft:zombie_nautilus'},
			{text: {en: 'Mannequin', zh: '玩家模型'}, value: 'minecraft:mannequin'},
			{text: {en: 'Skeleton Horse', zh: '骷髅马'}, value: 'minecraft:skeleton_horse'},
			{text: {en: 'Zombie Horse', zh: '僵尸马'}, value: 'minecraft:zombie_horse'},
			{text: {en: 'Acacia Boat', zh: '金合欢木船'}, value: 'minecraft:acacia_boat'},
			{text: {en: 'Acacia Chest Boat', zh: '金合欢木运输船'}, value: 'minecraft:acacia_chest_boat'},
			{text: {en: 'Area Effect Cloud', zh: '区域效果云'}, value: 'minecraft:area_effect_cloud'},
			{text: {en: 'Armor Stand', zh: '盔甲架'}, value: 'minecraft:armor_stand'},
			{text: {en: 'Arrow', zh: '箭'}, value: 'minecraft:arrow'},
			{text: {en: 'Bamboo Chest Raft', zh: '运输竹筏'}, value: 'minecraft:bamboo_chest_raft'},
			{text: {en: 'Bamboo Raft', zh: '竹筏'}, value: 'minecraft:bamboo_raft'},
			{text: {en: 'Birch Boat', zh: '白桦木船'}, value: 'minecraft:birch_boat'},
			{text: {en: 'Birch Chest Boat', zh: '白桦木运输船'}, value: 'minecraft:birch_chest_boat'},
			{text: {en: 'Block Display', zh: '方块展示实体'}, value: 'minecraft:block_display'},
			{text: {en: 'Wind Charge', zh: '风弹'}, value: 'minecraft:breeze_wind_charge'},
			{text: {en: 'Cherry Boat', zh: '樱花木船'}, value: 'minecraft:cherry_boat'},
			{text: {en: 'Cherry Chest Boat', zh: '樱花木运输船'}, value: 'minecraft:cherry_chest_boat'},
			{text: {en: 'Chest Minecart', zh: '运输矿车'}, value: 'minecraft:chest_minecart'},
			{text: {en: 'Command Cart', zh: '命令方块矿车'}, value: 'minecraft:command_block_minecart'},
			{text: {en: 'Dark Oak Boat', zh: '深色橡木船'}, value: 'minecraft:dark_oak_boat'},
			{text: {en: 'Dark Oak Chest Boat', zh: '深色橡木运输船'}, value: 'minecraft:dark_oak_chest_boat'},
			{text: {en: 'Dragon Fireball', zh: '末影龙火球'}, value: 'minecraft:dragon_fireball'},
			{text: {en: 'Thrown Egg', zh: '掷出的鸡蛋'}, value: 'minecraft:egg'},
			{text: {en: 'End Crystal', zh: '末地水晶'}, value: 'minecraft:end_crystal'},
			{text: {en: 'Ender Pearl', zh: '掷出的末影珍珠'}, value: 'minecraft:ender_pearl'},
			{text: {en: 'Evoker Fangs', zh: '唤魔者尖牙'}, value: 'minecraft:evoker_fangs'},
			{text: {en: 'XP Bottle', zh: '掷出的附魔之瓶'}, value: 'minecraft:experience_bottle'},
			{text: {en: 'Experience Orb', zh: '经验球'}, value: 'minecraft:experience_orb'},
			{text: {en: 'Eye of Ender', zh: '末影之眼'}, value: 'minecraft:eye_of_ender'},
			{text: {en: 'Falling Block', zh: '下落的方块'}, value: 'minecraft:falling_block'},
			{text: {en: 'Fireball', zh: '火球'}, value: 'minecraft:fireball'},
			{text: {en: 'Firework Rocket', zh: '烟花火箭'}, value: 'minecraft:firework_rocket'},
			{text: {en: 'Fishing Bobber', zh: '浮漂'}, value: 'minecraft:fishing_bobber'},
			{text: {en: 'Furnace Minecart', zh: '动力矿车'}, value: 'minecraft:furnace_minecart'},
			{text: {en: 'Glow Item Frame', zh: '荧光物品展示框'}, value: 'minecraft:glow_item_frame'},
			{text: {en: 'Hopper Minecart', zh: '漏斗矿车'}, value: 'minecraft:hopper_minecart'},
			{text: {en: 'Interaction', zh: '交互实体'}, value: 'minecraft:interaction'},
			{text: {en: 'Item', zh: '物品'}, value: 'minecraft:item'},
			{text: {en: 'Item Display', zh: '物品展示实体'}, value: 'minecraft:item_display'},
			{text: {en: 'Item Frame', zh: '物品展示框'}, value: 'minecraft:item_frame'},
			{text: {en: 'Jungle Boat', zh: '丛林木船'}, value: 'minecraft:jungle_boat'},
			{text: {en: 'Jungle Chest Boat', zh: '丛林木运输船'}, value: 'minecraft:jungle_chest_boat'},
			{text: {en: 'Leash Knot', zh: '拴绳结'}, value: 'minecraft:leash_knot'},
			{text: {en: 'Lightning Bolt', zh: '闪电束'}, value: 'minecraft:lightning_bolt'},
			{text: {en: 'Lingering Potion', zh: '滞留药水'}, value: 'minecraft:lingering_potion'},
			{text: {en: 'Llama Spit', zh: '羊驼唾沫'}, value: 'minecraft:llama_spit'},
			{text: {en: 'Mangrove Boat', zh: '红树木船'}, value: 'minecraft:mangrove_boat'},
			{text: {en: 'Mangrove Chest Boat', zh: '红树木运输船'}, value: 'minecraft:mangrove_chest_boat'},
			{text: {en: 'Marker', zh: '标记'}, value: 'minecraft:marker'},
			{text: {en: 'Minecart', zh: '矿车'}, value: 'minecraft:minecart'},
			{text: {en: 'Oak Boat', zh: '橡木船'}, value: 'minecraft:oak_boat'},
			{text: {en: 'Oak Chest Boat', zh: '橡木运输船'}, value: 'minecraft:oak_chest_boat'},
			{text: {en: 'Ominous Spawner', zh: '不祥之物生成器'}, value: 'minecraft:ominous_item_spawner'},
			{text: {en: 'Painting', zh: '画'}, value: 'minecraft:painting'},
			{text: {en: 'Pale Oak Boat', zh: '苍白橡木船'}, value: 'minecraft:pale_oak_boat'},
			{text: {en: 'Pale Oak Chest Boat', zh: '苍白橡木运输船'}, value: 'minecraft:pale_oak_chest_boat'},
			{text: {en: 'Shulker Bullet', zh: '潜影弹'}, value: 'minecraft:shulker_bullet'},
			{text: {en: 'Small Fireball', zh: '小火球'}, value: 'minecraft:small_fireball'},
			{text: {en: 'Snowball', zh: '雪球'}, value: 'minecraft:snowball'},
			{text: {en: 'Spawner Minecart', zh: '刷怪笼矿车'}, value: 'minecraft:spawner_minecart'},
			{text: {en: 'Spectral Arrow', zh: '光灵箭'}, value: 'minecraft:spectral_arrow'},
			{text: {en: 'Splash Potion', zh: '喷溅药水'}, value: 'minecraft:splash_potion'},
			{text: {en: 'Spruce Boat', zh: '云杉木船'}, value: 'minecraft:spruce_boat'},
			{text: {en: 'Spruce Chest Boat', zh: '云杉木运输船'}, value: 'minecraft:spruce_chest_boat'},
			{text: {en: 'Text Display', zh: '文本展示实体'}, value: 'minecraft:text_display'},
			{text: {en: 'Primed TNT', zh: '被激活的TNT'}, value: 'minecraft:tnt'},
			{text: {en: 'TNT Minecart', zh: 'TNT矿车'}, value: 'minecraft:tnt_minecart'},
			{text: {en: 'Trident', zh: '三叉戟'}, value: 'minecraft:trident'},
			{text: {en: 'Wind Charge', zh: '风弹'}, value: 'minecraft:wind_charge'},
			{text: {en: 'Wither Skull', zh: '凋灵之首'}, value: 'minecraft:wither_skull'}
		]},
		snapshotKind: {acceptReporters: false, items: [
			{text: {en: 'player snapshot', zh: '玩家快照'}, value: 'player'},
			{text: {en: 'entity (list item)', zh: '实体（列表项）'}, value: 'entity'},
			{text: {en: 'item entry', zh: '物品条目'}, value: 'item'},
			{text: {en: 'screen', zh: '界面'}, value: 'screen'},
			{text: {en: 'block', zh: '方块'}, value: 'block'},
			{text: {en: 'block entity', zh: '方块实体'}, value: 'block_entity'},
			{text: {en: 'world', zh: '世界'}, value: 'world'},
			{text: {en: 'online players (list item)', zh: '在线玩家（列表项）'}, value: 'players'},
			{text: {en: 'area scan', zh: '区域扫描'}, value: 'scan'},
			{text: {en: 'chat history', zh: '聊天历史'}, value: 'chat'}
		]},
		screenKind: {acceptReporters: false, items: [
			{text: {en: 'container', zh: '容器'}, value: 'container'},
			{text: {en: 'player inventory', zh: '玩家背包'}, value: 'inventory'},
			{text: {en: 'any screen', zh: '任意界面'}, value: 'any'}
		]},
		keyName: {acceptReporters: true, items: [
			{text: {en: 'a', zh: 'a'}, value: 'a'},
			{text: {en: 'b', zh: 'b'}, value: 'b'},
			{text: {en: 'c', zh: 'c'}, value: 'c'},
			{text: {en: 'd', zh: 'd'}, value: 'd'},
			{text: {en: 'e', zh: 'e'}, value: 'e'},
			{text: {en: 'f', zh: 'f'}, value: 'f'},
			{text: {en: 'g', zh: 'g'}, value: 'g'},
			{text: {en: 'h', zh: 'h'}, value: 'h'},
			{text: {en: 'i', zh: 'i'}, value: 'i'},
			{text: {en: 'j', zh: 'j'}, value: 'j'},
			{text: {en: 'k', zh: 'k'}, value: 'k'},
			{text: {en: 'l', zh: 'l'}, value: 'l'},
			{text: {en: 'm', zh: 'm'}, value: 'm'},
			{text: {en: 'n', zh: 'n'}, value: 'n'},
			{text: {en: 'o', zh: 'o'}, value: 'o'},
			{text: {en: 'p', zh: 'p'}, value: 'p'},
			{text: {en: 'q', zh: 'q'}, value: 'q'},
			{text: {en: 'r', zh: 'r'}, value: 'r'},
			{text: {en: 's', zh: 's'}, value: 's'},
			{text: {en: 't', zh: 't'}, value: 't'},
			{text: {en: 'u', zh: 'u'}, value: 'u'},
			{text: {en: 'v', zh: 'v'}, value: 'v'},
			{text: {en: 'w', zh: 'w'}, value: 'w'},
			{text: {en: 'x', zh: 'x'}, value: 'x'},
			{text: {en: 'y', zh: 'y'}, value: 'y'},
			{text: {en: 'z', zh: 'z'}, value: 'z'},
			{text: {en: '0', zh: '0'}, value: '0'},
			{text: {en: '1', zh: '1'}, value: '1'},
			{text: {en: '2', zh: '2'}, value: '2'},
			{text: {en: '3', zh: '3'}, value: '3'},
			{text: {en: '4', zh: '4'}, value: '4'},
			{text: {en: '5', zh: '5'}, value: '5'},
			{text: {en: '6', zh: '6'}, value: '6'},
			{text: {en: '7', zh: '7'}, value: '7'},
			{text: {en: '8', zh: '8'}, value: '8'},
			{text: {en: '9', zh: '9'}, value: '9'},
			{text: {en: 'F1', zh: 'F1'}, value: 'f1'},
			{text: {en: 'F2', zh: 'F2'}, value: 'f2'},
			{text: {en: 'F3', zh: 'F3'}, value: 'f3'},
			{text: {en: 'F4', zh: 'F4'}, value: 'f4'},
			{text: {en: 'F5', zh: 'F5'}, value: 'f5'},
			{text: {en: 'F6', zh: 'F6'}, value: 'f6'},
			{text: {en: 'F7', zh: 'F7'}, value: 'f7'},
			{text: {en: 'F8', zh: 'F8'}, value: 'f8'},
			{text: {en: 'F9', zh: 'F9'}, value: 'f9'},
			{text: {en: 'F10', zh: 'F10'}, value: 'f10'},
			{text: {en: 'F11', zh: 'F11'}, value: 'f11'},
			{text: {en: 'F12', zh: 'F12'}, value: 'f12'},
			{text: {en: 'Up Arrow', zh: '上'}, value: 'up'},
			{text: {en: 'Down Arrow', zh: '下'}, value: 'down'},
			{text: {en: 'Left Arrow', zh: '左'}, value: 'left'},
			{text: {en: 'Right Arrow', zh: '右'}, value: 'right'},
			{text: {en: 'Left Shift', zh: '左 Shift'}, value: 'left.shift'},
			{text: {en: 'Right Shift', zh: '右 Shift'}, value: 'right.shift'},
			{text: {en: 'Left Control', zh: '左 Ctrl'}, value: 'left.control'},
			{text: {en: 'Right Control', zh: '右 Ctrl'}, value: 'right.control'},
			{text: {en: 'Left Alt', zh: '左 Alt'}, value: 'left.alt'},
			{text: {en: 'Right Alt', zh: '右 Alt'}, value: 'right.alt'},
			{text: {en: 'Left Win', zh: '左 Win'}, value: 'left.win'},
			{text: {en: 'Right Win', zh: '右 Win'}, value: 'right.win'},
			{text: {en: 'Space', zh: '空格'}, value: 'space'},
			{text: {en: 'Enter', zh: '回车'}, value: 'enter'},
			{text: {en: 'Tab', zh: 'Tab'}, value: 'tab'},
			{text: {en: 'Backspace', zh: '退格'}, value: 'backspace'},
			{text: {en: 'Escape', zh: 'Esc'}, value: 'escape'},
			{text: {en: '\'', zh: '\''}, value: 'apostrophe'},
			{text: {en: '\\', zh: '\\'}, value: 'backslash'},
			{text: {en: ',', zh: '逗号'}, value: 'comma'},
			{text: {en: '=', zh: '等号'}, value: 'equal'},
			{text: {en: '`', zh: '`'}, value: 'grave.accent'},
			{text: {en: '[', zh: '['}, value: 'left.bracket'},
			{text: {en: ']', zh: ']'}, value: 'right.bracket'},
			{text: {en: '-', zh: '减号'}, value: 'minus'},
			{text: {en: '.', zh: '句点'}, value: 'period'},
			{text: {en: ';', zh: '分号'}, value: 'semicolon'},
			{text: {en: '/', zh: '斜杠'}, value: 'slash'},
			{text: {en: 'Left Button', zh: '鼠标左键'}, value: 'mouse.left'},
			{text: {en: 'Right Button', zh: '鼠标右键'}, value: 'mouse.right'},
			{text: {en: 'Middle Button', zh: '鼠标中键'}, value: 'mouse.middle'},
			{text: {en: 'Keypad 0', zh: '小键盘 0'}, value: 'keypad.0'},
			{text: {en: 'Keypad 1', zh: '小键盘 1'}, value: 'keypad.1'},
			{text: {en: 'Keypad 2', zh: '小键盘 2'}, value: 'keypad.2'},
			{text: {en: 'Keypad 3', zh: '小键盘 3'}, value: 'keypad.3'},
			{text: {en: 'Keypad 4', zh: '小键盘 4'}, value: 'keypad.4'},
			{text: {en: 'Keypad 5', zh: '小键盘 5'}, value: 'keypad.5'},
			{text: {en: 'Keypad 6', zh: '小键盘 6'}, value: 'keypad.6'},
			{text: {en: 'Keypad 7', zh: '小键盘 7'}, value: 'keypad.7'},
			{text: {en: 'Keypad 8', zh: '小键盘 8'}, value: 'keypad.8'},
			{text: {en: 'Keypad 9', zh: '小键盘 9'}, value: 'keypad.9'},
			{text: {en: 'Keypad +', zh: '小键盘 +'}, value: 'keypad.add'},
			{text: {en: 'Keypad Decimal', zh: '小键盘 .'}, value: 'keypad.decimal'},
			{text: {en: 'Keypad /', zh: '小键盘 /'}, value: 'keypad.divide'},
			{text: {en: 'Keypad Enter', zh: '小键盘回车'}, value: 'keypad.enter'},
			{text: {en: 'Keypad =', zh: '小键盘 ='}, value: 'keypad.equal'},
			{text: {en: 'Keypad *', zh: '小键盘 *'}, value: 'keypad.multiply'},
			{text: {en: 'Keypad -', zh: '小键盘 -'}, value: 'keypad.subtract'},
			{text: {en: 'Caps Lock', zh: 'CapsLock'}, value: 'caps.lock'},
			{text: {en: 'Delete', zh: 'Delete'}, value: 'delete'},
			{text: {en: 'End', zh: 'End'}, value: 'end'},
			{text: {en: 'Home', zh: 'Home'}, value: 'home'},
			{text: {en: 'Insert', zh: 'Insert'}, value: 'insert'},
			{text: {en: 'Menu', zh: '菜单键'}, value: 'menu'},
			{text: {en: 'Num Lock', zh: 'NumLock'}, value: 'num.lock'},
			{text: {en: 'Page Down', zh: 'PageDown'}, value: 'page.down'},
			{text: {en: 'Page Up', zh: 'PageUp'}, value: 'page.up'},
			{text: {en: 'Pause', zh: 'Pause'}, value: 'pause'},
			{text: {en: 'Print Screen', zh: 'PrintScreen'}, value: 'print.screen'},
			{text: {en: 'Scroll Lock', zh: 'ScrollLock'}, value: 'scroll.lock'},
			{text: {en: 'World 1', zh: 'World 1'}, value: 'world.1'},
			{text: {en: 'World 2', zh: 'World 2'}, value: 'world.2'},
			{text: {en: 'F13', zh: 'F13'}, value: 'f13'},
			{text: {en: 'F14', zh: 'F14'}, value: 'f14'},
			{text: {en: 'F15', zh: 'F15'}, value: 'f15'},
			{text: {en: 'F16', zh: 'F16'}, value: 'f16'},
			{text: {en: 'F17', zh: 'F17'}, value: 'f17'},
			{text: {en: 'F18', zh: 'F18'}, value: 'f18'},
			{text: {en: 'F19', zh: 'F19'}, value: 'f19'},
			{text: {en: 'F20', zh: 'F20'}, value: 'f20'},
			{text: {en: 'F21', zh: 'F21'}, value: 'f21'},
			{text: {en: 'F22', zh: 'F22'}, value: 'f22'},
			{text: {en: 'F23', zh: 'F23'}, value: 'f23'},
			{text: {en: 'F24', zh: 'F24'}, value: 'f24'},
			{text: {en: 'F25', zh: 'F25'}, value: 'f25'}
		]},
		mouseButton: {acceptReporters: true, items: [
			{text: {en: 'left', zh: '左键'}, value: 'left'},
			{text: {en: 'right', zh: '右键'}, value: 'right'},
			{text: {en: 'middle', zh: '中键'}, value: 'middle'}
		]},
		slotButton: {acceptReporters: true, items: [
			{text: {en: 'left', zh: '左键'}, value: 'left'},
			{text: {en: 'right', zh: '右键'}, value: 'right'}
		]},
		keyState: {acceptReporters: true, items: [
			{text: {en: 'down', zh: '按下'}, value: 'true'},
			{text: {en: 'up', zh: '松开'}, value: 'false'}
		]}
	}
};
