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

package io.appium.uiautomator2;

import org.junit.rules.ExternalResource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Registers static mocks for a test and closes them after it,
 * since a {@link MockedStatic} must not outlive the thread-local scope it was created in.
 */
public class StaticMocks extends ExternalResource {
    private final Deque<MockedStatic<?>> mocks = new ArrayDeque<>();

    public <T> MockedStatic<T> mock(Class<T> cls) {
        MockedStatic<T> mock = Mockito.mockStatic(cls);
        mocks.push(mock);
        return mock;
    }

    @Override
    protected void after() {
        while (!mocks.isEmpty()) {
            mocks.pop().close();
        }
    }
}
