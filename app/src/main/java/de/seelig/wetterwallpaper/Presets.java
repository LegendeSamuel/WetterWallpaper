package de.seelig.wetterwallpaper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Favoriten: der komplette Stand (config.json) plus Kopie des Bildes mit fertig berechneter Tiefe und Himmel,
 * je Favorit ein Ordner presets/<Zeitstempel>/ mit meta.txt (Zeile 1 Name, Zeile 2 Erstellzeit in ms).
 * Laden kopiert alles zurück; fehlt das Bild, werden nur die Einstellungen übernommen. Reines Java, Selbsttest per main.
 */
final class Presets {
    /** Bild und alles, was daraus berechnet wurde; foreground.png gibt es nur bei alter PC-Freistellung. */
    static final String[] IMAGE_FILES = {"background.png", "depth.png", "sky.png", "foreground.png", "source.jpg"}; // source.jpg = ImageImporter.SOURCE
    static final String CONFIG = "config.json";
    private static final String META = "meta.txt";

    static final class Preset {
        final File dir;
        final String name;
        final long created;

        Preset(File dir, String name, long created) {
            this.dir = dir;
            this.name = name;
            this.created = created;
        }

        /** Hat dieser Favorit ein Bild (sonst nur Einstellungen)? */
        boolean hasImage() {
            return new File(dir, "background.png").isFile();
        }

        File thumbnailSource() {
            return new File(dir, "background.png");
        }
    }

    private Presets() {
    }

    private static File root(File appDir) {
        return new File(appDir, "presets");
    }

    /** Alle Favoriten, neueste zuerst. */
    static List<Preset> list(File appDir) {
        List<Preset> out = new ArrayList<>();
        File[] dirs = root(appDir).listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File d : dirs) {
            try {
                List<String> meta = Files.readAllLines(new File(d, META).toPath(), StandardCharsets.UTF_8);
                out.add(new Preset(d, meta.isEmpty() ? d.getName() : meta.get(0),
                        meta.size() > 1 ? Long.parseLong(meta.get(1).trim()) : d.lastModified()));
            } catch (IOException | NumberFormatException e) {
                out.add(new Preset(d, d.getName(), d.lastModified())); // kaputte meta.txt: trotzdem anzeigen
            }
        }
        out.sort((a, b) -> Long.compare(b.created, a.created));
        return out;
    }

    /** Aktuellen Stand als neuen Favoriten sichern. */
    static Preset save(File appDir, String name, long now) throws IOException {
        File d = new File(root(appDir), String.valueOf(now));
        if (!d.mkdirs() && !d.isDirectory()) throw new IOException("Ordner nicht anlegbar: " + d);
        Preset p = new Preset(d, name, now);
        overwrite(appDir, p);
        return p;
    }

    /** Favoriten mit dem aktuellen Stand überschreiben (Name bleibt). */
    static void overwrite(File appDir, Preset p) throws IOException {
        copyAll(appDir, p.dir, true);
        writeMeta(p.dir, p.name, p.created);
    }

    /**
     * Favoriten laden: Einstellungen immer, Bild nur, wenn es im Favoriten liegt.
     * Gibt zurück, ob das Bild übernommen wurde.
     */
    static boolean apply(File appDir, Preset p) throws IOException {
        File cfg = new File(p.dir, CONFIG);
        if (!cfg.isFile()) throw new IOException("Favorit ohne Einstellungen: " + p.dir);
        boolean image = p.hasImage();
        if (image) copyAll(p.dir, appDir, false);
        // Einstellungen zuletzt: das Wallpaper lädt dann Bild und Einstellungen zusammen neu
        copy(cfg, new File(appDir, CONFIG));
        return image;
    }

    static void rename(Preset p, String name) throws IOException {
        writeMeta(p.dir, name, p.created);
    }

    static void delete(Preset p) {
        deleteTree(p.dir);
    }

    /** Bilddateien von → nach; was in der Quelle fehlt, wird im Ziel entfernt (z. B. alte foreground.png). */
    private static void copyAll(File from, File to, boolean withConfig) throws IOException {
        for (String name : IMAGE_FILES) {
            File src = new File(from, name);
            File dst = new File(to, name);
            if (src.isFile()) copy(src, dst);
            else dst.delete();
        }
        // eigene Fotos der Modi (modes/<Modus>/…) gehören zum Stand dazu
        File modesSrc = new File(from, MODES);
        File modesDst = new File(to, MODES);
        deleteTree(modesDst);
        File[] modeDirs = modesSrc.listFiles(File::isDirectory);
        if (modeDirs != null) {
            for (File m : modeDirs) {
                File target = new File(modesDst, m.getName());
                if (!target.mkdirs() && !target.isDirectory()) throw new IOException("Ordner nicht anlegbar: " + target);
                for (String name : IMAGE_FILES) {
                    File src = new File(m, name);
                    if (src.isFile()) copy(src, new File(target, name));
                }
            }
        }
        if (withConfig) {
            File cfg = new File(from, CONFIG);
            if (cfg.isFile()) copy(cfg, new File(to, CONFIG));
        }
    }

    static final String MODES = "modes";

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    // ---------- Teilen und Importieren (.zip) ----------

    /** Favorit als .zip (Ordnerinhalt mit relativen Pfaden), zum Teilen oder Sichern in der Cloud. */
    static void zip(Preset p, File out) throws IOException {
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(out))) {
            addToZip(z, p.dir, "");
        }
    }

    private static void addToZip(java.util.zip.ZipOutputStream z, File dir, String prefix) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            String name = prefix + k.getName();
            if (k.isDirectory()) {
                addToZip(z, k, name + "/");
                continue;
            }
            z.putNextEntry(new java.util.zip.ZipEntry(name));
            Files.copy(k.toPath(), z);
            z.closeEntry();
        }
    }

    /**
     * Geteilten Favoriten (.zip) als neuen Favoriten anlegen. Nur bekannte Dateien, keine Pfade nach außen.
     * Ohne config.json ist es kein Favorit: dann wird nichts angelegt.
     */
    static Preset importZip(File appDir, java.io.InputStream in, long now) throws IOException {
        File d = new File(root(appDir), String.valueOf(now));
        if (!d.mkdirs() && !d.isDirectory()) throw new IOException("Ordner nicht anlegbar: " + d);
        java.util.Set<String> allowed = new java.util.HashSet<>(java.util.Arrays.asList(IMAGE_FILES));
        allowed.add(CONFIG);
        allowed.add(META);
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(in)) {
            for (java.util.zip.ZipEntry e; (e = z.getNextEntry()) != null; ) {
                if (e.isDirectory()) continue;
                String[] parts = e.getName().replace('\\', '/').split("/");
                String file = parts[parts.length - 1];
                if (!allowed.contains(file)) continue;
                File target;
                if (parts.length == 1) target = new File(d, file);
                else if (parts.length == 3 && MODES.equals(parts[0]) && parts[1].matches("[A-Za-z0-9_]+")
                        && !CONFIG.equals(file) && !META.equals(file)) {
                    target = new File(new File(d, MODES), parts[1]);
                    if (!target.mkdirs() && !target.isDirectory()) throw new IOException("Ordner nicht anlegbar");
                    target = new File(target, file);
                } else continue;
                Files.copy(z, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (!new File(d, CONFIG).isFile()) {
            deleteTree(d);
            throw new IOException("Die Datei enthält keinen Favoriten.");
        }
        String name = "Importiert";
        File meta = new File(d, META);
        if (meta.isFile()) {
            List<String> lines = Files.readAllLines(meta.toPath(), StandardCharsets.UTF_8);
            if (!lines.isEmpty() && !lines.get(0).trim().isEmpty()) name = lines.get(0).trim();
        }
        writeMeta(d, name, now); // Zeitpunkt des Imports, damit er vorne steht
        return new Preset(d, name, now);
    }

    /** Über eine Zwischendatei kopieren, damit nie eine halbe Datei liegt. */
    private static void copy(File src, File dst) throws IOException {
        File tmp = new File(dst.getPath() + ".tmp");
        Files.copy(src.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void writeMeta(File d, String name, long created) throws IOException {
        String clean = name.replace('\n', ' ').trim();
        Files.write(new File(d, META).toPath(), (clean + "\n" + created + "\n").getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws IOException {
        File app = Files.createTempDirectory("presets-test").toFile();
        Files.write(new File(app, CONFIG).toPath(), "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(app, "background.png").toPath(), new byte[]{1});
        Files.write(new File(app, "depth.png").toPath(), new byte[]{2});
        Preset p = save(app, "Abend klar", 1000);
        check(list(app).size() == 1 && list(app).get(0).name.equals("Abend klar"), "speichern + Name");
        check(p.hasImage() && new File(p.dir, "depth.png").isFile(), "Bild kopiert");

        // anderes Bild + andere Einstellungen, dazu eine Datei, die der Favorit nicht kennt
        Files.write(new File(app, CONFIG).toPath(), "{\"a\":2}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(app, "background.png").toPath(), new byte[]{9});
        Files.write(new File(app, "foreground.png").toPath(), new byte[]{7});
        check(apply(app, p), "Bild übernommen");
        check(Files.readAllBytes(new File(app, "background.png").toPath())[0] == 1, "altes Bild zurück");
        check(!new File(app, "foreground.png").exists(), "fremde Freistellung entfernt");
        check(new String(Files.readAllBytes(new File(app, CONFIG).toPath()), StandardCharsets.UTF_8).contains("1"), "Einstellungen zurück");

        // Bild im Favoriten verschwunden: nur Einstellungen, aktuelles Bild bleibt
        new File(p.dir, "background.png").delete();
        Files.write(new File(app, "background.png").toPath(), new byte[]{5});
        check(!apply(app, p), "meldet fehlendes Bild");
        check(Files.readAllBytes(new File(app, "background.png").toPath())[0] == 5, "aktuelles Bild bleibt");

        save(app, "Zweiter", 2000);
        check(list(app).get(0).name.equals("Zweiter"), "neueste zuerst");
        rename(p, "Umbenannt");
        delete(list(app).get(0));
        check(list(app).size() == 1 && list(app).get(0).name.equals("Umbenannt"), "umbenennen + löschen");

        // eigenes Foto der Nacht wandert mit, Teilen als .zip und wieder importieren
        File night = new File(app, MODES + "/night");
        check(night.mkdirs(), "Nacht-Ordner");
        Files.write(new File(night, "background.png").toPath(), new byte[]{4});
        Preset withNight = save(app, "Mit Nacht", 3000);
        check(new File(withNight.dir, MODES + "/night/background.png").isFile(), "Modus-Foto im Favoriten");
        File zip = new File(app, "x.zip");
        zip(withNight, zip);
        Preset imported;
        try (java.io.InputStream in = new java.io.FileInputStream(zip)) {
            imported = importZip(app, in, 4000);
        }
        check(imported.name.equals("Mit Nacht") && imported.hasImage(), "Import mit Name und Bild");
        check(new File(imported.dir, MODES + "/night/background.png").isFile(), "Import mit Modus-Foto");
        // fremde Pfade werden nicht entpackt
        java.io.ByteArrayOutputStream bad = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(bad)) {
            z.putNextEntry(new java.util.zip.ZipEntry("../config.json"));
            z.write(new byte[]{1});
            z.closeEntry();
        }
        boolean refused = false;
        try {
            importZip(app, new java.io.ByteArrayInputStream(bad.toByteArray()), 5000);
        } catch (IOException e) {
            refused = true;
        }
        check(refused && !new File(app, "presets/5000").exists(), "kein Favorit ohne gültige config.json");
        System.out.println("Presets ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
