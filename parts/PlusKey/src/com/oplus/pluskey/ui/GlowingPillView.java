package com.oplus.pluskey.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.Random;

/**
 * Full-screen background view that draws the central pill from camera.png:
 *
 * <ol>
 *   <li><b>Interior fill</b> — vertical linear gradient (slightly lighter
 *       at centre than top/bottom) so the pill reads as a curved metal
 *       surface, plus a tiled noise overlay sampling the same gradient
 *       for a subtle "sandblasted" finish.</li>
 *   <li><b>Outer glow</b> — wide stroke painted with a BlurMaskFilter so
 *       a soft halo radiates outward from the pill. The blur is on the
 *       <i>halo stroke</i> only, not on the sharp stroke that follows —
 *       so the pill outline itself stays crisp.</li>
 *   <li><b>Sharp stroke</b> — solid accent stroke painted on top of the
 *       glow with no MaskFilter, giving the pill its definite "metal
 *       tube" edge.</li>
 * </ol>
 *
 * <p>The whole view runs on a software layer because BlurMaskFilter is
 * not honoured on hardware-accelerated draws.
 */
public class GlowingPillView extends View {

    /** Sharp stroke width — the thickness of the pill outline itself. */
    private static final float STROKE_DP = 24f;
    /** Halo stroke width — wider than the sharp stroke so the blurred
     *  halo extends visibly past the outline on either side. */
    private static final float GLOW_STROKE_DP = 36f;
    /** BlurMaskFilter radius applied to the halo stroke. */
    private static final float GLOW_RADIUS_DP = 28f;

    private static final float STAGE_MARGIN_HORIZ_DP = 64f;
    private static final float STAGE_TOP_INSET_DP = 64f;
    private static final float STAGE_BOTTOM_INSET_DP = 152f;
    private static final float STAGE_INNER_PAD_VERT_DP = 28f;

    /** Alpha used for the noise overlay (0..255). Low value = subtle. */
    private static final int NOISE_ALPHA = 22;
    private static final int NOISE_ALPHA_LIGHT = 10;

    private static final int[] PILL_GRADIENT_DARK = {
            0xFF1A1A1C, 0xFF222226, 0xFF131315
    };
    private static final int[] PILL_GRADIENT_LIGHT = {
            0xFFF2F0EA, 0xFFFFFFFF, 0xFFE6E1DA
    };

    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mNoisePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private Bitmap mNoiseBitmap;
    private BitmapShader mNoiseShader;

    private int mAccent = Color.RED;
    private ValueAnimator mColorAnim;

    public GlowingPillView(Context c) { this(c, null); }
    public GlowingPillView(Context c, AttributeSet a) { this(c, a, 0); }
    public GlowingPillView(Context c, AttributeSet a, int s) {
        super(c, a, s);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        mFillPaint.setStyle(Paint.Style.FILL);

        mNoisePaint.setStyle(Paint.Style.FILL);
        mNoisePaint.setAlpha(NOISE_ALPHA);

        mGlowPaint.setStyle(Paint.Style.STROKE);
        mGlowPaint.setStrokeWidth(dp(GLOW_STROKE_DP));
        mGlowPaint.setMaskFilter(
                new BlurMaskFilter(dp(GLOW_RADIUS_DP), BlurMaskFilter.Blur.NORMAL));

        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(STROKE_DP));
    }

    public void setAccent(int color, boolean animate) {
        if (color == mAccent) return;
        if (mColorAnim != null) mColorAnim.cancel();
        if (!animate) {
            mAccent = color;
            invalidate();
            return;
        }
        mColorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), mAccent, color);
        mColorAnim.setDuration(420);
        mColorAnim.addUpdateListener(a -> {
            mAccent = (int) a.getAnimatedValue();
            invalidate();
        });
        mColorAnim.start();
    }

    public int getAccent() { return mAccent; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // Re-bake the noise tile at every size change. Tile is small + tiled
        // via REPEAT, so 96x96 is plenty of variation without big bitmap costs.
        final int N = 96;
        if (mNoiseBitmap == null) {
            mNoiseBitmap = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888);
            int[] px = new int[N * N];
            // Seeded so the noise pattern stays stable across config changes
            // — otherwise the surface would "shimmer" on every accent change.
            Random r = new Random(0xC0FFEE);
            for (int i = 0; i < px.length; i++) {
                int v = r.nextInt(48) + 16;   // mid-grey range
                px[i] = Color.argb(255, v, v, v);
            }
            mNoiseBitmap.setPixels(px, 0, N, 0, 0, N, N);
            mNoiseShader = new BitmapShader(mNoiseBitmap,
                    Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            mNoisePaint.setShader(mNoiseShader);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float left   = dp(STAGE_MARGIN_HORIZ_DP);
        float right  = getWidth() - dp(STAGE_MARGIN_HORIZ_DP);
        float top    = dp(STAGE_TOP_INSET_DP) + dp(STAGE_INNER_PAD_VERT_DP);
        float bottom = getHeight() - dp(STAGE_BOTTOM_INSET_DP) - dp(STAGE_INNER_PAD_VERT_DP);
        mRect.set(left, top, right, bottom);
        float radius = (right - left) / 2f;

        // 1. Interior fill — vertical gradient that subtly fakes a curved
        //    metal surface lit from above.
        boolean night = isNightMode();
        LinearGradient grad = new LinearGradient(
                0, top, 0, bottom,
                night ? PILL_GRADIENT_DARK : PILL_GRADIENT_LIGHT,
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP);
        mFillPaint.setShader(grad);
        canvas.drawRoundRect(mRect, radius, radius, mFillPaint);
        mFillPaint.setShader(null);

        // 2. Noise overlay — drawn against the pill shape (the round-rect
        //    clips the noise to the pill silhouette automatically because
        //    we paint via drawRoundRect with the noise BitmapShader fill).
        if (mNoiseShader != null) {
            mNoisePaint.setAlpha(night ? NOISE_ALPHA : NOISE_ALPHA_LIGHT);
            canvas.drawRoundRect(mRect, radius, radius, mNoisePaint);
        }

        // 3. Outer glow — wide blurred stroke. Done BEFORE the sharp stroke
        //    so the sharp edge sits crisply on top of the soft halo.
        mGlowPaint.setColor(withAlpha(mAccent, night ? 0.95f : 0.45f));
        canvas.drawRoundRect(mRect, radius, radius, mGlowPaint);

        // 4. Sharp stroke — solid accent, no blur. This is the "metal tube"
        //    edge from camera.png.
        mStrokePaint.setColor(mAccent);
        canvas.drawRoundRect(mRect, radius, radius, mStrokePaint);
    }

    private static int withAlpha(int color, float factor) {
        int a = Math.round(Color.alpha(color) * factor);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private float dp(float v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
