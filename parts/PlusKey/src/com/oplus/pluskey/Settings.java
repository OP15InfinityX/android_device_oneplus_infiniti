package com.oplus.pluskey;

import android.content.Context;
import android.provider.Settings.System;

import java.util.LinkedHashSet;
import java.util.Set;

/** Tiny wrapper around Settings.System for our prefs. */
public final class Settings {
    private Settings() {}

    /** @return the persisted action id, or {@link Constants#ACTION_UNSET} if
     *  the user has never picked one. The receiver uses the sentinel to know
     *  it should open the picker Activity instead of dispatching. */
    public static int getAction(Context ctx) {
        return getAction(ctx, true);
    }

    public static int getAction(Context ctx, boolean longPress) {
        return System.getInt(ctx.getContentResolver(),
                longPress ? Constants.KEY_PLUSKEY_ACTION : Constants.KEY_PLUSKEY_SHORT_ACTION,
                Constants.ACTION_UNSET);
    }

    public static void setAction(Context ctx, int action) {
        setAction(ctx, true, action);
    }

    public static void setAction(Context ctx, boolean longPress, int action) {
        System.putInt(ctx.getContentResolver(),
                longPress ? Constants.KEY_PLUSKEY_ACTION : Constants.KEY_PLUSKEY_SHORT_ACTION,
                action);
    }

    public static int getCameraMode(Context ctx) {
        return System.getInt(ctx.getContentResolver(),
                Constants.KEY_PLUSKEY_CAMERA_MODE, Constants.CAM_MODE_PHOTO);
    }

    public static void setCameraMode(Context ctx, int mode) {
        System.putInt(ctx.getContentResolver(), Constants.KEY_PLUSKEY_CAMERA_MODE, mode);
    }

    public static boolean isShortPressScreenOnOnly(Context ctx) {
        return System.getInt(ctx.getContentResolver(),
                Constants.KEY_PLUSKEY_SHORT_SCREEN_ON_ONLY, 0) == 1;
    }

    public static void setShortPressScreenOnOnly(Context ctx, boolean enabled) {
        System.putInt(ctx.getContentResolver(),
                Constants.KEY_PLUSKEY_SHORT_SCREEN_ON_ONLY, enabled ? 1 : 0);
    }

    public static boolean isCameraTriggerEnabled(Context ctx) {
        return System.getInt(ctx.getContentResolver(),
                Constants.KEY_PLUSKEY_CAMERA_TRIGGER, 0) == 1;
    }

    public static void setCameraTriggerEnabled(Context ctx, boolean enabled) {
        System.putInt(ctx.getContentResolver(),
                Constants.KEY_PLUSKEY_CAMERA_TRIGGER, enabled ? 1 : 0);
    }

    public static Set<String> getCameraTriggerPkgs(Context ctx) {
        String raw = System.getString(ctx.getContentResolver(),
                Constants.KEY_PLUSKEY_CAMERA_TRIGGER_PKGS);
        Set<String> out = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pkg : raw.split(",")) {
            if (!pkg.isEmpty()) out.add(pkg);
        }
        return out;
    }

    public static void setCameraTriggerPkgs(Context ctx, Set<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String pkg : pkgs) {
            if (pkg == null || pkg.isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(pkg);
        }
        System.putString(ctx.getContentResolver(),
                Constants.KEY_PLUSKEY_CAMERA_TRIGGER_PKGS, sb.toString());
    }

    /** Package name of the user-picked "Open app" target, or null if unset. */
    public static String getOpenAppPkg(Context ctx) {
        return System.getString(ctx.getContentResolver(), Constants.KEY_PLUSKEY_OPEN_APP_PKG);
    }

    public static void setOpenAppPkg(Context ctx, String pkg) {
        System.putString(ctx.getContentResolver(), Constants.KEY_PLUSKEY_OPEN_APP_PKG, pkg);
    }
}
