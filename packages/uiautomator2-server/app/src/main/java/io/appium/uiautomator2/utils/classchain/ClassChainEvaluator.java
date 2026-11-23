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
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.appium.uiautomator2.utils.Attribute;
import io.appium.uiautomator2.utils.classchain.ChainStep.Filter;
import io.appium.uiautomator2.utils.predicate.PredicateNode.BoundsField;

/** Runs a {@link ClassChain} over a tree lazily, with the step semantics of WebDriverAgent. */
public final class ClassChainEvaluator<N> {
    private static final int UNLIMITED = Integer.MAX_VALUE;

    private final TreeAccess<N> tree;
    private final PredicateEvaluator predicates = new PredicateEvaluator();
    // per descendant filter: nodes whose subtree, including themselves, has no match
    private final Map<Filter, Set<N>> noMatches = new IdentityHashMap<>();

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
        int lazyFrom = multiple ? steps.size() : firstLazyStep(steps);
        List<N> current = Collections.singletonList(start);
        for (int i = 0; i < lazyFrom; i++) {
            boolean isLast = i == steps.size() - 1;
            current = apply(steps.get(i), current, isLast && !multiple);
            if (current.isEmpty()) {
                return current;
            }
        }
        if (lazyFrom < steps.size()) {
            // the first match needs no more of the intermediate matches than it takes to find it
            Iterator<N> stream = current.iterator();
            for (int i = lazyFrom; i < steps.size(); i++) {
                stream = stage(steps.get(i), stream);
            }
            return stream.hasNext()
                    ? Collections.singletonList(stream.next())
                    : Collections.<N>emptyList();
        }
        return multiple || current.size() == 1
                ? current
                : new ArrayList<>(current.subList(0, 1));
    }

    /**
     * A position on a child or descendant step counts among all the matches of the step, so the
     * steps up to the last such step run eagerly. The steps after it can be streamed, but that
     * only pays off for two or more of them (the last step stops early anyway).
     *
     * @return the index of the first streamed step, or the number of steps if none is
     */
    private static int firstLazyStep(List<ChainStep> steps) {
        int from = 0;
        for (int i = steps.size() - 1; i >= 0; i--) {
            ChainStep step = steps.get(i);
            if (step.getPosition() != null && isDownwards(step)) {
                from = i + 1;
                break;
            }
        }
        return steps.size() - from >= 2 ? from : steps.size();
    }

    private static boolean isDownwards(ChainStep step) {
        return step.getAxis() == ChainStep.Axis.CHILD || step.isDescendant();
    }

    /** The matches of a step for the nodes of {@code sources}, in the order of the eager step. */
    private Iterator<N> stage(ChainStep step, Iterator<N> sources) {
        if (isDownwards(step)) {
            return new DownwardMatches(step, sources, true);
        }
        Set<N> seen = new HashSet<>();
        return new Iterator<N>() {
            private Iterator<N> batch = Collections.emptyIterator();
            private N next;

            @Override
            public boolean hasNext() {
                while (next == null) {
                    if (batch.hasNext()) {
                        N candidate = batch.next();
                        next = seen.add(candidate) ? candidate : null;
                    } else if (sources.hasNext()) {
                        batch = alongAxis(step, sources.next()).iterator();
                    } else {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public N next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                N result = next;
                next = null;
                return result;
            }
        };
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
        Iterator<N> found = new DownwardMatches(step, from.iterator(), from.size() > 1);
        List<N> matches = new ArrayList<>();
        while (matches.size() < limit && found.hasNext()) {
            matches.add(found.next());
        }
        return select(position, matches);
    }

    /**
     * The matching children or descendants of the roots, one root after another in document
     * order. The children are fetched on demand, so stopping early leaves the rest unfetched.
     */
    private final class DownwardMatches implements Iterator<N> {
        private final ChainStep step;
        private final boolean descendants;
        private final Iterator<N> roots;
        @Nullable
        private final Set<N> visited;
        private final Deque<Iterator<N>> pending = new ArrayDeque<>();
        private N next;

        DownwardMatches(ChainStep step, Iterator<N> roots, boolean dedupe) {
            this.step = step;
            this.descendants = step.isDescendant();
            this.roots = roots;
            this.visited = dedupe ? new HashSet<>() : null;
        }

        @Override
        public boolean hasNext() {
            while (next == null) {
                if (pending.isEmpty()) {
                    if (!roots.hasNext()) {
                        return false;
                    }
                    pending.push(tree.iterateChildren(roots.next()));
                    continue;
                }
                Iterator<N> siblings = pending.peek();
                if (!siblings.hasNext()) {
                    pending.pop();
                    continue;
                }
                N node = siblings.next();
                if (visited != null && !visited.add(node)) {
                    continue;
                }
                boolean isMatch = matches(step, node);
                if (descendants) {
                    pending.push(tree.iterateChildren(node));
                }
                if (isMatch) {
                    next = node;
                }
            }
            return true;
        }

        @Override
        public N next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            N result = next;
            next = null;
            return result;
        }
    }

    // ---- axis steps ----

    private List<N> applyAlongAxis(ChainStep step, List<N> from) {
        Set<N> result = new LinkedHashSet<>();
        for (N node : from) {
            result.addAll(alongAxis(step, node));
        }
        return new ArrayList<>(result);
    }

    /** The matches of an axis step for one node, the position counts for the node alone. */
    private List<N> alongAxis(ChainStep step, N node) {
        List<N> matched = new ArrayList<>();
        for (N candidate : axisNodes(step.getAxis(), node)) {
            if (matches(step, candidate)) {
                matched.add(candidate);
            }
        }
        return select(step.getPosition(), matched);
    }

    /** The nodes of the axis, nearest first for the reverse ones. */
    private List<N> axisNodes(ChainStep.Axis axis, N node) {
        List<N> result = new ArrayList<>();
        switch (axis) {
            case PARENT:
                N parent = tree.getParent(node);
                if (parent != null) {
                    result.add(parent);
                }
                break;
            case ANCESTOR:
                // a broken tree may have a cycle of parents
                Set<N> seen = new HashSet<>();
                seen.add(node);
                for (N ancestor = tree.getParent(node); ancestor != null && seen.add(ancestor);
                     ancestor = tree.getParent(ancestor)) {
                    result.add(ancestor);
                }
                break;
            default:
                addSiblings(axis, node, result);
                break;
        }
        return result;
    }

    private void addSiblings(ChainStep.Axis axis, N node, List<N> result) {
        List<N> siblings = tree.getSiblings(node);
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
        Set<N> dead = noMatches.computeIfAbsent(filter, f -> new HashSet<>());
        Deque<Frame<N>> path = new ArrayDeque<>();
        path.push(new Frame<>(node, tree.iterateChildren(node)));
        while (!path.isEmpty()) {
            Frame<N> frame = path.peek();
            if (!frame.children.hasNext()) {
                path.pop();
                if (!path.isEmpty()) {
                    // the node itself did not match either, or the walk would have stopped
                    dead.add(frame.node);
                }
                continue;
            }
            N child = frame.children.next();
            if (dead.contains(child)) {
                continue;
            }
            if (predicates.evaluate(filter.getPredicate(), new NodeValues(child))) {
                return true;
            }
            path.push(new Frame<>(child, tree.iterateChildren(child)));
        }
        return false;
    }

    private static final class Frame<N> {
        final N node;
        final Iterator<N> children;

        Frame(N node, Iterator<N> children) {
            this.node = node;
            this.children = children;
        }
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
