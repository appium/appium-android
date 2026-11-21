/*
 * Copyright (C) 2013 DroidDriver committers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.appium.uiautomator2.utils;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum Attribute {
    CHECKABLE(Type.BOOLEAN, new String[]{"checkable"}),
    CHECKED(Type.BOOLEAN, new String[]{"checked"}),
    CLASS(Type.STRING, new String[]{"class", "className"}),
    CLICKABLE(Type.BOOLEAN, new String[]{"clickable"}),
    CONTENT_DESC(Type.STRING, new String[]{"content-desc", "contentDescription"}),
    ENABLED(Type.BOOLEAN, new String[]{"enabled"}),
    FOCUSABLE(Type.BOOLEAN, new String[]{"focusable"}),
    FOCUSED(Type.BOOLEAN, new String[]{"focused"}),
    LONG_CLICKABLE(Type.BOOLEAN, new String[]{"long-clickable", "longClickable"}),
    PACKAGE(Type.STRING, new String[]{"package"}),
    PASSWORD(Type.BOOLEAN, new String[]{"password"}),
    RESOURCE_ID(Type.STRING, new String[]{"resource-id", "resourceId"}),
    SCROLLABLE(Type.BOOLEAN, new String[]{"scrollable"}),
    SELECTION_START(Type.NUMBER, new String[]{"selection-start"}),
    SELECTION_END(Type.NUMBER, new String[]{"selection-end"}),
    SELECTED(Type.BOOLEAN, new String[]{"selected"}),
    TEXT(Type.STRING, new String[]{"text", "name"}),
    HINT(Type.STRING, new String[]{"hint"}),
    EXTRAS(Type.STRING, new String[]{"extras"}),

    // The main difference of this attribute from the preceding one is that
    // it does not replace null values with empty strings
    ORIGINAL_TEXT(Type.STRING, new String[]{"original-text"}, false, false),
    BOUNDS(Type.STRING, new String[]{"bounds"}),
    INDEX(Type.NUMBER, new String[]{"index"}, false, true),
    DISPLAYED(Type.BOOLEAN, new String[]{"displayed"}),
    CONTENT_SIZE(Type.STRING, new String[]{"contentSize"}, true, false),

    IMPORTANT_FOR_ACCESSIBILITY(Type.BOOLEAN,
            new String[]{"a11y-important", "importantForAccessibility"}),
    SCREEN_READER_FOCUSABLE(Type.BOOLEAN,
            new String[]{"screen-reader-focusable", "screenReaderFocusable"}),
    INPUT_TYPE(Type.NUMBER, new String[]{"input-type", "inputType"}),
    DRAWING_ORDER(Type.NUMBER, new String[]{"drawing-order", "drawingOrder"}),
    SHOWING_HINT_TEXT(Type.BOOLEAN, new String[]{"showing-hint", "showingHintText"}),
    TEXT_ENTRY_KEY(Type.BOOLEAN, new String[]{"text-entry-key", "textEntryKey"}),
    MULTI_LINE(Type.BOOLEAN, new String[]{"multiline", "multiLine"}),
    DISMISSABLE(Type.BOOLEAN, new String[]{"dismissable"}),
    ACCESSIBILITY_FOCUSED(Type.BOOLEAN, new String[]{"a11y-focused", "accessibilityFocused"}),
    HEADING(Type.BOOLEAN, new String[]{"heading"}),
    LIVE_REGION(Type.NUMBER, new String[]{"live-region", "liveRegion"}),
    CONTEXT_CLICKABLE(Type.BOOLEAN, new String[]{"context-clickable", "contextClickable"}),
    MAX_TEXT_LENGTH(Type.NUMBER, new String[]{"max-text-length", "maxTextLength"}),
    CONTENT_INVALID(Type.BOOLEAN, new String[]{"content-invalid", "contentInvalid"}),
    ERROR_TEXT(Type.STRING, new String[]{"error", "errorText"}),
    PANE_TITLE(Type.STRING, new String[]{"pane-title", "paneTitle"}),
    TOOLTIP_TEXT(Type.STRING, new String[]{"tooltip-text", "tooltipText"}),
    TEXT_HAS_CLICKABLE_SPAN(Type.BOOLEAN,
            new String[]{"text-has-clickable-span", "textHasClickableSpan"}),
    ACTIONS(Type.STRING, new String[]{"actions"}),
    WINDOW_ID(Type.NUMBER, new String[]{"window-id", "windowId"}),
    TEXT_SIZE(Type.NUMBER, new String[]{"text-size", "textSize"}),
    TEXT_UNIT(Type.STRING, new String[]{"text-unit", "textUnit"}),
    IS_COLLECTION(Type.BOOLEAN, new String[]{"is-collection", "isCollection"}),
    IS_COLLECTION_ITEM(Type.BOOLEAN, new String[]{"is-collection-item", "isCollectionItem"}),

    // These are only available on demand via getAttribute, and are never included in the page source
    COLLECTION_INFO(Type.STRING, new String[]{"collection-info", "collectionInfo"}, true, false),
    COLLECTION_ITEM_INFO(Type.STRING, new String[]{"collection-item-info", "collectionItemInfo"}, true, false);

    /** The type of the value, which is used to validate comparisons in predicates */
    public enum Type {
        STRING, NUMBER, BOOLEAN
    }

    private final Type type;
    private final String[] aliases;
    // Defines if the attribute is visible to the user from getAttribute call
    private final boolean isExposable;
    // Defines if the attribute is visible to the user in the xml tree/xpath search
    private final boolean isExposableToXml;

    Attribute(Type type, String[] aliases) {
        this.type = type;
        this.aliases = aliases;
        this.isExposable = true;
        this.isExposableToXml = true;
    }

    Attribute(Type type, String[] aliases, boolean isExposable, boolean isExposableToXml) {
        this.type = type;
        this.aliases = aliases;
        this.isExposable = isExposable;
        this.isExposableToXml = isExposableToXml;
    }

    public String getName() {
        return aliases[0];
    }

    public Type getType() {
        return type;
    }

    public String[] getAliases() {
        return aliases.clone();
    }

    public boolean isExposableToXml() {
        return isExposableToXml;
    }

    @Override
    public String toString() {
        return aliases[0];
    }

    public static String[] exposableAliases() {
        return Arrays.stream(Attribute.values())
                .filter(attribute -> attribute.isExposable)
                .map(attribute -> attribute.aliases.length > 1
                        ? String.format("{%s}", TextUtils.join(",", attribute.aliases))
                        : attribute.aliases[0])
                .toArray(String[]::new);
    }

    public static Set<Attribute> xmlExposableAttributes() {
        return Arrays.stream(Attribute.values())
                .filter(attribute -> attribute.isExposableToXml)
                .collect(Collectors.toSet());
    }

    @Nullable
    public static Attribute fromString(String alias) {
        if (alias == null) {
            return null;
        }

        return Arrays.stream(Attribute.values())
                .filter(attribute -> Arrays.stream(attribute.aliases)
                        .anyMatch(attrAlias -> attrAlias.equalsIgnoreCase(alias)))
                .findFirst()
                .orElse(null);
    }
}
