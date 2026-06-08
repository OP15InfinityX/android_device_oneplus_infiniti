package com.oplus.pluskey.actions;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;

import com.oplus.pluskey.Constants;
import com.oplus.pluskey.R;

/** Toggles the flashlight via Camera2 torch mode. State is read from the
 *  CameraManager.TorchCallback the first time we run; afterwards we track
 *  it ourselves to avoid a second listener registration. */
public class FlashlightAction implements Action {

    private static volatile boolean sTorchOn = false;
    private static volatile String sTorchCameraId = null;

    @Override
    public void run(Context ctx) {
        CameraManager cm = ctx.getSystemService(CameraManager.class);
        if (cm == null) return;
        try {
            String camId = pickRearCamera(cm);
            if (camId == null) {
                Feedback.show(ctx, R.string.feedback_flash_unavailable);
                return;
            }
            sTorchCameraId = camId;
            sTorchOn = !sTorchOn;
            cm.setTorchMode(camId, sTorchOn);
            Haptics.confirm(ctx);
        } catch (CameraAccessException e) {
            Log.e(Constants.TAG, "torch toggle failed", e);
            sTorchOn = !sTorchOn; // revert
        }
    }

    private static String pickRearCamera(CameraManager cm) throws CameraAccessException {
        for (String id : cm.getCameraIdList()) {
            CameraCharacteristics c = cm.getCameraCharacteristics(id);
            Boolean hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer facing  = c.get(CameraCharacteristics.LENS_FACING);
            if (Boolean.TRUE.equals(hasFlash)
                    && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        return null;
    }
}
