package ej2.webservices;

/** Payload for POST /api/contentlibrary/items/{id}/metadata. */
public class MetadataUpdate {

	private String title;
	private String category;
	private String status;
	private String author;
	private String version;

	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }

	public String getCategory() { return category; }
	public void setCategory(String category) { this.category = category; }

	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }

	public String getAuthor() { return author; }
	public void setAuthor(String author) { this.author = author; }

	public String getVersion() { return version; }
	public void setVersion(String version) { this.version = version; }
}