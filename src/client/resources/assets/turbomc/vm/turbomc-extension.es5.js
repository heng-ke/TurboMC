function _typeof(o) { "@babel/helpers - typeof"; return _typeof = "function" == typeof Symbol && "symbol" == typeof Symbol.iterator ? function (o) { return typeof o; } : function (o) { return o && "function" == typeof Symbol && o.constructor === Symbol && o !== Symbol.prototype ? "symbol" : typeof o; }, _typeof(o); }
function _classCallCheck(a, n) { if (!(a instanceof n)) throw new TypeError("Cannot call a class as a function"); }
function _defineProperties(r, t) { for (var o = 0; o < t.length; o++) { var i = t[o]; i.enumerable = i.enumerable || !1, i.configurable = !0, "value" in i && (i.writable = !0), Object.defineProperty(r, _toPropertyKey(i.key), i); } }
function _createClass(e, r, t) { return r && _defineProperties(e.prototype, r), t && _defineProperties(e, t), Object.defineProperty(e, "prototype", { writable: !1 }), e; }
function _toPropertyKey(r) { var o = _toPrimitive(r, "string"); return "symbol" == _typeof(o) ? o : o + ""; }
function _toPrimitive(t, e) { if ("object" != _typeof(t) || !t) return t; var r; if ("undefined" != typeof Symbol && void 0 !== (r = t[Symbol.toPrimitive])) { var i = r.call(t, e || "default"); if ("object" != _typeof(i)) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === e ? String : Number)(t); }
(function (Scratch) {
  'use strict';

  /**
   * turbomc 的 TurboWarp 扩展（中英双语）。
   *
   * 作用：在 TurboWarp 积木面板里提供 Minecraft 专用积木，导出 .sb3 后由模组解析执行。
   * 浏览器里没有 Minecraft，所有动作都是空实现，只打控制台日志。
   *
   * 注意：opcode 在 project.json 中呈现为 turbomc_<opcode>，模组按该规则映射，改名会导致导入失败。
   * 菜单项的 value 必须是英文（写入 sb3），显示文本才做本地化。
   */
  var lang = 'en';
  try {
    lang = (navigator.language || 'en').toLowerCase();
  } catch (e) {
    lang = 'en';
  }
  var ZH = lang.indexOf('zh') === 0;

  /** 中英切换 */
  var T = (en, zh) => ZH ? zh : en;
  var TurboMC = /*#__PURE__*/function () {
    function TurboMC() {
      _classCallCheck(this, TurboMC);
    }
    return _createClass(TurboMC, [{
      key: "getInfo",
      value: function getInfo() {
        return {
          // 扩展 id 决定 opcode 前缀：注册为 turbomc，积木在 project.json 里就是
          // turbomc_<opcode>。改这里等于让所有已导出的工程失效。
          id: 'turbomc',
          name: 'TurboMC',
          color1: '#6F45B0',
          color2: '#5A3894',
          blocks: [
          // ---- 事件 ----
          {
            opcode: 'whenDamaged',
            blockType: Scratch.BlockType.HAT,
            // 由宿主事件显式触发，不能让 VM 每帧自动拉起
            isEdgeActivated: false,
            text: T('when damaged', '当受伤时')
          }, {
            opcode: 'whenDeath',
            blockType: Scratch.BlockType.HAT,
            // 由宿主事件显式触发，不能让 VM 每帧自动拉起
            isEdgeActivated: false,
            text: T('when player dies', '当玩家死亡')
          }, {
            opcode: 'whenChat',
            blockType: Scratch.BlockType.HAT,
            // 由宿主事件显式触发，不能让 VM 每帧自动拉起
            isEdgeActivated: false,
            text: T('when chat contains [MATCH]', '当聊天包含 [MATCH]'),
            arguments: {
              MATCH: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: ''
              }
            }
          },
          // ---- 输入 ----
          {
            opcode: 'key',
            blockType: Scratch.BlockType.COMMAND,
            text: T('key [KEY] [STATE]', '按键 [KEY] [STATE]'),
            arguments: {
              KEY: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: 'w',
                menu: 'keyName'
              },
              STATE: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: 'down',
                menu: 'keyState'
              }
            }
          }, {
            opcode: 'click',
            blockType: Scratch.BlockType.COMMAND,
            text: T('click [BUTTON]', '点击 [BUTTON]'),
            arguments: {
              BUTTON: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: 'left',
                menu: 'mouseButton'
              }
            }
          }, {
            opcode: 'tap',
            blockType: Scratch.BlockType.COMMAND,
            text: T('tap [KEY] for [HOLD] ticks', '点按 [KEY] 按住 [HOLD] tick'),
            arguments: {
              KEY: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: 'w',
                menu: 'keyName'
              },
              HOLD: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 1
              }
            }
          }, {
            opcode: 'stop',
            blockType: Scratch.BlockType.COMMAND,
            text: T('stop moving', '停止移动')
          },
          // ---- 移动与视角 ----
          {
            opcode: 'moveToward',
            blockType: Scratch.BlockType.COMMAND,
            text: T('move toward x:[X] z:[Z]', '走向 x:[X] z:[Z]'),
            arguments: {
              X: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              },
              Z: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              }
            }
          }, {
            opcode: 'look',
            blockType: Scratch.BlockType.COMMAND,
            text: T('set view yaw:[YAW] pitch:[PITCH]', '设置视角 水平:[YAW] 俯仰:[PITCH]'),
            arguments: {
              YAW: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              },
              PITCH: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              }
            }
          }, {
            opcode: 'lookAt',
            blockType: Scratch.BlockType.COMMAND,
            text: T('look at x:[X] y:[Y] z:[Z]', '看向 x:[X] y:[Y] z:[Z]'),
            arguments: {
              X: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              },
              Y: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 64
              },
              Z: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              }
            }
          },
          // ---- 方块与挖掘 ----
          {
            opcode: 'breakWait',
            blockType: Scratch.BlockType.COMMAND,
            text: T('wait until block broken', '等待方块被挖破')
          }, {
            opcode: 'isMining',
            blockType: Scratch.BlockType.BOOLEAN,
            text: T('is mining', '正在挖掘')
          }, {
            opcode: 'miningProgress',
            blockType: Scratch.BlockType.REPORTER,
            text: T('mining progress', '挖掘进度')
          },
          // ---- 容器交互 ----
          {
            opcode: 'openContainerAt',
            blockType: Scratch.BlockType.COMMAND,
            text: T('open container at x:[X] y:[Y] z:[Z]', '打开坐标处的容器 x:[X] y:[Y] z:[Z]'),
            arguments: {
              X: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              },
              Y: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 64
              },
              Z: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              }
            }
          }, {
            opcode: 'containerSlots',
            blockType: Scratch.BlockType.REPORTER,
            text: T('container slots', '界面槽位数')
          }, {
            opcode: 'slotItem',
            blockType: Scratch.BlockType.REPORTER,
            text: T('item in slot [INDEX]', '槽位 [INDEX] 的物品'),
            arguments: {
              INDEX: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              }
            }
          }, {
            opcode: 'slotAmount',
            blockType: Scratch.BlockType.REPORTER,
            text: T('amount in slot [INDEX]', '槽位 [INDEX] 的数量'),
            arguments: {
              INDEX: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              }
            }
          }, {
            opcode: 'firstSlotOf',
            blockType: Scratch.BlockType.REPORTER,
            text: T('first slot with [ITEM]', '第一个装着 [ITEM] 的槽位'),
            arguments: {
              ITEM: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: 'minecraft:iron_ore'
              }
            }
          }, {
            opcode: 'clickSlot',
            blockType: Scratch.BlockType.COMMAND,
            text: T('click slot [INDEX] with [BUTTON]', '点击槽位 [INDEX] 用 [BUTTON]'),
            arguments: {
              INDEX: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              },
              BUTTON: {
                type: Scratch.ArgumentType.STRING,
                menu: 'slotButton',
                defaultValue: 'left'
              }
            }
          }, {
            opcode: 'quickMoveSlot',
            blockType: Scratch.BlockType.COMMAND,
            text: T('quick move slot [INDEX]', '快速移动槽位 [INDEX]'),
            arguments: {
              INDEX: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              }
            }
          }, {
            opcode: 'closeContainer',
            blockType: Scratch.BlockType.COMMAND,
            text: T('close container', '关闭界面')
          },
          // ---- 交互与通讯 ----
          {
            opcode: 'chat',
            blockType: Scratch.BlockType.COMMAND,
            text: T('say [TEXT] in chat', '在聊天栏说 [TEXT]'),
            arguments: {
              TEXT: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: 'hello'
              }
            }
          }, {
            opcode: 'command',
            blockType: Scratch.BlockType.COMMAND,
            text: T('run command [COMMAND]', '执行命令 [COMMAND]'),
            arguments: {
              COMMAND: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: 'time set day'
              }
            }
          }, {
            opcode: 'respawn',
            blockType: Scratch.BlockType.COMMAND,
            text: T('respawn', '重生')
          },
          // ---- 查询 · 玩家 ----
          {
            opcode: 'health',
            blockType: Scratch.BlockType.REPORTER,
            text: T('health', '血量')
          }, {
            opcode: 'food',
            blockType: Scratch.BlockType.REPORTER,
            text: T('food level', '饱食度')
          }, {
            opcode: 'xpLevel',
            blockType: Scratch.BlockType.REPORTER,
            text: T('xp level', '经验等级')
          }, {
            opcode: 'inventoryFull',
            blockType: Scratch.BlockType.BOOLEAN,
            text: T('inventory full?', '背包已满?')
          }, {
            opcode: 'onGround',
            blockType: Scratch.BlockType.BOOLEAN,
            text: T('on ground?', '站在地面？')
          }, {
            opcode: 'posX',
            blockType: Scratch.BlockType.REPORTER,
            text: T('x position', 'X 坐标')
          }, {
            opcode: 'posY',
            blockType: Scratch.BlockType.REPORTER,
            text: T('y position', 'Y 坐标')
          }, {
            opcode: 'posZ',
            blockType: Scratch.BlockType.REPORTER,
            text: T('z position', 'Z 坐标')
          }, {
            opcode: 'selectedSlot',
            blockType: Scratch.BlockType.REPORTER,
            text: T('selected slot', '当前槽位')
          }, {
            opcode: 'heldItem',
            blockType: Scratch.BlockType.REPORTER,
            text: T('held item', '手持物品')
          }, {
            opcode: 'heldDurability',
            blockType: Scratch.BlockType.REPORTER,
            text: T('held durability %', '手持耐久百分比')
          },
          // ---- 查询 · 世界 ----
          {
            opcode: 'isDay',
            blockType: Scratch.BlockType.BOOLEAN,
            text: T('is daytime?', '是白天?')
          }, {
            opcode: 'dimension',
            blockType: Scratch.BlockType.REPORTER,
            text: T('dimension', '当前维度')
          }, {
            opcode: 'biome',
            blockType: Scratch.BlockType.REPORTER,
            text: T('biome', '当前生物群系')
          }, {
            opcode: 'inWater',
            blockType: Scratch.BlockType.BOOLEAN,
            text: T('in water?', '在水中?')
          }, {
            opcode: 'inLava',
            blockType: Scratch.BlockType.BOOLEAN,
            text: T('in lava?', '在岩浆中?')
          }, {
            opcode: 'blockInFront',
            blockType: Scratch.BlockType.BOOLEAN,
            text: T('block in front?', '前方有方块？')
          }, {
            opcode: 'blockBelow',
            blockType: Scratch.BlockType.REPORTER,
            text: T('block below', '脚下方块')
          }, {
            opcode: 'blockAt',
            blockType: Scratch.BlockType.REPORTER,
            text: T('block at x:[X] y:[Y] z:[Z]', '坐标处方块 x:[X] y:[Y] z:[Z]'),
            arguments: {
              X: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              },
              Y: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 64
              },
              Z: {
                type: Scratch.ArgumentType.NUMBER,
                defaultValue: 0
              }
            }
          }, {
            opcode: 'frontBlock',
            blockType: Scratch.BlockType.REPORTER,
            text: T('block at crosshair', '准星所指方块')
          },
          // ---- 查询 · 实体 ----
          {
            opcode: 'nearestEntity',
            blockType: Scratch.BlockType.REPORTER,
            text: T('nearest entity distance', '最近实体距离')
          }, {
            opcode: 'nearestEntityX',
            blockType: Scratch.BlockType.REPORTER,
            text: T('nearest entity x', '最近实体 X')
          }, {
            opcode: 'nearestEntityY',
            blockType: Scratch.BlockType.REPORTER,
            text: T('nearest entity y', '最近实体 Y')
          }, {
            opcode: 'nearestEntityZ',
            blockType: Scratch.BlockType.REPORTER,
            text: T('nearest entity z', '最近实体 Z')
          }, {
            opcode: 'nearestEntityName',
            blockType: Scratch.BlockType.REPORTER,
            text: T('nearest entity name', '最近实体名称')
          }, {
            opcode: 'nearestHostile',
            blockType: Scratch.BlockType.REPORTER,
            text: T('nearest hostile distance', '最近敌对生物距离')
          }, {
            opcode: 'entityCount',
            blockType: Scratch.BlockType.REPORTER,
            text: T('count of [TYPE] nearby', '附近 [TYPE] 数量'),
            arguments: {
              TYPE: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: 'minecraft:zombie'
              }
            }
          },
          // ---- 运算与工具 ----
          {
            opcode: 'itemCount',
            blockType: Scratch.BlockType.REPORTER,
            text: T('count of [ITEM]', '背包中 [ITEM] 的数量'),
            arguments: {
              ITEM: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: 'minecraft:stone'
              }
            }
          }, {
            opcode: 'ping',
            blockType: Scratch.BlockType.REPORTER,
            text: T('server ping (ms)', '服务端延迟(ms)')
          }, {
            opcode: 'log',
            blockType: Scratch.BlockType.COMMAND,
            text: T('log [TEXT]', '输出日志 [TEXT]'),
            arguments: {
              TEXT: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: 'hello'
              }
            }
          }],
          menus: {
            keyName: {
              acceptReporters: true,
              items: [{
                text: 'a',
                value: 'a'
              }, {
                text: 'b',
                value: 'b'
              }, {
                text: 'c',
                value: 'c'
              }, {
                text: 'd',
                value: 'd'
              }, {
                text: 'e',
                value: 'e'
              }, {
                text: 'f',
                value: 'f'
              }, {
                text: 'g',
                value: 'g'
              }, {
                text: 'h',
                value: 'h'
              }, {
                text: 'i',
                value: 'i'
              }, {
                text: 'j',
                value: 'j'
              }, {
                text: 'k',
                value: 'k'
              }, {
                text: 'l',
                value: 'l'
              }, {
                text: 'm',
                value: 'm'
              }, {
                text: 'n',
                value: 'n'
              }, {
                text: 'o',
                value: 'o'
              }, {
                text: 'p',
                value: 'p'
              }, {
                text: 'q',
                value: 'q'
              }, {
                text: 'r',
                value: 'r'
              }, {
                text: 's',
                value: 's'
              }, {
                text: 't',
                value: 't'
              }, {
                text: 'u',
                value: 'u'
              }, {
                text: 'v',
                value: 'v'
              }, {
                text: 'w',
                value: 'w'
              }, {
                text: 'x',
                value: 'x'
              }, {
                text: 'y',
                value: 'y'
              }, {
                text: 'z',
                value: 'z'
              }, {
                text: '0',
                value: '0'
              }, {
                text: '1',
                value: '1'
              }, {
                text: '2',
                value: '2'
              }, {
                text: '3',
                value: '3'
              }, {
                text: '4',
                value: '4'
              }, {
                text: '5',
                value: '5'
              }, {
                text: '6',
                value: '6'
              }, {
                text: '7',
                value: '7'
              }, {
                text: '8',
                value: '8'
              }, {
                text: '9',
                value: '9'
              }, {
                text: 'F1',
                value: 'f1'
              }, {
                text: 'F2',
                value: 'f2'
              }, {
                text: 'F3',
                value: 'f3'
              }, {
                text: 'F4',
                value: 'f4'
              }, {
                text: 'F5',
                value: 'f5'
              }, {
                text: 'F6',
                value: 'f6'
              }, {
                text: 'F7',
                value: 'f7'
              }, {
                text: 'F8',
                value: 'f8'
              }, {
                text: 'F9',
                value: 'f9'
              }, {
                text: 'F10',
                value: 'f10'
              }, {
                text: 'F11',
                value: 'f11'
              }, {
                text: 'F12',
                value: 'f12'
              }, {
                text: T('Up Arrow', '上'),
                value: 'up'
              }, {
                text: T('Down Arrow', '下'),
                value: 'down'
              }, {
                text: T('Left Arrow', '左'),
                value: 'left'
              }, {
                text: T('Right Arrow', '右'),
                value: 'right'
              }, {
                text: T('Left Shift', '左 Shift'),
                value: 'left.shift'
              }, {
                text: T('Right Shift', '右 Shift'),
                value: 'right.shift'
              }, {
                text: T('Left Control', '左 Ctrl'),
                value: 'left.control'
              }, {
                text: T('Right Control', '右 Ctrl'),
                value: 'right.control'
              }, {
                text: T('Left Alt', '左 Alt'),
                value: 'left.alt'
              }, {
                text: T('Right Alt', '右 Alt'),
                value: 'right.alt'
              }, {
                text: T('Left Win', '左 Win'),
                value: 'left.win'
              }, {
                text: T('Right Win', '右 Win'),
                value: 'right.win'
              }, {
                text: T('Space', '空格'),
                value: 'space'
              }, {
                text: T('Enter', '回车'),
                value: 'enter'
              }, {
                text: 'Tab',
                value: 'tab'
              }, {
                text: T('Backspace', '退格'),
                value: 'backspace'
              }, {
                text: T('Escape', 'Esc'),
                value: 'escape'
              }, {
                text: '\'',
                value: 'apostrophe'
              }, {
                text: '\\',
                value: 'backslash'
              }, {
                text: T(',', '逗号'),
                value: 'comma'
              }, {
                text: T('=', '等号'),
                value: 'equal'
              }, {
                text: '`',
                value: 'grave.accent'
              }, {
                text: '[',
                value: 'left.bracket'
              }, {
                text: ']',
                value: 'right.bracket'
              }, {
                text: T('-', '减号'),
                value: 'minus'
              }, {
                text: T('.', '句点'),
                value: 'period'
              }, {
                text: T(';', '分号'),
                value: 'semicolon'
              }, {
                text: T('/', '斜杠'),
                value: 'slash'
              }, {
                text: T('Left Button', '鼠标左键'),
                value: 'mouse.left'
              }, {
                text: T('Right Button', '鼠标右键'),
                value: 'mouse.right'
              }, {
                text: T('Middle Button', '鼠标中键'),
                value: 'mouse.middle'
              }, {
                text: T('Keypad 0', '小键盘 0'),
                value: 'keypad.0'
              }, {
                text: T('Keypad 1', '小键盘 1'),
                value: 'keypad.1'
              }, {
                text: T('Keypad 2', '小键盘 2'),
                value: 'keypad.2'
              }, {
                text: T('Keypad 3', '小键盘 3'),
                value: 'keypad.3'
              }, {
                text: T('Keypad 4', '小键盘 4'),
                value: 'keypad.4'
              }, {
                text: T('Keypad 5', '小键盘 5'),
                value: 'keypad.5'
              }, {
                text: T('Keypad 6', '小键盘 6'),
                value: 'keypad.6'
              }, {
                text: T('Keypad 7', '小键盘 7'),
                value: 'keypad.7'
              }, {
                text: T('Keypad 8', '小键盘 8'),
                value: 'keypad.8'
              }, {
                text: T('Keypad 9', '小键盘 9'),
                value: 'keypad.9'
              }, {
                text: T('Keypad +', '小键盘 +'),
                value: 'keypad.add'
              }, {
                text: T('Keypad Decimal', '小键盘 .'),
                value: 'keypad.decimal'
              }, {
                text: T('Keypad /', '小键盘 /'),
                value: 'keypad.divide'
              }, {
                text: T('Keypad Enter', '小键盘回车'),
                value: 'keypad.enter'
              }, {
                text: T('Keypad =', '小键盘 ='),
                value: 'keypad.equal'
              }, {
                text: T('Keypad *', '小键盘 *'),
                value: 'keypad.multiply'
              }, {
                text: T('Keypad -', '小键盘 -'),
                value: 'keypad.subtract'
              }, {
                text: T('Caps Lock', 'CapsLock'),
                value: 'caps.lock'
              }, {
                text: 'Delete',
                value: 'delete'
              }, {
                text: 'End',
                value: 'end'
              }, {
                text: 'Home',
                value: 'home'
              }, {
                text: 'Insert',
                value: 'insert'
              }, {
                text: T('Menu', '菜单键'),
                value: 'menu'
              }, {
                text: T('Num Lock', 'NumLock'),
                value: 'num.lock'
              }, {
                text: T('Page Down', 'PageDown'),
                value: 'page.down'
              }, {
                text: T('Page Up', 'PageUp'),
                value: 'page.up'
              }, {
                text: 'Pause',
                value: 'pause'
              }, {
                text: T('Print Screen', 'PrintScreen'),
                value: 'print.screen'
              }, {
                text: T('Scroll Lock', 'ScrollLock'),
                value: 'scroll.lock'
              }, {
                text: 'World 1',
                value: 'world.1'
              }, {
                text: 'World 2',
                value: 'world.2'
              }, {
                text: 'F13',
                value: 'f13'
              }, {
                text: 'F14',
                value: 'f14'
              }, {
                text: 'F15',
                value: 'f15'
              }, {
                text: 'F16',
                value: 'f16'
              }, {
                text: 'F17',
                value: 'f17'
              }, {
                text: 'F18',
                value: 'f18'
              }, {
                text: 'F19',
                value: 'f19'
              }, {
                text: 'F20',
                value: 'f20'
              }, {
                text: 'F21',
                value: 'f21'
              }, {
                text: 'F22',
                value: 'f22'
              }, {
                text: 'F23',
                value: 'f23'
              }, {
                text: 'F24',
                value: 'f24'
              }, {
                text: 'F25',
                value: 'f25'
              }]
            },
            mouseButton: {
              acceptReporters: true,
              items: [{
                text: T('left', '左键'),
                value: 'left'
              }, {
                text: T('right', '右键'),
                value: 'right'
              }, {
                text: T('middle', '中键'),
                value: 'middle'
              }]
            },
            slotButton: {
              acceptReporters: true,
              items: [{
                text: T('left', '左键'),
                value: 'left'
              }, {
                text: T('right', '右键'),
                value: 'right'
              }]
            },
            keyState: {
              acceptReporters: true,
              items: [{
                text: T('down', '按下'),
                value: 'true'
              }, {
                text: T('up', '松开'),
                value: 'false'
              }]
            }
          }
        };
      }

      // ---- 输入 ----
    }, {
      key: "key",
      value: function key(args) {
        this.preview('key ' + args.KEY + ' ' + args.STATE);
      }
    }, {
      key: "click",
      value: function click(args) {
        this.preview('click ' + args.BUTTON);
      }
    }, {
      key: "tap",
      value: function tap(args) {
        this.preview('tap ' + args.KEY + ' x' + args.HOLD);
      }
    }, {
      key: "stop",
      value: function stop() {
        this.preview('stop');
      }

      // ---- 移动与视角 ----
    }, {
      key: "moveToward",
      value: function moveToward(args) {
        this.preview('moveToward ' + args.X + ',' + args.Z);
      }
    }, {
      key: "look",
      value: function look(args) {
        this.preview('look ' + args.YAW + ',' + args.PITCH);
      }
    }, {
      key: "lookAt",
      value: function lookAt(args) {
        this.preview('lookAt ' + args.X + ',' + args.Y + ',' + args.Z);
      }

      // ---- 方块与挖掘 ----
    }, {
      key: "breakWait",
      value: function breakWait() {
        this.preview('breakWait');
      }
    }, {
      key: "isMining",
      value: function isMining() {
        return false;
      }
    }, {
      key: "miningProgress",
      value: function miningProgress() {
        return 0;
      }

      // ---- 容器交互 ----
    }, {
      key: "openContainerAt",
      value: function openContainerAt(args) {
        this.preview('openContainerAt');
      }
    }, {
      key: "containerSlots",
      value: function containerSlots() {
        return 0;
      }
    }, {
      key: "slotItem",
      value: function slotItem(args) {
        return '';
      }
    }, {
      key: "slotAmount",
      value: function slotAmount(args) {
        return 0;
      }
    }, {
      key: "firstSlotOf",
      value: function firstSlotOf(args) {
        return -1;
      }
    }, {
      key: "clickSlot",
      value: function clickSlot(args) {
        this.preview('clickSlot');
      }
    }, {
      key: "quickMoveSlot",
      value: function quickMoveSlot(args) {
        this.preview('quickMoveSlot');
      }
    }, {
      key: "closeContainer",
      value: function closeContainer() {
        this.preview('closeContainer');
      }

      // ---- 交互与通讯 ----
    }, {
      key: "chat",
      value: function chat(args) {
        this.preview('chat ' + args.TEXT);
      }
    }, {
      key: "command",
      value: function command(args) {
        this.preview('command ' + args.COMMAND);
      }
    }, {
      key: "respawn",
      value: function respawn() {
        this.preview('respawn');
      }

      // ---- 查询 · 玩家 ----
    }, {
      key: "health",
      value: function health() {
        return 20;
      }
    }, {
      key: "food",
      value: function food() {
        return 20;
      }
    }, {
      key: "xpLevel",
      value: function xpLevel() {
        return 0;
      }
    }, {
      key: "inventoryFull",
      value: function inventoryFull() {
        return false;
      }
    }, {
      key: "onGround",
      value: function onGround() {
        this.preview('onGround');
        return true;
      }
    }, {
      key: "posX",
      value: function posX() {
        return 0;
      }
    }, {
      key: "posY",
      value: function posY() {
        return 64;
      }
    }, {
      key: "posZ",
      value: function posZ() {
        return 0;
      }
    }, {
      key: "selectedSlot",
      value: function selectedSlot() {
        return 1;
      }
    }, {
      key: "heldItem",
      value: function heldItem() {
        return 'minecraft:air';
      }
    }, {
      key: "heldDurability",
      value: function heldDurability() {
        return 100;
      }

      // ---- 查询 · 世界 ----
    }, {
      key: "isDay",
      value: function isDay() {
        return true;
      }
    }, {
      key: "dimension",
      value: function dimension() {
        return 'minecraft:overworld';
      }
    }, {
      key: "biome",
      value: function biome() {
        return 'minecraft:plains';
      }
    }, {
      key: "inWater",
      value: function inWater() {
        return false;
      }
    }, {
      key: "inLava",
      value: function inLava() {
        return false;
      }
    }, {
      key: "blockInFront",
      value: function blockInFront() {
        this.preview('blockInFront');
        return false;
      }
    }, {
      key: "blockBelow",
      value: function blockBelow() {
        return 'minecraft:grass_block';
      }
    }, {
      key: "blockAt",
      value: function blockAt(args) {
        this.preview('blockAt ' + args.X + ',' + args.Y + ',' + args.Z);
        return 'minecraft:air';
      }
    }, {
      key: "frontBlock",
      value: function frontBlock() {
        return 'minecraft:air';
      }

      // ---- 查询 · 实体 ----
    }, {
      key: "nearestEntity",
      value: function nearestEntity() {
        return -1;
      }
    }, {
      key: "nearestEntityX",
      value: function nearestEntityX() {
        return 0;
      }
    }, {
      key: "nearestEntityY",
      value: function nearestEntityY() {
        return 64;
      }
    }, {
      key: "nearestEntityZ",
      value: function nearestEntityZ() {
        return 0;
      }
    }, {
      key: "nearestEntityName",
      value: function nearestEntityName() {
        return '';
      }
    }, {
      key: "nearestHostile",
      value: function nearestHostile() {
        return -1;
      }
    }, {
      key: "entityCount",
      value: function entityCount() {
        return 0;
      }

      // ---- 运算与工具 ----
    }, {
      key: "itemCount",
      value: function itemCount() {
        return 0;
      }
    }, {
      key: "ping",
      value: function ping() {
        return 0;
      }
    }, {
      key: "log",
      value: function log(args) {
        console.log('[turbomc]', args.TEXT);
      }
    }, {
      key: "preview",
      value: function preview(name) {
        console.log('[turbomc] 预览模式（浏览器内无实际效果）:', name);
      }
    }]);
  }();
  Scratch.extensions.register(new TurboMC());
})(Scratch);
