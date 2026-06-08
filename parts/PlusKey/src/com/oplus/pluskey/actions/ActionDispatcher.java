package com.oplus.pluskey.actions;

import android.content.Context;
import android.util.Log;

import com.oplus.pluskey.Constants;

public final class ActionDispatcher {
    private final Context mCtx;

    public ActionDispatcher(Context ctx) {
        mCtx = ctx.getApplicationContext();
    }

    public void dispatch(int actionId) {
        Action action = forId(actionId);
        if (action == null) {
            Log.w(Constants.TAG, "no handler for action id " + actionId);
            return;
        }
        try {
            action.run(mCtx);
        } catch (Throwable t) {
            Log.e(Constants.TAG, "action " + actionId + " threw", t);
        }
    }

    public static Action forId(int id) {
        switch (id) {
            case Constants.ACTION_NONE:        return new NoOpAction();
            case Constants.ACTION_SOUND_VIB:   return new SoundVibrationAction();
            case Constants.ACTION_DND:         return new DndAction();
            case Constants.ACTION_CAMERA:      return new CameraAction();
            case Constants.ACTION_FLASHLIGHT:  return new FlashlightAction();
            case Constants.ACTION_SCREENSHOT:  return new ScreenshotAction();
            case Constants.ACTION_RECORDER:    return new RecorderAction();
            case Constants.ACTION_TRANSLATE:   return new TranslateAction();
            case Constants.ACTION_OPEN_APP:    return new OpenAppAction();
            default: return null;
        }
    }
}
