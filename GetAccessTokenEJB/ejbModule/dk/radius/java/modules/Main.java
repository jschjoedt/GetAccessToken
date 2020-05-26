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

import dk.radius.java.modules.pojo.AccessToken;

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
	private AccessToken ac = new AccessToken();
	

	@PostConstruct
	public void initializeResources() {
		try {
			audit = PublicAPIAccessFactory.getPublicAPIAccess().getAuditAccess();
		} catch (MessagingException e) {
			throw new RuntimeException("error in method 'initializeResources': " + e.getMessage());
		}
	}
	
	@Override
	public ModuleData process(ModuleContext moduleContext, ModuleData inputModuleData) throws ModuleException {				
		// Extract message input from module data
		msg = (Message) inputModuleData.getPrincipalData();
		msgKey = msg.getMessageKey();
		
		// Write to audit log
		audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "GetAccessToken: Module start...");
		
		// Get module parameters
		extractModuleParameters(moduleContext);
		
		// Start processing access token
		processAccessToken(inputModuleData, msg);
				
		// Write to audit log
		audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "GetAccessToken: Module end...");
		
		// Return payload
		return inputModuleData;
	}

	
	private void extractModuleParameters(ModuleContext moduleContext) {
		// Extract data from context and set in pojo
		ac.setAuthenticationUrl(moduleContext.getContextData("authenticationUrl"));
		ac.setClientId(moduleContext.getContextData("clientId"));
		ac.setClientSecret(moduleContext.getContextData("pwd.clientSecret"));
		ac.setGrantType(moduleContext.getContextData("grantType"));
		ac.setApiVersion(moduleContext.getContextData("apiVersion"));
	}

	private void processAccessToken(ModuleData inputModuleData, Message msg) {		
		// Get access token
		String accessToken = getAccessToken();
		
		// Set access token in header
		setDynamicConfiguration(msg, "accessTokenHeader", "http://sap.com/xi/XI/System/REST", accessToken);
		
		// Set message data with new headers
		inputModuleData.setPrincipalData(msg);
	}

	
	private String getAccessToken() {
		try {
			URL url = new URL(ac.getAuthenticationUrl());
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			
			// Create headers
			con.setRequestProperty("client-id", ac.getClientId());
			con.setRequestProperty("client-secret", ac.getClientSecret());
			con.setRequestProperty("grant-type", ac.getGrantType());
			con.setRequestProperty("api-version", ac.getApiVersion());

			audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Headers: " + con.getRequestProperties().toString());

			String response = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))
		      .lines()
		      .collect(Collectors.joining("\n"));
				
			audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "Response: " + response);
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// TODO Auto-generated method stub
		return "jesperTesterAccessTokenModul";
	}

	
	private void setDynamicConfiguration(Message msg, String propertyName, String propertyNamespace, String propertyValue) {
		
		try {
			MessagePropertyKey mpk = new MessagePropertyKey(propertyName, propertyNamespace);
			msg.setMessageProperty(mpk, propertyValue);
			// Write to audit log
			audit.addAuditLogEntry(msgKey, AuditLogStatus.SUCCESS, "GetAccessToken: set '" + propertyName + "': " + propertyValue);
		} catch (InvalidParamException e) {
			throw new RuntimeException("Error setting 'accessTokenHeader': " + e.getMessage());
		}
	}

}
