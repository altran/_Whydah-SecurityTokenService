package net.whydah.token.application;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import net.whydah.sso.application.mappers.ApplicationCredentialMapper;
import net.whydah.sso.application.mappers.ApplicationTokenMapper;
import net.whydah.sso.application.types.ApplicationCredential;
import net.whydah.sso.application.types.ApplicationToken;
import net.whydah.token.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Random;



public class ApplicationAuthenticationUASClient {

    private final static Logger log = LoggerFactory.getLogger(ApplicationAuthenticationUASClient.class);


    private static final String APPLICATION_AUTH_PATH = "application/auth";
    public static final String APP_CREDENTIAL_XML = "appCredentialXml";

    public static boolean checkAppsecretFromUAS(ApplicationCredential applicationCredential,AppConfig appConfig,ApplicationToken stsToken) {
        ApplicationToken token = ApplicationTokenMapper.fromApplicationCredentialXML(ApplicationCredentialMapper.toXML(applicationCredential));
        token.setBaseuri(appConfig.getProperty("myuri"));
        token.setExpires(String.valueOf((System.currentTimeMillis() + 10 * new Random().nextInt(500))));

        String useradminservice = appConfig.getProperty("useradminservice");
        //ApplicationToken stsToken = getSTSApplicationToken();
        AuthenticatedApplicationRepository.addApplicationToken(stsToken);

        WebResource uasResource = ApacheHttpClient.create().resource(useradminservice);
        int uasResponseCode = 0;
        WebResource webResource = uasResource.path(stsToken.getApplicationTokenId()).path(APPLICATION_AUTH_PATH);
        log.info("checkAppsecretFromUAS - Calling application auth " + webResource.toString());
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add(APP_CREDENTIAL_XML, ApplicationCredentialMapper.toXML(applicationCredential));
        try {
            ClientResponse response = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).post(ClientResponse.class, formData);
            uasResponseCode = response.getStatus();
            log.info("Response from UAS:" + uasResponseCode);
            if (uasResponseCode == 204) {
                return true;
            }
        } catch (Exception e) {
            log.error("checkAppsecretFromUAS - Problems connecting to {}", useradminservice);
            throw e;
        }
        log.warn("Illegal application tried to access whydah. ApplicationID: {}, Response from UAS: {}", applicationCredential.getApplicationID(), uasResponseCode);
        return false;
    }
}