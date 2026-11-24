(function (Scratch) {
	'use strict';

	/*
	 * 本文件由 tools/build-extension.js 生成，不要手改。
	 * 要改积木或菜单，请改 turbowarp/src/blocks.data.js（物品表在 items.data.js），然后重新生成。
	 *
	 * 编辑器副本：给 TurboWarp 用。浏览器里没有 Java，所有积木都是占位实现，
	 * 真正的执行发生在游戏内——由 vm-bootstrap 把每个 opcode 换成对宿主的调用。
	 */

	let lang = 'en';
	try {
		lang = (navigator.language || 'en').toLowerCase();
	} catch (e) {
		lang = 'en';
	}
	const ZH = lang.indexOf('zh') === 0;

	/** 中英切换 */
	const T = (en, zh) => (ZH ? zh : en);

const ITEM_TABLE = [
	'acacia_boat|金合欢木船|Acacia Boat',
	'acacia_button|金合欢木按钮|Acacia Button',
	'acacia_chest_boat|金合欢木运输船|Acacia Boat with Chest',
	'acacia_door|金合欢木门|Acacia Door',
	'acacia_fence|金合欢木栅栏|Acacia Fence',
	'acacia_fence_gate|金合欢木栅栏门|Acacia Fence Gate',
	'acacia_hanging_sign|悬挂式金合欢木告示牌|Acacia Hanging Sign',
	'acacia_leaves|金合欢树叶|Acacia Leaves',
	'acacia_log|金合欢原木|Acacia Log',
	'acacia_planks|金合欢木板|Acacia Planks',
	'acacia_pressure_plate|金合欢木压力板|Acacia Pressure Plate',
	'acacia_sapling|金合欢树苗|Acacia Sapling',
	'acacia_shelf|金合欢木展示架|Acacia Shelf',
	'acacia_sign|金合欢木告示牌|Acacia Sign',
	'acacia_slab|金合欢木台阶|Acacia Slab',
	'acacia_stairs|金合欢木楼梯|Acacia Stairs',
	'acacia_trapdoor|金合欢木活板门|Acacia Trapdoor',
	'acacia_wood|金合欢木|Acacia Wood',
	'activator_rail|激活铁轨|Activator Rail',
	'air|空气|Air',
	'allay_spawn_egg|悦灵刷怪蛋|Allay Spawn Egg',
	'allium|绒球葱|Allium',
	'amethyst_block|紫水晶块|Block of Amethyst',
	'amethyst_cluster|紫水晶簇|Amethyst Cluster',
	'amethyst_shard|紫水晶碎片|Amethyst Shard',
	'ancient_debris|远古残骸|Ancient Debris',
	'andesite|安山岩|Andesite',
	'andesite_slab|安山岩台阶|Andesite Slab',
	'andesite_stairs|安山岩楼梯|Andesite Stairs',
	'andesite_wall|安山岩墙|Andesite Wall',
	'angler_pottery_sherd|垂钓纹样陶片|Angler Pottery Sherd',
	'anvil|铁砧|Anvil',
	'apple|苹果|Apple',
	'archer_pottery_sherd|弓箭纹样陶片|Archer Pottery Sherd',
	'armadillo_scute|犰狳鳞甲|Armadillo Scute',
	'armadillo_spawn_egg|犰狳刷怪蛋|Armadillo Spawn Egg',
	'armor_stand|盔甲架|Armor Stand',
	'arms_up_pottery_sherd|举臂纹样陶片|Arms Up Pottery Sherd',
	'arrow|箭|Arrow',
	'axolotl_bucket|美西螈桶|Bucket of Axolotl',
	'axolotl_spawn_egg|美西螈刷怪蛋|Axolotl Spawn Egg',
	'azalea|杜鹃花丛|Azalea',
	'azalea_leaves|杜鹃树叶|Azalea Leaves',
	'azure_bluet|蓝花美耳草|Azure Bluet',
	'baked_potato|烤马铃薯|Baked Potato',
	'bamboo|竹子|Bamboo',
	'bamboo_block|竹块|Block of Bamboo',
	'bamboo_button|竹按钮|Bamboo Button',
	'bamboo_chest_raft|运输竹筏|Bamboo Raft with Chest',
	'bamboo_door|竹门|Bamboo Door',
	'bamboo_fence|竹栅栏|Bamboo Fence',
	'bamboo_fence_gate|竹栅栏门|Bamboo Fence Gate',
	'bamboo_hanging_sign|悬挂式竹告示牌|Bamboo Hanging Sign',
	'bamboo_mosaic|竹马赛克|Bamboo Mosaic',
	'bamboo_mosaic_slab|竹马赛克台阶|Bamboo Mosaic Slab',
	'bamboo_mosaic_stairs|竹马赛克楼梯|Bamboo Mosaic Stairs',
	'bamboo_planks|竹板|Bamboo Planks',
	'bamboo_pressure_plate|竹压力板|Bamboo Pressure Plate',
	'bamboo_raft|竹筏|Bamboo Raft',
	'bamboo_shelf|竹展示架|Bamboo Shelf',
	'bamboo_sign|竹告示牌|Bamboo Sign',
	'bamboo_slab|竹台阶|Bamboo Slab',
	'bamboo_stairs|竹楼梯|Bamboo Stairs',
	'bamboo_trapdoor|竹活板门|Bamboo Trapdoor',
	'barrel|木桶|Barrel',
	'barrier|屏障|Barrier',
	'basalt|玄武岩|Basalt',
	'bat_spawn_egg|蝙蝠刷怪蛋|Bat Spawn Egg',
	'beacon|信标|Beacon',
	'bedrock|基岩|Bedrock',
	'bee_nest|蜂巢|Bee Nest',
	'bee_spawn_egg|蜜蜂刷怪蛋|Bee Spawn Egg',
	'beef|生牛肉|Raw Beef',
	'beehive|蜂箱|Beehive',
	'beetroot|甜菜根|Beetroot',
	'beetroot_seeds|甜菜种子|Beetroot Seeds',
	'beetroot_soup|甜菜汤|Beetroot Soup',
	'bell|钟|Bell',
	'big_dripleaf|大型垂滴叶|Big Dripleaf',
	'birch_boat|白桦木船|Birch Boat',
	'birch_button|白桦木按钮|Birch Button',
	'birch_chest_boat|白桦木运输船|Birch Boat with Chest',
	'birch_door|白桦木门|Birch Door',
	'birch_fence|白桦木栅栏|Birch Fence',
	'birch_fence_gate|白桦木栅栏门|Birch Fence Gate',
	'birch_hanging_sign|悬挂式白桦木告示牌|Birch Hanging Sign',
	'birch_leaves|白桦树叶|Birch Leaves',
	'birch_log|白桦原木|Birch Log',
	'birch_planks|白桦木板|Birch Planks',
	'birch_pressure_plate|白桦木压力板|Birch Pressure Plate',
	'birch_sapling|白桦树苗|Birch Sapling',
	'birch_shelf|白桦木展示架|Birch Shelf',
	'birch_sign|白桦木告示牌|Birch Sign',
	'birch_slab|白桦木台阶|Birch Slab',
	'birch_stairs|白桦木楼梯|Birch Stairs',
	'birch_trapdoor|白桦木活板门|Birch Trapdoor',
	'birch_wood|白桦木|Birch Wood',
	'black_banner|黑色旗帜|Black Banner',
	'black_bed|黑色床|Black Bed',
	'black_bundle|黑色收纳袋|Black Bundle',
	'black_candle|黑色蜡烛|Black Candle',
	'black_carpet|黑色地毯|Black Carpet',
	'black_concrete|黑色混凝土|Black Concrete',
	'black_concrete_powder|黑色混凝土粉末|Black Concrete Powder',
	'black_dye|黑色染料|Black Dye',
	'black_glazed_terracotta|黑色带釉陶瓦|Black Glazed Terracotta',
	'black_harness|黑色挽具|Black Harness',
	'black_shulker_box|黑色潜影盒|Black Shulker Box',
	'black_stained_glass|黑色染色玻璃|Black Stained Glass',
	'black_stained_glass_pane|黑色染色玻璃板|Black Stained Glass Pane',
	'black_terracotta|黑色陶瓦|Black Terracotta',
	'black_wool|黑色羊毛|Black Wool',
	'blackstone|黑石|Blackstone',
	'blackstone_slab|黑石台阶|Blackstone Slab',
	'blackstone_stairs|黑石楼梯|Blackstone Stairs',
	'blackstone_wall|黑石墙|Blackstone Wall',
	'blade_pottery_sherd|利刃纹样陶片|Blade Pottery Sherd',
	'blast_furnace|高炉|Blast Furnace',
	'blaze_powder|烈焰粉|Blaze Powder',
	'blaze_rod|烈焰棒|Blaze Rod',
	'blaze_spawn_egg|烈焰人刷怪蛋|Blaze Spawn Egg',
	'blue_banner|蓝色旗帜|Blue Banner',
	'blue_bed|蓝色床|Blue Bed',
	'blue_bundle|蓝色收纳袋|Blue Bundle',
	'blue_candle|蓝色蜡烛|Blue Candle',
	'blue_carpet|蓝色地毯|Blue Carpet',
	'blue_concrete|蓝色混凝土|Blue Concrete',
	'blue_concrete_powder|蓝色混凝土粉末|Blue Concrete Powder',
	'blue_dye|蓝色染料|Blue Dye',
	'blue_egg|蓝色鸡蛋|Blue Egg',
	'blue_glazed_terracotta|蓝色带釉陶瓦|Blue Glazed Terracotta',
	'blue_harness|蓝色挽具|Blue Harness',
	'blue_ice|蓝冰|Blue Ice',
	'blue_orchid|兰花|Blue Orchid',
	'blue_shulker_box|蓝色潜影盒|Blue Shulker Box',
	'blue_stained_glass|蓝色染色玻璃|Blue Stained Glass',
	'blue_stained_glass_pane|蓝色染色玻璃板|Blue Stained Glass Pane',
	'blue_terracotta|蓝色陶瓦|Blue Terracotta',
	'blue_wool|蓝色羊毛|Blue Wool',
	'bogged_spawn_egg|沼骸刷怪蛋|Bogged Spawn Egg',
	'bolt_armor_trim_smithing_template|锻造模板|Smithing Template',
	'bone|骨头|Bone',
	'bone_block|骨块|Bone Block',
	'bone_meal|骨粉|Bone Meal',
	'book|书|Book',
	'bookshelf|书架|Bookshelf',
	'bordure_indented_banner_pattern|波纹边旗帜图案|Bordure Indented Banner Pattern',
	'bow|弓|Bow',
	'bowl|碗|Bowl',
	'brain_coral|脑纹珊瑚|Brain Coral',
	'brain_coral_block|脑纹珊瑚块|Brain Coral Block',
	'brain_coral_fan|脑纹珊瑚扇|Brain Coral Fan',
	'bread|面包|Bread',
	'breeze_rod|旋风棒|Breeze Rod',
	'breeze_spawn_egg|旋风人刷怪蛋|Breeze Spawn Egg',
	'brewer_pottery_sherd|佳酿纹样陶片|Brewer Pottery Sherd',
	'brewing_stand|酿造台|Brewing Stand',
	'brick|红砖|Brick',
	'brick_slab|红砖台阶|Brick Slab',
	'brick_stairs|红砖楼梯|Brick Stairs',
	'brick_wall|红砖墙|Brick Wall',
	'bricks|红砖块|Bricks',
	'brown_banner|棕色旗帜|Brown Banner',
	'brown_bed|棕色床|Brown Bed',
	'brown_bundle|棕色收纳袋|Brown Bundle',
	'brown_candle|棕色蜡烛|Brown Candle',
	'brown_carpet|棕色地毯|Brown Carpet',
	'brown_concrete|棕色混凝土|Brown Concrete',
	'brown_concrete_powder|棕色混凝土粉末|Brown Concrete Powder',
	'brown_dye|棕色染料|Brown Dye',
	'brown_egg|棕色鸡蛋|Brown Egg',
	'brown_glazed_terracotta|棕色带釉陶瓦|Brown Glazed Terracotta',
	'brown_harness|棕色挽具|Brown Harness',
	'brown_mushroom|棕色蘑菇|Brown Mushroom',
	'brown_mushroom_block|棕色蘑菇方块|Brown Mushroom Block',
	'brown_shulker_box|棕色潜影盒|Brown Shulker Box',
	'brown_stained_glass|棕色染色玻璃|Brown Stained Glass',
	'brown_stained_glass_pane|棕色染色玻璃板|Brown Stained Glass Pane',
	'brown_terracotta|棕色陶瓦|Brown Terracotta',
	'brown_wool|棕色羊毛|Brown Wool',
	'brush|刷子|Brush',
	'bubble_coral|气泡珊瑚|Bubble Coral',
	'bubble_coral_block|气泡珊瑚块|Bubble Coral Block',
	'bubble_coral_fan|气泡珊瑚扇|Bubble Coral Fan',
	'bucket|铁桶|Bucket',
	'budding_amethyst|紫水晶母岩|Budding Amethyst',
	'bundle|收纳袋|Bundle',
	'burn_pottery_sherd|烈焰纹样陶片|Burn Pottery Sherd',
	'bush|灌木丛|Bush',
	'cactus|仙人掌|Cactus',
	'cactus_flower|仙人掌花|Cactus Flower',
	'cake|蛋糕|Cake',
	'calcite|方解石|Calcite',
	'calibrated_sculk_sensor|校频幽匿感测体|Calibrated Sculk Sensor',
	'camel_husk_spawn_egg|骆驼尸壳刷怪蛋|Camel Husk Spawn Egg',
	'camel_spawn_egg|骆驼刷怪蛋|Camel Spawn Egg',
	'campfire|营火|Campfire',
	'candle|蜡烛|Candle',
	'carrot|胡萝卜|Carrot',
	'carrot_on_a_stick|胡萝卜钓竿|Carrot on a Stick',
	'cartography_table|制图台|Cartography Table',
	'carved_pumpkin|雕刻南瓜|Carved Pumpkin',
	'cat_spawn_egg|猫刷怪蛋|Cat Spawn Egg',
	'cauldron|炼药锅|Cauldron',
	'cave_spider_spawn_egg|洞穴蜘蛛刷怪蛋|Cave Spider Spawn Egg',
	'chain_command_block|连锁型命令方块|Chain Command Block',
	'chainmail_boots|锁链靴子|Chainmail Boots',
	'chainmail_chestplate|锁链胸甲|Chainmail Chestplate',
	'chainmail_helmet|锁链头盔|Chainmail Helmet',
	'chainmail_leggings|锁链护腿|Chainmail Leggings',
	'charcoal|木炭|Charcoal',
	'cherry_boat|樱花木船|Cherry Boat',
	'cherry_button|樱花木按钮|Cherry Button',
	'cherry_chest_boat|樱花木运输船|Cherry Boat with Chest',
	'cherry_door|樱花木门|Cherry Door',
	'cherry_fence|樱花木栅栏|Cherry Fence',
	'cherry_fence_gate|樱花木栅栏门|Cherry Fence Gate',
	'cherry_hanging_sign|悬挂式樱花木告示牌|Cherry Hanging Sign',
	'cherry_leaves|樱花树叶|Cherry Leaves',
	'cherry_log|樱花原木|Cherry Log',
	'cherry_planks|樱花木板|Cherry Planks',
	'cherry_pressure_plate|樱花木压力板|Cherry Pressure Plate',
	'cherry_sapling|樱花树苗|Cherry Sapling',
	'cherry_shelf|樱花木展示架|Cherry Shelf',
	'cherry_sign|樱花木告示牌|Cherry Sign',
	'cherry_slab|樱花木台阶|Cherry Slab',
	'cherry_stairs|樱花木楼梯|Cherry Stairs',
	'cherry_trapdoor|樱花木活板门|Cherry Trapdoor',
	'cherry_wood|樱花木|Cherry Wood',
	'chest|箱子|Chest',
	'chest_minecart|运输矿车|Minecart with Chest',
	'chicken|生鸡肉|Raw Chicken',
	'chicken_spawn_egg|鸡刷怪蛋|Chicken Spawn Egg',
	'chipped_anvil|开裂的铁砧|Chipped Anvil',
	'chiseled_bookshelf|雕纹书架|Chiseled Bookshelf',
	'chiseled_copper|雕纹铜块|Chiseled Copper',
	'chiseled_deepslate|雕纹深板岩|Chiseled Deepslate',
	'chiseled_nether_bricks|雕纹下界砖块|Chiseled Nether Bricks',
	'chiseled_polished_blackstone|雕纹磨制黑石|Chiseled Polished Blackstone',
	'chiseled_quartz_block|雕纹石英块|Chiseled Quartz Block',
	'chiseled_red_sandstone|雕纹红砂岩|Chiseled Red Sandstone',
	'chiseled_resin_bricks|雕纹树脂砖块|Chiseled Resin Bricks',
	'chiseled_sandstone|雕纹砂岩|Chiseled Sandstone',
	'chiseled_stone_bricks|雕纹石砖|Chiseled Stone Bricks',
	'chiseled_tuff|雕纹凝灰岩|Chiseled Tuff',
	'chiseled_tuff_bricks|雕纹凝灰岩砖|Chiseled Tuff Bricks',
	'chorus_flower|紫颂花|Chorus Flower',
	'chorus_fruit|紫颂果|Chorus Fruit',
	'chorus_plant|紫颂植株|Chorus Plant',
	'clay|黏土|Clay',
	'clay_ball|黏土球|Clay Ball',
	'clock|时钟|Clock',
	'closed_eyeblossom|闭合的眼眸花|Closed Eyeblossom',
	'coal|煤炭|Coal',
	'coal_block|煤炭块|Block of Coal',
	'coal_ore|煤矿石|Coal Ore',
	'coarse_dirt|砂土|Coarse Dirt',
	'coast_armor_trim_smithing_template|锻造模板|Smithing Template',
	'cobbled_deepslate|深板岩圆石|Cobbled Deepslate',
	'cobbled_deepslate_slab|深板岩圆石台阶|Cobbled Deepslate Slab',
	'cobbled_deepslate_stairs|深板岩圆石楼梯|Cobbled Deepslate Stairs',
	'cobbled_deepslate_wall|深板岩圆石墙|Cobbled Deepslate Wall',
	'cobblestone|圆石|Cobblestone',
	'cobblestone_slab|圆石台阶|Cobblestone Slab',
	'cobblestone_stairs|圆石楼梯|Cobblestone Stairs',
	'cobblestone_wall|圆石墙|Cobblestone Wall',
	'cobweb|蜘蛛网|Cobweb',
	'cocoa_beans|可可豆|Cocoa Beans',
	'cod|生鳕鱼|Raw Cod',
	'cod_bucket|鳕鱼桶|Bucket of Cod',
	'cod_spawn_egg|鳕鱼刷怪蛋|Cod Spawn Egg',
	'command_block|命令方块|Command Block',
	'command_block_minecart|命令方块矿车|Minecart with Command Block',
	'comparator|红石比较器|Redstone Comparator',
	'compass|指南针|Compass',
	'composter|堆肥桶|Composter',
	'conduit|潮涌核心|Conduit',
	'cooked_beef|牛排|Steak',
	'cooked_chicken|熟鸡肉|Cooked Chicken',
	'cooked_cod|熟鳕鱼|Cooked Cod',
	'cooked_mutton|熟羊肉|Cooked Mutton',
	'cooked_porkchop|熟猪排|Cooked Porkchop',
	'cooked_rabbit|熟兔肉|Cooked Rabbit',
	'cooked_salmon|熟鲑鱼|Cooked Salmon',
	'cookie|曲奇|Cookie',
	'copper_axe|铜斧|Copper Axe',
	'copper_bars|铜栏杆|Copper Bars',
	'copper_block|铜块|Block of Copper',
	'copper_boots|铜靴子|Copper Boots',
	'copper_bulb|铜灯|Copper Bulb',
	'copper_chain|铜链|Copper Chain',
	'copper_chest|铜箱子|Copper Chest',
	'copper_chestplate|铜胸甲|Copper Chestplate',
	'copper_door|铜门|Copper Door',
	'copper_golem_spawn_egg|铜傀儡刷怪蛋|Copper Golem Spawn Egg',
	'copper_golem_statue|铜傀儡像|Copper Golem Statue',
	'copper_grate|铜格栅|Copper Grate',
	'copper_helmet|铜头盔|Copper Helmet',
	'copper_hoe|铜锄|Copper Hoe',
	'copper_horse_armor|铜马铠|Copper Horse Armor',
	'copper_ingot|铜锭|Copper Ingot',
	'copper_lantern|铜灯笼|Copper Lantern',
	'copper_leggings|铜护腿|Copper Leggings',
	'copper_nautilus_armor|铜鹦鹉螺铠|Copper Nautilus Armor',
	'copper_nugget|铜粒|Copper Nugget',
	'copper_ore|铜矿石|Copper Ore',
	'copper_pickaxe|铜镐|Copper Pickaxe',
	'copper_shovel|铜锹|Copper Shovel',
	'copper_spear|铜矛|Copper Spear',
	'copper_sword|铜剑|Copper Sword',
	'copper_torch|铜火把|Copper Torch',
	'copper_trapdoor|铜活板门|Copper Trapdoor',
	'cornflower|矢车菊|Cornflower',
	'cow_spawn_egg|牛刷怪蛋|Cow Spawn Egg',
	'cracked_deepslate_bricks|裂纹深板岩砖|Cracked Deepslate Bricks',
	'cracked_deepslate_tiles|裂纹深板岩瓦|Cracked Deepslate Tiles',
	'cracked_nether_bricks|裂纹下界砖块|Cracked Nether Bricks',
	'cracked_polished_blackstone_bricks|裂纹磨制黑石砖|Cracked Polished Blackstone Bricks',
	'cracked_stone_bricks|裂纹石砖|Cracked Stone Bricks',
	'crafter|合成器|Crafter',
	'crafting_table|工作台|Crafting Table',
	'creaking_heart|嘎枝之心|Creaking Heart',
	'creaking_spawn_egg|嘎枝刷怪蛋|Creaking Spawn Egg',
	'creeper_banner_pattern|旗帜图案|Banner Pattern',
	'creeper_head|苦力怕的头|Creeper Head',
	'creeper_spawn_egg|苦力怕刷怪蛋|Creeper Spawn Egg',
	'crimson_button|绯红木按钮|Crimson Button',
	'crimson_door|绯红木门|Crimson Door',
	'crimson_fence|绯红木栅栏|Crimson Fence',
	'crimson_fence_gate|绯红木栅栏门|Crimson Fence Gate',
	'crimson_fungus|绯红菌|Crimson Fungus',
	'crimson_hanging_sign|悬挂式绯红木告示牌|Crimson Hanging Sign',
	'crimson_hyphae|绯红菌核|Crimson Hyphae',
	'crimson_nylium|绯红菌岩|Crimson Nylium',
	'crimson_planks|绯红木板|Crimson Planks',
	'crimson_pressure_plate|绯红木压力板|Crimson Pressure Plate',
	'crimson_roots|绯红菌索|Crimson Roots',
	'crimson_shelf|绯红木展示架|Crimson Shelf',
	'crimson_sign|绯红木告示牌|Crimson Sign',
	'crimson_slab|绯红木台阶|Crimson Slab',
	'crimson_stairs|绯红木楼梯|Crimson Stairs',
	'crimson_stem|绯红菌柄|Crimson Stem',
	'crimson_trapdoor|绯红木活板门|Crimson Trapdoor',
	'crossbow|弩|Crossbow',
	'crying_obsidian|哭泣的黑曜石|Crying Obsidian',
	'cut_copper|切制铜块|Cut Copper',
	'cut_copper_slab|切制铜台阶|Cut Copper Slab',
	'cut_copper_stairs|切制铜楼梯|Cut Copper Stairs',
	'cut_red_sandstone|切制红砂岩|Cut Red Sandstone',
	'cut_red_sandstone_slab|切制红砂岩台阶|Cut Red Sandstone Slab',
	'cut_sandstone|切制砂岩|Cut Sandstone',
	'cut_sandstone_slab|切制砂岩台阶|Cut Sandstone Slab',
	'cyan_banner|青色旗帜|Cyan Banner',
	'cyan_bed|青色床|Cyan Bed',
	'cyan_bundle|青色收纳袋|Cyan Bundle',
	'cyan_candle|青色蜡烛|Cyan Candle',
	'cyan_carpet|青色地毯|Cyan Carpet',
	'cyan_concrete|青色混凝土|Cyan Concrete',
	'cyan_concrete_powder|青色混凝土粉末|Cyan Concrete Powder',
	'cyan_dye|青色染料|Cyan Dye',
	'cyan_glazed_terracotta|青色带釉陶瓦|Cyan Glazed Terracotta',
	'cyan_harness|青色挽具|Cyan Harness',
	'cyan_shulker_box|青色潜影盒|Cyan Shulker Box',
	'cyan_stained_glass|青色染色玻璃|Cyan Stained Glass',
	'cyan_stained_glass_pane|青色染色玻璃板|Cyan Stained Glass Pane',
	'cyan_terracotta|青色陶瓦|Cyan Terracotta',
	'cyan_wool|青色羊毛|Cyan Wool',
	'damaged_anvil|损坏的铁砧|Damaged Anvil',
	'dandelion|蒲公英|Dandelion',
	'danger_pottery_sherd|危机纹样陶片|Danger Pottery Sherd',
	'dark_oak_boat|深色橡木船|Dark Oak Boat',
	'dark_oak_button|深色橡木按钮|Dark Oak Button',
	'dark_oak_chest_boat|深色橡木运输船|Dark Oak Boat with Chest',
	'dark_oak_door|深色橡木门|Dark Oak Door',
	'dark_oak_fence|深色橡木栅栏|Dark Oak Fence',
	'dark_oak_fence_gate|深色橡木栅栏门|Dark Oak Fence Gate',
	'dark_oak_hanging_sign|悬挂式深色橡木告示牌|Dark Oak Hanging Sign',
	'dark_oak_leaves|深色橡树树叶|Dark Oak Leaves',
	'dark_oak_log|深色橡木原木|Dark Oak Log',
	'dark_oak_planks|深色橡木木板|Dark Oak Planks',
	'dark_oak_pressure_plate|深色橡木压力板|Dark Oak Pressure Plate',
	'dark_oak_sapling|深色橡树树苗|Dark Oak Sapling',
	'dark_oak_shelf|深色橡木展示架|Dark Oak Shelf',
	'dark_oak_sign|深色橡木告示牌|Dark Oak Sign',
	'dark_oak_slab|深色橡木台阶|Dark Oak Slab',
	'dark_oak_stairs|深色橡木楼梯|Dark Oak Stairs',
	'dark_oak_trapdoor|深色橡木活板门|Dark Oak Trapdoor',
	'dark_oak_wood|深色橡木|Dark Oak Wood',
	'dark_prismarine|暗海晶石|Dark Prismarine',
	'dark_prismarine_slab|暗海晶石台阶|Dark Prismarine Slab',
	'dark_prismarine_stairs|暗海晶石楼梯|Dark Prismarine Stairs',
	'daylight_detector|阳光探测器|Daylight Detector',
	'dead_brain_coral|失活的脑纹珊瑚|Dead Brain Coral',
	'dead_brain_coral_block|失活的脑纹珊瑚块|Dead Brain Coral Block',
	'dead_brain_coral_fan|失活的脑纹珊瑚扇|Dead Brain Coral Fan',
	'dead_bubble_coral|失活的气泡珊瑚|Dead Bubble Coral',
	'dead_bubble_coral_block|失活的气泡珊瑚块|Dead Bubble Coral Block',
	'dead_bubble_coral_fan|失活的气泡珊瑚扇|Dead Bubble Coral Fan',
	'dead_bush|枯萎的灌木|Dead Bush',
	'dead_fire_coral|失活的火珊瑚|Dead Fire Coral',
	'dead_fire_coral_block|失活的火珊瑚块|Dead Fire Coral Block',
	'dead_fire_coral_fan|失活的火珊瑚扇|Dead Fire Coral Fan',
	'dead_horn_coral|失活的鹿角珊瑚|Dead Horn Coral',
	'dead_horn_coral_block|失活的鹿角珊瑚块|Dead Horn Coral Block',
	'dead_horn_coral_fan|失活的鹿角珊瑚扇|Dead Horn Coral Fan',
	'dead_tube_coral|失活的管珊瑚|Dead Tube Coral',
	'dead_tube_coral_block|失活的管珊瑚块|Dead Tube Coral Block',
	'dead_tube_coral_fan|失活的管珊瑚扇|Dead Tube Coral Fan',
	'debug_stick|调试棒|Debug Stick',
	'decorated_pot|饰纹陶罐|Decorated Pot',
	'deepslate|深板岩|Deepslate',
	'deepslate_brick_slab|深板岩砖台阶|Deepslate Brick Slab',
	'deepslate_brick_stairs|深板岩砖楼梯|Deepslate Brick Stairs',
	'deepslate_brick_wall|深板岩砖墙|Deepslate Brick Wall',
	'deepslate_bricks|深板岩砖|Deepslate Bricks',
	'deepslate_coal_ore|深层煤矿石|Deepslate Coal Ore',
	'deepslate_copper_ore|深层铜矿石|Deepslate Copper Ore',
	'deepslate_diamond_ore|深层钻石矿石|Deepslate Diamond Ore',
	'deepslate_emerald_ore|深层绿宝石矿石|Deepslate Emerald Ore',
	'deepslate_gold_ore|深层金矿石|Deepslate Gold Ore',
	'deepslate_iron_ore|深层铁矿石|Deepslate Iron Ore',
	'deepslate_lapis_ore|深层青金石矿石|Deepslate Lapis Lazuli Ore',
	'deepslate_redstone_ore|深层红石矿石|Deepslate Redstone Ore',
	'deepslate_tile_slab|深板岩瓦台阶|Deepslate Tile Slab',
	'deepslate_tile_stairs|深板岩瓦楼梯|Deepslate Tile Stairs',
	'deepslate_tile_wall|深板岩瓦墙|Deepslate Tile Wall',
	'deepslate_tiles|深板岩瓦|Deepslate Tiles',
	'detector_rail|探测铁轨|Detector Rail',
	'diamond|钻石|Diamond',
	'diamond_axe|钻石斧|Diamond Axe',
	'diamond_block|钻石块|Block of Diamond',
	'diamond_boots|钻石靴子|Diamond Boots',
	'diamond_chestplate|钻石胸甲|Diamond Chestplate',
	'diamond_helmet|钻石头盔|Diamond Helmet',
	'diamond_hoe|钻石锄|Diamond Hoe',
	'diamond_horse_armor|钻石马铠|Diamond Horse Armor',
	'diamond_leggings|钻石护腿|Diamond Leggings',
	'diamond_nautilus_armor|钻石鹦鹉螺铠|Diamond Nautilus Armor',
	'diamond_ore|钻石矿石|Diamond Ore',
	'diamond_pickaxe|钻石镐|Diamond Pickaxe',
	'diamond_shovel|钻石锹|Diamond Shovel',
	'diamond_spear|钻石矛|Diamond Spear',
	'diamond_sword|钻石剑|Diamond Sword',
	'diorite|闪长岩|Diorite',
	'diorite_slab|闪长岩台阶|Diorite Slab',
	'diorite_stairs|闪长岩楼梯|Diorite Stairs',
	'diorite_wall|闪长岩墙|Diorite Wall',
	'dirt|泥土|Dirt',
	'dirt_path|土径|Dirt Path',
	'disc_fragment_5|唱片残片|Disc Fragment',
	'dispenser|发射器|Dispenser',
	'dolphin_spawn_egg|海豚刷怪蛋|Dolphin Spawn Egg',
	'donkey_spawn_egg|驴刷怪蛋|Donkey Spawn Egg',
	'dragon_breath|龙息|Dragon\'s Breath',
	'dragon_egg|龙蛋|Dragon Egg',
	'dragon_head|龙首|Dragon Head',
	'dried_ghast|失水恶魂|Dried Ghast',
	'dried_kelp|干海带|Dried Kelp',
	'dried_kelp_block|干海带块|Dried Kelp Block',
	'dripstone_block|滴水石块|Dripstone Block',
	'dropper|投掷器|Dropper',
	'drowned_spawn_egg|溺尸刷怪蛋|Drowned Spawn Egg',
	'dune_armor_trim_smithing_template|锻造模板|Smithing Template',
	'echo_shard|回响碎片|Echo Shard',
	'egg|鸡蛋|Egg',
	'elder_guardian_spawn_egg|远古守卫者刷怪蛋|Elder Guardian Spawn Egg',
	'elytra|鞘翅|Elytra',
	'emerald|绿宝石|Emerald',
	'emerald_block|绿宝石块|Block of Emerald',
	'emerald_ore|绿宝石矿石|Emerald Ore',
	'enchanted_book|附魔书|Enchanted Book',
	'enchanted_golden_apple|附魔金苹果|Enchanted Golden Apple',
	'enchanting_table|附魔台|Enchanting Table',
	'end_crystal|末地水晶|End Crystal',
	'end_portal_frame|末地传送门框架|End Portal Frame',
	'end_rod|末地烛|End Rod',
	'end_stone|末地石|End Stone',
	'end_stone_brick_slab|末地石砖台阶|End Stone Brick Slab',
	'end_stone_brick_stairs|末地石砖楼梯|End Stone Brick Stairs',
	'end_stone_brick_wall|末地石砖墙|End Stone Brick Wall',
	'end_stone_bricks|末地石砖|End Stone Bricks',
	'ender_chest|末影箱|Ender Chest',
	'ender_dragon_spawn_egg|末影龙刷怪蛋|Ender Dragon Spawn Egg',
	'ender_eye|末影之眼|Eye of Ender',
	'ender_pearl|末影珍珠|Ender Pearl',
	'enderman_spawn_egg|末影人刷怪蛋|Enderman Spawn Egg',
	'endermite_spawn_egg|末影螨刷怪蛋|Endermite Spawn Egg',
	'evoker_spawn_egg|唤魔者刷怪蛋|Evoker Spawn Egg',
	'experience_bottle|附魔之瓶|Bottle o\' Enchanting',
	'explorer_pottery_sherd|探险纹样陶片|Explorer Pottery Sherd',
	'exposed_chiseled_copper|斑驳的雕纹铜块|Exposed Chiseled Copper',
	'exposed_copper|斑驳的铜块|Exposed Copper',
	'exposed_copper_bars|斑驳的铜栏杆|Exposed Copper Bars',
	'exposed_copper_bulb|斑驳的铜灯|Exposed Copper Bulb',
	'exposed_copper_chain|斑驳的铜链|Exposed Copper Chain',
	'exposed_copper_chest|斑驳的铜箱子|Exposed Copper Chest',
	'exposed_copper_door|斑驳的铜门|Exposed Copper Door',
	'exposed_copper_golem_statue|斑驳的铜傀儡像|Exposed Copper Golem Statue',
	'exposed_copper_grate|斑驳的铜格栅|Exposed Copper Grate',
	'exposed_copper_lantern|斑驳的铜灯笼|Exposed Copper Lantern',
	'exposed_copper_trapdoor|斑驳的铜活板门|Exposed Copper Trapdoor',
	'exposed_cut_copper|斑驳的切制铜块|Exposed Cut Copper',
	'exposed_cut_copper_slab|斑驳的切制铜台阶|Exposed Cut Copper Slab',
	'exposed_cut_copper_stairs|斑驳的切制铜楼梯|Exposed Cut Copper Stairs',
	'exposed_lightning_rod|斑驳的避雷针|Exposed Lightning Rod',
	'eye_armor_trim_smithing_template|锻造模板|Smithing Template',
	'farmland|耕地|Farmland',
	'feather|羽毛|Feather',
	'fermented_spider_eye|发酵蛛眼|Fermented Spider Eye',
	'fern|蕨|Fern',
	'field_masoned_banner_pattern|砖纹旗帜图案|Field Masoned Banner Pattern',
	'filled_map|地图|Map',
	'fire_charge|火焰弹|Fire Charge',
	'fire_coral|火珊瑚|Fire Coral',
	'fire_coral_block|火珊瑚块|Fire Coral Block',
	'fire_coral_fan|火珊瑚扇|Fire Coral Fan',
	'firefly_bush|萤火虫灌木丛|Firefly Bush',
	'firework_rocket|烟花火箭|Firework Rocket',
	'firework_star|烟火之星|Firework Star',
	'fishing_rod|钓鱼竿|Fishing Rod',
	'fletching_table|制箭台|Fletching Table',
	'flint|燧石|Flint',
	'flint_and_steel|打火石|Flint and Steel',
	'flow_armor_trim_smithing_template|锻造模板|Smithing Template',
	'flow_banner_pattern|旗帜图案|Banner Pattern',
	'flow_pottery_sherd|涡流纹样陶片|Flow Pottery Sherd',
	'flower_banner_pattern|旗帜图案|Banner Pattern',
	'flower_pot|花盆|Flower Pot',
	'flowering_azalea|盛开的杜鹃花丛|Flowering Azalea',
	'flowering_azalea_leaves|盛开的杜鹃树叶|Flowering Azalea Leaves',
	'fox_spawn_egg|狐狸刷怪蛋|Fox Spawn Egg',
	'friend_pottery_sherd|挚友纹样陶片|Friend Pottery Sherd',
	'frog_spawn_egg|青蛙刷怪蛋|Frog Spawn Egg',
	'frogspawn|青蛙卵|Frogspawn',
	'furnace|熔炉|Furnace',
	'furnace_minecart|动力矿车|Minecart with Furnace',
	'ghast_spawn_egg|恶魂刷怪蛋|Ghast Spawn Egg',
	'ghast_tear|恶魂之泪|Ghast Tear',
	'gilded_blackstone|镶金黑石|Gilded Blackstone',
	'glass|玻璃|Glass',
	'glass_bottle|玻璃瓶|Glass Bottle',
	'glass_pane|玻璃板|Glass Pane',
	'glistering_melon_slice|闪烁的西瓜片|Glistering Melon Slice',
	'globe_banner_pattern|旗帜图案|Banner Pattern',
	'glow_berries|发光浆果|Glow Berries',
	'glow_ink_sac|荧光墨囊|Glow Ink Sac',
	'glow_item_frame|荧光物品展示框|Glow Item Frame',
	'glow_lichen|发光地衣|Glow Lichen',
	'glow_squid_spawn_egg|发光鱿鱼刷怪蛋|Glow Squid Spawn Egg',
	'glowstone|荧石|Glowstone',
	'glowstone_dust|荧石粉|Glowstone Dust',
	'goat_horn|山羊角|Goat Horn',
	'goat_spawn_egg|山羊刷怪蛋|Goat Spawn Egg',
	'gold_block|金块|Block of Gold',
	'gold_ingot|金锭|Gold Ingot',
	'gold_nugget|金粒|Gold Nugget',
	'gold_ore|金矿石|Gold Ore',
	'golden_apple|金苹果|Golden Apple',
	'golden_axe|金斧|Golden Axe',
	'golden_boots|金靴子|Golden Boots',
	'golden_carrot|金胡萝卜|Golden Carrot',
	'golden_chestplate|金胸甲|Golden Chestplate',
	'golden_helmet|金头盔|Golden Helmet',
	'golden_hoe|金锄|Golden Hoe',
	'golden_horse_armor|金马铠|Golden Horse Armor',
	'golden_leggings|金护腿|Golden Leggings',
	'golden_nautilus_armor|金鹦鹉螺铠|Golden Nautilus Armor',
	'golden_pickaxe|金镐|Golden Pickaxe',
	'golden_shovel|金锹|Golden Shovel',
	'golden_spear|金矛|Golden Spear',
	'golden_sword|金剑|Golden Sword',
	'granite|花岗岩|Granite',
	'granite_slab|花岗岩台阶|Granite Slab',
	'granite_stairs|花岗岩楼梯|Granite Stairs',
	'granite_wall|花岗岩墙|Granite Wall',
	'grass_block|草方块|Grass Block',
	'gravel|沙砾|Gravel',
	'gray_banner|灰色旗帜|Gray Banner',
	'gray_bed|灰色床|Gray Bed',
	'gray_bundle|灰色收纳袋|Gray Bundle',
	'gray_candle|灰色蜡烛|Gray Candle',
	'gray_carpet|灰色地毯|Gray Carpet',
	'gray_concrete|灰色混凝土|Gray Concrete',
	'gray_concrete_powder|灰色混凝土粉末|Gray Concrete Powder',
	'gray_dye|灰色染料|Gray Dye',
	'gray_glazed_terracotta|灰色带釉陶瓦|Gray Glazed Terracotta',
	'gray_harness|灰色挽具|Gray Harness',
	'gray_shulker_box|灰色潜影盒|Gray Shulker Box',
	'gray_stained_glass|灰色染色玻璃|Gray Stained Glass',
	'gray_stained_glass_pane|灰色染色玻璃板|Gray Stained Glass Pane',
	'gray_terracotta|灰色陶瓦|Gray Terracotta',
	'gray_wool|灰色羊毛|Gray Wool',
	'green_banner|绿色旗帜|Green Banner',
	'green_bed|绿色床|Green Bed',
	'green_bundle|绿色收纳袋|Green Bundle',
	'green_candle|绿色蜡烛|Green Candle',
	'green_carpet|绿色地毯|Green Carpet',
	'green_concrete|绿色混凝土|Green Concrete',
	'green_concrete_powder|绿色混凝土粉末|Green Concrete Powder',
	'green_dye|绿色染料|Green Dye',
	'green_glazed_terracotta|绿色带釉陶瓦|Green Glazed Terracotta',
	'green_harness|绿色挽具|Green Harness',
	'green_shulker_box|绿色潜影盒|Green Shulker Box',
	'green_stained_glass|绿色染色玻璃|Green Stained Glass',
	'green_stained_glass_pane|绿色染色玻璃板|Green Stained Glass Pane',
	'green_terracotta|绿色陶瓦|Green Terracotta',
	'green_wool|绿色羊毛|Green Wool',
	'grindstone|砂轮|Grindstone',
	'guardian_spawn_egg|守卫者刷怪蛋|Guardian Spawn Egg',
	'gunpowder|火药|Gunpowder',
	'guster_banner_pattern|旗帜图案|Banner Pattern',
	'guster_pottery_sherd|旋风纹样陶片|Guster Pottery Sherd',
	'hanging_roots|垂根|Hanging Roots',
	'happy_ghast_spawn_egg|快乐恶魂刷怪蛋|Happy Ghast Spawn Egg',
	'hay_block|干草捆|Hay Bale',
	'heart_of_the_sea|海洋之心|Heart of the Sea',
	'heart_pottery_sherd|爱心纹样陶片|Heart Pottery Sherd',
	'heartbreak_pottery_sherd|心碎纹样陶片|Heartbreak Pottery Sherd',
	'heavy_core|沉重核心|Heavy Core',
	'heavy_weighted_pressure_plate|重质测重压力板|Heavy Weighted Pressure Plate',
	'hoglin_spawn_egg|疣猪兽刷怪蛋|Hoglin Spawn Egg',
	'honey_block|蜂蜜块|Honey Block',
	'honey_bottle|蜂蜜瓶|Honey Bottle',
	'honeycomb|蜜脾|Honeycomb',
	'honeycomb_block|蜜脾块|Honeycomb Block',
	'hopper|漏斗|Hopper',
	'hopper_minecart|漏斗矿车|Minecart with Hopper',
	'horn_coral|鹿角珊瑚|Horn Coral',
	'horn_coral_block|鹿角珊瑚块|Horn Coral Block',
	'horn_coral_fan|鹿角珊瑚扇|Horn Coral Fan',
	'horse_spawn_egg|马刷怪蛋|Horse Spawn Egg',
	'host_armor_trim_smithing_template|锻造模板|Smithing Template',
	'howl_pottery_sherd|狼嚎纹样陶片|Howl Pottery Sherd',
	'husk_spawn_egg|尸壳刷怪蛋|Husk Spawn Egg',
	'ice|冰|Ice',
	'infested_chiseled_stone_bricks|虫蚀雕纹石砖|Infested Chiseled Stone Bricks',
	'infested_cobblestone|虫蚀圆石|Infested Cobblestone',
	'infested_cracked_stone_bricks|虫蚀裂纹石砖|Infested Cracked Stone Bricks',
	'infested_deepslate|虫蚀深板岩|Infested Deepslate',
	'infested_mossy_stone_bricks|虫蚀苔石砖|Infested Mossy Stone Bricks',
	'infested_stone|虫蚀石头|Infested Stone',
	'infested_stone_bricks|虫蚀石砖|Infested Stone Bricks',
	'ink_sac|墨囊|Ink Sac',
	'iron_axe|铁斧|Iron Axe',
	'iron_bars|铁栏杆|Iron Bars',
	'iron_block|铁块|Block of Iron',
	'iron_boots|铁靴子|Iron Boots',
	'iron_chain|铁链|Iron Chain',
	'iron_chestplate|铁胸甲|Iron Chestplate',
	'iron_door|铁门|Iron Door',
	'iron_golem_spawn_egg|铁傀儡刷怪蛋|Iron Golem Spawn Egg',
	'iron_helmet|铁头盔|Iron Helmet',
	'iron_hoe|铁锄|Iron Hoe',
	'iron_horse_armor|铁马铠|Iron Horse Armor',
	'iron_ingot|铁锭|Iron Ingot',
	'iron_leggings|铁护腿|Iron Leggings',
	'iron_nautilus_armor|铁鹦鹉螺铠|Iron Nautilus Armor',
	'iron_nugget|铁粒|Iron Nugget',
	'iron_ore|铁矿石|Iron Ore',
	'iron_pickaxe|铁镐|Iron Pickaxe',
	'iron_shovel|铁锹|Iron Shovel',
	'iron_spear|铁矛|Iron Spear',
	'iron_sword|铁剑|Iron Sword',
	'iron_trapdoor|铁活板门|Iron Trapdoor',
	'item_frame|物品展示框|Item Frame',
	'jack_o_lantern|南瓜灯|Jack o\'Lantern',
	'jigsaw|拼图方块|Jigsaw Block',
	'jukebox|唱片机|Jukebox',
	'jungle_boat|丛林木船|Jungle Boat',
	'jungle_button|丛林木按钮|Jungle Button',
	'jungle_chest_boat|丛林木运输船|Jungle Boat with Chest',
	'jungle_door|丛林木门|Jungle Door',
	'jungle_fence|丛林木栅栏|Jungle Fence',
	'jungle_fence_gate|丛林木栅栏门|Jungle Fence Gate',
	'jungle_hanging_sign|悬挂式丛林木告示牌|Jungle Hanging Sign',
	'jungle_leaves|丛林树叶|Jungle Leaves',
	'jungle_log|丛林原木|Jungle Log',
	'jungle_planks|丛林木板|Jungle Planks',
	'jungle_pressure_plate|丛林木压力板|Jungle Pressure Plate',
	'jungle_sapling|丛林树苗|Jungle Sapling',
	'jungle_shelf|丛林木展示架|Jungle Shelf',
	'jungle_sign|丛林木告示牌|Jungle Sign',
	'jungle_slab|丛林木台阶|Jungle Slab',
	'jungle_stairs|丛林木楼梯|Jungle Stairs',
	'jungle_trapdoor|丛林木活板门|Jungle Trapdoor',
	'jungle_wood|丛林木|Jungle Wood',
	'kelp|海带|Kelp',
	'knowledge_book|知识之书|Knowledge Book',
	'ladder|梯子|Ladder',
	'lantern|灯笼|Lantern',
	'lapis_block|青金石块|Block of Lapis Lazuli',
	'lapis_lazuli|青金石|Lapis Lazuli',
	'lapis_ore|青金石矿石|Lapis Lazuli Ore',
	'large_amethyst_bud|大型紫晶芽|Large Amethyst Bud',
	'large_fern|大型蕨|Large Fern',
	'lava_bucket|熔岩桶|Lava Bucket',
	'lead|拴绳|Lead',
	'leaf_litter|枯叶堆|Leaf Litter',
	'leather|皮革|Leather',
	'leather_boots|皮革靴子|Leather Boots',
	'leather_chestplate|皮革外套|Leather Tunic',
	'leather_helmet|皮革帽子|Leather Cap',
	'leather_horse_armor|皮革马铠|Leather Horse Armor',
	'leather_leggings|皮革裤子|Leather Pants',
	'lectern|讲台|Lectern',
	'lever|拉杆|Lever',
	'light|光源方块|Light',
	'light_blue_banner|淡蓝色旗帜|Light Blue Banner',
	'light_blue_bed|淡蓝色床|Light Blue Bed',
	'light_blue_bundle|淡蓝色收纳袋|Light Blue Bundle',
	'light_blue_candle|淡蓝色蜡烛|Light Blue Candle',
	'light_blue_carpet|淡蓝色地毯|Light Blue Carpet',
	'light_blue_concrete|淡蓝色混凝土|Light Blue Concrete',
	'light_blue_concrete_powder|淡蓝色混凝土粉末|Light Blue Concrete Powder',
	'light_blue_dye|淡蓝色染料|Light Blue Dye',
	'light_blue_glazed_terracotta|淡蓝色带釉陶瓦|Light Blue Glazed Terracotta',
	'light_blue_harness|淡蓝色挽具|Light Blue Harness',
	'light_blue_shulker_box|淡蓝色潜影盒|Light Blue Shulker Box',
	'light_blue_stained_glass|淡蓝色染色玻璃|Light Blue Stained Glass',
	'light_blue_stained_glass_pane|淡蓝色染色玻璃板|Light Blue Stained Glass Pane',
	'light_blue_terracotta|淡蓝色陶瓦|Light Blue Terracotta',
	'light_blue_wool|淡蓝色羊毛|Light Blue Wool',
	'light_gray_banner|淡灰色旗帜|Light Gray Banner',
	'light_gray_bed|淡灰色床|Light Gray Bed',
	'light_gray_bundle|淡灰色收纳袋|Light Gray Bundle',
	'light_gray_candle|淡灰色蜡烛|Light Gray Candle',
	'light_gray_carpet|淡灰色地毯|Light Gray Carpet',
	'light_gray_concrete|淡灰色混凝土|Light Gray Concrete',
	'light_gray_concrete_powder|淡灰色混凝土粉末|Light Gray Concrete Powder',
	'light_gray_dye|淡灰色染料|Light Gray Dye',
	'light_gray_glazed_terracotta|淡灰色带釉陶瓦|Light Gray Glazed Terracotta',
	'light_gray_harness|淡灰色挽具|Light Gray Harness',
	'light_gray_shulker_box|淡灰色潜影盒|Light Gray Shulker Box',
	'light_gray_stained_glass|淡灰色染色玻璃|Light Gray Stained Glass',
	'light_gray_stained_glass_pane|淡灰色染色玻璃板|Light Gray Stained Glass Pane',
	'light_gray_terracotta|淡灰色陶瓦|Light Gray Terracotta',
	'light_gray_wool|淡灰色羊毛|Light Gray Wool',
	'light_weighted_pressure_plate|轻质测重压力板|Light Weighted Pressure Plate',
	'lightning_rod|避雷针|Lightning Rod',
	'lilac|丁香|Lilac',
	'lily_of_the_valley|铃兰|Lily of the Valley',
	'lily_pad|睡莲|Lily Pad',
	'lime_banner|黄绿色旗帜|Lime Banner',
	'lime_bed|黄绿色床|Lime Bed',
	'lime_bundle|黄绿色收纳袋|Lime Bundle',
	'lime_candle|黄绿色蜡烛|Lime Candle',
	'lime_carpet|黄绿色地毯|Lime Carpet',
	'lime_concrete|黄绿色混凝土|Lime Concrete',
	'lime_concrete_powder|黄绿色混凝土粉末|Lime Concrete Powder',
	'lime_dye|黄绿色染料|Lime Dye',
	'lime_glazed_terracotta|黄绿色带釉陶瓦|Lime Glazed Terracotta',
	'lime_harness|黄绿色挽具|Lime Harness',
	'lime_shulker_box|黄绿色潜影盒|Lime Shulker Box',
	'lime_stained_glass|黄绿色染色玻璃|Lime Stained Glass',
	'lime_stained_glass_pane|黄绿色染色玻璃板|Lime Stained Glass Pane',
	'lime_terracotta|黄绿色陶瓦|Lime Terracotta',
	'lime_wool|黄绿色羊毛|Lime Wool',
	'lingering_potion|滞留药水|Lingering Potion',
	'llama_spawn_egg|羊驼刷怪蛋|Llama Spawn Egg',
	'lodestone|磁石|Lodestone',
	'loom|织布机|Loom',
	'mace|重锤|Mace',
	'magenta_banner|品红色旗帜|Magenta Banner',
	'magenta_bed|品红色床|Magenta Bed',
	'magenta_bundle|品红色收纳袋|Magenta Bundle',
	'magenta_candle|品红色蜡烛|Magenta Candle',
	'magenta_carpet|品红色地毯|Magenta Carpet',
	'magenta_concrete|品红色混凝土|Magenta Concrete',
	'magenta_concrete_powder|品红色混凝土粉末|Magenta Concrete Powder',
	'magenta_dye|品红色染料|Magenta Dye',
	'magenta_glazed_terracotta|品红色带釉陶瓦|Magenta Glazed Terracotta',
	'magenta_harness|品红色挽具|Magenta Harness',
	'magenta_shulker_box|品红色潜影盒|Magenta Shulker Box',
	'magenta_stained_glass|品红色染色玻璃|Magenta Stained Glass',
	'magenta_stained_glass_pane|品红色染色玻璃板|Magenta Stained Glass Pane',
	'magenta_terracotta|品红色陶瓦|Magenta Terracotta',
	'magenta_wool|品红色羊毛|Magenta Wool',
	'magma_block|岩浆块|Magma Block',
	'magma_cream|岩浆膏|Magma Cream',
	'magma_cube_spawn_egg|岩浆怪刷怪蛋|Magma Cube Spawn Egg',
	'mangrove_boat|红树木船|Mangrove Boat',
	'mangrove_button|红树木按钮|Mangrove Button',
	'mangrove_chest_boat|红树木运输船|Mangrove Boat with Chest',
	'mangrove_door|红树木门|Mangrove Door',
	'mangrove_fence|红树木栅栏|Mangrove Fence',
	'mangrove_fence_gate|红树木栅栏门|Mangrove Fence Gate',
	'mangrove_hanging_sign|悬挂式红树木告示牌|Mangrove Hanging Sign',
	'mangrove_leaves|红树树叶|Mangrove Leaves',
	'mangrove_log|红树原木|Mangrove Log',
	'mangrove_planks|红树木板|Mangrove Planks',
	'mangrove_pressure_plate|红树木压力板|Mangrove Pressure Plate',
	'mangrove_propagule|红树胎生苗|Mangrove Propagule',
	'mangrove_roots|红树根|Mangrove Roots',
	'mangrove_shelf|红树木展示架|Mangrove Shelf',
	'mangrove_sign|红树木告示牌|Mangrove Sign',
	'mangrove_slab|红树木台阶|Mangrove Slab',
	'mangrove_stairs|红树木楼梯|Mangrove Stairs',
	'mangrove_trapdoor|红树木活板门|Mangrove Trapdoor',
	'mangrove_wood|红树木|Mangrove Wood',
	'map|空地图|Empty Map',
	'medium_amethyst_bud|中型紫晶芽|Medium Amethyst Bud',
	'melon|西瓜|Melon',
	'melon_seeds|西瓜种子|Melon Seeds',
	'melon_slice|西瓜片|Melon Slice',
	'milk_bucket|奶桶|Milk Bucket',
	'minecart|矿车|Minecart',
	'miner_pottery_sherd|采矿纹样陶片|Miner Pottery Sherd',
	'mojang_banner_pattern|旗帜图案|Banner Pattern',
	'mooshroom_spawn_egg|哞菇刷怪蛋|Mooshroom Spawn Egg',
	'moss_block|苔藓块|Moss Block',
	'moss_carpet|覆地苔藓|Moss Carpet',
	'mossy_cobblestone|苔石|Mossy Cobblestone',
	'mossy_cobblestone_slab|苔石台阶|Mossy Cobblestone Slab',
	'mossy_cobblestone_stairs|苔石楼梯|Mossy Cobblestone Stairs',
	'mossy_cobblestone_wall|苔石墙|Mossy Cobblestone Wall',
	'mossy_stone_brick_slab|苔石砖台阶|Mossy Stone Brick Slab',
	'mossy_stone_brick_stairs|苔石砖楼梯|Mossy Stone Brick Stairs',
	'mossy_stone_brick_wall|苔石砖墙|Mossy Stone Brick Wall',
	'mossy_stone_bricks|苔石砖|Mossy Stone Bricks',
	'mourner_pottery_sherd|悲恸纹样陶片|Mourner Pottery Sherd',
	'mud|泥巴|Mud',
	'mud_brick_slab|泥砖台阶|Mud Brick Slab',
	'mud_brick_stairs|泥砖楼梯|Mud Brick Stairs',
	'mud_brick_wall|泥砖墙|Mud Brick Wall',
	'mud_bricks|泥砖|Mud Bricks',
	'muddy_mangrove_roots|沾泥的红树根|Muddy Mangrove Roots',
	'mule_spawn_egg|骡刷怪蛋|Mule Spawn Egg',
	'mushroom_stem|蘑菇柄|Mushroom Stem',
	'mushroom_stew|蘑菇煲|Mushroom Stew',
	'music_disc_11|音乐唱片|Music Disc',
	'music_disc_13|音乐唱片|Music Disc',
	'music_disc_5|音乐唱片|Music Disc',
	'music_disc_blocks|音乐唱片|Music Disc',
	'music_disc_cat|音乐唱片|Music Disc',
	'music_disc_chirp|音乐唱片|Music Disc',
	'music_disc_creator|音乐唱片|Music Disc',
	'music_disc_creator_music_box|音乐唱片|Music Disc',
	'music_disc_far|音乐唱片|Music Disc',
	'music_disc_lava_chicken|音乐唱片|Music Disc',
	'music_disc_mall|音乐唱片|Music Disc',
	'music_disc_mellohi|音乐唱片|Music Disc',
	'music_disc_otherside|音乐唱片|Music Disc',
	'music_disc_pigstep|音乐唱片|Music Disc',
	'music_disc_precipice|音乐唱片|Music Disc',
	'music_disc_relic|音乐唱片|Music Disc',
	'music_disc_stal|音乐唱片|Music Disc',
	'music_disc_strad|音乐唱片|Music Disc',
	'music_disc_tears|音乐唱片|Music Disc',
	'music_disc_wait|音乐唱片|Music Disc',
	'music_disc_ward|音乐唱片|Music Disc',
	'mutton|生羊肉|Raw Mutton',
	'mycelium|菌丝体|Mycelium',
	'name_tag|命名牌|Name Tag',
	'nautilus_shell|鹦鹉螺壳|Nautilus Shell',
	'nautilus_spawn_egg|鹦鹉螺刷怪蛋|Nautilus Spawn Egg',
	'nether_brick|下界砖|Nether Brick',
	'nether_brick_fence|下界砖栅栏|Nether Brick Fence',
	'nether_brick_slab|下界砖台阶|Nether Brick Slab',
	'nether_brick_stairs|下界砖楼梯|Nether Brick Stairs',
	'nether_brick_wall|下界砖墙|Nether Brick Wall',
	'nether_bricks|下界砖块|Nether Bricks',
	'nether_gold_ore|下界金矿石|Nether Gold Ore',
	'nether_quartz_ore|下界石英矿石|Nether Quartz Ore',
	'nether_sprouts|下界苗|Nether Sprouts',
	'nether_star|下界之星|Nether Star',
	'nether_wart|下界疣|Nether Wart',
	'nether_wart_block|下界疣块|Nether Wart Block',
	'netherite_axe|下界合金斧|Netherite Axe',
	'netherite_block|下界合金块|Block of Netherite',
	'netherite_boots|下界合金靴子|Netherite Boots',
	'netherite_chestplate|下界合金胸甲|Netherite Chestplate',
	'netherite_helmet|下界合金头盔|Netherite Helmet',
	'netherite_hoe|下界合金锄|Netherite Hoe',
	'netherite_horse_armor|下界合金马铠|Netherite Horse Armor',
	'netherite_ingot|下界合金锭|Netherite Ingot',
	'netherite_leggings|下界合金护腿|Netherite Leggings',
	'netherite_nautilus_armor|下界合金鹦鹉螺铠|Netherite Nautilus Armor',
	'netherite_pickaxe|下界合金镐|Netherite Pickaxe',
	'netherite_scrap|下界合金碎片|Netherite Scrap',
	'netherite_shovel|下界合金锹|Netherite Shovel',
	'netherite_spear|下界合金矛|Netherite Spear',
	'netherite_sword|下界合金剑|Netherite Sword',
	'netherite_upgrade_smithing_template|锻造模板|Smithing Template',
	'netherrack|下界岩|Netherrack',
	'note_block|音符盒|Note Block',
	'oak_boat|橡木船|Oak Boat',
	'oak_button|橡木按钮|Oak Button',
	'oak_chest_boat|橡木运输船|Oak Boat with Chest',
	'oak_door|橡木门|Oak Door',
	'oak_fence|橡木栅栏|Oak Fence',
	'oak_fence_gate|橡木栅栏门|Oak Fence Gate',
	'oak_hanging_sign|悬挂式橡木告示牌|Oak Hanging Sign',
	'oak_leaves|橡树树叶|Oak Leaves',
	'oak_log|橡木原木|Oak Log',
	'oak_planks|橡木木板|Oak Planks',
	'oak_pressure_plate|橡木压力板|Oak Pressure Plate',
	'oak_sapling|橡树树苗|Oak Sapling',
	'oak_shelf|橡木展示架|Oak Shelf',
	'oak_sign|橡木告示牌|Oak Sign',
	'oak_slab|橡木台阶|Oak Slab',
	'oak_stairs|橡木楼梯|Oak Stairs',
	'oak_trapdoor|橡木活板门|Oak Trapdoor',
	'oak_wood|橡木|Oak Wood',
	'observer|侦测器|Observer',
	'obsidian|黑曜石|Obsidian',
	'ocelot_spawn_egg|豹猫刷怪蛋|Ocelot Spawn Egg',
	'ochre_froglight|赭黄蛙明灯|Ochre Froglight',
	'ominous_bottle|不祥之瓶|Ominous Bottle',
	'ominous_trial_key|不祥试炼钥匙|Ominous Trial Key',
	'open_eyeblossom|张开的眼眸花|Open Eyeblossom',
	'orange_banner|橙色旗帜|Orange Banner',
	'orange_bed|橙色床|Orange Bed',
	'orange_bundle|橙色收纳袋|Orange Bundle',
	'orange_candle|橙色蜡烛|Orange Candle',
	'orange_carpet|橙色地毯|Orange Carpet',
	'orange_concrete|橙色混凝土|Orange Concrete',
	'orange_concrete_powder|橙色混凝土粉末|Orange Concrete Powder',
	'orange_dye|橙色染料|Orange Dye',
	'orange_glazed_terracotta|橙色带釉陶瓦|Orange Glazed Terracotta',
	'orange_harness|橙色挽具|Orange Harness',
	'orange_shulker_box|橙色潜影盒|Orange Shulker Box',
	'orange_stained_glass|橙色染色玻璃|Orange Stained Glass',
	'orange_stained_glass_pane|橙色染色玻璃板|Orange Stained Glass Pane',
	'orange_terracotta|橙色陶瓦|Orange Terracotta',
	'orange_tulip|橙色郁金香|Orange Tulip',
	'orange_wool|橙色羊毛|Orange Wool',
	'oxeye_daisy|滨菊|Oxeye Daisy',
	'oxidized_chiseled_copper|氧化的雕纹铜块|Oxidized Chiseled Copper',
	'oxidized_copper|氧化的铜块|Oxidized Copper',
	'oxidized_copper_bars|氧化的铜栏杆|Oxidized Copper Bars',
	'oxidized_copper_bulb|氧化的铜灯|Oxidized Copper Bulb',
	'oxidized_copper_chain|氧化的铜链|Oxidized Copper Chain',
	'oxidized_copper_chest|氧化的铜箱子|Oxidized Copper Chest',
	'oxidized_copper_door|氧化的铜门|Oxidized Copper Door',
	'oxidized_copper_golem_statue|氧化的铜傀儡像|Oxidized Copper Golem Statue',
	'oxidized_copper_grate|氧化的铜格栅|Oxidized Copper Grate',
	'oxidized_copper_lantern|氧化的铜灯笼|Oxidized Copper Lantern',
	'oxidized_copper_trapdoor|氧化的铜活板门|Oxidized Copper Trapdoor',
	'oxidized_cut_copper|氧化的切制铜块|Oxidized Cut Copper',
	'oxidized_cut_copper_slab|氧化的切制铜台阶|Oxidized Cut Copper Slab',
	'oxidized_cut_copper_stairs|氧化的切制铜楼梯|Oxidized Cut Copper Stairs',
	'oxidized_lightning_rod|氧化的避雷针|Oxidized Lightning Rod',
	'packed_ice|浮冰|Packed Ice',
	'packed_mud|泥坯|Packed Mud',
	'painting|画|Painting',
	'pale_hanging_moss|苍白垂须|Pale Hanging Moss',
	'pale_moss_block|苍白苔藓块|Pale Moss Block',
	'pale_moss_carpet|苍白覆地苔藓|Pale Moss Carpet',
	'pale_oak_boat|苍白橡木船|Pale Oak Boat',
	'pale_oak_button|苍白橡木按钮|Pale Oak Button',
	'pale_oak_chest_boat|苍白橡木运输船|Pale Oak Boat with Chest',
	'pale_oak_door|苍白橡木门|Pale Oak Door',
	'pale_oak_fence|苍白橡木栅栏|Pale Oak Fence',
	'pale_oak_fence_gate|苍白橡木栅栏门|Pale Oak Fence Gate',
	'pale_oak_hanging_sign|悬挂式苍白橡木告示牌|Pale Oak Hanging Sign',
	'pale_oak_leaves|苍白橡树树叶|Pale Oak Leaves',
	'pale_oak_log|苍白橡木原木|Pale Oak Log',
	'pale_oak_planks|苍白橡木木板|Pale Oak Planks',
	'pale_oak_pressure_plate|苍白橡木压力板|Pale Oak Pressure Plate',
	'pale_oak_sapling|苍白橡树树苗|Pale Oak Sapling',
	'pale_oak_shelf|苍白橡木展示架|Pale Oak Shelf',
	'pale_oak_sign|苍白橡木告示牌|Pale Oak Sign',
	'pale_oak_slab|苍白橡木台阶|Pale Oak Slab',
	'pale_oak_stairs|苍白橡木楼梯|Pale Oak Stairs',
	'pale_oak_trapdoor|苍白橡木活板门|Pale Oak Trapdoor',
	'pale_oak_wood|苍白橡木|Pale Oak Wood',
	'panda_spawn_egg|熊猫刷怪蛋|Panda Spawn Egg',
	'paper|纸|Paper',
	'parched_spawn_egg|焦骸刷怪蛋|Parched Spawn Egg',
	'parrot_spawn_egg|鹦鹉刷怪蛋|Parrot Spawn Egg',
	'pearlescent_froglight|珠光蛙明灯|Pearlescent Froglight',
	'peony|牡丹|Peony',
	'petrified_oak_slab|石化橡木台阶|Petrified Oak Slab',
	'phantom_membrane|幻翼膜|Phantom Membrane',
	'phantom_spawn_egg|幻翼刷怪蛋|Phantom Spawn Egg',
	'pig_spawn_egg|猪刷怪蛋|Pig Spawn Egg',
	'piglin_banner_pattern|旗帜图案|Banner Pattern',
	'piglin_brute_spawn_egg|猪灵蛮兵刷怪蛋|Piglin Brute Spawn Egg',
	'piglin_head|猪灵的头|Piglin Head',
	'piglin_spawn_egg|猪灵刷怪蛋|Piglin Spawn Egg',
	'pillager_spawn_egg|掠夺者刷怪蛋|Pillager Spawn Egg',
	'pink_banner|粉红色旗帜|Pink Banner',
	'pink_bed|粉红色床|Pink Bed',
	'pink_bundle|粉红色收纳袋|Pink Bundle',
	'pink_candle|粉红色蜡烛|Pink Candle',
	'pink_carpet|粉红色地毯|Pink Carpet',
	'pink_concrete|粉红色混凝土|Pink Concrete',
	'pink_concrete_powder|粉红色混凝土粉末|Pink Concrete Powder',
	'pink_dye|粉红色染料|Pink Dye',
	'pink_glazed_terracotta|粉红色带釉陶瓦|Pink Glazed Terracotta',
	'pink_harness|粉红色挽具|Pink Harness',
	'pink_petals|粉红色花簇|Pink Petals',
	'pink_shulker_box|粉红色潜影盒|Pink Shulker Box',
	'pink_stained_glass|粉红色染色玻璃|Pink Stained Glass',
	'pink_stained_glass_pane|粉红色染色玻璃板|Pink Stained Glass Pane',
	'pink_terracotta|粉红色陶瓦|Pink Terracotta',
	'pink_tulip|粉红色郁金香|Pink Tulip',
	'pink_wool|粉红色羊毛|Pink Wool',
	'piston|活塞|Piston',
	'pitcher_plant|瓶子草|Pitcher Plant',
	'pitcher_pod|瓶子草荚果|Pitcher Pod',
	'player_head|玩家的头|Player Head',
	'plenty_pottery_sherd|富饶纹样陶片|Plenty Pottery Sherd',
	'podzol|灰化土|Podzol',
	'pointed_dripstone|滴水石锥|Pointed Dripstone',
	'poisonous_potato|毒马铃薯|Poisonous Potato',
	'polar_bear_spawn_egg|北极熊刷怪蛋|Polar Bear Spawn Egg',
	'polished_andesite|磨制安山岩|Polished Andesite',
	'polished_andesite_slab|磨制安山岩台阶|Polished Andesite Slab',
	'polished_andesite_stairs|磨制安山岩楼梯|Polished Andesite Stairs',
	'polished_basalt|磨制玄武岩|Polished Basalt',
	'polished_blackstone|磨制黑石|Polished Blackstone',
	'polished_blackstone_brick_slab|磨制黑石砖台阶|Polished Blackstone Brick Slab',
	'polished_blackstone_brick_stairs|磨制黑石砖楼梯|Polished Blackstone Brick Stairs',
	'polished_blackstone_brick_wall|磨制黑石砖墙|Polished Blackstone Brick Wall',
	'polished_blackstone_bricks|磨制黑石砖|Polished Blackstone Bricks',
	'polished_blackstone_button|磨制黑石按钮|Polished Blackstone Button',
	'polished_blackstone_pressure_plate|磨制黑石压力板|Polished Blackstone Pressure Plate',
	'polished_blackstone_slab|磨制黑石台阶|Polished Blackstone Slab',
	'polished_blackstone_stairs|磨制黑石楼梯|Polished Blackstone Stairs',
	'polished_blackstone_wall|磨制黑石墙|Polished Blackstone Wall',
	'polished_deepslate|磨制深板岩|Polished Deepslate',
	'polished_deepslate_slab|磨制深板岩台阶|Polished Deepslate Slab',
	'polished_deepslate_stairs|磨制深板岩楼梯|Polished Deepslate Stairs',
	'polished_deepslate_wall|磨制深板岩墙|Polished Deepslate Wall',
	'polished_diorite|磨制闪长岩|Polished Diorite',
	'polished_diorite_slab|磨制闪长岩台阶|Polished Diorite Slab',
	'polished_diorite_stairs|磨制闪长岩楼梯|Polished Diorite Stairs',
	'polished_granite|磨制花岗岩|Polished Granite',
	'polished_granite_slab|磨制花岗岩台阶|Polished Granite Slab',
	'polished_granite_stairs|磨制花岗岩楼梯|Polished Granite Stairs',
	'polished_tuff|磨制凝灰岩|Polished Tuff',
	'polished_tuff_slab|磨制凝灰岩台阶|Polished Tuff Slab',
	'polished_tuff_stairs|磨制凝灰岩楼梯|Polished Tuff Stairs',
	'polished_tuff_wall|磨制凝灰岩墙|Polished Tuff Wall',
	'popped_chorus_fruit|爆裂紫颂果|Popped Chorus Fruit',
	'poppy|虞美人|Poppy',
	'porkchop|生猪排|Raw Porkchop',
	'potato|马铃薯|Potato',
	'potion|药水|Potion',
	'powder_snow_bucket|细雪桶|Powder Snow Bucket',
	'powered_rail|动力铁轨|Powered Rail',
	'prismarine|海晶石|Prismarine',
	'prismarine_brick_slab|海晶石砖台阶|Prismarine Brick Slab',
	'prismarine_brick_stairs|海晶石砖楼梯|Prismarine Brick Stairs',
	'prismarine_bricks|海晶石砖|Prismarine Bricks',
	'prismarine_crystals|海晶砂粒|Prismarine Crystals',
	'prismarine_shard|海晶碎片|Prismarine Shard',
	'prismarine_slab|海晶石台阶|Prismarine Slab',
	'prismarine_stairs|海晶石楼梯|Prismarine Stairs',
	'prismarine_wall|海晶石墙|Prismarine Wall',
	'prize_pottery_sherd|珍宝纹样陶片|Prize Pottery Sherd',
	'pufferfish|河豚|Pufferfish',
	'pufferfish_bucket|河豚桶|Bucket of Pufferfish',
	'pufferfish_spawn_egg|河豚刷怪蛋|Pufferfish Spawn Egg',
	'pumpkin|南瓜|Pumpkin',
	'pumpkin_pie|南瓜派|Pumpkin Pie',
	'pumpkin_seeds|南瓜种子|Pumpkin Seeds',
	'purple_banner|紫色旗帜|Purple Banner',
	'purple_bed|紫色床|Purple Bed',
	'purple_bundle|紫色收纳袋|Purple Bundle',
	'purple_candle|紫色蜡烛|Purple Candle',
	'purple_carpet|紫色地毯|Purple Carpet',
	'purple_concrete|紫色混凝土|Purple Concrete',
	'purple_concrete_powder|紫色混凝土粉末|Purple Concrete Powder',
	'purple_dye|紫色染料|Purple Dye',
	'purple_glazed_terracotta|紫色带釉陶瓦|Purple Glazed Terracotta',
	'purple_harness|紫色挽具|Purple Harness',
	'purple_shulker_box|紫色潜影盒|Purple Shulker Box',
	'purple_stained_glass|紫色染色玻璃|Purple Stained Glass',
	'purple_stained_glass_pane|紫色染色玻璃板|Purple Stained Glass Pane',
	'purple_terracotta|紫色陶瓦|Purple Terracotta',
	'purple_wool|紫色羊毛|Purple Wool',
	'purpur_block|紫珀块|Purpur Block',
	'purpur_pillar|紫珀柱|Purpur Pillar',
	'purpur_slab|紫珀台阶|Purpur Slab',
	'purpur_stairs|紫珀楼梯|Purpur Stairs',
	'quartz|下界石英|Nether Quartz',
	'quartz_block|石英块|Block of Quartz',
	'quartz_bricks|石英砖|Quartz Bricks',
	'quartz_pillar|石英柱|Quartz Pillar',
	'quartz_slab|石英台阶|Quartz Slab',
	'quartz_stairs|石英楼梯|Quartz Stairs',
	'rabbit|生兔肉|Raw Rabbit',
	'rabbit_foot|兔子脚|Rabbit\'s Foot',
	'rabbit_hide|兔子皮|Rabbit Hide',
	'rabbit_spawn_egg|兔子刷怪蛋|Rabbit Spawn Egg',
	'rabbit_stew|兔肉煲|Rabbit Stew',
	'rail|铁轨|Rail',
	'raiser_armor_trim_smithing_template|锻造模板|Smithing Template',
	'ravager_spawn_egg|劫掠兽刷怪蛋|Ravager Spawn Egg',
	'raw_copper|粗铜|Raw Copper',
	'raw_copper_block|粗铜块|Block of Raw Copper',
	'raw_gold|粗金|Raw Gold',
	'raw_gold_block|粗金块|Block of Raw Gold',
	'raw_iron|粗铁|Raw Iron',
	'raw_iron_block|粗铁块|Block of Raw Iron',
	'recovery_compass|追溯指针|Recovery Compass',
	'red_banner|红色旗帜|Red Banner',
	'red_bed|红色床|Red Bed',
	'red_bundle|红色收纳袋|Red Bundle',
	'red_candle|红色蜡烛|Red Candle',
	'red_carpet|红色地毯|Red Carpet',
	'red_concrete|红色混凝土|Red Concrete',
	'red_concrete_powder|红色混凝土粉末|Red Concrete Powder',
	'red_dye|红色染料|Red Dye',
	'red_glazed_terracotta|红色带釉陶瓦|Red Glazed Terracotta',
	'red_harness|红色挽具|Red Harness',
	'red_mushroom|红色蘑菇|Red Mushroom',
	'red_mushroom_block|红色蘑菇方块|Red Mushroom Block',
	'red_nether_brick_slab|红色下界砖台阶|Red Nether Brick Slab',
	'red_nether_brick_stairs|红色下界砖楼梯|Red Nether Brick Stairs',
	'red_nether_brick_wall|红色下界砖墙|Red Nether Brick Wall',
	'red_nether_bricks|红色下界砖块|Red Nether Bricks',
	'red_sand|红沙|Red Sand',
	'red_sandstone|红砂岩|Red Sandstone',
	'red_sandstone_slab|红砂岩台阶|Red Sandstone Slab',
	'red_sandstone_stairs|红砂岩楼梯|Red Sandstone Stairs',
	'red_sandstone_wall|红砂岩墙|Red Sandstone Wall',
	'red_shulker_box|红色潜影盒|Red Shulker Box',
	'red_stained_glass|红色染色玻璃|Red Stained Glass',
	'red_stained_glass_pane|红色染色玻璃板|Red Stained Glass Pane',
	'red_terracotta|红色陶瓦|Red Terracotta',
	'red_tulip|红色郁金香|Red Tulip',
	'red_wool|红色羊毛|Red Wool',
	'redstone|红石粉|Redstone Dust',
	'redstone_block|红石块|Block of Redstone',
	'redstone_lamp|红石灯|Redstone Lamp',
	'redstone_ore|红石矿石|Redstone Ore',
	'redstone_torch|红石火把|Redstone Torch',
	'reinforced_deepslate|强化深板岩|Reinforced Deepslate',
	'repeater|红石中继器|Redstone Repeater',
	'repeating_command_block|循环型命令方块|Repeating Command Block',
	'resin_block|树脂块|Block of Resin',
	'resin_brick|树脂砖|Resin Brick',
	'resin_brick_slab|树脂砖台阶|Resin Brick Slab',
	'resin_brick_stairs|树脂砖楼梯|Resin Brick Stairs',
	'resin_brick_wall|树脂砖墙|Resin Brick Wall',
	'resin_bricks|树脂砖块|Resin Bricks',
	'resin_clump|树脂团|Resin Clump',
	'respawn_anchor|重生锚|Respawn Anchor',
	'rib_armor_trim_smithing_template|锻造模板|Smithing Template',
	'rooted_dirt|缠根泥土|Rooted Dirt',
	'rose_bush|玫瑰丛|Rose Bush',
	'rotten_flesh|腐肉|Rotten Flesh',
	'saddle|鞍|Saddle',
	'salmon|生鲑鱼|Raw Salmon',
	'salmon_bucket|鲑鱼桶|Bucket of Salmon',
	'salmon_spawn_egg|鲑鱼刷怪蛋|Salmon Spawn Egg',
	'sand|沙子|Sand',
	'sandstone|砂岩|Sandstone',
	'sandstone_slab|砂岩台阶|Sandstone Slab',
	'sandstone_stairs|砂岩楼梯|Sandstone Stairs',
	'sandstone_wall|砂岩墙|Sandstone Wall',
	'scaffolding|脚手架|Scaffolding',
	'scrape_pottery_sherd|刮削纹样陶片|Scrape Pottery Sherd',
	'sculk|幽匿块|Sculk',
	'sculk_catalyst|幽匿催发体|Sculk Catalyst',
	'sculk_sensor|幽匿感测体|Sculk Sensor',
	'sculk_shrieker|幽匿尖啸体|Sculk Shrieker',
	'sculk_vein|幽匿脉络|Sculk Vein',
	'sea_lantern|海晶灯|Sea Lantern',
	'sea_pickle|海泡菜|Sea Pickle',
	'seagrass|海草|Seagrass',
	'sentry_armor_trim_smithing_template|锻造模板|Smithing Template',
	'shaper_armor_trim_smithing_template|锻造模板|Smithing Template',
	'sheaf_pottery_sherd|麦捆纹样陶片|Sheaf Pottery Sherd',
	'shears|剪刀|Shears',
	'sheep_spawn_egg|绵羊刷怪蛋|Sheep Spawn Egg',
	'shelter_pottery_sherd|树荫纹样陶片|Shelter Pottery Sherd',
	'shield|盾牌|Shield',
	'short_dry_grass|矮枯草丛|Short Dry Grass',
	'short_grass|矮草丛|Short Grass',
	'shroomlight|菌光体|Shroomlight',
	'shulker_box|潜影盒|Shulker Box',
	'shulker_shell|潜影壳|Shulker Shell',
	'shulker_spawn_egg|潜影贝刷怪蛋|Shulker Spawn Egg',
	'silence_armor_trim_smithing_template|锻造模板|Smithing Template',
	'silverfish_spawn_egg|蠹虫刷怪蛋|Silverfish Spawn Egg',
	'skeleton_horse_spawn_egg|骷髅马刷怪蛋|Skeleton Horse Spawn Egg',
	'skeleton_skull|骷髅头颅|Skeleton Skull',
	'skeleton_spawn_egg|骷髅刷怪蛋|Skeleton Spawn Egg',
	'skull_banner_pattern|旗帜图案|Banner Pattern',
	'skull_pottery_sherd|头颅纹样陶片|Skull Pottery Sherd',
	'slime_ball|黏液球|Slimeball',
	'slime_block|黏液块|Slime Block',
	'slime_spawn_egg|史莱姆刷怪蛋|Slime Spawn Egg',
	'small_amethyst_bud|小型紫晶芽|Small Amethyst Bud',
	'small_dripleaf|小型垂滴叶|Small Dripleaf',
	'smithing_table|锻造台|Smithing Table',
	'smoker|烟熏炉|Smoker',
	'smooth_basalt|平滑玄武岩|Smooth Basalt',
	'smooth_quartz|平滑石英块|Smooth Quartz Block',
	'smooth_quartz_slab|平滑石英台阶|Smooth Quartz Slab',
	'smooth_quartz_stairs|平滑石英楼梯|Smooth Quartz Stairs',
	'smooth_red_sandstone|平滑红砂岩|Smooth Red Sandstone',
	'smooth_red_sandstone_slab|平滑红砂岩台阶|Smooth Red Sandstone Slab',
	'smooth_red_sandstone_stairs|平滑红砂岩楼梯|Smooth Red Sandstone Stairs',
	'smooth_sandstone|平滑砂岩|Smooth Sandstone',
	'smooth_sandstone_slab|平滑砂岩台阶|Smooth Sandstone Slab',
	'smooth_sandstone_stairs|平滑砂岩楼梯|Smooth Sandstone Stairs',
	'smooth_stone|平滑石头|Smooth Stone',
	'smooth_stone_slab|平滑石头台阶|Smooth Stone Slab',
	'sniffer_egg|嗅探兽蛋|Sniffer Egg',
	'sniffer_spawn_egg|嗅探兽刷怪蛋|Sniffer Spawn Egg',
	'snort_pottery_sherd|嗅探纹样陶片|Snort Pottery Sherd',
	'snout_armor_trim_smithing_template|锻造模板|Smithing Template',
	'snow|雪|Snow',
	'snow_block|雪块|Snow Block',
	'snow_golem_spawn_egg|雪傀儡刷怪蛋|Snow Golem Spawn Egg',
	'snowball|雪球|Snowball',
	'soul_campfire|灵魂营火|Soul Campfire',
	'soul_lantern|灵魂灯笼|Soul Lantern',
	'soul_sand|灵魂沙|Soul Sand',
	'soul_soil|灵魂土|Soul Soil',
	'soul_torch|灵魂火把|Soul Torch',
	'spawner|刷怪笼|Monster Spawner',
	'spectral_arrow|光灵箭|Spectral Arrow',
	'spider_eye|蜘蛛眼|Spider Eye',
	'spider_spawn_egg|蜘蛛刷怪蛋|Spider Spawn Egg',
	'spire_armor_trim_smithing_template|锻造模板|Smithing Template',
	'splash_potion|喷溅药水|Splash Potion',
	'sponge|海绵|Sponge',
	'spore_blossom|孢子花|Spore Blossom',
	'spruce_boat|云杉木船|Spruce Boat',
	'spruce_button|云杉木按钮|Spruce Button',
	'spruce_chest_boat|云杉木运输船|Spruce Boat with Chest',
	'spruce_door|云杉木门|Spruce Door',
	'spruce_fence|云杉木栅栏|Spruce Fence',
	'spruce_fence_gate|云杉木栅栏门|Spruce Fence Gate',
	'spruce_hanging_sign|悬挂式云杉木告示牌|Spruce Hanging Sign',
	'spruce_leaves|云杉树叶|Spruce Leaves',
	'spruce_log|云杉原木|Spruce Log',
	'spruce_planks|云杉木板|Spruce Planks',
	'spruce_pressure_plate|云杉木压力板|Spruce Pressure Plate',
	'spruce_sapling|云杉树苗|Spruce Sapling',
	'spruce_shelf|云杉木展示架|Spruce Shelf',
	'spruce_sign|云杉木告示牌|Spruce Sign',
	'spruce_slab|云杉木台阶|Spruce Slab',
	'spruce_stairs|云杉木楼梯|Spruce Stairs',
	'spruce_trapdoor|云杉木活板门|Spruce Trapdoor',
	'spruce_wood|云杉木|Spruce Wood',
	'spyglass|望远镜|Spyglass',
	'squid_spawn_egg|鱿鱼刷怪蛋|Squid Spawn Egg',
	'stick|木棍|Stick',
	'sticky_piston|黏性活塞|Sticky Piston',
	'stone|石头|Stone',
	'stone_axe|石斧|Stone Axe',
	'stone_brick_slab|石砖台阶|Stone Brick Slab',
	'stone_brick_stairs|石砖楼梯|Stone Brick Stairs',
	'stone_brick_wall|石砖墙|Stone Brick Wall',
	'stone_bricks|石砖|Stone Bricks',
	'stone_button|石头按钮|Stone Button',
	'stone_hoe|石锄|Stone Hoe',
	'stone_pickaxe|石镐|Stone Pickaxe',
	'stone_pressure_plate|石头压力板|Stone Pressure Plate',
	'stone_shovel|石锹|Stone Shovel',
	'stone_slab|石头台阶|Stone Slab',
	'stone_spear|石矛|Stone Spear',
	'stone_stairs|石头楼梯|Stone Stairs',
	'stone_sword|石剑|Stone Sword',
	'stonecutter|切石机|Stonecutter',
	'stray_spawn_egg|流浪者刷怪蛋|Stray Spawn Egg',
	'strider_spawn_egg|炽足兽刷怪蛋|Strider Spawn Egg',
	'string|线|String',
	'stripped_acacia_log|去皮金合欢原木|Stripped Acacia Log',
	'stripped_acacia_wood|去皮金合欢木|Stripped Acacia Wood',
	'stripped_bamboo_block|去皮竹块|Block of Stripped Bamboo',
	'stripped_birch_log|去皮白桦原木|Stripped Birch Log',
	'stripped_birch_wood|去皮白桦木|Stripped Birch Wood',
	'stripped_cherry_log|去皮樱花原木|Stripped Cherry Log',
	'stripped_cherry_wood|去皮樱花木|Stripped Cherry Wood',
	'stripped_crimson_hyphae|去皮绯红菌核|Stripped Crimson Hyphae',
	'stripped_crimson_stem|去皮绯红菌柄|Stripped Crimson Stem',
	'stripped_dark_oak_log|去皮深色橡木原木|Stripped Dark Oak Log',
	'stripped_dark_oak_wood|去皮深色橡木|Stripped Dark Oak Wood',
	'stripped_jungle_log|去皮丛林原木|Stripped Jungle Log',
	'stripped_jungle_wood|去皮丛林木|Stripped Jungle Wood',
	'stripped_mangrove_log|去皮红树原木|Stripped Mangrove Log',
	'stripped_mangrove_wood|去皮红树木|Stripped Mangrove Wood',
	'stripped_oak_log|去皮橡木原木|Stripped Oak Log',
	'stripped_oak_wood|去皮橡木|Stripped Oak Wood',
	'stripped_pale_oak_log|去皮苍白橡木原木|Stripped Pale Oak Log',
	'stripped_pale_oak_wood|去皮苍白橡木|Stripped Pale Oak Wood',
	'stripped_spruce_log|去皮云杉原木|Stripped Spruce Log',
	'stripped_spruce_wood|去皮云杉木|Stripped Spruce Wood',
	'stripped_warped_hyphae|去皮诡异菌核|Stripped Warped Hyphae',
	'stripped_warped_stem|去皮诡异菌柄|Stripped Warped Stem',
	'structure_block|结构方块|Structure Block',
	'structure_void|结构空位|Structure Void',
	'sugar|糖|Sugar',
	'sugar_cane|甘蔗|Sugar Cane',
	'sunflower|向日葵|Sunflower',
	'suspicious_gravel|可疑的沙砾|Suspicious Gravel',
	'suspicious_sand|可疑的沙子|Suspicious Sand',
	'suspicious_stew|谜之炖菜|Suspicious Stew',
	'sweet_berries|甜浆果|Sweet Berries',
	'tadpole_bucket|蝌蚪桶|Bucket of Tadpole',
	'tadpole_spawn_egg|蝌蚪刷怪蛋|Tadpole Spawn Egg',
	'tall_dry_grass|高枯草丛|Tall Dry Grass',
	'tall_grass|高草丛|Tall Grass',
	'target|标靶|Target',
	'terracotta|陶瓦|Terracotta',
	'test_block|测试方块|Test Block',
	'test_instance_block|测试实例方块|Test Instance Block',
	'tide_armor_trim_smithing_template|锻造模板|Smithing Template',
	'tinted_glass|遮光玻璃|Tinted Glass',
	'tipped_arrow|药箭|Tipped Arrow',
	'tnt|TNT|TNT',
	'tnt_minecart|TNT矿车|Minecart with TNT',
	'torch|火把|Torch',
	'torchflower|火把花|Torchflower',
	'torchflower_seeds|火把花种子|Torchflower Seeds',
	'totem_of_undying|不死图腾|Totem of Undying',
	'trader_llama_spawn_egg|行商羊驼刷怪蛋|Trader Llama Spawn Egg',
	'trapped_chest|陷阱箱|Trapped Chest',
	'trial_key|试炼钥匙|Trial Key',
	'trial_spawner|试炼刷怪笼|Trial Spawner',
	'trident|三叉戟|Trident',
	'tripwire_hook|绊线钩|Tripwire Hook',
	'tropical_fish|热带鱼|Tropical Fish',
	'tropical_fish_bucket|热带鱼桶|Bucket of Tropical Fish',
	'tropical_fish_spawn_egg|热带鱼刷怪蛋|Tropical Fish Spawn Egg',
	'tube_coral|管珊瑚|Tube Coral',
	'tube_coral_block|管珊瑚块|Tube Coral Block',
	'tube_coral_fan|管珊瑚扇|Tube Coral Fan',
	'tuff|凝灰岩|Tuff',
	'tuff_brick_slab|凝灰岩砖台阶|Tuff Brick Slab',
	'tuff_brick_stairs|凝灰岩砖楼梯|Tuff Brick Stairs',
	'tuff_brick_wall|凝灰岩砖墙|Tuff Brick Wall',
	'tuff_bricks|凝灰岩砖|Tuff Bricks',
	'tuff_slab|凝灰岩台阶|Tuff Slab',
	'tuff_stairs|凝灰岩楼梯|Tuff Stairs',
	'tuff_wall|凝灰岩墙|Tuff Wall',
	'turtle_egg|海龟蛋|Turtle Egg',
	'turtle_helmet|海龟壳|Turtle Shell',
	'turtle_scute|海龟鳞甲|Turtle Scute',
	'turtle_spawn_egg|海龟刷怪蛋|Turtle Spawn Egg',
	'twisting_vines|缠怨藤|Twisting Vines',
	'vault|宝库|Vault',
	'verdant_froglight|青翠蛙明灯|Verdant Froglight',
	'vex_armor_trim_smithing_template|锻造模板|Smithing Template',
	'vex_spawn_egg|恼鬼刷怪蛋|Vex Spawn Egg',
	'villager_spawn_egg|村民刷怪蛋|Villager Spawn Egg',
	'vindicator_spawn_egg|卫道士刷怪蛋|Vindicator Spawn Egg',
	'vine|藤蔓|Vines',
	'wandering_trader_spawn_egg|流浪商人刷怪蛋|Wandering Trader Spawn Egg',
	'ward_armor_trim_smithing_template|锻造模板|Smithing Template',
	'warden_spawn_egg|监守者刷怪蛋|Warden Spawn Egg',
	'warped_button|诡异木按钮|Warped Button',
	'warped_door|诡异木门|Warped Door',
	'warped_fence|诡异木栅栏|Warped Fence',
	'warped_fence_gate|诡异木栅栏门|Warped Fence Gate',
	'warped_fungus|诡异菌|Warped Fungus',
	'warped_fungus_on_a_stick|诡异菌钓竿|Warped Fungus on a Stick',
	'warped_hanging_sign|悬挂式诡异木告示牌|Warped Hanging Sign',
	'warped_hyphae|诡异菌核|Warped Hyphae',
	'warped_nylium|诡异菌岩|Warped Nylium',
	'warped_planks|诡异木板|Warped Planks',
	'warped_pressure_plate|诡异木压力板|Warped Pressure Plate',
	'warped_roots|诡异菌索|Warped Roots',
	'warped_shelf|诡异木展示架|Warped Shelf',
	'warped_sign|诡异木告示牌|Warped Sign',
	'warped_slab|诡异木台阶|Warped Slab',
	'warped_stairs|诡异木楼梯|Warped Stairs',
	'warped_stem|诡异菌柄|Warped Stem',
	'warped_trapdoor|诡异木活板门|Warped Trapdoor',
	'warped_wart_block|诡异疣块|Warped Wart Block',
	'water_bucket|水桶|Water Bucket',
	'waxed_chiseled_copper|涂蜡的雕纹铜块|Waxed Chiseled Copper',
	'waxed_copper_bars|涂蜡的铜栏杆|Waxed Copper Bars',
	'waxed_copper_block|涂蜡的铜块|Waxed Block of Copper',
	'waxed_copper_bulb|涂蜡的铜灯|Waxed Copper Bulb',
	'waxed_copper_chain|涂蜡的铜链|Waxed Copper Chain',
	'waxed_copper_chest|涂蜡的铜箱子|Waxed Copper Chest',
	'waxed_copper_door|涂蜡的铜门|Waxed Copper Door',
	'waxed_copper_golem_statue|涂蜡的铜傀儡像|Waxed Copper Golem Statue',
	'waxed_copper_grate|涂蜡的铜格栅|Waxed Copper Grate',
	'waxed_copper_lantern|涂蜡的铜灯笼|Waxed Copper Lantern',
	'waxed_copper_trapdoor|涂蜡的铜活板门|Waxed Copper Trapdoor',
	'waxed_cut_copper|涂蜡的切制铜块|Waxed Cut Copper',
	'waxed_cut_copper_slab|涂蜡的切制铜台阶|Waxed Cut Copper Slab',
	'waxed_cut_copper_stairs|涂蜡的切制铜楼梯|Waxed Cut Copper Stairs',
	'waxed_exposed_chiseled_copper|涂蜡的斑驳雕纹铜块|Waxed Exposed Chiseled Copper',
	'waxed_exposed_copper|涂蜡的斑驳铜块|Waxed Exposed Copper',
	'waxed_exposed_copper_bars|涂蜡的斑驳铜栏杆|Waxed Exposed Copper Bars',
	'waxed_exposed_copper_bulb|涂蜡的斑驳铜灯|Waxed Exposed Copper Bulb',
	'waxed_exposed_copper_chain|涂蜡的斑驳铜链|Waxed Exposed Copper Chain',
	'waxed_exposed_copper_chest|涂蜡的斑驳铜箱子|Waxed Exposed Copper Chest',
	'waxed_exposed_copper_door|涂蜡的斑驳铜门|Waxed Exposed Copper Door',
	'waxed_exposed_copper_golem_statue|涂蜡的斑驳铜傀儡像|Waxed Exposed Copper Golem Statue',
	'waxed_exposed_copper_grate|涂蜡的斑驳铜格栅|Waxed Exposed Copper Grate',
	'waxed_exposed_copper_lantern|涂蜡的斑驳铜灯笼|Waxed Exposed Copper Lantern',
	'waxed_exposed_copper_trapdoor|涂蜡的斑驳铜活板门|Waxed Exposed Copper Trapdoor',
	'waxed_exposed_cut_copper|涂蜡的斑驳切制铜块|Waxed Exposed Cut Copper',
	'waxed_exposed_cut_copper_slab|涂蜡的斑驳切制铜台阶|Waxed Exposed Cut Copper Slab',
	'waxed_exposed_cut_copper_stairs|涂蜡的斑驳切制铜楼梯|Waxed Exposed Cut Copper Stairs',
	'waxed_exposed_lightning_rod|涂蜡的斑驳避雷针|Waxed Exposed Lightning Rod',
	'waxed_lightning_rod|涂蜡的避雷针|Waxed Lightning Rod',
	'waxed_oxidized_chiseled_copper|涂蜡的氧化雕纹铜块|Waxed Oxidized Chiseled Copper',
	'waxed_oxidized_copper|涂蜡的氧化铜块|Waxed Oxidized Copper',
	'waxed_oxidized_copper_bars|涂蜡的氧化铜栏杆|Waxed Oxidized Copper Bars',
	'waxed_oxidized_copper_bulb|涂蜡的氧化铜灯|Waxed Oxidized Copper Bulb',
	'waxed_oxidized_copper_chain|涂蜡的氧化铜链|Waxed Oxidized Copper Chain',
	'waxed_oxidized_copper_chest|涂蜡的氧化铜箱子|Waxed Oxidized Copper Chest',
	'waxed_oxidized_copper_door|涂蜡的氧化铜门|Waxed Oxidized Copper Door',
	'waxed_oxidized_copper_golem_statue|涂蜡的氧化铜傀儡像|Waxed Oxidized Copper Golem Statue',
	'waxed_oxidized_copper_grate|涂蜡的氧化铜格栅|Waxed Oxidized Copper Grate',
	'waxed_oxidized_copper_lantern|涂蜡的氧化铜灯笼|Waxed Oxidized Copper Lantern',
	'waxed_oxidized_copper_trapdoor|涂蜡的氧化铜活板门|Waxed Oxidized Copper Trapdoor',
	'waxed_oxidized_cut_copper|涂蜡的氧化切制铜块|Waxed Oxidized Cut Copper',
	'waxed_oxidized_cut_copper_slab|涂蜡的氧化切制铜台阶|Waxed Oxidized Cut Copper Slab',
	'waxed_oxidized_cut_copper_stairs|涂蜡的氧化切制铜楼梯|Waxed Oxidized Cut Copper Stairs',
	'waxed_oxidized_lightning_rod|涂蜡的氧化避雷针|Waxed Oxidized Lightning Rod',
	'waxed_weathered_chiseled_copper|涂蜡的锈蚀雕纹铜块|Waxed Weathered Chiseled Copper',
	'waxed_weathered_copper|涂蜡的锈蚀铜块|Waxed Weathered Copper',
	'waxed_weathered_copper_bars|涂蜡的锈蚀铜栏杆|Waxed Weathered Copper Bars',
	'waxed_weathered_copper_bulb|涂蜡的锈蚀铜灯|Waxed Weathered Copper Bulb',
	'waxed_weathered_copper_chain|涂蜡的锈蚀铜链|Waxed Weathered Copper Chain',
	'waxed_weathered_copper_chest|涂蜡的锈蚀铜箱子|Waxed Weathered Copper Chest',
	'waxed_weathered_copper_door|涂蜡的锈蚀铜门|Waxed Weathered Copper Door',
	'waxed_weathered_copper_golem_statue|涂蜡的锈蚀铜傀儡像|Waxed Weathered Copper Golem Statue',
	'waxed_weathered_copper_grate|涂蜡的锈蚀铜格栅|Waxed Weathered Copper Grate',
	'waxed_weathered_copper_lantern|涂蜡的锈蚀铜灯笼|Waxed Weathered Copper Lantern',
	'waxed_weathered_copper_trapdoor|涂蜡的锈蚀铜活板门|Waxed Weathered Copper Trapdoor',
	'waxed_weathered_cut_copper|涂蜡的锈蚀切制铜块|Waxed Weathered Cut Copper',
	'waxed_weathered_cut_copper_slab|涂蜡的锈蚀切制铜台阶|Waxed Weathered Cut Copper Slab',
	'waxed_weathered_cut_copper_stairs|涂蜡的锈蚀切制铜楼梯|Waxed Weathered Cut Copper Stairs',
	'waxed_weathered_lightning_rod|涂蜡的锈蚀避雷针|Waxed Weathered Lightning Rod',
	'wayfinder_armor_trim_smithing_template|锻造模板|Smithing Template',
	'weathered_chiseled_copper|锈蚀的雕纹铜块|Weathered Chiseled Copper',
	'weathered_copper|锈蚀的铜块|Weathered Copper',
	'weathered_copper_bars|锈蚀的铜栏杆|Weathered Copper Bars',
	'weathered_copper_bulb|锈蚀的铜灯|Weathered Copper Bulb',
	'weathered_copper_chain|锈蚀的铜链|Weathered Copper Chain',
	'weathered_copper_chest|锈蚀的铜箱子|Weathered Copper Chest',
	'weathered_copper_door|锈蚀的铜门|Weathered Copper Door',
	'weathered_copper_golem_statue|锈蚀的铜傀儡像|Weathered Copper Golem Statue',
	'weathered_copper_grate|锈蚀的铜格栅|Weathered Copper Grate',
	'weathered_copper_lantern|锈蚀的铜灯笼|Weathered Copper Lantern',
	'weathered_copper_trapdoor|锈蚀的铜活板门|Weathered Copper Trapdoor',
	'weathered_cut_copper|锈蚀的切制铜块|Weathered Cut Copper',
	'weathered_cut_copper_slab|锈蚀的切制铜台阶|Weathered Cut Copper Slab',
	'weathered_cut_copper_stairs|锈蚀的切制铜楼梯|Weathered Cut Copper Stairs',
	'weathered_lightning_rod|锈蚀的避雷针|Weathered Lightning Rod',
	'weeping_vines|垂泪藤|Weeping Vines',
	'wet_sponge|湿海绵|Wet Sponge',
	'wheat|小麦|Wheat',
	'wheat_seeds|小麦种子|Wheat Seeds',
	'white_banner|白色旗帜|White Banner',
	'white_bed|白色床|White Bed',
	'white_bundle|白色收纳袋|White Bundle',
	'white_candle|白色蜡烛|White Candle',
	'white_carpet|白色地毯|White Carpet',
	'white_concrete|白色混凝土|White Concrete',
	'white_concrete_powder|白色混凝土粉末|White Concrete Powder',
	'white_dye|白色染料|White Dye',
	'white_glazed_terracotta|白色带釉陶瓦|White Glazed Terracotta',
	'white_harness|白色挽具|White Harness',
	'white_shulker_box|白色潜影盒|White Shulker Box',
	'white_stained_glass|白色染色玻璃|White Stained Glass',
	'white_stained_glass_pane|白色染色玻璃板|White Stained Glass Pane',
	'white_terracotta|白色陶瓦|White Terracotta',
	'white_tulip|白色郁金香|White Tulip',
	'white_wool|白色羊毛|White Wool',
	'wild_armor_trim_smithing_template|锻造模板|Smithing Template',
	'wildflowers|野花簇|Wildflowers',
	'wind_charge|风弹|Wind Charge',
	'witch_spawn_egg|女巫刷怪蛋|Witch Spawn Egg',
	'wither_rose|凋灵玫瑰|Wither Rose',
	'wither_skeleton_skull|凋灵骷髅头颅|Wither Skeleton Skull',
	'wither_skeleton_spawn_egg|凋灵骷髅刷怪蛋|Wither Skeleton Spawn Egg',
	'wither_spawn_egg|凋灵刷怪蛋|Wither Spawn Egg',
	'wolf_armor|狼铠|Wolf Armor',
	'wolf_spawn_egg|狼刷怪蛋|Wolf Spawn Egg',
	'wooden_axe|木斧|Wooden Axe',
	'wooden_hoe|木锄|Wooden Hoe',
	'wooden_pickaxe|木镐|Wooden Pickaxe',
	'wooden_shovel|木锹|Wooden Shovel',
	'wooden_spear|木矛|Wooden Spear',
	'wooden_sword|木剑|Wooden Sword',
	'writable_book|书与笔|Book and Quill',
	'written_book|成书|Written Book',
	'yellow_banner|黄色旗帜|Yellow Banner',
	'yellow_bed|黄色床|Yellow Bed',
	'yellow_bundle|黄色收纳袋|Yellow Bundle',
	'yellow_candle|黄色蜡烛|Yellow Candle',
	'yellow_carpet|黄色地毯|Yellow Carpet',
	'yellow_concrete|黄色混凝土|Yellow Concrete',
	'yellow_concrete_powder|黄色混凝土粉末|Yellow Concrete Powder',
	'yellow_dye|黄色染料|Yellow Dye',
	'yellow_glazed_terracotta|黄色带釉陶瓦|Yellow Glazed Terracotta',
	'yellow_harness|黄色挽具|Yellow Harness',
	'yellow_shulker_box|黄色潜影盒|Yellow Shulker Box',
	'yellow_stained_glass|黄色染色玻璃|Yellow Stained Glass',
	'yellow_stained_glass_pane|黄色染色玻璃板|Yellow Stained Glass Pane',
	'yellow_terracotta|黄色陶瓦|Yellow Terracotta',
	'yellow_wool|黄色羊毛|Yellow Wool',
	'zoglin_spawn_egg|僵尸疣猪兽刷怪蛋|Zoglin Spawn Egg',
	'zombie_head|僵尸的头|Zombie Head',
	'zombie_horse_spawn_egg|僵尸马刷怪蛋|Zombie Horse Spawn Egg',
	'zombie_nautilus_spawn_egg|僵尸鹦鹉螺刷怪蛋|Zombie Nautilus Spawn Egg',
	'zombie_spawn_egg|僵尸刷怪蛋|Zombie Spawn Egg',
	'zombie_villager_spawn_egg|僵尸村民刷怪蛋|Zombie Villager Spawn Egg',
	'zombified_piglin_spawn_egg|僵尸猪灵刷怪蛋|Zombified Piglin Spawn Egg'
];

	/**
	 * 物品菜单项（惰性构建，构建一次后缓存）。
	 *
	 * 必须返回**数组**：编辑器侧 getInfo() 要经 postMessage 结构化克隆，函数不能克隆。
	 */
	function itemMenuItems() {
		if (!itemMenuItems.cache) {
			itemMenuItems.cache = ITEM_TABLE.map((row) => {
				const parts = row.split('|');
				return {text: T(parts[2], parts[1]), value: 'minecraft:' + parts[0]};
			});
		}
		return itemMenuItems.cache;
	}

	class TurboMC {
		getInfo() {
			return {
				// 扩展 id 决定 opcode 前缀：注册为 turbomc，积木在 project.json 里就是
				// turbomc_<opcode>。改这里等于让所有已导出的工程失效。
				id: 'turbomc',
				name: T('TurboMC', 'TurboMC'),
				color1: '#6F45B0',
				blocks: [
					// 远程调试入口：点一下打开连接面板（地址 / 端口 / 连接 / 断开）
					{blockType: Scratch.BlockType.BUTTON, text: T('Remote debug', '远程调试'), func: 'openRemotePanel'},
					{blockType: Scratch.BlockType.LABEL, text: T('Events', '事件')},
					{
						opcode: 'whenDamaged',
						blockType: Scratch.BlockType.HAT,
						text: T('when damaged', '当受伤时'),
						isEdgeActivated: false
					},
					{
						opcode: 'whenDeath',
						blockType: Scratch.BlockType.HAT,
						text: T('when player dies', '当玩家死亡'),
						isEdgeActivated: false
					},
					{
						opcode: 'whenScreenOpen',
						blockType: Scratch.BlockType.HAT,
						text: T('when [SCREEN] opens', '当打开 [SCREEN]'),
						argOrder: ['SCREEN'],
						arguments: {
							SCREEN: {type: Scratch.ArgumentType.STRING, defaultValue: 'container', menu: 'screenKind'}
						},
						isEdgeActivated: false
					},
					{
						opcode: 'whenChat',
						blockType: Scratch.BlockType.HAT,
						text: T('when chat contains [MATCH]', '当聊天包含 [MATCH]'),
						argOrder: ['MATCH'],
						arguments: {
							MATCH: {type: Scratch.ArgumentType.STRING, defaultValue: ''}
						},
						isEdgeActivated: false
					},
					{blockType: Scratch.BlockType.LABEL, text: T('Movement & view', '移动与视角')},
					{
						opcode: 'moveToward',
						blockType: Scratch.BlockType.COMMAND,
						text: T('go to x[X] y[Y] z[Z]', '走向 x[X] y[Y] z[Z]'),
						argOrder: ['X', 'Y', 'Z'],
						arguments: {
							X: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							Y: {type: Scratch.ArgumentType.NUMBER, defaultValue: 64},
							Z: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0}
						}
					},
					{
						opcode: 'look',
						blockType: Scratch.BlockType.COMMAND,
						text: T('set view yaw[YAW] pitch[PITCH]', '设置视角 水平[YAW] 俯仰[PITCH]'),
						argOrder: ['YAW', 'PITCH'],
						arguments: {
							YAW: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							PITCH: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0}
						}
					},
					{
						opcode: 'lookAt',
						blockType: Scratch.BlockType.COMMAND,
						text: T('look at x[X] y[Y] z[Z]', '看向 x[X] y[Y] z[Z]'),
						argOrder: ['X', 'Y', 'Z'],
						arguments: {
							X: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							Y: {type: Scratch.ArgumentType.NUMBER, defaultValue: 64},
							Z: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0}
						}
					},
					{
						opcode: 'lookSmooth',
						blockType: Scratch.BlockType.COMMAND,
						text: T('turn smoothly to yaw[YAW] pitch[PITCH]', '平滑转到视角 水平[YAW] 俯仰[PITCH]'),
						argOrder: ['YAW', 'PITCH'],
						arguments: {
							YAW: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							PITCH: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0}
						}
					},
					{
						opcode: 'lookAtSmooth',
						blockType: Scratch.BlockType.COMMAND,
						text: T('look smoothly at x[X] y[Y] z[Z]', '平滑看向 x[X] y[Y] z[Z]'),
						argOrder: ['X', 'Y', 'Z'],
						arguments: {
							X: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							Y: {type: Scratch.ArgumentType.NUMBER, defaultValue: 64},
							Z: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0}
						}
					},
					{
						opcode: 'stop',
						blockType: Scratch.BlockType.COMMAND,
						text: T('stop moving', '停止移动')
					},
					{blockType: Scratch.BlockType.LABEL, text: T('Input', '输入')},
					{
						opcode: 'key',
						blockType: Scratch.BlockType.COMMAND,
						text: T('key [KEY] [STATE]', '按键 [KEY] [STATE]'),
						argOrder: ['KEY', 'STATE'],
						arguments: {
							KEY: {type: Scratch.ArgumentType.STRING, defaultValue: 'w', menu: 'keyName'},
							STATE: {type: Scratch.ArgumentType.STRING, defaultValue: 'down', menu: 'keyState'}
						}
					},
					{
						opcode: 'click',
						blockType: Scratch.BlockType.COMMAND,
						text: T('click [BUTTON]', '点击 [BUTTON]'),
						argOrder: ['BUTTON'],
						arguments: {
							BUTTON: {type: Scratch.ArgumentType.STRING, defaultValue: 'left', menu: 'mouseButton'}
						}
					},
					{
						opcode: 'tap',
						blockType: Scratch.BlockType.COMMAND,
						text: T('tap [KEY] for [HOLD] ticks', '点按 [KEY] 按住 [HOLD] tick'),
						argOrder: ['KEY', 'HOLD'],
						arguments: {
							KEY: {type: Scratch.ArgumentType.STRING, defaultValue: 'w', menu: 'keyName'},
							HOLD: {type: Scratch.ArgumentType.NUMBER, defaultValue: 1}
						}
					},
					{
						opcode: 'mouseDown',
						blockType: Scratch.BlockType.BOOLEAN,
						text: T('mouse [BUTTON] pressed?', '按下鼠标 [BUTTON]?'),
						argOrder: ['BUTTON'],
						arguments: {
							BUTTON: {type: Scratch.ArgumentType.STRING, defaultValue: 'left', menu: 'mouseButton'}
						}
					},
					{blockType: Scratch.BlockType.LABEL, text: T('Mining', '挖掘')},
					{
						opcode: 'breakWait',
						blockType: Scratch.BlockType.COMMAND,
						text: T('wait until block broken', '等待方块被挖破')
					},
					{
						opcode: 'isMining',
						blockType: Scratch.BlockType.BOOLEAN,
						text: T('is mining', '正在挖掘')
					},
					{
						opcode: 'miningProgress',
						blockType: Scratch.BlockType.REPORTER,
						text: T('mining progress', '挖掘进度')
					},
					{blockType: Scratch.BlockType.LABEL, text: T('Containers', '容器')},
					{
						opcode: 'openContainerAt',
						blockType: Scratch.BlockType.COMMAND,
						text: T('open container at x[X] y[Y] z[Z]', '打开坐标处的容器 x[X] y[Y] z[Z]'),
						argOrder: ['X', 'Y', 'Z'],
						arguments: {
							X: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							Y: {type: Scratch.ArgumentType.NUMBER, defaultValue: 64},
							Z: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0}
						}
					},
					{
						opcode: 'containerSlots',
						blockType: Scratch.BlockType.REPORTER,
						text: T('container slots', '界面槽位数')
					},
					{
						opcode: 'slotField',
						blockType: Scratch.BlockType.REPORTER,
						text: T('slot [INDEX] [FIELD]', '槽位 [INDEX] 的 [FIELD]'),
						argOrder: ['INDEX', 'FIELD'],
						arguments: {
							INDEX: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							FIELD: {type: Scratch.ArgumentType.STRING, defaultValue: 'id', menu: 'slotField'}
						}
					},
					{
						opcode: 'screenJson',
						blockType: Scratch.BlockType.REPORTER,
						text: T('open screen snapshot', '界面快照')
					},
					{
						opcode: 'firstSlotOf',
						blockType: Scratch.BlockType.REPORTER,
						text: T('first slot with [ITEM]', '第一个装着 [ITEM] 的槽位'),
						argOrder: ['ITEM'],
						arguments: {
							ITEM: {type: Scratch.ArgumentType.STRING, defaultValue: 'minecraft:iron_ore', menu: 'itemId'}
						}
					},
					{
						opcode: 'clickSlot',
						blockType: Scratch.BlockType.COMMAND,
						text: T('click slot [INDEX] with [BUTTON]', '点击槽位 [INDEX] 用 [BUTTON]'),
						argOrder: ['INDEX', 'BUTTON'],
						arguments: {
							INDEX: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							BUTTON: {type: Scratch.ArgumentType.STRING, defaultValue: 'left', menu: 'slotButton'}
						}
					},
					{
						opcode: 'quickMoveSlot',
						blockType: Scratch.BlockType.COMMAND,
						text: T('quick move slot [INDEX]', '快速移动槽位 [INDEX]'),
						argOrder: ['INDEX'],
						arguments: {
							INDEX: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0}
						}
					},
					{
						opcode: 'closeContainer',
						blockType: Scratch.BlockType.COMMAND,
						text: T('close container', '关闭界面')
					},
					{blockType: Scratch.BlockType.LABEL, text: T('Player', '玩家状态')},
					{
						opcode: 'health',
						blockType: Scratch.BlockType.REPORTER,
						text: T('health', '血量')
					},
					{
						opcode: 'food',
						blockType: Scratch.BlockType.REPORTER,
						text: T('food level', '饱食度')
					},
					{
						opcode: 'xpLevel',
						blockType: Scratch.BlockType.REPORTER,
						text: T('xp level', '经验等级')
					},
					{
						opcode: 'onGround',
						blockType: Scratch.BlockType.BOOLEAN,
						text: T('on ground?', '站在地面？')
					},
					{
						opcode: 'pos',
						blockType: Scratch.BlockType.REPORTER,
						text: T('player [AXIS] position', '玩家 [AXIS] 坐标'),
						argOrder: ['AXIS'],
						arguments: {
							AXIS: {type: Scratch.ArgumentType.STRING, defaultValue: 'x', menu: 'axis'}
						}
					},
					{
						opcode: 'selectedSlot',
						blockType: Scratch.BlockType.REPORTER,
						text: T('selected slot', '当前槽位')
					},
					{
						opcode: 'heldItemField',
						blockType: Scratch.BlockType.REPORTER,
						text: T('held [FIELD]', '手中的 [FIELD]'),
						argOrder: ['FIELD'],
						arguments: {
							FIELD: {type: Scratch.ArgumentType.STRING, defaultValue: 'id', menu: 'heldField'}
						}
					},
					{
						opcode: 'inventoryJson',
						blockType: Scratch.BlockType.REPORTER,
						text: T('inventory snapshot', '背包快照')
					},
					{
						opcode: 'inventoryFull',
						blockType: Scratch.BlockType.BOOLEAN,
						text: T('inventory full?', '背包已满?')
					},
					{
						opcode: 'playerJson',
						blockType: Scratch.BlockType.REPORTER,
						text: T('player snapshot', '玩家快照')
					},
					{
						opcode: 'itemCount',
						blockType: Scratch.BlockType.REPORTER,
						text: T('count of [ITEM]', '背包中 [ITEM] 的数量'),
						argOrder: ['ITEM'],
						arguments: {
							ITEM: {type: Scratch.ArgumentType.STRING, defaultValue: 'minecraft:stone', menu: 'itemId'}
						}
					},
					{
						opcode: 'respawn',
						blockType: Scratch.BlockType.COMMAND,
						text: T('respawn', '重生')
					},
					{blockType: Scratch.BlockType.LABEL, text: T('World', '世界查询')},
					{
						opcode: 'isDay',
						blockType: Scratch.BlockType.BOOLEAN,
						text: T('is daytime?', '是白天?')
					},
					{
						opcode: 'dimension',
						blockType: Scratch.BlockType.REPORTER,
						text: T('dimension', '当前维度')
					},
					{
						opcode: 'biome',
						blockType: Scratch.BlockType.REPORTER,
						text: T('biome', '当前生物群系')
					},
					{
						opcode: 'inWater',
						blockType: Scratch.BlockType.BOOLEAN,
						text: T('in water?', '在水中?')
					},
					{
						opcode: 'inLava',
						blockType: Scratch.BlockType.BOOLEAN,
						text: T('in lava?', '在岩浆中?')
					},
					{
						opcode: 'blockInFront',
						blockType: Scratch.BlockType.BOOLEAN,
						text: T('block in front?', '前方有方块？')
					},
					{
						opcode: 'blockBelow',
						blockType: Scratch.BlockType.REPORTER,
						text: T('block below', '脚下方块')
					},
					{
						opcode: 'blockAt',
						blockType: Scratch.BlockType.REPORTER,
						text: T('block at x[X] y[Y] z[Z]', '坐标处方块 x[X] y[Y] z[Z]'),
						argOrder: ['X', 'Y', 'Z'],
						arguments: {
							X: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							Y: {type: Scratch.ArgumentType.NUMBER, defaultValue: 64},
							Z: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0}
						}
					},
					{
						opcode: 'blockJson',
						blockType: Scratch.BlockType.REPORTER,
						text: T('snapshot of block x[X] y[Y] z[Z]', '方块 x[X] y[Y] z[Z] 的快照'),
						argOrder: ['X', 'Y', 'Z'],
						arguments: {
							X: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							Y: {type: Scratch.ArgumentType.NUMBER, defaultValue: 64},
							Z: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0}
						}
					},
					{
						opcode: 'playersJson',
						blockType: Scratch.BlockType.REPORTER,
						text: T('online players snapshot', '在线玩家快照')
					},
					{
						opcode: 'scanJson',
						blockType: Scratch.BlockType.REPORTER,
						text: T('scan x[X1] y[Y1] z[Z1] to x[X2] y[Y2] z[Z2] max [LIMIT]', '扫描 x[X1] y[Y1] z[Z1] 到 x[X2] y[Y2] z[Z2] 限 [LIMIT]'),
						argOrder: ['X1', 'Y1', 'Z1', 'X2', 'Y2', 'Z2', 'LIMIT'],
						arguments: {
							X1: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							Y1: {type: Scratch.ArgumentType.NUMBER, defaultValue: 64},
							Z1: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							X2: {type: Scratch.ArgumentType.NUMBER, defaultValue: 15},
							Y2: {type: Scratch.ArgumentType.NUMBER, defaultValue: 79},
							Z2: {type: Scratch.ArgumentType.NUMBER, defaultValue: 15},
							LIMIT: {type: Scratch.ArgumentType.NUMBER, defaultValue: 4096}
						}
					},
					{
						opcode: 'snapshotKeys',
						blockType: Scratch.BlockType.REPORTER,
						text: T('fields of [KIND]', '[KIND] 的字段'),
						argOrder: ['KIND'],
						arguments: {
							KIND: {type: Scratch.ArgumentType.STRING, defaultValue: 'player', menu: 'snapshotKind'}
						}
					},
					{
						opcode: 'worldJson',
						blockType: Scratch.BlockType.REPORTER,
						text: T('world snapshot', '世界快照')
					},
					{
						opcode: 'frontBlock',
						blockType: Scratch.BlockType.REPORTER,
						text: T('block at crosshair', '准星所指方块')
					},
					{
						opcode: 'aimPos',
						blockType: Scratch.BlockType.REPORTER,
						text: T('aimed block [AXIS] position', '准星方块的 [AXIS] 坐标'),
						argOrder: ['AXIS'],
						arguments: {
							AXIS: {type: Scratch.ArgumentType.STRING, defaultValue: 'x', menu: 'axis'}
						}
					},
					{
						opcode: 'ping',
						blockType: Scratch.BlockType.REPORTER,
						text: T('server ping (ms)', '服务端延迟(ms)')
					},
					{blockType: Scratch.BlockType.LABEL, text: T('Entities', '实体查询')},
					{
						opcode: 'nearestEntityField',
						blockType: Scratch.BlockType.REPORTER,
						text: T('nearest [TYPE] [FIELD]', '最近 [TYPE] 的 [FIELD]'),
						argOrder: ['TYPE', 'FIELD'],
						arguments: {
							TYPE: {type: Scratch.ArgumentType.STRING, defaultValue: 'minecraft:zombie', menu: 'entityType'},
							FIELD: {type: Scratch.ArgumentType.STRING, defaultValue: 'distance', menu: 'entityField'}
						}
					},
					{
						opcode: 'entityCount',
						blockType: Scratch.BlockType.REPORTER,
						text: T('count of [TYPE] nearby', '附近 [TYPE] 数量'),
						argOrder: ['TYPE'],
						arguments: {
							TYPE: {type: Scratch.ArgumentType.STRING, defaultValue: 'minecraft:zombie', menu: 'entityType'}
						}
					},
					{
						opcode: 'entitiesJson',
						blockType: Scratch.BlockType.REPORTER,
						text: T('snapshot [TYPE] r[RADIUS] max[LIMIT]', '附近 [TYPE] 快照 半径[RADIUS] 最多[LIMIT]'),
						argOrder: ['TYPE', 'RADIUS', 'LIMIT'],
						arguments: {
							TYPE: {type: Scratch.ArgumentType.STRING, defaultValue: 'minecraft:zombie', menu: 'entityType'},
							RADIUS: {type: Scratch.ArgumentType.NUMBER, defaultValue: 16},
							LIMIT: {type: Scratch.ArgumentType.NUMBER, defaultValue: 32}
						}
					},
					{blockType: Scratch.BlockType.LABEL, text: T('Display', '显示与日志')},
					{
						opcode: 'drawFrame',
						blockType: Scratch.BlockType.COMMAND,
						text: T('frame x[X1] y[Y1] z[Z1] to x[X2] y[Y2] z[Z2] [COLOR]', '画框 x[X1] y[Y1] z[Z1] 到 x[X2] y[Y2] z[Z2] [COLOR]'),
						argOrder: ['X1', 'Y1', 'Z1', 'X2', 'Y2', 'Z2', 'COLOR'],
						arguments: {
							X1: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							Y1: {type: Scratch.ArgumentType.NUMBER, defaultValue: 64},
							Z1: {type: Scratch.ArgumentType.NUMBER, defaultValue: 0},
							X2: {type: Scratch.ArgumentType.NUMBER, defaultValue: 10},
							Y2: {type: Scratch.ArgumentType.NUMBER, defaultValue: 70},
							Z2: {type: Scratch.ArgumentType.NUMBER, defaultValue: 10},
							COLOR: {type: Scratch.ArgumentType.COLOR, defaultValue: '#33ff66'}
						}
					},
					{
						opcode: 'clearFrames',
						blockType: Scratch.BlockType.COMMAND,
						text: T('clear all frames', '清除所有画框')
					},
					{
						opcode: 'log',
						blockType: Scratch.BlockType.COMMAND,
						text: T('log [TEXT]', '输出日志 [TEXT]'),
						argOrder: ['TEXT'],
						arguments: {
							TEXT: {type: Scratch.ArgumentType.STRING, defaultValue: 'hello'}
						}
					},
					{blockType: Scratch.BlockType.LABEL, text: T('Chat & AI', '聊天与 AI')},
					{
						opcode: 'chat',
						blockType: Scratch.BlockType.COMMAND,
						text: T('say [TEXT] in chat', '在聊天栏说 [TEXT]'),
						argOrder: ['TEXT'],
						arguments: {
							TEXT: {type: Scratch.ArgumentType.STRING, defaultValue: 'hello'}
						}
					},
					{
						opcode: 'command',
						blockType: Scratch.BlockType.COMMAND,
						text: T('run command [COMMAND]', '执行命令 [COMMAND]'),
						argOrder: ['COMMAND'],
						arguments: {
							COMMAND: {type: Scratch.ArgumentType.STRING, defaultValue: 'time set day'}
						}
					},
					{
						opcode: 'lastChat',
						blockType: Scratch.BlockType.REPORTER,
						text: T('last chat message', '最近的聊天')
					},
					{
						opcode: 'chatHistory',
						blockType: Scratch.BlockType.REPORTER,
						text: T('last [COUNT] chat messages', '最近 [COUNT] 条聊天'),
						argOrder: ['COUNT'],
						arguments: {
							COUNT: {type: Scratch.ArgumentType.NUMBER, defaultValue: 10}
						}
					},
					{
						opcode: 'triggerChat',
						blockType: Scratch.BlockType.REPORTER,
						text: T('chat that triggered me', '触发我的聊天')
					},
					{
						opcode: 'aiAsk',
						blockType: Scratch.BlockType.COMMAND,
						text: T('ask session [SESSION] to reply to [TEXT]', '让会话 [SESSION] 回应 [TEXT]'),
						argOrder: ['SESSION', 'TEXT'],
						arguments: {
							SESSION: {type: Scratch.ArgumentType.STRING, defaultValue: 'main'},
							TEXT: {type: Scratch.ArgumentType.STRING, defaultValue: 'hello'}
						}
					},
					{
						opcode: 'aiReply',
						blockType: Scratch.BlockType.REPORTER,
						text: T('last reply of session [SESSION]', '会话 [SESSION] 的最后回复'),
						argOrder: ['SESSION'],
						arguments: {
							SESSION: {type: Scratch.ArgumentType.STRING, defaultValue: 'main'}
						}
					},
					{
						opcode: 'aiReset',
						blockType: Scratch.BlockType.COMMAND,
						text: T('clear conversation of session [SESSION]', '清空会话 [SESSION] 的对话记录'),
						argOrder: ['SESSION'],
						arguments: {
							SESSION: {type: Scratch.ArgumentType.STRING, defaultValue: 'main'}
						}
					},
					{
						opcode: 'aiSystem',
						blockType: Scratch.BlockType.COMMAND,
						text: T('set system prompt of session [SESSION] to [TEXT]', '设置会话 [SESSION] 的系统提示为 [TEXT]'),
						argOrder: ['SESSION', 'TEXT'],
						arguments: {
							SESSION: {type: Scratch.ArgumentType.STRING, defaultValue: 'main'},
							TEXT: {type: Scratch.ArgumentType.STRING, defaultValue: 'you are a Minecraft helper'}
						}
					},
					{blockType: Scratch.BlockType.LABEL, text: T('JSON · Check & create', 'JSON · 校验与新建')},
					{
						opcode: 'jsonIs',
						blockType: Scratch.BlockType.BOOLEAN,
						text: T('is [json] [check]?', '[json] 是 [check] 吗?'),
						argOrder: ['json', 'check'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '{"a":1}'},
							check: {type: Scratch.ArgumentType.STRING, defaultValue: 'valid', menu: 'jsonCheck'}
						}
					},
					{
						opcode: 'jsonNew',
						blockType: Scratch.BlockType.REPORTER,
						text: T('new [json]', '新建 [json]'),
						argOrder: ['json'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: 'Object', menu: 'jsonKind'}
						}
					},
					{
						opcode: 'jsonLength',
						blockType: Scratch.BlockType.REPORTER,
						text: T('length of [json]', '[json] 的长度'),
						argOrder: ['json'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '{"a":1,"b":2}'}
						}
					},
					{
						opcode: 'jsonHasKey',
						blockType: Scratch.BlockType.BOOLEAN,
						text: T('[json] contains key [key]?', '[json] 包含键 [key] 吗?'),
						argOrder: ['json', 'key'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '{"a":1}'},
							key: {type: Scratch.ArgumentType.STRING, defaultValue: 'a'}
						}
					},
					{
						opcode: 'jsonGetAll',
						blockType: Scratch.BlockType.REPORTER,
						text: T('all [Stype] of [json]', '[json] 的全部 [Stype]'),
						argOrder: ['json', 'Stype'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '{"a":1,"b":2}'},
							Stype: {type: Scratch.ArgumentType.STRING, menu: 'jsonAll'}
						}
					},
					{
						opcode: 'jsonErrMsg',
						blockType: Scratch.BlockType.REPORTER,
						text: T('parse error from [json]', '[json] 的解析错误'),
						argOrder: ['json'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '{"a":1,}'}
						}
					},
					{
						opcode: 'jsonMinify',
						blockType: Scratch.BlockType.REPORTER,
						text: T('minify [json]', '压缩 [json]'),
						argOrder: ['json'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: ' { "a" : 1 } '}
						}
					},
					{blockType: Scratch.BlockType.LABEL, text: T('JSON · Objects & paths', 'JSON · 对象与路径')},
					{
						opcode: 'jsonGet',
						blockType: Scratch.BlockType.REPORTER,
						text: T('value of [item] in [json]', '[json] 中 [item] 的值'),
						argOrder: ['json', 'item'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '{"a":1}'},
							item: {type: Scratch.ArgumentType.STRING, defaultValue: 'a'}
						}
					},
					{
						opcode: 'jsonSet',
						blockType: Scratch.BlockType.REPORTER,
						text: T('set [item] in [json] to [value]', '把 [json] 中 [item] 设为 [value]'),
						argOrder: ['json', 'item', 'value'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '{"a":1}'},
							item: {type: Scratch.ArgumentType.STRING, defaultValue: 'a'},
							value: {type: Scratch.ArgumentType.STRING, defaultValue: '2'}
						}
					},
					{
						opcode: 'jsonDelete',
						blockType: Scratch.BlockType.REPORTER,
						text: T('delete [item] in [json]', '删除 [json] 中的 [item]'),
						argOrder: ['json', 'item'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '{"a":1,"b":2}'},
							item: {type: Scratch.ArgumentType.STRING, defaultValue: 'b'}
						}
					},
					{
						opcode: 'jsonGetPath',
						blockType: Scratch.BlockType.REPORTER,
						text: T('value by path [path] in [json]', '[json] 中路径 [path] 的值'),
						argOrder: ['json', 'path'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '{"a":{"b":[1,0]}}'},
							path: {type: Scratch.ArgumentType.STRING, defaultValue: '["a","b",1]'}
						}
					},
					{
						opcode: 'jsonSetPath',
						blockType: Scratch.BlockType.REPORTER,
						text: T('set value by path [path] in [json] to [data]', '把 [json] 中路径 [path] 的值设为 [data]'),
						argOrder: ['json', 'path', 'data'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '{"a":{"b":[1,0]}}'},
							path: {type: Scratch.ArgumentType.STRING, defaultValue: '["a","b",1]'},
							data: {type: Scratch.ArgumentType.STRING, defaultValue: '2'}
						}
					},
					{blockType: Scratch.BlockType.LABEL, text: T('JSON · Array access', 'JSON · 数组读写')},
					{
						opcode: 'jsonArrayPush',
						blockType: Scratch.BlockType.REPORTER,
						text: T('add [item] to array [json]', '向数组 [json] 添加 [item]'),
						argOrder: ['json', 'item'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '["a"]'},
							item: {type: Scratch.ArgumentType.STRING, defaultValue: 'b'}
						}
					},
					{
						opcode: 'jsonArraySet',
						blockType: Scratch.BlockType.REPORTER,
						text: T('replace item [pos] of [json] with [item]', '把数组 [json] 的第 [pos] 项换成 [item]'),
						argOrder: ['json', 'pos', 'item'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '["a","b"]'},
							pos: {type: Scratch.ArgumentType.NUMBER, defaultValue: 2},
							item: {type: Scratch.ArgumentType.STRING, defaultValue: 'c'}
						}
					},
					{
						opcode: 'jsonArrayInsert',
						blockType: Scratch.BlockType.REPORTER,
						text: T('insert [item] at [pos] of array [json]', '在数组 [json] 的第 [pos] 项插入 [item]'),
						argOrder: ['json', 'pos', 'item'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '["a","b"]'},
							pos: {type: Scratch.ArgumentType.NUMBER, defaultValue: 2},
							item: {type: Scratch.ArgumentType.STRING, defaultValue: 'c'}
						}
					},
					{
						opcode: 'jsonArrayDelete',
						blockType: Scratch.BlockType.REPORTER,
						text: T('delete item [item] of array [json]', '删除数组 [json] 的第 [item] 项'),
						argOrder: ['json', 'item'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '["a","b"]'},
							item: {type: Scratch.ArgumentType.NUMBER, defaultValue: 2}
						}
					},
					{
						opcode: 'jsonArrayRemoveAll',
						blockType: Scratch.BlockType.REPORTER,
						text: T('delete all [item] in array [json]', '删除数组 [json] 中所有 [item]'),
						argOrder: ['json', 'item'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '["a","b","a"]'},
							item: {type: Scratch.ArgumentType.STRING, defaultValue: 'a'}
						}
					},
					{
						opcode: 'jsonArrayItemNum',
						blockType: Scratch.BlockType.REPORTER,
						text: T('item # of [item] in array [json]', '[item] 在数组 [json] 中的项号'),
						argOrder: ['item', 'json'],
						arguments: {
							item: {type: Scratch.ArgumentType.STRING, defaultValue: 'a'},
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '["a","b"]'}
						}
					},
					{
						opcode: 'jsonArrayFromTo',
						blockType: Scratch.BlockType.REPORTER,
						text: T('items [item] to [item2] of array [json]', '数组 [json] 的第 [item] 到第 [item2] 项'),
						argOrder: ['json', 'item', 'item2'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '[1,2,3]'},
							item: {type: Scratch.ArgumentType.NUMBER, defaultValue: 2},
							item2: {type: Scratch.ArgumentType.NUMBER, defaultValue: 3}
						}
					},
					{
						opcode: 'jsonArraySetLen',
						blockType: Scratch.BlockType.REPORTER,
						text: T('set length of array [json] to [len]', '把数组 [json] 的长度设为 [len]'),
						argOrder: ['json', 'len'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '["a","b"]'},
							len: {type: Scratch.ArgumentType.NUMBER, defaultValue: 1}
						}
					},
					{blockType: Scratch.BlockType.LABEL, text: T('JSON · Array transform', 'JSON · 数组变换')},
					{
						opcode: 'jsonArrayConcat',
						blockType: Scratch.BlockType.REPORTER,
						text: T('concatenate array [json] [json2]', '拼接数组 [json] 和 [json2]'),
						argOrder: ['json', 'json2'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '["a"]'},
							json2: {type: Scratch.ArgumentType.STRING, defaultValue: '["b"]'}
						}
					},
					{
						opcode: 'jsonArrayMapField',
						blockType: Scratch.BlockType.REPORTER,
						text: T('get all values with key [key] in array [json]', '数组 [json] 中所有对象的 [key] 值'),
						argOrder: ['json', 'key'],
						arguments: {
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '[{"a":1},{"a":2}]'},
							key: {type: Scratch.ArgumentType.STRING, defaultValue: 'a'}
						}
					},
					{
						opcode: 'jsonArrayCreate',
						blockType: Scratch.BlockType.REPORTER,
						text: T('create array by [text] with delimiter [d]', '按分隔符 [d] 把文本 [text] 拆成数组'),
						argOrder: ['d', 'text'],
						arguments: {
							d: {type: Scratch.ArgumentType.STRING, defaultValue: ','},
							text: {type: Scratch.ArgumentType.STRING, defaultValue: 'a,b'}
						}
					},
					{
						opcode: 'jsonArrayJoin',
						blockType: Scratch.BlockType.REPORTER,
						text: T('join string by array [json] with delimiter [d]', '用分隔符 [d] 把数组 [json] 拼成文本'),
						argOrder: ['d', 'json'],
						arguments: {
							d: {type: Scratch.ArgumentType.STRING, defaultValue: ','},
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '["a","b"]'}
						}
					},
					{
						opcode: 'jsonArraySort',
						blockType: Scratch.BlockType.REPORTER,
						text: T('sort array [list] in [order] order', '数组 [list] 按 [order] 排序'),
						argOrder: ['list', 'order'],
						arguments: {
							list: {type: Scratch.ArgumentType.STRING, defaultValue: '[3,1,2]'},
							order: {type: Scratch.ArgumentType.STRING, menu: 'jsonSort'}
						}
					},
					{
						opcode: 'jsonArrayTextFilter',
						blockType: Scratch.BlockType.REPORTER,
						text: T('filter [list] text [options] [text] return [type]', '筛选 [list] 文本 [options] [text] 返回 [type]'),
						argOrder: ['list', 'options', 'text', 'type'],
						arguments: {
							list: {type: Scratch.ArgumentType.STRING, defaultValue: '["a","b"]'},
							options: {type: Scratch.ArgumentType.STRING, menu: 'jsonFilter'},
							text: {type: Scratch.ArgumentType.STRING, defaultValue: 'a'},
							type: {type: Scratch.ArgumentType.STRING, menu: 'jsonKind'}
						}
					},
					{
						opcode: 'jsonArrayAnalysis',
						blockType: Scratch.BlockType.REPORTER,
						text: T('[analysis] of array [list]', '数组 [list] 的 [analysis]'),
						argOrder: ['list', 'analysis'],
						arguments: {
							list: {type: Scratch.ArgumentType.STRING, defaultValue: '[1,2,3]'},
							analysis: {type: Scratch.ArgumentType.STRING, menu: 'jsonAnalysis'}
						}
					},
					{blockType: Scratch.BlockType.LABEL, text: T('JSON · List bridge', 'JSON · 与列表互通')},
					{
						opcode: 'jsonVmGetList',
						blockType: Scratch.BlockType.REPORTER,
						text: T('get list [list] as array', '把列表 [list] 读成数组'),
						argOrder: ['list'],
						arguments: {
							list: {type: Scratch.ArgumentType.STRING, defaultValue: 'mylist'}
						}
					},
					{
						opcode: 'jsonVmSetList',
						blockType: Scratch.BlockType.COMMAND,
						text: T('set list [list] to [json]', '把列表 [list] 设为 [json]'),
						argOrder: ['list', 'json'],
						arguments: {
							list: {type: Scratch.ArgumentType.STRING, defaultValue: 'mylist'},
							json: {type: Scratch.ArgumentType.STRING, defaultValue: '["a","b"]'}
						}
					}
				],
				menus: {
					jsonKind: {acceptReporters: true, items: [
							{text: T('object', '对象'), value: 'Object'},
							{text: T('array', '数组'), value: 'Array'}
					]},
					jsonCheck: {acceptReporters: true, items: [
							{text: T('valid JSON', '合法 JSON'), value: 'valid'},
							{text: T('object', '对象'), value: 'Object'},
							{text: T('array', '数组'), value: 'Array'}
					]},
					jsonAll: {acceptReporters: false, items: [
							{text: T('keys', '键'), value: 'keys'},
							{text: T('values', '值'), value: 'values'},
							{text: T('key-value pairs', '键值对'), value: 'datas'}
					]},
					jsonSort: {acceptReporters: true, items: [
							{text: T('ascending', '升序'), value: 'ascending'},
							{text: T('descending', '降序'), value: 'descending'}
					]},
					jsonFilter: {acceptReporters: false, items: [
							{text: T('includes', '包含'), value: 'includes'},
							{text: T('starts with', '开头是'), value: 'starts with'},
							{text: T('ends with', '结尾是'), value: 'ends with'}
					]},
					jsonAnalysis: {acceptReporters: true, items: [
							{text: T('minimum', '最小值'), value: 'minimum'},
							{text: T('maximum', '最大值'), value: 'maximum'},
							{text: T('sum', '总和'), value: 'sum'},
							{text: T('average', '平均值'), value: 'average'},
							{text: T('median', '中位数'), value: 'median'},
							{text: T('mode', '众数'), value: 'mode'},
							{text: T('variance', '方差'), value: 'variance'}
					]},
					axis: {acceptReporters: false, items: [
							{text: T('X', 'X'), value: 'x'},
							{text: T('Y', 'Y'), value: 'y'},
							{text: T('Z', 'Z'), value: 'z'}
					]},
					entityField: {acceptReporters: false, items: [
							{text: T('distance', '距离'), value: 'distance'},
							{text: T('X', 'X'), value: 'x'},
							{text: T('Y', 'Y'), value: 'y'},
							{text: T('Z', 'Z'), value: 'z'},
							{text: T('name', '名称'), value: 'name'},
							{text: T('health', '血量'), value: 'health'},
							{text: T('max health', '最大血量'), value: 'max_health'},
							{text: T('registry id', '注册名'), value: 'type_id'}
					]},
					heldField: {acceptReporters: false, items: [
							{text: T('item', '物品'), value: 'id'},
							{text: T('durability %', '耐久百分比'), value: 'durability'},
							{text: T('count', '数量'), value: 'count'},
							{text: T('max durability', '最大耐久'), value: 'max_durability'}
					]},
					slotField: {acceptReporters: false, items: [
							{text: T('item', '物品'), value: 'id'},
							{text: T('amount', '数量'), value: 'amount'},
							{text: T('max durability', '最大耐久'), value: 'max_durability'},
							{text: T('damage', '已损耗'), value: 'damage'},
							{text: T('custom name', '自定义名称'), value: 'custom_name'}
					]},
					itemId: {acceptReporters: true, items: itemMenuItems()},
					entityType: {acceptReporters: true, items: [
							{text: T('any entity', '任意实体'), value: 'any'},
							{text: T('hostile mob', '敌对生物'), value: 'hostile'},
							{text: T('player', '玩家'), value: 'player'},
							{text: T('Zombie', '僵尸'), value: 'minecraft:zombie'},
							{text: T('Skeleton', '骷髅'), value: 'minecraft:skeleton'},
							{text: T('Creeper', '苦力怕'), value: 'minecraft:creeper'},
							{text: T('Spider', '蜘蛛'), value: 'minecraft:spider'},
							{text: T('Enderman', '末影人'), value: 'minecraft:enderman'},
							{text: T('Witch', '女巫'), value: 'minecraft:witch'},
							{text: T('Slime', '史莱姆'), value: 'minecraft:slime'},
							{text: T('Drowned', '溺尸'), value: 'minecraft:drowned'},
							{text: T('Husk', '尸壳'), value: 'minecraft:husk'},
							{text: T('Zombie Villager', '僵尸村民'), value: 'minecraft:zombie_villager'},
							{text: T('Stray', '流浪者'), value: 'minecraft:stray'},
							{text: T('Bogged', '沼骸'), value: 'minecraft:bogged'},
							{text: T('Parched', '焦骸'), value: 'minecraft:parched'},
							{text: T('Wither Skeleton', '凋灵骷髅'), value: 'minecraft:wither_skeleton'},
							{text: T('Cave Spider', '洞穴蜘蛛'), value: 'minecraft:cave_spider'},
							{text: T('Silverfish', '蠹虫'), value: 'minecraft:silverfish'},
							{text: T('Endermite', '末影螨'), value: 'minecraft:endermite'},
							{text: T('Magma Cube', '岩浆怪'), value: 'minecraft:magma_cube'},
							{text: T('Blaze', '烈焰人'), value: 'minecraft:blaze'},
							{text: T('Ghast', '恶魂'), value: 'minecraft:ghast'},
							{text: T('Happy Ghast', '快乐恶魂'), value: 'minecraft:happy_ghast'},
							{text: T('Phantom', '幻翼'), value: 'minecraft:phantom'},
							{text: T('Pillager', '掠夺者'), value: 'minecraft:pillager'},
							{text: T('Vindicator', '卫道士'), value: 'minecraft:vindicator'},
							{text: T('Evoker', '唤魔者'), value: 'minecraft:evoker'},
							{text: T('Illusioner', '幻术师'), value: 'minecraft:illusioner'},
							{text: T('Ravager', '劫掠兽'), value: 'minecraft:ravager'},
							{text: T('Vex', '恼鬼'), value: 'minecraft:vex'},
							{text: T('Guardian', '守卫者'), value: 'minecraft:guardian'},
							{text: T('Elder Guardian', '远古守卫者'), value: 'minecraft:elder_guardian'},
							{text: T('Shulker', '潜影贝'), value: 'minecraft:shulker'},
							{text: T('Warden', '监守者'), value: 'minecraft:warden'},
							{text: T('Breeze', '旋风人'), value: 'minecraft:breeze'},
							{text: T('Creaking', '嘎枝'), value: 'minecraft:creaking'},
							{text: T('Giant', '巨人'), value: 'minecraft:giant'},
							{text: T('Wither', '凋灵'), value: 'minecraft:wither'},
							{text: T('Ender Dragon', '末影龙'), value: 'minecraft:ender_dragon'},
							{text: T('Piglin', '猪灵'), value: 'minecraft:piglin'},
							{text: T('Piglin Brute', '猪灵蛮兵'), value: 'minecraft:piglin_brute'},
							{text: T('Zombified Piglin', '僵尸猪灵'), value: 'minecraft:zombified_piglin'},
							{text: T('Hoglin', '疣猪兽'), value: 'minecraft:hoglin'},
							{text: T('Zoglin', '僵尸疣猪兽'), value: 'minecraft:zoglin'},
							{text: T('Villager', '村民'), value: 'minecraft:villager'},
							{text: T('Wandering Trader', '流浪商人'), value: 'minecraft:wandering_trader'},
							{text: T('Iron Golem', '铁傀儡'), value: 'minecraft:iron_golem'},
							{text: T('Snow Golem', '雪傀儡'), value: 'minecraft:snow_golem'},
							{text: T('Cow', '牛'), value: 'minecraft:cow'},
							{text: T('Pig', '猪'), value: 'minecraft:pig'},
							{text: T('Sheep', '绵羊'), value: 'minecraft:sheep'},
							{text: T('Chicken', '鸡'), value: 'minecraft:chicken'},
							{text: T('Rabbit', '兔子'), value: 'minecraft:rabbit'},
							{text: T('Horse', '马'), value: 'minecraft:horse'},
							{text: T('Donkey', '驴'), value: 'minecraft:donkey'},
							{text: T('Mule', '骡'), value: 'minecraft:mule'},
							{text: T('Camel', '骆驼'), value: 'minecraft:camel'},
							{text: T('Camel Husk', '骆驼尸壳'), value: 'minecraft:camel_husk'},
							{text: T('Wolf', '狼'), value: 'minecraft:wolf'},
							{text: T('Cat', '猫'), value: 'minecraft:cat'},
							{text: T('Ocelot', '豹猫'), value: 'minecraft:ocelot'},
							{text: T('Fox', '狐狸'), value: 'minecraft:fox'},
							{text: T('Panda', '熊猫'), value: 'minecraft:panda'},
							{text: T('Polar Bear', '北极熊'), value: 'minecraft:polar_bear'},
							{text: T('Bee', '蜜蜂'), value: 'minecraft:bee'},
							{text: T('Parrot', '鹦鹉'), value: 'minecraft:parrot'},
							{text: T('Bat', '蝙蝠'), value: 'minecraft:bat'},
							{text: T('Squid', '鱿鱼'), value: 'minecraft:squid'},
							{text: T('Glow Squid', '发光鱿鱼'), value: 'minecraft:glow_squid'},
							{text: T('Dolphin', '海豚'), value: 'minecraft:dolphin'},
							{text: T('Cod', '鳕鱼'), value: 'minecraft:cod'},
							{text: T('Salmon', '鲑鱼'), value: 'minecraft:salmon'},
							{text: T('Tropical Fish', '热带鱼'), value: 'minecraft:tropical_fish'},
							{text: T('Pufferfish', '河豚'), value: 'minecraft:pufferfish'},
							{text: T('Tadpole', '蝌蚪'), value: 'minecraft:tadpole'},
							{text: T('Frog', '青蛙'), value: 'minecraft:frog'},
							{text: T('Turtle', '海龟'), value: 'minecraft:turtle'},
							{text: T('Axolotl', '美西螈'), value: 'minecraft:axolotl'},
							{text: T('Mooshroom', '哞菇'), value: 'minecraft:mooshroom'},
							{text: T('Sniffer', '嗅探兽'), value: 'minecraft:sniffer'},
							{text: T('Armadillo', '犰狳'), value: 'minecraft:armadillo'},
							{text: T('Allay', '悦灵'), value: 'minecraft:allay'},
							{text: T('Goat', '山羊'), value: 'minecraft:goat'},
							{text: T('Llama', '羊驼'), value: 'minecraft:llama'},
							{text: T('Trader Llama', '行商羊驼'), value: 'minecraft:trader_llama'},
							{text: T('Strider', '炽足兽'), value: 'minecraft:strider'},
							{text: T('Copper Golem', '铜傀儡'), value: 'minecraft:copper_golem'},
							{text: T('Nautilus', '鹦鹉螺'), value: 'minecraft:nautilus'},
							{text: T('Zombie Nautilus', '僵尸鹦鹉螺'), value: 'minecraft:zombie_nautilus'},
							{text: T('Mannequin', '玩家模型'), value: 'minecraft:mannequin'},
							{text: T('Skeleton Horse', '骷髅马'), value: 'minecraft:skeleton_horse'},
							{text: T('Zombie Horse', '僵尸马'), value: 'minecraft:zombie_horse'},
							{text: T('Acacia Boat', '金合欢木船'), value: 'minecraft:acacia_boat'},
							{text: T('Acacia Chest Boat', '金合欢木运输船'), value: 'minecraft:acacia_chest_boat'},
							{text: T('Area Effect Cloud', '区域效果云'), value: 'minecraft:area_effect_cloud'},
							{text: T('Armor Stand', '盔甲架'), value: 'minecraft:armor_stand'},
							{text: T('Arrow', '箭'), value: 'minecraft:arrow'},
							{text: T('Bamboo Chest Raft', '运输竹筏'), value: 'minecraft:bamboo_chest_raft'},
							{text: T('Bamboo Raft', '竹筏'), value: 'minecraft:bamboo_raft'},
							{text: T('Birch Boat', '白桦木船'), value: 'minecraft:birch_boat'},
							{text: T('Birch Chest Boat', '白桦木运输船'), value: 'minecraft:birch_chest_boat'},
							{text: T('Block Display', '方块展示实体'), value: 'minecraft:block_display'},
							{text: T('Wind Charge', '风弹'), value: 'minecraft:breeze_wind_charge'},
							{text: T('Cherry Boat', '樱花木船'), value: 'minecraft:cherry_boat'},
							{text: T('Cherry Chest Boat', '樱花木运输船'), value: 'minecraft:cherry_chest_boat'},
							{text: T('Chest Minecart', '运输矿车'), value: 'minecraft:chest_minecart'},
							{text: T('Command Cart', '命令方块矿车'), value: 'minecraft:command_block_minecart'},
							{text: T('Dark Oak Boat', '深色橡木船'), value: 'minecraft:dark_oak_boat'},
							{text: T('Dark Oak Chest Boat', '深色橡木运输船'), value: 'minecraft:dark_oak_chest_boat'},
							{text: T('Dragon Fireball', '末影龙火球'), value: 'minecraft:dragon_fireball'},
							{text: T('Thrown Egg', '掷出的鸡蛋'), value: 'minecraft:egg'},
							{text: T('End Crystal', '末地水晶'), value: 'minecraft:end_crystal'},
							{text: T('Ender Pearl', '掷出的末影珍珠'), value: 'minecraft:ender_pearl'},
							{text: T('Evoker Fangs', '唤魔者尖牙'), value: 'minecraft:evoker_fangs'},
							{text: T('XP Bottle', '掷出的附魔之瓶'), value: 'minecraft:experience_bottle'},
							{text: T('Experience Orb', '经验球'), value: 'minecraft:experience_orb'},
							{text: T('Eye of Ender', '末影之眼'), value: 'minecraft:eye_of_ender'},
							{text: T('Falling Block', '下落的方块'), value: 'minecraft:falling_block'},
							{text: T('Fireball', '火球'), value: 'minecraft:fireball'},
							{text: T('Firework Rocket', '烟花火箭'), value: 'minecraft:firework_rocket'},
							{text: T('Fishing Bobber', '浮漂'), value: 'minecraft:fishing_bobber'},
							{text: T('Furnace Minecart', '动力矿车'), value: 'minecraft:furnace_minecart'},
							{text: T('Glow Item Frame', '荧光物品展示框'), value: 'minecraft:glow_item_frame'},
							{text: T('Hopper Minecart', '漏斗矿车'), value: 'minecraft:hopper_minecart'},
							{text: T('Interaction', '交互实体'), value: 'minecraft:interaction'},
							{text: T('Item', '物品'), value: 'minecraft:item'},
							{text: T('Item Display', '物品展示实体'), value: 'minecraft:item_display'},
							{text: T('Item Frame', '物品展示框'), value: 'minecraft:item_frame'},
							{text: T('Jungle Boat', '丛林木船'), value: 'minecraft:jungle_boat'},
							{text: T('Jungle Chest Boat', '丛林木运输船'), value: 'minecraft:jungle_chest_boat'},
							{text: T('Leash Knot', '拴绳结'), value: 'minecraft:leash_knot'},
							{text: T('Lightning Bolt', '闪电束'), value: 'minecraft:lightning_bolt'},
							{text: T('Lingering Potion', '滞留药水'), value: 'minecraft:lingering_potion'},
							{text: T('Llama Spit', '羊驼唾沫'), value: 'minecraft:llama_spit'},
							{text: T('Mangrove Boat', '红树木船'), value: 'minecraft:mangrove_boat'},
							{text: T('Mangrove Chest Boat', '红树木运输船'), value: 'minecraft:mangrove_chest_boat'},
							{text: T('Marker', '标记'), value: 'minecraft:marker'},
							{text: T('Minecart', '矿车'), value: 'minecraft:minecart'},
							{text: T('Oak Boat', '橡木船'), value: 'minecraft:oak_boat'},
							{text: T('Oak Chest Boat', '橡木运输船'), value: 'minecraft:oak_chest_boat'},
							{text: T('Ominous Spawner', '不祥之物生成器'), value: 'minecraft:ominous_item_spawner'},
							{text: T('Painting', '画'), value: 'minecraft:painting'},
							{text: T('Pale Oak Boat', '苍白橡木船'), value: 'minecraft:pale_oak_boat'},
							{text: T('Pale Oak Chest Boat', '苍白橡木运输船'), value: 'minecraft:pale_oak_chest_boat'},
							{text: T('Shulker Bullet', '潜影弹'), value: 'minecraft:shulker_bullet'},
							{text: T('Small Fireball', '小火球'), value: 'minecraft:small_fireball'},
							{text: T('Snowball', '雪球'), value: 'minecraft:snowball'},
							{text: T('Spawner Minecart', '刷怪笼矿车'), value: 'minecraft:spawner_minecart'},
							{text: T('Spectral Arrow', '光灵箭'), value: 'minecraft:spectral_arrow'},
							{text: T('Splash Potion', '喷溅药水'), value: 'minecraft:splash_potion'},
							{text: T('Spruce Boat', '云杉木船'), value: 'minecraft:spruce_boat'},
							{text: T('Spruce Chest Boat', '云杉木运输船'), value: 'minecraft:spruce_chest_boat'},
							{text: T('Text Display', '文本展示实体'), value: 'minecraft:text_display'},
							{text: T('Primed TNT', '被激活的TNT'), value: 'minecraft:tnt'},
							{text: T('TNT Minecart', 'TNT矿车'), value: 'minecraft:tnt_minecart'},
							{text: T('Trident', '三叉戟'), value: 'minecraft:trident'},
							{text: T('Wind Charge', '风弹'), value: 'minecraft:wind_charge'},
							{text: T('Wither Skull', '凋灵之首'), value: 'minecraft:wither_skull'}
					]},
					snapshotKind: {acceptReporters: false, items: [
							{text: T('player snapshot', '玩家快照'), value: 'player'},
							{text: T('entity (list item)', '实体（列表项）'), value: 'entity'},
							{text: T('item entry', '物品条目'), value: 'item'},
							{text: T('screen', '界面'), value: 'screen'},
							{text: T('block', '方块'), value: 'block'},
							{text: T('block entity', '方块实体'), value: 'block_entity'},
							{text: T('world', '世界'), value: 'world'},
							{text: T('online players (list item)', '在线玩家（列表项）'), value: 'players'},
							{text: T('area scan', '区域扫描'), value: 'scan'},
							{text: T('chat history', '聊天历史'), value: 'chat'}
					]},
					screenKind: {acceptReporters: false, items: [
							{text: T('container', '容器'), value: 'container'},
							{text: T('player inventory', '玩家背包'), value: 'inventory'},
							{text: T('any screen', '任意界面'), value: 'any'}
					]},
					keyName: {acceptReporters: true, items: [
							{text: T('a', 'a'), value: 'a'},
							{text: T('b', 'b'), value: 'b'},
							{text: T('c', 'c'), value: 'c'},
							{text: T('d', 'd'), value: 'd'},
							{text: T('e', 'e'), value: 'e'},
							{text: T('f', 'f'), value: 'f'},
							{text: T('g', 'g'), value: 'g'},
							{text: T('h', 'h'), value: 'h'},
							{text: T('i', 'i'), value: 'i'},
							{text: T('j', 'j'), value: 'j'},
							{text: T('k', 'k'), value: 'k'},
							{text: T('l', 'l'), value: 'l'},
							{text: T('m', 'm'), value: 'm'},
							{text: T('n', 'n'), value: 'n'},
							{text: T('o', 'o'), value: 'o'},
							{text: T('p', 'p'), value: 'p'},
							{text: T('q', 'q'), value: 'q'},
							{text: T('r', 'r'), value: 'r'},
							{text: T('s', 's'), value: 's'},
							{text: T('t', 't'), value: 't'},
							{text: T('u', 'u'), value: 'u'},
							{text: T('v', 'v'), value: 'v'},
							{text: T('w', 'w'), value: 'w'},
							{text: T('x', 'x'), value: 'x'},
							{text: T('y', 'y'), value: 'y'},
							{text: T('z', 'z'), value: 'z'},
							{text: T('0', '0'), value: '0'},
							{text: T('1', '1'), value: '1'},
							{text: T('2', '2'), value: '2'},
							{text: T('3', '3'), value: '3'},
							{text: T('4', '4'), value: '4'},
							{text: T('5', '5'), value: '5'},
							{text: T('6', '6'), value: '6'},
							{text: T('7', '7'), value: '7'},
							{text: T('8', '8'), value: '8'},
							{text: T('9', '9'), value: '9'},
							{text: T('F1', 'F1'), value: 'f1'},
							{text: T('F2', 'F2'), value: 'f2'},
							{text: T('F3', 'F3'), value: 'f3'},
							{text: T('F4', 'F4'), value: 'f4'},
							{text: T('F5', 'F5'), value: 'f5'},
							{text: T('F6', 'F6'), value: 'f6'},
							{text: T('F7', 'F7'), value: 'f7'},
							{text: T('F8', 'F8'), value: 'f8'},
							{text: T('F9', 'F9'), value: 'f9'},
							{text: T('F10', 'F10'), value: 'f10'},
							{text: T('F11', 'F11'), value: 'f11'},
							{text: T('F12', 'F12'), value: 'f12'},
							{text: T('Up Arrow', '上'), value: 'up'},
							{text: T('Down Arrow', '下'), value: 'down'},
							{text: T('Left Arrow', '左'), value: 'left'},
							{text: T('Right Arrow', '右'), value: 'right'},
							{text: T('Left Shift', '左 Shift'), value: 'left.shift'},
							{text: T('Right Shift', '右 Shift'), value: 'right.shift'},
							{text: T('Left Control', '左 Ctrl'), value: 'left.control'},
							{text: T('Right Control', '右 Ctrl'), value: 'right.control'},
							{text: T('Left Alt', '左 Alt'), value: 'left.alt'},
							{text: T('Right Alt', '右 Alt'), value: 'right.alt'},
							{text: T('Left Win', '左 Win'), value: 'left.win'},
							{text: T('Right Win', '右 Win'), value: 'right.win'},
							{text: T('Space', '空格'), value: 'space'},
							{text: T('Enter', '回车'), value: 'enter'},
							{text: T('Tab', 'Tab'), value: 'tab'},
							{text: T('Backspace', '退格'), value: 'backspace'},
							{text: T('Escape', 'Esc'), value: 'escape'},
							{text: T('\'', '\''), value: 'apostrophe'},
							{text: T('\\', '\\'), value: 'backslash'},
							{text: T(',', '逗号'), value: 'comma'},
							{text: T('=', '等号'), value: 'equal'},
							{text: T('`', '`'), value: 'grave.accent'},
							{text: T('[', '['), value: 'left.bracket'},
							{text: T(']', ']'), value: 'right.bracket'},
							{text: T('-', '减号'), value: 'minus'},
							{text: T('.', '句点'), value: 'period'},
							{text: T(';', '分号'), value: 'semicolon'},
							{text: T('/', '斜杠'), value: 'slash'},
							{text: T('Left Button', '鼠标左键'), value: 'mouse.left'},
							{text: T('Right Button', '鼠标右键'), value: 'mouse.right'},
							{text: T('Middle Button', '鼠标中键'), value: 'mouse.middle'},
							{text: T('Keypad 0', '小键盘 0'), value: 'keypad.0'},
							{text: T('Keypad 1', '小键盘 1'), value: 'keypad.1'},
							{text: T('Keypad 2', '小键盘 2'), value: 'keypad.2'},
							{text: T('Keypad 3', '小键盘 3'), value: 'keypad.3'},
							{text: T('Keypad 4', '小键盘 4'), value: 'keypad.4'},
							{text: T('Keypad 5', '小键盘 5'), value: 'keypad.5'},
							{text: T('Keypad 6', '小键盘 6'), value: 'keypad.6'},
							{text: T('Keypad 7', '小键盘 7'), value: 'keypad.7'},
							{text: T('Keypad 8', '小键盘 8'), value: 'keypad.8'},
							{text: T('Keypad 9', '小键盘 9'), value: 'keypad.9'},
							{text: T('Keypad +', '小键盘 +'), value: 'keypad.add'},
							{text: T('Keypad Decimal', '小键盘 .'), value: 'keypad.decimal'},
							{text: T('Keypad /', '小键盘 /'), value: 'keypad.divide'},
							{text: T('Keypad Enter', '小键盘回车'), value: 'keypad.enter'},
							{text: T('Keypad =', '小键盘 ='), value: 'keypad.equal'},
							{text: T('Keypad *', '小键盘 *'), value: 'keypad.multiply'},
							{text: T('Keypad -', '小键盘 -'), value: 'keypad.subtract'},
							{text: T('Caps Lock', 'CapsLock'), value: 'caps.lock'},
							{text: T('Delete', 'Delete'), value: 'delete'},
							{text: T('End', 'End'), value: 'end'},
							{text: T('Home', 'Home'), value: 'home'},
							{text: T('Insert', 'Insert'), value: 'insert'},
							{text: T('Menu', '菜单键'), value: 'menu'},
							{text: T('Num Lock', 'NumLock'), value: 'num.lock'},
							{text: T('Page Down', 'PageDown'), value: 'page.down'},
							{text: T('Page Up', 'PageUp'), value: 'page.up'},
							{text: T('Pause', 'Pause'), value: 'pause'},
							{text: T('Print Screen', 'PrintScreen'), value: 'print.screen'},
							{text: T('Scroll Lock', 'ScrollLock'), value: 'scroll.lock'},
							{text: T('World 1', 'World 1'), value: 'world.1'},
							{text: T('World 2', 'World 2'), value: 'world.2'},
							{text: T('F13', 'F13'), value: 'f13'},
							{text: T('F14', 'F14'), value: 'f14'},
							{text: T('F15', 'F15'), value: 'f15'},
							{text: T('F16', 'F16'), value: 'f16'},
							{text: T('F17', 'F17'), value: 'f17'},
							{text: T('F18', 'F18'), value: 'f18'},
							{text: T('F19', 'F19'), value: 'f19'},
							{text: T('F20', 'F20'), value: 'f20'},
							{text: T('F21', 'F21'), value: 'f21'},
							{text: T('F22', 'F22'), value: 'f22'},
							{text: T('F23', 'F23'), value: 'f23'},
							{text: T('F24', 'F24'), value: 'f24'},
							{text: T('F25', 'F25'), value: 'f25'}
					]},
					mouseButton: {acceptReporters: true, items: [
							{text: T('left', '左键'), value: 'left'},
							{text: T('right', '右键'), value: 'right'},
							{text: T('middle', '中键'), value: 'middle'}
					]},
					slotButton: {acceptReporters: true, items: [
							{text: T('left', '左键'), value: 'left'},
							{text: T('right', '右键'), value: 'right'}
					]},
					keyState: {acceptReporters: true, items: [
							{text: T('down', '按下'), value: 'true'},
							{text: T('up', '松开'), value: 'false'}
					]}
				}
			};
		}

		// ---- 浏览器占位实现：只打印，不做任何事；游戏内会被整体替换 ----
		whenDamaged() { return true; }
		whenDeath() { return true; }
		whenScreenOpen() { return true; }
		whenChat() { return true; }
		moveToward(args, util) { return this.__tmc('turbomc_moveToward', [args.X, args.Y, args.Z], 'command', function () { this.preview('moveToward ' + args.X + ',' + args.Y + ',' + args.Z); }, util); }
		look(args, util) { return this.__tmc('turbomc_look', [args.YAW, args.PITCH], 'command', function () { this.preview('look ' + args.YAW + ',' + args.PITCH); }, util); }
		lookAt(args, util) { return this.__tmc('turbomc_lookAt', [args.X, args.Y, args.Z], 'command', function () { this.preview('lookAt ' + args.X + ',' + args.Y + ',' + args.Z); }, util); }
		lookSmooth(args, util) { return this.__tmc('turbomc_lookSmooth', [args.YAW, args.PITCH], 'command', function () { this.preview('lookSmooth ' + args.YAW + ',' + args.PITCH); }, util); }
		lookAtSmooth(args, util) { return this.__tmc('turbomc_lookAtSmooth', [args.X, args.Y, args.Z], 'command', function () { this.preview('lookAtSmooth ' + args.X + ',' + args.Y + ',' + args.Z); }, util); }
		stop(args, util) { return this.__tmc('turbomc_stop', [], 'command', function () { this.preview('stop'); }, util); }
		key(args, util) { return this.__tmc('turbomc_key', [args.KEY, args.STATE], 'command', function () { this.preview('key ' + args.KEY + ' ' + args.STATE); }, util); }
		click(args, util) { return this.__tmc('turbomc_click', [args.BUTTON], 'command', function () { this.preview('click ' + args.BUTTON); }, util); }
		tap(args, util) { return this.__tmc('turbomc_tap', [args.KEY, args.HOLD], 'command', function () { this.preview('tap ' + args.KEY + ' x' + args.HOLD); }, util); }
		mouseDown(args, util) { return this.__tmc('turbomc_mouseDown', [args.BUTTON], 'boolean', function () { return false; }, util); }
		breakWait(args, util) { return this.__tmc('turbomc_breakWait', [], 'command', function () { this.preview('breakWait'); }, util); }
		isMining(args, util) { return this.__tmc('turbomc_isMining', [], 'boolean', function () { return false; }, util); }
		miningProgress(args, util) { return this.__tmc('turbomc_miningProgress', [], 'reporter', function () { return 0; }, util); }
		openContainerAt(args, util) { return this.__tmc('turbomc_openContainerAt', [args.X, args.Y, args.Z], 'command', function () { this.preview('openContainerAt'); }, util); }
		containerSlots(args, util) { return this.__tmc('turbomc_containerSlots', [], 'reporter', function () { return 0; }, util); }
		slotField(args, util) { return this.__tmc('turbomc_slotField', [args.INDEX, args.FIELD], 'reporter', function () { return ''; }, util); }
		screenJson(args, util) { return this.__tmc('turbomc_screenJson', [], 'reporter', function () { return '{}'; }, util); }
		firstSlotOf(args, util) { return this.__tmc('turbomc_firstSlotOf', [args.ITEM], 'reporter', function () { return -1; }, util); }
		clickSlot(args, util) { return this.__tmc('turbomc_clickSlot', [args.INDEX, args.BUTTON], 'command', function () { this.preview('clickSlot'); }, util); }
		quickMoveSlot(args, util) { return this.__tmc('turbomc_quickMoveSlot', [args.INDEX], 'command', function () { this.preview('quickMoveSlot'); }, util); }
		closeContainer(args, util) { return this.__tmc('turbomc_closeContainer', [], 'command', function () { this.preview('closeContainer'); }, util); }
		health(args, util) { return this.__tmc('turbomc_health', [], 'reporter', function () { return 20; }, util); }
		food(args, util) { return this.__tmc('turbomc_food', [], 'reporter', function () { return 20; }, util); }
		xpLevel(args, util) { return this.__tmc('turbomc_xpLevel', [], 'reporter', function () { return 0; }, util); }
		onGround(args, util) { return this.__tmc('turbomc_onGround', [], 'boolean', function () { this.preview('onGround'); return true; }, util); }
		pos(args, util) { return this.__tmc('turbomc_pos', [args.AXIS], 'reporter', function () { return 0; }, util); }
		selectedSlot(args, util) { return this.__tmc('turbomc_selectedSlot', [], 'reporter', function () { return 1; }, util); }
		heldItemField(args, util) { return this.__tmc('turbomc_heldItemField', [args.FIELD], 'reporter', function () { return ''; }, util); }
		inventoryJson(args, util) { return this.__tmc('turbomc_inventoryJson', [], 'reporter', function () { return '[]'; }, util); }
		inventoryFull(args, util) { return this.__tmc('turbomc_inventoryFull', [], 'boolean', function () { return false; }, util); }
		playerJson(args, util) { return this.__tmc('turbomc_playerJson', [], 'reporter', function () { return '{"name":"","uuid":"","health":0,"max_health":20,"absorption":0,"food":20,"saturation":5,"xp_level":0,"xp_progress":0,"armor":0,"air":300,"gamemode":"","dimension":"","x":0,"y":0,"z":0,"motion_x":0,"motion_y":0,"motion_z":0,"yaw":0,"pitch":0,"on_ground":false,"in_water":false,"in_lava":false,"selected_slot":1,"effects":[]}'; }, util); }
		itemCount(args, util) { return this.__tmc('turbomc_itemCount', [args.ITEM], 'reporter', function () { return 0; }, util); }
		respawn(args, util) { return this.__tmc('turbomc_respawn', [], 'command', function () { this.preview('respawn'); }, util); }
		isDay(args, util) { return this.__tmc('turbomc_isDay', [], 'boolean', function () { return true; }, util); }
		dimension(args, util) { return this.__tmc('turbomc_dimension', [], 'reporter', function () { return 'minecraft:overworld'; }, util); }
		biome(args, util) { return this.__tmc('turbomc_biome', [], 'reporter', function () { return 'minecraft:plains'; }, util); }
		inWater(args, util) { return this.__tmc('turbomc_inWater', [], 'boolean', function () { return false; }, util); }
		inLava(args, util) { return this.__tmc('turbomc_inLava', [], 'boolean', function () { return false; }, util); }
		blockInFront(args, util) { return this.__tmc('turbomc_blockInFront', [], 'boolean', function () { this.preview('blockInFront'); return false; }, util); }
		blockBelow(args, util) { return this.__tmc('turbomc_blockBelow', [], 'reporter', function () { return 'minecraft:grass_block'; }, util); }
		blockAt(args, util) { return this.__tmc('turbomc_blockAt', [args.X, args.Y, args.Z], 'reporter', function () { this.preview('blockAt ' + args.X + ',' + args.Y + ',' + args.Z); return 'minecraft:air'; }, util); }
		blockJson(args, util) { return this.__tmc('turbomc_blockJson', [args.X, args.Y, args.Z], 'reporter', function () { return '{"id":"","x":0,"y":0,"z":0,"state":{},"hardness":0,"is_air":false,"is_solid":false,"is_fluid":false,"light":0,"biome":"","block_entity":{"type":"","items":[],"items_total":0,"nbt":{},"text_front":"","text_back":""}}'; }, util); }
		playersJson(args, util) { return this.__tmc('turbomc_playersJson', [], 'reporter', function () { return '[]'; }, util); }
		scanJson(args, util) { return this.__tmc('turbomc_scanJson', [args.X1, args.Y1, args.Z1, args.X2, args.Y2, args.Z2, args.LIMIT], 'reporter', function () { return '{"x1":0,"y1":0,"z1":0,"x2":0,"y2":0,"z2":0,"truncated":false,"blocks":[]}'; }, util); }
		snapshotKeys(args, util) { return this.__tmc('turbomc_snapshotKeys', [args.KIND], 'reporter', function () { return '[]'; }, util); }
		worldJson(args, util) { return this.__tmc('turbomc_worldJson', [], 'reporter', function () { return '{"time":0,"day":1,"moon_phase":0,"raining":false,"thundering":false,"dimension":"","biome":"","light":0}'; }, util); }
		frontBlock(args, util) { return this.__tmc('turbomc_frontBlock', [], 'reporter', function () { return 'minecraft:air'; }, util); }
		aimPos(args, util) { return this.__tmc('turbomc_aimPos', [args.AXIS], 'reporter', function () { return 0; }, util); }
		ping(args, util) { return this.__tmc('turbomc_ping', [], 'reporter', function () { return 0; }, util); }
		nearestEntityField(args, util) { return this.__tmc('turbomc_nearestEntityField', [args.TYPE, args.FIELD], 'reporter', function () { return 0; }, util); }
		entityCount(args, util) { return this.__tmc('turbomc_entityCount', [args.TYPE], 'reporter', function () { return 0; }, util); }
		entitiesJson(args, util) { return this.__tmc('turbomc_entitiesJson', [args.TYPE, args.RADIUS, args.LIMIT], 'reporter', function () { return '[]'; }, util); }
		drawFrame(args, util) { return this.__tmc('turbomc_drawFrame', [args.X1, args.Y1, args.Z1, args.X2, args.Y2, args.Z2, args.COLOR], 'command', function () { this.preview('drawFrame ' + args.X1 + ',' + args.Y1 + ',' + args.Z1 + ' -> ' + args.X2 + ',' + args.Y2 + ',' + args.Z2 + ' ' + args.COLOR); }, util); }
		clearFrames(args, util) { return this.__tmc('turbomc_clearFrames', [], 'command', function () { this.preview('clearFrames'); }, util); }
		log(args, util) { return this.__tmc('turbomc_log', [args.TEXT], 'command', function () { console.log('[turbomc]', args.TEXT); }, util); }
		chat(args, util) { return this.__tmc('turbomc_chat', [args.TEXT], 'command', function () { this.preview('chat ' + args.TEXT); }, util); }
		command(args, util) { return this.__tmc('turbomc_command', [args.COMMAND], 'command', function () { this.preview('command ' + args.COMMAND); }, util); }
		lastChat(args, util) { return this.__tmc('turbomc_lastChat', [], 'reporter', function () { this.preview('lastChat'); return ''; }, util); }
		chatHistory(args, util) { return this.__tmc('turbomc_chatHistory', [args.COUNT], 'reporter', function () { return '[]'; }, util); }
		triggerChat(args, util) { return this.__tmc('turbomc_triggerChat', [], 'reporter', function () { this.preview('triggerChat'); return ''; }, util); }
		aiAsk(args, util) { return this.__tmc('turbomc_aiAsk', [args.SESSION, args.TEXT], 'command', function () { this.preview('aiAsk ' + args.SESSION + ' ' + args.TEXT); }, util); }
		aiReply(args, util) { return this.__tmc('turbomc_aiReply', [args.SESSION], 'reporter', function () { this.preview('aiReply'); return ''; }, util); }
		aiReset(args, util) { return this.__tmc('turbomc_aiReset', [args.SESSION], 'command', function () { this.preview('aiReset'); }, util); }
		aiSystem(args, util) { return this.__tmc('turbomc_aiSystem', [args.SESSION, args.TEXT], 'command', function () { this.preview('aiSystem'); }, util); }
		jsonIs(args, util) { return __tmcJson('jsonIs', [args.json, args.check], true); }
		jsonNew(args, util) { return __tmcJson('jsonNew', [args.json], false); }
		jsonLength(args, util) { return __tmcJson('jsonLength', [args.json], false); }
		jsonHasKey(args, util) { return __tmcJson('jsonHasKey', [args.json, args.key], true); }
		jsonGetAll(args, util) { return __tmcJson('jsonGetAll', [args.json, args.Stype], false); }
		jsonErrMsg(args, util) { return __tmcJson('jsonErrMsg', [args.json], false); }
		jsonMinify(args, util) { return __tmcJson('jsonMinify', [args.json], false); }
		jsonGet(args, util) { return __tmcJson('jsonGet', [args.json, args.item], false); }
		jsonSet(args, util) { return __tmcJson('jsonSet', [args.json, args.item, args.value], false); }
		jsonDelete(args, util) { return __tmcJson('jsonDelete', [args.json, args.item], false); }
		jsonGetPath(args, util) { return __tmcJson('jsonGetPath', [args.json, args.path], false); }
		jsonSetPath(args, util) { return __tmcJson('jsonSetPath', [args.json, args.path, args.data], false); }
		jsonArrayPush(args, util) { return __tmcJson('jsonArrayPush', [args.json, args.item], false); }
		jsonArraySet(args, util) { return __tmcJson('jsonArraySet', [args.json, args.pos, args.item], false); }
		jsonArrayInsert(args, util) { return __tmcJson('jsonArrayInsert', [args.json, args.pos, args.item], false); }
		jsonArrayDelete(args, util) { return __tmcJson('jsonArrayDelete', [args.json, args.item], false); }
		jsonArrayRemoveAll(args, util) { return __tmcJson('jsonArrayRemoveAll', [args.json, args.item], false); }
		jsonArrayItemNum(args, util) { return __tmcJson('jsonArrayItemNum', [args.item, args.json], false); }
		jsonArrayFromTo(args, util) { return __tmcJson('jsonArrayFromTo', [args.json, args.item, args.item2], false); }
		jsonArraySetLen(args, util) { return __tmcJson('jsonArraySetLen', [args.json, args.len], false); }
		jsonArrayConcat(args, util) { return __tmcJson('jsonArrayConcat', [args.json, args.json2], false); }
		jsonArrayMapField(args, util) { return __tmcJson('jsonArrayMapField', [args.json, args.key], false); }
		jsonArrayCreate(args, util) { return __tmcJson('jsonArrayCreate', [args.d, args.text], false); }
		jsonArrayJoin(args, util) { return __tmcJson('jsonArrayJoin', [args.d, args.json], false); }
		jsonArraySort(args, util) { return __tmcJson('jsonArraySort', [args.list, args.order], false); }
		jsonArrayTextFilter(args, util) { return __tmcJson('jsonArrayTextFilter', [args.list, args.options, args.text, args.type], false); }
		jsonArrayAnalysis(args, util) { return __tmcJson('jsonArrayAnalysis', [args.list, args.analysis], false); }
		jsonVmGetList(args, util) { return this.__tmc('turbomc_jsonVmGetList', [args.list], 'reporter', function () { this.preview('jsonVmGetList ' + args.list); return '[]'; }, util); }
		jsonVmSetList(args, util) { return this.__tmc('turbomc_jsonVmSetList', [args.list, args.json], 'command', function () { this.preview('jsonVmSetList ' + args.list); }, util); }

		preview(name) {
			console.log('[turbomc] 预览模式（浏览器内无实际效果）:', name);
		}
	}

/*
 * JSON 积木的唯一实现（浏览器编辑器与游戏内 Rhino 共用同一份源码）。
 *
 * <p><b>为什么是 ES5</b>：这份文件有两个宿主——TurboWarp 浏览器（V8，什么语法都吃）
 * 与内嵌 Rhino 1.7.15（{@code optimizationLevel=-1} 纯解释执行）。Rhino 1.7.15 不支持
 * {@code class}，{@code const} 在同一函数内重复声明还会直接报错，所以这里只写 ES5：
 * 浏览器跑 ES5 没有任何问题，反过来 Rhino 跑 ES6 会直接起不来。</p>
 *
 * <p><b>为什么不再有 Java 副本</b>：reporter 在 Scratch 里是同步求值，浏览器不可能为了一次读数
 * 去等 RPC，所以这批纯计算积木只能在 JS 里实现；两处各写一份就是两份实现、两份维护。
 * 现在只保留这一份：编辑器直接调用，游戏内由 vm-bootstrap 装到扩展对象上。
 * 与 Java 的历史基线仍可用 tools/json-parity-cases.json 逐条核对（{@code build/json-parity-java.json}
 * 是迁移前生成的 golden，用来证明改写没有改变行为）。</p>
 *
 * <p>由 tools/build-extension.js 拼进编辑器产物，并原样复制为运行时副本
 * {@code assets/turbomc/vm/json-core.es5.js}。</p>
 */
function createJsonLite() {
	'use strict';

	/** 与历史 Java 侧 MAX_DEPTH 一致：递归下降不设上限会被深层输入打穿栈。 */
	var MAX_DEPTH = 256;

	// ES5 没有 Number.isNaN / isFinite / isInteger / Math.trunc，统一在这里补。
	// 入参都已是 number，所以全局 isNaN / isFinite 的隐式转换不影响语义。
	function isNaNValue(value) {
		return value !== value;
	}

	function isFiniteValue(value) {
		return typeof value === 'number' && isFinite(value);
	}

	function isIntegerValue(value) {
		return isFinite(value) && Math.floor(value) === value;
	}

	/** Math.trunc 的 ES5 等价；只在这里用于切片下标，入参已是 number。 */
	function truncate(value) {
		return value < 0 ? Math.ceil(value) : Math.floor(value);
	}

	// ---- Scratch 值转换（对齐 ScratchCast） ----

	function toStr(value) {
		if (value === null || value === undefined) {
			return '';
		}
		if (typeof value === 'boolean') {
			return value ? 'true' : 'false';
		}
		return String(value);
	}

	/** 对齐 ScratchCast.rawNumber：解析不出给 NaN，不在这里归一成 0。 */
	function rawNumber(value) {
		if (typeof value === 'number') {
			return value;
		}
		if (typeof value === 'boolean') {
			return value ? 1 : 0;
		}
		if (value === null || value === undefined) {
			return 0;
		}
		var text = String(value).trim();
		if (text === '') {
			return 0;
		}
		var lower = text.toLowerCase();
		if (lower === 'infinity' || text === '+Infinity') {
			return Infinity;
		}
		if (lower === '-infinity') {
			return -Infinity;
		}
		if (lower === 'nan') {
			return NaN;
		}
		var hexadecimal;
		if (/^0[xX]/.test(text)) {
			hexadecimal = parseInt(text.slice(2), 16);
			return isNaNValue(hexadecimal) ? NaN : hexadecimal;
		}
		var parsed = Number(text);
		return isNaNValue(parsed) ? NaN : parsed;
	}

	/** 对齐 ScratchCast.toNumber：NaN 归 0。 */
	function toNum(value) {
		var number = rawNumber(value);
		return isNaNValue(number) ? 0 : number;
	}

	function isBlankText(value) {
		if (typeof value === 'number') {
			return false;
		}
		return toStr(value).trim() === '';
	}

	/** 对齐 ScratchCast.compare：两边都能当数字比数值，否则不分大小写比字典序。 */
	function castCompare(left, right) {
		var a = rawNumber(left);
		var b = rawNumber(right);
		if (a === 0 && isBlankText(left)) {
			a = NaN;
		} else if (b === 0 && isBlankText(right)) {
			b = NaN;
		}
		if (isNaNValue(a) || isNaNValue(b)) {
			var la = toStr(left).toLowerCase();
			var lb = toStr(right).toLowerCase();
			return la < lb ? -1 : (la > lb ? 1 : 0);
		}
		if ((a === Infinity && b === Infinity) || (a === -Infinity && b === -Infinity)) {
			return 0;
		}
		var diff = a - b;
		return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
	}

	// ---- 严格 JSON 解析（逐条对齐原 JsonText.Parser 的行为） ----
	//
	// 不用 JSON.parse 的原因只有一个：错误信息要逐字一致。
	// 「是不是合法 JSON」这块积木的返回值是给人看的，报错文案必须固定。

	function ParserError(message, position) {
		this.message = message;
		this.position = position;
	}

	function Parser(text) {
		this.text = text;
		this.pos = 0;
	}

	Parser.prototype.error = function (message) {
		return new ParserError(message, this.pos);
	};

	Parser.prototype.atEnd = function () {
		return this.pos >= this.text.length;
	};

	Parser.prototype.skipWhitespace = function () {
		while (this.pos < this.text.length) {
			var c = this.text.charAt(this.pos);
			if (c === ' ' || c === '\t' || c === '\n' || c === '\r') {
				this.pos++;
			} else {
				break;
			}
		}
	};

	Parser.prototype.readValue = function (depth) {
		if (depth > MAX_DEPTH) {
			throw this.error('JSON 嵌套过深');
		}
		this.skipWhitespace();
		if (this.atEnd()) {
			throw this.error('JSON 提前结束');
		}
		var c = this.text.charAt(this.pos);
		switch (c) {
			case '{':
				return this.readObject(depth);
			case '[':
				return this.readArray(depth);
			case '"':
				return this.readString();
			case 't':
				return this.readLiteral('true', true);
			case 'f':
				return this.readLiteral('false', false);
			case 'n':
				return this.readLiteral('null', null);
			default:
				return this.readNumber();
		}
	};

	Parser.prototype.readLiteral = function (literal, value) {
		if (this.text.substr(this.pos, literal.length) !== literal) {
			throw this.error('非法字面量');
		}
		this.pos += literal.length;
		return value;
	};

	Parser.prototype.readObject = function (depth) {
		var object = {};
		this.pos++;
		this.skipWhitespace();
		if (!this.atEnd() && this.text.charAt(this.pos) === '}') {
			this.pos++;
			return object;
		}
		while (true) {
			this.skipWhitespace();
			if (this.atEnd() || this.text.charAt(this.pos) !== '"') {
				throw this.error('键必须是双引号字符串');
			}
			var key = this.readString();
			this.skipWhitespace();
			if (this.atEnd() || this.text.charAt(this.pos) !== ':') {
				throw this.error('缺少冒号');
			}
			this.pos++;
			// 重复键以最后一次为准，与 JSON.parse 一致
			object[key] = this.readValue(depth + 1);
			this.skipWhitespace();
			if (this.atEnd()) {
				throw this.error('对象未闭合');
			}
			var c = this.text.charAt(this.pos);
			if (c === ',') {
				this.pos++;
				this.skipWhitespace();
				if (!this.atEnd() && this.text.charAt(this.pos) === '}') {
					throw this.error('不允许尾随逗号');
				}
				continue;
			}
			if (c === '}') {
				this.pos++;
				return object;
			}
			throw this.error('对象中出现非法字符');
		}
	};

	Parser.prototype.readArray = function (depth) {
		var array = [];
		this.pos++;
		this.skipWhitespace();
		if (!this.atEnd() && this.text.charAt(this.pos) === ']') {
			this.pos++;
			return array;
		}
		while (true) {
			array.push(this.readValue(depth + 1));
			this.skipWhitespace();
			if (this.atEnd()) {
				throw this.error('数组未闭合');
			}
			var c = this.text.charAt(this.pos);
			if (c === ',') {
				this.pos++;
				this.skipWhitespace();
				if (!this.atEnd() && this.text.charAt(this.pos) === ']') {
					throw this.error('不允许尾随逗号');
				}
				continue;
			}
			if (c === ']') {
				this.pos++;
				return array;
			}
			throw this.error('数组中出现非法字符');
		}
	};

	Parser.prototype.readString = function () {
		this.pos++;
		var out = '';
		while (true) {
			if (this.atEnd()) {
				throw this.error('字符串未闭合');
			}
			var c = this.text.charAt(this.pos++);
			if (c === '"') {
				return out;
			}
			if (c.charCodeAt(0) < 0x20) {
				throw this.error('字符串里有未转义的控制字符');
			}
			if (c !== '\\') {
				out += c;
				continue;
			}
			if (this.atEnd()) {
				throw this.error('转义未完成');
			}
			var escape = this.text.charAt(this.pos++);
			switch (escape) {
				case '"':
					out += '"';
					break;
				case '\\':
					out += '\\';
					break;
				case '/':
					out += '/';
					break;
				case 'b':
					out += '\b';
					break;
				case 'f':
					out += '\f';
					break;
				case 'n':
					out += '\n';
					break;
				case 'r':
					out += '\r';
					break;
				case 't':
					out += '\t';
					break;
				case 'u':
					if (this.pos + 4 > this.text.length) {
						throw this.error('\\u 转义不完整');
					}
					var hex = this.text.substr(this.pos, 4);
					this.pos += 4;
					if (!/^[0-9a-fA-F]{4}$/.test(hex)) {
						throw this.error('\\u 转义非法');
					}
					out += String.fromCharCode(parseInt(hex, 16));
					break;
				default:
					throw this.error('非法转义');
			}
		}
	};

	Parser.prototype.readNumber = function () {
		var start = this.pos;
		if (!this.atEnd() && this.text.charAt(this.pos) === '-') {
			this.pos++;
		}
		if (this.atEnd()) {
			throw this.error('数字不完整');
		}
		var first = this.text.charAt(this.pos);
		if (first === '0') {
			this.pos++;
		} else if (first >= '1' && first <= '9') {
			while (!this.atEnd() && this.text.charAt(this.pos) >= '0' && this.text.charAt(this.pos) <= '9') {
				this.pos++;
			}
		} else {
			throw this.error('非法数字');
		}
		if (!this.atEnd() && this.text.charAt(this.pos) === '.') {
			this.pos++;
			if (this.atEnd() || !(this.text.charAt(this.pos) >= '0' && this.text.charAt(this.pos) <= '9')) {
				throw this.error('小数点后必须有数字');
			}
			while (!this.atEnd() && this.text.charAt(this.pos) >= '0' && this.text.charAt(this.pos) <= '9') {
				this.pos++;
			}
		}
		if (!this.atEnd() && (this.text.charAt(this.pos) === 'e' || this.text.charAt(this.pos) === 'E')) {
			this.pos++;
			if (!this.atEnd() && (this.text.charAt(this.pos) === '+' || this.text.charAt(this.pos) === '-')) {
				this.pos++;
			}
			if (this.atEnd() || !(this.text.charAt(this.pos) >= '0' && this.text.charAt(this.pos) <= '9')) {
				throw this.error('指数必须有数字');
			}
			while (!this.atEnd() && this.text.charAt(this.pos) >= '0' && this.text.charAt(this.pos) <= '9') {
				this.pos++;
			}
		}
		return Number(this.text.slice(start, this.pos));
	};

	/** 解析结果：{ok, value}。失败与 JSON 字面量 null 都算「没有值」。 */
	function parse(text) {
		if (text === null || text === undefined) {
			return {ok: false, value: undefined};
		}
		try {
			var parser = new Parser(text);
			var value = parser.readValue(0);
			parser.skipWhitespace();
			if (!parser.atEnd()) {
				return {ok: false, value: undefined};
			}
			if (value === null) {
				return {ok: false, value: undefined};
			}
			return {ok: true, value: value};
		} catch (e) {
			return {ok: false, value: undefined};
		}
	}

	function errorMessage(text) {
		if (text === null || text === undefined) {
			return '';
		}
		try {
			var parser = new Parser(text);
			parser.readValue(0);
			parser.skipWhitespace();
			if (!parser.atEnd()) {
				return 'JSON 解析失败：值后面还有内容（位置 ' + parser.pos + '）';
			}
		} catch (e) {
			if (e instanceof ParserError) {
				return 'JSON 解析失败：' + e.message + '（位置 ' + e.position + '）';
			}
			return 'JSON 解析失败';
		}
		if (!looksLikeContainer(text)) {
			return 'JSON 必须是对象或数组';
		}
		return '';
	}

	function looksLikeContainer(text) {
		if (text === null || text === undefined) {
			return false;
		}
		var body = String(text).trim();
		if (body.length < 2) {
			return false;
		}
		var first = body.charAt(0);
		var last = body.charAt(body.length - 1);
		return (first === '[' && last === ']') || (first === '{' && last === '}');
	}

	/** 等价 JSON.stringify（不缩进）；非有限数写成 null，与 JSON.stringify 一致。 */
	function stringify(value) {
		return JSON.stringify(value === undefined ? null : value);
	}

	function isContainer(value) {
		return value !== null && typeof value === 'object';
	}

	function toScratch(value) {
		if (value === null || value === undefined) {
			return '';
		}
		if (isContainer(value)) {
			return stringify(value);
		}
		return value;
	}

	function toWriteValue(raw) {
		var text = toStr(raw);
		if (looksLikeContainer(text)) {
			var parsed = parse(text);
			if (parsed.ok) {
				return parsed.value;
			}
		}
		return text;
	}

	/** Scratch 风格下标 → 0 基：1 是首项，-1 是末项，0 与越界给 -1。 */
	function arrayIndex(raw, size) {
		var number = toNum(raw);
		if (!isFiniteValue(number) || number !== Math.round(number) || number === 0) {
			return -1;
		}
		var index = number;
		if (index > 0) {
			index--;
		} else {
			index += size;
		}
		return index >= 0 && index < size ? index : -1;
	}

	/** JS 的 arr[i]=x：越界会把中间填成空洞，序列化后就是 null。 */
	function setArraySlot(array, index, value) {
		while (array.length <= index) {
			array.push(null);
		}
		array[index] = value;
	}

	function jsSliceIndex(raw, size) {
		var index = truncate(raw);
		if (index < 0) {
			index = Math.max(size + index, 0);
		}
		return Math.min(index, size);
	}

	/** 对齐 keyText：数字按 ECMAScript 格式化，对象退化 [object Object]。 */
	function keyText(value) {
		if (value === null || value === undefined) {
			return 'null';
		}
		if (Array.isArray(value)) {
			return value.map(keyText).join(',');
		}
		if (typeof value === 'object') {
			return '[object Object]';
		}
		if (typeof value === 'boolean') {
			return value ? 'true' : 'false';
		}
		return String(value);
	}

	/** 写入用的数组位置：1 基，允许越界；非整数或小于 1 给 -1。 */
	function writeSlot(raw) {
		var number = toNum(raw);
		if (!isFiniteValue(number) || number !== Math.round(number) || number < 1) {
			return -1;
		}
		return number - 1;
	}

	/** JS ===：只有同类型标量相等；对象/数组按引用比，永远不相等。 */
	function strictEquals(left, right) {
		if (isContainer(left) || isContainer(right)) {
			return false;
		}
		if (left === null || right === null || left === undefined || right === undefined) {
			return false;
		}
		return typeof left === typeof right && left === right;
	}

	function parseArray(text) {
		var parsed = parse(text);
		return parsed.ok && Array.isArray(parsed.value) ? parsed.value : null;
	}

	// ---- 积木实现 ----

	var BLOCKS = {};

	BLOCKS.jsonNew = function (a) {
		var kind = toStr(a[0]);
		if (kind === 'Object') {
			return '{}';
		}
		if (kind === 'Array') {
			return '[]';
		}
		return '';
	};

	BLOCKS.jsonLength = function (a) {
		var parsed = parse(toStr(a[0]));
		if (!parsed.ok) {
			return '';
		}
		if (Array.isArray(parsed.value)) {
			return parsed.value.length;
		}
		if (typeof parsed.value === 'object') {
			return Object.keys(parsed.value).length;
		}
		return '';
	};

	BLOCKS.jsonGet = function (a) {
		var parsed = parse(toStr(a[0]));
		var item = toStr(a[1]);
		if (!parsed.ok) {
			return '';
		}
		if (Array.isArray(parsed.value)) {
			var index = arrayIndex(item, parsed.value.length);
			return index < 0 ? '' : toScratch(parsed.value[index]);
		}
		if (typeof parsed.value === 'object') {
			return Object.prototype.hasOwnProperty.call(parsed.value, item)
				? toScratch(parsed.value[item]) : '';
		}
		return '';
	};

	BLOCKS.jsonSet = function (a) {
		var parsed = parse(toStr(a[0]));
		var item = toStr(a[1]);
		var value = toWriteValue(a[2]);
		if (!parsed.ok) {
			return '';
		}
		if (Array.isArray(parsed.value)) {
			var slot = writeSlot(item);
			if (slot >= 0) {
				setArraySlot(parsed.value, slot, value);
			}
			return stringify(parsed.value);
		}
		if (typeof parsed.value === 'object') {
			parsed.value[item] = value;
			return stringify(parsed.value);
		}
		return '';
	};

	BLOCKS.jsonDelete = function (a) {
		var parsed = parse(toStr(a[0]));
		var item = toStr(a[1]);
		if (!parsed.ok) {
			return '';
		}
		if (Array.isArray(parsed.value)) {
			var index = arrayIndex(item, parsed.value.length);
			if (index >= 0) {
				// 照搬 delete arr[i]：长度不变，该位置变 null
				parsed.value[index] = null;
			}
			return stringify(parsed.value);
		}
		if (typeof parsed.value === 'object') {
			delete parsed.value[item];
			return stringify(parsed.value);
		}
		return '';
	};

	BLOCKS.jsonIs = function (a) {
		// 只有字符串才可能「是合法 JSON」——数字 1 不是 JSON
		if (typeof a[0] !== 'string') {
			return false;
		}
		var text = a[0];
		var check = toStr(a[1]);
		if (check === 'valid') {
			return looksLikeContainer(text) && parse(text).ok;
		}
		if (!looksLikeContainer(text)) {
			return false;
		}
		var parsed = parse(text);
		if (!parsed.ok) {
			return false;
		}
		var array = Array.isArray(parsed.value);
		if (check === 'Object') {
			return !array;
		}
		if (check === 'Array') {
			return array;
		}
		return false;
	};

	BLOCKS.jsonHasKey = function (a) {
		var parsed = parse(toStr(a[0]));
		if (!parsed.ok || !isContainer(parsed.value)) {
			return false;
		}
		var key = keyText(toWriteValue(a[1]));
		if (Array.isArray(parsed.value)) {
			if (key === 'length') {
				return false;
			}
			return arrayIndex(key, parsed.value.length) >= 0;
		}
		return Object.prototype.hasOwnProperty.call(parsed.value, key);
	};

	BLOCKS.jsonGetPath = function (a) {
		var root = parse(toStr(a[0]));
		var path = parse(toStr(a[1]));
		if (!root.ok || !path.ok || !Array.isArray(path.value)) {
			return '';
		}
		var current = root.value;
		for (var i = 0; i < path.value.length; i++) {
			var segment = path.value[i];
			if (Array.isArray(current)) {
				var index = arrayIndex(toScratch(segment), current.length);
				if (index < 0) {
					return '';
				}
				current = current[index];
			} else if (isContainer(current)) {
				var key = keyText(segment);
				if (!Object.prototype.hasOwnProperty.call(current, key)) {
					return '';
				}
				current = current[key];
			} else {
				return '';
			}
		}
		return toScratch(current);
	};

	BLOCKS.jsonSetPath = function (a) {
		var root = parse(toStr(a[0]));
		var path = parse(toStr(a[1]));
		if (!root.ok || !path.ok || !Array.isArray(path.value)) {
			return '';
		}
		var segments = path.value;
		if (segments.length === 0) {
			return stringify(root.value);
		}
		var data = toWriteValue(a[2]);
		var current = root.value;
		for (var i = 0; i < segments.length; i++) {
			var segment = segments[i];
			var last = i === segments.length - 1;
			if (Array.isArray(current)) {
				var index = arrayIndex(toScratch(segment), current.length);
				if (index < 0) {
					return '';
				}
				if (last) {
					current[index] = data;
				} else {
					current = current[index];
				}
			} else if (isContainer(current)) {
				var key = keyText(segment);
				if (last) {
					current[key] = data;
				} else {
					if (!Object.prototype.hasOwnProperty.call(current, key)) {
						return '';
					}
					current = current[key];
				}
			} else {
				return '';
			}
		}
		return stringify(root.value);
	};

	BLOCKS.jsonMinify = function (a) {
		var parsed = parse(toStr(a[0]));
		return parsed.ok ? stringify(parsed.value) : '';
	};

	BLOCKS.jsonErrMsg = function (a) {
		return errorMessage(toStr(a[0]));
	};

	BLOCKS.jsonGetAll = function (a) {
		var parsed = parse(toStr(a[0]));
		if (!parsed.ok || !isContainer(parsed.value)) {
			return '';
		}
		var kind = toStr(a[1]);
		if (kind !== 'keys' && kind !== 'values' && kind !== 'datas') {
			return '';
		}
		var out = [];
		var add = function (key, value) {
			var safe = value === null || value === undefined ? '' : value;
			if (kind === 'keys') {
				out.push(key);
			} else if (kind === 'values') {
				out.push(safe);
			} else {
				out.push([key, safe]);
			}
		};
		if (Array.isArray(parsed.value)) {
			parsed.value.forEach(function (value, index) {
				add(String(index), value);
			});
		} else {
			Object.keys(parsed.value).forEach(function (key) {
				add(key, parsed.value[key]);
			});
		}
		return stringify(out);
	};

	BLOCKS.jsonArrayPush = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		array.push(toWriteValue(a[1]));
		return stringify(array);
	};

	BLOCKS.jsonArraySet = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var index = toNum(a[1]) - 1;
		if (isIntegerValue(index) && index >= 0) {
			setArraySlot(array, index, toWriteValue(a[2]));
		}
		return stringify(array);
	};

	BLOCKS.jsonArrayInsert = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var at = jsSliceIndex(toNum(a[1]) - 1, array.length);
		array.splice(at, 0, toWriteValue(a[2]));
		return stringify(array);
	};

	BLOCKS.jsonArrayDelete = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var at = jsSliceIndex(toNum(a[1]) - 1, array.length);
		if (at < array.length) {
			array.splice(at, 1);
		}
		return stringify(array);
	};

	BLOCKS.jsonArrayRemoveAll = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var target = toWriteValue(a[1]);
		for (var i = array.length - 1; i >= 0; i--) {
			if (strictEquals(array[i], target)) {
				array.splice(i, 1);
			}
		}
		return stringify(array);
	};

	BLOCKS.jsonArrayItemNum = function (a) {
		var array = parseArray(toStr(a[1]));
		if (array === null) {
			return '';
		}
		var target = toWriteValue(a[0]);
		for (var i = 0; i < array.length; i++) {
			if (strictEquals(array[i], target)) {
				return i + 1;
			}
		}
		return 0;
	};

	BLOCKS.jsonArrayFromTo = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var start = jsSliceIndex(toNum(a[1]) - 1, array.length);
		var end = jsSliceIndex(toNum(a[2]), array.length);
		if (end < start) {
			end = start;
		}
		return stringify(array.slice(start, end));
	};

	BLOCKS.jsonArrayConcat = function (a) {
		var left = parseArray(toStr(a[0]));
		var right = parseArray(toStr(a[1]));
		if (left === null || right === null) {
			return '';
		}
		return stringify(left.concat(right));
	};

	BLOCKS.jsonArrayMapField = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var key = toStr(a[1]);
		return stringify(array.map(function (element) {
			return isContainer(element) && !Array.isArray(element)
				&& Object.prototype.hasOwnProperty.call(element, key)
				? element[key] : null;
		}));
	};

	BLOCKS.jsonArraySetLen = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var raw = toNum(a[1]);
		if (!isIntegerValue(raw) || raw < 0) {
			return '';
		}
		array.length = raw;
		// 变大时 JS 留下空洞，序列化后是 null——这里显式补 null，避免空洞让 stringify 写成 null 之外的东西
		for (var i = 0; i < raw; i++) {
			if (array[i] === undefined) {
				array[i] = null;
			}
		}
		return stringify(array);
	};

	BLOCKS.jsonArrayCreate = function (a) {
		var delimiter = toStr(a[0]);
		var text = toStr(a[1]);
		if (delimiter === '') {
			// 按 UTF-16 代码单元拆，与原 Java 的 charAt 循环一致（Array.from 会按码点拆，反而不一致）
			return stringify(text.split(''));
		}
		return stringify(text.split(delimiter));
	};

	BLOCKS.jsonArrayJoin = function (a) {
		var delimiter = toStr(a[0]);
		var array = parseArray(toStr(a[1]));
		if (array === null) {
			return '';
		}
		return array.map(function (element) {
			return element === null || element === undefined ? '' : keyText(element);
		}).join(delimiter);
	};

	BLOCKS.jsonArraySort = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return '';
		}
		var items = array.slice();
		// 参与比较的取值：对象/数组退化为文本，null 当 0
		var compareValue = function (element) {
			if (element === null || element === undefined) {
				return 0;
			}
			if (isContainer(element)) {
				return keyText(element);
			}
			return element;
		};
		items.sort(function (left, right) {
			return castCompare(compareValue(left), compareValue(right));
		});
		if (toStr(a[1]) === 'descending') {
			items.reverse();
		}
		return stringify(items);
	};

	BLOCKS.jsonArrayTextFilter = function (a) {
		var root = parse(toStr(a[0]));
		if (!root.ok || !isContainer(root.value)) {
			return '';
		}
		var option = toStr(a[1]);
		var needle = toStr(a[2]);
		var shape = toStr(a[3]);
		var asObject = shape === 'Object';
		if (!asObject && shape !== 'Array') {
			return '';
		}
		var values = [];
		var mapped = {};
		var matches = function (element) {
			if (typeof element !== 'string') {
				return false;
			}
			if (option === 'includes') {
				return element.indexOf(needle) >= 0;
			}
			if (option === 'starts with') {
				return element.indexOf(needle) === 0;
			}
			if (option === 'ends with') {
				return needle.length <= element.length
					&& element.lastIndexOf(needle) === element.length - needle.length;
			}
			return false;
		};
		if (Array.isArray(root.value)) {
			root.value.forEach(function (element, index) {
				if (matches(element)) {
					values.push(element);
					mapped[String(index + 1)] = element;
				}
			});
		} else {
			Object.keys(root.value).forEach(function (key) {
				var element = root.value[key];
				if (matches(element)) {
					values.push(element);
					mapped[key] = element;
				}
			});
		}
		return stringify(asObject ? mapped : values);
	};

	BLOCKS.jsonArrayAnalysis = function (a) {
		var array = parseArray(toStr(a[0]));
		if (array === null) {
			return 0;
		}
		var kind = toStr(a[1]);
		var values = array.map(function (element) {
			return toNum(toScratch(element));
		});
		var sum = values.reduce(function (total, value) {
			return total + value;
		}, 0);
		var i;
		switch (kind) {
			case 'maximum': {
				var max = -Infinity;
				for (i = 0; i < values.length; i++) {
					if (values[i] > max) {
						max = values[i];
					}
				}
				return max;
			}
			case 'minimum': {
				var min = Infinity;
				for (i = 0; i < values.length; i++) {
					if (values[i] < min) {
						min = values[i];
					}
				}
				return min;
			}
			case 'sum':
				return sum;
			case 'average':
				return sum / values.length;
			case 'median': {
				if (values.length === 0) {
					return NaN;
				}
				var sorted = values.slice().sort(function (x, y) {
					return x < y ? -1 : (x > y ? 1 : 0);
				});
				var middle = Math.floor(sorted.length / 2);
				return sorted.length % 2 === 0
					? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
			}
			case 'mode': {
				// 键统一加前缀：数字转字符串后不会撞上 Object 原型上的名字。
				// -0 与 0 算同一个键（SameValueZero）；NaN 也归一到同一个键。
				var counts = {};
				var mode = 0;
				var best = 0;
				for (i = 0; i < values.length; i++) {
					var value = values[i];
					var key = '#' + String(value === 0 ? 0 : value);
					var count = (counts[key] || 0) + 1;
					counts[key] = count;
					if (count > best) {
						best = count;
						mode = value;
					}
				}
				return mode;
			}
			case 'variance': {
				var average = sum / values.length;
				var total = 0;
				for (i = 0; i < values.length; i++) {
					var delta = values[i] - average;
					total += delta * delta;
				}
				return total / values.length;
			}
			default:
				return 0;
		}
	};

	return {
		blocks: BLOCKS,
		/** 供对拍脚本直接调用：返回与 Java 侧同级的字符串化结果。 */
		run: function (opcode, args) {
			var fn = BLOCKS[opcode];
			return fn ? fn(args) : '';
		},
		toScratchString: toStr
	};
}

	/*
	 * 纯计算积木（JSON 一族）的本地入口：构建期为这 27 块生成直调，
	 * 运行期完全不经过 __tmc，因此与「是否已连接游戏」无关。
	 *
	 * <p>以前它们和其他积木共用 __tmc，而 __tmc 的第一句就是「未连接 → 返回 fallback」，
	 * 于是「导入扩展后、连接游戏前」JSON 全部返回占位假值（长度恒 0、取键恒空串、
	 * 新建数组得到 {}）。这里把它们挪到连接判断之外，未连接也能算出真实结果。</p>
	 *
	 * <p>查表只做一次：createJsonLite() 每次调用都会重建整张积木表，热路径上不能重复付这个代价。</p>
	 */
	let __tmcJsonBlocks = null;
	function __tmcJson(opcode, argv, isBoolean) {
		if (!__tmcJsonBlocks) {
			__tmcJsonBlocks = createJsonLite().blocks;
		}
		const fn = __tmcJsonBlocks[opcode];
		if (!fn) {
			return isBoolean ? false : '';
		}
		try {
			const out = fn(Array.isArray(argv) ? argv : []);
			// 布尔积木必须归一化成 JS 原始布尔：scratch 的 Cast.toBoolean 会把任意对象判真
			return isBoolean ? !!out : out;
		} catch (e) {
			return isBoolean ? false : '';
		}
	}

/*
 * 远程调试的「画面」：浏览器原生屏幕/窗口捕获，覆盖在 TurboWarp 舞台上方。
 *
 * 为什么不用游戏端推流（FBO → JPEG → WebSocket）：同机开发时游戏窗口就在旁边，
 * 推流是把眼前的东西搬进浏览器，却要付掉帧、带宽与一整套 1.21.11 渲染 API 的风险。
 * `getDisplayMedia` 由系统合成器直接交给浏览器，游戏端零代码、零带宽（原方案见 ARCHITECTURE.md §7.8）。
 *
 * 由 tools/build-extension.js 拼进编辑器产物；运行时副本（游戏内）不包含本文件。
 */
function createStageView(Scratch) {
	'use strict';

	const STORAGE_KEY = 'turbomc.remote.view';
	const MODE_VIDEO = 'video';
	const MODE_STAGE = 'stage';

	const state = {
		/** @type {HTMLVideoElement|null} */
		video: null,
		/** @type {MediaStream|null} */
		stream: null,
		/** 覆盖层当前挂在哪个宿主元素下 */
		host: null,
		mode: loadMode(),
		lastError: '',
		/** 捕获源的原始分辨率，仅用于面板显示 */
		source: null,
		observer: null,
		timer: null
	};

	function loadMode() {
		try {
			return localStorage.getItem(STORAGE_KEY) === MODE_STAGE ? MODE_STAGE : MODE_VIDEO;
		} catch (e) {
			return MODE_VIDEO;
		}
	}

	function saveMode(mode) {
		try {
			localStorage.setItem(STORAGE_KEY, mode);
		} catch (e) {
			// 隐私模式写不进去：本次会话内仍然生效
		}
	}

	// ---- 舞台定位 ----
	// 与探针同一套：优先 runtime.renderer.canvas，拿不到再退回「页面最大画布」。
	// 实测自建站与官方站都能拿到真实 DOM（renderer 不在 worker 里）。

	function findStageCanvas() {
		try {
			const renderer = Scratch.vm && Scratch.vm.runtime ? Scratch.vm.runtime.renderer : null;
			const canvas = renderer ? renderer.canvas : null;
			if (canvas && typeof canvas.getBoundingClientRect === 'function' && canvas.width > 0) {
				return canvas;
			}
		} catch (e) {
			// 落到 DOM 兜底
		}
		const list = Array.prototype.slice.call(document.querySelectorAll('canvas'))
			.filter((c) => c.width > 100 && c.height > 100);
		list.sort((a, b) => (b.width * b.height) - (a.width * a.height));
		return list[0] || null;
	}

	/** 把 video 对齐到舞台画布；舞台找不到时固定一块区域，至少能看到画面。 */
	function place() {
		const video = state.video;
		if (!video || !state.stream) {
			return;
		}
		const canvas = findStageCanvas();
		const style = video.style;
		style.objectFit = 'contain';
		style.background = '#000';
		style.pointerEvents = 'none';
		style.zIndex = '999';
		style.display = state.mode === MODE_STAGE ? 'none' : 'block';

		if (!canvas) {
			if (video.parentElement !== document.body) {
				document.body.appendChild(video);
			}
			state.host = document.body;
			style.position = 'fixed';
			style.left = '40px';
			style.top = '40px';
			style.width = '640px';
			style.height = '360px';
			return;
		}

		const host = canvas.parentElement || document.body;
		if (state.host !== host) {
			if (video.parentElement) {
				video.parentElement.removeChild(video);
			}
			host.appendChild(video);
			state.host = host;
			// 绝对定位需要一个定位上下文；宿主是 static 时补上
			if (getComputedStyle(host).position === 'static') {
				host.style.position = 'relative';
			}
			watchStage(canvas);
		}
		const cr = canvas.getBoundingClientRect();
		const pr = host.getBoundingClientRect();
		style.position = 'absolute';
		style.left = (cr.left - pr.left) + 'px';
		style.top = (cr.top - pr.top) + 'px';
		style.width = cr.width + 'px';
		style.height = cr.height + 'px';
	}

	/**
	 * 跟随布局变化。`ResizeObserver` 管画布尺寸，窗口 resize 管滚动与缩放；
	 * 再加一个低频兜底，处理「面板开合导致舞台位移」这类观察不到的情况。
	 */
	function watchStage(canvas) {
		if (state.observer) {
			state.observer.disconnect();
		}
		if (typeof ResizeObserver === 'function') {
			state.observer = new ResizeObserver(() => place());
			state.observer.observe(canvas);
		}
		if (!state.timer) {
			state.timer = setInterval(() => {
				if (state.stream) {
					place();
				}
			}, 500);
		}
	}

	// ---- 捕获 ----

	function describeError(error) {
		if (!error) {
			return '未知错误';
		}
		if (error.name === 'NotAllowedError') {
			return '被拒绝或没有用户手势：再点一次按钮并允许共享';
		}
		if (error.name === 'NotSupportedError') {
			return '此环境不支持屏幕捕获（Desktop 版没有实现 setDisplayMediaRequestHandler）';
		}
		return (error.name || 'Error') + ': ' + (error.message || String(error));
	}

	/**
	 * 开始捕获。**必须由用户手势调用**（按钮点击），页面加载时自动调用会被浏览器拒绝。
	 */
	async function start() {
		if (state.stream) {
			return {ok: true, already: true};
		}
		if (!navigator.mediaDevices || typeof navigator.mediaDevices.getDisplayMedia !== 'function') {
			state.lastError = '当前环境没有 getDisplayMedia';
			return {ok: false, error: state.lastError};
		}
		state.lastError = '';
		try {
			// displaySurface: 'window' 只是偏好；用户仍可选整个屏幕
			const stream = await navigator.mediaDevices.getDisplayMedia({
				video: {frameRate: {ideal: 30, max: 60}, displaySurface: 'window'},
				audio: false
			});
			state.stream = stream;

			const video = document.createElement('video');
			video.autoplay = true;
			video.playsInline = true;
			video.muted = true;
			video.srcObject = stream;
			state.video = video;

			const track = stream.getVideoTracks()[0];
			const settings = track && track.getSettings ? track.getSettings() : {};
			state.source = {
				width: settings.width || 0,
				height: settings.height || 0,
				frameRate: settings.frameRate || 0,
				label: track && track.label ? track.label : ''
			};

			// 用户在浏览器原生「停止共享」里点了结束：跟着收尾
			if (track) {
				track.addEventListener('ended', () => stop());
			}

			state.mode = loadMode();
			place();
			try {
				await video.play();
			} catch (e) {
				// 自动播放被拦：无声音的 video 一般在用户手势后可以播放，失败也不致命
			}
			return {ok: true};
		} catch (error) {
			state.lastError = describeError(error);
			state.stream = null;
			return {ok: false, error: state.lastError};
		}
	}

	function stop() {
		const stream = state.stream;
		state.stream = null;
		if (stream) {
			stream.getTracks().forEach((track) => {
				try {
					track.stop();
				} catch (e) {
					// 已停止
				}
			});
		}
		if (state.video && state.video.parentElement) {
			state.video.parentElement.removeChild(state.video);
		}
		if (state.observer) {
			state.observer.disconnect();
			state.observer = null;
		}
		if (state.timer) {
			clearInterval(state.timer);
			state.timer = null;
		}
		state.video = null;
		state.host = null;
		state.source = null;
	}

	/** 画面 ↔ 舞台。舞台模式下隐藏覆盖层，原生舞台与变量监视器完全可见。 */
	function setMode(mode) {
		state.mode = mode === MODE_STAGE ? MODE_STAGE : MODE_VIDEO;
		saveMode(state.mode);
		if (state.video) {
			state.video.style.display = state.mode === MODE_STAGE ? 'none' : 'block';
			if (state.mode === MODE_VIDEO) {
				place();
			}
		}
		return state.mode;
	}

	function toggleMode() {
		return setMode(state.mode === MODE_STAGE ? MODE_VIDEO : MODE_STAGE);
	}

	return {
		start: start,
		stop: stop,
		setMode: setMode,
		toggleMode: toggleMode,
		isCapturing: () => !!state.stream,
		getMode: () => state.mode,
		lastError: () => state.lastError,
		source: () => state.source,
		place: place
	};
}

/*
 * TurboMC 远程开发桥 —— 浏览器端实现。
 *
 * 本文件由 tools/build-extension.js 拼进编辑器产物（与积木声明同处一个 IIFE 内），
 * 不在游戏内运行，也不进入运行时副本。
 *
 * 职责：
 *   1. 连接面板：地址 / 端口 / 连接 / 断开 / 自动重连
 *   2. __tmc：所有积木的统一入口（未连接时执行 fallback，连接后走 RPC 或快照）
 *   3. 快照缓存与 tick 时钟：wait 与 poll 都由快照驱动（见 ARCHITECTURE.md §7.3）
 *   4. 帽子派发：按服务端下发的 hats 表，实现三种通用匹配器（ARCHITECTURE.md §7.5）
 *
 * 只做这三件事之外的事都是错的方向——积木语义留在 Java，这里不做第二份判断。
 */
function installRemote(Scratch, ext, meta) {
	'use strict';

	if (!Scratch.extensions.unsandboxed) {
		throw new Error('TurboMC 远程模式必须以 unsandboxed 方式加载扩展');
	}

	const STORAGE_KEY = 'turbomc.remote.v1';
	const RECONNECT_MS = 3000;
	/** 单个调用最长等待；动作自身的上限更短，这里是「绝不永挂」的兜底。 */
	const CALL_TIMEOUT_MS = 60000;
	/** 扩展期望的快照字段版本；与客户端 RemoteProtocol.SNAPSHOT_SCHEMA 必须一致。 */
	const SNAP_SCHEMA = 6;
	/**
	 * 画面：浏览器原生捕获（stage-view.js，构建时拼在同一作用域里）。
	 * 缺失时面板不显示画面按钮，其余功能不受影响。
	 */
	const STAGE_VIEW = typeof createStageView === 'function' ? createStageView(Scratch) : null;

	const S = {
		ws: null,
		connected: false,
		welcome: null,
		tick: 0,
		seq: 0,
		/** id → { resolve, value, tick0, wait, check, limit, settled, pollTick } */
		pending: new Map(),
		snapshot: null,
		lastSnapAt: 0,
		reconnectTimer: null,
		reconnectWanted: false,
		panel: null,
		panelBody: null,
		/** 最近一次帽子派发的结论，显示在状态行最后一行（见 noteHat）。 */
		lastHat: '',
		/** 状态文字节点：快照只改这里的 textContent，不重建整个面板（见 refreshStatus）。 */
		panelStatus: null,
		/** 上一次渲染面板时的连接状态，用来判断要不要重建控件。 */
		panelConnected: null,
		missingReporters: new Set(),
		/** 画面捕获的失败原因（面板上显示一行） */
		viewError: '',
		config: loadConfig()
	};

	// ---- 配置 ----

	function loadConfig() {
		const fallback = {
			address: '127.0.0.1', port: 35671, name: '', autoReconnect: true,
			panelX: null, panelY: null
		};
		try {
			const raw = localStorage.getItem(STORAGE_KEY);
			if (!raw) {
				return fallback;
			}
			const parsed = JSON.parse(raw);
			return {
				address: parsed.address || fallback.address,
				port: Number(parsed.port) || fallback.port,
				name: parsed.name || '',
				autoReconnect: parsed.autoReconnect !== false,
				// 面板位置：拖过一次才记，没记过就按默认的左下角（CSS 的 bottom:12px）
				panelX: Number.isFinite(parsed.panelX) ? parsed.panelX : null,
				panelY: Number.isFinite(parsed.panelY) ? parsed.panelY : null
			};
		} catch (e) {
			return fallback;
		}
	}

	function saveConfig() {
		try {
			localStorage.setItem(STORAGE_KEY, JSON.stringify(S.config));
		} catch (e) {
			// 隐私模式下写不进去，不影响本次会话
		}
	}

	// ---- 面板 ----

	function ensurePanel() {
		if (S.panel) {
			return S.panel;
		}
		const root = document.createElement('div');
		root.style.cssText = [
			'position:fixed', 'left:12px', 'bottom:12px', 'z-index:1000000',
			'width:340px', 'border-radius:8px', 'overflow:hidden',
			'background:rgba(18,18,22,0.96)', 'color:#e8e8ea',
			'font:12px/1.5 ui-monospace,Consolas,monospace',
			'box-shadow:0 6px 24px rgba(0,0,0,0.45)',
			'border:1px solid rgba(255,255,255,0.14)'
		].join(';');
		root.style.display = 'none';

		const head = document.createElement('div');
		head.style.cssText = 'display:flex;align-items:center;justify-content:space-between;'
			+ 'padding:6px 10px;background:rgba(255,255,255,0.06);font-weight:600';
		const title = document.createElement('span');
		title.textContent = 'TurboMC 远程调试';
		const close = document.createElement('button');
		close.textContent = '×';
		close.style.cssText = 'background:none;border:none;color:#e8e8ea;font-size:16px;'
			+ 'line-height:1;cursor:pointer;padding:0 2px';
		close.addEventListener('click', () => { root.style.display = 'none'; });
		head.appendChild(title);
		head.appendChild(close);

		const body = document.createElement('div');
		body.style.padding = '8px 10px';

		root.appendChild(head);
		root.appendChild(body);
		document.body.appendChild(root);
		S.panel = root;
		S.panelBody = body;
		makeDraggable(root, head);
		applyPanelPosition(root);
		// 窗口变小后把面板拉回可视区，否则标题栏可能被推到屏幕外、再也抓不回来。
		// 探针沙箱（tools/probe）的 window 是精简桩，没有 addEventListener，故按能力探测
		if (typeof window.addEventListener === 'function') {
			window.addEventListener('resize', () => clampPanel(root));
		}
		renderPanel();
		return root;
	}

	/**
	 * 让面板可拖动。
	 *
	 * <p>面板固定 340px 宽、压在编辑器左下角，遮住积木区时没有任何办法挪开——这是
	 * 「远程调试按下去就挡住工作区」的直接原因。改成拖标题栏移动，位置记进 localStorage，
	 * 下次打开还在原处；双击标题栏回到默认位置。</p>
	 *
	 * <p>用 Pointer Events：鼠标 / 触屏 / 触控笔一套逻辑，不需要分别监听 mouse 与 touch。
	 * 坐标一律钳在视口内——标题栏是唯一的抓手，它跑到屏幕外就等于面板丢了。</p>
	 */
	function makeDraggable(root, head) {
		head.style.cursor = 'move';
		head.style.userSelect = 'none';
		head.title = '拖动可移动，双击回到左下角';

		head.addEventListener('pointerdown', (e) => {
			// 关闭按钮上的按下不算拖动（按钮自己还要收 click）
			if (e.target !== head && e.target.tagName !== 'SPAN') {
				return;
			}
			if (typeof e.button === 'number' && e.button !== 0) {
				return;
			}
			const rect = root.getBoundingClientRect();
			const offsetX = e.clientX - rect.left;
			const offsetY = e.clientY - rect.top;
			// 先把「左下角锚定」换成「左上角坐标」，否则拖动会从 12px 处跳一下
			root.style.left = rect.left + 'px';
			root.style.top = rect.top + 'px';
			root.style.bottom = 'auto';
			if (typeof head.setPointerCapture === 'function') {
				head.setPointerCapture(e.pointerId);
			}

			// 用 AbortController 统一收回监听：比 removeEventListener 少写三行、
			// 也不会漏掉 pointercancel 这一路
			const controller = typeof AbortController === 'function' ? new AbortController() : null;
			const opts = controller ? {signal: controller.signal} : undefined;

			const move = (ev) => {
				const maxX = Math.max(0, window.innerWidth - root.offsetWidth);
				const maxY = Math.max(0, window.innerHeight - root.offsetHeight);
				const x = Math.min(Math.max(0, ev.clientX - offsetX), maxX);
				const y = Math.min(Math.max(0, ev.clientY - offsetY), maxY);
				root.style.left = x + 'px';
				root.style.top = y + 'px';
				S.config.panelX = x;
				S.config.panelY = y;
			};
			const up = () => {
				if (controller) {
					controller.abort();
				} else {
					head.removeEventListener('pointermove', move);
					head.removeEventListener('pointerup', up);
				}
				// 拖完才写盘：拖动过程每帧都写 localStorage 会卡顿
				saveConfig();
			};
			head.addEventListener('pointermove', move, opts);
			head.addEventListener('pointerup', up, opts);
			if (controller) {
				head.addEventListener('pointercancel', up, opts);
			}
		});

		head.addEventListener('dblclick', () => resetPanelPosition(root));
	}

	/** 恢复默认位置（左下角），并把记录清掉。 */
	function resetPanelPosition(root) {
		S.config.panelX = null;
		S.config.panelY = null;
		saveConfig();
		root.style.left = '12px';
		root.style.top = 'auto';
		root.style.bottom = '12px';
	}

	/** 应用上次记录的位置；没记录就保持 CSS 里的左下角默认值。 */
	function applyPanelPosition(root) {
		if (!Number.isFinite(S.config.panelX) || !Number.isFinite(S.config.panelY)) {
			return;
		}
		root.style.left = S.config.panelX + 'px';
		root.style.top = S.config.panelY + 'px';
		root.style.bottom = 'auto';
		clampPanel(root);
	}

	/** 把面板钳回视口内（窗口缩小、换了显示器、上次保存时窗口更大）。 */
	function clampPanel(root) {
		if (!root || !Number.isFinite(S.config.panelX) || !Number.isFinite(S.config.panelY)) {
			return;
		}
		const width = root.offsetWidth || 340;
		const height = root.offsetHeight || 0;
		const maxX = Math.max(0, window.innerWidth - width);
		const maxY = Math.max(0, window.innerHeight - height);
		const x = Math.min(Math.max(0, S.config.panelX), maxX);
		const y = Math.min(Math.max(0, S.config.panelY), maxY);
		S.config.panelX = x;
		S.config.panelY = y;
		root.style.left = x + 'px';
		root.style.top = y + 'px';
		root.style.bottom = 'auto';
	}

	function field(label, value, onChange, type) {
		const row = document.createElement('div');
		row.style.cssText = 'display:flex;gap:6px;align-items:center;margin-bottom:6px';
		const text = document.createElement('span');
		text.textContent = label;
		text.style.cssText = 'flex:0 0 48px;opacity:0.75';
		const input = document.createElement('input');
		input.type = type || 'text';
		input.value = value;
		input.style.cssText = 'flex:1 1 auto;min-width:0;background:rgba(255,255,255,0.08);'
			+ 'border:1px solid rgba(255,255,255,0.15);border-radius:4px;color:#e8e8ea;'
			+ 'padding:2px 6px;font:inherit';
		input.addEventListener('change', () => onChange(input.value));
		row.appendChild(text);
		row.appendChild(input);
		return row;
	}

	function button(label, onClick) {
		const b = document.createElement('button');
		b.textContent = label;
		b.style.cssText = 'background:rgba(255,255,255,0.10);border:1px solid rgba(255,255,255,0.18);'
			+ 'color:#e8e8ea;border-radius:4px;padding:3px 10px;cursor:pointer;font:inherit';
		b.addEventListener('click', onClick);
		return b;
	}

	function renderPanel() {
		if (!S.panelBody) {
			return;
		}
		const body = S.panelBody;
		body.textContent = '';

		body.appendChild(field('地址', S.config.address, (v) => {
			S.config.address = v.trim() || '127.0.0.1';
			saveConfig();
		}));
		body.appendChild(field('端口', String(S.config.port), (v) => {
			const n = Number(v);
			S.config.port = Number.isFinite(n) && n > 0 && n < 65536 ? Math.trunc(n) : 35671;
			saveConfig();
			renderPanel();
		}, 'number'));

		const row = document.createElement('div');
		row.style.cssText = 'display:flex;gap:6px;margin:8px 0';
		if (S.connected) {
			row.appendChild(button('断开', () => disconnect(true)));
		} else {
			row.appendChild(button('连接', () => {
				// 首次连接必须由用户手势触发：LNA 的权限请求要求这样
				connect();
			}));
		}
		row.appendChild(button('打开目录说明', () => {
			window.alert('端口在游戏的 HUD 或 logs/latest.log 里（搜索「远程调试」）。\n'
				+ '首次连接浏览器会询问是否允许访问本地网络，选「允许」。');
		}));
		body.appendChild(row);

		// 画面：捕获的是系统窗口，与连接状态无关——断线后画面仍在，用户自己决定何时停
		if (STAGE_VIEW) {
			const viewRow = document.createElement('div');
			viewRow.style.cssText = 'display:flex;gap:6px;margin:8px 0';
			viewRow.appendChild(button(STAGE_VIEW.isCapturing() ? '停止捕获' : '开始捕获', async () => {
				if (STAGE_VIEW.isCapturing()) {
					STAGE_VIEW.stop();
					S.viewError = '';
				} else {
					// 必须由用户手势触发：页面加载时自动调用会被浏览器拒绝
					const result = await STAGE_VIEW.start();
					S.viewError = result.ok ? '' : result.error;
					if (!result.ok) {
						console.warn('[turbomc] 画面捕获失败：' + result.error);
					}
				}
				renderPanel();
			}));
			viewRow.appendChild(button(STAGE_VIEW.getMode() === 'video' ? '切到舞台' : '切到画面', () => {
				STAGE_VIEW.toggleMode();
				renderPanel();
			}));
			body.appendChild(viewRow);
		}

		const status = document.createElement('div');
		status.style.cssText = 'white-space:pre-wrap;word-break:break-all;opacity:0.9';
		status.textContent = statusText();
		S.panelStatus = status;
		S.panelConnected = S.connected;
		body.appendChild(status);
	}

	function statusText() {
		if (S.connected && S.welcome) {
			return '● 已连接 ' + (S.welcome.instance || '?')
				+ '\n  世界 ' + (S.welcome.world || '—')
				+ ' · ' + S.tick + ' tick'
				+ (S.snapshot ? '\n  (' + S.snapshot.x + ', ' + S.snapshot.y + ', ' + S.snapshot.z + ')' : '')
				+ (S.schemaSeen !== undefined && S.schemaSeen !== SNAP_SCHEMA
					? '\n  ⚠ 客户端快照版本旧（' + (S.schemaSeen || 0) + ' < ' + SNAP_SCHEMA
						+ '）：请更新模组，否则新积木恒为空'
					: '')
				+ (S.lastHat ? '\n  ' + S.lastHat : '')
				+ viewLine();
		}
		if (S.ws) {
			return '○ 连接中…';
		}
		return '○ 未连接\n  端口见游戏 HUD 或 logs/latest.log';
	}

	/** 画面那一行：失败原因优先，其次显示捕获源分辨率。 */
	function viewLine() {
		if (!STAGE_VIEW) {
			return '';
		}
		if (S.viewError) {
			return '\n  画面：' + S.viewError;
		}
		if (!STAGE_VIEW.isCapturing()) {
			return '';
		}
		const source = STAGE_VIEW.source();
		const size = source && source.width
			? source.width + '×' + source.height + '@' + Math.round(source.frameRate || 0)
			: '已捕获';
		return '\n  画面：' + size + (STAGE_VIEW.getMode() === 'stage' ? '（已切到舞台）' : '');
	}

	function refreshStatus() {		if (!S.panelBody || !S.panel || S.panel.style.display === 'none') {
			return;
		}
		// 只有「连接状态变化」才重建控件。快照每秒到这里 20 次，若每次都重建，
		// 按钮会在 mousedown 与 mouseup 之间被换掉——click 不触发，点「断开」就像没反应；
		// 更糟的是按下时是「断开」、抬起时那个位置已经变成「连接」，于是反而连了回去。
		// 输入框同理：每帧重建会让地址/端口根本敲不进去。
		if (!S.panelStatus || S.panelConnected !== S.connected) {
			renderPanel();
			return;
		}
		S.panelStatus.textContent = statusText();
	}

	function togglePanel() {
		const root = ensurePanel();
		if (root.style.display === 'none') {
			root.style.display = 'block';
			renderPanel();
		} else {
			root.style.display = 'none';
		}
	}

	// ---- 连接 ----

	function connect() {
		disconnect(false);
		const url = 'ws://' + S.config.address + ':' + S.config.port;
		let ws;
		try {
			ws = new WebSocket(url);
		} catch (e) {
			console.warn('[turbomc] 地址无效：' + url);
			return;
		}
		S.ws = ws;
		S.reconnectWanted = true;

		ws.onopen = () => {
			ws.send(JSON.stringify({t: 'hello', v: 1, client: 'turbowarp'}));
		};
		ws.onmessage = (event) => {
			let message;
			try {
				message = JSON.parse(event.data);
			} catch (e) {
				return;
			}
			handleMessage(message);
		};
		ws.onerror = () => {
			// 具体原因（LNA 未授权 / 端口未监听 / Origin 被拒）由浏览器控制台给出
			console.warn('[turbomc] 连接失败：' + url + '（常见原因：本地网络未授权、端口不对、Origin 被拒）');
		};
		ws.onclose = () => {
			const wasConnected = S.connected;
			S.connected = false;
			S.welcome = null;
			S.ws = null;
			rejectAllPending();
			refreshStatus();
			if (wasConnected) {
				console.info('[turbomc] 连接已断开');
			}
			scheduleReconnect();
		};
		refreshStatus();
	}

	/**
	 * @param {boolean} byUser 用户主动断开时为 true：不再自动重连
	 */
	function disconnect(byUser) {
		if (byUser) {
			S.reconnectWanted = false;
		}
		if (S.reconnectTimer) {
			clearTimeout(S.reconnectTimer);
			S.reconnectTimer = null;
		}
		const ws = S.ws;
		S.ws = null;
		S.connected = false;
		S.welcome = null;
		if (ws) {
			try {
				ws.onclose = null;
				ws.close();
			} catch (e) {
				// 忽略
			}
		}
		rejectAllPending();
		refreshStatus();
	}

	// ---- 停止链路 ----

	/**
	 * 订阅 VM 的「停止全部」。
	 *
	 * <p>点红方块后，线程在浏览器侧清空；但游戏侧按住的键、寻路会话、待发聊天没人收，
	 * 人物会继续往前走。必须主动通知服务端收手（ARCHITECTURE.md §7.1）。</p>
	 *
	 * <p>绿旗启动前 VM 自己也会 `stopAll`，这里同样上送——语义上没错：上一次运行的
	 * 动作不该留到新一次运行里。</p>
	 */
	function hookStopAll() {
		const runtime = Scratch.vm && Scratch.vm.runtime;
		if (!runtime || typeof runtime.on !== 'function' || runtime.__tmcStopHooked) {
			return;
		}
		runtime.__tmcStopHooked = true;
		runtime.on('PROJECT_STOP_ALL', () => {
			// 先清本地：挂起的 Promise 不结束，线程会永远停在 PROMISE_WAIT，
			// 表现是「点了红停止，脚本还在转」
			rejectAllPending();
			if (!S.connected || !S.ws) {
				return;
			}
			try {
				S.ws.send(JSON.stringify({t: 'stop', reason: 'user'}));
			} catch (e) {
				// 连接恰好断了，忽略
			}
		});
	}

	function scheduleReconnect() {
		if (!S.reconnectWanted || !S.config.autoReconnect || S.reconnectTimer) {
			return;
		}
		S.reconnectTimer = setTimeout(() => {
			S.reconnectTimer = null;
			if (S.reconnectWanted && !S.connected) {
				connect();
			}
		}, RECONNECT_MS);
	}

	// ---- 消息 ----

	function handleMessage(message) {
		switch (message.t) {
			case 'welcome':
				S.welcome = message;
				S.connected = true;
				// VM 可能比扩展晚就绪（沙箱/加载顺序），每次连上都补挂一次，函数内部幂等
				hookStopAll();
				checkCoverage(message);
				refreshStatus();
				return;
			case 'snap':
				S.snapshot = message;
				S.tick = typeof message.tick === 'number' ? message.tick : S.tick + 1;
				S.lastSnapAt = performance.now();
				// 版本不一致 = 客户端是旧的。字段只增不减，所以不会报错，
				// 只会让新积木恒为假/为空——不显式提示的话，看起来就是「积木坏了」
				const schema = typeof message.schema === 'number' ? message.schema : 0;
				if (schema !== SNAP_SCHEMA && S.schemaSeen !== schema) {
					S.schemaSeen = schema;
					console.warn('[turbomc] 快照版本不一致：客户端 schema=' + schema
						+ '，扩展期望 ' + SNAP_SCHEMA + '——请更新模组客户端');
				}
				advancePending();
				refreshStatus();
				return;
			case 'ret':
				handleReturn(message);
				return;
			case 'event':
				dispatchHat(message);
				return;
			case 'evicted':
				// 被另一个标签页接管：停止重连，避免两边来回抢占
				S.reconnectWanted = false;
				console.warn('[turbomc] 已被其他标签页接管，停止自动重连');
				return;
			case 'stopped':
				rejectAllPending();
				return;
			default:
				return;
		}
	}

	/** 版本自检：opcode 集合与服务端不一致时提示（与游戏内 HUD 同一套判断）。 */
	function checkCoverage(welcome) {
		if (!Array.isArray(welcome.opcodes)) {
			return;
		}
		const expected = new Set(welcome.opcodes);
		const actual = new Set(ext.getInfo().blocks
			.filter((b) => b.opcode && b.blockType !== 'button')
			.map((b) => meta.id + '_' + b.opcode));
		const missing = [...expected].filter((op) => !actual.has(op));
		const extra = [...actual].filter((op) => !expected.has(op));
		if (missing.length || extra.length) {
			console.warn('[turbomc] 版本不一致：缺少 ' + missing.length
				+ ' 块（模组较新），多出 ' + extra.length + ' 块（扩展较新）');
			if (missing.length) console.warn('[turbomc] 缺少的 opcode：', missing);
			if (extra.length) console.warn('[turbomc] 多出的 opcode：', extra);
		}
	}

	function handleReturn(message) {
		const p = S.pending.get(message.id);
		if (!p) {
			return;
		}
		// poll 的回包只有 done
		if (typeof message.done === 'boolean') {
			if (message.done) {
				finish(p, p.value);
			}
			return;
		}
		if (!message.ok) {
			console.warn('[turbomc] 调用失败 ' + (message.code || '') + '：' + (message.err || ''));
			finish(p, undefined);
			return;
		}
		p.value = message.v === undefined ? null : message.v;
		p.tick0 = S.tick;
		p.wait = Number(message.wait) || 0;
		p.check = message.check || '';
		// 上限必须留到 advancePending 里用：check 型等待结束与否由服务端判定，
		// 万一那边卡住（视角冻结、暂停菜单、会话被抢），没有这条就只能等 CallTimeout 60 秒。
		// 游戏内 vm-bootstrap 一直用这个值兜底（__tmcPending.push 的 until），两侧必须同一个上限。
		p.limit = Number(message.limit) || 0;
		if (!p.wait && !p.check) {
			finish(p, p.value);
		}
	}

	/**
	 * 每收到一帧快照推进一步等待。
	 *
	 * <p>这里就是 §7.3 的时钟：{@code wait} 按 tick 号推进，{@code check} 每帧发一次 poll，
	 * {@code limit} 是 check 型等待的兜底上限（与游戏内同一口径）。
	 * 不用 setTimeout——它的实际间隔是 52–56ms，跑几分钟会明显漂移。</p>
	 */
	function advancePending() {
		if (!S.pending.size || !S.ws) {
			return;
		}
		for (const [id, p] of S.pending) {
			if (p.wait > 0 && S.tick >= p.tick0 + p.wait) {
				finish(p, p.value);
				continue;
			}
			// 先判上限再发 poll：到点就不再追问，省掉一次没有意义的往返
			if (p.limit > 0 && S.tick >= p.tick0 + p.limit) {
				finish(p, p.value);
				continue;
			}
			if (p.check && p.pollTick !== S.tick) {
				p.pollTick = S.tick;
				try {
					S.ws.send(JSON.stringify({t: 'poll', id: id, key: p.check}));
				} catch (e) {
					finish(p, undefined);
				}
			}
		}
	}

	function finish(p, value) {
		if (p.settled) {
			return;
		}
		p.settled = true;
		S.pending.delete(p.id);
		p.resolve(value);
	}

	function rejectAllPending() {
		for (const [, p] of S.pending) {
			if (!p.settled) {
				p.settled = true;
				p.resolve(undefined);
			}
		}
		S.pending.clear();
	}

	// ---- 帽子派发 ----

	/** 帽子字段名 → 事件里的数据字段。名字不同，必须显式映射，不能靠大小写猜。 */
	const FIELD_SOURCE = {SCREEN: 'screen', KEY_OPTION: 'key', MATCH: 'text'};

	function dispatchHat(event) {
		const hats = S.welcome && S.welcome.hats;
		const runtime = Scratch.vm && Scratch.vm.runtime;
		if (!Array.isArray(hats) || !runtime) {
			return;
		}
		const data = event.data || {};
		for (const rule of hats) {
			if (rule.ev !== event.name) {
				continue;
			}
			if (!rule.match) {
				pushHatsMatching(runtime, rule.op, null);
				continue;
			}
			const parts = String(rule.match).split(':');
			const kind = parts[0];
			const name = parts[1];
			const value = data[FIELD_SOURCE[name] || name.toLowerCase()];
			if (kind === 'field') {
				pushHatsMatching(runtime, rule.op, makeFields(name, value));
				if (rule.alsoAny) {
					pushHatsMatching(runtime, rule.op, makeFields(name, 'any'));
				}
			} else if (kind === 'input' && rule.contains) {
				startInputMatches(runtime, rule.op, name, value);
			}
		}
	}

	function makeFields(name, value) {
		const fields = {};
		fields[name] = value === undefined || value === null ? '' : String(value);
		return fields;
	}

	/**
	 * 按字段匹配启动帽子。
	 *
	 * <p><b>为什么不直接用 {@code runtime.startHats}</b>：它会先 {@code stepThread} 再
	 * {@code goToNextBlock}，而第一步就把帽子块当作普通积木执行了一次。扩展帽子没有实现体，
	 * 返回 {@code undefined}，于是走 {@code n || retireThread(t)} 分支——线程当场退役。
	 * 症状是「帽子亮了，后面的积木一块都不跑」，而且内置键帽（{@code event_whenkeypressed}
	 * 是 edge-activated）不受影响，更加让人以为是自己的脚本写错了。</p>
	 *
	 * <p>所以这里自己遍历顶层块、匹配字段、{@code _pushThread} 之后立刻
	 * {@code goToNextBlock()}，让线程第一步就落在帽子的下一块上。与游戏内
	 * {@code __tmcStartHatField} 同一套做法。</p>
	 */
	function pushHatsMatching(runtime, opcode, fields) {
		const started = [];
		let matched = 0;
		for (const target of runtime.targets) {
			const blocks = target.blocks;
			if (!blocks || !blocks._blocks) {
				continue;
			}
			for (const id in blocks._blocks) {
				const block = blocks._blocks[id];
				if (!block || block.opcode !== opcode || !block.topLevel) {
					continue;
				}
				if (fields && !fieldsMatch(block, fields)) {
					continue;
				}
				matched++;
				if (isBusy(runtime, target, id)) {
					continue;
				}
				pushHatThread(runtime, target, id, started);
			}
		}
		// 面板上留一行：命中几顶、起了几个线程。分不清「没匹配上」与「起了但没跑」时，
		// 这行是唯一能在游戏画面外看到的信息
		noteHat('帽子 ' + opcode.replace(/^turbomc_/, '') + '：命中 ' + matched
			+ ' · 起线程 ' + started.length
			+ ' · 当前线程 ' + (Array.isArray(runtime.threads) ? runtime.threads.length : '?')
			+ (matched > started.length ? '（其余在跑或帽子是空的）' : ''));
		return started;
	}

	/** 把最近一次帽子派发结果写进面板状态行。 */
	function noteHat(text) {
		S.lastHat = text;
		refreshStatus();
	}

	/** 字段比较：沿用 {@code startHats} 的规则——两边都转大写再比。 */
	function fieldsMatch(block, fields) {
		for (const name in fields) {
			const actual = blockField(block, name);
			if (actual === null) {
				return false;
			}
			if (actual.toUpperCase() !== String(fields[name]).toUpperCase()) {
				return false;
			}
		}
		return true;
	}

	/** 读帽子字段：兼容运行期 {@code {name,value,id}} 与原始 JSON {@code [value, id]}。 */
	function blockField(block, name) {
		const field = block && block.fields ? block.fields[name] : null;
		if (!field) {
			return null;
		}
		if (typeof field === 'object' && field.value !== undefined) {
			return String(field.value);
		}
		if (Array.isArray(field) && field.length >= 1) {
			return String(field[0]);
		}
		return null;
	}

	/**
	 * 起一个帽子线程，**直接把栈顶放在帽子的下一块**。
	 *
	 * <p>踩过两次的坑：用 {@code runtime.startHats} 或在帽子块上 {@code _pushThread}
	 * 再 {@code goToNextBlock}，这份 VM 里都会先把帽子当普通积木执行一次。扩展帽子没有实现体，
	 * 返回 {@code undefined}，于是 {@code retireThread}——现象是「帽子高亮了，后面的积木不动」。</p>
	 *
	 * <p>所以这里绕过帽子本身：找到它的 {@code next}，从那一块起线程。空帽子（只有帽子、
	 * 下面没有积木）本来就没有可执行的东西，直接跳过。</p>
	 */
	function pushHatThread(runtime, target, blockId, out, chatText) {
		const nextId = nextBlockId(target, blockId);
		if (!nextId) {
			return null;
		}
		const ret = runtime._pushThread(nextId, target);
		// 兼容被包过一层的 _pushThread：可能不返回线程，取 threads 末尾那个
		const thread = ret && typeof ret === 'object'
			? ret
			: (Array.isArray(runtime.threads) ? runtime.threads[runtime.threads.length - 1] : null);
		// 聊天触发的线程带一份自己的消息副本：全局槽可能已经被后来的消息改写，
		// 「触发我的聊天」读的必须是这一条（与游戏内 vm-bootstrap 同一做法）
		if (thread && chatText !== undefined && chatText !== null) {
			thread.tmcChat = chatText;
		}
		if (out) {
			out.push(thread || true);
		}
		return thread;
	}

	/** 取帽子下面挂的第一块；没有则返回 null。 */
	function nextBlockId(target, blockId) {
		const blocks = target && target.blocks;
		if (!blocks) {
			return null;
		}
		if (typeof blocks.getNextBlock === 'function') {
			return blocks.getNextBlock(blockId) || null;
		}
		const block = blocks._blocks ? blocks._blocks[blockId] : null;
		return block && block.next ? block.next : null;
	}

	/**
	 * 按输入内容过滤后启动帽子。
	 *
	 * <p>{@code startHats} 的字段匹配只认 field，而「当聊天包含」的关键词是 input，
	 * 传 matchFields 会抛错。因此自行遍历顶层块、读输入、命中后 {@code _pushThread}。
	 * 这段与游戏内 {@code vm-bootstrap.js} 的 {@code __tmcStartHatFiltered} 同一套做法。</p>
	 */
	function startInputMatches(runtime, opcode, inputName, text) {
		const needle = text === undefined || text === null ? '' : String(text);
		let matched = 0;
		let started = 0;
		for (const target of runtime.targets) {
			const blocks = target.blocks;
			if (!blocks || !blocks._blocks) {
				continue;
			}
			for (const id in blocks._blocks) {
				const block = blocks._blocks[id];
				if (!block || block.opcode !== opcode || !block.topLevel) {
					continue;
				}
				// 读不出关键词（接了表达式）或关键词为空：按「匹配全部」处理，
				// 与玩家聊天通道的历史行为一致
				const keyword = resolveKeyword(runtime, target, blocks, block, inputName);
				if (keyword.known && keyword.value !== '' && needle.indexOf(keyword.value) < 0) {
					continue;
				}
				matched++;
				if (isBusy(runtime, target, id)) {
					continue;
				}
				// 线程起在帽子下一块：留在帽子上会执行帽子本身并立刻退役
				if (pushHatThread(runtime, target, id, null, needle)) {
					started++;
				}
			}
		}
		noteHat('帽子 ' + opcode.replace(/^turbomc_/, '') + '：命中 ' + matched
			+ ' · 起线程 ' + started
			+ ' · 当前线程 ' + (Array.isArray(runtime.threads) ? runtime.threads.length : '?'));
	}

	/**
	 * 读输入槽的字面量：读不出（接了变量/表达式）返回 {@code null}，空槽返回空串。
	 *
	 * <p><b>这是踩过的坑</b>：sb3 的字面量是嵌套形态 {@code [1, [10, "文字"]]}，
	 * 只看 {@code input[1]} 是不是字符串，会把**所有字面量都判成"读不出来"**，
	 * 于是关键词过滤形同虚设——「当聊天包含 你好」在任何一条聊天上都会触发。
	 * 这里的三种形态与游戏内 {@code __tmcReadInput} 一一对应。</p>
	 */
	function readInput(blocks, block, name) {
		const direct = fieldText(block, name);
		if (direct !== null) {
			return direct;
		}
		const input = block.inputs && block.inputs[name];
		if (!input) {
			return '';
		}
		// 运行期形态 {name, block, shadow}：block 与 shadow 不同说明槽里接了积木
		if (input.block !== undefined) {
			if (input.block !== input.shadow) {
				return null;
			}
			return shadowText(blocks, input.block);
		}
		// 原始 JSON 形态 [1, [10, "文字"]] 或 [1, "blockId"]
		const value = input[1];
		if (typeof value === 'string') {
			return shadowText(blocks, value);
		}
		if (value && value.length >= 2) {
			return String(value[1] === null || value[1] === undefined ? '' : value[1]);
		}
		return null;
	}

	/** 字段文本：兼容运行期的 {name,value,id} 与原始 JSON 的 [value, id]。 */
	function fieldText(block, name) {
		const field = block && block.fields ? block.fields[name] : null;
		if (!field) {
			return null;
		}
		if (typeof field === 'object' && field.value !== undefined) {
			return String(field.value);
		}
		if (Array.isArray(field) && field.length >= 1) {
			return String(field[0]);
		}
		return null;
	}

	/** 替身积木（只有字段没有操作）里取第一段文本。 */
	function shadowText(blocks, id) {
		const shadow = id && blocks && blocks._blocks ? blocks._blocks[id] : null;
		if (!shadow || !shadow.fields) {
			return '';
		}
		for (const key in shadow.fields) {
			const value = fieldText(shadow, key);
			if (value !== null) {
				return value;
			}
		}
		return '';
	}

	/** 槽里接的那块积木（可能是变量/表达式 reporter）；只有替身时返回 null。 */
	function reporterBlock(blocks, block, inputName) {
		const input = block.inputs && block.inputs[inputName];
		if (!input) {
			return null;
		}
		let id = null;
		if (input.block !== undefined) {
			id = input.block;
		} else if (typeof input[1] === 'string') {
			id = input[1];
		}
		if (!id || !blocks || !blocks._blocks) {
			return null;
		}
		return blocks._blocks[id] || null;
	}

	/** 读变量当前值；作用域先本角色再舞台（sb3 里变量通常定义在舞台上）。 */
	function variableText(runtime, target, variableField) {
		let id = null;
		if (variableField && typeof variableField === 'object' && variableField.id !== undefined) {
			id = variableField.id;
		} else if (Array.isArray(variableField) && variableField.length >= 2) {
			id = variableField[1];
		}
		if (!id) {
			return null;
		}
		const lookup = (scope) => {
			const bag = scope && scope.variables;
			return bag && bag[id] !== undefined ? bag[id] : null;
		};
		let value = lookup(target);
		if (value === null) {
			const stage = (runtime.targets || []).find((t) => t.isStage);
			value = lookup(stage);
		}
		return value === null ? null : String(value);
	}

	/** 关键词：字面量优先，其次变量；都读不出时 known=false（由调用方按「匹配全部」处理）。 */
	function resolveKeyword(runtime, target, blocks, block, inputName) {
		const literal = readInput(blocks, block, inputName);
		if (literal !== null) {
			return {known: true, value: literal};
		}
		const reporter = reporterBlock(blocks, block, inputName);
		if (reporter && reporter.opcode === 'data_variable') {
			const value = variableText(runtime, target, reporter.fields && reporter.fields.VARIABLE);
			if (value !== null) {
				return {known: true, value: value};
			}
		}
		return {known: false, value: ''};
	}

	function isBusy(runtime, target, blockId) {
		// 线程是从帽子的下一块起的，topBlock 记的是那一块——两个都要认，
		// 否则同一顶帽子会被连发的聊天叠出好几份线程
		const bodyId = nextBlockId(target, blockId);
		for (const thread of runtime.threads) {
			if (thread.target !== target) {
				continue;
			}
			if (thread.topBlock !== blockId && thread.topBlock !== bodyId) {
				continue;
			}
			if (thread.status !== 4) {
				return true;
			}
		}
		return false;
	}

	// ---- 积木入口 ----

	/**
	 * 快照读数表：由 tools/build-extension.js 从 blocks.data.js 的 read 声明生成，勿手改。
	 * 键是带前缀的 opcode，值是 (snapshot, argv, util) => value。
	 * 新增读数积木只在 blocks.data.js 写一处；这里缺一条，构建会直接失败。
	 */
	const READERS = {
		'turbomc_mouseDown': (s, a) => {
				const button = String((a && a[0]) || 'left').trim().toLowerCase();
				if (!s || !s.mouse) {
					return false;
				}
				if (button === 'any') {
					return !!(s.mouse.left || s.mouse.right || s.mouse.middle);
				}
				return !!s.mouse[button];
			},
		'turbomc_isMining': (s) => !!(s && s.mining && s.mining.active),
		'turbomc_miningProgress': (s) => (s && s.mining ? s.mining.progress : -1),
		'turbomc_containerSlots': (s) => (s && s.ui ? s.ui.slots : 0),
		'turbomc_slotField': (s, a) => {
				const index = Number((a && a[0]) === undefined ? 0 : a[0]);
				const field = String((a && a[1]) || 'id').trim().toLowerCase();
				const items = s && s.ui && Array.isArray(s.ui.items) ? s.ui.items : [];
				const item = items.find((entry) => entry.slot === index) || null;
				if (field === 'amount') {
					return item ? item.count : 0;
				}
				if (field === 'max_durability') {
					return item ? item.max_damage : 0;
				}
				if (field === 'damage') {
					return item ? item.damage : 0;
				}
				if (field === 'custom_name') {
					return item ? item.custom_name : '';
				}
				return item ? item.id : '';
			},
		'turbomc_screenJson': (s) => (s && s.ui ? JSON.stringify(s.ui) : '{}'),
		'turbomc_firstSlotOf': (s, a) => {
				const want = itemKey(a && a[0]);
				const items = s && s.ui && Array.isArray(s.ui.items) ? s.ui.items : [];
				if (!want) {
					return -1;
				}
				const hit = items.find((entry) => entry.id === want);
				return hit ? hit.slot : -1;
			},
		'turbomc_health': (s) => (s ? s.hp : 0),
		'turbomc_food': (s) => (s ? s.food : 0),
		'turbomc_xpLevel': (s) => (s ? s.xpLevel : 0),
		'turbomc_onGround': (s) => !!(s && s.onGround),
		'turbomc_pos': (s, a) => (s ? s[a[0]] : 0),
		'turbomc_selectedSlot': (s) => (s ? s.slot : 0),
		'turbomc_heldItemField': (s, a) => {
				const field = String((a && a[0]) || 'id').trim().toLowerCase();
				const hand = s && s.hand ? s.hand : {id: '', count: 0, maxDurability: 0, durability: 0};
				if (field === 'count') {
					return hand.count;
				}
				if (field === 'max_durability') {
					return hand.maxDurability;
				}
				if (field === 'durability') {
					return hand.durability;
				}
				return hand.id;
			},
		'turbomc_inventoryJson': (s) => JSON.stringify(s && Array.isArray(s.inv) ? s.inv : []),
		'turbomc_inventoryFull': (s) => {
				if (!s || !Array.isArray(s.inv)) {
					return false;
				}
				const filled = new Set(s.inv.map((item) => item.slot));
				for (let i = 0; i < 36; i++) {
					if (!filled.has(i)) {
						return false;
					}
				}
				return true;
			},
		'turbomc_playerJson': (s) => JSON.stringify(s && s.me ? s.me : {}),
		'turbomc_itemCount': (s, a) => {
				const want = itemKey(a && a[0]);
				if (!want || !s || !Array.isArray(s.inv)) {
					return 0;
				}
				let total = 0;
				for (const item of s.inv) {
					if (item.id === want) {
						total += item.count;
					}
				}
				return total;
			},
		'turbomc_isDay': (s) => !!(s && s.isDay),
		'turbomc_dimension': (s) => (s ? s.dim : ''),
		'turbomc_biome': (s) => (s ? s.biome : ''),
		'turbomc_inWater': (s) => !!(s && s.inWater),
		'turbomc_inLava': (s) => !!(s && s.inLava),
		'turbomc_blockInFront': (s) => !!(s && s.aim && s.aim.has),
		'turbomc_blockBelow': (s) => (s ? s.below : ''),
		'turbomc_playersJson': (s) => JSON.stringify(s && Array.isArray(s.players) ? s.players : []),
		'turbomc_snapshotKeys': (s, a) => {
				const kind = String((a && a[0]) || 'player').trim().toLowerCase();
				const table = S.welcome && S.welcome.snapshotKeys ? S.welcome.snapshotKeys : null;
				if (!table) {
					return '[]';
				}
				return JSON.stringify(Array.isArray(table[kind]) ? table[kind] : []);
			},
		'turbomc_worldJson': (s) => JSON.stringify(s && s.worldInfo ? s.worldInfo : {}),
		'turbomc_frontBlock': (s) => (s && s.aim ? s.aim.block : ''),
		'turbomc_aimPos': (s, a) => (s && s.aim ? s.aim[a[0]] : 0),
		'turbomc_ping': (s) => (s ? s.ping : 0),
		'turbomc_nearestEntityField': (s, a) => nearestEntityField(s, a),
		'turbomc_entityCount': (s, a) => countEntities(s, entityFilter(a && a[0])),
		'turbomc_entitiesJson': (s, a) => entitiesJson(s, a),
		'turbomc_lastChat': (s) => (s && s.chat ? s.chat.last : ''),
		'turbomc_chatHistory': (s, a) => {
				const count = Math.max(1, Math.min(32, Number((a && a[0]) === undefined ? 10 : a[0]) || 0));
				const history = s && s.chat && Array.isArray(s.chat.history) ? s.chat.history : [];
				return JSON.stringify(history.slice(Math.max(0, history.length - count)));
			},
		'turbomc_triggerChat': (s, a, util) => {
				if (util && util.thread && util.thread.tmcChat !== undefined && util.thread.tmcChat !== null) {
					return String(util.thread.tmcChat);
				}
				return '';
			},
		'turbomc_aiReply': (s, a) => {
				const session = String((a && a[0]) || '').trim();
				const table = s && s.ai ? s.ai : null;
				if (!table) {
					return '';
				}
				const key = session || 'default';
				return table[key] === undefined || table[key] === null ? '' : String(table[key]);
			},
	};

	/** 与游戏内 {@code ENTITY_SEARCH_RANGE} 一致：两个统计积木的默认搜索半径。 */
	const ENTITY_RANGE = 16;
	/** 快照只带这么远的实体（服务端 {@code SnapshotPublisher.ENTITY_RADIUS}）。 */
	const ENTITY_SNAPSHOT_RADIUS = 32;

	/**
	 * 实体过滤：{@code any} / {@code hostile} / {@code player} / 具体实体 id。
	 *
	 * <p>与游戏内 {@code EntityFilter.parse} 同一套判据；空串按「匹配不到」处理，
	 * 不回退成 any——参数没送到时宁可判否，也不要安静地匹配全部。</p>
	 */
	function entityFilter(raw) {
		const text = String(raw === undefined || raw === null ? '' : raw).trim().toLowerCase();
		if (text === 'any') {
			return {kind: 'any'};
		}
		if (text === 'hostile') {
			return {kind: 'hostile'};
		}
		if (text === 'player') {
			return {kind: 'player'};
		}
		return {kind: 'type', id: text.indexOf(':') >= 0 ? text : (text ? 'minecraft:' + text : '')};
	}

	function entityEntries(s) {
		return s && Array.isArray(s.ents) ? s.ents : [];
	}

	/**
	 * 是否在「盒范围」内。
	 *
	 * <p>游戏内是 {@code boundingBox.expand(r)} 的盒判定，不是球；这里用三轴分量还原，
	 * 差别只出现在大体型实体贴边时，比按 {@code distance} 判球更接近原语义。</p>
	 */
	function inEntityBox(entry, s, range) {
		const px = s && typeof s.x === 'number' ? s.x : 0;
		const py = s && typeof s.y === 'number' ? s.y : 0;
		const pz = s && typeof s.z === 'number' ? s.z : 0;
		return Math.abs(entry.x - px) <= range
			&& Math.abs(entry.y - py) <= range
			&& Math.abs(entry.z - pz) <= range;
	}

	function entityMatches(entry, filter, s) {
		if (filter.kind === 'any') {
			return true;
		}
		if (filter.kind === 'player') {
			return entry.type === 'minecraft:player';
		}
		if (filter.kind === 'hostile') {
			// 「是不是主动怪」在快照里是单独的 uuid 表，不污染实体对象的键序
			return Array.isArray(s && s.hostile) && s.hostile.indexOf(entry.uuid) >= 0;
		}
		return filter.id !== '' && entry.type === filter.id;
	}

	function countEntities(s, filter) {
		let count = 0;
		for (const entry of entityEntries(s)) {
			if (inEntityBox(entry, s, ENTITY_RANGE) && entityMatches(entry, filter, s)) {
				count++;
			}
		}
		return count;
	}

	/** 最近实体：快照已按距离升序，第一个命中的就是答案。 */
	function nearestEntityField(s, a) {
		const filter = entityFilter(a && a[0]);
		const field = String((a && a[1]) || 'distance').trim().toLowerCase();
		const hit = entityEntries(s).find((entry) => (
			inEntityBox(entry, s, ENTITY_RANGE) && entityMatches(entry, filter, s)));
		switch (field) {
			case 'x':
				return hit ? hit.x : NaN;
			case 'y':
				return hit ? hit.y : NaN;
			case 'z':
				return hit ? hit.z : NaN;
			case 'name':
				return hit ? hit.name : '';
			case 'health':
				return hit ? hit.health : 0;
			case 'max_health':
				return hit ? hit.max_health : 0;
			case 'type_id':
				return hit ? hit.type : '';
			default:
				return hit ? hit.distance : -1;
		}
	}

	/**
	 * 附近实体快照。半径与数量在远程模式有上限：快照只带 {@link ENTITY_SNAPSHOT_RADIUS} 格内的实体，
	 * 超出部分会被静默夹到上限（有效上限：半径 32 格、64 个，见 ARCHITECTURE.md §7.6）。
	 */
	function entitiesJson(s, a) {
		const filter = entityFilter(a && a[0]);
		const radius = Math.max(1, Math.min(64, Number((a && a[1]) === undefined ? ENTITY_RANGE : a[1]) || 0));
		const limit = Math.max(1, Math.min(256, Number((a && a[2]) === undefined ? 32 : a[2]) || 0));
		const effective = Math.min(radius, ENTITY_SNAPSHOT_RADIUS);
		const out = [];
		for (const entry of entityEntries(s)) {
			if (!inEntityBox(entry, s, effective) || !entityMatches(entry, filter, s)) {
				continue;
			}
			out.push(entry);
			if (out.length >= limit) {
				break;
			}
		}
		// 条目字段顺序与游戏内 {@code Snapshots.entityObject} 完全一致，stringify 即逐字相同
		return JSON.stringify(out);
	}

	/**
	 * 物品名归一成注册名：{@code stone} 与 {@code minecraft:stone} 视为同一个。
	 *
	 * <p>只做前缀补全，不查中文名——游戏内 {@code ItemIds.resolve} 认中文，
	 * 但浏览器侧没有那份表；用中文名写法时这里会匹配不到（见 ARCHITECTURE.md §7.6 已知限制）。</p>
	 */
	function itemKey(raw) {
		const text = String(raw === undefined || raw === null ? '' : raw).trim().toLowerCase();
		if (!text) {
			return '';
		}
		return text.indexOf(':') >= 0 ? text : 'minecraft:' + text;
	}

	function defaultValue(kind) {
		if (kind === 'boolean') {
			return false;
		}
		if (kind === 'reporter') {
			return '';
		}
		// command：返回 undefined，scratch-vm 不会把 undefined 当成 Promise 等待
		return undefined;
	}

	/**
	 * 所有积木的统一入口。
	 *
	 * @param {string} opcode 完整 opcode（{@code turbomc_xxx}）
	 * @param {Array} argv 有序参数
	 * @param {string} kind command / reporter / boolean
	 * @param {Function} fallback 未连接时的占位行为（原来的预览）
	 */
	ext.__tmc = function (opcode, argv, kind, fallback, util) {
		// 读写当前 VM 列表的两块：碰的是浏览器自己的列表对象，与连接状态无关。
		//
		// 必须排在「未连接 → fallback」之前。放在后面时，导入扩展、尚未连接游戏
		// 的这段时间里，读列表恒为 '[]'、写列表什么都不做——与 JSON 纯计算积木
		// 之前踩的是同一个坑（它们在构建期已改成直调，不再经过本函数）。
		if (opcode === 'turbomc_jsonVmGetList') {
			// 列表不存在给空串，存在但为空给 "[]"——两者语义不同，不能合并
			const found = findVmList(String((argv || [])[0] === undefined ? '' : argv[0]));
			return found ? JSON.stringify(found.value || []) : '';
		}
		if (opcode === 'turbomc_jsonVmSetList') {
			writeVmList(String((argv || [])[0] === undefined ? '' : argv[0]),
				String((argv || [])[1] === undefined ? '' : argv[1]));
			// 命令积木返回 undefined：VM 不会把它当 Promise 去等
			return undefined;
		}
		if (!S.connected) {
			if (typeof fallback === 'function') {
				return fallback.call(this);
			}
			return defaultValue(kind);
		}
		if (kind === 'reporter' || kind === 'boolean') {
			const reader = READERS[opcode];
			if (!reader) {
				if (!S.missingReporters.has(opcode)) {
					S.missingReporters.add(opcode);
					console.warn('[turbomc] 远程模式暂不支持该读数积木：' + opcode + '（返回空值）');
				}
				return defaultValue(kind);
			}
			try {
				const value = reader(S.snapshot, argv, util);
				if (value === undefined || value === null) {
					return defaultValue(kind);
				}
				return kind === 'boolean' ? !!value : value;
			} catch (e) {
				return defaultValue(kind);
			}
		}
		return callRemote(opcode, argv);
	};

	/**
	 * 按名字找列表：先各角色，再兼容老的 {@code target.lists}。
	 *
	 * <p>与游戏内 {@code __tmcFindList} 同一套查找顺序——两边对「哪个列表」的判断必须一致，
	 * 否则同名列表在两份工程里指向不同对象。</p>
	 */
	function findVmList(name) {
		const runtime = Scratch.vm && Scratch.vm.runtime;
		if (!runtime || !Array.isArray(runtime.targets)) {
			return null;
		}
		for (const target of runtime.targets) {
			const vars = target.variables || {};
			for (const id in vars) {
				const item = vars[id];
				if (item && item.name === name && item.type === 'list') {
					return item;
				}
			}
			const lists = target.lists || {};
			for (const listId in lists) {
				const legacy = lists[listId];
				if (legacy && legacy.name === name) {
					return legacy;
				}
			}
		}
		return null;
	}

	/**
	 * 用 JSON 数组覆盖列表。
	 *
	 * <p>列表项只能是标量：对象/数组转成 JSON 文本、{@code null} 变空串。不这么转，
	 * 塞进去的对象只会变成 {@code [object Object]}，一进一出结构就没了。</p>
	 *
	 * <p>读写两块（{@code jsonVmGetList} / {@code jsonVmSetList}）都在本地做：它们操作的是
	 * 当前 VM 自己的列表，发到对面写没有意义。所以游戏内与浏览器走同一条路径，
	 * 转换规则只此一份（见 {@code json-lite.js}）。</p>
	 */
	function writeVmList(name, json) {
		const found = findVmList(name);
		if (!found) {
			return;
		}
		let value;
		try {
			value = JSON.parse(json);
		} catch (e) {
			return;
		}
		if (!Array.isArray(value)) {
			return;
		}
		found.value = value.map((item) => {
			if (item === null || item === undefined) {
				return '';
			}
			if (typeof item === 'object') {
				return JSON.stringify(item);
			}
			return item;
		});
	}

	function callRemote(opcode, argv) {
		const ws = S.ws;
		if (!ws || ws.readyState !== 1) {
			return Promise.resolve(undefined);
		}
		const id = String(++S.seq);
		return new Promise((resolve) => {
			const entry = {id: id, resolve: resolve, value: null, tick0: S.tick, wait: 0, check: '', limit: 0, settled: false, pollTick: -1};
			S.pending.set(id, entry);
			try {
				ws.send(JSON.stringify({t: 'call', id: id, op: opcode, argv: argv || []}));
			} catch (e) {
				finish(entry, undefined);
				return;
			}
			setTimeout(() => finish(entry, undefined), CALL_TIMEOUT_MS);
		});
	}

	/** 供积木按钮调用：打开/关闭连接面板。 */
	ext.openRemotePanel = function () {
		togglePanel();
		return '';
	};

	// 安装即挂上停止订阅：漏了它，「点红停止后人物还在走」会是最难查的一类现象
	hookStopAll();

	return {
		togglePanel: togglePanel,
		connect: connect,
		disconnect: disconnect,
		state: S
	};
}

	// 注入远程能力后再注册：installRemote 会给实例挂上 __tmc 与 openRemotePanel。
	// 未连接时 __tmc 一律走 fallback，所以这份产物同时是「普通编辑器」与「远程调试器」。
	const extension = new TurboMC();
	installRemote(Scratch, extension, {id: 'turbomc'});
	Scratch.extensions.register(extension);
})(Scratch);
