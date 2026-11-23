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


package io.appium.uiautomator2.model;

import org.junit.Test;

import io.appium.uiautomator2.common.exceptions.ClassChainSyntaxException;
import io.appium.uiautomator2.common.exceptions.InvalidSelectorException;
import io.appium.uiautomator2.model.internal.ElementsLookupStrategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ClassChainLocatorTests {

    @Test
    public void strategyIsRegistered() {
        assertEquals(ElementsLookupStrategy.BY_CLASS_CHAIN,
                ElementsLookupStrategy.ofName("-android class chain"));
        assertEquals("-android class chain", ElementsLookupStrategy.BY_CLASS_CHAIN.toString());
    }

    @Test
    public void strategyCreatesAParsedLocator() {
        By by = ElementsLookupStrategy.BY_CLASS_CHAIN.toNativeSelector(
                "**/android.widget.Button[`text == 'OK'`]/..");
        assertTrue(by instanceof By.ByAndroidClassChain);
        assertEquals(2, ((By.ByAndroidClassChain) by).getChain().getSteps().size());
        assertEquals("**/android.widget.Button[`text == 'OK'`]/..",
                by.getElementLocator());
    }

    @Test
    public void locatorsAreEqualByTheirExpression() {
        assertEquals(By.androidClassChain("**/*"), By.androidClassChain("**/*"));
        assertEquals(By.androidClassChain("**/*").hashCode(),
                By.androidClassChain("**/*").hashCode());
        assertTrue(!By.androidClassChain("**/*").equals(By.androidClassChain("*")));
        assertTrue(!By.androidClassChain("**/*").equals(By.xpath("**/*")));
    }

    @Test
    public void invalidChainsFailWhenTheLocatorIsCreated() {
        try {
            ElementsLookupStrategy.BY_CLASS_CHAIN.toNativeSelector("**/*[`bogus == 1`]");
            fail("Expected a syntax error");
        } catch (ClassChainSyntaxException e) {
            assertTrue(e instanceof InvalidSelectorException);
            assertTrue(e.getMessage(), e.getMessage().contains("Invalid predicate"));
        }
    }

    @Test
    public void otherStrategiesStillWork() {
        assertTrue(ElementsLookupStrategy.BY_XPATH.toNativeSelector("//*") instanceof By.ByXPath);
        assertEquals(ElementsLookupStrategy.BY_UIAUTOMATOR,
                ElementsLookupStrategy.ofName("-android uiautomator"));
    }

    @Test
    public void unknownStrategiesListTheNewOne() {
        try {
            ElementsLookupStrategy.ofName("-android nothing");
            fail("Expected an invalid selector error");
        } catch (InvalidSelectorException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("-android class chain"));
        }
    }
}
