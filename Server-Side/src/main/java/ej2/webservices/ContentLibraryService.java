package ej2.webservices;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * File-system backed storage for the Content Library.
 * Files are stored in App_Data/ContentLibrary/{id}/v{n}.docx and
 * metadata is persisted in App_Data/ContentLibrary/library.json.
 * All public methods are thread-safe (a single lock guards the index).
 */
public class ContentLibraryService {

	private static final Object LOCK = new Object();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path rootPath;
	private final Path indexPath;

	public ContentLibraryService() {
		this.rootPath = Paths.get("App_Data", "ContentLibrary");
		this.indexPath = rootPath.resolve("library.json");
		try {
			Files.createDirectories(rootPath);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to create ContentLibrary root folder", e);
		}
	}

	public Path getRootPath() { return rootPath; }

	public Path getItemFolder(String id) { return rootPath.resolve(id); }

	public Path getVersionFile(String id, int version) {
		return getItemFolder(id).resolve("v" + version + ".docx");
	}

	public ContentLibraryStore load() {
		synchronized (LOCK) {
			try {
				if (!Files.exists(indexPath)) {
					return new ContentLibraryStore();
				}
				String json = new String(Files.readAllBytes(indexPath), StandardCharsets.UTF_8);
				if (json.trim().isEmpty()) {
					return new ContentLibraryStore();
				}
				ContentLibraryStore store = GSON.fromJson(json, ContentLibraryStore.class);
				return store != null ? store : new ContentLibraryStore();
			} catch (IOException e) {
				throw new IllegalStateException("Unable to read library index", e);
			}
		}
	}

	public void save(ContentLibraryStore store) {
		synchronized (LOCK) {
			try {
				String json = GSON.toJson(store);
				Files.write(indexPath, json.getBytes(StandardCharsets.UTF_8));
			} catch (IOException e) {
				throw new IllegalStateException("Unable to write library index", e);
			}
		}
	}

	public ContentItem findById(String id) {
		for (ContentItem item : load().getItems()) {
			if (item.getId().equals(id)) {
				return item;
			}
		}
		return null;
	}

	public List<ContentItem> list() {
		List<ContentItem> items = load().getItems();
		items.sort(Comparator.comparing(
				(ContentItem i) -> nullSafe(i.getModifiedDate()),
				Comparator.nullsLast(Comparator.reverseOrder())));
		return items;
	}

	private static String nullSafe(String s) { return s != null ? s : ""; }

	/**
	 * Adds a new version (v{n+1}.docx) to an existing item using the supplied bytes.
	 * The OUTGOING current version (v{n}) is stamped with the live item status —
	 * the status the user had set in the editor — before CurrentVersion is bumped,
	 * so each saved version keeps the workflow state it was authored under.
	 * Older versions are never touched here.
	 */
	public ContentItem addVersion(String id, byte[] docxBytes, String modifiedUser) throws IOException {
		synchronized (LOCK) {
			ContentLibraryStore store = load();
			ContentItem item = null;
			for (ContentItem candidate : store.getItems()) {
				if (candidate.getId().equals(id)) {
					item = candidate;
					break;
				}
			}
			if (item == null) {
				throw new IllegalArgumentException("Content item '" + id + "' not found.");
			}

			int next = item.getCurrentVersion() + 1;
			String fileName = "v" + next + ".docx";
			Files.createDirectories(getItemFolder(id));
			Path destPath = getVersionFile(id, next);
			Files.write(destPath, docxBytes);

			// Freeze the outgoing current version with the live status.
			for (ContentVersion v : item.getVersions()) {
				if (v.getVersionNumber() == item.getCurrentVersion() && item.getStatus() != null) {
					v.setStatus(item.getStatus());
				}
			}

			item.setCurrentVersion(next);
			item.setCurrentFile(fileName);
			// Sync the displayed Version to the new integer counter (1, 2, 3, ...).
			item.setVersion(String.valueOf(next));
			item.setModifiedDate(now());

			ContentVersion newVersion = new ContentVersion();
			newVersion.setVersionNumber(next);
			newVersion.setFileName(fileName);
			newVersion.setModifiedUser(modifiedUser != null ? modifiedUser : "Unknown");
			newVersion.setModifiedDate(now());
			// The new version starts life carrying the same workflow status the user was on.
			newVersion.setStatus(item.getStatus());
			item.getVersions().add(newVersion);

			save(store);
			return item;
		}
	}

	public ContentItem updateStatus(String id, String status) {
		synchronized (LOCK) {
			ContentLibraryStore store = load();
			ContentItem item = null;
			for (ContentItem candidate : store.getItems()) {
				if (candidate.getId().equals(id)) {
					item = candidate;
					break;
				}
			}
			if (item == null) {
				return null;
			}
			item.setStatus(status);
			item.setModifiedDate(now());
			// Status belongs to the *active* edit: also stamp the CURRENT version's
			// per-version Status so the history dialog stays in sync. Older versions
			// are never touched; their status was frozen at save time.
			for (ContentVersion v : item.getVersions()) {
				if (v.getVersionNumber() == item.getCurrentVersion()) {
					v.setStatus(status);
				}
			}
			save(store);
			return item;
		}
	}

	public ContentItem updateMetadata(String id, String title, String category, String status,
			String author, String version) {
		synchronized (LOCK) {
			ContentLibraryStore store = load();
			ContentItem item = null;
			for (ContentItem candidate : store.getItems()) {
				if (candidate.getId().equals(id)) {
					item = candidate;
					break;
				}
			}
			if (item == null) {
				return null;
			}
			if (title != null && !title.isEmpty()) item.setTitle(title);
			if (category != null && !category.isEmpty()) item.setCategory(category);
			if (status != null && !status.isEmpty()) item.setStatus(status);
			if (author != null && !author.isEmpty()) item.setAuthor(author);
			if (version != null && !version.isEmpty()) item.setVersion(version);
			item.setModifiedDate(now());
			// Mirror updateStatus for the current version's per-version Status.
			if (status != null && !status.isEmpty()) {
				for (ContentVersion v : item.getVersions()) {
					if (v.getVersionNumber() == item.getCurrentVersion()) {
						v.setStatus(status);
					}
				}
			}
			save(store);
			return item;
		}
	}

	public boolean delete(String id) {
		synchronized (LOCK) {
			ContentLibraryStore store = load();
			ContentItem item = null;
			for (ContentItem candidate : store.getItems()) {
				if (candidate.getId().equals(id)) {
					item = candidate;
					break;
				}
			}
			if (item == null) {
				return false;
			}
			store.getItems().remove(item);
			save(store);
			try {
				Path dir = getItemFolder(id);
				if (Files.exists(dir)) {
					deleteRecursive(dir);
				}
			} catch (IOException e) {
				// ignore cleanup errors
			}
			return true;
		}
	}

	private static void deleteRecursive(Path dir) throws IOException {
		try (var walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException ignored) {
				}
			});
		}
	}

	/**
	 * Build a unique, human-readable folder name (and Id) for a new content item.
	 * Derived from the document title with a short random suffix for uniqueness.
	 */
	public String buildUniqueFolderName(String title, ContentLibraryStore existing) {
		String baseName = sanitizeFolderName(title);
		if (baseName.isEmpty()) {
			baseName = "Document";
		}
		if (baseName.length() > 60) {
			baseName = baseName.substring(0, 60);
		}

		ContentLibraryStore store = existing != null ? existing : load();
		Set<String> existingNames = new HashSet<String>();
		for (ContentItem item : store.getItems()) {
			existingNames.add(item.getId().toLowerCase());
		}

		if (!existingNames.contains(baseName.toLowerCase())
				&& !Files.exists(getItemFolder(baseName))) {
			return baseName;
		}

		// Append a 4-character suffix to keep it unique.
		Random rng = new Random();
		for (int attempt = 0; attempt < 50; attempt++) {
			String suffix = String.format("%04x", rng.nextInt(0xFFFF));
			String candidate = baseName + "-" + suffix;
			if (!existingNames.contains(candidate.toLowerCase())
					&& !Files.exists(getItemFolder(candidate))) {
				return candidate;
			}
		}

		// Fallback: title + short GUID.
		return baseName + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

	/**
	 * Convert a human title into a safe folder name. Replaces invalid filesystem
	 * characters with '-', collapses whitespace, and trims.
	 */
	public static String sanitizeFolderName(String s) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder(s.length());
		for (char c : s.toCharArray()) {
			if (Character.isLetterOrDigit(c)) {
				sb.append(c);
			} else if (c == ' ' || c == '-' || c == '_' || c == '.') {
				sb.append('-');
			}
		}
		String result = sb.toString();
		while (result.startsWith("-")) result = result.substring(1);
		while (result.endsWith("-")) result = result.substring(0, result.length() - 1);
		while (result.contains("--")) result = result.replace("--", "-");
		return result;
	}

	/** Current local timestamp in the same ISO-ish shape the C# sample writes. */
	public static String now() {
		return java.time.LocalDateTime.now()
				.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS"));
	}
}