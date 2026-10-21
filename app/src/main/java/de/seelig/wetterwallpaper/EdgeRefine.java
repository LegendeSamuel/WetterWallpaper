package de.seelig.wetterwallpaper;

/**
 * Richtet die grobe Tiefe (Modell rechnet mit ~518 px Breite) und die Himmelsmaske an den Kanten
 * des Fotos aus. Ohne Android-Abhängigkeiten, damit der Selbsttest am PC läuft.
 */
final class EdgeRefine {
    /** Farbabstand (RGB 0..1) zur mitlaufenden Himmelsfarbe: darunter Himmel, darüber kein Himmel. */
    private static final float SKY_TOL0 = 0.10f;
    private static final float SKY_TOL1 = 0.40f; // breit: Unschärfeübergänge werden halb durchsichtig statt heller Saum
    /** So schnell darf sich die Himmelsfarbe je Zeile (bei 1080 px Breite) ändern; Unschärfekanten sind schneller. */
    private static final float SKY_DRIFT = 0.002f;

    private EdgeRefine() {
    }

    /**
     * Schneller Guided Filter (He et al.) mit Graustufen-Führung: Ergebnis ist glatt wie p, folgt aber
     * den Kanten von guide. Rechnet auf halber Auflösung, nur das Anwenden in voller.
     */
    static float[] guided(float[] guide, float[] p, int w, int h, int r, float eps) {
        int sw = Math.max(1, w / 2);
        int sh = Math.max(1, h / 2);
        int rs = Math.max(1, r / 2);
        float[] g = half(guide, w, h, sw, sh);
        float[] q = half(p, w, h, sw, sh);
        int m = sw * sh;
        float[] gq = new float[m];
        float[] gg = new float[m];
        for (int i = 0; i < m; i++) {
            gq[i] = g[i] * q[i];
            gg[i] = g[i] * g[i];
        }
        float[] mG = box(g, sw, sh, rs);
        float[] mQ = box(q, sw, sh, rs);
        float[] mGQ = box(gq, sw, sh, rs);
        float[] mGG = box(gg, sw, sh, rs);
        for (int i = 0; i < m; i++) {
            float a = (mGQ[i] - mG[i] * mQ[i]) / (mGG[i] - mG[i] * mG[i] + eps);
            gq[i] = a;
            gg[i] = mQ[i] - a * mG[i];
        }
        float[] a = box(gq, sw, sh, rs);
        float[] b = box(gg, sw, sh, rs);
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            float fy = Math.min(sh - 1, Math.max(0, (y + 0.5f) / 2 - 0.5f));
            int y0 = (int) fy;
            int y1 = Math.min(sh - 1, y0 + 1);
            float ty = fy - y0;
            for (int x = 0; x < w; x++) {
                float fx = Math.min(sw - 1, Math.max(0, (x + 0.5f) / 2 - 0.5f));
                int x0 = (int) fx;
                int x1 = Math.min(sw - 1, x0 + 1);
                float tx = fx - x0;
                int i = y * w + x;
                out[i] = lerp2(a, sw, x0, x1, y0, y1, tx, ty) * guide[i] + lerp2(b, sw, x0, x1, y0, y1, tx, ty);
            }
        }
        return out;
    }

    /**
     * Himmel verfeinern: Die Tiefe hält auch unscharfe ferne Baumreihen für Himmel. Von oben nach unten
     * läuft eine Referenz-Himmelsfarbe mit; was deutlich anders aussieht UND mit dem Land zusammenhängt,
     * ist kein Himmel (Wolken-Inseln mitten im Himmel bleiben Himmel). Danach Kanten ans Foto anlegen.
     */
    static float[] refineSky(int[] px, float[] lum, float[] sky, int w, int h, float k) {
        float[] acc = colorCheck(px, sky, w, h, k);
        keepOnlyLandConnected(acc, sky, w, h);
        float[] s = new float[sky.length];
        for (int i = 0; i < s.length; i++) s[i] = sky[i] * acc[i];
        float[] g = guided(lum, s, w, h, Math.max(2, Math.round(8 * k)), 1e-3f);
        for (int i = 0; i < s.length; i++) g[i] = clamp01(g[i]) * acc[i];
        return g;
    }

    private static float[] colorCheck(int[] px, float[] sky, int w, int h, float k) {
        float[] acc = new float[sky.length];
        float drift = SKY_DRIFT / k;
        float[] ref = null;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            if (ref == null) {
                ref = sureSkyMean(px, sky, row, w);
                if (ref == null) {
                    for (int x = 0; x < w; x++) acc[row + x] = sky[row + x] > 0.5f ? 1 : 0;
                    continue;
                }
            }
            float sr = 0, sg = 0, sb = 0;
            int good = 0;
            for (int x = 0; x < w; x++) {
                int i = row + x;
                if (sky[i] <= 0.02f) continue;
                int c = px[i];
                float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
                float dr = r - ref[0], dg = g - ref[1], db = b - ref[2];
                float a = 1 - smoothstep(SKY_TOL0, SKY_TOL1, (float) Math.sqrt(dr * dr + dg * dg + db * db));
                acc[i] = a;
                if (a > 0.9f && sky[i] > 0.98f) {
                    sr += r;
                    sg += g;
                    sb += b;
                    good++;
                }
            }
            if (good > 8) {
                ref[0] += clamp(sr / good - ref[0], drift);
                ref[1] += clamp(sg / good - ref[1], drift);
                ref[2] += clamp(sb / good - ref[2], drift);
            }
        }
        return acc;
    }

    /** Mittlere Farbe sicherer Himmelspixel einer Zeile, null wenn zu wenige (unter 5 %). */
    private static float[] sureSkyMean(int[] px, float[] sky, int row, int w) {
        float r = 0, g = 0, b = 0;
        int n = 0;
        for (int x = 0; x < w; x++) {
            if (sky[row + x] <= 0.98f) continue;
            int c = px[row + x];
            r += (c >> 16) & 0xFF;
            g += (c >> 8) & 0xFF;
            b += c & 0xFF;
            n++;
        }
        return n < w * 0.05f ? null : new float[]{r / n / 255f, g / n / 255f, b / n / 255f};
    }

    /** Abgelehntes, das nicht über andere abgelehnte Pixel ans Land grenzt, wird wieder Himmel. */
    private static void keepOnlyLandConnected(float[] acc, float[] sky, int w, int h) {
        int n = acc.length;
        boolean[] keep = new boolean[n];
        int[] stack = new int[n];
        int top = 0;
        for (int i = 0; i < n; i++) {
            if (!rejected(acc, sky, i)) continue;
            int x = i % w;
            int y = i / w;
            boolean nextToLand = (y < h - 1 && sky[i + w] <= 0.02f) || (x > 0 && sky[i - 1] <= 0.02f)
                    || (x < w - 1 && sky[i + 1] <= 0.02f);
            if (nextToLand) {
                keep[i] = true;
                stack[top++] = i;
            }
        }
        while (top > 0) {
            int i = stack[--top];
            int x = i % w;
            int y = i / w;
            if (x > 0) top = visit(acc, sky, keep, stack, top, i - 1);
            if (x < w - 1) top = visit(acc, sky, keep, stack, top, i + 1);
            if (y > 0) top = visit(acc, sky, keep, stack, top, i - w);
            if (y < h - 1) top = visit(acc, sky, keep, stack, top, i + w);
        }
        for (int i = 0; i < n; i++) if (rejected(acc, sky, i) && !keep[i]) acc[i] = 1;
    }

    private static boolean rejected(float[] acc, float[] sky, int i) {
        return sky[i] > 0.02f && acc[i] < 0.5f;
    }

    private static int visit(float[] acc, float[] sky, boolean[] keep, int[] stack, int top, int j) {
        if (!keep[j] && rejected(acc, sky, j)) {
            keep[j] = true;
            stack[top++] = j;
        }
        return top;
    }

    /**
     * Unscharfe Hintergründe: Das Foto blendet über viele Pixel vom hellen Himmel ins Land, die Maske
     * aber schnell. Diese aufgehellten Pixel würden nachts vor dem dunkleren Himmel als heller Strich
     * leuchten. Ihr Himmelsanteil wird aus der Helligkeit zwischen Himmel und tiefem Landinneren
     * geschätzt. Nur wo schon die Tiefe Himmel vermutete (depthSky), damit Schnee auf Gipfeln bleibt.
     */
    static float[] widenToBlur(float[] lum, float[] depthSky, float[] sky, int w, int h, int r, int inner) {
        int n = sky.length;
        float[] sure = new float[n];
        float[] deep = new float[n];
        for (int i = 0; i < n; i++) {
            sure[i] = sky[i] > 0.98f ? 1 : 0;
            deep[i] = sky[i] < 0.02f ? 1 : 0;
        }
        deep = box(deep, w, h, inner);
        float[] lumSure = new float[n];
        float[] lumDeep = new float[n];
        for (int i = 0; i < n; i++) {
            deep[i] = deep[i] > 0.999f ? 1 : 0;
            lumSure[i] = lum[i] * sure[i];
            lumDeep[i] = lum[i] * deep[i];
        }
        float[] ds = box(sure, w, h, r);
        float[] dd = box(deep, w, h, r);
        lumSure = box(lumSure, w, h, r);
        lumDeep = box(lumDeep, w, h, r);
        float[] out = sky.clone();
        for (int i = 0; i < n; i++) {
            if (sky[i] >= 0.98f || depthSky[i] <= 0.02f || ds[i] < 1e-3f || dd[i] < 1e-3f) continue;
            float ls = lumSure[i] / ds[i];
            float ld = lumDeep[i] / dd[i];
            if (Math.abs(ls - ld) <= 0.08f) continue;
            out[i] = Math.max(sky[i], clamp01((lum[i] - ld) / (ls - ld)));
        }
        return out;
    }

    /**
     * Farbe der Landebene an weichen Rändern: Himmelsanteil herausrechnen (Freistellung I = a·S + (1−a)·F),
     * sonst liegt die Mischfarbe mit dem hellen Foto-Himmel als Saum über dem Nachthimmel.
     */
    static int[] landColors(int[] px, float[] sky, int w, int h, int r) {
        int n = px.length;
        float[] sure = new float[n];
        float[] cr = new float[n], cg = new float[n], cb = new float[n];
        for (int i = 0; i < n; i++) {
            float m = sky[i] > 0.98f ? 1 : 0;
            int c = px[i];
            sure[i] = m;
            cr[i] = ((c >> 16) & 0xFF) * m;
            cg[i] = ((c >> 8) & 0xFF) * m;
            cb[i] = (c & 0xFF) * m;
        }
        sure = box(sure, w, h, r);
        cr = box(cr, w, h, r);
        cg = box(cg, w, h, r);
        cb = box(cb, w, h, r);
        int[] out = px.clone();
        for (int i = 0; i < n; i++) {
            if (sky[i] <= 0.02f || sky[i] >= 0.98f || sure[i] < 1e-3f) continue;
            float a = Math.min(sky[i], 0.9f);
            int c = px[i];
            out[i] = (c & 0xFF000000)
                    | (unmix((c >> 16) & 0xFF, cr[i] / sure[i], a) << 16)
                    | (unmix((c >> 8) & 0xFF, cg[i] / sure[i], a) << 8)
                    | unmix(c & 0xFF, cb[i] / sure[i], a);
        }
        return out;
    }

    private static int unmix(int value, float skyValue, float a) {
        int f = Math.round((value - a * skyValue) / (1 - a));
        return f < 0 ? 0 : (f > 255 ? 255 : f);
    }

    /**
     * Wie stark der echte Himmel das Foto ersetzt (0..1). Zwei Lagen übereinander:
     * 1. die Himmelsmaske weichgezeichnet (dreifacher Boxfilter; bei softness 1 ~1/3 Bildbreite Übergang),
     * 2. die harte Maske, oben 100 % und zum Horizont jeder Spalte hin ausgeblendet (bei softness 1 auf 25 %).
     * Oben bleibt der Himmel also klar ersetzt, am Horizont geht er weich ins Foto über.
     * softness 0 = fast die harte Maske (scharfe Silhouetten), 1 = extrem weich (Bokeh-Fotos).
     */
    static float[] softSky(float[] sky, int w, int h, float k, float softness) {
        softness = clamp01(softness);
        int r = Math.max(2, Math.round((10 + 130 * softness) * k));
        float hardAtHorizon = 1 - 0.75f * softness;
        float[] soft = box(box(box(sky, w, h, r), w, h, r), w, h, r);
        // Horizont je Spalte = Höhe des Himmels darüber, über die Breite geglättet
        float[] col = new float[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) col[x] += sky[y * w + x];
        }
        float[] horizon = box(col, w, 1, Math.max(1, w / 8));
        float[] out = new float[sky.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float down = horizon[x] < 1 ? 1 : smoothstep(0, horizon[x], y);
                float hard = sky[i] * (1 - (1 - hardAtHorizon) * down);
                out[i] = 1 - (1 - soft[i]) * (1 - hard);
            }
        }
        return out;
    }

    /** Separierbarer Boxfilter mit gleitender Summe, Rand wird fortgesetzt. */
    static float[] box(float[] a, int w, int h, int r) {
        float[] tmp = new float[a.length];
        float[] out = new float[a.length];
        float k = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            float sum = 0;
            for (int x = -r; x <= r; x++) sum += a[row + clampI(x, w)];
            for (int x = 0; x < w; x++) {
                tmp[row + x] = sum / k;
                sum += a[row + clampI(x + r + 1, w)] - a[row + clampI(x - r, w)];
            }
        }
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int y = -r; y <= r; y++) sum += tmp[clampI(y, h) * w + x];
            for (int y = 0; y < h; y++) {
                out[y * w + x] = sum / k;
                sum += tmp[clampI(y + r + 1, h) * w + x] - tmp[clampI(y - r, h) * w + x];
            }
        }
        return out;
    }

    /** 2x2-Mittel auf halbe Auflösung. */
    private static float[] half(float[] a, int w, int h, int sw, int sh) {
        float[] out = new float[sw * sh];
        for (int y = 0; y < sh; y++) {
            int r0 = Math.min(h - 1, 2 * y) * w;
            int r1 = Math.min(h - 1, 2 * y + 1) * w;
            for (int x = 0; x < sw; x++) {
                int x0 = Math.min(w - 1, 2 * x);
                int x1 = Math.min(w - 1, 2 * x + 1);
                out[y * sw + x] = (a[r0 + x0] + a[r0 + x1] + a[r1 + x0] + a[r1 + x1]) * 0.25f;
            }
        }
        return out;
    }

    private static float lerp2(float[] a, int w, int x0, int x1, int y0, int y1, float tx, float ty) {
        float top = a[y0 * w + x0] + (a[y0 * w + x1] - a[y0 * w + x0]) * tx;
        float bot = a[y1 * w + x0] + (a[y1 * w + x1] - a[y1 * w + x0]) * tx;
        return top + (bot - top) * ty;
    }

    private static float clamp(float v, float max) {
        return v < -max ? -max : (v > max ? max : v);
    }

    private static int clampI(int i, int n) {
        return i < 0 ? 0 : (i >= n ? n - 1 : i);
    }

    static float clamp01(float x) {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    /** Selbsttest: java app/src/main/java/de/seelig/wetterwallpaper/EdgeRefine.java */
    public static void main(String[] args) {
        int w = 40, h = 40;
        int white = 0xFFF8F8F8, tree = 0xFF505A40, blue = 0xFF6496E6;
        int[] px = new int[w * h];
        float[] sky = new float[w * h];
        float[] lum = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                boolean land = y >= 30;
                boolean treeline = y >= 20 && y < 30;            // Tiefe hält sie für Himmel
                boolean cloud = y >= 5 && y < 9 && x >= 10 && x < 20;
                px[i] = land || treeline ? tree : (cloud ? white : blue);
                sky[i] = land ? 0 : 1;
                lum[i] = ((px[i] >> 8) & 0xFF) / 255f;
            }
        }
        float[] s = refineSky(px, lum, sky, w, h, 1);
        check(s[25 * w + 20] < 0.1f, "ferne Baumreihe ist kein Himmel");
        check(s[7 * w + 15] > 0.9f, "Wolke im Himmel bleibt Himmel");
        check(s[2 * w + 30] > 0.9f, "blauer Himmel bleibt Himmel");
        check(s[35 * w + 20] < 0.1f, "Land bleibt Land");
        float[] step = new float[w * h];
        for (int i = 0; i < step.length; i++) step[i] = (i % w) < 20 ? 0 : 1;
        float[] gf = guided(step, step, w, h, 4, 1e-4f);
        check(gf[10 * w + 5] < 0.05f && gf[10 * w + 35] > 0.95f, "Guided Filter erhält Kante");
        // unscharfer Übergang: Himmel (hell) bis y=10, dann über 20 Zeilen ins dunkle Land
        float[] grad = new float[w * h], mask = new float[w * h], depthSky = new float[w * h];
        for (int i = 0; i < grad.length; i++) {
            int y = i / w;
            grad[i] = y < 10 ? 1f : (y < 30 ? 1f - (y - 10) / 20f * 0.7f : 0.3f);
            mask[i] = y < 12 ? 1 : 0;                   // Maske wechselt schnell
            depthSky[i] = (i % w) < 20 ? 1 : 0;          // rechts vermutete die Tiefe keinen Himmel
        }
        float[] wide = widenToBlur(grad, depthSky, mask, w, h, 12, 4);
        check(wide[20 * w + 5] > 0.15f && wide[20 * w + 5] < 0.7f,
                "aufgehellter Rand wird teilweise durchsichtig: " + wide[20 * w + 5]);
        check(wide[36 * w + 5] < 0.05f, "tiefes Land bleibt deckend: " + wide[36 * w + 5]);
        check(wide[20 * w + 35] == 0, "ohne Tiefen-Himmel bleibt Land deckend (Gipfelschnee)");

        int sw = 270, sh = 600; // Himmel in der oberen Hälfte, Horizont bei y = 300
        float[] half = new float[sw * sh];
        for (int i = 0; i < half.length; i++) half[i] = i / sw < 300 ? 1 : 0;
        float[] soft = softSky(half, sw, sh, sw / 1080f, 1f);
        check(soft[5 * sw + 100] > 0.99f, "oben voll ersetzt: " + soft[5 * sw + 100]);
        float atEdge = soft[299 * sw + 100], below = soft[340 * sw + 100];
        check(atEdge > 0.5f && atEdge < 0.9f, "an der Kante gemischt: " + atEdge);
        check(below > 0.05f && below < atEdge, "weicher Auslauf ins Land: " + below);
        check(soft[(sh - 5) * sw + 100] < 0.02f, "tief im Land bleibt das Foto: " + soft[(sh - 5) * sw + 100]);
        float[] sharp = softSky(half, sw, sh, sw / 1080f, 0f);
        check(sharp[295 * sw + 100] > 0.95f && sharp[320 * sw + 100] < 0.05f, "scharf: Kante bleibt hart");
        System.out.println("EdgeRefine ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
