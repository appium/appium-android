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

import java.util.List;

import io.appium.uiautomator2.common.exceptions.ClassChainSyntaxException;
import io.appium.uiautomator2.common.exceptions.InvalidSelectorException;
import io.appium.uiautomator2.utils.classchain.ChainStep.Scope;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ClassChainParserTests {
    private static final String BUTTON = "android.widget.Button";
    private static final String VIEW = "android.view.View";

    private static List<ChainStep> steps(String query) {
        return ClassChainParser.parse(query).getSteps();
    }

    private static String canonical(String query) {
        return ClassChainParser.parse(query).toString();
    }

    private static ClassChainSyntaxException assertInvalid(String query, String reasonPart) {
        try {
            ClassChainParser.parse(query);
        } catch (ClassChainSyntaxException e) {
            assertTrue(String.format("'%s' not in '%s'", reasonPart, e.getReason()),
                    e.getReason().contains(reasonPart));
            return e;
        }
        fail("Expected a syntax error for: " + query);
        return null;
    }

    private static void assertStep(ChainStep step, boolean descendant, String className,
                                   Integer position, int filters) {
        assertEquals(descendant, step.isDescendant());
        assertEquals(className, step.getClassName());
        assertEquals(position, step.getPosition());
        assertEquals(filters, step.getFilters().size());
    }

    // ---- valid chains (ported from WDA's FBClassChainTests) ----

    @Test
    public void childChain() {
        List<ChainStep> steps = steps(VIEW + "/" + BUTTON);
        assertEquals(2, steps.size());
        assertStep(steps.get(0), false, VIEW, null, 0);
        assertStep(steps.get(1), false, BUTTON, null, 0);
    }

    @Test
    public void starsAndPositions() {
        List<ChainStep> steps = steps(VIEW + "/" + BUTTON + "[3]/*[4]/*[5]/android.app.Dialog");
        assertEquals(5, steps.size());
        assertStep(steps.get(1), false, BUTTON, 3, 0);
        assertStep(steps.get(2), false, null, 4, 0);
        assertStep(steps.get(3), false, null, 5, 0);
        assertStep(steps.get(4), false, "android.app.Dialog", null, 0);
    }

    @Test
    public void singleStar() {
        List<ChainStep> steps = steps("*");
        assertEquals(1, steps.size());
        assertStep(steps.get(0), false, null, null, 0);
    }

    @Test
    public void leadingDescendantMarker() {
        List<ChainStep> steps = steps("**/*/*/" + BUTTON);
        assertEquals(3, steps.size());
        assertStep(steps.get(0), true, null, null, 0);
        assertStep(steps.get(1), false, null, null, 0);
        assertStep(steps.get(2), false, BUTTON, null, 0);
    }

    @Test
    public void descendantMarkersInTheMiddle() {
        List<ChainStep> steps = steps("**/" + BUTTON + "/**/*");
        assertEquals(2, steps.size());
        assertStep(steps.get(0), true, BUTTON, null, 0);
        assertStep(steps.get(1), true, null, null, 0);
        steps = steps(VIEW + "/**/" + BUTTON + "/" + VIEW);
        assertStep(steps.get(0), false, VIEW, null, 0);
        assertStep(steps.get(1), true, BUTTON, null, 0);
        assertStep(steps.get(2), false, VIEW, null, 0);
    }

    @Test
    public void negativePosition() {
        List<ChainStep> steps = steps(VIEW + "/" + BUTTON + "[-1]");
        assertStep(steps.get(1), false, BUTTON, -1, 0);
    }

    @Test
    public void predicates() {
        List<ChainStep> steps = steps(VIEW + "[`text == 'a'`]/" + BUTTON + "[`checked == true`]");
        assertStep(steps.get(0), false, VIEW, null, 1);
        assertStep(steps.get(1), false, BUTTON, null, 1);
        assertEquals(Scope.SELF, steps.get(0).getFilters().get(0).getScope());
        assertEquals("text == 'a'", steps.get(0).getFilters().get(0).getPredicate().toString());
    }

    @Test
    public void descendantAndSelfPredicatesKeepTheirOrder() {
        List<ChainStep> steps = steps("**/" + VIEW + "[$text == 'a' AND enabled == true$]"
                + "[`checked == true`][$clickable == true$][`text == 'b'`][10]");
        assertEquals(1, steps.size());
        ChainStep step = steps.get(0);
        assertStep(step, true, VIEW, 10, 4);
        assertEquals(Scope.DESCENDANT, step.getFilters().get(0).getScope());
        assertEquals(Scope.SELF, step.getFilters().get(1).getScope());
        assertEquals(Scope.DESCENDANT, step.getFilters().get(2).getScope());
        assertEquals(Scope.SELF, step.getFilters().get(3).getScope());
    }

    @Test
    public void predicateEscapes() {
        ChainStep step = steps("*[`text == \"к``ири````'лиця\"`][3]").get(0);
        assertEquals("text == 'к`ири``\\'лиця'",
                step.getFilters().get(0).getPredicate().toString());
        assertEquals(Integer.valueOf(3), step.getPosition());
        ChainStep dollar = steps("*[$text == 'a$$b'$]").get(0);
        assertEquals("text == 'a$b'", dollar.getFilters().get(0).getPredicate().toString());
    }

    @Test
    public void brackets() {
        // brackets and the other marker are plain characters inside a predicate
        ChainStep step = steps("*[`text == '[1]$'`][`text == 'a``]``'`][2]").get(0);
        assertEquals(2, step.getFilters().size());
    }

    // ---- class names ----

    @Test
    public void classNames() {
        assertEquals("android.widget.TextView", steps("android.widget.TextView").get(0).getClassName());
        assertEquals("Button", steps("Button").get(0).getClassName());
        assertEquals("androidx.recyclerview.widget.RecyclerView$ViewHolder",
                steps("androidx.recyclerview.widget.RecyclerView$ViewHolder").get(0).getClassName());
        assertEquals("a.b$1", steps("a.b$1").get(0).getClassName());
        assertEquals("com.example.Вид", steps("com.example.Вид").get(0).getClassName());
    }

    @Test
    public void dollarInClassNameIsNotAPredicateMarker() {
        ChainStep step = steps("a.B$C[$text == 'x'$]").get(0);
        assertEquals("a.B$C", step.getClassName());
        assertEquals(Scope.DESCENDANT, step.getFilters().get(0).getScope());
    }

    // ---- canonical form ----

    @Test
    public void canonicalFormRoundTrips() {
        String[] queries = {
                "*", "**/*", VIEW + "/" + BUTTON + "[2]", "**/" + VIEW + "/**/" + BUTTON + "[-1]",
                "**/" + VIEW + "[`text == 'a``b' AND checked == true`][$enabled == true$][3]",
                "*[`text == 'a$b'`][$text == 'a$$b'$]",
        };
        for (String query : queries) {
            String canonical = canonical(query);
            assertEquals(canonical, canonical(canonical));
        }
        assertEquals("**/*[`text == 'a``b'`]", canonical("**/*[`text   ==  'a``b'`]"));
        assertEquals("*[$text == 'a$$b'$]", canonical("*[$text=='a$$b'$]"));
    }

    // ---- invalid chains (ported from WDA's testInvalidChains) ----

    @Test
    public void invalidStructure() {
        String[] queries = {
                "/" + VIEW, VIEW + "/", VIEW + "//*", VIEW + "*/*", "**", "***", "**/*/**", "/**",
                VIEW + "/**", "**[1]/" + VIEW, "**[`text == '1'`]/" + VIEW, "**/**/*", "**/",
                "*/", " " + VIEW, VIEW + " ", VIEW + "/ " + BUTTON, "a..b", "a.", ".a", "1a", "a-b",
        };
        for (String query : queries) {
            assertInvalid(query, "");
        }
    }

    @Test
    public void invalidPositions() {
        String[] queries = {
                VIEW + "[0]", VIEW + "[-0]", VIEW + "[1][1]", VIEW + "[ 2 ]", VIEW + "[[2]",
                VIEW + "[2]]", VIEW + "[]", VIEW + "[-]", VIEW + "[+1]", VIEW + "[1.5]",
                VIEW + "[99999999999]", VIEW + "[1", VIEW + "[0x1]",
        };
        for (String query : queries) {
            assertInvalid(query, "");
        }
    }

    @Test
    public void invalidPredicates() {
        String[] queries = {
                VIEW + "[text = 'a']", VIEW + "[1][`checked == true`]",
                VIEW + "[1] [`checked == true`]", VIEW + "[ `checked == true`]",
                VIEW + "[`checked == true][`text == 'a'`]", VIEW + "[`checked == true]",
                VIEW + "[$checked == true]", VIEW + "[``]", VIEW + "[$$]",
                VIEW + "[`text == \"bla```bla\"`]", VIEW + "[$text == \"bla$$$bla\"$]",
                VIEW + "[`checked == true` ]", VIEW + "[`checked == true`x]",
                VIEW + "[`unknown == 1`]", VIEW + "[`text ==`]",
        };
        for (String query : queries) {
            assertInvalid(query, "");
        }
    }

    @Test
    public void emptyQuery() {
        assertInvalid("", "must not be empty");
        try {
            ClassChainParser.parse(null);
            fail();
        } catch (ClassChainSyntaxException e) {
            assertTrue(e.getReason().contains("must not be empty"));
        }
    }

    // ---- error reporting ----

    @Test
    public void errorsAreInvalidSelectorExceptionsWithPositions() {
        ClassChainSyntaxException e = assertInvalid(VIEW + "[0]", "non-zero");
        assertTrue(e instanceof InvalidSelectorException);
        assertEquals(VIEW.length() + 1, e.getPosition());
        assertEquals(VIEW + "[0]", e.getQuery());
        assertEquals(String.format("Cannot parse class chain query '%s[0]'. %s at position %d",
                VIEW, e.getReason(), e.getPosition()), e.getMessage());

        assertEquals(1, assertInvalid("a/", "Expected a class name").getPosition() - 1);
        assertEquals(2, assertInvalid("a/", "Expected a class name").getPosition());
        assertEquals(1, assertInvalid("a/**", "descendant marker").getPosition() - 1);
        assertEquals(0, assertInvalid("**", "descendant marker").getPosition());
        assertEquals(1, assertInvalid("a ", "Unexpected ' '").getPosition());
        assertEquals(3, assertInvalid("a.b.", "must not end with '.'").getPosition() - 1);
        assertEquals(1, assertInvalid("a[1][2]", "only once").getPosition() - 3);
        assertEquals(4, assertInvalid("a[1][`x == 1`]", "before the position").getPosition());
    }

    @Test
    public void unterminatedPredicateReportsTheOpeningBracket() {
        ClassChainSyntaxException e = assertInvalid("a/b[`text == 'x'", "closing '`'");
        assertEquals(3, e.getPosition());
        e = assertInvalid("a[$text == 'x'", "closing '$'");
        assertEquals(1, e.getPosition());
    }

    @Test
    public void predicateErrorsPointAtThePredicateAndKeepTheReason() {
        ClassChainSyntaxException e = assertInvalid("a[`bogus == 1`]", "Invalid predicate");
        assertTrue(e.getReason(), e.getReason().contains("bogus"));
        assertEquals(3, e.getPosition());
        e = assertInvalid("a[``]", "must not be empty");
        assertEquals(3, e.getPosition());
    }

    @Test
    public void nonDecimalLiteralsAreRejectedInsideChains() {
        assertInvalid("*[`inputType == 0x1`]", "not supported");
    }

    @Test
    public void stepsAreImmutable() {
        List<ChainStep> steps = steps("a/b");
        try {
            steps.add(steps.get(0));
            fail();
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        assertFalse(steps.get(0).isDescendant());
        assertNull(steps.get(0).getPosition());
        try {
            steps.get(0).getFilters().add(null);
            fail();
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}
