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

package io.appium.uiautomator2.utils.w3c;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import io.appium.uiautomator2.common.exceptions.InvalidArgumentException;

import static org.junit.Assert.assertEquals;

public class W3CCapsUtilsTests {

    private static JsonObject toJson(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static Map<String, Object> toMap(JsonObject json) {
        //noinspection unchecked
        return (Map<String, Object>) new Gson().fromJson(json, new TypeToken<Map<String, Object>>() { }.getType());
    }

    @Test
    public void verifyValidW3CCapsParsingWithoutPrefixes() {
        JsonArray firstMatch = new JsonArray();
        JsonObject firstMatchEntry = toJson("{" +
                "\"activity\": \"io.appium.activity\"," +
                "\"package\": \"io.appium\"" +
                "}");
        firstMatch.add(firstMatchEntry);
        JsonObject alwaysMatch = new JsonObject();
        JsonObject caps = new JsonObject();
        caps.add(W3CCapsUtils.FIRST_MATCH_KEY, firstMatch);
        caps.add(W3CCapsUtils.ALWAYS_MATCH_KEY, alwaysMatch);
        Map<String, Object> parsedCaps = W3CCapsUtils.parseCapabilities(toMap(caps));
        assertEquals(parsedCaps.get("activity"), "io.appium.activity");
        assertEquals(parsedCaps.get("package"), "io.appium");
    }

    @Test
    public void verifyValidW3CCapsParsingWithPrefixes() {
        JsonArray firstMatch = new JsonArray();
        JsonObject firstMatchEntry = toJson("{" +
                "\"appium:activity\": \"io.appium.activity\"," +
                "\"appium:package\": \"io.appium\"" +
                "}");
        firstMatch.add(firstMatchEntry);
        JsonObject alwaysMatch = new JsonObject();
        JsonObject caps = new JsonObject();
        caps.add(W3CCapsUtils.FIRST_MATCH_KEY, firstMatch);
        caps.add(W3CCapsUtils.ALWAYS_MATCH_KEY, alwaysMatch);
        Map<String, Object> parsedCaps = W3CCapsUtils.parseCapabilities(toMap(caps));
        assertEquals(parsedCaps.get("activity"), "io.appium.activity");
        assertEquals(parsedCaps.get("package"), "io.appium");
    }

    @Test
    public void verifyValidW3CCapsParsingWithUnknownPrefixes() {
        JsonArray firstMatch = new JsonArray();
        JsonObject firstMatchEntry = toJson("{" +
                "\"appium:activity\": \"io.appium.activity\"," +
                "\"appium:package\": \"io.appium\"," +
                "\"goog:chromeOptions\": {\"args\" :[\"--disable-popup-blocking\"]}" +
                "}");
        firstMatch.add(firstMatchEntry);
        JsonObject alwaysMatch = new JsonObject();
        JsonObject caps = new JsonObject();
        caps.add(W3CCapsUtils.FIRST_MATCH_KEY, firstMatch);
        caps.add(W3CCapsUtils.ALWAYS_MATCH_KEY, alwaysMatch);
        Map<String, Object> parsedCaps = W3CCapsUtils.parseCapabilities(toMap(caps));
        assertEquals(parsedCaps.get("activity"), "io.appium.activity");
        assertEquals(parsedCaps.get("package"), "io.appium");
        //noinspection unchecked,ConstantConditions
        assertEquals(((List<String>) ((Map<String, Object>) parsedCaps.get("goog:chromeOptions")).get("args")).get(0),
                "--disable-popup-blocking");
    }

    @Test
    public void verifyValidW3CCapsParsingWithPrefixesBothMatch() {
        JsonArray firstMatch = new JsonArray();
        JsonObject firstMatchEntry = toJson("{" +
                "\"appium:activity\": \"io.appium.activity\"" +
                "}");
        firstMatch.add(firstMatchEntry);
        JsonObject alwaysMatch = toJson("{" +
                "\"appium:package\": \"io.appium\"" +
                "}");
        JsonObject caps = new JsonObject();
        caps.add(W3CCapsUtils.FIRST_MATCH_KEY, firstMatch);
        caps.add(W3CCapsUtils.ALWAYS_MATCH_KEY, alwaysMatch);
        Map<String, Object> parsedCaps = W3CCapsUtils.parseCapabilities(toMap(caps));
        assertEquals(parsedCaps.get("activity"), "io.appium.activity");
        assertEquals(parsedCaps.get("package"), "io.appium");
    }

    @Test(expected = InvalidArgumentException.class)
    public void verifyInvalidW3CCapsParsingWithPrefixedStandardCap() {
        JsonArray firstMatch = new JsonArray();
        JsonObject firstMatchEntry = toJson("{" +
                "\"appium:activity\": \"io.appium.activity\"," +
                "\"appium:platformName\": \"android\"" +
                "}");
        firstMatch.add(firstMatchEntry);
        JsonObject alwaysMatch = toJson("{" +
                "\"appium:package\": \"io.appium\"" +
                "}");
        JsonObject caps = new JsonObject();
        caps.add(W3CCapsUtils.FIRST_MATCH_KEY, firstMatch);
        caps.add(W3CCapsUtils.ALWAYS_MATCH_KEY, alwaysMatch);
        W3CCapsUtils.parseCapabilities(toMap(caps));
    }

    @Test(expected = InvalidArgumentException.class)
    public void verifyInvalidW3CCapsParsingWithDuplicatedCaps() {
        JsonArray firstMatch = new JsonArray();
        JsonObject firstMatchEntry = toJson("{" +
                "\"appium:activity\": \"io.appium.activity\"" +
                "}");
        firstMatch.add(firstMatchEntry);
        JsonObject alwaysMatch = toJson("{" +
                "\"activity\": \"io.appium.activity\"" +
                "}");
        JsonObject caps = new JsonObject();
        caps.add(W3CCapsUtils.FIRST_MATCH_KEY, firstMatch);
        caps.add(W3CCapsUtils.ALWAYS_MATCH_KEY, alwaysMatch);
        W3CCapsUtils.parseCapabilities(toMap(caps));
    }
}
