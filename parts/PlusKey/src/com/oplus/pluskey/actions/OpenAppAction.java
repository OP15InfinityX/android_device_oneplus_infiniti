package com.oplus.pluskey.actions;

import android.content.Context;
import android.content.Intent;
import com.oplus.pluskey.R;
import com.oplus.pluskey.Settings;
import com.oplus.pluskey.ui.AppPickerActivity;

/**
 * Launches the user-chosen app. The package name is stored in Settings via
 * {@link AppPickerActivity}; if it's unset (or the app got uninstalled since
 * being picked), we open the picker so the user can choose a fresh target.
 */
public class OpenAppAction implements Action {
    @Override
    public void run(Context ctx) {
        String pkg = Settings.getOpenAppPkg(ctx);
        Intent launch = pkg == null ? null
                : ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            Feedback.show(ctx, R.string.action_open_app_pick_summary);
            Intent picker = new Intent(ctx, AppPickerActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(picker);
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Haptics.confirm(ctx);
        ctx.startActivity(launch);
    }
}
