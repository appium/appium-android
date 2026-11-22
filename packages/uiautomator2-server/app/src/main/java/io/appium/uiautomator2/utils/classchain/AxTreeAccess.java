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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.appium.uiautomator2.core.AxNodeInfoHelper;
import io.appium.uiautomator2.model.UiElementSnapshot;
import io.appium.uiautomator2.model.settings.AllowInvisibleElements;
import io.appium.uiautomator2.model.settings.Settings;
import io.appium.uiautomator2.utils.Attribute;
import io.appium.uiautomator2.utils.StringHelpers;

import static io.appium.uiautomator2.utils.AXWindowHelpers.getCachedWindowRoots;

/**
 * Walks the live accessibility tree with the visibility rules of the page source. The nodes
 * that were fetched on the way are recycled by {@link #release}.
 */
public final class AxTreeAccess implements TreeAccess<AxTreeAccess.Node> {

    /** A node of the tree, the root above all windows has no accessibility node. */
    public static final class Node {
        @Nullable
        private final AccessibilityNodeInfo info;

        private Node(@Nullable AccessibilityNodeInfo info) {
            this.info = info;
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
    private final Node start;
    private final AccessibilityNodeInfo[] windowRoots;
    // owned by somebody else, so they must not be recycled
    private final Set<AccessibilityNodeInfo> owned =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<AccessibilityNodeInfo> fetched = new ArrayList<>();

    /** @param context the node to search below, or null to search all windows */
    public AxTreeAccess(@Nullable AccessibilityNodeInfo context) {
        if (context == null) {
            windowRoots = getCachedWindowRoots();
            owned.addAll(Arrays.asList(windowRoots));
        } else {
            windowRoots = new AccessibilityNodeInfo[0];
            owned.add(context);
        }
        start = new Node(context);
    }

    /** The node the first step of a chain is relative to. */
    public Node getStart() {
        return start;
    }

    @Override
    public List<Node> getChildren(Node node) {
        AccessibilityNodeInfo info = node.info;
        if (info == null) {
            List<Node> roots = new ArrayList<>(windowRoots.length);
            for (AccessibilityNodeInfo root : windowRoots) {
                roots.add(new Node(root));
            }
            return roots;
        }
        int count = info.getChildCount();
        List<Node> children = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = info.getChild(i);
            if (child == null) {
                continue;
            }
            if (areInvisibleElementsAllowed
                    || child.isVisibleToUser()
                    || UiElementSnapshot.shouldTraverseIfRequested(child)) {
                fetched.add(child);
                children.add(new Node(child));
            } else {
                AxNodeInfoHelper.recycleSafely(child);
            }
        }
        return children;
    }

    @Nullable
    @Override
    public Node getParent(Node node) {
        AccessibilityNodeInfo parent = node.info == null ? null : node.info.getParent();
        if (parent == null) {
            return null;
        }
        fetched.add(parent);
        return new Node(parent);
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
        return node.info == null ? null : UiElementSnapshot.getAttributeValue(node.info, attribute);
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
    }
}
