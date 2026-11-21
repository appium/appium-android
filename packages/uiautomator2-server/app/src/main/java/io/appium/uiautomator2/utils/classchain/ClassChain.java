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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A parsed class chain: at least one step. The first step is relative to the search root
 * (window roots or the context element).
 */
public final class ClassChain {
    private final List<ChainStep> steps;

    ClassChain(List<ChainStep> steps) {
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public List<ChainStep> getSteps() {
        return steps;
    }

    /** Canonical form, which parses back to an equal chain */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            ChainStep step = steps.get(i);
            if (i == 0) {
                sb.append(step.isDescendant() ? "**/" : "");
            } else {
                sb.append(step.isDescendant() ? "/**/" : "/");
            }
            sb.append(step);
        }
        return sb.toString();
    }
}
