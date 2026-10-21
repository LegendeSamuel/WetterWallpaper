package de.seelig.wetterwallpaper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ausschnitt von source.jpg wählen (ziehen/zoomen) und daraus das Wallpaper berechnen. */
public class CropActivity extends AppCompatActivity {
    static final String EXTRA_SECONDS = "seconds";
    /** Modus mit eigenem Foto (Ordner modes/<Modus>), fehlt = gemeinsames Foto. */
    static final String EXTRA_MODE = "mode";
    private static final String TAG = "WetterWallpaper";
    private static final String PREFS = "crop";
    private String prefs = PREFS;

    /** Ordner des Fotos: App-Ordner oder modes/<Modus>; nur einfache Modus-Namen, nichts außerhalb. */
    static File photoDir(File appDir, String mode) {
        if (appDir == null || mode == null || "day".equals(mode) || !mode.matches("[A-Za-z0-9_]+")) return appDir;
        File d = new File(new File(appDir, Presets.MODES), mode);
        if (!d.isDirectory() && !d.mkdirs()) return appDir;
        return d;
    }

    private static String prefsName(String mode) {
        return mode == null || "day".equals(mode) ? PREFS : PREFS + "_" + mode;
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private CropView cropView;
    private TextView status;
    private MaterialButton apply;
    private LinearProgressIndicator progress;
    private Bitmap source;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_crop);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        Rect screen = getWindowManager().getMaximumWindowMetrics().getBounds();
        File appDir = getExternalFilesDir(null);
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        prefs = prefsName(mode);
        File dir = photoDir(appDir, mode);
        cropView = new CropView(this, screen.width() / (float) screen.height());
        ((FrameLayout) findViewById(R.id.cropContainer)).addView(cropView, 0,
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (appDir != null) {
            JSONObject clock = Settings.read(appDir).optJSONObject("clock");
            if (clock == null) clock = new JSONObject();
            cropView.setClockGuide(TextLayer.typeface(appDir, clock, "sans-serif-black"), (float) clock.optDouble("sizePct", 32),
                    (float) clock.optDouble("xPct", 50), (float) clock.optDouble("yPct", 33));
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            if (!busy) finish();
        });
        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);
        apply = findViewById(R.id.apply);
        apply.setOnClickListener(v -> apply(screen.width(), screen.height(), dir));

        worker.execute(() -> {
            Bitmap b = dir != null ? ImageImporter.loadSource(dir) : null;
            runOnUiThread(() -> {
                if (b == null) {
                    finish();
                    return;
                }
                source = b;
                cropView.setImage(b, savedCrop(b));
                apply.setEnabled(true);
            });
        });
    }

    private void apply(int screenW, int screenH, File dir) {
        RectF crop = cropView.cropRect();
        saveCrop(crop, source);
        setBusy(true);
        long start = System.currentTimeMillis();
        worker.execute(() -> {
            try {
                ImageImporter.importCropped(this, source, crop, screenW, screenH, dir);
                float secs = (System.currentTimeMillis() - start) / 1000f;
                runOnUiThread(() -> {
                    setResult(RESULT_OK, new Intent().putExtra(EXTRA_SECONDS, secs));
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "Import fehlgeschlagen", e);
                runOnUiThread(() -> {
                    setBusy(false);
                    status.setText("Das hat nicht geklappt. Bitte noch einmal versuchen.");
                });
            }
        });
    }

    private void setBusy(boolean b) {
        busy = b;
        apply.setEnabled(!b);
        apply.setText(b ? "Wird berechnet …" : "Übernehmen");
        cropView.setEnabled(!b);
        progress.setVisibility(b ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.hint).setVisibility(b ? View.GONE : View.VISIBLE);
        if (b) status.setText("Tiefe und Ebenen werden auf dem Handy berechnet. Das dauert ein paar Sekunden.");
    }

    @Override
    public void onBackPressed() {
        if (!busy) super.onBackPressed();
    }

    /** Letzter Ausschnitt, relativ zur Bildgröße gespeichert. */
    private RectF savedCrop(Bitmap b) {
        SharedPreferences p = getSharedPreferences(prefs, MODE_PRIVATE);
        if (!p.contains("l")) return null;
        return new RectF(p.getFloat("l", 0) * b.getWidth(), p.getFloat("t", 0) * b.getHeight(),
                p.getFloat("r", 1) * b.getWidth(), p.getFloat("b", 1) * b.getHeight());
    }

    private void saveCrop(RectF crop, Bitmap b) {
        getSharedPreferences(prefs, MODE_PRIVATE).edit()
                .putFloat("l", crop.left / b.getWidth()).putFloat("t", crop.top / b.getHeight())
                .putFloat("r", crop.right / b.getWidth()).putFloat("b", crop.bottom / b.getHeight())
                .apply();
    }

    /** Neues Bild gewählt: alter Ausschnitt passt nicht mehr. */
    static void clearSavedCrop(Context ctx) {
        clearSavedCrop(ctx, null);
    }

    static void clearSavedCrop(Context ctx, String mode) {
        ctx.getSharedPreferences(prefsName(mode), MODE_PRIVATE).edit().clear().apply();
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
