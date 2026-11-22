package xyz.turbomc.client.script.vm;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 扩展释放逻辑的无头冒烟：创建 → 内容一致不动 → 内容过期覆盖。
 *
 * <p>这三条覆盖了玩家实际会遇到的路径：首次启动释放、日常启动不折腾文件、
 * 模组更新后扩展跟着更新。跑法：{@code ./gradlew exportSmoke}。</p>
 */
public final class ExtensionExportSmokeTest {
	public static void main(String[] args) throws Exception {
		Path dir = Files.createTempDirectory("turbomc-ext");
		Path target = dir.resolve(ExtensionExporter.FILE_NAME);

		byte[] bundled;
		try (InputStream in = ExtensionExportSmokeTest.class.getResourceAsStream(ExtensionExporter.RESOURCE)) {
			bundled = in == null ? new byte[0] : in.readAllBytes();
		}

		ExtensionExporter.Result created = ExtensionExporter.exportTo(dir);
		boolean exists = Files.isRegularFile(target);
		byte[] installed = exists ? Files.readAllBytes(target) : new byte[0];

		ExtensionExporter.Result unchanged = ExtensionExporter.exportTo(dir);

		// 模拟旧版本遗留的文件：内容不同，应被覆盖
		Files.writeString(target, "stale content");
		ExtensionExporter.Result updated = ExtensionExporter.exportTo(dir);
		byte[] reloaded = Files.readAllBytes(target);

		// 失败路径：把目标位置占成一个**非空**目录，两次移动都会失败，临时文件必须被清掉。
		// 空目录在 Windows 上会被 MoveFileEx 直接替换掉，所以里面必须放一个文件。
		// 这里守的是「config/turbomc/ 里堆一堆 .tmp」——建了临时文件却不清理的老问题。
		Path blocked = Files.createTempDirectory("turbomc-ext-blocked");
		Path blocker = blocked.resolve(ExtensionExporter.FILE_NAME);
		Files.createDirectory(blocker);
		Files.writeString(blocker.resolve("occupied"), "x");
		boolean threw = false;
		try {
			ExtensionExporter.exportTo(blocked);
		} catch (java.io.IOException expected) {
			threw = true;
		}
		long leftovers;
		try (var stream = Files.list(blocked)) {
			leftovers = stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
		}
		boolean cleanupOk = threw && leftovers == 0;

		boolean ok = created == ExtensionExporter.Result.CREATED
				&& exists
				&& unchanged == ExtensionExporter.Result.UNCHANGED
				&& updated == ExtensionExporter.Result.UPDATED
				&& bundled.length > 0
				&& Arrays.equals(installed, bundled)
				&& Arrays.equals(reloaded, bundled)
				&& cleanupOk;

		System.out.printf("[ext] created=%s unchanged=%s updated=%s bytes=%d%n",
				created, unchanged, updated, bundled.length);
		System.out.printf("[ext] 失败清理：抛出=%s 残留 .tmp=%d%n", threw, leftovers);
		System.out.println(ok ? "EXT EXPORT PASS" : "EXT EXPORT FAIL");
		System.exit(ok ? 0 : 1);
	}
}
