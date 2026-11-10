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

package io.appium.uiautomator2.model.settings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum Settings {
    ACTION_ACKNOWLEDGMENT_TIMEOUT(new ActionAcknowledgmentTimeout()),
    ALLOW_INVISIBLE_ELEMENTS(new AllowInvisibleElements()),
    COMPRESSED_LAYOUT_HIERARCHY(new CompressedLayoutHierarchy()),
    ELEMENT_RESPONSE_ATTRIBUTES(new ElementResponseAttributes()),
    ENABLE_MULTI_WINDOWS(new EnableMultiWindows()),
    ENABLE_NOTIFICATION_LISTENER(new EnableNotificationListener()),
    ENABLE_TOPMOST_WINDOW_FROM_ACTIVE_PACKAGE(new EnableTopmostWindowFromActivePackage()),
    KEY_INJECTION_DELAY(new KeyInjectionDelay()),
    SCROLL_ACKNOWLEDGMENT_TIMEOUT(new ScrollAcknowledgmentTimeout()),
    SHOULD_USE_COMPACT_RESPONSES(new ShouldUseCompactResponses()),
    WAIT_FOR_IDLE_TIMEOUT(new WaitForIdleTimeout()),
    WAIT_FOR_SELECTOR_TIMEOUT(new WaitForSelectorTimeout()),
    NORMALIZE_TAG_NAMES(new NormalizeTagNames()),
    ENFORCE_XPATH1(new EnforceXpath1()),
    DISABLE_IDS_AUTOCOMPLETION(new DisableIdLocatorAutocompletion()),
    LIMIT_XPATH_CONTEXT_SCOPE(new LimitXpathContextScope()),
    INCLUDE_EXTRAS_IN_PAGE_SOURCE(new IncludeExtrasInPageSource()),
    INCLUDE_A11Y_ACTIONS_IN_PAGE_SOURCE(new IncludeA11yActionsInPageSource()),
    INCLUDE_EXTRA_RENDERING_INFO(new IncludeExtraRenderingInfo()),
    SHUTDOWN_ON_POWER_DISCONNECT(new ShutdownOnPowerDisconnect()),
    SIMPLE_BOUNDS_CALCULATION(new SimpleBoundsCalculation()),
    TRACK_SCROLL_EVENTS(new TrackScrollEvents()),
    WAKE_LOCK_TIMEOUT(new WakeLockTimeout()),
    SERVER_PORT(new ServerPort()),
    MJPEG_SERVER_PORT(new MjpegServerPort()),
    MJPEG_SERVER_FRAMERATE(new MjpegServerFramerate()),
    MJPEG_SCALING_FACTOR(new MjpegScalingFactor()),
    MJPEG_SERVER_SCREENSHOT_QUALITY(new MjpegServerScreenshotQuality()),
    MJPEG_BILINEAR_FILTERING(new MjpegBilinearFiltering()),
    USE_RESOURCES_FOR_ORIENTATION_DETECTION(new UseResourcesForOrientationDetection()),
    SNAPSHOT_MAX_DEPTH(new SnapshotMaxDepth()),
    CURRENT_DISPLAY_ID(new CurrentDisplayId()),
    ALWAYS_TRAVERSABLE_VIEW_CLASSES(new AlwaysTraversableViewClasses()),
    DEFER_ACCESSIBILITY_CACHE_RESET(new DeferAccessibilityCacheReset()),
    MAP_TEST_TAG_TO_RESOURCE_ID(new MapTestTagToResourceId());

    // Settings are looked up several times per node while the page source is built,
    // so the lookup must not allocate or scan all the items.
    private static final Map<Class<?>, ISetting<?>> SETTINGS_BY_TYPE = indexByType();

    private final ISetting<?> setting;

    Settings(ISetting<?> setting) {
        this.setting = setting;
    }

    public ISetting<?> getSetting() {
        return setting;
    }

    public static <T extends ISetting<?>> T get(Class<T> settingType) {
        ISetting<?> result = SETTINGS_BY_TYPE.get(settingType);
        if (result == null) {
            throw new IllegalArgumentException(String.format("%s setting is not known",
                    settingType.getCanonicalName()));
        }
        return settingType.cast(result);
    }

    public static void resetForNewSession() {
        for (Settings enumItem: values()) {
            if (enumItem.getSetting().isTiedToSession()) {
                enumItem.getSetting().reset();
            }
        }
    }

    @Override
    public String toString() {
        return setting.getName();
    }

    private static Map<Class<?>, ISetting<?>> indexByType() {
        Map<Class<?>, ISetting<?>> result = new HashMap<>();
        for (Settings enumItem : values()) {
            result.put(enumItem.getSetting().getClass(), enumItem.getSetting());
        }
        return Collections.unmodifiableMap(result);
    }
}
