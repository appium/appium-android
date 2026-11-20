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

import android.widget.HorizontalScrollView;

import androidx.annotation.Nullable;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObjectNotFoundException;

import java.util.function.BooleanSupplier;

import io.appium.uiautomator2.model.AccessibleUiObject;
import io.appium.uiautomator2.model.AndroidElement;
import io.appium.uiautomator2.model.internal.CustomUiDevice;

import static io.appium.uiautomator2.utils.AXWindowHelpers.resetAccessibilityCache;

public final class ScrollHelpers {
    public static final int DEFAULT_MAX_SWIPES = 30;
    // A swipe covers less than a page, so no items are skipped
    private static final float SWIPE_PERCENT = 0.7f;

    private ScrollHelpers() {
    }

    /**
     * @return the first scrollable element on the current display or null if there is none
     */
    @Nullable
    public static AccessibleUiObject findFirstScrollable() {
        // Without the reset, the window roots of the previous screen may be used
        resetAccessibilityCache();
        return CustomUiDevice.getInstance().findObject(androidx.test.uiautomator.By.scrollable(true));
    }

    /**
     * Horizontal scroll views are scrolled to the right, everything else to the bottom.
     * Other horizontal containers (for example, a RecyclerView) need an explicit direction.
     */
    public static Direction defaultDirection(AndroidElement container) throws UiObjectNotFoundException {
        return HorizontalScrollView.class.getName().equals(container.getAttribute("class"))
                ? Direction.RIGHT
                : Direction.DOWN;
    }

    /**
     * Scrolls the container until the target becomes visible.
     *
     * @return true if the target is visible
     */
    public static boolean scrollUntilVisible(AndroidElement container, BooleanSupplier isTargetVisible,
                                             Direction direction, boolean fromStart, int maxSwipes) {
        return ScrollSearch.run(new ScrollSearch.Steps() {
            @Override
            public boolean isTargetVisible() {
                return isTargetVisible.getAsBoolean();
            }

            @Override
            public boolean scrollForward() {
                return container.scroll(direction, SWIPE_PERCENT);
            }

            @Override
            public void scrollToStart(int maxSwipes) {
                // Values above 1 scroll that many pages, stopping at the end of the content.
                // Like UiScrollable, the way back is limited by the maximum number of swipes.
                container.scroll(Direction.reverse(direction), maxSwipes);
            }
        }, fromStart, maxSwipes);
    }
}
