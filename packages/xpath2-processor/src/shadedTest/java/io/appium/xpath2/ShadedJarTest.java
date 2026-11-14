package io.appium.xpath2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarFile;

import org.eclipse.wst.xml.xpath2.api.Item;
import org.eclipse.wst.xml.xpath2.api.ResultSequence;
import org.eclipse.wst.xml.xpath2.processor.Engine;
import org.eclipse.wst.xml.xpath2.processor.util.DynamicContextBuilder;
import org.eclipse.wst.xml.xpath2.processor.util.StaticContextBuilder;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXParseException;

/** Runs against the shaded jar only, see the shadedTest task. */
public class ShadedJarTest {
    private static final String XML = "<hierarchy>"
            + "<android.widget.TextView index=\"1\" text=\"Apple\"/>"
            + "<android.widget.Button index=\"2\" text=\"banana\"/>"
            + "</hierarchy>";

    private static List<String> evaluate(String xpath) throws Exception {
        Document doc = DocumentLoader.parse(
                new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)));
        StaticContextBuilder scb = new StaticContextBuilder()
                .withNamespace("xs", "http://www.w3.org/2001/XMLSchema");
        ResultSequence items = new Engine().parseExpression(xpath, scb)
                .evaluate(new DynamicContextBuilder(scb), new Object[] {doc});
        List<String> result = new ArrayList<>();
        for (Iterator<Item> it = items.iterator(); it.hasNext();) {
            Item item = it.next();
            Object value = item.getNativeValue();
            result.add(value instanceof Element
                    ? ((Element) value).getAttribute("index")
                    : item.getStringValue());
        }
        return result;
    }

    @Test
    public void evaluatesXPath2Expressions() throws Exception {
        assertEquals(Collections.singletonList("1"), evaluate("//*[matches(@text, '^[A-Z]')]"));
        assertEquals(Collections.singletonList("2"), evaluate("//*[lower-case(@text) = 'banana']"));
        assertEquals(2, evaluate("//*[@text = ('Apple', 'banana')]").size());
    }

    @Test
    public void evaluatesSchemaTypedExpressions() throws Exception {
        assertEquals(Collections.singletonList("6"), evaluate("xs:integer('5') + 1"));
        assertEquals(Collections.singletonList("2020-01-02"),
                evaluate("xs:date('2020-01-01') + xs:dayTimeDuration('P1D')"));
        assertEquals(Collections.singletonList("true"), evaluate("xs:decimal('1.5') gt xs:decimal('1.25')"));
    }

    @Test(expected = SAXParseException.class)
    public void rejectsDoctypeDeclarations() throws Exception {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE a [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><a>&x;</a>";
        DocumentLoader.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void shipsLicenseNotices() throws Exception {
        try (JarFile jar = new JarFile(new File(System.getProperty("shaded.jar")))) {
            for (String name : new String[] {"about.html", "plugin.properties", "META-INF/LICENSE", "META-INF/NOTICE"}) {
                assertTrue(name + " is missing", jar.getEntry(name) != null);
            }
        }
    }

    @Test
    public void bundlesDependenciesUnderPrivatePackage() throws Exception {
        List<String> unexpected = new ArrayList<>();
        int shaded = 0;
        try (JarFile jar = new JarFile(new File(System.getProperty("shaded.jar")))) {
            for (String name : Collections.list(jar.entries()).stream()
                    .map(e -> e.getName()).toArray(String[]::new)) {
                if (name.startsWith("io/appium/xpath2/shaded/")) {
                    shaded++;
                } else if (name.startsWith("org/apache/") || name.startsWith("java_cup/")
                        || name.startsWith("javax/") || name.startsWith("org/xml/")
                        || name.startsWith("org/w3c/") || name.startsWith("META-INF/services/")) {
                    unexpected.add(name);
                }
            }
        }
        assertTrue("Nothing was relocated", shaded > 1000);
        assertEquals("Entries outside of the private package", Collections.emptyList(), unexpected);
    }
}
