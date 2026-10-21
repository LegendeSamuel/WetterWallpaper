package de.seelig.wetterwallpaper;

import android.graphics.ColorMatrix;

import org.json.JSONObject;

import java.util.Calendar;

/**
 * Die 4 Jahreszeiten (Frühling, Sommer, Herbst, Winter).
 * Steuert saisonale Licht- und Farbanpassungen (z. B. goldenes Herbstlaub, frisches Frühlingsgrün)
 * sowie Partikel (Kirschblüten im Frühling, fallendes Laub im Herbst).
 */
enum Season {
    SPRING("Frühling", "spring"),
    SUMMER("Sommer", "summer"),
    AUTUMN("Herbst", "autumn"),
    WINTER("Winter", "winter");

    /** Standard-Stärke der Farbstimmung (0..1), wenn nichts eingestellt ist. */
    static final double DEFAULT_STRENGTH = 0.6;

    final String displayName;
    final String key;

    Season(String displayName, String key) {
        this.displayName = displayName;
        this.key = key;
    }

    /** Frühling, Sommer, Herbst färben nur Pflanzen; der Winter kühlt das ganze Bild (Luft, Licht). */
    boolean onlyPlants() {
        return this != WINTER;
    }

    static Season fromKey(String key) {
        for (Season s : values()) {
            if (s.key.equalsIgnoreCase(key)) return s;
        }
        return null;
    }

    /**
     * Bestimmt die Jahreszeit aus der Konfiguration (Abschnitt "season") oder automatisch
     * aus dem Kalendermonat. Gibt null zurück, wenn Jahreszeiten deaktiviert sind.
     */
    static Season resolve(JSONObject cfg, Calendar cal) {
        if (cfg == null || !cfg.optBoolean("enabled", true)) return null;
        String mode = cfg.optString("mode", "auto");
        Season fixed = fromKey(mode);
        if (fixed != null) return fixed;
        if ("off".equalsIgnoreCase(mode)) return null;

        // "auto": nach aktuellem Monat auf der Nordhalbkugel
        int month = cal.get(Calendar.MONTH);
        switch (month) {
            case Calendar.MARCH:
            case Calendar.APRIL:
            case Calendar.MAY:
                return SPRING;
            case Calendar.JUNE:
            case Calendar.JULY:
            case Calendar.AUGUST:
                return SUMMER;
            case Calendar.SEPTEMBER:
            case Calendar.OCTOBER:
            case Calendar.NOVEMBER:
                return AUTUMN;
            case Calendar.DECEMBER:
            case Calendar.JANUARY:
            case Calendar.FEBRUARY:
            default:
                return WINTER;
        }
    }

    /**
     * Farbkorrektur-Matrix für die jeweilige Jahreszeit.
     * Herbst verwandelt sommerliches Grün in goldene und warme Bernsteintöne;
     * Frühling lässt Knospen und Laub frisch und zart leuchten;
     * Sommer liefert satte, leuchtende Farben;
     * Winter kühlt Schatten sanft ab und entsättigt leicht.
     */
    void applyColorGrading(ColorMatrix cm, float strength) {
        float[] m = new float[20];
        switch (this) {
            case SPRING:
                // Frisches Hellgrün, zartes Licht
                m = new float[]{
                        1.02f, 0.05f, 0.00f, 0, 4,
                        0.02f, 1.08f, 0.00f, 0, 6,
                        0.00f, 0.02f, 0.98f, 0, 0,
                        0,     0,     0,     1, 0
                };
                break;
            case SUMMER:
                // Sattes, sonniges Grün, hohe Lebendigkeit
                m = new float[]{
                        1.05f, 0.02f, 0.00f, 0, 5,
                        0.00f, 1.08f, 0.00f, 0, 5,
                        0.00f, 0.00f, 1.02f, 0, -2,
                        0,     0,     0,     1, 0
                };
                break;
            case AUTUMN:
                // Herbstlaub: Grün wird Gold und Bernstein. Wirkt nur auf Pflanzen, darf daher kräftig sein
                m = new float[]{
                        1.10f, 0.50f, -0.05f, 0, 6,
                        0.02f, 0.86f, -0.04f, 0, 0,
                        -0.06f,-0.10f, 0.70f, 0, -6,
                        0,     0,     0,      1, 0
                };
                break;
            case WINTER:
                // Kühle, klare Luft, leicht gedämpfte Sättigung, kühler Schatten
                m = new float[]{
                        0.92f, 0.02f, 0.02f, 0, -2,
                        0.02f, 0.92f, 0.04f, 0, -2,
                        0.04f, 0.04f, 1.04f, 0, 4,
                        0,     0,     0,     1, 0
                };
                break;
        }
        // Stärke: zwischen Original (Einheitsmatrix) und voller Jahreszeiten-Farbe überblenden
        float s = Math.max(0, Math.min(1, strength));
        for (int i = 0; i < 20; i++) {
            float id = (i % 6 == 0) ? 1 : 0; // Diagonale 0, 6, 12, 18
            m[i] = id + s * (m[i] - id);
        }
        cm.postConcat(new ColorMatrix(m));
    }
}
