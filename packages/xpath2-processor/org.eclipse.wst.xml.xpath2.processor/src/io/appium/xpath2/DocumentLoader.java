package io.appium.xpath2;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Parses XML into a DOM tree that the processor can evaluate expressions against.
 * The bundled Xerces is instantiated directly, so that a parser provided by the
 * application the processor is loaded into is never picked up.
 */
public final class DocumentLoader {
    private DocumentLoader() {
    }

    public static Document parse(InputStream xml)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = JaxpFactories.documentBuilderFactory();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        return factory.newDocumentBuilder().parse(xml);
    }
}
