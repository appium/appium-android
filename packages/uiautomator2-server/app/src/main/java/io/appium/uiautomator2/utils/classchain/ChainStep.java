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
import java.util.Collections;
import java.util.List;

import io.appium.uiautomator2.utils.predicate.PredicateNode;

/**
 * One immutable step of a class chain, e.g. {@code android.widget.TextView[`text == 'A'`][2]}.
 */
public final class ChainStep {

    /** {@code SELF} filters the candidates, {@code DESCENDANT} keeps those having a matching descendant */
    public enum Scope {
        SELF('`'), DESCENDANT('$');

        private final char marker;

        Scope(char marker) {
            this.marker = marker;
        }

        public char getMarker() {
            return marker;
        }
    }

    public static final class Filter {
        private final Scope scope;
        private final PredicateNode predicate;

        Filter(Scope scope, PredicateNode predicate) {
            this.scope = scope;
            this.predicate = predicate;
        }

        public Scope getScope() {
            return scope;
        }

        public PredicateNode getPredicate() {
            return predicate;
        }

        @Override
        public String toString() {
            String marker = String.valueOf(scope.getMarker());
            return "[" + marker + predicate.toString().replace(marker, marker + marker) + marker + "]";
        }
    }

    private final boolean descendant;
    @Nullable
    private final String className;
    private final List<Filter> filters;
    @Nullable
    private final Integer position;

    ChainStep(boolean descendant, @Nullable String className, List<Filter> filters,
              @Nullable Integer position) {
        this.descendant = descendant;
        this.className = className;
        this.filters = Collections.unmodifiableList(new ArrayList<>(filters));
        this.position = position;
    }

    /** True for any depth below the previous step (or the root), false for direct children */
    public boolean isDescendant() {
        return descendant;
    }

    /** The exact class name, or null for {@code *} */
    @Nullable
    public String getClassName() {
        return className;
    }

    /** In source order */
    public List<Filter> getFilters() {
        return filters;
    }

    /** 1-based among the matches of the class name and filters; negative counts from the end; never 0 */
    @Nullable
    public Integer getPosition() {
        return position;
    }

    /** The step without its axis ({@code **}{@code /}), which {@link ClassChain} adds */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(className == null ? "*" : className);
        for (Filter filter : filters) {
            sb.append(filter);
        }
        if (position != null) {
            sb.append('[').append(position).append(']');
        }
        return sb.toString();
    }
}
