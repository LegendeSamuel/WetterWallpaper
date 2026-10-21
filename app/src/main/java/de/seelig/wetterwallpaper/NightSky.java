package de.seelig.wetterwallpaper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/** Sternenfeld mit Milchstraße (einmal vorgerendert), funkelnde helle Sterne, Sternschnuppen, Mond. */
final class NightSky {
    private static final int[] STAR_COLORS = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFCFE0FF, 0xFFAFC8FF, 0xFFFFE6C8, 0xFFFFD2A8};
    private static final int BRIGHT = 45;

    private final Bitmap field;
    private final float[] bright = new float[BRIGHT * 4]; // x, y, Radius, Phase
    private final int[] brightColor = new int[BRIGHT];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final int width;
    private final float skyBottom;
    private long nextMeteor;
    private long meteorStart = -1;
    private float meteorX;
    private float meteorY;
    private float meteorAngle;

    NightSky(int w, int h) {
        width = w;
        skyBottom = h * 0.5f;
        field = Bitmap.createBitmap(w, Math.round(skyBottom), Bitmap.Config.ARGB_8888);
        Random r = new Random(42);
        renderField(new Canvas(field), r);
        for (int i = 0; i < BRIGHT; i++) {
            bright[i * 4] = r.nextFloat() * w;
            bright[i * 4 + 1] = r.nextFloat() * skyBottom;
            bright[i * 4 + 2] = 1.6f + r.nextFloat() * 1.8f;
            bright[i * 4 + 3] = r.nextFloat() * 6.28f;
            brightColor[i] = STAR_COLORS[r.nextInt(STAR_COLORS.length)];
        }
        nextMeteor = System.currentTimeMillis() + 8000;
    }

    private void renderField(Canvas c, Random r) {
        // Milchstraße als flach geneigtes Band durch den oberen, meist sichtbaren Himmel
        float x0 = -0.1f * width, y0 = skyBottom * 0.5f, x1 = 1.1f * width, y1 = skyBottom * 0.06f;
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        float nx = -(y1 - y0) / len, ny = (x1 - x0) / len;
        float band = width * 0.11f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] glowColors = {0xFF9FB4FF, 0xFFFFD9B0, 0xFFC8A8FF, 0xFFE8ECFF};
        for (int i = 0; i < 170; i++) {
            float t = r.nextFloat();
            float off = (float) r.nextGaussian() * band * 0.6f;
            float cx = x0 + (x1 - x0) * t + nx * off, cy = y0 + (y1 - y0) * t + ny * off;
            float rad = width * (0.04f + r.nextFloat() * 0.09f);
            int col = glowColors[r.nextInt(glowColors.length)];
            int a = 10 + r.nextInt(18);
            p.setShader(new RadialGradient(cx, cy, rad, (a << 24) | (col & 0xFFFFFF), col & 0xFFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rad, p);
        }
        // dunkle Staubbahnen leicht neben der Bandmitte
        for (int i = 0; i < 70; i++) {
            float t = r.nextFloat();
            float off = (float) r.nextGaussian() * band * 0.2f + band * 0.12f;
            float cx = x0 + (x1 - x0) * t + nx * off, cy = y0 + (y1 - y0) * t + ny * off;
            float rad = width * (0.015f + r.nextFloat() * 0.04f);
            p.setShader(new RadialGradient(cx, cy, rad, 0x22000000, 0x00000000, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rad, p);
        }
        p.setShader(null);
        // Sterne, im Band dichter
        int placed = 0;
        while (placed < 2800) {
            float x = r.nextFloat() * width, y = r.nextFloat() * skyBottom;
            float d = Math.abs((x - x0) * nx + (y - y0) * ny);
            if (r.nextFloat() > 0.2f + 0.8f * Math.exp(-(d / band) * (d / band))) continue;
            float k = r.nextFloat();
            p.setColor(STAR_COLORS[r.nextInt(STAR_COLORS.length)]);
            p.setAlpha(40 + Math.round(k * 170));
            c.drawCircle(x, y, 0.5f + k * k * k * 1.3f, p);
            placed++;
        }
    }

    /** true solange Sterne sichtbar sind und deshalb flüssig neu gezeichnet werden sollte. */
    boolean animating(float visibility) {
        return visibility > 0.05f;
    }

    void drawStars(Canvas c, float visibility, long now) {
        if (visibility <= 0.01f) return;
        paint.setShader(null);
        paint.setAlpha(Math.round(visibility * 255));
        c.drawBitmap(field, 0, 0, paint);
        for (int i = 0; i < BRIGHT; i++) {
            float twinkle = 0.6f + 0.4f * (float) Math.sin(now / 420.0 + bright[i * 4 + 3]);
            float x = bright[i * 4], y = bright[i * 4 + 1], rad = bright[i * 4 + 2];
            paint.setColor(brightColor[i]);
            paint.setAlpha(Math.round(visibility * twinkle * 50));
            c.drawCircle(x, y, rad * 3f, paint);
            paint.setAlpha(Math.round(visibility * twinkle * 255));
            c.drawCircle(x, y, rad, paint);
        }
        drawMeteor(c, visibility, now);
    }

    private void drawMeteor(Canvas c, float visibility, long now) {
        if (meteorStart < 0 && now > nextMeteor && visibility > 0.6f) {
            meteorStart = now;
            meteorX = width * (0.2f + rnd.nextFloat() * 0.7f);
            meteorY = skyBottom * (0.05f + rnd.nextFloat() * 0.35f);
            meteorAngle = (float) Math.toRadians(140 + rnd.nextFloat() * 30);
        }
        if (meteorStart < 0) return;
        float t = (now - meteorStart) / 700f;
        if (t > 1) {
            meteorStart = -1;
            nextMeteor = now + 15000 + rnd.nextInt(30000);
            return;
        }
        float dist = width * 0.35f * t;
        float hx = meteorX + (float) Math.cos(meteorAngle) * dist;
        float hy = meteorY + (float) Math.sin(meteorAngle) * dist;
        float tail = width * 0.12f;
        float tx = hx - (float) Math.cos(meteorAngle) * tail;
        float ty = hy - (float) Math.sin(meteorAngle) * tail;
        int a = Math.round(visibility * 255 * (float) Math.sin(Math.PI * t));
        paint.setShader(new LinearGradient(tx, ty, hx, hy, 0x00FFFFFF, (a << 24) | 0xFFFFFF, Shader.TileMode.CLAMP));
        paint.setStrokeWidth(2.5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(tx, ty, hx, hy, paint);
        paint.setShader(null);
    }

    /** Mond mit Halo, Erdschein und Phase; phase 0 = Neumond, 0.5 = Vollmond. */
    void drawMoon(Canvas c, float cx, float cy, float r, double phase, float visibility) {
        if (visibility <= 0.01f) return;
        float illum = (float) SkyPosition.moonIllumination(phase);
        int vis = Math.round(visibility * 255);
        paint.setShader(new RadialGradient(cx, cy, r * 9, Color.argb(Math.round(vis * 0.22f * illum), 210, 220, 255),
                0x00D2DCFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 9, paint);
        paint.setShader(null);
        paint.setColor(0xFF1E2436);
        paint.setAlpha(Math.round(vis * 0.85f));
        c.drawCircle(cx, cy, r, paint);

        c.save();
        boolean waning = phase > 0.5;
        if (waning) c.scale(-1, 1, cx, cy); // abnehmend = zunehmend gespiegelt
        double p = waning ? 1 - phase : phase;
        float rx = r * (float) Math.cos(2 * Math.PI * p);
        Path lit = new Path();
        lit.arcTo(new RectF(cx - r, cy - r, cx + r, cy + r), -90, 180);
        lit.arcTo(new RectF(cx - Math.abs(rx), cy - r, cx + Math.abs(rx), cy + r), 90, rx > 0 ? -180 : 180);
        lit.close();
        paint.setColor(0xFFF3F0E6);
        paint.setAlpha(vis);
        c.drawPath(lit, paint);
        // Mare als dunklere Flecken, nur auf der beleuchteten Seite
        c.clipPath(lit);
        paint.setColor(0xFF9A9C9E);
        paint.setAlpha(Math.round(vis * 0.22f));
        float[][] maria = {{-0.25f, -0.3f, 0.32f}, {0.2f, -0.15f, 0.25f}, {-0.1f, 0.25f, 0.28f}, {0.3f, 0.3f, 0.15f}};
        for (float[] m : maria) c.drawCircle(cx + m[0] * r, cy + m[1] * r, m[2] * r, paint);
        c.restore();
    }
}
