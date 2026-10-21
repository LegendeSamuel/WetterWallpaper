package de.seelig.wetterwallpaper;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Vorhersage und Zusatzwerte von Open-Meteo für die Widgets (Zeiten als Unix-Sekunden).
 * Fehlende Felder bleiben leer bzw. NaN; die Widgets zeigen dann „–“.
 */
final class Forecast {
    static final Forecast EMPTY = new Forecast();

    // aktuell
    float apparent = Float.NaN;
    float humidity = Float.NaN;
    float uv = Float.NaN;
    float windSpeed = Float.NaN;
    float windDir = Float.NaN;
    // stündlich
    long[] hourTime = new long[0];
    float[] hourTemp = new float[0];
    int[] hourCode = new int[0];
    float[] hourCloud = new float[0];
    float[] hourRainChance = new float[0];
    // täglich
    long[] dayTime = new long[0];
    int[] dayCode = new int[0];
    float[] dayMax = new float[0];
    float[] dayMin = new float[0];
    // Viertelstunden
    long[] q15Time = new long[0];
    float[] q15Rain = new float[0];
    // Luft (eigener Abruf)
    int aqi = -1;
    float[] pollen = new float[0]; // Erle, Birke, Gräser, Beifuß, Ambrosia (Körner/m³)
    static final String[] POLLEN_NAMES = {"Erle", "Birke", "Gräser", "Beifuß", "Ambrosia"};
    static final String[] POLLEN_KEYS = {"alder_pollen", "birch_pollen", "grass_pollen", "mugwort_pollen", "ragweed_pollen"};

    /** Antwort des Vorhersage-Abrufs. */
    static Forecast parse(JSONObject root) {
        Forecast f = new Forecast();
        JSONObject c = root.optJSONObject("current");
        if (c != null) {
            f.apparent = (float) c.optDouble("apparent_temperature", Double.NaN);
            f.humidity = (float) c.optDouble("relative_humidity_2m", Double.NaN);
            f.uv = (float) c.optDouble("uv_index", Double.NaN);
            f.windSpeed = (float) c.optDouble("wind_speed_10m", Double.NaN);
            f.windDir = (float) c.optDouble("wind_direction_10m", Double.NaN);
        }
        JSONObject h = root.optJSONObject("hourly");
        if (h != null) {
            f.hourTime = longs(h.optJSONArray("time"));
            f.hourTemp = floats(h.optJSONArray("temperature_2m"));
            f.hourCode = ints(h.optJSONArray("weather_code"));
            f.hourCloud = floats(h.optJSONArray("cloud_cover"));
            f.hourRainChance = floats(h.optJSONArray("precipitation_probability"));
        }
        JSONObject d = root.optJSONObject("daily");
        if (d != null) {
            f.dayTime = longs(d.optJSONArray("time"));
            f.dayCode = ints(d.optJSONArray("weather_code"));
            f.dayMax = floats(d.optJSONArray("temperature_2m_max"));
            f.dayMin = floats(d.optJSONArray("temperature_2m_min"));
        }
        JSONObject q = root.optJSONObject("minutely_15");
        if (q != null) {
            f.q15Time = longs(q.optJSONArray("time"));
            f.q15Rain = floats(q.optJSONArray("precipitation"));
        }
        return f;
    }

    /** Luftqualität und Pollen in eine bestehende Vorhersage übernehmen. */
    void parseAir(JSONObject root) {
        JSONObject c = root.optJSONObject("current");
        if (c == null) return;
        aqi = c.optInt("european_aqi", -1);
        pollen = new float[POLLEN_KEYS.length];
        for (int i = 0; i < POLLEN_KEYS.length; i++) pollen[i] = (float) c.optDouble(POLLEN_KEYS[i], 0);
    }

    /** Index der ersten Stunde ab now (Unix-ms), -1 ohne Daten. */
    int hourIndex(long nowMs) {
        long s = nowMs / 1000;
        for (int i = 0; i < hourTime.length; i++) if (hourTime[i] + 3600 > s) return i;
        return -1;
    }

    int dayIndex(long nowMs) {
        long s = nowMs / 1000;
        for (int i = 0; i < dayTime.length; i++) if (dayTime[i] + 86400 > s) return i;
        return -1;
    }

    private static long[] longs(JSONArray a) {
        if (a == null) return new long[0];
        long[] r = new long[a.length()];
        for (int i = 0; i < r.length; i++) r[i] = a.optLong(i);
        return r;
    }

    private static float[] floats(JSONArray a) {
        if (a == null) return new float[0];
        float[] r = new float[a.length()];
        for (int i = 0; i < r.length; i++) r[i] = (float) a.optDouble(i, Double.NaN);
        return r;
    }

    private static int[] ints(JSONArray a) {
        if (a == null) return new int[0];
        int[] r = new int[a.length()];
        for (int i = 0; i < r.length; i++) r[i] = a.optInt(i);
        return r;
    }

    /** Wetter-Symbolname (WeatherIcon) für einen WMO-Code. */
    static String iconFor(int code, float cloudPct, boolean night) {
        Weather w = Weather.from(code, Float.isNaN(cloudPct) ? (code >= 3 ? 90 : code == 2 ? 50 : code == 1 ? 25 : 0) : cloudPct, 50000, 0);
        return WeatherIcon.kind(w, night);
    }
}
