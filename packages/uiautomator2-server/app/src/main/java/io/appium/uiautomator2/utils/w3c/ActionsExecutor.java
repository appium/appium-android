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

import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

import io.appium.uiautomator2.common.exceptions.InvalidArgumentException;
import io.appium.uiautomator2.common.exceptions.InvalidElementStateException;
import io.appium.uiautomator2.common.exceptions.StaleElementReferenceException;
import io.appium.uiautomator2.common.exceptions.UiAutomator2Exception;
import io.appium.uiautomator2.core.InteractionController;
import io.appium.uiautomator2.core.UiAutomatorBridge;
import io.appium.uiautomator2.model.settings.CurrentDisplayId;
import io.appium.uiautomator2.model.settings.Settings;
import io.appium.uiautomator2.utils.Logger;

import static io.appium.uiautomator2.model.internal.CustomUiDevice.getInstance;
import static io.appium.uiautomator2.core.AxNodeInfoHelper.recycleSafely;
import static io.appium.uiautomator2.utils.ElementHelpers.setText;
import static io.appium.uiautomator2.utils.ReflectionUtils.getMethod;
import static io.appium.uiautomator2.utils.ReflectionUtils.invoke;
import static io.appium.uiautomator2.utils.w3c.ActionHelpers.normalizeSequence;
import static io.appium.uiautomator2.utils.w3c.ActionsConstants.EVENT_INJECTION_DELAY_MS;

public class ActionsExecutor {
    private static final long TEXT_SETTLE_TIMEOUT_MS = 1000;
    private static final long TEXT_SETTLE_STABLE_MS = 150;
    private static final long TEXT_SETTLE_POLL_MS = 30;
    private static final long IME_SYNC_DELAY_MS = 150;
    private static final long FOCUS_WAIT_TIMEOUT_MS = 1000;
    private static final long FOCUS_WAIT_POLL_MS = 100;
    // Set when regular key events were injected after the last direct text insertion
    private boolean hasUnsettledKeyEvents = false;
    // The input method needs a moment to pick up the text and the cursor set by us
    private boolean isImeSyncPending = false;
    private final KeyCharacterMap keyCharacterMap;
    private static final List<Integer> HOVERING_ACTIONS = Arrays.asList(
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_EXIT, MotionEvent.ACTION_HOVER_MOVE
    );
    private static final long NANOSECONDS_IN_MILLISECOND = 1000000;
    private final ActionTokens actionTokens;
    private final InteractionController interactionController;

    public ActionsExecutor(ActionTokens actionTokens) {
        this.actionTokens = actionTokens;
        this.keyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        this.interactionController = UiAutomatorBridge.getInstance().getInteractionController();
    }

    private static boolean shouldIncludeEvent(MotionInputEventParams eventParams, boolean shouldHovering) {
        if (shouldHovering) {
            return HOVERING_ACTIONS.contains(eventParams.actionCode)
                    && eventParams.properties.toolType == MotionEvent.TOOL_TYPE_MOUSE;
        }
        return !HOVERING_ACTIONS.contains(eventParams.actionCode);
    }

    private static MotionEvent.PointerProperties[] filterPointerProperties(
            final List<MotionInputEventParams> motionEventsParams, final boolean shouldHovering) {
        return motionEventsParams.stream()
                .filter(eventParams -> shouldIncludeEvent(eventParams, shouldHovering))
                .map(eventParams -> eventParams.properties)
                .toArray(MotionEvent.PointerProperties[]::new);
    }

    private static MotionEvent.PointerCoords[] filterPointerCoordinates(
            final List<MotionInputEventParams> motionEventsParams, final boolean shouldHovering) {
        return motionEventsParams.stream()
                .filter(eventParams -> shouldIncludeEvent(eventParams, shouldHovering))
                .map(eventParams -> eventParams.coordinates)
                .toArray(MotionEvent.PointerCoords[]::new);
    }

    private static int metaKeysToState(final Set<Integer> metaKeys) {
        return metaKeys.stream()
                .reduce(0, (result, metaKey) -> result | metaKey);
    }

    private static void logEvent(Object event, long eventTime, boolean result) {
        Logger.info(String.format("[%s (%s)] Synthesized: %s", eventTime, result ? "success" : "fail", event.toString()));
        long currentTime = SystemClock.uptimeMillis();
        if (currentTime > eventTime + EVENT_INJECTION_DELAY_MS) {
            Logger.info(String.format("The event has been delayed for %sms", currentTime - eventTime));
        }
    }

    private static boolean isTypeableCharacter(int codePoint) {
        final int type = Character.getType(codePoint);
        return Character.isValidCodePoint(codePoint) && !Character.isISOControl(codePoint)
                && type != Character.PRIVATE_USE && type != Character.UNASSIGNED && type != Character.SURROGATE;
    }

    /**
     * Inserts a character that has no key event representation (e.g. Cyrillic, Arabic, emoji)
     * into the focused element at the current cursor position, replacing the selection if any.
     */
    private boolean insertCharacter(int codePoint, Set<Integer> depressedMetaKeys) {
        if (!isTypeableCharacter(codePoint)) {
            throw new InvalidArgumentException(String.format(
                    "The code point %s is neither a known W3C special key nor a printable character " +
                    "that could be typed", codePoint));
        }
        if (!depressedMetaKeys.isEmpty()) {
            throw new InvalidArgumentException(String.format(
                    "Cannot type the character with the code point %s while modifier keys are pressed",
                    codePoint));
        }
        final AccessibilityNodeInfo node = findFocusedEditableNode(codePoint);
        try {
            return insertCharacterIntoNode(node, codePoint);
        } finally {
            recycleSafely(node);
        }
    }

    private boolean insertCharacterIntoNode(AccessibilityNodeInfo node, int codePoint) {
        refreshNode(node, hasUnsettledKeyEvents);
        hasUnsettledKeyEvents = false;

        final boolean isHintShown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && node.isShowingHintText();
        final CharSequence currentText = node.getText();
        // An empty field reports no text and an unknown (-1) or zero selection
        if (node.isPassword() || (currentText == null && !isHintShown
                && Math.max(node.getTextSelectionStart(), node.getTextSelectionEnd()) > 0)) {
            // The content is masked or hidden, so replacing the text would corrupt it
            throw new InvalidElementStateException(String.format(
                    "Cannot type the character with the code point %s: the current text " +
                    "of the focused element is not exposed", codePoint));
        }
        final String text = currentText == null || isHintShown ? "" : currentText.toString();
        final int rawStart = isHintShown ? 0 : clamp(node.getTextSelectionStart(), text.length());
        final int rawEnd = isHintShown ? 0 : clamp(node.getTextSelectionEnd(), text.length());
        // The selection may be reversed if it has been made from right to left
        final int start = Math.min(rawStart, rawEnd);
        final int end = Math.max(rawStart, rawEnd);

        final String inserted = new String(Character.toChars(codePoint));
        final int maxLength = node.getMaxTextLength();
        if (maxLength > 0 && text.length() - (end - start) + inserted.length() > maxLength) {
            // Same as regular typing: the new character is dropped, the existing text is kept
            Logger.info(String.format("Not typing the character with the code point %s: " +
                    "the max text length %s would be exceeded", codePoint, maxLength));
            return true;
        }
        final String newText = text.substring(0, start) + inserted + text.substring(end);
        if (!setText(node, newText)) {
            return false;
        }
        // Setting the text moves the cursor to the end; put it right after the inserted character
        final int cursor = start + inserted.length();
        if (cursor < newText.length()) {
            final Bundle args = new Bundle();
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor);
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor);
            if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)) {
                throw new InvalidElementStateException(String.format(
                        "The character with the code point %s has been typed, but the cursor " +
                        "could not be moved to the position %s", codePoint, cursor));
            }
        }
        isImeSyncPending = true;
        return true;
    }

    /**
     * Looks for the input focus in the windows of the applications only, because the active window
     * (and the focused attribute) may point to the window of the input method.
     */
    @Nullable
    private static AccessibilityNodeInfo findInputFocusedNode() {
        final List<AccessibilityWindowInfo> windows = getInstance().getUiAutomation().getWindows();
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    continue;
                }
                final AccessibilityNodeInfo root = window.getRoot();
                try {
                            final AccessibilityNodeInfo node = root == null
                            ? null
                            : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    if (node != null) {
                        return node;
                    }
                } finally {
                    recycleSafely(root);
                }
            }
            return null;
        } finally {
            windows.forEach(window -> recycleSafely(window));
        }
    }

    /**
     * The focus may be moved to the element right after a click, so give it some time.
     */
    private static AccessibilityNodeInfo findFocusedEditableNode(int codePoint) {
        final long deadline = SystemClock.uptimeMillis() + FOCUS_WAIT_TIMEOUT_MS;
        while (true) {
            final AccessibilityNodeInfo node = findInputFocusedNode();
            if (node != null && node.isEditable()) {
                return node;
            }
            final boolean isFocused = node != null;
            recycleSafely(node);
            if (SystemClock.uptimeMillis() >= deadline) {
                throw new InvalidElementStateException(String.format(
                        "Cannot type the character with the code point %s: %s", codePoint,
                        isFocused ? "the focused element is not editable" : "no element is focused"));
            }
            SystemClock.sleep(FOCUS_WAIT_POLL_MS);
        }
    }

    /**
     * Reloads the node bypassing the accessibility cache, which may still return the state
     * from before our previous modification. Key events are delivered to the input method
     * asynchronously, so if there were any, wait until the text and the cursor stop changing.
     */
    private static void refreshNode(AccessibilityNodeInfo node, boolean waitForSettle) {
        final long deadline = SystemClock.uptimeMillis() + TEXT_SETTLE_TIMEOUT_MS;
        String lastState = null;
        long stableSince = SystemClock.uptimeMillis();
        do {
            if (!node.refresh()) {
                throw new StaleElementReferenceException();
            }
            if (!waitForSettle) {
                return;
            }
            final String state = node.getText() + "|" + node.getTextSelectionStart() + "|" + node.getTextSelectionEnd();
            final long now = SystemClock.uptimeMillis();
            if (!state.equals(lastState)) {
                lastState = state;
                stableSince = now;
            } else if (now - stableSince >= TEXT_SETTLE_STABLE_MS) {
                return;
            }
            SystemClock.sleep(TEXT_SETTLE_POLL_MS);
        } while (SystemClock.uptimeMillis() < deadline);
        Logger.warn(String.format("The text of the focused element did not settle in %sms", TEXT_SETTLE_TIMEOUT_MS));
    }

    private void syncImeIfNeeded() {
        if (isImeSyncPending) {
            isImeSyncPending = false;
            SystemClock.sleep(IME_SYNC_DELAY_MS);
        }
    }

    private static int clamp(int index, int length) {
        // Negative index means the cursor position is unknown, so append
        return index < 0 ? length : Math.min(index, length);
    }

    private boolean injectKeyEvent(KeyInputEventParams eventParam, long startTimestamp,
                                   Set<Integer> depressedMetaKeys) {
        final int keyCode = eventParam.keyCode;
        if (keyCode <= 0) {
            throw new InvalidArgumentException(String.format("'%s' is not a valid key code point", keyCode));
        }
        final int keyAction = eventParam.keyAction;

        boolean result = true;
        final W3CKeyCode w3CKeyCode = W3CKeyCode.fromCodePoint(keyCode);
        if (w3CKeyCode == null) {
            if (!Character.isValidCodePoint(keyCode)) {
                throw new InvalidArgumentException(String.format("'%s' is not a valid code point", keyCode));
            }
            final KeyEvent[] events = keyCharacterMap.getEvents(Character.toChars(keyCode));
            if (events == null) {
                // The virtual keyboard layout knows only about Latin characters
                // The character is typed on key down, same as regular keys, so the order of
                // characters is preserved when a client presses all the keys before releasing them
                return keyAction != KeyEvent.ACTION_DOWN || insertCharacter(keyCode, depressedMetaKeys);
            }
            for (KeyEvent event : events) {
                if (event.getAction() == keyAction) {
                    syncImeIfNeeded();
                    final long eventTime = SystemClock.uptimeMillis();
                    final KeyEvent keyEvent = new KeyEvent(startTimestamp + eventParam.startDelta,
                            eventTime, keyAction, event.getKeyCode(), 0,
                            event.getMetaState() | metaKeysToState(depressedMetaKeys),
                            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0);
                    result &= interactionController.injectEventSync(keyEvent);
                    logEvent(keyEvent, eventTime, result);
                    hasUnsettledKeyEvents = true;
                }
            }
            return result;
        }

        final Integer metaCode = w3CKeyCode.toAndroidMetaKeyCode();
        if (metaCode != null) {
            if (keyAction == KeyEvent.ACTION_DOWN) {
                depressedMetaKeys.add(metaCode);
            } else {
                depressedMetaKeys.remove(metaCode);
            }
            return true;
        }

        syncImeIfNeeded();
        final long eventTime = SystemClock.uptimeMillis();
        final KeyEvent keyEvent = new KeyEvent(startTimestamp + eventParam.startDelta,
                eventTime, keyAction, w3CKeyCode.getAndroidCodePoint(), 0,
                metaKeysToState(depressedMetaKeys), KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0);
        result = interactionController.injectEventSync(keyEvent);
        logEvent(keyEvent, eventTime, result);
        hasUnsettledKeyEvents = true;
        return result;
    }

    private boolean executeKeyEvents(List<KeyInputEventParams> events, long startTimestamp,
                                     Set<Integer> depressedMetaKeys) {
        return events.stream()
                .allMatch(event -> injectKeyEvent(event, startTimestamp, depressedMetaKeys));
    }

    private static int toolTypeToInputSource(final int toolType) {
        switch (toolType) {
            case MotionEvent.TOOL_TYPE_MOUSE:
                return InputDevice.SOURCE_MOUSE;
            case MotionEvent.TOOL_TYPE_STYLUS:
                return InputDevice.SOURCE_STYLUS;
            default:
                return InputDevice.SOURCE_TOUCHSCREEN;
        }
    }

    private static int extractInputSource(List<MotionInputEventParams> events) {
        return events.stream()
                .map(event -> toolTypeToInputSource(event.properties.toolType))
                .findFirst()
                .orElse(InputDevice.SOURCE_TOUCHSCREEN);
    }

    private static int getInitialPointersCount(List<MotionInputEventParams> events) {
        final Set<Integer> uniquePointerIds = new HashSet<>();
        for (MotionInputEventParams event : events) {
            if (event.actionCode == MotionEvent.ACTION_MOVE || event.actionCode == MotionEvent.ACTION_UP) {
                uniquePointerIds.add(event.properties.id);
            }
        }
        for (MotionInputEventParams event : events) {
            if (event.actionCode == MotionEvent.ACTION_DOWN) {
                uniquePointerIds.remove(event.properties.id);
            }
        }
        return uniquePointerIds.size();
    }

    private static int getPointerAction(int motionEvent, int index) {
        return motionEvent + (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    }

    private boolean executeMotionEvents(List<MotionInputEventParams> events, long startTimestamp, Set<Integer> depressedMetaKeys) {
        final MotionEvent.PointerProperties[] nonHoveringProps = filterPointerProperties(events, false);
        final MotionEvent.PointerProperties[] hoveringProps = filterPointerProperties(events, true);
        final MotionEvent.PointerCoords[] nonHoveringCoords = filterPointerCoordinates(events, false);
        final MotionEvent.PointerCoords[] hoveringCoords = filterPointerCoordinates(events, true);
        final int inputSource = extractInputSource(events);
        final int metaState = metaKeysToState(depressedMetaKeys);
        int pointersCount = getInitialPointersCount(events);
        boolean result = true;
        boolean isMoveActionTriggered = false;
        for (final MotionInputEventParams event : normalizeSequence(events)) {
            final int actionCode = event.actionCode;
            final long downTime = startTimestamp + event.startDelta;
            final long eventTime = SystemClock.uptimeMillis();
            MotionEvent synthesizedEvent = null;
            switch (actionCode) {
                case MotionEvent.ACTION_DOWN: {
                    final int action = ++pointersCount <= 1
                            ? MotionEvent.ACTION_DOWN
                            : getPointerAction(MotionEvent.ACTION_POINTER_DOWN, event.properties.id);
                    synthesizedEvent = MotionEvent.obtain(downTime, eventTime, action, pointersCount,
                            nonHoveringProps, nonHoveringCoords,
                            metaState, event.button, 1, 1, 0, 0, inputSource, 0);
                }
                break;
                case MotionEvent.ACTION_UP: {
                    final int action = pointersCount <= 1
                            ? MotionEvent.ACTION_UP
                            : getPointerAction(MotionEvent.ACTION_POINTER_UP, event.properties.id);
                    synthesizedEvent = MotionEvent.obtain(downTime, eventTime, action, pointersCount--,
                            nonHoveringProps, nonHoveringCoords,
                            metaState, event.button, 1, 1, 0, 0, inputSource, 0);
                }
                break;
                case MotionEvent.ACTION_MOVE: {
                    if (isMoveActionTriggered) {
                        break;
                    }
                    synthesizedEvent = MotionEvent.obtain(downTime, eventTime, actionCode, pointersCount,
                            nonHoveringProps, nonHoveringCoords,
                            metaState, event.button, 1, 1, 0, 0, inputSource, 0);
                    isMoveActionTriggered = true;
                }
                break;
                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_EXIT:
                case MotionEvent.ACTION_HOVER_MOVE: {
                    synthesizedEvent = MotionEvent.obtain(downTime, eventTime, actionCode, 1,
                            hoveringProps, hoveringCoords,
                            metaState, 0, 1, 1, 0, 0, inputSource, 0);
                }
                break;
                default:
                    // do nothing
                    break;
            } // switch
            if (synthesizedEvent != null) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    final int displayId = Settings.get(CurrentDisplayId.class).getValue();
                    try {
                        // Method is marked as public with @hide in AOSP https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/view/MotionEvent.java;l=2419;drc=61197364367c9e404c7da6900658f1b16c42d0da;bpv=1;bpt=1?q=MotionEvent
                        invoke(getMethod(synthesizedEvent.getClass(), "setDisplayId", int.class), synthesizedEvent, displayId);
                    } catch (UiAutomator2Exception e) {
                        Logger.error("Unable to set displayId on motion event", e);
                    }
                }

                result &= interactionController.injectEventSync(synthesizedEvent);
                logEvent(synthesizedEvent, eventTime, result);
                synthesizedEvent.recycle();
            }
        }
        return result;
    }

    private static void sleepTillNextEvent(long nextEventTimestampNs) {
        // LockSupport.parkNanos is precise, unlike Thread.sleep
        LockSupport.parkNanos(nextEventTimestampNs - System.nanoTime());
    }

    public boolean execute() {
        if (actionTokens.isEmpty()) {
            return true;
        }

        boolean result = true;
        final Set<Integer> depressedMetaKeys = new HashSet<>();
        final long startTimestampMs = SystemClock.uptimeMillis();
        final long startTimestampNs = System.nanoTime();
        final long maxDeltaMs = actionTokens.maxTimeDelta();
        Logger.debug(String.format("Max actions chain time delta: %sms", maxDeltaMs));
        for (long currentDeltaMs = 0; currentDeltaMs <= maxDeltaMs; currentDeltaMs += EVENT_INJECTION_DELAY_MS) {
            final List<InputEventParams> events = actionTokens.eventsAt(currentDeltaMs);
            final long nextEventTimestampNs = (
                    startTimestampNs + (currentDeltaMs + EVENT_INJECTION_DELAY_MS) * NANOSECONDS_IN_MILLISECOND
            );
            if (events == null || events.isEmpty()) {
                sleepTillNextEvent(nextEventTimestampNs);
                continue;
            }

            final List<MotionInputEventParams> motionEvents = new ArrayList<>();
            final List<KeyInputEventParams> keyEvents = new ArrayList<>();
            for (final InputEventParams eventParam : events) {
                if (eventParam instanceof KeyInputEventParams) {
                    keyEvents.add((KeyInputEventParams) eventParam);
                } else if (eventParam instanceof MotionInputEventParams) {
                    motionEvents.add((MotionInputEventParams) eventParam);
                }
            }
            if (!keyEvents.isEmpty()) {
                result &= executeKeyEvents(keyEvents, startTimestampMs, depressedMetaKeys);
            }
            if (!motionEvents.isEmpty()) {
                result &= executeMotionEvents(motionEvents, startTimestampMs, depressedMetaKeys);
            }

            sleepTillNextEvent(nextEventTimestampNs);
        }
        return result;
    }
}
