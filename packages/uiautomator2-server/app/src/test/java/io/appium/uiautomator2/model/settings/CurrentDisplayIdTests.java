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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;

import io.appium.uiautomator2.StaticMocks;
import io.appium.uiautomator2.core.UiAutomation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CurrentDisplayIdTests {
    @Rule
    public final StaticMocks staticMocks = new StaticMocks();

    private final CurrentDisplayId setting = new CurrentDisplayId();

    @Before
    public void setUp() {
        UiAutomation uiAutomation = mock(UiAutomation.class);
        when(uiAutomation.getDisplayIds()).thenReturn(Arrays.asList(0, 1));
        staticMocks.mock(UiAutomation.class).when(UiAutomation::getInstance)
                .thenReturn(uiAutomation);
    }

    @Test
    public void isNotCustomizedByDefault() {
        assertFalse(setting.isCustomized());
    }

    @Test
    public void isCustomizedAfterSelectingADisplay() {
        setting.update(1);
        assertTrue(setting.isCustomized());
        assertEquals(Integer.valueOf(1), setting.getValue());
    }

    @Test
    public void isNotCustomizedAfterTheResetOfASelectedDisplay() {
        setting.update(1);
        assertTrue(setting.reset());
        assertFalse(setting.isCustomized());
        assertEquals(Integer.valueOf(0), setting.getValue());
    }

    @Test
    public void isNotCustomizedAfterSettingTheInvalidDisplayValue() {
        setting.update(1);
        setting.update(-1);
        assertFalse(setting.isCustomized());
        assertEquals(Integer.valueOf(0), setting.getValue());
    }
}
