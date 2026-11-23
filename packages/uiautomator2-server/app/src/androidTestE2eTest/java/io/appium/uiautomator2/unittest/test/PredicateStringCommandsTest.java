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

import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.appium.uiautomator2.model.By;
import io.appium.uiautomator2.unittest.fixture.ClassChainActivity;
import io.appium.uiautomator2.unittest.test.internal.BaseTest;
import io.appium.uiautomator2.unittest.test.internal.Client;
import io.appium.uiautomator2.unittest.test.internal.Response;

import static io.appium.uiautomator2.unittest.test.internal.TestUtils.convertByToJson;
import static io.appium.uiautomator2.unittest.test.internal.TestUtils.extractElementId;
import static io.appium.uiautomator2.unittest.test.internal.TestUtils.waitForElement;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.findElement;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.findElements;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.getAttribute;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The {@code -android predicate string} strategy, it uses the hierarchy of the class chain tests. */
public class PredicateStringCommandsTest extends BaseTest {

    @Before
    @Override
    public void launchAUT() {
        dismissSystemAlert();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.startActivity(new Intent(context, ClassChainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue(waitForElement(By.accessibilityId("chain-root")).isSuccessful());
    }

    private static List<String> ids(Response response) {
        assertTrue(response.toString(), response.isSuccessful());
        JsonArray elements = response.getValue(JsonArray.class);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            String id = extractElementId(elements.get(i).getAsJsonObject());
            assertNotNull(id);
            result.add(id);
        }
        return result;
    }

    private static String description(String elementId) {
        return getAttribute(elementId, "content-desc").getValue(String.class);
    }

    private static List<String> descriptions(Response response) {
        List<String> result = new ArrayList<>();
        for (String id : ids(response)) {
            result.add(description(id));
        }
        return result;
    }

    /** The content descriptions of all matches, which identify the fixture views. */
    private static List<String> all(String predicate) {
        return descriptions(findElements(By.androidPredicate(predicate)));
    }

    private static String one(String predicate) {
        Response response = findElement(By.androidPredicate(predicate));
        assertTrue(predicate + ": " + response, response.isSuccessful());
        return description(response.getElementId());
    }

    private static List<String> list(String... values) {
        return Arrays.asList(values);
    }

    @Test
    public void findsElementsByTheirAttributes() {
        assertEquals("chain-label-0", one("text == 'Wi-Fi'"));
        assertEquals("chain-ok", one("class == 'android.widget.Button' AND content-desc == 'chain-ok'"));
        assertEquals(list("chain-label-0", "chain-label-1"),
                all("text == 'Wi-Fi' OR text == 'Bluetooth'"));
        assertEquals(list("chain-label-2"), all("text ==[cd] 'CAFE'"));
        assertEquals(list("chain-amount"), all("text MATCHES '\\\\d+\\\\.\\\\d+'"));
    }

    @Test
    public void findsAllMatchesInDocumentOrder() {
        assertEquals(list("chain-buy", "chain-ok", "chain-cancel"),
                all("class == 'android.widget.Button'"));
        assertEquals(list("chain-check-0"), all("class == 'android.widget.CheckBox' AND checked == true"));
        assertEquals(list("chain-cancel"), all("class == 'android.widget.Button' AND enabled == false"));
        assertEquals(list("chain-sized"), all("bounds.width == 300 AND bounds.height == 80"));
    }

    @Test
    public void searchesBelowAContextElement() {
        String listId = findElement(By.androidPredicate("content-desc == 'chain-list'"))
                .getElementId();
        assertNotNull(listId);
        assertEquals(list("chain-buy"), descriptions(findElements(
                By.androidPredicate("class == 'android.widget.Button'"), listId)));
        assertEquals("chain-label-1", description(findElement(
                By.androidPredicate("text == 'Bluetooth'"), listId).getElementId()));
        // the context element itself is not a match
        assertEquals(Collections.emptyList(), ids(findElements(
                By.androidPredicate("content-desc == 'chain-list'"), listId)));
    }

    @Test
    public void reportsMissingElements() {
        assertEquals(Collections.emptyList(), ids(findElements(By.androidPredicate("text == 'Nope'"))));
        Response response = Client.post("/element",
                convertByToJson(By.androidPredicate("text == 'Nope'"), ""));
        assertEquals(response.toString(), 404, response.code());
    }

    @Test
    public void rejectsInvalidPredicates() {
        for (String predicate : new String[]{"bogus == 1", "text ==", "text == 0x1"}) {
            for (String path : new String[]{"/element", "/elements"}) {
                JsonObject json = new JsonObject();
                json.addProperty("context", "");
                json.addProperty("strategy", "-android predicate string");
                json.addProperty("selector", predicate);
                Response response = Client.post(path, json);
                assertEquals(predicate + " " + path + ": " + response, 400, response.code());
                assertTrue(response.toString(), response.toString().contains("invalid selector"));
            }
        }
    }

    @Test
    public void matchesTheEquivalentClassChainAndXPathQueries() {
        assertEquals(ids(findElements(By.androidClassChain("**/*[`text BEGINSWITH 'B'`]"))),
                ids(findElements(By.androidPredicate("text BEGINSWITH 'B'"))));
        assertEquals(ids(findElements(By.xpath("//*[starts-with(@text, 'B')]"))),
                ids(findElements(By.androidPredicate("text BEGINSWITH 'B'"))));
    }
}
