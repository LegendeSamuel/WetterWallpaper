# Wetter Wallpaper (Android Live Wallpaper)

## Projektübersicht
Natives Java-Projekt, getestet auf dem Samsung Galaxy S24 Ultra (One UI 8.5 / Android 16, API 36). Build: compileSdk 34, targetSdk 34, minSdk 29, nur `arm64-v8a`, AGP 8.7.3, Gradle 8.13.
Erstellt ein dynamisches Wetter-Live-Wallpaper aus einem beliebigen Foto mit On-Device-Tiefenberechnung (Depth Anything V2 Small via ONNX Runtime).

## Architektur & Kernkomponenten
- **`MainActivity.java`**: Editor mit Vollbild-Vorschau. Oben Modus-Pille, Rückgängig/Wiederholen, ☰ (`SettingsSheet`) und Speichern; unten Elementzeile, ein Regler (`TextPanel`) und fünf Werkzeuggruppen.
- **`Settings.java`**: config.json in Ebenen: Tag (Wurzel), darüber `modes.<id>` nur mit Abweichungen, darüber `screens.home` (Startbildschirm). `Settings.resolve()` ergibt die wirksame Konfiguration.
- **`DayNight.java`**: Moduswahl (Regen-Look, eigene Zeiträume, Dämmerung, Nacht, Sonnenstand) und das Blatt „Modus & Zeiten“ mit Tagesband.
- **`WallpaperRenderer.java`**: Rendering-Pipeline für Wallpaper & Vorschau. Zeichnet Hintergrund, Himmelskörper (Sonne, Mond, Sterne), Wolken, Tiefenmasken/Motiv, Uhr und Wetter-Widget, Wetter- und Jahreszeiten-Partikel, zuoberst die Fensterscheibe (`WindowGlass`). Wetter verändert das Foto nur über Farbfilter.
- **`Scene.java`**: On-Device-Szenenberechnung (Tiefen-Clustering, Himmel-Maske, Dunst, Pflanzen-Ebene). Schneedecke und Nässe im Foto gibt es seit 17.10. nicht mehr.
- **`WindowGlass.java`**: Scheibe vor dem Bild, über der Uhr. `Frost.java` (Eisblumen wachsen ab ~1 °C vom Rand herein, voll bei -7 °C, `Weather.frost`) und `RainOnGlass.java` (Tropfen, ruckweise herunterlaufendes Wasser mit Spur). Beide reines Java mit Selbsttest; PC-Vorschau `tools/pc_harness/GlassHarness.java`. Tropfen zeigen das Foto als Linse kopfüber, hinter dem Frost wird die Szene weichgezeichnet (`RenderNode` + `RenderEffect`, ab Android 12), nach dem Regen trocknet die Scheibe in ~3 min.
- **Himmel nach Tageszeit**: ganzes Foto, darüber der echte Himmel durch `Scene.skyBlend` (`EdgeRefine.softSky`: extrem weiche Maske + harte Maske mit Verlauf von oben 100 % zum Horizont).
- **`Season.java`**: 4 Jahreszeiten (Frühling, Sommer, Herbst, Winter) mit Farbstimmungen (Atmospheric Grading) und Partikeln (Kirschblüten, Herbstlaub).
- **`WeatherFx.java`**: Partikelsysteme für Regen, Schnee, Gewitterblitze, Nebel und saisonale Partikel.
- **`PreviewView.java`**: Interaktive Vorschau mit unabhängigem Drag & Drop für Hauptuhr und Lockscreen-Subtitle-Widget.
- **`DepthEstimator.java`**: ONNX Runtime Inferenz für `depth.onnx`.
- **`WeatherWallpaperService.java`**: der Live-Wallpaper-Dienst. Liest den veröffentlichten Stand über `Published.forScreen` (home → lock → App-Ordner), rechnet die Szene des nächsten Modus vor.
- **`Published.java`**: „Speichern“ kopiert den App-Ordner nach `published/lock` bzw. `published/home`. Bearbeiten ändert das laufende Wallpaper nicht.
- **Widgets**: `WidgetManager` (anlegen, kopieren, Art wechseln), `InfoWidgets`/`WidgetParts` (Inhalt und Bausteine), `ClockRenderer` (Uhr-Stile), `TextLayer` (Füllung, Kontur, Glas).
- **Wetter**: `WeatherSource` (Open-Meteo), `Weather`, `Forecast`, `LocationHelper` (grober Standort).
- **Selbsttests**: `main`-Methoden in `ClockRenderer`, `DayNight`, `EdgeRefine`, `FontFamilies`, `Frost`, `GradientStops`, `Guides`, `PhotoColors`, `Presets`, `RainOnGlass`, `SkyMask`, `SkyPosition`, `SkyTimes`, `Vegetation`, `Weather`.

## Systemuhr auf dem Sperrbildschirm ausblenden (Workaround des Users, 15.10.2026)
One UI erlaubt kein Abschalten der Systemuhr. Der User hat das so gelöst:
1. In Samsung **Good Lock → LockStar** ein eigenes Uhr-Design anlegen.
2. Als „Uhr“ dort das Wetter-Element wählen.
3. Das Wetter danach ausblenden.

Formal ist dann eine Uhr gewählt, angezeigt wird aber nichts, und die Systemuhr ist weg. Die Uhr des Wallpapers ist dann die einzige Uhr. Bei Fragen zu doppelten Uhren oder zur Systemuhr zuerst daran denken und nichts daran ändern.

## Build & Test-Kommandos
- **Java Home**: `C:\Program Files\Android\Android Studio\jbr`
- **Gradle**: `~/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle assembleDebug` (`--offline` nur, wenn das AGP-Plugin schon im Gradle-Cache liegt; am 30.10. fehlte es). Läuft OneDrive, schützt es `app/build` gegen Schreiben und Gradle bricht mit AccessDenied ab. Dann OneDrive beenden und den Schreibschutz in `app/build` entfernen, oder mit `-I tools/outside-build.gradle` bauen (Build-Ordner liegt dann in `%LOCALAPPDATA%\WetterWallpaperBuild`).
- **ADB**: `C:\Users\flapp\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- **Build-Typ**: `debug` ist absichtlich `debuggable false`. Debuggable rechnet die Szene etwa 3x langsamer (18 s statt 6 s auf dem S24). Log und `adb` funktionieren trotzdem.
- **APK Installieren**: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (beim Bauen mit dem Init-Skript liegt sie in `%LOCALAPPDATA%\WetterWallpaperBuild\app\outputs\apk\debug\`)
- **App Starten**: `adb shell "am start -n de.seelig.wetterwallpaper/.MainActivity"`

## Richtlinien für Agents
1. Vor Tastatureingaben/Touch auf dem Testgerät immer `dumpsys window | grep -E 'mCurrentFocus|isKeyguardShowing'` prüfen.
2. Niemals an den Bildschirmrändern wischen ($X < 200$ oder $X > 880$), um keine One UI Zurück-Gesten auszulösen.
3. Scene-Berechnungen sind rechenintensiv und dürfen nie im UI-Thread laufen.
4. Nach jeder Iteration `ACTIVE_STATE.md` aktualisieren.
