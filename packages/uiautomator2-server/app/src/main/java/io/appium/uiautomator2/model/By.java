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

import io.appium.uiautomator2.utils.classchain.ClassChain;
import io.appium.uiautomator2.utils.classchain.ClassChainParser;
import io.appium.uiautomator2.utils.predicate.PredicateParser;

/**
 * Mechanism used to locate elements within a document. In order to create your own locating
 * mechanisms, it is possible to subclass this class and override the protected methods as
 * required.
 */
public abstract class By {

    /**
     * @param id The value of the "id" attribute to search for
     * @return a By which locates elements by the value of the "id" attribute.
     */
    public static By id(final String id) {
        if (id == null) {
            throw new IllegalArgumentException("Cannot find elements with a null id attribute.");
        }
        return new ById(id);
    }

    public static By accessibilityId(final String text) {
        if (text == null) {
            throw new IllegalArgumentException("Cannot find elements when text is null.");
        }
        return new ByAccessibilityId(text);
    }

    public static By xpath(String xpathExpression) {
        if (xpathExpression == null) {
            throw new IllegalArgumentException("Cannot find elements when xpath is null.");
        }
        return new ByXPath(xpathExpression);
    }

    public static By className(String className) {
        if (className == null) {
            throw new IllegalArgumentException("Cannot find elements when className is null.");
        }
        return new ByClass(className);
    }

    public static By androidUiAutomator(String expression) {
        if (expression == null) {
            throw new IllegalArgumentException(
                    "Cannot find elements when '-android uiautomator' is null.");
        }
        return new ByAndroidUiAutomator(expression);
    }

    public static By androidClassChain(String expression) {
        if (expression == null) {
            throw new IllegalArgumentException(
                    "Cannot find elements when '-android class chain' is null.");
        }
        return new ByAndroidClassChain(expression);
    }

    public static By androidPredicate(String expression) {
        if (expression == null) {
            throw new IllegalArgumentException(
                    "Cannot find elements when '-android predicate string' is null.");
        }
        return new ByAndroidPredicate(expression);
    }

    public static By text(final String text) {
        if (text == null) {
            throw new IllegalArgumentException("Cannot find elements when text is null.");
        }

        return new ByText(text);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        By by = (By) o;

        return toString().equals(by.toString());
    }

    public abstract String getElementLocator();

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        // A stub to prevent endless recursion in hashCode()
        return "[unknown locator]";
    }

    public static class ById extends By {
        private final String id;

        public ById(String id) {
            this.id = id;
        }

        @Override
        public String getElementLocator() {
            return id;
        }

        @Override
        public String toString() {
            return "By.id: " + id;
        }
    }

    public static class ByClass extends By {
        private final String clazz;

        public ByClass(String clazz) {
            this.clazz = clazz;
        }

        @Override
        public String getElementLocator() {
            return clazz;
        }

        @Override
        public String toString() {
            return "By.clazz: " + clazz;
        }
    }

    public static class ByAccessibilityId extends By {
        private final String accessibilityId;

        public ByAccessibilityId(String accessibilityId) {
            this.accessibilityId = accessibilityId;
        }

        @Override
        public String getElementLocator() {
            return accessibilityId;
        }

        @Override
        public String toString() {
            return "By.accessibilityId: " + accessibilityId;
        }
    }

    public static class ByXPath extends By {
        private final String xpathExpression;

        public ByXPath(String xpathExpression) {
            this.xpathExpression = xpathExpression;
        }

        @Override
        public String getElementLocator() {
            return xpathExpression;
        }

        @Override
        public String toString() {
            return "By.xpath: " + xpathExpression;
        }
    }

    public static class ByAndroidUiAutomator extends By {
        private final String expression;

        public ByAndroidUiAutomator(String expression) {
            this.expression = expression;
        }

        @Override
        public String getElementLocator() {
            return expression;
        }

        @Override
        public String toString() {
            return "By.AndroidUiAutomator: " + expression;
        }
    }

    /** A locator that is evaluated on the accessibility tree as a parsed class chain. */
    public abstract static class ByTreeQuery extends By {
        private final String expression;
        private final ClassChain chain;

        ByTreeQuery(String expression, ClassChain chain) {
            this.expression = expression;
            this.chain = chain;
        }

        public ClassChain getChain() {
            return chain;
        }

        @Override
        public String getElementLocator() {
            return expression;
        }
    }

    public static class ByAndroidClassChain extends ByTreeQuery {
        /** @throws io.appium.uiautomator2.common.exceptions.ClassChainSyntaxException if invalid */
        public ByAndroidClassChain(String expression) {
            super(expression, ClassChainParser.parse(expression));
        }

        @Override
        public String toString() {
            return "By.AndroidClassChain: " + getElementLocator();
        }
    }

    /** Matches all descendants of the search root that satisfy the predicate. */
    public static class ByAndroidPredicate extends ByTreeQuery {
        /** @throws io.appium.uiautomator2.common.exceptions.PredicateSyntaxException if invalid */
        public ByAndroidPredicate(String expression) {
            super(expression, ClassChain.ofPredicate(PredicateParser.parse(expression)));
        }

        @Override
        public String toString() {
            return "By.AndroidPredicate: " + getElementLocator();
        }
    }

    public static class ByText extends By {
        private final String text;

        public ByText(String text) {
            this.text = text;
        }

        @Override
        public String getElementLocator() {
            String textEscaped = text
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");

            return "new UiSelector().text(\"" + textEscaped + "\")";
        }

        @Override
        public String toString() {
            return "By.text: " + text;
        }
    }
}
