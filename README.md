# Wetter Wallpaper

Android-Live-Wallpaper, das aus einem beliebigen Foto eine lebendige Wetterszene macht. Die App schätzt auf dem Gerät die Tiefe des Fotos, trennt Himmel, Landschaft und Motiv und legt darüber echtes Wetter, Tageszeit, Jahreszeit und frei gestaltbare Widgets.

Entwickelt und getestet auf dem Samsung Galaxy S24 Ultra (One UI, arm64).

## Funktionen

**Szene aus dem Foto**
- Tiefenschätzung on-device mit *Depth Anything V2 Small* über ONNX Runtime, kein Upload des Fotos
- Himmelsmaske, Tiefen-Clustering, Dunst und Pflanzen-Ebene (`Scene`, `SkyMask`, `EdgeRefine`, `Vegetation`)
- Himmel nach Tageszeit mit Sonne, Mond und Sternen, weich ins Foto überblendet (Regler „Übergang zum Himmel“)

**Wetter**
- Live-Daten von [Open-Meteo](https://open-meteo.com/) (Wetter und Luftqualität), Standort grob über das Netz
- Partikel für Regen, Schnee, Gewitter, Nebel, dazu Kirschblüten und Herbstlaub je nach Jahreszeit
- Fensterscheibe vor der Szene: Eisblumen wachsen ab etwa 1 °C vom Rand herein, Regentropfen wirken als Linse und laufen ruckweise herunter, danach trocknet die Scheibe

**Tageszeiten und Modi**
- Tag, Morgen- und Abenddämmerung, Nacht, Regen-Look und eigene Zeiträume
- Wechsel nach Sonnenstand oder festen Uhrzeiten, mit einstellbarem Übergang
- Jeder Modus erbt vom Tag und speichert nur Abweichungen; Foto pro Modus möglich
- Start- und Sperrbildschirm getrennt gestaltbar

**Editor**
- Vollbild-Vorschau, Elemente per Drag & Drop, Größe und Drehung mit zwei Fingern
- Uhr in mehreren Stilen (u. a. zweizeilig, Spalte) und Widgets: Datum und Wetter, Temperatur, Regen, Stundenvorschau, Woche, Mondphase, Sonnenbogen, goldene Stunde, Sterne, Luftqualität, Akku, Countdown, nächster Termin, Zeitzone, freier Text
- Text-Füllungen (Farbe, Verlauf, Glas, Bild), Kontur, Schatten, Leuchten, Neon
- Farbwähler im Photoshop-Stil, Farben aus dem Foto, gemerkte Farben
- Rückgängig und Wiederholen, Ebenen-Reihenfolge
- Favoriten speichern sowie als ZIP teilen und importieren

## Voraussetzungen

| | |
|---|---|
| Android | ab 10 (minSdk 29), Unschärfe hinter dem Frost ab Android 12 |
| Architektur | nur `arm64-v8a` |
| Build | Android Studio (JBR, Java 17), Gradle 8.13 |
| Berechtigungen | Internet, ungefährer Standort, Kalender (für das Termin-Widget) |

## Bauen und installieren

Im Repository liegt kein Gradle-Wrapper. Gebaut wird mit einer lokalen Gradle-8.13-Installation (oder direkt aus Android Studio):

```bash
gradle assembleDebug --offline
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell "am start -n de.seelig.wetterwallpaper/.MainActivity"
```

Danach in den Android-Einstellungen „Wetter Wallpaper“ als Live-Hintergrund wählen.

Hinweise:
- Der `debug`-Build ist absichtlich nicht debuggable. Sonst rechnet die Szene etwa dreimal so lange.
- Liegt das Projekt in OneDrive, sperrt OneDrive `app/build`. Dann mit `-I tools/outside-build.gradle` bauen, der Build-Ordner liegt dann unter `%LOCALAPPDATA%\WetterWallpaperBuild`.

## Projektstruktur

```
app/src/main/
├── java/de/seelig/wetterwallpaper/
│   ├── WeatherWallpaperService.java   Live-Wallpaper-Service
│   ├── WallpaperRenderer.java         Rendering-Pipeline für Wallpaper und Vorschau
│   ├── MainActivity.java              Editor
│   ├── Scene.java, DepthEstimator.java, SkyMask.java, EdgeRefine.java
│   ├── WeatherSource.java, Weather.java, Forecast.java, WeatherFx.java
│   ├── WindowGlass.java, Frost.java, RainOnGlass.java
│   ├── DayNight.java, SkyTimes.java, SkyPosition.java, Season.java
│   ├── Settings.java, Presets.java    Konfiguration (config.json, Modus-Ebenen)
│   └── …                              Widgets, Panels, Farbwähler, Crop
├── assets/depth.onnx                  Tiefenmodell (~48 MB)
└── res/                               Layouts, Icons, Schrift
tools/
├── pc_harness/                        PC-Vorschau und Selbsttests der Bildverarbeitung
└── split_layers.py                    Foto per Tiefe in Vorder- und Hintergrund zerlegen
```

## Tests ohne Handy

Die Bildverarbeitung (`EdgeRefine`, `Frost`, `RainOnGlass`, `Vegetation`) ist reines Java mit eingebauten Selbsttests. `tools/pc_harness/run.ps1` kompiliert sie, führt die Tests aus und rendert Vorschaubilder für einen Testsatz:

```powershell
py -I tools\pc_harness\make_set.py ..\ <foto.jpg> <zielordner>   # Testsatz erzeugen (numpy, onnxruntime, Pillow)
.\tools\pc_harness\run.ps1 <zielordner>
```

## Für Mitwirkende und Agents

- [`AGENTS.md`](AGENTS.md): Architektur, Build-Befehle, Regeln für Gerätetests
- [`ACTIVE_STATE.md`](ACTIVE_STATE.md): aktueller Stand und nächste Schritte, wird nach jeder Iteration aktualisiert

## Drittanbieter

- [ONNX Runtime](https://github.com/microsoft/onnxruntime) (MIT)
- [Depth Anything V2 Small](https://github.com/DepthAnything/Depth-Anything-V2) (Apache 2.0)
- Wetterdaten: [Open-Meteo](https://open-meteo.com/) (CC BY 4.0)
- Schrift Sofia Pro: kommerzielle Schrift, Lizenz des Herstellers
