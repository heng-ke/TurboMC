package xyz.turbomc;

import net.fabricmc.api.ModInitializer;

import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组主入口。
 *
 * <p>这里只放两端共享的常量（模组 ID、标识符、日志器）。真正的功能初始化在客户端入口
 * {@code xyz.turbomc.client.TurboMCClient}：按键绑定、事件注册、脚本引擎与 HUD。</p>
 */
public class TurboMC implements ModInitializer {
	public static final String MOD_ID = "turbomc";

	/** 统一用模组 ID 作为日志器名，便于在日志里按来源过滤。 */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[turbomc] 主入口就绪（客户端功能由 TurboMCClient 初始化）");
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
