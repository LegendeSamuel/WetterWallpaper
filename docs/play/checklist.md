# Checkliste Play Store

Stand 11.11.2026. Nur Vorbereitung, es wurde nichts hochgeladen. Die Regeln unten stammen von Googles Hilfeseiten,
abgerufen am 11.11.2026. Vor dem Einreichen noch einmal dort nachsehen.

## Aktuelle Regeln (Quelle: Google)

- **Ziel-API:** Seit dem 31.08.2026 müssen neue Apps und Updates auf Android 16 (API-Level 36) oder höher zielen.
  Quelle: https://support.google.com/googleplay/android-developer/answer/11926878
- **Geschlossener Test:** Private Entwicklerkonten, die nach dem 13.11.2023 angelegt wurden, müssen vor dem Antrag
  auf Produktionszugriff einen geschlossenen Test fahren. Mindestens 12 Tester müssen 14 Tage lang ununterbrochen
  angemeldet sein. Quelle: https://support.google.com/googleplay/android-developer/answer/14151465
- **Verhaltensänderungen:** https://developer.android.com/about/versions/15/behavior-changes-15 und
  https://developer.android.com/about/versions/16/behavior-changes-16

## Stand im Repo

- [x] Branch `play-prep`: compileSdk und targetSdk 36, Android-Gradle-Plugin 8.13.2 (neueste 8.x-Version, läuft mit Gradle 8.13)
- [x] `./gradlew testDebugUnitTest assembleDebug assembleRelease bundleRelease` läuft durch
- [x] AAB: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` (etwa 63 MB)
- [x] Native Bibliotheken sind auf 16 KB ausgerichtet (`libonnxruntime.so`, `libonnxruntime4j_jni.so`: `p_align` 0x4000)
- [ ] Am Gerät getestet (siehe unten; offen sind nur noch Neustart und „Teilen“)
- [ ] `play-prep` nach `main` übernommen

## Verhaltensänderungen, im Code geprüft

| Änderung | Betrifft die App? |
|---|---|
| Edge-to-edge erzwungen (15), Opt-out entfällt (16) | Nein. `MainActivity` und `CropActivity` rufen schon `EdgeToEdge.enable` auf und werten die Insets aus. |
| Vorausschauende Zurück-Geste, `onBackPressed()` wird nicht mehr aufgerufen (16) | Ja, `CropActivity` sperrt damit „Zurück“ während der Berechnung. Gelöst über `android:enableOnBackInvokedCallback="false"` an dieser Activity. |
| Adaptive Layouts auf großen Bildschirmen (16) | Nein. Das Manifest schränkt weder Ausrichtung noch Größe ein. Auf Tablets oder Foldables ist die App aber ungetestet. |
| Foreground-Service-Änderungen (15) | Nein. Die App hat keinen Foreground-Service, nur den `WallpaperService`. |
| Feste Taktung von `scheduleAtFixedRate` (16) | Nein, wird nicht verwendet. |
| Eingeschränkte TLS-Versionen (15) | Nein. Open-Meteo wird über HTTPS abgerufen. |
| Start von Activities aus dem Hintergrund (15) | Nein. Der Dienst startet keine Activities. |
| Lokales-Netzwerk-Berechtigung (16) | Nein. Die App spricht nur Server im Internet an. |
| Standort | Keine Änderung durch API 35/36. Offen bleibt der Abruf im Hintergrund, siehe `data-safety.md`. |
| Kalender | Keine Änderung durch API 35/36. |
| Schriftmaße (`elegantTextHeight`, TextView-Breiten, 15/16) | Möglich. Zusammen mit dem Lexend-Check am Gerät ansehen. |

## Am Gerät testen (Build von `play-prep`)

Getestet am 11.11.2026 per adb auf dem Galaxy S24 Ultra (Android 17). Screenshots liegen lokal in `tools/out/device-test/`.

- [x] App startet, Editor und alle Blätter sitzen richtig unter Statusleiste und über der Navigationsleiste
- [x] Zuschneiden: „Zurück“ während der Berechnung bleibt gesperrt (zweimal gedrückt, Activity blieb), danach geht es wieder
- [x] Speichern nur auf Sperrbildschirm und nur auf Startbildschirm: der jeweils andere Stand bleibt unverändert (per Prüfsumme). „Beide“ nicht eigens getestet.
- [ ] Wallpaper läuft nach Neustart des Geräts
- [x] Wetter und Standort bei geschlossener App: Der Dienst läuft als gebundener Vordergrunddienst mit Standort-Berechtigung, liest den Standort und holt das Wetter (geprüft mit dem Build von `main`, Prozesszustand auch mit `play-prep`). Die Berechtigung steht auf „nur während der Nutzung“.
- [x] Kalender-Freigabe und Widget „Nächster Termin“ (zeigt den Eintrag von heute)
- [ ] Foto wählen: Der Photo Picker öffnet sich. „Teilen → Wetter Wallpaper“ lässt sich per adb nicht prüfen (Android verweigert adb die Rechtevergabe für das Bild), bitte von Hand aus der Galerie teilen.
- [x] Schrift: nichts Wichtiges abgeschnitten (Lexend). Drei kosmetische Stellen, die es mit Sofia Pro genauso gibt: Favoriten-Kachel „Aktuelles s…“, „FOTO ÄNDERN“ zweizeilig, Vorschau der Galerie-Kachel „Datum & Wetter“ seitlich abgeschnitten.

## Schritte in der Play Console

1. [ ] Entwicklerkonto anlegen und verifizieren. Aktuelle Anforderungen an private Konten (Identität, öffentlich
   sichtbare Angaben, Gebühr) in der Play Console lesen.
2. [ ] Release-Keystore anlegen und sichern (siehe `keystore.properties.example`). Er wird bei Play App Signing zum Upload-Schlüssel.
3. [ ] App anlegen: Name, Standardsprache, App oder Spiel, kostenlos.
4. [ ] Store-Eintrag: Texte aus `store-listing.md`, Symbol, Feature-Grafik, Screenshots.
5. [ ] Datenschutzerklärung aus `privacy-policy.md` fertigstellen und unter einer öffentlichen URL ablegen.
6. [ ] App-Inhalte: Datensicherheit (`data-safety.md`), Werbung (nein), Inhaltsfreigabe, Zielgruppe, App-Zugriff.
7. [ ] Interner Test mit dem AAB, um den Upload und die Installation aus Play zu prüfen.
8. [ ] Geschlossener Test: mindestens 12 Tester, 14 Tage ununterbrochen angemeldet. Rückmeldungen sammeln, das
   Antragsformular fragt danach.
9. [ ] Produktionszugriff beantragen, dann Produktion einreichen.

## Offene Fragen für den User

1. **Open-Meteo, nicht-kommerzielle Nutzung.** Die kostenlose API ist nur für nicht-kommerzielle Nutzung erlaubt
   (https://open-meteo.com/en/terms, abgerufen am 11.11.2026). Als Beispiel für nicht-kommerziell nennt Open-Meteo
   dort private oder gemeinnützige Websites und Apps ohne Abos und ohne Werbung; freiwillige Spenden sind erlaubt.
   Eine kostenlose App ohne Werbung fällt nach diesem Wortlaut darunter. Bitte selbst prüfen und bestätigen. Sobald
   die App Geld kostet, Werbung zeigt oder ein Produkt bewirbt, ist ein API-Plan nötig. Außerdem gelten Grenzen von
   10.000 Abrufen pro Tag, 5.000 pro Stunde und 600 pro Minute; die App ruft pro Gerät etwa alle 30 Minuten ab.
2. **Standort im Hintergrund** und **Android-Sicherung:** siehe `data-safety.md`.
3. **Nur arm64 und nur Deutsch.** Schränkt Geräte und Publikum ein.
4. **App-Name.** „Wetter Wallpaper“ ist beschreibend. Vor dem Einreichen im Play Store nach gleichnamigen Apps suchen.
5. **Größe.** Das AAB hat etwa 63 MB, vor allem wegen des Tiefenmodells (50 MB). Googles aktuelle Größengrenzen prüfen.
