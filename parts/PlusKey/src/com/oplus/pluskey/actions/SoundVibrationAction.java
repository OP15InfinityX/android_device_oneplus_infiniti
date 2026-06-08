package com.oplus.pluskey.actions;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;

import com.oplus.pluskey.R;

/**
 * Cycles RING -> VIBRATE -> DND -> RING. On this device ringer-mode SILENT
 * lands as vibrate + DND, so "silent" is represented by DND with ringer kept
 * normal instead.
 */
public class SoundVibrationAction implements Action {
    @Override
    public void run(Context ctx) {
        AudioManager am = ctx.getSystemService(AudioManager.class);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (am == null) return;

        int filter = nm != null
                ? nm.getCurrentInterruptionFilter()
                : NotificationManager.INTERRUPTION_FILTER_ALL;
        boolean dndOn = filter == NotificationManager.INTERRUPTION_FILTER_NONE
                || filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY;

        if (dndOn) {
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            }
            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            Haptics.longBuzz(ctx);
            Feedback.show(ctx, R.string.feedback_ring);
            return;
        }

        switch (am.getRingerMode()) {
            case AudioManager.RINGER_MODE_NORMAL:
                am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                Haptics.tickTwice(ctx);
                Feedback.show(ctx, R.string.feedback_vibrate);
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                if (nm == null || !nm.isNotificationPolicyAccessGranted()) {
                    Feedback.show(ctx, R.string.feedback_dnd_no_perm);
                    return;
                }
                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
                Haptics.tickOnce(ctx);
                Feedback.show(ctx, R.string.feedback_silent);
                break;
            case AudioManager.RINGER_MODE_SILENT:
            default:
                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                Haptics.longBuzz(ctx);
                Feedback.show(ctx, R.string.feedback_ring);
                break;
        }
    }
}
