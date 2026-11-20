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

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import io.appium.uiautomator2.StaticMocks;
import io.appium.uiautomator2.core.UiAutomation;
import io.appium.uiautomator2.core.UiAutomatorBridge;
import io.appium.uiautomator2.model.NotificationListener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class EnableNotificationListenerTests {
    @Rule
    public final StaticMocks staticMocks = new StaticMocks();

    private NotificationListener notificationListener;
    private EnableNotificationListener enableNotificationListener;

    @Before
    public void setup() {
        staticMocks.mock(InstrumentationRegistry.class).when(InstrumentationRegistry::getInstrumentation).thenReturn(null);

        staticMocks.mock(UiDevice.class)
                .when(() -> UiDevice.getInstance(null)).thenReturn(mock(UiDevice.class));
        staticMocks.mock(UiAutomatorBridge.class)
                .when(UiAutomatorBridge::getInstance).thenReturn(mock(UiAutomatorBridge.class));
        staticMocks.mock(UiAutomation.class)
                .when(UiAutomation::getInstance).thenReturn(mock(UiAutomation.class));

        notificationListener = mock(NotificationListener.class);
        staticMocks.mock(NotificationListener.class)
                .when(NotificationListener::getInstance).thenReturn(notificationListener);

        enableNotificationListener = new EnableNotificationListener();

        doNothing().when(notificationListener).stop();
        doNothing().when(notificationListener).start();
    }

    @Test
    public void shouldBeBoolean() {
        assertEquals(Boolean.class, enableNotificationListener.getValueType());
    }

    @Test
    public void shouldReturnValidSettingName() {
        assertEquals("enableNotificationListener", enableNotificationListener.getName());
    }

    @Test
    public void shouldBeAbleToStartNotificationListeners() {
        enableNotificationListener.update(true);
        verify(notificationListener).start();
    }

    @Test
    public void shouldBeAbleToStopNotificationListeners() {
        enableNotificationListener.update(false);
        verify(notificationListener).stop();
    }

    @Test
    public void shouldBeAbleToGetNotificationListenersStatus() {
        when(notificationListener.isListening()).thenReturn(true);

        assertTrue(enableNotificationListener.getValue());
        verify(notificationListener).isListening();
    }
}
