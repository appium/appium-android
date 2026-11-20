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

import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import io.appium.uiautomator2.handler.request.SafeRequestHandler;
import io.appium.uiautomator2.http.AppiumResponse;
import io.appium.uiautomator2.http.IHttpRequest;
import io.appium.uiautomator2.model.AndroidElement;
import io.appium.uiautomator2.model.AppiumUIA2Driver;
import io.appium.uiautomator2.model.ElementsCache;

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
        AndroidElement scrollToElement = ke.get(elementIds[1]);

        // UiScrollable only works with UiObject instances, which are the result of
        // the '-android uiautomator' locator strategy. Everything else is scrolled by gestures.
        if (!(element.getUiObject() instanceof UiObject) || !(scrollToElement.getUiObject() instanceof UiObject)) {
            final boolean isVisible = scrollUntilVisible(element, () -> isDisplayed(scrollToElement),
                    defaultDirection(element), true, DEFAULT_MAX_SWIPES);
            if (!isVisible) {
                throw new UiObjectNotFoundException("Cannot scroll to the given element");
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

    private static boolean isDisplayed(AndroidElement element) {
        try {
            return Boolean.parseBoolean(element.getAttribute("displayed"));
        } catch (UiObjectNotFoundException e) {
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
