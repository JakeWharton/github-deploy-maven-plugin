package com.jakewharton.maven.plugin.github_deploy;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
	private static final String[] GIT_GITHUB_USER = new String[] { "git", "config", "--global", "github.user" };
	private static final String[] GIT_GITHUB_TOKEN = new String[] { "git", "config", "--global", "github.token" };
	private static final Pattern REGEX_REPO = Pattern.compile("^scm:git:git@github.com:(.+?)\\.git$");
	private static final String REGEX_DOWNLOADS = "<a href=\"/%1$s/downloads/([0-9]+)\"(?:.*?)'value', '([0-9a-f]+)'(?:.*?)<a href=\"/downloads/%1$s/(.*?)\">";
	private static final String URL_DOWNLOADS = "https://github.com/%s/downloads";
	private static final String URL_DOWNLOADS_WITH_AUTH = URL_DOWNLOADS + "?login=%s&token=%s";
	private static final String URL_DOWNLOAD_DELETE = URL_DOWNLOADS + "/%s";
	private static final String URL_DEPLOY = "https://github.s3.amazonaws.com/";
	private static final String ENTITY_DEPLOY_INFO = "login=%s&token=%s&file_length=%s&content_type=%s&file_name=%s&description=";
	private static final String ENTITY_DELETE_DOWNLOAD = "login=%s&token=%s&_method=delete&authenticity_token=";
	private static final String SETTINGS_PROFILE_NAME = "github-deploy";
	private static final String MIME_TYPE = "application/octet-stream";
	private static final String DEBUG_CHECK_DOWNLOAD = "Checking existing download \"%s\".";
	private static final String DEBUG_NO_SETTINGS_CREDENTIALS = "No GitHub credentials in settings. Attempting to access from git config.";
	private static final String DEBUG_DONE = "Done!";
	private static final String INFO_SKIP = "Skipping artifact deployment.";
	private static final String INFO_CHECK_DOWNLOADS = "Checking existing downloads.";
	private static final String INFO_ARTIFACT_INFO = "Sending artifact information and obtaining S3 upload credentials.";
	private static final String INFO_DEPLOY = "Deploying \"%s\" to remote server.";
	private static final String INFO_DELETE_EXISTING = "Deleting existing download.";
	private static final String ERROR_FILE_NOT_FOUND = "File \"%s\" not found.";
	private static final String ERROR_OFFLINE = "Cannot deploy artifacts when Maven is in offline mode";
	private static final String ERROR_SCM_INVALID = "SCM developer connection is not a valid GitHub repository URL.";
	private static final String ERROR_DOWNLOAD_EXISTS = "Downloads already exists.";
	private static final String ERROR_JSON_PARSE = "Unable to parse JSON.";
	private static final String ERROR_JSON_PROPERTIES = "Unable to retrieve needed JSON properties.";
	private static final String ERROR_DEPLOYING = "Error deploying artifact to remote server.";
	private static final String ERROR_ENCODING = "Error encoding properties for file upload.";
	private static final String ERROR_DOWNLOADS = "Error checking existing downloads.";
	private static final String ERROR_DOWNLOAD_DELETE = "Error deleting existing download.";
	private static final String ERROR_DEPLOY_INFO = "Error fetching deploy information.";
	private static final String ERROR_NO_CREDENTIALS = "Error reading GitHub credentials from settings or git.";
	private static final String HTTP_PROPERTY_KEY = "key";
	private static final String HTTP_PROPERTY_ACL = "acl";
	private static final String HTTP_PROPERTY_FILENAME = "Filename";
	private static final String HTTP_PROPERTY_POLICY = "policy";
	private static final String HTTP_PROPERTY_AWS_ACCESS_ID = "AWSAccessKeyId";
	private static final String HTTP_PROPERTY_SIGNATURE = "signature";
	private static final String HTTP_PROPERTY_SUCCESS_ACTION_STATUS = "success_action_status";
	private static final String HTTP_PROPERTY_CONTENT_TYPE = "Content-Type";
	private static final String HTTP_PROPERTY_FILE = "file";
	private static final String JSON_PROPERTY_PREFIX = "prefix";
	private static final String JSON_PROPERTY_POLICY = "policy";
	private static final String JSON_PROPERTY_ACL = "acl";
	private static final String JSON_PROPERTY_ACCESS_KEY_ID = "accesskeyid";
	private static final String JSON_PROPERTY_SIGNATURE = "signature";
	
	
	/**
	 * SCM developer connection URL.
	 * 
	 * @parameter expression="${project.scm.developerConnection}"
	 * @required
	 */
	private String scmUrl;
	
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
        	this.error(ERROR_FILE_NOT_FOUND, this.file.getName());
        }
		this.getLog().debug("PATH: " + this.file.getAbsolutePath());
		this.getLog().debug("NAME: " + this.file.getName());

        //Get the target repository
		Matcher match = REGEX_REPO.matcher(this.scmUrl);
		if (!match.matches()) {
			this.error(ERROR_SCM_INVALID);
		}
		this.getLog().debug("SCM URL: " + this.scmUrl);

		//Get the repo owner and name from the match
		String repo = match.group(1);
		this.getLog().debug("REPO: " + repo);

		//Attempt to get GitHub credentials from settings and git if not already specified
		if (StringUtils.isNotBlank(this.githubLogin) && StringUtils.isNotBlank(this.githubToken)) {
			Server githubDeploy = this.settings.getServer(SETTINGS_PROFILE_NAME);
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

		
		/** CHECK EXISTING DOWNLOADS **/
		
		
		//Perform existing downloads request
		this.getLog().info(INFO_CHECK_DOWNLOADS);
		String dlCheckUrl = String.format(URL_DOWNLOADS_WITH_AUTH, repo, this.githubLogin, this.githubToken);
		this.getLog().debug("CHECK DOWNLOADS URLL " + dlCheckUrl);
		HttpGet dlCheck = new HttpGet(dlCheckUrl);
		String dlCheckContent = this.checkedExecute(dlCheck, HttpStatus.SC_OK, ERROR_DOWNLOADS);

		//Check for existing download for current artifact
		Pattern downloadsRegex = Pattern.compile(String.format(REGEX_DOWNLOADS, repo), Pattern.DOTALL);
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
					String dlDeleteUrl = String.format(URL_DOWNLOAD_DELETE, repo, downloadId);
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
		this.getLog().info(INFO_ARTIFACT_INFO);
		String deployInfoUrl = String.format(URL_DOWNLOADS, repo);
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
}
