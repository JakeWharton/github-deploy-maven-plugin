package com.jakewharton.maven.plugin.github_deploy;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
	/** Existing download check debugging message. */
	static final String DEBUG_CHECK_DOWNLOAD = STRINGS.getString("DEBUG_CHECK_DOWNLOAD");
	/** No settings.xml GitHub credentials debugging message. */
	static final String DEBUG_NO_SETTINGS_CREDENTIALS = STRINGS.getString("DEBUG_NO_SETTINGS_CREDENTIALS");
	/** Execution done debugging message. */
	static final String DEBUG_DONE = STRINGS.getString("DEBUG_DONE");
	
	/** Git command to get GitHub user login. */
	private static final String[] GIT_GITHUB_USER = new String[] { "git", "config", "--global", "github.user" };
	/** Git command to get GitHub user token. */
	private static final String[] GIT_GITHUB_TOKEN = new String[] { "git", "config", "--global", "github.token" };
	/** Regular expression to validate the pom.xml's SCM value. */
	private static final Pattern REGEX_REPO = Pattern.compile("^scm:git:git@github.com:(.+?)/(.+?)\\.git$");
	/** Regular expression to locate existing download entries. */
	private static final String REGEX_DOWNLOADS = "<a href=\"/%1$s/downloads/([0-9]+)\"(?:.*?)'value', '([0-9a-f]+)'(?:.*?)<a href=\"/downloads/%1$s/(.*?)\">";
	/** Repository seperator between owner and name. */
	private static final String REPO_SEPERATOR = "/";
	/** URL target for GitHub repo downloads. */
	private static final String URL_DOWNLOADS = "https://github.com/%s/downloads";
	/** URL target for GitHu repo downloads (including authentication). */
	private static final String URL_DOWNLOADS_WITH_AUTH = URL_DOWNLOADS + "?login=%s&token=%s";
	/** URL target for deleting existing download. */
	private static final String URL_DOWNLOAD_DELETE = URL_DOWNLOADS + "/%s";
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
		//Perform initialization
		this.initialize();

		//Load repository data
		this.loadRepositoryInformation();
		this.loadRepositoryCredentials();


		/** CHECK EXISTING DOWNLOADS **/


		//Perform existing downloads request
		this.getLog().info(INFO_CHECK_DOWNLOADS);
		String dlCheckUrl = String.format(URL_DOWNLOADS_WITH_AUTH, this.repo, this.githubLogin, this.githubToken);
		this.getLog().debug("CHECK DOWNLOADS URLL " + dlCheckUrl);
		HttpGet dlCheck = new HttpGet(dlCheckUrl);
		String dlCheckContent = this.checkedExecute(dlCheck, HttpStatus.SC_OK, ERROR_CHECK_DOWNLOADS);

		//Check for existing download for current artifact
		Pattern downloadsRegex = Pattern.compile(String.format(REGEX_DOWNLOADS, this.repo), Pattern.DOTALL);
		Matcher downloads = downloadsRegex.matcher(dlCheckContent);
		while (downloads.find()) {
			this.getLog().debug(String.format(DEBUG_CHECK_DOWNLOAD, downloads.group(1)));
			if (this.file.getName().equals(downloads.group(3))) {
				if (this.replaceExisting) {
					//Get download's id and authentication token
					int downloadId = Integer.parseInt(downloads.group(1));
					String authToken = downloads.group(2);
					this.getLog().debug("DOWNLOAD ID: " + downloadId);
					this.getLog().debug("AUTH TOKEN: " + authToken);
					
					//Setup download delete request
					this.getLog().info(INFO_DELETE_EXISTING);
					String dlDeleteUrl = String.format(URL_DOWNLOAD_DELETE, this.repo, downloadId);
					this.getLog().debug("DOWNLOAD DELETE URL: " + dlDeleteUrl);					
					HttpPost dlDelete = new HttpPost(dlDeleteUrl);
					BasicHttpEntity dlDeleteEntity = new BasicHttpEntity();
					String dlDeleteBody = String.format(ENTITY_DELETE_DOWNLOAD, this.githubLogin, this.githubToken, authToken);
					dlDeleteEntity.setContent(IOUtils.toInputStream(dlDeleteBody));
					dlDelete.setEntity(dlDeleteEntity);
					
					//Perform request
					this.checkedExecute(dlDelete, HttpStatus.SC_MOVED_TEMPORARILY, ERROR_DOWNLOAD_DELETE);
					break;
				} else {
					this.error(ERROR_DOWNLOAD_EXISTS);
				}
			}
		}

		
		/** SEND DEPLOY INFORMATION AND GET S3 CREDENTIALS **/
		
		
		//Perform upload info request
		this.getLog().info(INFO_DEPLOY_INFO);
		String deployInfoUrl = String.format(URL_DOWNLOADS, this.repo);
		this.getLog().debug("DEPLOY INFO URL: " + deployInfoUrl);
		HttpPost deployInfo = new HttpPost(deployInfoUrl);
		BasicHttpEntity deployInfoEntity = new BasicHttpEntity(); 
		String deployInfoBody = String.format(ENTITY_DEPLOY_INFO, this.githubLogin, this.githubToken, this.file.length(), MIME_TYPE, this.file.getName());
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
		String key = prefix + this.file.getName();
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
		
		
		/** DEPLOY ARTIFACT TO S3 **/
		
		
		//Set up deploy request
		this.getLog().info(String.format(INFO_DEPLOY, this.file.getName()));
		this.getLog().debug("DEPLOY URL: " + URL_DEPLOY);
		HttpPost upload = new HttpPost(URL_DEPLOY);
		MultipartEntity uploadEntity = new MultipartEntity();
		try {
			uploadEntity.addPart(HTTP_PROPERTY_KEY, new StringBody(key));
			uploadEntity.addPart(HTTP_PROPERTY_ACL, new StringBody(acl));
			uploadEntity.addPart(HTTP_PROPERTY_FILENAME, new StringBody(this.file.getName()));
			uploadEntity.addPart(HTTP_PROPERTY_POLICY, new StringBody(policy));
			uploadEntity.addPart(HTTP_PROPERTY_AWS_ACCESS_ID, new StringBody(accessKeyId));
			uploadEntity.addPart(HTTP_PROPERTY_SIGNATURE, new StringBody(signature));
			uploadEntity.addPart(HTTP_PROPERTY_SUCCESS_ACTION_STATUS, new StringBody(Integer.toString(HttpStatus.SC_CREATED)));
			uploadEntity.addPart(HTTP_PROPERTY_CONTENT_TYPE, new StringBody(MIME_TYPE));
			uploadEntity.addPart(HTTP_PROPERTY_FILE, new FileBody(this.file));
		} catch (UnsupportedEncodingException e) {
			this.error(e, ERROR_ENCODING);
		}
		upload.setEntity(uploadEntity);
		
		//Perform deployment
		this.checkedExecute(upload, HttpStatus.SC_CREATED, ERROR_DEPLOYING);
		
		this.getLog().debug(DEBUG_DONE);
	}
	
	private void initialize() throws MojoFailureException {
		//Do not run if we have been told to skip
		if (this.skip) {
			this.getLog().info(INFO_SKIP);
			return;
		}
		
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
}
