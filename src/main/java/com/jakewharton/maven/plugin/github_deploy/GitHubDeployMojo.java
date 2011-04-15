package com.jakewharton.maven.plugin.github_deploy;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
import org.apache.maven.artifact.Artifact;
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
	/** Ignoring artifact message. */
	static final String INFO_IGNORING_ARTIFACT = STRINGS.getString("INFO_IGNORING_ARTIFACT");
	/** Successful deployment message. */
	static final String INFO_SUCCESS = STRINGS.getString("INFO_SUCCESS");
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
	 * Artifact types to ignore.
	 * 
	 * @parameter
	 */
	private List<String> ignoreTypes;
	
    /**
     * Packaged artifact.
     * 
     * @parameter default-value="${project.artifact}"
     * @required
     * @readonly
     */
    private Artifact artifact;
    
    /**
     * Attached artifacts
     * 
     * @parameter default-value="${project.attachedArtifacts}"
     * @required
     * @readonly
     */
    private List<Artifact> attachedArtifacts;
	
    private String authToken;
    
    private Map<String, GitHubDownload> existingDownloads;
    
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
	private HttpClient httpClient;
	
	
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
		
		//Load existing download info
		this.loadExistingDownloadsInformation();

		//Assemble all valid deploy targets
		List<Artifact> artifacts = this.assembleDeployTargets();
		
		//Delete any matching downloads
		if (this.existingDownloads.size() > 0) {
			this.deleteAnyExisting(artifacts);
		}
		
		//Do deployment of artifact
		for (Artifact artifact : artifacts) {
			this.deploy(artifact.getFile());
		}
		
		this.getLog().info(String.format(INFO_SUCCESS, artifacts.size()));
		this.getLog().debug("Done!");
	}
	
	/**
	 * Perform plugin initialization.
	 * 
	 * @throws MojoFailureException
	 */
	void initialize() throws MojoFailureException {
		this.getLog().debug("Initializing plugin...");
		
		//Check we are not working offline
		this.getLog().debug(". Checking Maven is operating online.");
		if (this.settings.isOffline()) {
			this.error(ERROR_OFFLINE);
		}
		
		this.getLog().debug(". Instantiating default HTTP client.");
		this.httpClient = new DefaultHttpClient();
	}
	
	/**
	 * Load the target repository information. This can be specified directly in
	 * the plugin configuration or can be inferred from the SCM developer
	 * connection URL.
	 * 
	 * @throws MojoFailureException
	 */
	void loadRepositoryInformation() throws MojoFailureException {
		this.getLog().debug("Loading repository information...");
		
		if (StringUtils.isBlank(this.repoOwner) || StringUtils.isBlank(this.repoName)) {
			this.getLog().debug(". No information supplied in plugin configuration.");
			
			//Try to get the target repository from the SCM URL.
			this.getLog().debug(". Trying to infer from SCM URL.");
			this.getLog().debug("  $scmUrl = " + this.scmUrl);
			Matcher match = REGEX_REPO.matcher(this.scmUrl);
			if (!match.matches()) {
				this.error(ERROR_SCM_INVALID);
			}
			
			//Get the repo owner and name from the match
			this.repoOwner = match.group(1);
			this.repoName = match.group(2);
		}
		this.repo = this.repoOwner + REPO_SEPERATOR + this.repoName;
		this.getLog().debug("  $repoOwner = " + this.repoOwner);
		this.getLog().debug("  $repoName = " + this.repoName);
		this.getLog().debug("  $repo = " + this.repo);
	}
	
	/**
	 * Load the GitHub user login and token. These can be specified directly in
	 * the plugin configuration, in a <code>&lt;server&gt;</code> section of the
	 * user's <code>settings.xml</code> file, or in the global or local git
	 * configuration.
	 * 
	 * @throws MojoFailureException
	 */
	void loadRepositoryCredentials() throws MojoFailureException {
		this.getLog().debug("Loading repository credentials...");
		
		if (StringUtils.isBlank(this.githubLogin) || StringUtils.isBlank(this.githubToken)) {
			this.getLog().debug(". No information supplied in plugin configuration.");
			
			//Attempt to get GitHub credentials from settings and git if not already specified
			this.getLog().debug(". Checking settings.xml for credentials.");
			Server githubDeploy = this.settings.getServer(SETTINGS_SERVER_ID);
			if (githubDeploy != null) {
				this.githubLogin = githubDeploy.getUsername();
				this.githubToken = githubDeploy.getPassphrase();
			} else {
				this.getLog().debug(". No credentials in settings.xml. Checking git configuration.");
				try {
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
		this.getLog().debug("  $githubLogin = " + this.githubLogin);
		this.getLog().debug("  $githubToken = " + this.githubToken);
	}
	
	void loadExistingDownloadsInformation() throws MojoFailureException {
		this.getLog().info(INFO_CHECK_DOWNLOADS);
		this.getLog().debug("Loading existing downloads information...");
		
		//Perform request
		String url = String.format(URL_DOWNLOADS_WITH_AUTH, this.repo, this.githubLogin, this.githubToken);
		this.getLog().debug("  $url = " + url);
		this.getLog().debug(". Performing request.");
		String content = this.checkedExecute(new HttpGet(url), HttpStatus.SC_OK, ERROR_CHECK_DOWNLOADS);

		//Parse authentication token
		this.getLog().debug(". Parsing content for authentication token.");
		Matcher authTokenMatcher = REGEX_AUTH_TOKEN.matcher(content);
		if (authTokenMatcher.find()) {
			this.authToken = authTokenMatcher.group(1);
			this.getLog().debug("  $authToken = " + authTokenMatcher.group(1));
		} else {
			this.error(ERROR_AUTH_TOKEN);
		}

		//Parse download list
		this.getLog().debug(". Parsing content for existing downloads.");
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
			
			this.getLog().debug(String.format("  . Found download \"%s\".", download.getFileName()));
			this.existingDownloads.put(download.getFileName(), download);
		}
		this.getLog().debug(String.format(". Found %s downloads. ", this.existingDownloads.size()));
	}
	
	/**
	 * Delete an existing download from GitHub.
	 * 
	 * @param download Download to delete.
	 * @throws MojoFailureException
	 */
	void deleteExistingDownload(GitHubDownload download) throws MojoFailureException {
		this.getLog().info(String.format(INFO_DELETE_EXISTING, download.getFileName()));
		this.getLog().debug(". Deleting download...");
		
		//Setup download delete request
		String url = download.getDeleteUrl();
		this.getLog().debug("    $url = " + url);
		HttpPost request = new HttpPost(url);
		BasicHttpEntity entity = new BasicHttpEntity();
		String body = String.format(ENTITY_DELETE_DOWNLOAD, this.githubLogin, this.githubToken, this.authToken);
		this.getLog().debug("    $body = " + body);
		entity.setContent(IOUtils.toInputStream(body));
		request.setEntity(entity);
		
		//Perform request
		this.getLog().debug("  . Performing delete.");
		this.checkedExecute(request, HttpStatus.SC_MOVED_TEMPORARILY, ERROR_DOWNLOAD_DELETE);
	}
	
	List<Artifact> assembleDeployTargets() throws MojoFailureException {
		this.getLog().debug("Assembling deploy targets...");
		
		Map<String, Artifact> artifacts = new HashMap<String, Artifact>();
		
		this.checkAddArtifact(artifacts, this.artifact);
		for (Artifact attachedArtifact : this.attachedArtifacts) {
			this.checkAddArtifact(artifacts, attachedArtifact);
		}
		
		this.getLog().debug(String.format(". Found %s valid deployable artifacts.", artifacts.size()));
		return new LinkedList<Artifact>(artifacts.values());
	}
	
	private void checkAddArtifact(Map<String, Artifact> artifacts, Artifact checkArtifact) throws MojoFailureException {
		this.getLog().debug(String.format(". Check-adding artifact \"%s\"...", checkArtifact.getFile().getName()));
		
		//Check if the artifact is a valid upload candidate
		if ((checkArtifact.getFile() != null) && (checkArtifact.getFile().isFile())) {
			//Check that this artifact's type is not being ignored
			this.getLog().debug(String.format("  . Checking artifact type (%s) is not explicity being ignored.", checkArtifact.getType()));
			if ((this.ignoreTypes != null) && this.ignoreTypes.contains(checkArtifact.getType())) {
				this.getLog().info(String.format(INFO_IGNORING_ARTIFACT, checkArtifact.getFile().getName()));
			} else {
				//Check so duplicate artifact deployments are not attempted
				this.getLog().debug("  . Checking artifact has not already been marked for deployment.");
				if (!artifacts.containsKey(checkArtifact.getFile().getName())) {
					this.getLog().debug("  . Adding artifact to valid deployment list.");
					artifacts.put(checkArtifact.getFile().getName(), checkArtifact);
				}
			}
		}
	}
	
	private void deleteAnyExisting(List<Artifact> artifacts) throws MojoFailureException {
		this.getLog().debug("Deleting any existing downloads which match pending artifact deployments...");
		
		for (Artifact artifact : artifacts) {
			//Check if artifact download exists already
			this.getLog().debug(String.format("  . Checking for \"%s\".", artifact.getFile().getName()));
			if (this.existingDownloads.containsKey(artifact.getFile().getName())) {
				this.getLog().debug("  . Artifact already has an existing download.");
				//Handle existing download
				if (this.replaceExisting) {
					this.deleteExistingDownload(this.existingDownloads.get(artifact.getFile().getName()));
				} else {
					this.error(ERROR_DOWNLOAD_EXISTS);
				}
			}
		}
	}
	
	/**
	 * Deploy an artifact to GitHub downloads. This method assumes that a
	 * download with the same name does not already exist.
	 * 
	 * @param artifactFile Artifact for upload.
	 * @throws MojoFailureException
	 */
	void deploy(File artifactFile) throws MojoFailureException {
		this.getLog().info(INFO_DEPLOY_INFO);
		this.getLog().debug("Deploying file.");
		
		this.getLog().debug(". Sending deploy info and loading S3 details...");
		
		//Prepare request
		String url1 = String.format(URL_DOWNLOADS, this.repo);
		this.getLog().debug("  $url = " + url1);
		HttpPost request1 = new HttpPost(url1);
		BasicHttpEntity entity1 = new BasicHttpEntity();
		String body1 = String.format(ENTITY_DEPLOY_INFO, this.githubLogin, this.githubToken, artifactFile.length(), MIME_TYPE, artifactFile.getName());
		this.getLog().debug("  $body = " + body1);
		entity1.setContent(IOUtils.toInputStream(body1));
		request1.setEntity(entity1);
		this.getLog().debug(". Sending deploy information.");
		String content = this.checkedExecute(request1, HttpStatus.SC_OK, ERROR_DEPLOY_INFO);

		//Parse JSON response
		this.getLog().debug(". Parsing JSON response.");
		JSONObject deployInfo = null;
		try {
			deployInfo = new JSONObject(content);
		} catch (JSONException e) {
			this.error(e, ERROR_JSON_PARSE);
		}
		
		//Extract needed information
		this.getLog().debug(". Extracting S3 details.");
		String prefix = this.checkedJsonProperty(deployInfo, JSON_PROPERTY_PREFIX);
		String key = prefix + artifactFile.getName();
		String policy = this.checkedJsonProperty(deployInfo, JSON_PROPERTY_POLICY);
		String accessKeyId = this.checkedJsonProperty(deployInfo, JSON_PROPERTY_ACCESS_KEY_ID);
		String signature = this.checkedJsonProperty(deployInfo, JSON_PROPERTY_SIGNATURE);
		String acl = this.checkedJsonProperty(deployInfo, JSON_PROPERTY_ACL);
		this.getLog().debug("  $prefix = " + prefix);
		this.getLog().debug("  $key = " + key);
		this.getLog().debug("  $policy = " + policy);
		this.getLog().debug("  $accessKeyId = " + accessKeyId);
		this.getLog().debug("  $signature = " + signature);
		this.getLog().debug("  $acl = " + acl);
		
		this.getLog().info(String.format(INFO_DEPLOY, artifactFile.getName()));
		this.getLog().debug("Deploying artifact to repository.");
		
		//Set up upload request
		String url2 = URL_DEPLOY;
		this.getLog().debug("  $url = " + url2);
		HttpPost request2 = new HttpPost(url2);
		
		this.getLog().debug(". Assembling multipart request.");
		MultipartEntity entity2 = new MultipartEntity();
		try {
			entity2.addPart(HTTP_PROPERTY_KEY, new StringBody(key));
			entity2.addPart(HTTP_PROPERTY_ACL, new StringBody(acl));
			entity2.addPart(HTTP_PROPERTY_FILENAME, new StringBody(artifactFile.getName()));
			entity2.addPart(HTTP_PROPERTY_POLICY, new StringBody(policy));
			entity2.addPart(HTTP_PROPERTY_AWS_ACCESS_ID, new StringBody(accessKeyId));
			entity2.addPart(HTTP_PROPERTY_SIGNATURE, new StringBody(signature));
			entity2.addPart(HTTP_PROPERTY_SUCCESS_ACTION_STATUS, new StringBody(Integer.toString(HttpStatus.SC_CREATED)));
			entity2.addPart(HTTP_PROPERTY_CONTENT_TYPE, new StringBody(MIME_TYPE));
			entity2.addPart(HTTP_PROPERTY_FILE, new FileBody(artifactFile));
		} catch (UnsupportedEncodingException e) {
			this.error(e, ERROR_ENCODING);
		}
		request2.setEntity(entity2);
		
		//Perform upload
		this.getLog().debug(". Performing upload.");
		this.checkedExecute(request2, HttpStatus.SC_CREATED, ERROR_DEPLOYING);
		this.getLog().debug(String.format(". Successfully deployed \"%s\".", artifactFile.getName()));
	}

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
			this.getLog().debug("< HTTP " + status);
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

	
	String getScmUrl() {
		return this.scmUrl;
	}

	void setScmUrl(String scmUrl) {
		this.scmUrl = scmUrl;
	}

	String getRepoOwner() {
		return this.repoOwner;
	}

	void setRepoOwner(String repoOwner) {
		this.repoOwner = repoOwner;
	}

	String getRepoName() {
		return this.repoName;
	}

	void setRepoName(String repoName) {
		this.repoName = repoName;
	}

	boolean isSkip() {
		return this.skip;
	}

	void setSkip(boolean skip) {
		this.skip = skip;
	}

	boolean isReplaceExisting() {
		return this.replaceExisting;
	}

	void setReplaceExisting(boolean replaceExisting) {
		this.replaceExisting = replaceExisting;
	}

	String getGithubLogin() {
		return this.githubLogin;
	}

	void setGithubLogin(String githubLogin) {
		this.githubLogin = githubLogin;
	}

	String getGithubToken() {
		return this.githubToken;
	}

	void setGithubToken(String githubToken) {
		this.githubToken = githubToken;
	}

	Artifact getArtifact() {
		return this.artifact;
	}

	void setArtifact(Artifact artifact) {
		this.artifact = artifact;
	}

	Settings getSettings() {
		return this.settings;
	}

	void setSettings(Settings settings) {
		this.settings = settings;
	}

	HttpClient getHttpClient() {
		return this.httpClient;
	}
	void setHttpClient(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	List<String> getIgnoreTypes() {
		return ignoreTypes;
	}

	void setIgnoreTypes(List<String> ignoreTypes) {
		this.ignoreTypes = ignoreTypes;
	}

	List<Artifact> getAttachedArtifacts() {
		return attachedArtifacts;
	}

	void setAttachedArtifacts(List<Artifact> attachedArtifacts) {
		this.attachedArtifacts = attachedArtifacts;
	}
	String getRepo() {
		return repo;
	}
	void setRepo(String repo) {
		this.repo = repo;
	}
}
