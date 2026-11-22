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

import java.util.ArrayList;
import java.util.List;

import io.appium.uiautomator2.common.exceptions.ClassChainSyntaxException;
import io.appium.uiautomator2.common.exceptions.PredicateSyntaxException;
import io.appium.uiautomator2.utils.classchain.ChainStep.Axis;
import io.appium.uiautomator2.utils.classchain.ChainStep.Filter;
import io.appium.uiautomator2.utils.classchain.ChainStep.Scope;
import io.appium.uiautomator2.utils.predicate.PredicateParser;

/**
 * Parser of class chain queries like WebDriverAgent's, with {@link PredicateParser} predicates.
 * Beyond WDA, a step after {@code /} can start with {@code ..} (parent), {@code ^} (ancestors),
 * {@code >} / {@code <} (following / preceding siblings) or {@code ~} (all siblings).
 */
public final class ClassChainParser {
    private static final String DESCENDANT_MARKER = "**/";

    private final String source;
    private int pos;

    private ClassChainParser(String source) {
        this.source = source;
    }

    public static ClassChain parse(String query) {
        if (query == null || query.isEmpty()) {
            throw new ClassChainSyntaxException(String.valueOf(query),
                    "The class chain must not be empty", 0);
        }
        return new ClassChainParser(query).parseChain();
    }

    private ClassChain parseChain() {
        List<ChainStep> steps = new ArrayList<>();
        boolean descendant = source.startsWith(DESCENDANT_MARKER);
        if (descendant) {
            pos = DESCENDANT_MARKER.length();
        }
        while (true) {
            steps.add(parseStep(descendant ? Axis.DESCENDANT : Axis.CHILD));
            if (pos >= source.length()) {
                return new ClassChain(steps);
            }
            pos++; // the '/' that parseStep stopped at
            descendant = source.startsWith(DESCENDANT_MARKER, pos);
            if (descendant) {
                pos += DESCENDANT_MARKER.length();
            }
        }
    }

    private ChainStep parseStep(Axis plainAxis) {
        Axis axis = parseAxis(plainAxis);
        String className = axis == Axis.PARENT ? null : parseType();
        List<Filter> filters = new ArrayList<>();
        Integer position = null;
        while (peek() == '[') {
            char next = peek(1);
            if (next == '`' || next == '$') {
                if (position != null) {
                    throw error("Predicates must be set before the position");
                }
                filters.add(parseFilter(next == '`' ? Scope.SELF : Scope.DESCENDANT));
            } else {
                if (position != null) {
                    throw error("The position can be set only once");
                }
                if (axis == Axis.PARENT) {
                    throw error("A parent step '..' has a single candidate, it takes no position");
                }
                position = parsePosition();
            }
        }
        if (pos < source.length() && peek() != '/') {
            throw error(String.format("Unexpected '%s'", peek()));
        }
        return new ChainStep(axis, className, filters, position);
    }

    private Axis parseAxis(Axis plainAxis) {
        Axis axis;
        switch (peek()) {
            case '.':
                axis = Axis.PARENT;
                break;
            case '^':
                axis = Axis.ANCESTOR;
                break;
            case '>':
                axis = Axis.FOLLOWING_SIBLING;
                break;
            case '<':
                axis = Axis.PRECEDING_SIBLING;
                break;
            case '~':
                axis = Axis.SIBLING;
                break;
            default:
                return plainAxis;
        }
        if (plainAxis == Axis.DESCENDANT) {
            throw error(String.format("'%s' cannot follow the descendant marker '**/'",
                    axis.getSymbol()));
        }
        if (axis == Axis.PARENT) {
            if (peek(1) != '.') {
                throw error("Expected '..' for the parent step");
            }
            pos += 2;
            if (peek() == '.' || peek() == '*' || isIdentifierStart(codePoint())) {
                throw error("A parent step '..' takes no class name, "
                        + "filter with [`className == ...`] instead");
            }
        } else {
            pos++;
        }
        return axis;
    }

    @Nullable
    private String parseType() {
        if (peek() == '*') {
            if (peek(1) == '*') {
                throw error("Unexpected '**', the descendant marker '**/' must be followed by "
                        + "a class name or '*'");
            }
            pos++;
            return null;
        }
        int start = pos;
        while (true) {
            if (!isIdentifierStart(codePoint())) {
                throw error(pos >= source.length() && pos > start
                        ? "The class name must not end with '.'"
                        : "Expected a class name or '*'");
            }
            do {
                pos += Character.charCount(codePoint());
            } while (isIdentifierPart(codePoint()));
            if (peek() != '.') {
                return source.substring(start, pos);
            }
            pos++;
        }
    }

    private Filter parseFilter(Scope scope) {
        char marker = scope.getMarker();
        int open = pos;
        pos += 2; // "[" and the marker
        int contentStart = pos;
        StringBuilder text = new StringBuilder();
        while (true) {
            if (pos >= source.length()) {
                throw error(String.format("Cannot find the closing '%s' of the predicate", marker),
                        open);
            }
            char c = source.charAt(pos);
            if (c == marker) {
                if (peek(1) == marker) {
                    text.append(c);
                    pos += 2;
                    continue;
                }
                pos++;
                break;
            }
            text.append(c);
            pos++;
        }
        if (peek() != ']') {
            throw error("Expected ']' after the predicate");
        }
        pos++;
        try {
            return new Filter(scope, PredicateParser.parse(text.toString()));
        } catch (PredicateSyntaxException e) {
            int at = sourceOffset(contentStart, marker, Math.min(e.getPosition(), text.length()));
            throw new ClassChainSyntaxException(
                    source, "Invalid predicate: " + e.getReason(), at, e);
        }
    }

    /** The query index of the {@code offset}-th predicate character, which skips doubled markers */
    private int sourceOffset(int from, char marker, int offset) {
        int index = from;
        for (int n = 0; n < offset && index < source.length(); n++) {
            boolean doubled = source.charAt(index) == marker
                    && index + 1 < source.length() && source.charAt(index + 1) == marker;
            index += doubled ? 2 : 1;
        }
        return index;
    }

    private int parsePosition() {
        pos++; // '['
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        int digitsStart = pos;
        while (peek() >= '0' && peek() <= '9') {
            pos++;
        }
        if (pos == digitsStart) {
            throw error("Expected a predicate or an integer position after '['", start);
        }
        int value;
        try {
            value = Integer.parseInt(source.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("The position is out of the integer range", start);
        }
        if (value == 0) {
            throw error("The position must be a non-zero integer, counted from 1", start);
        }
        if (peek() != ']') {
            throw error("Expected ']' after the position");
        }
        pos++;
        return value;
    }

    /** The code point at the current position, or -1 at the end */
    private int codePoint() {
        return pos < source.length() ? source.codePointAt(pos) : -1;
    }

    private static boolean isIdentifierStart(int cp) {
        return cp > 0 && Character.isJavaIdentifierStart(cp)
                && !Character.isIdentifierIgnorable(cp);
    }

    private static boolean isIdentifierPart(int cp) {
        return cp > 0 && Character.isJavaIdentifierPart(cp)
                && !Character.isIdentifierIgnorable(cp);
    }

    private char peek() {
        return peek(0);
    }

    private char peek(int offset) {
        int index = pos + offset;
        return index < source.length() ? source.charAt(index) : '\0';
    }

    private ClassChainSyntaxException error(String reason) {
        return error(reason, pos);
    }

    private ClassChainSyntaxException error(String reason, int position) {
        return new ClassChainSyntaxException(source, reason, position);
    }
}
