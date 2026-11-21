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

package io.appium.uiautomator2.common.exceptions;

/**
 * Thrown when a predicate string cannot be parsed. The position is a 0-based character offset.
 */
public class PredicateSyntaxException extends InvalidSelectorException {
    private final String predicate;
    private final String reason;
    private final int position;

    public PredicateSyntaxException(String predicate, String reason, int position) {
        super(String.format("Cannot parse predicate '%s'. %s at position %d",
                predicate, reason, position));
        this.predicate = predicate;
        this.reason = reason;
        this.position = position;
    }

    public String getPredicate() {
        return predicate;
    }

    public String getReason() {
        return reason;
    }

    public int getPosition() {
        return position;
    }
}
