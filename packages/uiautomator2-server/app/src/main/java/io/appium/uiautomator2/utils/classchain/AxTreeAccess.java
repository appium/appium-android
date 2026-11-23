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

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.appium.uiautomator2.core.AxNodeInfoHelper;
import io.appium.uiautomator2.model.UiElementSnapshot;
import io.appium.uiautomator2.model.settings.AllowInvisibleElements;
import io.appium.uiautomator2.model.settings.Settings;
import io.appium.uiautomator2.model.settings.SnapshotMaxDepth;
import io.appium.uiautomator2.utils.Attribute;
import io.appium.uiautomator2.utils.StringHelpers;

import static io.appium.uiautomator2.utils.AXWindowHelpers.getCachedWindowRoots;

/** The live accessibility tree with the visibility and depth rules of the page source. */
public final class AxTreeAccess implements TreeAccess<AxTreeAccess.Node> {

    /** A node of the tree, the root above all windows has no accessibility node. */
    public static final class Node {
        @Nullable
        private final AccessibilityNodeInfo info;
        // the position among the parent's children like in the page source, -1 if unknown
        private final int index;
        // the root above all windows is at depth 0, like in the page source
        private final int depth;

        private Node(@Nullable AccessibilityNodeInfo info, int index, int depth) {
            this.info = info;
            this.index = index;
            this.depth = depth;
        }

        @Nullable
        public AccessibilityNodeInfo getInfo() {
            return info;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Node && Objects.equals(info, ((Node) o).info);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(info);
        }
    }

    private final boolean areInvisibleElementsAllowed =
            Settings.get(AllowInvisibleElements.class).getValue();
    private final int maxDepth = Settings.get(SnapshotMaxDepth.class).getValue();
    private final AccessibilityNodeInfo[] windowRoots;
    // owned by somebody else, so they must not be recycled
    private final Set<AccessibilityNodeInfo> owned =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<AccessibilityNodeInfo> fetched = new ArrayList<>();
    // siblings and repeated walks would otherwise fetch the same children again
    private final Map<Node, Children> childrenCache = new HashMap<>();
    private final Node start;

    /** @param context the node to search below, or null to search all windows */
    public AxTreeAccess(@Nullable AccessibilityNodeInfo context) {
        if (context == null) {
            windowRoots = getCachedWindowRoots();
            owned.addAll(Arrays.asList(windowRoots));
            start = new Node(null, -1, 0);
        } else {
            windowRoots = new AccessibilityNodeInfo[0];
            owned.add(context);
            start = new Node(context, -1, depthOf(context));
        }
    }

    /** The node the first step of a chain is relative to. */
    public Node getStart() {
        return start;
    }

    /** The window roots are at depth 1. */
    private int depthOf(AccessibilityNodeInfo node) {
        int depth = 1;
        for (AccessibilityNodeInfo parent = node.getParent(); parent != null;
             parent = parent.getParent()) {
            fetched.add(parent);
            depth++;
        }
        return depth;
    }

    @Override
    public List<Node> getChildren(Node node) {
        Children children = childrenOf(node);
        while (children.fetchNext()) {
            // fetches them all
        }
        return children.view;
    }

    @Override
    public Iterator<Node> iterateChildren(Node node) {
        Children children = childrenOf(node);
        return new Iterator<Node>() {
            private int position;

            @Override
            public boolean hasNext() {
                return position < children.loaded.size() || children.fetchNext();
            }

            @Override
            public Node next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return children.loaded.get(position++);
            }
        };
    }

    private Children childrenOf(Node node) {
        return childrenCache.computeIfAbsent(node, Children::new);
    }

    /** The visible children of a node, fetched one by one as they are asked for. */
    private final class Children {
        private final Node parent;
        private final List<Node> loaded = new ArrayList<>();
        private final List<Node> view = Collections.unmodifiableList(loaded);
        private final int count;
        private int nextChild;
        private boolean done;

        Children(Node parent) {
            this.parent = parent;
            AccessibilityNodeInfo info = parent.info;
            if (parent.depth >= maxDepth) {
                count = 0;
            } else if (info == null) {
                count = 0;
                for (int i = 0; i < windowRoots.length; i++) {
                    loaded.add(new Node(windowRoots[i], i, parent.depth + 1));
                }
            } else {
                count = info.getChildCount();
            }
            done = count == 0;
        }

        /** @return false if there are no more children, otherwise the next one is in {@code loaded} */
        boolean fetchNext() {
            while (!done && nextChild < count) {
                int i = nextChild++;
                AccessibilityNodeInfo child = parent.info.getChild(i);
                if (child == null) {
                    continue;
                }
                if (areInvisibleElementsAllowed
                        || child.isVisibleToUser()
                        || UiElementSnapshot.shouldTraverseIfRequested(child)) {
                    fetched.add(child);
                    loaded.add(new Node(child, i, parent.depth + 1));
                    return true;
                }
                AxNodeInfoHelper.recycleSafely(child);
            }
            done = true;
            return false;
        }
    }

    @Nullable
    @Override
    public Node getParent(Node node) {
        AccessibilityNodeInfo parent = node.info == null ? null : node.info.getParent();
        if (parent == null) {
            return null;
        }
        fetched.add(parent);
        return new Node(parent, -1, Math.max(0, node.depth - 1));
    }

    @Nullable
    @Override
    public String getClassName(Node node) {
        return node.info == null
                ? null
                : StringHelpers.charSequenceToNullableString(node.info.getClassName());
    }

    @Nullable
    @Override
    public Object getAttribute(Node node, Attribute attribute) {
        if (node.info == null) {
            return null;
        }
        // known from the walk, whereas reading it scans the siblings of the node
        if (attribute == Attribute.INDEX && node.index >= 0) {
            return node.index;
        }
        return UiElementSnapshot.getAttributeValue(node.info, attribute);
    }

    @Nullable
    @Override
    public int[] getBounds(Node node) {
        if (node.info == null) {
            return null;
        }
        Rect bounds = AxNodeInfoHelper.getBounds(node.info);
        return new int[]{bounds.left, bounds.top, bounds.right, bounds.bottom};
    }

    /**
     * Recycles every node fetched during the walk, except the ones to keep (usually the matches).
     * The window roots and the context are never recycled.
     */
    public void release(Collection<AccessibilityNodeInfo> keep) {
        Set<AccessibilityNodeInfo> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        kept.addAll(keep);
        for (AccessibilityNodeInfo node : fetched) {
            if (!owned.contains(node) && !kept.contains(node)) {
                AxNodeInfoHelper.recycleSafely(node);
            }
        }
        fetched.clear();
        childrenCache.clear();
    }
}
