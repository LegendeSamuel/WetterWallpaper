package de.seelig.wetterwallpaper;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Zeiten am Himmel für die Widgets: Sonnenauf-/untergang, goldene und blaue Stunde, Mondphase,
 * nächster Vollmond, Sternen-Wertung. Reines Java (nutzt SkyPosition), Selbsttest per main.
 */
final class SkyTimes {
    /** Sonnenhöhen der Lichtphasen in Grad. */
    static final double SUNRISE = -0.833;
    static final double GOLDEN_TOP = 6;
    static final double BLUE_TOP = -4;
    static final double BLUE_BOTTOM = -6;
    private static final long STEP = 60_000L; // Minutengenau reicht

    private SkyTimes() {
    }

    /**
     * Erster Zeitpunkt ab {@code from} (höchstens 26 h), an dem die Sonnenhöhe {@code elevation} kreuzt,
     * steigend (rising) oder fallend; -1 wenn es keinen gibt (Polartag/-nacht).
     */
    static long crossing(long from, double lat, double lon, double elevation, boolean rising) {
        double prev = SkyPosition.sun(from, lat, lon).elevation;
        for (long t = from + STEP; t <= from + 26 * 3600_000L; t += STEP) {
            double e = SkyPosition.sun(t, lat, lon).elevation;
            if (rising ? prev < elevation && e >= elevation : prev > elevation && e <= elevation) return t;
            prev = e;
        }
        return -1;
    }

    /** Sonnenauf- und -untergang des Kalendertags von {@code now} (in tz); -1, wenn es keinen gibt. */
    static long[] sunTimes(long now, double lat, double lon, TimeZone tz) {
        long midnight = startOfDay(now, tz);
        long rise = crossing(midnight, lat, lon, SUNRISE, true);
        long set = crossing(midnight, lat, lon, SUNRISE, false);
        long end = midnight + 24 * 3600_000L;
        return new long[]{rise >= 0 && rise < end ? rise : -1, set >= 0 && set < end ? set : -1};
    }

    /** Nächste goldene Stunde ab now: {Beginn, Ende}; läuft sie gerade, ist Beginn ≤ now. Null ohne. */
    static long[] nextGolden(long now, double lat, double lon) {
        return nextPhase(now, lat, lon, BLUE_TOP, GOLDEN_TOP);
    }

    /** Nächste blaue Stunde ab now: {Beginn, Ende}. Null ohne. */
    static long[] nextBlue(long now, double lat, double lon) {
        return nextPhase(now, lat, lon, BLUE_BOTTOM, BLUE_TOP);
    }

    /** Nächster Zeitraum, in dem die Sonne zwischen low und high steht (morgens oder abends). */
    private static long[] nextPhase(long now, double lat, double lon, double low, double high) {
        long start = inside(now, lat, lon, low, high) ? findStart(now, lat, lon, low, high) : -1;
        for (long t = now + STEP; t <= now + 26 * 3600_000L; t += STEP) {
            boolean in = inside(t, lat, lon, low, high);
            if (in && start < 0) start = t;
            if (!in && start >= 0) return new long[]{start, t};
        }
        return null;
    }

    private static long findStart(long now, double lat, double lon, double low, double high) {
        long t = now;
        while (t > now - 3 * 3600_000L && inside(t - STEP, lat, lon, low, high)) t -= STEP;
        return t;
    }

    private static boolean inside(long t, double lat, double lon, double low, double high) {
        double e = SkyPosition.sun(t, lat, lon).elevation;
        return e >= low && e <= high;
    }

    /** Deutscher Name der Mondphase (0 = Neumond, 0.5 = Vollmond). */
    static String moonName(double phase) {
        if (phase < 0.03 || phase > 0.97) return "Neumond";
        if (phase < 0.22) return "Zunehmende Sichel";
        if (phase < 0.28) return "Erstes Viertel";
        if (phase < 0.47) return "Zunehmender Mond";
        if (phase < 0.53) return "Vollmond";
        if (phase < 0.72) return "Abnehmender Mond";
        if (phase < 0.78) return "Letztes Viertel";
        return "Abnehmende Sichel";
    }

    /** Tage bis zum nächsten Vollmond (0 = heute). */
    static int daysToFullMoon(long now) {
        double phase = SkyPosition.moonPhase(now);
        double d = ((0.5 - phase + 1) % 1) * 29.530588853;
        return (int) Math.round(d) % 30;
    }

    /**
     * Wie gut die Nacht für Sterne wird, 0..1: wenig Wolken in den dunklen Stunden, wenig Mondlicht.
     * nightClouds = mittlere Bewölkung (0..1) der kommenden Nachtstunden.
     */
    static float starScore(float nightClouds, double moonIllumination) {
        float s = (1 - nightClouds) * (1 - 0.6f * (float) moonIllumination);
        return Math.max(0, Math.min(1, s));
    }

    static String starWord(float score) {
        if (score > 0.7f) return "sehr gut";
        if (score > 0.45f) return "gut";
        if (score > 0.25f) return "mäßig";
        return "schlecht";
    }

    static long startOfDay(long t, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static void main(String[] args) {
        TimeZone berlin = TimeZone.getTimeZone("Europe/Berlin");
        Calendar c = Calendar.getInstance(berlin);
        c.clear();
        c.set(2026, Calendar.JUNE, 21, 12, 0);
        long noon = c.getTimeInMillis();
        long[] sun = sunTimes(noon, 52.52, 13.40, berlin); // Berlin, Sommeranfang: ~04:43 / ~21:33
        check(hour(sun[0], berlin) == 4 && hour(sun[1], berlin) == 21, "Berlin Sommer " + hour(sun[0], berlin) + "/" + hour(sun[1], berlin));
        long[] golden = nextGolden(noon, 52.52, 13.40);
        check(golden != null && hour(golden[0], berlin) == 20 && golden[1] > golden[0], "goldene Stunde abends");
        long[] blue = nextBlue(noon, 52.52, 13.40);
        check(blue != null && blue[0] >= golden[1] - STEP, "blaue nach goldener Stunde");
        check(sunTimes(noon, 78.2, 15.6, TimeZone.getTimeZone("UTC"))[0] == -1, "Polartag ohne Aufgang");
        check(moonName(0).equals("Neumond") && moonName(0.5).equals("Vollmond") && moonName(0.35).equals("Zunehmender Mond"), "Mondnamen");
        check(starScore(0, 0) == 1 && starScore(1, 0) == 0 && starWord(0.5f).equals("gut"), "Sternen-Wertung");
        int days = daysToFullMoon(noon);
        check(days >= 0 && days <= 30, "Tage bis Vollmond " + days);
        System.out.println("SkyTimes ok");
    }

    private static int hour(long t, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(t);
        return c.get(Calendar.HOUR_OF_DAY);
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
