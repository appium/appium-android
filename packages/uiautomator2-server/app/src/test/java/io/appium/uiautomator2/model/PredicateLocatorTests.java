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

import io.appium.uiautomator2.common.exceptions.InvalidSelectorException;
import io.appium.uiautomator2.common.exceptions.PredicateSyntaxException;
import io.appium.uiautomator2.model.internal.ElementsLookupStrategy;
import io.appium.uiautomator2.utils.classchain.ChainStep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PredicateLocatorTests {

    @Test
    public void strategyIsRegistered() {
        assertEquals(ElementsLookupStrategy.BY_PREDICATE,
                ElementsLookupStrategy.ofName("-android predicate string"));
        assertEquals("-android predicate string", ElementsLookupStrategy.BY_PREDICATE.toString());
    }

    @Test
    public void strategyCreatesALocatorForAllDescendants() {
        By by = ElementsLookupStrategy.BY_PREDICATE.toNativeSelector(
                "text == 'OK' AND enabled == true");
        assertTrue(by instanceof By.ByAndroidPredicate);
        assertEquals("text == 'OK' AND enabled == true", by.getElementLocator());

        ChainStep step = ((By.ByAndroidPredicate) by).getChain().getSteps().get(0);
        assertEquals(1, ((By.ByAndroidPredicate) by).getChain().getSteps().size());
        assertEquals(ChainStep.Axis.DESCENDANT, step.getAxis());
        assertNull(step.getClassName());
        assertNull(step.getPosition());
        assertEquals(1, step.getFilters().size());
        assertEquals(ChainStep.Scope.SELF, step.getFilters().get(0).getScope());
    }

    @Test
    public void locatorsAreEqualByTheirExpression() {
        assertEquals(By.androidPredicate("text == 'a'"), By.androidPredicate("text == 'a'"));
        assertEquals(By.androidPredicate("text == 'a'").hashCode(),
                By.androidPredicate("text == 'a'").hashCode());
        assertNotEquals(By.androidPredicate("text == 'a'"), By.androidPredicate("text == 'b'"));
        assertNotEquals(By.androidPredicate("text == 'a'"), By.androidClassChain("**/*"));
    }

    @Test
    public void invalidPredicatesFailWhenTheLocatorIsCreated() {
        for (String predicate : new String[]{"bogus == 1", "text ==", "", "   ", "text == 'a' )"}) {
            try {
                ElementsLookupStrategy.BY_PREDICATE.toNativeSelector(predicate);
                fail("Expected a syntax error for '" + predicate + "'");
            } catch (PredicateSyntaxException e) {
                assertTrue(e instanceof InvalidSelectorException);
            }
        }
    }

    @Test
    public void nullExpressionIsRejected() {
        try {
            By.androidPredicate(null);
            fail("Expected an error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("-android predicate string"));
        }
    }

    @Test
    public void unknownStrategiesListTheNewOne() {
        try {
            ElementsLookupStrategy.ofName("-android nothing");
            fail("Expected an invalid selector error");
        } catch (InvalidSelectorException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("-android predicate string"));
        }
    }
}
