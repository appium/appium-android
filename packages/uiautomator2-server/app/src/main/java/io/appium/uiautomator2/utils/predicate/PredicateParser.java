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

package io.appium.uiautomator2.utils.predicate;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.appium.uiautomator2.common.exceptions.PredicateSyntaxException;
import io.appium.uiautomator2.utils.Attribute;
import io.appium.uiautomator2.utils.predicate.PredicateNode.BoundsField;
import io.appium.uiautomator2.utils.predicate.PredicateNode.Comparison;
import io.appium.uiautomator2.utils.predicate.PredicateNode.Operator;

/**
 * Recursive descent parser for an NSPredicate subset (NOT &gt; AND &gt; OR). Anything outside
 * the subset throws {@link PredicateSyntaxException}.
 */
public final class PredicateParser {
    private static final int MAX_DEPTH = 64;

    // Not scalar or too expensive to read for filtering
    private static final Set<Attribute> EXCLUDED_ATTRIBUTES = EnumSet.of(
            Attribute.BOUNDS, Attribute.EXTRAS, Attribute.ACTIONS, Attribute.CONTENT_SIZE,
            Attribute.COLLECTION_INFO, Attribute.COLLECTION_ITEM_INFO);
    private static final Set<String> UNSUPPORTED_KEYWORDS = new HashSet<>(Arrays.asList(
            "SELF", "ANY", "ALL", "SOME", "NONE", "SUBQUERY", "FUNCTION", "CAST", "TERNARY"));
    // Longer symbols first, the first prefix match wins
    private static final Map<String, Operator> SYMBOL_OPERATORS = buildSymbolOperators();
    private static final Map<String, Attribute> ATTRIBUTES = buildAttributeMap();
    private static final List<String> ATTRIBUTE_NAMES = buildAttributeNames();
    private static final Map<String, Operator> WORD_OPERATORS = buildWordOperators();

    private static final String BOUNDS_KEY = "bounds";
    private static final String BOUNDS_FIELD_KEYS = buildBoundsFieldKeys();

    private static final class Key {
        final Attribute attribute;
        @Nullable
        final BoundsField boundsField;

        Key(Attribute attribute, @Nullable BoundsField boundsField) {
            this.attribute = attribute;
            this.boundsField = boundsField;
        }
    }

    private final String source;
    private int pos;
    private int depth;

    private PredicateParser(String source) {
        this.source = source;
    }

    public static PredicateNode parse(String predicate) {
        if (predicate == null || predicate.trim().isEmpty()) {
            throw new PredicateSyntaxException(String.valueOf(predicate),
                    "The predicate must not be empty", 0);
        }
        PredicateParser parser = new PredicateParser(predicate);
        PredicateNode result = parser.parseOr();
        parser.skipWhitespace();
        if (parser.pos < predicate.length()) {
            throw parser.error(String.format("Unexpected '%s'", parser.source.charAt(parser.pos)));
        }
        return result;
    }

    /** Names accepted as attribute keys, sorted */
    public static List<String> supportedAttributeNames() {
        return ATTRIBUTE_NAMES;
    }

    /** The attribute name that this parser accepts, used for the canonical form */
    static String keyName(Attribute attribute) {
        for (String alias : attribute.getAliases()) {
            if (isIdentifier(alias)) {
                return alias;
            }
        }
        return attribute.getName();
    }

    private static List<String> buildAttributeNames() {
        List<String> names = new ArrayList<>(ATTRIBUTES.keySet());
        Collections.sort(names);
        return Collections.unmodifiableList(names);
    }

    private static Map<String, Operator> buildWordOperators() {
        Map<String, Operator> map = new HashMap<>();
        for (Operator operator : new Operator[]{Operator.BETWEEN, Operator.BEGINSWITH,
                Operator.ENDSWITH, Operator.CONTAINS, Operator.LIKE, Operator.MATCHES,
                Operator.IN}) {
            map.put(operator.getSymbol(), operator);
        }
        return map;
    }

    private static String buildBoundsFieldKeys() {
        List<String> keys = new ArrayList<>();
        for (BoundsField field : BoundsField.values()) {
            keys.add(BOUNDS_KEY + '.' + field.getName());
        }
        return String.join(", ", keys);
    }

    private static Map<String, Operator> buildSymbolOperators() {
        Map<String, Operator> map = new LinkedHashMap<>();
        map.put("==", Operator.EQ);
        map.put("=>", Operator.GE);
        map.put("=<", Operator.LE);
        map.put("=", Operator.EQ);
        map.put("!=", Operator.NE);
        map.put("<>", Operator.NE);
        map.put("<=", Operator.LE);
        map.put(">=", Operator.GE);
        map.put("<", Operator.LT);
        map.put(">", Operator.GT);
        return map;
    }

    private static Map<String, Attribute> buildAttributeMap() {
        Map<String, Attribute> map = new LinkedHashMap<>();
        for (Attribute attribute : Attribute.values()) {
            if (EXCLUDED_ATTRIBUTES.contains(attribute)) {
                continue;
            }
            for (String alias : attribute.getAliases()) {
                if (isIdentifier(alias)) {
                    map.put(alias, attribute);
                }
            }
        }
        return map;
    }

    // ---- grammar ----

    private PredicateNode parseOr() {
        enter();
        List<PredicateNode> operands = new ArrayList<>();
        operands.add(parseAnd());
        while (consumeKeyword("OR") || consumeSymbol("||")) {
            operands.add(parseAnd());
        }
        leave();
        return operands.size() == 1 ? operands.get(0)
                : new PredicateNode.Logical(false, operands);
    }

    private PredicateNode parseAnd() {
        List<PredicateNode> operands = new ArrayList<>();
        operands.add(parseNot());
        while (consumeKeyword("AND") || consumeSymbol("&&")) {
            operands.add(parseNot());
        }
        return operands.size() == 1 ? operands.get(0)
                : new PredicateNode.Logical(true, operands);
    }

    private PredicateNode parseNot() {
        skipWhitespace();
        boolean negated = consumeKeyword("NOT")
                || (peek() == '!' && peek(1) != '=' && consumeSymbol("!"));
        if (!negated) {
            return parsePrimary();
        }
        enter();
        PredicateNode operand = parseNot();
        leave();
        return new PredicateNode.Not(operand);
    }

    private PredicateNode parsePrimary() {
        skipWhitespace();
        if (pos >= source.length()) {
            throw error("Unexpected end of the predicate");
        }
        if (consumeSymbol("(")) {
            PredicateNode inner = parseOr();
            skipWhitespace();
            if (!consumeSymbol(")")) {
                throw error("Expected ')'");
            }
            return inner;
        }
        if (consumeKeyword("TRUEPREDICATE")) {
            return new PredicateNode.Constant(true);
        }
        if (consumeKeyword("FALSEPREDICATE")) {
            return new PredicateNode.Constant(false);
        }
        return parseComparison();
    }

    private PredicateNode parseComparison() {
        Key key = parseKey();
        skipWhitespace();
        Operator operator = parseOperator();
        boolean caseInsensitive = false;
        boolean diacriticInsensitive = false;
        if (operator.acceptsModifiers()) {
            skipWhitespace();
        }
        if (peek() == '[') {
            if (!operator.acceptsModifiers()) {
                throw error(String.format("Modifiers are not supported for '%s'",
                        operator.getSymbol()));
            }
            int modifiersPos = pos++;
            int close = source.indexOf(']', pos);
            if (close < 0) {
                throw error("Unterminated modifiers, expected ']'", modifiersPos);
            }
            String modifiers = source.substring(pos, close).toLowerCase(Locale.ROOT);
            for (int i = 0; i < modifiers.length(); i++) {
                char c = modifiers.charAt(i);
                boolean duplicate = c == 'c' ? caseInsensitive : diacriticInsensitive;
                if ((c != 'c' && c != 'd') || duplicate) {
                    throw error(String.format("Invalid modifiers '[%s]'. Only [c], [d] and [cd] "
                            + "are supported", source.substring(pos, close)), modifiersPos);
                }
                caseInsensitive |= c == 'c';
                diacriticInsensitive |= c == 'd';
            }
            if (modifiers.isEmpty()) {
                throw error("Empty modifiers '[]'", modifiersPos);
            }
            pos = close + 1;
        }

        List<Object> values;
        int valuePos = skipWhitespace();
        if (operator == Operator.IN || operator == Operator.BETWEEN) {
            values = parseAggregate();
        } else {
            values = Collections.singletonList(parseLiteral());
        }
        validate(operator, values, valuePos);
        validateTypes(key, operator, values, valuePos);
        return new Comparison(key.attribute, key.boundsField, operator, caseInsensitive,
                diacriticInsensitive, values);
    }

    private Key parseKey() {
        int start = pos;
        char c = peek();
        if (!isIdentifierStart(c)) {
            if (c == '$') {
                throw error("Variable substitution is not supported");
            }
            if (c == '@') {
                throw error("Function expressions are not supported");
            }
            if (c == '\'' || c == '"' || isAsciiDigit(c)) {
                throw error("The left operand must be an attribute name");
            }
            throw error(String.format("Unexpected '%s'", c));
        }
        String key = readWord();
        if (peek() == '-' && isIdentifierStart(peek(1))) {
            int end = pos;
            while (end < source.length()
                    && (isIdentifierPart(source.charAt(end)) || source.charAt(end) == '-')) {
                end++;
            }
            Attribute hyphenated = Attribute.fromString(source.substring(start, end));
            if (hyphenated != null && ATTRIBUTES.get(keyName(hyphenated)) == hyphenated) {
                throw error(String.format("Hyphenated names are not supported, use '%s' instead "
                        + "of '%s'", keyName(hyphenated), source.substring(start, end)), start);
            }
        }
        if (key.equals(BOUNDS_KEY)) {
            return parseBoundsKey(start);
        }
        if (peek() == '.') {
            throw error("Key paths are not supported", start);
        }
        if (peek() == '(') {
            throw error("Function expressions are not supported", start);
        }
        if (UNSUPPORTED_KEYWORDS.contains(key.toUpperCase(Locale.ROOT))) {
            throw error(String.format("'%s' is not supported", key), start);
        }
        Attribute attribute = ATTRIBUTES.get(key);
        if (attribute == null) {
            throw error(unknownAttributeMessage(key), start);
        }
        return new Key(attribute, null);
    }

    // 'bounds' is a string, so only its numeric parts can be compared
    private Key parseBoundsKey(int start) {
        if (peek() != '.') {
            throw error(String.format("'bounds' cannot be compared directly, use one of: %s",
                    BOUNDS_FIELD_KEYS), start);
        }
        pos++;
        String field = isIdentifierStart(peek()) ? readWord() : "";
        for (BoundsField candidate : BoundsField.values()) {
            if (candidate.getName().equals(field)) {
                return new Key(Attribute.BOUNDS, candidate);
            }
        }
        throw error(String.format("Unknown bounds field '%s'. Supported: %s", field,
                BOUNDS_FIELD_KEYS), start);
    }

    private static String unknownAttributeMessage(String key) {
        List<String> similar = new ArrayList<>();
        for (String name : ATTRIBUTE_NAMES) {
            if (editDistance(name.toLowerCase(Locale.ROOT), key.toLowerCase(Locale.ROOT)) <= 2) {
                similar.add(name);
            }
        }
        return String.format("Unknown attribute '%s'. %s", key, similar.isEmpty()
                ? "Supported attributes: " + ATTRIBUTE_NAMES
                : "Did you mean: " + String.join(", ", similar) + "?");
    }

    private static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = Math.min(Math.min(cur[j - 1], prev[j]) + 1,
                        prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            prev = cur;
        }
        return prev[b.length()];
    }

    private Operator parseOperator() {
        int start = pos;
        if (pos >= source.length()) {
            throw error("Expected a comparison operator");
        }
        for (Map.Entry<String, Operator> entry : SYMBOL_OPERATORS.entrySet()) {
            if (source.startsWith(entry.getKey(), pos)) {
                pos += entry.getKey().length();
                return entry.getValue();
            }
        }
        if (isIdentifierStart(peek())) {
            String word = readWord().toUpperCase(Locale.ROOT);
            if (word.equals("NOT")) {
                throw error("'NOT' is only supported as a prefix, e.g. NOT (text CONTAINS 'a')",
                        start);
            }
            Operator operator = WORD_OPERATORS.get(word);
            if (operator != null) {
                return operator;
            }
            throw error(String.format("Unknown or unsupported operator '%s'", word), start);
        }
        throw error(String.format("Expected a comparison operator, got '%s'", peek()));
    }

    private List<Object> parseAggregate() {
        if (!consumeSymbol("{")) {
            throw error("Expected '{' to start a list of values, e.g. {'a', 'b'}");
        }
        List<Object> values = new ArrayList<>();
        skipWhitespace();
        if (consumeSymbol("}")) {
            return values;
        }
        do {
            skipWhitespace();
            values.add(parseLiteral());
            skipWhitespace();
        } while (consumeSymbol(","));
        if (!consumeSymbol("}")) {
            throw error("Expected ',' or '}'");
        }
        return values;
    }

    // ---- literals ----

    private Object parseLiteral() {
        skipWhitespace();
        char c = peek();
        if (c == '\'' || c == '"') {
            return parseString();
        }
        if (isAsciiDigit(c) || ((c == '-' || c == '+') && isAsciiDigit(peek(1)))) {
            return parseNumber();
        }
        if (isIdentifierStart(c)) {
            int start = pos;
            String word = readWord();
            switch (word.toUpperCase(Locale.ROOT)) {
                case "TRUE":
                case "YES":
                    return Boolean.TRUE;
                case "FALSE":
                case "NO":
                    return Boolean.FALSE;
                case "NIL":
                case "NULL":
                    return null;
                default:
                    if (ATTRIBUTES.containsKey(word)) {
                        throw error("Comparing attributes with each other is not supported",
                                start);
                    }
                    throw error(String.format("Unexpected '%s', expected a literal", word), start);
            }
        }
        if (c == '$') {
            throw error("Variable substitution is not supported");
        }
        if (c == '@') {
            throw error("Function expressions are not supported");
        }
        if (pos >= source.length()) {
            throw error("Unexpected end of the predicate, expected a literal");
        }
        throw error(String.format("Unexpected '%s', expected a literal", c));
    }

    private String parseString() {
        int start = pos;
        char quote = source.charAt(pos++);
        StringBuilder sb = new StringBuilder();
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == quote) {
                pos++;
                return sb.toString();
            }
            if (c == '\\') {
                if (pos + 1 >= source.length()) {
                    break;
                }
                char next = source.charAt(pos + 1);
                switch (next) {
                    case '\\':
                    case '\'':
                    case '"':
                        sb.append(next);
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'x':
                    case 'X':
                    case 'u':
                    case 'U':
                        int digits = Character.toLowerCase(next) == 'x' ? 2 : 4;
                        sb.append(parseHexEscape(pos + 2, digits));
                        pos += digits;
                        break;
                    default:
                        throw error(String.format("Unsupported escape sequence '\\%s'. "
                                + "Use '\\\\' for a literal backslash", next), pos);
                }
                pos += 2;
                continue;
            }
            sb.append(c);
            pos++;
        }
        throw error("Unterminated string literal", start);
    }

    private char parseHexEscape(int from, int digits) {
        String hex = source.substring(Math.min(from, source.length()),
                Math.min(from + digits, source.length()));
        if (hex.length() != digits || !hex.matches("[0-9a-fA-F]+")) {
            throw error(String.format("Expected %d hex digits after '\\%s'", digits,
                    source.charAt(from - 1)), pos);
        }
        return (char) Integer.parseInt(hex, 16);
    }

    private Object parseNumber() {
        int start = pos;
        if (peek() == '-' || peek() == '+') {
            pos++;
        }
        readDigits();
        boolean decimal = false;
        if (peek() == '.' && isAsciiDigit(peek(1))) {
            decimal = true;
            pos++;
            readDigits();
        }
        if ((peek() == 'e' || peek() == 'E')
                && (isAsciiDigit(peek(1))
                || ((peek(1) == '-' || peek(1) == '+') && isAsciiDigit(peek(2))))) {
            decimal = true;
            pos += 2;
            readDigits();
        }
        if (isIdentifierPart(peek())) {
            throw error("Malformed number", start);
        }
        String text = source.substring(start, pos);
        try {
            if (!decimal) {
                return Long.valueOf(text);
            }
            double value = Double.parseDouble(text);
            if (Double.isInfinite(value)) {
                throw new NumberFormatException(text);
            }
            return value;
        } catch (NumberFormatException e) {
            throw error(String.format("Number '%s' is out of range", text), start);
        }
    }

    private void readDigits() {
        while (isAsciiDigit(peek())) {
            pos++;
        }
    }

    // ---- semantic checks ----

    private void validate(Operator operator, List<Object> values, int valuePos) {
        switch (operator) {
            case BEGINSWITH:
            case ENDSWITH:
            case CONTAINS:
            case LIKE:
            case MATCHES:
                if (!(values.get(0) instanceof String)) {
                    throw error(String.format("'%s' requires a string literal",
                            operator.getSymbol()), valuePos);
                }
                if (operator == Operator.MATCHES) {
                    try {
                        Pattern.compile((String) values.get(0));
                    } catch (PatternSyntaxException e) {
                        throw error(String.format("Invalid regular expression: %s",
                                e.getDescription()), valuePos);
                    }
                }
                break;
            case LT:
            case LE:
            case GT:
            case GE:
                if (values.get(0) == null || values.get(0) instanceof Boolean) {
                    throw error(String.format("'%s' requires a number or a string literal",
                            operator.getSymbol()), valuePos);
                }
                break;
            case BETWEEN:
                if (values.size() != 2) {
                    throw error("'BETWEEN' requires exactly two values, e.g. {1, 5}", valuePos);
                }
                boolean numbers = values.get(0) instanceof Number && values.get(1) instanceof Number;
                boolean strings = values.get(0) instanceof String && values.get(1) instanceof String;
                if (!numbers && !strings) {
                    throw error("'BETWEEN' requires two numbers or two strings", valuePos);
                }
                break;
            default:
                break;
        }
    }

    private void validateTypes(Key key, Operator operator, List<Object> values, int valuePos) {
        String name = key.boundsField == null ? keyName(key.attribute)
                : BOUNDS_KEY + '.' + key.boundsField.getName();
        Attribute.Type type = key.boundsField == null ? key.attribute.getType()
                : Attribute.Type.NUMBER;
        boolean isString = type == Attribute.Type.STRING;
        boolean isNumber = type == Attribute.Type.NUMBER;
        boolean isBoolean = type == Attribute.Type.BOOLEAN;
        boolean stringOperator = operator == Operator.BEGINSWITH || operator == Operator.ENDSWITH
                || operator == Operator.CONTAINS || operator == Operator.LIKE
                || operator == Operator.MATCHES;
        boolean relational = operator == Operator.LT || operator == Operator.LE
                || operator == Operator.GT || operator == Operator.GE
                || operator == Operator.BETWEEN;
        if ((isBoolean && (stringOperator || relational)) || (isNumber && stringOperator)) {
            throw error(String.format("'%s' cannot be applied to the %s attribute '%s'",
                    operator.getSymbol(), isBoolean ? "boolean" : "numeric", name), valuePos);
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            boolean valid = isString ? value instanceof String
                    : isNumber ? value instanceof Number
                    : value instanceof Boolean
                            || (value instanceof Long && ((Long) value == 0 || (Long) value == 1));
            if (!valid) {
                throw error(String.format("The %s attribute '%s' cannot be compared to %s",
                        isString ? "string" : isNumber ? "numeric" : "boolean", name,
                        isBoolean ? "a value other than true, false, YES, NO, 1 or 0"
                                : isString ? "a non-string value" : "a non-numeric value"),
                        valuePos);
            }
        }
    }

    // ---- lexical helpers ----

    private void enter() {
        if (++depth > MAX_DEPTH) {
            throw error("The predicate is nested too deeply");
        }
    }

    private void leave() {
        depth--;
    }

    private char peek() {
        return peek(0);
    }

    private char peek(int offset) {
        int index = pos + offset;
        return index < source.length() ? source.charAt(index) : '\0';
    }

    /** Skips whitespace and returns the new position */
    private int skipWhitespace() {
        while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    private boolean consumeSymbol(String symbol) {
        skipWhitespace();
        if (source.startsWith(symbol, pos)) {
            pos += symbol.length();
            return true;
        }
        return false;
    }

    private boolean consumeKeyword(String keyword) {
        skipWhitespace();
        int end = pos + keyword.length();
        if (source.regionMatches(true, pos, keyword, 0, keyword.length())
                && !isIdentifierPart(end < source.length() ? source.charAt(end) : '\0')) {
            pos = end;
            return true;
        }
        return false;
    }

    private String readWord() {
        int start = pos;
        while (isIdentifierPart(peek())) {
            pos++;
        }
        return source.substring(start, pos);
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || (c >= '0' && c <= '9');
    }

    private static boolean isIdentifier(String value) {
        if (value.isEmpty() || !isIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!isIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private PredicateSyntaxException error(String reason) {
        return error(reason, pos);
    }

    private PredicateSyntaxException error(String reason, int position) {
        return new PredicateSyntaxException(source, reason, position);
    }
}
