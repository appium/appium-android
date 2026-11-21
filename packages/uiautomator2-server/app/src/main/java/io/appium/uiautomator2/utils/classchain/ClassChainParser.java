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
import io.appium.uiautomator2.utils.classchain.ChainStep.Filter;
import io.appium.uiautomator2.utils.classchain.ChainStep.Scope;
import io.appium.uiautomator2.utils.predicate.PredicateParser;

/** Parser of class chain queries like WebDriverAgent's, with {@link PredicateParser} predicates. */
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
            steps.add(parseStep(descendant));
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

    private ChainStep parseStep(boolean descendant) {
        String className = parseType();
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
                position = parsePosition();
            }
        }
        if (pos < source.length() && peek() != '/') {
            throw error(String.format("Unexpected '%s'", peek()));
        }
        return new ChainStep(descendant, className, filters, position);
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
        StringBuilder text = new StringBuilder();
        // source index of each predicate character, so errors point into the query
        int[] sourceIndex = new int[source.length() + 1];
        while (true) {
            if (pos >= source.length()) {
                throw error(String.format("Cannot find the closing '%s' of the predicate", marker),
                        open);
            }
            char c = source.charAt(pos);
            if (c == marker) {
                if (peek(1) == marker) {
                    sourceIndex[text.length()] = pos;
                    text.append(c);
                    pos += 2;
                    continue;
                }
                sourceIndex[text.length()] = pos;
                pos++;
                break;
            }
            sourceIndex[text.length()] = pos;
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
            int at = sourceIndex[Math.min(e.getPosition(), text.length())];
            throw new ClassChainSyntaxException(
                    source, "Invalid predicate: " + e.getReason(), at, e);
        }
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
