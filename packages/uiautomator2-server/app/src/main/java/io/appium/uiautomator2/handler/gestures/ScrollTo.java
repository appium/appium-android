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

import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import java.util.Arrays;
import java.util.List;

import io.appium.uiautomator2.common.exceptions.ElementNotFoundException;
import io.appium.uiautomator2.common.exceptions.InvalidArgumentException;
import io.appium.uiautomator2.handler.request.SafeRequestHandler;
import io.appium.uiautomator2.http.AppiumResponse;
import io.appium.uiautomator2.http.IHttpRequest;
import io.appium.uiautomator2.model.AccessibleUiObject;
import io.appium.uiautomator2.model.AndroidElement;
import io.appium.uiautomator2.model.AppiumUIA2Driver;
import io.appium.uiautomator2.model.By;
import io.appium.uiautomator2.model.ElementsCache;
import io.appium.uiautomator2.model.api.ScrollToModel;
import io.appium.uiautomator2.model.internal.CustomUiDevice;
import io.appium.uiautomator2.model.internal.ElementsLookupStrategy;
import io.appium.uiautomator2.utils.Logger;

import static io.appium.uiautomator2.model.internal.ElementsLookupStrategy.BY_ACCESSIBILITY_ID;
import static io.appium.uiautomator2.model.internal.ElementsLookupStrategy.BY_CLASS;
import static io.appium.uiautomator2.model.internal.ElementsLookupStrategy.BY_UIAUTOMATOR;
import static io.appium.uiautomator2.utils.AXWindowHelpers.resetAccessibilityCache;
import static io.appium.uiautomator2.utils.Device.scrollToElement;
import static io.appium.uiautomator2.utils.ElementLocationHelpers.findElement;
import static io.appium.uiautomator2.utils.ElementLocationHelpers.toSelector;
import static io.appium.uiautomator2.utils.ModelUtils.toModel;
import static io.appium.uiautomator2.utils.ScrollHelpers.DEFAULT_MAX_SWIPES;
import static io.appium.uiautomator2.utils.ScrollHelpers.defaultDirection;
import static io.appium.uiautomator2.utils.ScrollHelpers.scrollUntilVisible;

public class ScrollTo extends SafeRequestHandler {
    private static final List<String> SUPPORTED_STRATEGIES =
            Arrays.asList(BY_ACCESSIBILITY_ID.toString(), BY_CLASS.toString(), BY_UIAUTOMATOR.toString());

    public ScrollTo(String mappedUri) {
        super(mappedUri);
    }

    @Override
    protected AppiumResponse safeHandle(IHttpRequest request) throws UiObjectNotFoundException {
        ScrollToModel model = toModel(request, ScrollToModel.class);
        String strategy = model.params.strategy;
        String selector = model.params.selector;
        final By by = ElementsLookupStrategy.ofName(strategy).toNativeSelector(selector);
        final ElementsCache ke = AppiumUIA2Driver.getInstance().getSessionOrThrow().getElementsCache();
        final AndroidElement originElement = model.origin == null
                ? null
                : ke.get(model.origin.getUnifiedId());

        // Whatever worked before keeps using UiScrollable. The new implementation serves
        // the strategies and the origin elements UiScrollable cannot handle, as well as the new options.
        final boolean isLegacyStrategy = by instanceof By.ByAccessibilityId
                || by instanceof By.ByClass || by instanceof By.ByAndroidUiAutomator;
        final boolean hasNewOptions = model.params.direction != null || model.params.fromStart != null;
        final boolean isLegacyOrigin = originElement == null || originElement.getUiObject() instanceof UiObject;
        if (isLegacyStrategy && isLegacyOrigin && !hasNewOptions) {
            scrollWithUiScrollable(model, by, originElement);
        } else {
            scrollWithoutUiScrollable(model, by, originElement);
        }

        Logger.info(String.format("Scrolled via strategy: '%s' and selector '%s'.", strategy, selector));

        return new AppiumResponse(getSessionId(request));
    }

    private static void scrollWithUiScrollable(
            ScrollToModel model, By by, AndroidElement originElement) throws UiObjectNotFoundException {
        final UiSelector uiselector;
        if (by instanceof By.ByAccessibilityId) {
            uiselector = new UiSelector().description(model.params.selector);
        } else if (by instanceof By.ByClass) {
            uiselector = new UiSelector().className(model.params.selector);
        } else if (by instanceof By.ByAndroidUiAutomator) {
            uiselector = toSelector(by.getElementLocator());
        } else {
            throw new InvalidArgumentException(
                    String.format("Unsupported strategy: '%s'. The only supported strategies are: %s.",
                            model.params.strategy, SUPPORTED_STRATEGIES));
        }

        UiScrollable origin = null;
        if (originElement != null) {
            origin = new UiScrollable(((UiObject) originElement.getUiObject()).getSelector());
        }
        scrollToElement(origin, uiselector, model.params.maxSwipes);
    }

    private static void scrollWithoutUiScrollable(
            ScrollToModel model, By by, AndroidElement originElement) throws UiObjectNotFoundException {
        final AndroidElement container = originElement == null ? findFirstScrollable() : originElement;
        final Direction direction = model.params.getDirection() == null
                ? defaultDirection(container)
                : model.params.getDirection();
        final int maxSwipes = model.params.maxSwipes == null ? DEFAULT_MAX_SWIPES : model.params.maxSwipes;
        if (maxSwipes <= 0) {
            throw new IllegalArgumentException("maxSwipes must be greater than zero");
        }

        final boolean isFound = scrollUntilVisible(container, () -> isPresent(by, container),
                direction, Boolean.TRUE.equals(model.params.fromStart), maxSwipes);
        if (!isFound) {
            throw new ElementNotFoundException(String.format("Cannot scroll to %s", by));
        }
    }

    private static AndroidElement findFirstScrollable() {
        AccessibleUiObject scrollable = CustomUiDevice.getInstance()
                .findObject(androidx.test.uiautomator.By.scrollable(true));
        if (scrollable == null) {
            throw new ElementNotFoundException("There are no scrollable elements on the current display. " +
                    "Provide the element to scroll");
        }
        return ElementsCache.toAndroidElement(scrollable, true, null, null);
    }

    private static boolean isPresent(By by, AndroidElement container) {
        resetAccessibilityCache();
        try {
            return findElement(by, container) != null;
        } catch (UiObjectNotFoundException | ElementNotFoundException e) {
            return false;
        }
    }
}
