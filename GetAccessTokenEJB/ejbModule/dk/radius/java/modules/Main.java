package dk.radius.java.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
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

		// Start processing access token
		processAccessToken(inputModuleData, msg);


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

	private void processAccessToken(ModuleData inputModuleData, Message msg) {		
		// Get access token
		getAccessToken();

		// Set access token in header
		setDynamicConfiguration(msg, "accessTokenHeader", "http://sap.com/xi/XI/System/REST", ac.getAccessTokenObject().accessToken);

		// Set message data with new headers
		inputModuleData.setPrincipalData(msg);
	}


	private void getAccessToken() {
		try {

			if (ac.isDebugMode()) {
				audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Requesting access token from url: " + ac.getAuthenticationUrl());
			}

			URL url = new URL(ac.getAuthenticationUrl());
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");

			// Create headers
			con.setRequestProperty("client-id", ac.getClientId());
			con.setRequestProperty("client-secret", ac.getClientSecret());
			con.setRequestProperty("grant-type", ac.getGrantType());
			con.setRequestProperty("api-version", ac.getApiVersion());

			if (ac.isDebugMode()) {
				audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Using headers: " + con.getRequestProperties().toString());
			}

			extractAccessTokenFromAuthResponse(con);


		} catch (MalformedURLException e) {
			// TODO: Raise specific exception
			audit.addAuditLogEntry(msgKey, AuditLogStatus.ERROR, "MalformedURLException: " + e.getMessage());
		} catch (IOException e) {
			// TODO: Raise specific exception
			audit.addAuditLogEntry(msgKey, AuditLogStatus.ERROR, "IOException: " + e.getMessage());
		}
	}


	private void extractAccessTokenFromAuthResponse(HttpURLConnection con) {
		
		String response = convertConnectionResponseToString(con);

		DO_AccessToken at = getAccessTokenDataFromResponse(response);
		
		ac.setAccessTokenObject(at);

	}

	private DO_AccessToken getAccessTokenDataFromResponse(String response) {
		// Get access token data from response
		DO_AccessToken at = null;
		try {
			Gson gson = new Gson();
			 at = gson.fromJson(response, DO_AccessToken.class);

			if (ac.isDebugMode()) {
				audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "AccessToken exctracted from response: " + at.accessToken);
			}
		} catch (JsonSyntaxException e) {
			// TODO: handle exception
		}
		
		return at;
	}

	private String convertConnectionResponseToString(HttpURLConnection con) {
		String response = null;
		try {
			// Convert response to string
			response = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))
					.lines()
					.collect(Collectors.joining("\n"));

			if (con.getResponseCode() != 200) {
				audit.addAuditLogEntry(msgKey, AuditLogStatus.ERROR, "Error getting accesstoken: " + con.getResponseCode() + ": " + con.getResponseMessage());
				throw new RuntimeException("DANG!");
			}

			if (ac.isDebugMode()) {
				audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Response data: " + response);
			}
			
		} catch (IOException e) {
			// TODO: Raise specific exeption
		}
		
		return response;
	}

	private void setDynamicConfiguration(Message msg, String propertyName, String propertyNamespace, String propertyValue) {

		try {
			MessagePropertyKey mpk = new MessagePropertyKey(propertyName, propertyNamespace);
			msg.setMessageProperty(mpk, propertyValue);

			if (ac.isDebugMode()) {
				audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Setting dynamic header: \"" + propertyName + "\": " + propertyValue);
			}
		} catch (InvalidParamException e) {
			// TODO: Raise specific exception
			throw new RuntimeException("Error setting 'accessTokenHeader': " + e.getMessage());
		}
	}

}
