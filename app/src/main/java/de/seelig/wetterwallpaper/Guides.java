package de.seelig.wetterwallpaper;

import java.util.List;

/** Ausrichtungslinien wie in Illustrator: Einrasten von Kanten und Mitte eines gezogenen Elements. Reines Java. */
final class Guides {
    private Guides() {
    }

    /**
     * Kleinste Verschiebung, mit der eine der Kanten (links, Mitte, rechts bzw. oben, Mitte, unten) auf einer Linie
     * liegt, und diese Linie: {Verschiebung, Linie}. null, wenn keine Linie näher als max ist.
     */
    static float[] snap(float[] edges, List<Float> lines, float max) {
        float[] best = null;
        for (float e : edges) {
            for (float l : lines) {
                float d = l - e;
                if (Math.abs(d) <= max && (best == null || Math.abs(d) < Math.abs(best[0]))) best = new float[]{d, l};
            }
        }
        return best;
    }

    /** Selbsttest: java Guides.java */
    public static void main(String[] args) {
        List<Float> lines = List.of(100f, 250f);
        float[] s = snap(new float[]{95, 140, 185}, lines, 10);
        check(s != null && s[0] == 5 && s[1] == 100, "linke Kante rastet an 100 ein");
        s = snap(new float[]{200, 247, 294}, lines, 10);
        check(s != null && s[0] == 3 && s[1] == 250, "Mitte rastet an 250 ein, die nähere Linie gewinnt");
        check(snap(new float[]{120, 170, 220}, lines, 10) == null, "nichts in Reichweite");
        System.out.println("Guides ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
