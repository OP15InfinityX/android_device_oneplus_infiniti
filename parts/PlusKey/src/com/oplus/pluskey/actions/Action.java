package com.oplus.pluskey.actions;

import android.content.Context;

/**
 * Common interface for the eight Plus Key actions. Each implementation
 * does its thing and is responsible for its own UX feedback (toast, vibe,
 * sound). Returns nothing — fire-and-forget on long-press.
 */
public interface Action {
    void run(Context ctx);
}
