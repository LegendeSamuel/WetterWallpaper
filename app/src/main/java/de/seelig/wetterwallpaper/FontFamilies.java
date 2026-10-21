package de.seelig.wetterwallpaper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Schriftdateien zu Familien zusammenfassen ("SofiaPro-Light.otf", "SofiaPro-Bold.otf" → "Sofia Pro") und zur
 * gewünschten Stärke (100..900) die nächstliegende Datei finden. Reines Java, Selbsttest per main.
 */
final class FontFamilies {
    /** Stärke-Wörter in Dateinamen; längere zuerst, damit "ExtraLight" nicht als "Light" erkannt wird. */
    private static final Object[][] WEIGHTS = {
            {"extralight", 200}, {"ultralight", 150}, {"extrabold", 800}, {"ultrabold", 800}, {"semibold", 600},
            {"demibold", 600}, {"hairline", 100}, {"regular", 400}, {"medium", 500}, {"normal", 400}, {"light", 300},
            {"black", 900}, {"heavy", 900}, {"thin", 100}, {"bold", 700}, {"book", 400}};

    private FontFamilies() {
    }

    /** Stärke aus dem Dateinamen, 400 wenn nichts erkennbar. */
    static int weightOf(String file) {
        String n = stem(file).toLowerCase(Locale.ROOT).replace("italic", "");
        for (Object[] w : WEIGHTS) if (n.endsWith((String) w[0])) return (Integer) w[1];
        return 400;
    }

    static boolean italic(String file) {
        return stem(file).toLowerCase(Locale.ROOT).contains("italic");
    }

    /** Familienname zum Anzeigen, ohne Stärke und Kursiv: "SofiaPro-UltraLightitalic.otf" → "Sofia Pro". */
    static String familyOf(String file) {
        String n = stem(file);
        String lower = n.toLowerCase(Locale.ROOT);
        int cut = lower.lastIndexOf("italic");
        if (cut > 0) {
            n = n.substring(0, cut);
            lower = lower.substring(0, cut);
        }
        for (Object[] w : WEIGHTS) {
            if (lower.endsWith((String) w[0])) {
                n = n.substring(0, n.length() - ((String) w[0]).length());
                break;
            }
        }
        n = n.replaceAll("[-_ ]+$", "").replaceAll("[-_]", " ").replaceAll("([a-z])([A-Z])", "$1 $2").trim();
        return n.isEmpty() ? stem(file) : n;
    }

    /** Familien → Dateien (Reihenfolge wie übergeben). */
    static Map<String, List<String>> group(List<String> files) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String f : files) out.computeIfAbsent(familyOf(f), k -> new ArrayList<>()).add(f);
        return out;
    }

    /** Datei derselben Familie (und Kursiv-Art) wie {@code current}, deren Stärke am nächsten an {@code weight} liegt. */
    static String nearest(List<String> files, String current, int weight) {
        String family = familyOf(current);
        boolean it = italic(current);
        String best = current;
        int bestDiff = Integer.MAX_VALUE;
        for (String f : files) {
            if (!familyOf(f).equals(family) || italic(f) != it) continue;
            int diff = Math.abs(weightOf(f) - weight);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = f;
            }
        }
        return best;
    }

    /** Wie viele Stärken eine Familie (gleiche Kursiv-Art) hat; 1 = Einzeldatei, dann evtl. variable Schrift. */
    static int weightCount(List<String> files, String current) {
        String family = familyOf(current);
        boolean it = italic(current);
        int n = 0;
        for (String f : files) if (familyOf(f).equals(family) && italic(f) == it) n++;
        return n;
    }

    private static String stem(String file) {
        return file.replaceAll("\\.(ttf|otf|TTF|OTF)$", "");
    }

    public static void main(String[] args) {
        List<String> files = List.of("SofiaPro-UltraLight.otf", "SofiaPro-ExtraLight.otf", "SofiaPro-Light.otf",
                "SofiaPro-Regular.otf", "SofiaPro-Bold.otf", "SofiaPro-Black.otf", "SofiaPro-Lightitalic.otf",
                "SofiaPro-Bolditalic.otf", "Inter.ttf");
        check(weightOf("SofiaPro-UltraLight.otf") == 150, "UltraLight");
        check(weightOf("SofiaPro-ExtraLight.otf") == 200, "ExtraLight vor Light");
        check(weightOf("SofiaPro-Bolditalic.otf") == 700, "Bold kursiv");
        check(weightOf("Inter.ttf") == 400, "ohne Stärke");
        check(familyOf("SofiaPro-UltraLightitalic.otf").equals("Sofia Pro"), "Familie kursiv: " + familyOf("SofiaPro-UltraLightitalic.otf"));
        check(familyOf("SofiaPro-SemiBold.otf").equals("Sofia Pro"), "Familie SemiBold");
        check(familyOf("Inter.ttf").equals("Inter"), "Familie ohne Stärke");
        check(group(files).size() == 2, "zwei Familien");
        check(nearest(files, "SofiaPro-UltraLight.otf", 700).equals("SofiaPro-Bold.otf"), "nächste Stärke");
        check(nearest(files, "SofiaPro-Lightitalic.otf", 900).equals("SofiaPro-Bolditalic.otf"), "bleibt kursiv");
        check(nearest(files, "SofiaPro-Regular.otf", 160).equals("SofiaPro-UltraLight.otf"), "ganz dünn");
        check(weightCount(files, "Inter.ttf") == 1, "Einzeldatei");
        System.out.println("FontFamilies ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
