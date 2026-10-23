package ej2.webservices;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reusable content item stored in the Content Library.
 * Files are kept on disk under App_Data/ContentLibrary/{id}/
 * with one file per version (v1.docx, v2.docx, ...).
 */
public class ContentItem {

	private String id = UUID.randomUUID().toString().replace("-", "");
	private String title;
	private String version = "1.0";
	private String category;
	private String status;
	private String author;
	private String createdDate;
	private String modifiedDate;
	private String currentFile;
	private int currentVersion = 1;
	private List<ContentVersion> versions = new ArrayList<ContentVersion>();

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }

	public String getVersion() { return version; }
	public void setVersion(String version) { this.version = version; }

	public String getCategory() { return category; }
	public void setCategory(String category) { this.category = category; }

	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }

	public String getAuthor() { return author; }
	public void setAuthor(String author) { this.author = author; }

	public String getCreatedDate() { return createdDate; }
	public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

	public String getModifiedUser() { return author; }

	public String getModifiedDate() { return modifiedDate; }
	public void setModifiedDate(String modifiedDate) { this.modifiedDate = modifiedDate; }

	public String getCurrentFile() { return currentFile; }
	public void setCurrentFile(String currentFile) { this.currentFile = currentFile; }

	public int getCurrentVersion() { return currentVersion; }
	public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }

	public List<ContentVersion> getVersions() { return versions; }
	public void setVersions(List<ContentVersion> versions) { this.versions = versions; }
}