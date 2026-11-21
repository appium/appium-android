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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.appium.uiautomator2.utils.Attribute;

/**
 * Immutable AST of the supported NSPredicate subset. Literal values are
 * {@code String}, {@code Long}, {@code Double}, {@code Boolean} or {@code null}.
 */
public abstract class PredicateNode {

    public enum Operator {
        EQ("==", true), NE("!=", true),
        LT("<", false), LE("<=", false), GT(">", false), GE(">=", false),
        BETWEEN("BETWEEN", false),
        BEGINSWITH("BEGINSWITH", true), ENDSWITH("ENDSWITH", true), CONTAINS("CONTAINS", true),
        LIKE("LIKE", true), MATCHES("MATCHES", true),
        IN("IN", true);

        private final String symbol;
        private final boolean acceptsModifiers;

        Operator(String symbol, boolean acceptsModifiers) {
            this.symbol = symbol;
            this.acceptsModifiers = acceptsModifiers;
        }

        public String getSymbol() {
            return symbol;
        }

        public boolean acceptsModifiers() {
            return acceptsModifiers;
        }
    }

    /** Numeric parts of the {@code bounds} attribute, written as {@code bounds.left} etc. */
    public enum BoundsField {
        LEFT, TOP, RIGHT, BOTTOM;

        public String getName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** TRUEPREDICATE / FALSEPREDICATE */
    public static final class Constant extends PredicateNode {
        private final boolean value;

        public Constant(boolean value) {
            this.value = value;
        }

        public boolean getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value ? "TRUEPREDICATE" : "FALSEPREDICATE";
        }
    }

    public static final class Not extends PredicateNode {
        private final PredicateNode operand;

        public Not(PredicateNode operand) {
            this.operand = operand;
        }

        public PredicateNode getOperand() {
            return operand;
        }

        @Override
        public String toString() {
            return "NOT " + operand;
        }
    }

    /** N-ary AND/OR. Operands are kept in source order, so evaluation can short-circuit. */
    public static final class Logical extends PredicateNode {
        private final boolean isAnd;
        private final List<PredicateNode> operands;

        public Logical(boolean isAnd, List<PredicateNode> operands) {
            this.isAnd = isAnd;
            this.operands = Collections.unmodifiableList(new ArrayList<>(operands));
        }

        public boolean isAnd() {
            return isAnd;
        }

        public List<PredicateNode> getOperands() {
            return operands;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < operands.size(); i++) {
                if (i > 0) {
                    sb.append(isAnd ? " AND " : " OR ");
                }
                sb.append(operands.get(i));
            }
            return sb.append(')').toString();
        }
    }

    /**
     * {@code attribute OP value}. For BETWEEN and IN the values are the aggregate members,
     * otherwise there is exactly one value.
     */
    public static final class Comparison extends PredicateNode {
        private final Attribute attribute;
        private final BoundsField boundsField;
        private final Operator operator;
        private final boolean caseInsensitive;
        private final boolean diacriticInsensitive;
        private final List<Object> values;

        public Comparison(Attribute attribute, @Nullable BoundsField boundsField,
                          Operator operator, boolean caseInsensitive,
                          boolean diacriticInsensitive, List<Object> values) {
            this.attribute = attribute;
            this.boundsField = boundsField;
            this.operator = operator;
            this.caseInsensitive = caseInsensitive;
            this.diacriticInsensitive = diacriticInsensitive;
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
        }

        public Attribute getAttribute() {
            return attribute;
        }

        /** Non-null only for {@code bounds.<field>} keys, where the attribute is BOUNDS. */
        @Nullable
        public BoundsField getBoundsField() {
            return boundsField;
        }

        public Operator getOperator() {
            return operator;
        }

        public boolean isCaseInsensitive() {
            return caseInsensitive;
        }

        public boolean isDiacriticInsensitive() {
            return diacriticInsensitive;
        }

        public List<Object> getValues() {
            return values;
        }

        /** The single operand of non-aggregate operators. */
        public Object getValue() {
            return values.get(0);
        }

        @Override
        public String toString() {
            String key = boundsField == null
                    ? PredicateParser.keyName(attribute)
                    : PredicateParser.keyName(attribute) + '.' + boundsField.getName();
            StringBuilder sb = new StringBuilder(key)
                    .append(' ').append(operator.getSymbol());
            if (caseInsensitive || diacriticInsensitive) {
                sb.append('[').append(caseInsensitive ? "c" : "")
                        .append(diacriticInsensitive ? "d" : "").append(']');
            }
            sb.append(' ');
            if (operator == Operator.IN || operator == Operator.BETWEEN) {
                sb.append('{');
                for (int i = 0; i < values.size(); i++) {
                    sb.append(i > 0 ? ", " : "").append(formatLiteral(values.get(i)));
                }
                return sb.append('}').toString();
            }
            return sb.append(formatLiteral(getValue())).toString();
        }
    }

    private static String formatLiteral(Object value) {
        if (value == null) {
            return "nil";
        }
        if (value instanceof String) {
            return "'" + ((String) value).replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).toString().toUpperCase(Locale.ROOT);
        }
        return value.toString();
    }
}
