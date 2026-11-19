# 架构说明

> 本文是**当前实现的唯一事实来源**。改代码时请同步改这里——落后于实现的架构文档
> 比没有更糟，后来人会照着它去理解不存在的结构。

## 一、这个模组是什么

Fabric 1.21.11 客户端模组：用 Scratch 积木描述自动化行为，在 Minecraft 里执行。

**核心设计决策**：脚本的编写与修改完全交给 TurboWarp，游戏内只负责「加载 sb3 并运行」。

这条决策决定了后面所有结构。早期版本曾在游戏内实现过一套可写编辑器（积木树、参数控件、
自定义积木管理，约 6000 行），后来整体移除——TurboWarp 的编辑体验远强于自建，而维护
两套积木定义（扩展一份、界面一份）的成本是持续的。

## 二、数据流

```
TurboWarp  ──导出──▶  .sb3  ──投放──▶  config/turbomc/imports/
                                              │
                                        F7 点「导入」
                                              │
                                              ▼
                                   config/turbomc/packages/
                                              │
                     ┌────────────────────────┴────────────────────────┐
                     ▼                                                 ▼
           project.json 原样交给 VM                            Sb3Importer 解析成积木树
              （运行路径，零中间格式）                            （查看路径，只读）
                     │                                                 │
                     ▼                                                 ▼
             Rhino + scratch-vm                                  只读脚本树界面
                     │
                     ▼
             ScriptBridge（JS → Java）
                     │
                     ▼
              动作层 action/  ──▶  Minecraft
                     │
                     ▼
              ScriptHudElement（状态 + 「说 / 思考」气泡）
```

**运行路径没有任何序列化。** 启用的包就是 sb3 里那份 project.json，直接
`loadProject`。这样「看到什么」和「跑什么」永远一致——早期版本用中间格式往返，
曾出现 `wait 20` 被当成 20 秒、`chat` 的消息被静默清空这类只有真机才暴露的问题。

## 三、包级开关是互斥的

同时只运行一个 sb3。点另一个包会切换过去，不会合并。

原因：scratch-vm 只有「载入一个工程」这一个入口。要让多个包并存，必须合并它们的
`targets`，而**舞台（Stage）只能有一个**，合并时其余包的全局变量要并进主 Stage，
变量 ID 冲突会让另一包的全局变量静默读不到。这个风险与收益不成比例，所以第一版
选择互斥。

将来若要做并存：增量只需新增一个合并器，现有结构不用改。

## 四、代码结构

```
src/client/java/xyz/turbomc/client/
├── TurboMCClient.java          入口：按键、事件注册、tick 驱动
├── mixin/
│   └── KeyBindingMixin.java    脚本按下的键在读取端接管（原版每 tick 刷新会覆盖 setPressed）
├── config/
│   ├── TurboMCConfig.java      配置数据与持久化
│   ├── TurboMCConfigScreen.java 模组菜单（需 Cloth Config）
│   ├── PathEffort.java         「走向」的 A* 搜索强度档位（轻量 / 标准 / 重量）
│   └── HudCorner.java           HUD 旧版停靠角落（仅用于旧配置回退）
└── script/
    ├── ScriptEngine.java        门面：取启用包 → 载入 → 绿旗 → 事件转发
    ├── action/                  动作层（Java 侧真正操作游戏的地方）
    │   ├── GameActions.java     移动、挖掘、放置、聊天、命令、容器……
    │   ├── ContainerActions.java 容器：开界面、点槽位、搬东西、读槽位内容
    │   ├── WorldQueries.java    世界查询（方块、实体、时间……）
    │   ├── ScriptKeys.java      按键按住 / 释放
    │   ├── InputKeys.java       Scratch 键名 ↔ Minecraft 按键
    │   ├── NativeRegistry.java  opcode → 动作的注册表
    │   ├── JsonActions.java     JSON 积木的动作层（29 块，纯计算，不碰游戏状态）
    │   ├── JsonText.java        严格 JSON 解析 + ECMAScript 兼容序列化（不借 Gson 的解析器）
    │   ├── ChatText.java        聊天清洗：换行 / § / 控制字符，256 字符与 768 字节双上限
    │   ├── ChatQueue.java       聊天发送队列：超长分段、按 tick 拉开间隔
    │   ├── path/                「走向」的移动会话（见 PATHFINDING.md）
    │   │   ├── PathActions.java    解析 x/y/z，启动会话
    │   │   ├── PathFollower.java   每 tick 驱动：转向 / 前进 / 跳跃 / 卡住
    │   │   ├── PathPlanner.java    假僵尸 + 原版 A*（LandPathNodeMaker + PathNodeNavigator）
    │   │   └── MoveTarget.java     目标坐标（三轴全部来自积木）
    │   └── motion/              朝向平滑（寻路与「平滑看向」积木共用）
    │       ├── TurnMath.java       比例收敛步进：gain / 限幅 / 死区
    │       ├── TurnSession.java    转向会话：目标 yaw/pitch、超时、中止
    │       └── TurnActions.java    注册两个平滑积木
    ├── runtime/                 宿主上下文
    │   ├── ScriptContext.java   每 tick 重建的宿主环境（含等待请求通道）
    │   ├── TriggerDispatcher.java 按键 / 受伤 / 死亡事件源
    │   ├── ChatState.java       最近一条聊天原文（供「最近的聊天」积木读）
    │   └── Values / ScratchCast  值转换（scratch-vm 已自带运算与比较，Java 不重复实现）
    ├── ai/                      大模型对话
    │   ├── AiService.java       HTTP（HttpClient 异步）、会话表、历史裁剪、并发与取消
    │   └── AiActions.java       四个积木的动作：ask / reply / reset / system
    ├── vm/                      JS 运行时
    │   ├── RhinoVm.java         Rhino Context 生命周期、时间预算、单帧中断
    │   ├── ScriptBridge.java    JS → Java 的唯一桥
    │   ├── VmListActions.java   「把列表读成数组 / 设为」——需要 VM 内部状态，故注册在 RhinoVm.init()
    │   ├── JsonBench.java       JSON 放 Java 还是 VM 的耗时基准（仅 verification 任务调用）
    │   └── *SmokeTest.java      纯 JVM 冒烟（不发布）
    ├── view/
    │   └── ViewSmoother.java    视角平滑：写入必须在渲染帧（tick 内写 50 ms 才一步，看得见台阶）
    ├── render/                  世界渲染出口
    │   ├── PathRenderer.java    「走向」的路线：折线（已走金黄 / 未走青色）+ 当前节点框 + 终点框
    │   ├── FrameOverlay.java    「画框」：最多 128 个线框，按帧从不可变快照重画
    │   └── FrameBox.java        框的纯数据（坐标归一化 + 颜色解析，不依赖 MC，可纯 JVM 测试）
    ├── sb3/
    │   ├── Sb3Mapping.java      opcode ↔ 内部类型、字段/输入改名（全项目唯一一份）
    │   └── Sb3Importer.java     sb3 → 积木树（只读查看用）
    ├── store/
    │   ├── PackStore.java       包管理：导入、互斥启用、改名、删除、解析缓存
    │   ├── ScriptPack.java      一个包的视图
    │   └── ScriptDefinition.java 解析出的一份脚本（视图模型，不持久化）
    ├── block/                   积木定义（只服务于「渲染成一行文本」）
    ├── node/                    积木树结构
    └── ui/                      界面
        ├── ScriptListScreen.java   F7：包列表（按行滚动，可开 imports 目录）+ 展开脚本名
        ├── ScriptEditScreen.java   只读脚本树
        ├── ScriptHudElement.java   HUD（状态行 + 气泡 + 监视器 + AI）
        ├── RuntimePanelScreen.java 运行状态（可滚动、可重新载入复位故障）
        └── NodeChain / ParamLabels / WaitUnit / TextInputScreen
```

`src/client/resources/assets/turbomc/vm/` 存放内嵌的 JS 运行时：
`vm.es5.js`（scratch-vm）、`turbomc-extension.es5.js`（扩展）、`vm-bootstrap.js`
（宿主与 VM 的粘合层）、若干 shim。

扩展与映射表的**源**不在上面这棵树里，它们是生成物的输入：

```
turbowarp/src/
├── blocks.data.js             96 块积木、12 个菜单、15 个分组标题、每块的占位方法体
└── items.data.js              1505 条物品（注册名|中文名|英文名）

tools/
├── build-extension.js         渲染两份扩展副本（--check 供 CI 守新鲜度）
├── build-mapping-report.js    渲染 turbowarp/mapping-report.md（同上）
└── compare-getinfo.js         迁移期的一次性语义闸门（保留作回归用）
```

改积木只改 `blocks.data.js`，再跑两个生成器；直接改产物会在下次生成时被覆盖，
CI 两步 `--check` 会拦。详见 §24。

## 五、必须知道的约束

### 1. `startHats` 的字段匹配只认 field

`runtime.startHats(opcode, fields)` 会用 `hatFields[matchField]` 取值。如果传入的
参数在积木里是 **input** 而不是 field，VM 会直接崩。因此聊天关键词过滤没有走
这个接口，而是在 `vm-bootstrap.js` 里自己遍历顶层积木判断。

读这个 input 要按**运行期形态**来：`project.json` 里的 `[1, [10, "文字"]]` 在载入后
会变成 `{name, block, shadow}`，字面量本身成为一个独立的替身积木，值放在它的
`fields.TEXT`（文本）/ `fields.NUM`（数字）里。按原始数组去读会永远得到空串，
而空串的语义是「关键词为空 ⇒ 匹配全部」——症状是「随便说句话都能触发」，
不报错、日志也没有。`block !== shadow` 则表示槽里接了变量或表达式，静态读不出值。

### 2. 阻塞类积木靠 Promise 挂起

scratch-vm 见到 primitive 返回 thenable 就把线程置为 `PROMISE_WAIT`。Java 动作通过
`ScriptContext.requestWait(n)` 声明要等多少帧，桥把它转给 JS 的 `__tmcWaitTicks`，
每帧由 `__tmcPumpPromises` 兑现。条件型等待（等待方块被挖破）用
`requestWaitUntil(key, maxTicks)` + 每帧 `poll(key)`。

### 3. ClassShutter 白名单

脚本只能看到 `vm` 包的桥类，外加 6 个装箱类型的精确白名单（String/Double/Boolean/
Integer/Long/Object）。**不要放开整个 `java.lang`**——那样脚本就能摸到 `Runtime`。

### 3.1 标量必须以原生 JS 值进入 VM

`ScriptBridge.call` 的返回类型是 `Object`，Rhino 默认会把 `Double` 封装成
`NativeJavaObject`。而上游 `Cast.toString` 的实现就是 `String(e)`——对封装对象
取到的是 **Java 的 `Double.toString`**，于是生命值 20.0 拼进字符串会得到
`"20.0"`（Scratch 期望 `"20"`），`1e-5` 得到 `"1.0E-5"`（期望 `"0.00001"`）。
数值运算本身没错（`Number()` 会解包），**错的只是字符串化**。

修法是 `RhinoVm.ScalarWrapFactory`：覆写 `WrapFactory.wrap`，让 `Double` /
`Boolean` / `String` 直接以原生 JS 值进入 VM，`String(e)` 于是走 ECMAScript 的
`String(number)`。只放行这三个类型——脚本的值域本来只有这三种，其余类型
（数组、对象）封成 `NativeJavaObject` 才是正确行为。

这条由 `./gradlew vmNumberFormat` 守住。

### 4. 时间单位

编辑器概念里的 `TICKS` 按 tick 计（20 tick = 1 秒），sb3 的 `control_wait` 用
`DURATION` 按**秒**计。导入时 ×20、显示时 ÷20，两处都要做。

### 5. 单帧超时只跳过该帧

`RhinoVm` 用 Rhino 的指令观察器做时间预算（`budgetNanos × 4`）。超时**不能
`stopAll`**——那会把整个工程停掉，用户看到的现象是「脚本跑着跑着不动了」，
而且没有任何提示。现在只跳过该帧并节流记日志。

### 6. opcode 映射必须一对一

`Sb3Mapping.TYPES` 里一个内部类型只能对应一个 opcode。曾经把
`data_changevariableby` 和 `data_setvariableto` 都映射到 `data.set`，结果
「将 i 增加 1」在查看器里显示成「将 i 设为 1」。这类错误不报错，只是语义悄悄偏移。

### 6.1 扩展积木的参数名来自显示文本的占位符

积木方法体转发到 Java 时，实参按**位置**取（Java 侧读 `args.<名字>`），而位置
来自积木显示文本里的 `[NAME]` 占位符——`vm-bootstrap.js` 的 `__tmcArgNames`
用正则提取。这是唯一与 `arguments` 一一对应且顺序可靠的来源（对象 key 顺序在
ES5 里不保证）。

代价是：**改写显示文案时若动了占位符的拼写或顺序，实参会静默错位**，不报错，
只是某个积木的参数语义悄悄偏移。

`RhinoVm.argsSelfTest()` 交叉校验两边数量：扩展侧的占位符数（JS 侧经
`__tmcArgNamesDump` 暴露）与查看器侧登记的参数数（`BlockRegistry`）必须相等。
数量对不上，参数就一定错位。这条由 `./gradlew vmSmoke` 守住。

> 它已经抓到过一次真实漂移：查看器给 `action.break_wait` 登记了 `TICKS` 参数，
> 而扩展里的 `breakWait` 早已无参数（上限由宿主侧固定）。运行路径一直是对的，
> 错的是查看器多显示了一个不存在的参数。

### 7. 脚本按下的键必须在引擎停止时统一释放

`KeyBindingMixin` 从 `isPressed` 的读取端接管：只要 `ScriptKeys` 表里登记了某个键就
恒返回 `true`，**它不看引擎是否在运行**。因此「一直按住」的键（`ticks < 0`）只能由
`ScriptEngine.stop()` 与 `restart()` 调用 `releaseEveryKey()` 松开。漏掉这一步，
用户关掉引擎后角色会一直往前走，且没有任何报错。

限时按键（`ticks >= 0`）另有一条路径：每 tick 的 `ScriptKeys.tick()` 到期自动松手。

### 8. 容器操作只能走 `clickSlot`

客户端 `world` 只是服务端状态的副本，直接改 `BlockEntity` 会被下一次同步覆盖——
看起来「写成功」，实际无效。`clickSlot` 发的是和玩家手点完全相同的包，是唯一合法路径。

另一个必须记住的点：**点击是异步的**。发包 → 服务端处理 → 回发槽位更新，
同一帧读到的仍是旧值。脚本里每次点击后要等一 tick 再读状态。
槽位编号与其余时序细节见 [CONTAINER.md](CONTAINER.md)。

### 9. 按键归属标识恒为 `null`

`ScriptContext.currentInstance()` 目前恒返回 `null`——scratch-vm 里没有稳定的脚本句柄
可以传出来（线程 id 会复用）。后果是所有按键都记在同一个归属上：两个脚本按同一个键时，
一个松手会连带松开另一个。单脚本场景无影响。

`ScriptKeys.releaseAll(owner)` 因此也没有调用方，真正兜底的是引擎停止时的
`releaseEveryKey()`。要做按脚本精确回收，得先让 JS 侧把线程标识带进桥。

### 10. 扩展 id 是 `turbomc`，opcode 前缀随之固定

TurboWarp 扩展的 opcode 由「扩展 id + 下划线 + 积木名」组成，所以积木在
`project.json` 里写作 `turbomc_log`、`turbomc_tap` 这样。

**改扩展 id 等于改所有积木的名字**：已经导出的工程会变成未知积木，而且不报错，
只是积木消失。要改就必须同时改 `Sb3Mapping` 的 67 条扩展映射、`ScriptEngine` 里三个
帽子 opcode、`vm-bootstrap.js` 的拼接前缀，以及所有既有的 `.sb3`。

### 11. 同一条聊天只触发一次

`CHAT` 回调可能对同一条消息触发多次（服务端广播、代理转发、其它模组重放），
而脚本关心的是「这条消息处理过没有」，不是「收到几次回调」。

`ChatGate` 用消息签名当身份：签名由服务端为每条消息生成，天然唯一，同一条消息
无论回调几次只放行一次；玩家再打一遍相同文字属于新消息，照常触发。
单机 / 离线模式没有签名，退化为「正文 + 1 秒窗口」——重复投递都在毫秒级，够用。

**刻意不按正文永久去重**：那会让「再说一次同样的暗号」永远失效，玩家只能重开引擎，
等于把一个 bug 换成更难理解的行为。切工程时 `ChatGate.reset()` 清空记录。

另一层是 `SelfChatEcho`：脚本自己发的聊天会被服务端回传，这是一条**新消息**
（新签名），`ChatGate` 管不着，由它按「发送时登记、收到时抵消」拦掉，
避免「当聊天包含 X → 回复含 X 的聊天」形成无限回路。

### 12. 不提供攻击积木

`interactionManager.attackEntity` 发的包和玩家左键完全相同，但**自己选目标**这条路走不通：

- 服务端对四类目标直接 `disconnect(invalid_entity_attacked)`：掉落物（`ItemEntity`）、
  经验球（`ExperienceOrbEntity`）、玩家自己、`isAttackable()` 为假的箭。
  刷怪塔里「最近的实体」几乎必然是掉落物，脚本一跑就被踢出存档。
- 就算过滤掉，自动选目标 + 瞬间转向 + 等间隔连点，在带反作弊的服务器上仍是
  killaura 的典型特征（原版服务端不管这些，但插件会）。

因此攻击统一走输入积木：`点击左键` / `按住 mouse.left`。这条路径是
`KeyBinding.onKeyPressed` → 原版 `doAttack()`，客户端自己做射线与距离判定，
服务端收到的与手点一模一样。

### 13. 「走向」是会话，不是一次调用

`走向 x y z` 不返回估算时长，而是启动一个跨 tick 的移动会话，脚本靠
`requestWaitUntil("move", …)` + `ScriptBridge.poll("move")` 挂起。三条必须守住的规矩：

1. **按键按状态变化收发**：`ScriptKeys.hold` 是**幂等**的（重复按下只延长到期时间，不累加计数），
   所以每 tick 重复调用不会把键卡住。跟随器仍走 `setForward` / `setJump` 这种"状态变了才动"的包装：
   少做无谓的 `setPressed`，也让内部状态位始终等于我们以为的物理状态。
   注意 `ticks = -1` 的长按键不会自动到期，收尾只能靠 `abort()` 或引擎停止时的统一释放。
2. **JS 等待上限要比 Java 超时大 40 tick**：`__tmcWaitUntil` 到点会自行 resolve、不通知 Java；
   两边相等时脚本会先往下跑，而角色还在走。
3. **`halted`（停止全部）分支必须 `abort()`**：该分支在 `tick` 里直接 return，
   跟随器不再被驱动，不补就是前进键永不松开。

寻路本身复用原版僵尸的 A\*，但**只复用"找路"**：`MoveControl` 是给 `MobEntity` 直接写移动向量的，
玩家没有，位移只能靠按键。细节与选型见 [PATHFINDING.md](PATHFINDING.md)。

同理，「平滑转向」也是会话：`TurnSession` 每 tick 判定一次进度，脚本用
`requestWaitUntil("turn", …)` 挂起。它与 `PathFollower` 共享 `TurnMath`——两份平滑实现迟早会走偏。
`TurnSession.isActive()` 为真时 `PathFollower` 让出 yaw，否则两边各写一次、互相抵消。

**视角的实际写入在渲染帧上，不在 tick 里**（`script/view/ViewSmoother`，注册于
`WorldRenderEvents.START_MAIN`）。原因：`ClientPlayerEntity.getYaw(float)` 在非骑乘时直接返回原始 yaw，
相机不做 tick 内插值，所以 tick 里每 50 ms 写一次就是每 3 帧（60 fps）跳一格的台阶。
tick 只登记目标角度，帧层用 `1 - exp(-k·dt)` 收敛并按 240°/s 限速——写入频率跟上帧率，台阶消失，
整条路径不需要 mixin。玩家动鼠标（yaw 被外部改过）时本帧让位，下一帧继续。

视角归属分四档：`WALK`（寻路）/ `TURN`（平滑转向）/ `MANUAL`（「看向 / 看向坐标」积木直接写值，
保持到下一次走向或转向开始）/ `NONE`。`MANUAL` 期间移动方向由 yaw 派生，因此走向会把角色带向
新朝向——这是「看向接管视角」的既定取舍，不是缺陷。

### 14. 寻路有两处输出：世界里的路线 + HUD 的状态行

路线走世界渲染（`PathRenderer`），状态行走 HUD（`ScriptHudElement` 加两行）。
两者都只读 `PathFollower` 的快照（`nodeView()` / `status()`），不反向驱动会话。

HUD 的文本要按「内容变化才重建」处理：距离每 tick 变，取到 0.1 格后约 5 tick 变一次，
中间复用同一对 `Text`。

路线用 `PathRenderer`，挂在 `WorldRenderEvents.AFTER_ENTITIES`，读 `PathFollower.nodeView()` 的**不可变快照**
（`volatile` 整体替换）。渲染线程不遍历可变的 `Path`、不分配集合——那是客户端线程的对象。

线层用 `RenderLayers.lines()`，带深度测试：被墙挡住的段落看不见，这是有意的，
能直接暴露"路径穿墙"。

### 15. 引擎停下后不再接受任何外部触发

`ScriptEngine.active()`（`running && !halted && healthy`）是唯一入口守卫，`fire` / `fireKey` /
`fireChat` 都先过它。少了这道判断，引擎停止后按键与聊天仍会 `startHat`：线程被压进
`runtime.threads` 而 `_step` 不再调用——只进不出，长时间挂机会持续涨内存。
`stop()` 还必须清空 `cachedTriggerKeys`：键表留着，分发器仍会按旧键逐 tick 派发。

**退出世界要复位持久开关**：`scriptEngineEnabled` 是写进配置的持久值，玩家上一局按过 F6
它就一直是 `true`，下一局进世界的第一帧会直接 `start()` + `greenFlag()`——从玩家视角是
「没按 F6 脚本自己跑了」。`ClientPlayConnectionEvents.DISCONNECT` 是退出存档与断开服务器
唯一共用的收口点，回调已投递主线程，在那里调用 `ScriptEngine.onWorldExit()`：
按 `disableEngineOnExit`（缺省视为开）复位并 `save()`，随后 `stop("退出世界")` 立即收尾，
不等下一 tick 的 `player == null` 分支——断线那一帧世界引用可能还没清干净。

### 16. 「停止全部」要自己订阅 `PROJECT_STOP_ALL`

scratch-vm 的 `runtime.stopAll()` 只清线程并发一个事件，宿主不订阅就完全不知情：
脚本已经停了，「走向」会话还在每 tick 按前进键，最长走满 `MAX_TICKS`。
`vm-bootstrap.js` 把事件计成计数器，`ScriptEngine` 每 tick 取走，>0 就置 `halted` 并收尾。

**启动流程必须清一次计数**：`greenFlag()` 内部也会 `stopAll`，不清就会在进入世界的第一帧
被误判成玩家执行了停止全部。`vmSmoke` 有一条断言钉住这点（计数 ≥1、再取为 0）。

### 17. 列表与变量同存 `target.variables`，用 `type` 区分

这套 scratch-vm 不再把列表放在 `target.lists`：`lookupOrCreateList` 先按 id 在 `variables`
里找，再按名字 + `LIST_TYPE`（字面值 `"list"`）找。所以读列表必须遍历 `variables` 并检查
`type === 'list'`——只看 `target.lists` 会永远拿到空数组，表现为「HUD 上的列表一直是空的」。
`__tmcGetList` 两个位置都看，兼容旧数据模型。

### 18. VM 在标题界面预热，不在进世界时初始化

`init()` 约 0.6–0.7 秒（解析 1.5 MB VM 脚本），首次 `loadProject` 还有约 0.3 秒冷路径，
两者都在主线程。放标题界面用空工程走一遍，进世界只剩几十毫秒（实测 331 ms → 37 ms）。
预热失败不拖累客户端：`start()` 会再试一次，异常只让脚本暂时不可用。

**预热只看总开关，不看引擎开关**（1.4.1 引入回归、本次修正）。`disableEngineOnExit` 默认开，退出世界会把
`scriptEngineEnabled` 复位成 false 并写盘；预热若跟着这个开关走，下次启动在标题界面就不预热，
玩家进世界后按 F6 才现付 `vm.init`(0.7 s) + 工程冷载(0.4 s)，全部落在渲染线程上——
现象是「进游戏后第一次开脚本卡死约 1 秒」。

判据在日志里很直接：有 `[script] VM 预热完成` 说明预热跑过；没有它而 F6 时出现
`[perf] vm.init 单次 700 ms`，就是冷启动落在了游戏内。1.4.1–1.4.5 的日志都是后者，
1.4.0 及更早每天首次启动都有前者。

代价转移到启动阶段（游戏本来在加载资源，几乎无感）；这是刻意的交换，别为了省 0.6 秒改回去。

### 19. 聊天文本在帽子积木里只进不出

`当聊天包含 [MATCH]` 是帽子积木，`MATCH` 只做过滤：`vm-bootstrap.js` 的
`__tmcStartHatFiltered` 用 `needle.indexOf(pattern)` 判断，命中就 `_pushThread`，
**文本随即丢弃**。帽子积木没有输出参数，线程启动后拿不到那句话。

要读聊天必须走 `ChatState`。写入位置在 `TurboMCClient` 的聊天回调里，且必须在
`ChatGate.accept()` **之后**、`fireChat()` **之前**：

- 放进 `fireChat` 里写 → 只有命中关键词的那几条会被记录，脚本无法「按内容自己判断要不要理」
- 放在 gate 之前 → 同一条消息重复投递时会反复覆盖（内容相同，语义脏）

读取是 `query.last_chat`，最终由「最近的聊天」积木暴露。**存的是完整消息**，带
`<玩家名> ` 前缀——这是刻意的：多人服里脚本要能区分是谁在说话。副作用是「开头是 X」
这类判断永远不成立，只能用「包含」，文档里已写明。

**只保留最后一条**：脚本正在
等待（寻路、AI 请求）时连来三条，前两条会被覆盖——这是这个名字承诺的语义，要完整队列
是另一件事，别在这里偷偷扩。

#### 双通道：玩家聊天 ≠ 服务器系统消息

代理服与子服常把玩家聊天包成 system 消息下发。客户端日志里两种形态一眼可分：

| 通道 | 日志形态 | Fabric 事件 | 正文 |
|---|---|---|---|
| 真玩家聊天 | `[CHAT] <名字> 内容` | `CHAT`（在 `onChatMessage` 触发） | `<名字> 内容` |
| 服务端消息 | `[System] [CHAT] 名字: 内容` | `GAME`（在 `onGameMessage` 触发） | `名字: 内容` |

聊天栏里两者完全一样，所以「单人正常、进服后当聊天包含永不触发」只可能来自这条差异。
`TurboMCClient` 因此注册了两个回调；`fireServerChat()` 与 `fireChat()` 只差一处：
**系统消息通道只启动「关键词可确定且命中」的帽子**。

- 关键词为空、或槽里接的是复合表达式 → 读不出，默认不启动（`chatServerAnyMatch = false`）。
  这个通道混着插件广告、加入离开提示与倒计时，放行等于每条广告都把脚本拉起来。
- 关键词是**直接接进槽里的变量** → `__tmcResolveKeyword` 去 `target.variables` 取值，
  角色里找不到再回退舞台，因此仍是精确过滤。
- 复合表达式不求值：本版 VM 没有暴露 `getInputValue`，自行实现等于复制一遍运算语义，
  一旦与 VM 不一致，表现就是「关键词明明命中却不触发」，比不触发更难查。
  确实需要这类写法时打开 `chatServerAnyMatch`，退化为任意匹配。

`ChatState` 在系统消息通道上是**无条件覆盖**（含插件广告）：通道不同，语义不变——
「最近的聊天」永远等于最后到达的那条消息。

#### 触发消息必须绑在「线程」上，不能只放全局槽

聊天事件的处理顺序是「先写 `ChatState`，再 `_pushThread`」，而线程要到**下一帧**才执行。
中间任何一条消息（别的玩家、系统通道的插件广告）都会改写全局槽。实测：A 触发帽子、B 在
中间到达，线程读到的是 **B**。脚本如果在 `aiAsk` 前面还有「等待」「说」「走向」，窗口能拉到
秒级，等于必错。

修法：入队时把消息挂到线程对象上，读的时候优先取它——

```js
var pushed = runtime._pushThread(id, target);
if (!pushed) { pushed = runtime.threads[runtime.threads.length - 1]; } // 本构建不返回线程
if (pushed && needle !== null) { pushed.tmcChat = needle; }
```

读取端在积木包装器的第二参数 `util` 上：`util.thread.tmcChat`。因此包装器签名必须是
`function (args, util)`——少写这个参数不会报错，只会静默失去上下文。

两个报告器的分工：

| 积木 | 语义 | 非聊天线程 |
|---|---|---|
| 最近的聊天 | 有线程副本就用副本，否则回落到全局槽 | 全局最新一条 |
| 触发我的聊天 | 只用线程副本 | **空串**，刻意不回落 |

不回落是刻意的：回落就等于「最近的聊天」，这个积木没有存在意义。空串同时给脚本一个
「我是不是被聊天拉起来的」判断手段。

**标签不跨线程**：广播派生的线程、绿旗线程身上没有副本，只能走回落/空串。要跨广播传消息，
自己在线程里先存进变量。

**已知隐患（未处理）**：两个回调都运行在网络线程，会直接操作 Rhino 的
`runtime.threads`——`_pushThread` 前的 busy 检查与随后的 push 并非原子。现有 `CHAT`
路径一直如此，本批次为控制改动面保持一致。要改成主线程投递，得同时重排「记录 → 派发」
的时序与 `ChatGate` 去重窗口，属独立批次。

### 20. AI 请求靠 poll 挂起，而 `poll` 的默认返回值是「已完成」

`aiAsk` 提交请求后调 `requestWaitUntil("ai:" + id, timeoutTicks + 40)`，JS 侧包装成
Promise 让线程进入 `PROMISE_WAIT`，每帧 `__tmcPumpPromises` 回调 `ScriptBridge.poll`。
两个必须守住的点：

1. **`poll` 必须在「未知 key 一律 true」之前识别 `ai:` 前缀**。漏了它就等于立刻放行，
   现象是积木一闪而过、回复永远空串，且没有任何异常。这条由 `./gradlew vmAi` 的端到端
   断言守住（变量必须变成桩返回的文本）。
2. **JS 等待上限要比 HTTP 超时大 40 tick**。`__tmcWaitUntil` 到点自行 resolve 且不通知
   Java，两边相等时脚本会先往下跑而请求还在飞（与「走向」同一条坑）。

网络只能在 Java 侧：Rhino 没有 `fetch` / `Worker`。会话表、历史裁剪、并发上限、
在途请求取消都在 `AiService`；它不引用任何 Minecraft 类型，配置由 `Supplier` 注入，
因此能在纯 JVM 里用本地 HTTP 桩测全部分支（`./gradlew vmAi`）。

失败一律**返回空串、不抛异常、不挂起**：异常会被桥吞成 null，脚本静默乱走，比空串更难查。
失败时同时清空 `last`——否则脚本会把上一次的旧回复当成这一次的。

切包与停引擎必须 `cancelAll()`：回调落到已停止的 VM 上没有意义，线程池里的连接不取消就是泄漏。

**会话模型**：`AiService` 按 `SESSION` 参数分流（trim 后区分大小写），每个会话持有 system、
历史（只存 user / assistant）、在途请求 id、最近回复。历史按 `historyTurns × 2` 条裁剪；
改 system 提示时**同时清空历史**——人格换了，旧对话只会让模型前后矛盾；同一会话同时只允许
一个在途请求，第二条直接失败而不是排队，否则历史顺序会与服务端实际处理顺序错开。

**可观测性**：`AiService.snapshot()` 是唯一的只读出口，HUD 与运行面板都只读它，不碰内部字段。

- 每个会话给出：阶段（`IDLE / WAITING / OK / FAILED`）、已等毫秒、最近耗时、截断后的
  提问 / 回复 / 错误、历史轮数、是否设过 system。
- 全局给出：是否配置、是否「用过」、在途数 / 并发上限、模型名。
- **失败原因两面分家**：日志留完整异常链，快照里只存短原因（`超时` / `HTTP 401` /
  `连接失败` / `响应解析失败`）。HUD 一行放不下类名，玩家也不需要。
- **2xx + 空 content 算失败**，理由写 `空回复`，日志里附 `finish_reason`。
  算成功的话 HUD 上是「绿勾 + 空白」，玩家只会以为模组坏了；而 `finish_reason=length`
  通常意味着 `maxTokens` 被推理过程吃光，这条信息是唯一能自解释的线索。
- HUD 的成功行**把回复拼成字面量**而不是塞进翻译格式串：少一个 `%s` 就整段消失，
  这种坑的排查成本远高于省下来的那几行。
- **文本一律截断**（提问 80、回复 80、错误 80），HUD 再按宽度二次裁剪；完整内容在日志。
- 聚合逐会话加锁并拷贝，不做长临界区：调用方是渲染线程，每帧一次。

呈现分两层：HUD 一行结论（只在有活动时出现，成功/失败停留时长、最多行数、回复预览字数
三个值都在模组设置里可调），运行面板给明细（阶段、耗时、历史轮数、回复前 80 字、错误原文）。
要加新字段时改 `SessionStatus` 一处，两层自动跟上。

**已知限制**（改之前先算代价）：
- 并发上限检查（`inFlight.size() >= maxConcurrent`）不是原子操作，当前依赖 VM 单线程执行；
  将来若从多线程调 `ask`，这条会失效
- `HttpClient` 的线程池大小与连接超时在 build 时固化，改配置后**下一次请求**才按新值重建
  （旧池走 `shutdown()`，在途请求照常完成）；两者都变了才重建，不会每次请求都换 client

> 这两项曾经是「配置骗人」：池写死 2 线程，`maxConcurrent` 配到 8 实际仍只跑 2；
> 连接超时只在首建时读一次，改了要重启客户端。现在都按配置重建，
> `vmAi` 有一条「四个请求真并行」的断言守住它——判据是**桩侧同时处理过 4 个请求**，
> 不是总耗时：耗时在高负载机器上会飘，同时处理数只由线程池大小决定。

**未决**：部分厂商要额外请求头（`anthropic-version` 之类）或非 OpenAI 格式；
`java.net.http` 默认不读 `http_proxy` 环境变量；脚本可以写出无数个会话名，是否需要上限。

### 21. 聊天必须先清洗再发，否则玩家会被踢

模型回复与脚本拼出来的文本是自由文本，可能带换行、`§` 或超过 256 字符。原版服务端
对这些**不截断，直接 `disconnect`**，客户端只看到一句「非法聊天字符」，玩家被踢出存档，
而日志里没有原文——这次真机事故就是这么发生的，排查时无从下手。

所以 `GameActions.chat()` 在发送前一律走 `ChatText.sanitize()`：换行 / 回车 / 制表折成空格、
删 `§` 及其格式码、删其余控制字符与孤立代理项、折叠连续空格、超长时截断并补 `…`。
中文与 emoji 不动——它们本身合法。

**截断要同时满足两个上限**：256 字符，且 UTF-8 字节 ≤ 768。服务端的
`ByteBuf.readUtf(256)` 内部按 `maxLength * 3` 校验字节，只盯字符数会漏掉一种情况——
256 个字符里混了 emoji（4 字节/个）时字节数超标，服务端读包失败同样掉线，
只是断开原因与「非法聊天字符」不同，更难联想。截断按码点逐个填充，不切代理对。

两个必须守住的细节：

1. **回声登记要用清洗后的文本**。`SelfChatEcho.sentByScript()` 登记的是实际发出去的内容，
   服务端回传的也是同一串；用原文登记会认不出来，`当聊天包含` 就会被自己的回复再次拉起。
2. **不要去清洗 `ai.reply` 的返回值**。脚本可能拿回复做比较、取子串，改写数据源会破坏
   这类用法。清洗只放在唯一出口。

提示只打一次（`warnedChatSanitize`），且只记长度不记内容——聊天内容不该进日志。

**超长分段发**：单条上限之外的文本由 `ChatText.split()` 按标点切成多段（切点优先落在
`。！？；，` 上，找不到才硬切），交给 `ChatQueue` 按配置的间隔逐条发出
（`chatIntervalTicks`，默认 3 tick ≈ 150 ms）。间隔**不要取 1**：原版 1.19+ 的
`ChatSpamTracker` 每秒上限 20 条，每 tick 一条正好贴着红线。段数上限 `chatMaxSegments`
（默认 8，约 2000 字），超出的部分不再发送，末段补 `…`。

`ScriptEngine.tick()` 每 tick 推一次队列；停引擎、切包、停止全部都必须 `clearChatQueue()`，
否则上一份工程的长回复会在新工程里继续冒出来。

这顺带修掉一个旧行为：以前同帧的第二次「在聊天栏说」会被 50 ms 节流**静默丢弃**，
现在全部入队，一条不丢。

`执行命令` 不走这条：`sendChatCommand` 是独立包，服务端按命令解析，非法内容只报
「未知命令」不会断开，256 字符的上限也不适用于命令（命令上限是 32500），
套用聊天的截断反而会把长命令切坏。

### 22. 工程声明了未实现的扩展时，必须用占位实例登记

带第三方扩展的工程（`project.json` 里有 `extensions` 与 `extensionURLs`）直接载入会失败：

```
ReferenceError: "Worker" is not defined
    at RhinoVm.loadProject
```

原因是 scratch-vm 的 `installTargets` 对每个「未加载」的 extensionID 调
`loadExtensionURL(url)`，而沙箱路径是 `new Worker("./extension-worker.js")`——
Rhino 没有 Worker，Promise 被拒，整个 `loadProject` 失败。玩家看到的是
**「✕ 工程载入失败」**，不是「某个积木不生效」，这是最容易让人以为模组坏了的一种失败。

`vm-bootstrap.js` 的 `__tmcRegisterUnknownExtensions` 在载入前为每个未知 id 注册一个
`blocks: []` 的占位实例，让 `isExtensionLoaded` 为真，从而跳过 Worker 路径。
该扩展的积木没有实现，走到时返回空值；其余积木照常运行。

**两个必须记住的点**：`__tmcLoadProject` 收到的是 project.json 的**字符串**，
必须先 `JSON.parse` 才能读 `.extensions`；占位注册只是让工程能载入，不等于支持该扩展。

### 23. JSON 积木是「文本进、文本出」，不引入对象句柄

29 块 JSON 积木（对齐 TurboWarp 扩展 `skyhigh173JSON`，MIT；原 32 块按下列规则合并）的输入输出全是标量，
所以完全落在桥的「标量单值」约定内，没有第二套值模型：

- 解析与序列化在 `action/JsonText`。**解析器是自己写的**：Gson 的 `JsonParser` 入口会把
  `JsonReader` 临时切成宽松模式，`{a:1}` 会被判成合法，与浏览器的 `JSON.parse` 分叉；
  数值序列化复用 `ScratchCast.numberToString`，否则 `1.0` 写回成 `"1.0"`，字符串比较会莫名不成立。
- 列表互通的两块（`json.vm_getlist` / `json.vm_setlist`）必须读 VM 内部状态，
  实现放在 `vm/VmListActions`、由 `RhinoVm.init()` 注册——保持 `vm → action` 这条既有依赖方向，
  且仍走「桥 → NativeRegistry」单一路径，不在积木包装器里开分支。
- **面板瘦身**（原 32 块 → 29 块）：「键数量」与「数组长度」合成一块「长度」（本就是同一实现）；
  「是合法 JSON 吗」与「是 [对象/数组] 吗」合成三选一菜单；数组下标统一为 1 基后，
  「数组第 n 项」与「按键取值」不再需要两块——**下标基准不一致比少一块积木危险得多**。
- 与原文的偏差只有三处：解析错误返回中文说明带位置（浏览器文案无法跨引擎复刻）、
  「长度」解析失败返回空串（原文是一个空格，明显笔误）、嵌套深度上限 256（防御递归栈）。

### 24. 扩展的两份产物是生成物，唯一源在 `turbowarp/src`

`turbowarp/turbomc-extension.js`（编辑器副本）与
`assets/turbomc/vm/turbomc-extension.es5.js`（运行时副本）都由
`node tools/build-extension.js` 渲染，数据源是：

- `turbowarp/src/blocks.data.js` —— 96 块积木、12 个菜单、15 个分组标题、每块的占位方法体；
- `turbowarp/src/items.data.js` —— 1505 条物品（`注册名|中文名|英文名`）。

**直接改产物会在下次生成时被覆盖**，CI 用 `--check` 拦「产物过期」，`verify-extension.js` 继续守
编辑器契约。两份产物只有四处差异，其余逐字一致：

| 差异 | 编辑器副本 | 运行时副本 |
|---|---|---|
| 语法 | ES6 `class` | 普通对象 + 函数属性（**没有 Babel 中间层**） |
| 分组标题 | 渲染 LABEL | 整段跳过（内嵌 `vm.es5.js` 不支持 label） |
| 菜单 items | 内联数组（Worker 要结构化克隆） | 大菜单用函数引用（注册期不展开 1505 项，省约 1.6 秒） |
| 占位方法 | 生成（浏览器里只能返回假值） | 不生成——包装器在注册扩展**之前**就把实现装到该对象上 |

迁移时用 `tools/compare-getinfo.js` 做语义闸门：把基线与新产物分别加载、取 `getInfo()`，
在**中英两种语言**下比较积木顺序/文案/参数/菜单取值/帽子属性；运行时按 opcode 排序后比较
（顺序无意义），并抹平「数组 vs 函数引用」「有无标题」两处设计差异。要求零差异才允许切换。

**`turbowarp/mapping-report.md` 同理**：由 `node tools/build-mapping-report.js` 从
`Sb3Mapping.java` 与 `blocks.data.js` 生成，不再手工维护。生成时还会核对两边的 opcode 集合
——「数据源有、映射表缺」或反之都会直接报错，因为那正是「面板里有积木、游戏里没反应」的成因。
上一版手工维护的表已经漂移（JSON 组内顺序、`sensing` 组顺序都与面板/源码不符），生成后自动一致。

## 六、HUD
HUD 的第一行是引擎状态，由 `ScriptEngine.Status` 单一来源决定，三处界面共用
（HUD / 包列表 / 运行面板），不各自判断 `isRunning()`：

| 状态 | 前缀 | 颜色 | 触发 |
|---|---|---|---|
| `RUNNING` | `●` | 绿 | 工程已载入并在推进 |
| `OFF` / `NO_PACK` / `NO_WORLD` / `OFF_WORLD_EXIT` | `○` | 灰 | 没开、没选包、还没进世界、退出世界自动关 |
| `HALTED` | `○` | 黄 | 脚本执行了「停止全部」，需按 F6 |
| `LOAD_FAILED` / `RUNTIME_ERROR` | `✕` | 红 | 工程读不出/启动失败、运行期异常 |

状态之后跟具体原因（异常摘要，截断 60 字），故障时单独占一行：挤在状态行里会被宽度
裁掉，等于没显示。这是「为什么脚本不动」在游戏内唯一的自查出口。

**故障状态不被覆盖**：`stop(status, detail)` 内部拆成 `teardown()` + `setStatus()`，
`teardown()` 只收尾（会话、按键表、聊天队列、AI 请求），不改状态。否则「载入失败」
会在下一 tick 被 `!healthy` 分支改写成普通的「已停止」，原因丢掉。

**VM 不反向依赖引擎**：`RhinoVm` 在 `catch (RuntimeException)` 里只把摘要存进
`fatalError`，由 `ScriptEngine.tick()` 用 `takeFatalError()` 拉取——结构与
`takeProjectStops()` 一致。让 VM 直接调引擎会形成双向依赖。

状态行之下是脚本的「说 / 思考」气泡；寻路进行时再追加两行（目标 / 距离 / 节点进度 /
规划耗时），受 `showPathHud` 控制。

**位置用两个百分比，不用四角枚举**：`hudOffsetX/Y`（0–100）映射到「可放置区间」而不是
裸屏幕坐标——`x = MARGIN + (W - boxW - 2·MARGIN) · pct/100`，于是任意百分比都落在屏幕内，
窗口缩放与 GUI scale 变化自动跟随，不需要事后裁剪。代价是面板变宽时同一百分比的左边界会
左移（`pct=100` 贴右不受影响）。四角退化成 `0 / 100` 的端点；旧配置没有这两个字段时按
`hudCorner` 推导，行为不变。面板始终向右下展开、文字左对齐——右对齐会在内容宽度变化时
左右抖动。

这里补的是一个真实缺口：`looks_say` / `looks_think` 是 scratch-vm 的原生积木，
游戏内原本**没有任何渲染出口**——不补的话脚本永远无法对外表达。

读法是 `target.getCustomState('Scratch.looks')`，用其中的 `usageId` 判断是否是新
一句（每次说话都会重新生成，比对比文本可靠）。每句停留 5 秒，最多同时 3 条。

## 七、构建

```bash
# Loom 1.19 要求 JDK 25，系统默认 JDK 21 会在配置阶段失败
JAVA_HOME=~/.jdks/ms-25.0.5.0.2 ./gradlew build
```

产物：`build/libs/turbomc-<version>.jar`

### 冒烟测试（不启动客户端）

| 任务 | 作用 |
|---|---|
| `./gradlew vmSmoke` | 载入 project.json，验证顺序执行 |
| `./gradlew vmSmoke -PvmMode=hat -PvmProject=hat-project.json` | 验证事件触发帽子积木 |
| `./gradlew vmSmoke -PvmMode=block -PvmProject=block-project.json` | 验证阻塞动作按时长挂起 |
| `./gradlew vmImport` | 验证 sb3 能解析成树，并被 VM 执行 |
| `./gradlew vmChat` | 验证聊天帽子一条消息只触发一次、关键词能过滤、自己发的聊天不回声 |
| `./gradlew vmNumberFormat` | 验证数值积木的字符串化是 ECMAScript 格式（`20` 而非 `20.0`） |
| `./gradlew vmKeyHat` | 验证「当按下 [键]」按 `KEY_OPTION` 正确派发：对得上起 1 个线程、对不上起 0 个 |
| `./gradlew vmList` | 验证 HUD 能读到列表内容，以及算术积木的 `NUM1/NUM2` 映射成 `A/B`；`-PlistProject=路径` 可换成任意工程 |
| `./gradlew vmDepth` | 验证超长 `next` 链被深度保护截断，而不是打穿线程栈 |
| `./gradlew vmPreload -Pmode=preload` | 测量预热前后首次载入工程的耗时（`cold` 为对照） |
| `./gradlew exportSmoke` | 验证扩展释放：创建 / 内容一致不动 / 旧版覆盖 |
| `./gradlew vmAi` | AI 对话：本地 HTTP 桩（正常 / 非 2xx / 坏 JSON / 超时 / 会话互斥）+ VM 端到端 |

**夹具在 `src/test/resources/turbomc-vm/`，进版本控制**——CI 上只有这一份，
放在 `run/` 下会因被 gitignore 而在 CI 上消失。

读取顺序：`-P` 参数指定的路径 → classpath 里的同名夹具。常用夹具：
`project.json`（顺序执行）、`hat-project.json`（聊天帽子）、`keyhat-project.json`（按键帽子）、
`list-project.json`（列表监视器）、`block-project.json`（条件等待）、`ask-project.json`（询问链路）、
`ai-project.json`（AI 对话：ask → reply → 写变量）、`fixture.sb3`（sb3 导入）。

> **`hat` 与 `block` 模式必须显式指定 `-PvmProject`**：默认的 `project.json`
> 里没有对应的帽子积木，`startHat` 会起 0 个线程——那是**假失败**，不是真问题。

### 单元测试

`./gradlew test`（已挂在 `build` 上，CI 自动执行）。只放**不依赖 Minecraft 客户端**
的纯逻辑，覆盖最容易悄悄回归的几处：

| 测试类 | 守住什么 |
|---|---|
| `ScratchCastTest` | Scratch 弱类型转换规则（非数字串得 0、`" false "` 为真、数字转字符串按 ECMAScript） |
| `ChatGateTest` | 同一条聊天只触发一次：有签名按签名去重、无签名按正文 + 1 秒窗口 |
| `TurnMathTest` | 转向步进的限幅 / 死区 / 最短路径，以及 NaN 输入得 0 |
| `ChatTextTest` | 聊天清洗与切段：换行 / `§` / 控制字符、256 字符与 768 字节双上限、按标点切、不切代理对；含「先 clean 再 split」的顺序回归 |
| `ChatQueueTest` | 聊天队列：首段立即发、间隔准确、溢出丢弃、clear 生效、未装出口不崩 |
| `EntityFilterTest` | 实体类型参数解析（`any` / `hostile` / `player` / 具体 id） |
| `ItemIdsTest` | 物品 id 规范化（`iron_ore`、`IRON_ORE`、带空格 → `minecraft:iron_ore`） |
| `Sb3RenderingTest` | sb3 → 查看器积木树的三条还原规则（错了像面板 bug，其实是导入 bug） |
| `ParamLabelsTest` | 参数本地化：拼出的语言键与语言文件里的键必须对得上 |

涉及客户端的验证一律走冒烟任务——纯 JVM 里没有客户端，动作会抛 NPE 被桥吞成
`null`，测不到真实行为。

## 八、使用流程

1. 在 TurboWarp 里加载 `turbowarp/turbomc-extension.js`（否则扩展积木显示为未知块）
2. 编写脚本，导出为 `.sb3`
3. 放进 `.minecraft/config/turbomc/imports/`（F7 界面里的「文件夹」可直接打开该目录）
4. 游戏内按 **F7** → 点「导入/更新」→ 点圆点启用
5. 按 **F6** 开引擎，HUD 出现 `● 运行中：包名` 即表示在跑
   - 总开关是持久化的：配置里 `scriptEngineEnabled` 为 `true` 时，进世界第一帧就会
     `start()` + `greenFlag()`，与有没有按 F6 无关
   - 退出存档/断开服务器时默认复位为 `false`（配置 `disableEngineOnExit`，字段缺失视为
     开），所以每局都要重新按 F6；关掉该项则保留上一局状态，进世界自动运行

## 九、日志关键字

排查时优先看这几个：

| 日志 | 含义 |
|---|---|
| `[script] 引擎就绪：积木定义 N 个，脚本包 N 个` | 初始化完成 |
| `[script] 已启动 xxx.sb3：N 个脚本，线程 N 个，监听按键 N 个` | 工程载入成功 |
| `[script] 没有启用的脚本包，等待导入` | 包目录为空或全部未启用 |
| `[pack] 脚本包就绪：N 个，启用 M` | 包扫描结果 |
| `[pack] 首次启动：从 imports 自动接管 N 个 sb3` | 升级后第一次运行的自动迁移 |
| `[vm] 单帧执行超出预算……已跳过该帧并继续运行` | 某帧超时（多为 GC 或同步发聊天），不影响后续 |
| `[vm] 未映射的扩展积木: xxx` | 扩展里有积木没进 `Sb3Mapping`，调用返回空值 |
| `[vm] 积木 xxx 已映射为 xxx，但动作层没有实现` | 映射到了类型，但 `NativeRegistry` 里没有对应实现 |
| `[script] VM 预热完成，进世界只需载入工程` | 标题界面的预热跑完了（正常应出现一次） |
| `[script] VM 预热失败，进世界后再试：…` | 预热出错，但进世界时 `start()` 会再 init 一次 |
| `[script] 检测到「停止全部」，已收尾，等待玩家按 F6 重新开启` | 脚本执行了停止全部，会话与按键已收尾 |
| `[script] 已按配置关闭引擎，下次进入世界需按 F6 开启` | 退出世界时按 `disableEngineOnExit` 复位了持久开关 |
| `[script] 按键「x」派发帽子但没起线程（工程里没有这个键的帽子？）` | 键名收集到了但 `startHats` 匹配不上 |
| `[perf] 最近一窗（30000 ms）：…` | 每 30 秒一条的性能汇总（`vm.step` / `path.plan` / `hud.read` 等） |
| `[perf] xxx 单次 N ms（≥33 ms…）` | 某一次调用超过两帧，卡顿就发生在这一下 |
| `[script] 容器距离 N 格超过交互上限` | 服务端会静默拒绝，所以这里提前说明 |
