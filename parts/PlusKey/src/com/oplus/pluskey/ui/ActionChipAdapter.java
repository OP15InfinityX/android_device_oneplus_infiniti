package com.oplus.pluskey.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oplus.pluskey.R;

/**
 * Horizontal RecyclerView adapter for the action chip row. Selection is
 * driven from the outside by {@link #setCenteredPosition(int)} — the
 * Activity uses a LinearSnapHelper + scroll listener to figure out which
 * chip is currently in the centre slot, and tells us so we can render the
 * full-opacity / accent-tinted state on the right item.
 *
 * <p>Tapping a chip invokes the listener, which in the Activity simply
 * smooth-scrolls that chip to the centre. The new centre then propagates
 * back through {@link #setCenteredPosition(int)} on snap, keeping a single
 * source of truth: "centred chip == focused action".
 */
public class ActionChipAdapter extends RecyclerView.Adapter<ActionChipAdapter.VH> {

    public interface OnTap { void onTap(int positionInList); }

    private final Context mCtx;
    private final OnTap mListener;
    private int mCenteredPos;

    public ActionChipAdapter(Context ctx, int initialActionId, OnTap listener) {
        mCtx = ctx;
        mListener = listener;
        mCenteredPos = ActionRegistry.indexOf(initialActionId);
    }

    /** Caller (Activity) tells us which chip is currently centred. We just
     *  redraw the affected items; we do not move the list. */
    public void setCenteredPosition(int pos) {
        int prev = mCenteredPos;
        if (prev == pos) return;
        mCenteredPos = pos;
        notifyItemChanged(prev);
        notifyItemChanged(pos);
    }

    public int getCenteredPosition() { return mCenteredPos; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_action_chip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ActionRegistry.Item item = ActionRegistry.get(pos);
        h.icon.setImageResource(item.icon);

        boolean centered = pos == mCenteredPos;
        int accent = mCtx.getColor(item.color);
        int tint = centered ? accent : 0xFF6B6B6B;
        h.icon.setImageTintList(ColorStateList.valueOf(tint));

        h.itemView.setAlpha(centered ? 1f : 0.55f);
        h.itemView.setOnClickListener(v -> mListener.onTap(pos));
    }

    @Override
    public int getItemCount() { return ActionRegistry.ITEMS.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        VH(View v) { super(v); icon = v.findViewById(R.id.chip_icon); }
    }
}
