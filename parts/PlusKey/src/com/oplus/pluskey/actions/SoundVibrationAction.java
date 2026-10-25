package com.oplus.pluskey.actions;

import android.content.Context;
import android.media.AudioManager;

import com.oplus.pluskey.R;

/** Cycles the actual ringer mode: RING -> VIBRATE -> SILENT -> RING. */
public class SoundVibrationAction implements Action {
    @Override
    public void run(Context ctx) {
        AudioManager am = ctx.getSystemService(AudioManager.class);
        if (am == null) return;

        // The external ringer API represents silent through ZenModeHelper and can enable DND.
        // PlusKey runs as the system UID: use the same internal API as the volume controller.
        switch (am.getRingerModeInternal()) {
            case AudioManager.RINGER_MODE_NORMAL:
                am.setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
                Haptics.tickTwice(ctx);
                Feedback.show(ctx, R.string.feedback_vibrate);
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                am.setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
                Haptics.tickOnce(ctx);
                Feedback.show(ctx, R.string.feedback_silent);
                break;
            case AudioManager.RINGER_MODE_SILENT:
            default:
                am.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                Haptics.longBuzz(ctx);
                Feedback.show(ctx, R.string.feedback_ring);
                break;
        }
    }
}
