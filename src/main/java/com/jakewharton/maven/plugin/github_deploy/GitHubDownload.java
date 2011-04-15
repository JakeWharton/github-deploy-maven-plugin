package com.jakewharton.maven.plugin.github_deploy;

/**
 * Simple POJO for representing an existing GitHub download.
 * 
 * @author Jake Wharton <jakewharton@gmail.com>
 */
class GitHubDownload {
	private long id;
	private String fileName;
	private String name;
	private String url;
	private String deleteUrl;
	
	public long getId() {
		return this.id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public String getFileName() {
		return this.fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	public String getName() {
		return this.name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getUrl() {
		return this.url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public String getDeleteUrl() {
		return this.deleteUrl;
	}
	public void setDeleteUrl(String deleteUrl) {
		this.deleteUrl = deleteUrl;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof String) {
			return this.getFileName().equals((String)obj);
		}
		return super.equals(obj);
	}
}
