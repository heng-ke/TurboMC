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
                defaultValue: 'w'
              },
              STATE: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: 'down',
                menu: 'keyState'
              }
            }
          }, {
            opcode: 'mouse',
            blockType: Scratch.BlockType.COMMAND,
            text: T('mouse [BUTTON] [STATE]', '鼠标 [BUTTON] [STATE]'),
            arguments: {
              BUTTON: {
                type: Scratch.ArgumentType.STRING,
                defaultValue: 'left',
                menu: 'mouseButton'
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
                defaultValue: 'w'
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
            opcode: 'placeBlock',
            blockType: Scratch.BlockType.COMMAND,
            text: T('place block at x:[X] y:[Y] z:[Z]', '在坐标放方块 x:[X] y:[Y] z:[Z]'),
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
          }, {
            opcode: 'sleep',
            blockType: Scratch.BlockType.COMMAND,
            text: T('sleep in nearby bed', '睡附近的床')
          },
          // ---- 战斗 ----
          {
            opcode: 'attackNearest',
            blockType: Scratch.BlockType.COMMAND,
            text: T('attack nearest entity', '攻击最近实体')
          }, {
            opcode: 'attackNearestHostile',
            blockType: Scratch.BlockType.COMMAND,
            text: T('attack nearest hostile', '攻击最近敌对生物')
          },
          // ---- 容器交互 ----
          {
            opcode: 'openContainer',
            blockType: Scratch.BlockType.COMMAND,
            text: T('open nearest container', '打开最近容器')
          }, {
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
      key: "mouse",
      value: function mouse(args) {
        this.preview('mouse ' + args.BUTTON + ' ' + args.STATE);
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
      key: "placeBlock",
      value: function placeBlock(args) {
        this.preview('placeBlock ' + args.X + ',' + args.Y + ',' + args.Z);
      }
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
    }, {
      key: "sleep",
      value: function sleep() {
        this.preview('sleep');
      }

      // ---- 战斗 ----
    }, {
      key: "attackNearest",
      value: function attackNearest() {
        this.preview('attackNearest');
      }
    }, {
      key: "attackNearestHostile",
      value: function attackNearestHostile() {
        this.preview('attackNearestHostile');
      }

      // ---- 容器交互 ----
    }, {
      key: "openContainer",
      value: function openContainer() {
        this.preview('openContainer');
      }
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
