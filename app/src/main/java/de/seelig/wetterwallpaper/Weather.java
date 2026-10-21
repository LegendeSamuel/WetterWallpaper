package de.seelig.wetterwallpaper;

/** Wetterzustand, auf 0..1-Stärken abgebildet. Reines Java, damit es ohne Android testbar ist. */
final class Weather {
    static final Weather CLEAR = from(0, 0, 50000, 5);

    final int code;
    final float cloud;
    final float rain;
    final float snow;
    final float fog;
    final float wind;
    final boolean thunder;
    /** Liegender Schnee auf dem Land, 0..1. */
    final float snowCover;
    /** Nasse Oberflächen, 0..1. */
    final float wetness;
    /** Temperatur in Grad Celsius. */
    final float temperature;
    /** Eis auf der Fensterscheibe, 0..1: ab etwa 1 °C, bei -7 °C voll. */
    final float frost;

    private Weather(int code, float cloud, float rain, float snow, float fog, float wind, boolean thunder,
                    float snowCover, float wetness, float temperature) {
        this.code = code;
        this.cloud = cloud;
        this.rain = rain;
        this.snow = snow;
        this.fog = fog;
        this.wind = wind;
        this.thunder = thunder;
        this.snowCover = snowCover;
        this.wetness = wetness;
        this.temperature = temperature;
        this.frost = clamp01((1f - temperature) / 8f);
    }

    static Weather from(int code, double cloudPct, double visibilityM, double windKmh) {
        return from(code, cloudPct, visibilityM, windKmh, 0, 20);
    }

    static Weather from(int code, double cloudPct, double visibilityM, double windKmh, double snowDepthM) {
        return from(code, cloudPct, visibilityM, windKmh, snowDepthM, 20);
    }

    /** Aus Open-Meteo "current": WMO-Code, Bewölkung in %, Sichtweite in m, Wind in km/h, Schneehöhe in m, Temperatur in °C. */
    static Weather from(int code, double cloudPct, double visibilityM, double windKmh, double snowDepthM, double tempC) {
        float rain = 0;
        float snow = 0;
        switch (code) {
            case 51: case 53: case 55: case 56: case 57: rain = 0.25f; break; // Niesel
            case 61: rain = 0.4f; break;
            case 63: rain = 0.65f; break;
            case 65: rain = 0.9f; break;
            case 66: case 67: rain = 0.5f; break; // gefrierender Regen
            case 80: rain = 0.45f; break;
            case 81: rain = 0.7f; break;
            case 82: rain = 0.95f; break;
            case 95: case 96: case 99: rain = 0.85f; break;
            case 71: snow = 0.35f; break;
            case 73: snow = 0.6f; break;
            case 75: snow = 0.9f; break;
            case 77: snow = 0.3f; break;
            case 85: snow = 0.5f; break;
            case 86: snow = 0.85f; break;
            default: break;
        }
        float fog = (code == 45 || code == 48) ? 0.8f : clamp01((float) (1 - visibilityM / 2000)) * 0.8f;
        float cloud = clamp01((float) cloudPct / 100f);
        if (rain > 0 || snow > 0) cloud = Math.max(cloud, 0.85f);
        // ab ~5 cm geschlossene Decke; bei Schneefall bleibt sofort etwas liegen
        float cover = Math.max(clamp01((float) snowDepthM / 0.05f), snow > 0 ? 0.35f + snow * 0.5f : 0);
        float wet = rain > 0 ? 0.5f + rain * 0.5f : 0;
        return new Weather(code, cloud, rain, snow, fog, clamp01((float) windKmh / 40f), code >= 95, cover, wet, (float) tempC);
    }

    /** Feste Wetterlagen zum Testen über config debug.weather. */
    static Weather preset(String name) {
        switch (name) {
            case "clear": return from(0, 0, 50000, 5, 0, 22);
            case "partly": return from(2, 45, 30000, 10, 0, 19);
            case "overcast": return from(3, 100, 20000, 15, 0, 16);
            case "drizzle": return from(53, 100, 10000, 10, 0, 13);
            case "rain": return from(63, 100, 8000, 15, 0, 12);
            case "heavyrain": return from(65, 100, 4000, 30, 0, 11);
            case "snow": return from(73, 100, 3000, 10, 0.03, -1);
            case "heavysnow": return from(75, 100, 800, 25, 0.2, -4);
            case "winter": return from(1, 20, 40000, 5, 0.3, -3); // sonnig mit Schneedecke
            case "fog": return from(45, 100, 300, 3, 0, 8);
            case "storm": return from(95, 100, 5000, 45, 0, 15);
            case "frost": return from(0, 10, 40000, 3, 0, -8); // klare Frostnacht
            default: return null;
        }
    }

    String weatherSymbol(boolean night) {
        if (thunder) return "⛈️";
        if (snow > 0) return "❄️";
        if (rain > 0.5f) return "🌧️";
        if (rain > 0) return "🌦️";
        if (fog > 0.4f) return "🌫️";
        if (cloud > 0.7f) return "☁️";
        if (cloud > 0.2f) return night ? "☁️" : "⛅";
        return night ? "🌙" : "☀️";
    }

    String temperatureText() {
        return Math.round(temperature) + "°";
    }

    boolean hasParticles() {
        return rain > 0 || snow > 0;
    }

    private static float clamp01(float x) {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }

    /** Selbsttest: java app/src/main/java/de/seelig/wetterwallpaper/Weather.java */
    public static void main(String[] args) {
        Weather clear = from(0, 0, 50000, 0);
        check(clear.cloud == 0 && clear.rain == 0 && clear.snow == 0 && clear.fog == 0, "klar");
        check(from(63, 40, 10000, 10).cloud >= 0.85f, "Regen erzwingt Wolken");
        check(from(65, 100, 10000, 0).rain > from(61, 100, 10000, 0).rain, "starker Regen > leichter");
        check(from(75, 100, 10000, 0).snow > 0 && from(75, 100, 10000, 0).rain == 0, "Schnee ist kein Regen");
        check(from(45, 100, 50000, 0).fog > 0.5f, "Nebel-Code");
        check(from(0, 0, 500, 0).fog > 0.5f, "schlechte Sicht = Nebel");
        check(from(95, 100, 5000, 0).thunder && !from(65, 100, 5000, 0).thunder, "Gewitter");
        check(from(0, 0, 50000, 200).wind == 1f, "Wind gedeckelt");
        check(clear.snowCover == 0 && clear.wetness == 0, "klar = trocken, kein Schnee");
        check(from(0, 0, 50000, 0, 0.3).snowCover == 1f, "liegender Schnee ohne Schneefall");
        check(from(73, 100, 3000, 0).snowCover > 0, "Schneefall bedeckt sofort etwas");
        check(from(63, 100, 8000, 0).wetness > 0.5f && from(63, 100, 8000, 0).snowCover == 0, "Regen = nass");
        for (String p : new String[]{"clear", "partly", "overcast", "drizzle", "rain", "heavyrain", "snow",
                "heavysnow", "winter", "fog", "storm", "frost"}) {
            check(preset(p) != null, "Preset " + p);
        }
        check(preset("frost").frost == 1f && preset("rain").frost == 0 && preset("snow").frost > 0, "Frost nach Temperatur");
        check(preset("unbekannt") == null, "unbekanntes Preset");
        System.out.println("Weather ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
