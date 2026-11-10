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

package io.appium.uiautomator2.model.settings;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class SettingsTests {
    private static class UnknownSetting extends AbstractSetting<Boolean> {
        UnknownSetting() {
            super(Boolean.class, "unknownSetting");
        }

        @Override
        public Boolean getValue() {
            return false;
        }

        @Override
        public Boolean getDefaultValue() {
            return false;
        }

        @Override
        protected void apply(Boolean value) {
        }
    }

    @Test
    public void shouldReturnTheRegisteredInstanceForEverySetting() {
        for (Settings item : Settings.values()) {
            ISetting<?> expected = item.getSetting();
            Assert.assertSame(expected, Settings.get(expected.getClass()));
        }
    }

    @Test
    public void shouldRegisterEverySettingTypeOnlyOnce() {
        Set<Class<?>> types = new HashSet<>();
        for (Settings item : Settings.values()) {
            Assert.assertTrue(
                    item + " shares its setting type with another item",
                    types.add(item.getSetting().getClass())
            );
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForUnknownSettingType() {
        Settings.get(UnknownSetting.class);
    }
}
