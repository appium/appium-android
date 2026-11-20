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

/**
 * The "scroll until the target is visible" algorithm, independent of how the target is located
 * and how the container is scrolled.
 */
public final class ScrollSearch {
    private ScrollSearch() {
    }

    public interface Steps {
        boolean isTargetVisible();

        /**
         * @return false if the end of the content has been reached
         */
        boolean scrollForward();

        void scrollToStart(int maxSwipes);
    }

    /**
     * @param fromStart whether to scroll to the start of the content if the target is not visible yet
     * @param maxSwipes the maximum number of forward swipes
     * @return true if the target has become visible
     */
    public static boolean run(Steps steps, boolean fromStart, int maxSwipes) {
        if (steps.isTargetVisible()) {
            return true;
        }
        if (fromStart) {
            steps.scrollToStart(maxSwipes);
            if (steps.isTargetVisible()) {
                return true;
            }
        }
        for (int i = 0; i < maxSwipes; i++) {
            final boolean canScrollMore = steps.scrollForward();
            if (steps.isTargetVisible()) {
                return true;
            }
            if (!canScrollMore) {
                return false;
            }
        }
        return false;
    }
}
