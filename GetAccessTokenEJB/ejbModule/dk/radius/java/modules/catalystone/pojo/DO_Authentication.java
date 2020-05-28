package dk.radius.java.modules.catalystone.pojo;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DO_Authentication {
	private String authenticationUrl;
	private String clientId;
	private String clientSecret;
	private DO_AccessToken accessTokenObject;
	private String apiVersion;
	private String grantType;
	private String dynamicConfigurationPropertyName;
	private String adapterType;
	private boolean debugMode = false;
	private List<String> supportedAdapterTypes = Arrays.asList(new String[]{"REST", "HTTP", "SOAP"});
	private final String DYNAMIC_CONFIGURATION_PROPERTY_NAMESPACE = "http://sap.com/xi/XI/System/";
	
	private final String ERROR_MESSAGE_MODULE_PARAMTER_PLACEHOLDER = "###PLACEHODER###";
	private final String ERROR_MESSAGE_MODULE_PARAMETER_EMPTY = "ModuleParameter \"" + ERROR_MESSAGE_MODULE_PARAMTER_PLACEHOLDER + "\" must have a value!\"";

	public void validate() throws ValidationException {
		ArrayList<String> errorMessages = new ArrayList<String>();
		String errorMessage;
		
		if (authenticationUrl.equals("")) {
			errorMessage = ERROR_MESSAGE_MODULE_PARAMETER_EMPTY.replace(ERROR_MESSAGE_MODULE_PARAMTER_PLACEHOLDER, "authenticationUrl");
			errorMessages.add(errorMessage);
		} else if (!urlIsValid(authenticationUrl)) {
			errorMessage = "ModuleParameter \"authenticationUrl\" is not velformed!";
			errorMessages.add(errorMessage);
		}
		
		if(clientId.equals("")) {
			errorMessage = ERROR_MESSAGE_MODULE_PARAMETER_EMPTY.replace(ERROR_MESSAGE_MODULE_PARAMTER_PLACEHOLDER, "clientId");
			errorMessages.add(errorMessage);
		}
		
		if(clientSecret.equals("")) {	
			errorMessage = ERROR_MESSAGE_MODULE_PARAMETER_EMPTY.replace(ERROR_MESSAGE_MODULE_PARAMTER_PLACEHOLDER, "clientSecret");
			errorMessages.add(errorMessage);
		}
		
		if(apiVersion.equals("")) {
			errorMessage = ERROR_MESSAGE_MODULE_PARAMETER_EMPTY.replace(ERROR_MESSAGE_MODULE_PARAMTER_PLACEHOLDER, "apiVersion");
			errorMessages.add(errorMessage);
		}
		
		if(grantType.equals("")) {
			errorMessage = ERROR_MESSAGE_MODULE_PARAMETER_EMPTY.replace(ERROR_MESSAGE_MODULE_PARAMTER_PLACEHOLDER, "grantType");
			errorMessages.add(errorMessage);
		}
		
		if(dynamicConfigurationPropertyName.equals("")) {
			errorMessage = ERROR_MESSAGE_MODULE_PARAMETER_EMPTY.replace(ERROR_MESSAGE_MODULE_PARAMTER_PLACEHOLDER, "accessTokenHeaderName");
			errorMessages.add(errorMessage);
		}
		
		if(adapterType.equals("")) {
			errorMessage = ERROR_MESSAGE_MODULE_PARAMETER_EMPTY.replace(ERROR_MESSAGE_MODULE_PARAMTER_PLACEHOLDER, "accessTokenHeaderName");
			errorMessages.add(errorMessage);
		} else {
			// If value is found, check that it is a supported one
			if (!supportedAdapterTypes.contains(adapterType)) {
				errorMessage = "ModuleParameter \"adapterType\": " 
					     + adapterType 
					     + " not supported. Supported types are: " 
					     + supportedAdapterTypes.toString();
			
				errorMessages.add(errorMessage);
			}
		}
		
		// Throw error if any error messages has been set
		if (errorMessages.size() != 0) {
			errorMessages.add(0, "Validation errors found: " + errorMessages.size());
			throw new ValidationException(errorMessages.toString());
		}	
	}

	private boolean urlIsValid(String urlToValidate) {
		boolean isValid = false;
		
		try {
			@SuppressWarnings("unused")
			URL url = new URL(urlToValidate);
			isValid = true;
		} catch (MalformedURLException e) {
			isValid = false;
		}
		
		return isValid;	
	}

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
	public String getDynamicConfigurationPropertyName() {
		return dynamicConfigurationPropertyName;
	}
	public void setDynamicConfigurationPropertyName(String dynamicConfigurationPropertyName) {
		this.dynamicConfigurationPropertyName = dynamicConfigurationPropertyName;
	}
	public String getDynamicConfigurationPropertyNamespace() {
		return DYNAMIC_CONFIGURATION_PROPERTY_NAMESPACE + adapterType;
	}
	
	public String getAdapterType() {
		return adapterType;
	}
	public void setAdapterType(String adapterType) {
		this.adapterType = adapterType;
	}
}
