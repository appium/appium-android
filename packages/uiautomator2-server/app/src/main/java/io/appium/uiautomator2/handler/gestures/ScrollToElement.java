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

import androidx.annotation.Nullable;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import io.appium.uiautomator2.common.exceptions.StaleElementReferenceException;
import io.appium.uiautomator2.common.exceptions.UiAutomator2Exception;
import io.appium.uiautomator2.handler.request.SafeRequestHandler;
import io.appium.uiautomator2.http.AppiumResponse;
import io.appium.uiautomator2.http.IHttpRequest;
import io.appium.uiautomator2.model.AndroidElement;
import io.appium.uiautomator2.model.AppiumUIA2Driver;
import io.appium.uiautomator2.model.ElementsCache;

import static io.appium.uiautomator2.utils.AXWindowHelpers.resetAccessibilityCache;
import static io.appium.uiautomator2.utils.ScrollHelpers.DEFAULT_MAX_SWIPES;
import static io.appium.uiautomator2.utils.ScrollHelpers.defaultDirection;
import static io.appium.uiautomator2.utils.ScrollHelpers.scrollUntilVisible;
import static java.util.Objects.requireNonNull;

public class ScrollToElement extends SafeRequestHandler {

    public ScrollToElement(String mappedUri) {
        super(mappedUri);
    }

    @Override
    protected AppiumResponse safeHandle(IHttpRequest request) throws UiObjectNotFoundException {
        String[] elementIds = getElementIds(request);
        ElementsCache ke = AppiumUIA2Driver.getInstance().getSessionOrThrow().getElementsCache();
        AndroidElement element = ke.get(elementIds[0]);
        AndroidElement scrollToElement = getLiveElementOrNull(ke, elementIds[1]);

        // UiScrollable only works with UiObject instances, which are the result of
        // the '-android uiautomator' locator strategy. Everything else is scrolled by gestures.
        if (!(element.getUiObject() instanceof UiObject)
                || scrollToElement == null || !(scrollToElement.getUiObject() instanceof UiObject)) {
            final boolean isVisible = scrollUntilVisible(element, () -> isDisplayed(ke, elementIds[1]),
                    defaultDirection(element), true, DEFAULT_MAX_SWIPES);
            if (!isVisible) {
                throw new UiObjectNotFoundException(String.format(
                        "Cannot scroll the element '%s' to the element '%s'. Neither of them was located " +
                                "using the '-android uiautomator' strategy, so the view has been scrolled " +
                                "with gestures from the beginning, up to %s swipes",
                        elementIds[0], elementIds[1], DEFAULT_MAX_SWIPES));
            }
            return new AppiumResponse(getSessionId(request));
        }

        UiObject elementUiObject = (UiObject) element.getUiObject();
        UiObject scrollElementUiObject = (UiObject) scrollToElement.getUiObject();
        UiScrollable uiScrollable = new UiScrollable(requireNonNull(elementUiObject).getSelector());
        if (!uiScrollable.scrollIntoView(requireNonNull(scrollElementUiObject))) {
            throw new UiObjectNotFoundException(String.format("Cannot scroll to %s element",
                    requireNonNull(scrollElementUiObject).getSelector()));
        }
        return new AppiumResponse(getSessionId(request));
    }

    /**
     * A target detached by a virtualized list is resolved by gestures while the container scrolls,
     * so null is returned for it. A target the cache cannot restore would never be found again,
     * so the stale element error is propagated before anything is swiped.
     */
    @Nullable
    static AndroidElement getLiveElementOrNull(ElementsCache cache, String elementId) {
        try {
            return cache.get(elementId);
        } catch (StaleElementReferenceException e) {
            if (!cache.isRestorable(elementId)) {
                throw e;
            }
            return null;
        }
    }

    private static boolean isDisplayed(ElementsCache cache, String elementId) {
        resetAccessibilityCache();
        try {
            // The cache restores the elements that became stale after scrolling
            return Boolean.parseBoolean(cache.get(elementId).getAttribute("displayed"));
        } catch (UiObjectNotFoundException | StaleObjectException | UiAutomator2Exception e) {
            return false;
        }
    }

    private static class UiScrollable extends androidx.test.uiautomator.UiScrollable {
        public UiScrollable(UiSelector container) {
            super(container);
        }

        @Override
        public boolean scrollIntoView(UiObject obj) throws UiObjectNotFoundException {
            if (obj.exists()) {
                return true;
            }

            // we will need to reset the search from the beginning to start search
            flingToBeginning(getMaxSearchSwipes());
            if (obj.exists()) {
                return true;
            }

            for (int x = 0; x < getMaxSearchSwipes(); x++) {
                if (!scrollForward()) {
                    return false;
                }

                if (obj.exists()) {
                    return true;
                }
            }

            return false;
        }

    }
}
