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

import java.util.List;

import io.appium.uiautomator2.utils.Attribute;

/** What {@link ClassChainEvaluator} needs to know about a UI tree with nodes of type {@code N}. */
public interface TreeAccess<N> {

    /** The children that take part in lookups, in order. */
    List<N> getChildren(N node);

    @Nullable
    N getParent(N node);

    @Nullable
    String getClassName(N node);

    /** The attribute value the way the page source shows it; null if the node has none. */
    @Nullable
    Object getAttribute(N node, Attribute attribute);

    /** {@code [left, top, right, bottom]} or null if the node has no bounds. */
    @Nullable
    int[] getBounds(N node);
}
