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

package io.appium.uiautomator2.model.api;

import androidx.test.uiautomator.Direction;

import java.util.Arrays;
import java.util.Locale;

import io.appium.uiautomator2.model.RequiredField;

public class ScrollToModel extends BaseModel {
    public ElementModel origin;
    @RequiredField
    public ScrollParams params;

    public ScrollToModel() {}

    public static class ScrollParams extends BaseModel {
        @RequiredField
        public String strategy;
        @RequiredField
        public String selector;
        public Integer maxSwipes;
        public String direction;
        public Boolean fromStart;

        public Direction getDirection() {
            if (direction == null) {
                return null;
            }
            try {
                return Direction.valueOf(direction.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        String.format("Scroll direction must be one of %s", Arrays.toString(Direction.values())));
            }
        }

        public ScrollParams() {}
    }
}
