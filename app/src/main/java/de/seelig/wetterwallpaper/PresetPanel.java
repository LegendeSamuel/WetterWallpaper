package de.seelig.wetterwallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Favoriten-Leiste im Blatt „Bild & Wetter“: „+“ sichert den aktuellen Stand (Einstellungen + Bild),
 * Antippen lädt einen Favoriten, langes Drücken bietet Überschreiben, Umbenennen, Löschen.
 */
final class PresetPanel {
    private final MainActivity activity;
    private final LinearLayout row;
    private final File dir;
    private final ExecutorService worker;

    PresetPanel(MainActivity activity, LinearLayout row, File dir, ExecutorService worker) {
        this.activity = activity;
        this.row = row;
        this.dir = dir;
        this.worker = worker;
        refresh();
    }

    /** Liste und Vorschaubilder im Hintergrund laden, dann die Leiste neu bauen. */
    void refresh() {
        worker.execute(() -> {
            List<Presets.Preset> list = Presets.list(dir);
            List<Bitmap> thumbs = new ArrayList<>();
            for (Presets.Preset p : list) thumbs.add(p.hasImage() ? thumbnail(p.thumbnailSource()) : null);
            activity.runOnUiThread(() -> build(list, thumbs));
        });
    }

    private static Bitmap thumbnail(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = Math.max(1, o.outWidth / 200);
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private void build(List<Presets.Preset> list, List<Bitmap> thumbs) {
        row.removeAllViews();
        row.addView(card(null, null, "Aktuelles speichern", v -> askName("Favorit speichern",
                "Favorit " + (list.size() + 1), this::save)));
        for (int i = 0; i < list.size(); i++) {
            Presets.Preset p = list.get(i);
            MaterialCardView c = card(thumbs.get(i), p, p.name, v -> load(p));
            c.setOnLongClickListener(v -> {
                options(p);
                return true;
            });
            row.addView(c);
        }
    }

    /** Karte: Vorschau (oder „+“ / „Bild fehlt“) und Name darunter. */
    private MaterialCardView card(Bitmap thumb, Presets.Preset p, String label, View.OnClickListener click) {
        int w = activity.dp(96);
        MaterialCardView card = new MaterialCardView(activity);
        card.setRadius(activity.dp(15)); // radius-lg
        card.setCardBackgroundColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorSurfaceContainerHigh));
        card.setStrokeColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorOutlineVariant));
        card.setStrokeWidth(activity.dp(1));
        card.setContentDescription(p == null ? "Aktuellen Stand als Favorit speichern" : "Favorit " + label + " laden");
        card.setOnClickListener(click);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        FrameLayout top = new FrameLayout(activity);
        if (thumb != null) {
            ImageView img = new ImageView(activity);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageBitmap(thumb);
            top.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView t = new TextView(activity);
            t.setGravity(Gravity.CENTER);
            t.setText(p == null ? "+" : "Bild fehlt");
            t.setTextAppearance(p == null ? R.style.TextAppearance_Wetter_DisplaySmall : R.style.TextAppearance_Wetter_LabelMedium);
            t.setTextColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorPrimary));
            top.addView(t, new FrameLayout.LayoutParams(-1, -1));
        }
        box.addView(top, new LinearLayout.LayoutParams(w, activity.dp(150)));
        TextView name = new TextView(activity);
        name.setText(label);
        name.setTextAppearance(R.style.TextAppearance_Wetter_LabelMedium);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(activity.dp(8), activity.dp(6), activity.dp(8), activity.dp(8));
        box.addView(name, new LinearLayout.LayoutParams(w, -2));
        card.addView(box);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(activity.dp(10));
        card.setLayoutParams(lp);
        return card;
    }

    private void save(String name) {
        activity.flushSettings(); // letzte Änderungen erst in config.json schreiben
        worker.execute(() -> {
            try {
                Presets.save(dir, name, System.currentTimeMillis());
                activity.runOnUiThread(() -> toast("„" + name + "“ gespeichert"));
            } catch (IOException e) {
                activity.runOnUiThread(() -> toast("Speichern hat nicht geklappt: " + e.getMessage()));
            }
            refresh();
        });
    }

    private void load(Presets.Preset p) {
        activity.flushSettings();
        worker.execute(() -> {
            try {
                boolean image = Presets.apply(dir, p);
                if (image) CropActivity.clearSavedCrop(activity); // alter Ausschnitt passt nicht zum Bild des Favoriten
                WeatherWallpaperService.filesChanged();
                activity.runOnUiThread(() -> {
                    if (image) {
                        toast("„" + p.name + "“ geladen");
                        activity.reloadAll();
                    } else {
                        // Hinweis vor dem Neuaufbau zeigen, sonst ginge er verloren
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle("Bild fehlt")
                                .setMessage("Das Bild von „" + p.name + "“ ist nicht mehr da. Übernommen wurden nur die Einstellungen, "
                                        + "dein aktuelles Bild bleibt. Speichere den Favoriten neu, um wieder ein Bild dabeizuhaben.")
                                .setPositiveButton("Verstanden", null)
                                .setOnDismissListener(d -> activity.reloadAll())
                                .show();
                    }
                });
            } catch (IOException e) {
                activity.runOnUiThread(() -> toast("Laden hat nicht geklappt: " + e.getMessage()));
            }
        });
    }

    private void options(Presets.Preset p) {
        String[] items = {"Mit aktuellem Stand überschreiben", "Teilen oder sichern", "Umbenennen", "Löschen"};
        new MaterialAlertDialogBuilder(activity)
                .setTitle(p.name)
                .setItems(items, (d, which) -> {
                    if (which == 0) overwrite(p);
                    else if (which == 1) activity.sharePreset(p);
                    else if (which == 2) askName("Umbenennen", p.name, name -> worker.execute(() -> {
                        try {
                            Presets.rename(p, name);
                        } catch (IOException e) {
                            activity.runOnUiThread(() -> toast("Umbenennen hat nicht geklappt"));
                        }
                        refresh();
                    }));
                    else confirmDelete(p);
                })
                .show();
    }

    private void overwrite(Presets.Preset p) {
        activity.flushSettings();
        worker.execute(() -> {
            try {
                Presets.overwrite(dir, p);
                activity.runOnUiThread(() -> toast("„" + p.name + "“ überschrieben"));
            } catch (IOException e) {
                activity.runOnUiThread(() -> toast("Überschreiben hat nicht geklappt"));
            }
            refresh();
        });
    }

    private void confirmDelete(Presets.Preset p) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("„" + p.name + "“ löschen?")
                .setMessage("Der Favorit samt gespeichertem Bild wird entfernt. Dein aktuelles Wallpaper bleibt.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", (d, w) -> worker.execute(() -> {
                    Presets.delete(p);
                    refresh();
                }))
                .show();
    }

    private interface NameCallback {
        void accept(String name);
    }

    private void askName(String title, String preset, NameCallback done) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(preset);
        input.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(activity);
        int pad = activity.dp(24);
        box.setPadding(pad, activity.dp(8), pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", (d, w) -> {
                    String name = input.getText().toString().trim();
                    done.accept(name.isEmpty() ? preset : name);
                })
                .show();
        input.requestFocus();
    }

    private void toast(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }
}
