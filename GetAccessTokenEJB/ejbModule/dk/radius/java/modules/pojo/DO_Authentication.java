package dk.radius.java.modules.pojo;

public class DO_Authentication {
	private String authenticationUrl;
	private String clientId;
	private String clientSecret;
	private DO_AccessToken accessTokenObject;
	private String apiVersion = "v3";
	private String grantType = "client_credentials";
	private boolean debugMode = false;


	/*
	 * Getters and setters
	 */
	public String getAuthenticationUrl() {
		return authenticationUrl;
	}
	public void setAuthenticationUrl(String authenticationUrl) {
		this.authenticationUrl = authenticationUrl;
	}
	public String getClientId() {
		return clientId;
	}
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}
	public String getClientSecret() {
		return clientSecret;
	}
	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}
	public String getApiVersion() {
		return apiVersion;
	}
	public void setApiVersion(String apiVersion) {
		this.apiVersion = apiVersion;
	}
	public String getGrantType() {
		return grantType;
	}
	public void setGrantType(String grantType) {
		this.grantType = grantType;
	}
	public boolean isDebugMode() {
		return debugMode;
	}
	public void setDebugMode(boolean debugMode) {
		this.debugMode = debugMode;
	}
	public DO_AccessToken getAccessTokenObject() {
		return accessTokenObject;
	}
	public void setAccessTokenObject(DO_AccessToken accessTokenObject) {
		this.accessTokenObject = accessTokenObject;
	}
}
