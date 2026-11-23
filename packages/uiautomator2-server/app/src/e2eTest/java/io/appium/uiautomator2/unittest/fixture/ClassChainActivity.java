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


package io.appium.uiautomator2.unittest.fixture;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A fixed hierarchy for testing class chain locators:
 * <pre>
 * chain-root
 *   chain-header "Settings"
 *   chain-list
 *     chain-row-0: chain-label-0 "Wi-Fi", chain-check-0 (checked)
 *     chain-row-1: chain-label-1 "Bluetooth", chain-check-1
 *     chain-row-2: chain-label-2 "Café", chain-amount "9.99", chain-buy "Buy"
 *   chain-sized (300x80 px)
 *   chain-footer: chain-ok "OK", chain-cancel "Cancel" (disabled)
 * </pre>
 */
public class ClassChainActivity extends Activity {
    // Fixed strings are intentional: this screen is only a test fixture.
    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = linear(this, LinearLayout.VERTICAL, "chain-root");
        root.addView(text(this, "Settings", "chain-header"));

        LinearLayout list = linear(this, LinearLayout.VERTICAL, "chain-list");
        list.addView(row(this, 0, text(this, "Wi-Fi", "chain-label-0"), check(this, 0, true)));
        list.addView(row(this, 1, text(this, "Bluetooth", "chain-label-1"), check(this, 1, false)));
        list.addView(row(this, 2, text(this, "Café", "chain-label-2"),
                text(this, "9.99", "chain-amount"), button(this, "Buy", "chain-buy", true)));
        root.addView(list);

        TextView sized = text(this, "Sized", "chain-sized");
        sized.setLayoutParams(new ViewGroup.LayoutParams(300, 80));
        root.addView(sized);

        LinearLayout footer = linear(this, LinearLayout.HORIZONTAL, "chain-footer");
        footer.addView(button(this, "OK", "chain-ok", true));
        footer.addView(button(this, "Cancel", "chain-cancel", false));
        root.addView(footer);
        setContentView(root);
    }

    private static LinearLayout linear(Context context, int orientation, String description) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(orientation);
        layout.setContentDescription(description);
        // a plain container is left out of the accessibility tree otherwise
        layout.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return layout;
    }

    private static LinearLayout row(Context context, int index, View... children) {
        LinearLayout row = linear(context, LinearLayout.HORIZONTAL, "chain-row-" + index);
        for (View child : children) {
            row.addView(child);
        }
        return row;
    }

    private static TextView text(Context context, String text, String description) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setContentDescription(description);
        return view;
    }

    private static CheckBox check(Context context, int index, boolean checked) {
        CheckBox view = new CheckBox(context);
        view.setChecked(checked);
        view.setContentDescription("chain-check-" + index);
        return view;
    }

    private static Button button(Context context, String text, String description,
                                 boolean enabled) {
        Button view = new Button(context);
        view.setText(text);
        view.setContentDescription(description);
        view.setEnabled(enabled);
        return view;
    }
}
