package ej2.webservices;

import java.util.ArrayList;
import java.util.List;

/**
 * Root JSON document stored in App_Data/ContentLibrary/library.json.
 */
public class ContentLibraryStore {

	private List<ContentItem> items = new ArrayList<ContentItem>();

	public List<ContentItem> getItems() { return items; }
	public void setItems(List<ContentItem> items) { this.items = items; }
}