package de.seelig.wetterwallpaper;

import java.util.Random;

/**
 * Eisblumen auf der Fensterscheibe. Reines Java (Selbsttest per main).
 * texture(): Farbe = Frost mit Kristallen, Alpha = Bereitschaft (Rand und Ecken zuerst, Farnkristalle
 * wachsen der Front voraus). apply(): schneidet die Bereitschaft bei einer Froststärke ab, sodass der
 * Frost mit sinkender Temperatur vom Rand hereinwächst.
 */
final class Frost {
    /** Breite der Übergangskante; kleiner = härtere Frostfront. */
    private static final float EDGE = 0.12f;

    private Frost() {
    }

    /** Kanal R = Kristalle, G = Korn, A = Bereitschaft; Farbe entsteht erst in apply(). */
    static int[] texture(int w, int h, int seed) {
        float[] ready = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Abstände in Bildbreiten; unten sammelt sich mehr Feuchte, dort friert es weiter herein
                float dl = x / (float) w, dr = (w - 1 - x) / (float) w, dt = y / (float) w, db = (h - 1 - y) / (float) w;
                float s = exp(dl / 0.10f) + exp(dr / 0.10f) + exp(dt / 0.08f) + exp(db / 0.16f);
                float base = 1f - (float) Math.exp(-s * 1.3f);
                float warp = Noise.fbm(x / (float) w * 3f, y / (float) w * 3f, 4096, seed + 7, 3);
                float n = Noise.fbm(x / (float) w * 9f + warp * 3f, y / (float) w * 9f, 4096, seed, 5);
                ready[y * w + x] = clamp01(base * (0.35f + 1.3f * n));
            }
        }
        float[] crystal = crystals(ready, w, h, seed);
        // leichter Schein um die Kristalle, wie Licht im Eis
        float[] glow = EdgeRefine.box(crystal, w, h, Math.max(1, Math.round(w / 270f)));
        int[] out = new int[w * h];
        for (int i = 0; i < out.length; i++) {
            float c = clamp01(crystal[i] + glow[i] * 0.8f);
            float grain = Noise.valueNoise((i % w) * 0.7f, (i / w) * 0.7f, 1 << 20, seed + 3);
            out[i] = (Math.round(ready[i] * 255) << 24) | (Math.round(c * 255) << 16) | (Math.round(grain * 255) << 8);
        }
        return out;
    }

    /**
     * Frost bei Stärke amount (0..1), Ergebnis als ARGB. Bei 0 nichts, bei 1 fast die ganze Scheibe,
     * die Mitte bleibt aber meist frei. Dichter Frost ist ein körniger Eisschleier, davor wachsen
     * Farnkristalle. Schreibt nach out (gleiche Länge wie tex).
     */
    static void apply(int[] tex, float amount, int[] out) {
        float start = 1f - 0.8f * clamp01(amount); // ab dieser Bereitschaft friert es
        for (int i = 0; i < tex.length; i++) {
            if (amount <= 0) {
                out[i] = 0;
                continue;
            }
            float ready = (tex[i] >>> 24) / 255f;
            float c = ((tex[i] >> 16) & 0xFF) / 255f;
            float grain = ((tex[i] >> 8) & 0xFF) / 255f;
            float veil = smooth(start, start + EDGE, ready);           // geschlossene Eisschicht
            float reach = smooth(start - 0.22f, start + 0.02f, ready); // Kristalle wachsen voraus
            float a = Math.max(veil * (0.42f + 0.2f * grain), c * reach * 0.95f);
            a = clamp01(a + veil * c * 0.3f);
            float bright = 0.80f + 0.20f * c + 0.06f * (grain - 0.5f);
            int r = Math.round(clamp01(bright * 0.90f) * 255), g = Math.round(clamp01(bright * 0.95f) * 255);
            int b = Math.round(clamp01(bright) * 255);
            out[i] = (Math.round(a * 255) << 24) | (r << 16) | (g << 8) | b;
        }
    }

    /**
     * Eisfarne: ein Stiel mit dicht stehenden Fiedern zu beiden Seiten, die zur Spitze kürzer werden.
     * Sie keimen dort, wo es früh friert, und wachsen grob zur Bildmitte. Werte 0..1.
     */
    private static float[] crystals(float[] ready, int w, int h, int seed) {
        float[] c = new float[w * h];
        Random rnd = new Random(seed);
        float unit = w / 540f;
        int tries = Math.round(w * h / (90 * unit * unit));
        for (int s = 0; s < tries; s++) {
            int x = rnd.nextInt(w), y = rnd.nextInt(h);
            float r0 = ready[y * w + x];
            if (rnd.nextFloat() > r0 * r0) continue;
            // grob nach innen: weg vom nächsten Rand, kräftig gestreut
            float inX = x < w / 2 ? 1 : -1, inY = y < h / 2 ? 1 : -1;
            float toSide = Math.min(x, w - x), toTop = Math.min(y, h - y);
            double dir = toSide < toTop ? Math.atan2(0, inX) : Math.atan2(inY, 0);
            dir += (rnd.nextFloat() - 0.5f) * 2.2f;
            float len = (14 + rnd.nextFloat() * rnd.nextFloat() * 70) * unit;
            fern(c, w, h, x, y, (float) dir, len, unit, 0, rnd);
        }
        return c;
    }

    private static void fern(float[] c, int w, int h, float x, float y, float dir, float len, float unit,
                             int depth, Random rnd) {
        float step = 0.8f * unit;
        float spacing = (depth == 0 ? 2.6f : 2.0f) * unit;
        float nextFork = spacing;
        float bend = (rnd.nextFloat() - 0.5f) * 0.02f; // ganzer Wedel leicht gebogen
        float v = depth == 0 ? 0.75f : 0.55f;
        boolean left = rnd.nextBoolean();
        for (float walked = 0; walked < len; walked += step) {
            dir += bend + (rnd.nextFloat() - 0.5f) * 0.03f;
            x += (float) Math.cos(dir) * step;
            y += (float) Math.sin(dir) * step;
            if (x < 0 || y < 0 || x >= w || y >= h) return;
            float t = walked / len;
            stamp(c, w, h, x, y, 0.55f * unit * (1 - 0.5f * t), v * (1 - 0.5f * t));
            if (depth < 2 && walked > nextFork) {
                // Fiedern abwechselnd links und rechts, ~60° schräg nach vorn, zur Spitze hin kürzer
                float side = left ? 1 : -1;
                left = !left;
                float sideLen = len * (depth == 0 ? 0.32f : 0.4f) * (1 - t) * (0.6f + 0.6f * rnd.nextFloat());
                if (sideLen > unit) {
                    fern(c, w, h, x, y, dir + side * (0.95f + rnd.nextFloat() * 0.2f), sideLen, unit, depth + 1, rnd);
                }
                nextFork = walked + spacing * (0.8f + 0.4f * rnd.nextFloat());
            }
        }
    }

    /** Weicher Punkt mit Radius r (Kantenglättung über den Abstand). */
    private static void stamp(float[] c, int w, int h, float cx, float cy, float r, float v) {
        int x0 = Math.max(0, (int) (cx - r - 1)), x1 = Math.min(w - 1, (int) (cx + r + 1));
        int y0 = Math.max(0, (int) (cy - r - 1)), y1 = Math.min(h - 1, (int) (cy + r + 1));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float dx = x - cx, dy = y - cy;
                float a = clamp01(r + 0.5f - (float) Math.sqrt(dx * dx + dy * dy)) * v;
                int i = y * w + x;
                if (a > c[i]) c[i] = a;
            }
        }
    }

    private static float smooth(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    private static float exp(float d) {
        return (float) Math.exp(-d);
    }

    private static float clamp01(float x) {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }

    /** Selbsttest: Rand friert vor der Mitte, mehr Frost deckt mehr, 0 deckt nichts. */
    public static void main(String[] args) {
        int w = 180, h = 390;
        int[] tex = texture(w, h, 5);
        int[] out = new int[tex.length];
        float[] cover = new float[3];
        float[] amounts = {0f, 0.4f, 1f};
        for (int k = 0; k < amounts.length; k++) {
            apply(tex, amounts[k], out);
            long sum = 0;
            for (int p : out) sum += p >>> 24;
            cover[k] = sum / (255f * out.length);
        }
        check(cover[0] == 0, "kein Frost bei 0");
        check(cover[1] > 0.02f && cover[1] < cover[2], "mehr Frost deckt mehr");
        apply(tex, 0.4f, out);
        float edge = 0, mid = 0;
        for (int y = h / 3; y < 2 * h / 3; y++) {
            edge += (out[y * w + 2] >>> 24) + (out[y * w + w - 3] >>> 24);
            mid += out[y * w + w / 2] >>> 24;
        }
        check(edge / 2 > mid, "Rand friert vor der Mitte");
        System.out.println("Frost ok " + cover[1] + " " + cover[2]);
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
