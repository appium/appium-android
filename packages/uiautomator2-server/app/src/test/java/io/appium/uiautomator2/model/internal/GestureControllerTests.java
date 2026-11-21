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

package io.appium.uiautomator2.model.internal;

import android.graphics.Point;
import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(RobolectricTestRunner.class)
public class GestureControllerTests {
    private static final Point DISPLAY = new Point(1080, 2424);

    @Test
    public void fullDisplayAreaIsClampedToLastPixel() {
        assertEquals(new Rect(0, 0, 1079, 2423),
                GestureController.clampToDisplay(new Rect(0, 0, 1080, 2424), DISPLAY));
    }

    @Test
    public void areaOutsideOfDisplayIsClamped() {
        assertEquals(new Rect(0, 100, 1079, 2423),
                GestureController.clampToDisplay(new Rect(-50, 100, 5000, 5000), DISPLAY));
    }

    @Test
    public void areaInsideOfDisplayIsNotChanged() {
        Rect area = new Rect(108, 242, 972, 2182);
        assertEquals(area, GestureController.clampToDisplay(area, DISPLAY));
    }

    @Test
    public void areaBeyondTheRightEdgeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GestureController.clampToDisplay(new Rect(1200, 100, 1300, 500), DISPLAY));
    }

    @Test
    public void areaBelowTheBottomEdgeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GestureController.clampToDisplay(new Rect(100, 2500, 500, 2600), DISPLAY));
    }

    @Test
    public void areaBeforeTheOriginIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GestureController.clampToDisplay(new Rect(-300, -300, -100, -100), DISPLAY));
    }

    @Test
    public void areaEndingAtTheLeftEdgeIsRejected() {
        // The right edge is exclusive, so this area lies entirely to the left of the display
        assertThrows(IllegalArgumentException.class,
                () -> GestureController.clampToDisplay(new Rect(-100, 10, 0, 50), DISPLAY));
    }

    @Test
    public void areaEndingAtTheTopEdgeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GestureController.clampToDisplay(new Rect(10, -100, 50, 0), DISPLAY));
    }

    @Test
    public void areaStartingAtTheRightEdgeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GestureController.clampToDisplay(new Rect(1080, 10, 1200, 50), DISPLAY));
    }
}
