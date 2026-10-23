package com.oplus.pluskey.actions;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
        KeyguardManager keyguard = ctx.getSystemService(KeyguardManager.class);
        boolean locked = keyguard != null && keyguard.isKeyguardLocked();
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
                // Match the power-button camera gesture: a locked device must
                // use the secure entry point, without exposing existing photos.
                i = new Intent(locked
                        ? MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
                        : MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                break;
        }
        if (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(i.getAction())) {
            selectSecureCameraPackage(ctx, i);
            i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
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

    private static void selectSecureCameraPackage(Context ctx, Intent secureIntent) {
        PackageManager pm = ctx.getPackageManager();
        ResolveInfo camera = pm.resolveActivity(
                new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                PackageManager.MATCH_DEFAULT_ONLY);
        if (camera == null || camera.activityInfo == null) return;

        String cameraPackage = camera.activityInfo.packageName;
        ResolveInfo secureCamera = pm.resolveActivity(
                new Intent(secureIntent).setPackage(cameraPackage),
                PackageManager.MATCH_DEFAULT_ONLY);
        if (secureCamera == null || secureCamera.activityInfo == null
                || !cameraPackage.equals(secureCamera.activityInfo.packageName)
                || !secureCamera.activityInfo.enabled || !secureCamera.activityInfo.exported) {
            // No matching secure entry point: retain the original secure intent.
            // Never fall back to an ordinary camera activity on the lockscreen.
            return;
        }

        // A separate secure default may be unset when several camera apps are
        // installed. Reuse only the normal default's package, not its activity:
        // OOS photo and secure-camera entry points are different components.
        secureIntent.setPackage(cameraPackage);
    }
}
