package de.seelig.wetterwallpaper;

import android.Manifest;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hauptbildschirm: Live-Vorschau (Elemente ziehen, mit zwei Fingern skalieren). Oben Modus-Pille, Rückgängig und
 * Wiederholen, Einstellungen (☰) und Speichern. Unten die Regler-Karte mit den Elementen, genau einem Regler und den
 * Werkzeugen der gewählten Gruppe, darunter die fünf Gruppen Text, Farbe, Effekte, Ebene und Inhalt.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "WetterWallpaper";

    /** Gruppen unten und ihre Werkzeuge; neue Werkzeuge kommen einfach als weiterer Eintrag in eine Gruppe. */
    private static final String[][] GROUPS = {{"text", "Text"}, {"color", "Farbe"}, {"fx", "Effekte"},
            {"place", "Ebene"}, {"content", "Inhalt"}};
    private static final int[] GROUP_ICONS = {R.drawable.ic_text, R.drawable.ic_palette, R.drawable.ic_sparkle,
            R.drawable.ic_layers, R.drawable.ic_grid};
    private static final String[][][] GROUP_TOOLS = {
            {{"font", "Schrift"}, {"size", "Größe"}, {"spacing", "Abstand"}, {"align", "Ausrichtung"}, {"rotate", "Drehung"}},
            {{"fill", "Füllung"}, {"outline", "Kontur"}},
            {{"shadow", "Effekt"}, {"fade", "Ausblenden"}, {"opacity", "Deckkraft"}},
            {{"layer", "Ebene"}, {"order", "Reihenfolge"}},
            {{"content", "Inhalt"}}};

    /** Welche Schlüssel ein Werkzeug schreibt (für den Punkt „weicht vom Tag ab“ und „Wie Tag“). */
    private static final Map<String, Set<String>> TOOL_KEYS = Map.ofEntries(
            Map.entry("font", Set.of("font", "systemFont", "weight", "uppercase")),
            Map.entry("size", Set.of("sizePct")),
            Map.entry("spacing", Set.of("letterSpacing")),
            Map.entry("align", Set.of("align")),
            Map.entry("rotate", Set.of("rotation")),
            Map.entry("fill", Set.of("color", "colorNight", "colorDark", "colorLight", "fill", "gradientColor", "gradientColors",
                    "gradientStops", "gradientSoft", "gradientAngle", "glassFrost", "glassBlur", "glassEdge",
                    "photoBright", "hollowWidth", "gradientOverlay", "gradientMix")),
            Map.entry("outline", Set.of("outline", "outlineColor", "outlineWidth", "outlineFill", "outlineGradientColors",
                    "outlineGradientStops", "outlineGradientSoft", "outlineGradientAngle", "outlineGlassFrost", "outlineGlassBlur",
                    "outlinePhotoBright")),
            Map.entry("shadow", Set.of("shadow", "shadowStyle", "shadowColor", "shadowBlur", "shadowDistance", "shadowAngle",
                    "shadowStrength", "glowColor", "glowSize", "glowStrength", "neonColor", "neonSize", "neonStrength",
                    "longColor", "longLength", "longAngle", "longStrength", "retroColor1", "retroColor2", "retroDistance",
                    "retroAngle")),
            Map.entry("fade", Set.of("fade", "fadeStart", "fadeLength", "fadeAngle")),
            Map.entry("opacity", Set.of("opacity")));
    /** Gehören zu keinem Werkzeug (Position, Sichtbarkeit, Typ). */
    private static final Set<String> NOT_CONTENT = Set.of("xPct", "yPct", "enabled", "present", "type", "layerThreshold");

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveTask = this::saveNow;
    private final Runnable commitTask = this::commit;
    /** Rückgängig/Wiederholen: ganze Stände von config.json, Änderungen kurz hintereinander zählen als einer. */
    private final ArrayDeque<String> undo = new ArrayDeque<>();
    private final ArrayDeque<String> redo = new ArrayDeque<>();
    private String committed;
    private File dir;
    private Settings settings;
    private SharedPreferences ui;
    private PreviewView preview;
    private View root;
    private MaterialButton statusButton;
    private MaterialButton modeButton;
    private MaterialButton undoButton;
    private MaterialButton redoButton;
    private View editor;
    /** Leiste unten eingeklappt: die ganze Vorschau ist frei, z. B. zum Verschieben an den unteren Rand. */
    private boolean collapsed;
    /** Leiste oben (gewähltes Element liegt unten) bzw. wohin sie gerade wechselt. */
    private boolean editorTop, wantTop;
    private Insets bars = Insets.NONE;
    private int editorPad;
    private static final int SLIDE_MS = 220;
    private String tool = "size";
    private String group = "text";
    private final View[] groupItems = new View[GROUPS.length];
    private final View[] groupDots = new View[GROUPS.length];
    private View orderView;
    private WidgetManager widgets;
    private View emptyState;
    private View dragHint;
    private TextPanel clockPanel;
    private TextPanel widgetPanel;
    /** Panel, das "Schrift hinzufügen" ausgelöst hat. */
    private TextPanel fontTarget;
    /** Für welchen Modus das gerade gewählte Foto ist ("day" = gemeinsames Foto). */
    private String photoTarget = "day";
    private PresetPanel importTarget;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) loadPicked(uri);
            });
    private final ActivityResultLauncher<String[]> pickFont = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) importFont(uri);
            });
    private final ActivityResultLauncher<String[]> pickPreset = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) importPresetFile(uri);
            });
    private final ActivityResultLauncher<Intent> crop = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    onCropped(r.getData().getFloatExtra(CropActivity.EXTRA_SECONDS, 0));
                }
            });
    private final ActivityResultLauncher<String> calendarPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) changed();
            });
    private final ActivityResultLauncher<String> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        dir = getExternalFilesDir(null);
        if (dir == null) {
            Snackbar.make(findViewById(R.id.root), "Der App-Speicher ist gerade nicht verfügbar.", Snackbar.LENGTH_INDEFINITE).show();
            return;
        }
        settings = new Settings(dir);
        ui = getSharedPreferences("ui", MODE_PRIVATE);

        root = findViewById(R.id.root);
        statusButton = findViewById(R.id.statusButton);
        modeButton = findViewById(R.id.modeButton);
        modeButton.setOnClickListener(v -> DayNight.showDialog(this, settings, this::onModeSwitched));
        undoButton = findViewById(R.id.undoButton);
        redoButton = findViewById(R.id.redoButton);
        undoButton.setOnClickListener(v -> undo());
        redoButton.setOnClickListener(v -> redo());
        findViewById(R.id.menuButton).setOnClickListener(v -> new SettingsSheet(this, settings).show());
        editor = findViewById(R.id.editor);
        emptyState = findViewById(R.id.emptyState);
        dragHint = findViewById(R.id.dragHint);
        preview = findViewById(R.id.preview);
        capToolCard();
        int[] glassIds = {R.id.toolCard, R.id.toolBar, R.id.modeGlass, R.id.historyGlass, R.id.menuGlass};
        for (int id : glassIds) ((GlassCard) findViewById(id)).setSource(preview);
        preview.setOnBackdrop(() -> {
            for (int id : glassIds) findViewById(id).invalidate();
        });

        Rect screen = getWindowManager().getMaximumWindowMetrics().getBounds();
        preview.setDirectory(dir);
        preview.setScreenAspect(screen.width() / (float) screen.height());
        preview.setConfig(settings.snapshot());
        preview.setListener(new PreviewView.Listener() {
            @Override
            public void onMoved(String id, float xPct, float yPct) {
                onElementMoved(id, xPct, yPct);
            }

            @Override
            public void onSelected(String id) {
                if (widgets != null) widgets.select(id);
                placeEditorFor(id);
            }

            @Override
            public void onEmptyTap() {
                // Leiste unten aus- und einblenden, um das ganze Bild zu sehen
                setCollapsed(!collapsed);
            }

            @Override
            public void onResized(String id, float sizePct, float xPct, float yPct, float rotation) {
                settings.put(id, "sizePct", Math.round(sizePct * 10) / 10.0);
                settings.put(id, "xPct", Math.round(xPct * 10) / 10.0);
                settings.put(id, "yPct", Math.round(yPct * 10) / 10.0);
                settings.put(id, "rotation", Math.round(rotation));
                hideDragHint();
                changed();
                refreshPanels();
            }
        });

        findViewById(R.id.deleteButton).setOnClickListener(v -> {
            if (widgets != null) widgets.confirmRemove(widgets.selected());
        });
        findViewById(R.id.copyButton).setOnClickListener(v -> {
            if (widgets != null) widgets.duplicate(widgets.selected());
        });
        findViewById(R.id.visibleButton).setOnClickListener(v -> {
            String sel = widgets != null ? widgets.selected() : null;
            if (sel == null) return;
            settings.put(sel, "enabled", !settings.b(sel, "enabled", true));
            changed();
            widgets.buildChips();
        });
        findViewById(R.id.resetDayButton).setOnClickListener(v -> resetTool());

        applyInsets();
        setupTextPanels();
        setupEditor();
        findViewById(R.id.emptyPick).setOnClickListener(v -> launchPicker("day"));
        updateModeButton();
        preview.setScreen(settings.screen());
        DayNight.applyPreviewForMode(this, settings, settings.activeMode());
        committed = settings.text();

        if (!LocationHelper.granted(this) && !ui.getBoolean("askedLocation", false)) {
            ui.edit().putBoolean("askedLocation", true).apply();
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        handleShare(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settings == null) return;
        updateStatus();
        updateEmptyState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler.hasCallbacks(saveTask)) {
            handler.removeCallbacks(saveTask);
            saveNow();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShare(intent);
    }

    // ---------- Layout ----------

    /** Vollbild: Leisten oben und unten bekommen Abstand zu Statusleiste und Navigationsleiste. */
    private void applyInsets() {
        View top = findViewById(R.id.topBar);
        int topPad = top.getPaddingTop();
        editorPad = editor.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            top.setPadding(top.getPaddingLeft(), topPad + bars.top, top.getPaddingRight(), top.getPaddingBottom());
            padEditor();
            return insets;
        });
    }

    /** Unten: Abstand zur Navigationsleiste. Oben: unter der oberen Leiste. */
    private void padEditor() {
        int top = editorTop ? findViewById(R.id.topBar).getBottom() : 0;
        editor.setPadding(editor.getPaddingLeft(), top, editor.getPaddingRight(), editorTop ? 0 : editorPad + bars.bottom);
    }

    /** Werkzeugkarte samt Tabs höchstens etwa halb so hoch wie der Bildschirm, der Rest scrollt. */
    private void capToolCard() {
        ViewGroup card = findViewById(R.id.toolCard);
        View tabs = findViewById(R.id.toolBar);
        View content = card.getChildAt(0);
        card.removeView(content);
        ScrollView scroll = new ScrollView(this) {
            @Override
            protected void onMeasure(int w, int h) {
                int max = getResources().getDisplayMetrics().heightPixels / 2 - Math.max(tabs.getHeight(), dp(64));
                if (MeasureSpec.getMode(h) != MeasureSpec.UNSPECIFIED) max = Math.min(max, MeasureSpec.getSize(h));
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(24));
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        card.addView(scroll, new FrameLayout.LayoutParams(-1, -2));
    }

    // ---------- Werkzeuge: Gruppen unten, Werkzeuge der Gruppe als Chips ----------

    private void setupEditor() {
        LinearLayout row = findViewById(R.id.groupRow);
        ColorStateList fg = getColorStateList(R.color.group_item_fg);
        for (int i = 0; i < GROUPS.length; i++) {
            int index = i;
            FrameLayout item = new FrameLayout(this);
            item.setBackgroundResource(R.drawable.bg_group_item);
            item.setContentDescription("Werkzeuggruppe " + GROUPS[i][1]);
            TextView label = new TextView(this);
            label.setText(GROUPS[i][1]);
            label.setGravity(Gravity.CENTER);
            label.setTextAppearance(R.style.TextAppearance_Wetter_LabelMedium);
            label.setTextColor(fg);
            label.setDuplicateParentStateEnabled(true);
            label.setCompoundDrawablesWithIntrinsicBounds(0, GROUP_ICONS[i], 0, 0);
            label.setCompoundDrawableTintList(fg);
            label.setCompoundDrawablePadding(dp(2));
            label.setPadding(0, dp(7), 0, dp(6));
            item.addView(label, new FrameLayout.LayoutParams(-1, -2));
            ImageView dot = new ImageView(this);
            dot.setImageResource(R.drawable.ic_dot);
            dot.setVisibility(View.GONE);
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(7), dp(7), Gravity.TOP | Gravity.END);
            dlp.topMargin = dp(6);
            dlp.rightMargin = dp(14);
            item.addView(dot, dlp);
            item.setOnClickListener(v -> {
                // aktiven Tab nochmal antippen: abwählen, Leiste einklappen
                if (v.isSelected()) {
                    setCollapsed(true);
                    return;
                }
                group = GROUPS[index][0];
                tool = toolsOf(group, selected())[0][0];
                showEditor();
            });
            groupItems[i] = item;
            groupDots[i] = dot;
            row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        ChipGroup chips = findViewById(R.id.toolChips);
        chips.setOnCheckedStateChangeListener((g, ids) -> {
            Chip c = g.findViewById(g.getCheckedChipId());
            if (c == null || c.getTag() == null || c.getTag().equals(tool)) return;
            tool = (String) c.getTag();
            showEditor();
        });
        orderView = buildOrderView();
        int highlight = (MaterialColors.getColor(root, com.google.android.material.R.attr.colorPrimary) & 0xFFFFFF) | 0x99000000;
        widgets = new WidgetManager(this, settings, findViewById(R.id.elementChips), findViewById(R.id.panelExtra), highlight, this::showEditor);
        showEditor(); // im Konstruktor war widgets noch nicht gesetzt
    }

    /** Werkzeug „Reihenfolge“: kurzer Hinweis und der Knopf zum Sortieren. */
    private View buildOrderView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText("Was liegt vorn, wenn sich Uhr und Widgets überlappen?");
        t.setTextAppearance(R.style.TextAppearance_Wetter_BodyMedium);
        box.addView(t);
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText("Reihenfolge ändern");
        b.setIconResource(R.drawable.ic_drag);
        b.setOnClickListener(v -> widgets.showOrder());
        box.addView(b);
        box.setVisibility(View.GONE);
        ViewGroup parent = (ViewGroup) findViewById(R.id.panelExtra).getParent();
        parent.addView(box, parent.indexOfChild(findViewById(R.id.panelExtra)) + 1);
        return box;
    }

    private void setCollapsed(boolean c) {
        collapsed = c;
        if (!c) layoutEditor(); // Wechsel oben/unten, der während des Einklappens anstand
        slideEditor(!c, null);
    }

    /** Leiste weich ein- oder ausblenden: gleitet zu ihrem Rand hinaus bzw. von dort herein. */
    private void slideEditor(boolean in, Runnable then) {
        float off = dp(48) * (editorTop ? -1 : 1);
        editor.animate().cancel();
        if (in && editor.getVisibility() != View.VISIBLE) {
            editor.setAlpha(0);
            editor.setTranslationY(off);
            editor.setVisibility(View.VISIBLE);
        }
        editor.animate()
                .alpha(in ? 1 : 0)
                .translationY(in ? 0 : off)
                .setDuration(SLIDE_MS)
                .setInterpolator(in ? new DecelerateInterpolator() : new AccelerateInterpolator())
                .setUpdateListener(a -> { // Glas zeigt sonst den alten Ausschnitt
                    findViewById(R.id.toolCard).invalidate();
                    findViewById(R.id.toolBar).invalidate();
                })
                .withEndAction(() -> {
                    if (!in) editor.setVisibility(View.GONE);
                    if (then != null) then.run();
                });
    }

    /** Leiste auf die andere Bildhälfte als das Element legen; Schwelle mit Spiel, damit sie nicht flattert. */
    private void placeEditorFor(String id) {
        float[] p = id != null ? preview.position(id) : null;
        if (p == null) return;
        boolean top = wantTop ? p[1] > 45 : p[1] > 55;
        if (top == wantTop) return;
        wantTop = top;
        if (collapsed || editor.getVisibility() != View.VISIBLE) {
            layoutEditor();
            return;
        }
        slideEditor(false, () -> {
            layoutEditor();
            if (!collapsed) slideEditor(true, null);
        });
    }

    /** Leiste an den oberen bzw. unteren Rand; die Tabs liegen jeweils am Rand, die Karte zur Mitte hin. */
    private void layoutEditor() {
        if (editorTop == wantTop) return;
        editorTop = wantTop;
        ViewGroup box = (ViewGroup) editor;
        View[] kids = new View[box.getChildCount()];
        for (int i = 0; i < kids.length; i++) kids[i] = box.getChildAt(i);
        box.removeAllViews();
        for (int i = kids.length - 1; i >= 0; i--) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) kids[i].getLayoutParams();
            int t = lp.topMargin;
            lp.topMargin = lp.bottomMargin;
            lp.bottomMargin = t;
            box.addView(kids[i]);
        }
        ((FrameLayout.LayoutParams) editor.getLayoutParams()).gravity = editorTop ? Gravity.TOP : Gravity.BOTTOM;
        padEditor();
        editor.requestLayout();
    }

    private String selected() {
        return widgets != null ? widgets.selected() : null;
    }

    /** Werkzeuge einer Gruppe für das gewählte Element. */
    private static String[][] toolsOf(String group, String sel) {
        for (int i = 0; i < GROUPS.length; i++) {
            if (GROUPS[i][0].equals(group)) return GROUP_TOOLS[i];
        }
        return GROUP_TOOLS[0];
    }

    private static String toolName(String tool) {
        for (String[][] g : GROUP_TOOLS) for (String[] t : g) if (t[0].equals(tool)) return t[1];
        return tool;
    }

    /** Panel des gewählten Elements mit genau dem gewählten Werkzeug zeigen, Rahmen in der Vorschau setzen. */
    private void showEditor() {
        if (widgets == null) return;
        String sel = widgets.selected();
        MaterialButton visible = findViewById(R.id.visibleButton);
        View deleteBtn = findViewById(R.id.deleteButton);
        TextView title = findViewById(R.id.toolTitle);
        ChipGroup chips = findViewById(R.id.toolChips);
        if (sel == null) {
            findViewById(R.id.panelClock).setVisibility(View.GONE);
            findViewById(R.id.panelWidget).setVisibility(View.GONE);
            widgets.showOnly(null);
            orderView.setVisibility(View.GONE);
            findViewById(R.id.toolScroll).setVisibility(View.GONE);
            findViewById(R.id.resetDayButton).setVisibility(View.GONE);
            title.setText("Element antippen oder hinzufügen");
            visible.setEnabled(false);
            deleteBtn.setEnabled(false);
            findViewById(R.id.copyButton).setEnabled(false);
            updateGroupBar(null);
            preview.outline(null);
            return;
        }
        visible.setEnabled(true);
        deleteBtn.setEnabled(true);
        findViewById(R.id.copyButton).setEnabled(true);
        boolean on = settings.b(sel, "enabled", true);
        visible.setIconResource(on ? R.drawable.ic_eye : R.drawable.ic_eye_off);
        visible.setContentDescription(on ? "Ausblenden" : "Wieder einblenden");

        String[][] tools = toolsOf(group, sel);
        boolean known = false;
        for (String[] t : tools) known |= t[0].equals(tool);
        if (!known) tool = tools[0][0];

        // Werkzeuge der Gruppe als Chips, mit Punkt, wenn der Wert vom Tag abweicht
        chips.removeAllViews();
        for (String[] t : tools) {
            Chip c = filterChip(chips, t[1]);
            c.setTag(t[0]);
            if (!overriddenKeys(t[0], sel).isEmpty()) {
                c.setChipIconResource(R.drawable.ic_dot);
                c.setChipIconVisible(true);
                c.setContentDescription(t[1] + ", weicht ab");
            }
            if (t[0].equals(tool)) c.setChecked(true);
        }
        findViewById(R.id.toolScroll).setVisibility(tools.length > 1 ? View.VISIBLE : View.GONE);

        boolean clock = "clock".equals(sel);
        boolean order = "order".equals(tool);
        findViewById(R.id.panelClock).setVisibility(clock && !order ? View.VISIBLE : View.GONE);
        findViewById(R.id.panelWidget).setVisibility("widget".equals(sel) && !order ? View.VISIBLE : View.GONE);
        widgets.showOnly(WidgetManager.isInfo(sel) && !order ? sel : null);
        orderView.setVisibility(order ? View.VISIBLE : View.GONE);
        if (!order) {
            TextPanel panel = clock ? clockPanel : "widget".equals(sel) ? widgetPanel : widgets.panel(sel);
            panel.showTool(tool);
        }

        // Kopf: Werkzeug · Element · gilt für …
        SpannableStringBuilder head = new SpannableStringBuilder(toolName(tool) + " · " + widgets.name(sel) + "   ");
        if (!order) {
            int start = head.length();
            head.append(settings.editingOverlay() ? "● gilt für " + DayNight.fullLabel(settings) : "Tag · Grundlage für alle Modi");
            if (settings.editingOverlay()) {
                head.setSpan(new ForegroundColorSpan(0xFFF2A007), start, head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        title.setText(head);
        MaterialButton reset = findViewById(R.id.resetDayButton);
        boolean differs = !order && !overriddenKeys(tool, sel).isEmpty();
        reset.setVisibility(differs ? View.VISIBLE : View.GONE);
        reset.setText("home".equals(settings.screen()) ? "↺ Wie Sperrbildschirm" : "↺ Wie Tag");
        updateGroupBar(sel);
        preview.outline(sel);
        placeEditorFor(sel);
    }

    private void updateGroupBar(String sel) {
        for (int i = 0; i < GROUPS.length; i++) {
            groupItems[i].setSelected(sel != null && GROUPS[i][0].equals(group));
            boolean dot = false;
            if (sel != null) for (String[] t : toolsOf(GROUPS[i][0], sel)) dot |= !overriddenKeys(t[0], sel).isEmpty();
            groupDots[i].setVisibility(dot ? View.VISIBLE : View.GONE);
        }
    }

    /** Abschnitt, in dem ein Werkzeug speichert (die Ebene der Uhr liegt in "scene"). */
    private static String sectionOf(String tool, String sel) {
        return "layer".equals(tool) && "clock".equals(sel) ? "scene" : sel;
    }

    /** Schlüssel, die die bearbeitete Ebene (Modus/Startbildschirm) für dieses Werkzeug selbst festlegt. */
    private Set<String> overriddenKeys(String tool, String sel) {
        Set<String> out = new LinkedHashSet<>();
        if (sel == null || "order".equals(tool) || !settings.editingOverlay()) return out;
        Set<String> ov = settings.overridden(sectionOf(tool, sel));
        if (ov.isEmpty()) return out;
        if ("layer".equals(tool)) {
            String key = "clock".equals(sel) ? "subjectThreshold" : "layerThreshold";
            if (ov.contains(key)) out.add(key);
        } else if ("content".equals(tool)) {
            Set<String> other = new HashSet<>(NOT_CONTENT);
            for (Set<String> keys : TOOL_KEYS.values()) other.addAll(keys);
            for (String k : ov) if (!other.contains(k)) out.add(k);
        } else {
            for (String k : ov) if (TOOL_KEYS.getOrDefault(tool, Set.of()).contains(k)) out.add(k);
        }
        return out;
    }

    /** „Wie Tag“: Abweichungen dieses Werkzeugs verwerfen. */
    private void resetTool() {
        String sel = selected();
        if (sel == null) return;
        settings.resetKeys(sectionOf(tool, sel), overriddenKeys(tool, sel));
        changed();
        refreshPanels();
    }

    /** Regler neu aufbauen, nachdem sich Werte von außen geändert haben (Modus, Rückgängig, Geste). */
    void refreshPanels() {
        setupTextPanels();
        if (widgets != null) widgets.rebuildForMode();
        showEditor();
    }

    // ---------- Modus ----------

    private void updateModeButton() {
        String m = settings.activeMode();
        modeButton.setText(DayNight.buttonLabel(settings));
        modeButton.setIconResource(DayNight.iconFor(m));
        modeButton.setIconTint(ColorStateList.valueOf(DayNight.colorFor(settings, m)));
    }

    void onModeSwitched() {
        updateModeButton();
        preview.setScreen(settings.screen());
        refreshPanels();
    }

    // ---------- Reiter: Uhr und Widget ----------

    private void setupTextPanels() {
        int highlight = (MaterialColors.getColor(root, com.google.android.material.R.attr.colorPrimary) & 0xFFFFFF)
                | 0x99000000;
        TextPanel.Spec clock = new TextPanel.Spec();
        clock.section = "clock";
        clock.noun = "die Uhr";
        clock.defaultFont = "sans-serif-black";
        clock.defaultColor = "auto";
        clock.sizeFrom = 0;
        clock.sizeTo = 100;
        clock.sizeStep = 1;
        clock.sizeDefault = 32;
        clock.layerSection = "scene";
        clock.layerKey = "subjectThreshold";
        clock.currentLayer = preview::currentSubjectThreshold;
        clock.content = clockContentView();
        FrameLayout clockHost = findViewById(R.id.panelClock);
        clockHost.removeAllViews();
        View clockView = getLayoutInflater().inflate(R.layout.panel_text, clockHost, false);
        clockHost.addView(clockView);
        clockPanel = new TextPanel(this, settings, clockView, clock, highlight);

        TextPanel.Spec widget = new TextPanel.Spec();
        widget.section = "widget";
        widget.noun = "das Widget";
        widget.defaultFont = "sans-serif-medium";
        widget.defaultColor = settings.s("clock", "color", "auto"); // wie der Renderer: sonst Farbe der Uhr
        widget.sizeFrom = 0;
        widget.sizeTo = 100;
        widget.sizeStep = 0.1f;
        widget.sizeDefault = 5.4f;
        widget.layerSection = "widget";
        widget.layerKey = "layerThreshold";
        widget.currentLayer = preview::currentWidgetThreshold;
        widget.widget = true;
        FrameLayout widgetHost = findViewById(R.id.panelWidget);
        widgetHost.removeAllViews();
        View widgetView = getLayoutInflater().inflate(R.layout.panel_text, widgetHost, false);
        widgetHost.addView(widgetView);
        widgetPanel = new TextPanel(this, settings, widgetView, widget, highlight);

        preview.setOnSceneLoaded(() -> {
            clockPanel.onSceneLoaded();
            widgetPanel.onSceneLoaded();
            if (widgets != null) widgets.onSceneLoaded();
        });
        loadPhotoColors();
    }

    /** Farbvorschläge aus dem Foto (des bearbeiteten Modus) im Hintergrund berechnen. */
    private void loadPhotoColors() {
        String m = settings.activeMode();
        File photo = hasOwnPhoto(m) ? modeDir(m) : dir;
        worker.execute(() -> {
            int[] c = PhotoColors.fromImage(new File(photo, "background.png"));
            runOnUiThread(() -> {
                clockPanel.setPhotoColors(c);
                widgetPanel.setPhotoColors(c);
                if (widgets != null) widgets.setPhotoColors(c);
            });
        });
    }

    /** Blatt als Milchglas: halbtransparent, dahinter weichgezeichnet (System-Unschärfe ab Android 12). */
    void glassify(BottomSheetDialog d) {
        d.setOnShowListener(x -> {
            View sheetView = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheetView != null && sheetView.getBackground() != null) sheetView.getBackground().setAlpha(200);
            android.view.Window w = d.getWindow();
            if (w == null) return;
            w.setDimAmount(0.1f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                android.view.WindowManager.LayoutParams lp = w.getAttributes();
                lp.setBlurBehindRadius(dp(28));
                w.setAttributes(lp);
            }
        });
    }

    /** Offene Änderungen sofort in config.json schreiben (vor Favorit speichern/laden). */
    void flushSettings() {
        handler.removeCallbacks(saveTask);
        saveNow();
    }

    /** Nach dem Laden eines Favoriten: alles aus den Dateien neu aufbauen. */
    void reloadAll() {
        recreate();
    }

    /** Für das Widget „Nächster Termin“. */
    void requestCalendar() {
        calendarPermission.launch(Manifest.permission.READ_CALENDAR);
    }

    void pickFontFor(TextPanel panel) {
        fontTarget = panel;
        pickFont.launch(new String[]{"font/ttf", "font/otf", "font/sfnt",
                "application/x-font-ttf", "application/x-font-otf", "application/vnd.ms-opentype",
                "application/font-sfnt", "application/octet-stream"});
    }

    /** Eigene Schrift (alle Dateien der Familie) löschen; wer sie nutzt, bekommt wieder die Systemschrift. */
    void confirmDeleteFont(String family, java.util.List<String> files) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Schrift „" + family + "“ löschen?")
                .setMessage("Uhr und Widgets mit dieser Schrift bekommen wieder die Standardschrift.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", (d, w) -> {
                    java.util.List<String> sections = new java.util.ArrayList<>(java.util.List.of("clock", "widget"));
                    sections.addAll(settings.widgetIds());
                    for (String sec : sections) if (files.contains(settings.s(sec, "font", ""))) settings.remove(sec, "font");
                    for (String f : files) {
                        if (!new File(dir, f).delete()) Log.w(TAG, "Schrift nicht gelöscht: " + f);
                    }
                    changed();
                    clockPanel.buildFontChips();
                    widgetPanel.buildFontChips();
                    widgets.refreshFonts();
                    Snackbar.make(root, "Schrift „" + family + "“ gelöscht.", Snackbar.LENGTH_SHORT).show();
                })
                .show();
    }

    PreviewView preview() {
        return preview;
    }

    File dir() {
        return dir;
    }

    ExecutorService worker() {
        return worker;
    }

    WidgetManager widgets() {
        return widgets;
    }

    private View clockContentView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Uhr-Design");
        title.setTextAppearance(R.style.TextAppearance_Wetter_LabelLarge);
        title.setPadding(0, dp(6), 0, dp(4));
        box.addView(title);

        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setPadding(0, dp(4), 0, 0);
        ChipGroup styleChips = new ChipGroup(this);
        styleChips.setSingleLine(true);
        styleChips.setSingleSelection(true);
        styleChips.setSelectionRequired(true);
        scroll.addView(styleChips);
        box.addView(scroll);

        FrameLayout subHost = new FrameLayout(this);
        box.addView(subHost);

        Runnable updateSubOptions = () -> {
            subHost.removeAllViews();
            String st = settings.s("clock", "style", "digital");

            LinearLayout sub = new LinearLayout(MainActivity.this);
            sub.setOrientation(LinearLayout.VERTICAL);
            sub.setPadding(0, dp(8), 0, 0);

            if ("digital".equals(st)) {
                sub.addView(label("Format"));
                ChipGroup formats = new ChipGroup(MainActivity.this);
                formats.setSingleLine(true);
                formats.setSingleSelection(true);
                formats.setSelectionRequired(true);
                String currentFormat = settings.s("clock", "format", "HH:mm");
                String[][] fmts = {
                        {"HH:mm", "24 Stunden (14:30)"},
                        {"HH:mm:ss", "24 h mit Sekunden"},
                        {"h:mm a", "12 h mit AM/PM"},
                        {"h:mm", "12 h (2:30)"},
                        {"hh:mm a", "12 h mit Null (02:30 PM)"}
                };
                for (String[] f : fmts) {
                    Chip c = filterChip(formats, f[1]);
                    c.setTag(f[0]);
                    if (f[0].equals(currentFormat)) c.setChecked(true);
                }
                formats.setOnCheckedStateChangeListener((g, ids) -> {
                    Chip ch = g.findViewById(g.getCheckedChipId());
                    if (ch != null) {
                        settings.put("clock", "format", ch.getTag());
                        changed();
                    }
                });
                android.widget.HorizontalScrollView fmtScroll = new android.widget.HorizontalScrollView(MainActivity.this);
                fmtScroll.setHorizontalScrollBarEnabled(false);
                fmtScroll.addView(formats);
                sub.addView(fmtScroll);
                sub.addView(switchFor("Führende Null (09:05 statt 9:05)", "leadingZero", true));
            } else if ("analog".equals(st)) {
                sub.addView(label("Zifferblatt"));
                ChipGroup dials = new ChipGroup(MainActivity.this);
                dials.setSingleLine(true);
                dials.setSingleSelection(true);
                dials.setSelectionRequired(true);
                String curDial = settings.s("clock", "analogDial", "ticks");
                String[][] dl = {{"ticks", "Striche"}, {"numbers4", "12, 3, 6, 9"}, {"numbers12", "Alle Zahlen"}, {"minimal", "Minimal"}};
                for (String[] d : dl) {
                    Chip c = filterChip(dials, d[1]);
                    c.setTag(d[0]);
                    if (d[0].equals(curDial)) c.setChecked(true);
                }
                dials.setOnCheckedStateChangeListener((g, ids) -> {
                    Chip ch = g.findViewById(g.getCheckedChipId());
                    if (ch != null) {
                        settings.put("clock", "analogDial", ch.getTag());
                        changed();
                    }
                });
                android.widget.HorizontalScrollView dialScroll = new android.widget.HorizontalScrollView(MainActivity.this);
                dialScroll.setHorizontalScrollBarEnabled(false);
                dialScroll.addView(dials);
                sub.addView(dialScroll);
                sub.addView(switchFor("Sekundenzeiger", "analogSeconds", false));
            } else if ("vertical".equals(st)) {
                sub.addView(switchFor("Führende Null bei Stunden", "leadingZero", true));
                MaterialSwitch is12h = new MaterialSwitch(MainActivity.this);
                is12h.setText("12-Stunden-Format (1–12)");
                is12h.setChecked(settings.b("clock", "vertical12h", false));
                sub.addView(is12h);
                MaterialSwitch amPm = switchFor("AM / PM anzeigen", "verticalAmPm", false);
                amPm.setVisibility(is12h.isChecked() ? View.VISIBLE : View.GONE);
                sub.addView(amPm);
                is12h.setOnCheckedChangeListener((b, on) -> {
                    settings.put("clock", "vertical12h", on);
                    amPm.setVisibility(on ? View.VISIBLE : View.GONE);
                    changed();
                });
            } else if ("column".equals(st)) {
                sub.addView(switchFor("Führende Null bei Stunden", "leadingZero", true));
                sub.addView(switchFor("12-Stunden-Format (1–12)", "vertical12h", false));
                sub.addView(switchFor("Von unten nach oben lesen", "columnUp", false));
            } else if ("words".equals(st)) {
                sub.addView(switchFor("Umgangssprachlich (halb elf)", "colloquial", false));
                sub.addView(switchFor("Großbuchstaben", "uppercase", false));
            }
            subHost.addView(sub);
        };

        String current = settings.s("clock", "style", "digital");
        String[][] list = {{"digital", "Digital"}, {"analog", "Analog"}, {"vertical", "Zweizeilig"},
                {"column", "Spalte"}, {"words", "Wortuhr"}};
        for (String[] st : list) {
            Chip c = filterChip(styleChips, st[1]);
            c.setTag(st[0]);
            if (st[0].equals(current)) c.setChecked(true);
        }
        updateSubOptions.run();

        styleChips.setOnCheckedStateChangeListener((g, ids) -> {
            Chip ch = g.findViewById(g.getCheckedChipId());
            if (ch != null) {
                settings.put("clock", "style", ch.getTag());
                changed();
                updateSubOptions.run();
            }
        });
        return box;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextAppearance(R.style.TextAppearance_Wetter_LabelLarge);
        t.setPadding(0, dp(4), 0, dp(2));
        return t;
    }

    private MaterialSwitch switchFor(String text, String key, boolean def) {
        MaterialSwitch s = new MaterialSwitch(this);
        s.setText(text);
        s.setChecked(settings.b("clock", key, def));
        s.setOnCheckedChangeListener((b, on) -> {
            settings.put("clock", key, on);
            changed();
        });
        return s;
    }

    void later(Runnable r, long delayMs) {
        handler.postDelayed(r, delayMs);
    }

    // ---------- Bild (gemeinsam oder eigenes Foto eines Modus) ----------

    private File modeDir(String mode) {
        return new File(new File(dir, Presets.MODES), mode);
    }

    boolean hasOwnPhoto(String mode) {
        return mode != null && !"day".equals(mode) && new File(modeDir(mode), "background.png").isFile();
    }

    boolean hasCropSource(String mode) {
        File d = mode == null || "day".equals(mode) ? dir : modeDir(mode);
        return new File(d, ImageImporter.SOURCE).exists();
    }

    /** Eigenes Foto eines Modus löschen: er zeigt dann wieder das gemeinsame Foto. */
    void dropOwnPhoto(String mode) {
        if (mode == null || "day".equals(mode)) return;
        File[] files = modeDir(mode).listFiles();
        if (files != null) for (File f : files) f.delete();
        modeDir(mode).delete();
        CropActivity.clearSavedCrop(this, mode);
        WeatherWallpaperService.filesChanged();
        preview.reloadImage();
        loadPhotoColors();
    }

    void launchPicker(String mode) {
        photoTarget = mode == null ? "day" : mode;
        pickImage.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
    }

    void openCrop(String mode) {
        crop.launch(new Intent(this, CropActivity.class).putExtra(CropActivity.EXTRA_MODE, mode));
    }

    private void loadPicked(Uri uri) {
        Snackbar loading = Snackbar.make(root, "Foto wird geladen …", Snackbar.LENGTH_INDEFINITE);
        loading.show();
        Rect screen = getWindowManager().getMaximumWindowMetrics().getBounds();
        String target = photoTarget;
        worker.execute(() -> {
            try {
                ImageImporter.saveSource(this, uri, screen.width(), screen.height(), CropActivity.photoDir(dir, target));
                CropActivity.clearSavedCrop(this, target);
                runOnUiThread(() -> {
                    loading.dismiss();
                    openCrop(target);
                });
            } catch (Exception e) {
                Log.e(TAG, "Bild laden fehlgeschlagen", e);
                runOnUiThread(() -> {
                    loading.dismiss();
                    Snackbar.make(root, "Das Foto konnte nicht geladen werden.", Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private void onCropped(float secs) {
        preview.reloadImage();
        loadPhotoColors();
        updateEmptyState();
        Snackbar s = Snackbar.make(root, String.format(Locale.GERMANY,
                "Fertig in %.1f s. Mit „Speichern“ kommt es aufs Wallpaper.", secs), Snackbar.LENGTH_LONG);
        s.show();
    }

    /** Bild über "Teilen" aus Galerie oder anderer App. */
    private void handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        // aus dem Verlauf wieder geöffnet = Bild wurde schon verarbeitet
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return;
        setIntent(new Intent(this, MainActivity.class)); // nur einmal verarbeiten
        @SuppressWarnings("deprecation") // typsichere Variante erst ab API 33, minSdk ist 29
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri != null) {
            photoTarget = "day";
            loadPicked(uri);
        }
    }

    // ---------- Wetter-Vorschau, Standort ----------

    String previewWeather() {
        return preview.userWeather();
    }

    void setPreviewWeather(String weather) {
        preview.setUserWeather(weather);
    }

    boolean locationGranted() {
        return LocationHelper.granted(this);
    }

    void requestLocation() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
                || !ui.getBoolean("askedLocation", false)) {
            ui.edit().putBoolean("askedLocation", true).apply();
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
        } else {
            // dauerhaft abgelehnt: nur noch über die System-Einstellungen
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        }
    }

    // ---------- Favoriten teilen und importieren ----------

    void importPreset(PresetPanel panel) {
        importTarget = panel;
        pickPreset.launch(new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
    }

    private void importPresetFile(Uri uri) {
        worker.execute(() -> {
            String msg;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Datei nicht lesbar");
                Presets.Preset p = Presets.importZip(dir, in, System.currentTimeMillis());
                msg = "„" + p.name + "“ importiert";
            } catch (IOException e) {
                Log.e(TAG, "Favorit importieren fehlgeschlagen", e);
                msg = "Das ist kein Favorit dieser App.";
            }
            String text = msg;
            runOnUiThread(() -> {
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                if (importTarget != null) importTarget.refresh();
            });
        });
    }

    /** Favorit als .zip über das Teilen-Menü (Messenger, Drive, Dateien …). */
    void sharePreset(Presets.Preset p) {
        worker.execute(() -> {
            try {
                File shareDir = new File(getCacheDir(), "share");
                if (!shareDir.isDirectory() && !shareDir.mkdirs()) throw new IOException("Ordner nicht anlegbar");
                String name = p.name.replaceAll("[^A-Za-z0-9äöüÄÖÜß _-]", "").trim();
                File out = new File(shareDir, (name.isEmpty() ? "Favorit" : name) + ".wetterwallpaper.zip");
                Presets.zip(p, out);
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", out);
                Intent send = new Intent(Intent.ACTION_SEND)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(send, "„" + p.name + "“ teilen oder sichern")));
            } catch (IOException | IllegalArgumentException e) {
                Log.e(TAG, "Favorit teilen fehlgeschlagen", e);
                runOnUiThread(() -> Toast.makeText(this, "Teilen hat nicht geklappt.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ---------- Status, Vorschau, Speichern ----------

    private void onElementMoved(String id, float xPct, float yPct) {
        settings.put(id, "xPct", Math.round(xPct * 10) / 10.0);
        settings.put(id, "yPct", Math.round(yPct * 10) / 10.0);
        hideDragHint();
        changed();
        showEditor(); // Punkt „weicht ab“ kann sich geändert haben
    }

    private void hideDragHint() {
        if (ui.getBoolean("dragged", false)) return;
        ui.edit().putBoolean("dragged", true).apply();
        dragHint.animate().alpha(0).withEndAction(() -> dragHint.setVisibility(View.GONE));
    }

    /** Vorschau sofort aktualisieren, Datei gebündelt schreiben (das Wallpaper lädt dann nach). */
    void changed() {
        preview.setConfig(settings.snapshot());
        handler.removeCallbacks(saveTask);
        handler.postDelayed(saveTask, 400);
        handler.removeCallbacks(commitTask);
        handler.postDelayed(commitTask, 700);
    }

    private void saveNow() {
        try {
            settings.save();
        } catch (IOException e) {
            Log.e(TAG, "Einstellungen speichern fehlgeschlagen", e);
            Snackbar.make(root, "Einstellungen konnten nicht gespeichert werden.", Snackbar.LENGTH_LONG).show();
        }
    }

    // ---------- Rückgängig / Wiederholen ----------

    private void commit() {
        String cur = settings.text();
        if (committed != null && !cur.equals(committed)) {
            undo.push(committed);
            while (undo.size() > 60) undo.removeLast();
            redo.clear();
        }
        committed = cur;
        updateHistory();
    }

    private void undo() {
        handler.removeCallbacks(commitTask);
        commit();
        if (undo.isEmpty()) return;
        redo.push(committed);
        committed = undo.pop();
        restore(committed);
    }

    private void redo() {
        handler.removeCallbacks(commitTask);
        commit();
        if (redo.isEmpty()) return;
        undo.push(committed);
        committed = redo.pop();
        restore(committed);
    }

    private void restore(String state) {
        settings.replace(state);
        preview.setConfig(settings.snapshot());
        handler.removeCallbacks(saveTask);
        handler.postDelayed(saveTask, 400);
        updateModeButton();
        refreshPanels();
        updateHistory();
    }

    private void updateHistory() {
        undoButton.setEnabled(!undo.isEmpty());
        redoButton.setEnabled(!redo.isEmpty());
        undoButton.setAlpha(undo.isEmpty() ? 0.4f : 1f);
        redoButton.setAlpha(redo.isEmpty() ? 0.4f : 1f);
    }

    private void updateEmptyState() {
        boolean hasImage = new File(dir, "background.png").exists();
        emptyState.setVisibility(hasImage ? View.GONE : View.VISIBLE);
        preview.setVisibility(hasImage ? View.VISIBLE : View.INVISIBLE);
        editor.setVisibility(hasImage && !collapsed ? View.VISIBLE : View.GONE);
        dragHint.setVisibility(hasImage && !ui.getBoolean("dragged", false) ? View.VISIBLE : View.GONE);
    }

    private void updateStatus() {
        boolean active = isOurWallpaperActive();
        statusButton.setText(active ? "Speichern" : "Festlegen");
        statusButton.setOnClickListener(v -> {
            handler.removeCallbacks(saveTask);
            saveNow();
            // getrennte Live-Wallpaper für Start- und Sperrbildschirm gibt es erst ab Android 14
            if (Build.VERSION.SDK_INT < 34) {
                publish("both");
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Wo speichern?")
                    .setItems(new String[]{"Sperrbildschirm", "Startbildschirm", "Beide"},
                            (d, w) -> publish(new String[]{"lock", "home", "both"}[w]))
                    .show();
        });
    }

    /** Stand für den Bildschirm speichern; läuft das Wallpaper dort noch nicht, danach festlegen. */
    private void publish(String screen) {
        worker.execute(() -> {
            try {
                Published.save(dir, screen);
                WeatherWallpaperService.filesChanged();
                runOnUiThread(() -> {
                    if (activeOn(screen)) Snackbar.make(root, "Gespeichert", Snackbar.LENGTH_SHORT).show();
                    else openWallpaperPicker();
                });
            } catch (IOException e) {
                Log.e(TAG, "Wallpaper speichern fehlgeschlagen", e);
                runOnUiThread(() -> Snackbar.make(root, "Das Wallpaper konnte nicht gespeichert werden.", Snackbar.LENGTH_LONG).show());
            }
        });
    }

    private boolean activeOn(String screen) {
        WallpaperManager wm = WallpaperManager.getInstance(this);
        ComponentName ours = new ComponentName(this, WeatherWallpaperService.class);
        boolean home = isOurs(wm.getWallpaperInfo(), ours);
        // ohne eigenes Sperrbild (Id < 0) zeigt der Sperrbildschirm das Startbild
        boolean lock = Build.VERSION.SDK_INT >= 34 && wm.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0
                ? isOurs(wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK), ours) : home;
        return "home".equals(screen) ? home : "lock".equals(screen) ? lock : home && lock;
    }

    /** Aktiv auf Start- oder Sperrbildschirm. */
    private boolean isOurWallpaperActive() {
        WallpaperManager wm = WallpaperManager.getInstance(this);
        ComponentName ours = new ComponentName(this, WeatherWallpaperService.class);
        if (isOurs(wm.getWallpaperInfo(), ours)) return true;
        return Build.VERSION.SDK_INT >= 34 && isOurs(wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK), ours);
    }

    private static boolean isOurs(WallpaperInfo info, ComponentName ours) {
        return info != null && ours.equals(info.getComponent());
    }

    private void openWallpaperPicker() {
        startActivity(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        new ComponentName(this, WeatherWallpaperService.class)));
    }

    // ---------- Schriften importieren ----------

    private void importFont(Uri uri) {
        worker.execute(() -> {
            String name = sanitize(displayName(uri));
            File target = new File(dir, name);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(target)) {
                if (in == null) throw new IOException("Datei nicht lesbar");
                byte[] buf = new byte[1 << 15];
                for (int r; (r = in.read(buf)) > 0; ) out.write(buf, 0, r);
            } catch (IOException e) {
                Log.e(TAG, "Schrift kopieren fehlgeschlagen", e);
                target.delete();
            }
            boolean ok = target.exists() && new Typeface.Builder(target).build() != null;
            if (!ok) target.delete();
            runOnUiThread(() -> {
                if (!ok) {
                    Snackbar.make(root, "Das ist keine lesbare Schriftdatei (.ttf oder .otf).", Snackbar.LENGTH_LONG).show();
                    return;
                }
                settings.put(fontTarget != null ? fontTarget.section() : "clock", "font", name);
                changed();
                clockPanel.buildFontChips();
                widgetPanel.buildFontChips();
                if (widgets != null) widgets.refreshFonts();
                Snackbar.make(root, "Schrift „" + TextPanel.prettyFontName(name) + "“ hinzugefügt.", Snackbar.LENGTH_SHORT).show();
            });
        });
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException e) {
            Log.w(TAG, "Dateiname nicht lesbar", e);
        }
        return "Schrift.ttf";
    }

    /** Nur harmlose Zeichen, Endung .ttf/.otf erzwingen (der Name landet als Dateiname im App-Ordner). */
    private static String sanitize(String name) {
        String n = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!n.toLowerCase(Locale.ROOT).matches(".*\\.(ttf|otf)")) n = n + ".ttf";
        return n.startsWith(".") ? "Schrift" + n : n;
    }

    // ---------- Hilfen ----------

    Chip filterChip(ChipGroup group, String text) {
        Chip c = (Chip) getLayoutInflater().inflate(R.layout.chip_filter, group, false);
        c.setText(text);
        c.setId(View.generateViewId());
        group.addView(c);
        return c;
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
