# hk-script 开发进度

Fabric 1.21.11 客户端自动化模组。核心能力：**类 Scratch 的积木式自动化编排**。

最后更新：2026-11-03

> **相关文档**
> - `PROJECT-ASSESSMENT.md`：**组内评估用总览**（架构、指标、风险登记、工程债、演进路线）
> - `TURBOWARP-COMPAT-REPORT.md`：TurboWarp 语义兼容性审计（6 个 P0 / 8 个 P1 / 8 项缺口，含修复方案与批次）
> - `ENGINE-REFACTOR-PLAN.md`：引擎重构评估与方案（结论：不重写内核，分层对齐重构，7.5 天四批次）
> - `NODE-BRIDGE-ASSESSMENT.md`：外挂 Node.js 跑 scratch-vm 的可行性评估（结论：技术上最优，产品上不建议作主后端；建议当测试裁判）
> - `turbowarp/README.md`：扩展加载与导入步骤
> - `turbowarp/mapping-report.md`：opcode 逐条对应表
> - `turbowarp/hkscript-extension.js`：TurboWarp 扩展（44 个积木，中英双语）

---

## 一、构建须知

- **Loom 1.19-SNAPSHOT 要求 Gradle 运行于 JDK 25**，系统默认 Java 21 会失败。
- 本机可用 JDK 25：`C:\Users\13692\.jdks\ms-25.0.5.0.2`
- 命令行构建：

```bash
./gradlew build -Dorg.gradle.java.home="C:/Users/13692/.jdks/ms-25.0.5.0.2"
```

- 编译目标仍是 Java 21（`build.gradle` 中 `release = 21`）。
- 源码含中文，已强制 `options.encoding = "UTF-8"`。

---

## 二、总体架构

```
script/
├── block/     积木类型与参数元数据（BlockType / BlockParam / Blocks）
├── node/      积木树 BlockNode（同时是 JSON 持久化格式）
├── ir/        Op / Instr / Compiler / CompiledScript（树 → 扁平指令）
├── runtime/   ScriptContext / ScriptInstance / ScriptScheduler / Values
├── action/    NativeRegistry / GameActions / KeyForceState（唯一直接依赖 MC 的脚本层）
├── store/     ScriptDefinition / ScriptStore（config/hk-script/scripts）
└── ui/        树形编辑器、参数面板、运行面板、函数与脚本列表
```

执行模型：积木树编译为**扁平指令 + 显式跳转**，解释器持有 pc 与求值栈；`wait` 编译为 `YIELD` 挂起，循环回边受每 tick 预算约束（单实例 400 条 / 全局 4000 条）。

---

## 三、已完成

### P1a 引擎骨架 ✅ 已验证
- [x] IR 指令集（13 个操作码）与编译器
- [x] 解释器（显式栈、YIELD 挂起、预算让出）
- [x] 调度器（双层预算、实例快照）
- [x] 启动自检 12 项（跳转越界、原生引用、循环计数、wait 时序、预算、扩展积木编译、repeat until、wait until、列表、spawn、函数、sb3 切片）
- [x] 内置 demo 脚本（挖掘-移动）

### P1b-1 动作与查询库 ✅ 已验证（36 个积木）
- [x] 事件：`event.tick`
- [x] 控制：if / repeat / forever / wait
- [x] 运算：lt / add
- [x] 变量：set / get（脚本局部）
- [x] 查询：血量、饱食度、坐标 XYZ、当前槽位、最近实体距离与坐标、最近敌对生物、物品数量、前方有方块、是否在地面
- [x] 动作：挖掘、移动（7 方向）、攻击准星、攻击最近实体、攻击最近敌对生物、使用、打开最近容器、走向坐标、设置视角、看向坐标、切换槽位、停止
- [x] 调试：日志
- [x] `KeyBindingMixin` 拦截 `isPressed()` 修复按键注入被原版刷新覆盖

### P1b-2 存储 + 列表式编辑器 ✅ 已验证
- [x] `ScriptStore`：增删改查、重名加序号、首次启动写 demo
- [x] 脚本列表（启用切换、删除确认、分页）
- [x] 列表式编辑器（选中、上下移、删除、进入 C 形块、返回上层）
- [x] 积木选择面板（按外形过滤）
- [x] F6 启停引擎、F7 打开编辑器

### P1c 参数控件 + 运行面板 ✅ 已验证
- [x] `BlockParam` 元数据：TEXT / NUMBER / BOOLEAN / ENUM
- [x] 参数面板按规格生成控件；数字输入过滤
- [x] `fx` 按钮把表达式积木放入参数槽；点击已嵌套槽可清除
- [x] 运行面板（脚本名、状态、pc/总指令、局部变量）

### 导入导出 ✅ 已验证
- [x] 导出到 `config/hk-script/exports/`
- [x] 从 `config/hk-script/imports/` 导入，源文件移入 `imported/`

### P2 拖拽画布 ❌ 已移除
自制画布已被 TurboWarp 前端取代，相关代码（`ui/canvas` 包、相关文案与入口）已全部删除。

---

### P0 触发源扩充 ✅ 已验证（积木 36 → 41）
- [x] `event.tick` 语义澄清：文案改为「脚本启动时」，启动时创建唯一常驻实例
- [x] `event.key`：按键触发（F/G/R/V/C/空格/Shift，边沿检测）
- [x] `event.damage`：受伤触发（每 tick 检测血量下降）
- [x] `event.chat`：收到聊天触发（`ClientReceiveMessageEvents.CHAT` + 关键词匹配）
- [x] `event.broadcast` + `action.broadcast`：脚本间通信
- [x] 实例上限：同脚本并发不超过 4，防触发风暴
- [x] 编辑屏「触发」按钮可切换事件头，保留脚本本体
- 新增 `runtime/TriggerDispatcher`：按键边沿检测 + 血量下降检测

---

### P1 Scratch 语义补齐 ✅ 已完成（积木 41 → 67）

**已完成 ✅**
- [x] 全局变量：`data.set` / `data.get` 增加 `SCOPE` 字段（local / global），全局表由 `GlobalVariables` 保存，引擎启动时清空
- [x] 运算补齐：减 / 乘 / 除 / 取余 / 随机数 / 取整 / 拼接文本
- [x] 比较补齐：大于 / 等于 / 大于等于（原有小于）
- [x] 逻辑运算：并且 / 或者 / 取反（新增 `Op.AND / OR / NOT`）
- [x] 控制补齐：`repeat until` / `wait until` / `stop`
- [x] 列表类型（8 个积木）：添加 / 插入 / 删除 / 替换 / 清空 / 取项 / 长度 / 包含；全局命名空间、索引 1 起、越界安全
- [x] 自检扩至 10 项（新增 repeat until 计数、wait until 立即通过、列表长度与取项、spawn 派生）
- [x] 运行面板显示全局变量与列表尺寸
- [x] 并行分支 `control.spawn`：分支编译到主流程之后，`SPAWN` 派生独立实例；调度器总实例上限 64
- [x] 自定义积木：`func.call`（参数固定 `$p1`~`$p3`），函数体编译为独立段以 `RETURN` 收尾，解释器维护调用帧（返回地址 + 上层局部变量），递归深度上限 32
- [x] 函数管理界面（新建 / 删除 / 进入编辑函数体），与主流程共用同一套积木编辑器

**剩余（低优先级）**
- [ ] 脚本局部列表（当前列表为全局命名空间）
- [ ] 函数带返回值（当前为无返回值的命令块）

### P5 TurboWarp 前端 ✅ 已完成（积木 67 → 104）

用真正的 Scratch 编辑器编写脚本，导出 `.sb3` 后导入执行；游戏内编辑器保留作为快速调试通道。两者共用同一套 `BlockNode` 与 IR。

**已完成**
- [x] `script/sb3/Sb3Mapping`：全量映射——40 个原生 opcode + 44 个扩展 opcode
- [x] `script/sb3/Sb3Parser`：zip → `project.json` → 积木图还原为 `BlockNode`；处理 inputs 的 1/2/3 类型码、变量 shadow `[12,name,id]`、扩展菜单 shadow `hkscript_menu_*`
- [x] `ScriptStore` 导入流程支持 `.sb3`，一个项目可产出多个脚本（每个帽子积木一个）
- [x] `data_changevariableby` 展开为 `读取 → 相加 → 写回`
- [x] 新增内部积木 `control.if_else`（SUBSTACK / SUBSTACK2 双分支）
- [x] 新增内部积木 `control.stop_all`：Scratch 的「停止 [全部]」按 `STOP_OPTION` 拆分，运行时由引擎整体停机并关闭开关（按 F6 重启）
- [x] 语义对齐修正 6 处：wait 秒→tick（×20）、变量默认全局作用域、`KEY_OPTION→KEY`、`BROADCAST_OPTION→MSG`、运算输入名 `FROM/TO/NUM/STRING1/2`、列表与扩展的 `ITEM` 区分
- [x] 按键触发支持任意字母与方向键（`InputUtil.fromTranslationKey`）
- [x] `turbowarp/hkscript-extension.js`：44 个扩展积木，中英双语 + 菜单本地化
- [x] `turbowarp/README.md`、`turbowarp/mapping-report.md`
- [x] 自检扩至 15 项（新增 sb3 切片含 wait 换算、if/else 双方向、停止全部三段验证）

**R1 解析层重建 ✅（2026-11-03）**

按 `ENGINE-REFACTOR-PLAN.md` 的 B 方案推进，本批解决"静默错误"类问题：

- [x] `control.nop` 占位积木：未映射语句不再截断后续积木链（**P0-1**）
- [x] 内联原语 `[12,名字,ID]` 补上作用域，与变量积木走同一通路（**P0-2**）
- [x] 变量/列表按 **ID** 定位，内部名加角色前缀（`score` / `猪/score`），新增 `TargetVariables` 与第三作用域 `instr.a=2`（**P0-4**）
- [x] 内联列表 `[13,...]` 还原为新增积木 `list.contents`（**P2-8**）
- [x] 导入报告：`ParseResult.summary()` 汇总"跳过 M 块（opcode 列表）与未支持的事件"（**P2-7**）
- [x] 编辑器显示层省略当前角色前缀（`VariableLabels`）
- [x] 自检扩至 16 项（新增 sb3 作用域样例：三个 target 的同名变量、内联变量/列表、未映射积木不断链）

**R2 语义层抽取 ✅（2026-11-03）**

把散落在解释器与工具类里的语义规则集中到三个纯函数类，逐条对齐上游 `cast.js` 与 `scratch3_operators.js`：

- [x] `runtime/ScratchCast`：`Number()` 语义（含 0x/Infinity/NaN）、布尔不 trim、ECMAScript 数字转字符串
- [x] `runtime/ScratchCompare`：数字优先、失败转小写字符串、空白特判（**P0-3**）
- [x] `runtime/ScratchMath`：向下取整的 `mod`（**P1-1**）、整数/浮点分支的 `random`（**P1-2**）、14 种 `mathop`、`listIndex` 支持 `last/random/any/all`（**P1-5**）
- [x] 新增 4 个运算积木：`operator.letter_of` / `length` / `contains` / `mathop`（**P1-3**）
- [x] `repeat` 次数按 `Math.round` 取整（**P1-4**）
- [x] `Values` 三个转换方法委托给 `ScratchCast`，全项目一套语义；`toBoolean` 去掉 trim（**P1-8**）
- [x] 自检扩至 17 项（新增 18 条语义断言：文本比较、取模负数、随机浮点区间、sin(180)=0、`" false "` 为真、列表特殊下标、repeat 2.1 只跑 2 轮）

**R3 调度对齐 ✅（2026-11-03）**

- [x] `TriggerPolicy`：广播/常驻 → 原地重启；按键 → 已在跑则放弃；受伤/聊天 → 保留并发（**P0-5**）
- [x] `ScriptScheduler.killByName`：按脚本名终止全部实例
- [x] 公平预算：按"剩余实例数"均分全局预算，并给每个实例保底 32 条指令，消除第 11 个起的饥饿（**P1-6**）
- [x] 循环节流：`LOOP.b=1` 表示每轮让出一 tick；配置项「循环节流（对齐 Scratch）」默认关闭（快速），可在 Mod Menu 配置页切换后自动重启引擎（**P0-6**）
- [x] 自检扩至 19 项（新增触发策略枚举、按名终止、12 实例公平预算、节流前后 tick 数对比）

**R4a 感知与输出 ✅（2026-11-03）**

- [x] `input.key_pressed`「按下 [键]?」，支持 `any`（任意按键，排除 ESC/F6/F7 防止误关引擎）（**P2-3**）
- [x] 计时器族：`sensing.timer` / `sensing.reset_timer` / `sensing.current`（年/月/日/星期/时/分/秒）/ `sensing.days_since_2000`（**P2-4**）
- [x] `debug.say` / `debug.say_for`：Scratch 的「说 / 思考」映射到动作栏 + 日志；`说 n 秒` 会换算成 tick 并阻塞当前脚本（**P2-5**）
- [x] 按键名解析抽成 `action/InputKeys`，触发分发与查询积木共用一套；`event.key` 从固定下拉改为可填任意键名（**P2-6**）
- [x] 引擎启动时计时器归零（等价于点绿旗）
- [x] 自检扩至 20 项（新增「说 n 秒」换算断言 40 tick、时钟合理性、按键/时间积木的编译断言）

**R4b 自定义积木 ✅（2026-11-03）**

- [x] `procedures_definition` 不再被丢弃：扫描 prototype 的 `proccode` / `argumentnames` 生成 `FunctionDefinition`
- [x] `procedures_call` → `func.call`：函数名取 `proccode`，实参按 `argumentids` 顺序落到 `P1..P5`
- [x] `argument_reporter_*` → `data.get $pN`（局部作用域，指向调用帧的参数）
- [x] `func.call` 支持可变参数个数（`ARGC` 字段，1–5），编译器与解释器原本就支持 `$pN`
- [x] 同一角色的所有脚本共享该角色的函数表（与 Scratch 的角色级函数一致）
- [x] 自检扩至 21 项（新增：定义不丢、参数映射到 `$p1`、调用名与 `ARGC` 一致、编译出 `CALL_FUNC`）

**R4c 广播并等待 ✅（2026-11-03）**

- [x] 新增指令 `BROADCAST_WAIT` 与积木 `control.broadcast_wait`：广播后挂起，直到被唤醒的实例全部结束
- [x] `ScriptContext` 增加 broadcaster 回调，引擎注入「发出广播并返回实例」的实现
- [x] 被等待的实例被强制终止（广播重启 / 停止全部）时置为完成态，避免等待方死锁
- [x] **修复一个既有崩溃**：调度器遍历期间新建实例（广播、`spawn` 都会走到）会抛 `ConcurrentModificationException`。
      改为遍历期间进 `pendingAdd`、tick 结束统一并入；`killByName` 改为先标记再回收
- [x] 自检扩至 22 项（新增：无接收者直接通过、有接收者保持挂起、被等待方被终止后恢复执行、tick 内派生实例不崩）

**R5 判断能力（Scratch 补漏 + 游戏查询）✅（2026-11-03）**

之前脚本"只能开环执行"，无法根据耐久、背包、方块、环境做判断。本批补齐：

- [x] `data_itemnumoflist` → `list.item_num`「列表中第一个 [项] 的编号」（1 起算，查不到返回 0）
- [x] **P0 六项**：吃食物、手持物品、手持耐久%、准星所指方块、脚下方块、坐标处方块
- [x] **P1 八项**：是否白天、当前维度、在水中、在岩浆中、最近实体名称、丢弃手持物品、经验等级、背包已满
- [x] 吃食物只在手持带食物组件的物品时触发，按住使用键 36 tick 后由 `GameActions.tick` 自动松开（避免误触右键放方块）
- [x] TurboWarp 扩展同步扩到 44 个积木，映射表补 15 条
- [x] 自检扩至 23 项（注册完整性、坐标查询压 3 参、吃食物编译、列表编号命中/未命中）

**R6 建造与界面（P2）✅（2026-11-03）**

- [x] 在坐标放方块（目标须为空气，从首个相邻固体面出手；不预判可否放置，交给服务端校验）
- [x] 睡附近的床（8 格内按 `BlockTags.BEDS` 找最近床并右键）
- [x] 打开玩家背包（`InventoryScreen`，会打断当前输入，仅在明确需要时使用）
- [x] 附近实体数量（类型支持 `any` / `hostile` / `player` 或实体 id，16 格内）
- [x] 当前生物群系（返回 `minecraft:plains` 形式的 id）
- [x] 服务端延迟（毫秒，单人游戏为 0）
- [x] TurboWarp 扩展扩到 50 个积木，映射补 6 条
- [x] 自检扩至 24 项（注册完整性、坐标放方块压 3 参、实体计数压 1 参）

**R7 结构瘦身 ✅（2026-11-03）**

不新增功能，只处理"难读"与"该合/该拆"的地方：

- [x] 删除死指令 `Op.POP`（编译器从未生成，仅解释器有分支）
- [x] `BlockColors` / `VariableLabels` 合入 `ParamLabels`：显示层文本工具（积木名/参数值/配色/变量前缀）集中一处
- [x] `GameActions` 拆分：动作 395 行 + `WorldQueries` 399 行（**只读查询与改变世界分离**，便于单独审查副作用）
- [x] 按键状态统一为 `ScriptKeys`：原 `KeyForceState` + `useKeyTicksLeft` + 固定 7 键释放 → 一张「按键 → 剩余 tick」表；
      `stop` 时只松开**脚本接管过**的键，不再打断玩家自己按着的方向键
- [x] `SelfCheck` 1,320 行拆为三：入口与基础（438）/ sb3 解析（524）/ 运行时语义（476）
- [x] 结果：**最大文件 1,320 → 723（-45%）**，动作层 768 → 395；总行数 8,528 → 8,669（拆分带来的 import/类头重复），文件 60 → 61
- [x] 自检 24 项全过，积木 104 不变

**R8 按键积木改为物理键 ✅（2026-11-03）**

- [x] `action.key`「按键 [KEY] [按下/松开]」按的是**物理键**：`w` / `k` / `space` / `shift` / `f5`
- [x] 实现：解析成 `key.keyboard.*` → 找出所有绑定在该键上的动作 → 逐个强制按下 + 触发一次按下事件（让「开背包」「切视角」这类边沿动作也生效）
- [x] 改键位后脚本自动跟着走（前进绑 W 就走，绑上箭头就按上箭头）
- [x] 跳过会打开界面的动作：聊天、命令、截图、进度、社交、全屏、暂停
- [x] **删除 `action.move`**（积木 / 编译分支 / 映射 / 语言 / 扩展 JS / 示例脚本 / 自检样例全部清理），积木 105 → 104
- [x] 修复一个通用缺陷：`putLiteral` 对「内部是字段、sb3 是字面量输入」的参数不落字段，导致 `action.key` 的按键名从 sb3 导入后静默丢失
- [x] 自检 24 项全过（`action.key` 的注册、2 参数编译、sb3 导入后按键名落在字段）

**R9 代码审查第 1 批修复 ✅（2026-11-03）**

审查报告见 `CODE-REVIEW.md`（三面共 19 条）。第 1 批三个"静默损坏"问题已修：

- [x] **E1**：`Sb3Parser` 把「链长度」与「嵌套深度」共用 `MAX_DEPTH=200`，超过 200 块的脚本被静默截断。
      拆出独立 `chain` 参数（沿 next 递增），`MAX_CHAIN=10_000`，嵌套仍限 200
- [x] **U1**：脚本文件名归一化后可能相同（`挖矿 1` / `挖矿_1` → 同一文件）导致互相覆盖。
      `exists()` 改为按 `safeFileName` 比较（新增 `ScriptStore.sameFileName`）
- [x] **E3**：脚本结束/崩溃后不释放它按下的键，角色会一直移动。
      `ScriptKeys` 改为**按实例归属 + 引用计数**：`ScriptContext.currentInstance` 每 tick 由 `ScriptInstance.step` 设置，
      调度器移除实例时 `releaseAll(instance)`
- [x] 自检 24 → 27 项，每条修复配一条断言（250 块长链完整导入、文件名冲突判定、按键归属与引用计数）

**R10 代码审查第 2 批修复 ✅（2026-11-04）**

- [x] **E2**：`func.call` 找不到函数时跳转目标保持 0，脚本会跳回开头重跑。改为编译期抛
      `IllegalStateException`（列出所有未定义的名字），该脚本不启动并在日志中说明
- [x] **B1**：`action.key` 每次调用都伪造一次按下事件，循环里每 tick 会重复触发「开背包」。
      改为只在「松开 → 按下」的边沿调用 `onKeyPressed`
- [x] **U2**：编辑器内 4 处 `save + restart` 收敛为 `markDirty()`，关闭编辑器时统一重启一次。
      改一个参数不再清空全局变量、不再打断正在跑的任务
- [x] 自检 27 → 28 项（新增：调用未定义积木必须编译失败）

**R11 代码审查第 3 批修复 ✅（2026-11-04）**

- [x] **U3**：`writeFile` 改为返回布尔并在失败时给玩家发动作栏提示（新增 `message.hk-script.save_failed`）；
      `rename` 改为**先写新文件、成功后才删旧文件**——原顺序一旦写入失败就彻底丢脚本
- [x] **B2**：新增 `GameActions.clampCoord`（±30,000,000，NaN 归零），`world.block_at` 与 `action.place_block` 全部改用，
      避免 `1e20` 饱和成 `Integer.MAX_VALUE` 后触发远端区块加载
- [x] **B4**：进食时长从固定 36 tick 改为读 `ConsumableComponent.getConsumeTicks()`
      （1.21.11 已把进食时长从 `FoodComponent` 移到此组件），干海带不再多按 20 tick
- [x] 断言并入现有项（项数仍 28）：坐标钳制三例、干海带 tick 数必须小于熟牛肉

**R12 代码审查第 4 批修复 ✅（2026-11-04）**

- [x] **E4**：`project.json` 改为**限长读取**（32 MB，边读边计数），防压缩炸弹。注意不能先 `readAllBytes` 再判断长度
- [x] **E5**：`startInstance` 增加 `catch (StackOverflowError)`——嵌套过深的脚本跳过而不是让客户端崩
- [x] **E6**：`triggerKeys()` 启动时算一次并缓存，`stop()` 清空（原来每 tick 新建 HashSet）
- [x] **U6**：`control.nop` 占位积木不再出现在积木选择器里
- [x] **B7**：`WorldQueries.nearbyEntities(ctx)` 单 tick 缓存，最近实体与实体计数共用一次世界遍历
- [x] **B8**：`anyDown` 增加 8 ms 缓存，同一 tick 内不重复扫 300+ 键
- [x] **B9**：`input.key_pressed` 对无法识别的键名 warning 一次，与 `action.key` 一致
- [x] 清理两处未使用 import（`Box`、`GameOptions`）
- [x] 审查 22 条中 16 条已修，6 条**有意保留**（Scratch 语义 / 需要新功能 / 影响可忽略），在报告第 6 节逐条说明理由

**待做**
- [ ] Scratch 补漏：询问并等待、用户名、碰到实体
- [ ] 反向导出：脚本 → sb3
- [ ] 编辑器撤销/重做（审查 U4 记录的新功能）

---

## 四、待办（按优先级）

### P2 运动能力
- [ ] 真正的寻路（A* 或简化绕行），当前 `move_toward` 遇墙即卡
- [ ] 跳跃/上台阶判断
- [ ] 容器存取（当前只能打开，不能存/取物品）
- [ ] 背包整理

### P3 编辑器体验（进行中）

- [x] **树形编辑器**：取消「进入/返回」导航，整棵树一次展开；C 形块内联缩进，`否则` 分支单独标注
- [x] 行由本类自绘，选中与折叠只重绘不重建界面（消除点击闪烁）
- [x] 折叠/展开（点击 `+/-` 或按 ←→）
- [x] 滚动浏览（滚轮，替代分页）
- [x] 快捷键：↑↓ 选择、Enter 改参数、Delete 删除
- [x] 参数名与枚举值中文化（`VAR`→变量、`SCOPE`→作用域、`forward`→前进…）
- [x] 脚本列表：复制、改名（名字由导入产生的 `Sprite1#1` 可改成有意义的名字）
- [x] 通用单行输入屏 `TextInputScreen`
- [x] **语义模板**：47 个积木按 Scratch 句式渲染（`重复 {TIMES} 次`、`{B} 的第 {A} 个字符`），表达式内联而不是 `[...]`
- [x] **指令映射**：编译期记录每块积木的指令区间，选中显示 `指令 12-16`，报错日志带 `积木=xxx`
- [x] **执行高亮**：编辑器实时标出运行中的行（金色竖条），打开时自动滚入视野；运行面板同步显示当前积木摘要
- [x] 枚举值本地化（`true`→按下、`year`→年）
- [x] **删除 A 档 5 个专用动作**：挖掘 / 攻击准星 / 使用 / 吃食物 / 丢弃手持物品 —— 全部改用「点按」「点击」等通用积木代替
- [x] **删除「选择物品栏」积木**：实测确认 `key.hotbar.1` 在 `allKeys` 里且绑物理键 `key.keyboard.1`，所以「点按 1-9」完全可替代
- [x] **鼠标积木**：「鼠标 左/右/中键 按下/松开」+「点击 左/右/中键」，与键盘的「按键 / 点按」完全对称
- [x] **死亡自动重生**：配置里一键开启（死亡界面出现即重生）；同时提供「当玩家死亡」触发源 + 「重生」动作，可写死亡后逻辑
- [x] **按键黑白名单可配置**：模组菜单 → 按键权限，两个可编辑列表 + 可展开的键名速查；默认黑名单为 T / / / F2 / F11 / Esc / L / P
- [x] **删除「打开背包」积木**：改为放行 `key.inventory`，用「点按 e」即可开背包，少一个专用积木
- [x] **聊天与命令积木**：「在聊天栏说 [TEXT]」「执行命令 [COMMAND]」，50ms 内只放行一条防刷屏
- [x] **点按积木**：「点按 [KEY] 按住 [HOLD] tick」，按下后自动松开，不必手写两遍
- [x] **参数嵌套可视**：参数屏里嵌套积木显示可读摘要，点击逐层进入，`×` 才是清除（不再误点即删）
- [x] **导入即更新**：重新导入同名 sb3 时覆盖旧脚本（保留启用状态与自定义名字），不再堆积 `#1/#2`
- [x] **等待单位开关**：编辑器底部「单位：秒 / tick」，默认秒，脚本内始终存 tick；换算用 `BigDecimal` 保证往返无损
- [ ] 撤销/重做
- [ ] Mod Menu 内增加「打开编辑器」按钮

### P4 工程化
- [ ] `fabric.mod.json` 的 `environment` 改为 `client`，移除 main 入口
- [ ] 单元测试（执行内核已与 MC 解耦，可脱离游戏测试）
- [ ] 多语言补全

---

## 五、已知限制

| 限制 | 影响 |
|---|---|
| 触发源 | 已支持 6 种：启动 / 按键 / 受伤 / 死亡 / 聊天 / 广播 |
| 列表仅全局作用域 | 无法为每个脚本实例维护独立列表 |
| `move_toward` 非寻路 | 遇障碍卡住 |
| 函数无返回值 | 只能做命令块，不能当表达式使用 |
| 无撤销 | 误删需重拼 |
| 黑名单按物理键名 | 默认值写成 `t` / `f5` 这类键名，改键位后需同步修改；想一劳永逸可写 `key.chat` 这种动作名 |
| 无滚轮 | 鼠标只支持左/右/中键的按下与点击；切换物品栏请用「点按 1-9」（依赖 hotbar 键位） |
| 吃食物需手动计时 | 原「吃食物」会按食物自动计算时长，删除后要写「鼠标 右键 按下 → 等待 2 秒 → 松开」 |
| 切槽依赖键位 | 「点按 1-9」能切物品栏，但如果玩家把 hotbar 键改到别处，脚本要跟着改键名 |
| 高亮不跟随折叠 | 运行行被折叠时只能看到「运行中（不在视野内）」提示，需手动展开 |
| 按键注入只保证「按下」 | 脚本松手后立刻交还控制权，玩家可抢回 |

---

## 六、验证方式

启动开发客户端：

```bash
./gradlew runClient -Dorg.gradle.java.home="C:/Users/13692/.jdks/ms-25.0.5.0.2"
```

日志出现以下三行即视为引擎健康：

```
[script] 脚本仓库就绪：N 个脚本
[script] 自检通过：34 项检查全部正常
[script] 引擎就绪：注册积木 104 个，脚本 N 个
```

崩溃排查顺序：指令字段错位 → 原生注册缺失 → mixin 注入失败 → 屏幕坐标换算。
