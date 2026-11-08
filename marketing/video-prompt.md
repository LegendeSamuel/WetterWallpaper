# Prompt: Social-Media-Video für Wetter Wallpaper

Empfohlen: Claude Opus, Aufwand „hoch“. Screenshots aus `tools/out/` als Anhang mitgeben (siehe Liste unten).

---

Du agierst als Creative Director für High-Retention Short-Form Video (Reels/TikTok/Shorts, 9:16) und als Motion Designer.

Ich entwickle allein eine Android-App namens **Wetter Wallpaper** und brauche ein Produktions-Blueprint für ein Social-Media-Video von 26 Sekunden.

### Was die App macht (das ist der Inhalt des Videos)
- **Dein eigenes Foto wird zum Live-Wallpaper.** Die App rechnet auf dem Handy eine Tiefenkarte (Depth Anything V2, offline, keine Cloud) und zerlegt das Foto in Ebenen.
- **Die Uhr steht zwischen den Ebenen.** Sie sitzt hinter dem Berg, dem Baum oder der Person. Das ist der Effekt, der sofort auffällt.
- **Das Wallpaper lebt mit dem echten Tag:** Morgen, Mittag, Golden Hour, Dämmerung und Nacht nach Sonnenstand, dazu echter Himmel mit Sternen und Mond.
- **Live-Wetter (Open-Meteo):** Regen, Schnee, Gewitter mit Blitzen und Nebel. Bei Regen laufen Tropfen über eine Glasscheibe vor dem Bild, und jeder Tropfen zeigt das Foto als Linse. Bei Frost wachsen Eisblumen vom Rand herein, und dahinter wird die Szene weich.
- **Jahreszeiten:** Farbstimmung, Kirschblüten im Frühling, Laub im Herbst.
- **Editor:** Uhr und 16 Widgets (Sonnenbogen, Mondphase, Regen in 60 min …) frei ziehen und gestalten. Es gibt Glas-Schrift, Verläufe und Neon, und für jede Tageszeit lässt sich ein eigener Look einstellen.
- Gerät: Samsung Galaxy S24 Ultra (Android 16). Status: Beta, Build-in-Public.

**USP in einem Satz:** „Dein Foto, deine Uhr hinter dem Berg, und das Wetter draußen ist das Wetter auf deinem Homescreen.“

### Vorhandenes Material (als Bilder angehängt)
- Gleicher Berg-Homescreen in 7 Zuständen: Mittag, Golden Hour, Nacht, Regen, Schnee, Gewitter, Nebel (`w_*.png`)
- Ebenen desselben Fotos: `background.png`, `foreground.png` (Berg freigestellt), `depth.png` (Tiefenkarte)
- Tempel-Szene um 12:00, 18:35 und 22:30 sowie bei Nacht mit Regen (`t3_*`, `t4_2230_rain`)
- Editor am Gerät mit Frost auf der Scheibe (`geraet_frost.png`), Nahaufnahme der Regentropfen als Linse (`geraet_regen_ausschnitt.png`)

### Meine Rahmenbedingungen
1. **Voiceover-First mit Lücken:** Ich spreche selbst, locker im Build-in-Public-Ton und nicht wie ein Werbesprecher. Wo schnelle Animationen oder Wetterwechsel laufen, setzt meine Stimme aus, damit Sound und Beat-Drops Platz haben.
2. **Visuelle Dynamik:** Kein abgefilmter Screen. Ich will harte Match-Cuts, Wetter- und Tageszeit-Wechsel im Takt, Zoom-Ins auf UI-Details und schnelle Wechsel zwischen Editor und fertigem Homescreen.
3. **Der Money-Shot ist der Ebenen-Effekt:** Die Uhr fällt hinter den Berg, danach klappen die Ebenen in 3D auseinander (Hintergrund, Uhr, Vordergrund, kurz die Tiefenkarte). Er gehört in die ersten 5 Sekunden.
4. Musik: 120 BPM, Schnitte auf dem Beat-Raster (1 Beat = 0,5 s = 15 Frames bei 30 fps).

### Deine Aufgabe
Erstelle keinen allgemeinen Marketing-Plan. Ich brauche direkt ein produktionsfertiges **3-Spalten-Skript (Timecode | Visual & Motion Design | Mein Voiceover + SFX)** mit Angaben in Sekunden:

- **00:00–00:03 Hook:** Derselbe Homescreen wechselt im 0,3-s-Takt durch Mittag, Golden Hour, Nacht, Regen und Schnee. Danach fällt die Uhr hinter den Berg. Dazu ein einziger starker Einstiegssatz für mich.
- **00:03–00:12 Ebenen und Editor:** Die Ebenen klappen in 3D auf. Danach kommt der Editor im Phone-Mockup per Spring-Animation, und die Tageszeit läuft von 12:00 über 18:35 bis 22:30 durch. Ich erkläre kurz, warum ich die App gebaut habe.
- **00:12–00:20 High-Pace Wetter-Showcase:** Frost-Eisblumen, ein Snap von Tag zu Nacht mit Regen, ein Zoom durch eine Maske in die Regentropfen-Linse, dann eine Swipe-Salve durch Gewitter, Nebel, Schnee und Golden Hour. Hier sage ich höchstens 1–2 kurze Sätze.
- **00:20–00:26 Call to Action:** Ruhiger Ausklang auf dem Ebenen-Shot, die Uhr springt eine Minute weiter. Mein letzter Satz: Beta-Tester gesucht, Link in Bio. Der letzte Frame muss sauber auf 00:00 loopen.

Liefere unter dem Skript zusätzlich **3 konkrete Motion-Design-Anweisungen**, wie ich die Screens programmatisch (Remotion oder HTML/CSS) animiere, damit es nicht nach Screenrecording aussieht. Gib dabei konkrete Werte an: Spring-Config, Bezier-Kurven, Frame-Zahlen. Halte den Ton direkt und modern, ohne Marketing-Floskeln, und schreib das Voiceover auf Deutsch.
