package de.seelig.wetterwallpaper;

/** Mehrlagiges Wertrauschen (für Wolken und Pfützen), in x periodisch damit Texturen nahtlos kacheln. */
final class Noise {
    private Noise() {
    }

    static float fbm(float x, float y, int period, int seed, int octaves) {
        float sum = 0;
        float amp = 0.5f;
        float norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * valueNoise(x, y, period, seed + o * 101);
            norm += amp;
            amp *= 0.5f;
            x *= 2;
            y *= 2;
            period *= 2;
        }
        return sum / norm;
    }

    /** Wertrauschen mit Periode in x. */
    static float valueNoise(float x, float y, int period, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float tx = x - xi;
        float ty = y - yi;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        int x0 = Math.floorMod(xi, period);
        int x1 = Math.floorMod(xi + 1, period);
        float a = hash(x0, yi, seed), b = hash(x1, yi, seed);
        float c = hash(x0, yi + 1, seed), d = hash(x1, yi + 1, seed);
        return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty;
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
    }
}
