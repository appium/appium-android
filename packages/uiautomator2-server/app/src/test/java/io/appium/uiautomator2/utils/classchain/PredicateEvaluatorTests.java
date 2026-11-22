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


package io.appium.uiautomator2.utils.classchain;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

import io.appium.uiautomator2.utils.Attribute;
import io.appium.uiautomator2.utils.predicate.PredicateNode.BoundsField;
import io.appium.uiautomator2.utils.predicate.PredicateParser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PredicateEvaluatorTests {

    private static boolean eval(String predicate, Object... attributes) {
        Map<Attribute, Object> map = new EnumMap<>(Attribute.class);
        for (int i = 0; i < attributes.length; i += 2) {
            map.put((Attribute) attributes[i], attributes[i + 1]);
        }
        return evalWithBounds(predicate, null, map);
    }

    private static boolean evalWithBounds(String predicate, int[] bounds,
                                          Map<Attribute, Object> attributes) {
        PredicateEvaluator.Values values = new PredicateEvaluator.Values() {
            @Override
            public Object get(Attribute attribute) {
                return attributes.get(attribute);
            }

            @Override
            public Long getBound(BoundsField field) {
                if (bounds == null) {
                    return null;
                }
                switch (field) {
                    case LEFT:
                        return (long) bounds[0];
                    case TOP:
                        return (long) bounds[1];
                    case RIGHT:
                        return (long) bounds[2];
                    case BOTTOM:
                        return (long) bounds[3];
                    case WIDTH:
                        return (long) bounds[2] - bounds[0];
                    default:
                        return (long) bounds[3] - bounds[1];
                }
            }
        };
        return new PredicateEvaluator().evaluate(PredicateParser.parse(predicate), values);
    }

    // ---- logic ----

    @Test
    public void constantsAndLogic() {
        assertTrue(eval("TRUEPREDICATE"));
        assertFalse(eval("FALSEPREDICATE"));
        assertTrue(eval("text == 'a' OR text == 'b'", Attribute.TEXT, "b"));
        assertFalse(eval("text == 'a' AND checked == true", Attribute.TEXT, "a",
                Attribute.CHECKED, false));
        assertTrue(eval("NOT (text == 'a' OR text == 'b')", Attribute.TEXT, "c"));
        assertTrue(eval("text == 'x' OR text == 'y' AND checked == true", Attribute.TEXT, "x",
                Attribute.CHECKED, false));
    }

    @Test
    public void shortCircuitsBeforeReadingLaterAttributes() {
        PredicateEvaluator.Values values = new PredicateEvaluator.Values() {
            @Override
            public Object get(Attribute attribute) {
                if (attribute == Attribute.HINT) {
                    throw new AssertionError("must not be read");
                }
                return "a";
            }

            @Override
            public Long getBound(BoundsField field) {
                return null;
            }
        };
        PredicateEvaluator evaluator = new PredicateEvaluator();
        assertFalse(evaluator.evaluate(
                PredicateParser.parse("text == 'b' AND hint == 'x'"), values));
        assertTrue(evaluator.evaluate(PredicateParser.parse("text == 'a' OR hint == 'x'"), values));
    }

    // ---- strings ----

    @Test
    public void stringEquality() {
        assertTrue(eval("text == 'Wi-Fi'", Attribute.TEXT, "Wi-Fi"));
        assertFalse(eval("text == 'wi-fi'", Attribute.TEXT, "Wi-Fi"));
        assertTrue(eval("text != 'x'", Attribute.TEXT, "Wi-Fi"));
        assertTrue(eval("text IN {'a', 'Wi-Fi'}", Attribute.TEXT, "Wi-Fi"));
        assertFalse(eval("text IN {'a', 'b'}", Attribute.TEXT, "Wi-Fi"));
    }

    @Test
    public void modifiers() {
        assertTrue(eval("text ==[c] 'wi-fi'", Attribute.TEXT, "Wi-Fi"));
        assertTrue(eval("text ==[d] 'Cafe'", Attribute.TEXT, "Café"));
        assertFalse(eval("text ==[d] 'cafe'", Attribute.TEXT, "Café"));
        assertTrue(eval("text ==[cd] 'cafe'", Attribute.TEXT, "Café"));
        assertTrue(eval("text CONTAINS[c] 'FI'", Attribute.TEXT, "Wi-Fi"));
        assertTrue(eval("text BEGINSWITH[c] 'WI'", Attribute.TEXT, "Wi-Fi"));
        assertTrue(eval("text ENDSWITH[c] 'FI'", Attribute.TEXT, "Wi-Fi"));
        assertTrue(eval("text IN[c] {'WI-FI'}", Attribute.TEXT, "Wi-Fi"));
    }

    @Test
    public void stringOperators() {
        assertTrue(eval("text BEGINSWITH 'Wi'", Attribute.TEXT, "Wi-Fi"));
        assertFalse(eval("text BEGINSWITH 'Fi'", Attribute.TEXT, "Wi-Fi"));
        assertTrue(eval("text ENDSWITH 'Fi'", Attribute.TEXT, "Wi-Fi"));
        assertTrue(eval("text CONTAINS '-'", Attribute.TEXT, "Wi-Fi"));
        assertFalse(eval("text CONTAINS 'x'", Attribute.TEXT, "Wi-Fi"));
    }

    @Test
    public void likeIsAFullMatchWithWildcards() {
        assertTrue(eval("text LIKE 'W?-F*'", Attribute.TEXT, "Wi-Fi"));
        assertTrue(eval("text LIKE '*'", Attribute.TEXT, ""));
        assertFalse(eval("text LIKE 'Wi'", Attribute.TEXT, "Wi-Fi"));
        assertTrue(eval("text LIKE 'a*b'", Attribute.TEXT, "a\nb"));
        assertTrue(eval("text LIKE[c] 'WI*'", Attribute.TEXT, "wi-fi"));
        assertTrue(eval("text LIKE 'a.b'", Attribute.TEXT, "a.b"));
        assertFalse(eval("text LIKE 'a.b'", Attribute.TEXT, "axb"));
    }

    @Test
    public void matchesIsAFullMatch() {
        assertTrue(eval("text MATCHES 'Item \\\\d+'", Attribute.TEXT, "Item 12"));
        assertFalse(eval("text MATCHES 'Item'", Attribute.TEXT, "Item 12"));
        assertTrue(eval("text MATCHES[c] 'item \\\\D+'", Attribute.TEXT, "ITEM ab"));
        assertFalse(eval("text MATCHES[c] 'item \\\\D+'", Attribute.TEXT, "ITEM 12"));
    }

    @Test
    public void relationalOperatorsOnStrings() {
        assertTrue(eval("text < 'b'", Attribute.TEXT, "a"));
        assertTrue(eval("text BETWEEN {'a', 'c'}", Attribute.TEXT, "b"));
        assertFalse(eval("text BETWEEN {'a', 'c'}", Attribute.TEXT, "d"));
    }

    // ---- numbers ----

    @Test
    public void numbersAreComparedByValue() {
        assertTrue(eval("index == 3", Attribute.INDEX, 3));
        assertTrue(eval("index == 3", Attribute.INDEX, 3L));
        assertTrue(eval("index == 3.0", Attribute.INDEX, 3));
        assertTrue(eval("textSize == 12.5", Attribute.TEXT_SIZE, 12.5f));
        assertTrue(eval("index > 2", Attribute.INDEX, 3));
        assertFalse(eval("index > 3", Attribute.INDEX, 3));
        assertTrue(eval("index >= 3 AND index <= 3", Attribute.INDEX, 3));
        assertTrue(eval("index BETWEEN {1, 3}", Attribute.INDEX, 3));
        assertFalse(eval("index BETWEEN {1, 2}", Attribute.INDEX, 3));
        assertTrue(eval("index IN {1, 3}", Attribute.INDEX, 3));
        assertTrue(eval("index != 4", Attribute.INDEX, 3));
    }

    @Test
    public void boundsFields() {
        int[] bounds = {10, 20, 110, 70};
        Map<Attribute, Object> none = new EnumMap<>(Attribute.class);
        assertTrue(evalWithBounds("bounds.left == 10 AND bounds.top == 20", bounds, none));
        assertTrue(evalWithBounds("bounds.right == 110 AND bounds.bottom == 70", bounds, none));
        assertTrue(evalWithBounds("bounds.width == 100 AND bounds.height == 50", bounds, none));
        assertTrue(evalWithBounds("bounds.width > 99 AND bounds.height < 51", bounds, none));
        assertFalse(evalWithBounds("bounds.width > 100", bounds, none));
        assertFalse(evalWithBounds("bounds.width > 0", null, none));
    }

    // ---- booleans and nil ----

    @Test
    public void booleans() {
        assertTrue(eval("checked == true", Attribute.CHECKED, true));
        assertTrue(eval("checked == 1", Attribute.CHECKED, true));
        assertTrue(eval("checked == NO", Attribute.CHECKED, false));
        assertTrue(eval("checked != true", Attribute.CHECKED, false));
        assertTrue(eval("checked IN {1, nil}", Attribute.CHECKED, true));
    }

    @Test
    public void nilOnlyMatchesMissingValues() {
        assertTrue(eval("original-text == nil"));
        assertFalse(eval("original-text == nil", Attribute.ORIGINAL_TEXT, ""));
        assertTrue(eval("original-text != nil", Attribute.ORIGINAL_TEXT, ""));
        assertTrue(eval("hint != 'x'"));
        assertFalse(eval("hint == 'x'"));
        assertFalse(eval("hint CONTAINS 'x'"));
        assertFalse(eval("hint LIKE '*'"));
        assertFalse(eval("index > 0"));
        assertFalse(eval("index BETWEEN {0, 5}"));
        assertTrue(eval("hint IN {'a', nil}"));
        assertFalse(eval("hint IN {'a', 'b'}"));
    }

    @Test
    public void actionsContainsMatchesWholeEntries() {
        String actions = "ACTION_CLICK,ACTION_LONG_CLICK,ACTION_FOCUS";
        assertTrue(eval("actions CONTAINS 'ACTION_CLICK'", Attribute.ACTIONS, actions));
        assertTrue(eval("actions CONTAINS 'ACTION_LONG_CLICK'", Attribute.ACTIONS, actions));
        assertFalse(eval("actions CONTAINS 'CLICK'", Attribute.ACTIONS, actions));
        assertTrue(eval("actions CONTAINS[c] 'action_focus'", Attribute.ACTIONS, actions));
        assertFalse(eval("actions CONTAINS 'ACTION_FOCUS'", Attribute.ACTIONS, "ACTION_FOCUS_X"));
        // other string attributes keep the substring semantics
        assertTrue(eval("text CONTAINS 'CLICK'", Attribute.TEXT, actions));
        assertEquals(true, eval("actions == 'ACTION_FOCUS'", Attribute.ACTIONS, "ACTION_FOCUS"));
    }
}
