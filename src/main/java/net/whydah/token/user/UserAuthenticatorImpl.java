package net.whydah.token.user;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache.ApacheHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

public class UserAuthenticatorImpl implements UserAuthenticator {
    private static final Logger logger = LoggerFactory.getLogger(UserAuthenticatorImpl.class);
//    private static final String USER_AUTHENTICATION_PATH = "/authenticate/user";
    private static final String USER_AUTHENTICATION_PATH = "/auth/logon/user";
    private static final String CREATE_AND_LOGON_OPERATION = "createandlogon";


    private URI useradminservice;
    private URI useridentitybackend;
    private final WebResource uibResource;
    private final WebResource uasResource;
    private final UserTokenFactory userTokenFactory;



    @Inject
    public UserAuthenticatorImpl(@Named("useridentitybackend") URI useridentitybackend, @Named("useradminservice") URI useradminservice, UserTokenFactory userTokenFactory) {
        this.useridentitybackend = useridentitybackend;
        this.uibResource = ApacheHttpClient.create().resource(useridentitybackend);
        this.useradminservice = useradminservice;
        this.uasResource = ApacheHttpClient.create().resource(useradminservice);
        this.userTokenFactory = userTokenFactory;
    }

    @Override
    public UserToken logonUser(final String applicationTokenId, final String appTokenXml, final String userCredentialXml) {
        logger.trace("logonUser - Calling UserAdminService at " + useradminservice + " appTokenXml:" + appTokenXml + " userCredentialXml:" + userCredentialXml);
        try {
            // /uib/{applicationTokenId}/authenticate/user
//            WebResource webResource = uibResource.path(applicationTokenId).path(USER_AUTHENTICATION_PATH);
            WebResource webResource = uasResource.path(applicationTokenId).path(USER_AUTHENTICATION_PATH);
            ClientResponse response = webResource.type(MediaType.APPLICATION_XML).post(ClientResponse.class, userCredentialXml);

            UserToken userToken = getUserToken(appTokenXml, response);
            return userToken;
        } catch (Exception e) {
            logger.error("Problems connecting to {}", useradminservice);
            throw e;
        }
    }

    @Override
    public UserToken createAndLogonUser(String applicationtokenid, String appTokenXml, String userCredentialXml, String fbUserXml) {
        logger.trace("createAndLogonUser - Calling UserAdminService at with appTokenXml:\n" + appTokenXml + "userCredentialXml:\n" + userCredentialXml + "fbUserXml:\n" + fbUserXml);
        // TODO /uib//{applicationTokenId}/{applicationTokenId}/createandlogon/
        // TODO /authenticate/user
//        WebResource webResource = uibResource.path(applicationtokenid).path(USER_AUTHENTICATION_PATH).path(CREATE_AND_LOGON_OPERATION);
        WebResource webResource = uasResource.path(applicationtokenid).path(USER_AUTHENTICATION_PATH).path(CREATE_AND_LOGON_OPERATION);
        logger.debug("createAndLogonUser - Calling createandlogon " + webResource.toString());
        ClientResponse response = webResource.type(MediaType.APPLICATION_XML).post(ClientResponse.class, fbUserXml);

        UserToken token = getUserToken(appTokenXml, response);
        token.setSecurityLevel("0");  // 3rd party token as source = securitylevel=0
        return token;
    }


    private UserToken getUserToken(String appTokenXml, ClientResponse response) {
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
            logger.error("Response from UAS: {}: {}", response.getStatus(), response.getEntity(String.class));
            throw new AuthenticationFailedException("Authentication failed. Status code " + response.getStatus());
        }
        String identityXML = response.getEntity(String.class);
        logger.debug("Response from UserAdminService: {}", identityXML);
        if (identityXML.contains("logonFailed")) {
            throw new AuthenticationFailedException("Authentication failed.");
        }

        //UserToken token = UserToken.createFromUserAggregate(appTokenXml, identityXML);
        UserToken userToken = userTokenFactory.fromUserAggregate(identityXML);
        //token.setSecurityLevel("1");  // UserIdentity as source = securitylevel=0
        ActiveUserTokenRepository.addUserToken(userToken);
        return userToken;
    }

}
