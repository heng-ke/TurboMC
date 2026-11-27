# 积木与 opcode 对应表

> 由 `node tools/build-mapping-report.js` 从 `Sb3Mapping.java` 与 `turbowarp/src/blocks.data.js`
> 生成，**不要手改**（CI 的 `--check` 会拦下过期版本）。表格顺序与 TurboWarp 积木面板一致。

## 一、统计

| 项 | 数量 |
|---|---|
| 扩展积木 | 91 |
| 其中事件帽子（宿主触发） | 4 |
| 需要动作实现 | 87 |
| 映射表条目 | 158 |
| 其中原生积木 | 67 |

## 二、扩展积木

| # | opcode（带前缀） | 内部类型 | 外形 |
|---|---|---|---|
| | **事件** | | |
| 1 | `turbomc_whenDamaged` | `event.damage` | HAT |
| 2 | `turbomc_whenDeath` | `event.death` | HAT |
| 3 | `turbomc_whenScreenOpen` | `event.screen_open` | HAT |
| 4 | `turbomc_whenChat` | `event.chat` | HAT |
| | **移动与视角** | | |
| 5 | `turbomc_moveToward` | `action.move_toward` | COMMAND |
| 6 | `turbomc_look` | `action.look` | COMMAND |
| 7 | `turbomc_lookAt` | `action.look_at` | COMMAND |
| 8 | `turbomc_lookSmooth` | `action.look_smooth` | COMMAND |
| 9 | `turbomc_lookAtSmooth` | `action.look_at_smooth` | COMMAND |
| 10 | `turbomc_stop` | `action.stop` | COMMAND |
| | **输入** | | |
| 11 | `turbomc_key` | `action.key` | COMMAND |
| 12 | `turbomc_click` | `action.click` | COMMAND |
| 13 | `turbomc_tap` | `action.tap` | COMMAND |
| 14 | `turbomc_mouseDown` | `input.mouse_button_down` | BOOLEAN |
| | **挖掘** | | |
| 15 | `turbomc_breakWait` | `action.break_wait` | COMMAND |
| 16 | `turbomc_isMining` | `world.is_mining` | BOOLEAN |
| 17 | `turbomc_miningProgress` | `world.mining_progress` | REPORTER |
| | **容器** | | |
| 18 | `turbomc_openContainerAt` | `action.open_container_at` | COMMAND |
| 19 | `turbomc_containerSlots` | `world.container_slots` | REPORTER |
| 20 | `turbomc_slotField` | `world.slot_field` | REPORTER |
| 21 | `turbomc_screenJson` | `world.screen_json` | REPORTER |
| 22 | `turbomc_firstSlotOf` | `world.first_slot_of` | REPORTER |
| 23 | `turbomc_clickSlot` | `action.click_slot` | COMMAND |
| 24 | `turbomc_quickMoveSlot` | `action.quick_move_slot` | COMMAND |
| 25 | `turbomc_closeContainer` | `action.close_container` | COMMAND |
| | **玩家状态** | | |
| 26 | `turbomc_playerField` | `world.player_field` | REPORTER |
| 27 | `turbomc_onGround` | `world.on_ground` | BOOLEAN |
| 28 | `turbomc_heldItemField` | `world.held_item_field` | REPORTER |
| 29 | `turbomc_inventoryJson` | `world.inventory_json` | REPORTER |
| 30 | `turbomc_inventoryFull` | `world.inventory_full` | BOOLEAN |
| 31 | `turbomc_playerJson` | `world.player_json` | REPORTER |
| 32 | `turbomc_itemCount` | `world.item_count` | REPORTER |
| 33 | `turbomc_respawn` | `action.respawn` | COMMAND |
| | **世界查询** | | |
| 34 | `turbomc_isDay` | `world.is_day` | BOOLEAN |
| 35 | `turbomc_biome` | `world.biome` | REPORTER |
| 36 | `turbomc_inWater` | `world.in_water` | BOOLEAN |
| 37 | `turbomc_inLava` | `world.in_lava` | BOOLEAN |
| 38 | `turbomc_blockInFront` | `world.block_in_front` | BOOLEAN |
| 39 | `turbomc_blockBelow` | `world.block_below` | REPORTER |
| 40 | `turbomc_blockAt` | `world.block_at` | REPORTER |
| 41 | `turbomc_blockJson` | `world.block_json` | REPORTER |
| 42 | `turbomc_playersJson` | `world.players_json` | REPORTER |
| 43 | `turbomc_scanJson` | `world.scan_json` | REPORTER |
| 44 | `turbomc_snapshotKeys` | `world.snapshot_keys` | REPORTER |
| 45 | `turbomc_worldJson` | `world.world_json` | REPORTER |
| 46 | `turbomc_frontBlock` | `world.front_block` | REPORTER |
| 47 | `turbomc_aimPos` | `world.aim_pos` | REPORTER |
| | **实体查询** | | |
| 48 | `turbomc_nearestEntityField` | `world.nearest_entity_field` | REPORTER |
| 49 | `turbomc_entityCount` | `world.entity_count` | REPORTER |
| 50 | `turbomc_entitiesJson` | `world.entities_json` | REPORTER |
| | **显示与日志** | | |
| 51 | `turbomc_drawFrame` | `region.draw_frame` | COMMAND |
| 52 | `turbomc_clearFrames` | `region.clear_frames` | COMMAND |
| 53 | `turbomc_log` | `debug.log` | COMMAND |
| | **聊天与 AI** | | |
| 54 | `turbomc_chat` | `action.chat` | COMMAND |
| 55 | `turbomc_command` | `action.command` | COMMAND |
| 56 | `turbomc_lastChat` | `query.last_chat` | REPORTER |
| 57 | `turbomc_chatHistory` | `query.chat_history` | REPORTER |
| 58 | `turbomc_triggerChat` | `query.trigger_chat` | REPORTER |
| 59 | `turbomc_aiAsk` | `ai.ask` | COMMAND |
| 60 | `turbomc_aiReply` | `ai.reply` | REPORTER |
| 61 | `turbomc_aiReset` | `ai.reset` | COMMAND |
| 62 | `turbomc_aiSystem` | `ai.system` | COMMAND |
| | **JSON · 校验与新建** | | |
| 63 | `turbomc_jsonIs` | `json.is` | BOOLEAN |
| 64 | `turbomc_jsonNew` | `json.new` | REPORTER |
| 65 | `turbomc_jsonLength` | `json.length` | REPORTER |
| 66 | `turbomc_jsonHasKey` | `json.has_key` | BOOLEAN |
| 67 | `turbomc_jsonGetAll` | `json.get_all` | REPORTER |
| 68 | `turbomc_jsonErrMsg` | `json.err_msg` | REPORTER |
| 69 | `turbomc_jsonMinify` | `json.minify` | REPORTER |
| | **JSON · 对象与路径** | | |
| 70 | `turbomc_jsonGet` | `json.get` | REPORTER |
| 71 | `turbomc_jsonSet` | `json.set` | REPORTER |
| 72 | `turbomc_jsonDelete` | `json.delete` | REPORTER |
| 73 | `turbomc_jsonGetPath` | `json.get_path` | REPORTER |
| 74 | `turbomc_jsonSetPath` | `json.set_path` | REPORTER |
| | **JSON · 数组读写** | | |
| 75 | `turbomc_jsonArrayPush` | `json.array_push` | REPORTER |
| 76 | `turbomc_jsonArraySet` | `json.array_set` | REPORTER |
| 77 | `turbomc_jsonArrayInsert` | `json.array_insert` | REPORTER |
| 78 | `turbomc_jsonArrayDelete` | `json.array_delete` | REPORTER |
| 79 | `turbomc_jsonArrayRemoveAll` | `json.array_remove_all` | REPORTER |
| 80 | `turbomc_jsonArrayItemNum` | `json.array_item_num` | REPORTER |
| 81 | `turbomc_jsonArrayFromTo` | `json.array_fromto` | REPORTER |
| 82 | `turbomc_jsonArraySetLen` | `json.array_setlen` | REPORTER |
| | **JSON · 数组变换** | | |
| 83 | `turbomc_jsonArrayConcat` | `json.array_concat` | REPORTER |
| 84 | `turbomc_jsonArrayMapField` | `json.array_map_field` | REPORTER |
| 85 | `turbomc_jsonArrayCreate` | `json.array_create` | REPORTER |
| 86 | `turbomc_jsonArrayJoin` | `json.array_join` | REPORTER |
| 87 | `turbomc_jsonArraySort` | `json.array_sort` | REPORTER |
| 88 | `turbomc_jsonArrayTextFilter` | `json.array_textfilter` | REPORTER |
| 89 | `turbomc_jsonArrayAnalysis` | `json.array_analysis` | REPORTER |
| | **JSON · 与列表互通** | | |
| 90 | `turbomc_jsonVmGetList` | `json.vm_getlist` | REPORTER |
| 91 | `turbomc_jsonVmSetList` | `json.vm_setlist` | COMMAND |

## 三、原生积木

这些是 Scratch 自带积木，导入后由 scratch-vm 直接执行，不经过 Java 动作层。

| 内部类型 | sb3 opcode |
|---|---|
| `event.tick` | `event_whenflagclicked` |
| `event.key` | `event_whenkeypressed` |
| `event.broadcast` | `event_whenbroadcastreceived` |
| `event.when_greater` | `event_whengreaterthan` |
| `control.if` | `control_if` |
| `control.if_else` | `control_if_else` |
| `control.repeat` | `control_repeat` |
| `control.forever` | `control_forever` |
| `control.repeat_until` | `control_repeat_until` |
| `control.wait` | `control_wait` |
| `control.wait_until` | `control_wait_until` |
| `control.stop` | `control_stop` |
| `control.while` | `control_while` |
| `control.for_each` | `control_for_each` |
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
| `data.show_var` | `data_showvariable` |
| `data.hide_var` | `data_hidevariable` |
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
| `data.show_list` | `data_showlist` |
| `data.hide_list` | `data_hidelist` |
| `func.call` | `procedures_call` |
| `func.arg_string` | `argument_reporter_string_number` |
| `func.arg_boolean` | `argument_reporter_boolean` |
| `action.broadcast` | `event_broadcast` |
| `control.broadcast_wait` | `event_broadcastandwait` |
| `input.key_pressed` | `sensing_keypressed` |
| `input.mouse_down` | `sensing_mousedown` |
| `sensing.timer` | `sensing_timer` |
| `sensing.reset_timer` | `sensing_resettimer` |
| `sensing.current` | `sensing_current` |
| `sensing.days_since_2000` | `sensing_dayssince2000` |
| `sensing.username` | `sensing_username` |
| `sensing.ask` | `sensing_askandwait` |
| `sensing.answer` | `sensing_answer` |
| `debug.say` | `looks_say` |
| `debug.think` | `looks_think` |
| `debug.say_for` | `looks_sayforsecs` |
| `debug.think_for` | `looks_thinkforsecs` |

## 四、维护提示

- **面板分类靠 `Scratch.BlockType.LABEL`**：标题块只有 `blockType` 与 `text`，没有 opcode，
  不进本表、不进 `.sb3`、不参与执行。数组顺序就是面板顺序，改顺序不影响任何已导出工程。
  运行时副本（`assets/turbomc/vm/turbomc-extension.es5.js`）刻意不带标题——内嵌的
  `vm.es5.js` 不支持 label；`vm-bootstrap.js` 也对无 opcode 的条目直接跳过。
- 一个内部类型只能对应一个 opcode。曾把「将变量增加」与「将变量设为」映射到同一类型，
  结果查看器把「增加 1」显示成「设为 1」——这类错误不报错，只是语义偏移。
- 扩展 id 是 `turbomc`，所以 opcode 前缀是 `turbomc_`；改 id 等于让所有已导出工程失效。
- 扩展里每个非帽子积木都必须在 `NativeRegistry` 有对应实现，否则运行时静默返回空值。
  改动后的核对方式：比对 `turbomc_*` 映射到的类型与 action 包里的注册名。
- **参数的 `menu` 必须写菜单名（字符串），不能内联 items 对象**。scratch-vm 用
  `menuInfo[argInfo.menu]` 查表，传对象会得到 undefined 并抛错——积木在 TurboWarp 里
  直接拖不出来，日志只有一行 `Error parsing block`。
- 不提供攻击积木：自动选目标会打掉落物/经验球，服务端判定无效实体直接断开连接。
  攻击用「点击左键」，走原版射线。
