package de.seelig.wetterwallpaper;

import java.util.Arrays;

/**
 * Farbpunkte eines Verlaufs: Position (0..1) und Weichheit des Übergangs zum nächsten Punkt (0 = harte Kante
 * in der Mitte zwischen beiden, 1 = über die ganze Strecke wie ein normaler Verlauf). Reines Java mit Selbsttest.
 */
final class GradientStops {
    private GradientStops() {
    }

    /** Positionen für n Punkte: gespeicherte (auf 0..1 und aufsteigend gebracht), wenn die Anzahl passt, sonst gleichmäßig. */
    static float[] positions(float[] saved, int n) {
        boolean ok = saved != null && saved.length == n;
        float[] p = new float[n];
        float prev = 0;
        for (int i = 0; i < n; i++) {
            p[i] = ok ? Math.max(prev, Math.min(1, saved[i])) : (n == 1 ? 0 : i / (float) (n - 1));
            prev = p[i];
        }
        return p;
    }

    /** Weichheit je Punkt, gespeicherte, wenn die Anzahl passt, sonst 1 (weich wie bisher). */
    static float[] softness(float[] saved, int n) {
        float[] s = new float[n];
        for (int i = 0; i < n; i++) s[i] = saved != null && saved.length == n ? Math.max(0, Math.min(1, saved[i])) : 1;
        return s;
    }

    /** Farben für LinearGradient zu stops(): jeder Übergang bekommt eigene Stützstellen, also je Farbe zwei. */
    static int[] colors(int[] c) {
        int n = c.length;
        int[] out = new int[2 * n];
        out[0] = c[0];
        for (int i = 0; i < n - 1; i++) {
            out[1 + 2 * i] = c[i];
            out[2 + 2 * i] = c[i + 1];
        }
        out[2 * n - 1] = c[n - 1];
        return out;
    }

    /** Stützstellen zu colors(): der Übergang von i nach i+1 ist soft[i] · Abstand breit und liegt mittig. */
    static float[] stops(float[] pos, float[] soft) {
        int n = pos.length;
        float[] out = new float[2 * n];
        out[0] = pos[0];
        for (int i = 0; i < n - 1; i++) {
            float mid = (pos[i] + pos[i + 1]) / 2;
            float half = soft[i] * (pos[i + 1] - pos[i]) / 2;
            out[1 + 2 * i] = mid - half;
            out[2 + 2 * i] = mid + half;
        }
        out[2 * n - 1] = pos[n - 1];
        return out;
    }

    /** Neuer Punkt kommt in die größte Lücke: zwischen i und i+1 (Rückgabe i). */
    static int widestGap(float[] pos) {
        int best = 0;
        for (int i = 1; i < pos.length - 1; i++) if (pos[i + 1] - pos[i] > pos[best + 1] - pos[best]) best = i;
        return best;
    }

    /** Selbsttest: java GradientStops.java */
    public static void main(String[] args) {
        check(Arrays.equals(positions(null, 3), new float[]{0, 0.5f, 1}), "ohne Liste gleichmäßig");
        check(Arrays.equals(positions(new float[]{0.2f, 0.1f, 0.9f}, 3), new float[]{0.2f, 0.2f, 0.9f}), "aufsteigend gemacht");
        check(Arrays.equals(positions(new float[]{0.3f}, 2), new float[]{0, 1}), "falsche Anzahl: gleichmäßig");
        check(Arrays.equals(softness(null, 2), new float[]{1, 1}), "ohne Liste weich");
        check(Arrays.equals(stops(new float[]{0, 1}, new float[]{1, 1}), new float[]{0, 0, 1, 1}), "weich wie bisher");
        check(Arrays.equals(stops(new float[]{0, 1}, new float[]{0, 1}), new float[]{0, 0.5f, 0.5f, 1}), "harte Kante in der Mitte");
        check(Arrays.equals(stops(new float[]{0, 0.5f, 1}, new float[]{0.5f, 1, 1}), new float[]{0, 0.125f, 0.375f, 0.5f, 1, 1}),
                "halb weich, dann ganz weich");
        check(Arrays.equals(colors(new int[]{1, 2, 3}), new int[]{1, 1, 2, 2, 3, 3}), "Farben doppelt");
        check(widestGap(new float[]{0, 0.2f, 1}) == 1, "größte Lücke hinten");
        check(widestGap(new float[]{0, 1}) == 0, "zwei Punkte");
        System.out.println("GradientStops ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
