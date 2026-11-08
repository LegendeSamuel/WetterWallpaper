package de.seelig.wetterwallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONObject;

import java.io.File;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live-Vorschau des Wallpapers mit demselben Renderer. Die Uhr lässt sich direkt im Bild ziehen;
 * Tageszeit und Wetter lassen sich nur für die Vorschau simulieren.
 */
public final class PreviewView extends View {

    /** Tageszeiten der Vorschau; Morgen/Abend folgen dem echten Sonnenstand am Standort. */
    enum TimeOfDay { NOW, MORNING, NOON, EVENING, NIGHT }

    interface Listener {
        /** Element ("clock", "widget" oder Abschnittsname eines Info-Widgets) wurde verschoben. */
        void onMoved(String id, float xPct, float yPct);
        /** Element angetippt. */
        void onSelected(String id);
        /** Freie Stelle angetippt (kein Ziehen). */
        void onEmptyTap();
        /** Mit zwei Fingern skaliert und verschoben (Größe in % der Bildbreite). */
        void onResized(String id, float sizePct, float xPct, float yPct, float rotation);
    }

    /** Einrasten an Ausrichtungslinien: Abstand in % der Bildbreite. */
    private static final float SNAP_PCT = 2f;


    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final WeatherSource weatherSource = new WeatherSource();
    private float aspect = 9f / 19.5f;
    private WallpaperRenderer renderer;
    private File dir;
    private JSONObject config = new JSONObject();
    private String loadedKey = "";
    private long imageStamp = -1;
    private TimeOfDay timeOfDay = TimeOfDay.NOW;
    private int customMinutes = -1;
    private String previewMode = "day";
    private String previewScreen = "lock";
    /** Vorschau-Wetter vom Nutzer (null = wie eingestellt) und vom Modus (Regen-Look zeigt Regen). */
    private String userWeather;
    private String modeWeather;
    /** Zwei Finger: Element, Startabstand, Startgröße, Startmitte und Startposition. */
    private String pinchId;
    private float pinchDist0;
    private float pinchSize0;
    private float pinchFx0;
    private float pinchFy0;
    private float[] pinchPos0;
    private float pinchAngle0;
    private float pinchRot0;
    private boolean rotSnapped;
    private boolean pinchDone;
    private double lat = 51.0;
    private double lon = 10.0;
    private Listener listener;
    private Runnable onSceneLoaded;
    private int loadsInFlight;
    private boolean hidePending;
    /** Gezogenes Element, null = keins. */
    private String dragId;
    private DeviceInfo device;
    private final InfoWidgets.Data widgetData = new InfoWidgets.Data();
    /** Ausrichtungslinien beim Ziehen: Kanten und Mitten der anderen Elemente, Bildmitte; NaN = keine sichtbar. */
    private final java.util.List<Float> linesX = new java.util.ArrayList<>();
    private final java.util.List<Float> linesY = new java.util.ArrayList<>();
    private RectF dragBox0;
    private float[] dragPos0;
    private float guideX = Float.NaN;
    private float guideY = Float.NaN;
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float dragDx;
    private float dragDy;
    private boolean emptyTap;
    private float downX;
    private float downY;
    /** Gestrichelter Rahmen um das gewählte Element: 0 keiner, 1 Uhr, 2 Widget. */
    private String outlined;
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Kleines Abbild der Vorschau (1/BACKDROP_SCALE) für die Milchglas-Flächen darüber. */
    private static final int BACKDROP_SCALE = 6;
    private Bitmap backdrop;
    private Runnable onBackdrop;
    private final Runnable makeBackdrop = this::renderBackdrop;

    public PreviewView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        device = new DeviceInfo(ctx);
        setContentDescription("Vorschau des Wallpapers. Uhr oder Widget antippen zum Bearbeiten, ziehen zum Verschieben.");
        float d = getResources().getDisplayMetrics().density;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(1.5f * d);
        outline.setColor(0xE6FFFFFF);
        outline.setPathEffect(new DashPathEffect(new float[]{6 * d, 4 * d}, 0));
        outline.setShadowLayer(2 * d, 0, 0, 0x99000000);
        guide.setStyle(Paint.Style.STROKE);
        guide.setStrokeWidth(1f * d);
        guide.setColor(0xD94FC3F7);
        guide.setPathEffect(new DashPathEffect(new float[]{4 * d, 3 * d}, 0));
        guide.setShadowLayer(1.5f * d, 0, 0, 0x80000000);

        Location l = LocationHelper.lastKnown(ctx, this::useLocation);
        if (l != null) useLocation(l);
    }

    private void useLocation(Location l) {
        lat = l.getLatitude();
        lon = l.getLongitude();
        invalidate();
    }

    void setScreenAspect(float aspect) {
        this.aspect = aspect;
        requestLayout();
    }

    /** Position eines Elements in % der Vorschau, null ohne Bild. */
    float[] position(String id) {
        return renderer != null ? renderer.position(id) : null;
    }

    /** Rahmen um Uhr (false) oder Widget (true) zeigen. */
    void outline(String id) {
        outlined = id;
        invalidate();
    }

    Bitmap backdrop() {
        return backdrop;
    }

    void setOnBackdrop(Runnable r) {
        onBackdrop = r;
    }

    /** Abbild gebündelt neu zeichnen, wenn sich etwas Sichtbares geändert hat. */
    private void scheduleBackdrop() {
        main.removeCallbacks(makeBackdrop);
        main.postDelayed(makeBackdrop, 250);
    }

    /** Ein Frame in klein und ohne Rahmen/Bewegung (Software-Canvas), nur für die Glas-Flächen. */
    private void renderBackdrop() {
        if (renderer == null || getWidth() == 0) return;
        Bitmap b = Bitmap.createBitmap(Math.max(1, getWidth() / BACKDROP_SCALE), Math.max(1, getHeight() / BACKDROP_SCALE),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(1f / BACKDROP_SCALE, 1f / BACKDROP_SCALE);
        long now = System.currentTimeMillis();
        renderer.render(c, sceneTime(now), now, previewWeather(), lat, lon, false);
        backdrop = b;
        if (onBackdrop != null) onBackdrop.run();
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setDirectory(File dir) {
        this.dir = dir;
    }

    /** Neue Einstellungen; Ebenen werden nur neu berechnet, wenn nötig. */
    void setConfig(JSONObject config) {
        this.config = config;
        if (renderer == null) return;
        renderer.applyConfig(config, dir);
        reloadSceneIfNeeded(false);
        invalidate();
        scheduleBackdrop();
    }

    /** Nach neuem Bild/Ausschnitt aufrufen. */
    void reloadImage() {
        reloadSceneIfNeeded(true);
    }

    void setPreviewMode(String mode) {
        previewMode = mode;
        if (renderer != null) renderer.setPreviewMode(mode);
        reloadSceneIfNeeded(false); // eigenes Foto des Modus
        invalidate();
        scheduleBackdrop();
    }

    void setScreen(String screen) {
        previewScreen = screen;
        if (renderer != null) renderer.setScreen(screen);
        reloadSceneIfNeeded(false);
        invalidate();
        scheduleBackdrop();
    }

    void simulateMinutes(int minutes) {
        customMinutes = minutes;
        invalidate();
        scheduleBackdrop();
    }

    /** Tageszeit der Vorschau (folgt dem bearbeiteten Modus). */
    void simulateTime(TimeOfDay time) {
        customMinutes = -1;
        timeOfDay = time;
        invalidate();
        scheduleBackdrop();
    }

    /** Wetter nur für die Vorschau, null = wie eingestellt. */
    void setUserWeather(String weather) {
        userWeather = weather;
        invalidate();
        scheduleBackdrop();
    }

    String userWeather() {
        return userWeather;
    }

    /** Wetter, das ein Modus für die Vorschau braucht (Regen-Look), null = keins. */
    void setModeWeather(String weather) {
        modeWeather = weather;
        invalidate();
        scheduleBackdrop();
    }

    private Weather previewWeather() {
        String preset = userWeather != null ? userWeather : modeWeather;
        Weather wx = preset != null ? Weather.preset(preset) : renderer.chooseWeather(weatherSource, lat, lon);
        return wx != null ? wx : Weather.CLEAR;
    }

    double lat() {
        return lat;
    }

    double lon() {
        return lon;
    }

    /** Wird aufgerufen, sobald neue Bildebenen geladen sind (z. B. um den Ebenen-Regler nachzuführen). */
    void setOnSceneLoaded(Runnable r) {
        onSceneLoaded = r;
    }

    /** Färbt ein, was bei dieser Schwelle vor der Uhr läge; schnell genug für Live-Feedback beim Ziehen. */
    void showLayerHighlight(float threshold, int color) {
        Scene s = renderer != null ? renderer.scene() : null;
        if (s == null) return;
        hidePending = false;
        renderer.setHighlight(s.frontMask(threshold), color);
        invalidate();
    }

    /** Erst ausblenden, wenn neue Ebenen fertig sind; bis dahin zeigt die Markierung die gültige Verdeckung. */
    private final Runnable forceHide = () -> {
        hidePending = false;
        if (renderer != null) renderer.setHighlight(null, 0);
        invalidate();
    };

    void hideLayerHighlight() {
        if (loadsInFlight > 0) {
            hidePending = true;
            // Sicherheitsnetz: spätestens nach 10 s weg, auch wenn eine Berechnung hängt
            main.removeCallbacks(forceHide);
            main.postDelayed(forceHide, 10_000);
            return;
        }
        if (renderer != null) renderer.setHighlight(null, 0);
        invalidate();
    }

    /** Tatsächlich genutzte Motiv-Schwelle (für den manuellen Regler), NaN solange nichts geladen ist. */
    float currentSubjectThreshold() {
        Scene s = renderer != null ? renderer.scene() : null;
        return s != null ? s.subjectThreshold : Float.NaN;
    }

    float currentWidgetThreshold() {
        Scene s = renderer != null ? renderer.scene() : null;
        return autoThreshold("widget");
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxW = MeasureSpec.getSize(widthSpec);
        int maxH = MeasureSpec.getSize(heightSpec);
        int h = maxH;
        int w = Math.round(h * aspect);
        if (w > maxW) {
            w = maxW;
            h = Math.round(w / aspect);
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == 0 || h == 0) return;
        renderer = new WallpaperRenderer(w, h);
        renderer.setPreviewMode(previewMode);
        renderer.setScreen(previewScreen);
        renderer.applyConfig(config, dir);
        loadedKey = "";
        reloadSceneIfNeeded(true);
    }

    private void reloadSceneIfNeeded(boolean imageChanged) {
        if (renderer == null || dir == null) return;
        WallpaperRenderer.SceneParams params = renderer.sceneParams(getWidth());
        File photo = renderer.photoDir();
        long stamp = new File(photo, "background.png").lastModified() + new File(photo, "depth.png").lastModified()
                + new File(photo, "sky.png").lastModified();
        if (!imageChanged && stamp == imageStamp && params.key().equals(loadedKey)) return;
        imageStamp = stamp;
        loadedKey = params.key();
        WallpaperRenderer target = renderer;
        File d = dir;
        loadsInFlight++;
        // schwer (Tiefe, Masken): im Hintergrund, das alte Bild bleibt solange stehen
        loader.execute(() -> {
            Scene loaded = null;
            try {
                loaded = params.load(d);
            } catch (RuntimeException | OutOfMemoryError e) {
                android.util.Log.e("WetterWallpaper", "Vorschau-Ebenen nicht berechnet", e);
            }
            Scene s = loaded;
            // immer zurückmelden, sonst bliebe die Ebenen-Markierung für immer stehen
            main.post(() -> {
                loadsInFlight--;
                if (s == null) {
                    if (hidePending && loadsInFlight == 0) {
                        hidePending = false;
                        hideLayerHighlight();
                    }
                    return;
                }
                if (target == renderer) {
                    target.setScene(s);
                    invalidate();
                    scheduleBackdrop();
                    if (onSceneLoaded != null) onSceneLoaded.run();
                }
                if (hidePending && loadsInFlight == 0) {
                    hidePending = false;
                    hideLayerHighlight();
                }
            });
        });
    }

    /** Vorhersage, Akku und Termin für die Info-Widgets (Zeit und Wetter setzt der Renderer). */
    private void feedWidgetData() {
        weatherSource.setWantAir(renderer.wants("air"));
        widgetData.f = weatherSource.forecast();
        widgetData.battery = device.battery();
        widgetData.charging = device.charging();
        widgetData.calendarGranted = DeviceInfo.calendarGranted(getContext());
        widgetData.event = device.nextEvent(renderer.wants("event"));
        renderer.setWidgetData(widgetData);
    }

    InfoWidgets.Data widgetData() {
        return widgetData;
    }

    /** Automatisch gewählte Ebene eines Elements, NaN solange unbekannt. */
    float autoThreshold(String id) {
        return renderer != null ? renderer.autoThreshold(id) : Float.NaN;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (renderer == null) return;
        long now = System.currentTimeMillis();
        Weather wx = previewWeather();
        long time = sceneTime(now);
        feedWidgetData();
        long delay = renderer.render(c, time, now, wx, lat, lon, true);
        if (outlined != null) {
            RectF b = new RectF(renderer.elementBox(outlined, time, wx));
            if (!b.isEmpty()) {
                float pad = getWidth() * 0.02f;
                b.inset(-pad, -pad);
                c.drawRoundRect(b, pad * 1.5f, pad * 1.5f, outline);
            }
        }
        if (dragId != null) {
            if (!Float.isNaN(guideX)) c.drawLine(guideX, 0, guideX, getHeight(), guide);
            if (!Float.isNaN(guideY)) c.drawLine(0, guideY, getWidth(), guideY, guide);
        }
        postInvalidateDelayed(delay > 0 ? delay : 1000 - now % 1000);
    }

    private long sceneTime(long now) {
        if (customMinutes >= 0) return at(now, customMinutes / 60, customMinutes % 60);
        switch (timeOfDay) {
            case MORNING: return sunCrossing(now, true) + 40 * 60_000L;
            case NOON: return noon(now);
            case EVENING: return sunCrossing(now, false) - 15 * 60_000L;
            case NIGHT: return at(now, 23, 0);
            default: return now;
        }
    }

    /** Sonnenauf- bzw. -untergang heute (Höhe 0°), in 5-Minuten-Schritten gesucht. */
    private long sunCrossing(long now, boolean rise) {
        long noon = noon(now);
        long step = 5 * 60_000L;
        long t = noon;
        for (int i = 0; i < 288; i++) {
            t += rise ? -step : step;
            if (SkyPosition.sun(t, lat, lon).elevation < 0) return t;
        }
        return rise ? at(now, 7, 0) : at(now, 19, 0); // Polartag/-nacht
    }

    private long noon(long now) {
        long best = at(now, 12, 0);
        double max = -90;
        for (long t = at(now, 9, 0); t <= at(now, 16, 0); t += 10 * 60_000L) {
            double e = SkyPosition.sun(t, lat, lon).elevation;
            if (e > max) {
                max = e;
                best = t;
            }
        }
        return best;
    }

    private static long at(long now, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        return c.getTimeInMillis();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (renderer == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                pinchDone = false;
                long now = System.currentTimeMillis();
                String hit = hitTest(e.getX(), e.getY(), sceneTime(now), previewWeather());
                if (hit == null) {
                    // freie Stelle: beim Loslassen ohne Ziehen als Tipp melden
                    emptyTap = true;
                    downX = e.getX();
                    downY = e.getY();
                    return true;
                }
                dragId = hit;
                float[] pos = renderer.position(hit);
                dragDx = pos[0] / 100 * getWidth() - e.getX();
                dragDy = pos[1] / 100 * getHeight() - e.getY();
                startGuides(hit, sceneTime(now), previewWeather());
                if (listener != null) listener.onSelected(hit);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                // zweiter Finger: das gegriffene, sonst das gewählte Element skalieren und verschieben
                String id = dragId != null ? dragId : outlined;
                if (e.getPointerCount() != 2 || id == null || pinchDone) return true;
                pinchId = id;
                dragId = null;
                emptyTap = false;
                pinchDist0 = Math.max(1f, distance(e));
                pinchSize0 = renderer.sizePct(id);
                pinchFx0 = (e.getX(0) + e.getX(1)) / 2;
                pinchFy0 = (e.getY(0) + e.getY(1)) / 2;
                pinchPos0 = renderer.position(id);
                pinchAngle0 = angle(e);
                pinchRot0 = renderer.rotation(id);
                rotSnapped = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (pinchId != null) {
                    if (e.getPointerCount() < 2) return true;
                    float size = clampPct(pinchSize0 * distance(e) / pinchDist0, 0.5f, 100);
                    float fx = (e.getX(0) + e.getX(1)) / 2;
                    float fy = (e.getY(0) + e.getY(1)) / 2;
                    float xPct = clampPct(pinchPos0[0] + (fx - pinchFx0) / getWidth() * 100, 5, 95);
                    float yPct = clampPct(pinchPos0[1] + (fy - pinchFy0) / getHeight() * 100, 5, 97);
                    // Drehen: Winkel zwischen den Fingern; rastet bei 0, 90, 180 und 270 Grad ein
                    float rot = pinchRot0 + angle(e) - pinchAngle0;
                    rot = ((rot % 360) + 540) % 360 - 180;
                    float snapTo = Math.round(rot / 90f) * 90f;
                    boolean snap = Math.abs(rot - snapTo) < 7;
                    if (snap) rot = snapTo == -180 ? 180 : snapTo;
                    if (snap && !rotSnapped) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    rotSnapped = snap;
                    renderer.setSize(pinchId, size);
                    renderer.setPosition(pinchId, xPct, yPct);
                    renderer.setRotation(pinchId, rot);
                    invalidate();
                    return true;
                }
                if (pinchDone) return true;
                if (emptyTap && Math.hypot(e.getX() - downX, e.getY() - downY) > getWidth() * 0.03f) emptyTap = false;
                if (dragId == null) return emptyTap;
                float xPct = clampPct((e.getX() + dragDx) / getWidth() * 100, 5, 95);
                float yPct = clampPct((e.getY() + dragDy) / getHeight() * 100, 5, 97);
                // Fläche verschiebt sich mit der Position: Kanten und Mitte an den Linien einrasten
                float dx = (xPct - dragPos0[0]) / 100 * getWidth();
                float dy = (yPct - dragPos0[1]) / 100 * getHeight();
                float max = getWidth() * SNAP_PCT / 100;
                float[] sx = Guides.snap(new float[]{dragBox0.left + dx, dragBox0.centerX() + dx, dragBox0.right + dx}, linesX, max);
                float[] sy = Guides.snap(new float[]{dragBox0.top + dy, dragBox0.centerY() + dy, dragBox0.bottom + dy}, linesY, max);
                if (sx != null) xPct += sx[0] / getWidth() * 100;
                if (sy != null) yPct += sy[0] / getHeight() * 100;
                float gx = sx != null ? sx[1] : Float.NaN;
                float gy = sy != null ? sy[1] : Float.NaN;
                if ((sx != null && gx != guideX) || (sy != null && gy != guideY)) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                guideX = gx;
                guideY = gy;
                renderer.setPosition(dragId, xPct, yPct);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (pinchId != null) finishPinch();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (pinchId != null) {
                    finishPinch();
                    return true;
                }
                if (pinchDone) {
                    pinchDone = false;
                    return true;
                }
                if (dragId == null) {
                    boolean tap = emptyTap && e.getActionMasked() == MotionEvent.ACTION_UP;
                    emptyTap = false;
                    if (tap && listener != null) listener.onEmptyTap();
                    return tap;
                }
                if (listener != null) {
                    float[] pos = renderer.position(dragId);
                    listener.onMoved(dragId, pos[0], pos[1]);
                }
                dragId = null;
                scheduleBackdrop();
                return true;
            }
            default:
                return dragId != null || pinchId != null;
        }
    }

    /** Getroffenes Element; liegen mehrere übereinander, das mit der nächsten Mitte. */
    private String hitTest(float x, float y, long time, Weather wx) {
        feedWidgetData();
        float slop = getWidth() * 0.06f; // großzügige Trefferfläche
        String hit = null;
        float best = Float.MAX_VALUE;
        for (String id : renderer.elementIds()) {
            RectF box = new RectF(renderer.elementBox(id, time, wx));
            if (box.isEmpty()) continue;
            box.inset(-slop, -slop);
            if (!box.contains(x, y)) continue;
            float d = distSq(x, y, box.centerX(), box.centerY());
            if (d < best) {
                best = d;
                hit = id;
            }
        }
        return hit;
    }

    /** Linien für das Ziehen von id: Bildmitte sowie Kanten und Mitten aller anderen sichtbaren Elemente. */
    private void startGuides(String id, long time, Weather wx) {
        dragPos0 = renderer.position(id);
        dragBox0 = new RectF(renderer.elementBox(id, time, wx));
        linesX.clear();
        linesY.clear();
        linesX.add(getWidth() / 2f);
        linesY.add(getHeight() / 2f);
        for (String other : renderer.elementIds()) {
            if (other.equals(id)) continue;
            RectF b = renderer.elementBox(other, time, wx);
            if (b.isEmpty()) continue;
            java.util.Collections.addAll(linesX, b.left, b.centerX(), b.right);
            java.util.Collections.addAll(linesY, b.top, b.centerY(), b.bottom);
        }
        guideX = Float.NaN;
        guideY = Float.NaN;
    }

    /** Geste fertig: Größe und Position melden; bis alle Finger weg sind, nichts mehr verschieben. */
    private void finishPinch() {
        String id = pinchId;
        pinchId = null;
        pinchDone = true;
        float[] pos = renderer.position(id);
        if (listener != null) listener.onResized(id, renderer.sizePct(id), pos[0], pos[1], renderer.rotation(id));
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        scheduleBackdrop();
    }

    private static float angle(MotionEvent e) {
        return (float) Math.toDegrees(Math.atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0)));
    }

    private static float distance(MotionEvent e) {
        return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
    }

    private static float distSq(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private static float clampPct(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    protected void onDetachedFromWindow() {
        loader.shutdownNow();
        super.onDetachedFromWindow();
    }
}
