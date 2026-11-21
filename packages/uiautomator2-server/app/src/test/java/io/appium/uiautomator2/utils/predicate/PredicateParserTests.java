package io.appium.uiautomator2.utils.predicate;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import io.appium.uiautomator2.common.exceptions.PredicateSyntaxException;
import io.appium.uiautomator2.utils.Attribute;
import io.appium.uiautomator2.utils.predicate.PredicateNode.Comparison;
import io.appium.uiautomator2.utils.predicate.PredicateNode.Operator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PredicateParserTests {

    private static String canonical(String predicate) {
        return PredicateParser.parse(predicate).toString();
    }

    private static PredicateSyntaxException assertInvalid(String predicate, String reasonPart) {
        try {
            PredicateParser.parse(predicate);
        } catch (PredicateSyntaxException e) {
            assertTrue(String.format("'%s' not in '%s'", reasonPart, e.getReason()),
                    e.getReason().contains(reasonPart));
            return e;
        }
        fail("Expected a syntax error for: " + predicate);
        return null;
    }

    private static Comparison comparison(String predicate) {
        return (Comparison) PredicateParser.parse(predicate);
    }

    // ---- constants and logic ----

    @Test
    public void constants() {
        assertEquals("TRUEPREDICATE", canonical("TRUEPREDICATE"));
        assertEquals("FALSEPREDICATE", canonical("falsepredicate"));
    }

    @Test
    public void andOrNotSymbolsAndWordsAreEquivalent() {
        assertEquals(canonical("text == 'a' AND checked == true OR NOT enabled == true"),
                canonical("text == 'a' && checked == true || ! enabled == true"));
    }

    @Test
    public void precedenceNotAndOr() {
        assertEquals("(text == 'a' OR (text == 'b' AND text == 'c'))",
                canonical("text == 'a' OR text == 'b' AND text == 'c'"));
        assertEquals("((text == 'a' OR text == 'b') AND text == 'c')",
                canonical("(text == 'a' OR text == 'b') AND text == 'c'"));
        assertEquals("(NOT text == 'a' AND text == 'b')",
                canonical("NOT text == 'a' AND text == 'b'"));
        assertEquals("NOT (text == 'a' AND text == 'b')",
                canonical("NOT (text == 'a' AND text == 'b')"));
    }

    @Test
    public void chainedOperatorsAreFlattened() {
        PredicateNode node = PredicateParser.parse("text == 'a' OR text == 'b' OR text == 'c'");
        assertEquals(3, ((PredicateNode.Logical) node).getOperands().size());
    }

    @Test
    public void doubleNegation() {
        assertEquals("NOT NOT checked == TRUE", canonical("NOT NOT checked == true"));
        assertEquals("NOT NOT checked == TRUE", canonical("!!checked == true"));
    }

    @Test
    public void notEqualIsNotNegation() {
        Comparison c = comparison("text != 'a'");
        assertEquals(Operator.NE, c.getOperator());
    }

    @Test
    public void whitespaceIsFlexible() {
        assertEquals("text == 'a'", canonical("  text=='a'  "));
        assertEquals("(text == 'a' AND checked == TRUE)",
                canonical("(text=='a')&&(checked==true)"));
        assertEquals("(text == 'a' AND checked == TRUE)",
                canonical("\ttext\n==\n'a'\nAND\nchecked\n==\ntrue"));
    }

    // ---- comparison operators ----

    @Test
    public void comparisonOperatorAliases() {
        assertEquals(Operator.EQ, comparison("text = 'a'").getOperator());
        assertEquals(Operator.EQ, comparison("text == 'a'").getOperator());
        assertEquals(Operator.NE, comparison("text != 'a'").getOperator());
        assertEquals(Operator.NE, comparison("text <> 'a'").getOperator());
        assertEquals(Operator.LT, comparison("index < 3").getOperator());
        assertEquals(Operator.LE, comparison("index <= 3").getOperator());
        assertEquals(Operator.LE, comparison("index =< 3").getOperator());
        assertEquals(Operator.GT, comparison("index > 3").getOperator());
        assertEquals(Operator.GE, comparison("index >= 3").getOperator());
        assertEquals(Operator.GE, comparison("index => 3").getOperator());
    }

    @Test
    public void between() {
        Comparison c = comparison("index BETWEEN {1, 5}");
        assertEquals(Operator.BETWEEN, c.getOperator());
        assertEquals(Arrays.<Object>asList(1L, 5L), c.getValues());
        assertEquals(Arrays.<Object>asList(1L, 2.5), comparison("index BETWEEN { 1 , 2.5 }")
                .getValues());
        assertEquals(Arrays.<Object>asList("a", "z"), comparison("text BETWEEN {'a','z'}")
                .getValues());
    }

    @Test
    public void betweenRequiresTwoComparableValues() {
        assertInvalid("index BETWEEN {1}", "exactly two");
        assertInvalid("index BETWEEN {1, 2, 3}", "exactly two");
        assertInvalid("index BETWEEN {1, 'a'}", "two numbers or two strings");
        assertInvalid("index BETWEEN 1", "Expected '{'");
        assertInvalid("index BETWEEN[c] {1, 2}", "Modifiers are not supported");
    }

    @Test
    public void inSet() {
        Comparison c = comparison("text IN {'a', \"b\", nil}");
        assertEquals(Operator.IN, c.getOperator());
        assertEquals(Arrays.<Object>asList("a", "b", null), c.getValues());
        assertEquals(Arrays.<Object>asList(1L, 2.5), comparison("index IN {1, 2.5}").getValues());
        assertTrue(comparison("text IN {}").getValues().isEmpty());
        assertEquals(Arrays.<Object>asList("a"), comparison("text IN{'a'}").getValues());
    }

    @Test
    public void inSetErrors() {
        assertInvalid("text IN 'a'", "Expected '{'");
        assertInvalid("text IN {'a' 'b'}", "Expected ',' or '}'");
        assertInvalid("text IN {'a',}", "expected a literal");
        assertInvalid("text IN {'a', 1}", "cannot be compared");
        assertInvalid("text IN {'a'", "Expected ',' or '}'");
    }

    // ---- string operators and modifiers ----

    @Test
    public void stringOperators() {
        assertEquals(Operator.BEGINSWITH, comparison("text BEGINSWITH 'a'").getOperator());
        assertEquals(Operator.ENDSWITH, comparison("text endswith 'a'").getOperator());
        assertEquals(Operator.CONTAINS, comparison("text CONTAINS 'a'").getOperator());
        assertEquals(Operator.LIKE, comparison("text LIKE '*a?'").getOperator());
        assertEquals(Operator.MATCHES, comparison("text MATCHES '.*a'").getOperator());
    }

    @Test
    public void stringOperatorsRequireStringLiteral() {
        assertInvalid("text CONTAINS 1", "requires a string literal");
        assertInvalid("text LIKE nil", "requires a string literal");
        assertInvalid("text BEGINSWITH true", "requires a string literal");
    }

    @Test
    public void modifiers() {
        Comparison c = comparison("text BEGINSWITH[c] 'a'");
        assertTrue(c.isCaseInsensitive());
        assertFalse(c.isDiacriticInsensitive());

        c = comparison("text ==[d] 'a'");
        assertFalse(c.isCaseInsensitive());
        assertTrue(c.isDiacriticInsensitive());

        c = comparison("text CONTAINS[cd] 'a'");
        assertTrue(c.isCaseInsensitive() && c.isDiacriticInsensitive());
        assertEquals("text CONTAINS[cd] 'a'", c.toString());

        c = comparison("text LIKE[DC] 'a'");
        assertTrue(c.isCaseInsensitive() && c.isDiacriticInsensitive());

        c = comparison("text IN[c] {'a'}");
        assertTrue(c.isCaseInsensitive());

        c = comparison("text !=[c] 'a'");
        assertTrue(c.isCaseInsensitive());

        c = comparison("text CONTAINS [c] 'a'");
        assertTrue(c.isCaseInsensitive());

        c = comparison("text CONTAINS 'a'");
        assertFalse(c.isCaseInsensitive() || c.isDiacriticInsensitive());
    }

    @Test
    public void invalidModifiers() {
        assertInvalid("text CONTAINS[x] 'a'", "Invalid modifiers");
        assertInvalid("text CONTAINS[cc] 'a'", "Invalid modifiers");
        assertInvalid("text CONTAINS[] 'a'", "Empty modifiers");
        assertInvalid("text CONTAINS[c 'a'", "Unterminated modifiers");
        assertInvalid("index <[c] 1", "Modifiers are not supported");
        assertInvalid("index < [c] 1", "Unexpected '['");
    }

    @Test
    public void matchesValidatesRegex() {
        assertInvalid("text MATCHES '(unclosed'", "Invalid regular expression");
        comparison("text MATCHES '\\\\d+'");
    }

    @Test
    public void relationalOperatorsRejectBooleanAndNull() {
        assertInvalid("index < true", "requires a number or a string");
        assertInvalid("index >= nil", "requires a number or a string");
        assertEquals(Operator.LT, comparison("text < 'b'").getOperator());
    }

    // ---- literals ----

    @Test
    public void stringLiterals() {
        assertEquals("a b", comparison("text == 'a b'").getValue());
        assertEquals("a b", comparison("text == \"a b\"").getValue());
        assertEquals("", comparison("text == ''").getValue());
        assertEquals("a'b", comparison("text == \"a'b\"").getValue());
        assertEquals("a\"b", comparison("text == 'a\"b'").getValue());
    }

    @Test
    public void stringEscapes() {
        assertEquals("a'b", comparison("text == 'a\\'b'").getValue());
        assertEquals("a\"b", comparison("text == \"a\\\"b\"").getValue());
        assertEquals("a\\b", comparison("text == 'a\\\\b'").getValue());
        assertEquals("a\nb\tc\r", comparison("text == 'a\\nb\\tc\\r'").getValue());
        assertEquals("A", comparison("text == '\\x41'").getValue());
        assertEquals("é", comparison("text == '\\u00e9'").getValue());
        assertEquals("é", comparison("text == '\\U00E9'").getValue());
    }

    @Test
    public void invalidEscapes() {
        assertInvalid("text == 'a\\db'", "Unsupported escape sequence '\\d'");
        assertInvalid("text == '\\x4'", "hex digits");
        assertInvalid("text == '\\uZZZZ'", "hex digits");
        assertInvalid("text == 'abc\\", "Unterminated string");
    }

    @Test
    public void unterminatedString() {
        PredicateSyntaxException e = assertInvalid("text == 'abc", "Unterminated string literal");
        assertEquals(8, e.getPosition());
    }

    @Test
    public void numberLiterals() {
        assertEquals(42L, comparison("index == 42").getValue());
        assertEquals(-42L, comparison("index == -42").getValue());
        assertEquals(42L, comparison("index == +42").getValue());
        assertEquals(2.5, comparison("index == 2.5").getValue());
        assertEquals(-2.5, comparison("index == -2.5").getValue());
        assertEquals(9.2e-5, comparison("index == 9.2e-5").getValue());
        assertEquals(100.0, comparison("index == 1E2").getValue());
        assertEquals(Long.MAX_VALUE, comparison("index == 9223372036854775807").getValue());
        assertEquals(-3L, comparison("index>-3").getValue());
    }

    @Test
    public void invalidNumbers() {
        assertInvalid("index == 9223372036854775808", "out of range");
        assertInvalid("index == 1e999", "out of range");
        assertInvalid("index == \u0663", "Unexpected");
        assertInvalid("index == 12abc", "Malformed number");
        assertInvalid("inputType == 0x1", "Hexadecimal, octal and binary literals are not supported");
        assertInvalid("inputType == 0X1F", "Hexadecimal, octal and binary literals");
        assertInvalid("inputType == 0o7", "Hexadecimal, octal and binary literals");
        assertInvalid("inputType == -0b11", "Hexadecimal, octal and binary literals");
        assertEquals(0L, comparison("inputType == 0").getValue());
        assertEquals(7L, comparison("inputType == 007").getValue());
        assertInvalid("index == 1.", "Unexpected '.'");
        assertInvalid("index == -", "expected a literal");
    }

    @Test
    public void booleanAndNullLiterals() {
        assertEquals(true, comparison("checked == true").getValue());
        assertEquals(true, comparison("checked == TRUE").getValue());
        assertEquals(true, comparison("checked == YES").getValue());
        assertEquals(false, comparison("checked == false").getValue());
        assertEquals(false, comparison("checked == NO").getValue());
        assertNull(comparison("text == nil").getValue());
        assertNull(comparison("text != NULL").getValue());
    }

    // ---- attributes ----

    @Test
    public void attributeAliases() {
        assertEquals(Attribute.TEXT, comparison("text == 'a'").getAttribute());
        assertEquals(Attribute.TEXT, comparison("name == 'a'").getAttribute());
        assertEquals(Attribute.CLASS, comparison("className == 'a'").getAttribute());
        assertEquals(Attribute.CLASS, comparison("class == 'a'").getAttribute());
        assertEquals(Attribute.CONTENT_DESC, comparison("contentDescription == 'a'")
                .getAttribute());
        assertEquals(Attribute.RESOURCE_ID, comparison("resourceId == 'a'").getAttribute());
        assertEquals(Attribute.PACKAGE, comparison("package == 'a'").getAttribute());
        assertEquals(Attribute.LONG_CLICKABLE, comparison("longClickable == true").getAttribute());
        assertEquals(Attribute.DISPLAYED, comparison("displayed == 1").getAttribute());
        assertEquals(Attribute.HEADING, comparison("heading == true").getAttribute());
    }

    @Test
    public void originalTextAllowsNullChecks() {
        Comparison c = comparison("original-text == nil");
        assertEquals(Attribute.ORIGINAL_TEXT, c.getAttribute());
        assertNull(c.getValue());
        assertEquals(Attribute.ORIGINAL_TEXT, comparison("original-text != nil").getAttribute());
    }

    @Test
    public void commonAttributesAreAccepted() {
        for (String name : Arrays.asList("text", "className", "resourceId", "contentDescription",
                "package", "hint")) {
            comparison(name + " == 'a'");
        }
        for (String name : Arrays.asList("checkable", "checked", "clickable", "enabled",
                "focusable", "focused", "longClickable", "scrollable", "selected", "password",
                "displayed", "heading", "dismissable")) {
            comparison(name + " == true");
        }
    }

    @Test
    public void literalsMustMatchTheAttributeType() {
        assertInvalid("checked == 'abc'", "cannot be compared");
        assertInvalid("checked == 2", "cannot be compared");
        assertInvalid("checked > 1", "cannot be applied to the boolean attribute");
        assertInvalid("displayed LIKE '*'", "cannot be applied to the boolean attribute");
        assertInvalid("checked BETWEEN {0, 1}", "cannot be applied to the boolean attribute");
        assertInvalid("index CONTAINS 'a'", "cannot be applied to the numeric attribute");
        assertInvalid("index == 'a'", "cannot be compared");
        assertInvalid("index == true", "cannot be compared");
        assertInvalid("text > 5", "cannot be compared");
        assertInvalid("text == true", "cannot be compared");
        assertInvalid("text BETWEEN {1, 2}", "cannot be compared");
        assertEquals(true, comparison("checked == 1").getValue());
        assertEquals(false, comparison("checked != 0").getValue());
        assertEquals(Arrays.<Object>asList(true, false, null),
                comparison("checked IN {1, NO, nil}").getValues());
        // Numeric attributes keep Long/Double literals
        assertEquals(1L, comparison("index == 1").getValue());
        assertNull(comparison("hint == nil").getValue());
        assertNull(comparison("maxTextLength != nil").getValue());
    }

    @Test
    public void supportedAttributeNames() {
        List<String> names = PredicateParser.supportedAttributeNames();
        assertTrue(names.contains("resourceId"));
        assertTrue(names.contains("resource-id"));
        assertFalse(names.contains("bounds"));
        assertFalse(names.contains("extras"));
    }

    @Test
    public void unknownAttribute() {
        PredicateSyntaxException e = assertInvalid("foo == 'a'", "Unknown attribute 'foo'");
        assertEquals(0, e.getPosition());
        assertTrue(e.getReason().contains("Supported attributes:"));
        assertTrue(assertInvalid("txt == 'a'", "Did you mean: text").getReason().length() < 60);
        assertTrue(assertInvalid("resourceID1 == 'a'", "Did you mean").getReason()
                .contains("resourceId"));
        assertInvalid("text == 'a' AND bar == 1", "Unknown attribute 'bar'");
    }

    private static List<String> suggestions(String key) {
        return PredicateParser.similarAttributeNames(key);
    }

    @Test
    public void suggestsCloseAttributeNames() {
        assertEquals(Arrays.asList("text"), suggestions("txt"));
        assertEquals(Arrays.asList("text"), suggestions("Text").subList(0, 1));
        assertEquals(Arrays.asList("resourceId"), suggestions("resourceID1"));
        assertEquals(Arrays.asList("checked"), suggestions("chekced").subList(0, 1));
        assertEquals("clickable", suggestions("clickble").get(0));
    }

    @Test
    public void suggestsNamesByPrefixAndSubstring() {
        assertEquals("contentDescription", suggestions("contentDesc").get(0));
        assertEquals("contentDescription", suggestions("desc").get(0));
        assertEquals("resourceId", suggestions("resource").get(0));
        assertEquals("longClickable", suggestions("longClick").get(0));
        assertEquals(Arrays.asList("selection-end", "selection-start"),
                suggestions("selection").subList(0, 2));
    }

    @Test
    public void suggestionsKeepTheTypedSpelling() {
        assertEquals("content-desc", suggestions("content-des").get(0));
        assertEquals("resource-id", suggestions("resource-i").get(0));
        assertEquals("contentDescription", suggestions("contentDescriptio").get(0));
        // One suggestion per attribute, the preferred spelling wins ties
        assertEquals(1, suggestions("desc").stream().filter(n -> n.startsWith("content")
                && n.contains("esc")).count());
    }

    @Test
    public void suggestionsAreRankedAndCapped() {
        // A case-only difference wins over a prefix, which wins over a substring
        assertEquals("text", suggestions("TEXT").get(0));
        assertEquals(Arrays.asList("text", "textSize", "textUnit"), suggestions("tex"));
        assertEquals("text", suggestions("text").get(0));
        assertEquals(3, suggestions("text").size());
        assertEquals("package", suggestions("pakage").get(0));
    }

    @Test
    public void noSuggestionsForUnrelatedNames() {
        assertTrue(suggestions("foo").isEmpty());
        assertTrue(suggestions("zzzzzz").isEmpty());
        assertTrue(suggestions("x").isEmpty());
        PredicateSyntaxException e = assertInvalid("foo == 'a'", "Supported attributes:");
        assertTrue(e.getReason().contains("resourceId"));
    }

    @Test
    public void unknownAttributeMessageListsSuggestions() {
        PredicateSyntaxException e = assertInvalid("contentDesc == 'a'",
                "Did you mean: contentDescription");
        assertEquals(0, e.getPosition());
        assertInvalid("desc == 'a'", "Did you mean: contentDescription");
        assertFalse(assertInvalid("tex == 'a'", "Did you mean: text, textSize, textUnit")
                .getReason().contains("Supported attributes"));
    }

    @Test
    public void attributeNamesAreCaseSensitive() {
        assertInvalid("RESOURCEID == 'a'", "Unknown attribute");
        assertInvalid("Text == 'a'", "Unknown attribute");
    }

    @Test
    public void dashedAttributeNames() {
        assertEquals(Attribute.CONTENT_DESC, comparison("content-desc == 'a'").getAttribute());
        assertEquals(Attribute.RESOURCE_ID, comparison("resource-id == 'a'").getAttribute());
        assertEquals(Attribute.LONG_CLICKABLE, comparison("long-clickable == true")
                .getAttribute());
        assertEquals(Attribute.SELECTION_START, comparison("selection-start >= 0")
                .getAttribute());
        assertEquals(Attribute.SELECTION_END, comparison("selection-end == 3").getAttribute());
        assertEquals(Attribute.ORIGINAL_TEXT, comparison("original-text == nil").getAttribute());
        // Both spellings give the same AST and the canonical form uses the identifier one
        assertEquals(canonical("contentDescription == 'a'"), canonical("content-desc == 'a'"));
        assertEquals("contentDescription == 'a'", canonical("content-desc == 'a'"));
        assertEquals("(content-desc == 'a' AND long-clickable == TRUE)".replace("content-desc",
                "contentDescription").replace("long-clickable", "longClickable"),
                canonical("content-desc=='a'&&long-clickable==true"));
    }

    @Test
    public void dashedAttributeNameErrors() {
        assertInvalid("foo-bar == 'a'", "Unknown attribute 'foo-bar'");
        assertInvalid("content-desc-x == 'a'", "Unknown attribute 'content-desc-x'");
        assertInvalid("content- == 'a'", "Unknown attribute 'content'");
        assertInvalid("-content == 'a'", "Unexpected '-'");
        assertInvalid("Content-Desc == 'a'", "Unknown attribute 'Content-Desc'");
        assertInvalid("content-desc == 1", "cannot be compared");
        assertInvalid("selection-start == 'a'", "cannot be compared");
        assertInvalid("content-desc.x == 'a'", "Key paths are not supported");
    }

    @Test
    public void dashDoesNotActAsAnOperator() {
        assertInvalid("index-1 == 2", "Unknown attribute 'index-1'");
        assertEquals(-1L, comparison("index == -1").getValue());
        assertEquals(-1L, comparison("index>-1").getValue());
    }

    @Test
    public void nonScalarAttributesAreRejected() {
        assertInvalid("extras == 'a'", "Unknown attribute");
        assertInvalid("contentSize == 'a'", "Unknown attribute");
    }

    @Test
    public void everySupportedNameParses() {
        for (String name : PredicateParser.supportedAttributeNames()) {
            Attribute attribute = Attribute.fromString(name);
            String literal = attribute.getType() == Attribute.Type.STRING ? "'a'"
                    : attribute.getType() == Attribute.Type.NUMBER ? "1" : "true";
            assertEquals(name, attribute, comparison(name + " == " + literal).getAttribute());
        }
    }

    @Test
    public void actions() {
        Comparison c = comparison("actions CONTAINS 'ACTION_CLICK'");
        assertEquals(Attribute.ACTIONS, c.getAttribute());
        assertEquals("actions CONTAINS 'ACTION_CLICK'", c.toString());
        assertNull(comparison("actions == nil").getValue());
        assertInvalid("actions == 1", "cannot be compared");
    }

    @Test
    public void boundsFields() {
        for (String field : Arrays.asList("left", "top", "right", "bottom", "width", "height")) {
            Comparison c = comparison("bounds." + field + " >= 10");
            assertEquals(Attribute.BOUNDS, c.getAttribute());
            assertEquals(field, c.getBoundsField().getName());
            assertEquals(Operator.GE, c.getOperator());
            assertEquals(10L, c.getValue());
        }
        assertNull(comparison("text == 'a'").getBoundsField());
        assertEquals("(bounds.left > 5 AND bounds.width BETWEEN {100, 200.5})",
                canonical("bounds.left>5 AND bounds.width BETWEEN {100, 200.5}"));
        assertEquals("bounds.top IN {1, 2}", canonical("bounds.top IN {1,2}"));
        assertEquals("bounds.right != nil", canonical("bounds.right != nil"));
        assertEquals("bounds.height <= 300", canonical("bounds.height =< 300"));
    }

    @Test
    public void boundsFieldErrors() {
        assertInvalid("bounds == 'a'", "'bounds' cannot be compared directly");
        assertInvalid("bounds.size > 1", "Unknown bounds field 'size'");
        assertInvalid("bounds. > 1", "Unknown bounds field ''");
        assertInvalid("bounds.LEFT > 1", "Unknown bounds field 'LEFT'");
        assertInvalid("bounds.left.x > 1", "Expected a comparison operator");
        assertInvalid("bounds.left == 'a'", "cannot be compared");
        assertInvalid("bounds.left CONTAINS '1'", "cannot be applied to the numeric attribute");
        assertInvalid("bounds.left == true", "cannot be compared");
        assertInvalid("text.left > 1", "Key paths are not supported");
        assertEquals(0, assertInvalid("bounds.foo > 1", "Unknown bounds field").getPosition());
    }

    // ---- unsupported syntax ----

    @Test
    public void unsupportedFeatures() {
        assertInvalid("SELF == 'a'", "'SELF' is not supported");
        assertInvalid("ANY text == 'a'", "'ANY' is not supported");
        assertInvalid("ALL text == 'a'", "'ALL' is not supported");
        assertInvalid("SOME text == 'a'", "'SOME' is not supported");
        assertInvalid("NONE text == 'a'", "'NONE' is not supported");
        assertInvalid("SUBQUERY(a, $x, $x.b == 1).@count > 0", "not supported");
        assertInvalid("text == $VALUE", "Variable substitution is not supported");
        assertInvalid("$VALUE == text", "Variable substitution is not supported");
        assertInvalid("@count == 1", "Function expressions are not supported");
        assertInvalid("FUNCTION(text, 'a') == 1", "Function expressions are not supported");
        assertInvalid("text.length == 1", "Key paths are not supported");
        assertInvalid("text == checked", "Comparing attributes with each other");
        assertInvalid("'a' == text", "The left operand must be an attribute name");
        assertInvalid("1 == index", "The left operand must be an attribute name");
        assertInvalid("text NOT CONTAINS 'a'", "'NOT' is only supported as a prefix");
        assertInvalid("text IS 'a'", "Unknown or unsupported operator 'IS'");
        assertInvalid("text CONTAINS 'a' ? 1 : 2", "Unexpected '?'");
    }

    // ---- structural errors ----

    @Test
    public void emptyInput() {
        assertInvalid("", "must not be empty");
        assertInvalid("   ", "must not be empty");
        assertInvalid(null, "must not be empty");
    }

    @Test
    public void incompleteInput() {
        assertInvalid("text", "Expected a comparison operator");
        assertInvalid("text ==", "expected a literal");
        assertInvalid("text == 'a' AND", "Unexpected end");
        assertInvalid("NOT", "Unexpected end");
        assertInvalid("(text == 'a'", "Expected ')'");
        assertInvalid("text == 'a')", "Unexpected ')'");
        assertInvalid("()", "Unexpected ')'");
        assertInvalid("text == 'a' checked == true", "Unexpected 'c'");
        assertInvalid("== 'a'", "Unexpected '='");
    }

    @Test
    public void errorPositionsAndMessageFormat() {
        PredicateSyntaxException e = assertInvalid("text == 'a' AND foo == 1", "Unknown attribute");
        assertEquals(16, e.getPosition());
        assertEquals("text == 'a' AND foo == 1", e.getPredicate());
        assertTrue(e.getMessage().startsWith(
                "Cannot parse predicate 'text == 'a' AND foo == 1'. Unknown attribute 'foo'."));
        assertTrue(e.getMessage().endsWith("at position 16"));

        assertEquals(7, assertInvalid("text ==", "expected a literal").getPosition());
        assertEquals(11, assertInvalid("text == 'a')", "Unexpected ')'").getPosition());
    }

    @Test
    public void deepNestingIsRejected() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append('(');
        }
        sb.append("TRUEPREDICATE");
        for (int i = 0; i < 1000; i++) {
            sb.append(')');
        }
        assertInvalid(sb.toString(), "nested too deeply");
        assertInvalid(new String(new char[1000]).replace("\0", "NOT ") + "TRUEPREDICATE",
                "nested too deeply");
    }

    @Test
    public void keywordsRequireWordBoundaries() {
        // "ORDER" must not be read as OR + "DER"
        assertInvalid("text == 'a' ORDER text == 'b'", "Unexpected 'O'");
        assertEquals(Operator.IN, comparison("text IN {'a'}").getOperator());
    }

    // ---- real world examples ----

    @Test
    public void selectorMappingExamples() {
        assertEquals("text == 'A'", canonical("text == 'A'"));
        assertEquals("text CONTAINS 'A'", canonical("text CONTAINS 'A'"));
        assertEquals("text BEGINSWITH 'A'", canonical("text BEGINSWITH 'A'"));
        assertEquals("text MATCHES 'A.*'", canonical("text MATCHES 'A.*'"));
        assertEquals("contentDescription == 'A'", canonical("contentDescription == 'A'"));
        assertEquals("resourceId == 'pkg:id/a'", canonical("resourceId == 'pkg:id/a'"));
        assertEquals("scrollable == TRUE", canonical("scrollable == true"));
        assertEquals("original-text == nil", canonical("original-text == nil"));
    }

    @Test
    public void iosDocExamples() {
        assertEquals("text LIKE '*Total: $*'", canonical("name LIKE '*Total: $*'"));
        assertEquals("text MATCHES '.*of [1-9]'", canonical("name MATCHES '.*of [1-9]'"));
        assertEquals("text IN {'Ben', 'Melissa', 'Matthew'}",
                canonical("name IN { 'Ben', 'Melissa', 'Matthew' }"));
        assertEquals("(class == 'android.widget.Button' AND text BEGINSWITH[cd] 'results')",
                canonical("className == 'android.widget.Button' AND "
                        + "name BEGINSWITH[cd] 'results'"));
        assertEquals("displayed == TRUE", canonical("displayed == 1"));
    }

    @Test
    public void toStringRoundTrips() {
        String[] predicates = {
                "text == 'it\\'s'",
                "(text == 'a' OR checked == TRUE) AND NOT enabled == FALSE",
                "text IN {'a', nil}",
                "index IN {1, 2.5}",
                "contentDescription == 'x'",
                "resourceId BEGINSWITH[c] 'pkg:'",
                "index BETWEEN {1, 2}",
                "text LIKE[cd] 'a*b?'",
        };
        for (String predicate : predicates) {
            assertEquals(canonical(predicate), canonical(canonical(predicate)));
        }
    }
}
