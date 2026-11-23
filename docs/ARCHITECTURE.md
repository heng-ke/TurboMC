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
                        每 2 秒查 imports 指纹（autoReload，可关）
                              或 F7 点「导入」
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

**两个目录的分工（不要合并）**：`imports/` 是用户写入区，`packages/` 是运行副本区。
编辑时保存的目标文件不是瞬间写完的；若让它直接写 `packages/`，游戏恰在此刻
`restart()` 打开它会解析到半个 zip，表现为「工程载入失败」。同步用
「临时文件 + `ATOMIC_MOVE`」替换，保证 `packages/` 里永远是完整文件。
用户侧只需要面对 `imports/`。

**热重载**：`ScriptEngine.tick` 每 `reloadIntervalMs`（默认 2000）调一次
`PackStore.pollImports()`。指纹取「文件名 + 大小 + 文件 mtime」——
**不能用目录 mtime**，Windows 上覆盖同名文件不更新父目录时间戳，会永远不触发。
启用中的包被更新时才 `restart()`，导入新包不打断正在跑的脚本。

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
│   └── PathEffort.java         「走向」的 A* 搜索强度档位（轻量 / 标准 / 重量）
└── script/
    ├── ScriptEngine.java        门面：取启用包 → 载入 → 绿旗 → 事件转发
    ├── action/                  动作层（Java 侧真正操作游戏的地方）
    │   ├── GameActions.java     移动、挖掘、放置、聊天、命令、容器……
    │   ├── ContainerActions.java 容器：开界面、点槽位、搬东西、读槽位内容
    │   ├── WorldQueries.java    世界查询（方块、实体、时间……）与快照的取值层
    │   ├── Snapshots.java       快照的 JSON 拼装（只收原语，纯 JVM 单测）
    │   ├── ScriptKeys.java      按键按住 / 释放
    │   ├── InputKeys.java       Scratch 键名 ↔ Minecraft 按键
    │   ├── NativeRegistry.java  opcode → 动作的注册表
    │   ├── JsonText.java        严格 JSON 解析 + ECMAScript 兼容序列化（快照用；JSON 积木本身在 JS 侧）
    │   ├── ChatText.java        聊天清洗：换行 / § / 控制字符，256 字符与 768 字节双上限
    │   ├── ChatQueue.java       聊天发送队列：超长分段、按 tick 拉开间隔
    │   ├── path/                「走向」的移动会话（见 PATHFINDING.md）
    │   │   ├── PathActions.java    解析 x/y/z，启动会话
    │   │   ├── PathFollower.java   每 tick 驱动：转向 / 前进 / 跳跃 / 卡住
    │   │   ├── PathMath.java       到达判定 / 会话超时 / 卡住窗口 / 氧气余量（纯算术，有单测）
    │   │   ├── PathPlanner.java    假僵尸 + 原版 A*（LandPathNodeMaker + PathNodeNavigator）
    │   │   └── MoveTarget.java     目标坐标（三轴全部来自积木）
    │   └── motion/              朝向平滑（寻路与「平滑看向」积木共用）
    │       ├── TurnMath.java       比例收敛步进：gain / 限幅 / 死区
    │       ├── TurnSession.java    转向会话：目标 yaw/pitch、超时、中止
    │       └── TurnActions.java    注册两个平滑积木
    ├── runtime/                 宿主上下文
    │   ├── ScriptContext.java   每 tick 重建的宿主环境（含等待请求通道）
    │   ├── TriggerDispatcher.java 按键 / 受伤 / 死亡 / 界面打开 事件源
    │   ├── ChatState.java       最近 32 条聊天的环形缓冲（网络线程写、脚本线程读）
    │   ├── ExtensionHealth.java 扩展与模组的 opcode 集合自检（不一致时 HUD 红字）
    │   └── Values / ScratchCast  值转换（scratch-vm 已自带运算与比较，Java 不重复实现）
    ├── remote/                  浏览器远程开发桥（见 §7）
    │   ├── RemoteBridge.java    监听、Origin 校验、握手、心跳、单控制者、tick 入口
    │   ├── RemoteProtocol.java  协议常量、错误码、originAllowed()
    │   ├── RemoteExecutor.java  FIFO 队列、每 tick 预算、call / poll 执行
    │   └── SnapshotPublisher.java 每 tick 采集快照（采集在主线程，发送异步入队）
    ├── ai/                      大模型对话
    │   ├── AiService.java       HTTP（HttpClient 异步）、会话表、历史裁剪、并发与取消
    │   └── AiActions.java       四个积木的动作：ask / reply / reset / system
    ├── vm/                      JS 运行时
    │   ├── RhinoVm.java         Rhino Context 生命周期、时间预算、单帧中断
    │   ├── ScriptBridge.java    JS → Java 的唯一桥
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
    │   ├── PackStore.java       包管理：导入、热重载指纹、原子替换、互斥启用、改名、删除、解析缓存
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
├── blocks.data.js             97 块积木、16 个菜单、15 个分组标题、每块的占位方法体
├── items.data.js              1505 条物品（注册名|中文名|英文名）
├── remote-client.js           远程桥客户端：连接面板、__tmc、快照缓存、帽子匹配（见 §7.7）
├── json-lite.js               JSON 积木的唯一实现（ES5；编辑器内联，运行时复制为 json-core.es5.js）
└── stage-view.js              getDisplayMedia 画面捕获与舞台覆盖（同上）

tools/
├── build-extension.js         渲染两份扩展副本（--check 供 CI 守新鲜度）
├── build-mapping-report.js    渲染 turbowarp/mapping-report.md（同上）
├── json-parity-cases.json     JSON 语义对拍的 134 条用例
├── json-parity-golden.json    迁移前 Java 实现产出的权威基线（行为回归用）
├── check-json-parity.js       在 V8 里跑用例并与 golden 逐字比对（--write 重生成，见 §7.6）
├── probe/                     人工验证与回归工具（不发布）
│   ├── turbomc-probe-extension.js  独立探针扩展：画面捕获、权限、手势、连接四项一次测全
│   ├── probe-server.js             最小 WS 服务，用来单独验连通性与 LNA 授权
│   ├── test-stop-hats.js           扩展侧回归：帽子派发、停止链路、读数、列表（57 项）
│   └── test-panel-render.js        面板回归：快照 20Hz 刷新时控件不得被重建
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

### 5. 单帧超时只跳过该帧；载入期另有一道时限

`RhinoVm` 用 Rhino 的指令观察器做时间预算（`budgetNanos × 4`）。超时**不能
`stopAll`**——那会把整个工程停掉，用户看到的现象是「脚本跑着跑着不动了」，
而且没有任何提示。现在只跳过该帧并节流记日志。

**载入期（`loadProject` / `greenFlag`）必须单独设限**：这段时间此前完全不在预算内，
而坏工程恰恰在这里出事——`next` 链成环时 VM 不报错，只是无限执行同一批积木，
堆涨到数 GB、窗口无响应（DEF-04，实测 7.9 GB）。现在这两个入口会武装
`LOAD_DEADLINE_MS`（10 s），超时抛 `EvaluatorException`，工程直接标为载入失败。
数值给得宽，是因为正常载入要几百毫秒，与单帧的 16 ms 不是一个量级。

### 6. opcode 映射必须一对一

`Sb3Mapping.TYPES` 里一个内部类型只能对应一个 opcode。曾经把
`data_changevariableby` 和 `data_setvariableto` 都映射到 `data.set`，结果
「将 i 增加 1」在查看器里显示成「将 i 设为 1」。这类错误不报错，只是语义悄悄偏移。

### 6.1 扩展积木的参数顺序由 `argOrder` 固定，不跟界面语言走

积木方法体转发到 Java 时，实参按**位置**取（Java 侧读 `args.<名字>`）。
这份顺序来自产物的 `argOrder` 字段，由 `build-extension` 从**中文文案**的
`[NAME]` 占位符生成。运行时只认这个字段——若改成从 `block.text` 现场提取，
顺序会跟着界面语言走（英文文案的占位符次序不同），那正是这条规则修掉的 bug。

**为什么不能直接提取 `block.text`**：运行时的 `text` 是 `T(en, zh)`，选哪份取决于
界面语言。JSON 那批积木的中英顺序本来就不一样（`set [item] in [json] to [value]`
对 `把 [json] 中 [item] 设为 [value]`），直接提取会让英文环境下的实参整体错位，
而 Java 侧始终按中文顺序读——这类错位不报错，只是取值全错。

代价与守则：

- **改中文文案的占位符顺序 = 改协议**，Java 侧读 `args[i]` 的地方要跟着改；
- 英文文案的顺序可以不同，但**占位符名字集合必须与中文一致**（少一个就等于该参数在英文界面填不了）；
- `RhinoVm.argsSelfTest()` 只比对**数量**（扩展占位符数 vs `BlockRegistry` 参数数），
  名字与顺序由 `tools/build-mapping-report.js` 在构建期比对：中文文案 ↔ `arguments` ↔
  `Blocks.java` 的模板占位符与 `BlockParam` 名，四项必须逐字一致。

> 它已经抓到过一次真实漂移：查看器给 `action.break_wait` 登记了 `TICKS` 参数，
> 而扩展里的 `breakWait` 早已无参数（上限由宿主侧固定）。运行路径一直是对的，
> 错的是查看器多显示了一个不存在的参数。
>
> 加校验时又抓出一次：16 块 JSON 积木中英占位符顺序不同，英文环境下参数全错位。

#### 6.1.1 菜单参数在 sb3 里是「输入 + 影子菜单块」，不是字段

扩展积木的菜单参数（含 `acceptReporters: false` 的下拉）在工程文件里必须写成
`inputs: { NAME: [1, "<影子块 id>"] }`，影子块的 `opcode` 就是菜单名、字段名也是菜单名：

```json
"inputs": {"SCREEN": [1, "s1"]},
"s1": {"opcode": "screenKind", "fields": {"screenKind": ["container", null]}, "shadow": true}
```

**写成 `fields: {SCREEN: ["container", null]}` 时，载入阶段整块会被静默丢弃**——
不报错、不留日志，现象是「积木在工程里，但帽子永不触发」。手工构造夹具或生成 sb3 时尤其要小心；
TurboWarp 自己导出的工程天然是正确形态。

同一条也解释了为什么 `vmScreenHat` 用自己的遍历而不是 `runtime.startHats`：
压缩版 VM 对「带参数的扩展帽子」的字段匹配不可用（同一工程里无参数的 `whenDamaged`
能起线程，带 `SCREEN` 的起 0 个）。此外 `RhinoVm.threadCountOf` 读的是结果的 `.length`，
JS 侧必须返回**数组**而不是计数，否则永远显示 0。

### 6.2 合并积木：旧 opcode 已删除，旧工程不再兼容

把「同一对象的多个字段」合成一块下拉（坐标三轴、最近实体的距离/坐标/名称），
面板从 101 块降到 86 块。**这是有代价的**：sb3 里存的是 opcode，删掉旧 opcode
就等于删掉旧工程里那块积木。

2026-11-19 的决定是硬删 15 块：`posX/posY/posZ`、`aimX/aimY/aimZ`、
`nearestEntity` 与它的 `X/Y/Z/name`、`heldItem/heldDurability`、`slotItem/slotAmount`。

**失效形态比报错更隐蔽**——实测（`vmSmoke -PvmProject=<旧工程的 project.json>`）：
未知 opcode 被静默跳过，工程照常载入、脚本照常往下跑、`未映射` 计数仍为 0，
只是那一步没有结果。玩家看到的是「这块积木好像没生效」，而不是一条错误。

将来若要兼容旧工程，正确做法是**载入期迁移**：在 `__tmcLoadProject` 解析 JSON 后、
交给 VM 之前，把旧 opcode 改写为新 opcode 并补默认字段（`posX` → `pos` + `AXIS:"x"`）。
不要走「把 15 条登记加回来」这条路——那会让面板外永久保留一份平行实现。

其余规则不变：

- 新增积木必须同时改三处：`blocks.data.js` 声明、`Sb3Mapping` 映射、`Blocks.java` 登记。
  名字与顺序的一致性由 `tools/build-mapping-report.js` 在构建期校验（§6.1）。
- 两份扩展产物由 `tools/build-extension.js` 从 `turbowarp/src` 渲染。`processResources`
  依赖 `checkExtensionFreshness`（同一脚本的 `--check`），改了源忘记重新生成时，
  `./gradlew build` 与 `runClient` 都会直接失败，不会把旧扩展打进 jar——那种 jar 在游戏里
  表现为「扩展与模组不一致」，最容易被误判成代码 bug。该任务**不声明 `inputs/outputs`**：
  允许 up-to-date 等于让校验被跳过。CI 另有一道同名步骤，命令完全相同。
- 下拉的 `value` 必须是 ASCII 且与语言无关（工程里存的是 value），显示名走
  `ParamLabels` 的 `value.turbomc.field.*`。

当前是 15 块合成 5 块：`pos` / `aimPos` / `nearestEntityField` / `heldItemField` / `slotField`。

### 6.3 扩展产物与宿主对不上时，必须在 HUD 上说

两个方向的症状都很像「模组坏了」，而且都不报错：

- **扩展旧**（改了模组没重载扩展）：新积木在面板里根本不存在，用户以为功能没做；
- **模组旧**（换了扩展没换 jar）：面板里有积木，点下去未映射，静默返回 `null`。

`RhinoVm.init()` 把扩展 `getInfo().blocks` 的 opcode 清单交给
`ScriptBridge.checkExtensionCoverage`，与 `Sb3Mapping.extensionOpcodes()` 双向比对；
结论存进 `ExtensionHealth`，HUD 常驻一行红字，明细（缺哪些、多哪些）进日志。
`vmSmoke` 断言这句话为空，防止产物与映射表漂移着进构建。

> 为什么不用「版本号比对」：版本号一致不等于积木一致（改文案、删积木都不改版本），
> 而 opcode 集合是真正决定「积木能不能用」的那份数据。

### 6.4 未知扩展前缀要在载入前兜住，不能整包报废

scratch-vm 的 `installTargets` **不读**工程里的 `extensions` 数组，它逐个积木调
`getExtensionIdForOpcode(opcode)` 反推扩展 id，再对「未加载」的 id 调
`loadExtensionURL()`。Rhino 里没有 `Worker`，那条路必然以
`ReferenceError: "Worker" is not defined` 结束，**整个工程载入失败**——玩家看到的是
「✕ 工程载入失败」，而不是「某个积木不生效」。

因此 `__tmcLoadProject` 在交给 VM 之前接管 `extensionManager.loadExtensionURL`：
未知 id 直接登记一个空占位实例（`isExtensionLoaded` 为真）并返回已完成的 Promise，
日志点名是哪个扩展。核心前缀（`operator` / `control` …）在更早的
`getExtensionIdForOpcode` 阶段就返回 null，根本走不到这里，所以**不需要维护前缀白名单**
——漏项会误判，后果比兜底更严重。

代价写在明处：未知扩展的积木执行时返回空值（与 KN-05 的占位语义一致）。
`logic_*` 这类 Blockly 遗留 opcode 是最常见的触发源（DEF-10）。

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

视角归属分四档：`WALK`（寻路）/ `TURN`（平滑转向）/ `MANUAL`（「看向 / 看向坐标」积木直接写值）/
`NONE`。转移规则只有两条：**`TURN` 谁也抢不走**（`TurnSession.isActive()` 时 `PathFollower` 干脆不登记
走路目标）；**其余状态都会被 `setWalkTarget` 降级成 `WALK`，包括 `MANUAL`**。

`MANUAL` 必须能被走向接管，这不是可选项：帧层在 `MANUAL` 里只把「上一帧写下的 yaw」对齐就返回，
不再写角度。若走向还在跑却抢不回视角，`PathFollower` 每 tick 算出的角度差恒大于
`standTurnDeg`（默认 30°），于是永远松着前进键——角色站在原地直到走向会话超时（按距离算最长
1200 tick）。类注释里的「保持到下一次走向或转向开始」就是这个语义；只排除 `TURN` 才是对的。
代价是「看向」在走向期间保持不住：下一 tick 视角就平滑转回路径方向。

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

**兜底路径要给提示**：预热没跑成（标题界面时总开关是关的、或预热抛了异常）时，`start()` 会在
进世界的第一帧再付一次约 1.1 秒的 `vm.init`，没有提示就是「卡死」。所以 `vm.init()` 之前会发一条
动作栏消息（`message.turbomc.vm_warming`）。把 `preload` 挪到后台线程是另一件事：得先验证
scratch-vm 的构造与 Rhino `Context` 的线程归属，没验证之前不要动初始化时机。

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

**保留最近 32 条（环形缓冲）**：`ChatState` 已从「只存最后一条」扩为固定容量的环形缓冲
（{@code ChatState.CAPACITY = 32}）。脚本正在等待（寻路、AI 请求）时连来几条，前几条不会被
覆盖；超过 32 条才丢弃最旧的，且容量**不随参数放大**——无界队列会在长时间挂机时吃光内存。
读写分别在网络线程与脚本线程，所有访问都在锁内，读出去的是一份拷贝。

两条读出口：「最近的聊天」（`query.last_chat`）只给最新一句；「最近 [N] 条聊天」
（`query.chat_history`）给**旧的在前**的字符串数组，可直接拼成对话上下文。

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

**线程归属：两个回调都在客户端主线程**，与 `vm.tick` 天然串行，`_pushThread` 前的 busy 检查
不存在并发问题。依据是 1.21.11 的 `ClientPlayNetworkHandler.onChatMessage` / `onGameMessage`
第一句就调 `NetworkThreadUtils.forceMainThread(packet, this, client.getPacketApplyBatcher())`：
不在 batcher 线程时把包塞进队列并抛 `OffThreadException`，包随后在 `MinecraftClient.tick()`
的 `packetApplyBatcher.apply()` 里执行——而 batcher 是用 `MinecraftClient.thread`（渲染线程）
构造的。Fabric 的 `CHAT` / `GAME` 事件就在这条链路的 `ChatListener.showMessageToPlayer` /
`handleSystemMessage` 里同步触发。

**真正在 Netty 上的是 `DISCONNECT`**（`ClientPlayConnectionEvents`），所以它的收尾必须
`client.execute(...)` 投递回主线程——见 `TurboMCClient.onInitializeClient` 里的注册。

两个聊天监听器入口各留了一次 `isOnThread()` 断言：将来上游改了这条路径、或别的模组手工
重放事件，日志会先露头，而不是变成「帽子偶尔丢一次」这种查不出来的现象。
曾经有一份审计把聊天回调判成网络线程，进而建议改成「入队 + 主线程派发」——那会白白延后
一个 tick 并引入丢消息，不要照着做。

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
- 聚合逐会话加锁并拷贝，不做长临界区：调用方是渲染线程（HUD 每 250 ms 采样一次，面板按需刷新）。

**`ask()` 只允许在主线程调用**（`AiActions` 在 `vm.tick` 里同步调）。并发上限读的是全局
`inFlight.size()`，而「检查 → 构造请求 → `sendAsync` → 占位」不是原子操作；它现在成立，
靠的就是单线程串行。**刻意不加锁**：lock 方案要在五个失败分支（未配置 / 会话忙 / 并发满 /
构造失败 / 提交失败）上各释放一次占位，漏一个就是「AI 永久报并发已满」，比这个当前不可达的
竞态更难查。入口留了一次「构造线程比对」，越线程调用会在日志里露头。

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

`ScriptEngine.tick()` **第一行**推一次队列，与任何分支无关；停引擎、切包、停止全部都必须
`clearChatQueue()`，否则上一份工程的长回复会在新工程里继续冒出来。

**为什么必须放在第一行**：队列的间隔靠 `tick()` 递减冷却。早先它只在 `vm.tick()` 之后
调用，而「引擎开关关闭」「总开关关闭」「远程会话让位」三条分支都在之前 `return`，
于是第一条消息发出后冷却永远不减，后续全部卡在队列里——症状是「说了一次就再也不说」，
日志里一句异常也没有。

这顺带修掉一个旧行为：以前同帧的第二次「在聊天栏说」会被 50 ms 节流**静默丢弃**，
现在全部入队，一条不丢。

`执行命令` **同样要清洗，但不能套用聊天的截断**。这两条容易搞反，逐条记下实测结论：

- **会断开**。`ServerPlayNetworkHandler.onCommandExecution` 第一句是 `validateMessage`，它先跑
  `hasIllegalCharacter`（判定式 `c != '§' && c >= ' ' && c != 0x7F`），命中直接
  `disconnect("multiplayer.disconnect.illegal_characters")`。所以命令里带换行、`§`、控制字符
  一样会把玩家踢掉——「命令只是报未知命令」是错的。`GameActions.command()` 因此也走
  `ChatText.clean`，但**只在检测到非法字符时**才清洗（`clean` 会把连续空格折成一个，
  而命令的 NBT / JSON 参数里可能有合法的双空格）。
- **长度不判**。命令包用 `PacketByteBuf.readString()` / `writeString()`，上限是 **32767**
  （不是 32500，也不是聊天的 256——256 只属于聊天包 `readUtf(256)` 与原版输入框）。
  截断后的命令会变成另一条命令（`/give` 的物品名被切一半），比原样发出去更危险，
  所以这里既不截断也不丢弃，长命令交给服务端与代理自行裁决。

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

- 解析与序列化在 **JS 侧**（`turbowarp/src/json-lite.js`），与快照用的 `JsonText` 无关。
  **解析器是自己写的**递归下降：Gson 的 `JsonParser` 入口会把
  `JsonReader` 临时切成宽松模式，`{a:1}` 会被判成合法，与浏览器的 `JSON.parse` 分叉；
  数值序列化沿用 `JSON.stringify` 的 ECMAScript 规则，否则 `1.0` 写回成 `"1.0"`，
  字符串比较会莫名不成立。`JsonText.java` 仍保留，但只服务快照（§18）——
  快照在 Java 侧拼 JSON，和积木语义无关。
- **单一实现**：这批积木只有 `json-lite.js` 一份源码。浏览器直接内联，且**构建期就生成直调**
  （`__tmcJson`），运行期不经过 `__tmc`——因此未连接游戏时也算真实结果；游戏内由
  `build-extension.js` 原样复制为 `json-core.es5.js`，`vm-bootstrap.js` 装积木时
  直接挂本地实现，热路径上不经过桥。之所以写 ES5：两个宿主之一是 Rhino 1.7.15
  （`optimizationLevel=-1`），它不支持 `class`，同一函数内重复 `const` 还会直接报错；
  浏览器跑 ES5 没有副作用。代价是 Rhino 解释执行比原 Java 慢（逐元素操作尤其明显），
  这是明确接受的取舍——换来单份实现、单份用例，不会再分叉。
- 列表互通的两块（`jsonVmGetList` / `jsonVmSetList`）读的是宿主 VM 的列表容器：
  浏览器侧在 `remote-client.js`，游戏内侧在 `vm-bootstrap.js`；两边都只做
  「列表项 → JSON 文本」的标量化转换，不承载积木语义。
- **面板瘦身**（原 32 块 → 29 块）：「键数量」与「数组长度」合成一块「长度」（本就是同一实现）；
  「是合法 JSON 吗」与「是 [对象/数组] 吗」合成三选一菜单；数组下标统一为 1 基后，
  「数组第 n 项」与「按键取值」不再需要两块——**下标基准不一致比少一块积木危险得多**。
- 与原文的偏差只有三处：解析错误返回中文说明带位置、「长度」解析失败返回空串
  （原文是一个空格，明显笔误）、嵌套深度上限 256（防御递归栈）。
- **行为回归靠 golden**：`tools/json-parity-cases.json` 的 134 条用例，基线是迁移前
  由 Java 实现产出的 `tools/json-parity-golden.json`。`JsonCoreParityTest` 在内嵌
  Rhino 1.7.15 里跑**运行时产物本身**并与基线逐字比对，`tools/check-json-parity.js`
  在 V8 里跑同一份源码做同样的比对；两条链路都必须绿。只有明确要改语义时才用
  `node tools/check-json-parity.js --write` 重生成基线——那是一次有意识的决定，
  不应顺手做。
- **覆盖口径**：29 块里 27 块有直接用例（`jsonVmGetList` / `jsonVmSetList` 读写 VM 自己的列表，
  走 `vmSmoke -PvmMode=json` 端到端，不进 cases）。每块至少一组正向 + 一组非法输入；
  「非法」统一指容器参数不是合法 JSON、或类型不是数组/对象，期望值为空串。
  两个例外：「分析」(`jsonArrayAnalysis`) 对非数组输入返回 `0`（见 BACKLOG JSON-06）；
  「按分隔符拆」(`jsonArrayCreate`) 入参只有分隔符与任意文本，没有非法分支，所以不设反例。

### 24. 扩展的两份产物是生成物，唯一源在 `turbowarp/src`

`turbowarp/turbomc-extension.js`（编辑器副本）与
`assets/turbomc/vm/turbomc-extension.es5.js`（运行时副本）都由
`node tools/build-extension.js` 渲染，数据源是：

- `turbowarp/src/blocks.data.js` —— 97 块积木、16 个菜单、15 个分组标题、每块的占位方法体；
- `turbowarp/src/items.data.js` —— 1505 条物品（`注册名|中文名|英文名`）。

同一脚本还负责 `json-lite.js` 的运行时副本：源码本身就是 ES5，原样写为
`assets/turbomc/vm/json-core.es5.js`（独立文件、先于 `vm-bootstrap.js` 加载）。
`--check` 同时校验这份副本与源码逐字一致。

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

### 25. 配置只在加载后补齐缺失段，不做逐字段迁移

`TurboMCConfig` 的 `hudCorner` 字段已删（HUD 位置只用 `hudOffsetX` / `hudOffsetY` 两个百分比，
`HudCorner` 枚举一并移除）。旧 json 里的 `hudCorner` 会被 Gson 忽略，下次保存时自然消失。

Gson 用 Unsafe 建实例、**不执行字段初始化器**，所以 json 缺 `ai` / `pathing` / `blockedKeys`
时这几个字段是 `null`。处理方式收敛在 `normalizeSections()`：加载后补一次，
而不是让二十多个 getter 各写一遍 null 判断。

段内字段仍用包装类型——缺字段与「配成 0」必须能区分（`accuracy = 0`、`temperature = 0`
都是合法值，用原始类型会把两者混成一件事）。

### 26. 快照类积木是单向只读出口

> 状态：**批次 1–3 已上线并实机验证**（2026-11-19）。九块快照全部通过验收：玩家 / 附近实体列表 /
> 方块 / 背包 / 界面 / 世界 / 在线玩家 / 区域扫描 / 聊天历史，另有「当打开 [界面]」帽子。
> 验收中发现并修正三处：玩家/创造背包 `getType()` 抛异常、NBT 混入重复键、1.21.11 的 NBT 键名为
> snake_case。剩余事件类积木见 `BACKLOG.md` SNAP-04。

「快照」指把游戏状态读成 JSON 文本的一组积木（玩家 / 附近实体列表 / 方块 / 背包 / 界面 / 世界），
用来补上 29 块 JSON 积木缺的输入源——那批积木是纯计算，游戏数据此前只能一块一个标量地搬。

**方向固定为只读，五条约束：**

1. **只出不进**。快照的返回值是普通字符串，与 JSON 积木的输出同质；脚本对它做 `json.set_path`
   只改字符串本身，不产生任何回写。改变世界仍只能用既有动作积木。
2. **不提供「应用快照」**。将来若要写回，必须是独立的 `action.*` 块并单独评审——不允许某块积木
   「把 JSON 结果悄悄套用到玩家 / 实体 / 背包」。否则 JSON 会变成隐式状态源，
   而它今天的价值正是「纯值、可单测、可复制」。
3. **不用 `json.*` 前缀**。快照依赖 `MinecraftClient`，进不了纯 JVM 单测；`json.*` 的前提是
   「纯计算、不碰游戏状态、可脱离客户端测试」。opcode 归 `world.*`。
4. **拼装逻辑要能脱离客户端测**。取值仍在 `WorldQueries` 里读 `MinecraftClient`，
   但「原语 → JSON 文本」抽成只收 primitives 的纯函数（`Snapshots`），让字段名、顺序、数值格式有单测
   （同 `EntityFilter` 把解析与判定拆开的手法）。
5. **代价**：读写要在脚本里显式展开成两步；换来的是没有隐式副作用与「快照是否还持有对象引用」
   的生命周期问题。这是取舍，不是遗漏。

已实现的九个 opcode：`world.player_json`、`world.block_json`（x / y / z）、
`world.entities_json`（类型 / 半径 / 上限）、`world.inventory_json`、`world.screen_json`、
`world.world_json`、`world.players_json`、`world.scan_json`（两角 + 上限）、
`query.chat_history`（条数）。三条形状约定：

- **固定字段扁平**（`x` / `motion_x` / `health`），只有随游戏变化的开放结构才做子对象
  （方块状态的 `state`、容器内容的 `block_entity`）。理由是可组合：`json.array_map_field`
  只能取顶层键，嵌套的 `pos.x` 在数组上没法批量映射。
- **列表一律返回裸数组**，不包 `{count, list}`：包一层就接不上 `json.length` /
  `json.array_map_field` / 把列表设为。真实总数由旁边的计数积木或 `slots` 字段给。
- **缺数据给空容器不给空串**：无玩家/无世界时返回 `{}` 或 `[]`，
  空串会让下游 JSON 积木直接解析失败。

**键名的可见性由三处守卫**：键只存在于代码里，用户在编辑器看不见，所以
`Snapshots` 里为每种快照声明了键清单常量（`PLAYER_KEYS` 等），并配三处对齐：

1. **写入顺序**——`SnapshotJsonTest` 断言输出 JSON 的键顺序等于清单；
2. **编辑器骨架**——`SnapshotKeysDocTest` 解析 `blocks.data.js` 的 stub，断言键集合一致
   （浏览器里运行一次就能看到真实键名）；
3. **文档表格**——同一测试解析 `turbowarp/README.md` 的「键：」行，逐项比对。

另有一块 `world.snapshot_keys`（「[快照] 的字段」）在运行时直接返回清单，
作者接「说」即可看全，不必翻文档。改键时上述三处会一起失败，这是刻意的。

**背包与界面是两块，编号体系不同，不能混用**：

- `world.inventory_json` 固定用 inventory 索引（0–35 主背包、36–39 盔甲、40 副手），
  只列非空槽位。不能用 `currentScreenHandler` 取：玩家格在界面里的编号会随打开的容器整体后移
  （大箱子里是 27–62），拿它当背包编号会随场景漂移。
- `world.screen_json` 固定用 `ScreenHandler` 槽位号，与「点击槽位」同源，可直接拿去点；
  `type` 取菜单注册名，**玩家背包固定给 `minecraft:player`**——`PlayerScreenHandler` 的类型是
  `null`，`getType()` 会抛 `UnsupportedOperationException`（原版设计，不是异常状态），实机踩到过；
  每项带 `player_slot`，用来区分自己的背包与容器。没有界面时给 `{}`——
  此时 `currentScreenHandler` 仍指向玩家自己的 46 格合成界面，照搬会让脚本以为有东西可点。

**世界快照只给八个稳定字段**：`time` / `day` / `moon_phase` / `raining` / `thundering` /
`dimension` / `biome` / `light`。难度、服务器品牌在客户端没有可靠来源（品牌在 1.21.11 的
客户端处理器上不暴露，难度只有单人模式能从服务端读），TPS 只能靠帧间隔估算——
三者都不给，避免脚本依赖看起来像真值的假数据。

**区域扫描只给非空气方块，且有两道闸**：体积上限 {@code SCAN_VOLUME_LIMIT = 8192} 格
（{@code getBlockState} 在主线程，超出部分不扫）、条数上限由 {@code LIMIT} 参数给
（默认 4096，钳 1–16384）。任一触发都置 {@code truncated = true}——它表示「结果不全」，
不是「扫完了没有方块」。遍历顺序 y → x → z，截断先丢高层，所以竖向建筑应分段扫。
同一 tick 内重复调用只扫一次（缓存键含范围与上限）。

**在线玩家快照只有四项**：`uuid` / `name` / `latency` / `gamemode`。其他玩家的坐标、血量、
背包由服务端下发，客户端拿不到——要判断「附近有谁」，用实体列表（`TYPE = 玩家`），
不要指望这份名单。顺序就是 tab 列表顺序，不做重排。

**聊天历史是环形缓冲的读出口**，不是新的存储：容量固定 32 条（见 §19），积木只决定取几条，
`COUNT` 超过 32 会被钳到 32，不能靠参数绕过容量。

方块的 `block_entity` 只取**标量与结构化内容**：箱子一类走 `items`（最多 64 条，
`items_total` 给出真实槽位数），告示牌走 `text_front` / `text_back`，
其余标量 NBT 放 `nbt`（上限 32 键，剔除与顶层重复的 `x`/`y`/`z`/`id`）。

**熔炉进度拿不到**（实机验证，2026-11-19）：1.21.11 的 NBT 键名已改为 snake_case
（`cooking_time_spent` / `cooking_total_time` / `lit_time_remaining` / `lit_total_time`），
但客户端方块实体的这四个值**恒为 0**，即使 `state.lit = true`——方块实体更新不会同步进度。
要精确进度只能读打开界面后的 `ScreenHandler` 属性，本版未提供，因此不要在脚本里做
「等到烧完」这类判断，改为「等待固定时长后取输出槽」。

### 27. 失焦暂停只在两种条件下被接管

原版在窗口失去焦点时会弹暂停菜单、把单人世界冻住。开发脚本时经常要切到浏览器或编辑器，
一失焦世界就静止：坐标不再变化、`TriggerDispatcher` 的按键派发被 GUI 挡住（它要求
`currentScreen == null`）、动作积木却仍返回成功——看起来像积木坏了。

`TurboMCClient.guardFocusPause()` 每 tick 判断 `wanted = 处于 REMOTE || 配置项
keepRunningWhenUnfocused`，成立时：

1. 把 `GameOptions.pauseOnLostFocus` 置假；
2. 若窗口失焦且 `currentScreen instanceof GameMenuScreen`，`setScreen(null)` 关掉它。

三个边界不能动：

- **只认「失焦 + 暂停界面」这一种组合**。玩家在窗口内按 ESC 的暂停照旧，否则按 ESC 关不掉菜单。
- **不写持久化设置**，只改内存里的 `options`；`wanted` 转假时写回备份值。
- **单字段备份就是引用计数**：只有一个 `pauseOnLostFocusBackup`，两个触发条件并存时不会互相覆盖，
  都撤掉才恢复——否则「远程会话结束」会把用户打开的配置项一起撤掉。

配置项默认关：它改的是原版行为，不该在玩家没要求时生效。

## 六、HUD
HUD 的第一行是引擎状态，由 `ScriptEngine.Status` 单一来源决定，三处界面共用
（HUD / 包列表 / 运行面板），不各自判断 `isRunning()`：

| 状态 | 前缀 | 颜色 | 触发 |
|---|---|---|---|
| `RUNNING` | `●` | 绿 | 工程已载入并在推进 |
| `OFF` / `NO_PACK` / `NO_WORLD` / `OFF_WORLD_EXIT` | `○` | 灰 | 没开、没选包、还没进世界、退出世界自动关 |
| `HALTED` | `○` | 黄 | 脚本执行了「停止全部」，按**一次** F6 即重开 |
| `REMOTE` | `●` | 青 | 浏览器远程会话进行中，内 VM 让位（按 F6 会提示先断开） |
| `LOAD_FAILED` / `RUNTIME_ERROR` | `✕` | 红 | 工程读不出/启动失败、运行期异常 |

状态之后跟具体原因（异常摘要，截断 60 字），故障时单独占一行：挤在状态行里会被宽度
裁掉，等于没显示。这是「为什么脚本不动」在游戏内唯一的自查出口。
唯一例外是 `REMOTE`：它的 detail 只有「实例 · 端口」，`hudLine()` 已拼进状态行，
不再单列——否则同一句话会打两遍。

**远程桥端口常驻一行**：只要监听中，HUD 就显示 `○ 远程调试监听 · 端口 N（未连接）`，
连上后变青色 `● 远程调试已连接 · 端口 N`。端口是扩展面板唯一要手填的参数，
只写在 `logs/latest.log` 里等于每次都要去翻文件；会话中（`REMOTE`）状态行已带端口，不重复。

**`NO_WORLD` 只在标题界面成立**：人已经站在世界里、只是引擎关着时，必须显示
`OFF`。早先关闭分支只在 `running / RUNNING / HALTED` 时收尾，`NO_WORLD` 会永久残留，
表现是「人在世界里，HUD 说还没进入世界」，按一次 F6 才纠正（DEF-01）。
判据收在 `Status.staleWhenEngineOff()` 里，只认 `NO_WORLD` 与 `OFF_WORLD_EXIT`——
`LOAD_FAILED` / `RUNTIME_ERROR` 必须保留，改成「已关闭」等于抹掉唯一的诊断信息。

**故障状态不被覆盖**：`stop(status, detail)` 内部拆成 `teardown()` + `setStatus()`，
`teardown()` 只收尾（会话、按键表、聊天队列、AI 请求），不改状态。否则「载入失败」
会在下一 tick 被 `!healthy` 分支改写成普通的「已停止」，原因丢掉。

**VM 不反向依赖引擎**：`RhinoVm` 在 `catch (RuntimeException)` 里只把摘要存进
`fatalError`，由 `ScriptEngine.tick()` 用 `takeFatalError()` 拉取——结构与
`takeProjectStops()` 一致。让 VM 直接调引擎会形成双向依赖。

状态行之下是脚本的「说 / 思考」气泡；寻路进行时再追加两行（目标 / 距离 / 节点进度 /
规划耗时），受 `showPathHud` 控制。

气泡同时显示几条由 `hudMaxBubbles` 决定（默认 3，范围 1～6）。选取与过期都收在
`SpeechBubbleBook` 里：到期时把 `usageId` 记进 `retired`，否则 VM 会一直上报同一句，
下一帧又被当成新气泡重新登记，形成「删掉即复活」（DEF-06）。被上限挤掉的不静默丢弃，
末尾补一行「…还有 N 条」——同一 tick 内多个角色说话时，最早登记的那个必然被挤出
（时间戳相同，稳定排序按登记顺序），没有这行提示，玩家会以为它没开口。

**HUD 在渲染帧上跑，只缓存「不变的东西」**：气泡 JSON 与列表解析结果按原始字符串比较后
缓存（VM 每帧返回同一份 JSON 是常态），AI 快照 250 ms 采一次，寻路两行按内容变化才重建 `Text`。
**监视器的数值仍然每帧读 VM**——`vm.getVariable` 是这条路径上最贵的一次反射，
但缓存它会让数字滞后，调脚本时看着「不跟手」，所以宁可留着。
判断要不要优化的依据是 `hud.read` 的 PerfProbe 窗口（`avg` 长期为 0，`max` 才是有意义的那一列）。

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

## 七、远程开发桥

> 状态：**B1–B3 已落地并实机联调通过**（2026-11-22）。仅剩一项人工闸门：
> V8/Rhino 双引擎回归（`BACKLOG.md` ENG-07）。

**定位**：让 TurboWarp 浏览器里的积木直接遥控正在运行的游戏。开发时工程在浏览器里跑，
游戏只当「执行器 + 状态源」；发布仍走原来的「导出 sb3 → `imports/` → 热重载」，两条路互不影响。

```
TurboWarp（主 VM）
  ├ 积木读   ← 快照缓存（每 tick 一帧，零往返）
  ├ 积木写   → RPC，主线程 tick 边界按 FIFO 执行
  ├ 事件帽子 ← 游戏侧边沿检测 → event → 浏览器起线程
  └ 画面     ← getDisplayMedia（系统合成器直供，不经游戏、不进 socket）
```

**为什么读要走快照**：Scratch 的 reporter 是同步求值。若每个 reporter 都往返一次，
20Hz 快照下每次读要等一个 tick，一段 `重复 10 次 { 说 (X 坐标) }` 就会慢到跑不动。
快照把「读」变成本地缓存，是这套方案成立的前提。

### 7.1 进程与生命周期

| 时机 | 动作 |
|---|---|
| 握手成功 | `suspendForRemote()` → `Status.REMOTE`，HUD 青蓝「远程调试中：实例 · 端口」 |
| 会话期间 | `ScriptEngine.tick()` 命中 `REMOTE` 直接 return，**不再被 `if (!running) start()` 拉起** |
| 断开 / 心跳超时 / 被新连接接管 | `endRemoteSession()` → `Status.OFF`，并把 `scriptEngineEnabled` 写回 `false` |

- **让位复用 `stop()`**，不新造暂停态。代价是 `teardown()` 会松开按键、中止寻路与转向、
  清空监视器与画框、**取消进行中的 AI 会话**——最后一条与原「AI 收手不取消会话」的决议相反，
  是刻意改的：内 VM 已停用，回复没有脚本能接收，留着跑只浪费连接与 token。
- **断开后一律关闭，不恢复会话前状态**。否则断开下一帧 `start()` 就把脚本拉起来，
  开发者刚在浏览器里调完，游戏里突然开跑，是最坏的意外。
- **`REMOTE` 期间按 F6 只提示不翻转**——翻了开关但内 VM 不动，HUD 会与浏览器侧对不上。
- **不碰 `ScriptBridge` 的静态字段**（`current` / `pendingWait` / `pendingCheck`）。远程路径直接
  `NativeRegistry` + 每 tick 新建的 `ScriptContext`，静态状态冲突从设计上消失。

每 tick 的调用顺序（`TurboMCClient.onClientTick`）：

```
ScriptEngine.tick      → 内 VM（REMOTE 时直接 return）
guardFocusPause        → 压住失焦暂停（见 §27）
TriggerDispatcher.tick → 按键/界面/聊天边沿，REMOTE 时转发给浏览器
RemoteBridge.tick      → 先执行队列，再采集快照
```

快照必须排在最后：本 tick 的界面、血量、坐标都已落定；同时它也承担浏览器侧的时钟。

### 7.2 协议

一条连接纯文本 JSON，没有二进制帧、没有 Token。`Origin` 只在 WebSocket 握手头校验。

```
C→S {"t":"hello","v":1,"client":"turbowarp"}
S→C {"t":"welcome","v":1,"build":"1.6.0-beta","instance":"run","world":"...","port":35671,
     "tickRate":20,"snapshotHz":20,"schema":6,"maxCallsPerTick":64,"queue":1024,
     "opcodes":[...],"hats":[...],"snapshotKeys":{...}}

C→S {"t":"call","id":"41","op":"turbomc_key","argv":["w","true"]}
S→C {"t":"ret","id":"41","ok":true,"v":null,"wait":0,"check":"","limit":0}

C→S {"t":"poll","id":"42","key":"move"}
S→C {"t":"ret","id":"42","ok":true,"done":false}

C→S {"t":"cancel","id":"42"} | {"t":"stop","reason":"user"} | {"t":"ping"}
S→C {"t":"stopped"} | {"t":"pong"} | {"t":"evicted"} | {"t":"snap",...} | {"t":"event",...}
```

- **`argv` 是有序数组，不是对象**。动作层按位置读（`GameActions.key` 里是 `args[0]`），
  对象没有顺序，按原设计的 `args: {KEY: ...}` 实现会静默取到 `undefined`。
- **枚举参数用菜单的 `value`，不是显示文本**。`turbomc_key` 的「按下/松开」实际是字符串布尔
  `"true"` / `"false"`；传 `"up"` 会经 `Cast.toBoolean` 判为**真**（非空字符串），表现为「再按一次」。
- **`wait` / `check` / `limit` 是三元组**，对应 `ScriptContext` 的三个 `takeWaitXxx`。
  缺 `limit` 时「等条件但最多等 N 帧」会退化成无限等。
- **`welcome` 下发服务端自己的参数**（`snapshotHz` / `maxCallsPerTick` / `queue` / `schema`），
  客户端不硬编码，换版本不会对不上。
- **`instance` / `world` 读的是主线程缓存**（`RemoteBridge.cachedInstance` / `cachedWorld`，每 tick 刷新）。
  握手在 WebSocket I/O 线程上，直接读 `client.world` 会撞上主线程切维度，欢迎包里就可能出现旧维度或空串。
- **错误带 `code`**：`unmapped`（opcode 未映射）、`rate`（限流）、`budget`（超预算）、
  `notfound`（动作层未实现）、`internal`（异常）。只有 `rate` 值得重试，靠字符串区分不出来。
- `id` 由客户端单调递增，重连后归零。

### 7.3 执行模型

| 项 | 取值 | 理由 |
|---|---|---|
| 队列 | FIFO，容量 1024，满了丢**最旧** | 旧的通常是断线前堆积的过期动作 |
| `call` 与 `poll` | **共用一条队列** | 浏览器先发调用、拿到 `check` 再轮询，两条消息的先后有意义 |
| 每 tick 预算 | 8ms + 64 条，超出回 `rate` | 防脚本死循环刷动作卡帧；不静默丢弃 |
| 异常隔离 | 单条失败回 `internal`，同批其他照跑 | 与 `ScriptBridge.call` 既有口径一致 |
| 时钟 | **快照 `tick` 号**，不是 `setTimeout` | `setTimeout(50)` 实际 52–56ms，跑久累积漂移；且 poll 会掉到 20Hz 以下 |

阻塞积木在浏览器侧返回 Promise：`wait: n` 等快照 tick 前进 n；`check` 非空时由每帧快照触发一次
`poll`。快照 3 秒不来（心跳超时）由收手路径统一 resolve，不留永挂的线程。

**`ScriptContext` 每 tick 重建**，与 `RhinoVm.tick` 同口径；并且必须
`setBreakProbe(WorldQueries::probeBreak)`——不注入的话「等待挖破」会立刻通过，看起来没报错、
实际一秒都没等。

### 7.4 快照

每 tick 一帧（20Hz），全量推，不做订阅化。**`SNAPSHOT_SCHEMA` 加/改字段必须 +1**：
字段只增不减，旧客户端缺字段不报错、只让积木恒为假/空——那种症状与「积木坏了」无法区分。
扩展比对不一致时在面板直接写「客户端快照版本旧」，不再靠猜。

| 组 | 字段 |
|---|---|
| 时钟与状态 | `tick` / `schema` / `inWorld` / `paused` |
| 玩家扁平 | `x` `y` `z` / `yaw` `pitch` / `motion_*` / `hp` `maxHp` `absorption` / `food` `saturation` / `air` `xpLevel` `xpProgress` `armor` / `dim` `slot` |
| 结构化子对象 | `hand` / `aim` / `below` / `mining` / `mouse` / `me` / `worldInfo` / `players` / `inv` / `ui` / `chat` / `ai` / `ents` / `hostile` |
| 环境 | `onGround` / `isDay` / `inWater` / `inLava` / `biome` / `ping` |

**拼装只有一处**：`Snapshots` 里返回 `JsonObject` 的方法同时服务两条路——游戏内动作
`stringify` 它，远程桥把它塞进快照。所以「浏览器里看到的背包 JSON」与「游戏内跑出来的」
是同一串，键序、字段名都不会分叉。这类地方最容易悄悄不一致，从源头消掉比事后对拍便宜。

**派生值不单列**：「前方有方块」读 `aim.has`，`any` 鼠标键由三键合并，都不多存一份——
快照是 20 次/秒的长期成本。`inv` 只列非空槽位，`ui` 最多 128 格，最坏约 40 KB/s（环回可忽略）。

**`ui` 只在当前界面带槽位（`ScreenHandlerProvider`）时才有值**。用 `currentScreenHandler` 顶替的话，
暂停菜单、聊天界面都会被当成 46 格背包，槽位编号整体错位。

**耐久百分比用 `round(remaining * 1000 / max) / 10`**，与游戏内同一算式。浮点除再乘 100
会差 0.1，「耐久 > 50」这类判断会偶发不成立——这类偏差最难查。

### 7.5 事件与帽子

`ScriptEngine.fire / fireScreen / fireKey / fireChat / fireServerChat` 各加一个 `REMOTE` 分支，
转发 `RemoteBridge.publishEvent()`。**同一处同时喂游戏内与浏览器**，不另写一套语义。

判定入口是 `acceptsTriggers() = active() || REMOTE`：触发源原本只看 `active()`，
而远程会话里内 VM 是停的，`active()` 恒假，帽子会全哑。

**匹配规则由服务端下发**（`welcome.hats`），因为两处各写一套必然漂移：

```json
{"ev":"screen_open","op":"turbomc_whenScreenOpen","match":"field:SCREEN","alsoAny":true}
{"ev":"chat","op":"turbomc_whenChat","match":"input:MATCH","contains":true}
```

客户端只需三种通用匹配器：无参数 / `field:名称`（带 `alsoAny` 时额外发一次 `any`）/
`input:名称` + `contains`（遍历 `targets` 读输入做包含判断）。改语义只改 Java 一处。

**硬约束：线程必须起在帽子的下一块，不能在帽子上。** `runtime.startHats` 内部先
`stepThread(thread)` 再 `goToNextBlock()`；扩展帽子没有实现体，执行时返回 `undefined`，
命中 `n || retireThread(t)` 当场退役——**帽子高亮、后面一块都不跑**。内置
`event_whenkeypressed` 是 edge-activated，走另一条分支，所以只有它「看起来正常」。
`pushHatsMatching` 因此自己遍历顶层块、匹配字段、`_pushThread(nextId, target)`。

**手动点击帽子是另一条路径**：`runtime.toggleScript` 把线程起在帽子上并执行帽子本身，
所以生成器必须给每个 hat 生成 `opcode() { return true; }`，否则点击同样退役。

**「触发我的聊天」不能读快照**：一条聊天进来后帽子线程下一帧才执行，中间任何一条消息
（别人说话、系统广告）都会改写全局槽，脚本读到的就是别人的话。所以入队时把消息挂到
**线程对象**（`thread.tmcChat`），积木方法因此需要 `util` 参数。

`teardown()` 会清空键表，而按键边沿检测要它——`suspendForRemote` / `abortForRemote` 之后
必须 `refreshRemoteContext()` 重新解析一遍（只读 sb3，不跑绿旗）。

### 7.6 读数覆盖

69 个 reporter/boolean 中 **66 个可用**：

| 类 | 数量 | 做法 |
|---|---|---|
| 快照类 | 约 36 | 直接读缓存，同步返回 |
| JSON 一族 | 27 | **同一份 ES5 实现**（`json-lite.js`），浏览器与游戏内共用；浏览器侧构建期直调，**未连接也算真实结果** |
| 聊天/AI/实体 | 其余 | 快照里的 `chat` / `ai` / `ents` 子对象 |
| `blockAt` / `blockJson` / `scanJson` | 3 | **不支持**：任意坐标查询无法预取，reporter 等不了 RPC |

JSON 一族是纯计算，浏览器与游戏内跑的是同一份源码；浏览器侧在 `build-extension.js` 里
被生成为直调（不经 `__tmc`），所以**是否连接游戏不影响结果**。语义靠**golden 回归**守，不靠自觉：

```bash
./gradlew test --tests "*JsonCoreParityTest*"  # Rhino 1.7.15 跑运行时产物，与 golden 逐字比对
node tools/check-json-parity.js                # V8 跑同一份源码，与 golden 逐字比对
node turbowarp/verify-extension.js             # 额外守一条：未连接时 JSON 必须算对
```

比对的是「积木最终显示出来的字符串」，所以 `1.0` vs `1` 这类数值格式差异也会被抓到。
当前 134 条用例与基线一致。

**涉及游戏世界的命令积木全部走 RPC**，没有本地实现——它们都有副作用，必须落在游戏主线程。
唯一例外是 `jsonVmSetList`：它写的是**当前 VM 自己的列表**，发到对面写没有意义，所以两侧都在本地执行。

**远程模式特有的三条限制**（游戏内引擎没有）：

| 项 | 限制 | 原因 |
|---|---|---|
| 附近实体 | 半径上限 **32 格**、最多 **64 个** | 快照只带这个盒范围内的实体；请求更大也拿不到 |
| 物品名 | 只认注册名，`stone` 自动补 `minecraft:`，**中文名不认** | 浏览器侧没有 `ItemIds` 那份 1505 条中文表 |
| 任意坐标查询 | 三个积木返回空 | 见上，同步求值等不了 RPC |

### 7.7 扩展侧结构

远程逻辑不写在模板字符串里（没法 lint、没法测、转义易错），而是真实源文件，构建时拼接：

| 文件 | 作用 | 进哪份产物 |
|---|---|---|
| `turbowarp/src/remote-client.js` | 连接面板、`__tmc`、快照缓存与 tick 时钟、帽子匹配器、Promise 包装 | 编辑器产物 |
| `turbowarp/src/json-lite.js` | 27 个 JSON 积木的唯一实现 | 编辑器内联 + 运行时 `json-core.es5.js` |
| `turbowarp/src/stage-view.js` | `getDisplayMedia` 捕获 + `<video>` 覆盖舞台 | 编辑器产物 |

面板是 `position:fixed` 的浮窗，会遮住积木区，所以标题栏可拖动：位置写进 `localStorage`
（与地址/端口同一个键），松手才落盘，双击标题栏回左下角。坐标与窗口尺寸变化时都钳在视口内——
标题栏是唯一的抓手，它跑出屏幕等于面板丢了。`renderPanel()` 只重建 `panelBody` 的子节点，
不碰 `root` 的位置，所以「连接状态变化触发重绘」不会把拖好的位置弹回去。
| `turbowarp/src/blocks.data.js` 等 | 积木定义与 stub（唯一数据源） | 两份 |

运行时副本（游戏内）**不含** `remote-client.js` 与 `stage-view.js`：那里由 `vm-bootstrap.js`
注入真实调用，没有远程逻辑。`json-lite.js` 是例外——它同样进运行时，但以独立文件
`json-core.es5.js` 先于 bootstrap 加载，因为 JSON 一族不走桥。
除 JSON 纯计算积木外，所有非 hat 积木的方法体统一生成成：

```js
opcode(args, util) { return this.__tmc('turbomc_x', [args.A, args.B], 'command', fallback, util); }
```

**未连接时执行 fallback**（即原来的预览行为），所以一份产物同时是「普通编辑器」和「远程调试器」。
`util` 只给需要当前线程的积木（`triggerChat`），其余不需要。

**JSON 一族是唯一例外**：`build-extension.js` 构建期执行 `json-lite.js` 取得 27 个键，
这些积木直接生成为 `__tmcJson('jsonGet', [args.json, args.item], false)`，运行期**不经过 `__tmc`**。
理由是它们不读写游戏状态：放进 `__tmc` 就会被「未连接 → fallback」短路成假值，
表现为「导入扩展后、连接游戏前 JSON 全返回空/0」——这条曾经真实发生过。
读写 VM 列表的两块（`jsonVmGetList` / `jsonVmSetList`）不在此列：它们需要 runtime 对象，
仍走 `__tmc` 的本地分支，但该分支已排在连接判断**之前**。

构建期还有一条硬断言：数据源里 `json*` 的纯计算积木集合必须与 `json-lite.js` 的 `blocks` 键
逐项一致，缺一边就构建失败。这比运行期查表可靠——查表失败只会静默返回空值。

### 7.8 画面

`getDisplayMedia` + `<video>` 覆盖舞台，**游戏端零代码**：画面由系统合成器直供浏览器，
不走 socket、不占带宽、不影响游戏帧率。原设计的「FBO 缩放 + JPEG + WS 二进制帧」需要
约 1000 行且全项目无离屏渲染先例，已否决——它的唯一正当场景是跨机与无人值守录制。

| 项 | 做法 |
|---|---|
| 舞台定位 | 优先 `runtime.renderer.canvas`，回退页面上面积最大的 canvas |
| 比例 | `object-fit: contain`（游戏 16:9 / 舞台默认 4:3），建议把舞台设 640×360 消黑边 |
| 切到舞台 | 只 `display:none`，**不停止捕获**（停了再开要重新选源） |
| 源被外部停止 | 监听 track 的 `ended` 收尾，不留一个黑掉的 video |

已知限制：游戏窗口**最小化会黑屏**（捕获跟随窗口可见性，与遮挡无关）；每次会话要选一次捕获源。

### 7.9 宿主、授权与安全

| 项 | 结论 |
|---|---|
| unsandboxed | **硬要求**。sandboxed 扩展跑在扩展 Worker 里，没有 `document`，权限请求直接失败 |
| LNA（本地网络访问） | https 页面连 `ws://127.0.0.1` 需一次 `loopback-network` 授权，且**首次必须由用户手势触发**（故「保存后自动连接」被禁止）；`file://` 与桌面自定义协议属同地址空间，不触发 |
| TurboWarp Desktop | RPC **可用**（`tw-editor://.`，p50 0.5ms）；画面 `NotSupportedError`（主进程无 display media handler） |
| Origin 白名单 | 默认只有 TurboWarp 官方站、桌面版与 `http://localhost:*` / `http://127.0.0.1:*`——**不含任何个人站点，也不含 `null`**（那是 `file://` 页面，等于「下载一个 HTML 双击打开就能接管游戏」，需要时手动加一行）。ModMenu「远程开发桥」下面的列表直接显示这份出厂值，可增删；整份清空后保存回到出厂值。**改动下一 tick 生效，不需要重开监听**：握手读的是主线程维护的快照（`allowedOrigins`），删掉某个来源还会断开它正在用的连接 |
| 绑定 | 只绑 `127.0.0.1`，不做跨机；单控制者，新连接接管时旧连接收 `evicted` |

**`permissions.query` 的状态 ≠ 是否被拦**。Desktop 的 `loopback-network` 报 `denied` 却仍能连——
LNA 在网络栈按地址空间判定，权限查询读的是另一套视图。实测比读文档可靠。

### 7.10 文件职责

| 文件 | 职责 |
|---|---|
| `script/remote/RemoteBridge.java` | 端口绑定与自动分配、Origin 校验、握手、心跳、单控制者、tick 入口 |
| `script/remote/RemoteProtocol.java` | 协议常量、错误码、`originAllowed()` |
| `script/remote/RemoteExecutor.java` | FIFO 队列、每 tick 预算、`call` / `poll` 执行与回包 |
| `script/remote/SnapshotPublisher.java` | 每 tick 采集快照（**采集留在主线程**，序列化与发送异步入队） |
| `script/ScriptEngine` | `Status.REMOTE`、`suspendForRemote` / `endRemoteSession`、事件转发分支 |
| `config/TurboMCConfig` | `dev` 段：开关、端口、实例名、Origin 白名单 |
| `TurboMCClient` | 每 tick 跟随配置起停监听、`guardFocusPause` |
| `turbowarp/src/remote-client.js` 等 | 扩展侧实现，见 §7.7 |

## 八、构建

```bash
# Loom 1.19 要求 JDK 25，系统默认 JDK 21 会在配置阶段失败
JAVA_HOME=~/.jdks/ms-25.0.5.0.2 ./gradlew build
```

产物：`build/libs/turbomc-<version>.jar`

### 冒烟测试（不启动客户端）

| 任务 | 作用 |
|---|---|
| `./gradlew vmSmoke` | 载入 project.json，验证顺序执行；并**全量断言**扩展积木的实现覆盖（见下） |
| `./gradlew vmSmoke -PvmMode=hat -PvmProject=hat-project.json` | 验证事件触发帽子积木 |
| `./gradlew vmSmoke -PvmMode=block -PvmProject=block-project.json` | 验证阻塞动作按时长挂起 |
| `./gradlew vmSmoke -PvmProject=merged-project.json` | 验证合并积木（一块 + 下拉）走真实的 sb3 → VM → 桥链路 |
| `./gradlew vmSmoke -PvmMode=wait -PvmProject=wait-mouse-project.json` | 验证「等待直到 <按下鼠标? >」在无输入时一直挂住（布尔归一化接线） |
| `./gradlew vmSmoke -PvmMode=json -PvmProject=json-project.json` | 验证 29 块 JSON 全在 JS 侧求值：桥调用必须为 0，且抽查结果非空 |
| `./gradlew vmImport` | 验证 sb3 能解析成树，并被 VM 执行 |
| `./gradlew vmChat` | 验证聊天帽子一条消息只触发一次、关键词能过滤、自己发的聊天不回声 |
| `./gradlew vmNumberFormat` | 验证数值积木的字符串化是 ECMAScript 格式（`20` 而非 `20.0`） |
| `./gradlew vmKeyHat` | 验证「当按下 [键]」按 `KEY_OPTION` 正确派发：对得上起 1 个线程、对不上起 0 个 |
| `./gradlew vmScreenHat` | 验证「当打开 [界面]」按 `SCREEN` 正确派发（`container` / `inventory` / `any` 命中数） |
| `./gradlew vmList` | 验证 HUD 能读到列表内容，以及算术积木的 `NUM1/NUM2` 映射成 `A/B`；`-PlistProject=路径` 可换成任意工程 |
| `./gradlew vmDepth` | 验证超长 `next` 链被深度保护截断，而不是打穿线程栈 |
| `./gradlew vmPreload -Pmode=preload` | 测量预热前后首次载入工程的耗时（`cold` 为对照） |
| `./gradlew exportSmoke` | 验证扩展释放：创建 / 内容一致不动 / 旧版覆盖 |
| `./gradlew vmAi` | AI 对话：本地 HTTP 桩（正常 / 非 2xx / 坏 JSON / 超时 / 会话互斥）+ VM 端到端 |

**夹具在 `src/test/resources/turbomc-vm/`，进版本控制**——CI 上只有这一份，
放在 `run/` 下会因被 gitignore 而在 CI 上消失。

**CI 闸门**：上表除 `vmPreload`（测量型，不该当闸门）外全部由 `.github/workflows/build.yml`
逐步执行，失败即红。曾经漏过的是 `vmKeyHat` / `vmScreenHat` / `vmList` / `vmDepth`
和 `vmSmoke -PvmMode=json`——它们注册在 gradle 里却没人调用，等于写了没跑；
新增冒烟任务时**必须同时把它加进 workflow**，否则只算本地脚本。

读取顺序：`-P` 参数指定的路径 → classpath 里的同名夹具。常用夹具：
`project.json`（顺序执行）、`hat-project.json`（聊天帽子）、`keyhat-project.json`（按键帽子）、
`list-project.json`（列表监视器）、`block-project.json`（条件等待）、`ask-project.json`（询问链路）、
`ai-project.json`（AI 对话：ask → reply → 写变量）、`fixture.sb3`（sb3 导入）。

> **`hat` 与 `block` 模式必须显式指定 `-PvmProject`**：默认的 `project.json`
> 里没有对应的帽子积木，`startHat` 会起 0 个线程——那是**假失败**，不是真问题。

### 覆盖断言（`vmSmoke` 内置，六种模式都跑）

`vmSmoke` 在进入模式分支之前，顺着 `Sb3Mapping.extensionOpcodes()` 把扩展的每个 opcode
往实现层查一遍，三类分开判：

| 类别 | 数量 | 断言 |
|---|---|---|
| JSON | 29 | **不许**有原生实现——有就说明双实现回来了（§23 单一实现契约） |
| 帽子 | 4 | 由宿主事件派发，不走 `NativeRegistry` |
| 其余 | 64 | 必须有 `Action` 或 `Function` |

合计 97 块，帽子之外 93 块。缺哪个就逐个打印 `名字（动作层未实现 类型）` 并以非零退出。
这条守的是「面板上有、拖得出来、点下去没反应」那类静默失效：夹具里只写了 4 个动作，
其余 60 块要么读世界状态（纯 JVM 必 NPE）、要么没有夹具，端到端跑不出来。

它替代了原先只硬编码 15 个 id 的抽查。**新增积木时不必再往名单里补一行**——
只要 `Sb3Mapping` 与 `NativeRegistry` 对得上就自动通过。

### 单元测试

`./gradlew test`（已挂在 `build` 上，CI 自动执行）。只放**不依赖 Minecraft 客户端**
的纯逻辑，覆盖最容易悄悄回归的几处：

| 测试类 | 守住什么 |
|---|---|
| `ScratchCastTest` | Scratch 弱类型转换规则（非数字串得 0、`" false "` 为真、数字转字符串按 ECMAScript） |
| `ChatGateTest` | 同一条聊天只触发一次：有签名按签名去重、无签名按正文 + 1 秒窗口 |
| `ChatStateTest` | 聊天环形缓冲：旧的在前、超 32 条环绕、条数钳在 1–容量、清空、`null` 转空串 |
| `ChatQueueTest` | 聊天队列：首段立即发、间隔准确、溢出丢弃、clear 生效、未装出口不崩 |
| `ChatTextTest` | 聊天清洗与切段：换行 / `§` / 控制字符、256 字符与 768 字节双上限、按标点切、不切代理对 |
| `SpeechBubbleBookTest` | 气泡登记：到期移除后不被重新登记（DEF-06）、被顶掉 / 说新句、上限与非法上限、clear |
| `TurnMathTest` | 转向步进的限幅 / 死区 / 最短路径，以及 NaN 输入得 0 |
| `FrameBoxTest` | 画框的坐标换算与颜色回退（写错不抛异常，只会框跑偏 / 变色） |
| `PathMathTest` | 走向的四段纯算术：到达判定（水平/垂直分开）、会话超时公式与钳制、卡住窗口的两个阈值、氧气余量 |
| `Sb3RenderingTest` | sb3 → 查看器积木树的三条还原规则（错了像面板 bug，其实是导入 bug） |
| `MergedBlockContractTest` | 合并积木三处登记齐全：扩展声明 / `Sb3Mapping` / `BlockRegistry`，缺一处都静默 |
| `ParamLabelsTest` | 参数本地化：拼出的语言键与语言文件里的键必须对得上 |
| `ProjectSanityTest` | 载入前校验：造型为空的工程给出「哪个角色没有造型」，正常工程必须放行（DEF-03） |
| `EngineStatusTest` | `ScriptEngine.Status` 语义：关闭时纠正过期现场（DEF-01）、停止后按一次 F6 就重开（DEF-02） |
| `EntityFilterTest` | 实体类型参数解析（`any` / `hostile` / `player` / 具体 id） |
| `ItemIdsTest` | 物品 id 规范化（`iron_ore`、`IRON_ORE`、带空格 → `minecraft:iron_ore`） |
| `JsonTextTest` | 快照仍在用的 JSON 解析 / 序列化（积木语义归 `json-lite.js`，见 `JsonCoreParityTest`） |
| `JsonCoreParityTest` | JSON 唯一实现的回归：运行时副本与源码逐字一致；在 Rhino 1.7.15 里跑 134 条用例并与迁移基线比对（见 §7.6） |
| `SnapshotJsonTest` | 快照 JSON 形状：键集合与 `*_KEYS` 逐项相等、键顺序、扁平结构、数值格式、转义、空数据给 `{}` / `[]` |
| `SnapshotKeysDocTest` | 键名三处一致：`Snapshots` 清单 ↔ `turbowarp/README.md` 键行 ↔ 编辑器 stub |

> 这张表要跟着测试类一起改：**列漏了就等于「没有」，下一轮盘点会照着它误判缺口**
> （2026-11-22 的 CI 缺口核查里，三项「无自动化」的误判就是只有 10 个类上榜导致的）。


涉及客户端的验证一律走冒烟任务——纯 JVM 里没有客户端，动作会抛 NPE 被桥吞成
`null`，测不到真实行为。

**远程桥的扩展侧回归**（不进 gradle，测的是生成产物，需要先 `node tools/build-extension.js`）：

```bash
node tools/probe/test-stop-hats.js      # 帽子派发 / 停止链路 / 读数 / 列表，57 项
node tools/probe/test-panel-render.js   # 面板 20Hz 刷新时控件不得被重建
node tools/check-json-parity.js         # V8 侧回归；golden 基线见 §7.6
```

## 九、使用流程

1. 在 TurboWarp 里加载 `turbowarp/turbomc-extension.js`（否则扩展积木显示为未知块）
2. 编写脚本，导出为 `.sb3`
3. 放进 `.minecraft/config/turbomc/imports/`（F7 界面里的「文件夹」可直接打开该目录）
4. 游戏内按 **F7** → 点「导入/更新」→ 点圆点启用
   - **开着 `autoReload` 时可跳过这一步**：导入与重启都会在 2 秒内自动发生，
     迭代时只需在编辑器里覆盖同一个文件
5. 按 **F6** 开引擎，HUD 出现 `● 运行中：包名` 即表示在跑
   - 总开关是持久化的：配置里 `scriptEngineEnabled` 为 `true` 时，进世界第一帧就会
     `start()` + `greenFlag()`，与有没有按 F6 无关
   - 退出存档/断开服务器时默认复位为 `false`（配置 `disableEngineOnExit`，字段缺失视为
     开），所以每局都要重新按 F6；关掉该项则保留上一局状态，进世界自动运行

## 十、日志关键字

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
