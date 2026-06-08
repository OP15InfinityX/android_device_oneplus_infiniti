package com.oplus.pluskey.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.oplus.pluskey.Constants;
import com.oplus.pluskey.actions.TranslateAction;

/**
 * Listens for {@link Intent#ACTION_PACKAGE_ADDED} and auto-launches Google
 * Translate the moment its package install completes. Only fires when the
 * added package is Translate AND it's a fresh install (not a replace/
 * upgrade), so future Translate updates don't surprise-launch the app.
 *
 * <p>Lives in the manifest because PACKAGE_ADDED is one of the broadcasts
 * that's still allowed to wake manifest-registered receivers on Android 8+.
 */
public class TranslateInstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) return;
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
        Uri data = intent.getData();
        if (data == null || !TranslateAction.TRANSLATE_PKG.equals(data.getSchemeSpecificPart())) {
            return;
        }
        Intent launch = ctx.getPackageManager()
                .getLaunchIntentForPackage(TranslateAction.TRANSLATE_PKG);
        if (launch == null) return;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(launch);
            Log.i(Constants.TAG, "auto-launched Translate after install");
        } catch (Throwable t) {
            Log.w(Constants.TAG, "auto-launch Translate failed", t);
        }
    }
}
