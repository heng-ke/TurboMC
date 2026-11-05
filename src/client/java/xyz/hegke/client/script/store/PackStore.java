package xyz.hegke.client.script.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.hegke.client.script.sb3.Sb3Importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 脚本包仓库：管理 {@code config/hk-script/packages/} 下的 sb3 文件。
 *
 * <p>一个包就是一份 sb3 工程。启用状态是<b>互斥</b>的——同时只有一个包参与运行，
 * 因此不需要合并多个工程的 targets，也就不会出现舞台变量冲突这类静默问题。</p>
 *
 * <p>导入入口仍是 {@code config/hk-script/imports/}：把 sb3 丢进去，游戏内点「导入」即可。</p>
 */
public final class PackStore {
	private static final Logger LOG = LoggerFactory.getLogger("hk-script/script");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final PackStore INSTANCE = new PackStore();
	private static final String INDEX_FILE = "index.json";

	private final List<ScriptPack> packs = new ArrayList<>();
	/** 解析结果缓存：文件名 → (修改时间, 脚本)。sb3 不大，但每次打开界面都重解析没必要。 */
	private final Map<String, Cache> cache = new HashMap<>();
	private Path directory;
	private boolean loaded;
	private String lastMessage = "";

	private record Cache(long modifiedAt, Sb3Importer.Result result) {
	}

	private PackStore() {
	}

	public static PackStore get() {
		return INSTANCE;
	}

	public Path directory() {
		if (directory == null) {
			directory = FabricLoader.getInstance().getConfigDir().resolve("hk-script").resolve("packages");
		}
		return directory;
	}

	/** 投放入口：把 sb3 放到这里，再点「导入」。 */
	public Path importsDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("hk-script").resolve("imports");
	}

	public String lastMessage() {
		return lastMessage;
	}

	public void clearMessage() {
		lastMessage = "";
	}

	public void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		reload();
		// 升级路径：老版本把 sb3 放在 imports/ 里当整体工程跑，从未建过包目录。
		// 首次没有包时自动搬一次，免得用户以为「升级后脚本全没了」。
		if (packs.isEmpty()) {
			int added = importFrom(importsDir()) + importFrom(importsDir().resolve("imported"));
			if (added > 0) {
				LOG.info("[pack] 首次启动：从 imports 自动接管 {} 个 sb3", added);
				reload();
			}
		}
	}

	/** 重新扫描目录并恢复启用状态。 */
	public void reload() {
		packs.clear();
		cache.clear();
		try {
			Files.createDirectories(directory());
			String active = readActiveName();
			try (Stream<Path> files = Files.list(directory())) {
				files.filter(PackStore::isSb3)
						.sorted()
						.forEach(path -> {
							String name = path.getFileName().toString();
							packs.add(new ScriptPack(name, name.equals(active)));
						});
			}
			// 记录的包被删了：不留下悬空状态
			if (!packs.isEmpty() && packs.stream().noneMatch(pack -> pack.enabled)) {
				packs.get(0).enabled = true;
				writeIndex();
			}
		} catch (IOException e) {
			LOG.error("[pack] 包目录读取失败: {}", directory(), e);
		}
		LOG.info("[pack] 脚本包就绪：{} 个，启用 {}",
				packs.size(), activePack() == null ? "无" : activePack().fileName);
	}

	private static boolean isSb3(Path path) {
		return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sb3");
	}

	public List<ScriptPack> packs() {
		load();
		return packs;
	}

	/** 当前启用的包；没有包时为 {@code null}。 */
	public ScriptPack activePack() {
		load();
		for (ScriptPack pack : packs) {
			if (pack.enabled) {
				return pack;
			}
		}
		return null;
	}

	/** 启用指定包（互斥：其余包自动关闭）。传 {@code null} 表示全部停用。 */
	public void setActive(ScriptPack target) {
		for (ScriptPack pack : packs) {
			pack.enabled = pack == target;
		}
		writeIndex();
	}

	public Path pathOf(ScriptPack pack) {
		return directory().resolve(pack.fileName);
	}

	/** 读取包里的 project.json，供 VM 直接加载。 */
	public String projectJson(ScriptPack pack) {
		Path path = pathOf(pack);
		try (ZipFile zip = new ZipFile(path.toFile())) {
			ZipEntry entry = zip.getEntry("project.json");
			if (entry == null) {
				LOG.warn("[pack] {} 内缺少 project.json", pack.fileName);
				return null;
			}
			try (InputStream in = zip.getInputStream(entry)) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			LOG.error("[pack] 读取工程失败: {}", pack.fileName, e);
			return null;
		}
	}

	/** 解析包内的脚本（带缓存），供界面展示与按键收集。 */
	public Sb3Importer.Result scripts(ScriptPack pack) {
		Path path = pathOf(pack);
		long modified;
		try {
			modified = Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return new Sb3Importer.Result(List.of(), List.of(), "无法读取文件时间");
		}
		Cache hit = cache.get(pack.fileName);
		if (hit != null && hit.modifiedAt() == modified) {
			return hit.result();
		}
		Sb3Importer.Result result = Sb3Importer.read(path);
		cache.put(pack.fileName, new Cache(modified, result));
		return result;
	}

	/**
	 * 扫描 {@code imports/} 与 {@code imports/imported/}，把其中的 sb3 复制进包目录。
	 * 同名文件已存在时跳过，避免重复导入产生副本。
	 */
	public int importAll() {
		load();
		int added = 0;
		added += importFrom(importsDir());
		added += importFrom(importsDir().resolve("imported"));
		lastMessage = added > 0 ? "已导入 " + added + " 个脚本包" : "imports 目录里没有新的 sb3";
		if (added > 0) {
			reload();
		}
		LOG.info("[pack] 导入完成：新增 {} 个", added);
		return added;
	}

	private int importFrom(Path dir) {
		if (!Files.isDirectory(dir)) {
			return 0;
		}
		int added = 0;
		try {
			Files.createDirectories(directory());
			try (Stream<Path> files = Files.list(dir)) {
				for (Path path : files.filter(PackStore::isSb3).toList()) {
					Path target = directory().resolve(path.getFileName().toString());
					if (Files.exists(target)) {
						continue;
					}
					Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
					added++;
				}
			}
		} catch (IOException e) {
			LOG.error("[pack] 导入失败: {}", dir, e);
		}
		return added;
	}

	/** 重命名包（同时改文件名）。重名时自动加后缀。 */
	public boolean rename(ScriptPack pack, String newName) {
		String safe = newName == null ? "" : newName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
		if (safe.isEmpty()) {
			lastMessage = "名称不能为空";
			return false;
		}
		if (!safe.toLowerCase(Locale.ROOT).endsWith(".sb3")) {
			safe = safe + ".sb3";
		}
		Path from = pathOf(pack);
		Path to = directory().resolve(uniqueName(safe));
		try {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOG.error("[pack] 重命名失败: {}", pack.fileName, e);
			lastMessage = "重命名失败，详见日志";
			return false;
		}
		boolean wasActive = pack.enabled;
		reload();
		if (wasActive) {
			// reload 会按索引恢复启用态，但索引里记的还是旧名字；这里显式改到新文件上，
			// 否则可能出现「旧名已失效、另有包被自动接管」导致两个包同时启用
			String renamed = to.getFileName().toString();
			for (ScriptPack item : packs) {
				item.enabled = item.fileName.equals(renamed);
			}
			writeIndex();
		}
		lastMessage = "已重命名为 " + to.getFileName();
		return true;
	}

	private String uniqueName(String name) {
		String base = name.substring(0, name.length() - 4);
		String candidate = name;
		int index = 2;
		while (Files.exists(directory().resolve(candidate))) {
			candidate = base + "_" + index + ".sb3";
			index++;
		}
		return candidate;
	}

	public boolean delete(ScriptPack pack) {
		try {
			Files.deleteIfExists(pathOf(pack));
		} catch (IOException e) {
			LOG.error("[pack] 删除失败: {}", pack.fileName, e);
			lastMessage = "删除失败，详见日志";
			return false;
		}
		boolean wasActive = pack.enabled;
		cache.remove(pack.fileName);
		reload();
		// 删掉的是启用中的包：自动接管下一个，避免引擎跑空
		if (wasActive && !packs.isEmpty()) {
			packs.get(0).enabled = true;
			writeIndex();
		}
		lastMessage = "已删除 " + pack.fileName;
		return true;
	}

	/** 打开包目录（用于把 sb3 放进去）。 */
	public void ensureDirectories() {
		try {
			Files.createDirectories(directory());
			Files.createDirectories(importsDir());
		} catch (IOException e) {
			LOG.warn("[pack] 创建目录失败: {}", e.getMessage());
		}
	}

	private String readActiveName() {
		Path index = directory().resolve(INDEX_FILE);
		if (!Files.isRegularFile(index)) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(index, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			return root.has("active") && !root.get("active").isJsonNull()
					? root.get("active").getAsString()
					: null;
		} catch (Exception e) {
			LOG.warn("[pack] 索引读取失败，按无启用处理: {}", e.getMessage());
			return null;
		}
	}

	private void writeIndex() {
		JsonObject root = new JsonObject();
		ScriptPack active = null;
		for (ScriptPack pack : packs) {
			if (pack.enabled) {
				active = pack;
				break;
			}
		}
		root.addProperty("active", active == null ? null : active.fileName);
		try (Writer writer = Files.newBufferedWriter(
				directory().resolve(INDEX_FILE), StandardCharsets.UTF_8)) {
			GSON.toJson(root, writer);
		} catch (IOException e) {
			LOG.error("[pack] 索引写入失败", e);
		}
	}

}
