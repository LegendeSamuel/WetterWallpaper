package de.seelig.wetterwallpaper;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Einstellungen als config.json im Wallpaper-Ordner. Das Live-Wallpaper beobachtet die Datei und
 * übernimmt Änderungen selbst; geschrieben wird atomar, damit es nie eine halbe Datei liest.
 *
 * Ebenen: Der Tag steht direkt im Wurzelobjekt. Jeder andere Modus (modes.&lt;id&gt;) speichert nur, was vom Tag
 * abweicht; ein entfernter Wert steht dort als JSON null. Darüber kann der Startbildschirm (screens.home) noch
 * einmal abweichen, in allen Modi gleich. Gelesen wird immer die zusammengeführte Sicht, geschrieben in die
 * oberste gerade bearbeitete Ebene.
 */
final class Settings {
    static final String FILE = "config.json";
    private static final String TAG = "WetterWallpaper";
    /** 2 = Modi speichern nur Abweichungen vom Tag. */
    private static final int MODEL = 2;

    /** Gelten für alle Modi und Bildschirme. */
    private static final Set<String> GLOBAL_SECTIONS = Set.of(
            "dayNight", "weather", "performance", "location", "debug", "modes", "screens", "modelVersion");

    private final File dir;
    private JSONObject json;
    private String activeMode = "day";
    /** Bearbeiteter Bildschirm: "lock" (= beide, Grundlage) oder "home" (Abweichungen des Startbildschirms). */
    private String screen = "lock";

    Settings(File dir) {
        this.dir = dir;
        this.json = read(dir);
        migrate(json);
    }

    static JSONObject read(File dir) {
        File f = new File(dir, FILE);
        try {
            if (f.exists()) return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "config.json fehlerhaft, nutze Standardwerte", e);
        }
        return new JSONObject();
    }

    // ---------- Bearbeitete Ebene ----------

    /** Aktuell im Editor bearbeiteter Modus ("day", "dawn", "dusk", "night", "rain" oder "slot_..."). */
    String activeMode() {
        return activeMode;
    }

    void setActiveMode(String mode) {
        activeMode = (mode == null || mode.isEmpty()) ? "day" : mode;
    }

    String screen() {
        return screen;
    }

    /** "home" nur, wenn Start- und Sperrbildschirm getrennt sind. */
    void setScreen(String s) {
        screen = "home".equals(s) && splitScreens() ? "home" : "lock";
    }

    boolean splitScreens() {
        JSONObject s = json.optJSONObject("screens");
        return s != null && s.optBoolean("split", false);
    }

    /** Wird gerade eine Abweichungs-Ebene bearbeitet (nicht der Tag auf beiden Bildschirmen)? */
    boolean editingOverlay() {
        return "home".equals(screen) || !"day".equals(activeMode);
    }

    /** Alle Abweichungen des Modus verwerfen: er sieht dann wieder genau wie der Tag aus. */
    void copyFromDay(String mode) {
        if (mode == null || "day".equals(mode)) return;
        JSONObject modes = json.optJSONObject("modes");
        if (modes != null) modes.remove(mode);
        ensureMode(mode);
    }

    void removeMode(String mode) {
        if (mode == null || "day".equals(mode)) return;
        JSONObject modes = json.optJSONObject("modes");
        if (modes != null) modes.remove(mode);
        if (mode.equals(activeMode)) activeMode = "day";
    }

    /** Abweichungen eines Modus (leer anlegen, falls noch keine). */
    JSONObject ensureMode(String mode) {
        if (mode == null || "day".equals(mode)) return json;
        JSONObject modes = child(json, "modes");
        JSONObject m = modes.optJSONObject(mode);
        if (m != null) return m;
        // Früher gab es eine gemeinsame Dämmerung; Morgen und Abend starten von ihr
        JSONObject tw = modes.optJSONObject("twilight");
        m = new JSONObject();
        if (("dawn".equals(mode) || "dusk".equals(mode)) && tw != null) m = copy(tw);
        putQuiet(modes, mode, m);
        return m;
    }

    private JSONObject homeOverlay() {
        return child(child(json, "screens"), "home");
    }

    /** Ebenen von unten nach oben, die der Editor gerade sieht. Zum Lesen wird nichts angelegt. */
    private List<JSONObject> layers() {
        return layers(false);
    }

    private List<JSONObject> layers(boolean create) {
        List<JSONObject> l = new ArrayList<>();
        l.add(json);
        if (!"day".equals(activeMode)) {
            JSONObject modes = json.optJSONObject("modes");
            JSONObject m = modes != null ? modes.optJSONObject(activeMode) : null;
            if (m == null && modes != null && ("dawn".equals(activeMode) || "dusk".equals(activeMode))) {
                JSONObject tw = modes.optJSONObject("twilight");
                if (tw != null && !create) m = tw;
            }
            if (create) m = ensureMode(activeMode);
            l.add(m != null ? m : new JSONObject());
        }
        if ("home".equals(screen)) {
            JSONObject screens = json.optJSONObject("screens");
            JSONObject h = screens != null ? screens.optJSONObject("home") : null;
            if (create) h = homeOverlay();
            l.add(h != null ? h : new JSONObject());
        }
        return l;
    }

    private JSONObject target(String section) {
        if (GLOBAL_SECTIONS.contains(section)) return json;
        List<JSONObject> l = layers(true);
        return l.get(l.size() - 1);
    }

    // ---------- Lesen ----------

    /** Zusammengeführte Sicht eines Abschnitts (Kopie, nur zum Lesen). */
    JSONObject sec(String name) {
        if (GLOBAL_SECTIONS.contains(name)) {
            JSONObject s = json.optJSONObject(name);
            return s != null ? s : new JSONObject();
        }
        JSONObject out = new JSONObject();
        for (JSONObject layer : layers()) overlaySection(out, layer.optJSONObject(name));
        return out;
    }

    /** Wert eines Abschnitts unterhalb der bearbeiteten Ebene (Vergleich: weicht der Wert ab?). */
    private Object below(String section, String key) {
        List<JSONObject> l = layers();
        Object v = null;
        for (int i = 0; i < l.size() - 1; i++) {
            JSONObject s = l.get(i).optJSONObject(section);
            if (s == null || !s.has(key)) continue;
            Object o = s.opt(key);
            v = o == JSONObject.NULL ? null : o;
        }
        return v;
    }

    double d(String section, String key, double def) {
        return sec(section).optDouble(key, def);
    }

    String s(String section, String key, String def) {
        return sec(section).optString(key, def);
    }

    boolean b(String section, String key, boolean def) {
        return sec(section).optBoolean(key, def);
    }

    boolean has(String section, String key) {
        return sec(section).has(key);
    }

    org.json.JSONArray array(String section, String key) {
        return sec(section).optJSONArray(key);
    }

    // ---------- Schreiben ----------

    void put(String section, String key, Object value) {
        JSONObject root = target(section);
        boolean overlay = root != json;
        if (overlay && jsonEquals(below(section, key), value)) {
            JSONObject s = root.optJSONObject(section);
            if (s != null) {
                s.remove(key);
                if (s.length() == 0) root.remove(section);
            }
            return;
        }
        putQuiet(child(root, section), key, value);
    }

    void remove(String section, String key) {
        JSONObject root = target(section);
        if (root == json) {
            JSONObject s = json.optJSONObject(section);
            if (s != null) s.remove(key);
            return;
        }
        // in einer Abweichungs-Ebene: entfernen heißt "Standardwert", auch wenn darunter ein Wert steht
        if (below(section, key) != null) putQuiet(child(root, section), key, JSONObject.NULL);
        else {
            JSONObject s = root.optJSONObject(section);
            if (s != null) s.remove(key);
        }
    }

    /** Schlüssel, die die bearbeitete Ebene in diesem Abschnitt selbst festlegt (leer auf dem Tag). */
    Set<String> overridden(String section) {
        Set<String> keys = new LinkedHashSet<>();
        if (!editingOverlay() || GLOBAL_SECTIONS.contains(section)) return keys;
        List<JSONObject> l = layers();
        JSONObject s = l.get(l.size() - 1).optJSONObject(section);
        if (s != null) for (Iterator<String> it = s.keys(); it.hasNext(); ) keys.add(it.next());
        return keys;
    }

    /** Abweichungen verwerfen: die Werte kommen wieder vom Tag (bzw. vom Sperrbildschirm). */
    void resetKeys(String section, Collection<String> keys) {
        if (!editingOverlay()) return;
        List<JSONObject> l = layers();
        JSONObject root = l.get(l.size() - 1);
        JSONObject s = root.optJSONObject(section);
        if (s == null) return;
        for (String k : keys) s.remove(k);
        if (s.length() == 0) root.remove(section);
    }

    /** Reihenfolge der Info-Widgets (Abschnittsnamen) in der bearbeiteten Ebene. */
    List<String> widgetIds() {
        List<String> ids = new ArrayList<>();
        JSONArray a = topArray("widgetList");
        for (int i = 0; a != null && i < a.length(); i++) ids.add(a.optString(i));
        return ids;
    }

    void setWidgetIds(List<String> ids) {
        setTopArray("widgetList", ids);
    }

    /** Zeichenreihenfolge der Elemente über dem Motiv (erstes = ganz hinten). */
    List<String> order() {
        List<String> ids = new ArrayList<>();
        JSONArray a = topArray("order");
        for (int i = 0; a != null && i < a.length(); i++) ids.add(a.optString(i));
        return ids;
    }

    void setOrder(List<String> ids) {
        setTopArray("order", ids);
    }

    private JSONArray topArray(String key) {
        JSONArray found = null;
        for (JSONObject layer : layers()) {
            JSONArray a = layer.optJSONArray(key);
            if (a != null) found = a;
        }
        return found;
    }

    private void setTopArray(String key, List<String> ids) {
        List<JSONObject> l = layers(true);
        JSONObject top = l.get(l.size() - 1);
        JSONArray below = null;
        for (int i = 0; i < l.size() - 1; i++) if (l.get(i).optJSONArray(key) != null) below = l.get(i).optJSONArray(key);
        JSONArray value = new JSONArray(ids);
        if (top != json && below != null && below.toString().equals(value.toString())) top.remove(key);
        else putQuiet(top, key, value);
    }

    /** Abschnitt eines entfernten Info-Widgets löschen (nur in der bearbeiteten Ebene). */
    void removeSection(String name) {
        target(name).remove(name);
    }

    /** Werte (beliebigen Typs) von einem Abschnitt in einen anderen übernehmen, soweit vorhanden. */
    void copyKeys(String from, String to, String[] keys) {
        JSONObject src = sec(from);
        for (String k : keys) if (src.has(k)) put(to, k, src.opt(k));
    }

    // ---------- Für den Renderer ----------

    /** Kopie für den Renderer, damit spätere Änderungen ihn nicht mitten im Frame treffen. */
    JSONObject snapshot() {
        return copy(json);
    }

    /** Ganzen Stand ersetzen (Rückgängig/Wiederholen). */
    void replace(String text) {
        try {
            json = new JSONObject(text);
        } catch (JSONException e) {
            Log.e(TAG, "Stand für Rückgängig nicht lesbar", e);
        }
    }

    String text() {
        return json.toString();
    }

    static JSONObject resolveForMode(JSONObject raw, String mode) {
        return resolve(raw, mode, "lock");
    }

    /** Wirksame Konfiguration für Modus und Bildschirm: Tag, darüber Modus, darüber Startbildschirm. */
    static JSONObject resolve(JSONObject raw, String mode, String screen) {
        if (raw == null) return new JSONObject();
        JSONObject modes = raw.optJSONObject("modes");
        JSONObject m = null;
        if (mode != null && !"day".equals(mode) && modes != null) {
            m = modes.optJSONObject(mode);
            if (m == null && ("dawn".equals(mode) || "dusk".equals(mode))) m = modes.optJSONObject("twilight");
        }
        JSONObject screens = raw.optJSONObject("screens");
        JSONObject home = "home".equals(screen) && screens != null && screens.optBoolean("split", false)
                ? screens.optJSONObject("home") : null;
        if (m == null && home == null) return raw;
        JSONObject out = copy(raw);
        overlay(out, m);
        overlay(out, home);
        return out;
    }

    private static void overlay(JSONObject out, JSONObject layer) {
        if (layer == null) return;
        for (Iterator<String> it = layer.keys(); it.hasNext(); ) {
            String k = it.next();
            if (GLOBAL_SECTIONS.contains(k)) continue;
            Object v = layer.opt(k);
            if (v instanceof JSONObject) {
                JSONObject target = out.optJSONObject(k);
                JSONObject merged = target != null ? copy(target) : new JSONObject();
                overlaySection(merged, (JSONObject) v);
                putQuiet(out, k, merged);
            } else if (v instanceof JSONArray) {
                putQuiet(out, k, copyArray((JSONArray) v));
            } else {
                putQuiet(out, k, v);
            }
        }
    }

    /** Schlüssel einer Ebene über einen Abschnitt legen; null = Wert entfernen. */
    private static void overlaySection(JSONObject out, JSONObject layer) {
        if (layer == null) return;
        for (Iterator<String> it = layer.keys(); it.hasNext(); ) {
            String k = it.next();
            Object v = layer.opt(k);
            if (v == JSONObject.NULL) out.remove(k);
            else putQuiet(out, k, v instanceof JSONArray ? copyArray((JSONArray) v) : v);
        }
    }

    // ---------- Umstellung alter Dateien ----------

    /** Modi waren früher volle Kopien des Tages: nur die echten Abweichungen behalten. */
    static void migrate(JSONObject json) {
        if (json.optInt("modelVersion", 1) >= MODEL) return;
        JSONObject modes = json.optJSONObject("modes");
        if (modes != null) {
            for (Iterator<String> it = modes.keys(); it.hasNext(); ) {
                JSONObject m = modes.optJSONObject(it.next());
                if (m != null) sparsify(m, json);
            }
        }
        // ganz alte Nacht-Felder (styleNight/colorNight) als Abweichung der Nacht
        JSONObject clock = json.optJSONObject("clock");
        if (clock != null && (clock.has("styleNight") || clock.has("colorNight"))
                && (modes == null || !modes.has("night"))) {
            JSONObject night = new JSONObject();
            String sn = clock.optString("styleNight", "");
            String cn = clock.optString("colorNight", "");
            if (!sn.isEmpty() && !"same".equals(sn)) putQuiet(child(night, "clock"), "style", sn);
            if (!cn.isEmpty()) putQuiet(child(night, "clock"), "color", cn);
            if (night.length() > 0) putQuiet(child(json, "modes"), "night", night);
        }
        putQuiet(json, "modelVersion", MODEL);
    }

    private static void sparsify(JSONObject mode, JSONObject day) {
        List<String> keys = new ArrayList<>();
        for (Iterator<String> it = mode.keys(); it.hasNext(); ) keys.add(it.next());
        for (String k : keys) {
            Object mv = mode.opt(k);
            Object dv = day.opt(k);
            if (mv instanceof JSONObject && dv instanceof JSONObject) {
                JSONObject ms = (JSONObject) mv;
                JSONObject ds = (JSONObject) dv;
                List<String> sub = new ArrayList<>();
                for (Iterator<String> it = ms.keys(); it.hasNext(); ) sub.add(it.next());
                for (String sk : sub) if (ds.has(sk) && jsonEquals(ds.opt(sk), ms.opt(sk))) ms.remove(sk);
                if (ms.length() == 0) mode.remove(k);
            } else if (dv != null && jsonEquals(dv, mv)) {
                mode.remove(k);
            }
        }
    }

    // ---------- Speichern ----------

    void save() throws IOException {
        File tmp = new File(dir, FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException(e);
        }
        File target = new File(dir, FILE);
        if (target.exists() && !target.delete()) throw new IOException("Kann config.json nicht ersetzen");
        if (!tmp.renameTo(target)) throw new IOException("Kann config.json nicht schreiben");
        WeatherWallpaperService.filesChanged();
    }

    // ---------- Hilfen ----------

    static boolean jsonEquals(Object a, Object b) {
        if (a == JSONObject.NULL) a = null;
        if (b == JSONObject.NULL) b = null;
        if (a == null || b == null) return a == b;
        if (a instanceof Number && b instanceof Number) return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private static JSONObject child(JSONObject parent, String key) {
        JSONObject c = parent.optJSONObject(key);
        if (c == null) {
            c = new JSONObject();
            putQuiet(parent, key, c);
        }
        return c;
    }

    private static void putQuiet(JSONObject o, String key, Object value) {
        try {
            o.put(key, value);
        } catch (JSONException e) {
            throw new IllegalArgumentException(key, e);
        }
    }

    static JSONObject copy(JSONObject o) {
        try {
            return new JSONObject(o.toString());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONArray copyArray(JSONArray a) {
        try {
            return new JSONArray(a.toString());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
