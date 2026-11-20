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

import androidx.test.uiautomator.Configurator;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.appium.uiautomator2.StaticMocks;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ScrollAcknowledgmentTimeoutTests {
    @Rule
    public final StaticMocks staticMocks = new StaticMocks();

    private ScrollAcknowledgmentTimeout scrollAcknowledgmentTimeout;

    @Mock
    private Configurator configurator;

    @Before
    public void setup() {
        scrollAcknowledgmentTimeout = new ScrollAcknowledgmentTimeout();
        staticMocks.mock(Configurator.class).when(Configurator::getInstance).thenReturn(configurator);
        when(configurator.setScrollAcknowledgmentTimeout(anyLong())).thenReturn(configurator);
    }

    @Test
    public void shouldBeLong() {
        Assert.assertEquals(Long.class, scrollAcknowledgmentTimeout.getValueType());
    }

    @Test
    public void shouldReturnValidSettingName() {
        Assert.assertEquals("scrollAcknowledgmentTimeout", scrollAcknowledgmentTimeout.getName());
    }

    @Test
    public void shouldBeAbleToSetScrollAcknowledgmentTimeout() {
        scrollAcknowledgmentTimeout.update(123L);
        verify(configurator).setScrollAcknowledgmentTimeout(123L);
    }

    @Test
    public void shouldBeAbleToGetScrollAcknowledgmentTimeout() {
        when(configurator.getScrollAcknowledgmentTimeout()).thenReturn(123L);
        Assert.assertEquals(Long.valueOf(123), scrollAcknowledgmentTimeout.getValue());
    }
}
