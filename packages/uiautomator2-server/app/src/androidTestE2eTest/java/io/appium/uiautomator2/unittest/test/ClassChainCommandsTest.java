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
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.getText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The {@code -android class chain} strategy, see {@link ClassChainActivity} for the hierarchy. */
public class ClassChainCommandsTest extends BaseTest {
    private static final String LIST = "**/*[`content-desc == 'chain-list'`]";

    @Before
    @Override
    public void launchAUT() {
        dismissSystemAlert();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.startActivity(new Intent(context, ClassChainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue(waitForElement(By.accessibilityId("chain-root")).isSuccessful());
    }

    // ---- helpers ----

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

    /** The content descriptions of all matches, which identify the fixture views. */
    private static List<String> all(String chain) {
        List<String> result = new ArrayList<>();
        for (String id : ids(findElements(By.androidClassChain(chain)))) {
            result.add(description(id));
        }
        return result;
    }

    /** The content description of the single match. */
    private static String one(String chain) {
        Response response = findElement(By.androidClassChain(chain));
        assertTrue(chain + ": " + response, response.isSuccessful());
        return description(response.getElementId());
    }

    private static List<String> list(String... values) {
        return Arrays.asList(values);
    }

    // ---- finding ----

    @Test
    public void findsElementsByTheirAttributes() {
        assertEquals("chain-label-0", one("**/*[`text == 'Wi-Fi'`]"));
        assertEquals("chain-ok", one("**/android.widget.Button[`content-desc == 'chain-ok'`]"));
        assertEquals("chain-header", one("**/*[`contentDescription == 'chain-header'`]"));
        assertEquals(list("chain-label-0", "chain-label-1"),
                all("**/android.widget.TextView[`text == 'Wi-Fi' OR text == 'Bluetooth'`]"));
    }

    @Test
    public void findsElementsByResourceIdInAnotherApp() {
        startActivity(Config.APP_NAME);
        assertTrue(waitForElement(By.accessibilityId("Accessibility")).isSuccessful());
        Response response = findElement(By.androidClassChain(
                "**/android.widget.TextView[`resource-id == 'android:id/text1' "
                        + "AND text == 'Accessibility'`]"));
        assertTrue(response.toString(), response.isSuccessful());
        assertEquals("Accessibility", getText(response.getElementId()).getValue(String.class));
    }

    @Test
    public void findsAllMatchesInDocumentOrder() {
        assertEquals(list("chain-header", "chain-label-0", "chain-label-1", "chain-label-2",
                "chain-amount", "chain-sized"), all("**/android.widget.TextView"));
        assertEquals(list("chain-buy", "chain-ok", "chain-cancel"),
                all("**/android.widget.Button"));
    }

    @Test
    public void picksPositionsAmongAllMatches() {
        assertEquals("chain-ok", one("**/android.widget.Button[2]"));
        assertEquals("chain-cancel", one("**/android.widget.Button[-1]"));
        assertEquals(list("chain-buy"), all("**/android.widget.Button[1]"));
        assertEquals(list(), all("**/android.widget.Button[4]"));
        assertEquals(list(), all("**/android.widget.Button[-4]"));
        // the position counts among the matches of all rows
        assertEquals(list("chain-label-1"), all("**/*[`content-desc BEGINSWITH 'chain-row'`]"
                + "/android.widget.TextView[2]"));
    }

    @Test
    public void childStepsOnlyGoOneLevelDown() {
        assertEquals(list("chain-row-0", "chain-row-1", "chain-row-2"),
                all(LIST + "/android.widget.LinearLayout"));
        assertEquals(list(), all(LIST + "/android.widget.TextView"));
        assertEquals(list("chain-label-0", "chain-label-1", "chain-label-2", "chain-amount"),
                all(LIST + "/**/android.widget.TextView"));
        assertEquals(list("chain-row-1"), all(LIST + "/*[2]"));
    }

    // ---- predicates ----

    @Test
    public void comparesStrings() {
        assertEquals(list("chain-label-0"), all("**/*[`text LIKE[c] 'wi-*'`]"));
        assertEquals(list("chain-label-2"), all("**/*[`text ==[d] 'Cafe'`]"));
        assertEquals(list("chain-label-2"), all("**/*[`text ==[cd] 'CAFE'`]"));
        assertEquals(list("chain-label-1", "chain-buy"), all("**/*[`text BEGINSWITH 'B'`]"));
        assertEquals(list("chain-label-1"), all("**/*[`text CONTAINS[c] 'TOOTH'`]"));
        // buttons show their text in capitals in this theme
        assertEquals(list("chain-ok", "chain-cancel"), all("**/*[`text IN[c] {'ok', 'Cancel'}`]"));
        // a regex needs doubled backslashes inside the predicate string
        assertEquals(list("chain-amount"), all("**/*[`text MATCHES '\\\\d+\\\\.\\\\d+'`]"));
        assertEquals(list(), all("**/*[`text MATCHES '\\\\d+'`]"));
    }

    @Test
    public void comparesBooleansAndNumbers() {
        assertEquals(list("chain-check-0"), all("**/android.widget.CheckBox[`checked == true`]"));
        assertEquals(list("chain-check-1"), all("**/android.widget.CheckBox[`checked == NO`]"));
        assertEquals(list("chain-cancel"), all("**/android.widget.Button[`enabled == false`]"));
        assertEquals(list("chain-row-1"), all(
                "**/*[`content-desc BEGINSWITH 'chain-row' AND index == 1`]"));
        assertEquals(list("chain-row-1", "chain-row-2"), all(
                "**/*[`content-desc BEGINSWITH 'chain-row' AND index > 0`]"));
        assertEquals(list("chain-sized"),
                all("**/*[`bounds.width == 300 AND bounds.height == 80`]"));
        assertEquals(6, all("**/android.widget.TextView[`bounds.width > 0`]").size());
    }

    @Test
    public void matchesWholeActionEntries() {
        assertEquals(list("chain-ok"),
                all("**/*[`actions CONTAINS 'ACTION_CLICK' AND text == 'OK'`]"));
        assertEquals(list(), all("**/*[`actions CONTAINS 'CLICK' AND text == 'OK'`]"));
    }

    @Test
    public void matchesDescendantsWithPredicates() {
        assertEquals(list("chain-row-2"), all("**/android.widget.LinearLayout[$text == 'Café'$]"
                + "[`content-desc BEGINSWITH 'chain-row'`]"));
        assertEquals(list("chain-check-1"),
                all("**/android.widget.LinearLayout[$text == 'Bluetooth'$]"
                        + "[`content-desc BEGINSWITH 'chain-row'`]/android.widget.CheckBox"));
        assertEquals(list(), all("**/android.widget.TextView[$text == 'Wi-Fi'$]"));
    }

    // ---- axes ----

    @Test
    public void followsTheParentAndAncestors() {
        assertEquals("chain-row-0", one("**/*[`content-desc == 'chain-label-0'`]/.."));
        assertEquals(list(), all("**/*[`content-desc == 'chain-label-0'`]/..[`enabled == false`]"));
        assertEquals("chain-list",
                one("**/*[`content-desc == 'chain-label-0'`]/^android.widget.LinearLayout[2]"));
        assertEquals("chain-row-0",
                one("**/*[`content-desc == 'chain-label-0'`]/^android.widget.LinearLayout[1]"));
        // the row, the list and the root, and then a LinearLayout of the system decor
        assertEquals("chain-root", one("**/*[`content-desc == 'chain-label-0'`]"
                + "/^android.widget.LinearLayout[3]"));
        assertEquals(list("chain-buy"),
                all("**/*[`content-desc == 'chain-buy'`]/../**/android.widget.Button"));
    }

    @Test
    public void followsSiblings() {
        assertEquals("chain-amount", one("**/*[`text == 'Café'`]/>android.widget.TextView[1]"));
        assertEquals("chain-buy", one("**/*[`text == 'Café'`]/>*[-1]"));
        assertEquals("chain-amount", one("**/*[`content-desc == 'chain-buy'`]/<*[1]"));
        assertEquals("chain-label-2", one("**/*[`content-desc == 'chain-buy'`]/<*[2]"));
        assertEquals(list("chain-label-2", "chain-amount"),
                all("**/*[`content-desc == 'chain-buy'`]/~android.widget.TextView"));
        assertEquals(list("chain-label-2", "chain-buy"),
                all("**/*[`content-desc == 'chain-amount'`]/~*"));
        assertEquals(list(), all("**/*[`content-desc == 'chain-ok'`]/<*"));
        assertEquals(list("chain-cancel"), all("**/*[`content-desc == 'chain-ok'`]/>*"));
    }

    @Test
    public void chainsAxisSteps() {
        assertEquals("chain-label-2", one("**/*[`text == 'Café'`]/>android.widget.TextView[1]"
                + "/<android.widget.TextView[1]"));
        assertEquals("chain-row-1", one("**/*[`text == 'Café'`]/../<*[1]"));
        assertEquals("chain-check-1", one("**/*[`text == 'Café'`]/../<*[1]/*[2]"));
    }

    // ---- context and failures ----

    @Test
    public void searchesBelowAContextElement() {
        String listId = findElement(By.androidClassChain(LIST)).getElementId();
        assertNotNull(listId);

        assertEquals(list("chain-buy"), descriptions(findElements(
                By.androidClassChain("**/android.widget.Button"), listId)));
        assertEquals(list("chain-row-0", "chain-row-1", "chain-row-2"),
                descriptions(findElements(By.androidClassChain("*"), listId)));
        Response single = findElement(
                By.androidClassChain("**/android.widget.TextView[2]"), listId);
        assertEquals("chain-label-1", description(single.getElementId()));
        // the first step is relative to the context element
        assertEquals("chain-root", description(
                findElement(By.androidClassChain(".."), listId).getElementId()));
        assertEquals("chain-sized", description(
                findElement(By.androidClassChain(">android.widget.TextView[1]"), listId)
                        .getElementId()));
    }

    private static List<String> descriptions(Response response) {
        List<String> result = new ArrayList<>();
        for (String id : ids(response)) {
            result.add(description(id));
        }
        return result;
    }

    @Test
    public void reportsMissingElements() {
        assertEquals(Collections.emptyList(),
                ids(findElements(By.androidClassChain("**/android.widget.Nothing"))));
        Response response = Client.post("/element",
                convertByToJson(By.androidClassChain("**/android.widget.Nothing"), ""));
        assertEquals(response.toString(), 404, response.code());
    }

    @Test
    public void rejectsInvalidChains() {
        String[] chains = {"**/*[`bogus == 1`]", "**/", "**/*[0]", "*[`text == 0x1`]"};
        for (String chain : chains) {
            for (String path : new String[]{"/element", "/elements"}) {
                JsonObject json = new JsonObject();
                json.addProperty("context", "");
                json.addProperty("strategy", "-android class chain");
                json.addProperty("selector", chain);
                Response response = Client.post(path, json);
                assertEquals(chain + " " + path + ": " + response, 400, response.code());
                assertTrue(response.toString(), response.toString().contains("invalid selector"));
            }
        }
    }

    // ---- the same results as the equivalent XPath queries ----

    private void assertSameAsXPath(String chain, String xpath) {
        List<String> expected = ids(findElements(By.xpath(xpath)));
        assertTrue("The XPath query should match something: " + xpath, !expected.isEmpty());
        assertEquals(chain, expected, ids(findElements(By.androidClassChain(chain))));
    }

    @Test
    public void matchesTheEquivalentXPathQueries() {
        assertSameAsXPath("**/*[`text == 'Wi-Fi'`]", "//*[@text='Wi-Fi']");
        assertSameAsXPath("**/android.widget.Button[`enabled == true AND text BEGINSWITH 'O'`]",
                "//android.widget.Button[@enabled='true' and starts-with(@text, 'O')]");
        assertSameAsXPath("**/android.widget.TextView", "//android.widget.TextView");
        assertSameAsXPath("**/android.widget.TextView[3]", "(//android.widget.TextView)[3]");
        assertSameAsXPath("**/android.widget.TextView[-1]",
                "(//android.widget.TextView)[last()]");
        assertSameAsXPath("**/*[`text CONTAINS[c] 'FI'`]",
                "//*[contains(lower-case(@text), 'fi')]");
        assertSameAsXPath("**/*[`text MATCHES '\\\\d+\\\\.\\\\d+'`]",
                "//*[matches(@text, '^\\d+\\.\\d+$')]");
        assertSameAsXPath(LIST + "/android.widget.LinearLayout[2]",
                "//*[@content-desc='chain-list']/android.widget.LinearLayout[2]");
        assertSameAsXPath("**/android.widget.LinearLayout[$text == 'Wi-Fi'$]"
                        + "[`content-desc BEGINSWITH 'chain-row'`]/**/android.widget.CheckBox",
                "//android.widget.LinearLayout[.//*[@text='Wi-Fi'] "
                        + "and starts-with(@content-desc, 'chain-row')]//android.widget.CheckBox");
        assertSameAsXPath("**/*[`text == 'Café'`]/>android.widget.TextView[1]",
                "//*[@text='Café']/following-sibling::android.widget.TextView[1]");
        // XPath of this server counts the positions of preceding-sibling in document order
        assertSameAsXPath("**/*[`content-desc == 'chain-buy'`]/<*[2]",
                "(//*[@content-desc='chain-buy']/preceding-sibling::*)[1]");
        assertSameAsXPath("**/*[`content-desc == 'chain-label-0'`]/^android.widget.LinearLayout[1]",
                "//*[@content-desc='chain-label-0']/ancestor::android.widget.LinearLayout[1]");
        assertSameAsXPath("**/*[`content-desc == 'chain-buy'`]/~android.widget.TextView",
                "//*[@content-desc='chain-buy']/(preceding-sibling::android.widget.TextView "
                        + "| following-sibling::android.widget.TextView)");
        assertSameAsXPath("**/*[`content-desc == 'chain-label-0'`]/..",
                "//*[@content-desc='chain-label-0']/..");
        assertSameAsXPath("**/android.widget.LinearLayout[`original-text == nil`]",
                "//android.widget.LinearLayout[not(@original-text)]");
    }
}
