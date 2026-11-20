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
package io.appium.uiautomator2.unittest.test.internal;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import io.netty.handler.codec.http.HttpResponseStatus;

public class Response {

    private static final String ERR_MSG = "Unable to extract '%s' from body '%s'.";
    private static final Gson GSON = new Gson();

    private final String body;
    private final int code;

    public Response(final int code, final String body) {
        this.code = code;
        this.body = body;
    }

    public boolean isSuccessful() {
        return code == HttpResponseStatus.OK.code();
    }

    @Nullable
    public String getElementId() {
        try {
            return TestUtils.extractElementId(parseValue().getAsJsonObject());
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException(String.format(ERR_MSG, "element", body), e);
        }
    }

    public int code() {
        return code;
    }

    public <T> T getValue(Class<T> type) {
        try {
            return GSON.fromJson(parseValue(), type);
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException(String.format(ERR_MSG, "value", body), e);
        }
    }

    private JsonElement parseValue() {
        final JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("value")) {
            throw new IllegalStateException("No 'value' key");
        }
        return root.get("value");
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return String.format("Code:%d; Body:%s;", code, body);
    }
}
