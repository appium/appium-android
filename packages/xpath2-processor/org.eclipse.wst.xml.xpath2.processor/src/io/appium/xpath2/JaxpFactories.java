package io.appium.xpath2;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.xerces.jaxp.DocumentBuilderFactoryImpl;
import org.apache.xerces.jaxp.datatype.DatatypeFactoryImpl;

/**
 * Creates the JAXP factories the processor depends on from the bundled Xerces directly. A lookup via
 * {@code newInstance()} would fail on Android, which has no implementation of its own for some of them,
 * or pick up a provider of the application the processor is loaded into.
 */
public final class JaxpFactories {
    private JaxpFactories() {
    }

    // Declared to keep the callers that handle the exception of DatatypeFactory.newInstance() unchanged
    public static DatatypeFactory datatypeFactory() throws DatatypeConfigurationException {
        return new DatatypeFactoryImpl();
    }

    public static DocumentBuilderFactory documentBuilderFactory() {
        return new DocumentBuilderFactoryImpl();
    }
}
