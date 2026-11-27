package xyz.turbomc.client.script.vm;

import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.runtime.ChatGate;
import xyz.turbomc.client.script.block.BlockRegistry;

/**
 * 「当聊天包含」的触发次数冒烟。
 *
 * <p>要回答三个问题：一条聊天会不会让同一个帽子跑多次；关键词过滤在
 * <b>玩家聊天通道</b>（{@code allowAnyKeyword = true}）与<b>服务端消息通道</b>
 * （{@code false}）下各自的行为；接在关键词槽里的变量能否被正确求值。</p>
 *
 * <p>scratch-vm 只对 {@code edgeActivated} 的帽子做逐帧检查，而扩展里三个事件帽子都显式声明了
 * {@code isEdgeActivated: false}，理论上只能由宿主的 {@code startHats} 拉起。这个用例用
 * 「计数变量 +1」把「跑了几次」变成可断言的事实。</p>
 *
 * <p>工程里有三顶帽子，各自一个计数器：</p>
 *
 * <ol>
 *   <li>关键词 {@code hello}（字面量）；</li>
 *   <li>关键词为空（任意匹配语义）；</li>
 *   <li>关键词是<b>舞台上的变量</b> {@code kw}，初值 {@code abc}。</li>
 * </ol>
 *
 * <p>仅手动运行：{@code ./gradlew vmChat}。</p>
 */
public final class ChatHatTest {
	private ChatHatTest() {
	}

	public static void main(String[] args) throws Exception {
		NativeRegistry.bootstrapOnce();
		BlockRegistry.bootstrapOnce();

		RhinoVm vm = RhinoVm.get();
		vm.setBudgetMs(4);
		vm.init();
		vm.loadProject(project());
		vm.greenFlag();
		for (int i = 0; i < 3; i++) {
			vm.tick(null);
		}

		// 1) 只认关键词：字面量命中，空关键词与变量都不该被拉起
		int literalStart = vm.startChatHat("say hello world", false);
		tick(vm, 30);
		int hits1 = num(vm, "hits");
		int blank1 = num(vm, "blankHits");
		int var1 = num(vm, "varHits");

		// 2) 只认关键词：谁都不命中 —— 空关键词帽子必须被跳过
		int noneStart = vm.startChatHat("nothing here", false);
		tick(vm, 30);
		int blank2 = num(vm, "blankHits");

		// 3) 只认关键词：变量求值为 abc，应当命中变量帽子
		int variableStart = vm.startChatHat("xx abc yy", false);
		tick(vm, 30);
		int var2 = num(vm, "varHits");

		// 4) 放行模式：空关键词帽子这时才允许启动
		int anyStart = vm.startChatHat("nothing here", true);
		tick(vm, 30);
		int blank3 = num(vm, "blankHits");

		// 5) 放行模式不等于「全都启动」：字面量不命中、变量不包含，仍只有空关键词帽子跑
		int anyStart2 = vm.startChatHat("zzz", true);
		tick(vm, 30);
		int hits = num(vm, "hits");
		int blank = num(vm, "blankHits");
		int varHits = num(vm, "varHits");

		boolean literalOnly = literalStart == 1 && hits1 == 1 && blank1 == 0 && var1 == 0;
		boolean emptySkipped = noneStart == 0 && blank2 == 0;
		boolean variableResolved = variableStart == 1 && var2 == 1;
		boolean anyModeFires = anyStart == 1 && blank3 == 1;
		boolean stillFiltered = anyStart2 == 1 && blank == 2 && hits == 1 && varHits == 1;

		System.out.println("[chat] 字面量命中=起" + literalStart + "/hits=" + hits1
				+ " 空关键词跳过=起" + noneStart + "/blankHits=" + blank2
				+ " 变量求值=起" + variableStart + "/varHits=" + var2
				+ " 放行模式=起" + anyStart + "→" + blank3
				+ " 放行仍过滤=起" + anyStart2 + "→" + blank);

		// ---- A：触发消息绑定到线程 ----
		// 模拟真实时序：A 到货 → 帽子入队（此刻还没执行）→ 同一 tick 内 B 到货覆盖全局槽
		// → 线程执行时「最近的聊天」必须仍是 A。
		xyz.turbomc.client.script.runtime.ChatState.clear();
		xyz.turbomc.client.script.runtime.ChatState.record("cap A-message");
		int capStart = vm.startChatHat("cap A-message", false);
		xyz.turbomc.client.script.runtime.ChatState.record("cap B-message");
		tick(vm, 3);
		String seen = String.valueOf(vm.getVariable("seen"));
		String trig = String.valueOf(vm.getVariable("trig"));
		String flagTrig = String.valueOf(vm.getVariable("flagTrig"));

		boolean pinned = capStart == 1 && "cap A-message".equals(seen);
		boolean triggerBlock = "cap A-message".equals(trig);
		// 绿旗线程没有聊天副本：「触发我的聊天」必须是空，而不是回落到全局槽
		boolean triggerEmptyOnFlag = flagTrig.isEmpty() || "0".equals(flagTrig);
		System.out.println("[chat] 线程绑定：入队 " + capStart + " 个，读到「" + seen + "」"
				+ "，触发我的聊天=「" + trig + "」，绿旗线程=「" + flagTrig + "」");

		// 探针 2：同一顶块的线程还在跑（等待中）时，新消息会不会把帽子拉起来
		int busy1 = vm.startChatHat("busy one", false);
		int busy2 = vm.startChatHat("busy two", false);
		tick(vm, 2);
		System.out.println("[chat] 探针：同一帽子连发两条，入队 " + busy1 + " / " + busy2
				+ "（第二个被 busy 跳过即为 0）");

		// 回声抑制：脚本自己发的那条不应再触发帽子
		xyz.turbomc.client.script.runtime.SelfChatEcho.sentByScript("bot reply");
		boolean echoSuppressed = xyz.turbomc.client.script.runtime.SelfChatEcho.isEcho("<Steve> bot reply");
		boolean playerStillWorks = !xyz.turbomc.client.script.runtime.SelfChatEcho.isEcho("<Steve> hello there");

		// 同一条消息只触发一次：签名相同视为同一条，签名不同视为新消息
		boolean sameMessageOnce = ChatGate.accept("hello", "sig-1") && !ChatGate.accept("hello", "sig-1");
		boolean newMessagePasses = ChatGate.accept("hello", "sig-2");
		boolean unsignedWindow = ChatGate.accept("hello there", null) && !ChatGate.accept("hello there", null);

		boolean ok = literalOnly && emptySkipped && variableResolved && anyModeFires && stillFiltered
				&& pinned && triggerBlock && triggerEmptyOnFlag
				&& echoSuppressed && playerStillWorks
				&& sameMessageOnce && newMessagePasses && unsignedWindow;
		System.out.println("[chat] 字面量只起一次=" + literalOnly
				+ " 空关键词被跳过=" + emptySkipped
				+ " 变量被求值=" + variableResolved
				+ " 放行模式生效=" + anyModeFires
				+ " 放行仍按关键词过滤=" + stillFiltered);
		System.out.println("[chat] 消息绑定线程=" + pinned
				+ " 触发我的聊天=" + triggerBlock
				+ " 非聊天线程为空=" + triggerEmptyOnFlag);
		System.out.println("[chat] 回声被吞=" + echoSuppressed + " 玩家消息仍触发=" + playerStillWorks
				+ " 同一条消息只触发一次=" + sameMessageOnce
				+ " 相同文字的新消息仍触发=" + newMessagePasses
				+ " 无签名短窗口去重=" + unsignedWindow);
		System.out.println(ok ? "CHAT HAT PASS" : "CHAT HAT FAIL");
		System.exit(ok ? 0 : 1);
	}

	private static void tick(RhinoVm vm, int frames) {
		for (int i = 0; i < frames; i++) {
			vm.tick(null);
		}
	}

	private static int num(RhinoVm vm, String name) {
		Object value = vm.getVariable(name);
		return value instanceof Number number ? number.intValue() : -1;
	}

	/**
	 * 最小工程：三顶「当聊天包含」帽子，体内各自把一个计数变量加一。
	 *
	 * <p>关键词 {@code kw} 定义在舞台上、由角色引用——这是 sb3 里最常见的作用域形态，
	 * 顺带把「变量查找要回退到舞台」这条也钉住。</p>
	 */
	private static String project() {
		return """
				{
				  "targets": [
				    {
				      "isStage": true,
				      "name": "Stage",
				      "variables": {
				        "hits-id": ["hits", 0],
				        "blank-id": ["blankHits", 0],
				        "var-id": ["varHits", 0],
				        "kw-id": ["kw", "abc"],
				        "seen-id": ["seen", ""],
				        "trig-id": ["trig", ""],
				        "ftrig-id": ["flagTrig", "未执行"]
				      },
				      "lists": {}, "broadcasts": {}, "blocks": {}, "comments": {},
				      "currentCostume": 0,
				      "costumes": [{"name":"c","dataFormat":"svg","assetId":"cd21514d0531fdffb22204e0ec5ed84a",
				        "md5ext":"cd21514d0531fdffb22204e0ec5ed84a.svg","rotationCenterX":0,"rotationCenterY":0}],
				      "sounds": [], "volume": 100, "layerOrder": 0, "tempo": 60,
				      "videoTransparency": 50, "videoState": "on", "textToSpeechLanguage": null
				    },
				    {
				      "isStage": false,
				      "name": "角色1",
				      "variables": {}, "lists": {}, "broadcasts": {},
				      "blocks": {
				        "hat1": {"opcode":"turbomc_whenChat","next":"add1","parent":null,
				          "inputs":{"MATCH":[1,[10,"hello"]]},"fields":{},
				          "shadow":false,"topLevel":true,"x":0,"y":0},
				        "add1": {"opcode":"data_changevariableby","next":null,"parent":"hat1",
				          "inputs":{"VALUE":[1,[4,"1"]]},
				          "fields":{"VARIABLE":["hits","hits-id"]},
				          "shadow":false,"topLevel":false},

				        "hat2": {"opcode":"turbomc_whenChat","next":"add2","parent":null,
				          "inputs":{"MATCH":[1,[10,""]]},"fields":{},
				          "shadow":false,"topLevel":true,"x":0,"y":150},
				        "add2": {"opcode":"data_changevariableby","next":null,"parent":"hat2",
				          "inputs":{"VALUE":[1,[4,"1"]]},
				          "fields":{"VARIABLE":["blankHits","blank-id"]},
				          "shadow":false,"topLevel":false},

				        "hat3": {"opcode":"turbomc_whenChat","next":"add3","parent":null,
				          "inputs":{"MATCH":[3,[12,"kw","kw-id"],[10,""]]},"fields":{},
				          "shadow":false,"topLevel":true,"x":0,"y":300},
				        "add3": {"opcode":"data_changevariableby","next":null,"parent":"hat3",
				          "inputs":{"VALUE":[1,[4,"1"]]},
				          "fields":{"VARIABLE":["varHits","var-id"]},
				          "shadow":false,"topLevel":false},

				        "hat4": {"opcode":"turbomc_whenChat","next":"set4","parent":null,
				          "inputs":{"MATCH":[1,[10,"cap"]]},"fields":{},
				          "shadow":false,"topLevel":true,"x":0,"y":450},
				        "set4": {"opcode":"data_setvariableto","next":"setTrig","parent":"hat4",
				          "inputs":{"VALUE":[3,"read4",[10,""]]},
				          "fields":{"VARIABLE":["seen","seen-id"]},
				          "shadow":false,"topLevel":false},
				        "read4": {"opcode":"turbomc_chatSource","next":null,"parent":"set4",
				          "inputs":{"SOURCE":[1,"srcLast"]},"fields":{},"shadow":false,"topLevel":false},
				        "srcLast": {"opcode":"chatSource","next":null,"parent":"read4",
				          "inputs":{},"fields":{"chatSource":["last",null]},"shadow":true,"topLevel":false},
				        "setTrig": {"opcode":"data_setvariableto","next":null,"parent":"set4",
				          "inputs":{"VALUE":[3,"readTrig",[10,""]]},
				          "fields":{"VARIABLE":["trig","trig-id"]},
				          "shadow":false,"topLevel":false},
				        "readTrig": {"opcode":"turbomc_chatSource","next":null,"parent":"setTrig",
				          "inputs":{"SOURCE":[1,"srcTrig"]},"fields":{},"shadow":false,"topLevel":false},
				        "srcTrig": {"opcode":"chatSource","next":null,"parent":"readTrig",
				          "inputs":{},"fields":{"chatSource":["trigger",null]},"shadow":true,"topLevel":false},

				        "flagHat": {"opcode":"event_whenflagclicked","next":"setFlag","parent":null,
				          "inputs":{},"fields":{},
				          "shadow":false,"topLevel":true,"x":0,"y":750},
				        "setFlag": {"opcode":"data_setvariableto","next":null,"parent":"flagHat",
				          "inputs":{"VALUE":[3,"readFlag",[10,""]]},
				          "fields":{"VARIABLE":["flagTrig","ftrig-id"]},
				          "shadow":false,"topLevel":false},
				        "readFlag": {"opcode":"turbomc_chatSource","next":null,"parent":"setFlag",
				          "inputs":{"SOURCE":[1,"srcFlag"]},"fields":{},"shadow":false,"topLevel":false},
				        "srcFlag": {"opcode":"chatSource","next":null,"parent":"readFlag",
				          "inputs":{},"fields":{"chatSource":["trigger",null]},"shadow":true,"topLevel":false},

				        "hat5": {"opcode":"turbomc_whenChat","next":"wait5","parent":null,
				          "inputs":{"MATCH":[1,[10,"busy"]]},"fields":{},
				          "shadow":false,"topLevel":true,"x":0,"y":600},
				        "wait5": {"opcode":"control_wait","next":null,"parent":"hat5",
				          "inputs":{"DURATION":[1,[5,"999"]]},"fields":{},
				          "shadow":false,"topLevel":false}
				      },
				      "comments": {},
				      "currentCostume": 0,
				      "costumes": [{"name":"c","dataFormat":"svg","assetId":"cd21514d0531fdffb22204e0ec5ed84a",
				        "md5ext":"cd21514d0531fdffb22204e0ec5ed84a.svg","rotationCenterX":0,"rotationCenterY":0}],
				      "sounds": [], "volume": 100, "layerOrder": 1, "visible": true,
				      "x": 0, "y": 0, "size": 100, "direction": 90,
				      "draggable": false, "rotationStyle": "all around"
				    }
				  ],
				  "monitors": [],
				  "extensions": ["turbomc"],
				  "meta": {"semver": "3.0.0", "vm": "0.2.0", "agent": "chat-hat"}
				}
				""";
	}
}
