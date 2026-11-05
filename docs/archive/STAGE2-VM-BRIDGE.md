# 阶段 2 报告：Java 桥 + 事件回调

- **日期**：2026-11-05
- **目标**：把 Rhino 宿主接进 Fabric 客户端，让 JS 积木能调用现有 Java 动作层，并支持宿主事件触发
- **结论**：**桥已打通，端到端可运行**。两条冒烟链路（greenFlag 顺序执行 / startHat 事件触发）均通过

---

## 0. 验收结果

| 验收项 | 结果 | 证据 |
|---|---|---|
| Rhino 嵌入客户端并初始化 | ✅ 625–1206 ms，50 积木 | `[vm] 初始化完成，耗时 655 ms，积木 50 个` |
| 工程载入 → 执行 → 动作回调 | ✅ | `hkscript_log` 经桥打到 `GameActions.log`，日志输出 `from-rhino` |
| 宿主事件触发帽子积木 | ✅ | `startHat` 起了 1 个线程，脚本内 `hkscript_log` 输出 `hat-fired` |
| 桥调用统计 | ✅ | `桥调用 1 次，未映射 0 次` |
| 指令级超时中断 | ✅ 生效，且已调校 | 见 §3 |
| 打包（Rhino + VM 资源） | ✅ 2.17 MB dev jar，含 559 个 Rhino 类与 6 个 VM 脚本 | `build/devlibs/hk-script-1.0.0-dev.jar` |

---

## 1. 新增结构

```
src/client/java/xyz/hegke/client/script/vm/
├── RhinoVm.java        宿主：Context 生命周期、资源注入、预算与中断、tick 步进
├── ScriptBridge.java   JS ↔ Java 唯一桥：opcode → 积木类型 → 动作层
├── VmController.java   生命周期：首次 tick 初始化、读工程、greenFlag、事件入口
└── VmSmokeTest.java    冒烟测试（./gradlew vmSmoke [-PvmMode=hat]）

src/client/resources/assets/hk-script/vm/
├── vm.es5.js                1.5 MB  scratch-vm（阶段 1 产物）
├── hkscript-extension.es5.js  21 KB  扩展（Babel 降级）
├── scratch-globals.js       扩展所需的 Scratch 全局对象
├── shim-globals.js          console / 定时器 / process / performance
├── sync-promise.js          同步微任务 Promise
└── vm-bootstrap.js          覆盖积木方法体 + 暴露 __hk* 入口
```

设计要点：

1. **不新增映射表**。opcode（`hkscript_log`）→ 积木类型（`debug.log`）复用 `Sb3Mapping`，→ 动作实现复用 `NativeRegistry`。三层已有结构直接串起来。
2. **扩展文件不改**。`vm-bootstrap.js` 在加载后遍历 `getInfo().blocks`，把每个积木方法替换成转发函数；扩展本身在浏览器里仍然可用。
3. **参数顺序取自积木显示文本**的 `[NAME]` 占位符。对象 key 顺序在 ES5 不保证，文本里的出现顺序是唯一可靠来源。
4. **两条执行链路解耦**：`ScriptEngine`（原生积木编辑器）与 `VmController`（sb3 工程）互不影响，由 `scratchVmEnabled` 开关分别启用。

---

## 2. 依赖与打包

```gradle
configurations { embed; implementation.extendsFrom(embed) }
dependencies { embed "org.mozilla:rhino:1.7.15" }

jar {
    from({ configurations.embed.collect { it.isDirectory() ? it : zipTree(it) } }) {
        exclude "META-INF/MANIFEST.MF"
        exclude "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
```

Loom 的 `include` 只适用于 mod 依赖，普通 Java 库必须走独立的 `embed` 配置手动解包合并。产出在 `build/devlibs/`，体积 252 KB → **2.17 MB**。

> 环境坑：Loom 1.19.3 要求 **JDK 25**，用 JDK 21 会在配置阶段直接失败（`Dependency requires at least JVM runtime version 25`）。

---

## 3. 指令级中断：两次调校

第一版把观察器阈值设为 `budget × 1`，结果**冷启动帧被误杀**：VM 自身时间片就要 3 ms，加宿主开销正好越界，日志出现 `脚本超时已中断`，线程被 `stopAll` 清空。

最终方案：

| 参数 | 值 | 理由 |
|---|---|---|
| `setOptimizationLevel` | **-1（解释模式）** | Rhino 的指令观察器只在解释模式生效；实测 opt 9 与 -1 吞吐几乎一致，代价可忽略 |
| `setInstructionObserverThreshold` | 10000 条 | 足够密，又不至于拖慢执行 |
| 判定阈值 | `budget × 2` | 1:1 会在边界频繁误判 |
| 冷启动豁免 | 前 3 帧 | 首帧要跑 dispatch 队列与首次编译 |
| 超时处置 | `runtime.stopAll()` | 避免 VM 停在半步状态 |

---

## 4. 三个必须知道的行为差异

1. **帽子积木必须有实现方法**
   扩展里 3 个 HAT 只声明了 `blockType`，`startHats` 调用其方法判断条件时读到 `undefined` 直接崩。`vm-bootstrap.js` 里给它们补了恒真实现。

2. **`startHats` 的字段匹配只认 field，不认 input**
   `hkscript_whenChat` 的 `MATCH` 声明为 input，传 `optMatchFields={MATCH:'hello'}` 会在 `hatFields[matchField].value` 处抛 `Cannot read property "value" from undefined`。
   当前实现**不传匹配字段**，所有 `whenChat` 脚本都会被触发；关键词过滤需要在 Java 侧读积木输入值另做。

3. **costume 仍会告警**
   没有 storage 模块，每个造型打一条 `No storage module present`。不影响执行，B 方案完整实现时应接 Java 资源层或清空造型。

---

## 5. 已知限制（阶段 3/4 处理）

| # | 限制 | 影响 | 处置 |
|---|---|---|---|
| 1 | 阻塞类动作不阻塞 | `sleep` / `move_toward` / `wait_until` 在 VM 侧立即返回，脚本不会真的停下 | 需要 primitive 返回 Promise 并接 Java 侧的条件轮询 |
| 2 | 聊天关键词未过滤 | 任意聊天会触发所有 whenChat 脚本 | 读 hat 块的 input shadow 值做匹配 |
| 3 | 受伤/死亡事件未接 | 只有聊天接了 `fireChat` | 复用 `TriggerDispatcher` 的判定来源 |
| 4 | 工程来源固定 | 只读 `run/hkscript-vm/project.sb3` 或 `project.json` | 阶段 3 接 UI/导入目录 |
| 5 | 初始化阻塞主线程 0.6–1.2 s | 首次开启会掉一帧 | 改为后台线程建 Context 后移交，或进世界预加载 |
| 6 | `VmSmokeTest` 会打进 jar | 多一个带 main 的类 | 正式发布前移到独立 sourceSet |

---

## 6. 复现

```bash
export JAVA_HOME=~/.jdks/ms-25.0.5.0.2          # Loom 1.19.3 需要 JDK 25

./gradlew vmSmoke                                          # 顺序执行冒烟
./gradlew vmSmoke -PvmProject=run/hkscript-vm/hat-project.json -PvmMode=hat   # 事件触发冒烟
./gradlew jar                                              # 产物在 build/devlibs/
```

游戏内验证：配置 `scratchVmEnabled = true`，把工程放到 `run/hkscript-vm/project.sb3`，进世界后日志应出现
`[vm] 运行时就绪`，聊天发送 `hello` 应触发脚本。

---

## 7. 下一步（阶段 3：链路替换）

1. 把 `ScriptStore` 的 sb3 导入改为「落盘到 hkscript-vm 目录 + 触发 VM 重载」，废弃 `Sb3Parser`/`Compiler`/`ScriptInstance` 主链路；
2. 编辑器序列化改为直接产出 `project.json`（`hkscript_*` 积木写成合法扩展积木定义）；
3. 补齐阻塞动作：primitive 返回 Promise，Java 侧按 tick 兑现；
4. 事件补全：受伤/死亡接入 `TriggerDispatcher`；
5. 变量监视与存档：VM 的变量表映射到现有 `GlobalVariables`。
