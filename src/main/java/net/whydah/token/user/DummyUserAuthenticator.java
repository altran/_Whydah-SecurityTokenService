package net.whydah.token.user;

import net.whydah.token.config.ApplicationMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * This class is responsible for handling test and development of this module as a standalone instance, shortcutting user authentication
 */

public class DummyUserAuthenticator implements UserAuthenticator {
    private static final Logger logger = LoggerFactory.getLogger(DummyUserAuthenticator.class);
    private final static DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();


    public UserToken logonUser(String applicationTokenId, String appTokenXml, String userCredentialXml) {
        if (ApplicationMode.getApplicationMode().equalsIgnoreCase(ApplicationMode.DEV)) {
            UserToken ut = null;
            try {
                String xml = loadFromFile(parseUsernameFromUserCredential(userCredentialXml));
                ut = UserTokenFactory.fromXml(xml);
            } catch (Exception ioe) {
                logger.info("Could not load dummy token for username. " + ioe);
            }

            return ut;
        }
        throw new IllegalStateException();
    }

    public UserToken createAndLogonUser(String applicationtokenid, String appTokenXml, String userCredentialXml, String fbUserXml) {
        if (ApplicationMode.getApplicationMode().equalsIgnoreCase(ApplicationMode.DEV)) {
            return new UserToken();
        }
        throw new IllegalStateException();
    }


    private static String loadFromFile(String dummytokenuser) throws IOException {
        String xmlToken;
        try (BufferedReader br = new BufferedReader(new FileReader("t_" + dummytokenuser + ".token"))) {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            xmlToken = sb.toString();
        }
        return xmlToken;
    }

    private String parseUsernameFromUserCredential(String userCredential) {

        XPath xpath = XPathFactory.newInstance().newXPath();
        try {
            InputSource source = new InputSource(new StringReader(userCredential));
            String userName = xpath.evaluate("/usercredential/params/username", source).trim();
            return userName;
        } catch (XPathExpressionException e) {
            e.printStackTrace();
            return "";
        }
    }

}