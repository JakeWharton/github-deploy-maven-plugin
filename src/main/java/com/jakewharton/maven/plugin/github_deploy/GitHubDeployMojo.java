package com.jakewharton.maven.plugin.github_deploy;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Deploy a project's packaged artifacts to its corresponding GitHub project.
 * 
 * @phase deploy
 * @goal deploy
 * @author Jake Wharton <jakewharton@gmail.com>
 */
public class GitHubDeployMojo extends AbstractMojo {
	/** String resources for messages. */
	static final ResourceBundle STRINGS = ResourceBundle.getBundle(GitHubDeployMojo.class.getPackage().getName() + ".Strings");
	/** Skipped execution message. */
	static final String INFO_SKIP = STRINGS.getString("INFO_SKIP");
	/** Checking downloads message. */
	static final String INFO_CHECK_DOWNLOADS = STRINGS.getString("INFO_CHECK_DOWNLOADS");
	/** Sending deploy information message. */
	static final String INFO_DEPLOY_INFO = STRINGS.getString("INFO_DEPLOY_INFO");
	/** Deploying message. */
	static final String INFO_DEPLOY = STRINGS.getString("INFO_DEPLOY");
	/** Deleting existing download message. */
	static final String INFO_DELETE_EXISTING = STRINGS.getString("INFO_DELETE_EXISTING");
	/** Artifact not found error message. */
	static final String ERROR_NOT_FOUND = STRINGS.getString("ERROR_NOT_FOUND");
	/** Maven offline error message. */
	static final String ERROR_OFFLINE = STRINGS.getString("ERROR_OFFLINE");
	/** Invalid SCM URL error message. */
	static final String ERROR_SCM_INVALID = STRINGS.getString("ERROR_SCM_INVALID");
	/** Download exists error message. */
	static final String ERROR_DOWNLOAD_EXISTS = STRINGS.getString("ERROR_DOWNLOAD_EXISTS");
	/** JSON parsing error message. */
	static final String ERROR_JSON_PARSE = STRINGS.getString("ERROR_JSON_PARSE");
	/** JSON property retreival error message. */
	static final String ERROR_JSON_PROPERTIES = STRINGS.getString("ERROR_JSON_PROPERTIES");
	/** Deployment error message. */
	static final String ERROR_DEPLOYING = STRINGS.getString("ERROR_DEPLOYING");
	/** Property encoding error message. */
	static final String ERROR_ENCODING = STRINGS.getString("ERROR_ENCODING");
	/** Check downloads error message. */
	static final String ERROR_CHECK_DOWNLOADS = STRINGS.getString("ERROR_CHECK_DOWNLOADS");
	/** Delete existing download error message. */
	static final String ERROR_DOWNLOAD_DELETE = STRINGS.getString("ERROR_DOWNLOAD_DELETE");
	/** Send deploy info error message. */
	static final String ERROR_DEPLOY_INFO = STRINGS.getString("ERROR_DEPLOY_INFO");
	/** GitHub credentials error message. */
	static final String ERROR_NO_CREDENTIALS = STRINGS.getString("ERROR_NO_CREDENTIALS");
	/** GitHub authentication token error message. */
	static final String ERROR_AUTH_TOKEN = STRINGS.getString("ERROR_AUTH_TOKEN");
	/** Existing download check debugging message. */
	static final String DEBUG_CHECK_DOWNLOAD = STRINGS.getString("DEBUG_CHECK_DOWNLOAD");
	/** No settings.xml GitHub credentials debugging message. */
	static final String DEBUG_NO_SETTINGS_CREDENTIALS = STRINGS.getString("DEBUG_NO_SETTINGS_CREDENTIALS");
	/** Successful deployment debugging message. */
	static final String DEBUG_DEPLOY_SUCCESS = STRINGS.getString("DEBUG_DEPLOY_SUCCESS");
	/** Execution done debugging message. */
	static final String DEBUG_DONE = STRINGS.getString("DEBUG_DONE");
	
	/** Git command to get GitHub user login. */
	private static final String[] GIT_GITHUB_USER = new String[] { "git", "config", "--global", "github.user" };
	/** Git command to get GitHub user token. */
	private static final String[] GIT_GITHUB_TOKEN = new String[] { "git", "config", "--global", "github.token" };
	/** Regular expression to validate the pom.xml's SCM value. */
	private static final Pattern REGEX_REPO = Pattern.compile("^scm:git:git@github.com:(.+?)/(.+?)\\.git$");
	/** Regular expression to get the downloads authentication token. */
	private static final Pattern REGEX_AUTH_TOKEN = Pattern.compile("<script>window._auth_token = \"([0-9a-f]+)\"</script>");
	/** Regular expression to locate existing download entries. */
	private static final String REGEX_DOWNLOADS = "<a href=\"(/%1$s/downloads/([0-9]+))\"(?:.*?)<a href=\"(/downloads/%1$s/(.*?))\">(.*?)</a>";
	/** Repository seperator between owner and name. */
	private static final String REPO_SEPERATOR = "/";
	/** GitHub base URL. */
	private static final String URL_BASE = "https://github.com";
	/** URL target for GitHub repo downloads. */
	private static final String URL_DOWNLOADS = URL_BASE + "/%s/downloads";
	/** URL target for GitHu repo downloads (including authentication). */
	private static final String URL_DOWNLOADS_WITH_AUTH = URL_DOWNLOADS + "?login=%s&token=%s";
	/** URL target for artifact deployment. */
	private static final String URL_DEPLOY = "https://github.s3.amazonaws.com/";
	/** HTTP entity for sending deploy info. */
	private static final String ENTITY_DEPLOY_INFO = "login=%s&token=%s&file_length=%s&content_type=%s&file_name=%s&description=";
	/** HTTP entity for deleting existing download. */
	private static final String ENTITY_DELETE_DOWNLOAD = "login=%s&token=%s&_method=delete&authenticity_token=";
	/** Settings server ID. */
	private static final String SETTINGS_SERVER_ID = "github-deploy";
	/** Artifact MIME type. */
	private static final String MIME_TYPE = "application/octet-stream";
	/** HTTP POST property name for artifact key. */
	private static final String HTTP_PROPERTY_KEY = "key";
	/** HTTP POST property name for ACL. */
	private static final String HTTP_PROPERTY_ACL = "acl";
	/** HTTP POST property name for artifact file name. */
	private static final String HTTP_PROPERTY_FILENAME = "Filename";
	/** HTTP POST property name for policy. */
	private static final String HTTP_PROPERTY_POLICY = "policy";
	/** HTTP POST property name for AWS access ID. */
	private static final String HTTP_PROPERTY_AWS_ACCESS_ID = "AWSAccessKeyId";
	/** HTTP POST property name for signature. */
	private static final String HTTP_PROPERTY_SIGNATURE = "signature";
	/** HTTP POST property name for successful status code. */
	private static final String HTTP_PROPERTY_SUCCESS_ACTION_STATUS = "success_action_status";
	/** HTTP POST property name for artifact MIME type. */
	private static final String HTTP_PROPERTY_CONTENT_TYPE = "Content-Type";
	/** HTTP POST property name for artifact data. */
	private static final String HTTP_PROPERTY_FILE = "file";
	/** JSON property name of prefix value. */
	private static final String JSON_PROPERTY_PREFIX = "prefix";
	/** JSON property name of policy value. */
	private static final String JSON_PROPERTY_POLICY = "policy";
	/** JSON property name of ACL value. */
	private static final String JSON_PROPERTY_ACL = "acl";
	/** JSON property name of access key ID value. */
	private static final String JSON_PROPERTY_ACCESS_KEY_ID = "accesskeyid";
	/** JSON property name of signature value. */
	private static final String JSON_PROPERTY_SIGNATURE = "signature";
	
	
	/**
	 * SCM developer connection URL.
	 * 
	 * @parameter expression="${project.scm.developerConnection}"
	 * @required
	 */
	private String scmUrl;
	
	/**
	 * Target repository owner.
	 * 
	 * @parameter
	 */
	private String repoOwner;
	
	/**
	 * Target repository name.
	 * 
	 * @parameter
	 */
	private String repoName;
	
	/**
	 * Target repository string in the format "owner/name".
	 */
	private String repo;
	
	/**
	 * Skip execution.
	 * 
	 * @parameter default-value="false"
	 */
	private boolean skip;
	
	/**
	 * Replace existing downloads.
	 * 
	 * @parameter default-value="false"
	 */
	private boolean replaceExisting;
	
	/**
	 * GitHub login name.
	 * 
	 * @parameter
	 */
	private String githubLogin;
	
	/**
	 * GitHub authentication token.
	 * 
	 * @parameter
	 */
	private String githubToken;
	
    /**
     * Packaged artifact file.
     * 
     * @parameter default-value="${project.artifact.file}"
     * @required
     * @readonly
     */
    private File file;
	
	/**
	 * Maven settings.
	 * 
	 * @parameter default-value="${settings}"
	 * @required
	 * @readonly
	 */
	private Settings settings;
	
	/**
	 * List of existing downloads for the GitHub repo.
	 */
	private Map<String, GitHubDownload> existingDownloads;
	
	/**
	 * GitHub authentication token.
	 */
	private String authToken;
	
	/**
	 * Common HTTP client.
	 */
	private final HttpClient httpClient = new DefaultHttpClient();
	
	
	/**
	 * Display and throw error.
	 * 
	 * @param message Error message.
	 * @param messageArgs Any arguments for formatting the error message.
	 * @throws MojoFailureException
	 */
	private void error(String message, Object... messageArgs) throws MojoFailureException {
		String formattedMessage = String.format(message, messageArgs);
		this.getLog().error(formattedMessage);
		throw new MojoFailureException(formattedMessage);
	}
	
	/**
	 * Display and throw error.
	 * 
	 * @param cause Cause of the error.
	 * @param message Error message.
	 * @param messageArgs Any arguments for formatting the error message.
	 * @throws MojoFailureException
	 */
	private void error(Throwable cause, String message, Object... messageArgs) throws MojoFailureException {
		String formattedMessage = String.format(message, messageArgs);
		this.getLog().error(formattedMessage);
		this.getLog().error(cause.getLocalizedMessage());
		throw new MojoFailureException(formattedMessage, cause);
	}
	
	/**
	 * Execute an HTTP request in a checked manner.
	 * 
	 * @param request Request to execute.
	 * @param expectedStatus Expected HTTP return status.
	 * @param errorMessage Error message to display is status does not match.
	 * @return Contents of return body.
	 * @throws MojoFailureException
	 */
	private String checkedExecute(HttpUriRequest request, int expectedStatus, String errorMessage) throws MojoFailureException {
		try {
			HttpResponse response = this.httpClient.execute(request);
			
			int status = response.getStatusLine().getStatusCode();
			this.getLog().debug("HTTP status code: " + status);
			if (status == expectedStatus) {
				return IOUtils.toString(response.getEntity().getContent());
			}
		} catch (ClientProtocolException e) {
			this.error(e, errorMessage);
		} catch (IOException e) {
			this.error(e, errorMessage);
		}
		
		this.error(errorMessage);
		return null; //Never reached
	}
	
	/**
	 * Fetch a JSON object property in a checked manner.
	 * 
	 * @param object JSON object.
	 * @param propertyName Name of property.
	 * @return Property value.
	 * @throws MojoFailureException
	 */
	private String checkedJsonProperty(JSONObject object, String propertyName) throws MojoFailureException {
		try {
			return object.getString(propertyName);
		} catch (JSONException e) {
			this.error(e, ERROR_JSON_PROPERTIES);
		}
		return null; //Never reached
	}
	
	@Override
	public void execute() throws MojoFailureException {
		//Do not run if we have been told to skip
		if (this.skip) {
			this.getLog().info(INFO_SKIP);
			return;
		}
		
		//Perform initialization
		this.initialize();

		//Load repository data
		this.loadRepositoryInformation();
		this.loadRepositoryCredentials();

		//Perform existing downloads request
		this.getLog().info(INFO_CHECK_DOWNLOADS);
		String dlCheckUrl = String.format(URL_DOWNLOADS_WITH_AUTH, this.repo, this.githubLogin, this.githubToken);
		this.getLog().debug("CHECK DOWNLOADS URL " + dlCheckUrl);
		HttpGet dlCheck = new HttpGet(dlCheckUrl);
		String dlCheckContent = this.checkedExecute(dlCheck, HttpStatus.SC_OK, ERROR_CHECK_DOWNLOADS);
		
		//Get GitHub auth token
		this.parseGitHubAuthenticationToken(dlCheckContent);
		//Get all existing downloads
		this.parseExistingDownloads(dlCheckContent);

		//Check for existing download for current artifact
		if (this.existingDownloads.containsKey(this.file.getName())) {
			if (this.replaceExisting) {
				GitHubDownload existing = this.existingDownloads.get(this.file.getName());
				this.deleteExistingDownload(existing);
			} else {
				this.error(ERROR_DOWNLOAD_EXISTS);
			}
		}

		this.deploy(this.file);
		this.getLog().debug(DEBUG_DONE);
	}
	
	/**
	 * Perform plugin initialization.
	 * 
	 * @throws MojoFailureException
	 */
	private void initialize() throws MojoFailureException {
		//Check we are not working offline
		if (this.settings.isOffline()) {
			this.error(ERROR_OFFLINE);
		}
		
		//Get the packaged artifact
        if (!this.file.exists()) {
        	this.error(ERROR_NOT_FOUND, this.file.getName());
        }
		this.getLog().debug("PATH: " + this.file.getAbsolutePath());
		this.getLog().debug("NAME: " + this.file.getName());
	}
	
	/**
	 * Load the target repository information. This can be specified directly in
	 * the plugin configuration or can be inferred from the SCM developer
	 * connection URL.
	 * 
	 * @throws MojoFailureException
	 */
	private void loadRepositoryInformation() throws MojoFailureException {
		if (StringUtils.isBlank(this.repoOwner) || StringUtils.isBlank(this.repoName)) {
			//Get the target repository
			Matcher match = REGEX_REPO.matcher(this.scmUrl);
			if (!match.matches()) {
				this.error(ERROR_SCM_INVALID);
			}
			this.getLog().debug("SCM URL: " + this.scmUrl);
			
			//Get the repo owner and name from the match
			this.repoOwner = match.group(1);
			this.repoName = match.group(2);
		}
		this.repo = this.repoOwner + REPO_SEPERATOR + this.repoName;
		this.getLog().debug("REPO OWNER: " + this.repoOwner);
		this.getLog().debug("REPO NAME: " + this.repoName);
		this.getLog().debug("REPO: " + this.repo);
	}
	
	/**
	 * Load the GitHub user login and token. These can be specified directly in
	 * the plugin configuration, in a <code>&lt;server&gt;</code> section of the
	 * user's <code>settings.xml</code> file, or in the global or local git
	 * configuration.
	 * 
	 * @throws MojoFailureException
	 */
	private void loadRepositoryCredentials() throws MojoFailureException {
		if (StringUtils.isBlank(this.githubLogin) || StringUtils.isBlank(this.githubToken)) {
			//Attempt to get GitHub credentials from settings and git if not already specified
			Server githubDeploy = this.settings.getServer(SETTINGS_SERVER_ID);
			if (githubDeploy != null) {
				this.githubLogin = githubDeploy.getUsername();
				this.githubToken = githubDeploy.getPassphrase();
			} else {
				try {
					this.getLog().debug(DEBUG_NO_SETTINGS_CREDENTIALS);
					this.githubLogin = IOUtils.toString(Runtime.getRuntime().exec(GIT_GITHUB_USER).getInputStream());
					this.githubToken = IOUtils.toString(Runtime.getRuntime().exec(GIT_GITHUB_TOKEN).getInputStream());
				} catch (IOException e) {}
			}
			if (StringUtils.isBlank(this.githubLogin) || StringUtils.isBlank(this.githubToken)) {
				this.error(ERROR_NO_CREDENTIALS);
			}
		}
		this.githubLogin = this.githubLogin.trim();
		this.githubToken = this.githubToken.trim();
		this.getLog().debug("LOGIN: " + this.githubLogin);
		this.getLog().debug("TOKEN: " + this.githubToken);
	}
	
	/**
	 * Parse a list of existing downloads from the page contents.
	 * 
	 * @param content Page contents.
	 */
	private void parseExistingDownloads(String content) {
		this.existingDownloads = new HashMap<String, GitHubDownload>();
		
		String regex = String.format(REGEX_DOWNLOADS, this.repo);
		Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
		Matcher matcher = pattern.matcher(content);
		while (matcher.find()) {
			GitHubDownload download = new GitHubDownload();
			
			download.setDeleteUrl(URL_BASE + matcher.group(1));
			download.setId(Long.parseLong(matcher.group(2)));
			download.setUrl(URL_BASE + matcher.group(3));
			download.setFileName(matcher.group(4));
			download.setName(matcher.group(5));
			
			this.existingDownloads.put(download.getFileName(), download);
		}
	}
	
	/**
	 * Parse the GitHub authentication token from the page contents.
	 * 
	 * @param content Page contents.
	 * @throws MojoFailureException
	 */
	private void parseGitHubAuthenticationToken(String content) throws MojoFailureException {
		Matcher authTokenMatcher = REGEX_AUTH_TOKEN.matcher(content);
		if (authTokenMatcher.find()) {
			this.authToken = authTokenMatcher.group();
			this.getLog().debug("AUTH TOKEN: " + this.authToken);
		} else {
			this.error(ERROR_AUTH_TOKEN);
		}
	}
	
	/**
	 * Delete an existing download from GitHub.
	 * 
	 * @param download Download to delete.
	 * @throws MojoFailureException
	 */
	private void deleteExistingDownload(GitHubDownload download) throws MojoFailureException {
		//Setup download delete request
		this.getLog().info(INFO_DELETE_EXISTING);
		this.getLog().debug("DOWNLOAD DELETE URL: " + download.getDeleteUrl());					
		HttpPost dlDelete = new HttpPost(download.getDeleteUrl());
		BasicHttpEntity dlDeleteEntity = new BasicHttpEntity();
		String dlDeleteBody = String.format(ENTITY_DELETE_DOWNLOAD, this.githubLogin, this.githubToken, this.authToken);
		dlDeleteEntity.setContent(IOUtils.toInputStream(dlDeleteBody));
		dlDelete.setEntity(dlDeleteEntity);
		
		//Perform request
		this.checkedExecute(dlDelete, HttpStatus.SC_MOVED_TEMPORARILY, ERROR_DOWNLOAD_DELETE);
	}
	
	/**
	 * Deploy an artifact to the GitHub downloads. This method assumes that a
	 * download with the same name does not already exist.
	 * 
	 * @param artifact Artifact for deployment.
	 * @throws MojoFailureException
	 */
	private void deploy(File artifact) throws MojoFailureException {
		//Perform upload info request
		this.getLog().info(INFO_DEPLOY_INFO);
		String deployInfoUrl = String.format(URL_DOWNLOADS, this.repo);
		this.getLog().debug("DEPLOY INFO URL: " + deployInfoUrl);
		HttpPost deployInfo = new HttpPost(deployInfoUrl);
		BasicHttpEntity deployInfoEntity = new BasicHttpEntity(); 
		String deployInfoBody = String.format(ENTITY_DEPLOY_INFO, this.githubLogin, this.githubToken, artifact.length(), MIME_TYPE, artifact.getName());
		deployInfoEntity.setContent(IOUtils.toInputStream(deployInfoBody));
		deployInfo.setEntity(deployInfoEntity);
		String deployInfoContent = this.checkedExecute(deployInfo, HttpStatus.SC_OK, ERROR_DEPLOY_INFO);
		
		//Parse JSON response
		JSONObject postData = null;
		try {
			postData = new JSONObject(deployInfoContent);
		} catch (JSONException e) {
			this.error(e, ERROR_JSON_PARSE);
		}
		
		//Extract needed information
		String prefix = this.checkedJsonProperty(postData, JSON_PROPERTY_PREFIX);
		String key = prefix + artifact.getName();
		String policy = this.checkedJsonProperty(postData, JSON_PROPERTY_POLICY);
		String accessKeyId = this.checkedJsonProperty(postData, JSON_PROPERTY_ACCESS_KEY_ID);
		String signature = this.checkedJsonProperty(postData, JSON_PROPERTY_SIGNATURE);
		String acl = this.checkedJsonProperty(postData, JSON_PROPERTY_ACL);
		this.getLog().debug("PREFIX: " + prefix);
		this.getLog().debug("KEY: " + key);
		this.getLog().debug("POLICY: " + policy);
		this.getLog().debug("ACCESS KEY ID: " + accessKeyId);
		this.getLog().debug("SIGNATURE: " + signature);
		this.getLog().debug("ACL: " + acl);
		
		
		//Set up deploy request
		this.getLog().info(String.format(INFO_DEPLOY, artifact.getName()));
		this.getLog().debug("DEPLOY URL: " + URL_DEPLOY);
		HttpPost upload = new HttpPost(URL_DEPLOY);
		MultipartEntity uploadEntity = new MultipartEntity();
		try {
			uploadEntity.addPart(HTTP_PROPERTY_KEY, new StringBody(key));
			uploadEntity.addPart(HTTP_PROPERTY_ACL, new StringBody(acl));
			uploadEntity.addPart(HTTP_PROPERTY_FILENAME, new StringBody(artifact.getName()));
			uploadEntity.addPart(HTTP_PROPERTY_POLICY, new StringBody(policy));
			uploadEntity.addPart(HTTP_PROPERTY_AWS_ACCESS_ID, new StringBody(accessKeyId));
			uploadEntity.addPart(HTTP_PROPERTY_SIGNATURE, new StringBody(signature));
			uploadEntity.addPart(HTTP_PROPERTY_SUCCESS_ACTION_STATUS, new StringBody(Integer.toString(HttpStatus.SC_CREATED)));
			uploadEntity.addPart(HTTP_PROPERTY_CONTENT_TYPE, new StringBody(MIME_TYPE));
			uploadEntity.addPart(HTTP_PROPERTY_FILE, new FileBody(artifact));
		} catch (UnsupportedEncodingException e) {
			this.error(e, ERROR_ENCODING);
		}
		upload.setEntity(uploadEntity);
		
		//Perform deployment
		this.checkedExecute(upload, HttpStatus.SC_CREATED, ERROR_DEPLOYING);
		this.getLog().debug(String.format(DEBUG_DEPLOY_SUCCESS, artifact.getName(), this.repo));
	}
}
