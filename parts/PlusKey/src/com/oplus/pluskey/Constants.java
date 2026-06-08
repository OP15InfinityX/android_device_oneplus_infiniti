package com.oplus.pluskey;

import android.view.KeyEvent;

public final class Constants {
    private Constants() {}

    public static final String TAG = "PlusKey";

    /** The hardware key the OnePlus 15 "Plus Key" reports (BTN_TRIGGER_HAPPY32). */
    public static final int PLUS_KEY_KEYCODE = KeyEvent.KEYCODE_ASSIST;

    /** Long-press threshold. Below this is treated as a short-press. */
    public static final long LONG_PRESS_MS = 400L;

    // ===== action ids — persisted as int in Settings.System =====
    public static final int ACTION_NONE          = 0;
    public static final int ACTION_SOUND_VIB     = 1;
    public static final int ACTION_DND           = 2;
    public static final int ACTION_CAMERA        = 3;
    public static final int ACTION_FLASHLIGHT    = 4;
    public static final int ACTION_SCREENSHOT    = 5;
    public static final int ACTION_RECORDER      = 6;
    public static final int ACTION_TRANSLATE     = 7;
    public static final int ACTION_OPEN_APP      = 8;

    /** Sentinel returned by Settings.getAction() when the user has never
     *  picked an action. Distinct from ACTION_NONE (an explicit "do nothing"
     *  choice). On long-press in this state the receiver opens the picker
     *  Activity instead of dispatching anything. */
    public static final int ACTION_UNSET = -1;

    /** Initial value shown on the chip row when the user has never picked
     *  anything. Not persisted until they tap "Set". */
    public static final int DEFAULT_DISPLAY_ACTION = ACTION_SOUND_VIB;

    // ===== Settings.System keys =====
    public static final String KEY_PLUSKEY_SHORT_ACTION = "pluskey_short_action";
    public static final String KEY_PLUSKEY_ACTION       = "pluskey_action";
    public static final String KEY_PLUSKEY_CAMERA_MODE  = "pluskey_camera_mode";
    public static final String KEY_PLUSKEY_SHORT_SCREEN_ON_ONLY =
            "pluskey_short_screen_on_only";
    public static final String KEY_PLUSKEY_CAMERA_TRIGGER =
            "pluskey_camera_trigger";
    public static final String KEY_PLUSKEY_CAMERA_TRIGGER_PKGS =
            "pluskey_camera_trigger_pkgs";
    /** Package name of the user-chosen "Open app" target (string). */
    public static final String KEY_PLUSKEY_OPEN_APP_PKG = "pluskey_open_app_pkg";

    // ===== camera mode IDs (passed to camera intent extras) =====
    public static final int CAM_MODE_PHOTO     = 0;
    public static final int CAM_MODE_VIDEO     = 1;
    public static final int CAM_MODE_SELFIE    = 2;
    public static final int CAM_MODE_PORTRAIT  = 3;
    public static final int CAM_MODE_MACRO     = 4;
    public static final int CAM_MODE_SLO_MO    = 5;
}
