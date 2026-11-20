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
package io.appium.uiautomator2.unittest.test;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.google.gson.JsonObject;

import org.junit.Test;

import io.appium.uiautomator2.model.By;
import io.appium.uiautomator2.unittest.test.internal.BaseTest;
import io.appium.uiautomator2.unittest.test.internal.Response;
import io.appium.uiautomator2.unittest.test.internal.SkipHeadlessDevices;

import static io.appium.uiautomator2.unittest.test.internal.TestUtils.waitForElementInvisibility;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.findElement;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.click;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.getAttribute;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.getLocation;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.getName;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.getSize;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.getText;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.screenshot;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.sendKeys;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("JavaDoc")
public class ElementCommandsTest extends BaseTest {

    /**
     * Test for click on element
     */
    @Test
    public void clickElementTest() {
        By by = By.accessibilityId("Accessibility");
        Response response = findElement(by);
        assertTrue(by + " should be found", response.isSuccessful());
        click(response.getElementId());
        response = waitForElementInvisibility(response.getElementId());
        assertFalse(by + " should not be found", response.isSuccessful());
    }

    /**
     * Test for get Attributes
     */
    @Test
    public void getAttributeTest() {
        Response response = findElement(By.accessibilityId("App"));
        String elementId = response.getElementId();

        response = getAttribute(elementId, "resourceId");
        assertEquals("android:id/text1", response.getValue(String.class));

        response = getAttribute(elementId, "contentDescription");
        assertEquals("App", response.getValue(String.class));

        response = getAttribute(elementId, "text");
        assertEquals("App", response.getValue(String.class));

        response = getAttribute(elementId, "className");
        assertEquals("android.widget.TextView", response.getValue(String.class));
    }

    /**
     * Test for getElement Text
     */
    @Test
    public void getTextTest() {
        Response response = findElement(By.accessibilityId("App"));
        response = getText(response.getElementId());
        assertEquals("App", response.getValue(String.class));
    }

    /**
     * Test for send keys to element
     */
    @Test
    public void sendKeysTest() {
        startActivity(".view.Controls1");
        Response response = findElement(By.id("io.appium.android.apis:id/edit"));
        sendKeys(response.getElementId(), "Dummy Theme");
        response = getText(response.getElementId());
        assertEquals("Dummy Theme", response.getValue(String.class));
    }

    /**
     * Test for element name
     *
     */
    @Test
    public void getNameTest() {
        Response response = findElement(By.id("android:id/text1"));
        response = getName(response.getElementId());
        assertEquals("Access'ibility", response.getValue(String.class));
    }

    /**
     * Test for element size
     *
     */
    @Test
    public void getElementSizeTest() {
        Response response = findElement(By.id("android:id/text1"));
        JsonObject value = getSize(response.getElementId()).getValue(JsonObject.class);
        int height = value.get("height").getAsInt();
        int width = value.get("width").getAsInt();
        assertTrue("Element height is zero(0), which is not expected", height > 0);
        assertTrue("Element width is zero(0), which is not expected", width > 0);
    }

    /**
     * getLocation will get the location of the element on the screen
     *
     */
    @Test
    public void getLocationTest() {
        startActivity(".view.ChronometerDemo");
        Response response = findElement(By.id("io.appium.android.apis:id/start"));
        response = getLocation(response.getElementId());
        JsonObject json = response.getValue(JsonObject.class);
        int x = json.get("x").getAsInt();
        int y = json.get("y").getAsInt();
        assertTrue("element location x coordinate is zero(0), which is not expected", x > 0);
        assertTrue("element location y coordinate is zero(0), which is not expected", y > 0);
    }

    @Test
    @SkipHeadlessDevices
    public void elementScreenshotTest() {
        Response response = findElement(By.accessibilityId("Accessibility"));
        clickAndWaitForStaleness(response.getElementId());

        String elementId = findElement(By.id("android:id/text1")).getElementId();
        response = getSize(elementId);
        JsonObject json = response.getValue(JsonObject.class);
        int height = json.get("height").getAsInt();
        int width = json.get("width").getAsInt();

        response = screenshot(elementId);
        String value = response.getValue(String.class);
        byte[] bytes = Base64.decode(value, Base64.NO_WRAP);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        assertNotNull(bitmap);
        assertEquals(bitmap.getWidth(), width);
        assertEquals(bitmap.getHeight(), height);
    }
}
