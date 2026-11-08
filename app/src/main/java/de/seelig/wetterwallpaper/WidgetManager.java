package de.seelig.wetterwallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import com.google.android.material.snackbar.Snackbar;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Elemente im Bild: Leiste unten (Uhr, Datum & Wetter, Info-Widgets, „+“), je Info-Widget ein eigenes
 * Einstellungs-Panel (gleiche Werkzeuge wie die Uhr + „Inhalt“), Hinzufügen über die Galerie, Entfernen.
 */
final class WidgetManager {
    private final MainActivity a;
    private final Settings settings;
    private final ChipGroup bar;
    private final FrameLayout host;
    private final int highlight;
    private final Runnable onSelect;
    private final Map<String, TextPanel> panels = new LinkedHashMap<>();
    private final Map<String, View> views = new LinkedHashMap<>();
    private int[] photo = new int[0];
    private String selected = "clock";

    WidgetManager(MainActivity a, Settings settings, ChipGroup bar, FrameLayout host, int highlight, Runnable onSelect) {
        this.a = a;
        this.settings = settings;
        this.bar = bar;
        this.host = host;
        this.highlight = highlight;
        this.onSelect = onSelect;
        bar.setOnCheckedStateChangeListener((g, ids) -> {
            Chip c = g.findViewById(g.getCheckedChipId());
            if (c != null && c.getTag() != null && !c.getTag().equals(selected)) {
                selected = (String) c.getTag();
                onSelect.run();
            }
        });
        buildChips();
    }

    String selected() {
        return selected;
    }

    static boolean isInfo(String id) {
        return !"clock".equals(id) && !"widget".equals(id);
    }

    /** Element wählen (aus der Vorschau oder nach dem Hinzufügen). */
    void select(String id) {
        selected = id;
        for (int i = 0; i < bar.getChildCount(); i++) {
            View v = bar.getChildAt(i);
            if (v instanceof Chip && ((Chip) v).isCheckable()) {
                boolean match = id != null && id.equals(v.getTag());
                ((Chip) v).setChecked(match);
                if (match) {
                    final View chipView = v;
                    chipView.post(() -> {
                        View parent = (View) bar.getParent();
                        if (parent instanceof android.widget.HorizontalScrollView) {
                            ((android.widget.HorizontalScrollView) parent).smoothScrollTo(Math.max(0, chipView.getLeft() - 40), 0);
                        }
                    });
                }
            }
        }
        onSelect.run();
    }

    void buildChips() {
        bar.removeAllViews();
        boolean hasClock = settings.b("clock", "present", true);
        boolean hasWidget = settings.b("widget", "present", true);
        if (hasClock) chip("clock", "Uhr");
        // Rest in Zeichenreihenfolge: hinten zuerst
        for (String id : ordered()) if (!"clock".equals(id)) chip(id, name(id));
        Chip add = (Chip) a.getLayoutInflater().inflate(R.layout.chip_action, bar, false);
        add.setText("Element");
        add.setCheckable(false);
        add.setContentDescription("Element hinzufügen: Uhr, Wetter, Widgets");
        add.setOnClickListener(v -> showGallery());
        bar.addView(add);

        if (selected != null && !isPresent(selected)) selected = null;
        if (selected == null) {
            if (hasClock) selected = "clock";
            else if (hasWidget) selected = "widget";
            else if (!settings.widgetIds().isEmpty()) selected = settings.widgetIds().get(0);
        }
        select(selected);
    }

    private void chip(String id, String name) {
        Chip c = a.filterChip(bar, name);
        c.setTag(id);
        // ausgeblendet (Auge): bleibt in der Leiste, aber blass
        if (!settings.b(id, "enabled", true)) {
            c.setAlpha(0.5f);
            c.setContentDescription(name + ", ausgeblendet");
        }
    }

    /** Uhr und Datum & Wetter werden beim Entfernen nur als nicht vorhanden markiert. */
    boolean isPresent(String id) {
        if ("clock".equals(id) || "widget".equals(id)) return settings.b(id, "present", true);
        return settings.widgetIds().contains(id);
    }

    String name(String id) {
        if ("clock".equals(id)) return "Uhr";
        if ("widget".equals(id)) return "Datum & Wetter";
        return InfoWidgets.name(settings.s(id, "type", "text"));
    }

    /** Elemente in Zeichenreihenfolge (hinten zuerst), wie der Renderer; die Uhr ohne Platz ganz hinten. */
    List<String> ordered() {
        List<String> base = new java.util.ArrayList<>();
        boolean clock = settings.b("clock", "present", true);
        if (settings.b("widget", "present", true)) base.add("widget");
        base.addAll(settings.widgetIds());
        List<String> out = new java.util.ArrayList<>();
        for (String id : settings.order()) {
            if (base.remove(id) || ("clock".equals(id) && clock && !out.contains(id))) out.add(id);
        }
        out.addAll(base);
        if (clock && !out.contains("clock")) out.add(0, "clock");
        return out;
    }

    /** Blatt „Reihenfolge“: Elemente per Ziehen sortieren, oben = ganz vorn. */
    void showOrder() {
        List<String> front = ordered();
        java.util.Collections.reverse(front);
        BottomSheetDialog d = new BottomSheetDialog(a);
        a.glassify(d);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = a.dp(20);
        root.setPadding(pad, a.dp(8), pad, a.dp(28));
        root.addView(SheetKit.heading(a, "Reihenfolge"));
        root.addView(SheetKit.help(a, "Am Griff ziehen. Oben liegt ganz vorn. Ganz unten liegt die Uhr hinter dem Motiv, weiter oben vor ihm wie ein Widget."));
        RecyclerView list = new RecyclerView(a);
        list.setLayoutManager(new LinearLayoutManager(a));
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int type) {
                LinearLayout row = new LinearLayout(a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(a.dp(52));
                android.widget.ImageView grip = new android.widget.ImageView(a);
                grip.setImageResource(R.drawable.ic_drag);
                grip.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFAAAAAA));
                row.addView(grip, new LinearLayout.LayoutParams(a.dp(24), a.dp(24)));
                TextView t = new TextView(a);
                t.setTextAppearance(R.style.TextAppearance_Wetter_BodyLarge);
                t.setPadding(a.dp(14), 0, 0, 0);
                row.addView(t);
                row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerView.ViewHolder(row) {
                };
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
                ((TextView) ((LinearLayout) h.itemView).getChildAt(1)).setText((pos + 1) + ".  " + name(front.get(pos)));
            }

            @Override
            public int getItemCount() {
                return front.size();
            }
        };
        list.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder from, RecyclerView.ViewHolder to) {
                int f = from.getAdapterPosition();
                int t = to.getAdapterPosition();
                front.add(t, front.remove(f));
                adapter.notifyItemMoved(f, t);
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder h, int dir) {
            }

            @Override
            public void clearView(RecyclerView rv, RecyclerView.ViewHolder h) {
                super.clearView(rv, h);
                adapter.notifyDataSetChanged(); // Nummern neu
                List<String> back = new java.util.ArrayList<>(front);
                java.util.Collections.reverse(back);
                settings.setOrder(back);
                a.changed();
                buildChips();
            }
        }).attachToRecyclerView(list);
        root.addView(list);
        if (front.isEmpty()) root.addView(SheetKit.help(a, "Noch keine Elemente im Bild."));
        d.setContentView(root);
        d.show();
    }

    /** Panel eines Info-Widgets (bei Bedarf aufbauen). */
    TextPanel panel(String id) {
        TextPanel p = panels.get(id);
        if (p != null) return p;
        String type = settings.s(id, "type", "text");
        View v = a.getLayoutInflater().inflate(R.layout.panel_text, host, false);
        TextPanel.Spec spec = new TextPanel.Spec();
        spec.section = id;
        spec.noun = "das Widget";
        spec.defaultFont = "sans-serif-medium";
        spec.defaultColor = settings.s("clock", "color", "auto");
        spec.sizeFrom = 0;
        spec.sizeTo = 100;
        spec.sizeStep = 0.1f;
        spec.sizeDefault = (float) InfoWidgets.defaultSize(type);
        spec.layerSection = id;
        spec.layerKey = "layerThreshold";
        spec.currentLayer = () -> a.preview().autoThreshold(id);
        spec.widget = true;
        spec.linkDefault = true;
        spec.content = optionsView(id, type);
        p = new TextPanel(a, settings, v, spec, highlight);
        p.setPhotoColors(photo);
        host.addView(v);
        panels.put(id, p);
        views.put(id, v);
        return p;
    }

    /** Alle Info-Panels ausblenden außer dem gewählten (null = alle aus). */
    void showOnly(String id) {
        if (id != null && isInfo(id)) panel(id);
        for (Map.Entry<String, View> e : views.entrySet()) e.getValue().setVisibility(e.getKey().equals(id) ? View.VISIBLE : View.GONE);
    }

    void onSceneLoaded() {
        for (TextPanel p : panels.values()) p.onSceneLoaded();
    }

    void setPhotoColors(int[] c) {
        photo = c;
        for (TextPanel p : panels.values()) p.setPhotoColors(c);
    }

    void refreshFonts() {
        for (TextPanel p : panels.values()) p.buildFontChips();
    }

    /** Baut alle Chips und Panels für den neu gewählten Modus frisch auf. */
    void rebuildForMode() {
        host.removeAllViews();
        views.clear();
        panels.clear();
        buildChips();
    }

    private void dropPanel(String id) {
        View v = views.remove(id);
        if (v != null) host.removeView(v);
        panels.remove(id);
    }

    // ---------- Hinzufügen / Entfernen ----------

    void addOrEnable(String type) {
        // gibt es Uhr bzw. Datum & Wetter schon, kommt ein weiteres als Widget dazu
        boolean clockType = type.startsWith("clock_");
        if ((clockType && isPresent("clock")) || ("widget_weather".equals(type) && isPresent("widget"))) {
            add(type);
            return;
        }
        if (clockType) {
            String style = type.substring("clock_".length());
            settings.put("clock", "present", true);
            settings.put("clock", "enabled", true);
            settings.put("clock", "style", style);
            a.changed();
            selected = "clock";
            buildChips();
            select("clock");
            return;
        }
        if ("widget_weather".equals(type)) {
            settings.put("widget", "present", true);
            settings.put("widget", "enabled", true);
            a.changed();
            selected = "widget";
            buildChips();
            select("widget");
            return;
        }
        add(type);
    }

    void add(String type) {
        add(type, null);
    }

    /**
     * Neues Widget im Stil von from (null = Uhr, neu gestapelt; sonst Kopie unter from) als Startpunkt, aber nicht
     * gekoppelt (der Schalter lässt sich bei Bedarf einschalten).
     */
    private void add(String type, String from) {
        List<String> ids = settings.widgetIds();
        String id = "w" + System.currentTimeMillis();
        settings.put(id, "type", type);
        settings.put(id, "enabled", true);
        String style = from == null ? "clock" : from;
        // Datum & Wetter kann an der Uhr hängen, dann kommt sein Stil von dort
        settings.copyKeys(settings.b(style, "linkClock", false) ? "clock" : style, id, WallpaperRenderer.LINK_KEYS);
        settings.put(id, "linkClock", false);
        if (from == null) {
            settings.put(id, "xPct", 50);
            settings.put(id, "yPct", 50 + 7 * (ids.size() % 6));
        } else {
            settings.copyKeys(from, id, new String[]{"align", "uppercase", "rotation"});
            placeBelow(id, from);
        }
        ids.add(id);
        settings.setWidgetIds(ids);
        a.changed();
        selected = id;
        buildChips();
        if ("event".equals(type) && !DeviceInfo.calendarGranted(a)) a.requestCalendar();
    }

    /** Kopie mit allen Einstellungen, etwas unter dem Original; Uhr und Datum & Wetter werden ein Widget im selben Stil. */
    void duplicate(String id) {
        if (id == null) return;
        if (!isInfo(id)) {
            add("clock".equals(id) ? "clock_" + settings.s("clock", "style", "digital") : "widget_weather", id);
            return;
        }
        String copy = "w" + System.currentTimeMillis();
        try {
            JSONObject src = new JSONObject(settings.sec(id).toString()); // tiefe Kopie, Listen nicht teilen
            for (java.util.Iterator<String> it = src.keys(); it.hasNext(); ) {
                String k = it.next();
                settings.put(copy, k, src.get(k));
            }
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
        settings.put(copy, "enabled", true); // auch die Kopie eines ausgeblendeten Widgets ist sichtbar
        placeBelow(copy, id);
        List<String> ids = settings.widgetIds();
        ids.add(copy);
        settings.setWidgetIds(ids);
        a.changed();
        selected = copy;
        buildChips();
    }

    /** Gleiche Spalte wie from, eine Zeile darunter (am unteren Rand darüber). */
    private void placeBelow(String id, String from) {
        float[] p = a.preview().position(from);
        if (p == null) p = new float[]{(float) settings.d(from, "xPct", 50), (float) settings.d(from, "yPct", 55)};
        settings.put(id, "xPct", Math.round(p[0] * 10) / 10.0);
        settings.put(id, "yPct", Math.round((p[1] > 85 ? p[1] - 7 : p[1] + 7) * 10) / 10.0);
    }

    /** Andere Widget-Art, Stil und Platz bleiben. */
    private void changeType(String id) {
        showGallery("Widget wechseln", type -> {
            settings.put(id, "type", type);
            a.changed();
            dropPanel(id);
            buildChips();
            if ("event".equals(type) && !DeviceInfo.calendarGranted(a)) a.requestCalendar();
        });
    }

    void confirmRemove(String id) {
        if (id == null) return;
        String name = name(id);
        new MaterialAlertDialogBuilder(a)
                .setTitle(name + " entfernen?")
                .setMessage("Möchtest du " + name + " wirklich entfernen?")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Entfernen", (d, w) -> remove(id))
                .show();
    }

    void remove(String id) {
        if (id == null) return;
        String name = name(id);
        if ("clock".equals(id)) {
            settings.put("clock", "present", false);
            selected = null;
            buildChips();
            a.changed();
        } else if ("widget".equals(id)) {
            settings.put("widget", "present", false);
            selected = null;
            buildChips();
            a.changed();
        } else {
            List<String> ids = settings.widgetIds();
            ids.remove(id);
            settings.setWidgetIds(ids);
            settings.removeSection(id);
            dropPanel(id);
            selected = null;
            buildChips();
            a.changed();
        }
        Snackbar.make(a.findViewById(R.id.root), name + " entfernt", Snackbar.LENGTH_LONG)
                .setAction("Rückgängig", v -> {
                    if ("clock".equals(id)) {
                        settings.put("clock", "present", true);
                    } else if ("widget".equals(id)) {
                        settings.put("widget", "present", true);
                    } else {
                        List<String> ids = settings.widgetIds();
                        if (!ids.contains(id)) ids.add(id);
                        settings.setWidgetIds(ids);
                    }
                    a.changed();
                    selected = id;
                    buildChips();
                })
                .show();
    }

    // ---------- Werkzeug „Inhalt“ eines Info-Widgets ----------

    private View optionsView(String id, String type) {
        LinearLayout o = new LinearLayout(a);
        o.setOrientation(LinearLayout.VERTICAL);
        MaterialSwitch link = new MaterialSwitch(a);
        link.setText("Stil der Uhr übernehmen");
        link.setChecked(settings.b(id, "linkClock", true));
        link.setOnCheckedChangeListener((b, on) -> {
            // beim Lösen den Stil der Uhr als Startpunkt übernehmen, dann ist alles einzeln einstellbar
            if (!on) settings.copyKeys("clock", id, WallpaperRenderer.LINK_KEYS);
            settings.put(id, "linkClock", on);
            a.changed();
            dropPanel(id); // Regler mit den übernommenen Werten neu aufbauen
            select(id);
        });
        o.addView(link);
        if ("event".equals(type) && !DeviceInfo.calendarGranted(a)) {
            MaterialButton grant = new MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            grant.setText("Kalender freigeben");
            grant.setOnClickListener(v -> a.requestCalendar());
            o.addView(grant);
        }
        for (InfoWidgets.Opt opt : InfoWidgets.options(type)) o.addView(control(id, opt));
        MaterialButton swap = new MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        swap.setText("Anderes Widget, gleicher Stil");
        swap.setOnClickListener(v -> changeType(id));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = a.dp(10);
        o.addView(swap, slp);
        MaterialButton rm = new MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        rm.setText("Widget entfernen");
        rm.setOnClickListener(v -> confirmRemove(id));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = a.dp(10);
        o.addView(rm, lp);
        return o;
    }

    private View control(String id, InfoWidgets.Opt opt) {
        switch (opt.kind) {
            case "switch": {
                MaterialSwitch s = new MaterialSwitch(a);
                s.setText(opt.label);
                s.setChecked(settings.b(id, opt.key, Boolean.parseBoolean(opt.def)));
                s.setOnCheckedChangeListener((b, on) -> put(id, opt.key, on));
                return s;
            }
            case "choice": {
                LinearLayout box = labeled(opt.label);
                android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(a);
                scroll.setHorizontalScrollBarEnabled(false);
                ChipGroup g = new ChipGroup(a);
                g.setSingleLine(true);
                g.setSingleSelection(true);
                g.setSelectionRequired(true);
                String cur = settings.s(id, opt.key, opt.def);
                for (String[] ch : opt.choices) {
                    Chip c = a.filterChip(g, ch[1]);
                    c.setTag(ch[0]);
                    if (ch[0].equals(cur)) c.setChecked(true);
                }
                g.setOnCheckedStateChangeListener((gr, ids) -> {
                    Chip c = gr.findViewById(gr.getCheckedChipId());
                    if (c != null) put(id, opt.key, c.getTag());
                });
                scroll.addView(g);
                box.addView(scroll);
                return box;
            }
            case "slider": {
                LinearLayout line = new LinearLayout(a);
                line.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = new TextView(a);
                name.setText(opt.label);
                name.setTextAppearance(R.style.TextAppearance_Wetter_LabelLarge);
                name.setMinWidth(a.dp(110));
                Slider s = new Slider(a);
                s.setValueFrom(opt.min);
                s.setValueTo(opt.max);
                s.setStepSize(1);
                s.setTickVisible(false);
                s.setContentDescription(opt.label);
                s.setValue(Math.max(opt.min, Math.min(opt.max, (int) Math.round(settings.d(id, opt.key, Double.parseDouble(opt.def))))));
                s.setLabelFormatter(v -> Math.round(v) + (opt.unit.isEmpty() ? "" : " " + opt.unit));
                s.addOnChangeListener((sl, v, fromUser) -> {
                    if (fromUser) put(id, opt.key, Math.round(v));
                });
                line.addView(name);
                line.addView(s, new LinearLayout.LayoutParams(0, -2, 1));
                line.addView(SliderInput.value(s, null));
                return line;
            }
            default: { // text
                LinearLayout box = labeled(opt.label);
                EditText e = new EditText(a);
                e.setText(settings.s(id, opt.key, opt.def));
                e.setMaxLines(3);
                e.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int st, int c, int af) {
                    }

                    public void onTextChanged(CharSequence s, int st, int b, int c) {
                    }

                    public void afterTextChanged(Editable s) {
                        put(id, opt.key, s.toString());
                    }
                });
                box.addView(e);
                return box;
            }
        }
    }

    private LinearLayout labeled(String label) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextAppearance(R.style.TextAppearance_Wetter_LabelLarge);
        t.setPadding(0, a.dp(8), 0, a.dp(2));
        box.addView(t);
        return box;
    }

    private void put(String id, String key, Object value) {
        settings.put(id, key, value);
        a.changed();
    }

    // ---------- Galerie ----------

    void showGallery() {
        showGallery("Widgets", this::addOrEnable);
    }

    /** Galerie aller Widget-Arten. */
    private void showGallery(String heading, java.util.function.Consumer<String> pick) {
        BottomSheetDialog d = new BottomSheetDialog(a);
        a.glassify(d);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = a.dp(20);
        root.setPadding(pad, a.dp(4), pad, a.dp(28));
        TextView title = new TextView(a);
        title.setText(heading);
        title.setTextAppearance(R.style.TextAppearance_Wetter_TitleLarge);
        title.setTextColor(MaterialColors.getColor(bar, com.google.android.material.R.attr.colorPrimary));
        root.addView(title);
        android.widget.HorizontalScrollView catScroll = new android.widget.HorizontalScrollView(a);
        catScroll.setHorizontalScrollBarEnabled(false);
        ChipGroup cats = new ChipGroup(a);
        cats.setSingleLine(true);
        cats.setSingleSelection(true);
        cats.setSelectionRequired(true);
        catScroll.addView(cats);
        root.addView(catScroll);
        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(a);
        scroll.addView(grid);
        root.addView(scroll);
        String[] groups = new String[InfoWidgets.GROUPS.length + 1];
        groups[0] = "Alle";
        System.arraycopy(InfoWidgets.GROUPS, 0, groups, 1, InfoWidgets.GROUPS.length);
        for (String gname : groups) {
            Chip c = a.filterChip(cats, gname);
            c.setTag(gname);
            if ("Alle".equals(gname)) c.setChecked(true);
        }
        Runnable fill = () -> {
            Chip c = cats.findViewById(cats.getCheckedChipId());
            String group = c == null ? "Alle" : (String) c.getTag();
            grid.removeAllViews();
            LinearLayout row = null;
            int n = 0;
            for (String[] t : InfoWidgets.TYPES) {
                if (!"Alle".equals(group) && !group.equals(t[2])) continue;
                if (n % 2 == 0) {
                    row = new LinearLayout(a);
                    grid.addView(row);
                }
                row.addView(card(t, () -> {
                    d.dismiss();
                    pick.accept(t[0]);
                }), new LinearLayout.LayoutParams(0, -2, 1));
                n++;
            }
            if (row != null && n % 2 == 1) row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
        };
        cats.setOnCheckedStateChangeListener((g, ids) -> fill.run());
        fill.run();
        d.setContentView(root);
        d.show();
    }

    private View card(String[] t, Runnable pick) {
        MaterialCardView card = new MaterialCardView(a);
        card.setRadius(a.dp(15));
        card.setCardBackgroundColor(MaterialColors.getColor(bar, com.google.android.material.R.attr.colorSurfaceContainerHigh));
        card.setStrokeColor(MaterialColors.getColor(bar, com.google.android.material.R.attr.colorOutlineVariant));
        card.setStrokeWidth(a.dp(1));
        card.setContentDescription(t[1] + " hinzufügen");
        card.setOnClickListener(v -> pick.run());
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(new Preview(a, t[0], a.preview().widgetData(), a.dir()), new LinearLayout.LayoutParams(-1, a.dp(86)));
        TextView name = new TextView(a);
        name.setText(t[1]);
        name.setTextAppearance(R.style.TextAppearance_Wetter_LabelLarge);
        name.setPadding(a.dp(10), a.dp(8), a.dp(10), 0);
        TextView desc = new TextView(a);
        desc.setText(t[3]);
        desc.setTextAppearance(R.style.TextAppearance_Wetter_BodySmall);
        desc.setPadding(a.dp(10), a.dp(2), a.dp(10), a.dp(10));
        box.addView(name);
        box.addView(desc);
        card.addView(box);
        FrameLayout wrap = new FrameLayout(a);
        int m = a.dp(4);
        wrap.setPadding(m, m, m, m);
        wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
        return wrap;
    }

    /** Live-Vorschau eines Widget-Typs mit echten Daten auf dunklem Grund, in die Fläche eingepasst. */
    static final class Preview extends View {
        private final String type;
        private final InfoWidgets.Data data;
        private final java.io.File dir;
        private final Paint bg = new Paint();

        Preview(Context ctx, String type, InfoWidgets.Data data, java.io.File dir) {
            super(ctx);
            this.type = type;
            this.data = data != null ? data : new InfoWidgets.Data();
            this.dir = dir;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            bg.setShader(new LinearGradient(0, 0, 0, h, 0xFF2B3550, 0xFF3A3F52, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, bg);
            TextLayer layer = new TextLayer(TextLayer.WIDGET_SHADOW);
            JSONObject cfg = new JSONObject();
            layer.apply(cfg, dir, "sans-serif-medium", 100, w);
            float s = h * 0.24f;
            layer.paint.setTextSize(s);
            layer.prepare(0.1f, 0); // dunkler Grund → helle Schrift

            if ("clock_digital".equals(type)) {
                String text = new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(data.now));
                float tw = layer.paint.measureText(text);
                RectF box = new RectF((w - tw) / 2f, (h - s) / 2f, (w + tw) / 2f, (h + s) / 2f);
                layer.draw(c, box, p -> c.drawText(text, (w - tw) / 2f, h / 2f + s * 0.35f, p));
                return;
            }
            if ("clock_vertical".equals(type)) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(data.now);
                String hStr = String.format(Locale.GERMANY, "%02d", cal.get(Calendar.HOUR_OF_DAY));
                String mStr = String.format(Locale.GERMANY, "%02d", cal.get(Calendar.MINUTE));
                float sz = h * 0.30f;
                layer.paint.setTextSize(sz);
                float tw1 = layer.paint.measureText(hStr);
                float tw2 = layer.paint.measureText(mStr);
                float mw = Math.max(tw1, tw2);
                RectF box = new RectF((w - mw) / 2f, h * 0.12f, (w + mw) / 2f, h * 0.88f);
                layer.draw(c, box, p -> {
                    c.drawText(hStr, (w - tw1) / 2f, h * 0.42f, p);
                    c.drawText(mStr, (w - tw2) / 2f, h * 0.78f, p);
                });
                return;
            }
            if ("clock_words".equals(type)) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(data.now);
                String[] lines = ClockRenderer.words(cal, false, false);
                float sz = h * 0.19f;
                layer.paint.setTextSize(sz);
                float w1 = layer.paint.measureText(lines[0]);
                float w2 = layer.paint.measureText(lines[1]);
                float mw = Math.max(w1, w2);
                RectF box = new RectF((w - mw) / 2f, h * 0.15f, (w + mw) / 2f, h * 0.85f);
                layer.draw(c, box, p -> {
                    c.drawText(lines[0], (w - w1) / 2f, h * 0.42f, p);
                    c.drawText(lines[1], (w - w2) / 2f, h * 0.74f, p);
                });
                return;
            }
            if ("clock_analog".equals(type)) {
                float r = h * 0.36f;
                float cx = w / 2f;
                float cy = h / 2f;
                RectF box = new RectF(cx - r, cy - r, cx + r, cy + r);
                layer.draw(c, box, p -> {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    for (int i = 0; i < 12; i++) {
                        double a = Math.toRadians(i * 30);
                        boolean maj = (i % 3 == 0);
                        p.setStrokeWidth(maj ? 2.5f : 1.5f);
                        c.drawLine((float) (cx + Math.sin(a) * (maj ? r * 0.72f : r * 0.82f)),
                                (float) (cy - Math.cos(a) * (maj ? r * 0.72f : r * 0.82f)),
                                (float) (cx + Math.sin(a) * r * 0.95f),
                                (float) (cy - Math.cos(a) * r * 0.95f), p);
                    }
                    p.setStrokeWidth(3f);
                    double ha = Math.toRadians(305);
                    c.drawLine(cx, cy, (float) (cx + Math.sin(ha) * r * 0.55f), (float) (cy - Math.cos(ha) * r * 0.55f), p);
                    p.setStrokeWidth(2f);
                    double ma = Math.toRadians(60);
                    c.drawLine(cx, cy, (float) (cx + Math.sin(ma) * r * 0.82f), (float) (cy - Math.cos(ma) * r * 0.82f), p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy, r * 0.08f, p);
                });
                return;
            }
            if ("widget_weather".equals(type)) {
                String dStr = new SimpleDateFormat("EEE, d. MMM", Locale.GERMANY).format(new Date(data.now));
                String text = dStr + "  ⛅  " + (data.wx != null ? Math.round(data.wx.temperature) + "°" : "18°");
                float tw = layer.paint.measureText(text);
                RectF box = new RectF((w - tw) / 2f, (h - s) / 2f, (w + tw) / 2f, (h + s) / 2f);
                layer.draw(c, box, p -> c.drawText(text, (w - tw) / 2f, h / 2f + s * 0.35f, p));
                return;
            }

            WidgetParts.Content content = InfoWidgets.build(type, cfg, data);
            if (content.isEmpty()) return;
            float[] m = WidgetParts.measure(content, layer.paint, s);
            float fit = Math.min(1f, Math.min(w * 0.88f / Math.max(1, m[0]), h * 0.8f / Math.max(1, m[1])));
            float size = s * fit;
            layer.paint.setTextSize(size);
            float[] m2 = WidgetParts.measure(content, layer.paint, size);
            RectF box = new RectF((w - m2[0]) / 2, (h - m2[1]) / 2, (w + m2[0]) / 2, (h + m2[1]) / 2);
            layer.draw(c, box, p -> WidgetParts.draw(c, content, p, size, box.left, box.top, "center"));
        }
    }
}
