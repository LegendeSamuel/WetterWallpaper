package de.seelig.wetterwallpaper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * Wolken (zwei vorgerenderte, horizontal kachelbare Texturen), Regen und Schnee in einer fernen Ebene
 * (hinter der Uhr) und einer nahen Ebene (vor dem Motiv), Blitze bei Gewitter.
 */
final class WeatherFx {
    private static final int FAR_MAX = 400;
    private static final int NEAR_MAX = 90;

    private final int width;
    private final int height;
    private final Bitmap scattered;
    private final Bitmap dense;
    private final Paint cloudPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final Random rnd = new Random();
    // Partikel: x, y, Größe, Tempo, Phase
    private final float[] far = new float[FAR_MAX * 5];
    private final float[] near = new float[NEAR_MAX * 5];
    private final float[] lines = new float[Math.max(FAR_MAX, NEAR_MAX) * 4];
    private long lastUpdate;
    private long nextFlash;
    private long flashStart = -1;

    WeatherFx(int w, int h) {
        width = w;
        height = h;
        // halbe Auflösung reicht, Wolken sind weich; Textur deckt die obere Bildschirmhälfte
        scattered = cloudTexture(w / 2, h / 4, 0.35f, 11);
        dense = cloudTexture(w / 2, h / 4, 0.8f, 23);
        seed(far, FAR_MAX);
        seed(near, NEAR_MAX);
    }

    /**
     * Wolkenfeld aus mehrlagigem Rauschen, horizontal nahtlos kachelbar. Zum Horizont (unten) hin
     * flacher gestaucht; von oben beleuchtet: was darüber liegt, schattet die Unterseite ab.
     */
    private static Bitmap cloudTexture(int w, int h, float coverage, int seed) {
        final int period = 4; // Grundzellen über die Breite
        float[] d = new float[w * h];
        for (int y = 0; y < h; y++) {
            float yy = y / (float) h;
            float ny = (yy + 1.5f * yy * yy) * 3f;
            for (int x = 0; x < w; x++) {
                float f = Noise.fbm(x / (float) w * period, ny, period, seed, 5);
                d[y * w + x] = smoothstep(1 - coverage - 0.08f, 1 - coverage + 0.22f, f);
            }
        }
        int[] px = new int[w * h];
        int dy = Math.max(1, h / 30);
        for (int y = 0; y < h; y++) {
            // über dem Rand die oberste Zeile nehmen, sonst entsteht oben ein ungeschatteter heller Streifen
            float fade = smoothstep(1f, 0.75f, y / (float) h); // weich auslaufen statt harter Kante unten
            for (int x = 0; x < w; x++) {
                float above = d[Math.max(0, y - dy) * w + x];
                float shade = 0.82f + 0.18f * (1 - 0.6f * above);
                int g = Math.round(255 * shade);
                int a = Math.round(d[y * w + x] * fade * 255);
                px[y * w + x] = (a << 24) | (Math.round(g * 0.96f) << 16) | (Math.round(g * 0.98f) << 8) | g;
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    private void seed(float[] p, int n) {
        for (int i = 0; i < n; i++) {
            p[i * 5] = rnd.nextFloat() * width;
            p[i * 5 + 1] = rnd.nextFloat() * height;
            p[i * 5 + 2] = rnd.nextFloat();
            p[i * 5 + 3] = 0.7f + rnd.nextFloat() * 0.6f;
            p[i * 5 + 4] = rnd.nextFloat() * 6.28f;
        }
    }

    /** Zieht Wolken über den Himmel; tint färbt sie passend zum Licht. */
    void drawClouds(Canvas c, Weather wx, int tint, long now) {
        if (wx.cloud < 0.02f) return;
        cloudPaint.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.MULTIPLY));
        float drift = (0.004f + wx.wind * 0.02f) * width; // px pro Sekunde
        drawLayer(c, scattered, clamp01(wx.cloud * 2f), now / 1000f * drift);
        drawLayer(c, dense, clamp01((wx.cloud - 0.4f) / 0.6f), now / 1000f * drift * 0.6f + width * 0.37f);
    }

    private void drawLayer(Canvas c, Bitmap tex, float alpha, float offset) {
        if (alpha <= 0.01f) return;
        cloudPaint.setAlpha(Math.round(alpha * 255));
        float scale = width / (float) tex.getWidth();
        float x = offset % width;
        for (float dx : new float[]{x - width, x}) {
            m.setScale(scale, scale);
            m.postTranslate(dx, 0);
            c.drawBitmap(tex, m, cloudPaint);
        }
    }

    /** Bewegt die Partikel; ohne Aufruf bleiben sie stehen (statisches Bild spart Akku). */
    void update(Weather wx, Season season, long now) {
        float dt = lastUpdate == 0 ? 0 : Math.min(0.1f, (now - lastUpdate) / 1000f);
        lastUpdate = now;
        boolean hasSeasonal = !wx.hasParticles() && (season == Season.AUTUMN || season == Season.SPRING);
        if ((!wx.hasParticles() && !hasSeasonal) || dt == 0) return;
        boolean snow = wx.snow > wx.rain;
        boolean floating = snow || hasSeasonal;
        float speed = snow ? 90 : (hasSeasonal ? 110 : 1500);
        float nearSpeed = snow ? 220 : (hasSeasonal ? 210 : 2600);
        float windX = wx.wind * (floating ? 120 : 500);
        move(far, FAR_MAX, dt, speed, windX * 0.7f, floating);
        move(near, NEAR_MAX, dt, nearSpeed, windX, floating);
    }

    private void move(float[] p, int n, float dt, float speed, float windX, boolean swayActive) {
        for (int i = 0; i < n; i++) {
            int o = i * 5;
            float sway = swayActive ? (float) Math.sin(lastUpdate / 900.0 + p[o + 4]) * 40 : 0;
            p[o] += (windX + sway) * dt;
            p[o + 1] += speed * p[o + 3] * dt;
            if (p[o + 1] > height) {
                p[o + 1] -= height + 120;
                p[o] = rnd.nextFloat() * width;
            }
            if (p[o] > width + 50) p[o] -= width + 100;
            if (p[o] < -50) p[o] += width + 100;
        }
    }

    /** nearLayer = vordere Ebene (groß, vor dem Motiv), sonst ferne Ebene. light = Umgebungshelligkeit 0..1. */
    void drawParticles(Canvas c, Weather wx, Season season, boolean nearLayer, float light) {
        float[] p = nearLayer ? near : far;
        int max = nearLayer ? NEAR_MAX : FAR_MAX;
        float alphaBase = 0.35f + 0.65f * light;
        if (wx.rain > 0) {
            int n = Math.round(max * wx.rain);
            float len = nearLayer ? 90 : 26;
            float lean = wx.wind * 0.35f;
            for (int i = 0; i < n; i++) {
                int o = i * 5;
                float l = len * (0.6f + p[o + 2] * 0.6f);
                lines[i * 4] = p[o];
                lines[i * 4 + 1] = p[o + 1];
                lines[i * 4 + 2] = p[o] + l * lean;
                lines[i * 4 + 3] = p[o + 1] + l;
            }
            paint.setShader(null);
            paint.setColor(0xFFDDE6F0);
            paint.setAlpha(Math.round(255 * alphaBase * (nearLayer ? 0.3f : 0.55f)));
            paint.setStrokeWidth(nearLayer ? 3f : 1.7f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            c.drawLines(lines, 0, n * 4, paint);
        }
        if (wx.snow > 0) {
            int n = Math.round(max * wx.snow);
            paint.setShader(null);
            paint.setColor(0xFFFFFFFF);
            for (int i = 0; i < n; i++) {
                int o = i * 5;
                float r = nearLayer ? 5 + p[o + 2] * 6 : 1.4f + p[o + 2] * 2.2f;
                if (nearLayer) { // nahe Flocken unscharf: weicher Rand
                    paint.setAlpha(Math.round(255 * alphaBase * 0.18f));
                    c.drawCircle(p[o], p[o + 1], r * 1.6f, paint);
                }
                paint.setAlpha(Math.round(255 * alphaBase * (nearLayer ? 0.45f : 0.8f)));
                c.drawCircle(p[o], p[o + 1], r, paint);
            }
        }
        if (!wx.hasParticles() && season != null) {
            if (season == Season.AUTUMN) {
                // Herbstlaub: rotierend taumelnde Blätter in Bernstein, Goldgelb und Rostrot
                int n = nearLayer ? 5 : 14;
                int[] colors = {0xFFD97724, 0xFFE5A932, 0xFFB23A28, 0xFFC95420};
                for (int i = 0; i < n; i++) {
                    int o = i * 5;
                    float sz = nearLayer ? (7f + p[o + 2] * 8f) : (3.5f + p[o + 2] * 4.5f);
                    float rot = (float) Math.toDegrees(lastUpdate / 600.0 + p[o + 4]);
                    paint.setShader(null);
                    paint.setColor(colors[i % colors.length]);
                    paint.setAlpha(Math.round(255 * alphaBase * (nearLayer ? 0.6f : 0.45f)));
                    c.save();
                    c.translate(p[o], p[o + 1]);
                    c.rotate(rot);
                    c.drawOval(-sz, -sz * 0.45f, sz, sz * 0.45f, paint);
                    c.restore();
                }
            } else if (season == Season.SPRING) {
                // Frühling: sanft schwebende Kirschblütenblätter (Sakura)
                int n = nearLayer ? 10 : 24;
                int[] colors = {0xFFFFD8E4, 0xFFFFEFF4, 0xFFFFC2D4};
                for (int i = 0; i < n; i++) {
                    int o = i * 5;
                    float sz = nearLayer ? (6f + p[o + 2] * 6f) : (3f + p[o + 2] * 3.5f);
                    float rot = (float) Math.toDegrees(lastUpdate / 800.0 + p[o + 4]);
                    paint.setShader(null);
                    paint.setColor(colors[i % colors.length]);
                    paint.setAlpha(Math.round(255 * alphaBase * (nearLayer ? 0.7f : 0.55f)));
                    c.save();
                    c.translate(p[o], p[o + 1]);
                    c.rotate(rot);
                    c.drawOval(-sz, -sz * 0.65f, sz, sz * 0.65f, paint);
                    c.restore();
                }
            }
        }
    }

    boolean hasParticles(Weather wx, Season season) {
        return (wx != null && wx.hasParticles()) || season == Season.AUTUMN || season == Season.SPRING;
    }

    /** Helligkeit eines Blitzes 0..1 (Doppelblitz, schnell abklingend). */
    float flash(Weather wx, long now) {
        if (!wx.thunder) return 0;
        if (flashStart < 0 && now > nextFlash) flashStart = now;
        if (flashStart < 0) return 0;
        float t = (now - flashStart) / 1000f;
        if (t > 0.6f) {
            flashStart = -1;
            nextFlash = now + 6000 + rnd.nextInt(14000);
            return 0;
        }
        float a = (float) Math.exp(-t * 14);
        float b = t > 0.18f ? (float) Math.exp(-(t - 0.18f) * 10) * 0.8f : 0;
        return Math.min(1, a + b);
    }

    void drawFlash(Canvas c, float flash) {
        if (flash <= 0.01f) return;
        paint.setShader(null);
        paint.setColor(0xFFEEF2FF);
        paint.setAlpha(Math.round(flash * 0.5f * 255));
        c.drawRect(0, 0, width, height, paint);
    }

    private static float clamp01(float x) {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }
}
