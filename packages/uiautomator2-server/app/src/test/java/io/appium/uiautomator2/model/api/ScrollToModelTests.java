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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class ScrollToModelTests {
    private Locale defaultLocale;

    @Before
    public void setUp() {
        defaultLocale = Locale.getDefault();
    }

    @After
    public void tearDown() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    public void directionIsParsedInTurkishLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        ScrollToModel.ScrollParams params = new ScrollToModel.ScrollParams();
        params.direction = "right";
        assertEquals(Direction.RIGHT, params.getDirection());
    }
}
