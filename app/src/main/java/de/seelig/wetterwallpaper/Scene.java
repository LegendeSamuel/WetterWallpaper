package de.seelig.wetterwallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Bildebenen eines Fotos, abgeleitet aus background.png + depth.png (+ foreground.png).
 * depth.png: Graustufen, Wert = sqrt(Nähe) * 255, damit ferne Bereiche fein aufgelöst bleiben.
 */
final class Scene {
    /** Foto-Himmel (Alpha = Himmelsmaske), null wenn keine Tiefenkarte da ist. */
    final Bitmap sky;
    /** Wie stark der echte Himmel das Foto ersetzt (ALPHA_8, EdgeRefine.softSky) für blendSoftness; null ohne Tiefe. */
    private volatile Bitmap blend;
    /** Weichheit, für die blend gilt oder gerade gerechnet wird. */
    private float blendSoftness;
    private CompletableFuture<?> blendJob;
    /** Alles außer Himmel. */
    final Bitmap land;
    /** Motiv vor der Uhr, kann null sein. */
    final Bitmap subject;
    /** ALPHA_8-Masken für Land bzw. Motiv: Dunst (fern = viel). */
    final Bitmap haze;
    final Bitmap subjectHaze;
    /** Pflanzen (Alpha = Pflanzenanteil) für Land bzw. Motiv: Jahreszeitenfarbe nur dort; null ohne Pflanzen. */
    final Bitmap plants;
    final Bitmap subjectPlants;
    /** Mittlere Helligkeit des Fotos hinter der Uhr (0..1), für automatische Uhrfarbe. */
    final float clockLuminance;
    /** Tatsächlich verwendete Schwelle für "liegt vor der Uhr" (auch wenn automatisch bestimmt). */
    final float subjectThreshold;
    /** Verfeinerte Tiefe je Pixel als Byte (sqrt(Nähe) × 255): Masken für Widgets und Ebenen-Markierung ohne Neuberechnung. */
    final byte[] depth8;
    /** Das Foto selbst und eine stark verkleinerte, damit weiche Fassung: Füllungen Bild und Milchglas der Schrift. */
    final Bitmap photo;
    final Bitmap photoSoft;

    private Scene(Bitmap sky, Bitmap blend, float blendSoftness, Bitmap land, Bitmap subject, Bitmap[] masks, Bitmap[] plantLayers,
                  float clockLuminance, float subjectThreshold, byte[] depth8, Bitmap photo) {
        this.photo = photo;
        this.photoSoft = Bitmap.createScaledBitmap(photo, Math.max(1, photo.getWidth() / 12), Math.max(1, photo.getHeight() / 12), true);
        this.plants = plantLayers[0];
        this.subjectPlants = plantLayers[1];
        this.depth8 = depth8;
        this.clockLuminance = clockLuminance;
        this.subjectThreshold = subjectThreshold;
        this.sky = sky;
        this.blend = blend;
        this.blendSoftness = blendSoftness;
        this.land = land;
        this.subject = subject;
        this.haze = masks[0];
        this.subjectHaze = masks[1];
    }

    /**
     * subjectThreshold: ab dieser Nähe liegt etwas vor der Uhr; NaN = automatisch aus dem, was hinter
     * clockBox liegt. Gilt nur, wenn keine foreground.png (alte PC-Freistellung) existiert.
     * Himmel kommt aus sky.png (vom Import), sonst aus der Tiefe (alter PC-Weg).
     * targetWidth: Ebenen höchstens so breit berechnen (Vorschau braucht weniger), 0 = Originalgröße.
     * clockBoxRel: Uhrfläche relativ zur Bildgröße (0..1).
     */
    static Scene load(File dir, int targetWidth, float skyThreshold, float hazeRange, float subjectThreshold,
                      RectF clockBoxRel, float skySoftness) {
        Bitmap bg = decode(new File(dir, "background.png"), 0, 0);
        if (bg == null) return null;
        if (targetWidth > 0 && bg.getWidth() > targetWidth) {
            bg = Bitmap.createScaledBitmap(bg, targetWidth, Math.round(bg.getHeight() * targetWidth / (float) bg.getWidth()), true);
        }
        int w = bg.getWidth();
        int h = bg.getHeight();
        float k = w / 1080f; // Filterradien sind auf 1080 px Breite abgestimmt
        RectF clockBox = clockBoxRel == null ? null
                : new RectF(clockBoxRel.left * w, clockBoxRel.top * h, clockBoxRel.right * w, clockBoxRel.bottom * h);
        Bitmap subject = decode(new File(dir, "foreground.png"), w, h);
        Bitmap depth = decode(new File(dir, "depth.png"), w, h);
        if (depth == null) return new Scene(null, null, 0, bg, subject, new Bitmap[2], new Bitmap[2],
                0.5f, subjectThreshold, null, bg);

        int n = w * h;
        int[] px = pixels(bg);
        int[] fg = subject != null ? pixels(subject) : null;
        float[] v = new float[n];   // sqrt(Nähe)
        float[] lum = new float[n];
        int[] dp = pixels(depth);
        for (int i = 0; i < n; i++) {
            v[i] = (dp[i] & 0xFF) / 255f;
            int c = px[i];
            lum[i] = (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
        }
        dp = null;
        // Tiefe kommt grob (~518 px) und hochskaliert: an die Kanten des Fotos anlegen
        v = EdgeRefine.guided(lum, v, w, h, Math.max(2, Math.round(12 * k)), 1e-3f);
        for (int i = 0; i < n; i++) v[i] = clamp01(v[i]);
        Bitmap skyBmp = decode(new File(dir, "sky.png"), w, h);
        int[] skyMask = skyBmp != null ? pixels(skyBmp) : null;
        float[] skyA = new float[n];
        for (int i = 0; i < n; i++) {
            skyA[i] = skyMask != null
                    ? (skyMask[i] & 0xFF) / 255f
                    : clamp01(0.5f + (skyThreshold - v[i] * v[i]) / skyThreshold);
        }
        skyMask = null;
        float[] depthSky = skyA;
        skyA = EdgeRefine.refineSky(px, lum, depthSky, w, h, k);
        skyA = EdgeRefine.widenToBlur(lum, depthSky, skyA, w, h, Math.max(2, Math.round(48 * k)),
                Math.max(1, Math.round(24 * k)));
        depthSky = null;
        // unabhängige Schritte parallel (Szene auf dem Handy sonst ~7 s); keiner verändert seine Eingaben
        final float[] skyF = skyA;
        CompletableFuture<float[]> blendJob = CompletableFuture.supplyAsync(() -> EdgeRefine.softSky(skyF, w, h, k, skySoftness));
        CompletableFuture<int[]> landJob = CompletableFuture.supplyAsync(
                () -> EdgeRefine.landColors(px, skyF, w, h, Math.max(2, Math.round(48 * k))));
        CompletableFuture<float[]> vegJob = CompletableFuture.supplyAsync(() -> Vegetation.mask(px, w, h, k));
        if (Float.isNaN(subjectThreshold)) subjectThreshold = autoSubjectThreshold(v, w, h, clockBox);
        float clockLum = meanIn(lum, w, h, clockBox);
        int[] landRgb = landJob.join();
        float[] veg = vegJob.join();
        int[] plantPx = new int[n];
        int[] subjectPlantPx = new int[n];
        double vegSum = 0;

        int[] skyPx = new int[n];
        int[] landPx = new int[n];
        int[] subjectPx = fg == null ? new int[n] : null;
        // schmaler Übergang, sonst scheint Fernes halb durch die Uhr
        float subjectSoft = Math.max(0.004f, subjectThreshold * 0.15f);
        byte[][] m = new byte[2][n];
        for (int i = 0; i < n; i++) {
            float near = v[i] * v[i];
            float sky = skyA[i];
            float ground = 1f - sky;
            int rgb = px[i] & 0xFFFFFF;
            int a = Math.round(sky * 255);
            skyPx[i] = (a << 24) | rgb;
            landPx[i] = ((255 - a) << 24) | (landRgb[i] & 0xFFFFFF);

            float far = 1f - clamp01(near / hazeRange);
            float haze = far * far * ground;

            float fa = fg != null
                    ? (fg[i] >>> 24) / 255f
                    : clamp01((near - subjectThreshold) / subjectSoft + 0.5f);
            float plant = veg[i] * ground;
            vegSum += plant;
            plantPx[i] = (Math.round(plant * 255) << 24) | (landRgb[i] & 0xFFFFFF);
            subjectPlantPx[i] = (Math.round(plant * fa * 255) << 24) | rgb;

            if (subjectPx != null) subjectPx[i] = (Math.round(fa * 255) << 24) | rgb;
            m[0][i] = (byte) Math.round(haze * 255);
            m[1][i] = (byte) Math.round(haze * fa * 255);
        }
        if (subjectPx != null) subject = Bitmap.createBitmap(subjectPx, w, h, Bitmap.Config.ARGB_8888);
        Bitmap[] masks = {alpha8(m[0], w, h), alpha8(m[1], w, h)};
        // kaum Pflanzen im Bild: zwei Vollbild-Ebenen sparen
        Bitmap[] plantLayers = vegSum / n < 0.005 ? new Bitmap[2] : new Bitmap[]{
                Bitmap.createBitmap(plantPx, w, h, Bitmap.Config.ARGB_8888),
                Bitmap.createBitmap(subjectPlantPx, w, h, Bitmap.Config.ARGB_8888)};
        float[] blend = blendJob.join();
        byte[] blend8 = new byte[n];
        for (int i = 0; i < n; i++) blend8[i] = (byte) Math.round(clamp01(blend[i]) * 255);
        return new Scene(Bitmap.createBitmap(skyPx, w, h, Bitmap.Config.ARGB_8888), alpha8(blend8, w, h), skySoftness,
                Bitmap.createBitmap(landPx, w, h, Bitmap.Config.ARGB_8888), subject, masks, plantLayers, clockLum, subjectThreshold, toBytes(v), bg);
    }

    /**
     * Himmelsüberblendung für softness 0..1 (scharf..weich). Bei einer anderen Weichheit als bisher wird sie
     * im Hintergrund neu gerechnet (aus dem Alpha des Foto-Himmels); bis dahin gilt die alte.
     */
    synchronized Bitmap skyBlend(float softness) {
        if (sky == null) return null;
        float want = Math.round(softness * 20) / 20f;
        if (want != blendSoftness && (blendJob == null || blendJob.isDone())) {
            blendSoftness = want;
            blendJob = CompletableFuture.runAsync(() -> {
                int w = sky.getWidth(), h = sky.getHeight();
                int[] px = pixels(sky);
                float[] a = new float[px.length];
                for (int i = 0; i < a.length; i++) a[i] = (px[i] >>> 24) / 255f;
                float[] b = EdgeRefine.softSky(a, w, h, w / 1080f, want);
                byte[] b8 = new byte[b.length];
                for (int i = 0; i < b.length; i++) b8[i] = (byte) Math.round(clamp01(b[i]) * 255);
                blend = alpha8(b8, w, h);
            });
        }
        return blend;
    }

    private static byte[] toBytes(float[] v) {
        byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) b[i] = (byte) Math.round(clamp01(v[i]) * 255);
        return b;
    }

    /** sqrt(Nähe) aus depth8. */
    private static float v8(byte[] d, int i) {
        return (d[i] & 0xFF) / 255f;
    }

    /**
     * Ausschnitt über einem Element (Widget): was näher ist als seine Ebene, wird darüber gezeichnet.
     * mask = ALPHA_8 ab (x, y) in Szenenpixeln, null wenn nichts davor liegt.
     */
    static final class Cut {
        final Bitmap mask;
        final int x;
        final int y;
        final float threshold;

        private Cut(Bitmap mask, int x, int y, float threshold) {
            this.mask = mask;
            this.x = x;
            this.y = y;
            this.threshold = threshold;
        }
    }

    /** Maske für ein Element; box in Szenenpixeln, threshold NaN = automatisch (was dahinter liegt, bleibt hinten). */
    Cut cut(RectF box, float threshold) {
        if (depth8 == null || box == null || box.isEmpty()) return null;
        int w = width();
        int h = height();
        if (Float.isNaN(threshold)) threshold = autoThreshold8(depth8, w, h, box);
        float soft = Math.max(0.004f, threshold * 0.15f);
        // etwas Rand für Effekte; Breite auf 4 runden (ALPHA_8-Zeilen ohne Auffüllung)
        int pad = Math.round(Math.max(8, box.height() * 0.4f));
        int x0 = Math.max(0, (int) box.left - pad);
        int y0 = Math.max(0, (int) box.top - pad);
        int x1 = Math.min(w, (int) box.right + pad);
        int y1 = Math.min(h, (int) box.bottom + pad);
        int bw = (x1 - x0) / 4 * 4;
        int bh = y1 - y0;
        if (bw <= 0 || bh <= 0) return new Cut(null, x0, y0, threshold);
        byte[] a = new byte[bw * bh];
        boolean any = false;
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                float d = v8(depth8, (y0 + y) * w + x0 + x);
                int al = Math.round(clamp01((d * d - threshold) / soft + 0.5f) * 255);
                a[y * bw + x] = (byte) al;
                any |= al > 0;
            }
        }
        return new Cut(any ? alpha8(a, bw, bh) : null, x0, y0, threshold);
    }

    /** Wie autoSubjectThreshold, auf depth8. */
    private static float autoThreshold8(byte[] d, int w, int h, RectF box) {
        int x0 = Math.max(0, (int) box.left), x1 = Math.min(w - 1, (int) box.right);
        int y0 = Math.max(0, (int) box.top), y1 = Math.min(h - 1, (int) box.bottom);
        java.util.ArrayList<Float> samples = new java.util.ArrayList<>();
        for (int y = y0; y <= y1; y += 3) {
            for (int x = x0; x <= x1; x += 3) {
                float v = v8(d, y * w + x);
                samples.add(v * v);
            }
        }
        if (samples.isEmpty()) return 0.006f;
        java.util.Collections.sort(samples);
        return samples.get((int) (samples.size() * 0.6f)) * 1.15f + 0.004f;
    }

    /**
     * Was bei dieser Schwelle vor der Uhr läge, als ALPHA_8-Maske (nur in der Vorschau verfügbar).
     * Schnell genug, um sie beim Ziehen des Ebenen-Reglers live neu zu berechnen.
     */
    Bitmap frontMask(float threshold) {
        if (depth8 == null) return null;
        int w = width();
        int h = height();
        float soft = Math.max(0.004f, threshold * 0.15f);
        byte[] a = new byte[w * h];
        for (int i = 0; i < a.length; i++) {
            float near = v8(depth8, i) * v8(depth8, i);
            a[i] = (byte) Math.round(clamp01((near - threshold) / soft + 0.5f) * 255);
        }
        return alpha8(a, w, h);
    }

    private static float meanIn(float[] a, int w, int h, RectF box) {
        if (box == null || box.isEmpty()) return 0.5f;
        int x0 = Math.max(0, (int) box.left), x1 = Math.min(w - 1, (int) box.right);
        int y0 = Math.max(0, (int) box.top), y1 = Math.min(h - 1, (int) box.bottom);
        double sum = 0;
        int n = 0;
        for (int y = y0; y <= y1; y += 4) {
            for (int x = x0; x <= x1; x += 4) {
                sum += a[y * w + x];
                n++;
            }
        }
        return n == 0 ? 0.5f : (float) (sum / n);
    }

    /**
     * Was hinter der Uhr liegt (60-%-Quantil der Nähe im Uhrbereich) bleibt hinten, nur deutlich Näheres
     * kommt davor. Berge: Uhr meist im Himmel → Gipfel davor. Dach direkt an der Uhr → nichts davor.
     */
    private static float autoSubjectThreshold(float[] v, int w, int h, RectF box) {
        if (box == null || box.isEmpty()) return 0.006f;
        int x0 = Math.max(0, (int) box.left), x1 = Math.min(w - 1, (int) box.right);
        int y0 = Math.max(0, (int) box.top), y1 = Math.min(h - 1, (int) box.bottom);
        java.util.ArrayList<Float> samples = new java.util.ArrayList<>();
        for (int y = y0; y <= y1; y += 4) {
            for (int x = x0; x <= x1; x += 4) samples.add(v[y * w + x] * v[y * w + x]);
        }
        if (samples.isEmpty()) return 0.006f;
        java.util.Collections.sort(samples);
        float behind = samples.get((int) (samples.size() * 0.6f));
        return behind * 1.15f + 0.004f;
    }

    int width() {
        return land.getWidth();
    }

    int height() {
        return land.getHeight();
    }

    private static Bitmap decode(File f, int w, int h) {
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null || w == 0 || (b.getWidth() == w && b.getHeight() == h)) return b;
        return Bitmap.createScaledBitmap(b, w, h, true);
    }

    private static int[] pixels(Bitmap b) {
        int[] p = new int[b.getWidth() * b.getHeight()];
        b.getPixels(p, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return p;
    }

    private static Bitmap alpha8(byte[] a, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        b.copyPixelsFromBuffer(ByteBuffer.wrap(a));
        return b;
    }

    private static float clamp01(float x) {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }
}
