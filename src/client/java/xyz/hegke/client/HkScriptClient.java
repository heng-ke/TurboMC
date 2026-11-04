package xyz.hegke.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import xyz.hegke.HkScript;
import xyz.hegke.client.config.HkScriptConfig;
import xyz.hegke.client.script.ScriptEngine;
import xyz.hegke.client.script.runtime.TriggerDispatcher;
import xyz.hegke.client.script.ui.ScriptListScreen;

public class HkScriptClient implements ClientModInitializer {
	private static KeyBinding toggleScriptKey;
	private static KeyBinding openEditorKey;

	private final TriggerDispatcher triggerDispatcher = new TriggerDispatcher();

	@Override
	public void onInitializeClient() {
		// 客户端配置在初始化阶段加载，保证后续所有客户端功能都读到同一份数据。
		HkScriptConfig.load();

		// Category.create 会向全局注册表登记，同一个 id 只能创建一次，因此两个按键共享同一实例。
		KeyBinding.Category category = KeyBinding.Category.create(HkScript.id("general"));

		toggleScriptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.hk-script.toggle_script",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F6,
				category
		));

		openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.hk-script.open_editor",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F7,
				category
		));

		ScriptEngine.get().init();

		ClientReceiveMessageEvents.CHAT.register(
				(message, signedMessage, sender, params, receptionTimestamp) ->
						ScriptEngine.get().fire("event.chat", hat -> {
							String match = ScriptEngine.get().hatText(hat, "MATCH");
							// null 表示关键词是表达式，触发时无法求值：不匹配，而不是匹配全部
							return match != null
									&& (match.isBlank() || message.getString().contains(match));
						}));

		// 脚本在 START_CLIENT_TICK 推进，使本 tick 设置的动作能赶在实体移动逻辑之前生效。
		ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
	}

	private void onClientTick(MinecraftClient client) {
		while (toggleScriptKey.wasPressed()) {
			boolean enabled = !HkScriptConfig.get().scriptEngineEnabled;
			HkScriptConfig.get().scriptEngineEnabled = enabled;
			HkScriptConfig.save();
			HkScript.LOGGER.info("[script] 开关切换为 {}", enabled);
			if (client.player != null) {
				client.player.sendMessage(
						Text.translatable(enabled ? "message.hk-script.script_on" : "message.hk-script.script_off"),
						true);
			}
		}

		while (openEditorKey.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new ScriptListScreen(null));
			}
		}

		ScriptEngine.get().tick(client);
		triggerDispatcher.tick(client);
	}
}
