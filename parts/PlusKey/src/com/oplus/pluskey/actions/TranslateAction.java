package com.oplus.pluskey.actions;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.oplus.pluskey.Constants;
import com.oplus.pluskey.R;

/**
 * Launches Google Translate. If the app isn't installed, opens its Play
 * Store details page (via the {@code market://} scheme so Play Store
 * intercepts directly without a chooser). Once installed, the
 * {@link com.oplus.pluskey.service.TranslateInstallReceiver} fires on
 * PACKAGE_ADDED and auto-launches Translate so the user doesn't have to
 * press Plus Key a second time.
 */
public class TranslateAction implements Action {

    public static final String TRANSLATE_PKG = "com.google.android.apps.translate";

    @Override
    public void run(Context ctx) {
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(TRANSLATE_PKG);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(launch);
                Haptics.confirm(ctx);
                return;
            } catch (Throwable t) {
                Log.w(Constants.TAG, "translate launch failed", t);
            }
        }

        // Not installed — deep-link to Play Store. The market:// scheme is
        // resolved by Play Store directly; if Play Store is missing we fall
        // back to the https URL which any browser will open.
        Intent market = new Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("market://details?id=" + TRANSLATE_PKG))
                .setPackage("com.android.vending")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (market.resolveActivity(ctx.getPackageManager()) != null) {
            ctx.startActivity(market);
            Feedback.show(ctx, R.string.feedback_translate_installing);
            return;
        }
        Intent web = new Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("https://play.google.com/store/apps/details?id=" + TRANSLATE_PKG))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(web);
        Feedback.show(ctx, R.string.feedback_translate_installing);
    }
}
