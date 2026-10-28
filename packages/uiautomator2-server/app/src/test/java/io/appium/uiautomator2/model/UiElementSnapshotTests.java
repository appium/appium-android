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

import android.view.accessibility.AccessibilityNodeInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.EnumSet;

import io.appium.uiautomator2.model.settings.AllowInvisibleElements;
import io.appium.uiautomator2.model.settings.AlwaysTraversableViewClasses;
import io.appium.uiautomator2.model.settings.Settings;
import io.appium.uiautomator2.utils.Attribute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class UiElementSnapshotTests {
    private static final String HOLDER = "androidx.compose.ui.viewinterop.ViewFactoryHolder";
    private String originalClasses;
    private boolean originalAllowInvisible;

    @Before
    public void setUp() {
        originalClasses = Settings.get(AlwaysTraversableViewClasses.class).getValue();
        originalAllowInvisible = Settings.get(AllowInvisibleElements.class).getValue();
        Settings.get(AlwaysTraversableViewClasses.class).update("");
        Settings.get(AllowInvisibleElements.class).update(false);
    }

    @After
    public void tearDown() {
        Settings.get(AlwaysTraversableViewClasses.class).update(originalClasses);
        Settings.get(AllowInvisibleElements.class).update(originalAllowInvisible);
    }

    @Test
    public void shouldTraverseMatchingInvisibleChild() {
        assertHolderIsTraversed(HOLDER);
    }

    @Test
    public void shouldTraverseMatchingInvisibleChildWithGlobList() {
        assertHolderIsTraversed("android.widget.ImageButton, androidx.compose.ui.viewinterop.*");
    }

    private void assertHolderIsTraversed(String classes) {
        Settings.get(AlwaysTraversableViewClasses.class).update(classes);
        AccessibilityNodeInfo content = node("android.widget.FrameLayout", true);
        AccessibilityNodeInfo hiddenContent = node("android.widget.TextView", false);
        AccessibilityNodeInfo holder = node(HOLDER, false, content, hiddenContent);
        AccessibilityNodeInfo sibling = node("android.widget.ImageView", false);
        UiElementSnapshot snapshot = snapshot(node("android.view.View", true, holder, sibling));

        assertEquals(1, snapshot.getChildren().size());
        UiElementSnapshot holderSnapshot = snapshot.getChildren().get(0);
        assertSame(holder, holderSnapshot.getNode());
        assertEquals(Boolean.FALSE, holderSnapshot.get(Attribute.DISPLAYED));
        assertEquals(1, holderSnapshot.getChildren().size());
        assertSame(content, holderSnapshot.getChildren().get(0).getNode());
    }

    @Test
    public void shouldNotExemptInvisibleChildWhenOnlyParentMatches() {
        Settings.get(AlwaysTraversableViewClasses.class).update("android.view.View");
        AccessibilityNodeInfo holder = node(HOLDER, false,
                node("android.widget.FrameLayout", true));
        assertTrue(snapshot(node("android.view.View", true, holder)).getChildren().isEmpty());
    }

    @Test
    public void shouldFilterInvisibleChildrenByDefault() {
        AccessibilityNodeInfo visible = node("android.widget.TextView", true);
        UiElementSnapshot snapshot = snapshot(node("android.view.View", true,
                node(HOLDER, false, node("android.widget.FrameLayout", true)), visible));
        assertEquals(1, snapshot.getChildren().size());
        assertSame(visible, snapshot.getChildren().get(0).getNode());
    }

    @Test
    public void shouldKeepAllInvisibleChildrenWhenAllowed() {
        Settings.get(AllowInvisibleElements.class).update(true);
        UiElementSnapshot snapshot = snapshot(node("android.view.View", true,
                node(HOLDER, false), node(null, false)));
        assertEquals(2, snapshot.getChildren().size());
    }

    @Test
    public void shouldNotMatchMissingClassNameEvenWithWildcard() {
        Settings.get(AlwaysTraversableViewClasses.class).update("*");
        assertTrue(snapshot(node("android.view.View", true, node(null, false)))
                .getChildren().isEmpty());
    }

    private static UiElementSnapshot snapshot(AccessibilityNodeInfo root) {
        return UiElementSnapshot.take(root, EnumSet.of(Attribute.CLASS, Attribute.DISPLAYED));
    }

    private static AccessibilityNodeInfo node(String className, boolean visible,
                                               AccessibilityNodeInfo... children) {
        AccessibilityNodeInfo node = mock(AccessibilityNodeInfo.class);
        when(node.getClassName()).thenReturn(className);
        when(node.isVisibleToUser()).thenReturn(visible);
        when(node.getChildCount()).thenReturn(children.length);
        for (int i = 0; i < children.length; ++i) {
            when(node.getChild(i)).thenReturn(children[i]);
        }
        return node;
    }
}
