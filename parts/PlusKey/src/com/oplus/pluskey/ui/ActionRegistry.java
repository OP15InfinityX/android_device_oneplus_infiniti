package com.oplus.pluskey.ui;

import com.oplus.pluskey.Constants;
import com.oplus.pluskey.R;

import java.util.Arrays;
import java.util.List;

/**
 * Single source of truth for everything the settings UI needs to render an
 * action: name, description, icon, and accent color. Order here is also the
 * order they appear in the bottom action chip row.
 */
public final class ActionRegistry {

    public static final class Item {
        public final int id, label, desc, icon, color;
        Item(int id, int label, int desc, int icon, int color) {
            this.id = id; this.label = label; this.desc = desc;
            this.icon = icon; this.color = color;
        }
    }

    /** Order matches the chip row left-to-right (None last so it visually
     *  reads as "off the action set"). */
    public static final List<Item> ITEMS = Arrays.asList(
        new Item(Constants.ACTION_SOUND_VIB,  R.string.action_sound_vib,  R.string.action_sound_vib_desc,  R.drawable.ic_bell,       R.color.accent_sound_vib),
        new Item(Constants.ACTION_DND,        R.string.action_dnd,        R.string.action_dnd_desc,        R.drawable.ic_dnd,        R.color.accent_dnd),
        new Item(Constants.ACTION_CAMERA,     R.string.action_camera,     R.string.action_camera_desc,     R.drawable.ic_camera,     R.color.accent_camera),
        new Item(Constants.ACTION_FLASHLIGHT, R.string.action_flashlight, R.string.action_flashlight_desc, R.drawable.ic_flashlight, R.color.accent_flashlight),
        new Item(Constants.ACTION_SCREENSHOT, R.string.action_screenshot, R.string.action_screenshot_desc, R.drawable.ic_screenshot, R.color.accent_screenshot),
        new Item(Constants.ACTION_RECORDER,   R.string.action_recorder,   R.string.action_recorder_desc,   R.drawable.ic_recorder,   R.color.accent_recorder),
        new Item(Constants.ACTION_TRANSLATE,  R.string.action_translate,  R.string.action_translate_desc,  R.drawable.ic_translate,  R.color.accent_translate),
        new Item(Constants.ACTION_OPEN_APP,   R.string.action_open_app,   R.string.action_open_app_desc,   R.drawable.ic_open_app,   R.color.accent_open_app),
        new Item(Constants.ACTION_NONE,       R.string.action_none,       R.string.action_none_desc,       R.drawable.ic_noaction,   R.color.accent_none)
    );

    public static int indexOf(int actionId) {
        for (int i = 0; i < ITEMS.size(); i++) {
            if (ITEMS.get(i).id == actionId) return i;
        }
        return 0;
    }

    public static Item get(int index) { return ITEMS.get(index); }

    private ActionRegistry() {}
}
