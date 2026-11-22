package io.appium.xpath2;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.wst.xml.xpath2.api.Item;
import org.eclipse.wst.xml.xpath2.api.ResultSequence;
import org.eclipse.wst.xml.xpath2.processor.Engine;
import org.eclipse.wst.xml.xpath2.processor.util.DynamicContextBuilder;
import org.eclipse.wst.xml.xpath2.processor.util.StaticContextBuilder;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The context position of a predicate on a reverse axis counts in reverse document order,
 * and the result of the step is in document order (XPath 2.0, section 3.2.2).
 */
public class ReverseAxisPositionTest {
    private static final String XML = "<r><a n=\"1\"/><a n=\"2\"/><a n=\"3\"/><b/>"
            + "<c><a n=\"4\"/></c></r>";

    private Document doc;

    @Before
    public void parse() throws Exception {
        doc = DocumentLoader.parse(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)));
    }

    private static String label(Element element) {
        return element.hasAttribute("n")
                ? element.getTagName() + element.getAttribute("n")
                : element.getTagName();
    }

    private List<String> evaluate(String xpath, Object context) throws Exception {
        StaticContextBuilder scb = new StaticContextBuilder();
        ResultSequence items = new Engine().parseExpression(xpath, scb)
                .evaluate(new DynamicContextBuilder(scb), new Object[] {context});
        List<String> result = new ArrayList<>();
        for (Iterator<Item> it = items.iterator(); it.hasNext();) {
            Object value = it.next().getNativeValue();
            result.add(value instanceof Element ? label((Element) value) : String.valueOf(value));
        }
        return result;
    }

    private List<String> evaluate(String xpath) throws Exception {
        return evaluate(xpath, doc);
    }

    private static List<String> list(String... values) {
        return Arrays.asList(values);
    }

    @Test
    public void precedingSiblingCountsNearestFirst() throws Exception {
        assertEquals(list("a3"), evaluate("//b/preceding-sibling::*[1]"));
        assertEquals(list("a2"), evaluate("//b/preceding-sibling::*[2]"));
        assertEquals(list("a1"), evaluate("//b/preceding-sibling::*[3]"));
        assertEquals(list(), evaluate("//b/preceding-sibling::*[4]"));
        assertEquals(list("a3"), evaluate("//b/preceding-sibling::a[1]"));
        assertEquals(list("a3"), evaluate("//b/preceding-sibling::*[position() = 1]"));
        assertEquals(list("a1"), evaluate("//b/preceding-sibling::*[last()]"));
        assertEquals(list("a2"), evaluate("//b/preceding-sibling::*[last() - 1]"));
        assertEquals(list("a2"), evaluate("//a[@n = '3']/preceding-sibling::*[1]"));
    }

    @Test
    public void predicatesAreAppliedInOrder() throws Exception {
        assertEquals(list("a3"), evaluate("//b/preceding-sibling::*[1][@n = '3']"));
        assertEquals(list(), evaluate("//b/preceding-sibling::*[2][@n = '3']"));
        assertEquals(list("a3"), evaluate("//b/preceding-sibling::*[@n != '2'][1]"));
        assertEquals(list("a1"), evaluate("//b/preceding-sibling::*[@n != '2'][2]"));
        assertEquals(list("a1"), evaluate("//b/preceding-sibling::*[@n != '3'][last()]"));
    }

    @Test
    public void stepResultsAreInDocumentOrder() throws Exception {
        assertEquals(list("a1", "a2", "a3"), evaluate("//b/preceding-sibling::*"));
        assertEquals(list("a2", "a3"), evaluate("//b/preceding-sibling::*[position() < 3]"));
        assertEquals(list("a1", "a3"), evaluate("//b/preceding-sibling::*[@n != '2']"));
        // each context node has its own nearest sibling, and the union has no duplicates
        assertEquals(list("a1", "a2"), evaluate("//a/preceding-sibling::*[1]"));
        assertEquals(list("a1", "a2"), evaluate("//a/preceding-sibling::*[position() < 3]"));
        assertEquals(list("a1"), evaluate("(//b/preceding-sibling::*)[1]"));
    }

    @Test
    public void ancestorAxesCountNearestFirst() throws Exception {
        assertEquals(list("c"), evaluate("//a[@n = '4']/ancestor::*[1]"));
        assertEquals(list("r"), evaluate("//a[@n = '4']/ancestor::*[2]"));
        assertEquals(list("r"), evaluate("//a[@n = '4']/ancestor::*[last()]"));
        assertEquals(list("a4"), evaluate("//a[@n = '4']/ancestor-or-self::*[1]"));
        assertEquals(list("c"), evaluate("//a[@n = '4']/ancestor-or-self::*[2]"));
        assertEquals(list("r"), evaluate("//a[@n = '4']/ancestor-or-self::*[last()]"));
        assertEquals(list("r", "c", "a4"), evaluate("//a[@n = '4']/ancestor-or-self::*"));
        assertEquals(list("r", "c"), evaluate("//a[@n = '4']/ancestor-or-self::*[position() > 1]"));
        assertEquals(list("c"), evaluate("//a[@n = '4']/parent::*[1]"));
        assertEquals(list("c"), evaluate("//a[@n = '4']/..[1]"));
        assertEquals(list("r"), evaluate("(//a[@n = '4']/ancestor::*)[1]"));
    }

    @Test
    public void precedingCountsNearestFirst() throws Exception {
        assertEquals(list("b"), evaluate("//c/preceding::*[1]"));
        assertEquals(list("a3"), evaluate("//c/preceding::*[2]"));
        assertEquals(list("a2"), evaluate("//c/preceding::*[3]"));
        assertEquals(list("a1"), evaluate("//c/preceding::*[last()]"));
        // the ancestors are not on the preceding axis
        assertEquals(list("b"), evaluate("//a[@n = '4']/preceding::*[1]"));
        assertEquals(list("a1"), evaluate("//a[@n = '4']/preceding::*[4]"));
        assertEquals(list("a1", "a2", "a3", "b"), evaluate("//a[@n = '4']/preceding::*"));
        assertEquals(list("a3", "b"), evaluate("//a[@n = '4']/preceding::*[position() < 3]"));
    }

    @Test
    public void relativeStepsFromAnElementAreInDocumentOrderToo() throws Exception {
        Element b = (Element) doc.getElementsByTagName("b").item(0);
        assertEquals(list("a3"), evaluate("preceding-sibling::*[1]", b));
        assertEquals(list("a1"), evaluate("preceding-sibling::*[last()]", b));
        assertEquals(list("a2", "a3"), evaluate("preceding-sibling::*[position() < 3]", b));
        assertEquals(list("a1", "a3"), evaluate("preceding-sibling::*[@n != '2']", b));
        assertEquals(list("r"), evaluate("ancestor::*[1]", b));
    }

    @Test
    public void forwardAxesAndOtherQueriesAreUnchanged() throws Exception {
        assertEquals(list("c"), evaluate("//b/following-sibling::*[1]"));
        assertEquals(list("a3"), evaluate("//a[@n = '1']/following-sibling::*[2]"));
        assertEquals(list("c"), evaluate("//a[@n = '1']/following-sibling::*[last()]"));
        assertEquals(list("a1"), evaluate("/r/a[1]"));
        assertEquals(list("a1", "a2", "a3", "a4"), evaluate("//a"));
        assertEquals(list("a4"), evaluate("//a[last()][@n = '4']"));
        assertEquals(Collections.singletonList("c"), evaluate("//a[@n = '4']/ancestor::c"));
    }
}
