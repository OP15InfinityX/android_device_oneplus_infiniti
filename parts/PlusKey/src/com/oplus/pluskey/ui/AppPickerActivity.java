package com.oplus.pluskey.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.oplus.pluskey.R;
import com.oplus.pluskey.Settings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lists every launcher-visible app sorted by display name. Tapping a row
 * persists the package to {@link Settings#setOpenAppPkg} and finishes —
 * the Plus Key activity then re-renders the description with the chosen
 * app's name and the user can hit "Set" to commit the action choice.
 */
public class AppPickerActivity extends Activity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_CAMERA_TRIGGER = "camera_trigger";

    private boolean mCameraTriggerMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        mCameraTriggerMode = MODE_CAMERA_TRIGGER.equals(getIntent().getStringExtra(EXTRA_MODE));

        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setTitle(mCameraTriggerMode
                ? R.string.setting_camera_trigger_apps
                : R.string.action_open_app_pick_title);
        tb.setNavigationOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.app_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new AppAdapter(loadApps(), mCameraTriggerMode));
    }

    private List<AppEntry> loadApps() {
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = getPackageManager()
                .queryIntentActivities(main, PackageManager.MATCH_ALL);
        List<AppEntry> out = new ArrayList<>(ris.size());
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;
            if (mCameraTriggerMode && !hasCameraPermission(pkg)) continue;
            String label = ri.loadLabel(getPackageManager()).toString();
            Drawable icon = ri.loadIcon(getPackageManager());
            out.add(new AppEntry(pkg, label, icon));
        }
        Collator coll = Collator.getInstance();
        Collections.sort(out, (a, b) -> coll.compare(a.label, b.label));
        return out;
    }

    private boolean hasCameraPermission(String pkg) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                    pkg, PackageManager.GET_PERMISSIONS);
            String[] permissions = info.requestedPermissions;
            if (permissions == null) return false;
            for (String permission : permissions) {
                if (Manifest.permission.CAMERA.equals(permission)) return true;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return false;
    }

    private static class AppEntry {
        final String pkg, label;
        final Drawable icon;
        AppEntry(String pkg, String label, Drawable icon) {
            this.pkg = pkg; this.label = label; this.icon = icon;
        }
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {
        private final List<AppEntry> mItems;
        private final boolean mMultiSelect;
        private final Set<String> mSelected;

        AppAdapter(List<AppEntry> items, boolean multiSelect) {
            mItems = items;
            mMultiSelect = multiSelect;
            mSelected = multiSelect
                    ? new LinkedHashSet<>(Settings.getCameraTriggerPkgs(AppPickerActivity.this))
                    : new LinkedHashSet<>();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_picker, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AppEntry e = mItems.get(pos);
            h.icon.setImageDrawable(e.icon);
            h.label.setText(e.label);
            h.checkbox.setVisibility(mMultiSelect ? View.VISIBLE : View.GONE);
            h.checkbox.setChecked(mSelected.contains(e.pkg));
            h.itemView.setOnClickListener(v -> {
                if (mMultiSelect) {
                    if (mSelected.contains(e.pkg)) {
                        mSelected.remove(e.pkg);
                    } else {
                        mSelected.add(e.pkg);
                    }
                    Settings.setCameraTriggerPkgs(AppPickerActivity.this, mSelected);
                    notifyItemChanged(h.getBindingAdapterPosition());
                    return;
                }
                Settings.setOpenAppPkg(AppPickerActivity.this, e.pkg);
                finish();
            });
        }

        @Override public int getItemCount() { return mItems.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;
            final CheckBox checkbox;
            VH(View v) {
                super(v);
                icon = v.findViewById(android.R.id.icon);
                label = v.findViewById(android.R.id.text1);
                checkbox = v.findViewById(android.R.id.checkbox);
            }
        }
    }
}
