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

package io.appium.uiautomator2.handler.gestures;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScrollToTests {
    @Test
    public void visibleMatchAfterHiddenOneIsDetected() {
        assertTrue(ScrollTo.anyVisible(Arrays.asList(false, true), visible -> visible));
    }

    @Test
    public void onlyHiddenMatchesAreNotDetected() {
        assertFalse(ScrollTo.anyVisible(Arrays.asList(false, false), visible -> visible));
    }

    @Test
    public void noMatchesAreNotDetected() {
        assertFalse(ScrollTo.anyVisible(Collections.<Boolean>emptyList(), visible -> visible));
    }
}
