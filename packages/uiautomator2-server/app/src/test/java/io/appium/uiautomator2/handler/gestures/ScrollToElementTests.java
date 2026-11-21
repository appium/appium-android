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
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import io.appium.uiautomator2.common.exceptions.StaleElementReferenceException;
import io.appium.uiautomator2.model.AndroidElement;
import io.appium.uiautomator2.model.ElementsCache;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ScrollToElementTests {
    private final ElementsCache cache = mock(ElementsCache.class);

    @Test
    public void liveTargetIsReturned() {
        AndroidElement element = mock(AndroidElement.class);
        when(cache.get("id")).thenReturn(element);
        assertSame(element, ScrollToElement.getLiveElementOrNull(cache, "id"));
    }

    @Test
    public void detachedRestorableTargetIsResolvedByGestures() {
        when(cache.get("id")).thenThrow(new StaleElementReferenceException("detached"));
        when(cache.isRestorable("id")).thenReturn(true);
        assertNull(ScrollToElement.getLiveElementOrNull(cache, "id"));
    }

    @Test
    public void staleErrorIsPropagatedForNotRestorableTarget() {
        // The handler resolves the target before it swipes anything, so no swipe happens
        when(cache.get("id")).thenThrow(new StaleElementReferenceException("detached"));
        when(cache.isRestorable("id")).thenReturn(false);
        assertThrows(StaleElementReferenceException.class,
                () -> ScrollToElement.getLiveElementOrNull(cache, "id"));
    }
}
