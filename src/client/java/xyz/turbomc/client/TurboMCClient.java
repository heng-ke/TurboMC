package xyz.turbomc.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Base64;
import xyz.turbomc.TurboMC;
import xyz.turbomc.client.config.TurboMCConfig;
import xyz.turbomc.client.script.ScriptEngine;
import xyz.turbomc.client.script.runtime.ChatGate;
import xyz.turbomc.client.script.runtime.TriggerDispatcher;
import xyz.turbomc.client.script.ui.ScriptHudElement;
import xyz.turbomc.client.script.ui.ScriptListScreen;
import xyz.turbomc.client.script.vm.ExtensionExporter;

public class TurboMCClient implements ClientModInitializer {
	private static KeyBinding toggleScriptKey;
	private static KeyBinding openPacksKey;

	private final TriggerDispatcher triggerDispatcher = new TriggerDispatcher();

	@Override
	public void onInitializeClient() {
		// 客户端配置在初始化阶段加载，保证后续所有客户端功能都读到同一份数据。
		TurboMCConfig.load();

		// 把打包进 jar 的 TurboWarp 扩展释放到 config/turbomc/，分发出去的 jar 才自带可导入的 js
		ExtensionExporter.exportToConfig();

		// Category.create 会向全局注册表登记，同一个 id 只能创建一次，因此两个按键共享同一实例。
		KeyBinding.Category category = KeyBinding.Category.create(TurboMC.id("general"));

		toggleScriptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.turbomc.toggle_script",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F6,
				category
		));

		openPacksKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				// 翻译键沿用旧名（改 key binding id 会让玩家自定义的按键失效），显示文本已改为「打开脚本包」
				"key.turbomc.open_editor",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F7,
				category
		));

		ScriptEngine.get().init();

		// 脚本 HUD：引擎状态 + 「说 / 思考」气泡。scratch-vm 的 say/think 在游戏内
		// 没有任何渲染出口，不在这里补，脚本的话就没人看得见。
		HudElementRegistry.addLast(TurboMC.id("script_hud"), new ScriptHudElement());

		ClientReceiveMessageEvents.CHAT.register(
				(message, signedMessage, sender, params, receptionTimestamp) -> {
					// 同一条消息只触发一次：回调可能重复，用签名当身份；无签名时按短窗口兜底
					String signature = signedMessage != null && signedMessage.hasSignature()
							? Base64.getEncoder().encodeToString(signedMessage.signature().data())
							: null;
					if (!ChatGate.accept(message.getString(), signature)) {
						return;
					}
					// 关键词过滤在 VM 侧按每个「当聊天包含」积木的输入值完成
					ScriptEngine.get().fireChat(message.getString());
				});

		// 脚本在 START_CLIENT_TICK 推进，使本 tick 设置的动作能赶在实体移动逻辑之前生效。
		ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
	}

	private void onClientTick(MinecraftClient client) {
		while (toggleScriptKey.wasPressed()) {
			boolean enabled = !TurboMCConfig.get().scriptEngineEnabled;
			TurboMCConfig.get().scriptEngineEnabled = enabled;
			TurboMCConfig.save();
			TurboMC.LOGGER.info("[script] 开关切换为 {}", enabled);
			if (client.player != null) {
				client.player.sendMessage(
						Text.translatable(enabled ? "message.turbomc.script_on" : "message.turbomc.script_off"),
						true);
			}
		}

		while (openPacksKey.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new ScriptListScreen(null));
			}
		}

		ScriptEngine.get().tick(client);
		triggerDispatcher.tick(client);
	}
}
