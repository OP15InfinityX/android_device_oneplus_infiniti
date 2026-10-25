package com.oplus.pluskey.actions;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.util.ScreenshotHelper;
import com.oplus.pluskey.Constants;

/** Takes a screenshot through the same SystemUI service used by the key chord. */
public class ScreenshotAction implements Action {
    @Override
    public void run(Context ctx) {
        new ScreenshotHelper(ctx).takeScreenshot(
                WindowManager.ScreenshotSource.SCREENSHOT_KEY_OTHER,
                new Handler(Looper.getMainLooper()),
                uri -> {
                    if (uri == null) {
                        Log.e(Constants.TAG, "screenshot service did not save an image");
                    }
                });
    }
}
