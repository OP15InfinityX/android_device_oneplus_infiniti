package com.oplus.pluskey.ui;

import android.animation.ArgbEvaluator;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.SmoothScroller;

import com.google.android.material.appbar.MaterialToolbar;
import com.oplus.pluskey.Constants;
import com.oplus.pluskey.R;
import com.oplus.pluskey.Settings;
import com.oplus.pluskey.actions.Haptics;
import com.oplus.pluskey.service.AssistantRoleClearer;

/**
 * Plus Key settings — the slick page from pluskey.webp.
 *
 * <p>Interaction model:
 * <ol>
 *   <li>The chip row at the bottom snaps the touched / scrolled chip into
 *       the screen-centre slot (LinearSnapHelper). Whichever chip lands in
 *       the centre is the "focused" action.</li>
 *   <li>The big pill above shows that focused action's icon + label +
 *       description, with a zoom-in entry whenever the focus changes.</li>
 *   <li>The bottom CTA pill says <b>"Set"</b> while the focused chip differs
 *       from what's persisted; tapping it writes the new selection. Once
 *       saved, the CTA flips to <b>"In use"</b> and goes inert.</li>
 *   <li>Camera focused → a Material 3 styled mode dropdown appears between
 *       the description and the chip row; tapping it opens a PopupMenu.</li>
 * </ol>
 */
public class PlusKeyActivity extends Activity {

    private GlowingPillView mHalo;
    private ImageView mActionIcon;
    private TextView mActionLabel, mActionDesc;
    private View mPillContent;     // wrapper around icon+label+desc
    private View mOpenAppPicker;
    private TextView mOpenAppPickerLabel;
    private View mCameraPicker;
    private TextView mCameraPickerLabel;
    private TextView mGestureSelector;
    private TextView mCtaButton;
    private RecyclerView mChipRow;
    private ActionChipAdapter mAdapter;

    private boolean mEditingLongPress = true;
    private int mSavedActionId;       // the user's persisted choice (-1 = unset)
    private int mFocusedActionId;     // whichever chip is currently centred

    @Override
    protected void onResume() {
        super.onResume();
        // If the user just came back from AppPickerActivity having chosen
        // a different target package, re-render the description so it
        // reflects the new app's name without requiring a chip re-scroll.
        if (mFocusedActionId == Constants.ACTION_OPEN_APP) {
            renderFocus(mFocusedActionId, /*animate=*/false);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pluskey);

        AssistantRoleClearer.clearOnce(this);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> finish());
        ImageButton settingsButton = new ImageButton(this);
        settingsButton.setImageResource(R.drawable.ic_settings);
        settingsButton.setBackgroundResource(android.R.color.transparent);
        settingsButton.setColorFilter(getColor(android.R.color.transparent));
        settingsButton.clearColorFilter();
        settingsButton.setContentDescription(getString(R.string.pluskey_settings));
        settingsButton.setPadding(
                (int) (16 * getResources().getDisplayMetrics().density),
                (int) (16 * getResources().getDisplayMetrics().density),
                (int) (16 * getResources().getDisplayMetrics().density),
                (int) (16 * getResources().getDisplayMetrics().density));
        tb.addView(settingsButton, new MaterialToolbar.LayoutParams(
                (int) (56 * getResources().getDisplayMetrics().density),
                (int) (56 * getResources().getDisplayMetrics().density),
                Gravity.END | Gravity.CENTER_VERTICAL));
        settingsButton.setOnClickListener(v -> showSettingsMenu(settingsButton));

        mHalo = findViewById(R.id.pill_halo);
        mGestureSelector = findViewById(R.id.gesture_selector);
        mGestureSelector.setOnClickListener(v -> showGestureMenu());
        mActionIcon = findViewById(R.id.action_icon);
        mActionLabel = findViewById(R.id.action_label);
        mActionDesc = findViewById(R.id.action_desc);
        mPillContent = findViewById(R.id.pill_content);
        mOpenAppPicker = findViewById(R.id.open_app_picker);
        mOpenAppPickerLabel = findViewById(R.id.open_app_label);
        mOpenAppPicker.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, AppPickerActivity.class)));
        mCameraPicker = findViewById(R.id.camera_mode_picker);
        mCameraPickerLabel = findViewById(R.id.camera_mode_label);
        mCtaButton = findViewById(R.id.cta_button);
        mChipRow = findViewById(R.id.action_chips);

        mSavedActionId = Settings.getAction(this, mEditingLongPress);
        // If the user has never picked anything, initialise the focused
        // chip to the default but DON'T persist it — they'll have to tap Set.
        int initialFocus = mSavedActionId == Constants.ACTION_UNSET
                ? Constants.DEFAULT_DISPLAY_ACTION : mSavedActionId;

        setupCameraPicker();
        setupChipRow(initialFocus);
        updateGestureSelector();
        renderFocus(initialFocus, /*animate=*/false);
        animateEntry();
    }

    // ---------------------------------------------------------------- chips

    private void setupChipRow(int initialFocusId) {
        mAdapter = new ActionChipAdapter(this, initialFocusId, this::scrollToCenter);

        // Side padding equal to half the screen width minus half a chip, so
        // first/last chips can actually reach the centre snap slot.
        int chipPx = (int) (56 * getResources().getDisplayMetrics().density);
        int sidePad = (getResources().getDisplayMetrics().widthPixels - chipPx) / 2;
        mChipRow.setPadding(sidePad, mChipRow.getPaddingTop(),
                sidePad, mChipRow.getPaddingBottom());
        mChipRow.setClipToPadding(false);

        LinearLayoutManager lm = new LinearLayoutManager(
                this, RecyclerView.HORIZONTAL, false);
        mChipRow.setLayoutManager(lm);
        mChipRow.setAdapter(mAdapter);
        mChipRow.setItemAnimator(null);   // no flicker on rebind

        LinearSnapHelper snap = new LinearSnapHelper();
        snap.attachToRecyclerView(mChipRow);

        // Two-track scroll listener:
        //   • onScrolled — fires constantly during a fling. Track the chip
        //     currently nearest the centre and tick a tiny haptic each time
        //     that "nearest" position changes — gives the premium detent
        //     feel (like turning a notched dial).
        //   • onScrollStateChanged → IDLE — settled. Commit the focus
        //     change (zoom-in title/desc + halo accent crossfade), so the
        //     pill content only updates when the user has actually stopped
        //     on something instead of flickering through every chip.
        final int[] lastDetentPos = { mAdapter.getCenteredPosition() };
        mChipRow.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                View centerView = snap.findSnapView(lm);
                if (centerView == null) return;
                int pos = lm.getPosition(centerView);
                if (pos != lastDetentPos[0]) {
                    lastDetentPos[0] = pos;
                    mAdapter.setCenteredPosition(pos);
                    Haptics.scrollDetent(PlusKeyActivity.this);
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                View centerView = snap.findSnapView(lm);
                if (centerView == null) return;
                int pos = lm.getPosition(centerView);
                int focusId = ActionRegistry.get(pos).id;
                if (focusId != mFocusedActionId) {
                    renderFocus(focusId, /*animate=*/true);
                }
            }
        });

        // Centre the initial chip without animation. Two-stage post:
        //   1) wait for the RecyclerView to lay itself out (post #1) so its
        //      width and child views exist;
        //   2) wait one more frame (post #2) so findViewByPosition returns
        //      the laid-out chip view; then scrollBy the exact pixel delta
        //      that places that chip's centre at the RecyclerView's centre.
        // scrollToPositionWithOffset alone doesn't account for our horizontal
        // padding correctly when clipToPadding=false, hence the manual maths.
        final int pos = ActionRegistry.indexOf(initialFocusId);
        mChipRow.post(() -> {
            lm.scrollToPosition(pos);
            mChipRow.post(() -> {
                View v = lm.findViewByPosition(pos);
                if (v == null) return;
                int chipCentre = v.getLeft() + v.getWidth() / 2;
                int rvCentre = mChipRow.getWidth() / 2;
                mChipRow.scrollBy(chipCentre - rvCentre, 0);
                mAdapter.setCenteredPosition(pos);
            });
        });
    }

    private void scrollToCenter(int pos) {
        SmoothScroller s = new LinearSmoothScroller(this) {
            @Override protected int getHorizontalSnapPreference() { return SNAP_TO_ANY; }
            @Override protected float calculateSpeedPerPixel(android.util.DisplayMetrics dm) {
                return 60f / dm.densityDpi;   // smoother / slower than default
            }
        };
        s.setTargetPosition(pos);
        if (mChipRow.getLayoutManager() != null) {
            mChipRow.getLayoutManager().startSmoothScroll(s);
        }
    }

    private void switchGesture(boolean longPress) {
        if (mEditingLongPress == longPress) return;
        mEditingLongPress = longPress;
        mSavedActionId = Settings.getAction(this, mEditingLongPress);
        int actionId = mSavedActionId == Constants.ACTION_UNSET
                ? Constants.DEFAULT_DISPLAY_ACTION : mSavedActionId;
        updateGestureSelector();
        renderFocus(actionId, /*animate=*/true);
        int pos = ActionRegistry.indexOf(actionId);
        mAdapter.setCenteredPosition(pos);
        scrollToCenter(pos);
        Haptics.scrollDetent(this);
    }

    private void updateGestureSelector() {
        mGestureSelector.setText(getString(mEditingLongPress
                ? R.string.long_press : R.string.short_press) + "  v");
    }

    private void showGestureMenu() {
        PopupMenu pm = new PopupMenu(this, mGestureSelector, Gravity.END);
        pm.getMenu().add(0, 0, 0, R.string.short_press);
        pm.getMenu().add(0, 1, 1, R.string.long_press);
        pm.setOnMenuItemClickListener(item -> {
            switchGesture(item.getItemId() == 1);
            return true;
        });
        pm.show();
    }

    private void showSettingsMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor, Gravity.END);
        pm.getMenu().add(0, 0, 0, R.string.setting_short_screen_on_only)
                .setCheckable(true)
                .setChecked(Settings.isShortPressScreenOnOnly(this));
        pm.getMenu().add(0, 1, 1, R.string.setting_camera_trigger)
                .setCheckable(true)
                .setChecked(Settings.isCameraTriggerEnabled(this));
        pm.getMenu().add(0, 2, 2, R.string.setting_camera_trigger_apps);
        pm.setOnMenuItemClickListener(item -> {
            boolean enabled = !item.isChecked();
            if (item.getItemId() == 0) {
                Settings.setShortPressScreenOnOnly(this, enabled);
            } else if (item.getItemId() == 1) {
                Settings.setCameraTriggerEnabled(this, enabled);
            } else if (item.getItemId() == 2) {
                startActivity(new android.content.Intent(this, AppPickerActivity.class)
                        .putExtra(AppPickerActivity.EXTRA_MODE,
                                AppPickerActivity.MODE_CAMERA_TRIGGER));
                return true;
            }
            item.setChecked(enabled);
            return true;
        });
        pm.show();
    }

    // -------------------------------------------------------------- focused

    /** Update the central pill (icon, label, description, halo colour, camera
     *  picker visibility) and the bottom CTA to reflect the focused chip. */
    private void renderFocus(int actionId, boolean animate) {
        mFocusedActionId = actionId;
        ActionRegistry.Item item = ActionRegistry.get(ActionRegistry.indexOf(actionId));
        int accent = getColor(item.color);

        mHalo.setAccent(accent, animate);

        mActionIcon.setImageResource(item.icon);
        mActionIcon.setImageTintList(ColorStateList.valueOf(accent));

        mActionLabel.setText(item.label);
        mActionDesc.setText(descriptionFor(item));

        mCameraPicker.setVisibility(
                actionId == Constants.ACTION_CAMERA ? View.VISIBLE : View.GONE);
        if (actionId == Constants.ACTION_CAMERA) {
            mCameraPickerLabel.setText(cameraModeLabel(Settings.getCameraMode(this)));
        }

        // Open-app picker pill — visible only on this action so the
        // affordance "tap to change app" is unmistakable. Shows the chosen
        // app's display name, or a "Choose app" prompt when none is set.
        boolean isOpenApp = actionId == Constants.ACTION_OPEN_APP;
        mOpenAppPicker.setVisibility(isOpenApp ? View.VISIBLE : View.GONE);
        if (isOpenApp) {
            String pkg = Settings.getOpenAppPkg(this);
            String label = pkg == null ? null : appLabelFor(pkg);
            mOpenAppPickerLabel.setText(label != null ? label
                    : getString(R.string.action_open_app_pick_summary));
        }

        if (animate) {
            zoomIn(mActionIcon);
            zoomIn(mActionLabel);
            zoomIn(mActionDesc);
            if (mCameraPicker.getVisibility() == View.VISIBLE) zoomIn(mCameraPicker);
            if (mOpenAppPicker.getVisibility() == View.VISIBLE) zoomIn(mOpenAppPicker);
        }

        updateCta();
    }

    /** Description text. The "Open app" action substitutes the chosen app's
     *  display name when one is configured, falling back to the generic
     *  "Choose app" prompt otherwise. */
    private CharSequence descriptionFor(ActionRegistry.Item item) {
        if (item.id == Constants.ACTION_OPEN_APP) {
            String pkg = Settings.getOpenAppPkg(this);
            if (pkg != null) {
                String label = appLabelFor(pkg);
                if (label != null) {
                    return getString(R.string.action_open_app_desc_named, label);
                }
            }
            return getString(R.string.action_open_app_desc);
        }
        return getString(item.desc);
    }

    private String appLabelFor(String pkg) {
        try {
            return getPackageManager()
                    .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))
                    .toString();
        } catch (Exception e) { return null; }
    }

    private String cameraModeLabel(int mode) {
        int[] names = {
                R.string.camera_mode_photo, R.string.camera_mode_video,
                R.string.camera_mode_selfie, R.string.camera_mode_portrait,
                R.string.camera_mode_macro, R.string.camera_mode_slo_mo
        };
        return getString(names[Math.max(0, Math.min(mode, names.length - 1))]);
    }

    // ----------------------------------------------------------------- CTA

    private void updateCta() {
        boolean isCurrent = mFocusedActionId == mSavedActionId;
        if (isCurrent) {
            mCtaButton.setText(R.string.in_use);
            mCtaButton.setAlpha(0.55f);
            mCtaButton.setClickable(false);
            mCtaButton.setOnClickListener(null);
        } else {
            mCtaButton.setText(R.string.set_action);
            mCtaButton.setAlpha(1f);
            mCtaButton.setClickable(true);
            mCtaButton.setOnClickListener(v -> commitFocusedAction());
        }
    }

    private void commitFocusedAction() {
        // For Open app, route through the picker if no app has been chosen
        // yet — committing without a target would be useless.
        if (mFocusedActionId == Constants.ACTION_OPEN_APP
                && Settings.getOpenAppPkg(this) == null) {
            startActivity(new android.content.Intent(this, AppPickerActivity.class));
            return;
        }
        Settings.setAction(this, mEditingLongPress, mFocusedActionId);
        mSavedActionId = mFocusedActionId;
        updateCta();
    }

    // ---------------------------------------------------- camera mode popup

    private void setupCameraPicker() {
        mCameraPicker.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, v, Gravity.CENTER);
            int[] names = {
                    R.string.camera_mode_photo, R.string.camera_mode_video,
                    R.string.camera_mode_selfie, R.string.camera_mode_portrait,
                    R.string.camera_mode_macro, R.string.camera_mode_slo_mo
            };
            for (int i = 0; i < names.length; i++) {
                pm.getMenu().add(0, i, i, names[i]);
            }
            pm.setOnMenuItemClickListener(it -> {
                int mode = it.getItemId();
                Settings.setCameraMode(PlusKeyActivity.this, mode);
                mCameraPickerLabel.setText(cameraModeLabel(mode));
                return true;
            });
            pm.show();
        });
    }

    // ----------------------------------------------------------- animations

    private void zoomIn(View v) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setScaleX(0.85f);
        v.setScaleY(0.85f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(280)
                // No overshoot — we want a smooth zoom-to-rest without the
                // bouncy spring that the previous OvershootInterpolator gave.
                .setInterpolator(new DecelerateInterpolator(2.2f))
                .start();
    }

    private void animateEntry() {
        mHalo.setScaleX(0.92f); mHalo.setScaleY(0.92f); mHalo.setAlpha(0f);
        mHalo.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(560).setInterpolator(new DecelerateInterpolator(1.6f))
                .start();

        mChipRow.setTranslationY(120f);
        mChipRow.setAlpha(0f);
        mChipRow.animate().translationY(0).alpha(1f)
                .setStartDelay(140).setDuration(420)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .start();
    }

}
