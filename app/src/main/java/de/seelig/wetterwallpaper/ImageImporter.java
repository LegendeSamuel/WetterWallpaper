package de.seelig.wetterwallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.RectF;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Foto aus der Galerie → source.jpg (zum späteren Nachjustieren) → gewählter Ausschnitt in
 * Bildschirmgröße + Tiefenkarte + Himmelsmaske, direkt in den Ordner, den das Wallpaper beobachtet.
 */
final class ImageImporter {
    static final String SOURCE = "source.jpg";
    /** Ab dieser Nähe (0 = am fernsten) ist ein Pixel kein Himmel mehr. */
    private static final float SKY_NEAR = 0.008f;

    private ImageImporter() {
    }

    /** Dekodiert gedreht (EXIF) und so groß, dass man noch bis 2x hineinzoomen kann, speichert als source.jpg. */
    static void saveSource(Context ctx, Uri uri, int screenW, int screenH, File dir) throws IOException {
        ImageDecoder.Source src = ImageDecoder.createSource(ctx.getContentResolver(), uri);
        Bitmap b = ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            float scale = 2 * Math.max(screenW / (float) w, screenH / (float) h);
            // ponytail: Deckel 12 MP, sonst wird das Bild für den Editor zu groß zum Zeichnen
            scale = Math.min(scale, (float) Math.sqrt(12e6 / ((double) w * h)));
            if (scale < 1) decoder.setTargetSize(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
        });
        File tmp = new File(dir, "source.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            if (!b.compress(Bitmap.CompressFormat.JPEG, 95, out)) throw new IOException("JPEG schreiben fehlgeschlagen");
        }
        replace(tmp, new File(dir, SOURCE));
    }

    static Bitmap loadSource(File dir) {
        File f = new File(dir, SOURCE);
        return f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
    }

    /** crop in Pixeln von source; Ergebnis landet als background/depth/sky im Wallpaper-Ordner. */
    static void importCropped(Context ctx, Bitmap source, RectF crop, int screenW, int screenH, File dir) throws Exception {
        int l = Math.max(0, Math.round(crop.left));
        int t = Math.max(0, Math.round(crop.top));
        int r = Math.min(source.getWidth(), Math.round(crop.right));
        int b = Math.min(source.getHeight(), Math.round(crop.bottom));
        Bitmap screen = Bitmap.createScaledBitmap(Bitmap.createBitmap(source, l, t, r - l, b - t), screenW, screenH, true);
        DepthEstimator.Depth depth;
        try (DepthEstimator estimator = new DepthEstimator(ctx)) {
            depth = estimator.estimate(screen);
        }
        // erst in Temp-Dateien schreiben, dann umbenennen: das Wallpaper soll nie halbe Dateien laden
        File bgTmp = new File(dir, "background.tmp");
        File depthTmp = new File(dir, "depth.tmp");
        File skyTmp = new File(dir, "sky.tmp");
        writePng(screen, bgTmp);
        writePng(depthBitmap(depth), depthTmp);
        writePng(skyBitmap(depth), skyTmp);
        new File(dir, "foreground.png").delete(); // alte PC-Freistellung würde sonst gewinnen
        replace(skyTmp, new File(dir, "sky.png"));
        replace(depthTmp, new File(dir, "depth.png"));
        replace(bgTmp, new File(dir, "background.png"));
        WeatherWallpaperService.filesChanged();
    }

    /** Graustufen, sqrt-kodiert wie bei split_layers.py; Scene skaliert sie selbst auf Bildgröße. */
    private static Bitmap depthBitmap(DepthEstimator.Depth d) {
        int[] px = new int[d.width * d.height];
        for (int i = 0; i < px.length; i++) {
            int v = Math.round((float) Math.sqrt(Math.max(0f, d.near[i])) * 255);
            px[i] = Color.rgb(v, v, v);
        }
        return Bitmap.createBitmap(px, d.width, d.height, Bitmap.Config.ARGB_8888);
    }

    private static Bitmap skyBitmap(DepthEstimator.Depth d) {
        boolean[] sky = SkyMask.compute(d.near, d.width, d.height, SKY_NEAR);
        int[] px = new int[sky.length];
        for (int i = 0; i < px.length; i++) px[i] = sky[i] ? Color.WHITE : Color.BLACK;
        return Bitmap.createBitmap(px, d.width, d.height, Bitmap.Config.ARGB_8888);
    }

    private static void writePng(Bitmap b, File f) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            if (!b.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("PNG schreiben fehlgeschlagen: " + f);
        }
    }

    private static void replace(File from, File to) throws IOException {
        if (to.exists() && !to.delete()) throw new IOException("Kann nicht ersetzen: " + to);
        if (!from.renameTo(to)) throw new IOException("Umbenennen fehlgeschlagen: " + to);
    }
}
