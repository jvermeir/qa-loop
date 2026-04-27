package com.example.demo.util;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

public class XmlParser {

    // S2755: XXE — external entity expansion is not disabled
    public void parseXml(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Missing security hardening:
            //   factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            //   factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new InputSource(new StringReader(xmlContent)));
        } catch (Exception e) {
            // S1166: swallowed
        }
    }

    // S2755: SAX parser also not hardened against XXE
    @SuppressWarnings("deprecation")
    public void parseWithSax(String xmlContent) {
        try {
            XMLReader reader = XMLReaderFactory.createXMLReader();
            // No security features set
            reader.parse(new InputSource(new StringReader(xmlContent)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // S2076: command injection — user-controlled path injected into shell command
    public String convertXmlToJson(String xmlFilePath) {
        try {
            Process p = Runtime.getRuntime().exec("python3 convert.py " + xmlFilePath);
            java.io.InputStream is = p.getInputStream();
            return new String(is.readAllBytes()); // S2095: stream never closed
        } catch (Exception e) {
            return null; // S2259: callers risk NPE
        }
    }
}
