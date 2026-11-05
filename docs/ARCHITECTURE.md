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
├── config/
│   ├── TurboMCConfig.java      配置数据与持久化
│   ├── TurboMCConfigScreen.java 模组菜单（需 Cloth Config）
│   └── HudCorner.java           HUD 停靠角落
└── script/
    ├── ScriptEngine.java        门面：取启用包 → 载入 → 绿旗 → 事件转发
    ├── action/                  动作层（Java 侧真正操作游戏的地方）
    │   ├── GameActions.java     移动、挖掘、放置、攻击、聊天、命令……
    │   ├── ContainerActions.java 容器：开界面、点槽位、搬东西、读槽位内容
    │   ├── WorldQueries.java    世界查询（方块、实体、时间……）
    │   ├── ScriptKeys.java      按键按住 / 释放
    │   ├── InputKeys.java       Scratch 键名 ↔ Minecraft 按键
    │   └── NativeRegistry.java  opcode → 动作的注册表
    ├── runtime/                 宿主上下文
    │   ├── ScriptContext.java   每 tick 重建的宿主环境（含等待请求通道）
    │   ├── TriggerDispatcher.java 按键 / 受伤 / 死亡事件源
    │   └── Values / ScratchCast  值转换（scratch-vm 已自带运算与比较，Java 不重复实现）
    ├── vm/                      JS 运行时
    │   ├── RhinoVm.java         Rhino Context 生命周期、时间预算、单帧中断
    │   ├── ScriptBridge.java    JS → Java 的唯一桥
    │   └── *SmokeTest.java      纯 JVM 冒烟（不发布）
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
        ├── ScriptListScreen.java   F7：包列表 + 展开脚本名
        ├── ScriptEditScreen.java   只读脚本树
        ├── ScriptHudElement.java   HUD
        ├── RuntimePanelScreen.java 运行状态
        └── NodeChain / ParamLabels / WaitUnit / TextInputScreen
```

`src/client/resources/assets/turbomc/vm/` 存放内嵌的 JS 运行时：
`vm.es5.js`（scratch-vm）、`turbomc-extension.es5.js`（扩展）、`vm-bootstrap.js`
（宿主与 VM 的粘合层）、若干 shim。

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
只是积木消失。要改就必须同时改 `Sb3Mapping` 的 61 条映射、`ScriptEngine` 里三个
帽子 opcode、`vm-bootstrap.js` 的拼接前缀，以及所有既有的 `.sb3`。

## 六、HUD

两行内容：引擎状态（`● 包名` / `○ 已停用`）与脚本的「说 / 思考」气泡。

这里补的是一个真实缺口：`looks_say` / `looks_think` 是 scratch-vm 的原生积木，
游戏内原本**没有任何渲染出口**——不补的话脚本永远无法对外表达。

读法是 `target.getCustomState('Scratch.looks')`，用其中的 `usageId` 判断是否是新
一句（每次说话都会重新生成，比对比文本可靠）。每句停留 5 秒，最多同时 3 条。

## 七、构建

```bash
# Loom 1.19 要求 JDK 25，系统默认 JDK 21 会在配置阶段失败
JAVA_HOME=~/.jdks/ms-25.0.5.0.2 ./gradlew build
```

产物：`build/libs/turbomc-1.0.0.jar`

### 冒烟测试（不启动客户端）

| 任务 | 作用 |
|---|---|
| `./gradlew vmSmoke` | 载入 project.json，验证顺序执行 |
| `./gradlew vmSmoke -PvmMode=hat` | 验证事件触发帽子积木 |
| `./gradlew vmSmoke -PvmMode=block` | 验证阻塞动作按时长挂起 |
| `./gradlew vmImport` | 验证 sb3 能解析成树，并被 VM 执行 |
| `./gradlew vmChat` | 验证聊天帽子一条消息只触发一次、关键词能过滤、自己发的聊天不回声 |

`vmImport` 默认读 `run/turbomc-vm/fixture.sb3`，可用
`-Psb3=路径` 指定其他工程。

## 八、使用流程

1. 在 TurboWarp 里加载 `turbowarp/turbomc-extension.js`（否则扩展积木显示为未知块）
2. 编写脚本，导出为 `.sb3`
3. 放进 `.minecraft/config/turbomc/imports/`
4. 游戏内按 **F7** → 点「导入」→ 点圆点启用
5. 按 **F6** 开引擎，HUD 出现 `● 包名` 即表示在跑

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
| `[script] 容器距离 N 格超过交互上限` | 服务端会静默拒绝，所以这里提前说明 |
