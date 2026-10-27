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

package io.appium.uiautomator2.handler;

import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import io.appium.uiautomator2.model.AndroidElement;
import io.appium.uiautomator2.model.api.SendKeysModel;
import io.appium.uiautomator2.utils.Device;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Device.class})
public class SendKeysToElementTests {

    @Mock
    private AndroidElement element;

    @Mock
    private UiDevice uiDevice;

    private static SendKeysModel createModel(String text, Boolean replace) {
        SendKeysModel model = new SendKeysModel();
        model.text = text;
        model.replace = replace;
        return model;
    }

    @Before
    public void setUp() {
        PowerMockito.mockStatic(Device.class);
        when(Device.getUiDevice()).thenReturn(uiDevice);
        when(element.setText(anyString())).thenReturn(true);
    }

    @Test
    public void shouldStripTrailingEscapedNewlineAndPressEnter() throws UiObjectNotFoundException {
        SendKeysToElement.setText(element, createModel("aa\\n", true));

        verify(element).setText("aa");
        verify(uiDevice).pressEnter();
    }

    @Test
    public void shouldOnlyStripTrailingEscapedNewlineWhenMultiplePresent() throws UiObjectNotFoundException {
        SendKeysToElement.setText(element, createModel("aa\\nbb\\n", true));

        verify(element).setText("aa\\nbb");
        verify(uiDevice).pressEnter();
    }

    @Test
    public void shouldStripTrailingEscapedNewlineWhenAppendingWithReplaceFalse() throws UiObjectNotFoundException {
        when(element.getText()).thenReturn("existing_", "");

        SendKeysToElement.setText(element, createModel("aa\\n", false));

        verify(element).clear();
        verify(element).setText("existing_aa");
        verify(uiDevice).pressEnter();
    }

    @Test
    public void shouldStripTrailingEscapedNewlineWhenAppendingWithReplaceOmitted() throws UiObjectNotFoundException {
        when(element.getText()).thenReturn("existing_", "");

        SendKeysToElement.setText(element, createModel("aa\\n", null));

        verify(element).clear();
        verify(element).setText("existing_aa");
        verify(uiDevice).pressEnter();
    }

    @Test
    public void shouldNotPressEnterOrStripActualLfCharacter() throws UiObjectNotFoundException {
        SendKeysToElement.setText(element, createModel("aa\n", true));

        verify(element).setText("aa\n");
        verify(uiDevice, never()).pressEnter();
    }

    @Test
    public void shouldNotPressEnterWhenNoTrailingNewline() throws UiObjectNotFoundException {
        SendKeysToElement.setText(element, createModel("aa\\nbb", true));

        verify(element).setText("aa\\nbb");
        verify(uiDevice, never()).pressEnter();
    }
}
