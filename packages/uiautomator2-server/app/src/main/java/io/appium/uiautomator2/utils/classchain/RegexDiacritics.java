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

/**
 * Removes the diacritics from the literals of a regex, like it is done for the text it is matched
 * against. Escapes, classes and ranges stay valid.
 */
final class RegexDiacritics {

    private RegexDiacritics() {
    }

    /** A literal (code point) or another construct (literal is -1) with its end in the source. */
    private static final class Atom {
        private final int start;
        private final int end;
        private final int literal;
        private final String folded;

        private Atom(int start, int end, int literal) {
            this.start = start;
            this.end = end;
            this.literal = literal;
            String plain = literal < 0 ? null : new String(Character.toChars(literal));
            String result = plain == null ? null : PredicateEvaluator.removeDiacritics(plain);
            // a lone combining mark folds to nothing, it can only be kept as it is
            this.folded = result == null || result.isEmpty() || result.equals(plain) ? null : result;
        }

        private boolean isChanged() {
            return folded != null;
        }

        private int foldedCodePoint() {
            return isChanged() ? folded.codePointAt(0) : literal;
        }
    }

    static String fold(String regex) {
        StringBuilder result = new StringBuilder(regex.length());
        int classDepth = 0;
        int i = 0;
        while (i < regex.length()) {
            if (regex.startsWith("\\Q", i)) {
                // a quoted text is all literal, up to \E that is copied as an escape
                int close = regex.indexOf("\\E", i + 2);
                int end = close < 0 ? regex.length() : close;
                result.append("\\Q")
                        .append(PredicateEvaluator.removeDiacritics(regex.substring(i + 2, end)));
                i = end;
                continue;
            }
            char c = regex.charAt(i);
            boolean isBracket = c == '[' || c == ']';
            if (c == '[') {
                classDepth++;
            } else if (c == ']' && classDepth > 0) {
                classDepth--;
            }
            Atom atom = readAtom(regex, i);
            i = atom.end;
            if (classDepth > 0 && !isBracket && atom.literal >= 0 && isRange(regex, atom.end)) {
                Atom high = readAtom(regex, atom.end + 1);
                if (high.literal >= 0) {
                    appendRange(regex, atom, high, result);
                    i = high.end;
                    continue;
                }
            }
            result.append(emit(regex, atom));
        }
        return result.toString();
    }

    private static boolean isRange(String regex, int dash) {
        return dash + 1 < regex.length() && regex.charAt(dash) == '-'
                && regex.charAt(dash + 1) != ']' && regex.charAt(dash + 1) != '[';
    }

    private static String emit(String regex, Atom atom) {
        return atom.isChanged() ? atom.folded : regex.substring(atom.start, atom.end);
    }

    private static void appendRange(String regex, Atom low, Atom high, StringBuilder result) {
        if (!low.isChanged() && !high.isChanged()) {
            result.append(regex, low.start, high.end);
        } else if (low.foldedCodePoint() <= high.foldedCodePoint()) {
            result.append(emit(regex, low)).append('-').append(emit(regex, high));
        } else {
            // the folded ends are no range anymore, so the range stays and the ends are added to it
            result.append(regex, low.start, high.end);
            result.append(low.isChanged() ? low.folded : "");
            result.append(high.isChanged() ? high.folded : "");
        }
    }

    private static Atom readAtom(String regex, int start) {
        int cp = regex.codePointAt(start);
        int next = start + Character.charCount(cp);
        if (cp != '\\' || next >= regex.length()) {
            return new Atom(start, next, cp);
        }
        int escaped = regex.codePointAt(next);
        int end = next + Character.charCount(escaped);
        if (escaped == 'u') {
            return readUnicodeEscape(regex, start, end);
        }
        if (escaped == 'x') {
            return readHexEscape(regex, start, end);
        }
        boolean isSpecial = escaped < 128 && Character.isLetterOrDigit(escaped);
        // an escaped letter with a diacritic is a literal one, like the unescaped letter
        return new Atom(start, end, isSpecial ? -1 : escaped);
    }

    /** {@code \}{@code uXXXX}, a surrogate pair is two of them. */
    private static Atom readUnicodeEscape(String regex, int start, int digits) {
        int value = hex(regex, digits, digits + 4);
        if (value < 0) {
            return new Atom(start, digits, -1);
        }
        int end = digits + 4;
        if (Character.isHighSurrogate((char) value) && regex.startsWith("\\u", end)) {
            int low = hex(regex, end + 2, end + 6);
            if (low >= 0 && Character.isLowSurrogate((char) low)) {
                return new Atom(start, end + 6,
                        Character.toCodePoint((char) value, (char) low));
            }
        }
        return new Atom(start, end, value);
    }

    /** {@code \}{@code xhh} or {@code \}{@code x{h...h}}. */
    private static Atom readHexEscape(String regex, int start, int digits) {
        if (digits < regex.length() && regex.charAt(digits) == '{') {
            int close = regex.indexOf('}', digits);
            int value = close < 0 ? -1 : hex(regex, digits + 1, close);
            return value >= 0 && Character.isValidCodePoint(value)
                    ? new Atom(start, close + 1, value)
                    : new Atom(start, digits, -1);
        }
        int value = hex(regex, digits, digits + 2);
        return value >= 0
                ? new Atom(start, digits + 2, value)
                : new Atom(start, digits, -1);
    }

    /** @return the value of the hex digits in the range, or -1 if they are not (only) hex digits */
    private static int hex(String regex, int from, int to) {
        if (from >= to || to > regex.length() || to - from > 8) {
            return -1;
        }
        long value = 0;
        for (int i = from; i < to; i++) {
            char c = regex.charAt(i);
            int digit = c < 128 ? Character.digit(c, 16) : -1;
            if (digit < 0) {
                return -1;
            }
            value = value * 16 + digit;
        }
        return value > Integer.MAX_VALUE ? -1 : (int) value;
    }
}
