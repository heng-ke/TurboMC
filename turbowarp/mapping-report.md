# 积木与 opcode 对应表

> 由脚本从 `Sb3Mapping.java` 与 `hkscript-extension.js` 直接生成，不是手写。
> 改完映射或扩展后重跑生成即可，避免文档与代码不一致。

## 一、统计

| 项 | 数量 |
|---|---|
| 扩展积木 | 53 |
| 映射表条目 | 107 |
| 其中原生积木 | 54 |
| 内部类型（去重） | 107 |

## 二、扩展积木

| opcode（带前缀） | 内部类型 | 外形 |
|---|---|---|
| `hkscript_whenDamaged` | `event.damage` | HAT |
| `hkscript_whenDeath` | `event.death` | HAT |
| `hkscript_whenChat` | `event.chat` | HAT |
| `hkscript_key` | `action.key` | COMMAND |
| `hkscript_respawn` | `action.respawn` | COMMAND |
| `hkscript_mouse` | `action.mouse` | COMMAND |
| `hkscript_click` | `action.click` | COMMAND |
| `hkscript_tap` | `action.tap` | COMMAND |
| `hkscript_chat` | `action.chat` | COMMAND |
| `hkscript_command` | `action.command` | COMMAND |
| `hkscript_stop` | `action.stop` | COMMAND |
| `hkscript_attackNearest` | `action.attack_nearest` | COMMAND |
| `hkscript_attackNearestHostile` | `action.attack_nearest_hostile` | COMMAND |
| `hkscript_openContainer` | `action.open_container` | COMMAND |
| `hkscript_moveToward` | `action.move_toward` | COMMAND |
| `hkscript_look` | `action.look` | COMMAND |
| `hkscript_lookAt` | `action.look_at` | COMMAND |
| `hkscript_blockInFront` | `world.block_in_front` | BOOLEAN |
| `hkscript_onGround` | `world.on_ground` | BOOLEAN |
| `hkscript_gte` | `operator.gte` | BOOLEAN |
| `hkscript_health` | `world.health` | REPORTER |
| `hkscript_food` | `world.food` | REPORTER |
| `hkscript_posX` | `world.pos_x` | REPORTER |
| `hkscript_posY` | `world.pos_y` | REPORTER |
| `hkscript_posZ` | `world.pos_z` | REPORTER |
| `hkscript_selectedSlot` | `world.selected_slot` | REPORTER |
| `hkscript_nearestEntity` | `world.nearest_entity` | REPORTER |
| `hkscript_nearestEntityX` | `world.nearest_entity_x` | REPORTER |
| `hkscript_nearestEntityY` | `world.nearest_entity_y` | REPORTER |
| `hkscript_nearestEntityZ` | `world.nearest_entity_z` | REPORTER |
| `hkscript_nearestHostile` | `world.nearest_hostile` | REPORTER |
| `hkscript_itemCount` | `world.item_count` | REPORTER |
| `hkscript_log` | `debug.log` | COMMAND |
| `hkscript_heldItem` | `world.held_item` | REPORTER |
| `hkscript_heldDurability` | `world.held_durability` | REPORTER |
| `hkscript_frontBlock` | `world.front_block` | REPORTER |
| `hkscript_blockBelow` | `world.block_below` | REPORTER |
| `hkscript_blockAt` | `world.block_at` | REPORTER |
| `hkscript_isDay` | `world.is_day` | BOOLEAN |
| `hkscript_dimension` | `world.dimension` | REPORTER |
| `hkscript_inWater` | `world.in_water` | BOOLEAN |
| `hkscript_inLava` | `world.in_lava` | BOOLEAN |
| `hkscript_nearestEntityName` | `world.nearest_entity_name` | REPORTER |
| `hkscript_xpLevel` | `world.xp_level` | REPORTER |
| `hkscript_inventoryFull` | `world.inventory_full` | BOOLEAN |
| `hkscript_entityCount` | `world.entity_count` | REPORTER |
| `hkscript_biome` | `world.biome` | REPORTER |
| `hkscript_ping` | `world.ping` | REPORTER |
| `hkscript_placeBlock` | `action.place_block` | COMMAND |
| `hkscript_sleep` | `action.sleep` | COMMAND |
| `hkscript_breakWait` | `action.break_wait` | COMMAND |
| `hkscript_isMining` | `world.is_mining` | BOOLEAN |
| `hkscript_miningProgress` | `world.mining_progress` | REPORTER |

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

- 一个内部类型只能对应一个 opcode。曾把「将变量增加」和「将变量设为」映射到
  同一类型，结果查看器把「增加 1」显示成「设为 1」——这类错误不报错，只是语义偏移。
- 扩展积木的 opcode 不能随便改名：模组按 `hkscript_<opcode>` 查找映射。
