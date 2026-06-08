package com.oplus.pluskey.actions;

import android.content.Context;
import android.widget.Toast;

/**
 * Tiny wrapper around Toast that cancels the previously-shown toast before
 * displaying a new one. Without this, rapid Plus-Key repeats queue toasts
 * — by the time you've cycled silent → vibrate → ring (3 presses), the
 * "Silent" toast is still showing while the phone is actually on Ring.
 *
 * <p>Toast.cancel() requires a live Context-bound Toast reference, so we
 * keep one as a process-global field. The PlusKey app process stays alive
 * across broadcasts within a few seconds of each other, which is exactly
 * the window where queue collisions matter.
 */
public final class Feedback {
    private Feedback() {}

    private static Toast sCurrent;

    public static void show(Context ctx, int resId) {
        try { if (sCurrent != null) sCurrent.cancel(); } catch (Throwable ignored) {}
        sCurrent = Toast.makeText(ctx.getApplicationContext(), resId, Toast.LENGTH_SHORT);
        sCurrent.show();
    }

    public static void show(Context ctx, CharSequence text) {
        try { if (sCurrent != null) sCurrent.cancel(); } catch (Throwable ignored) {}
        sCurrent = Toast.makeText(ctx.getApplicationContext(), text, Toast.LENGTH_SHORT);
        sCurrent.show();
    }
}
