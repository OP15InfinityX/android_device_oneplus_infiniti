package com.oplus.pluskey.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.oplus.pluskey.R;

/**
 * "AI — coming soon" page. Animated sparkle pulse + subtitle. Replaced once
 * actual on-device AI features land.
 */
public class AiActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        if (tb != null) tb.setNavigationOnClickListener(v -> finish());

        ImageView sparkle = findViewById(R.id.sparkle);
        TextView title = findViewById(R.id.title);
        TextView subtitle = findViewById(R.id.subtitle);

        // entrance
        sparkle.setAlpha(0f); sparkle.setScaleX(0.5f); sparkle.setScaleY(0.5f);
        sparkle.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(700)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> startBreathing(sparkle))
                .start();

        title.setAlpha(0f); title.setTranslationY(40f);
        title.animate().alpha(1f).translationY(0).setStartDelay(220)
                .setDuration(500).start();

        subtitle.setAlpha(0f); subtitle.setTranslationY(40f);
        subtitle.animate().alpha(0.7f).translationY(0).setStartDelay(360)
                .setDuration(500).start();
    }

    private void startBreathing(View v) {
        ObjectAnimator pulse = ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, 1.07f, 1f);
        pulse.setDuration(2200);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator pulseY = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, 1.07f, 1f);
        pulseY.setDuration(2200);
        pulseY.setRepeatCount(ValueAnimator.INFINITE);
        pulseY.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator rotate = ObjectAnimator.ofFloat(v, View.ROTATION, 0f, 12f, 0f, -12f, 0f);
        rotate.setDuration(7000);
        rotate.setRepeatCount(ValueAnimator.INFINITE);
        rotate.setInterpolator(new AccelerateDecelerateInterpolator());

        pulse.start(); pulseY.start(); rotate.start();
    }
}
