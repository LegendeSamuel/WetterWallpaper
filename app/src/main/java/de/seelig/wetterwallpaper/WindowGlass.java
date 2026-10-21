package de.seelig.wetterwallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import java.util.concurrent.CompletableFuture;

/**
 * Die Scheibe vor dem Bild: Eisblumen bei Frost (Frost.java), Tropfen und herunterlaufendes Wasser
 * bei Regen (RainOnGlass.java). Liegt über allem, auch über der Uhr.
 * Hinter dem Frost wird die Szene weichgezeichnet (nur auf der Grafikkarte, ab Android 12); jeder
 * Tropfen zeigt das Foto als Linse kopfüber.
 */
final class WindowGlass {
    private static final int SPRITE = 64;
    /** Wie viel größer der Ausschnitt ist, den ein Tropfen kopfüber zeigt. */
    private static final float LENS = 3.5f;
    /** Unschärfe hinter dem Eis in px bei 1080 px Breite. */
    private static final float FROST_BLUR = 28f;

    private final int width;
    private final int height;
    private final float unit;
    private final RainOnGlass rain;
    private final Bitmap drop;
    private final Bitmap dropOverLens;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint lensPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint maskPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final Matrix inverse = new Matrix();
    private final float[] pt = new float[2];
    private Bitmap shaderPhoto;
    private final RenderNode blurNode = new RenderNode("frostBlur");
    private long lastUpdate;

    // Frost: Textur einmal, Zuschnitt je Stärke; beides im Hintergrund, bis dahin bleibt die Scheibe klar
    private int[] frostTex;
    private volatile Bitmap frost;
    private float frostAmount = -1;
    private CompletableFuture<?> frostJob;

    WindowGlass(int width, int height) {
        this.width = width;
        this.height = height;
        unit = width / 1080f;
        rain = new RainOnGlass(width, height);
        drop = Bitmap.createBitmap(RainOnGlass.sprite(SPRITE, false), SPRITE, SPRITE, Bitmap.Config.ARGB_8888);
        dropOverLens = Bitmap.createBitmap(RainOnGlass.sprite(SPRITE, true), SPRITE, SPRITE, Bitmap.Config.ARGB_8888);
        // die Unschärfe nur dort, wo Eis ist; der Eisschleier selbst ist halb durchsichtig, die Maske deckender
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        maskPaint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 2.2f, 0})));
        blurNode.setPosition(0, 0, width, height);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float r = FROST_BLUR * unit;
            blurNode.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
        }
    }

    /**
     * Bewegt die Tropfen. motion = flüssige Animation erlaubt; sonst steht die Scheibe, trocknet nach dem
     * Regen aber trotzdem nach der echten Zeit ab.
     */
    void update(Weather wx, long now, boolean motion) {
        float dt = lastUpdate == 0 ? 0 : (now - lastUpdate) / 1000f;
        lastUpdate = now;
        if (motion) rain.update(wx.rain, Math.min(0.1f, dt));
        else rain.update(wx.rain, wx.rain > 0 ? 0 : Math.min(60f, dt));
    }

    boolean animating(Weather wx) {
        return wx.rain > 0 || rain.running();
    }

    /** Liegt (bald) Eis auf der Scheibe? Dann zeichnet der Renderer die Szene für die Unschärfe auf. */
    boolean frosted(Weather wx) {
        return wx.frost > 0.02f;
    }

    /**
     * light = Umgebungshelligkeit 0..1: nachts glänzen Tropfen und Eis gedämpft. scene = aufgezeichnete
     * Szene für die Unschärfe hinter dem Eis oder null. photo/fit/grade: Foto, wie es im Bild liegt, für
     * die Tropfen-Linsen; photo null = Tropfen ohne Linse.
     */
    void draw(Canvas c, Weather wx, float light, RenderNode scene, Bitmap photo, Matrix fit, ColorFilter grade) {
        float lit = 0.35f + 0.65f * Math.min(1f, light);
        int shade = Math.round(255 * lit);
        paint.setColorFilter(new PorterDuffColorFilter(0xFF000000 | shade << 16 | shade << 8 | shade, PorterDuff.Mode.MULTIPLY));
        drawFrost(c, wx.frost, scene);
        drawRain(c, lit, photo, fit, grade);
        paint.setColorFilter(null);
        paint.setAlpha(255);
    }

    private void drawFrost(Canvas c, float amount, RenderNode scene) {
        // auf 5 % runden: Zuschnitt nur bei spürbarer Änderung neu
        float want = Math.round(amount * 20) / 20f;
        if (want != frostAmount && (frostJob == null || frostJob.isDone())) {
            frostAmount = want;
            if (want <= 0) {
                frost = null;
            } else {
                int fw = Math.max(1, width / 2), fh = Math.max(1, height / 2); // Eis ist weich, halbe Auflösung reicht
                frostJob = CompletableFuture.runAsync(() -> {
                    if (frostTex == null) frostTex = Frost.texture(fw, fh, 17);
                    int[] out = new int[frostTex.length];
                    Frost.apply(frostTex, want, out);
                    frost = Bitmap.createBitmap(out, fw, fh, Bitmap.Config.ARGB_8888);
                });
            }
        }
        Bitmap f = frost;
        if (f == null || frostAmount <= 0) return;
        dst.set(0, 0, width, height);
        if (scene != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RecordingCanvas rc = blurNode.beginRecording(width, height);
            try {
                rc.drawRenderNode(scene);
            } finally {
                blurNode.endRecording();
            }
            int layer = c.saveLayer(null, null);
            c.drawRenderNode(blurNode);
            c.drawBitmap(f, null, dst, maskPaint);
            c.restoreToCount(layer);
        }
        paint.setAlpha(255);
        c.drawBitmap(f, null, dst, paint);
    }

    private void drawRain(Canvas c, float lit, Bitmap photo, Matrix fit, ColorFilter grade) {
        if (!rain.wet()) return;
        boolean refract = photo != null && fit.invert(inverse);
        if (refract && photo != shaderPhoto) {
            lensPaint.setShader(new BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            shaderPhoto = photo;
        }
        lensPaint.setColorFilter(grade);
        float fitScale = refract ? fit.mapRadius(1f) : 1f;
        Bitmap sprite = refract ? dropOverLens : drop;
        paint.setAlpha(Math.round(255 * (0.55f + 0.45f * lit)));
        for (int i = 0; i < rain.count; i++) {
            float x = rain.x[i], y = rain.y[i], r = rain.r[i];
            if (refract && r > 2.5f * unit) lens(c, x, y, r, fit, fitScale); // winzige Tropfen: Linse lohnt nicht
            dst.set(x - r, y - r, x + r, y + r);
            c.drawBitmap(sprite, null, dst, paint);
        }
        for (int k = 0; k < RainOnGlass.MAX_RUNNERS; k++) {
            if (!rain.active[k]) continue;
            float r = rain.rr[k];
            float rh = rain.moving[k] ? r * 1.25f : r; // laufend leicht in die Länge gezogen
            if (refract) lens(c, rain.rx[k], rain.ry[k] - rh + r, r, fit, fitScale);
            dst.set(rain.rx[k] - r, rain.ry[k] - 2 * rh + r, rain.rx[k] + r, rain.ry[k] + r);
            c.drawBitmap(sprite, null, dst, paint);
        }
    }

    /** Der Tropfen bei (x, y) als Linse: zeigt einen LENS-mal größeren Ausschnitt des Fotos kopfüber. */
    private void lens(Canvas c, float x, float y, float r, Matrix fit, float fitScale) {
        pt[0] = x;
        pt[1] = y;
        inverse.mapPoints(pt);
        c.save();
        c.translate(x, y);
        c.scale(-1f / LENS, -1f / LENS);
        c.translate(-x, -y);
        c.concat(fit);
        c.drawCircle(pt[0], pt[1], r * LENS / fitScale, lensPaint);
        c.restore();
    }
}
