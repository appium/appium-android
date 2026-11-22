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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.appium.uiautomator2.utils.Attribute;
import io.appium.uiautomator2.utils.classchain.ChainStep.Filter;
import io.appium.uiautomator2.utils.predicate.PredicateNode.BoundsField;

/**
 * Runs a {@link ClassChain} over a tree. Each step is applied to all nodes that the previous
 * step returned (without duplicates, in order):
 * <ul>
 * <li>{@code CHILD} and {@code DESCENDANT} steps collect the candidates of all these nodes, keep
 * the ones matching the class and filters and then pick the position among all of them, like
 * WebDriverAgent. A step with a position therefore returns a single node.</li>
 * <li>Axis steps pick the position along the axis of each node separately.</li>
 * <li>A {@code [$...$]} filter keeps the candidates with a descendant matching the predicate.</li>
 * </ul>
 * The tree is walked lazily, so a lookup of a single node stops at the first match where the
 * chain allows it.
 */
public final class ClassChainEvaluator<N> {
    private static final int UNLIMITED = Integer.MAX_VALUE;

    private final TreeAccess<N> tree;
    private final PredicateEvaluator predicates = new PredicateEvaluator();

    public ClassChainEvaluator(TreeAccess<N> tree) {
        this.tree = tree;
    }

    /**
     * @param start the node the first step is relative to
     * @param multiple false if only the first match is needed
     * @return the matches, at most one if {@code multiple} is false
     */
    public List<N> evaluate(ClassChain chain, N start, boolean multiple) {
        List<ChainStep> steps = chain.getSteps();
        List<N> current = Collections.singletonList(start);
        for (int i = 0; i < steps.size(); i++) {
            boolean isLast = i == steps.size() - 1;
            current = apply(steps.get(i), current, isLast && !multiple);
            if (current.isEmpty()) {
                return current;
            }
        }
        return multiple || current.size() == 1
                ? current
                : new ArrayList<>(current.subList(0, 1));
    }

    private List<N> apply(ChainStep step, List<N> from, boolean firstOnly) {
        switch (step.getAxis()) {
            case CHILD:
            case DESCENDANT:
                return applyDownwards(step, from, firstOnly);
            default:
                return applyAlongAxis(step, from);
        }
    }

    // ---- child and descendant steps ----

    private List<N> applyDownwards(ChainStep step, List<N> from, boolean firstOnly) {
        Integer position = step.getPosition();
        // a negative position counts from the end, so it needs all the matches
        int limit = position != null ? (position > 0 ? position : UNLIMITED)
                : firstOnly ? 1 : UNLIMITED;
        // overlapping roots would walk (and count) the same node more than once
        Set<N> visited = from.size() > 1 ? new HashSet<>() : null;
        List<N> matches = new ArrayList<>();
        for (N root : from) {
            if (!collect(step, root, visited, matches, limit)) {
                break;
            }
        }
        return select(position, matches);
    }

    /** @return false once {@code limit} matches have been found */
    private boolean collect(ChainStep step, N root, @Nullable Set<N> visited, List<N> matches,
                            int limit) {
        boolean descendants = step.isDescendant();
        Deque<N> pending = new ArrayDeque<>();
        pushChildren(pending, root);
        while (!pending.isEmpty()) {
            N node = pending.pop();
            if (visited != null && !visited.add(node)) {
                continue;
            }
            if (matches(step, node)) {
                matches.add(node);
                if (matches.size() >= limit) {
                    return false;
                }
            }
            if (descendants) {
                pushChildren(pending, node);
            }
        }
        return true;
    }

    /** Pushes the children so that the first one is popped first. */
    private void pushChildren(Deque<N> pending, N node) {
        List<N> children = tree.getChildren(node);
        for (int i = children.size() - 1; i >= 0; i--) {
            pending.push(children.get(i));
        }
    }

    // ---- axis steps ----

    private List<N> applyAlongAxis(ChainStep step, List<N> from) {
        Set<N> result = new LinkedHashSet<>();
        for (N node : from) {
            List<N> matched = new ArrayList<>();
            for (N candidate : axisNodes(step.getAxis(), node)) {
                if (matches(step, candidate)) {
                    matched.add(candidate);
                }
            }
            result.addAll(select(step.getPosition(), matched));
        }
        return new ArrayList<>(result);
    }

    /** The nodes of the axis, nearest first for the reverse ones. */
    private List<N> axisNodes(ChainStep.Axis axis, N node) {
        List<N> result = new ArrayList<>();
        N parent = tree.getParent(node);
        switch (axis) {
            case PARENT:
                if (parent != null) {
                    result.add(parent);
                }
                break;
            case ANCESTOR:
                for (N ancestor = parent; ancestor != null; ancestor = tree.getParent(ancestor)) {
                    result.add(ancestor);
                }
                break;
            default:
                addSiblings(axis, node, parent, result);
                break;
        }
        return result;
    }

    private void addSiblings(ChainStep.Axis axis, N node, @Nullable N parent, List<N> result) {
        if (parent == null) {
            return;
        }
        List<N> siblings = tree.getChildren(parent);
        int index = siblings.indexOf(node);
        if (index < 0) {
            return;
        }
        switch (axis) {
            case FOLLOWING_SIBLING:
                result.addAll(siblings.subList(index + 1, siblings.size()));
                break;
            case PRECEDING_SIBLING:
                for (int i = index - 1; i >= 0; i--) {
                    result.add(siblings.get(i));
                }
                break;
            default:
                result.addAll(siblings.subList(0, index));
                result.addAll(siblings.subList(index + 1, siblings.size()));
                break;
        }
    }

    // ---- matching ----

    private boolean matches(ChainStep step, N node) {
        String className = step.getClassName();
        if (className != null && !className.equals(tree.getClassName(node))) {
            return false;
        }
        NodeValues values = null;
        for (Filter filter : step.getFilters()) {
            boolean passed;
            if (filter.getScope() == ChainStep.Scope.SELF) {
                values = values == null ? new NodeValues(node) : values;
                passed = predicates.evaluate(filter.getPredicate(), values);
            } else {
                passed = hasMatchingDescendant(filter, node);
            }
            if (!passed) {
                return false;
            }
        }
        return true;
    }

    private boolean hasMatchingDescendant(Filter filter, N node) {
        Deque<N> pending = new ArrayDeque<>();
        pushChildren(pending, node);
        while (!pending.isEmpty()) {
            N descendant = pending.pop();
            if (predicates.evaluate(filter.getPredicate(), new NodeValues(descendant))) {
                return true;
            }
            pushChildren(pending, descendant);
        }
        return false;
    }

    /** 1-based, negative counts from the end; at most one element. */
    private static <T> List<T> select(@Nullable Integer position, List<T> matches) {
        if (position == null) {
            return matches;
        }
        int index = position > 0 ? position - 1 : matches.size() + position;
        return index >= 0 && index < matches.size()
                ? Collections.singletonList(matches.get(index))
                : Collections.<T>emptyList();
    }

    /** Reads every attribute of a node at most once. */
    private final class NodeValues implements PredicateEvaluator.Values {
        private final N node;
        private final Map<Attribute, Object> cache = new EnumMap<>(Attribute.class);
        private boolean boundsRead;
        @Nullable
        private int[] bounds;

        NodeValues(N node) {
            this.node = node;
        }

        @Nullable
        @Override
        public Object get(Attribute attribute) {
            if (!cache.containsKey(attribute)) {
                cache.put(attribute, tree.getAttribute(node, attribute));
            }
            return cache.get(attribute);
        }

        @Nullable
        @Override
        public Long getBound(BoundsField field) {
            if (!boundsRead) {
                bounds = tree.getBounds(node);
                boundsRead = true;
            }
            if (bounds == null) {
                return null;
            }
            switch (field) {
                case LEFT:
                    return (long) bounds[0];
                case TOP:
                    return (long) bounds[1];
                case RIGHT:
                    return (long) bounds[2];
                case BOTTOM:
                    return (long) bounds[3];
                case WIDTH:
                    return (long) bounds[2] - bounds[0];
                default:
                    return (long) bounds[3] - bounds[1];
            }
        }
    }
}
