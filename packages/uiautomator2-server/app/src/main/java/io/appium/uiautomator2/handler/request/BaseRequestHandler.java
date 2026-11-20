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

package io.appium.uiautomator2.handler.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.UiObjectNotFoundException;

import java.util.ArrayList;
import java.util.List;

import io.appium.uiautomator2.http.AppiumResponse;
import io.appium.uiautomator2.http.IHttpRequest;
import io.appium.uiautomator2.server.AppiumServlet;

public abstract class BaseRequestHandler {

    private final String mappedUri;

    public BaseRequestHandler(String mappedUri) {
        this.mappedUri = mappedUri;
    }

    public String getMappedUri() {
        return mappedUri;
    }

    @Nullable
    private static <T> T extractRequestValue(IHttpRequest request, String key,
                                             @SuppressWarnings("SameParameterValue") Class<T> valueClass) {
        Object result = request.data().get(key);
        return result == null ? null : valueClass.cast(result);
    }

    @Nullable
    public static String getElementId(IHttpRequest request) {
        return extractRequestValue(request, AppiumServlet.ELEMENT_ID_KEY, String.class);
    }

    protected static String[] getElementIds(IHttpRequest request) {
        List<String> result = new ArrayList<>();
        String firstElementId = getElementId(request);
        result.add(firstElementId);
        for (int elementIdx = AppiumServlet.SECOND_ELEMENT_IDX;
             elementIdx < AppiumServlet.MAX_ELEMENTS + AppiumServlet.SECOND_ELEMENT_IDX; ++elementIdx) {
            String elementId = extractRequestValue(request, AppiumServlet.ELEMENT_ID_KEY + elementIdx, String.class);
            result.add(elementId);
         }
        return result.toArray(new String[0]);
    }

    @Nullable
    protected static String getNameAttribute(IHttpRequest request) {
        return extractRequestValue(request, AppiumServlet.NAME_ID_KEY, String.class);
    }

    @Nullable
    public static String getSessionId(IHttpRequest request) {
        return (String) request.data().get(AppiumServlet.SESSION_ID_KEY);
    }

    @NonNull
    public abstract AppiumResponse handle(IHttpRequest request);

    @NonNull
    protected AppiumResponse safeHandle(IHttpRequest request) throws UiObjectNotFoundException {
        return handle(request);
    }
}
