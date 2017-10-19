package net.whydah.sts.user.authentication;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache.ApacheHttpClient;
import net.whydah.sso.application.mappers.ApplicationTokenMapper;
import net.whydah.sso.application.types.ApplicationToken;
import net.whydah.sso.commands.adminapi.user.CommandGetUserAggregate;
import net.whydah.sso.commands.adminapi.user.CommandListUsers;
import net.whydah.sso.user.mappers.UserCredentialMapper;
import net.whydah.sso.user.mappers.UserTokenMapper;
import net.whydah.sso.user.types.UserCredential;
import net.whydah.sso.user.types.UserToken;
import net.whydah.sts.application.AuthenticatedApplicationTokenRepository;
import net.whydah.sts.config.AppConfig;
import net.whydah.sts.errorhandling.AuthenticationFailedException;
import net.whydah.sts.threat.ThreatResource;
import net.whydah.sts.user.AuthenticatedUserTokenRepository;
import net.whydah.sts.user.UserTokenFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.UUID;

public class UserAuthenticatorImpl implements UserAuthenticator {
	private static final Logger log = LoggerFactory.getLogger(UserAuthenticatorImpl.class);
	private static final String USER_AUTHENTICATION_PATH = "/auth/logon/user";
	private static final String CREATE_AND_LOGON_OPERATION = "createandlogon";
	private static final String defaultlifespan = "245000";


	private URI useradminservice;
	private final AppConfig appConfig;
	private final WebResource uasResource;
	private final UserTokenFactory userTokenFactory;


	@Inject
	public UserAuthenticatorImpl(@Named("useradminservice") URI useradminservice, UserTokenFactory userTokenFactory, AppConfig appConfig) {
		this.useradminservice = useradminservice;
		this.appConfig = appConfig;
		this.uasResource = ApacheHttpClient.create().resource(useradminservice);
		this.userTokenFactory = userTokenFactory;
	}

	@Override
	public UserToken logonUser(String applicationTokenId, String appTokenXml, final String userCredentialXml) throws AuthenticationFailedException {
        UserCredential userCredential = UserCredentialMapper.fromXml(userCredentialXml);
        if (userCredential != null) {
            log.trace("logonUser - Calling UserAdminService at " + useradminservice + " appTokenXml:" + appTokenXml + " userCredentialSafeXml:" + userCredential.toSafeXML());
        } else {
            log.trace("logonUser - Unable to map userCredentialXML - Calling UserAdminService at " + useradminservice + " appTokenXml:" + appTokenXml + " userCredentialXml:" + userCredentialXml);

        }

		WebResource webResource = uasResource.path(applicationTokenId).path(USER_AUTHENTICATION_PATH);
		ClientResponse response = webResource.type(MediaType.APPLICATION_XML).post(ClientResponse.class, userCredentialXml);
		UserToken userToken = getUserToken(applicationTokenId, appTokenXml, response);
		return userToken;

	}

	@Override
	public UserToken createAndLogonUser(String applicationtokenid, String appTokenXml, String userCredentialXml, String fbUserXml) throws AuthenticationFailedException {
		log.trace("createAndLogonUser - Calling UserAdminService at with appTokenXml:\n" + appTokenXml + "userCredentialXml:\n" + userCredentialXml + "fbUserXml:\n" + fbUserXml);
		WebResource webResource = uasResource.path(applicationtokenid).path(USER_AUTHENTICATION_PATH).path(CREATE_AND_LOGON_OPERATION);
		log.debug("createAndLogonUser - Calling createandlogon " + webResource.toString());
		ClientResponse response = webResource.type(MediaType.APPLICATION_XML).post(ClientResponse.class, fbUserXml);

		UserToken token = getUserToken(applicationtokenid, appTokenXml, response);
		token.setSecurityLevel("0");  // 3rd party sts as source = securitylevel=0
		return token;
	}


	@Override
	public UserToken createAndLogonPinUser(String applicationtokenid, String appTokenXml, String adminUserTokenId, String cellPhone, String pin, String userJson) {
		if (ActivePinRepository.usePin(cellPhone, pin)) {
			try {
				WebResource uasWR = uasResource.path(applicationtokenid).path(adminUserTokenId).path("user");
				ClientResponse uasResponse = uasWR.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, userJson);
				if (uasResponse.getStatus() != ClientResponse.Status.OK.getStatusCode()) {
					String error = uasResponse.getEntity(String.class);
					log.error(error);
				} else {
					String userIdentityJson = uasResponse.getEntity(String.class);
					UserToken userToken = UserTokenFactory.fromUserIdentityJson(userIdentityJson);
					userToken.setSecurityLevel("0");  // 3rd party sts as source = securitylevel=0
//					userToken.setLifespan(String.valueOf(1000 * ApplicationSessionHelper.getApplicationLifeSpanSeconds(applicationtokenid)));
                    userToken.setLifespan(String.valueOf(86400000));

					userToken.setTimestamp(String.valueOf(System.currentTimeMillis()));

					AuthenticatedUserTokenRepository.addUserToken(userToken, applicationtokenid, "pin");
					return userToken;
					// return Response.ok(new Viewable("/usertoken.ftl", myToken)).build();
				}
			} catch (Exception e) {
				log.error("createAndLogonPinUser - Problems connecting to {}", useradminservice);
				throw e;
			}
		}
		throw new AuthenticationFailedException("APin uthentication failed. Status code ");
	}

	public UserToken getRefreshedUserToken(String usertokenid) {
        ApplicationToken stsApplicationToken = AuthenticatedApplicationTokenRepository.getSTSApplicationToken();
//		AuthenticatedApplicationTokenRepository.addApplicationToken(stsApplicationToken);
        String user = appConfig.getProperty("whydah.adminuser.username");
		String password = appConfig.getProperty("whydah.adminuser.password");
		UserCredential userCredential = new UserCredential(user, password);
        UserToken whydahUserAdminUserToken = logonUser(stsApplicationToken.getApplicationTokenId(), ApplicationTokenMapper.toXML(stsApplicationToken), userCredential.toXML());

        UserToken oldUserToken = AuthenticatedUserTokenRepository.getUserToken(usertokenid, stsApplicationToken.getApplicationTokenId());

        String userAggregateJson = new CommandGetUserAggregate(useradminservice, stsApplicationToken.getApplicationTokenId(), whydahUserAdminUserToken.getUserTokenId(), oldUserToken.getUid()).execute();

        UserToken refreshedUserToken = UserTokenMapper.fromUserAggregateJson(userAggregateJson);
        
        
		return refreshedUserToken;

	}

	@Override
	public UserToken logonPinUser(String applicationtokenid, String appTokenXml, String adminUserTokenId, String cellPhone, String pin) {
		log.info("logonPinUser() called with " + "applicationtokenid = [" + applicationtokenid + "], appTokenXml = [" + appTokenXml + "], cellPhone = [" + cellPhone + "], pin = [" + pin + "]");
		if (ActivePinRepository.usePin(cellPhone, pin)) {
			String usersQuery = cellPhone;
			// produserer userJson. denne kan inneholde fler users dette er json av
			String usersJson = new CommandListUsers(useradminservice, applicationtokenid, adminUserTokenId, usersQuery).execute();
			log.info("CommandListUsers for query {} found users {}", usersQuery, usersJson);
			UserToken userTokenIdentity = getFirstMatch(usersJson, usersQuery);
			if (userTokenIdentity != null) {
				log.info("Found matching UserIdentity {}", userTokenIdentity);

				String userAggregateJson = new CommandGetUserAggregate(useradminservice, applicationtokenid, adminUserTokenId, userTokenIdentity.getUid()).execute();

				UserToken userToken = UserTokenMapper.fromUserAggregateJson(userAggregateJson);
				userToken.setSecurityLevel("0");  // UserIdentity as source = securitylevel=0
//				userToken.setLifespan(String.valueOf(1000 * ApplicationSessionHelper.getApplicationLifeSpanSeconds(applicationtokenid)));
                userToken.setLifespan(String.valueOf(86400000));

				userToken.setTimestamp(String.valueOf(System.currentTimeMillis()));

				AuthenticatedUserTokenRepository.addUserToken(userToken, applicationtokenid, "pin");
				return userToken;

			} else {
				log.error("Unable to find a user matching the given phonenumber.");
				throw new AuthenticationFailedException("Unable to find a user matching the given phonenumber.");
			}
		} else {
			log.warn("logonPinUser, illegal pin attempted - pin not registered");
			throw new AuthenticationFailedException("Pin authentication failed. Status code ");
		}

	}


	/**
	 * Method to enable pin-logon for whydah users
	 * Implements the following prioritizing
	 * a)  userName+cellPhone = number
	 * b)  userName = number
	 * c)  cellPhone=number
	 *
	 * @param usersJson
	 * @param cellPhone
	 * @return
	 */
	private UserToken getFirstMatch(String usersJson, String cellPhone) {
		log.info("Searching for: ", cellPhone);
		log.info("Searching in: ", usersJson);
		List<UserToken> userTokens = UserTokenFactory.fromUsersIdentityJson(usersJson);
		// First lets find complete matches
		for (UserToken userIdentity : userTokens) {
			if (cellPhone.equals(userIdentity.getCellPhone()) && cellPhone.equals(userIdentity.getUserName())) {
				return userIdentity;
			}
		}
		// The prioritize userName
		for (UserToken userIdentity : userTokens) {
			log.info("getFirstMatch: getUserName: " + userIdentity.getUserName());
			if (cellPhone.equals(userIdentity.getUserName())) {
				return userIdentity;
			}
		}
		// The and finally cellPhone users
		for (UserToken userIdentity : userTokens) {
			log.info("getFirstMatch: cellPhone: " + userIdentity.getCellPhone());
			if (cellPhone.equals(userIdentity.getCellPhone())) {
				return userIdentity;
			}
		}
		return null;
	}


	private UserToken getUserToken(String applicationtokenid, String appTokenXml, ClientResponse response) {
		if (response.getStatus() == Response.Status.OK.getStatusCode() || response.getStatus() == Response.Status.NO_CONTENT.getStatusCode()) {
			String userAggregateJson = response.getEntity(String.class);
			log.debug("Response from UserAdminService: {}", userAggregateJson);
			if (userAggregateJson.contains("logonFailed")) {
				throw new AuthenticationFailedException("Authentication failed.");
			}

			UserToken userToken = UserTokenMapper.fromUserAggregateXml(userAggregateJson);
			userToken.setSecurityLevel("1");  // UserIdentity as source = securitylevel=0
			userToken.setUserTokenId(generateID());
			userToken.setDefcon(ThreatResource.getDEFCON());
//			userToken.setLifespan(String.valueOf(1000 * ApplicationSessionHelper.getApplicationLifeSpanSeconds(applicationtokenid)));
            userToken.setLifespan(String.valueOf(86400000));

			userToken.setTimestamp(String.valueOf(System.currentTimeMillis()));

			AuthenticatedUserTokenRepository.addUserToken(userToken, applicationtokenid, "usertokenid");
			return userToken;

		} else {
			log.error("getUserToken - Response from UAS: {}: {}", response.getStatus(), response.getEntity(String.class));
			throw new AuthenticationFailedException("Authentication failed. Status code from UAS: " + response.getStatus());
		}
	}

    private static String generateID() {
        return UUID.randomUUID().toString();
    }


}