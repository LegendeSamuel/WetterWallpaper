# Notizen für das Data-Safety-Formular (Play Console)

Stand 11.11.2026. Grundlage sind Googles Definitionen aus
https://support.google.com/googleplay/android-developer/answer/10787469 (abgerufen am 11.11.2026):
„Collect“ heißt, Daten vom Gerät weg zu übertragen; „Sharing“ heißt, erhobene Daten an Dritte zu übertragen.
Die Antworten unten sind ein Vorschlag. Vor dem Absenden gegen die aktuellen Formulartexte prüfen.

## Was die App tatsächlich tut (aus dem Code)

| Daten | Zugriff | Verlässt das Gerät? | Stelle im Code |
|---|---|---|---|
| Ungefährer Standort (`ACCESS_COARSE_LOCATION`) | nach Freigabe, auch im Hintergrund durch den Wallpaper-Dienst | Ja: Koordinaten auf 2 Nachkommastellen an Open-Meteo, etwa alle 30 min, über HTTPS | `LocationHelper`, `WeatherSource.fetch` |
| Kalender (`READ_CALENDAR`) | nach Freigabe, nur für das Widget „Nächster Termin“ | Nein | `DeviceInfo` |
| Fotos | vom Nutzer gewählt (Photo Picker, „Teilen“) | Nein. Ausnahme: Favorit als ZIP teilen, vom Nutzer ausgelöst | `MainActivity`, `Presets` |
| Akkustand | für das Akku-Widget | Nein | `DeviceInfo` |
| Einstellungen | privater App-Speicher | Nein | `Settings` |

Kein Konto, keine Werbung, keine Analyse-, Crash- oder Tracking-Bibliothek. Abhängigkeiten sind nur ONNX Runtime,
AndroidX und Material Components; keine davon überträgt Daten.

## Vorschlag für die Antworten

- **Erhebt oder teilt die App Nutzerdaten?** Ja, wegen des Standorts.
- **Standort → Ungefährer Standort**
  - Erhoben: Ja. Die Koordinaten werden vom Gerät weg übertragen.
  - Geteilt: Ja. Empfänger ist Open-Meteo, ein Dritter. Die Ausnahme „Dienstleister, der im Auftrag des Entwicklers
    verarbeitet“ passt beim kostenlosen Open-Meteo-Zugang vermutlich nicht, weil es keinen Auftragsvertrag gibt. **Prüfen.**
  - Zweck: App-Funktionalität.
  - Pflicht oder optional: optional. Ohne Freigabe nimmt die App die Mitte Deutschlands an.
  - Hinweis: Der Entwickler selbst erhält die Daten nie. Das Formular fragt trotzdem nach allem, was die App überträgt.
- **Kalender, Fotos, Akku:** nicht anzugeben, weil nichts davon das Gerät verlässt. Beim geteilten Favoriten handelt
  der Nutzer selbst; Googles Text zu nutzerinitiierten Übertragungen dazu noch einmal lesen.
- **Daten bei der Übertragung verschlüsselt?** Ja (HTTPS).
- **Können Nutzer die Löschung verlangen?** Es gibt kein Konto und keine beim Entwickler gespeicherten Daten.
  Die passende Antwort im Formular wählen.
- **IP-Adresse:** Open-Meteo sieht sie technisch bei jedem Abruf. Die App selbst liest oder nutzt sie nicht.
  Googles Hinweise zur IP-Adresse im Formular dazu lesen.

## Weitere Angaben in der Play Console

- **Werbung:** Nein.
- **App-Zugriff:** Alle Funktionen ohne Anmeldung nutzbar.
- **Zielgruppe:** nicht an Kinder gerichtet. Altersgruppe wählen.
- **Inhaltsfreigabe:** Fragebogen ausfüllen (keine Gewalt, keine Nutzerinhalte, kein Kauf).
- **Berechtigungen:** `ACCESS_COARSE_LOCATION`, `INTERNET`, `READ_CALENDAR`. Kein `ACCESS_BACKGROUND_LOCATION`.

## Offene Punkte

1. **Standort im Hintergrund.** Der Wallpaper-Dienst fragt den Standort etwa alle 30 Minuten ab, auch wenn die App
   nicht offen ist, und zwar ohne `ACCESS_BACKGROUND_LOCATION`. Am Gerät prüfen, ob Android dem Dienst dann noch
   einen Standort liefert. Falls die Hintergrund-Berechtigung nötig wäre, verlangt Play dafür eine eigene Erklärung.
2. **Android-Sicherung.** Im Manifest steht kein `android:allowBackup`, der Standard ist „an“. Android kann
   Einstellungen und Fotos der App dann im Google-Konto des Nutzers sichern. Entscheiden, ob das so bleiben soll
   (die Datenschutzerklärung nennt es) oder ob die Sicherung abgeschaltet wird. Das wäre eine Änderung an der App.
