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
package io.appium.uiautomator2.unittest.test.internal.commands;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;


import io.appium.uiautomator2.model.By;
import io.appium.uiautomator2.unittest.test.Config;
import io.appium.uiautomator2.unittest.test.internal.Client;
import io.appium.uiautomator2.unittest.test.internal.Response;
import io.appium.uiautomator2.unittest.test.internal.TestUtils;
import io.appium.uiautomator2.utils.Logger;

import static android.os.SystemClock.elapsedRealtime;

@SuppressWarnings("JavaDoc")
public class DeviceCommands {

    /**
     * finds the element using By selector
     *
     * @param by Element locator
     * @return Response from UiAutomator2 server
     */
    public static Response findElement(By by) {
        return findElement(by, "");
    }

    /**
     * finds the element using By selector
     *
     * @param by        Element locator
     * @param contextId Context id
     * @return Response from UiAutomator2 server
     */
    public static Response findElement(By by, String contextId) {
        final long start = elapsedRealtime();
        JsonObject json = TestUtils.convertByToJson(by, contextId);
        Response response;
        do {
            response = Client.post("/element", json);
            Logger.info("Find element response: " + response);
            if (response.isSuccessful()) {
                return response;
            }
            TestUtils.waitForMillis(Config.DEFAULT_POLLING_INTERVAL);
        } while (elapsedRealtime() - start < Config.IMPLICIT_TIMEOUT);
        return response;
    }

    /**
     * finds the elements using By selector
     *
     * @param by Element locator
     * @return Response from UiAutomator2 server
     */
    public static Response findElements(By by) {
        return findElements(by, "");
    }

    /**
     * finds the elements using By selector below a context element
     *
     * @param by        Element locator
     * @param contextId Context id
     * @return Response from UiAutomator2 server
     */
    public static Response findElements(By by, String contextId) {
        JsonObject json = TestUtils.convertByToJson(by, contextId);
        return Client.post("/elements", json);
    }

    /**
     * Finds the height and width of screen
     *
     * @return Response from UiAutomator2 server
     */
    public static Response getDeviceSize() {
        Response response = Client.get("/window/current/size");
        Logger.info("Device window Size response:" + response);
        return response;
    }

    /**
     * performs screen rotation
     *
     * @return Response from UiAutomator2 server
     */
    public static Response rotateScreen(String orientation) {
        JsonObject postBody = new JsonObject();
        postBody.addProperty("orientation", orientation);
        return Client.post("/orientation", postBody);
    }

    /**
     * return screen orientation
     *
     * @return Response from UiAutomator2 server
     */
    public static String getScreenOrientation() {
        Response response = Client.get("/orientation");
        return response.getValue(String.class);
    }

    /**
     * return rotation
     *
     * @return Response from UiAutomator2 server
     */
    public static JsonObject getRotation() {
        Response response = Client.get("/rotation");
        return response.getValue(JsonObject.class);
    }

    /**
     * return rotation
     *
     * @return Response from UiAutomator2 server
     */
    public static Response setRotation(JsonObject rotateMap) {
        return Client.post("/rotation", rotateMap);
    }

    public static Response source() {
        return Client.get("/source");
    }

    public static Response createSession() {
        JsonObject caps = new JsonObject();
        caps.add("capabilities", new JsonObject());
        return Client.post(Config.HOST, "/session", caps);
    }

    public static Response deleteSession() {
        return Client.delete();
    }

    /**
     * return the appStrings
     *
     * @return Response from UiAutomator2 server
     */
    public static Response appStrings() {
        JsonObject jsonObject = new JsonObject();
        return Client.post("/appium/app/strings", jsonObject);
    }

    /**
     * update setting
     *
     * @return Response from UiAutomator2 server
     */
    public static Response updateSetting(String settingName, Object settingValue) {
        JsonObject postBody = new JsonObject();
        JsonObject settings = new JsonObject();
        settings.add(settingName, new Gson().toJsonTree(settingValue));
        postBody.add("settings", settings);
        return Client.post("/appium/settings", postBody);
    }

    /**
     * update settings
     *
     * @return Response from UiAutomator2 server
     */
    public static Response updateSettings(JsonObject settings) {
        JsonObject postBody = new JsonObject();
        postBody.add("settings", settings);
        return Client.post("/appium/settings", postBody);
    }

    /**
     * return settings
     *
     * @return Response from UiAutomator2 server
     */
    public static Response getSettings() {
        return Client.get("/appium/settings");
    }

    /**
     * retrieve device information
     *
     * @return Response from UiAutomator2 server
     */
    public static Response getInfo() {
        return Client.get("/appium/device/info");
    }

    /**
     * retrieve manifest-declared screen orientation of the current activity
     *
     * @return Response from UiAutomator2 server
     */
    public static Response getDeclaredOrientation() {
        return Client.get("/appium/device/declared_orientation");
    }

    /**
     * Performs scroll to an element displaying the given text.
     * The default maximum number of swipes will be used during the element search.
     *
     * @param text
     * @return Response from UiAutomator2 server
     */
    public static Response scrollToText(String text) {
        return scrollToText(text, 0);
    }

    /**
     * Performs scroll to an element displaying the given text.
     * The given maximum number of swipes will be used during the element search.
     *
     * @param text
     * @param maxSwipes
     * @return Response from UiAutomator2 server
     */
    public static Response scrollToText(String text, int maxSwipes) {
        String textEscaped = text.replace("\"", "\\\"");
        String uiSelectorSpec = String.format("new UiSelector().text(\"%s\")", textEscaped);

        return scrollToElement(uiSelectorSpec, maxSwipes);
    }

    /**
     * Scrolls until the element identified by the given locator is visible,
     * without the use of UiScrollable.
     *
     * @return Response from UiAutomator2 server
     */
    public static Response scrollTo(String strategy, String selector, String originId,
                                    String direction, Boolean fromStart, Integer maxSwipes) {
        JsonObject postBody = new JsonObject();
        if (originId != null) {
            JsonObject origin = new JsonObject();
            origin.addProperty("ELEMENT", originId);
            postBody.add("origin", origin);
        }
        JsonObject params = new JsonObject();
        params.addProperty("strategy", strategy);
        params.addProperty("selector", selector);
        if (direction != null) {
            params.addProperty("direction", direction);
        }
        if (fromStart != null) {
            params.addProperty("fromStart", fromStart);
        }
        if (maxSwipes != null) {
            params.addProperty("maxSwipes", maxSwipes);
        }
        postBody.add("params", params);
        return Client.post("/gestures/scroll_to", postBody);
    }

    public static Response scrollBackTo(String elementId, String toElementId) {
        return Client.post(String.format("/appium/element/%s/scroll_to/%s", elementId, toElementId),
                new JsonObject());
    }

    /**
     * performs scroll to an element with the given accessibility id
     *
     * @param scrollToId
     * @return Response from UiAutomator2 server
     */
    public static Response scrollToAccessibilityId(String scrollToId) {
        JsonObject postBody = new JsonObject();
        postBody.addProperty("cmd", "action");
        postBody.addProperty("action", "find");

        JsonObject params = new JsonObject();
        params.addProperty("strategy", "accessibility id");
        params.addProperty("selector", scrollToId);
        params.addProperty("context",  "");
        params.addProperty("multiple", false);

        postBody.add("params", params);
        return Client.post("/gestures/scroll_to", postBody);
    }

    /**
     * Performs scroll to an element identified by the given UiSelector specification.
     * The default maximum number of swipes will be used during the element search.
     *
     * @param className
     * @return Response from UiAutomator2 server
     */
    public static Response scrollToClassName(String className) {
        JsonObject postBody = new JsonObject();
        postBody.addProperty("cmd", "action");
        postBody.addProperty("action", "find");

        JsonObject params = new JsonObject();
        params.addProperty("strategy", "class name");
        params.addProperty("selector", className);
        params.addProperty("context",  "");
        params.addProperty("multiple", false);

        postBody.add("params", params);
        return Client.post("/gestures/scroll_to", postBody);
    }

    /**
     * Performs scroll to an element identified by the given UiSelector specification.
     * The given maximum number of swipes will be used during the element search.
     *
     * @param uiSelectorSpec
     * @return Response from UiAutomator2 server
     */
    public static Response scrollToElement(String uiSelectorSpec) {
        return scrollToElement(uiSelectorSpec, 0);
    }

    /**
     * Performs scroll to an element identified by the given UiSelector specification.
     *
     * @param uiSelectorSpec
     * @param maxSwipes
     * @return Response from UiAutomator2 server
     */
    public static Response scrollToElement(String uiSelectorSpec, int maxSwipes) {
        JsonObject postBody = new JsonObject();
        postBody.addProperty("cmd", "action");
        postBody.addProperty("action", "find");

        JsonObject params = new JsonObject();
        params.addProperty("strategy", "-android uiautomator");
        params.addProperty("selector",  uiSelectorSpec);
        params.addProperty("context",  "");
        params.addProperty("multiple", false);

        if(maxSwipes > 0) {
            params.addProperty("maxSwipes", maxSwipes);
        }

        postBody.add("params", params);
        return Client.post("/gestures/scroll_to", postBody);
    }

    /**
     * Get device screenshot
     *
     * @return Base64-encoded screenshot string
     */
    public static Response screenshot() {
        return Client.get("/screenshot");
    }

    /**
     * Accepts an on-screen alert
     *
     * @param buttonLabel optional button label to click on
     * @return Response from UiAutomator2 server
     */
    public static Response acceptAlert(@Nullable String buttonLabel) {
        final JsonObject payload = new JsonObject();
        if (buttonLabel != null) {
            payload.addProperty("buttonLabel", buttonLabel);
        }
        return Client.post("/alert/accept", payload);
    }

    /**
     * Dismisses an on-screen alert
     *
     * @param buttonLabel optional button label to click on
     * @return Response from UiAutomator2 server
     */
    public static Response dismissAlert(@Nullable String buttonLabel) {
        final JsonObject payload = new JsonObject();
        if (buttonLabel != null) {
            payload.addProperty("buttonLabel", buttonLabel);
        }
        return Client.post("/alert/dismiss", payload);
    }

    /**
     * Gets the text content of an on-screen alert
     *
     * @return Response from UiAutomator2 server
     */
    public static Response getAlertText() {
        return Client.get("/alert/text");
    }

    /**
     * Performs W3C action
     *
     * @param actions valid W3C actions list
     * @return Response from UiAutomator2 server
     */
    public static Response performActions(JsonArray actions) {
        JsonObject payload = new JsonObject();
        payload.add("actions", actions);
        return Client.post("/actions", payload);
    }

    /**
     * Gets the system amd installed applications with main activity
     *
     * @return Response from UiAutomator2 server
     */
    public static Response getPackages() {
        return Client.get("/appium/device/apps");
    }
}
