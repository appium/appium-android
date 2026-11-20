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
package io.appium.uiautomator2.unittest.test.internal;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.Configurator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.IOException;

import io.appium.uiautomator2.model.By;
import io.appium.uiautomator2.server.ServerInstrumentation;
import io.appium.uiautomator2.unittest.test.Config;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static io.appium.uiautomator2.unittest.test.internal.TestUtils.waitForElement;
import static io.appium.uiautomator2.unittest.test.internal.TestUtils.waitForElementInvisibility;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.createSession;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.deleteSession;
import static io.appium.uiautomator2.unittest.test.internal.commands.DeviceCommands.findElements;
import static io.appium.uiautomator2.unittest.test.internal.commands.ElementCommands.click;
import static io.appium.uiautomator2.utils.Device.getUiDevice;
import static org.junit.Assert.assertNotNull;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4.class)
public abstract class BaseTest {

    @Rule
    public TestWatcher watcher = new TestWatcher();

    /**
     * start io.appium.uiautomator2.server and launch the application main activity
     */
    @BeforeClass
    public static void startServer() throws IOException {
        assertNotNull(getUiDevice());
        ServerInstrumentation.getInstance().start();
        Client.waitForNettyStatus(NettyStatus.ONLINE);
        JsonObject responseValue = createSession().getValue(JsonObject.class);
        WebDriverSession.getInstance().setId(responseValue.get("sessionId").getAsString());
        Configurator.getInstance().setWaitForSelectorTimeout(0);
        Configurator.getInstance().setWaitForIdleTimeout(50000);
        TestUtils.grantPermission(getApplicationContext(), READ_EXTERNAL_STORAGE);
        TestUtils.grantPermission(getApplicationContext(), WRITE_EXTERNAL_STORAGE);
    }

    @AfterClass
    public static void stopSever() {
        deleteSession();
        WebDriverSession.getInstance().setId(null);
        Client.waitForNettyStatus(NettyStatus.OFFLINE);
    }

    @Before
    public void launchAUT() {
        dismissSystemAlert();
        startActivity(Config.APP_NAME);
        waitForElement(By.accessibilityId("Accessibility"));
    }

    protected void dismissSystemAlert() {
        String[] ids = {"android:id/button1", "android:id/aerr_wait"};
        for (String id : ids) {
            try {
                Logger.info("Checking for alert using id '" + id + "'");
                Response response = findElements(By.id(id));
                JsonArray elements = response.getValue(JsonArray.class);
                if (elements.size() == 0) {
                    continue;
                }

                String elementId = TestUtils.extractElementId(elements.get(0).getAsJsonObject());
                clickAndWaitForStaleness(elementId);
            } catch (Exception e) {
                Logger.error("Error getting alert: ", e);
            }
        }
    }

    protected void startActivity(String activity) {
        TestUtils.startActivity(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), activity
        );
    }

    protected void clickAndWaitForStaleness(String elementId) {
        if (elementId == null) {
            return;
        }

        click(elementId);
        waitForElementInvisibility(elementId);
    }
}
