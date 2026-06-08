package com.oplus.pluskey.actions;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Curated haptic patterns for action feedback. */
public final class Haptics {
    private Haptics() {}

    private static final AudioAttributes ATTR = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    public static void tickOnce(Context ctx) {
        Vibrator v = ctx.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;
        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK), ATTR);
    }

    public static void tickTwice(Context ctx) {
        Vibrator v = ctx.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;
        v.vibrate(VibrationEffect.createWaveform(
                new long[]{0, 30, 80, 30}, new int[]{0, 180, 0, 180}, -1), ATTR);
    }

    public static void heavy(Context ctx) {
        Vibrator v = ctx.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;
        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK), ATTR);
    }

    /** Long, sustained buzz used for "Ring" mode confirmation — distinct
     *  from the short tick used for Silent and the double tick used for
     *  Vibrate, so the user can tell modes apart by feel alone. */
    public static void longBuzz(Context ctx) {
        Vibrator v = ctx.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;
        v.vibrate(VibrationEffect.createWaveform(
                new long[]{0, 220}, new int[]{0, 220}, -1), ATTR);
    }

    public static void confirm(Context ctx) {
        Vibrator v = ctx.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;
        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK), ATTR);
    }

    /** Sub-tick used by the chip row when the centred function changes — a
     *  premium "detent" feel like a knob clicking through positions. Soft
     *  enough to fire dozens per fling without being annoying. */
    public static void scrollDetent(Context ctx) {
        Vibrator v = ctx.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;
        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TEXTURE_TICK), ATTR);
    }
}
