package xyz.turbomc.client.script.vm;

import xyz.turbomc.client.script.action.NativeRegistry;
import xyz.turbomc.client.script.runtime.ChatGate;
import xyz.turbomc.client.script.block.BlockRegistry;

/**
 * 「当聊天包含」的触发次数冒烟。
 *
 * <p>要回答的问题很具体：一条聊天消息会不会让同一个帽子跑多次。</p>
 *
 * <p>scratch-vm 只对 {@code edgeActivated} 的帽子做逐帧检查，而扩展里三个事件帽子都显式声明了
 * {@code isEdgeActivated: false}，理论上只能由宿主的 {@code startHats} 拉起。这个用例用
 * 「计数变量 +1」把「跑了几次」变成可断言的事实：</p>
 *
 * <ol>
 *   <li>发一条命中关键词的消息 → 计数应为 1；</li>
 *   <li>再发同样的消息 → 计数应为 2（每条消息各跑一次）；</li>
 *   <li>发一条不命中的消息 → 计数不变。</li>
 * </ol>
 *
 * <p>若第 1 步得到大于 1，说明帽子在被逐帧重复拉起，那是 VM 侧的配置问题；
 * 若等于 1，则游戏里「说一次却一直触发」只能来自事件本身被重复投递
 * （例如脚本自己发的聊天又被自己收到）。</p>
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

		int before = count(vm);
		vm.startChatHat("say hello world");
		tick(vm, 30);
		int first = count(vm);

		vm.startChatHat("say hello again");
		tick(vm, 30);
		int second = count(vm);

		vm.startChatHat("bye bye");
		tick(vm, 30);
		int third = count(vm);

		System.out.println("[chat] 初始=" + before
				+ " 第一条命中=" + first
				+ " 第二条命中=" + second
				+ " 不命中=" + third);

		boolean single = first == before + 1 && second == first + 1;
		boolean filtered = third == second;

		// 回声抑制：脚本自己发的那条不应再触发帽子
		xyz.turbomc.client.script.runtime.SelfChatEcho.sentByScript("bot reply");
		boolean echoSuppressed = xyz.turbomc.client.script.runtime.SelfChatEcho.isEcho("<Steve> bot reply");
		boolean playerStillWorks = !xyz.turbomc.client.script.runtime.SelfChatEcho.isEcho("<Steve> hello there");

		// 同一条消息只触发一次：签名相同视为同一条，签名不同视为新消息
		boolean sameMessageOnce = ChatGate.accept("hello", "sig-1") && !ChatGate.accept("hello", "sig-1");
		boolean newMessagePasses = ChatGate.accept("hello", "sig-2");
		boolean unsignedWindow = ChatGate.accept("hello there", null) && !ChatGate.accept("hello there", null);

		boolean ok = single && filtered && echoSuppressed && playerStillWorks
				&& sameMessageOnce && newMessagePasses && unsignedWindow;
		System.out.println("[chat] 一条消息只跑一次=" + single + " 关键词过滤=" + filtered
				+ " 回声被吞=" + echoSuppressed + " 玩家消息仍触发=" + playerStillWorks);
		System.out.println("[chat] 同一条消息只触发一次=" + sameMessageOnce
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

	private static int count(RhinoVm vm) {
		Object value = vm.getVariable("hits");
		return value instanceof Number number ? number.intValue() : -1;
	}

	/** 最小工程：一个「当聊天包含 hello」帽子，体内把 hits 加一。 */
	private static String project() {
		return """
				{
				  "targets": [
				    {
				      "isStage": true,
				      "name": "Stage",
				      "variables": {"hits-id": ["hits", 0]},
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
				        "hat": {"opcode":"turbomc_whenChat","next":"add","parent":null,
				          "inputs":{"MATCH":[1,[10,"hello"]]},"fields":{},
				          "shadow":false,"topLevel":true,"x":0,"y":0},
				        "add": {"opcode":"data_changevariableby","next":null,"parent":"hat",
				          "inputs":{"VALUE":[1,[4,"1"]]},
				          "fields":{"VARIABLE":["hits","hits-id"]},
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
