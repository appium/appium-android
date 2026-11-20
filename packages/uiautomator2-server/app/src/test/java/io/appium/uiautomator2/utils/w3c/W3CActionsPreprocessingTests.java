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

package io.appium.uiautomator2.utils.w3c;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import io.appium.uiautomator2.model.api.actions.W3CItemModel;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class W3CActionsPreprocessingTests {
    private static final ActionsPreprocessor actionsPreprocessor = new ActionsPreprocessor();

    private static List<W3CItemModel> toActionItems(String json) {
        //noinspection unchecked
        return (List<W3CItemModel>) new Gson().fromJson(json, new TypeToken<List<W3CItemModel>>() { }.getType());
    }

    @Test
    public void verifyInvalidActionChainsPreprocessing() {
        final List<String> invalidActions = Arrays.asList(
                // missing action id
                "[ {" +
                        "\"type\": \"pointer\"," +
                        "\"parameters\": {\"pointerType\": \"touch\"}," +
                        "\"actions\": [" +
                        "{\"type\": \"pointerMove\", \"duration\": 0, \"x\": 100, \"y\": 100}," +
                        "{\"type\": \"pointerDown\", \"button\": 0}," +
                        "{\"type\": \"pause\", \"duration\": 500}," +
                        "{\"type\": \"pointerMove\", \"duration\": 1000, \"origin\": \"pointer\", \"x\": -50, \"y\": 0}," +
                        "{\"type\": \"pointerUp\", \"button\": 0}]" +
                        "} ]",

                // missing type
                "[ {" +
                        "\"id\": \"finger1\"," +
                        "\"parameters\": {\"pointerType\": \"touch\"}," +
                        "\"actions\": [" +
                        "{\"type\": \"pointerMove\", \"duration\": 0, \"x\": 100, \"y\": 100}," +
                        "{\"type\": \"pointerDown\", \"button\": 0}," +
                        "{\"type\": \"pause\", \"duration\": 500}," +
                        "{\"type\": \"pointerMove\", \"duration\": 1000, \"origin\": \"pointer\", \"x\": -50, \"y\": 0}," +
                        "{\"type\": \"pointerUp\", \"button\": 0}]" +
                        "} ]",

                // unknown type
                "[ {" +
                        "\"type\": \"bla\"," +
                        "\"id\": \"finger1\"," +
                        "\"parameters\": {\"pointerType\": \"touch\"}," +
                        "\"actions\": [" +
                        "{\"type\": \"pointerMove\", \"duration\": 0, \"x\": 100, \"y\": 100}," +
                        "{\"type\": \"pointerDown\", \"button\": 0}," +
                        "{\"type\": \"pause\", \"duration\": 500}," +
                        "{\"type\": \"pointerMove\", \"duration\": 1000, \"origin\": \"pointer\", \"x\": -50, \"y\": 0}," +
                        "{\"type\": \"pointerUp\", \"button\": 0}]" +
                        "} ]",

                // missing action items
                "[ {" +
                        "\"type\": \"bla\"," +
                        "\"id\": \"finger1\"," +
                        "\"parameters\": {\"pointerType\": \"touch\"}" +
                        "} ]",

                // unknown pointer type
                "[ {" +
                        "\"type\": \"pointer\"," +
                        "\"id\": \"finger1\"," +
                        "\"parameters\": {\"pointerType\": \"bla\"}," +
                        "\"actions\": [" +
                        "{\"type\": \"pointerMove\", \"duration\": 0, \"x\": 100, \"y\": 100}," +
                        "{\"type\": \"pointerDown\", \"button\": 0}," +
                        "{\"type\": \"pause\", \"duration\": 500}," +
                        "{\"type\": \"pointerMove\", \"duration\": 1000, \"origin\": \"pointer\", \"x\": -50, \"y\": 0}," +
                        "{\"type\": \"pointerUp\", \"button\": 0}]" +
                        "} ]",

                // non-matching pointer type
                "[ {" +
                        "\"type\": \"key\"," +
                        "\"id\": \"finger1\"," +
                        "\"parameters\": {\"pointerType\": \"touch\"}," +
                        "\"actions\": [" +
                        "{\"type\": \"pointerMove\", \"duration\": 0, \"x\": 100, \"y\": 100}," +
                        "{\"type\": \"pointerDown\", \"button\": 0}," +
                        "{\"type\": \"pause\", \"duration\": 500}," +
                        "{\"type\": \"pointerMove\", \"duration\": 1000, \"origin\": \"pointer\", \"x\": -50, \"y\": 0}," +
                        "{\"type\": \"pointerUp\", \"button\": 0}]" +
                        "} ]",

                // duplicated action id
                "[ {" +
                        "\"id\": \"finger1\"," +
                        "\"type\": \"pointer\"," +
                        "\"parameters\": {\"pointerType\": \"touch\"}," +
                        "\"actions\": [" +
                        "{\"type\": \"pointerMove\", \"duration\": 0, \"x\": 100, \"y\": 100}," +
                        "{\"type\": \"pointerDown\", \"button\": 0}," +
                        "{\"type\": \"pause\", \"duration\": 500}," +
                        "{\"type\": \"pointerMove\", \"duration\": 1000, \"origin\": \"pointer\", \"x\": -50, \"y\": 0}," +
                        "{\"type\": \"pointerUp\", \"button\": 0}]" +
                        "}, {" +
                        "\"id\": \"finger1\"," +
                        "\"type\": \"pointer\"," +
                        "\"parameters\": {\"pointerType\": \"touch\"}," +
                        "\"actions\": [" +
                        "{\"type\": \"pointerMove\", \"duration\": 0, \"x\": 100, \"y\": 100}," +
                        "{\"type\": \"pointerDown\", \"button\": 0}," +
                        "{\"type\": \"pause\", \"duration\": 500}," +
                        "{\"type\": \"pointerMove\", \"duration\": 1000, \"origin\": \"pointer\", \"x\": -50, \"y\": 0}," +
                        "{\"type\": \"pointerUp\", \"button\": 0}]" +
                        "} ]"
        );
        for (final String invalidAction : invalidActions) {
            try {
                actionsPreprocessor.preprocess(toActionItems(invalidAction));
                fail(String.format("'%s' should throw an exception", invalidAction));
            } catch (ActionsParseException e) {
                // expected
            }
        }
    }

    @Test
    public void verifyWheelInputSourceIsRejectedWithHint() {
        final String wheelAction = "[ {" +
                "\"type\": \"wheel\"," +
                "\"id\": \"w\"," +
                "\"actions\": [" +
                "{\"type\": \"scroll\", \"x\": 100, \"y\": 100, \"deltaX\": 0, \"deltaY\": 300}]" +
                "} ]";
        try {
            actionsPreprocessor.preprocess(toActionItems(wheelAction));
            fail("The wheel input source should be rejected");
        } catch (ActionsParseException e) {
            assertThat(e.getMessage().contains("'wheel'"), is(true));
            assertThat(e.getMessage().contains("mobile: scrollGesture"), is(true));
        }
    }

    @Test
    public void verifyValidActionChainPreprocessingWithoutChange() {
        final String actionJson = "[ {" +
                "\"type\": \"pointer\"," +
                "\"id\": \"finger1\"," +
                "\"parameters\": {\"pointerType\": \"touch\"}," +
                "\"actions\": [" +
                "{\"type\": \"pointerMove\", \"duration\": 0, \"x\": 100, \"y\": 100}," +
                "{\"type\": \"pointerDown\", \"button\": 0}," +
                "{\"type\": \"pause\", \"duration\": 500}," +
                "{\"type\": \"pointerMove\", \"duration\": 1000, \"origin\": \"pointer\", \"x\": -50, \"y\": 0}," +
                "{\"type\": \"pointerUp\", \"button\": 0}]" +
                "} ]";
        assertThat(actionsPreprocessor.preprocess(toActionItems(actionJson)).get(0).actions.size(), is(equalTo(5)));
    }

    @Test
    public void verifyValidActionChainPreprocessingWithCancelChange() {
        final String actionJson = "[ {" +
                "\"type\": \"pointer\"," +
                "\"id\": \"finger1\"," +
                "\"parameters\": {\"pointerType\": \"touch\"}," +
                "\"actions\": [" +
                "{\"type\": \"pointerMove\", \"duration\": 0, \"x\": 100, \"y\": 100}," +
                "{\"type\": \"pointerCancel\"}," +
                "{\"type\": \"pause\", \"duration\": 500}," +
                "{\"type\": \"pointerMove\", \"duration\": 1000, \"origin\": \"pointer\", \"x\": -50, \"y\": 0}," +
                "{\"type\": \"pointerUp\", \"button\": 0}]" +
                "} ]";
        assertThat(actionsPreprocessor.preprocess(toActionItems(actionJson)).get(0).actions.size(), is(equalTo(3)));
    }

    @Test(expected = ActionsParseException.class)
    public void verifyChainPreprocessingFailsIfMultiplePointerTypesArePresent() {
        final String actionJson = "[ {" +
                "\"type\": \"pointer\"," +
                "\"id\": \"finger1\"," +
                "\"parameters\": {\"pointerType\": \"touch\"}," +
                "\"actions\": [" +
                "{\"type\": \"pause\", \"duration\": 500}," +
                "{\"type\": \"pointerMove\", \"duration\": 1000, \"origin\": \"pointer\", \"x\": -50, \"y\": 0}," +
                "{\"type\": \"pointerUp\", \"button\": 0}]" +
                "}, {" +
                "\"type\": \"pointer\"," +
                "\"id\": \"finger2\"," +
                "\"parameters\": {\"pointerType\": \"mouse\"}," +
                "\"actions\": [" +
                "{\"type\": \"pause\", \"duration\": 500}," +
                "{\"type\": \"pointerMove\", \"duration\": 1000, \"origin\": \"pointer\", \"x\": -50, \"y\": 0}," +
                "{\"type\": \"pointerUp\", \"button\": 0}]" +
                "} ]";
        actionsPreprocessor.preprocess(toActionItems(actionJson));
    }

}
