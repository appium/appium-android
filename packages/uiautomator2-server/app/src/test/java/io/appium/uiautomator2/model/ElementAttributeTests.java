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

package io.appium.uiautomator2.model;

import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import io.appium.uiautomator2.common.exceptions.NoSuchAttributeException;
import io.appium.uiautomator2.core.AxNodeInfoExtractor;
import io.appium.uiautomator2.utils.Attribute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Every attribute named in the "Only the following attributes are supported" error must be readable
 * with getAttribute, not only in the page source (appium/appium-uiautomator2-driver#893).
 * The values come from the page source reader {@link UiElementSnapshot#getAttributeValue}, which is
 * stubbed here because the unit test Android runtime predates these AccessibilityNodeInfo getters.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({AxNodeInfoExtractor.class, UiElementSnapshot.class})
public class ElementAttributeTests {
    @Mock
    private AccessibilityNodeInfo node;
    @Mock
    private UiObject2 uiObject2;
    @Mock
    private UiObject uiObject;

    private BaseElement uiObject2Element;
    private BaseElement uiObjectElement;

    @Before
    public void setup() {
        PowerMockito.mockStatic(AxNodeInfoExtractor.class);
        when(AxNodeInfoExtractor.toAxNodeInfo(any())).thenReturn(node);
        PowerMockito.mockStatic(UiElementSnapshot.class);
        when(UiElementSnapshot.getAttributeValue(node, Attribute.ERROR_TEXT)).thenReturn("Enter a valid email");
        when(UiElementSnapshot.getAttributeValue(node, Attribute.PANE_TITLE)).thenReturn("Sign in");
        when(UiElementSnapshot.getAttributeValue(node, Attribute.HEADING)).thenReturn(true);
        when(UiElementSnapshot.getAttributeValue(node, Attribute.INPUT_TYPE)).thenReturn(33);
        uiObject2Element = new UiObject2Element(uiObject2, true, null, null);
        uiObjectElement = new UiObjectElement(uiObject, true, null, null);
    }

    private void assertAttributes(BaseElement element) throws Exception {
        assertEquals("Enter a valid email", element.getAttribute("error"));
        assertEquals("Enter a valid email", element.getAttribute("errorText"));
        assertEquals("Sign in", element.getAttribute("pane-title"));
        assertEquals("true", element.getAttribute("heading"));
        assertEquals("33", element.getAttribute("input-type"));
        // an attribute the node does not expose is null, the same as in the page source
        assertNull(element.getAttribute("tooltip-text"));
    }

    @Test
    public void uiObject2ElementReturnsPageSourceAttributes() throws Exception {
        assertAttributes(uiObject2Element);
    }

    @Test
    public void uiObjectElementReturnsPageSourceAttributes() throws Exception {
        assertAttributes(uiObjectElement);
    }

    @Test
    public void unknownAttributeStillFails() {
        assertThrows(NoSuchAttributeException.class, () -> uiObject2Element.getAttribute("no-such-attribute"));
        assertThrows(NoSuchAttributeException.class, () -> uiObjectElement.getAttribute("no-such-attribute"));
    }
}
