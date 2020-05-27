package dk.radius.java.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.ejb.Local;
import javax.ejb.LocalHome;
import javax.ejb.Remote;
import javax.ejb.RemoteHome;
import javax.ejb.Stateless;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sap.aii.af.lib.mp.module.Module;
import com.sap.aii.af.lib.mp.module.ModuleContext;
import com.sap.aii.af.lib.mp.module.ModuleData;
import com.sap.aii.af.lib.mp.module.ModuleException;
import com.sap.aii.af.lib.mp.module.ModuleHome;
import com.sap.aii.af.lib.mp.module.ModuleLocal;
import com.sap.aii.af.lib.mp.module.ModuleLocalHome;
import com.sap.aii.af.lib.mp.module.ModuleRemote;
import com.sap.engine.interfaces.messaging.api.Message;
import com.sap.engine.interfaces.messaging.api.MessageKey;
import com.sap.engine.interfaces.messaging.api.MessagePropertyKey;
import com.sap.engine.interfaces.messaging.api.PublicAPIAccessFactory;
import com.sap.engine.interfaces.messaging.api.auditlog.AuditAccess;
import com.sap.engine.interfaces.messaging.api.auditlog.AuditLogStatus;
import com.sap.engine.interfaces.messaging.api.exception.InvalidParamException;
import com.sap.engine.interfaces.messaging.api.exception.MessagingException;

import dk.radius.java.modules.pojo.DO_AccessToken;
import dk.radius.java.modules.pojo.DO_Authentication;

/**
 * Session Bean implementation class GetAccessToken
 */
@Stateless(name="GetAccessTokenBean")
@Local(value={ModuleLocal.class})
@Remote(value={ModuleRemote.class})
@LocalHome(value=ModuleLocalHome.class)
@RemoteHome(value=ModuleHome.class)
public class Main implements Module {

	private AuditAccess audit;
	private MessageKey msgKey = null;
	private Message msg = null;
	private DO_Authentication ac = new DO_Authentication();


	@PostConstruct
	public void initializeResources() {
		try {
			audit = PublicAPIAccessFactory.getPublicAPIAccess().getAuditAccess();
		} catch (MessagingException e) {
			throw new RuntimeException("Error in method 'initializeResources': " + e.getMessage());
		}
	}

	@Override
	public ModuleData process(ModuleContext moduleContext, ModuleData inputModuleData) throws ModuleException {				

		// Extract message input from module data
		msg = (Message) inputModuleData.getPrincipalData();
		msgKey = msg.getMessageKey();

		// Get module parameters
		extractModuleParameters(moduleContext);

		// Write debug status to log
		audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Debug mode set to: " + ac.isDebugMode());

		if (ac.isDebugMode()) {
			audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "**** GetAccessToken: Module start ****");
		}

		try {
			// Start processing access token
			processAccessToken(inputModuleData, msg);
			
		} catch (AccessTokenException e) {
			// Write error to log
			audit.addAuditLogEntry(msgKey, AuditLogStatus.ERROR, e.getMessage());
			// Terminate processing
			throw new RuntimeException(e.getMessage());
		}


		if (ac.isDebugMode()) {
			audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "**** GetAccessToken: Module end ****");
		}

		return inputModuleData;
	}


	private void extractModuleParameters(ModuleContext moduleContext) {
		// Extract data from context and set in pojo
		ac.setAuthenticationUrl(moduleContext.getContextData("authenticationUrl"));
		ac.setClientId(moduleContext.getContextData("clientId"));
		ac.setClientSecret(moduleContext.getContextData("pwd.clientSecret"));
		ac.setGrantType(moduleContext.getContextData("grantType"));
		ac.setApiVersion(moduleContext.getContextData("apiVersion"));
		ac.setDebugMode(Boolean.parseBoolean(moduleContext.getContextData("debugEnabled")));
	}

	private void processAccessToken(ModuleData inputModuleData, Message msg) throws AccessTokenException {		
		// Get access token
		getAccessToken();

		// Set access token in header
		setDynamicConfiguration(msg, "accessTokenHeader", "http://sap.com/xi/XI/System/REST", ac.getAccessTokenObject().accessToken);

		// Set message data with new headers
		inputModuleData.setPrincipalData(msg);
	}


	private void getAccessToken() throws AccessTokenException {
		
		// Create connection to authentication server
		HttpURLConnection con = createAccessTokenConnection();
		
		// Get data from response
		extractAccessTokenFromAuthResponse(con);
	}


	private HttpURLConnection createAccessTokenConnection() throws AccessTokenException {
		HttpURLConnection con = null;
		try {
			URL url = new URL(ac.getAuthenticationUrl());
			con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");

			createRequestHeaders(con);

			if (ac.isDebugMode()) {
				audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Requesting AccessToken from url: " + ac.getAuthenticationUrl());
			}

		} catch (IOException e) {
			String errorMessage = "Error creating Http connection to authentication server with url: " + ac.getAuthenticationUrl();
			throw new AccessTokenException(errorMessage);
		}
		
		return con;
	}

	private void createRequestHeaders(HttpURLConnection con) {

		if (ac.isDebugMode()) {
			audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Creating request headers based on module parameters..." );
		}

		con.setRequestProperty("client-id", ac.getClientId());
		con.setRequestProperty("client-secret", ac.getClientSecret());
		con.setRequestProperty("grant-type", ac.getGrantType());
		con.setRequestProperty("api-version", ac.getApiVersion());


	}

	private void extractAccessTokenFromAuthResponse(HttpURLConnection con) throws AccessTokenException {
		// Get response string
		String response = convertConnectionResponseToString(con);

		// Get access token data from response (json)
		DO_AccessToken at = getAccessTokenDataFromResponse(response);

		// Set access token data
		ac.setAccessTokenObject(at);
	}

	private DO_AccessToken getAccessTokenDataFromResponse(String response) throws AccessTokenException {
		DO_AccessToken at = null;

		try {
			// Get access token data from response
			Gson gson = new Gson();
			at = gson.fromJson(response, DO_AccessToken.class);

			if (ac.isDebugMode()) {
				audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "AccessToken exctracted from response: " + at.accessToken);
			}
		} catch (JsonSyntaxException e) {
			String errorMessage = "Error parsing json response: " + response;
			throw new AccessTokenException(errorMessage);
		}

		// Return access token object
		return at;
	}

	private String convertConnectionResponseToString(HttpURLConnection con) throws AccessTokenException {
		String response = null;
		try {
			// Convert response to string
			response = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))
					.lines()
					.collect(Collectors.joining("\n"));

			if (con.getResponseCode() != 200) {
				String msg = "Error getting accesstoken: " + con.getResponseCode() + ": " + con.getResponseMessage();
				throw new AccessTokenException(msg);
			}

			if (ac.isDebugMode()) {
				audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Authentication server returned code: " + con.getResponseCode());
			}

		} catch (IOException e) {
			
		}

		return response;
	}

	private void setDynamicConfiguration(Message msg, String propertyName, String propertyNamespace, String propertyValue) throws AccessTokenException {

		try {
			MessagePropertyKey mpk = new MessagePropertyKey(propertyName, propertyNamespace);
			msg.setMessageProperty(mpk, propertyValue);

			if (ac.isDebugMode()) {
				audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Setting dynamic header: \"" + propertyName + "\": " + propertyValue);
			}
		} catch (InvalidParamException e) {
			String errorMessage = "Error setting \"accessTokenHeader\" in dynamic configuration: " + e.getMessage();
			throw new AccessTokenException(errorMessage);
		}
	}

}
