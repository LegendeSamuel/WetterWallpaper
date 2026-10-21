package de.seelig.wetterwallpaper;

import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Aktuelles Wetter von Open-Meteo (kostenlos, ohne Schlüssel). Wird nur abgefragt, wenn das Wallpaper
 * sichtbar ist und die letzte Antwort älter als 30 Minuten ist; kein Hintergrunddienst.
 */
final class WeatherSource {
    private static final String TAG = "WetterWallpaper";
    private static final long FRESH_MS = 30 * 60 * 1000;
    private static final long RETRY_MS = 10 * 60 * 1000;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile Weather current = Weather.CLEAR;
    private volatile Forecast forecast = Forecast.EMPTY;
    /** Luftqualität nur abrufen, wenn ein Widget sie zeigt. */
    private volatile boolean wantAir;
    private volatile long nextFetch;
    private volatile boolean inFlight;

    Weather get(double lat, double lon) {
        long now = System.currentTimeMillis();
        if (!inFlight && now >= nextFetch) {
            inFlight = true;
            io.execute(() -> fetch(lat, lon));
        }
        return current;
    }

    /** Vorhersage und Zusatzwerte des letzten Abrufs (für die Widgets). */
    Forecast forecast() {
        return forecast;
    }

    void setWantAir(boolean want) {
        if (want && !wantAir) nextFetch = 0; // gleich nachholen
        wantAir = want;
    }

    private void fetch(double lat, double lon) {
        long now = System.currentTimeMillis();
        HttpURLConnection con = null;
        try {
            // grob gerundet: ~1 km reicht fürs Wetter und verrät weniger
            URL url = new URL(String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                            + "&current=weather_code,cloud_cover,visibility,wind_speed_10m,snow_depth,temperature_2m,"
                            + "apparent_temperature,relative_humidity_2m,uv_index,wind_direction_10m"
                            + "&hourly=temperature_2m,weather_code,cloud_cover,precipitation_probability&forecast_hours=24"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=7"
                            + "&minutely_15=precipitation&forecast_minutely_15=8&timeformat=unixtime&timezone=auto", lat, lon));
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            if (con.getResponseCode() != 200) throw new IllegalStateException("HTTP " + con.getResponseCode());
            JSONObject root = new JSONObject(read(con.getInputStream()));
            JSONObject c = root.getJSONObject("current");
            Forecast f = Forecast.parse(root);
            if (wantAir) fetchAir(f, lat, lon);
            forecast = f;
            current = Weather.from(c.optInt("weather_code", 0), c.optDouble("cloud_cover", 0),
                    c.optDouble("visibility", 50000), c.optDouble("wind_speed_10m", 0), c.optDouble("snow_depth", 0),
                    c.optDouble("temperature_2m", 20.0));
            nextFetch = now + FRESH_MS;
            Log.i(TAG, "Wetter aktualisiert: " + c);
        } catch (Exception e) {
            Log.w(TAG, "Wetterabruf fehlgeschlagen, behalte letzten Stand", e);
            nextFetch = now + RETRY_MS;
        } finally {
            if (con != null) con.disconnect();
            inFlight = false;
        }
    }

    /** Europäischer Luftindex und Pollen; Fehler hier lassen das Wetter unberührt. */
    private static void fetchAir(Forecast f, double lat, double lon) {
        HttpURLConnection con = null;
        try {
            URL url = new URL(String.format(Locale.US, "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.2f&longitude=%.2f"
                    + "&current=european_aqi,%s&timeformat=unixtime", lat, lon, String.join(",", Forecast.POLLEN_KEYS)));
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            if (con.getResponseCode() != 200) throw new IllegalStateException("HTTP " + con.getResponseCode());
            f.parseAir(new JSONObject(read(con.getInputStream())));
        } catch (Exception e) {
            Log.w(TAG, "Luftqualität nicht abrufbar", e);
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String read(InputStream in) throws java.io.IOException {
        try (InputStream s = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            for (int n; (n = s.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
