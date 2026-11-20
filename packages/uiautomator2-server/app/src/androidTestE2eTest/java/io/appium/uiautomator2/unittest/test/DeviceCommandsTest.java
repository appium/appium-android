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
import android.os.Build;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.appium.uiautomator2.model.By;
import io.appium.uiautomator2.unittest.test.internal.BaseTest;
import io.appium.uiautomator2.unittest.test.internal.Response;
import io.appium.uiautomator2.unittest.test.internal.SkipHeadlessDevices;
import io.appium.uiautomator2.unittest.test.internal.TestUtils;
import io.appium.uiautomator2.utils.Device;

import static android.os.SystemClock.elapsedRealtime;
import static io.appium.uiautomator2.unittest.test.Config.DEFAULT_POLLING_INTERVAL;
import static io.appium.uiautomator2.unittest.test.Config.EXPLICIT_TIMEOUT;
import static io.appium.uiautomator2.unittest.test.internal.TestUtils.getJsonObjectCountInJsonArray;
import static io.appium.uiautomator2.unittest.test.internal.TestUtils.waitForElement;
import static io.appium.uiautomator2.unittest.test.internal.TestUtils.waitForElementInvisibility;
import static io.appium.uiautomator2.unittest.test.internal.TestUtils.waitForMillis;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.findElement;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.findElements;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.getDeclaredOrientation;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.getDeviceSize;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.getInfo;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.getPackages;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.getScreenOrientation;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.getSettings;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.rotateScreen;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.screenshot;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.scrollToClassName;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.scrollBackTo;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.scrollTo;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.scrollToElement;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.scrollToText;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.setRotation;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.updateSetting;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.updateSettings;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.click;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.getAttribute;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.getText;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.sendKeys;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("JavaDoc")
public class DeviceCommandsTest extends BaseTest {
    private static JsonObject rotation(int z) {
        final JsonObject rotateMap = new JsonObject();
        rotateMap.addProperty("x", 0);
        rotateMap.addProperty("y", 0);
        rotateMap.addProperty("z", z);
        return rotateMap;
    }

    @After
    public void resetRotation() {
        assertTrue(setRotation(rotation(0)).isSuccessful());
    }

    /**
     * Test for findElement
     *
     * While working on PR #340 to: `appium:appium-uiautomator2-server` we were
     * seeing this test fail consistently on API-26. Ultimately we confirmed
     * that it was a test-ordering issue. By prefixing the test-case w/ `"zzz"`
     * we forced it to be run first which produced consistent result(s)
     *
     * TODO: Figure out test state ordering issue
     */
    @Test
    public void zzzFindElementTest() {
        By by = By.xpath("//*[@text='API Demos']");
        Response response = findElement(by);
        assertTrue(by + " should be found", response.isSuccessful());

        by = By.xpath("//hierarchy//*[@text='API Demos']");
        response = findElement(by);
        assertTrue(by + " should be found", response.isSuccessful());

        by = By.xpath("//hierarchy");
        response = findElement(by);
        assertFalse(by + " should not be found", response.isSuccessful());

        by = By.xpath("//*[@resource-id='android:id/action_bar']");
        response = findElement(by);
        assertTrue(response.isSuccessful());

        by = By.xpath("//*[@*='android:id/action_bar']");
        response = findElement(by);
        assertTrue(response.isSuccessful());

        by = By.xpath("(//*[@class='android.widget.TextView'])[3]");
        response = findElement(by);
        response = getAttribute(response.getElementId(), "text");
        assertEquals("Accessibility", response.getValue(String.class));

        by = By.xpath("//*[@resource-id='android:id/content']" +
                "//*[@resource-id='android:id/text1'][5]");
        response = findElement(by);
        response = getAttribute(response.getElementId(), "text");
        assertEquals("Content", response.getValue(String.class));

        // Xpath 2.0
        by = By.xpath("//*[@resource-id[matches(., 'action_bar$')]]");
        response = findElement(by);
        assertTrue(response.isSuccessful());
    }

    /**
     * test to find element using "-android automator" property
     */
    @Test
    public void findElementUsingUiAutomatorTest() {
        scrollToText("Views"); // Due to 'Views' option not visible on small screen
        Response response = findElement(By.accessibilityId("Views"));
        clickAndWaitForStaleness(response.getElementId());

        By androidUiAutomator = By.androidUiAutomator("new UiSelector().text(\"Radio Group\");" +
                "new UiScrollable(new UiSelector().resourceId(\"android:id/list\"))" +
                ".scrollIntoView(new UiSelector().text(\"Radio Group\"));");
        response = findElement(androidUiAutomator);
        assertTrue(androidUiAutomator + " should be found", response.isSuccessful());
        click(response.getElementId());

        response = waitForElementInvisibility(response.getElementId());
        assertFalse(androidUiAutomator + " should not be found", response.isSuccessful());
    }

    /**
     * test to find elements using "-android automator" property
     */
    @Test
    public void findElementsUsingUiAutomatorTest() {
        By by = By.androidUiAutomator("resourceId(\"android:id/text1\")");
        Response response = findElements(by);
        assertTrue(by + " should be found", response.isSuccessful());

        JsonArray elements = response.getValue(JsonArray.class);
        int elementCount = getJsonObjectCountInJsonArray(elements);
        // Local exceeded 5, but CI was 5. This adding '=' is Android 16 emulator on CI related,
        // but it's ok for this test's purpose to get multiple elements.
        assertTrue("Elements Count in views screen should at least >= 5, " +
                "in all variants of screen sizes, but actual: " + elementCount, elementCount >= 5);
    }

    /**
     * Test for findElements
     */
    @Test
    public void findElementsTest() {
        By by = By.className("android.widget.TextView");
        Response response = findElements(by);
        assertTrue(by + " should be found", response.isSuccessful());
        JsonArray elements = response.getValue(JsonArray.class);
        int elementCount = getJsonObjectCountInJsonArray(elements);
        assertTrue("Elements Count in Home launch screen should at least > 5, " +
                "in all variants of screen sizes, but actual: " + elementCount, elementCount > 5);
    }

    /**
     * Test for Device size
     *
     */
    @Test
    public void getDeviceSizeTest() {
        JsonObject value = getDeviceSize().getValue(JsonObject.class);
        int height = value.get("height").getAsInt();
        int width = value.get("width").getAsInt();
        assertTrue("device window height is " + height + ", which is not expected", height > 200);
        assertTrue("device window width is " + width + ", which is not expected", width > 100);
    }

    /**
     * verifies manifest-declared screen orientation is exposed via dedicated endpoint
     */
    @Test
    public void getDeclaredOrientationTest() {
        final long start = elapsedRealtime();
        String declaredOrientation = null;
        do {
            Response response = getDeclaredOrientation();
            assertTrue(response.isSuccessful());
            declaredOrientation = response.getValue(String.class);
            if (declaredOrientation != null && declaredOrientation.matches("SCREEN_ORIENTATION_.+")) {
                return;
            }
            waitForMillis(DEFAULT_POLLING_INTERVAL);
        } while (elapsedRealtime() - start < EXPLICIT_TIMEOUT);
        assertNotNull("Declared orientation should be available after session start", declaredOrientation);
        assertTrue("Declared orientation should be a SCREEN_ORIENTATION_* constant",
                declaredOrientation.matches("SCREEN_ORIENTATION_.+"));
    }

    /**
     * performs screen rotation
     *
     */
    @Test
    public void screenRotationLandscapeTest() {
        Response response = rotateScreen("LANDSCAPE");
        assertTrue(response.isSuccessful());
        Device.waitForIdle();
        assertEquals("LANDSCAPE", getScreenOrientation());
    }

    /**
     * performs screen rotation
     *
     */
    @Test
    public void screenRotationPortraitTest() {
        Response response = rotateScreen("PORTRAIT");
        assertTrue(response.isSuccessful());
        Device.waitForIdle();
        assertEquals("PORTRAIT", getScreenOrientation());
    }

    /**
     * performs screen rotation
     *
     */
    @Test
    public void screenRotationLandscapeRightTest() {
        /*
          LANDSCAPE RIGHT
         */
        JsonObject rotateMap = rotation(90);
        Response response = setRotation(rotateMap);
        Device.waitForIdle();
        assertTrue(response.isSuccessful());
    }

    /**
     * performs screen rotation
     *
     */
    @Test
    public void screenRotationPortraitUpsideDownTest() {
        /*
          PORTRAIT UPSIDE DOWN
         */
        JsonObject rotateMap = rotation(180);
        Response response = setRotation(rotateMap);
        Device.waitForIdle();
        assertTrue(response.isSuccessful());
    }

    /**
     * performs screen rotation
     *
     */
    @Test
    public void screenRotationPortraitMapTest() {
        /*
          PORTRAIT
         */
        JsonObject rotateMap = rotation(0);
        Response response = setRotation(rotateMap);
        Device.waitForIdle();
        assertTrue(response.isSuccessful());
    }

    /**
     * performs screen rotation
     *
     */
    @Test
    public void screenRotationInvalidTest() {
        /*
          INVALID MAP
         */
        JsonObject rotateMap = rotation(10);
        Response response = setRotation(rotateMap);
        assertFalse(response.isSuccessful());
    }

    @Test
    public void findElementWithContextId1() {
        //parent element - By.androidUiAutomator (UiObject)
        Response response = findElement(By.androidUiAutomator("new UiSelector().resourceId" +
                "(\"android:id/list\")"));
        String contextId = response.getElementId();

        //child  element - By.className (UiObject2)
        response = findElement(By.className("android.widget.TextView"), contextId);
        response = getText(response.getElementId());
        assertEquals("Access'ibility", response.getValue(String.class));
    }

    @Test
    public void findElementWithContextId2() {
        //parent element - By.className  (UiObject2)
        Response response = findElement(By.className("android.widget.ListView"));
        String contextId = response.getElementId();

        //child  element - By.className (UiObject2)
        response = findElement(By.className("android.widget.TextView"), contextId);
        response = getText(response.getElementId());
        assertEquals("Access'ibility", response.getValue(String.class));
    }

    @Test
    public void findElementWithContextId3() {
        //parent element - By.className  (UiObject2)
        Response response = findElement(By.className("android.widget.ListView"));
        String contextId = response.getElementId();

        //child element - By.xpath  (UiObject2)
        response = findElement(By.xpath("//*[@class='android.widget.TextView'][2]"), contextId);
        response = getText(response.getElementId());
        assertEquals("Accessibility", response.getValue(String.class));
    }

    @Test
    public void findElementWithContextId4() {
        //parent element - By.className  (UiObject2)
        Response response = findElement(By.className("android.widget.ListView"));
        String contextId = response.getElementId();

        //child element - By.xpath  (UiObject2)
        response = findElement(By.xpath("(//*[@class='android.widget.TextView'])[2]"), contextId);
        response = getText(response.getElementId());
        assertEquals("Accessibility", response.getValue(String.class));
    }

    @Test
    public void findElementWithContextId5() {
        //parent element - By.className  (UiObject2)
        Response response = waitForElement(By.className("android.widget.ListView"));
        String contextId = response.getElementId();

        //child  element - By.androidUiAutomator (UiObject)
        response = waitForElement(By.androidUiAutomator("new UiSelector().text(\"Animation\");"),
                contextId);
        response = getText(response.getElementId());
        assertEquals("Animation", response.getValue(String.class));
    }

    @Test
    public void findElementWithContextId6() {
        //parent element - By.xpath
        Response response = findElement(By.xpath("(//hierarchy//*[@class='android.widget.FrameLayout'])[3]"));
        String contextId = response.getElementId();

        //child element - By.xpath  (UiObject2)
        response = findElement(By.xpath("(//*[@class='android.widget.TextView'])[2]"), contextId);
        response = getText(response.getElementId());
        assertEquals("Accessibility", response.getValue(String.class));
    }

    @Test
    public void findElementWithContextId7() {
        //parent element - By.androidUiAutomator (UiObject)
        Response response = findElement(By.androidUiAutomator("new UiSelector()"
                + ".resourceId(\"android:id/list\");"));
        String contextId = response.getElementId();

        //child element - By.xpath  (UiObject2)
        response = findElement(By.xpath("//*[@class='android.widget.TextView'][2]"), contextId);
        response = getText(response.getElementId());
        assertEquals("Accessibility", response.getValue(String.class));
    }

    @Test
    public void findElementWithContextId8() {
        Response response = findElement(By.accessibilityId("Animation"));
        clickAndWaitForStaleness(response.getElementId());
        response = findElement(By.accessibilityId("Events"));
        clickAndWaitForStaleness(response.getElementId());
    }

    @Test
    public void findElementWithContextId9() {
        //parent element - By.xpath (UiObject2)
        Response response = waitForElement(By.xpath("//*[@class='android.widget.ListView']"));
        String contextId = response.getElementId();

        //child  element - By.androidUiAutomator (UiObject)
        response = waitForElement(By.androidUiAutomator("new UiSelector().className(\"android.widget" +
                ".TextView\")"), contextId);
        response = getText(response.getElementId());
        assertEquals("Access'ibility", response.getValue(String.class));
    }

    @Test
    public void findElementWithContextId10() {
        //parent element - By.androidUiAutomator (UiObject)
        Response response = findElement(By.androidUiAutomator("new UiSelector().resourceId" +
                "(\"android:id/list\")"));
        String contextId = response.getElementId();

        //child element - relative By.xpath
        response = findElement(By.xpath("./*"), contextId);
        assertTrue(response.isSuccessful());
    }

    @Test
    public void findElementWithAttributes() {
        scrollToText("Views");
        Response response = findElement(By.accessibilityId("Views"));
        clickAndWaitForStaleness(response.getElementId());

        By by = By.accessibilityId("Buttons");
        response = findElement(by);
        String elementId = response.getElementId();
        assertTrue(response.isSuccessful());

        response = getAttribute(elementId, "clickable");
        assertEquals("true", response.getValue(String.class));

        response = getAttribute(elementId, "enabled");
        assertEquals("true", response.getValue(String.class));
    }

    @Test
    public void findElementWithClassName() {
        Response response = findElement(By.className("android.widget.TextView"));
        response = getText(response.getElementId());
        assertEquals("API Demos", response.getValue(String.class));
    }

    @Test
    public void findElementsWithAttribute() {
        Response response = findElement(By.accessibilityId("Accessibility"));
        clickAndWaitForStaleness(response.getElementId());

        response = findElements(By.xpath("//*[@enabled='true' and @class='android.widget" +
                ".TextView']"));

        JsonArray elements = response.getValue(JsonArray.class);
        int elementCount = getJsonObjectCountInJsonArray(elements);
        assertTrue("Elements Count in views screen should at least > 4, " +
                "in all variants of screen sizes, but actual: " + elementCount, elementCount > 4);
        if (Build.VERSION.SDK_INT >= 30) {
            return;
        }
        List<String> expectedTexts = Arrays.asList("API Demos", "Accessibility Node Provider",
                "Accessibility Node Querying", "Accessibility Service");
        for (int i = 0; i < 4; i++) {
            String elementId = TestUtils.extractElementId(elements.get(i).getAsJsonObject());
            assertEquals(expectedTexts.get(i), getText(elementId).getValue(String.class));
        }
    }

    @Test
    public void toastVerificationTest1() {
        startActivity(".view.PopupMenu1");

        Response response = waitForElement(By.accessibilityId("Make a Popup!"));
        click(response.getElementId());
        response = waitForElement(By.xpath(".//*[@text='Search']"));
        click(response.getElementId());

        By by = By.xpath("//*[@text='Clicked popup menu item Search']");
        response = waitForElement(by);
        response = getText(response.getElementId());
        assertEquals("Clicked popup menu item Search", response.getValue(String.class));
        assertTrue(response.isSuccessful());
    }

    @Test
    public void toastVerificationTest2() {
        startActivity(".view.PopupMenu1");

        Response response = waitForElement(By.accessibilityId("Make a Popup!"));
        click(response.getElementId());
        response = waitForElement(By.xpath(".//*[@text='Add']"));
        click(response.getElementId());

        By by = By.xpath("//*[contains(@text,'Clicked popup menu item Add')]");
        response = waitForElement(by);
        assertTrue(response.isSuccessful());
        response = getText(response.getElementId());
        assertEquals("Clicked popup menu item Add", response.getValue(String.class));
    }

    @Test
    public void toastVerificationTest3() {
        startActivity(".view.PopupMenu1");

        Response response = waitForElement(By.accessibilityId("Make a Popup!"));
        click(response.getElementId());
        response = waitForElement(By.xpath(".//*[@text='Edit']"));
        click(response.getElementId());

        By by = By.xpath("//*[@text='Clicked popup menu item Edit']");
        response = waitForElement(by);
        assertTrue(response.isSuccessful());
        response = getText(response.getElementId());
        assertEquals("Clicked popup menu item Edit", response.getValue(String.class));
    }

    @Test
    public void toastVerificationTest4() {
        startActivity(".view.PopupMenu1");

        Response response = waitForElement(By.accessibilityId("Make a Popup!"));
        click(response.getElementId());
        response = waitForElement(By.xpath(".//*[@text='Edit']"));
        click(response.getElementId());

        By by = By.xpath("//*[@text='Clicked popup menu item Edit']");
        response = waitForElement(by);
        assertTrue(response.isSuccessful());

        response = waitForElement(By.xpath(".//*[@text='Share']"));
        click(response.getElementId());

        by = By.xpath("//*[@text='Clicked popup menu item Share']");
        response = waitForElement(by);
        assertTrue(response.isSuccessful());
        response = getText(response.getElementId());
        assertEquals("Clicked popup menu item Share", response.getValue(String.class));
    }

    /**
     * Performs scrolling through a horizontal list to an element specified by its text.
     *
     */
    @Test
    public void scrollHorizontalListTest() {
        startActivity(".view.Tabs5"); // Page with horizontally-scrollable tabs
        waitForElement(By.id("android:id/tabs"));

        // On devices with Android API Level 23 and earlier, the tab labels are displayed
        // in all-caps, whereas their actual text is rather like 'Tab 13', 'Tab 26', etc.
        // The arguments of methods findElement() and scrollToText() are case-sensitive,
        // therefore we need to use different sets of inputs for different target devices.
        int apiLevel = Build.VERSION.SDK_INT;

        String[] items = apiLevel < 24
            ? new String[] { "Tab 13", "Tab 26", "Tab 5" }  // up to Android 6.0 Marshmallow
            : new String[] { "TAB 13", "TAB 26", "TAB 5" }; // Android 7.0 Nougat and later

        for (String item : items) {
            assertFalse(String.format(
                "Item '%s' should not be found in the horizontal list.", item),
                findElement(By.text(item)).isSuccessful());

            assertTrue(String.format(
                "Scroll-to-text should have succeeded for item '%s'.", item),
                scrollToText(item).isSuccessful());

            assertTrue(String.format(
                "Item '%s' should now be found in the horizontal list.", item),
                findElement(By.text(item)).isSuccessful());
        }
    }

    @Test
    public void scrollToElementWithoutUiScrollable() {
        startActivity(".view.List1");
        waitForElement(By.id("android:id/list"));
        String target = "//*[@text='Armenian String']";

        assertFalse(findElement(By.xpath(target)).isSuccessful());
        // The first scrollable element is scrolled
        assertTrue(scrollTo("xpath", target, null, null, null, null).isSuccessful());
        assertTrue(findElement(By.xpath(target)).isSuccessful());
        // The target is visible already
        assertTrue(scrollTo("xpath", target, null, null, null, null).isSuccessful());
        assertTrue(scrollTo("id", "android:id/list", null, null, null, null).isSuccessful());
    }

    @Test
    public void scrollToElementInContainerFoundByIdWithoutUiScrollable() {
        startActivity(".view.List1");
        String listId = waitForElement(By.id("android:id/list")).getElementId();
        String target = "//*[@text='Armenian String']";

        assertTrue(scrollTo("xpath", target, listId, "down", null, null).isSuccessful());
        assertTrue(findElement(By.xpath(target)).isSuccessful());
    }

    @Test
    public void scrollToElementWithoutUiScrollableFailsIfElementDoesNotExist() {
        startActivity(".view.List1");
        waitForElement(By.id("android:id/list"));
        assertFalse(scrollTo("xpath", "//*[@text='No such cheese']", null, null, null, 3).isSuccessful());
        assertFalse(scrollTo("xpath", "//*[@text='Armenian String']", null, null, null, 0).isSuccessful());
        assertFalse(scrollTo("xpath", "//*[@text='Armenian String']", null, "sideways", null, null).isSuccessful());
    }

    @Test
    public void scrollToElementWithoutUiScrollableCanStartFromTheBeginning() {
        startActivity(".view.List1");
        waitForElement(By.id("android:id/list"));
        String top = "//*[@text='Abbaye de Belloc']";
        assertTrue(scrollTo("xpath", "//*[@text='Armenian String']", null, null, null, null).isSuccessful());
        assertFalse(findElement(By.xpath(top)).isSuccessful());

        assertFalse(scrollTo("xpath", top, null, null, null, 3).isSuccessful());
        assertTrue(scrollTo("xpath", top, null, null, true, null).isSuccessful());
        assertTrue(findElement(By.xpath(top)).isSuccessful());
    }

    @Test
    public void scrollToElementWithoutUiScrollableUsesHorizontalDirectionForHorizontalScrollViews() {
        startActivity(".view.Tabs5");
        waitForElement(By.xpath("//*[@text='TAB 1']"));
        String target = "//*[@text='TAB 20']";
        assertFalse(findElement(By.xpath(target)).isSuccessful());
        assertTrue(scrollTo("xpath", target, null, null, null, null).isSuccessful());
        assertTrue(findElement(By.xpath(target)).isSuccessful());
    }

    @Test
    public void scrollBackToElementWithoutUiScrollable() {
        startActivity(".view.Tabs5");
        waitForElement(By.xpath("//*[@text='TAB 1']"));
        String stripId = findElement(By.androidUiAutomator("new UiSelector().scrollable(true).instance(0)"))
                .getElementId();
        String tabId = findElement(By.xpath("//*[@text='TAB 3']")).getElementId();
        // Scroll the tab away, but keep the reference to it
        assertTrue(io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands
                .scroll(stripId, null, 2f, "right", null).isSuccessful());

        // Neither the strip nor the tab have been located by the strategy UiScrollable supports
        String xpathStripId = findElement(By.xpath("(//*[@scrollable='true'])[1]")).getElementId();
        assertTrue(scrollBackTo(xpathStripId, tabId).isSuccessful());
        assertTrue(findElement(By.xpath("//*[@text='TAB 3']")).isSuccessful());
    }

    /**
     * Performs scrolling through a very long list to an element specified by its text.
     *
     */
    @Test
    @Ignore("The run-time of this test is ~8.5 minutes, which might fail automated build jobs " +
            "that have timeout of 10 minutes. To verify this scenario, run the test locally.")
    public void scrollVeryLongListSuccessfully() {
        startActivity(".view.List1"); // A very long list (500+ items).
        waitForElement(By.id("android:id/list"));

        String[] items = {
            "Zanetti Parmigiano Reggiano", // at the very end of the list
            "Abbaye de Belloc",            // at the very beginning of the list
            "Grafton Village Cheddar",     // somewhere half-way through the list
            "Yorkshire Blue"               // almost at the end of the list
        };

        for (String item : items) {
            assertFalse(String.format(
                "Item '%s' should not be found in the list.", item),
                findElement(By.text(item)).isSuccessful());

            assertTrue(String.format(
                "Scroll-to-text should have succeeded for item '%s'.", item),
                scrollToText(item, 150).isSuccessful());

            assertTrue(String.format(
                "Item '%s' should now be found in the list.", item),
                findElement(By.text(item)).isSuccessful());
        }
    }

    /**
     * Attempts scrolling through a very long list with a limited number of swipes.
     *
     */
    @Test
    public void scrollVeryLongListUnsuccessfully() {
        startActivity(".view.List1"); // A very long list (500+ items).
        waitForElement(By.id("android:id/list"));

        // Attempt to scroll to the item that is at the end of the very long list.
        // This cannot be done with 10 swipes, hence it should fail.
        assertFalse(
            "Scroll-to-text should not have succeeded.",
            scrollToText("Zanetti Parmigiano Reggiano", 10).isSuccessful());

        // Attempt to scroll back to the item that is at the beginning of the list.
        // This will need at least 10 swipes, but now we allow only 5. Thus, it should fail.
        assertFalse(
            "Scroll-to-text should not have succeeded.",
            scrollToText("Abbaye de Belloc", 5).isSuccessful());
    }

    /**
     * Performs scrolling to an element specified by its class name.
     *
     */
    @Test
    public void scrollByClassNameTest() {
        startActivity(".preference.PreferencesFromCode");
        waitForElement(By.id("android:id/list"));

        // This page contains GUI elements of different classes (text, checkbox, switch).
        // However, in the portrait mode, all elements fit on one page. We need to rotate
        // the screen to the landscape mode to shorten the visible part of the list.
        String defaultOrientation = getScreenOrientation();
        rotateScreen("LANDSCAPE");

        try {
            String badClassName = "non.existent.ClassName";

            assertFalse(
                "Scroll-by-class-name should fail for non-existent element.",
                scrollToClassName(badClassName).isSuccessful());

            // At this point, we are at the end of the list, and the Switch element is off the screen.

            String goodClassName = android.widget.Switch.class.getName();

            assertFalse(
            "Element of type '" + goodClassName + "' should not be found in the list.",
            findElement(By.className(goodClassName)).isSuccessful());

            assertTrue(
                "Scroll-to-class-name should have succeeded.",
                scrollToClassName(goodClassName).isSuccessful());

            assertTrue(
                "Element of type '" + goodClassName + "' should now be found in the list.",
                findElement(By.className(goodClassName)).isSuccessful());
        } finally {
            rotateScreen(defaultOrientation);
        }
    }

    /**
     * Performs scrolling to an element identified by a UiSelector specification.
     *
     */
    @Test
    public void scrollByUiSelectorTest() {
        scrollToText("Views"); // Due to 'Views' option not visible on small screen
        Response response = findElement(By.accessibilityId("Views"));
        clickAndWaitForStaleness(response.getElementId());

        String uiSelectorSpec = "new UiSelector().textStartsWith(\"WebView\")";

        By by = By.androidUiAutomator(uiSelectorSpec);

        assertTrue(
            "Scroll-to-element should have succeeded.",
            scrollToElement(uiSelectorSpec).isSuccessful());

        assertTrue(
            by + " should be found in the list.",
            findElement(by).isSuccessful());
    }

    @Test
    @SkipHeadlessDevices
    public void screenshotTest() {
        Response response = findElement(By.accessibilityId("Accessibility"));
        clickAndWaitForStaleness(response.getElementId());
        waitForElement(By.accessibilityId("Custom View"));

        response = screenshot();
        String value = response.getValue(String.class);
        byte[] bytes = Base64.decode(value, Base64.NO_WRAP);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        assertNotNull(bitmap);
    }

    @Test
    public void shouldBeAbleToUpdateSettings() {
        Response response = getSettings();
        JsonObject defaultSettings = response.getValue(JsonObject.class);
        Map<String, Object> settings = new HashMap<>();
        settings.put("actionAcknowledgmentTimeout", 123);
        settings.put("allowInvisibleElements", true);
        settings.put("ignoreUnimportantViews", true);
        settings.put("elementResponseAttributes", "text");
        settings.put("enableNotificationListener", false);
        settings.put("keyInjectionDelay", 10);
        settings.put("scrollAcknowledgmentTimeout", 300);
        settings.put("shouldUseCompactResponses", false);
        settings.put("waitForIdleTimeout", 50001);
        settings.put("waitForSelectorTimeout", 10);
        settings.put("shutdownOnPowerDisconnect", false);
        try {
            for (Map.Entry<String, Object> entry : settings.entrySet()) {
                updateSetting(entry.getKey(), entry.getValue());
            }
            response = getSettings();
            JsonObject jsonObject = response.getValue(JsonObject.class);
            for (Map.Entry<String, Object> entry : settings.entrySet()) {
                assertEquals(new Gson().toJsonTree(entry.getValue()), jsonObject.get(entry.getKey()));
            }
        } finally {
            updateSettings(defaultSettings);
        }
    }

    @Test
    public void shouldBeAbleToFindElementViaUiSelectorWithQuotesInParams() {
        startActivity(".view.TextFields");
        Response response = waitForElement(By.id("io.appium.android.apis:id/edit"));
        sendKeys(response.getElementId(), "Use a \"tel:\" URL");
        By androidUiAutomator = By.androidUiAutomator("new UiSelector().textContains(" +
                "\"Use a \\\"tel:\\\" URL\");");
        response = findElement(androidUiAutomator);
        assertTrue(androidUiAutomator + " should be found", response.isSuccessful());
    }

    @Test
    public void shouldBeAbleToFindElementViaUiSelectorWithParenthesesInParams() {
        startActivity(".view.TextFields");
        Response response = waitForElement(By.id("io.appium.android.apis:id/edit"));
        sendKeys(response.getElementId(), "(415)");
        By androidUiAutomator = By.androidUiAutomator("new UiSelector().textContains(" +
                "\"(415)\");");
        response = findElement(androidUiAutomator);
        assertTrue(androidUiAutomator + " should be found", response.isSuccessful());
    }

    @Test
    public void shouldBeAbleToFindElementViaUiSelectorWithCommasInParams() {
        startActivity(".view.TextFields");
        Response response = waitForElement(By.id("io.appium.android.apis:id/edit"));
        sendKeys(response.getElementId(), "Expressway, Suite 400, Austin");
        By androidUiAutomator = By.androidUiAutomator("new UiSelector().text(" +
                "\"Expressway, Suite 400, Austin\");");
        response = findElement(androidUiAutomator);
        assertTrue(androidUiAutomator + " should be found", response.isSuccessful());
    }

    @Test
    public void shouldBeAbleToFindElementViaUiScrollableScrollIntoView() {
        startActivity(".view.List1");
        waitForElement(By.id("android:id/list"));
        By androidUiAutomator = By.androidUiAutomator(
                "new UiScrollable(new UiSelector().resourceId(\"android:id/list\"))" +
                        ".scrollIntoView(new UiSelector().text(\"Beer Cheese\"));");
        Response response = findElement(androidUiAutomator);
        assertTrue(androidUiAutomator + " should be found", response.isSuccessful());
    }

    @Test
    public void shouldBeAbleToFindElementViaUiScrollableScrollTextIntoView() {
        startActivity(".view.List1");
        waitForElement(By.id("android:id/list"));
        By androidUiAutomator = By.androidUiAutomator(
                "new UiScrollable(new UiSelector().resourceId(\"android:id/list\"))" +
                        ".scrollTextIntoView(\"Beer Cheese\");");
        Response response = findElement(androidUiAutomator);
        assertTrue(androidUiAutomator + " should be found", response.isSuccessful());
    }

    @Test
    public void shouldBeAbleToFindElementViaUiScrollableGetChildByDescription() {
        startActivity(".view.ScrollBar1");
        waitForElement(By.accessibilityId("Lorem ipsum dolor sit amet."));
        By androidUiAutomator = By.androidUiAutomator(
                " new UiScrollable (new UiSelector().className (android.widget.ScrollView))" +
                        ".getChildByDescription ( new UiSelector() " +
                        ".className( android.widget.TextView ),\"Lorem ipsum dolor sit amet.\"," +
                        "true ) ; ");
        Response response = findElement(androidUiAutomator);
        assertTrue(androidUiAutomator + " should be found", response.isSuccessful());
    }

    @Test
    public void scrollIntoViewShouldNotThrowExceptionIfScrollableDoesNotExist() {
        startActivity(".view.ScrollBar1");
        waitForElement(By.accessibilityId("Lorem ipsum dolor sit amet."));
        By androidUiAutomator = By.androidUiAutomator(
                "new UiScrollable(new UiSelector().text(\"test\"))" +
                        ".scrollIntoView(new UiSelector().text(\"Lorem ipsum dolor sit amet.\"))");
        Response response = findElement(androidUiAutomator);
        assertTrue(androidUiAutomator + " should be found", response.isSuccessful());
    }

    // @Test
    // @RootRequired
    // public void shouldShutdownServerOnPowerDisconnect() throws IOException {
    //     try {
    //         UiDevice.getInstance(getInstrumentation()).executeShellCommand(
    //                 "su 0 am broadcast -a android.intent.action.ACTION_POWER_DISCONNECTED " +
    //                         "io.appium.uiautomator2.e2etest &");
    //         waitForNettyStatus(NettyStatus.OFFLINE);
    //         assertTrue(serverInstrumentation.isServerStopped());
    //     } finally {
    //         serverInstrumentation = null;
    //         startServer();
    //     }
    // }

    @Test
    public void shouldExtractDeviceInformation() {
        JsonObject info = getInfo().getValue(JsonObject.class);
        assertEquals(Build.MANUFACTURER, info.get("manufacturer").getAsString());
        assertEquals(Build.VERSION.RELEASE, info.get("platformVersion").getAsString());
        assertTrue(info.getAsJsonArray("networks").size() > 0);
    }

    @Test
    public void shouldExtractPackages() {
        JsonArray apps = getPackages().getValue(JsonArray.class);
        assertTrue(apps.size() > 0);
    }

}
