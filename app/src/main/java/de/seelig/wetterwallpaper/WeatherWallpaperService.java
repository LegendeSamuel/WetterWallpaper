package de.seelig.wetterwallpaper;

import android.graphics.Canvas;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import org.json.JSONObject;

import java.io.File;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Das Live-Wallpaper: Bildwiederholung, Akku-Regeln und Dateibeobachtung. Gezeichnet wird vom
 * WallpaperRenderer, derselbe wie in der App-Vorschau.
 */
public class WeatherWallpaperService extends WallpaperService {

    private static final String TAG = "WetterWallpaper";
    private static final long LOCATION_REFRESH_MS = 30 * 60 * 1000;
    private static final String[] IMAGE_FILES = {"background.png", "depth.png", "foreground.png", "sky.png"};

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Laufende Engines (nur im Hauptthread anfassen). App und Wallpaper teilen sich den Prozess. */
    private static final Set<LayerEngine> ENGINES = new HashSet<>();

    /**
     * Von der App nach dem Speichern aufgerufen: Engines rechnen Bild und Ebenen schon jetzt im
     * Hintergrund, nicht erst beim nächsten Einschalten (sonst steht dort kurz das alte Bild).
     */
    static void filesChanged() {
        MAIN.post(() -> {
            for (LayerEngine e : ENGINES) e.preload();
        });
    }

    /**
     * Eine Szene für alle Engines (Sperr-, Startbildschirm, System-Vorschau beim Festlegen): gleiche Bilder
     * und Parameter werden nur einmal berechnet, nacheinander statt parallel um die Kerne konkurrierend.
     */
    private static final ExecutorService SCENE_LOADER = Executors.newSingleThreadExecutor();
    /** Zuletzt benutzte Szenen; zwei, damit Start- und Sperrbildschirm mit verschiedenen Einstellungen nicht abwechselnd neu rechnen. */
    private static final java.util.LinkedHashMap<String, Future<Scene>> SCENES = new java.util.LinkedHashMap<String, Future<Scene>>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Future<Scene>> eldest) {
            return size() > 3; // Sperr- und Startbildschirm plus der vorausberechnete naechste Modus
        }
    };

    private static synchronized Future<Scene> sharedScene(File dir, WallpaperRenderer.SceneParams params, long imageStamp) {
        String key = dir + "|" + imageStamp + "|" + params.key();
        Future<Scene> f = SCENES.get(key);
        if (f == null) {
            f = SCENE_LOADER.submit(() -> {
                long t = System.currentTimeMillis();
                Scene s = params.load(dir);
                Log.d(TAG, "Szene berechnet in " + (System.currentTimeMillis() - t) + " ms");
                return s;
            });
            SCENES.put(key, f);
        }
        return f;
    }

    @Override
    public Engine onCreateEngine() {
        return new LayerEngine();
    }

    private class LayerEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable tick = this::drawFrame;
        private final WeatherSource weatherSource = new WeatherSource();
        private final DeviceInfo device = new DeviceInfo(WeatherWallpaperService.this);
        private final InfoWidgets.Data widgetData = new InfoWidgets.Data();
        private final ExecutorService loader = Executors.newSingleThreadExecutor();
        private final long startMillis = System.currentTimeMillis();
        private WallpaperRenderer renderer;
        private String configStamp = "";
        private long imageStamp = -1;
        private String sceneKey = "";
        /** Modus, zu dem die geladene Szene gehört. */
        private String sceneMode;
        private long nextChange = -1;
        private long plannedAt;
        private final Runnable prefetch = this::prefetch;
        private final Runnable afterChange = this::preload;
        private long visibleSince;
        private double lat = Double.NaN;
        private double lon;
        private long locationCheckedAt;
        private boolean visible;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            ENGINES.add(this);
        }

        /** Auch unsichtbar einen Frame zeichnen: beim Einschalten zeigt das System zuerst den letzten. */
        void preload() {
            if (renderer != null && getSurfaceHolder().getSurface().isValid()) drawFrame();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            renderer = new WallpaperRenderer(w, h);
            renderer.setScreen(screen());
            configStamp = "";
            imageStamp = -1;
            sceneKey = "";
            drawFrame();
        }

        /** Gespeicherter Stand dieses Bildschirms; die System-Vorschau beim Festlegen zeigt den Stand der App. */
        private File dataDir() {
            File app = getExternalFilesDir(null);
            return app == null || isPreview() ? app : Published.forScreen(app, screen());
        }

        /** Nur Startbildschirm = "home"; Sperrbildschirm oder beide zusammen = "lock" (die Grundlage). */
        private String screen() {
            if (android.os.Build.VERSION.SDK_INT < 34) return "lock";
            int f = getWallpaperFlags();
            boolean lock = (f & android.app.WallpaperManager.FLAG_LOCK) != 0;
            boolean system = (f & android.app.WallpaperManager.FLAG_SYSTEM) != 0;
            return system && !lock ? "home" : "lock";
        }

        @Override
        public void onWallpaperFlagsChanged(int which) {
            if (renderer == null) return;
            renderer.setScreen(screen());
            sceneKey = "";
            drawFrame();
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            if (v) {
                visibleSince = System.currentTimeMillis();
                drawFrame();
            } else {
                handler.removeCallbacks(tick);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            handler.removeCallbacks(tick);
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacksAndMessages(null);
            ENGINES.remove(this);
            loader.shutdownNow();
            super.onDestroy();
        }

        /** Einstellungen sofort übernehmen; Ebenen nur neu berechnen, wenn Bild oder bildrelevante Werte sich ändern. */
        private void reloadIfChanged() {
            File dir = dataDir();
            if (dir == null) return;
            String cfg = dir + "|" + new File(dir, Settings.FILE).lastModified();
            if (!cfg.equals(configStamp)) {
                configStamp = cfg;
                JSONObject raw = Settings.read(dir);
                Settings.migrate(raw);
                renderer.applyConfig(raw, dir);
            }
            // Szene für den Modus, der jetzt gilt (nicht für einen gehaltenen)
            String mode = renderer.liveMode(System.currentTimeMillis(), la(), lo());
            WallpaperRenderer.SceneParams params = renderer.sceneParamsFor(mode, 0);
            long img = imageStamp(params.photoDir != null ? params.photoDir : dir);
            String full = img + "|" + params.key();
            if (!full.equals(sceneKey)) {
                sceneKey = full;
                WallpaperRenderer target = renderer;
                String key = sceneKey;
                Future<Scene> shared = sharedScene(dir, params, img);
                if (shared.isDone()) {
                    // vorausberechnet: sofort tauschen, Uhr und Bild wechseln gemeinsam
                    try {
                        renderer.setScene(shared.get());
                        renderer.hold(null);
                        sceneMode = mode;
                        return;
                    } catch (ExecutionException | InterruptedException e) {
                        Log.e(TAG, "Szene konnte nicht berechnet werden", e);
                    }
                }
                // bis die neue Szene da ist, den alten Modus zeichnen: passt zum noch alten Bild
                if (renderer.scene() != null && sceneMode != null && !sceneMode.equals(mode)) renderer.hold(sceneMode);
                // schwer (Tiefe, Masken, Pfützen): nie im Hauptthread, sonst hängt auch die App (gleicher Prozess)
                loader.execute(() -> {
                    Scene s;
                    try {
                        s = shared.get();
                    } catch (ExecutionException e) {
                        Log.e(TAG, "Szene konnte nicht berechnet werden", e);
                        return;
                    } catch (InterruptedException e) {
                        return; // Engine wird beendet
                    }
                    handler.post(() -> {
                        if (target == renderer && key.equals(sceneKey)) {
                            target.setScene(s);
                            target.hold(null);
                            sceneMode = mode;
                            preload();
                        }
                    });
                });
            }
        }

        private long imageStamp(File photo) {
            long img = 0;
            for (String f : IMAGE_FILES) img += new File(photo, f).lastModified();
            return img;
        }

        private double la() {
            return Double.isNaN(lat) ? renderer.section("location").optDouble("lat", 51.0) : lat;
        }

        private double lo() {
            return Double.isNaN(lat) ? renderer.section("location").optDouble("lon", 10.0) : lon;
        }

        /**
         * Nächsten Moduswechsel einplanen: 3 Minuten vorher die Szene des neuen Modus berechnen (auch bei aus
         * geschaltetem Bildschirm) und kurz danach einen Frame zeichnen. Beim Einschalten ist dann alles fertig.
         */
        private void planPrefetch(long now) {
            if (nextChange > now && now - plannedAt < 10 * 60_000L) return;
            plannedAt = now;
            handler.removeCallbacks(prefetch);
            handler.removeCallbacks(afterChange);
            nextChange = DayNight.nextChange(renderer.section("dayNight"), now, la(), lo(), 24 * 60);
            if (nextChange > 0) handler.postDelayed(prefetch, Math.max(0, nextChange - now - 3 * 60_000L));
        }

        private void prefetch() {
            File dir = dataDir();
            if (dir == null || renderer == null || nextChange <= 0) return;
            String mode = renderer.liveMode(nextChange + 60_000L, la(), lo());
            WallpaperRenderer.SceneParams params = renderer.sceneParamsFor(mode, 0);
            sharedScene(dir, params, imageStamp(params.photoDir != null ? params.photoDir : dir));
            handler.postDelayed(afterChange, Math.max(0, nextChange - System.currentTimeMillis() + 5_000L));
        }

        private void refreshLocation() {
            long now = System.currentTimeMillis();
            if (now - locationCheckedAt < LOCATION_REFRESH_MS) return;
            locationCheckedAt = now;
            Location l = LocationHelper.lastKnown(WeatherWallpaperService.this, this::useLocation);
            if (l != null) useLocation(l);
        }

        private void useLocation(Location l) {
            lat = l.getLatitude();
            lon = l.getLongitude();
        }

        private void drawFrame() {
            handler.removeCallbacks(tick);
            if (renderer == null) return;
            reloadIfChanged();
            planPrefetch(System.currentTimeMillis());
            refreshLocation();
            JSONObject loc = renderer.section("location");
            double la = Double.isNaN(lat) ? loc.optDouble("lat", 51.0) : lat;
            double lo = Double.isNaN(lat) ? loc.optDouble("lon", 10.0) : lon;
            long now = System.currentTimeMillis();
            Weather wx = renderer.chooseWeather(weatherSource, la, lo);
            // Daten für die Info-Widgets (Zeit und Wetter setzt der Renderer)
            weatherSource.setWantAir(renderer.wants("air"));
            widgetData.f = weatherSource.forecast();
            widgetData.battery = device.battery();
            widgetData.charging = device.charging();
            widgetData.calendarGranted = DeviceInfo.calendarGranted(WeatherWallpaperService.this);
            widgetData.event = device.nextEvent(renderer.wants("event"));
            renderer.setWidgetData(widgetData);
            long frameDelay = 0;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockHardwareCanvas();
                if (c != null) frameDelay = renderer.render(c, sceneTime(now), now, wx, la, lo, animationAllowed(now));
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
            if (visible) {
                long delay;
                if (renderer.section("debug").optDouble("timelapseSeconds", 0) > 0) delay = 33;
                else if (frameDelay > 0) delay = frameDelay;
                else delay = 1000 - now % 1000; // statisch: nur die Uhr zur vollen Sekunde
                handler.postDelayed(tick, delay);
            }
        }

        /** Animation nur kurz nach dem Einschalten und nie im Energiesparmodus. */
        private boolean animationAllowed(long now) {
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null && pm.isPowerSaveMode()) return false;
            double seconds = renderer.section("performance").optDouble("animateSeconds", 20);
            return seconds < 0 || now - visibleSince < seconds * 1000;
        }

        /** Echte Zeit, oder zum Testen eine feste Uhrzeit (debug.time) bzw. ein 24-h-Zeitraffer. */
        private long sceneTime(long now) {
            JSONObject debug = renderer.section("debug");
            double lapse = debug.optDouble("timelapseSeconds", 0);
            if (lapse > 0) return now + (long) ((now - startMillis) / (lapse * 1000) * 86400000L);
            String fixed = debug.optString("time", "");
            if (fixed.matches("\\d{1,2}:\\d{2}")) {
                Calendar cal = Calendar.getInstance();
                String[] hm = fixed.split(":");
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                cal.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                return cal.getTimeInMillis();
            }
            return now;
        }
    }
}
