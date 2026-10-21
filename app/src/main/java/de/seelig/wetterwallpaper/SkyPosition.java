package de.seelig.wetterwallpaper;

/** Sonnen- und Mondstand nach der vereinfachten Almanach-Formel. */
final class SkyPosition {
    /** Grad über dem Horizont, negativ = untergegangen. */
    final double elevation;
    /** Grad, Nord = 0, im Uhrzeigersinn. */
    final double azimuth;

    private SkyPosition(double elevation, double azimuth) {
        this.elevation = elevation;
        this.azimuth = azimuth;
    }

    static SkyPosition sun(long utcMillis, double lat, double lon) {
        return at(utcMillis, lat, lon, 0);
    }

    // ponytail: Mond auf der Ekliptik um die Phase versetzt, ignoriert 5° Bahnneigung und Parallaxe
    static SkyPosition moon(long utcMillis, double lat, double lon) {
        return at(utcMillis, lat, lon, moonPhase(utcMillis) * 360);
    }

    /** 0 = Neumond, 0.5 = Vollmond. */
    static double moonPhase(long utcMillis) {
        double jd = utcMillis / 86400000.0 + 2440587.5;
        double p = ((jd - 2451550.26) / 29.530588853) % 1;
        return p < 0 ? p + 1 : p;
    }

    /** Beleuchteter Anteil der Mondscheibe, 0..1. */
    static double moonIllumination(double phase) {
        return (1 - Math.cos(2 * Math.PI * phase)) / 2;
    }

    private static SkyPosition at(long utcMillis, double lat, double lon, double eclipticOffset) {
        double d = utcMillis / 86400000.0 + 2440587.5 - 2451545.0; // Tage seit J2000
        double g = Math.toRadians(357.529 + 0.98560028 * d);
        double q = 280.459 + 0.98564736 * d;
        double l = Math.toRadians(q + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g) + eclipticOffset);
        double e = Math.toRadians(23.439 - 0.00000036 * d);
        double ra = Math.atan2(Math.cos(e) * Math.sin(l), Math.cos(l));
        double dec = Math.asin(Math.sin(e) * Math.sin(l));
        double gmstHours = 18.697374558 + 24.06570982441908 * d;
        double ha = Math.toRadians(gmstHours * 15 + lon) - ra;
        double la = Math.toRadians(lat);
        double el = Math.asin(Math.sin(la) * Math.sin(dec) + Math.cos(la) * Math.cos(dec) * Math.cos(ha));
        double az = Math.atan2(-Math.sin(ha), Math.tan(dec) * Math.cos(la) - Math.sin(la) * Math.cos(ha));
        return new SkyPosition(Math.toDegrees(el), (Math.toDegrees(az) + 360) % 360);
    }

    /** Selbsttest: java app/src/main/java/de/seelig/wetterwallpaper/SkyPosition.java */
    public static void main(String[] args) {
        long summerNoon = ms("2026-06-21T11:08:00Z");
        // Berlin: Mittagshöhe Sommer 90-52.52+23.44, Winter 90-52.52-23.44
        check(sun(summerNoon, 52.52, 13.40).elevation, 60.9, 1.5, "Sommer-Mittag Höhe");
        check(sun(summerNoon, 52.52, 13.40).azimuth, 180, 1.5, "Sommer-Mittag Azimut");
        check(sun(ms("2026-12-21T11:08:00Z"), 52.52, 13.40).elevation, 14.0, 1.5, "Winter-Mittag Höhe");
        if (sun(ms("2026-06-21T23:08:00Z"), 52.52, 13.40).elevation > -10) throw new AssertionError("Mitternacht hell");
        // bekannte Mondphasen: Neumond 11.01.2024 11:57 UTC, Vollmond 25.01.2024 17:54 UTC
        check(Math.min(moonPhase(ms("2024-01-11T11:57:00Z")), 1 - moonPhase(ms("2024-01-11T11:57:00Z"))), 0, 0.03, "Neumond");
        check(moonPhase(ms("2024-01-25T17:54:00Z")), 0.5, 0.03, "Vollmond");
        // Vollmond steht um Mitternacht etwa im Süden
        check(moon(ms("2024-01-25T23:00:00Z"), 52.52, 13.40).azimuth, 180, 25, "Vollmond Mitternacht Azimut");
        System.out.println("SkyPosition ok");
    }

    private static long ms(String iso) {
        return java.time.Instant.parse(iso).toEpochMilli();
    }

    private static void check(double actual, double expected, double tol, String what) {
        if (Math.abs(actual - expected) > tol) throw new AssertionError(what + ": " + actual + " statt " + expected);
    }
}
