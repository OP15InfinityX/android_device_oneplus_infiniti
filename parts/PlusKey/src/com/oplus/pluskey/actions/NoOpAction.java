package com.oplus.pluskey.actions;

import android.content.Context;

/** Explicit "do nothing" — exists so the user can disable the long-press
 *  behaviour entirely without uninstalling the service. */
public class NoOpAction implements Action {
    @Override
    public void run(Context ctx) {}
}
