package com.oplus.pluskey.service;

import android.app.role.RoleManager;
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import com.oplus.pluskey.Constants;

import java.util.concurrent.Executors;

/**
 * Clears the system ASSISTANT role once on first run so the framework's
 * default ASSIST keycode handler stops auto-launching Gemini. The user
 * asked for the Plus Key to be exclusively driven by our long-press
 * pipeline, so any role-based dispatch is undesirable.
 *
 * <p>Idempotent — checks the current holder and only clears if it points
 * to a known assistant package.
 */
public final class AssistantRoleClearer {

    private static final String FLAG_KEY = "pluskey_assistant_role_cleared";

    public static void clearOnce(Context ctx) {
        android.content.SharedPreferences sp =
                ctx.getSharedPreferences("pluskey", Context.MODE_PRIVATE);
        if (sp.getBoolean(FLAG_KEY, false)) return;

        RoleManager rm = ctx.getSystemService(RoleManager.class);
        if (rm == null) return;
        if (!rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            sp.edit().putBoolean(FLAG_KEY, true).apply();
            return;
        }

        try {
            rm.clearRoleHoldersAsUser(
                    RoleManager.ROLE_ASSISTANT,
                    /* flags */ 0,
                    UserHandle.of(Process.myUserHandle().getIdentifier()),
                    Executors.newSingleThreadExecutor(),
                    success -> {
                        Log.i(Constants.TAG, "cleared ASSISTANT role: success=" + success);
                        sp.edit().putBoolean(FLAG_KEY, true).apply();
                    });
        } catch (Throwable t) {
            Log.w(Constants.TAG, "couldn't clear ASSISTANT role", t);
        }
    }

    private AssistantRoleClearer() {}
}
