package ej2.webservices;

/** Payload for POST /api/contentlibrary/items/{id}/status. */
public class StatusUpdate {

	private String status;

	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
}