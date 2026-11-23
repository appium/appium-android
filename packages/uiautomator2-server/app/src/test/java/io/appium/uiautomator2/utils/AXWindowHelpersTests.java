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

import android.view.accessibility.AccessibilityWindowInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class AXWindowHelpersTests {

    private static AccessibilityWindowInfo window(int type, int layer, boolean active, boolean focused) {
        AccessibilityWindowInfo window = mock(AccessibilityWindowInfo.class);
        when(window.getType()).thenReturn(type);
        when(window.getLayer()).thenReturn(layer);
        when(window.isActive()).thenReturn(active);
        when(window.isFocused()).thenReturn(focused);
        return window;
    }

    @Test
    public void theActiveWindowIsPreferred() {
        AccessibilityWindowInfo top = window(AccessibilityWindowInfo.TYPE_APPLICATION, 5, false, true);
        AccessibilityWindowInfo active = window(AccessibilityWindowInfo.TYPE_APPLICATION, 1, true, false);
        assertSame(active, AXWindowHelpers.selectActiveWindow(Arrays.asList(top, active)));
    }

    @Test
    public void theFocusedWindowIsTheNextChoice() {
        AccessibilityWindowInfo top = window(AccessibilityWindowInfo.TYPE_APPLICATION, 5, false, false);
        AccessibilityWindowInfo focused = window(AccessibilityWindowInfo.TYPE_APPLICATION, 1, false, true);
        assertSame(focused, AXWindowHelpers.selectActiveWindow(Arrays.asList(top, focused)));
    }

    @Test
    public void theTopmostApplicationWindowIsTheFallback() {
        AccessibilityWindowInfo bar = window(AccessibilityWindowInfo.TYPE_SYSTEM, 9, false, false);
        AccessibilityWindowInfo low = window(AccessibilityWindowInfo.TYPE_APPLICATION, 1, false, false);
        AccessibilityWindowInfo high = window(AccessibilityWindowInfo.TYPE_APPLICATION, 3, false, false);
        assertSame(high, AXWindowHelpers.selectActiveWindow(Arrays.asList(bar, low, high)));
    }

    @Test
    public void theTopmostWindowIsUsedIfThereIsNoApplicationOne() {
        AccessibilityWindowInfo low = window(AccessibilityWindowInfo.TYPE_SYSTEM, 1, false, false);
        AccessibilityWindowInfo high = window(AccessibilityWindowInfo.TYPE_SYSTEM, 3, false, false);
        assertSame(high, AXWindowHelpers.selectActiveWindow(Arrays.asList(low, high)));
    }

    @Test
    public void thereIsNoWindowWithoutWindows() {
        assertNull(AXWindowHelpers.selectActiveWindow(Collections.emptyList()));
    }
}
