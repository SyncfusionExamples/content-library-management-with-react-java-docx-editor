package ej2.webservices;

import java.util.ArrayList;
import java.util.List;

/** Payload for POST /api/contentlibrary/merge-docx. All fields optional except ids. */
public class MergeRequest {

	private List<String> ids = new ArrayList<String>();
	private String title;
	private String category;
	private String status;
	private String author;
	private String version;

	public List<String> getIds() { return ids; }
	public void setIds(List<String> ids) { this.ids = ids; }

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