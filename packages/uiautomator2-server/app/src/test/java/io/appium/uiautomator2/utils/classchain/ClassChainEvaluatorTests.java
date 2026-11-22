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

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.appium.uiautomator2.utils.Attribute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClassChainEvaluatorTests {
    private static final String FRAME = "android.widget.FrameLayout";
    private static final String LINEAR = "android.widget.LinearLayout";
    private static final String TEXT = "android.widget.TextView";
    private static final String SWITCH = "android.widget.Switch";
    private static final String BUTTON = "android.widget.Button";

    private static final class FakeNode {
        final String className;
        final String id;
        final Map<Attribute, Object> attributes = new EnumMap<>(Attribute.class);
        final List<FakeNode> children = new ArrayList<>();
        FakeNode parent;
        int[] bounds = {0, 0, 100, 50};

        FakeNode(String className, String id) {
            this.className = className;
            this.id = id;
        }

        FakeNode with(Attribute attribute, Object value) {
            attributes.put(attribute, value);
            return this;
        }

        FakeNode add(FakeNode... nodes) {
            for (FakeNode node : nodes) {
                node.parent = this;
                children.add(node);
            }
            return this;
        }
    }

    private static final class FakeTree implements TreeAccess<FakeNode> {
        int visitedChildren;
        int attributeReads;

        @Override
        public List<FakeNode> getChildren(FakeNode node) {
            visitedChildren++;
            return node.children;
        }

        @Override
        public FakeNode getParent(FakeNode node) {
            // like a window root, a child of the virtual root has no parent
            return node.parent == null || node.parent.parent == null ? null : node.parent;
        }

        @Override
        public String getClassName(FakeNode node) {
            return node.className;
        }

        @Override
        public Object getAttribute(FakeNode node, Attribute attribute) {
            attributeReads++;
            return node.attributes.get(attribute);
        }

        @Override
        public int[] getBounds(FakeNode node) {
            return node.bounds;
        }
    }

    private static FakeNode node(String className, String id) {
        return new FakeNode(className, id);
    }

    private static FakeNode text(String id, String text) {
        return node(TEXT, id).with(Attribute.TEXT, text);
    }

    private FakeNode root;
    private FakeTree tree;

    /**
     * <pre>
     * root (the node above the windows)
     *   frame
     *     list (LinearLayout)
     *       row1 (LinearLayout, clickable): wifi "Wi-Fi", wifiSwitch
     *       row2 (LinearLayout): bt "Bluetooth", btSwitch
     *       row3 (LinearLayout): price "Price", amount "9.99", buy (Button)
     *     ok (Button "OK")
     * </pre>
     */
    @Before
    public void buildTree() {
        root = node("hierarchy", "root");
        FakeNode row1 = node(LINEAR, "row1").with(Attribute.CLICKABLE, true)
                .add(text("wifi", "Wi-Fi"),
                        node(SWITCH, "wifiSwitch").with(Attribute.CHECKED, true));
        FakeNode row2 = node(LINEAR, "row2").with(Attribute.CLICKABLE, false)
                .add(text("bt", "Bluetooth"),
                        node(SWITCH, "btSwitch").with(Attribute.CHECKED, false));
        FakeNode row3 = node(LINEAR, "row3").with(Attribute.CLICKABLE, false)
                .add(text("price", "Price"), text("amount", "9.99"),
                        node(BUTTON, "buy").with(Attribute.TEXT, "Buy"));
        FakeNode list = node(LINEAR, "list").add(row1, row2, row3);
        FakeNode ok = node(BUTTON, "ok").with(Attribute.TEXT, "OK");
        root.add(node(FRAME, "frame").add(list, ok));
        tree = new FakeTree();
    }

    private List<String> all(String chain) {
        return ids(new ClassChainEvaluator<>(tree)
                .evaluate(ClassChainParser.parse(chain), root, true));
    }

    private List<String> allFrom(String chain, FakeNode start) {
        return ids(new ClassChainEvaluator<>(tree)
                .evaluate(ClassChainParser.parse(chain), start, true));
    }

    private List<String> first(String chain) {
        return ids(new ClassChainEvaluator<>(tree)
                .evaluate(ClassChainParser.parse(chain), root, false));
    }

    private static List<String> ids(List<FakeNode> nodes) {
        return nodes.stream().map(n -> n.id).collect(Collectors.toList());
    }

    private static List<String> list(String... ids) {
        return Arrays.asList(ids);
    }

    // ---- child and descendant steps ----

    @Test
    public void descendantsAreFoundInDocumentOrder() {
        assertEquals(list("wifi", "bt", "price", "amount"), all("**/" + TEXT));
        assertEquals(list("frame", "list", "row1", "wifi", "wifiSwitch", "row2", "bt", "btSwitch",
                "row3", "price", "amount", "buy", "ok"), all("**/*"));
    }

    @Test
    public void childStepsOnlyLookOneLevelDown() {
        assertEquals(list("frame"), all("*"));
        assertEquals(list("list"), all(FRAME + "/" + LINEAR));
        assertEquals(list("row1", "row2", "row3"), all(FRAME + "/" + LINEAR + "/" + LINEAR));
        assertEquals(list(), all(TEXT));
        assertEquals(list("ok"), all(FRAME + "/" + BUTTON));
        assertEquals(list("wifi", "wifiSwitch"), all("*/*/*[1]/*"));
    }

    @Test
    public void descendantStepAfterAStep() {
        assertEquals(list("wifiSwitch", "btSwitch"), all(FRAME + "/**/" + SWITCH));
        assertEquals(list("buy", "ok"), all("**/" + FRAME + "/**/" + BUTTON));
    }

    @Test
    public void selfPredicates() {
        assertEquals(list("wifi"), all("**/*[`text == 'Wi-Fi'`]"));
        assertEquals(list("row1"), all("**/" + LINEAR + "[`clickable == true`]"));
        assertEquals(list("wifiSwitch"), all("**/*[`checked == true`]"));
        assertEquals(list("bt", "price"),
                all("**/" + TEXT + "[`text == 'Bluetooth' OR text == 'Price'`]"));
        assertEquals(list("wifi", "bt", "price", "amount"),
                all("**/" + TEXT + "[`text != 'x'`][`text LIKE '*'`]"));
        assertEquals(13, all("**/*[`bounds.width == 100`]").size());
        assertEquals(list(), all("**/*[`bounds.width > 100`]"));
    }

    @Test
    public void descendantPredicatesNeedAMatchBelowTheNodeItself() {
        assertEquals(list("row1"),
                all("**/" + LINEAR + "[$text == 'Wi-Fi'$][`clickable == true`]"));
        // the list and the frame contain it too
        assertEquals(list("frame", "list", "row1"), all("**/*[$text == 'Wi-Fi'$]"));
        // a node does not count as its own descendant
        assertEquals(list("row3"), all("**/*[$text == 'Price'$][`clickable == false`]"));
        // the frame and the list are roots too, so the other row's Switch is found
        assertEquals(list("wifiSwitch", "btSwitch"), all("**/*[$text == 'Wi-Fi'$]/**/" + SWITCH));
        assertEquals(list("wifiSwitch"),
                all("**/*[$text == 'Wi-Fi'$][`clickable == true`]/**/" + SWITCH));
        assertEquals(list(), all("**/" + TEXT + "[$text == 'Wi-Fi'$]"));
    }

    @Test
    public void descendantPredicatesDoNotWalkTheSameSubtreeTwice() {
        assertEquals(list(), all("**/*[$text == 'zzz'$]"));
        // the frame walks its 12 descendants once, the nested candidates reuse that
        assertEquals(12, tree.attributeReads);
    }

    @Test
    public void descendantPredicateResultsAreCorrectWithTheMemo() {
        assertEquals(list("frame", "list", "row2"), all("**/*[$checked == false$]"));
        assertEquals(list("row1"), all("**/*[$checked == true$][`clickable == true`]"));
        assertEquals(list("frame", "list", "row3"), all("**/*[$text == '9.99'$]"));
    }

    // ---- positions ----

    @Test
    public void positionsCountAmongAllMatches() {
        assertEquals(list("price"), all("**/" + TEXT + "[3]"));
        assertEquals(list("amount"), all("**/" + TEXT + "[-1]"));
        assertEquals(list("bt"), all("**/" + TEXT + "[-3]"));
        assertEquals(list(), all("**/" + TEXT + "[5]"));
        assertEquals(list(), all("**/" + TEXT + "[-5]"));
        assertEquals(list("wifi"), all("**/" + TEXT + "[1]"));
    }

    @Test
    public void positionsOfSeveralParentsAreCountedTogether() {
        // XPath would count per parent, class chains (like WDA) count among all matches
        assertEquals(list("bt"), all("**/" + LINEAR + "/" + TEXT + "[2]"));
    }

    @Test
    public void anIntermediatePositionNarrowsTheRoots() {
        assertEquals(list("row1"), all("**/" + LINEAR + "[2]"));
        assertEquals(list("wifi", "wifiSwitch"), all("**/" + LINEAR + "[2]/*"));
        assertEquals(list("price"), all("**/" + LINEAR + "[-1]/" + TEXT + "[1]"));
        assertEquals(list(), all("**/" + LINEAR + "[9]/*"));
    }

    @Test
    public void singleLookupsGiveTheSameFirstMatch() {
        assertEquals(list("wifi"), first("**/" + TEXT));
        assertEquals(list("amount"), first("**/" + TEXT + "[-1]"));
        assertEquals(list("bt"), first("**/" + TEXT + "[2]"));
        assertEquals(list("wifiSwitch"), first("**/*[$text == 'Wi-Fi'$]/**/" + SWITCH));
        assertEquals(list(), first("**/" + TEXT + "[9]"));
        assertEquals(list(), first("**/android.widget.Nothing"));
    }

    @Test
    public void singleLookupsStopAtTheFirstMatch() {
        first("**/" + TEXT);
        int visitedForFirst = tree.visitedChildren;
        tree = new FakeTree();
        all("**/" + TEXT);
        assertTrue(visitedForFirst + " < " + tree.visitedChildren,
                visitedForFirst < tree.visitedChildren);
        // the first Switch is found before the second row is opened
        tree = new FakeTree();
        first("**/" + SWITCH);
        assertTrue(tree.visitedChildren <= 6);
    }

    @Test
    public void overlappingRootsDoNotProduceDuplicates() {
        // every Linear is a root, and the list contains the rows
        assertEquals(list("wifi", "bt", "price", "amount"), all("**/" + LINEAR + "/**/" + TEXT));
        assertEquals(list("price"), all("**/" + LINEAR + "/**/" + TEXT + "[3]"));
        assertEquals(list("amount"), all("**/" + LINEAR + "/**/" + TEXT + "[-1]"));
    }

    // ---- axes ----

    @Test
    public void parentAndAncestors() {
        assertEquals(list("row1"), all("**/*[`text == 'Wi-Fi'`]/.."));
        assertEquals(list("row1"), all("**/*[`text == 'Wi-Fi'`]/..[`clickable == true`]"));
        assertEquals(list(), all("**/*[`text == 'Wi-Fi'`]/..[`clickable == false`]"));
        assertEquals(list("row1", "list", "frame"), all("**/*[`text == 'Wi-Fi'`]/^*"));
        assertEquals(list("list"), all("**/*[`text == 'Wi-Fi'`]/^" + LINEAR + "[2]"));
        assertEquals(list("row1"), all("**/*[`text == 'Wi-Fi'`]/^" + LINEAR + "[1]"));
        assertEquals(list("list"), all("**/*[`text == 'Wi-Fi'`]/^" + LINEAR + "[-1]"));
        assertEquals(list("frame"), all("**/*[`text == 'Wi-Fi'`]/^" + FRAME));
        assertEquals(list(), all("**/*[`text == 'Wi-Fi'`]/^" + BUTTON));
    }

    @Test
    public void siblings() {
        assertEquals(list("amount", "buy"), all("**/*[`text == 'Price'`]/>*"));
        assertEquals(list("amount"), all("**/*[`text == 'Price'`]/>" + TEXT));
        assertEquals(list("amount"), all("**/*[`text == 'Price'`]/>*[1]"));
        assertEquals(list("buy"), all("**/*[`text == 'Price'`]/>*[-1]"));
        assertEquals(list("price", "buy"), all("**/*[`text == '9.99'`]/~*"));
        assertEquals(list("price"), all("**/*[`text == '9.99'`]/<*[1]"));
        assertEquals(list(), all("**/*[`text == 'Price'`]/<*"));
        assertEquals(list("buy"), all("**/*[`text == 'Price'`]/~" + BUTTON));
        assertEquals(list("row1", "row3"), all("**/*[`text == 'Bluetooth'`]/../~" + LINEAR));
    }

    @Test
    public void reverseAxesCountNearestFirst() {
        assertEquals(list("amount"), all("**/" + BUTTON + "[`text == 'Buy'`]/<*[1]"));
        assertEquals(list("price"), all("**/" + BUTTON + "[`text == 'Buy'`]/<*[2]"));
        assertEquals(list("price"), all("**/" + BUTTON + "[`text == 'Buy'`]/<*[-1]"));
    }

    @Test
    public void axisStepsCanBeChained() {
        assertEquals(list("amount"),
                all("**/*[`text == 'Price'`]/>" + TEXT + "[1]/<" + TEXT + "[1]/>" + TEXT + "[1]"));
        assertEquals(list("price"),
                all("**/*[`text == 'Price'`]/>" + TEXT + "[1]/<" + TEXT + "[1]"));
        // up to the parent, and down again to something else
        assertEquals(list("wifiSwitch"), all("**/*[`text == 'Wi-Fi'`]/../**/" + SWITCH));
        assertEquals(list("btSwitch"), all("**/*[`text == 'Bluetooth'`]/>" + SWITCH));
        assertEquals(list("ok"), all("**/*[`text == 'Wi-Fi'`]/^" + FRAME + "/" + BUTTON));
    }

    @Test
    public void axisPositionsAreCountedPerNode() {
        // a child step counts among all candidates of all rows, an axis step per row
        assertEquals(list("wifi"), all("**/" + LINEAR + "[`clickable != nil`]/*[1]"));
        // the previous sibling of every Switch and Button in the rows
        assertEquals(list("wifi", "bt", "amount"),
                all("**/*[`checked != nil OR text == 'Buy'`]/<*[1]"));
        // the nearest following sibling of every row
        assertEquals(list("row2", "row3"), all("**/" + LINEAR + "[`clickable != nil`]/>*[1]"));
    }

    @Test
    public void axesFromTheStartNode() {
        FakeNode ok = tree.getChildren(root.children.get(0)).get(1);
        // relative to a context node: its siblings, parent and ancestors
        assertEquals(list("list"), allFrom("<*", ok));
        assertEquals(list("frame"), allFrom("..", ok));
        assertEquals(list("frame"), allFrom("^*", ok));
        // nothing above the top
        assertEquals(list(), allFrom("..", root));
        assertEquals(list(), allFrom("~*", root));
    }

    @Test
    public void searchesRelativeToAContextNode() {
        FakeNode list = root.children.get(0).children.get(0);
        assertEquals(list("row1", "row2", "row3"), allFrom("*", list));
        assertEquals(list("wifi", "bt", "price", "amount"), allFrom("**/" + TEXT, list));
        assertEquals(list(), allFrom("**/" + BUTTON + "[`text == 'OK'`]", list));
    }

    // ---- laziness ----

    @Test
    public void attributesAreOnlyReadWhenNeeded() {
        all("**/" + SWITCH);
        assertEquals(0, tree.attributeReads);
        tree = new FakeTree();
        all("**/*[`text == 'Wi-Fi' AND checked == true`]");
        // text for every node, checked only for the matching text
        assertEquals(13 + 1, tree.attributeReads);
    }
}
