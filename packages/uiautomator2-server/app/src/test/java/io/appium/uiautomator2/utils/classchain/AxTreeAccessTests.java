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

import android.view.accessibility.AccessibilityNodeInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.appium.uiautomator2.model.settings.AllowInvisibleElements;
import io.appium.uiautomator2.model.settings.Settings;
import io.appium.uiautomator2.model.settings.SnapshotMaxDepth;
import io.appium.uiautomator2.utils.Attribute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class AxTreeAccessTests {
    private int originalMaxDepth;
    private boolean originalAllowInvisible;

    @Before
    public void setUp() {
        originalMaxDepth = Settings.get(SnapshotMaxDepth.class).getValue();
        originalAllowInvisible = Settings.get(AllowInvisibleElements.class).getValue();
        Settings.get(SnapshotMaxDepth.class).update(70);
        Settings.get(AllowInvisibleElements.class).update(false);
    }

    @After
    public void tearDown() {
        Settings.get(SnapshotMaxDepth.class).update(originalMaxDepth);
        Settings.get(AllowInvisibleElements.class).update(originalAllowInvisible);
    }

    private static AccessibilityNodeInfo node(String className, boolean visible,
                                              AccessibilityNodeInfo... children) {
        AccessibilityNodeInfo node = mock(AccessibilityNodeInfo.class);
        when(node.getClassName()).thenReturn(className);
        when(node.isVisibleToUser()).thenReturn(visible);
        when(node.getChildCount()).thenReturn(children.length);
        for (int i = 0; i < children.length; i++) {
            when(node.getChild(i)).thenReturn(children[i]);
            when(children[i].getParent()).thenReturn(node);
        }
        return node;
    }

    @Test
    public void childrenAreFetchedOnlyOnce() {
        AccessibilityNodeInfo first = node("a", true);
        AccessibilityNodeInfo second = node("b", true);
        AccessibilityNodeInfo context = node("c", true, first, second);
        AxTreeAccess tree = new AxTreeAccess(context);

        List<AxTreeAccess.Node> children = tree.getChildren(tree.getStart());
        assertEquals(2, children.size());
        assertSame(children, tree.getChildren(tree.getStart()));
        // a parent that is reached from a child is the same node
        assertSame(children, tree.getChildren(tree.getParent(children.get(0))));
        verify(context, times(1)).getChild(0);
        verify(context, times(1)).getChild(1);
    }

    @Test
    public void iteratingFetchesChildrenOnDemand() {
        AccessibilityNodeInfo hidden = node("hidden", false);
        AccessibilityNodeInfo first = node("a", true);
        AccessibilityNodeInfo second = node("b", true);
        AccessibilityNodeInfo context = node("c", true, hidden, first, second);
        AxTreeAccess tree = new AxTreeAccess(context);

        Iterator<AxTreeAccess.Node> it = tree.iterateChildren(tree.getStart());
        assertTrue(it.hasNext());
        assertEquals("a", tree.getClassName(it.next()));
        verify(context, never()).getChild(2);

        // the part that is already fetched is reused
        assertEquals(2, tree.getChildren(tree.getStart()).size());
        verify(context, times(1)).getChild(1);
        verify(context, times(1)).getChild(2);
        Iterator<AxTreeAccess.Node> again = tree.iterateChildren(tree.getStart());
        assertEquals("a", tree.getClassName(again.next()));
        assertEquals("b", tree.getClassName(again.next()));
        assertTrue(!again.hasNext());
    }

    @Test
    public void invisibleChildrenAreSkippedUnlessAllowed() {
        AccessibilityNodeInfo hidden = node("hidden", false);
        AccessibilityNodeInfo shown = node("shown", true);
        AxTreeAccess tree = new AxTreeAccess(node("c", true, hidden, shown));
        List<AxTreeAccess.Node> children = tree.getChildren(tree.getStart());
        assertEquals(1, children.size());
        assertEquals("shown", tree.getClassName(children.get(0)));
        verify(hidden).recycle();

        Settings.get(AllowInvisibleElements.class).update(true);
        tree = new AxTreeAccess(node("c", true, node("hidden", false), node("shown", true)));
        assertEquals(2, tree.getChildren(tree.getStart()).size());
    }

    @Test
    public void indexIsTheChildPositionLikeInThePageSource() {
        AccessibilityNodeInfo hidden = node("hidden", false);
        AccessibilityNodeInfo shown = node("shown", true);
        AxTreeAccess tree = new AxTreeAccess(node("c", true, hidden, shown));
        AxTreeAccess.Node child = tree.getChildren(tree.getStart()).get(0);

        // the dropped invisible sibling still counts
        assertEquals(1, tree.getAttribute(child, Attribute.INDEX));
        verify(shown, never()).getParent();
    }

    @Test
    public void indexOfTheContextIsReadFromItsParent() {
        AccessibilityNodeInfo context = node("c", true);
        AccessibilityNodeInfo parent = mock(AccessibilityNodeInfo.class);
        when(parent.getChildCount()).thenReturn(3);
        when(parent.getChild(2)).thenReturn(context);
        when(context.getParent()).thenReturn(parent);
        AxTreeAccess tree = new AxTreeAccess(context);

        assertEquals(2, tree.getAttribute(tree.getStart(), Attribute.INDEX));
    }

    @Test
    public void otherAttributesAreReadFromTheNode() {
        AccessibilityNodeInfo shown = node("shown", true);
        when(shown.isChecked()).thenReturn(true);
        AxTreeAccess tree = new AxTreeAccess(node("c", true, shown));
        AxTreeAccess.Node child = tree.getChildren(tree.getStart()).get(0);
        assertEquals(true, tree.getAttribute(child, Attribute.CHECKED));
        assertEquals("shown", tree.getAttribute(child, Attribute.CLASS));
    }

    @Test
    public void parentsAreFound() {
        AccessibilityNodeInfo child = node("child", true);
        AccessibilityNodeInfo context = node("context", true, child);
        AccessibilityNodeInfo top = node("top", true, context);
        AxTreeAccess tree = new AxTreeAccess(context);

        assertEquals("top", tree.getClassName(tree.getParent(tree.getStart())));
        assertNull(tree.getParent(tree.getParent(tree.getStart())));
        assertSame(top, tree.getParent(tree.getStart()).getInfo());
    }

    @Test
    public void theDepthLimitOfThePageSourceApplies() {
        Settings.get(SnapshotMaxDepth.class).update(2);
        AccessibilityNodeInfo grandChild = node("grandChild", true);
        AccessibilityNodeInfo child = node("child", true, grandChild);
        // the window root is at depth 1, and its children at depth 2 get no children
        AxTreeAccess tree = new AxTreeAccess(node("root", true, child));
        List<AxTreeAccess.Node> children = tree.getChildren(tree.getStart());
        assertEquals(1, children.size());
        assertTrue(tree.getChildren(children.get(0)).isEmpty());
    }

    @Test
    public void theDepthOfTheContextCountsItsAncestors() {
        Settings.get(SnapshotMaxDepth.class).update(2);
        AccessibilityNodeInfo context = node("context", true, node("child", true));
        node("top", true, context);
        // the context is at depth 2 like in the page source, so it has no children
        assertTrue(new AxTreeAccess(context).getChildren(new AxTreeAccess(context).getStart())
                .isEmpty());
    }

    @Test
    public void releaseRecyclesFetchedNodesExceptTheKeptOnesAndTheContext() {
        AccessibilityNodeInfo kept = node("kept", true);
        AccessibilityNodeInfo other = node("other", true);
        AccessibilityNodeInfo context = node("context", true, kept, other);
        AxTreeAccess tree = new AxTreeAccess(context);
        tree.getChildren(tree.getStart());

        tree.release(Collections.singletonList(kept));

        verify(other).recycle();
        verify(kept, never()).recycle();
        verify(context, never()).recycle();
    }
}
