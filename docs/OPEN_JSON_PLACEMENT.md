# JSON 积木的实现位置（事实 · 待决策）

> 状态：**未决**，仅记录事实与候选方案，不含决定。
> 记录日期：2026-11-19。决策落地后，结论并入 `ARCHITECTURE.md` §23 并删除本文件。
> 触发问题：在 TurboWarp 编辑器里点 JSON 积木，返回的数值不对；怀疑「纯计算为什么放在 Java」。
> 复现：`./gradlew jsonBench`（基准）、`./gradlew vmSmoke -PvmProject=json-project.json`（功能）。

---

## 一、现状（已核对）

1. **29 块 JSON 积木全部在 Java**（原 32 块，2026-11-19 按「长度/校验合并、下标统一 1 基」瘦身）：`action/JsonActions` + `action/JsonText`，
   经 `NativeRegistry` 注册；其中 `json.vm_getlist` / `json.vm_setlist` 在 `vm/VmListActions`。
2. **这不是机制强制，是路径惯性**。`vm-bootstrap.js` 为每个非帽子积木装了同一个包装器，
   无条件下发桥：

   ```js
   Scratch.__instance[block.opcode] = function (args, util) {
       ...
       var result = __tmcApi().call('turbomc_' + block.opcode, names, args);
   ```

   加积木时照抄这条路径最省事，因此没有按「是否需要 Minecraft」来选位置。
3. **新增一块积木要动 5 处**：扩展定义（两份 JS）+ `Sb3Mapping` + `BlockRegistry`（查看器参数）
   + `NativeRegistry`（动作实现）。
4. **编辑器里必然算不对**：`turbowarp/turbomc-extension.js` 是给 TurboWarp 用的那份，
   JSON 积木的方法是占位：

   | 积木 | 浏览器里返回 |
   |---|---|
   | 是合法 JSON 吗? | 恒 `true` |
   | 新建 | `{}`（不区分对象/数组） |
   | 长度 | `0` |
   | 取值 | 空串 |
   | 设为 / 删除 / 压缩 | 原样返回输入 |
   | 全部 / 排序 / 统计 | `[]` / 输入 / `0` |

5. **运行时副本没有方法体**：`assets/turbomc/vm/turbomc-extension.es5.js` 只有 `opcode` 定义，
   方法由包装器在游戏内注入；把它拖进 TurboWarp 会报 `xxx is not a function`。

---

## 二、实测数据（`jsonBench`，两轮，Rhino `optimizationLevel=-1`）

数据：712 KB 对象数组（12000 条 × 5 字段）、1000 项数值数组、32 项小数组。

| 场景 | JS（Rhino） | Java | JS/Java |
|---|---|---|---|
| 解析 712 KB（单次） | 4.6–4.8 ms | 5.4–5.5 ms | 0.9x |
| 解析+序列化 712 KB | 17.2–17.3 ms | 10.6–11.0 ms | 1.6x |
| 按键取值（单次，含解析） | 4.9–5.0 ms | 6.8–7.0 ms | 0.7x |
| 排序 32 项（含解析+序列化） | 1.36 ms | 0.01–0.02 ms | ≈99x |
| 排序 1000 项 | 23.6–24.9 ms | 0.62–0.71 ms | ≈40x |
| Rhino → Java 单次调用 | — | 1.2 µs/次 | — |

解读（事实层面）：

- **解析与取值 JS 不慢**：Rhino 的 `JSON.parse` 本身是 Java 实现，被解释执行的只是外层调用。
- **差距集中在排序**：`Array.prototype.sort(comparator)` 每次比较都要从 Java 排序循环回到解释器，
  32 项约 160 次比较就花掉 1.36 ms（单次比较约 8 µs）。
- **跨语言开销可忽略**：1.2 µs/次，一帧调 20 块积木约 24 µs，占 4 ms 预算的 0.6%。
- 按 4 ms 帧预算换算：32 项排序占 1/3 帧；1000 项排序跨约 5 帧。

---

## 三、待决策项（未定）

### D1. 实现位置

| 选项 | 收益 | 代价 |
|---|---|---|
| 保持 Java（现状） | 排序快 40–100 倍；JUnit 直接覆盖；与既有 96 块同构 | 编辑器不可用；每块多一处登记 |
| 迁到 VM（JS） | 编辑器可调试；少一处登记；语义天然对齐浏览器 | 排序在解释执行下慢 40–100 倍；JUnit 覆盖要迁到 Rhino/Node |
| 双实现（Java 权威 + 编辑器 JS） | 两边都要的优点都拿到 | 两份语义，需对照测试防漂移 |

### D2. 编辑器可调试性

- 补 JS 实现在编辑器副本（真能算）；
- 或把占位返回值改成显眼假值（如「（浏览器预览）」），不再伪装成正常结果；
- 或维持现状，只写文档。

### D3. Java 解析器优化

手写解析器比 Rhino 原生慢约 15%（5.4 vs 4.7 ms）：每个数字箱装成 `Double`、每个字符串新建 `String`。
改基本类型容器可望收回 20–30%，非必需。

---

## 四、候选方案（均未实施，成本为估算）

| 方案 | 内容 | 估算 |
|---|---|---|
| A | 编辑器副本补 29 块 JS 实现 + Node 对照脚本（同一批用例跑 JS 与 Java，结果必须一致） | ~400 行 JS + 1 个脚本 |
| B | 29 块移进两个 JS 文件，删除 Java 版，包装器加「本地实现」注册表，测试改跑 Rhino | 包装器改动 + 29 块重写 + 测试迁移 |
| C | 只改占位返回值 + 文档 | ~30 行 |

注：B 要动积木包装器——它是每 tick 最热的代码，改动后必须重跑全部冒烟（`vmSmoke` / `vmList` /
`vmChat` / `vmKeyHat` / `vmAi` / `json-project.json`）。

---

## 五、不决策的代价

作者在 TurboWarp 里写 JSON 脚本时无法试算，只能「导出 → 进游戏 → 看结果」，
反馈回路从秒级变成分钟级；且占位返回值看似正常（`true` / `0` / 空串），
容易被误认为真实结果。
