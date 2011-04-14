package com.jakewharton.maven.plugin.github_deploy;

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Tests for {@link GitHubDeployMojo}.
 * 
 * @author Jake Wharton <jakewharton@gmail.com>
 */
public class GitHubDeployMojoTest extends TestCase {
	/**
	 * Check to make sure the properties file was loaded and that all of the
	 * strings are available.
	 */
	public void test_resourcesLoaded() {
		//Check properties file loaded
		Assert.assertNotNull(GitHubDeployMojo.STRINGS);
		
		//Check strings loaded
		Assert.assertNotNull(GitHubDeployMojo.INFO_SKIP);
		Assert.assertNotNull(GitHubDeployMojo.INFO_CHECK_DOWNLOADS);
		Assert.assertNotNull(GitHubDeployMojo.INFO_DEPLOY_INFO);
		Assert.assertNotNull(GitHubDeployMojo.INFO_DEPLOY);
		Assert.assertNotNull(GitHubDeployMojo.INFO_DELETE_EXISTING);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_NOT_FOUND);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_OFFLINE);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_SCM_INVALID);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_DOWNLOAD_EXISTS);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_JSON_PARSE);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_JSON_PROPERTIES);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_DEPLOYING);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_ENCODING);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_CHECK_DOWNLOADS);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_DOWNLOAD_DELETE);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_DEPLOY_INFO);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_NO_CREDENTIALS);
		Assert.assertNotNull(GitHubDeployMojo.ERROR_AUTH_TOKEN);
		Assert.assertNotNull(GitHubDeployMojo.DEBUG_CHECK_DOWNLOAD);
		Assert.assertNotNull(GitHubDeployMojo.DEBUG_NO_SETTINGS_CREDENTIALS);
		Assert.assertNotNull(GitHubDeployMojo.DEBUG_DONE);
	}
}
