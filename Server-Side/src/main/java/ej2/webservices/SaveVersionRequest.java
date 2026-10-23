package ej2.webservices;

/** Payload for POST /api/contentlibrary/items/{id}/versions (SFDT autosave). */
public class SaveVersionRequest {

	private String documentData;
	private String modifiedUser;

	public String getDocumentData() { return documentData; }
	public void setDocumentData(String documentData) { this.documentData = documentData; }

	public String getModifiedUser() { return modifiedUser; }
	public void setModifiedUser(String modifiedUser) { this.modifiedUser = modifiedUser; }
}