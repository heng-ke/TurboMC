package xyz.turbomc.client.script.vm;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * 把打包进 jar 的 TurboWarp 扩展释放到 {@code config/turbomc/}。
 *
 * <p>在 TurboWarp 里「导入扩展」需要一个 js 文件，而模组分发出去只有一个 jar，
 * 拿到 jar 的人手里没有仓库里的 {@code turbowarp/} 目录。因此构建时把扩展源文件
 * 打进 {@code assets/turbomc/turbowarp/}，客户端启动时释放一份到配置目录。</p>
 *
 * <p>每次启动比对内容：一致就不碰文件（不刷新时间戳，避免同步盘反复上传），
 * 不一致说明模组版本更新了扩展，直接覆盖——这份副本以 jar 内的为准，
 * <b>用户自己改的内容会被覆盖</b>，要改扩展请改仓库里的源文件。</p>
 */
public final class ExtensionExporter {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/ext");

	/** jar 内路径，由 build.gradle 的 processResources 决定。 */
	static final String RESOURCE = "/assets/turbomc/turbowarp/turbomc-extension.js";

	/** 释放出来的文件名，与仓库里的源文件同名。 */
	static final String FILE_NAME = "turbomc-extension.js";

	/** 释放结果，供日志与冒烟测试区分。 */
	public enum Result {
		/** 配置目录里原本没有，已写入。 */
		CREATED,
		/** 原本存在但内容不同，已按 jar 内版本覆盖。 */
		UPDATED,
		/** 内容一致，未改动文件。 */
		UNCHANGED,
		/** jar 内没有这个资源（构建漏打包）。 */
		MISSING
	}

	private ExtensionExporter() {
	}

	/** 客户端启动时调用；失败只记日志，不影响游戏启动。 */
	public static void exportToConfig() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("turbomc");
		Path target = dir.resolve(FILE_NAME);
		try {
			switch (exportTo(dir)) {
				case CREATED -> LOG.info("[ext] 已释放 TurboWarp 扩展：{}", target);
				case UPDATED -> LOG.info("[ext] TurboWarp 扩展已随模组更新：{}", target);
				case UNCHANGED -> LOG.debug("[ext] TurboWarp 扩展无变化：{}", target);
				case MISSING -> LOG.warn("[ext] jar 内缺少 {}，本次未释放扩展", RESOURCE);
			}
		} catch (IOException e) {
			LOG.warn("[ext] 释放 TurboWarp 扩展失败：{}", e.toString());
		}
	}

	/**
	 * 把 jar 内的扩展写到 {@code dir} 下。
	 *
	 * <p>包私有：只给 {@link ExtensionExportSmokeTest} 用，避免外部误调。</p>
	 */
	static Result exportTo(Path dir) throws IOException {
		byte[] bundled;
		try (InputStream in = ExtensionExporter.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				return Result.MISSING;
			}
			bundled = in.readAllBytes();
		}

		Files.createDirectories(dir);
		Path target = dir.resolve(FILE_NAME);
		if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), bundled)) {
			return Result.UNCHANGED;
		}

		boolean existed = Files.exists(target);
		// 先写临时文件再替换：写到一半崩掉不会留下半截 js，TurboWarp 那边不至于读到坏文件
		Path temp = Files.createTempFile(dir, ".turbomc-extension", ".tmp");
		boolean moved = false;
		try {
			Files.write(temp, bundled);
			try {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicUnsupported) {
				// 部分网络盘 / 非 NTFS 文件系统不支持原子移动，退回普通覆盖。
				// 这个 catch 也会接住「目标被占用 / 磁盘满」，所以下面这次同样可能失败——
				// 失败时由 finally 负责清理，别在 config/turbomc/ 里留下半成品
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
			moved = true;
		} finally {
			if (!moved) {
				// 删除自身失败只能忽略：它不能盖掉导致清理的那条原始异常
				try {
					Files.deleteIfExists(temp);
				} catch (IOException ignored) {
					// 忽略
				}
			}
		}
		return existed ? Result.UPDATED : Result.CREATED;
	}
}
