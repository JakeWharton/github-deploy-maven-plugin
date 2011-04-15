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
	 * Check to make sure the properties file was loaded.
	 */
	public void test_resourcesLoaded() {
		//Check properties file loaded
		Assert.assertNotNull(GitHubDeployMojo.STRINGS);
	}
	
	/**
	 * Check to make sure all of the info strings are available.
	 */
	public void test_infoStrings() {
		//Check info strings
		Assert.assertNotNull(GitHubDeployMojo.INFO_SKIP);
		Assert.assertNotNull(GitHubDeployMojo.INFO_ARTIFACTS);
		Assert.assertNotNull(GitHubDeployMojo.INFO_ARTIFACT_DETAIL);
		Assert.assertNotNull(GitHubDeployMojo.INFO_ARTIFACT_IGNORE);
		Assert.assertNotNull(GitHubDeployMojo.INFO_EXISTING);
		Assert.assertNotNull(GitHubDeployMojo.INFO_EXISTING_DELETE);
		Assert.assertNotNull(GitHubDeployMojo.INFO_DEPLOY);
		Assert.assertNotNull(GitHubDeployMojo.INFO_DEPLOY_SEND);
		Assert.assertNotNull(GitHubDeployMojo.INFO_DEPLOY_UPLOAD);
		Assert.assertNotNull(GitHubDeployMojo.INFO_SUCCESS);
	}
	
	/**
	 * Check to make sure all of the error strings are available.
	 */
	public void test_errorStrings() {
		//Check error strings
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
	}
}
