package com.oplus.pluskey.actions;

import android.app.NotificationManager;
import android.content.Context;

import com.oplus.pluskey.R;

public class DndAction implements Action {
    @Override
    public void run(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null || !nm.isNotificationPolicyAccessGranted()) {
            Feedback.show(ctx, R.string.feedback_dnd_no_perm);
            return;
        }
        boolean on = nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_NONE
                  || nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        nm.setInterruptionFilter(on
                ? NotificationManager.INTERRUPTION_FILTER_ALL
                : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        Haptics.confirm(ctx);
        Feedback.show(ctx, on ? R.string.feedback_dnd_off : R.string.feedback_dnd_on);
    }
}
