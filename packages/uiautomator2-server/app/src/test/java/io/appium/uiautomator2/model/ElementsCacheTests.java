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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ElementsCacheTests {
    private final ElementsCache cache = new ElementsCache(10);

    private AndroidElement element(String id, boolean isSingleMatch, String contextId) {
        AndroidElement result = mock(AndroidElement.class);
        when(result.getId()).thenReturn(id);
        when(result.getBy()).thenReturn(mock(By.class));
        when(result.isSingleMatch()).thenReturn(isSingleMatch);
        when(result.getContextId()).thenReturn(contextId);
        cache.put(result);
        return result;
    }

    private static void stale(AndroidElement element) {
        try {
            when(element.getName()).thenThrow(new IllegalStateException("stale"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void singleMatchWithLocatorIsRestorable() {
        element("a", true, null);
        assertTrue(cache.isRestorable("a"));
    }

    @Test
    public void multiMatchIsNotRestorable() {
        element("a", false, null);
        assertFalse(cache.isRestorable("a"));
    }

    @Test
    public void elementWithoutLocatorIsNotRestorable() {
        AndroidElement element = element("a", true, null);
        when(element.getBy()).thenReturn(null);
        assertFalse(cache.isRestorable("a"));
    }

    @Test
    public void unknownElementIsNotRestorable() {
        assertFalse(cache.isRestorable("a"));
    }

    @Test
    public void childOfRestorableParentIsRestorable() {
        element("parent", true, null);
        element("child", true, "parent");
        assertTrue(cache.isRestorable("child"));
    }

    @Test
    public void childOfLiveMultiMatchParentIsRestorable() {
        element("parent", false, null);
        element("child", true, "parent");
        assertTrue(cache.isRestorable("child"));
    }

    @Test
    public void childOfStaleMultiMatchParentIsNotRestorable() {
        stale(element("parent", false, null));
        element("child", true, "parent");
        assertFalse(cache.isRestorable("child"));
    }

    @Test
    public void childOfStaleRestorableParentIsRestorable() {
        stale(element("parent", true, null));
        element("child", true, "parent");
        assertTrue(cache.isRestorable("child"));
    }

    @Test
    public void childOfStaleParentWithStaleMultiMatchGrandparentIsNotRestorable() {
        stale(element("grandparent", false, null));
        stale(element("parent", true, "grandparent"));
        element("child", true, "parent");
        assertFalse(cache.isRestorable("child"));
    }

    @Test
    public void childOfEvictedParentIsNotRestorable() {
        element("child", true, "parent");
        assertFalse(cache.isRestorable("child"));
    }

    @Test
    public void ownContextDoesNotLoop() {
        element("a", true, "a");
        assertTrue(cache.isRestorable("a"));
    }
}
