# 积木与 opcode 对应表

> 由脚本从 `Sb3Mapping.java` 与 `hkscript-extension.js` 直接生成，不是手写。
> 表格顺序与 TurboWarp 积木面板一致，改完扩展后重跑生成即可。

## 一、统计

| 项 | 数量 |
|---|---|
| 扩展积木 | 60 |
| 其中事件帽子（宿主触发） | 3 |
| 需要动作实现 | 57 |
| 映射表条目 | 114 |
| 其中原生积木 | 54 |

## 二、扩展积木

| # | opcode（带前缀） | 内部类型 | 外形 |
|---|---|---|---|
| 1 | `hkscript_whenDamaged` | `event.damage` | HAT |
| 2 | `hkscript_whenDeath` | `event.death` | HAT |
| 3 | `hkscript_whenChat` | `event.chat` | HAT |
| 4 | `hkscript_key` | `action.key` | COMMAND |
| 5 | `hkscript_mouse` | `action.mouse` | COMMAND |
| 6 | `hkscript_click` | `action.click` | COMMAND |
| 7 | `hkscript_tap` | `action.tap` | COMMAND |
| 8 | `hkscript_stop` | `action.stop` | COMMAND |
| 9 | `hkscript_moveToward` | `action.move_toward` | COMMAND |
| 10 | `hkscript_look` | `action.look` | COMMAND |
| 11 | `hkscript_lookAt` | `action.look_at` | COMMAND |
| 12 | `hkscript_placeBlock` | `action.place_block` | COMMAND |
| 13 | `hkscript_breakWait` | `action.break_wait` | COMMAND |
| 14 | `hkscript_isMining` | `world.is_mining` | BOOLEAN |
| 15 | `hkscript_miningProgress` | `world.mining_progress` | REPORTER |
| 16 | `hkscript_sleep` | `action.sleep` | COMMAND |
| 17 | `hkscript_attackNearest` | `action.attack_nearest` | COMMAND |
| 18 | `hkscript_attackNearestHostile` | `action.attack_nearest_hostile` | COMMAND |
| 19 | `hkscript_openContainer` | `action.open_container` | COMMAND |
| 20 | `hkscript_openContainerAt` | `action.open_container_at` | COMMAND |
| 21 | `hkscript_containerSlots` | `world.container_slots` | REPORTER |
| 22 | `hkscript_slotItem` | `world.slot_item` | REPORTER |
| 23 | `hkscript_slotAmount` | `world.slot_amount` | REPORTER |
| 24 | `hkscript_firstSlotOf` | `world.first_slot_of` | REPORTER |
| 25 | `hkscript_clickSlot` | `action.click_slot` | COMMAND |
| 26 | `hkscript_quickMoveSlot` | `action.quick_move_slot` | COMMAND |
| 27 | `hkscript_closeContainer` | `action.close_container` | COMMAND |
| 28 | `hkscript_chat` | `action.chat` | COMMAND |
| 29 | `hkscript_command` | `action.command` | COMMAND |
| 30 | `hkscript_respawn` | `action.respawn` | COMMAND |
| 31 | `hkscript_health` | `world.health` | REPORTER |
| 32 | `hkscript_food` | `world.food` | REPORTER |
| 33 | `hkscript_xpLevel` | `world.xp_level` | REPORTER |
| 34 | `hkscript_inventoryFull` | `world.inventory_full` | BOOLEAN |
| 35 | `hkscript_onGround` | `world.on_ground` | BOOLEAN |
| 36 | `hkscript_posX` | `world.pos_x` | REPORTER |
| 37 | `hkscript_posY` | `world.pos_y` | REPORTER |
| 38 | `hkscript_posZ` | `world.pos_z` | REPORTER |
| 39 | `hkscript_selectedSlot` | `world.selected_slot` | REPORTER |
| 40 | `hkscript_heldItem` | `world.held_item` | REPORTER |
| 41 | `hkscript_heldDurability` | `world.held_durability` | REPORTER |
| 42 | `hkscript_isDay` | `world.is_day` | BOOLEAN |
| 43 | `hkscript_dimension` | `world.dimension` | REPORTER |
| 44 | `hkscript_biome` | `world.biome` | REPORTER |
| 45 | `hkscript_inWater` | `world.in_water` | BOOLEAN |
| 46 | `hkscript_inLava` | `world.in_lava` | BOOLEAN |
| 47 | `hkscript_blockInFront` | `world.block_in_front` | BOOLEAN |
| 48 | `hkscript_blockBelow` | `world.block_below` | REPORTER |
| 49 | `hkscript_blockAt` | `world.block_at` | REPORTER |
| 50 | `hkscript_frontBlock` | `world.front_block` | REPORTER |
| 51 | `hkscript_nearestEntity` | `world.nearest_entity` | REPORTER |
| 52 | `hkscript_nearestEntityX` | `world.nearest_entity_x` | REPORTER |
| 53 | `hkscript_nearestEntityY` | `world.nearest_entity_y` | REPORTER |
| 54 | `hkscript_nearestEntityZ` | `world.nearest_entity_z` | REPORTER |
| 55 | `hkscript_nearestEntityName` | `world.nearest_entity_name` | REPORTER |
| 56 | `hkscript_nearestHostile` | `world.nearest_hostile` | REPORTER |
| 57 | `hkscript_entityCount` | `world.entity_count` | REPORTER |
| 58 | `hkscript_itemCount` | `world.item_count` | REPORTER |
| 59 | `hkscript_ping` | `world.ping` | REPORTER |
| 60 | `hkscript_log` | `debug.log` | COMMAND |

## 三、原生积木

这些是 Scratch 自带积木，导入后由 scratch-vm 直接执行，不经过 Java 动作层。

| 内部类型 | sb3 opcode |
|---|---|
| `event.tick` | `event_whenflagclicked` |
| `event.key` | `event_whenkeypressed` |
| `event.broadcast` | `event_whenbroadcastreceived` |
| `control.if` | `control_if` |
| `control.if_else` | `control_if_else` |
| `control.repeat` | `control_repeat` |
| `control.forever` | `control_forever` |
| `control.repeat_until` | `control_repeat_until` |
| `control.wait` | `control_wait` |
| `control.wait_until` | `control_wait_until` |
| `control.stop` | `control_stop` |
| `operator.lt` | `operator_lt` |
| `operator.gt` | `operator_gt` |
| `operator.eq` | `operator_equals` |
| `operator.add` | `operator_add` |
| `operator.subtract` | `operator_subtract` |
| `operator.multiply` | `operator_multiply` |
| `operator.divide` | `operator_divide` |
| `operator.mod` | `operator_mod` |
| `operator.and` | `operator_and` |
| `operator.or` | `operator_or` |
| `operator.not` | `operator_not` |
| `operator.random` | `operator_random` |
| `operator.round` | `operator_round` |
| `operator.join` | `operator_join` |
| `operator.letter_of` | `operator_letter_of` |
| `operator.length` | `operator_length` |
| `operator.contains` | `operator_contains` |
| `operator.mathop` | `operator_mathop` |
| `data.set` | `data_setvariableto` |
| `data.get` | `data_variable` |
| `data.change` | `data_changevariableby` |
| `list.add` | `data_addtolist` |
| `list.delete` | `data_deleteoflist` |
| `list.clear` | `data_deletealloflist` |
| `list.insert` | `data_insertatlist` |
| `list.replace` | `data_replaceitemoflist` |
| `list.item` | `data_itemoflist` |
| `list.length` | `data_lengthoflist` |
| `list.contains` | `data_listcontainsitem` |
| `list.item_num` | `data_itemnumoflist` |
| `list.contents` | `data_listcontents` |
| `func.call` | `procedures_call` |
| `action.broadcast` | `event_broadcast` |
| `control.broadcast_wait` | `event_broadcastandwait` |
| `input.key_pressed` | `sensing_keypressed` |
| `sensing.timer` | `sensing_timer` |
| `sensing.reset_timer` | `sensing_resettimer` |
| `sensing.current` | `sensing_current` |
| `sensing.days_since_2000` | `sensing_dayssince2000` |
| `debug.say` | `looks_say` |
| `debug.think` | `looks_think` |
| `debug.say_for` | `looks_sayforsecs` |
| `debug.think_for` | `looks_thinkforsecs` |

## 四、维护提示

- 一个内部类型只能对应一个 opcode。曾把「将变量增加」与「将变量设为」映射到同一类型，
  结果查看器把「增加 1」显示成「设为 1」——这类错误不报错，只是语义偏移。
- 扩展积木的 opcode 不能随便改名：模组按 `hkscript_<opcode>` 查找映射。
- 扩展里每个非帽子积木都必须在 `NativeRegistry` 有对应实现，否则运行时静默返回空值。
  改动后的核对方式：比对 `hkscript_*` 映射到的类型与 action 包里的注册名。
- **参数的 `menu` 必须写菜单名（字符串），不能内联 items 对象**。scratch-vm 用
  `menuInfo[argInfo.menu]` 查表，传对象会得到 undefined 并抛错——积木在 TurboWarp 里
  直接拖不出来，日志只有一行 `Error parsing block`。
