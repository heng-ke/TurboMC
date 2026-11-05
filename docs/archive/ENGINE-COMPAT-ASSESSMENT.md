# 引擎与 sb3 翻译层兼容性评估

> 触发：用户实测"按键脚本无效"，怀疑引擎实现与翻译层存在严重兼容性问题。
> 方法：不猜，把链路三层（解析产物 / 编译指令流 / 运行时按键矩阵）全部打出来核对。
> 日期：2026-11-04

---

## 0. 结论

**不是严重不兼容。** 三层验证都指向同一个结论：翻译与执行是通的，"无效"有明确且可复现的原因。

| 层 | 验证方式 | 结果 |
|---|---|---|
| 解析 | 读取导入后的 `角色1_1.json`，逐节点核对 | ✅ 结构正确 |
| 编译 | 打印 37 条指令，逐条核对 | ✅ 全部正确 |
| 运行时 | 直接调用 `action.key`，检查 KeyBinding 状态 | ✅ 按下的键真的被按住 |

真实存在的问题是 **3 个可定位的小缺陷**（其中 2 个已修），以及**用户脚本本身的三个设计问题**。

---

## 1. 证据一：编译指令流

脚本是"逐字按下 `TWASD`"：

```
重复 (长度 指令) 次
  按键 (第 i 个字符 of 指令) 按下
  等待
  按键 (第 i 个字符 of 指令) 松开
  等待
  把 i 增加 1
```

编译产物（37 条指令）：

```
 0: PUSH_CONST TWASD              ← 指令 = "TWASD"
 1: STORE_VAR 指令
 2: PUSH_CONST 1.0
 3: STORE_VAR i                   ← i = 1
 4: LOAD_VAR 指令
 5: CALL_BUILTIN operator.length  ← 长度(指令) = 5
 6: CALL_BUILTIN operator.round
 7: STORE_VAR $repeat0            ← 循环计数 = 5
 8: LOAD_VAR $repeat0
 9: PUSH_CONST 0.0
10: COMPARE >
11: JUMP_IF_FALSE 35              ← 计数为 0 退出
12: LOAD_VAR i
13: LOAD_VAR 指令
14: CALL_BUILTIN operator.letter_of   ← 第 i 个字符
15: PUSH_CONST true
16: CALL_ACTION action.key            ← 按下
17: PUSH_CONST 1.0
18: YIELD                             ← 等 1 tick
19: LOAD_VAR i
20: LOAD_VAR 指令
21: CALL_BUILTIN operator.letter_of
22: PUSH_CONST false
23: CALL_ACTION action.key            ← 松开
24: PUSH_CONST 1.0
25: YIELD
26: LOAD_VAR i
27: PUSH_CONST 1.0
28: BIN_OP +
29: STORE_VAR i                       ← i = i + 1
30-34: 计数递减并 LOOP 回 8
35: LOOP 2                            ← 永久循环
36: HALT
```

**逐条核对：无一处错误。** `letter_of` 的参数顺序（i, 指令）、`true/false` 的按压状态、等待换算（0.05 秒 = 1 tick）全部正确。

---

## 2. 证据二：运行时按键矩阵

在客户端 tick 里直接调用 `action.key(键名, true)`，然后读 `KeyBinding.isPressed()`：

| 输入 | forward | left | jump | chat | inventory | 判定 |
|---|---|---|---|---|---|---|
| `w` | **true** | false | false | false | false | ✅ 前进生效 |
| `a` | false | **true** | false | false | false | ✅ 左移生效 |
| `space` | false | false | **true** | false | false | ✅ 跳跃生效 |
| `e` | false | false | false | false | **true** | ⚠️ 背包键**未屏蔽** |
| `t` | false | false | false | false | false | 被黑名单拦截 |
| `k` | false | false | false | false | false | 无绑定，告警 |

机制是有效的：`ScriptKeys` 记录 + `KeyBindingMixin` 拦截 `isPressed`，游戏侧能读到"按下"。

---

## 3. 那为什么"实测无效"

三个原因叠加，缺一不可：

### 原因一：`T` 是聊天键，被黑名单拦截

`BLOCKED_ACTIONS` 包含 `key.chat`（默认 T）。脚本第一个字符就是 T → 直接跳过，没有告警以外的任何效果。

### 原因二：每个键只按 1 tick

`等待` 填的是 0.05 秒 = 1 tick。MC 的行走速度约 4.3 格/秒 = **0.215 格/tick**。

按 1 tick ≈ 移动 **0.2 格**——大约是玩家身位的 1/3，肉眼几乎看不出来。

### 原因三：四个方向互相抵消

`TWASD` 去掉被屏蔽的 T，剩下 W（前）、A（左）、S（后）、D（右）。

各走 0.2 格 → **净位移接近 0**，看起来就是"角色抖了一下，没动"。

这三个因素叠在一起，用户看到的现象就是"完全无效"。

---

## 4. 真实缺陷（已修 2 个）

| 编号 | 问题 | 状态 |
|---|---|---|
| **G1** | `key.inventory` 没进黑名单 → 脚本按 E 会弹出背包界面 | ✅ 已修 |
| **G2** | 键被屏蔽时告警写"没有任何游戏动作绑定"，误导用户 | ✅ 已修（区分"用于打开界面"与"真没绑定"） |
| **G3** | 文档未说明"按键需要按住足够时间才有可见位移" | 待补 |

---

## 5. 覆盖率评估

| 类别 | 官方积木 | 已映射 | 说明 |
|---|---|---|---|
| 运算符 | 18 | **18** | 100% |
| 变量/列表 | 17 | 12 | 缺 `show/hide variable` 等舞台专用 |
| 控制 | 11 | 8 | 缺 `wait until` 已支持，缺 `clone` / `delete clone` / `create clone` |
| 事件 | 8 | 5 | 缺 `when this sprite clicked` / `when backdrop switches` / `when loudness` |
| 感知 | 12 | 4 | 缺计时器重置以外的多数（`touching`、`distance to`、`loudness`、`username`） |
| 运动 | 17 | 0 | **语义不可映射**（MC 里没有"坐标移动"概念，用按键模拟） |
| 外观 | 20 | 4 | 仅 `say`/`think` 映射到动作栏/聊天 |
| 声音 | 9 | 0 | 无音频通道 |

**可映射部分（事件+控制+运算+变量+列表+感知，共 66 个）：已覆盖 47 个 = 71%。**
不可映射部分（运动/外观/声音，46 个）：本质是"Scratch 舞台语义"，在 MC 里没有对应物。

未映射的积木不会中断脚本——用 `control.nop` 占位，链继续。导入报告会列出 opcode。

---

## 6. 如何自行验证"有效"

用一个**必然能看出效果**的脚本替换现有测试：

```
当绿旗被点击
  按键 [w] [按下]
  等待 [1.5] 秒        ← 30 tick，走约 6 格
  按键 [w] [松开]
```

预期：角色向前走约 6 格，肉眼明显。

如果这个也不动，再排查三件事（按顺序）：

1. 按 **F6** 确认引擎开启（动作栏会提示"脚本已开启"）
2. 看日志有没有 `[script] 按键 "x" 无效：…`
3. 看运行面板（F7 → 运行状态）里有没有 `run` 状态的实例

---

## 7. 结论与建议

**不建议现在做"引擎大改"**——三层证据表明执行链路是通的，改引擎属于"没有病灶却动手术"。

建议顺序：

1. **用第 6 节的脚本验证一次**，确认"按住 1.5 秒"能走
2. 若有效，把原脚本的 `等待 0.05 秒` 改成 `0.2 秒`（4 tick），并把 `T` 换成可用键（如 `1` 或 `q`）
3. 若仍无效，提供**当时的日志片段**（含 `[script]` 前缀的行），按日志定位而不是继续猜

需要我补的东西：把"按键需要按住多久"写进使用文档，并在 `按键` 积木的注释里标注
"单 tick 位移约 0.2 格"——这是唯一能让用户少走弯路的说明。
