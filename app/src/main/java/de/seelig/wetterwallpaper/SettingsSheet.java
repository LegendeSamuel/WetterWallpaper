package de.seelig.wetterwallpaper;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Locale;

/**
 * Blatt hinter ☰: alle Einstellungen in aufklappbaren Abschnitten. Was pro Modus gilt (Szene, eigenes Foto),
 * trägt das Abzeichen „gilt für …“. Neue Einstellungen kommen als weiterer Abschnitt oder Unterpunkt dazu.
 */
final class SettingsSheet {
    private static final String[][] WEATHER = {{"clear", "Klar"}, {"partly", "Wolkig"}, {"overcast", "Bedeckt"},
            {"rain", "Regen"}, {"storm", "Gewitter"}, {"snow", "Schnee"}, {"frost", "Frost"}, {"fog", "Nebel"}};
    private static final String[][] SEASONS = {{"auto", "Automatisch"}, {"spring", "Frühling"}, {"summer", "Sommer"},
            {"autumn", "Herbst"}, {"winter", "Winter"}};

    private final MainActivity a;
    private final Settings settings;
    private final BottomSheetDialog dialog;
    private final NestedScrollView scroll;
    private final LinearLayout root;

    SettingsSheet(MainActivity a, Settings settings) {
        this.a = a;
        this.settings = settings;
        dialog = new BottomSheetDialog(a);
        a.glassify(dialog);
        scroll = new NestedScrollView(a);
        root = SheetKit.column(a);
        int pad = a.dp(20);
        root.setPadding(pad, a.dp(8), pad, a.dp(32));
        scroll.addView(root);
        dialog.setContentView(scroll);
    }

    void show() {
        build();
        dialog.show();
    }

    /** Neu aufbauen, wenn sich sichtbare Unterpunkte ändern; die Scrollposition bleibt. */
    private void rebuild() {
        int y = scroll.getScrollY();
        build();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void build() {
        root.removeAllViews();
        root.addView(SheetKit.heading(a, "Einstellungen"));
        String mode = DayNight.fullLabel(settings);
        boolean perMode = settings.editingOverlay();
        root.addView(SheetKit.section(a, "scene", R.drawable.ic_layers, "Szene & Effekte",
                "Wetter-Partikel, Wolken, Himmel, Filter", mode, this::scene));
        root.addView(SheetKit.section(a, "fav", R.drawable.ic_heart, "Favoriten",
                "Bild und alle Einstellungen speichern", null, this::favorites));
        root.addView(SheetKit.section(a, "photo", R.drawable.ic_image, "Bild", photoSummary(),
                perMode && !"home".equals(settings.screen()) ? mode : null, this::photo));
        root.addView(SheetKit.section(a, "weather", R.drawable.ic_rain, "Wetter", weatherSummary(), null, this::weather));
        root.addView(SheetKit.section(a, "motion", R.drawable.ic_bolt, "Bewegung & Akku", motionSummary(), null, this::motion));
        root.addView(SheetKit.section(a, "screens", R.drawable.ic_phone, "Start- & Sperrbildschirm",
                settings.splitScreens() ? "Getrennt gestaltet" : "Beide gleich", null, this::screens));
        root.addView(SheetKit.section(a, "place", R.drawable.ic_pin, "Standort",
                a.locationGranted() ? "Freigegeben" : "Nicht freigegeben", null, this::location));
        root.addView(SheetKit.section(a, "info", R.drawable.ic_info, "Info",
                "Wetterdaten: Open-Meteo.com", null, this::info));
    }

    /** Quellenangabe (CC BY 4.0 verlangt Nennung und Link) und Lizenzen; ausführlich in THIRD_PARTY_NOTICES.md. */
    private View info() {
        LinearLayout b = SheetKit.column(a);
        TextView source = SheetKit.text(a, "Wetterdaten: Open-Meteo.com (https://open-meteo.com), Lizenz CC BY 4.0.",
                R.style.TextAppearance_Wetter_BodyMedium, 0);
        android.text.util.Linkify.addLinks(source, android.text.util.Linkify.WEB_URLS);
        b.addView(source);
        b.addView(SheetKit.label(a, "Open-Source-Lizenzen"));
        b.addView(SheetKit.help(a, "ONNX Runtime · MIT\n"
                + "Depth Anything V2 Small (Tiefenmodell) · Apache-2.0\n"
                + "AndroidX, Material Components · Apache-2.0\n"
                + "Lexend (Schrift) · SIL OFL 1.1"));
        return b;
    }

    private void put(String section, String key, Object value) {
        settings.put(section, key, value);
        a.changed();
    }

    // ---------- Szene & Effekte (pro Modus) ----------

    private View scene() {
        LinearLayout b = SheetKit.column(a);
        b.addView(SheetKit.switchRow(a, "Wetter-Partikel", "Regen, Schnee und Laub. Ausgeschaltet fallen auch keine Blätter oder Blüten.",
                settings.b("layers", "particles", true), on -> put("layers", "particles", on)));
        b.addView(SheetKit.switchRow(a, "Fenster: Eisblumen & Regentropfen", "Bei Frost wachsen Eisblumen vom Rand herein, "
                        + "bei Regen sitzen Tropfen auf der Scheibe und laufen herunter.",
                settings.b("layers", "glass", true), on -> put("layers", "glass", on)));
        b.addView(SheetKit.switchRow(a, "Sonne, Mond & Sterne", "Nach dem echten Stand am Himmel deines Ortes.",
                settings.b("layers", "skyObjects", true), on -> put("layers", "skyObjects", on)));
        b.addView(SheetKit.switchRow(a, "Wolken", "Ziehen mit dem echten Wetter über den Himmel.",
                settings.b("layers", "clouds", true), on -> put("layers", "clouds", on)));
        boolean skyReplace = settings.b("layers", "skyReplace", true);
        b.addView(SheetKit.switchRow(a, "Himmel nach Tageszeit", "Ersetzt den Himmel im Foto nachts, in der Dämmerung und bei "
                        + "Wolken durch den echten Himmel mit Sternen und Mond. Aus: Der Himmel aus dem Foto bleibt immer.",
                skyReplace, on -> {
                    put("layers", "skyReplace", on);
                    rebuild();
                }));
        if (skyReplace) {
            float soft = (float) Math.round(settings.d("scene", "skySoftness", 1) * 100);
            b.addView(SheetKit.slider(a, "Übergang zum Himmel", 0, 100, 5, soft, " %", false,
                    v -> put("scene", "skySoftness", v / 100.0), "scharf (klare Silhouette)", "weich (unscharfe Fotos)"));
        }
        boolean filter = settings.b("layers", "backgroundFilter", true);
        b.addView(SheetKit.switchRow(a, "Foto abdunkeln (Licht & Wetter)", "Dunkelt Landschaft und Motiv bei Nacht und Regen ab und "
                + "passt die Farbstimmung an. Aus: Das Foto selbst bleibt unverändert, der Himmel nach Tageszeit geht trotzdem.", filter, on -> {
            put("layers", "backgroundFilter", on);
            rebuild();
        }));
        if (filter) {
            float bright = (float) Math.round((1 - settings.d("look", "darkness", 0.7)) / 0.7 * 100);
            b.addView(SheetKit.slider(a, "Helligkeit bei Nacht und Regen", 0, 100, 1, bright, " %", true,
                    v -> put("look", "darkness", 1 - v / 100.0 * 0.7), "realistisch dunkel", "hell"));
        }
        boolean season = settings.b("season", "enabled", true);
        b.addView(SheetKit.switchRow(a, "Jahreszeiten", "Färbt Pflanzen passend zur Jahreszeit, im Winter wird das Bild kühler.",
                season, on -> {
                    put("season", "enabled", on);
                    rebuild();
                }));
        if (season) {
            b.addView(SheetKit.choiceLine(a, SEASONS, settings.s("season", "mode", "auto").toLowerCase(Locale.ROOT),
                    m -> put("season", "mode", m)));
            boolean tint = settings.b("season", "tint", true);
            b.addView(SheetKit.switchRow(a, "Farbstimmung", null, tint, on -> {
                put("season", "tint", on);
                rebuild();
            }));
            if (tint) {
                b.addView(SheetKit.slider(a, "Stärke der Farbstimmung", 0, 100, 1,
                        (float) Math.round(settings.d("season", "strength", Season.DEFAULT_STRENGTH) * 100), " %", true,
                        v -> put("season", "strength", v / 100.0), null, null));
            }
            b.addView(SheetKit.switchRow(a, "Fallende Blätter & Blüten", "Braucht die Wetter-Partikel.",
                    settings.b("season", "particles", true), on -> put("season", "particles", on)));
        }
        return b;
    }

    // ---------- Favoriten ----------

    private View favorites() {
        LinearLayout b = SheetKit.column(a);
        b.addView(SheetKit.help(a, "Antippen lädt. Lange drücken: überschreiben, teilen, umbenennen oder löschen."));
        android.widget.HorizontalScrollView h = new android.widget.HorizontalScrollView(a);
        h.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(a);
        h.addView(row);
        b.addView(h);
        PresetPanel panel = new PresetPanel(a, row, a.dir(), a.worker());
        b.addView(SheetKit.button(a, "Favorit importieren", R.drawable.ic_add, false, v -> a.importPreset(panel)));
        return b;
    }

    // ---------- Bild ----------

    private String photoSummary() {
        String m = settings.activeMode();
        if ("day".equals(m)) return "Foto, Ausschnitt";
        return a.hasOwnPhoto(m) ? "Eigenes Foto für " + DayNight.fullLabel(settings) : "Wie am Tag";
    }

    private View photo() {
        LinearLayout b = SheetKit.column(a);
        String m = settings.activeMode();
        boolean day = "day".equals(m);
        boolean own = !day && a.hasOwnPhoto(m);
        if (!day) {
            b.addView(SheetKit.switchRow(a, "Eigenes Foto für " + DayNight.fullLabel(settings),
                    "Zum Beispiel ein Nachtfoto für die Nacht. Ohne eigenes Foto gilt das Foto vom Tag.", own, on -> {
                        if (on) a.launchPicker(m);
                        else confirmDropPhoto(m);
                    }));
        }
        if (day || own) {
            String target = day ? "day" : m;
            LinearLayout row = new LinearLayout(a);
            View pick = SheetKit.button(a, "Foto ändern", R.drawable.ic_image, true, v -> a.launchPicker(target));
            View crop = SheetKit.button(a, "Ausschnitt", R.drawable.ic_crop, false, v -> a.openCrop(target));
            crop.setEnabled(a.hasCropSource(target));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMarginEnd(a.dp(8));
            row.addView(pick, lp);
            row.addView(crop, new LinearLayout.LayoutParams(0, -2, 1));
            b.addView(row);
        }
        return b;
    }

    private void confirmDropPhoto(String mode) {
        new MaterialAlertDialogBuilder(a)
                .setTitle("Eigenes Foto entfernen?")
                .setMessage(DayNight.fullLabel(settings) + " zeigt dann wieder das Foto vom Tag.")
                .setNegativeButton("Abbrechen", (d, w) -> rebuild())
                .setOnCancelListener(d -> rebuild())
                .setPositiveButton("Entfernen", (d, w) -> {
                    a.dropOwnPhoto(mode);
                    rebuild();
                })
                .show();
    }

    // ---------- Wetter ----------

    private String weatherSummary() {
        if (settings.b("weather", "live", true)) return "Echtes Wetter an deinem Ort";
        String fixed = settings.s("weather", "fixed", "rain");
        for (String[] w : WEATHER) if (w[0].equals(fixed)) return "Fest: " + w[1];
        return "Fest";
    }

    private View weather() {
        LinearLayout b = SheetKit.column(a);
        boolean live = settings.b("weather", "live", true);
        b.addView(SheetKit.switchRow(a, "Echtes Wetter an deinem Ort",
                "Daten von Open-Meteo, höchstens alle 30 Minuten und nur, wenn das Wallpaper sichtbar ist.", live, on -> {
                    put("weather", "live", on);
                    rebuild();
                }));
        if (!live) {
            b.addView(SheetKit.label(a, "Feste Wetterlage"));
            b.addView(SheetKit.choice(a, WEATHER, settings.s("weather", "fixed", "rain"), w -> put("weather", "fixed", w)));
        }
        b.addView(SheetKit.label(a, "Nur in der Vorschau ansehen"));
        String[][] sim = new String[WEATHER.length + 1][];
        sim[0] = new String[]{"", "Wie eingestellt"};
        System.arraycopy(WEATHER, 0, sim, 1, WEATHER.length);
        String cur = a.previewWeather();
        ChipGroup g = SheetKit.choice(a, sim, cur == null ? "" : cur, w -> a.setPreviewWeather(w.isEmpty() ? null : w));
        b.addView(g);
        return b;
    }

    // ---------- Bewegung & Akku ----------

    private String motionSummary() {
        double s = settings.d("performance", "animateSeconds", 20);
        return s < 0 ? "Dauerhaft in Bewegung" : Math.round(s) + " s nach dem Einschalten";
    }

    private View motion() {
        LinearLayout b = SheetKit.column(a);
        double seconds = settings.d("performance", "animateSeconds", 20);
        boolean always = seconds < 0;
        if (!always) {
            b.addView(SheetKit.slider(a, "Bewegung nach dem Einschalten", 5, 60, 5, (float) seconds, " s", false,
                    v -> put("performance", "animateSeconds", Math.round(v)), null, null));
        }
        b.addView(SheetKit.switchRow(a, "Dauerhaft bewegen", "Bewegt sich, solange der Bildschirm an ist. Das kostet spürbar mehr Akku. "
                + "Im Energiesparmodus bleibt das Wallpaper immer still.", always, on -> {
            put("performance", "animateSeconds", on ? -1 : 20);
            rebuild();
        }));
        return b;
    }

    // ---------- Start- & Sperrbildschirm ----------

    private View screens() {
        LinearLayout b = SheetKit.column(a);
        boolean split = settings.splitScreens();
        b.addView(SheetKit.switchRow(a, "Getrennt gestalten",
                "Der Startbildschirm übernimmt alles vom Sperrbildschirm und speichert nur seine Abweichungen, zum Beispiel "
                        + "ohne Uhr. Wirkt nur, wenn das Wallpaper auf beiden Bildschirmen einzeln festgelegt ist.", split, on -> {
                    put("screens", "split", on);
                    if (!on) settings.setScreen("lock");
                    a.onModeSwitched();
                    rebuild();
                }));
        if (split) {
            b.addView(SheetKit.label(a, "Gerade bearbeiten"));
            b.addView(SheetKit.choice(a, new String[][]{{"lock", "Sperrbildschirm"}, {"home", "Startbildschirm"}},
                    settings.screen(), s -> {
                        settings.setScreen(s);
                        a.onModeSwitched();
                        rebuild();
                    }));
        }
        return b;
    }

    // ---------- Standort ----------

    private View location() {
        LinearLayout b = SheetKit.column(a);
        boolean granted = a.locationGranted();
        TextView t = SheetKit.text(a, granted
                ? "Freigegeben. Sonne, Mond und Wetter passen zu deinem Ort."
                : "Nicht freigegeben. Es wird die Mitte Deutschlands angenommen.", R.style.TextAppearance_Wetter_BodyMedium, 0);
        b.addView(t);
        if (!granted) b.addView(SheetKit.button(a, "Freigeben", R.drawable.ic_pin, false, v -> a.requestLocation()));
        return b;
    }

    static boolean hasBackground(File dir) {
        return new File(dir, "background.png").exists();
    }
}
