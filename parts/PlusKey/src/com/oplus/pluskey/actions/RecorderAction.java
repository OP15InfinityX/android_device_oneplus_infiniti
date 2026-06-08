package com.oplus.pluskey.actions;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.oplus.pluskey.Constants;
import com.oplus.pluskey.R;

/**
 * Starts a recording immediately.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code com.google.android.apps.recorder.VOICE_COMMAND_START_RECORDING}
 *       — Google Recorder's Assistant-integration intent. Goes straight to
 *       VoiceCommandActivity which begins recording without further taps.</li>
 *   <li>The Record quick-shortcut intent
 *       ({@code android.intent.action.INSERT} on
 *       {@code com.google.android.apps.recorder/.record.ui.RecordActivity}
 *       with extra {@code extra_start_recording_list_at_finish=true}) —
 *       same shortcut that surfaces on the launcher long-press menu.</li>
 *   <li>{@code MediaStore.RECORD_SOUND} — generic fallback any recorder
 *       can implement.</li>
 *   <li>Launch whichever recorder app the system resolves and let the user
 *       hit the Record button manually.</li>
 * </ol>
 */
public class RecorderAction implements Action {

    private static final String GOOGLE_RECORDER_PKG = "com.google.android.apps.recorder";
    private static final String VOICE_CMD_START =
            "com.google.android.apps.recorder.VOICE_COMMAND_START_RECORDING";

    @Override
    public void run(Context ctx) {
        if (tryStart(ctx, voiceCommandIntent())) return;
        if (tryStart(ctx, quickRecordShortcutIntent())) return;
        if (tryStart(ctx, recordSoundIntent())) return;

        // Last resort: launch the recorder app if installed, else surface
        // the placeholder toast.
        Intent launch = ctx.getPackageManager()
                .getLaunchIntentForPackage(GOOGLE_RECORDER_PKG);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(launch);
            Haptics.confirm(ctx);
            return;
        }
        Feedback.show(ctx, R.string.feedback_todo);
    }

    private Intent voiceCommandIntent() {
        return new Intent(VOICE_CMD_START)
                .setPackage(GOOGLE_RECORDER_PKG)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private Intent quickRecordShortcutIntent() {
        return new Intent(Intent.ACTION_INSERT)
                .setComponent(new ComponentName(GOOGLE_RECORDER_PKG,
                        GOOGLE_RECORDER_PKG + ".record.ui.RecordActivity"))
                .putExtra("extra_start_recording_list_at_finish", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private Intent recordSoundIntent() {
        return new Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private boolean tryStart(Context ctx, Intent intent) {
        if (intent.resolveActivity(ctx.getPackageManager()) == null) return false;
        try {
            ctx.startActivity(intent);
            Haptics.confirm(ctx);
            return true;
        } catch (Throwable t) {
            Log.w(Constants.TAG, "RecorderAction failed " + intent.getAction(), t);
            return false;
        }
    }
}
