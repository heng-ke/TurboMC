package xyz.turbomc.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu 集成入口。仅在安装 Mod Menu 时由 Fabric 加载该 entrypoint。
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// 软依赖守卫：未安装 Cloth Config 时给出提示界面，而不是让玩家面对空白屏或崩溃。
		if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
			return MissingClothConfigScreen::new;
		}
		return TurboMCConfigScreen::create;
	}
}
