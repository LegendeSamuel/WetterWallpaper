package de.seelig.wetterwallpaper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Gespeicherter Stand je Bildschirm (Foto, Ebenen, Schriften, config.json). Das Wallpaper liest nur diesen Stand,
 * Bearbeiten in der App ändert ihn erst beim Speichern. So können Start- und Sperrbildschirm verschieden aussehen.
 */
final class Published {
    static final String DIR = "published";

    private Published() {
    }

    static File dir(File app, String screen) {
        return new File(new File(app, DIR), screen);
    }

    private static boolean exists(File d) {
        return new File(d, Settings.FILE).isFile();
    }

    /** Ordner für eine Engine: eigener Stand, für den Startbildschirm sonst der des Sperrbildschirms, sonst der App-Ordner. */
    static File forScreen(File app, String screen) {
        File own = dir(app, screen);
        if (exists(own)) return own;
        File lock = dir(app, "lock");
        return exists(lock) ? lock : app;
    }

    /** Aktuellen Stand der App speichern; screen "lock", "home" oder "both". */
    static void save(File app, String screen) throws IOException {
        File lock = dir(app, "lock");
        File home = dir(app, "home");
        if ("both".equals(screen)) {
            replace(app, lock);
            deleteTree(home);
            return;
        }
        // der andere Bildschirm las bisher mit: er behält den Stand, den er gerade zeigt
        if ("lock".equals(screen) && !exists(home)) replace(exists(lock) ? lock : app, home);
        if ("home".equals(screen) && !exists(lock)) replace(app, lock);
        replace(app, "home".equals(screen) ? home : lock);
    }

    /** Erst neben dst fertig kopieren, dann tauschen: eine Engine sieht nie einen halben Stand. */
    private static void replace(File src, File dst) throws IOException {
        File tmp = new File(dst.getPath() + ".tmp");
        deleteTree(tmp);
        copy(src, tmp, true);
        deleteTree(dst);
        if (!tmp.renameTo(dst)) throw new IOException("Kann " + dst + " nicht anlegen");
    }

    /** Dateien im Ordner und der Ordner der Modus-Fotos; ohne Originalfoto, halbe Dateien und andere Stände. */
    private static void copy(File src, File dst, boolean root) throws IOException {
        if (!dst.mkdirs() && !dst.isDirectory()) throw new IOException("Kann " + dst + " nicht anlegen");
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            if (f.isDirectory()) {
                if (!root || Presets.MODES.equals(n)) copy(f, new File(dst, n), false);
            } else if (!ImageImporter.SOURCE.equals(n) && !n.endsWith(".tmp")) {
                Files.copy(f.toPath(), new File(dst, n).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
