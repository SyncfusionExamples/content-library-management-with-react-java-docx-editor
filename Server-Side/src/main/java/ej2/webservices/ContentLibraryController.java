package ej2.webservices;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.gson.Gson;
import com.syncfusion.docio.BuiltinStyle;
import com.syncfusion.docio.FormatType;
import com.syncfusion.docio.ImportOptions;
import com.syncfusion.docio.SectionBreakCode;
import com.syncfusion.docio.WordDocument;
import com.syncfusion.docio.WCharacterFormat;
import com.syncfusion.ej2.wordprocessor.WordProcessorHelper;
import com.syncfusion.javahelper.system.drawing.ColorSupport;

/**
 * Content Library API.
 * Handles upload, list, metadata, version management, native download,
 * and combined DOCX export / merge. The WordEditorController keeps the
 * import/export endpoints that the Syncfusion document editor uses at
 * runtime; this controller adds the library-specific surfaces on top.
 *
 * NOTE: the Syncfusion Excel (XlsIO) library is not available on the Java
 * platform, so the Excel metadata export endpoint is intentionally not
 * implemented in this sample.
 */
@RestController
@RequestMapping("/api/contentlibrary")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ContentLibraryController {

	private final ContentLibraryService library;

	public ContentLibraryController() {
		this.library = new ContentLibraryService();
	}

	// ---------- 1. List all items ----------
	@GetMapping("/items")
	public ResponseEntity<List<ContentItem>> listItems() {
		return ResponseEntity.ok(library.list());
	}

	// ---------- 2. Get a single item (with version history) ----------
	@GetMapping("/items/{id}")
	public ResponseEntity<ContentItem> getItem(@PathVariable("id") String id) {
		ContentItem item = library.findById(id);
		if (item == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(item);
	}

	// ---------- 3. Upload a new document ----------
	@PostMapping("/upload")
	public ResponseEntity<?> upload(@RequestParam("files") MultipartFile file,
			@RequestParam(value = "title", required = false) String title,
			@RequestParam(value = "version", required = false) String version,
			@RequestParam(value = "category", required = false) String category,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "author", required = false) String author) throws Exception {
		if (file == null || file.isEmpty()) {
			return badRequest("No file uploaded.");
		}

		String originalName = file.getOriginalFilename();
		String ext = originalName != null && originalName.contains(".")
				? originalName.substring(originalName.lastIndexOf('.')).toLowerCase()
				: "";
		if (!ext.equals(".docx")) {
			return badRequest("Only .docx is supported.");
		}

		ContentItem item = new ContentItem();
		item.setTitle(isBlank(title) ? sanitizeFileNameBase(originalName) : title.trim());
		item.setVersion(isBlank(version) ? "1.0" : version.trim());
		item.setCategory(isBlank(category) ? "Other" : category.trim());
		item.setStatus(isBlank(status) ? "Draft" : status.trim());
		item.setAuthor(isBlank(author) ? "Unknown" : author.trim());

		// Always store under the canonical v1.docx name so the index record
		// is consistent.
		String storedFileName = "v1" + ext;

		// Folder name = sanitized title + short random suffix (also used as Id).
		ContentLibraryStore store = library.load();
		item.setId(library.buildUniqueFolderName(item.getTitle(), store));
		item.setCreatedDate(ContentLibraryService.now());
		item.setModifiedDate(ContentLibraryService.now());
		item.setCurrentVersion(1);
		item.setCurrentFile(storedFileName);

		ContentVersion v1 = new ContentVersion();
		v1.setVersionNumber(1);
		v1.setFileName(storedFileName);
		v1.setModifiedUser(item.getAuthor());
		v1.setModifiedDate(ContentLibraryService.now());
		// Seed the v1 status so the history dialog shows the correct state
		// for the first version of a new upload.
		v1.setStatus(item.getStatus());
		item.getVersions().add(v1);

		Path folder = library.getItemFolder(item.getId());
		Files.createDirectories(folder);
		Files.copy(file.getInputStream(), folder.resolve(storedFileName),
				java.nio.file.StandardCopyOption.REPLACE_EXISTING);

		store.getItems().add(item);
		library.save(store);

		return ResponseEntity.ok(item);
	}

	// ---------- 4. Save a new version (DOCX editor autosave) ----------
	@PostMapping("/items/{id}/versions")
	public ResponseEntity<?> saveNewVersion(@PathVariable("id") String id,
			@RequestBody SaveVersionRequest body) throws Exception {
		if (body == null || isBlank(body.getDocumentData())) {
			return badRequest("DocumentData (SFDT JSON) is required.");
		}

		// The client posts Syncfusion SFDT directly. Convert to DOCX the same
		// way the WordEditorController.Save endpoint does:
		//   1. WordProcessorHelper.save(sfdt) -> WordDocument
		//   2. document.save(stream, FormatType.Docx) -> DOCX bytes
		WordDocument document;
		try {
			document = WordProcessorHelper.save(body.getDocumentData());
		} catch (Exception ex) {
			return badRequest("Failed to parse SFDT: " + ex.getMessage());
		}

		ByteArrayOutputStream docxStream = new ByteArrayOutputStream();
		try {
			document.save(docxStream, FormatType.Docx);
		} catch (Exception ex) {
			return badRequest("Failed to save DOCX: " + ex.getMessage());
		} finally {
			document.close();
		}

		try {
			ContentItem item = library.addVersion(id, docxStream.toByteArray(),
					isBlank(body.getModifiedUser()) ? "Unknown" : body.getModifiedUser());
			return ResponseEntity.ok(item);
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.notFound().build();
		}
	}

	// ---------- 5. Download the original (or specific version) ----------
	@GetMapping("/items/{id}/download")
	public ResponseEntity<?> download(@PathVariable("id") String id,
			@RequestParam(value = "version", required = false) Integer version) throws Exception {
		ContentItem item = library.findById(id);
		if (item == null) {
			return ResponseEntity.notFound().build();
		}

		int v = version != null ? version : item.getCurrentVersion();
		ContentVersion versionInfo = null;
		for (ContentVersion candidate : item.getVersions()) {
			if (candidate.getVersionNumber() == v) {
				versionInfo = candidate;
				break;
			}
		}
		if (versionInfo == null) {
			return ResponseEntity.notFound().build();
		}

		Path path = library.getItemFolder(id).resolve(versionInfo.getFileName());
		if (!Files.exists(path)) {
			return ResponseEntity.notFound().build();
		}

		byte[] bytes = Files.readAllBytes(path);
		String downloadName = ContentLibraryService.sanitizeFolderName(item.getTitle()) + "_v" + v + ".docx";

		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(
						"application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(downloadName, StandardCharsets.UTF_8)
								.build().toString())
				.body(new ByteArrayResource(bytes));
	}

	// ---------- 5b. Load a stored version as SFDT for the document editor ----------
	// Equivalent of the C# sample's POST /api/documenteditor/LoadDocument: returns
	// the Syncfusion SFDT (JSON) serialization of a stored version so the client
	// document editor can open it directly.
	@GetMapping("/items/{id}/sfdt")
	public ResponseEntity<String> loadSfdt(@PathVariable("id") String id,
			@RequestParam(value = "version", required = false) Integer version) throws Exception {
		ContentItem item = library.findById(id);
		if (item == null) {
			return ResponseEntity.notFound().build();
		}

		int v = version != null ? version : item.getCurrentVersion();
		ContentVersion versionInfo = null;
		for (ContentVersion candidate : item.getVersions()) {
			if (candidate.getVersionNumber() == v) {
				versionInfo = candidate;
				break;
			}
		}
		if (versionInfo == null) {
			return ResponseEntity.notFound().build();
		}

		Path path = library.getItemFolder(id).resolve(versionInfo.getFileName());
		if (!Files.exists(path)) {
			return ResponseEntity.notFound().build();
		}

		String sfdt = WordProcessorHelper.load(Files.newInputStream(path),
				com.syncfusion.ej2.wordprocessor.FormatType.Docx);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/json"))
				.body(sfdt);
	}

	// ---------- 6. Delete an item ----------
	@DeleteMapping("/items/{id}")
	public ResponseEntity<?> delete(@PathVariable("id") String id) {
		boolean removed = library.delete(id);
		if (!removed) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().build();
	}

	// ---------- 7. Update status (workflow transition) ----------
	@PostMapping("/items/{id}/status")
	public ResponseEntity<?> updateStatus(@PathVariable("id") String id, @RequestBody StatusUpdate body) {
		if (body == null || isBlank(body.getStatus())) {
			return badRequest("Status is required.");
		}
		ContentItem item = library.updateStatus(id, body.getStatus().trim());
		if (item == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(item);
	}

	// ---------- 8. Update editable metadata ----------
	@PostMapping("/items/{id}/metadata")
	public ResponseEntity<?> updateMetadata(@PathVariable("id") String id, @RequestBody MetadataUpdate body) {
		if (body == null) {
			return badRequest("Body is required.");
		}
		ContentItem item = library.updateMetadata(id, body.getTitle(), body.getCategory(),
				body.getStatus(), body.getAuthor(), body.getVersion());
		if (item == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(item);
	}

	// ---------- 9. Combined DOCX export ----------
	@PostMapping("/export/combined-docx")
	public ResponseEntity<?> exportCombinedDocx(@RequestBody List<String> ids) throws Exception {
		if (ids == null || ids.isEmpty()) {
			return badRequest("Select at least one item.");
		}

		List<ContentItem> selected = resolveItems(ids);
		if (selected.isEmpty()) {
			return badRequest("None of the supplied items were found.");
		}

		byte[] combined = buildCombinedDocx(selected);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(
						"application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("Combined.docx", StandardCharsets.UTF_8)
								.build().toString())
				.body(new ByteArrayResource(combined));
	}

	// ---------- 9b. Merge selected DOCX items and save as a new library item ----------
	// Performs the same merge as exportCombinedDocx but persists the resulting
	// .docx as a new ContentItem in the library instead of returning a download.
	@PostMapping("/merge-docx")
	public ResponseEntity<?> mergeAndSaveDocx(@RequestBody MergeRequest request) throws Exception {
		List<String> ids = request != null ? request.getIds() : null;
		if (ids == null || ids.size() < 2) {
			return badRequest("Select at least two items to merge.");
		}

		List<ContentItem> selected = resolveItems(ids);
		if (selected.size() < 2) {
			return badRequest("None of the supplied items were found.");
		}
		for (ContentItem item : selected) {
			String currentFile = item.getCurrentFile();
			if (currentFile == null || !currentFile.toLowerCase().endsWith(".docx")) {
				return badRequest("Merge only accepts DOCX items.");
			}
		}

		byte[] combinedBytes = buildCombinedDocx(selected);

		// User-supplied metadata wins; missing fields fall back to defaults.
		ContentItem newItem = new ContentItem();
		newItem.setTitle(isBlank(request.getTitle())
				? "Combined \u2014 " + selected.size() + " items \u2014 "
						+ java.time.LocalDateTime.now().format(
								java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
				: request.getTitle().trim());
		newItem.setVersion(isBlank(request.getVersion()) ? "1" : request.getVersion().trim());
		newItem.setCategory(isBlank(request.getCategory()) ? "Other" : request.getCategory().trim());
		newItem.setStatus(isBlank(request.getStatus()) ? "Draft" : request.getStatus().trim());
		String author = isBlank(request.getAuthor()) ? "Merge" : request.getAuthor().trim();
		newItem.setAuthor(author);
		newItem.setCurrentFile("v1.docx");
		newItem.setCurrentVersion(1);
		newItem.setCreatedDate(ContentLibraryService.now());
		newItem.setModifiedDate(ContentLibraryService.now());

		ContentVersion newVersion = new ContentVersion();
		newVersion.setVersionNumber(1);
		newVersion.setFileName("v1.docx");
		newVersion.setModifiedUser(author);
		newVersion.setModifiedDate(ContentLibraryService.now());
		newVersion.setStatus(newItem.getStatus());
		newItem.getVersions().add(newVersion);

		ContentLibraryStore store = library.load();
		newItem.setId(library.buildUniqueFolderName(newItem.getTitle(), store));
		Path folder = library.getItemFolder(newItem.getId());
		Files.createDirectories(folder);
		Files.write(library.getVersionFile(newItem.getId(), 1), combinedBytes);

		store.getItems().add(newItem);
		library.save(store);

		return ResponseEntity.ok(newItem);
	}

	// NOTE: The C# sample also exposes POST /api/contentlibrary/export/excel
	// (metadata + plain-text workbook). Syncfusion XlsIO is not available on
	// the Java platform, so that endpoint is intentionally not implemented.

	// ---------- Helpers ----------

	private List<ContentItem> resolveItems(List<String> ids) {
		List<ContentItem> all = library.list();
		List<ContentItem> selected = new ArrayList<ContentItem>();
		for (String id : ids) {
			for (ContentItem item : all) {
				if (item.getId().equals(id)) {
					selected.add(item);
					break;
				}
			}
		}
		return selected;
	}

	/**
	 * Builds the combined DOCX used by both the combined-docx export and the
	 * merge-docx endpoints: a metadata header per source item followed by the
	 * item's DOCX body, with gray separator lines between items.
	 */
	private byte[] buildCombinedDocx(List<ContentItem> selected) throws Exception {
		WordDocument doc = new WordDocument();
		try {
			com.syncfusion.docio.IWSection section = doc.addSection();
			section.getPageSetup().getMargins().setAll(72f);

			for (int idx = 0; idx < selected.size(); idx++) {
				ContentItem item = selected.get(idx);
				Path filePath = library.getItemFolder(item.getId()).resolve(item.getCurrentFile());

				// Metadata header
				com.syncfusion.docio.IWParagraph titlePara = section.addParagraph();
				titlePara.applyStyle(BuiltinStyle.Heading1);
				titlePara.appendText("Title: " + item.getTitle());

				com.syncfusion.docio.IWParagraph metaPara = section.addParagraph();
				metaPara.appendText("Version: " + item.getVersion()
						+ "    Category: " + item.getCategory()
						+ "    Status: " + item.getStatus()
						+ "    Author: " + item.getAuthor()
						+ "    Modified: " + shortenDate(item.getModifiedDate()));

				section.addParagraph(); // blank line

				// Body — only DOCX items can be merged directly
				String currentFile = item.getCurrentFile();
				if (currentFile != null && currentFile.toLowerCase().endsWith(".docx")
						&& Files.exists(filePath)) {
					try (var srcStream = Files.newInputStream(filePath)) {
						WordDocument srcDoc = new WordDocument(srcStream, FormatType.Docx);
						try {
							srcDoc.getSections().get(0).setBreakCode(SectionBreakCode.NoBreak);
							doc.importContent(srcDoc, ImportOptions.UseDestinationStyles);
						} finally {
							srcDoc.close();
						}
					}
				} else {
					com.syncfusion.docio.IWParagraph p = section.addParagraph();
					p.appendText("[Non-DOCX content — download the original file to view.]");
				}

				// Separator between items
				if (idx < selected.size() - 1) {
					com.syncfusion.docio.IWParagraph sep = doc.getLastSection().addParagraph();
					com.syncfusion.docio.IWTextRange dash = sep.appendText(
							"------------------------------------------------");
					dash.getCharacterFormat().setTextColor(ColorSupport.getGray());
					doc.getLastSection().addParagraph();
				}
			}

			ByteArrayOutputStream combined = new ByteArrayOutputStream();
			doc.save(combined, FormatType.Docx);
			return combined.toByteArray();
		} finally {
			doc.close();
		}
	}

	private static String shortenDate(String iso) {
		if (iso != null && iso.length() >= 10) {
			return iso.substring(0, 10);
		}
		return iso != null ? iso : "";
	}

	private static String sanitizeFileNameBase(String name) {
		if (name == null || name.isEmpty()) {
			return "Document";
		}
		String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
		StringBuilder sb = new StringBuilder(base.length());
		for (char c : base.toCharArray()) {
			sb.append(Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' ? c : '_');
		}
		return sb.toString();
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static ResponseEntity<String> badRequest(String message) {
		return ResponseEntity.badRequest().body(message);
	}
}