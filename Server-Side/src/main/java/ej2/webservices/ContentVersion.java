package ej2.webservices;

/**
 * One stored version of a content item (one file per save).
 * Status is snapshotted at save time so the history view shows
 * what state each version was in when it was authored.
 */
public class ContentVersion {

	private int versionNumber;
	private String fileName;
	private String modifiedUser;
	private String modifiedDate;
	private String status;

	public int getVersionNumber() { return versionNumber; }
	public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }

	public String getFileName() { return fileName; }
	public void setFileName(String fileName) { this.fileName = fileName; }

	public String getModifiedUser() { return modifiedUser; }
	public void setModifiedUser(String modifiedUser) { this.modifiedUser = modifiedUser; }

	public String getModifiedDate() { return modifiedDate; }
	public void setModifiedDate(String modifiedDate) { this.modifiedDate = modifiedDate; }

	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
}