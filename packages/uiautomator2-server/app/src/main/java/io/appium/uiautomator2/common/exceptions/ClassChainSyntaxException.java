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
 * Thrown when a class chain query cannot be parsed. The position is a 0-based character offset.
 */
public class ClassChainSyntaxException extends InvalidSelectorException {
    private final String query;
    private final String reason;
    private final int position;

    public ClassChainSyntaxException(String query, String reason, int position) {
        super(String.format("Cannot parse class chain query '%s'. %s at position %d",
                query, reason, position));
        this.query = query;
        this.reason = reason;
        this.position = position;
    }

    public String getQuery() {
        return query;
    }

    public String getReason() {
        return reason;
    }

    public int getPosition() {
        return position;
    }
}
