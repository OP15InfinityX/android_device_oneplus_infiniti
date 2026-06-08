package com.oplus.pluskey.actions;

import android.content.Context;
import android.content.Intent;
import android.provider.MediaStore;

import com.oplus.pluskey.Constants;
import com.oplus.pluskey.Settings;

/**
 * Opens the system camera app. The mode preference is passed via two
 * extras the OPLUS / Google camera apps both look at — others ignore them
 * and just open in their default mode, which is fine.
 */
public class CameraAction implements Action {
    @Override
    public void run(Context ctx) {
        int mode = Settings.getCameraMode(ctx);
        Intent i;
        switch (mode) {
            case Constants.CAM_MODE_VIDEO:
                i = new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
                break;
            case Constants.CAM_MODE_PHOTO:
            case Constants.CAM_MODE_SELFIE:
            case Constants.CAM_MODE_PORTRAIT:
            case Constants.CAM_MODE_MACRO:
            case Constants.CAM_MODE_SLO_MO:
            default:
                i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                break;
        }
        // OPlusCamera reads these — Google Camera ignores them gracefully.
        i.putExtra("com.oplus.camera.mode_int", mode);
        i.putExtra("android.intent.extra.USE_FRONT_CAMERA",
                mode == Constants.CAM_MODE_SELFIE);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        Haptics.confirm(ctx);
        ctx.startActivity(i);
    }
}
