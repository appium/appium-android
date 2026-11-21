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

package io.appium.uiautomator2.utils;

import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import io.appium.uiautomator2.model.AccessibleUiObject;
import io.appium.uiautomator2.model.AndroidElement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class ByUiAutomatorFinderTests {
    private final UiSelector instanceSelector = new UiSelector().className("android.widget.ListView").instance(1);

    @Test
    public void explicitInstanceIsSearchedInsideOfTheContext() throws UiObjectNotFoundException {
        // The device is not available in unit tests, so a lookup that ignores the context would fail
        AndroidElement context = mock(AndroidElement.class);
        AccessibleUiObject match = mock(AccessibleUiObject.class);
        when(context.getChild(instanceSelector)).thenReturn(match);

        List<AccessibleUiObject> result = ByUiAutomatorFinder.matchDescendantElements(instanceSelector, context);
        assertEquals(1, result.size());
        assertSame(match, result.get(0));
    }

    @Test
    public void explicitInstanceMissingInTheContextYieldsNoMatches() throws UiObjectNotFoundException {
        AndroidElement context = mock(AndroidElement.class);
        when(context.getChild(instanceSelector)).thenThrow(new UiObjectNotFoundException("none"));

        assertTrue(ByUiAutomatorFinder.matchDescendantElements(instanceSelector, context).isEmpty());
    }
}
