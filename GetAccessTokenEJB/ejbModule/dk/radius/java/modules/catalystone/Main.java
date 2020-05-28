package dk.radius.java.modules.catalystone;

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

import dk.radius.java.modules.catalystone.pojo.DO_AccessToken;
import dk.radius.java.modules.catalystone.pojo.DO_AccessTokenError;
import dk.radius.java.modules.catalystone.pojo.DO_Authentication;
import dk.radius.java.modules.catalystone.pojo.ValidationException;

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
	Gson gson = new Gson();


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
		
		audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "**** GetAccessToken: Module start ****");
		
		try {
			// Get module parameters
			extractModuleParameters(moduleContext);

			// Write debug status to log
			audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Debug mode set to: " + ac.isDebugMode());

			// Start processing access token
			start(inputModuleData, msg);

		} catch (AccessTokenException e) {
			// Write error to log
			audit.addAuditLogEntry(msgKey, AuditLogStatus.ERROR, e.getMessage());
			// Terminate processing
			throw new RuntimeException(e.getMessage());
		} catch (ValidationException e) {
			// Write error to log
			audit.addAuditLogEntry(msgKey, AuditLogStatus.ERROR, e.getMessage());
			// Terminate processing
			throw new RuntimeException(e.getMessage());
		} finally {
			audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "**** GetAccessToken: Module end ****");
		}

		return inputModuleData;
	}

	
	/**
	 * Extract adapter module parameters set in communication channel.
	 * @param moduleContext carries the context information which might be used by an XI AF module to access the current channel ID
	 * @throws ValidationException
	 */
	private void extractModuleParameters(ModuleContext moduleContext) throws ValidationException {
		
		// Extract data from context and set in pojo
		ac.setAuthenticationUrl(moduleContext.getContextData("authenticationUrl"));
		ac.setClientId(moduleContext.getContextData("clientId"));
		ac.setClientSecret(moduleContext.getContextData("pwd.clientSecret"));
		ac.setGrantType(moduleContext.getContextData("grantType"));
		ac.setApiVersion(moduleContext.getContextData("apiVersion"));
		ac.setDebugMode(Boolean.parseBoolean(moduleContext.getContextData("debugEnabled")));
		ac.setDynamicConfigurationPropertyName(moduleContext.getContextData("accessTokenHeaderName"));
		ac.setAdapterType(moduleContext.getContextData("adapterType"));

		// Validate module parameter values
		ac.validate();
	}


	/**
	 * Start the process of getting "AccessToken" from authentication server set in adapter module.
	 * @param inputModuleData is the container which carries the module input data and module output data
	 * @param msg A <i>Message</i> is what an application sends or receives when interacting with the Messaging System
	 * @throws AccessTokenException
	 */
	private void start(ModuleData inputModuleData, Message msg) throws AccessTokenException {		
		// Get access token
		getAccessToken();

		// Set access token in header
		setDynamicConfiguration(msg, ac.getDynamicConfigurationPropertyName(), ac.getDynamicConfigurationPropertyNamespace(), ac.getAccessTokenObject().accessToken);

		// Set message data with new headers
		inputModuleData.setPrincipalData(msg);
	}

	
	/**
	 * Get "AccessToken" from authentication server set in adapter module.
	 * @throws AccessTokenException
	 */
	private void getAccessToken() throws AccessTokenException {

		// Create connection to authentication server
		HttpURLConnection con = createAccessTokenConnection();

		// Get data from response
		extractAccessTokenFromAuthResponse(con);
	}


	/**
	 * Create an <i>HttpURLConnection</i> using data set in adapter module
	 * @return HttpURLConnection
	 * @throws AccessTokenException
	 */
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


	/**
	 * Create request headers based on values set in adapter module
	 * @param con <i>HttpURLConnection</i> to authentication server
	 */
	private void createRequestHeaders(HttpURLConnection con) {

		if (ac.isDebugMode()) {
			audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Creating request headers based on module parameters..." );
		}

		con.setRequestProperty("client-id", ac.getClientId());
		con.setRequestProperty("client-secret", ac.getClientSecret());
		con.setRequestProperty("grant-type", ac.getGrantType());
		con.setRequestProperty("api-version", ac.getApiVersion());
	}


	/**
	 * Extracts "AccessToken" from authentication server response and sets if for later use.
	 * @param con <i>HttpURLConnection</i> to authentication server
	 * @throws AccessTokenException
	 */
	private void extractAccessTokenFromAuthResponse(HttpURLConnection con) throws AccessTokenException {
		// Get response string
		String response = convertConnectionResponseToString(con);

		// Get access token data from response (json)
		DO_AccessToken at = getAccessTokenDataFromResponse(response);

		// Set access token data
		ac.setAccessTokenObject(at);
	}


	/**
	 * Convert response from authentication server using <i>gson api</i>
	 * @param response <i>String</i> raw authentication server response.
	 * @return <i>DO_AccessToken</i> "AccessToken" object containing response data
	 * @see <a href="https://sites.google.com/site/gson/gson-user-guide">gson documentation</a>
	 * @throws AccessTokenException
	 */
	private DO_AccessToken getAccessTokenDataFromResponse(String response) throws AccessTokenException {
		DO_AccessToken at = null;

		try {
			// Get access token data from response
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


	/**
	 * Convert <i>HttpURLConnection -> InputStream</i> to <i>String</i>.
	 * @param con <i>HttpURLConnection</i> to authentication server
	 * @return <i>String</i> response from authentication server
	 * @throws AccessTokenException
	 */
	private String convertConnectionResponseToString(HttpURLConnection con) throws AccessTokenException {
		String response = null;
		try {
			// Convert response to string
			response = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))
					.lines()
					.collect(Collectors.joining("\n"));

			if (con.getResponseCode() != 200) {
				// Get error data from JSON response
				DO_AccessTokenError ate = gson.fromJson(response, DO_AccessTokenError.class);

				// Throw exception
				String errorMessage = "Error getting AccessToken with server code: " + con.getResponseCode() + " and message: " + ate.message;
				throw new AccessTokenException(errorMessage);
			}

			if (ac.isDebugMode()) {
				audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Authentication server returned code: " + con.getResponseCode());
			}

		} catch (IOException e) {
			String errorMessage = "Error getting response from HttpConnection inputStream: " + e.getMessage();
			throw new AccessTokenException(errorMessage);
		}

		return response;
	}


	/**
	 * Set dynamic configuration data (this can later be fetched in communication channel).
	 * @param msg A <i>Message</i> is what an application sends or receives when interacting with the Messaging System
	 * @param propertyName <i>String</i> dynamic configuration: name to reference when reading from dynamic configuration
	 * @param propertyNamespace <i>String</i> dynamic configuration: namespace (eg. http://sap.com/xi/XI/System/REST)
	 * @param propertyValue <i>String</i> dynamic configuration: value returned when reading dynamic configuration using <b>propertyName</b>
	 * @throws AccessTokenException
	 */
	private void setDynamicConfiguration(Message msg, String propertyName, String propertyNamespace, String propertyValue) throws AccessTokenException {

		try {
			MessagePropertyKey mpk = new MessagePropertyKey(propertyName, propertyNamespace);
			msg.setMessageProperty(mpk, propertyValue);

			if (ac.isDebugMode()) {
				audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Setting dynamic header: \"" + propertyName + "\": " 
																		+ propertyValue
																		+ "(" + propertyNamespace + ")");
			}
		} catch (InvalidParamException e) {
			String errorMessage = "Error setting \"accessTokenHeader\" in dynamic configuration: " + e.getMessage();
			throw new AccessTokenException(errorMessage);
		}
	}

}
