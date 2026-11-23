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

import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import io.appium.uiautomator2.utils.Attribute;
import io.appium.uiautomator2.utils.GlobMatcher;
import io.appium.uiautomator2.utils.predicate.PredicateNode;
import io.appium.uiautomator2.utils.predicate.PredicateNode.BoundsField;
import io.appium.uiautomator2.utils.predicate.PredicateNode.Comparison;
import io.appium.uiautomator2.utils.predicate.PredicateNode.Logical;
import io.appium.uiautomator2.utils.predicate.PredicateNode.Not;

/** Evaluates a parsed predicate against a node. Use one instance per lookup, it caches patterns. */
public final class PredicateEvaluator {

    /** The values of a single node, read on demand. */
    public interface Values {
        @Nullable
        Object get(Attribute attribute);

        @Nullable
        Long getBound(BoundsField field);
    }

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private final Map<Comparison, Pattern> patterns = new IdentityHashMap<>();
    // the expected values with the modifiers of their comparison applied
    private final Map<Comparison, List<Object>> expectedValues = new IdentityHashMap<>();

    public boolean evaluate(PredicateNode predicate, Values values) {
        if (predicate instanceof PredicateNode.Constant) {
            return ((PredicateNode.Constant) predicate).getValue();
        }
        if (predicate instanceof Not) {
            return !evaluate(((Not) predicate).getOperand(), values);
        }
        if (predicate instanceof Logical) {
            Logical logical = (Logical) predicate;
            for (PredicateNode operand : logical.getOperands()) {
                if (evaluate(operand, values) != logical.isAnd()) {
                    return !logical.isAnd();
                }
            }
            return logical.isAnd();
        }
        return compare((Comparison) predicate, values);
    }

    private boolean compare(Comparison comparison, Values values) {
        BoundsField field = comparison.getBoundsField();
        Object actual = field == null
                ? values.get(comparison.getAttribute())
                : values.getBound(field);
        List<Object> expected = expectedValues(comparison);
        switch (comparison.getOperator()) {
            case EQ:
                return isEqual(actual, expected.get(0), comparison);
            case NE:
                return !isEqual(actual, expected.get(0), comparison);
            case IN:
                for (Object value : expected) {
                    if (isEqual(actual, value, comparison)) {
                        return true;
                    }
                }
                return false;
            case LT:
            case LE:
            case GT:
            case GE:
                Integer order = compareOrder(actual, expected.get(0));
                return order != null && matchesOrder(comparison.getOperator(), order);
            case BETWEEN:
                Integer low = compareOrder(actual, expected.get(0));
                Integer high = compareOrder(actual, expected.get(1));
                return low != null && high != null && low >= 0 && high <= 0;
            default:
                return matchesString(comparison, actual, (String) expected.get(0));
        }
    }

    private List<Object> expectedValues(Comparison comparison) {
        List<Object> values = expectedValues.get(comparison);
        if (values == null) {
            values = new ArrayList<>(comparison.getValues().size());
            for (Object value : comparison.getValues()) {
                if (!(value instanceof String)) {
                    values.add(value);
                } else if (comparison.getOperator() == PredicateNode.Operator.MATCHES) {
                    // a regex keeps its escapes, and the case is handled by a flag
                    values.add(comparison.isDiacriticInsensitive()
                            ? foldRegexDiacritics((String) value) : value);
                } else {
                    values.add(fold((String) value, comparison));
                }
            }
            expectedValues.put(comparison, values);
        }
        return values;
    }

    private static boolean matchesOrder(PredicateNode.Operator operator, int order) {
        switch (operator) {
            case LT:
                return order < 0;
            case LE:
                return order <= 0;
            case GT:
                return order > 0;
            default:
                return order >= 0;
        }
    }

    /** The sign of {@code actual - expected}, or null if they cannot be ordered. */
    @Nullable
    private static Integer compareOrder(@Nullable Object actual, @Nullable Object expected) {
        if (actual instanceof Number && expected instanceof Number) {
            double left = ((Number) actual).doubleValue();
            double right = ((Number) expected).doubleValue();
            return Double.isNaN(left) || Double.isNaN(right) ? null : Double.compare(left, right);
        }
        // a styled text is a CharSequence, but not a String
        if (actual instanceof CharSequence && expected instanceof String) {
            return actual.toString().compareTo((String) expected);
        }
        return null;
    }

    private static boolean isEqual(@Nullable Object actual, @Nullable Object expected,
                                   Comparison comparison) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual instanceof Number && expected instanceof Number) {
            return ((Number) actual).doubleValue() == ((Number) expected).doubleValue();
        }
        if (actual instanceof Boolean || expected instanceof Boolean) {
            return actual.equals(expected);
        }
        if (expected instanceof String) {
            return fold(actual.toString(), comparison).equals(expected);
        }
        return false;
    }

    private boolean matchesString(Comparison comparison, @Nullable Object actual, String expected) {
        if (actual == null) {
            return false;
        }
        String text = fold(actual.toString(), comparison);
        switch (comparison.getOperator()) {
            case BEGINSWITH:
                return text.startsWith(expected);
            case ENDSWITH:
                return text.endsWith(expected);
            case CONTAINS:
                return comparison.getAttribute() == Attribute.ACTIONS
                        && comparison.getBoundsField() == null
                        ? hasEntry(text, expected)
                        : text.contains(expected);
            default:
                return pattern(comparison, expected).matcher(text).matches();
        }
    }

    /** The actions are a comma separated list, so a whole entry has to match. */
    private static boolean hasEntry(String list, String entry) {
        for (String item : list.split(",")) {
            if (item.trim().equals(entry)) {
                return true;
            }
        }
        return false;
    }

    private Pattern pattern(Comparison comparison, String source) {
        Pattern pattern = patterns.get(comparison);
        if (pattern == null) {
            boolean like = comparison.getOperator() == PredicateNode.Operator.LIKE;
            // the LIKE glob is already folded, a regex gets flags instead
            int flags = like ? Pattern.DOTALL : 0;
            if (!like && comparison.isCaseInsensitive()) {
                flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            }
            pattern = Pattern.compile(like ? GlobMatcher.globToRegex(source) : source, flags);
            patterns.put(comparison, pattern);
        }
        return pattern;
    }

    /**
     * Removes the diacritics from the literals of a regex, like it is done for the text. An escaped
     * letter with a diacritic (a literal one) becomes a plain letter, since a backslash before an
     * ASCII letter would have another meaning.
     */
    private static String foldRegexDiacritics(String regex) {
        StringBuilder result = new StringBuilder(regex.length());
        int i = 0;
        while (i < regex.length()) {
            int cp = regex.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\\' && i < regex.length()) {
                int escaped = regex.codePointAt(i);
                i += Character.charCount(escaped);
                String folded = removeDiacritics(new String(Character.toChars(escaped)));
                if (folded.equals(new String(Character.toChars(escaped)))) {
                    result.append('\\').appendCodePoint(escaped);
                } else {
                    result.append(folded);
                }
            } else {
                result.append(removeDiacritics(new String(Character.toChars(cp))));
            }
        }
        return result.toString();
    }

    private static String removeDiacritics(String value) {
        return DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
    }

    private static String fold(String value, Comparison comparison) {
        String result = value;
        if (comparison.isDiacriticInsensitive()) {
            result = removeDiacritics(result);
        }
        if (comparison.isCaseInsensitive()
                && comparison.getOperator() != PredicateNode.Operator.MATCHES) {
            result = result.toLowerCase(Locale.ROOT);
        }
        return result;
    }
}
