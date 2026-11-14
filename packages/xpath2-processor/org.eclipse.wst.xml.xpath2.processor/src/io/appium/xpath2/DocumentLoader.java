package io.appium.xpath2;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Parses XML into a DOM tree that the processor can evaluate expressions against.
 * The bundled Xerces is instantiated directly, so that a parser provided by the
 * application the processor is loaded into is never picked up. DOCTYPE declarations are rejected.
 */
public final class DocumentLoader {
    private DocumentLoader() {
    }

    public static Document parse(InputStream xml)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = JaxpFactories.documentBuilderFactory();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(xml);
    }
}
