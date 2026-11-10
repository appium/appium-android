/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.appium.uiautomator2.core;

import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import io.appium.uiautomator2.model.settings.LimitXpathContextScope;
import io.appium.uiautomator2.model.settings.NormalizeTagNames;
import io.appium.uiautomator2.model.settings.Settings;
import io.appium.uiautomator2.utils.Attribute;
import io.appium.uiautomator2.utils.StringHelpers;
import io.appium.uiautomator2.utils.XMLHelpers;

import static net.gcardone.junidecode.Junidecode.unidecode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class AccessibilityNodeInfoDumperTests {
    private static final String ROOT_CLASS_NAME = "android.widget.FrameLayout";
    // Contains characters, which are only rewritten if tag names normalization is enabled
    private static final String NON_ASCII_CLASS_NAME = "com.example.\u00DCnic\u00F6de";
    private static final String[] CLASS_NAMES = new String[]{
            "android.widget.TextView",
            // repeated on purpose, so the second node gets its name from the cache
            "android.widget.TextView",
            "com.example.Outer$Inner",
            "a@b#c&d$e",
            "a..b...c",
            ".leading.dot",
            "trailing.dot.",
            "..both..edges..",
            "$$$",
            "1starts.with.Digit",
            "with space.And-dash_underscore",
            NON_ASCII_CLASS_NAME,
            NON_ASCII_CLASS_NAME,
            "   ",
            null,
    };

    private boolean originalNormalizeTagNames;
    private boolean originalLimitXpathContextScope;

    @Before
    public void setUp() {
        originalNormalizeTagNames = Settings.get(NormalizeTagNames.class).getValue();
        originalLimitXpathContextScope = Settings.get(LimitXpathContextScope.class).getValue();
        Settings.get(NormalizeTagNames.class).update(false);
        // Makes the dumper to take the snapshot of the provided root rather than of the active window
        Settings.get(LimitXpathContextScope.class).update(true);
    }

    @After
    public void tearDown() {
        Settings.get(NormalizeTagNames.class).update(originalNormalizeTagNames);
        Settings.get(LimitXpathContextScope.class).update(originalLimitXpathContextScope);
    }

    @Test
    public void shouldDeriveNodeNamesFromClassNames() throws Exception {
        assertEquals(expectedNodeNames(false), nodeNames(newDumper().dumpToXml()));
    }

    @Test
    public void shouldDeriveNormalizedNodeNamesFromClassNames() throws Exception {
        Settings.get(NormalizeTagNames.class).update(true);
        assertEquals(expectedNodeNames(true), nodeNames(newDumper().dumpToXml()));
    }

    @Test
    public void shouldNotReuseNodeNamesBetweenDumpsOfTheSameDumper() throws Exception {
        AccessibilityNodeInfoDumper dumper = newDumper();

        List<String> plainNames = nodeNames(dumper.dumpToXml());
        Settings.get(NormalizeTagNames.class).update(true);
        List<String> normalizedNames = nodeNames(dumper.dumpToXml());
        Settings.get(NormalizeTagNames.class).update(false);
        List<String> plainNamesAgain = nodeNames(dumper.dumpToXml());

        assertEquals(expectedNodeNames(false), plainNames);
        assertEquals(expectedNodeNames(true), normalizedNames);
        assertEquals(expectedNodeNames(false), plainNamesAgain);
        // Makes sure the setting does change the output for this tree
        assertNotEquals(plainNames, normalizedNames);
    }

    @Test
    public void shouldProduceTheSameXmlForRepeatedDumps() {
        AccessibilityNodeInfoDumper dumper = newDumper();
        assertEquals(dumper.dumpToXml(), dumper.dumpToXml());
        assertEquals(newDumper().dumpToXml(), newDumper().dumpToXml());
    }

    /**
     * The algorithm, which was used to derive XML node names from class names
     * before the names caching has been introduced.
     */
    private static String referenceNodeName(@Nullable String className, boolean shouldNormalize) {
        if (StringHelpers.isBlank(className)) {
            return View.class.getName();
        }
        String fixedName = className
                .replaceAll("[$@#&]", ".")
                .replaceAll("\\.+", ".")
                .replaceAll("(^\\.|\\.$)", "");
        if (shouldNormalize) {
            fixedName = unidecode(fixedName).replaceAll("[^A-Za-z0-9\\-._]", "_");
        }
        fixedName = XMLHelpers.toNodeName(fixedName);
        return StringHelpers.isBlank(fixedName) ? View.class.getName() : fixedName;
    }

    private static List<String> expectedNodeNames(boolean shouldNormalize) {
        List<String> result = new ArrayList<>();
        result.add(referenceNodeName(ROOT_CLASS_NAME, shouldNormalize));
        for (String className : CLASS_NAMES) {
            result.add(referenceNodeName(className, shouldNormalize));
        }
        return result;
    }

    private static AccessibilityNodeInfoDumper newDumper() {
        AccessibilityNodeInfo[] children = new AccessibilityNodeInfo[CLASS_NAMES.length];
        for (int i = 0; i < CLASS_NAMES.length; ++i) {
            children[i] = node(CLASS_NAMES[i]);
        }
        return new AccessibilityNodeInfoDumper(
                node(ROOT_CLASS_NAME, children), EnumSet.of(Attribute.CLASS, Attribute.DISPLAYED)
        );
    }

    private static AccessibilityNodeInfo node(@Nullable String className,
                                               AccessibilityNodeInfo... children) {
        AccessibilityNodeInfo node = mock(AccessibilityNodeInfo.class);
        when(node.getClassName()).thenReturn(className);
        when(node.isVisibleToUser()).thenReturn(true);
        when(node.getChildCount()).thenReturn(children.length);
        for (int i = 0; i < children.length; ++i) {
            when(node.getChild(i)).thenReturn(children[i]);
        }
        return node;
    }

    /**
     * @return names of the root element and of its children in the document order
     */
    private static List<String> nodeNames(String xml) throws Exception {
        Element root = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
        List<String> result = new ArrayList<>();
        result.add(root.getNodeName());
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); ++i) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                result.add(children.item(i).getNodeName());
            }
        }
        return result;
    }
}
