# 用 TurboWarp 编写 hk-script 脚本

用真正的 Scratch 编辑器写脚本，再导入模组执行。编辑体验（撤销、复制粘贴、注释、搜索）远超游戏内编辑器。

## 一、加载扩展

1. 打开 <https://turbowarp.org/editor>
2. 左下角「添加扩展」（积木面板底部的 ＋）
3. 滚到底部选「Custom Extension / 自定义扩展」
4. 选 **File** 标签，加载本目录下的 `hkscript-extension.js`
5. 积木面板出现紫色的 **Hk Script** 分类

> 也可以用 **Text** 标签把 js 内容粘贴进去，效果相同。

## 二、扩展积木清单

**事件**
| 积木 | 说明 |
|---|---|
| when damaged | 玩家受伤时触发 |
| when chat contains [ ] | 收到含关键词的聊天时触发 |

**动作**
| 积木 | 说明 |
|---|---|
| mine block in front | 挖掘准星所指方块 |
| move [方向] [true/false] | 按方向按住 / 松开移动键（7 个方向） |
| stop moving | 释放所有移动键 |
| attack crosshair target | 攻击准星指向的实体 |
| attack nearest entity | 转向并攻击最近实体 |
| attack nearest hostile | 转向并攻击最近敌对生物 |
| use item / block | 右键使用物品或方块 |
| open nearest container | 打开 4 格内最近的容器 |
| move toward x:[ ] z:[ ] | 朝目标坐标前进（直线，非寻路） |
| set view yaw:[ ] pitch:[ ] | 直接设定视角角度 |
| look at x:[ ] y:[ ] z:[ ] | 视角看向世界坐标 |
| select hotbar slot [ ] | 切换快捷栏 1–9 |

**查询**
| 积木 | 说明 |
|---|---|
| block in front? | 准星是否指向实体方块 |
| on ground? | 是否站在地面 |
| [ ] >= [ ] | 大于等于（Scratch 原生没有） |
| health / food level | 血量 / 饱食度 |
| x / y / z position | 玩家坐标 |
| selected slot | 当前快捷栏槽位 |
| nearest entity distance / x / y / z | 最近实体的距离与坐标 |
| nearest hostile distance | 最近敌对生物距离 |
| count of [ ] | 背包中指定物品数量（如 `minecraft:stone`） |

**调试**
| 积木 | 说明 |
|---|---|
| log [ ] | 输出到游戏日志 |

## 三、可用的原生积木

**事件**：当绿旗被点击、当按下某键、当收到广播
**控制**：如果、如果否则、重复、永久循环、重复直到、等待、等待直到、停止
**运算**：`< > =`、加减乘除、取余、与或非、随机数、取整、拼接
**变量**：设定、读取、增加（Scratch 的变量即模组的全局变量）
**列表**：添加、删除、插入、替换、清空、取项、长度、包含
**广播**：广播、当收到广播

## 四、不支持的积木

- **运动 / 外观 / 声音 / 画笔**：Scratch 是 480×360 的 2D 舞台，模组是 3D 世界且服务器权威，强行映射会得到"看着像、落点全错"的行为。
- **克隆**：`create clone` 与模组的 `并行分支` 语义不等价。
- **自定义积木**：参数通道模型不同，暂不导入。

这些积木在导入时会被跳过，日志里会打印 `[sb3] 未映射的积木，已跳过: <opcode>`。

## 五、导出与导入

1. TurboWarp 菜单 **File → Save to your computer**，得到 `.sb3`
2. 把 `.sb3` 放进 `config/hk-script/imports/`
3. 游戏内按 **F7** → 脚本列表 → 点 **导入**
4. 导入后的脚本默认**未启用**，需要手动切到 ON
5. 按 **F6** 启停引擎

导入时会为每个「帽子积木」生成一个独立脚本，命名为 `角色名#序号`。Scratch 里的多个角色可以用来分类组织，角色本身没有其他意义。

## 六、注意事项

- **浏览器里看不到效果**：扩展积木在 TurboWarp 里只是空实现，只能看控制台日志。真正的验证要在游戏里跑。
- **不要改扩展积木的 opcode**：模组按 `hkscript_<opcode>` 做映射，改名会导致导入失败。
- **变量名、列表名**直接按名称对应，中文可用。
- **扩展必须先加载再拼积木**，否则导出的项目里没有扩展积木。
