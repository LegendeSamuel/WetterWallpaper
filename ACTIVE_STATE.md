# ACTIVE_STATE

**Last Updated:** 2026-11-11 14:40
**Active Agent:** Claude Code (Fable 5.2)
**Current Goal:** Repo auf Portfolio-Stand bringen (Plan A, Phasen 0–5) und Play Store vorbereiten (Plan B, Phase 6). Keine Funktionsänderungen an der App. Phasen 0 und 1 sind fertig, als Nächstes Phase 2 (Gradle-Wrapper, sauberer Klon außerhalb von OneDrive).

## 11.11. 14:40: Phase 1 fertig (Schrift, Lizenzen, Repo-Hygiene)
- **Entscheidungen:** Lizenz des Codes = MIT (`LICENSE`, Inhaber „Samuel Seelig“, vom User noch zu bestätigen). Debuggable-Zahl (3× oder 10×) wird später am Gerät gemessen, bis dahin keine Zahl im README.
- **Schrift:** Lexend 200–700 als statische TTF in `res/font/lexend_*.ttf` (aus `googlefonts/lexend`, Commit 7894f02b2e), `licenses/OFL-Lexend.txt`. `res/font/app_sans.xml` ersetzt `sofia_pro.xml`, `themes.xml` zeigt an 33 Stellen darauf. Im Java-Code gab es keinen Verweis auf die UI-Schrift; `FontFamilies` (Schriften des Users) ist unberührt.
- **Sofia Pro:** 6 .otf liegen jetzt in `../_fonts/SofiaPro/` (außerhalb des Repos), aus dem Index entfernt, `.gitignore` sperrt `**/sofiapro_*.otf` und `**/SofiaPro*`. In `local.properties` steht lokal `brandFonts.dir=../_fonts/SofiaPro`.
- **Gradle (`app/build.gradle`):** `BrandFontsTask`/`generateBrandFonts` legt Sofia Pro nur für die Debug-Variante unter den Lexend-Namen nach `build/generated/brandFonts/res/font`. Prüfung an `mergeReleaseResources` bricht bei Dateien mit „sofia“ im Namen ab.
- **Tests auf APK-Ebene:** (a) Debug mit `brandFonts.dir` → 6 OTF (Sofia Pro), (b) Debug ohne → 6 TTF (Lexend), (c) Release mit gesetztem `brandFonts.dir` → 6 TTF; mit einer Testdatei `sofiapro_test.otf` in `res/font` scheitern `assembleRelease` und `bundleRelease` wie gewollt. (d) frischer Klon folgt in Phase 2.
- **Neu:** `THIRD_PARTY_NOTICES.md` (ONNX Runtime MIT, Depth Anything V2 Small Apache-2.0, AndroidX/Material Apache-2.0, Lexend OFL-1.1, Open-Meteo CC BY 4.0; alle am 11.11. an der Quelle geprüft). `depth.onnx` ist per SHA-256 identisch mit `onnx/model_fp16.onnx` aus `onnx-community/depth-anything-v2-small`.
- **In der App:** ☰ → neuer Abschnitt „Info“ (`SettingsSheet.info()`): „Wetterdaten: Open-Meteo.com“ mit Link und die Lizenzliste.
- **Hygiene:** `tools/__pycache__` aus dem Index, `.gitignore` um Python, Sofia Pro und Keystore-Dateien ergänzt. Keine Schlüssel oder Mailadressen in den getrackten Dateien. Absolute Benutzerpfade nur in `AGENTS.md` (erlaubt) und einer Zeile hier.
- **Nicht geprüft:** Alles am Gerät. Lexend läuft breiter als Sofia Pro, der Check auf Abschneiden (Editor-Panels, Chips, Buttons) mit Screenshots nach `tools/out/font-check/` steht aus. Der neue Abschnitt „Info“ ist auf dem Gerät noch nicht angesehen.
- **Offen, braucht den User:**
  - `marketing/wetter-reel.html` bettet Sofia Pro in fünf Schnitten als Base64 ein (1,6 MB) und liegt im öffentlichen Repo. Nicht angefasst, weil außerhalb des Auftrags.
  - `tools/input/berge.jpg`: Herkunft und Lizenz des Fotos sind im Repo nicht vermerkt.
  - Sofia Pro steckt weiter in der Git-Historie. Bereinigung (git filter-repo + Force-Push) entscheidet der User.

## 11.11. 12:20: Portfolio-Auftrag, Phase 0 (Ist-Stand)
- **Gerät:** Per adb war kein Gerät verbunden. Der User hat den Stand vom 30.10. selbst auf dem Handy geprüft: läuft sauber.
- **Build:** `gradle -I tools/outside-build.gradle assembleDebug` (Gradle 8.13, JBR 21) läuft durch, ohne `--offline`.
- **AGENTS.md an den Code angepasst:** compileSdk/targetSdk 34 statt „targetSdk 35“, minSdk 29, AGP/Gradle-Version ergänzt; kaputter APK-Pfad repariert (zwei Steuerzeichen statt `\a`); `--offline` als Sonderfall; fehlende Klassen (`WeatherWallpaperService`, `Published`, Widgets, Wetter) und die Liste der Selbsttests ergänzt.
- **Offene Abweichung:** AGENTS.md nennt „debuggable etwa 3x langsamer (18 s statt 6 s)“, der Kommentar in `app/build.gradle` „~10x“. Nicht geändert, der User entscheidet, welche Zahl stimmt.
- **Entscheidungen des Users zum Auftrag:** UI-Schrift wird Lexend (OFL), Sofia Pro nur lokal für Debug-Builds. README englisch mit deutscher Kurzfassung. **Lizenz des Codes ist noch offen** (im Auftrag stand der Platzhalter „MIT | GPL-3.0 | keine“).
- **Zum Selbsttesten am Gerät (aus dem 30.10.):** Speichern nur Start- bzw. nur Sperrbildschirm, Uhr vor einem Widget, zweite Uhr ziehen und skalieren.

## 30.10. 09:30: Wünsche aus dem Alltagstest (gebaut, nicht installiert)
- **Ziehen-zum-Löschen entfernt:** Mülleimer-Zone, `hoveringTrash` und `Listener.onDelete` in `PreviewView` sind weg. Entfernen geht über 🗑 im Editor.
- **Mehrere Uhren / Datum & Wetter:** Gibt es Uhr bzw. Datum & Wetter schon, legt die Galerie ein Info-Widget der Art `clock_*` bzw. `widget_weather` an (`WidgetManager.addOrEnable`). ⧉ an Uhr/Datum & Wetter macht direkt so eine Kopie, die Galerie „Kopie als …“ ist entfallen. Der Renderer zeichnet `clock_*` mit `ClockRenderer` (Stil aus der Art, Format nur, wenn es eine Uhrzeit ist: `clockFormat`), `widget_weather` baut `InfoWidgets.build` als eine Zeile (Datum, Symbol, Temperatur). Grenze: keine Format-Optionen für die zusätzlichen Uhren und kein Zweizeilig/Nur-Datum beim zusätzlichen Datum & Wetter. Der Uhr-Stil `column` hat keine Galerie-Art, seine Kopie heißt in der Leiste „Eigener Text“.
- **Speichern pro Bildschirm:** Neue Klasse `Published`. „Speichern“ fragt ab Android 14 „Sperrbildschirm / Startbildschirm / Beide“ und kopiert den App-Ordner (ohne `source.jpg`, `*.tmp`, nur `modes/` als Unterordner) nach `published/lock` bzw. `published/home` (erst `.tmp`, dann umbenennen). Läuft das Wallpaper dort noch nicht, öffnet danach der System-Dialog. Der Dienst liest `Published.forScreen` (home → lock → App-Ordner), die System-Vorschau weiter den App-Ordner. Speichert man nur einen Bildschirm, behält der andere seinen bisherigen Stand. Szenen-Cache-Schlüssel und `configStamp` enthalten jetzt den Ordner. Bearbeiten ändert das laufende Wallpaper nicht mehr. Unter Android 14 gilt immer „Beide“.
- **Sonnenbogen:** Grundlinie in `WidgetParts.arc` entfernt.
- **Uhr in der Reihenfolge:** `"clock"` steht jetzt in `order`. Ganz hinten (Standard, auch ohne Eintrag) wird sie wie bisher hinter dem Motiv gezeichnet; weiter vorn in der Widget-Schleife mit `drawCut` auf der Motiv-Schwelle der Szene. Werkzeug „Reihenfolge“ gibt es jetzt auch für die Uhr.
- **Geprüft:** Gradle-Build läuft (JAVA_HOME = Android Studio `jbr`; `jdk-25` in JAVA_HOME existiert nicht mehr, `--offline` fand das AGP-Plugin nicht), APK `WetterWallpaper-debug.apk` vom 30.10. 09:25. **Nicht am Gerät geprüft**, kein Gerät per adb verbunden.
- **Offen zum Testen:** Speichern auf nur Start- bzw. Sperrbildschirm (Samsung), Uhr vor einem Widget (Ausschnitt um Motiv), zweite Uhr ziehen und skalieren.

## 23.10. 14:35: Editor-Wünsche aus dem Alltagstest (gebaut, nicht installiert)
- **Kopieren:** Knopf ⧉ (`copyButton`) zwischen 👁 und 🗑 → `WidgetManager.duplicate(id)`. Info-Widgets werden samt aller Schlüssel kopiert (tiefe Kopie von `settings.sec`) und eine Zeile darunter gesetzt (`placeBelow`). Uhr und Datum & Wetter gibt es nur einmal: Ihre Kopie öffnet die Galerie „Kopie als …“ (nur Info-Arten), das neue Widget bekommt deren Stil (`add(type, from)`, bei gekoppeltem Datum & Wetter der Stil der Uhr). Unter „Inhalt“ eines Info-Widgets wechselt „Anderes Widget, gleicher Stil“ die Art (`changeType`).
- **Hilfslinien:** Beim Ziehen in `PreviewView` rasten Kante links/Mitte/rechts bzw. oben/Mitte/unten an den Kanten und Mitten der anderen Elemente und an der Bildmitte ein (`Guides.snap`, 2 % der Breite, Tick beim Einrasten), gestrichelte hellblaue Linie über das ganze Bild. Ersetzt das alte Einrasten von `xPct` bei 50.
- **Glas-Unschärfe:** Neuer Regler „Unschärfe“ (`glassBlur`, `outlineGlassBlur`, Standard 40). `Scene.photoMid` (1/4) ist neu, `photoSoft` entsteht jetzt daraus (weniger Flimmern). `TextLayer` blendet scharf → mittel → weich über (`glassShader`). `setBackdrop` hat dafür `photoMid` als neuen Parameter.
- **Zahlen an Reglern:** `SliderInput` setzt neben jeden Regler (Panel, Effekte, Widget-Optionen, Blätter über `SheetKit.slider`) ein antippbares Zahlenfeld. Ein getippter Wert läuft über `AccessibilityNodeProvider.performAction(0, ACTION_SET_PROGRESS)`, so feuern die Listener wie beim Ziehen (`fromUser = true`, per javap an Material 1.12 geprüft). Regler, die erst beim Loslassen speichern (Ebene, SheetKit „nicht live“), bekommen den Rückruf `then`.
- **Schrift löschen:** Am gewählten eigenen Schrift-Chip erscheint ✕ → `MainActivity.confirmDeleteFont` löscht alle Dateien der Familie und entfernt den `font`-Schlüssel bei Uhr, Datum & Wetter und den Widgets der bearbeiteten Ebene. Andere Modi fallen beim Zeichnen auf die Systemschrift zurück (Datei fehlt).
- **Verlauf:** Jeder Farbpunkt hat Position und Weichheit zum nächsten Punkt (`gradientStops`, `gradientSoft`, 0..100, parallel zu `gradientColors`; Kontur mit Präfix `outline`). Rechnung in `GradientStops` (reines Java, `java GradientStops.java`). Editor in `EffectTools.stops`: `GradientBar` (Punkte ziehen, überholen sortiert um), Kreise darunter (Farbe/Entfernen/+ in der größten Lücke), Regler Position und Weichheit für den gewählten Punkt. Alte Verläufe ohne Listen sehen aus wie vorher (gleichmäßig, weich).
- **Geprüft:** Selbsttests `GradientStops` und `Guides` laufen, Gradle-Build läuft (`-I tools/outside-build.gradle`), APK `WetterWallpaper-debug.apk` vom 23.10. 14:29. **Nicht am Gerät geprüft**, das Handy war nicht per adb verbunden.
- **Offen:** Look der Unschärfe und der Hilfslinien am Gerät, Bedienung der Verlaufsleiste mit dem Finger.

## 19.10. 18:15: Neue Widgets nicht mehr an die Uhr gekoppelt
- **`WidgetManager.add()`:** Kopiert den Stil der Uhr (`LINK_KEYS`) als Startwert und setzt `linkClock = false`. Der Schalter „Stil der Uhr übernehmen“ ist bei neuen Widgets also aus. Bestehende Widgets ohne Schlüssel bleiben gekoppelt (`infoStyle`).
- **`LINK_KEYS`:** Die Kontur-Füllschlüssel (`outlineFill`, `outlineGradientColors`, `outlineGradientAngle`, `outlineGlassFrost`, `outlinePhotoBright`) sind ergänzt.
- **Geprüft:** Build läuft, APK liegt im Projektordner. **Nicht installiert**, das Handy war nicht verbunden.

## 19.10. 13:55: Kontur mit Füllarten, Glas statt Milchglas
- **Kontur:** Unter „Kontur zeigen“ stehen erst die Breite, dann die Auswahl `outlineFill` (Farbe, Verlauf, Glas, Bild) mit eigenen Schlüsseln (`outlineColor`, `outlineGradientColors`, `outlineGradientAngle`, `outlineGlassFrost`, `outlinePhotoBright`). `TextLayer.drawBaseFill(…, prefix, stroke)` zeichnet Schrift und Kontur, `TextLayer.key(prefix, key)` bildet die Schlüssel. Standardwerte kommen aus `fx()` ohne Präfix.
- **Glas:** Heißt jetzt „Glas“. Gezeichnet wird das scharfe Foto, um 3 % der Schriftgröße versetzt und leicht aufgehellt (klares Glas). Darüber liegt das weiche, weißliche Foto mit Deckkraft = „Milchig“ (`glassFrost`, Standard 50). Bei 0 % ist es klar, bei 100 % so milchig wie das alte Milchglas. Die Lichtkante gibt es nur bei der Schrift-Füllung.
- **Geprüft:** Build läuft, installiert, App startet ohne Absturz. Den Look prüft der User.

## 19.10. 13:30: Werkzeug „Farbe“ in „Füllung“ aufgegangen
- **Grund (User):** „Farbe“ und „Füllung → Farbe“ waren doppelt, unter Füllung → Farbe standen keine Farben.
- **Änderung:** Die Gruppe „Farbe“ hat nur noch die Werkzeuge Füllung und Kontur. `EffectTools` bekommt die Farbzeile (`R.id.groupColor`, ColorRow) und hängt sie bei Füllung = Farbe unter die Auswahl. In `TextPanel.tools` ist „color“ entfallen. Die Farbschlüssel (`color`, `colorNight` …) zählen jetzt zu `TOOL_KEYS["fill"]`.
- **Geprüft:** Build läuft, installiert, App startet ohne Absturz. Die Bedienung prüft der User.

## 19.10. 13:10: Farbwähler wie in Photoshop
- **`ColorPicker.java` neu** (Schnittstelle `show(ctx, initial, onPicked)` gleich, Aufrufer `ColorRow` und `EffectTools` unverändert): großes Feld (Sättigung nach rechts, Helligkeit nach oben), Farbton-Streifen rechts (oben 360, unten 0 wie in Photoshop), darüber „Neu | Vorher“ (Vorher antippen setzt zurück), darunter das Hex-Feld (gleicht sich in beide Richtungen ab).
- **Geprüft:** Build läuft, am Handy installiert. Den Dialog selbst habe ich nicht angesehen, das testet der User.
- **Offen:** RGB- und HSB-Zahlenfelder, Pipette aus dem Foto, Live-Vorschau im Wallpaper während des Wählens.

## 19.10. 12:50: Leiste animiert, halb hoch, wechselt die Seite
- **Animation:** `slideEditor(in, then)` blendet in 220 ms ein und aus, gleitet dabei 48 dp zum Rand. Die Glaskarten werden pro Bild neu gezeichnet.
- **Höhe:** `capToolCard()` legt den Inhalt der `toolCard` in einen ScrollView, der höchstens halbe Bildschirmhöhe minus Tab-Leiste groß wird. Der Rest scrollt.
- **Seitenwechsel:** `placeEditorFor(id)` (aus `showEditor` und `onSelected`): Liegt das Element unter 55 %, wandert die Leiste nach oben unter die obere Leiste, über 45 % wieder nach unten (Spielraum gegen Flattern). `layoutEditor()` dreht dabei die Reihenfolge der Kinder um (Tabs am Rand) samt Abständen. `PreviewView.position(id)` ist neu.
- **Bekannte Grenze:** Liegt die Leiste oben, verdeckt sie beim Ziehen den Mülleimer (Preview, 72 dp von oben).
- **Geprüft:** Build läuft, am Handy installiert. Der User testet selbst.

## 19.10. 12:25: Leiste unten einklappbar (Wunsch des Users)
- **Grund:** Beim Verschieben von Widgets an den unteren Rand war die Werkzeugleiste im Weg.
- **MainActivity:** Ein zweiter Tipp auf den aktiven Tab wählt ihn ab und blendet den ganzen `editor` aus (Karte und Tabs, Feld `collapsed`, `setCollapsed`). Ein Tipp auf eine freie Stelle blendet ihn wieder ein, statt wie bisher nur die Karte umzuschalten. Wenn man ein Widget anfasst, klappt die Leiste nicht mehr auf. Das Feld `toolCard` ist entfallen.
- **Geprüft:** Gradle-Build läuft, die APK liegt als `WetterWallpaper-debug.apk` im Projektordner. **Nicht am Gerät geprüft**, das Handy war nicht verbunden.

## 18.10. 01:20: Gerätetest per adb (App-Vorschau)
- **Frost** (Vorschau „Frost“, −8 °C): Die Eisfarne wachsen vom Rand herein, dahinter ist die Szene sichtbar weichgezeichnet (RenderNode-Blur läuft). Bild: `tools/out/geraet_frost.png`.
- **Regen:** Tropfen zeigen die Felsen kopfüber, Läufer mit Spur. `gfxinfo`: ~33 Bilder/s wie geplant, GPU 7 ms (Median), Gesamtdauer 30 ms, legacy jank 6 %. Bild: `tools/out/geraet_regen_ausschnitt.png`.
- **Abtrocknen:** Nach dem Wechsel auf „Klar“ blieben die Tropfen wie gewollt stehen und trocknen.
- **Regler:** 0 % und 100 % wirken (Nacht, klar). Bild: `tools/out/geraet_himmel_scharf_weich.png`, links scharf, rechts weich. Der Regler gilt pro Modus (er stand auf „gilt für Nacht“), am Ende ist er wieder auf 100 %.
- **Keine Abstürze** (`logcat -b crash` leer).
- Für spätere adb-Tests: `MSYS_NO_PATHCONV=1` in Git Bash setzen, sonst wird `/sdcard/...` verbogen. `uiautomator dump` liefert die Koordinaten.

## 18.10. 01:10: Regler für den Himmelsübergang, installiert
- **Regler:** ☰ → Szene & Effekte → „Übergang zum Himmel“ (nur bei eingeschaltetem „Himmel nach Tageszeit“), 0 = scharf, 100 % = extrem weich (Standard). Schlüssel `scene.skySoftness` (0..1).
- `EdgeRefine.softSky(..., softness)`: Radius (10 + 130·s)·k, Anteil der harten Kante am Horizont 1 − 0,75·s. Der Selbsttest prüft weich und scharf.
- `Scene.skyBlend(softness)`: Der Startwert kommt beim Laden über `SceneParams.skySoftness` (nicht im `key()`, löst also kein Neuladen aus). Bei einer anderen Weichheit (auf 5 % gerundet) rechnet Scene die Überblendung im Hintergrund aus dem Alpha von `scene.sky` neu, bis dahin gilt die alte.
- **Am Gerät:** `adb install` ging durch. Der Sperrbildschirm (Live-Wallpaper, Nacht, Nebelberg-Foto) zeigt keinen Absturz und einen weichen Übergang von den Sternen in die Berge. Die App-Vorschau konnte ich nicht öffnen, weil das Handy gesperrt war. `run-as` geht nicht, weil die APK nicht debuggable ist.

## 17.10. 22:30: Fenster ausgebaut, Himmel weich überblendet
- **Himmel (Idee des Users):** `EdgeRefine.softSky` → `Scene.skyBlend` (ALPHA_8). Zwei Lagen übereinander:
  1. die Himmelsmaske extrem weich (dreifacher Boxfilter, Radius 140 px bei 1080 Breite),
  2. die harte Maske, oben 100 % und zum Horizont jeder Spalte hin auf `HARD_AT_HORIZON` = 0,25 ausgeblendet.
  - Kombiniert als 1 − (1 − weich) · (1 − hart · Verlauf).
  - Renderer (`renderMode`): Zuerst kommt das ganze Foto (Foto-Himmel + Land). Darüber liegt eine Ebene mit echtem Himmel (Deckkraft = 1 − photoSky) und Wolken, maskiert mit `skyBlend` (DST_IN). Ohne Tiefe gilt der alte Weg.
  - Bekannte Grenze: Bei scharfen Silhouetten verschwimmt die Kante mit, z. B. die Bergspitze (`tools/out/himmel_weich_vergleich.png`, links alt, rechts neu). Für Bokeh-Fotos ist genau das gewollt. Idee: Regler „Himmelsübergang weich ↔ scharf“ pro Foto. Die Ausschnitte über Widgets (`drawCut`) zeichnen das Land ohne Himmelsschleier nach, das ist nur am Horizont sichtbar.
- **Scheibe zuoberst:** `render()` zeichnet jetzt erst `renderScene` (alter Ablauf samt Moduswechsel) und dann `glass.draw`, also auch über dem Übergangsbild. Licht und Farbfilter kommen aus dem aktuellen Modus (`glassLight`/`glassGrade`).
- **Frost-Unschärfe:** Bei Frost zeichnet der Renderer die Szene in den `RenderNode sceneNode` auf. `WindowGlass` legt sie mit `RenderEffect`-Blur (28 px) in eine Ebene, maskiert mit dem Frost-Alpha (Faktor 2,2). Das geht nur auf der Grafikkarte ab Android 12, sonst ohne Unschärfe.
- **Tropfen als Linse:** Jeder Tropfen über 2,5 px (bei 1080 Breite) zeigt das Foto 3,5-fach kopfüber, mit dem aktuellen Farbfilter. Gezeichnet über die Canvas-Matrix (`lens()`), dadurch ein einziger BitmapShader. Darüber liegt `RainOnGlass.sprite(size, true)` (nur Rand, Lichtsichel, Glanz).
- **Abtrocknen:** Nach dem Regen laufen die Läufer aus. Die Tropfen schrumpfen um 4 px pro Minute und sind nach gut 3 Minuten weg. Das geht auch ohne Animation nach der echten Zeit (`update(..., motion=false)`, dt bis 60 s). Bildrate 33 ms nur bei Regen oder laufenden Tropfen.
- **PC-Vorschau:** `Harness` schreibt zusätzlich `sky_compare.png` (alt | neu). Bei leerer sky.png nimmt es den Himmel aus der Tiefe. `GlassHarness` zeigt die Linsen. Neuer Testsatz mit echtem Himmel: `make_set.py` aus `tools/input/berge.jpg` (die Sätze `ag` und `street` haben eine leere sky.png und keinen echten Himmel).
- **Geprüft:** Alle Selbsttests (EdgeRefine samt softSky, Frost, RainOnGlass mit Abtrocknen, Vegetation, Weather). Gradle-Build läuft. APK `WetterWallpaper-debug.apk` vom 17.10. 22:23.
- **Nicht geprüft:** Alles am Gerät, das Handy war nicht verbunden. Kritisch sind: Bildrate mit bis zu 900 Linsen, `RenderNode`-Unschärfe im Live-Wallpaper und Look des Himmels beim Frosch.

## 17.10. 18:35: Fenster statt Schnee/Nässe im Foto (Entscheidung des Users nach Brainstorm)
- **Grund:** Schneedecke, Pfützen und der Himmelsübergang scheitern an Bokeh-Fotos (Frosch). KI-Varianten wurden verworfen. Gewählt wurde Idee F, „Wetter auf der Scheibe“. Fallender Schnee und fallende Blätter bleiben.
- **Entfernt:** `SnowCover.java`, `WetGround.java`, `tools/pc_harness/SnowHarness.java`, `Scene.upness`, Ebenen `wet`/`snow`/`puddles` samt Motiv-Varianten, `drawSurface` im Renderer, Schalter „Nasser Boden & Pfützen“ (`layers.wet`). Das Laden einer Szene wird dadurch ca. 4 s schneller (Schnee 2,3 s, Nässe 1,3 s, upness 0,8 s).
- **Geblieben:** die Farbfilter nach Wetter (`wetness`/`snowCover` dunkeln und entsättigen nur noch global), Dunst, Pflanzenfarben, Partikel.
- **Neu:**
  - `Frost.java`: Textur in halber Auflösung, Kanäle R = Kristalle, G = Korn, A = Bereitschaft. `apply(tex, amount)` erzeugt den Frost; Eisfarne wachsen der geschlossenen Eisschicht voraus.
  - `RainOnGlass.java`: bis 900 Tropfen, 7 Läufer. Die Läufer stocken ruckweise, schlucken Tropfen auf ihrem Weg und lassen Reste zurück. Ohne Regen ist die Scheibe sofort trocken.
  - `WindowGlass.java`: zeichnet beides zuoberst, über Uhr und Widgets und nur unter dem Blitz. Den Frost rechnet es im Hintergrund, nachts wird es gedämpft. Bei Regen läuft es mit 33 ms pro Bild.
  - `Weather.frost` = clamp((1 − °C) / 8). Neues Preset „Frost“ (−8 °C, klar), auch unter „Nur in der Vorschau ansehen“.
  - Schalter „Fenster: Eisblumen & Regentropfen“ (`layers.glass`, Standard an).
- **Geprüft:** Selbsttests Frost, RainOnGlass, Weather, EdgeRefine und Vegetation laufen. Der Gradle-Build läuft (mit `-I tools/outside-build.gradle`). APK `WetterWallpaper-debug.apk` vom 17.10. 18:30. PC-Vorschau: `tools/out/fenster_vorschau.png`.
- **Nicht geprüft:** Am Gerät (Handy war nicht per adb verbunden): Look, Bildrate und Akku bei Regen, Dauer der Frost-Berechnung auf dem Handy.
- **Offene Ideen:** Erledigt am 17.10. 22:30, siehe oben. „Himmel nach Tageszeit“ bleibt (Entscheidung des Users).

## 16.10. 20:16: Feedback aus dem ersten Test (gebaut und installiert, startet ohne Absturz)
- **Drehung:** Werkzeug Text -> Drehung (Regler -180..180, Schnellwahl 0/90/180/-90) und mit zwei Fingern (rastet bei Vielfachen von 90 Grad ein). Schlüssel `rotation`. Der Renderer dreht um die Mitte der ungedrehten Fläche; Trefferfläche, Rahmen und Verdeckung nutzen das umschließende Rechteck.
- **Neue Uhr "Spalte":** eine Ziffer pro Zeile (`style: "column"`), Optionen führende Null, 12 h (`vertical12h`), "Von unten nach oben lesen" (`columnUp`). Die alte "Vertikal" heißt jetzt "Zweizeilig". In der Widget-Galerie fehlt die Spalte noch.
- **Farbauswahl:** Automatisch, Weiß, Schwarz, + (Farbwähler), dann gemerkte Farben, dann "Aus dem Foto" (4 Fotofarben + Gegenfarbe + heller Ton, `PhotoColors.withHarmonies`).
  - Mit + gewählte Farben werden gemerkt (SharedPreferences `colors`, `saved`, max. 12), lange drücken entfernt sie.
- **Füllungen stapeln:** Über Milchglas, Bild und Hohl kann ein Verlauf liegen (`gradientOverlay`, `gradientMix`). Die Effekte (Schatten, Leuchten, Neon ...) sind weiter nur einzeln wählbar.
- **Himmel getrennt vom Foto-Filter:** neuer Schalter "Himmel nach Tageszeit" (`layers.skyReplace`, Standard an). "Foto abdunkeln (Licht & Wetter)" (`backgroundFilter`) betrifft nur noch Landschaft und Motiv.
- **Moduswechsel:** Der Service rechnet die Szene des nächsten Modus 3 Minuten vorher (`DayNight.nextChange`, `planPrefetch`), auch bei ausgeschaltetem Bildschirm, und zeichnet kurz nach dem Wechsel einen Frame. Ist die Szene noch nicht fertig, zeichnet er weiter den alten Modus (`renderer.hold`), damit Uhr und Bild zusammenpassen. Der Szenen-Cache hält 4 Einträge.
- **Nicht geprüft:** Alle neuen Punkte sind am Gerät noch nicht bedient worden.
- **Für später (Wunsch des Users):**
  - Ebenen wie in Photoshop (Idee, noch auszuarbeiten)
  - Übergang zum Himmel bei stark unscharfem Hintergrund verbessern (wirkt bei Bokeh-Fotos schlecht, siehe Frosch-Foto)

## 16.10. 15:55: Umbau nach Bedien-Labor, Schritte 1–4 (gebaut, nicht installiert)
Wahl des Users: Variante A in allen Bereichen, Einstellungen mit ☰-Symbol (aus C), Größe und Position auch mit zwei Fingern, alle 14 Ideen.
- **APK:** `WetterWallpaper-debug.apk` im Projektordner, gebaut 15:53. Der User installiert selbst.
- **Bauen:** Bei beendetem OneDrive normal mit `gradle assembleDebug --offline` (16.10. 17:18 geprüft, APK in `app/build/outputs/apk/debug/`). Läuft OneDrive, setzt es Schreibschutz auf `app/build`. Dann entweder den Schreibschutz entfernen oder `-I tools/outside-build.gradle` verwenden.
- **Geprüft:** Build ohne Fehler. Selbsttests laufen: `Presets` (`java Presets.java`), `DayNight` und ein Settings-Test (Scratchpad `settingscheck`). Die beiden letzten liefen am PC mit Android Studios `layoutlib.jar` als echtem `org.json`.
- **Nicht geprüft:** Auf dem Gerät ist nichts geöffnet worden. Das gilt für Layout, Blätter, Geste, Übergänge, Startbildschirm, Fotos pro Modus, Teilen und Import.

### Schritt 1: Oberfläche A
- `activity_main.xml`:
  - Oben: Modus-Pille (`modeButton`, Symbol und Farbe je Modus), ↶ ↷ (`undoButton`/`redoButton`), ☰ (`menuButton`), Speichern.
  - Regler-Karte: Elementzeile mit 👁 (`visibleButton`) und 🗑. Darunter ein Kopf mit Werkzeugname, „● gilt für …“ und „↺ Wie Tag“ (`resetDayButton`). Dann das Panel und die Werkzeug-Chips der Gruppe (`toolChips`).
  - Unten fünf Gruppen in `groupRow`: Text, Farbe, Effekte, Ebene, Inhalt.
- `MainActivity`: `GROUPS`/`GROUP_TOOLS`, `TOOL_KEYS` (welche Schlüssel ein Werkzeug schreibt) und `showEditor()`.
- Neues Blatt `SettingsSheet` (☰) mit Bausteinen aus `SheetKit` (Akkordeon, Schalter mit (i), Regler, Chips, Abzeichen). Abschnitte:
  - Szene & Effekte, pro Modus: Partikel, Boden, Himmel, Wolken, Filter samt Helligkeit, Jahreszeiten.
  - Favoriten
  - Bild
  - Wetter, mit „Nur in der Vorschau ansehen“
  - Bewegung & Akku
  - Start- & Sperrbildschirm
  - Standort
- **Entfernt:**
  - `sheet_settings.xml` und `dialog_layers.xml` (⋮-Menü)
  - „Vorschau simulieren“: Die Vorschau zeigt den bearbeiteten Modus, Wetter nur noch als Vorschau-Wahl.
  - Doppelte Schalter: Filter, Tag-und-Nacht-Knopf, Sichtbarkeit, Widget-Format und die führende Null im Schrift-Werkzeug.
- `DayNight` neu:
  - Blatt mit Modus-Karten (heutige Zeiten), dem Tagesband `DayNight.DayBand` (Antippen wählt den Modus) und den Regeln nach Vorrang (Nummern, oben gewinnt).
  - Eigene Zeiträume über „Neu“ und das ⋮-Menü der Karte.

### Schritt 2: Vererbung vom Tag (`Settings`)
- Der Tag steht im Wurzelobjekt. `modes.<id>` speichert nur Abweichungen, `null` = Wert entfernt. `screens.home` speichert die Abweichungen des Startbildschirms in allen Modi.
- Lesen: `sec()` liefert die zusammengeführte Kopie und legt nichts an. Schreiben: `put()` in die oberste Ebene; ein Wert gleich dem darunter löscht die Abweichung.
- `Settings.resolve(raw, mode, screen)` für den Renderer, `overridden(section)` und `resetKeys()` für Punkt und „Wie Tag“.
- **Umstellung:** `modelVersion: 2`. Alte Vollkopien werden beim Laden auf die Abweichungen reduziert, auch im Wallpaper-Service (`Settings.migrate`).
  - Folge: Werte, die erst nach dem Anlegen eines Modus am Tag dazukamen, erbt der Modus jetzt.
- Punkt (`ic_dot`) an abweichenden Werkzeugen und Gruppen, „↺ Wie Tag“ verwirft sie.

### Schritt 3: Geste, Rückgängig, Reihenfolge
- `PreviewView`: zweiter Finger skaliert (Abstand) und verschiebt (Mitte) das gegriffene oder gewählte Element.
  - Renderer: `setSize`/`sizePct`. Listener: `onResized` → `sizePct`, `xPct`, `yPct`.
- Rückgängig/Wiederholen in `MainActivity` (`commit` 700 ms nach der letzten Änderung, 60 Stände). Fotos sind davon ausgenommen.
- Werkzeug „Ebene → Reihenfolge“: Blatt mit Ziehen (`WidgetManager.showOrder`, RecyclerView 1.1 aus material).
  - Gespeichert als `order` (hinten zuerst). Der Renderer zeichnet nach `drawOrder()`; die Uhr liegt immer hinter Motiv und Widgets.
- Neu `present`: Uhr und Datum & Wetter gelten beim Entfernen als nicht vorhanden. `enabled` = Auge, der Chip bleibt dann blass.
  - Alte Configs mit `clock.enabled=false` zeigen die Uhr jetzt als blassen Chip.

### Schritt 4: Neue Funktionen
- **Übergänge:**
  - Einstellung `dayNight.transitionMin` (0–60, im Modus-Blatt).
  - Wirkung: `WallpaperRenderer.render` legt den vorigen Modus in halber Auflösung darüber und blendet ihn aus.
  - Das Bild des vorigen Modus wird alle 15 s neu gezeichnet; `DayNight.transition()` sucht dafür den letzten Wechsel.
- **Start-/Sperrbildschirm:**
  - Einstellung `screens.split`; im Editor wählbar im ☰-Blatt und im Modus-Blatt.
  - Service: `Engine.getWallpaperFlags()` (API 34): nur System → "home".
  - Wirkt nur, wenn das Wallpaper auf beiden Bildschirmen einzeln gesetzt ist.
  - Der Szenen-Cache hält jetzt 2 Schlüssel.
- **Foto pro Modus:**
  - Ablage `modes/<id>/` mit `background.png` usw.
  - Ablauf: `CropActivity.EXTRA_MODE` und `photoDir()` → `ImageImporter` schreibt dorthin.
  - Wirkung: Der Renderer nutzt `photoDir()` (in `SceneParams.photoDir`); Service und Vorschau beobachten diesen Ordner.
  - Ein Modus-Wechsel lädt das andere Foto nach (Sekunden).
- **Regen-Look:** `dayNight.rainLook`, Modus "rain", höchste Priorität (`wx.rain > 0.15` oder Gewitter). Die Vorschau zeigt im Regen-Modus Regen.
- **Favoriten teilen/importieren:**
  - Code: `Presets.zip` und `importZip` (nur bekannte Dateien, keine Pfade nach außen).
  - Teilen über den FileProvider `${applicationId}.files` (`res/xml/file_paths.xml`, `cache/share/`). Importieren über den Knopf im Abschnitt Favoriten.
  - Favoriten enthalten jetzt `modes/`-Fotos.

### Nächste Schritte
1. Der User installiert die APK und testet.
   - Erst den Start ohne Absturz, dann ☰-Blatt, Modus-Blatt, Gruppen und Punkte, Zwei-Finger-Geste, Rückgängig und Reihenfolge.
   - Dann die Funktionen aus Schritt 4.
2. Bei Abstürzen: `adb logcat -d | grep -i -A20 "FATAL\|WetterWallpaper"`.

## 16.10. 13:30: Bedien-Labor (Artefakt, kein Code geändert)
- Artefakt: https://claude.ai/artifact/S5c6BaWpsZBrX6sKAMMBio. Quelle: `scratchpad/bedien-labor.src.html`, Screenshots werden per Python eingesetzt. Die Quelle liegt nicht im Projekt.
- Ist-Stand am Handy angesehen (nur Screenshots, nichts verstellt). Befunde:
  - Obere Leiste läuft über, „Speichern“ ist abgeschnitten.
  - Element-Chips sind gequetscht: „Datum & Wetter“ wird zu „D…“.
  - 12 Werkzeuge stehen ohne Gruppen in einer Leiste.
  - Pro Modus gespeicherte Einstellungen sind nicht als solche erkennbar.
  - Doppelte Wege: Filter, Tag-und-Nacht-Einstieg, Sichtbarkeit, Widget-Format.
  - Doppelte Zeitsimulation: „Vorschau simulieren“ und Modus-Knopf.
  - Die Vorrang-Reihenfolge der Regeln ist unsichtbar.
  - Viel Erklärtext in den Blättern.
- Vier bedienbare Konzepte:
  - **A Aufgeräumt:** Modus-Pille, ein Einstellungsblatt als Akkordeon, 5 Werkzeuggruppen (Text, Farbe, Effekte, Ebene, Inhalt) mit Unterchips.
  - **B Tabs unten:** Gestalten, Zeiten, Szene, Mehr.
  - **C Ebenen (Empfehlung):** Uhr, Widgets und Szene sind Zeilen in einem dreistufigen Blatt mit aufklappbaren Gruppen. Darüber das Tagesband zur Moduswahl.
  - **D Direkt am Bild:** Wischen wechselt den Modus, eine schwebende Werkzeugleiste sitzt am Element.
- Gemeinsame Ideen im Labor:
  - Andere Modi erben vom Tag. Abweichungen bekommen einen Punkt und „↺ wie Tag“.
  - Abzeichen „gilt für …“ an allem, was pro Modus gilt.
  - Regeln nach Vorrang sortiert, oben gewinnt.
- Zukunfts-Schalter im Labor: weitere Widgets, eigener Zeitraum, Übergänge, Drehung/Position/Bewegung, Start- und Sperrbildschirm getrennt, Foto pro Modus, Regen-Look, Einheiten, Bildrate, Hilfe.
- **Nächster Schritt:** Der User schickt seine Auswahl (Knopf „Auswahl kopieren“, pro Bereich mischbar). Danach wird die MainActivity entsprechend umgebaut.

## 16.10. mittags: Zentrales Tag-, Morgen-/Abenddämmerungs-, Nacht- & Zeitintervall-System (gebaut & installiert)
User-Wunsch:
- Oben in der Leiste einen Mond-Button platzieren, der ein zentrales Tag/Nacht-Menü öffnet.
- Dort zentral zwischen den Modi wechseln (`☀️ Tag`, `🌅 Morgendämmerung`, `🌇 Abenddämmerung`, `🌙 Nacht` oder eigene Zeitintervalle), sodass **alle** Einstellungen (Position, Größe, Schrift, Farbe, Füllung, Kontur, Effekt, Deckkraft, Ebene, Uhr-Preset, Widgets) für den gewählten Modus bearbeitet werden – statt verstreuter Einzel-Schalter bei Farbe oder Uhr.
- Morgen- und Abenddämmerung getrennt bearbeitbar und schaltbar machen (`dawn` und `dusk`).
- Haken (Schalter) für:
  1. Automatischen Wechsel Tag & Nacht nach Sonnenstand (`sun`).
  2. Dämmerung nach Sonnenstand (`twilightSun`, getrennt schaltbar für `dawnSun` Morgendämmerung & `duskSun` Abenddämmerung).
  3. Feste Uhrzeit für Nacht (`nightTime`, minutengenau Von–Bis).
  4. Extra-Dämmerungs-Zeitfenster (`twilightTime`, getrennt für Morgendämmerung `twilightMorning` & Abenddämmerung `twilightEvening`).
- Eigene Zeitintervalle ohne künstliches Limit über `+ Eigenes Zeitintervall hinzufügen` (den extra 24-Stunden-Button wieder entfernt, da die 24h nur als Beispiel gemeint waren, dass es kein Limit gibt).

Umsetzung:
1. **Mond-Button in der oberen Leiste (`activity_main.xml`, `ic_day_night.xml`, `MainActivity.java`)**:
   - `dayNightButton` (in `dayNightGlass`) oben zwischen „Bild & Wetter“ und ⋮-Menü zeigt jederzeit den aktuell bearbeiteten Modus (`Tag`, `Morgen`, `Abend`, `Nacht` oder Name des Zeitintervalls) und öffnet `DayNight.showDialog(...)`.
2. **Zentrales Modus-Routing in `Settings.java`**:
   - `activeMode` (Standard `"day"`). Alle visuellen Sektionen (`clock`, `widget`, `widgetList`, `w*`, `layers`, `look`, `season`, `scene`) werden transparent in `json.modes[activeMode]` gelesen und geschrieben (Initialisierung als Kopie vom Tag-Modus inkl. Migration alter `styleNight`/`colorNight`-Felder).
   - `Settings.resolveForMode(raw, mode)` erzeugt für den Renderer ein aufgelöstes Config-Objekt für den jeweiligen Modus (`"day"`, `"dawn"`, `"dusk"`, `"night"` oder `"slot_..."`).
   - `copyFromDay(targetMode)` erlaubt im Menü per Knopfdruck „Vom Tag-Modus übernehmen“.
3. **Logik & BottomSheet-Dialog in `DayNight.java`**:
   - Prioritäts-Auflösung in `resolveMode(dn, time, lat, lon)`:
     1. Eigene Zeitintervalle (`intervals`, minutengenau `0..1439`, inkl. Mitternachts-Überlauf)
     2. Feste Dämmerungs-Zeitfenster (`twilightTime`: `twilightMorning` -> `"dawn"`, `twilightEvening` -> `"dusk"`)
     3. Festes Nacht-Zeitfenster (`nightTime` -> `"night"`)
     4. Dämmerung nach Sonnenstand (`twilightSun`, Sonnenhöhe `-7°..+4°`: vormittags `"dawn"`, nachmittags/abends `"dusk"`)
     5. Nacht nach Sonnenstand (`sun`, Sonnenhöhe `< 0°` -> `"night"`)
     6. Standard `"day"`
   - Komfort-UI im BottomSheet: Modus-Auswahl-Chips (`☀️ Tag`, `🌅 Morgendämmerung`, `🌇 Abenddämmerung`, `🌙 Nacht` + eigene Zeitintervalle), 4 Haken mit aufklappbaren Unteroptionen & `TimePickerDialog`-Buttons, unbegrenzte eigene Zeitintervalle (`+ Eigenes Zeitintervall hinzufügen`, Umbenennen, Von/Bis minutengenau ändern, Löschen).
4. **Live-Vorschau & Bereinigung redundanter Einzel-Toggles (`WallpaperRenderer.java`, `PreviewView.java`, `ColorRow.java`, `TextLayer.java`, `WidgetManager.java`)**:
   - `WallpaperRenderer` wechselt bei Modusänderung dynamisch das Profil (`activateMode`) und unterstützt `previewMode` im Editor.
   - Auswahl eines Modus im Mond-Menü schaltet sofort die Live-Vorschau (`PreviewView.setPreviewMode` + passende Sonnenstands-Uhrzeit `MORNING` / `NOON` / `EVENING` / `NIGHT` bzw. `simulateMinutes`) sowie alle UI-Panels (`setupTextPanels`, `WidgetManager.rebuildForMode`) um.
   - Alte verstreute Tag/Nacht-Umschalter in `ColorRow` und `clockContentView` entfernt.

## 16.10. nachts: Hintergrundfilter abschaltbar + Tag/Nacht-Uhren & Wechsel + 12h Formate + Widget "+"-Button (gebaut)
Feedback & Wünsche des Users:
- Freundin S20 FE: Warum kann sie nur "Home" & "Home+Sperr" wählen, User nur "Sperr"? (-> OS-Erklärung zu Android 13 vs 14).
- Widgets hinzufügen war versteckt (nicht ersichtlich, dass der kleine Teil horizontal scrollbar ist).
- Hintergrundfilter (Nacht/Wetter) ausschaltbar machen: Bild war nachts "zu grau", Freundin will einfach Hintergrund wie er ist mit Widgets.
- Farbwahl Tag/Nacht soll die gesamte Vorschau umschalten, damit man das echte Ergebnis sieht.
- Tag- und Nacht-Modus generell: komplett unterschiedliche Uhren für Tag und Nacht (Sonnenstand vs. feste Zeitintervalle). Übergänge kommen auf die Liste für später.
- 12h Format (AM/PM) vs. 24h Format.

Umsetzung:
1. **Hintergrund-Licht- & Wetterfilter abschaltbar**:
   - `layers.backgroundFilter` (Standard: true).
   - Neuer Schalter „Licht- & Wetterfilter auf Foto“ in `sheet_settings.xml` (unter Bild) und in `dialog_layers.xml` (Anzeige & Ebenen).
   - In `WallpaperRenderer.render`: Wenn ausgeschaltet, wird `grade = null`, `cm = identity`, `hazeAlpha = 0` und `sceneLight = 1f`. Das Foto behält 100% seine originalen, natürlichen Farben ohne Abdunklung oder Grauschleier bei Nacht/Regen.
2. **Prominenter "+"-Button & Fading Edge**:
   - In `activity_main.xml`: `addWidgetButton` (Amber, IconButton `@drawable/ic_add`) dauerhaft sichtbar direkt neben den Element-Chips im Header platziert. Öffnet sofort die Widget-Galerie.
   - `HorizontalScrollView` hat jetzt `android:requiresFadingEdge="horizontal"` (24dp), wodurch sichtbar ist, dass die Leiste weitergeht.
   - `WidgetManager.select(id)`: Scrollt automatisch sanft zum ausgewählten Element, wenn es angewählt oder hinzugefügt wird.
3. **Farbwahl Tag/Nacht schaltet Vorschau um**:
   - In `ColorRow.java`: Beim Umschalten zwischen Tag und Nacht im Farbwähler wird sofort `activity.preview().simulate(...)` aufgerufen (NOON für Tag, NIGHT für Nacht). Der User sieht sofort das Resultat im Gesamtbild.
4. **Tag- und Nacht-Uhren & Wechsel-Trigger**:
   - In `sheet_settings.xml`: Neuer Abschnitt „Tag- & Nacht-Wechsel“ mit Wahl zwischen „Nach Sonnenstand“ und „Feste Uhrzeit“ via RangeSlider (z. B. Tag von 07:00 bis 22:00 Uhr).
   - `WallpaperRenderer`: Berechnet `effectiveNight` basierend auf Modus (Sonnenstand via Elevation vs. Stunden-Intervall) und steuert damit Uhrfarben, Widgetfarben und Uhren-Preset.
   - `clockContentView()`: Neuer Umschalter „Tag-Uhr“ / „Nacht-Uhr“. Für die Nacht kann entweder „Wie tagsüber“ oder ein völlig eigenständiges Preset (Digital, Analog, Vertikal, Wortuhr) gewählt werden!
5. **12h (AM/PM) vs. 24h Formate**:
   - **Digitaluhr**: `HH:mm` (24h), `HH:mm:ss` (24h mit Sekunden), `h:mm a` (12h AM/PM), `h:mm` (12h ohne AM/PM), `hh:mm a` (12h mit Null).
   - **Vertikaluhr**: Schalter für „12-Stunden-Format (1–12)“ (`vertical12h`) und optionaler „AM / PM“-Schriftzug (`verticalAmPm`). `ClockRenderer.drawVertical` und `clockBox` unterstützen beides.
6. **Build**:
   - Erfolgreich mit `gradle assembleDebug` kompiliert (BUILD SUCCESSFUL in 9s). APK liegt in `app/build/outputs/apk/debug/app-debug.apk`.

## Projekt in einem Satz
Android-Live-Wallpaper (Java, kein Kotlin) für das Samsung S24 Ultra: Ein eigenes Foto wird auf dem Handy in Ebenen zerlegt (Tiefe per Depth Anything V2 über ONNX). Die Uhr sitzt zwischen den Ebenen, dazu kommen Tageszeit, Live-Wetter (Open-Meteo) und Jahreszeiten. Die App (`MainActivity`) bietet eine Live-Vorschau und Einstellungen. Akku sparen ist dem User wichtig.

## 15.10. spätnachts: Widget-Entfernung + Drag-to-Trash + Uhr-Presets + Uhr & Wetter in Galerie (gebaut & installiert)
User-Wunsch:
- Widgets konnten nicht entfernt werden → Button zum Entfernen und/oder Mülleimer-Symbol beim Ziehen an den Rand.
- Uhr und Wetter auch im Widget-Menü (Galerie), damit man sie ganz entfernen und wieder neu hinzufügen kann.
- Weitere Presets für die Uhr (u.a. Analoge Uhr, etc.).

Umsetzung:
1. **Entfernen von Widgets & Elementen**:
   - `ic_delete.xml`: Vector-Icon für Löschen hinzugefügt.
   - `activity_main.xml`: Lösch-Button (`deleteButton`) direkt oben in der Regler-Karte (`toolCard`) neben `visibleSwitch`. Jederzeit mit 1 Tipp erreichbar für das gewählte Element (Uhr, Datum & Wetter oder jedes Info-Widget).
   - Dialog-Abfrage vor dem Löschen (`confirmRemove(id)`) mit Option zum Bestätigen.
   - Nach dem Löschen: Snackbar mit „RÜCKGÄNGIG“-Button, falls man versehentlich gelöscht hat.
   - **Drag-to-Trash (Mülleimer-Symbol)**: In `PreviewView` poppt beim Ziehen eines Elements oben eine schwebende Pille „Hierher ziehen zum Entfernen“ auf. Zieht man das Element dorthin, wird die Pille leuchtend rot (`0xEEFF3B30`), vibriert haptisch (`CLOCK_TICK`) und löscht das Element beim Loslassen mit haptischer Bestätigung und Undo-Snackbar.
   - Im Werkzeug „Inhalt“ gibt es für Info-Widgets, für Datum & Wetter und für die Uhr ebenfalls den Button „[Element] entfernen“.
2. **Uhr und Datum & Wetter in der Galerie**:
   - In `InfoWidgets`: Gruppe „Uhr“ als erste Kategorie nach „Alle“.
   - 4 Uhr-Karten:
     - `clock_digital`: „Digitale Uhr“ – Klassisch einzeilig
     - `clock_analog`: „Analoge Uhr“ – Elegantes Zifferblatt mit Zeigern und Indizes
     - `clock_vertical`: „Vertikale Uhr“ – Moderne zweizeilige Digitaluhr (Stunde über Minute)
     - `clock_words`: „Wort-Uhr“ – Zeit auf Deutsch in Worten
   - Unter „Wetter“: Karte `widget_weather` („Datum & Wetter“).
   - `WidgetManager.Preview`: Jedes Uhren-Preset und Datum & Wetter hat eine maßgeschneiderte Live-Vorschaukarte in der Galerie.
   - Antippen in der Galerie (`addOrEnable`): Aktiviert das Element, stellt den Stil ein und wählt es direkt aus.
   - Wird Uhr oder Datum & Wetter entfernt, verschwindet ihr Chip aus der Elementleiste und sie werden im Wallpaper ausgeblendet.
3. **Uhren-Presets & ClockRenderer**:
   - Neue Klasse `ClockRenderer.java`:
     - **Digital**: 24h, mit Sekunden, 12h, führende Null.
     - **Analog**: Stunden- und Minutenzeiger (optional Sekundenzeiger), 4 Zifferblatt-Stile (`ticks`, `numbers4`, `numbers12`, `minimal`). Alle Zeiger und Markierungen werden über `TextLayer.draw` gerendert – dadurch wirken Neon, Glow, Schatten, Farbverläufe, Glas und Kontur nahtlos auf die analoge Uhr!
     - **Vertikal**: Stunde über Minute, wie Samsung LockStar / Google Pixel Lockscreen.
     - **Wortuhr**: Uhrzeit in deutschen Worten (`words(cal, colloquial, uppercase)`), exakt („Zehn Uhr zweiundvierzig“) oder umgangssprachlich („Halb elf“), mit Selbsttest in `ClockRenderer.main()`.
   - `WallpaperRenderer.clockBox()` und `drawClock()` delegieren an `ClockRenderer`.
   - Werkzeug „Inhalt“ steht jetzt auch der Uhr zur Verfügung (`clockContentView` in `MainActivity`): Preset-Auswahl (Digital / Analog / Vertikal / Wortuhr) mit spezifischen Optionen und Lösch-Button.


## 15./16.10. nachts: 16 Widgets + Verlauf mit 2–8 Farben (gebaut, installiert, Feintest offen)
User-Wunsch, wörtlich zusammengefasst:
- Alle 16 Widgets aus der Galerie.
- Das „+“ sitzt am Ende der Element-Leiste unten.
- Jedes Widget ist komplett einstellbar (vor allem Breite, gestapelt oder nebeneinander, Anzahl …).
- Standardmäßig gilt der Stil der Uhr, außer man stellt es pro Widget einzeln ein.
- Farbverlauf: alle Farben wählbar, 2 bis 8 Farbpunkte.

Plan (abhaken):
1. [x] `SkyTimes.java` (reines Java, Selbsttest): Auf- und Untergang, goldene/blaue Stunde (`nextGolden`/`nextBlue`), `moonName`, `daysToFullMoon`, `starScore`/`starWord`.
2. [x] `Forecast` + WeatherSource (Felder siehe Forecast.java, `forecast()`, `setWantAir()`):
   - Abruf mit `&timeformat=unixtime&timezone=auto`.
   - current zusätzlich: apparent_temperature, relative_humidity_2m, uv_index, wind_direction_10m.
   - hourly: temperature_2m, weather_code, cloud_cover, precipitation_probability (24 h).
   - daily: weather_code, temperature_2m_max/min (7 Tage).
   - minutely_15: precipitation (8 Werte).
   - Luft getrennt (air-quality-api, current=european_aqi + Pollen), nur wenn ein Luft-Widget existiert.
3. [x] `DeviceInfo` (Klasse fertig; READ_CALENDAR fehlt noch im Manifest, ebenso die Laufzeit-Anfrage beim Hinzufügen): Akku (sticky ACTION_BATTERY_CHANGED) und nächster Termin (CalendarContract.Instances, READ_CALENDAR ins Manifest, Anfrage beim Hinzufügen).
4. [x] `InfoWidgets` (Typen, Optionen `options(type)`, `build(type,cfg,Data)`) + `WidgetParts` (Parts + `measure`/`draw`); kompiliert, aber noch nicht eingebunden:
   - Typen + Options-Schema + Inhalt als Items aus Parts (Text, Icon, Balken, Bogen, Mond, Ring).
   - Anordnung `arrange` = row (nebeneinander, Spalten) oder stack (gestapelt, Zeilen).
   - Zeichnen über `TextLayer.draw` (Effekte gelten).
5. [x] Config + Renderer (WallpaperRenderer: infoCfg/infoText aus config.widgetList, infoStyle = widgetStyle mit linkClock-Standard true, LINK_KEYS-Konstante, elementIds/elementBox/position/setPosition, infoBox, drawInfo, drawCut + cutFor (Masken-Cache pro Element), wants(type), setWidgetData; SceneParams ohne Widget). Settings-Hilfen fehlen noch. Config:
   - `widgetList` (Array von Abschnittsnamen `w<ms>`).
   - Je Abschnitt: type, enabled, xPct/yPct (y = Oberkante), sizePct, `linkClock` (neu = true), Optionen, `layerThreshold`.
   - Settings bekommt Hilfen für Array und Abschnitt entfernen.
6. [x] Ebenen ohne Neuberechnung (Scene.depth8, Scene.cut(box, threshold) + autoThreshold8; frontMask nutzt depth8; depthForEditing und WidgetCut sind weg):
   - Scene behält `depth8` (byte, refined v). WidgetCut aus Scene.load und SceneParams raus.
   - Der Renderer rechnet die Masken pro Element aus depth8 im Box-Ausschnitt, mit Cache, und zeichnet die Verdeckung pro Element (`drawLand` + DST_IN).
   - Gilt auch für das bestehende Datum & Wetter.
7. [x] PreviewView generisch (Listener.onMoved(id,x,y)/onSelected(id)/onEmptyTap, outline(String id), Hit-Test über elementIds, feedWidgetData mit DeviceInfo + forecast, autoThreshold(id)). MainActivity ist noch NICHT angepasst, es nutzt noch onClockMoved/onWidgetMoved/onSelected(boolean)/outline(boolean) und kompiliert deshalb gerade nicht! PreviewView/Renderer: `elementIds()`, `elementBox(id)`, `setPosition(id)`, `Listener.onMoved(id)` und `onSelected(id)`, `outline(id)`.
8. [x] MainActivity über neue Klasse WidgetManager (Element-Leiste elementChips mit Chip „+ Widget“ am Ende, Panels pro Info-Widget in panelExtra, Inhalt-Werkzeug mit „Stil der Uhr übernehmen“ (beim Lösen kopiert copyKeys die Uhr-Werte), Optionen aus InfoWidgets.options, „Widget entfernen“, Galerie-BottomSheet mit Live-Vorschau WidgetManager.Preview), requestCalendar, Settings.widgetIds/setWidgetIds/removeSection/copyKeys/array; Service füttert Widget-Daten; Manifest READ_CALENDAR. (Alt:: feedWidgetData wie PreviewView mit DeviceInfo + setWantAir; Manifest READ_CALENDAR):
   - Element-Chips (Uhr, Datum & Wetter, …, „+“) statt Uhr/Widget-Umschalter.
   - TextPanel pro Widget dynamisch (panel_text inflaten), Spec.content = Optionsansicht.
   - „+“ öffnet ein Galerie-BottomSheet mit Vorschau-Karten. Entfernen im Inhalt-Werkzeug.
   - Lösen von „Stil der Uhr“ kopiert die Uhr-Stilwerte in den Abschnitt.
9. [x] Verlauf (TextLayer.gradientColors, EffectTools.stops): `gradientColors` (JSON-Array 2–8, "text" = Schriftfarbe; ohne Wert gilt [text, gradientColor]). UI: Stopp-Kreise antippen = Farbwähler, „+“/„−“, Richtung.
Zusätzlich erledigt (16.10.):
- Fix: Der Schalter oben in der Regler-Karte heißt „Sichtbar“, der Werkzeug-Name ist weg. Vorher las sich das wie „Kontur An“ und blendete das ganze Element aus.
- Fix: Die Ebenen-Markierung blieb hängen. PreviewView zählt `loadsInFlight` jetzt auch bei Fehlern herunter, dazu `forceHide` nach 10 s.
- Installiert (inkl. Favoriten und Effekte). Startet ohne Fehler.
10. [x] Gebaut + installiert 15.10. 22:53, startet ohne Absturz, Werkzeug-Anzeige beim Start korrigiert. NICHT selbst geprüft: Galerie öffnen/Widget hinzufügen (das + liegt am Ende der Element-Leiste), Darstellung der 16 Widgets, Optionen, Verlauf-Punkte, Masken pro Widget, Favoriten. Hinweis: mergeDex schlägt manchmal fehl (OneDrive-Sperre), einfach noch mal bauen. Bauen, installieren (Handy war zuletzt nicht verbunden; Favoriten sind auch noch ungetestet), ACTIVE_STATE.

## 15.10. nachts: Widget-Galerie (Ideen) + Favoriten/Presets (gebaut, NICHT installiert)
- **Widget-Galerie** (Artefakt https://claude.ai/artifact/KVMbJYHvX57DsGPaJwzdkA, Quelle `scratchpad/widget-galerie.html`):
  - 16 Ideen in 5 Gruppen:
    - Wetter: Stunden, Tage, Regen in 60 min, Temperatur groß, Wind/Feuchte/UV, Luft & Pollen.
    - Himmel & Licht: Sonnenbogen, goldene/blaue Stunde, Mondphase, Sternenhimmel.
    - Zeit: zweite Zeitzone, Countdown, KW.
    - Persönlich: nächster Termin, eigener Text.
    - Gerät: Akku.
  - Dazu eine Attrappe des Ablaufs „+ → Galerie (Milchglas-Blatt) → Widget im Bild“. Das „+“ sitzt wahlweise oben („+ Widget“) oder am Ende der Element-Leiste unten.
  - Der User kreuzt an und schickt die Auswahl. Danach folgt der Umbau: aus dem Umschalter Uhr/Widget wird eine Liste aller Elemente, `config.widgets[]` statt fester Abschnitte.
- **Favoriten** (Wunsch des Users: das ganze Layout samt Bild speichern, Meldung, wenn das Bild fehlt):
  - `Presets.java` (reines Java, Selbsttest `java Presets.java`): `presets/<ms>/` mit `meta.txt` (Name, Zeit) und Kopien von `config.json` + background/depth/sky/foreground.png + source.jpg.
  - `apply()` kopiert das Bild nur, wenn es im Favoriten liegt. Dateien, die dem Favoriten fehlen (z. B. foreground.png), werden entfernt. Die Einstellungen kommen zuletzt. Rückgabe: ob das Bild übernommen wurde.
  - `PresetPanel.java` ist die Leiste ganz oben im Blatt „Bild & Wetter“: „+“ = aktuellen Stand speichern (Name abfragen), Antippen = laden, lange drücken = überschreiben, umbenennen, löschen (mit Rückfrage).
  - Beim Laden: `flushSettings()`, dann `Presets.apply` im Worker, `CropActivity.clearSavedCrop` und `filesChanged()`. Fehlt das Bild, erscheint der Dialog „Bild fehlt“, danach `reloadAll()` (dismiss + `recreate()`).
- `assembleDebug` läuft durch. **Nicht installiert**, weil das Handy nicht verbunden war. Danach prüfen: App öffnen → „Bild & Wetter“ → Favoriten-Leiste, Speichern und Laden.

## 15.10. spätabends: 11 Schrift-Effekte + Seelig-Designsystem (installiert, startet)
- Der User hat aus dem Effekt-Labor gewählt: Schatten, Leuchten, Kontur, Hohl, Neon, Langer Schatten, Retro-Versatz, Farbverlauf, Ausblenden (mit einstellbarer Position/„Höhe“), Milchglas-Schrift, Bild in der Schrift. Relief wollte er nicht.
- **Modell, vier kombinierbare Werkzeuge** (Klasse `EffectTools`, Gruppen im Code gebaut):
  - **Füllung** `fill`: color, gradient (`gradientColor`, `gradientAngle`), glass (`glassFrost`, `glassEdge`), photo (`photoBright`), hollow (`hollowWidth`).
  - **Kontur** `outline` + `outlineColor` + `outlineWidth`.
  - **Effekt** `shadowStyle`: none, shadow, glow, neon (`neonColor`, `neonSize`, `neonStrength`), long (`longColor`, `longLength`, `longAngle`, `longStrength`), retro (`retroColor1`, `retroColor2`, `retroDistance`, `retroAngle`).
  - **Ausblenden** `fade` + `fadeStart` (Position), `fadeLength`, `fadeAngle`.
- **Zeichnen:** `TextLayer.draw(canvas, box, content)`.
  - Eigene Ebene (`saveLayerAlpha`) mit der Deckkraft. Effekt-Durchgänge mit BlurMaskFilter bzw. versetzten Kopien, dann Kontur (Strich), Füllung (Shader), zuletzt Ausblenden als DST_IN-Verlauf.
  - `prepare()` setzt keinen Schatten mehr. Die Standardwerte liegen in `TextLayer.FX` und `FX_COLOR`.
  - Das Widget zeichnet Text und Symbole im content-Lambda. `WeatherIcon` übernimmt Shader, Maske und Strichbreite des jeweiligen Durchgangs.
  - `Scene` hält jetzt `photo` (das Hintergrundbild) und `photoSoft` (1/12) für die Füllungen Bild und Milchglas. Der Renderer setzt pro Frame `setBackdrop(photo, soft, fit, grade)`.
- **Seelig-Designsystem** (Design-System-Artefakt https://claude.ai/artifact/NBmQBSmvpvcqeoCmQb8Kv8):
  - `themes.xml` ist fest dunkel (`Theme.Material3.Dark`) mit den Token-Farben: amber #F2A007 als primary, gewählte Chips/Umschalter (secondaryContainer) amber mit #121212, Flächen #121212/#181818/#1C1C1C, outline #B07A12.
  - Sofia Pro aus `res/font` (Schnitte 200–700, vom Handy gezogen, als `@font/sofia_pro`) für alle M3-Textrollen, Body leicht (300).
  - Chips als Pille (`Widget.Wetter.Chip.*`), Regler mit dunkler inaktiver Spur (`Widget.Wetter.Slider`), Haupt-Knöpfe in Großbuchstaben, GlassCard-Radius 20dp.
  - `WetterApp` nutzt kein DynamicColors mehr. Der Orangeton vorher kam von Material You aus dem Systemhintergrund.
- **Geprüft:** Installiert, startet, Werkzeug „Füllung“ geht auf, die obere Leiste passt (Häkchen-Icon bei „Speichern“ entfernt).
- **Nicht geprüft:** Die einzelnen neuen Effekte auf dem Gerät. Ich habe keine Einstellungen des Users verstellt.

## 15.10. abends: Regler 0–100, Effekt-Regler, Milchglas-Oberfläche
- Der User will **jede Kleinigkeit einstellbar**, mit vollem Bereich:
  - Deckkraft 0–100 (vorher ab 20), Größe 0–100 % für Uhr und Widget, Laufweite −50 bis 100.
  - In der Kopfzeile der Regler-Karte gibt es den Schalter „An“ (`visibleSwitch`). Er setzt `clock.enabled` bzw. `widget.enabled`, dieselbe Einstellung wie im ⋮-Menü.
- **Effekt-Regler** (Werkzeug „Schatten“, Gruppe `groupShadow`, `TextPanel.buildEffectOptions`):
  - Schatten: `shadowColor` (auto = kontrastierend) sowie `shadowBlur`, `shadowDistance` und `shadowStrength` (je 0–100) und `shadowAngle` (0–360, 90 = nach unten).
  - Leuchten: `glowColor` (auto = Textfarbe), `glowSize` und `glowStrength`.
  - Die Standardwerte in `TextLayer.CLOCK_SHADOW`, `WIDGET_SHADOW` und `GLOW` ergeben den bisherigen Look.
  - `ColorRow` hat eine zweite Form für Effektfarben, ohne Tag/Nacht.
- **Milchglas:**
  - Neue Klasse `GlassCard` (FrameLayout). Sie zeichnet `PreviewView.backdrop()` (Software-Abbild der Vorschau in 1/6 Größe, gebündelt 250 ms nach Änderungen) per RenderNode mit `RenderEffect`-Unschärfe, ab API 31. Darüber kommt eine Tönung in colorSurfaceContainer mit ~55 % Deckung und eine feine Kante.
  - Im Einsatz für Regler-Karte, Werkzeugleiste und die Knöpfe „Bild & Wetter“ und ⋮. „Speichern“ bleibt voll.
  - BottomSheets über `MainActivity.glassify()`: Hintergrund mit Alpha 200, `FLAG_BLUR_BEHIND`, Radius 28dp.
- **Effekt-Labor** im Artefakt (https://claude.ai/artifact/FH45poQUGiwZdo9RAgZMFU, Version 2):
  - 12 Effekte mit Live-Reglern: Schatten, Leuchten, Kontur, Hohl, Neon, Langer Schatten, Retro-Versatz, Farbverlauf, Relief, Ausblenden, Milchglas-Schrift, Bild in der Schrift.
  - Der User kreuzt an und schickt die Auswahl. Danach werden die gewählten Effekte in TextLayer und in die Werkzeug-Chips eingebaut.
- Gradle hing beim ersten Bauen ca. 10 Minuten mit wenig CPU. Gestoppt und neu gebaut mit `--offline --console=plain`, das Protokoll liegt in `%TEMP%\gradle_build.log`.

## 15.10. später Nachmittag: Layout Variante C + alle 12 Ideen (am Abend installiert und geprüft: startet, Auswahl + Inhalt gehen)
- Der User hat im Layout-Labor Variante C gewählt und alle 12 Ideen angekreuzt.
- **Stand: `assembleDebug` läuft durch. Installiert ist es nicht**, weil das Handy nicht mehr per ADB verbunden war (`adb devices` leer). Nach dem Verbinden: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Danach die App öffnen und alles prüfen. Bisher ist nichts davon auf dem Gerät gesehen.
- **Layout (Variante C):**
  - `activity_main.xml`: Vollbild-`PreviewView`. Oben schwebt die Leiste mit „Bild & Wetter“, dem Menü ⋮ und „Speichern“. Unten liegt `editor` mit `toolCard` (Umschalter Uhr/Widget + genau ein Regler) und der Werkzeug-Chipleiste `toolChips`.
  - „Bild & Wetter“ öffnet ein BottomSheet `sheet_settings.xml` mit Vorschau-Simulation, Bild und Wetter. Es wird einmal aufgebaut, die Setup-Methoden nutzen `sheet.findViewById`.
  - `PreviewView`: `Listener.onSelected(widget)` beim Antippen und `onEmptyTap()` auf freier Fläche (blendet die Regler-Karte aus und ein). `outline(widget)` zeichnet einen gestrichelten Rahmen.
  - Die Sicherung des alten Layouts liegt nur im Scratchpad: `activity_main.xml.bak`.
- **Werkzeuge** (`MainActivity.TOOLS`): Schrift, Größe, Abstand, Ausrichtung, Farbe, Deckkraft, Schatten, Ebene, dazu Inhalt nur beim Widget. `panel_text.xml` hat je Werkzeug eine Gruppe, `TextPanel.showTool()`.
- **Renderer:**
  - Uhr und Widget laufen über die neue Klasse `TextLayer`: Schrift + Stärke, Größe, Laufweite, Farbe auto oder Tag/Nacht, Deckkraft, `shadowStyle` (shadow/glow/none), `align` (left/center/right, xPct ist der Ankerpunkt) und `uppercase`.
  - `WallpaperRenderer.widgetStyle()`: Das Widget erbt Farbe und Schatten von der Uhr. Mit `linkClock` übernimmt es auch Schrift, Stärke, Farbe und Schatten ganz.
  - Widget-Zeilen (`widgetLines`): `format`, `dateFormat`, `twoLines`, `lineIcons` (neue Klasse `WeatherIcon`, Linien-Symbole per Path) und `glass` (milchige Fläche, `drawGlass`).
- **Neue Klassen:**
  - `FontFamilies` (reines Java, Selbsttest `java FontFamilies.java`): fasst Dateien zu Familien zusammen und sucht die Stärke aus dem Dateinamen sowie die nächste Datei zur gewünschten Stärke.
  - `PhotoColors` (Fotofarben, Selbsttest über javac mit android.jar).
  - `ColorPicker` (Dialog mit Farbton, Sättigung, Helligkeit) und `ColorRow` (Farbwerkzeug für Tag/Nacht, feste Farben, Fotofarben, „+“).
- **Schriftstärke:** Bei Familien aus mehreren Dateien (Sofia Pro) wechselt der Regler direkt `font` zur nächsten Datei. Bei Einzeldateien wird `weight` gespeichert (Achse 'wght'), bei Systemschriften ebenfalls `weight` über `Typeface.create(base, weight, false)`.
- **Bekannte Grenzen und offen:**
  - Der Glas-Hintergrund ist eine milchige Fläche ohne echte Unschärfe.
  - Die Formatwahl des Widgets steht doppelt, im Werkzeug „Inhalt“ und im ⋮-Menü.
  - Die Datumsformate sind feste Vorgaben.

## 15.10. nachmittags: Widget als eigener Reiter, gleiche Optionen wie die Uhr
- Der User hat getestet: Speichern ist schneller, die Widget-Ebene gefällt ihm.
- **Neuer Reiter „Widget“** zwischen Uhr und Bild.
  - Uhr und Widget nutzen dasselbe Layout `res/layout/panel_text.xml`. Es ist zweimal per `<include>` in `activity_main.xml` eingebunden, mit den IDs `panelClock` und `panelWidget`.
  - Die Logik steckt in der neuen Klasse `TextPanel.java` mit einer `Spec` je Abschnitt. Abgedeckt sind Schrift, Größe, Laufweite, Farbe, Deckkraft, Schatten und Ebene, dazu „führende Null“ nur bei der Uhr.
  - Neue Optionen also nur an einer Stelle einbauen, dann gelten sie für beide.
- **Neu für beide: Deckkraft** (`clock.opacity`, `widget.opacity`, 0..1). Der Renderer skaliert damit auch den Schatten (`withAlpha`).
- Die Laufweite des Widgets hat im Renderer jetzt den Standard 0 (vorher 0,01), passend zum Regler.
- Die MainActivity ist von ~880 auf ~720 Zeilen geschrumpft. Die alten Felder für Schrift und Farbe und `setupLayer` sind weg.
- Gebaut und installiert. **Die App wurde noch nicht geöffnet und geprüft**, weil der User das Handy benutzt hat. Die Slider-Raster sind abgesichert (`TextPanel.snap`).
- **Layout-Labor (Artefakt):** https://claude.ai/artifact/FH45poQUGiwZdo9RAgZMFU
  - Quelle: `scratchpad/layout-labor.html`, also nicht im Projekt.
  - Vier bedienbare Varianten: A wie jetzt, B große Vorschau mit Blatt unten, C Antippen mit Werkzeugleiste, D Liste mit kleiner Vorschau.
  - Dazu 12 Ideen zum Ankreuzen und der Knopf „Auswahl kopieren“. Der User schickt seine Wahl in den Chat, danach wird umgebaut.

## 15.10. mittags (2): Speichern schneller, Widget mit eigener Ebene und Größe
- **Speichern dauerte lange.**
  - Ursache 1: Der Knopf öffnet die System-Vorschau. Deren neue Engine hat die Szene komplett neu berechnet, parallel zu den Engines von Sperr- und Startbildschirm.
  - Ursache 2: Der Debug-Build war debuggable, dadurch lief die Szene etwa 3x langsamer.
  - Gemessen auf dem S24: debuggable 18 s, nicht debuggable 6,2 s (erster Lauf nach der Installation, kalt).
  - Neu: `WeatherWallpaperService.sharedScene(...)` ist ein Cache für alle Engines. Schlüssel: Bild-Zeitstempel + `SceneParams.key()`. Gerechnet wird in einem Thread, die Rechenzeit steht im Log `WetterWallpaper: Szene berechnet in … ms`.
  - In `app/build.gradle`: `buildTypes { debug { debuggable false } }`.
  - `Scene.load` rechnet `landColors`, `Vegetation` und `WetGround` per CompletableFuture parallel zu `SnowCover`. Das bringt nur wenig.
  - Schritte kalt auf dem Handy: Schnee 2,3 s, Nässe 1,3 s, refineSky + widen 1,1 s, upness 0,8 s, landColors 0,5 s.
  - Nicht geprüft: ob die System-Vorschau beim Festlegen jetzt wirklich sofort aus dem Cache kommt. Ich habe das Handy nicht bedient, weil der User es benutzt hat.
- **Widget:**
  - Wird jetzt nach dem Motiv gezeichnet und hat eine eigene Ebene: `widget.layerThreshold`, fehlt der Wert = automatisch wie bei der Uhr.
  - `Scene.WidgetCut` erzeugt eine kleine ALPHA_8-Maske nur über der Widget-Fläche. Der Renderer zeichnet darüber in einem `saveLayer` den Land-Stapel (`drawLand`) und maskiert ihn per DST_IN.
  - Die Widget-Fläche für die Szene wird mit einem festen langen Datum gemessen (`LONG_DATE`), damit nicht täglich neu gerechnet wird.
  - Bedienung im Uhr-Reiter: „Widget: Größe“ (`widget.sizePct`, 3–12 %) und „Widget: Ebene“ (Automatik + Regler). Die Ebenen-Steuerung von Uhr und Widget läuft über die gemeinsame Methode `MainActivity.setupLayer`.
  - Bekannte Grenze: Überlappen sich Widget und Uhr und liegt die Uhr weiter vorn als das Widget, kann Land über die Uhr gemalt werden.
  - Noch nicht auf dem Handy angesehen, die App wurde nach der Installation nicht geöffnet.
- **Systemuhr:** Den LockStar-Trick des Users habe ich in `AGENTS.md` aufgeschrieben.

## Fix 15.10. mittags: heller Balken oben
- Ursache war `WeatherFx.cloudTexture`, nicht das Foto. Die Schattierung „von oben“ nahm für die obersten h/30 Texturzeilen `above = 0` an. Dadurch war oben ein ~39 px hoher Streifen ungeschattet und heller, nur sichtbar bei Bewölkung.
- Gleichzeitig endete die Textur hart bei halber Bildhöhe.
- Fix: Oben wird die oberste Zeile geklemmt, und das Alpha läuft im unteren Viertel der Textur weich aus (smoothstep). Gebaut und installiert. Am PC per Nachbau verglichen, auf dem Handy noch nicht angesehen.
- Aktuelles Bild auf dem Handy: gemalte Berglandschaft mit Turm (seit 15.10. 12:31), nicht mehr das Weizenfeld.

## Stand nach dieser Sitzung (Claude Code, 14./15.10.)
- **Jahreszeiten schaltbar** (3-Punkte-Menü → `dialog_layers.xml`):
  - Hauptschalter `season.enabled`.
  - `season.tint` (Farbstimmung) und `season.strength` (0..1, Standard `Season.DEFAULT_STRENGTH` = 0.6; blendet die Matrix gegen die Einheitsmatrix).
  - `season.particles` (fallende Blätter/Blüten; braucht zusätzlich `layers.particles`).
- **Änderungen sofort im Wallpaper:**
  - `Settings.save()` und `ImageImporter.importCropped()` rufen `WeatherWallpaperService.filesChanged()` auf. Das geht an alle Engines im selben Prozess, die laden und zeichnen sofort, auch unsichtbar.
  - Vorher zeigte das System beim Einschalten zuerst den alten Frame.
- **Knopf oben „Speichern“** (vorher „Aktiv“): Er speichert sofort und öffnet direkt den System-Bildschirm zum Festlegen. Ohne aktives Wallpaper heißt er „Festlegen“.
- **Schritt 1 Kanten – `EdgeRefine.java`** (reines Java, Selbsttest per `main`), eingebunden in `Scene.load`:
  - `guided`: schneller Guided Filter, legt die grobe Tiefe (518 px) an die Fotokanten an.
  - `refineSky`: Farbprüfung gegen eine Himmelsfarbe, die zeilenweise mitläuft. Unscharfe ferne Baumreihen galten in der Tiefe als Himmel. Abgelehnt wird nur, was mit dem Land verbunden ist, Wolken bleiben Himmel.
  - `widenToBlur`: Der Übergang wird so breit wie die Unschärfe, nur wo die Tiefe Himmel vermutete.
  - `landColors`: Freistellungsformel I = a·S + (1−a)·F, gegen einen hellen Saum vor dem Nachthimmel.
  - `Scene.box` heißt jetzt `EdgeRefine.box`.
- **Schritt 2 Schnee – `SnowCover.java`** (reines Java, Selbsttest):
  - Der Alphawert ist die Bereitschaft einer Stelle, Schnee zu halten. Der Renderer schneidet ihn per `SnowCover.alphaRow(snowCover)` als Alphazeile der ColorMatrix ab: wenig Schnee = nur flache Stellen, viel Schnee = fast alles. GAIN 2.4.
  - Bereitschaft:
    - großräumig geglättete Neigung (`Scene.upness`, jetzt package-private) × Sicherheit nach Nähe × (1 − `uprightness`). `uprightness` nutzt einen Strukturtensor: senkrechte Halme und Stämme zählen nicht als Boden.
    - Kappen an Oberkanten (`topCaps`)
    - auf Laub und Gras nur die hellen Oberseiten (Sprenkel), dort weichgezeichnet
    - fBm-Flecken
  - Farbe: Licht aus dem Foto, Schatten bläulich, Sonne warmweiß, Relief schimmert durch.
  - Der Renderer zeichnet Schnee nachts heller (lit mindestens 0.42) und kühl.
- **Schritt 3 Pflanzen – `Vegetation.java`** (Farbton 42–190°, Sättigung, Selbsttest):
  - `Scene.plants`/`subjectPlants` = Land bzw. Motiv mit Alpha = Pflanzenanteil. Bei unter 0,5 % Pflanzen ist der Wert null, damit kein Speicher verschwendet wird.
  - Frühling, Sommer und Herbst (`Season.onlyPlants()`) wirken nur über `WallpaperRenderer.drawPlants` mit `plantGrade`. Der Winter kühlt weiter das ganze Bild.
  - Die Herbstmatrix ist wieder kräftiger, weil sie nur noch Pflanzen trifft.
- **PC-Werkzeug `tools/pc_harness/`:**
  - `run.ps1 <testsatz-ordner…>` kompiliert gegen android.jar, führt die Selbsttests aus und erzeugt `java_night.png`, `snow_sheet.png` (alt | 0.4 | 0.75 | 1.0) und `veg_sheet.png`.
  - `make_set.py` erzeugt einen Testsatz aus einem Foto.
  - Vergleichsbilder vom Schnee liegen in `tools/out/schnee/`.

## Zustand auf dem Handy (Stand 15.10. 00:40)
- Die neueste Version ist installiert.
- Config des Users: `season.enabled=false`, `tint=false`. Der User hat das selbst so eingestellt, nicht ändern.
- **Offen beim User:**
  - Die Uhrgröße steht bei etwa 40 %, weil ein Test sie verstellt hat. Vorher waren es etwa 27 %, der User wollte sie selbst zurückstellen.
  - Die App-Vorschau steht auf der Simulation „Abend + Klar“. Das ist nur Vorschau und betrifft das Wallpaper nicht.
- Aktuelles Bild: Weizenfeld mit stark unscharfem Hintergrund.

## Immediate Next Steps
1. Feedback des Users zu den neuen Uhren-Presets (Digital, Analog, Vertikal, Wortuhr), der Widget-Entfernung (Button oben, Button im Inhalt-Tab & Drag-to-Trash Mülleimer-Pille) und der Integration in die Galerie abwarten.
2. Optional weitere Uhren-Presets oder Zeiger-Stile nach Wunsch des Users ergänzen.
3. Vor jeder Übergabe immer diese Datei aktualisieren.

## Context & Gotchas for Next Agent
- **Projektpfad:** `C:\Users\flapp\OneDrive\_Projekte\WetterWallpaper` (kein Git-Repo).
- **Build:**
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  $gradle = (Get-ChildItem -Path "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin\*\gradle-8.13\bin\gradle.bat" | Select-Object -First 1).FullName
  & $gradle assembleDebug -q
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
  ```
  Eine Neuinstallation startet die App neu, dabei geht die Simulationswahl in der Vorschau verloren.
- **Selbsttests ohne Gradle:**
  - `java app/src/main/java/de/seelig/wetterwallpaper/EdgeRefine.java`
  - SnowCover und Vegetation brauchen Noise/EdgeRefine mit im Klassenpfad, siehe `tools/pc_harness/run.ps1`.
- **Gerät:** S24 Ultra `R5CX11E1GHM`, One UI 8.5 / Android 16.
- **Privatsphäre und Bedienung am Handy (wichtig):**
  - Vor jeder Eingabe `dumpsys window | grep -E 'mCurrentFocus|isKeyguardShowing'` prüfen. Nicht stören, solange der User das Handy benutzt.
  - Screenshots nur, wenn `mCurrentFocus` die `de.seelig.wetterwallpaper/...MainActivity` ist. Einmal ist die App verlassen und der Startbildschirm des Users erfasst worden, der Screenshot wurde sofort gelöscht.
  - Keine Gesten am Rand: Chips nur antippen, wenn x zwischen 150 und 930 liegt. Die Chipleiste in der Mitte wischen (700 → 400, y = 1415). Das 3-Punkte-Menü liegt bei (966, 185) und wird mit Taste 4 (Back) geschlossen.
  - Elemente per `uiautomator dump` suchen, danach `/sdcard/ui.xml` löschen.
  - Git-Bash: `export MSYS_NO_PATHCONV=1` setzen, sonst werden `/sdcard`-Pfade verbogen.
- **Konfiguration:** `/sdcard/Android/data/de.seelig.wetterwallpaper/files/config.json` ist maßgeblich. Zum Testen nur `debug.*` ändern und danach leeren. Einstellungen des Users nach dem Test zurückstellen.
- **Architektur:**
  - `WallpaperRenderer` zeichnet für Engine und Vorschau.
  - `Scene.load` rechnet alle Ebenen einmal im Hintergrund (Executor). `SceneParams.key()` steuert, wann neu gerechnet wird.
  - Pro Frame laufen nur Bitmaps und ColorMatrix, keine Pixelschleifen.
- **Kosten der Ebenen-Berechnung:** rund 1–2 s auf dem PC pro Bild (EdgeRefine, SnowCover, Vegetation). Sie läuft nur beim Laden oder bei Änderungen.
- **Ältere Bausteine** (siehe frühere Verläufe): `WetGround` (Pfützen), `NightSky`, `Mood`, `SkyPosition`, `WeatherFx` (Partikel/Wolken), verschiebbares Widget (`widget.*`), Ebene der Uhr (`scene.subjectThreshold`).
