# sb3 ↔ hk-script 对应关系报告

生成时间：2026-11-03 · 引擎版本 hk-script 1.0.0

---

## 一、覆盖统计

| 检查项 | 结果 |
|---|---|
| 内部注册积木 | 104 |
| 中文名覆盖 | 104 / 104 ✅ |
| 英文名覆盖 | 104 / 104 ✅ |
| 扩展 opcode 映射 | 50 条（`hkscript_*`） |
| 可从 sb3 导入 | 104 / 104 ✅ |
| 仅编辑器内部使用 | `control.spawn`、`func.call`（有意设计，见第四节） |

---

## 二、对应关系

### 事件

| Scratch | 内部积木 | 语义 |
|---|---|---|
| 当绿旗被点击 | `event.tick` | 一致：引擎启动时执行一次 |
| 当按下某键 | `event.key` | 一致：按键**按下瞬间**触发（边沿检测，按住不重复触发） |
| 当收到广播 | `event.broadcast` | 一致 |
| when damaged（扩展） | `event.damage` | 血量下降即触发 |
| when chat contains（扩展） | `event.chat` | 关键词子串匹配 |

### 控制

| Scratch | 内部积木 | 语义 |
|---|---|---|
| 如果 | `control.if` | 一致 |
| 如果否则 | `control.if_else` | 一致 |
| 重复 n 次 | `control.repeat` | 一致 |
| 永久循环 | `control.forever` | 一致 |
| 重复直到 | `control.repeat_until` | 一致 |
| 等待 n 秒 | `control.wait` | **已换算**：秒 × 20 = 游戏 tick |
| 等待直到 | `control.wait_until` | 一致（每 tick 重算一次条件） |
| 停止 | `control.stop` / `control.stop_all` | **已对齐**：`STOP_OPTION=all` 映射为「停止全部」（引擎整体停机，按 F6 重启）；其余停止当前脚本 |

### 运算

| Scratch | 内部积木 | 语义 |
|---|---|---|
| `<` `>` `=` | `operator.lt/gt/eq` | 一致 |
| `[ ] ≥ [ ]`（扩展） | `operator.gte` | Scratch 原生缺此积木，用扩展补 |
| 加减乘除 / 取余 | `operator.*` | 一致；除数为 0 返回 0（Scratch 返回 Infinity，差异已知） |
| 与 / 或 / 非 | `operator.and/or/not` | 一致（非短路求值，与 Scratch 相同） |
| 随机数 | `operator.random` | 一致：闭区间，按整数取 |
| 取整 | `operator.round` | 一致：四舍五入 |
| 拼接 | `operator.join` | 一致 |

### 变量与列表

| Scratch | 内部积木 | 语义 |
|---|---|---|
| 设定变量 / 读取变量 | `data.set` / `data.get` | **已对齐**：导入时自动设为全局作用域，对应 Scratch 的角色级共享 |
| 将变量增加 n | `data.set` + `operator.add` | **已展开**：读取 → 相加 → 写回三步 |
| 列表 8 个操作 | `list.*` | 一致：索引从 1 开始，越界读返回空串 |

### 游戏交互（扩展积木）

| 扩展 | 内部积木 |
|---|---|
| mine / move / stop / attack / attackNearest / attackNearestHostile / use / openContainer / moveToward / look / lookAt / selectSlot | `action.*` |
| blockInFront / onGround / health / food / posX / posY / posZ / selectedSlot / nearestEntity(+XYZ) / nearestHostile / itemCount | `world.*` |
| log | `debug.log` |

---

## 三、本轮修正的偏差（8 处）

这些是"看起来映射上了、实际行为不对"的问题，全部已修并加了自动断言：

| # | 问题 | 表现 | 修复 |
|---|---|---|---|
| 1 | **等待单位错 20 倍** | Scratch 写"等待 1 秒"，实际等 1 tick（0.05 秒） | 秒 × 20 换算，字面量与表达式都处理 |
| 2 | **变量作用域错位** | 多个脚本共享变量时，各自独立、互不可见 | 导入时 `SCOPE` 自动设为 `global` |
| 3 | **按键名丢失** | 选空格键，实际变成 F 键（字段名 `KEY_OPTION` 未映射） | `KEY_OPTION → KEY`，并支持任意字母与方向键 |
| 4 | **广播名丢失** | 「当收到广播 [开始]」永远不触发 | `BROADCAST_OPTION → MSG` |
| 5 | **运算输入名错位** | 随机数 / 取整 / 拼接的参数读不到，恒用默认值 | `FROM/TO → A/B`、`NUM → A`、`STRING1/2 → A/B` |
| 6 | **扩展菜单参数丢失** | `move` 的方向与开关设了不生效，参数面板多一行英文 `ONOFF` | `ONOFF → PRESSED`；并按规格决定写入字段还是输入 |
| 7 | **物品计数参数丢失** | `count of [minecraft:stone]` 恒为 0 | `ITEM` 只在列表积木里改名 `VALUE`，扩展积木保持 `ITEM` |
| 8 | **「停止全部」等同「停止当前」** | 期望停掉所有脚本，实际只有发起的那一个停下，其余继续跑 | 按 `STOP_OPTION=all` 拆出新积木 `control.stop_all`；引擎整体停机、同步关闭开关并提示按 F6 重启 |

其中 1–8 都属于"静默失效"——不报错、不崩溃，只是行为不对，是最难排查的一类。第 1 项已由自检断言覆盖（"等待 2 秒必须编译出 40 tick"），第 6、7 项由自检的菜单/命名断言覆盖，第 8 项由自检的「停止全部」三段断言（编译出 `STOP_ALL`、执行后发出停机请求、解析后不残留参数）覆盖。

---

## 四、仍未对齐的项

| 项 | 状态 | 原因 |
|---|---|---|
| 停止其他脚本（other scripts in sprite） | 降级 | Scratch 无「角色内其他脚本」概念，导入时按「停止当前脚本」处理并打日志 |
| 克隆（create clone） | 不支持 | 与内部 `并行分支` 语义不等价（克隆有独立状态与生命周期） |
| Scratch 自定义积木 | 不支持导入 | 参数通过 `mutation.argumentids` 传递，通道模型与内部 `$p1~$p3` 不同 |
| 广播并等待 | 不支持 | 只有单向广播 |
| 并行分支 `control.spawn` | 无法从 sb3 导入 | Scratch 没有对应积木；并行靠"多个帽子脚本"实现，这本来就更符合 Scratch 习惯 |
| `func.call` | 无法从 sb3 导入 | 同上，依赖自定义积木 |
| 运动 / 外观 / 声音 / 画笔 | 不支持 | 2D 舞台坐标系无法对应 3D 世界 |
| 计时器、询问并等待、鼠标键盘状态 | 不支持 | 与游戏内场景无关 |
| 除数为 0 | 返回 0 | Scratch 返回 Infinity，为避免污染后续计算而有意不同 |

---

## 五、验证方式

自动断言（每次启动执行，任一项失败会停用引擎并打日志）：

```
[script] 自检通过：15 项检查全部正常
```

其中 sb3 相关三项分别为：

**切片**（绿旗 → 重复 2 次 → 如果(1<2) → 挖掘 → 等待 2 秒）
1. 解析出 1 个脚本，帽子积木映射为 `event.tick`
2. 编译指令流中存在 `CALL_ACTION action.mine`
3. 编译指令流中存在常量 `40`（即 2 秒被正确换算成 40 tick）

**菜单**（`hkscript_move` 带 `DIRECTION`/`ONOFF` 菜单影子积木）
4. `DIRECTION` 写入字段且值为 `back`
5. `ONOFF` 改名为 `PRESSED` 并写入输入
6. 列表积木的 `ITEM` 改名为 `VALUE`，而 `itemCount` 的 `ITEM` 保持原名

**停止全部**（`control_stop` 带 `STOP_OPTION=all`）
7. 编译出 `STOP_ALL` 而非 `HALT`
8. 执行后向引擎发出停机请求
9. 解析结果不残留 `STOP_OPTION` 参数

人工验证：按 `turbowarp/README.md` 拼一个项目导出，观察游戏内行为是否与预期一致。

---

## 五、扩展积木映射（`hkscript_*`）

TurboWarp 扩展 `hkscript-extension.js` 提供这些积木，导出后在 sb3 里表现为 `hkscript_<opcode>`。


### 事件

| sb3 opcode | 内部积木 |
|---|---|
| `hkscript_whenDamaged` | `event.damage` |
| `hkscript_whenChat` | `event.chat` |

### 运算

| sb3 opcode | 内部积木 |
|---|---|
| `hkscript_gte` | `operator.gte` |

### 世界查询

| sb3 opcode | 内部积木 |
|---|---|
| `hkscript_blockInFront` | `world.block_in_front` |
| `hkscript_health` | `world.health` |
| `hkscript_food` | `world.food` |
| `hkscript_posX` | `world.pos_x` |
| `hkscript_posY` | `world.pos_y` |
| `hkscript_posZ` | `world.pos_z` |
| `hkscript_selectedSlot` | `world.selected_slot` |
| `hkscript_nearestEntity` | `world.nearest_entity` |
| `hkscript_nearestEntityX` | `world.nearest_entity_x` |
| `hkscript_nearestEntityY` | `world.nearest_entity_y` |
| `hkscript_nearestEntityZ` | `world.nearest_entity_z` |
| `hkscript_nearestHostile` | `world.nearest_hostile` |
| `hkscript_onGround` | `world.on_ground` |
| `hkscript_itemCount` | `world.item_count` |
| `hkscript_heldItem` | `world.held_item` |
| `hkscript_heldDurability` | `world.held_durability` |
| `hkscript_frontBlock` | `world.front_block` |
| `hkscript_blockBelow` | `world.block_below` |
| `hkscript_blockAt` | `world.block_at` |
| `hkscript_isDay` | `world.is_day` |
| `hkscript_dimension` | `world.dimension` |
| `hkscript_inWater` | `world.in_water` |
| `hkscript_inLava` | `world.in_lava` |
| `hkscript_nearestEntityName` | `world.nearest_entity_name` |
| `hkscript_xpLevel` | `world.xp_level` |
| `hkscript_inventoryFull` | `world.inventory_full` |
| `hkscript_entityCount` | `world.entity_count` |
| `hkscript_biome` | `world.biome` |
| `hkscript_ping` | `world.ping` |

### 动作

| sb3 opcode | 内部积木 |
|---|---|
| `hkscript_tap` | `action.tap` |
| `hkscript_respawn` | `action.respawn` |
| `hkscript_mouse` | `action.mouse` |
| `hkscript_click` | `action.click` |
| `hkscript_chat` | `action.chat` |
| `hkscript_command` | `action.command` |
| `hkscript_attackNearest` | `action.attack_nearest` |
| `hkscript_attackNearestHostile` | `action.attack_nearest_hostile` |
| `hkscript_openContainer` | `action.open_container` |
| `hkscript_moveToward` | `action.move_toward` |
| `hkscript_look` | `action.look` |
| `hkscript_lookAt` | `action.look_at` |
| `hkscript_stop` | `action.stop` |
| `hkscript_placeBlock` | `action.place_block` |
| `hkscript_sleep` | `action.sleep` |

### 调试

| sb3 opcode | 内部积木 |
|---|---|
| `hkscript_log` | `debug.log` |
