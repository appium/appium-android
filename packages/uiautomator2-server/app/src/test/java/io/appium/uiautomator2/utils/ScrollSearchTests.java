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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScrollSearchTests {
    /** A list of items where the viewport shows one item at a time, starting at {@code position}. */
    private static class FakeList implements ScrollSearch.Steps {
        private final int size;
        private final int target;
        private int position;
        private int forwardSwipes = 0;
        private int startResets = 0;

        FakeList(int size, int position, int target) {
            this.size = size;
            this.position = position;
            this.target = target;
        }

        @Override
        public boolean isTargetVisible() {
            return position == target;
        }

        @Override
        public boolean scrollForward() {
            forwardSwipes++;
            position = Math.min(position + 1, size - 1);
            return position < size - 1;
        }

        @Override
        public void scrollToStart(int maxSwipes) {
            startResets++;
            position = 0;
        }
    }

    @Test
    public void returnsImmediatelyIfTargetIsVisible() {
        FakeList list = new FakeList(10, 3, 3);
        assertTrue(ScrollSearch.run(list, true, 5));
        assertEquals(0, list.forwardSwipes);
        assertEquals(0, list.startResets);
    }

    @Test
    public void scrollsForwardUntilTargetIsVisible() {
        FakeList list = new FakeList(10, 0, 4);
        assertTrue(ScrollSearch.run(list, false, 10));
        assertEquals(4, list.forwardSwipes);
    }

    @Test
    public void doesNotFindTargetAboveViewportWithoutFromStart() {
        FakeList list = new FakeList(10, 8, 2);
        assertFalse(ScrollSearch.run(list, false, 10));
        assertEquals(0, list.startResets);
    }

    @Test
    public void findsTargetAboveViewportWithFromStart() {
        FakeList list = new FakeList(10, 8, 2);
        assertTrue(ScrollSearch.run(list, true, 10));
        assertEquals(1, list.startResets);
        assertEquals(2, list.forwardSwipes);
    }

    @Test
    public void stopsAtTheEndOfTheContent() {
        FakeList list = new FakeList(5, 0, 99);
        assertFalse(ScrollSearch.run(list, false, 50));
        assertEquals(4, list.forwardSwipes);
    }

    @Test
    public void stopsAtMaxSwipes() {
        FakeList list = new FakeList(100, 0, 50);
        assertFalse(ScrollSearch.run(list, false, 3));
        assertEquals(3, list.forwardSwipes);
    }

    @Test
    public void checksTheTargetAfterTheLastSwipeReachedTheEnd() {
        // The last swipe reveals the target and reports the end of the content at the same time
        FakeList list = new FakeList(5, 0, 4);
        assertTrue(ScrollSearch.run(list, false, 50));
    }
}
