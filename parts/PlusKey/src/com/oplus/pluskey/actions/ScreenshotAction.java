package com.oplus.pluskey.actions;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.oplus.pluskey.Constants;

import java.lang.reflect.Method;

/**
 * Trigger the system screenshot. Tries the most reliable paths in order:
 *
 * <ol>
 *   <li><b>InputManager.injectInputEvent</b> via reflection — fastest, no
 *       process fork. Goes through the standard input pipeline so PWM's
 *       screenshot interceptor fires every call.</li>
 *   <li><b>Runtime.exec("input keyevent KEYCODE_SYSRQ")</b> — fallback if
 *       reflection fails. Forks /system/bin/input which then injects via
 *       cmd input keyevent. Slightly slower but works on every Android
 *       since the input cmd existed.</li>
 * </ol>
 *
 * <p>The earlier {@link android.app.StatusBarManager#handleSystemKey}
 * approach was tried but was unreliable on Android 16: the first call
 * triggered SystemUI's screenshot, subsequent calls silently no-op'd.
 * Confirmed via logcat: ScreenshotUI window appeared once, then
 * handleSystemKey calls produced no SystemUI log lines at all.
 */
public class ScreenshotAction implements Action {
    @Override
    public void run(Context ctx) {
        if (injectViaInputManager()) return;
        try {
            Runtime.getRuntime().exec(
                    new String[]{"input", "keyevent", String.valueOf(KeyEvent.KEYCODE_SYSRQ)});
        } catch (Throwable t) {
            Log.e(Constants.TAG, "screenshot via `input keyevent` failed", t);
        }
    }

    /** @return true if both ACTION_DOWN and ACTION_UP injection succeeded. */
    private static boolean injectViaInputManager() {
        try {
            // hidden API: InputManager.getInstance().injectInputEvent(event, mode)
            Class<?> imCls = Class.forName("android.hardware.input.InputManager");
            Method getInstance = imCls.getMethod("getInstance");
            Object im = getInstance.invoke(null);
            Method inject = imCls.getMethod("injectInputEvent", KeyEvent.class, int.class);
            // INJECT_INPUT_EVENT_MODE_ASYNC = 0
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now,
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SYSRQ, 0,
                    0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM, android.view.InputDevice.SOURCE_KEYBOARD);
            KeyEvent up = KeyEvent.changeAction(down, KeyEvent.ACTION_UP);
            inject.invoke(im, down, 0);
            inject.invoke(im, up, 0);
            return true;
        } catch (Throwable t) {
            Log.w(Constants.TAG, "screenshot reflection failed, falling back to `input` cmd", t);
            return false;
        }
    }
}
