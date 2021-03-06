package net.whydah.token.application;

import net.whydah.token.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ApplicationCredential {

    private String applicationID;
    private String applicationsecret;
    private static final Logger logger = LoggerFactory.getLogger(ApplicationCredential.class);

    public void ApplicationCredential() {
        try {
            AppConfig config = new AppConfig();
            applicationID = config.getProperty("applicationid");
            applicationsecret = config.getProperty("applicationsecret");
        } catch (Exception e) {
            logger.warn("Unable to read application properties", e);
        }

    }

    public String getApplicationID() {
        return applicationID;
    }

    public void setApplicationID(String applicationID) {
        this.applicationID = applicationID;
    }

    public String getApplicationSecret() {
        return applicationsecret;
    }

    public void setApplicationSecret(String applicationsecret) {
        this.applicationsecret = applicationsecret;
    }

    public String toXML() {
        if (applicationID == null) {
            return templateToken;
        } else {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?> \n " +
                    "<applicationcredential>\n" +
                    "    <params>\n" +
                    "        <applicationID>" + applicationID + "</applicationID>\n" +
                    "        <applicationSecret>" + applicationsecret + "</applicationSecret>\n" +
                    "    </params> \n" +
                    "</applicationcredential>\n";
        }
    }

    private final String templateToken = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?> \n " +
            "    <applicationcredential>\n" +
            "        <params>\n" +
            "            <applicationID>" + applicationID + "</applicationID>\n" +
            "            <applicationSecret>" + applicationsecret + "</applicationSecret>\n" +
            "        </params> \n" +
            "    </applicationcredential>\n";
}
