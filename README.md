# MiniScreen Takeover

## English

MiniScreen Takeover displays a customizable digital clock, a complete HTML design or an experimental Pebble watchface on the rear screen of the **Ulefone Armor 28 Ultra**. Controls and a pinned live preview appear on the main screen. The rear display ignores touch and navigation input and can remain visible while the phone is locked.

The app uses **display 1** for the rear screen and **display 0** for its controls. The rear screen normally measures 340 × 340 pixels. Rear-screen control depends on the manufacturer's `com.yft.miniscreendisplay` system app; this is not a general-purpose solution for arbitrary Android secondary displays.

### First-time setup

The setup assistant has three required pages, followed by optional health-data access. Progress and manual confirmations are retained when switching to Android settings or reopening the app.

1. **Choose the language.** Select English, Deutsch, 中文（简体）, Français, Türkçe, Español, Italiano or Русский, or follow the system language. The selection immediately applies to the rest of setup. Unsupported system languages use the English interface.
2. **Allow background operation.** Open Android settings and navigate to **Performance Management**, formerly **DuraSpeed**. Enable MiniScreen Takeover in its app list. Otherwise the manufacturer’s background management can terminate the app after a few minutes. Return to the assistant and confirm that you have enabled it.
3. **Prepare the rear screen and optional notification access.** Open the manufacturer's Miniscreen / Subscreen settings and apply the following configuration:

| Manufacturer setting | Required configuration |
| --- | --- |
| Screen timeout | **Never** |
| Go home time / Back to Home | **Never** |
| Open personalized signature | **Off** |
| All other switches, including call and notification options | **Off** |
| Music player switch | May remain enabled |

Scroll through every section, including those below the initial screen. Backlight level can be adjusted separately. Return to the assistant and confirm the Miniscreen configuration. These confirmations are your statements; the app does not automatically verify the manufacturer settings.

For the optional ticker or notification dot, use **Open app details** to open MiniScreen Takeover’s Android information page. In the three-dot menu at the top, choose **Allow restricted settings** and authenticate if requested. Then return to setup and separately grant **notification access**. If the restricted-settings option is absent, attempt to enable notification access first, then check the app details again. Notification access is optional: setup can finish without it. Granting access does not enable the ticker or select any apps.

General settings links request the Android settings home page with a fresh Settings task, rather than resuming the previous subpage. Dedicated links open the manufacturer settings, app details or notification-access page directly on the main screen.

After setup, the control interface opens. Use **Show on rear display** to activate the display. Setup can be reopened from the language section.

### Everyday use

Choose a native clock or HTML design in **Design & Profiles**, then adjust the appearance. The preview stays visible while scrolling through settings; optional guides mark the pixelshifting margin and appear only in the preview. Settings are applied to the active rear-screen design as you change them.

| Control | Effect |
| --- | --- |
| Show on rear display | Requests rear-screen activation and brings the Takeover display forward. |
| Hide the rear display / stop monitoring | Closes the Takeover display and stops recovery and its schedule. Does not send a physical screen-off command. |
| Turn off rear display | Stops recovery, closes the Takeover display and requests physical rear-screen power-off. A manual off remains in effect until an explicit show request or the next scheduled on time. |
| Pause | Leaves the current display in place and suspends recovery, indefinitely or for the selected duration. |
| Resume | Resumes operation subject to the selected monitoring mode and operating conditions. |

The controls can be closed while the rear display continues running. Showing the clock on a locked phone does not unlock the phone.

### Appearance and profiles

- **Clock and date:** 12/24-hour format, seconds, leading zero, independently switchable clock and date, independent colors and sizes, bold text, system fonts or imported TTF/OTF fonts, and separate horizontal/vertical positions. Date formats include presets and custom patterns such as `dd.MM.yyyy`, `EEE, dd.MM.yyyy` and `yyyy-MM-dd`.
- **Background:** solid color or an imported image, crop/fill, fit, or **Window / Schaufenster**. Window mode zooms into an image and slowly pans between positions within its bounds. Zoom, movement duration and darkening are adjustable; the image can also be removed.
- **Pixelshifting:** periodically moves content within an adjustable margin. Range is ±1–30 reference pixels and interval is 10–600 seconds. Dimensions use a 340-pixel reference and scale with the display. Pixelshifting reduces persistent loading patterns but does not eliminate burn-in risk.
- **Charging indicator:** appears while a charger is connected. Choose a battery symbol with percentage and optionally a static or animated ring around the display. Text and symbol colors, font, size, boldness, position, ring color, width and animation speed are configurable. The preview can simulate charging.
- **Profiles:** create, duplicate, rename, select, delete, import or export designs as ZIP files with their referenced images, fonts and HTML resources. Language, monitoring, scheduling, selected external apps ticker, dot and widget settings are device settings and are not included in design profiles.

### Monitoring, scheduling and other apps

| Monitoring mode | Behavior |
| --- | --- |
| Manual | Starts on request without continuous recovery after a successful start. The locked-only option can keep the service active to track lock state. |
| Keep display | Restores the display after an interruption. If Takeover loses visibility while the rear screen remains on, it yields to the other display content until an explicit show request. |
| Force Takeover | Attempts to bring Takeover back when its display is interrupted. This can displace another rear-screen app. |

**Only while locked** closes Takeover when the main screen is unlocked and allows a new display session when it is locked again. It does not physically power off the rear screen. Optional restart restoration applies after the first unlock, provided display operation was enabled. Recovery uses display and activity events plus an adjustable 15–300-second check interval; periodic checks do not continuously wake the CPU. Failed attempts wait progressively longer before retrying.

The **schedule** sets on/off times and weekdays, including periods across midnight. It uses the device’s local time zone. Exact timing depends on Android's exact-alarm permission; without it, alarms may be delayed. Pauses and an explicitly selected external app take precedence over schedule takeover.

An installed app can be selected and launched on the rear display. Takeover suspends its own display while that app is selected. **Show MiniScreen Takeover again** returns to the clock. The optional return-to-main-screen setting requests the selected app’s launcher activity on display 0 before returning to Takeover. Android and the target app may reuse a task or create another instance; the foreign app is not forcibly terminated.

### Notification ticker and privacy

Enable the ticker, grant Android notification access and select the apps to include. The app list includes launcher apps and apps observed by the notification listener. Ongoing notifications and group summaries are excluded by default; Takeover’s own notifications are always excluded.

Content levels can be configured separately for locked and unlocked states, with overrides for individual apps:

| Level | Rear-screen content |
| --- | --- |
| Discreet | App icon and notification count |
| App name | App name and a generic notification label |
| Title | App name and notification title; the title may identify the sender |
| Full | App name, title and message text supplied by Android |

The app icon is centered above the ticker text. The native ticker fits its card inside the circular display, reserving space for pixelshifting. Choose scrolling text or a static multiline card. Font, size, boldness, text/background colors, opacity, position, width, icon size, speed, display duration and pauses are adjustable. The clock can stay visible, dim or hide temporarily. The pinned preview uses a sample notification rather than real messages.

Reminder modes are **once**, **until unlocking**, **while the notification exists**, or **with a time limit**. Repeat intervals start after an individual display finishes. Unlocking acknowledges ticker reminders in the corresponding mode without dismissing Android notifications. New or changed content can be queued again. Notifications removed by Android are removed from the ticker too. The ticker does not wake a deliberately switched-off rear screen or override the schedule.

Up to 50 entries are held in memory; cached entries age out after 24 hours. Notification text is not written to profiles, exports or diagnostic logs. After a process restart, active Android notifications are read again and previous ticker acknowledgements are no longer known. Android can redact sensitive content before supplying it to the listener.

The permission to **read other apps’ notifications** is separate from Android's permission to **show Takeover’s own notifications**, including the monitoring service controls.

### Widgets and Notification Dot

**Widgets:** Enable the module, choose an installed Android widget and accept Android’s binding request. Complete any provider configuration on the main screen. Adjust position, width, height and scale using the pinned preview. Replace or remove the widget from the same section. One widget is displayed over native or HTML designs; charging, ticker and dot overlays can remain visible above it. Rear-screen interaction is disabled, so widgets that need tapping are unsuitable. Update frequency and content depend on the provider. The same binding updates the preview and rear display. Cancelling setup retains the previous widget.

**Notification Dot:** Enable it, grant notification access and choose apps independently of the ticker. Assign a color to each app and set the circle’s X/Y position and diameter. With multiple active apps, the dot cycles through each app’s color at the selected interval (1–30 seconds), regardless of the number of messages per app. Select **until unlock** or **until removed**. Unlocking acknowledges existing messages in the first mode; new or changed messages appear again. Removed notifications disappear in both modes. Ongoing notifications and group summaries can be included optionally. Pixelshifting applies and the circle is kept inside the round display. The preview can simulate the selected colors.

The dot displays no notification text and retains only app identifiers and content fingerprints in memory. A process restart loses acknowledgements and reloads active notifications. Neither module wakes a deliberately switched-off display or overrides the schedule. Widget bindings and dot settings remain device-specific and are not included in exported profiles.

### Experimental Pebble watchfaces

Enable **Use as display mode** in the **Pebble Watchfaces** section and open its settings. The switch changes the active profile’s display mode; disabling it returns to native mode. The same selection is available under Design & Profiles. Use **Show on rear display** under Controls to start the rear display. Import a `.pbw`. Emulator and PebbleOS 4.35.0 images for Gabbro, Emery and Flint are included; a matching runnable platform is selected automatically. Other platforms still require an optional compatible runtime ZIP. Configure it and operate its virtual buttons on the main screen. Rear display and preview share one virtual watch. Scaling/cropping, frame rate and optional internet access are adjustable. The Pebble watchface’s own clock is independent of the native clock switch.

The emulator requires an Android System WebView provider supporting cross-origin isolation and shared WebAssembly memory. The app enables this only for its local emulator origin; if unsupported, it shows the missing capability and the installed WebView version. Updating WebView may be required.

Complete PBW compatibility is **not established**. The runtime is experimental and depends on Android WebView capabilities, the imported firmware and the upstream installer. Runtime binaries and stock firmware for Gabbro, Emery and Flint are bundled; stock firmware does not include the Health Connect bridge. Native Android QEMU, complete Pebble Health history, phone/sensor parity and all-platform compatibility remain incomplete. Runtime packaging and exact limitations are explained in [docs/PEBBLE.md](docs/PEBBLE.md).

After the three setup pages, an optional **Health data** page offers independent steps, heart-rate and background-read permissions. It can be skipped and reopened in Pebble settings. Health Connect requires Android 14 or later; an external data source must provide measurements. Current-day steps and the newest fresh pulse are read without writing data back. A matching firmware with the included source bridge is needed to deliver them to Pebble Health; standard firmware reports the missing bridge. A maximum pulse age is configurable. Complete historical HealthService queries are not supplied.

Internet access is disabled by default. Enabling it lets watchface code contact external services and potentially transmit data available to that code. The emulator uses a local token-scoped server; native HTML designs continue to block network resources. Imported watchfaces/runtime files and third-party components retain their own licenses; see [pebble/THIRD-PARTY.md](pebble/THIRD-PARTY.md). PBWs, runtime packages and health selections are device settings, not profile exports.

### HTML designs

Import a self-contained HTML file or a ZIP containing `index.html` and local resources. HTML controls the complete display and decides how to use clock, date, background and layout settings. The app supplies data through `window.miniScreen` and the `miniscreen:update` event. Pixelshifting moves the WebView externally; charging indicators remain a native overlay.

HTML designs run locally with network access blocked and without a JavaScript-to-Android bridge. External navigation and network resources are blocked. By default the ticker is a native overlay and HTML receives no notification content. Enabling **HTML design handles notifications** supplies privacy-filtered current/queued messages for the design to render. Previously delivered text can remain in a design’s own JavaScript memory, so use designs you trust for the chosen content level.

The HTML interface is described in [docs/HTML-DESIGNS.md](docs/HTML-DESIGNS.md). Examples are available in [examples/minimal.html](examples/minimal.html), [examples/notification-clock.html](examples/notification-clock.html) and [examples/Classic-HTML-Design.zip](examples/Classic-HTML-Design.zip).

### Technical rear-screen control

Sender package: **`com.miniscreen.takeover`**. All three manufacturer commands use Android **`Context.sendBroadcast()`**, with **`Intent.setPackage("com.yft.miniscreendisplay")`**. They are package-scoped, unordered broadcasts to the manufacturer system app; the sender does not address a specific receiver component and does not use an ordered-broadcast acknowledgement.

| Exact action | Destination package | Extras | Purpose |
| --- | --- | --- | --- |
| `com.yft.miniscreen.action.GC_SCREEN_ON` | `com.yft.miniscreendisplay` | `HALL_CLOSE`: boolean `false` | Request rear-screen activation. |
| `com.yft.miniscreen.action.START_ACTIVITY` | `com.yft.miniscreendisplay` | `activity`: flattened component string | Ask the manufacturer app to launch the supplied activity on its rear screen. |
| `com.yft.miniscreen.action.GC_SCREEN_OFF` | `com.yft.miniscreendisplay` | `HALL_CLOSE`: boolean `false` | Request rear-screen power-off. The manufacturer OFF branch ignores this extra. |

For the clock, the `activity` value is:

```text
com.miniscreen.takeover/com.miniscreen.takeover.ScreenActivity
```

For another app it is the selected exported launcher component, formatted as `package/fully.qualified.ActivityClass` by `ComponentName.flattenToString()`.

When the rear display is not reported as ON, recovery sends `GC_SCREEN_ON`, allows a short activation period of approximately 700 ms, then sends `START_ACTIVITY`. An ON event may bring the launch forward. A missing private display entry does not block the launch. Launch success is evaluated from Takeover’s activity lifecycle and available display state, rather than from the broadcast call alone. Launching an external app sends ON first and its activity request after 700 ms.

Power-off cancels recovery, releases the Takeover window’s `KEEP_SCREEN_ON` flag and closes its activity. After **400 ms**, it sends `GC_SCREEN_OFF`; approximately one second later it checks the available display state. A short CPU-only wake lock protects this operation even when the main screen is locked. A missing/private display is reported as unknown, not as proof of OFF. An explicit new show request invalidates an outstanding off request.

The manufacturer app performs the privileged display operation. Takeover does not send `GC_POWER`, `GC_POWER_ON`, `GC_POWER_OFF` or synthetic Android screen-on/off broadcasts. Hiding Takeover does not send `GC_SCREEN_OFF`. Onboarding opens settings through activity intents, not manufacturer power broadcasts.

---

## Deutsch

MiniScreen Takeover zeigt eine anpassbare Digitaluhr, ein vollständiges HTML-Design oder ein experimentelles Pebble-Watchface auf dem Rückdisplay des **Ulefone Armor 28 Ultra**. Bedienung und angeheftete Live-Vorschau erscheinen auf dem Hauptbildschirm. Die Rückdisplay-Anzeige ignoriert Berührungen und Navigationseingaben und kann bei gesperrtem Handy sichtbar bleiben.

Die App verwendet **Display 1** für das Rückdisplay und **Display 0** für die Bedienung. Das Rückdisplay misst normalerweise 340 × 340 Pixel. Seine Steuerung benötigt die Hersteller-Systemapp `com.yft.miniscreendisplay`; die App ist keine allgemeine Lösung für beliebige Android-Zweitbildschirme.

### Ersteinrichtung

Der Assistent umfasst drei Seiten. Fortschritt und manuelle Bestätigungen bleiben beim Wechsel in die Android-Einstellungen und beim erneuten Öffnen erhalten.

1. **Sprache wählen.** English, Deutsch, 中文（简体）, Français, Türkçe, Español, Italiano, Русский oder die Systemsprache auswählen. Die Auswahl gilt sofort für den weiteren Assistenten. Bei nicht unterstützter Systemsprache erscheint die Oberfläche auf Englisch.
2. **Hintergrundbetrieb erlauben.** In den Android-Einstellungen **Performance Management**, früher **DuraSpeed**, öffnen und MiniScreen Takeover in der App-Liste aktivieren. Andernfalls kann die Herstellerverwaltung die App nach wenigen Minuten im Hintergrund beenden. Zum Assistenten zurückkehren und die Aktivierung bestätigen.
3. **Rückdisplay und optionalen Benachrichtigungszugriff vorbereiten.** Die Miniscreen-/Subscreen-Einstellungen des Herstellers öffnen und wie folgt einstellen:

| Hersteller-Einstellung | Erforderliche Einstellung |
| --- | --- |
| Screen timeout | **Never** |
| Go home time / Back to Home | **Never** |
| Open personalized signature | **Aus** |
| Alle übrigen Schalter, einschließlich Anruf- und Benachrichtigungsoptionen | **Aus** |
| Musikplayer-Schalter | Darf eingeschaltet bleiben |

Alle Bereiche bis ganz nach unten prüfen. Die Hintergrundbeleuchtung ist separat einstellbar. Anschließend zum Assistenten zurückkehren und die Miniscreen-Konfiguration bestätigen. Die Bestätigungen stammen vom Nutzer; die App prüft die Hersteller-Einstellungen nicht automatisch.

Für den optionalen Ticker oder Benachrichtigungspunkt über **App-Details öffnen** die Android-Infoseite von MiniScreen Takeover aufrufen. Im Drei-Punkte-Menü oben **Eingeschränkte Einstellungen zulassen** wählen und gegebenenfalls authentifizieren. Danach zum Assistenten zurückkehren und separat den **Benachrichtigungszugriff** freigeben. Fehlt der Menüpunkt, zunächst die Freigabe des Benachrichtigungszugriffs versuchen und die App-Details erneut prüfen. Die Einrichtung lässt sich ohne diesen Zugriff abschließen. Seine Freigabe aktiviert weder den Ticker noch eine App-Auswahl.

Allgemeine Einstellungslinks fordern die Android-Hauptseite mit einer neuen Settings-Task an, statt die vorherige Unterseite fortzusetzen. Gezielte Links öffnen Hersteller-Einstellungen, App-Details oder Benachrichtigungszugriff direkt auf dem Hauptbildschirm.

Nach Abschluss öffnet sich die Bedienoberfläche. **Auf Rückdisplay anzeigen** aktiviert die Anzeige. Über den Sprachbereich lässt sich die Einrichtung erneut öffnen.

### Tägliche Nutzung

Unter **Design & Profile** native Uhr oder HTML wählen und das Erscheinungsbild einstellen. Die Vorschau bleibt beim Scrollen sichtbar. Ihre optionalen Hilfslinien markieren den Pixelshift-Rand und erscheinen nur in der Vorschau. Änderungen werden auf das aktive Rückdisplay-Design angewendet.

| Steuerung | Wirkung |
| --- | --- |
| Auf Rückdisplay anzeigen | Rückdisplay aktivieren und Takeover nach vorne bringen. |
| Rückdisplay-Anzeige ausblenden / Überwachung beenden | Takeover schließen sowie Wiederherstellung und Zeitsteuerung beenden. Kein physischer Ausschaltbefehl. |
| Rückdisplay ausschalten | Wiederherstellung stoppen, Takeover schließen und physisches Ausschalten anfordern. Manuelles Ausschalten gilt bis zum ausdrücklichen Anzeigen oder nächsten geplanten Einschalten. |
| Pausieren | Aktuelle Anzeige stehen lassen und Wiederherstellung für die gewählte Dauer oder unbefristet aussetzen. |
| Fortsetzen | Betrieb unter Beachtung des gewählten Modus und der Betriebsbedingungen fortsetzen. |

Die Bedienoberfläche kann geschlossen werden, während das Rückdisplay weiterläuft. Die Anzeige auf einem gesperrten Handy entsperrt das Gerät nicht.

### Gestaltung und Profile

- **Uhr und Datum:** 12/24 Stunden, Sekunden, führende Null, unabhängig abschaltbare Uhr und Datum, getrennte Farben und Größen, fette Schrift, Systemschriften oder importierte TTF/OTF-Schriften sowie getrennte X/Y-Positionen. Datumsformate umfassen Vorlagen und eigene Muster wie `dd.MM.yyyy`, `EEE, dd.MM.yyyy` und `yyyy-MM-dd`.
- **Hintergrund:** Vollfarbe oder importiertes Bild, Zuschneiden/Füllen, Einpassen oder **Schaufenster**. Schaufenster zoomt in das Bild und bewegt sich langsam zwischen Positionen innerhalb seiner Grenzen. Zoom, Bewegungsdauer und Abdunklung sind einstellbar; das Bild lässt sich wieder entfernen.
- **Pixelshifting:** verschiebt Inhalte regelmäßig innerhalb eines einstellbaren Randes. Bereich ±1–30 Referenzpixel, Intervall 10–600 Sekunden. Größen beziehen sich auf 340 Pixel und skalieren mit dem Display. Pixelshifting reduziert feste Belastungsmuster, verhindert Einbrennen jedoch nicht vollständig.
- **Ladeanzeige:** erscheint bei angeschlossenem Ladegerät. Akkusymbol mit Prozentwert und optional statischer oder animierter Kreis um das Display. Text-/Symbolfarben, Schrift, Größe, Fett, Position sowie Kreisfarbe, Breite und Animationstempo sind einstellbar. Die Vorschau kann Laden simulieren.
- **Profile:** erstellen, duplizieren, umbenennen, auswählen, löschen sowie als ZIP mit verwendeten Bildern, Schriften und HTML-Ressourcen importieren/exportieren. Sprache, Überwachung, Zeitsteuerung, gewählte Fremd-App , Ticker-, Punkt- und Widget-Einstellungen sind Geräteoptionen und werden nicht mit Designprofilen exportiert.

### Überwachung, Zeitsteuerung und andere Apps

| Betriebsart | Verhalten |
| --- | --- |
| Manuell | Start auf Anforderung, nach erfolgreichem Start keine dauerhafte Wiederherstellung. Die Sperrbedingung kann den Dienst zur Beobachtung des Sperrzustands aktiv halten. |
| Anzeige erhalten | Anzeige nach Unterbrechungen wiederherstellen. Verliert Takeover bei weiterhin eingeschaltetem Rückdisplay seine Sichtbarkeit, wird anderer Inhalt bis zum ausdrücklichen Anzeigen respektiert. |
| Takeover erzwingen | Takeover nach einer Unterbrechung erneut nach vorne bringen. Dabei kann eine andere Rückdisplay-App verdrängt werden. |

**Nur bei gesperrtem Hauptbildschirm** schließt Takeover beim Entsperren und erlaubt beim nächsten Sperren eine neue Anzeige. Das Rückdisplay wird dabei nicht physisch ausgeschaltet. Die optionale Wiederherstellung nach Neustart erfolgt nach dem ersten Entsperren, sofern der Anzeigebetrieb aktiviert war. Die Überwachung nutzt Display-/Activity-Ereignisse und ein Kontrollintervall von 15–300 Sekunden. Regelmäßige Prüfungen wecken die CPU nicht dauerhaft. Nach Fehlern werden erneute Versuche mit zunehmender Wartezeit ausgeführt.

Die **Zeitsteuerung** legt Ein-/Ausschaltzeiten und Wochentage fest, auch über Mitternacht hinweg. Sie verwendet die lokale Zeitzone des Geräts. Exakte Zeiten hängen von Androids Berechtigung für genaue Alarme ab; ohne diese können Alarme später eintreffen. Pausen und eine ausdrücklich gewählte Fremd-App haben Vorrang vor der zeitgesteuerten Übernahme.

Eine installierte App kann ausgewählt und auf dem Rückdisplay gestartet werden. Takeover setzt seine eigene Anzeige während dieser Auswahl aus. **MiniScreen Takeover wieder anzeigen** kehrt zur Uhr zurück. Die optionale Rückkehr zum Hauptbildschirm fordert zuvor die Launcher-Activity der gewählten App auf Display 0 an. Android und die Ziel-App können eine vorhandene Task verwenden oder eine weitere Instanz öffnen; die fremde App wird nicht zwangsweise beendet.

### Benachrichtigungsticker und Datenschutz

Ticker aktivieren, Android-Benachrichtigungszugriff freigeben und Apps auswählen. Die Auswahl umfasst Apps mit Startsymbol und vom Benachrichtigungsdienst beobachtete Apps. Dauerhafte Meldungen und Gruppenzusammenfassungen sind standardmäßig ausgeschlossen; Takeover-Benachrichtigungen werden immer ausgeschlossen.

Die Inhaltsstufe ist für gesperrtes und entsperrtes Gerät getrennt wählbar, mit Ausnahmen je App:

| Stufe | Inhalt auf dem Rückdisplay |
| --- | --- |
| Diskret | App-Symbol und Anzahl |
| App-Name | App-Name und allgemeiner Benachrichtigungshinweis |
| Titel | App-Name und Titel; dieser kann den Absender enthalten |
| Vollständig | App-Name, Titel und der von Android bereitgestellte Nachrichtentext |

Das App-Symbol steht zentriert über dem Tickertext. Die native Ticker-Karte wird in die runde Displayfläche eingepasst, mit Reserve für Pixelshifting. Laufschrift oder statische mehrzeilige Karte wählen. Schrift, Größe, Fett, Text-/Hintergrundfarben, Deckkraft, Position, Breite, Symbolgröße, Geschwindigkeit, Anzeigedauer und Pausen sind einstellbar. Die Uhr kann sichtbar bleiben, abdunkeln oder vorübergehend verschwinden. Die angeheftete Vorschau verwendet eine Beispielmeldung statt echter Nachrichten.

Reminder-Modi sind **einmal**, **bis zum Entsperren**, **solange die Benachrichtigung vorhanden ist** und **mit Zeitlimit**. Die Wiederholungspause beginnt nach dem Ende einer Anzeige. Entsperren quittiert im entsprechenden Modus die Ticker-Reminder, ohne Android-Benachrichtigungen zu entfernen. Neue oder inhaltlich geänderte Meldungen können erneut aufgenommen werden. Von Android entfernte Meldungen verschwinden auch aus dem Ticker. Dieser schaltet ein bewusst ausgeschaltetes Rückdisplay nicht ein und überschreibt keine Zeitsteuerung.

Maximal 50 Einträge werden im Arbeitsspeicher gehalten; zwischengespeicherte Einträge verfallen nach 24 Stunden. Nachrichtentext wird nicht in Profile, Exporte oder Diagnoseprotokolle geschrieben. Nach Prozessneustart werden aktive Android-Benachrichtigungen erneut eingelesen; frühere Ticker-Quittierungen sind nicht mehr bekannt. Android kann sensible Inhalte bereits vor der Übergabe an den Dienst ausblenden.

Die Berechtigung zum **Lesen der Benachrichtigungen anderer Apps** ist von der Berechtigung zum **Anzeigen eigener Takeover-Benachrichtigungen**, einschließlich der Überwachungssteuerung, getrennt.

### Widgets und Notification Dot

**Widgets:** Modul einschalten, ein installiertes Android-Widget auswählen und die Android-Freigabe bestätigen. Eine gegebenenfalls erforderliche Einrichtung erfolgt am Hauptbildschirm. Position, Breite, Höhe und Skalierung mit der angehefteten Vorschau anpassen. Im selben Abschnitt lässt sich das Widget ersetzen oder entfernen. Ein Widget liegt über nativen oder HTML-Designs; Ladeanzeige, Ticker und Punkt können darüber sichtbar bleiben. Eingaben am Rückdisplay sind deaktiviert, daher eignen sich Widgets mit erforderlicher Bedienung nicht. Inhalte und Aktualisierungstempo bestimmt der Anbieter. Dieselbe Bindung aktualisiert Vorschau und Rückdisplay. Beim Abbrechen der Einrichtung bleibt das bisherige Widget erhalten.

**Notification Dot:** Einschalten, Benachrichtigungszugriff freigeben und unabhängig vom Ticker Apps auswählen. Jeder App eine Farbe zuweisen und X/Y-Position sowie Durchmesser einstellen. Bei mehreren aktiven Apps wechselt der Punkt im gewählten Intervall von 1–30 Sekunden durch deren Farben, unabhängig von der Anzahl der Meldungen pro App. **Bis zum Entsperren** oder **bis zum Entfernen** wählen. Im ersten Modus quittiert Entsperren bestehende Meldungen; neue oder geänderte erscheinen wieder. Entfernte Meldungen verschwinden in beiden Modi. Dauerhafte Meldungen und Gruppenzusammenfassungen lassen sich optional einschließen. Pixelshifting wird berücksichtigt und der Punkt bleibt innerhalb des runden Displays. Die Vorschau kann die ausgewählten Farben simulieren.

Der Punkt zeigt keine Nachrichtentexte und hält nur App-Kennungen und Inhaltsprüfsummen im Arbeitsspeicher. Ein Prozessneustart verliert Quittierungen und liest aktive Meldungen erneut ein. Keines der Module weckt ein bewusst ausgeschaltetes Display oder überschreibt die Zeitsteuerung. Widget-Bindungen und Punkt-Einstellungen sind gerätebezogen und nicht Teil exportierter Profile.

### Experimentelle Pebble-Watchfaces

Im Abschnitt **Pebble-Watchfaces** den Schalter **Als Anzeigemodus verwenden** aktivieren und die Einstellungen öffnen. Der Schalter ändert den Anzeigemodus des aktiven Profils; Ausschalten wählt die native Anzeige. Dieselbe Auswahl befindet sich unter Design & Profile. Mit **Auf Rückdisplay anzeigen** unter Steuerung das Rückdisplay starten. Eine `.pbw` importieren. Emulator und PebbleOS-4.35.0-Images für Gabbro, Emery und Flint sind enthalten; eine passende lauffähige Plattform wird automatisch gewählt. Andere Plattformen benötigen weiterhin ein optionales passendes Laufzeit-ZIP. Konfiguration und virtuelle Uhrentasten werden am Hauptbildschirm bedient. Rückdisplay und Vorschau teilen eine virtuelle Uhr. Skalierung/Zuschneiden, Bildrate und optionaler Internetzugriff sind einstellbar. Die eigene Uhr des Pebble-Watchfaces ist unabhängig vom nativen Uhr-Schalter.

Der Emulator benötigt einen Android-System-WebView-Anbieter mit Unterstützung für Isolation und gemeinsamen WebAssembly-Speicher. Die App aktiviert dies gezielt für ihren lokalen Emulator. Bei fehlender Unterstützung werden die Ursache und die installierte WebView-Version angezeigt; gegebenenfalls muss WebView aktualisiert werden.

Vollständige PBW-Kompatibilität ist **nicht bestätigt**. Die experimentelle Laufzeit hängt von Android-WebView, importierter Firmware und dem vorhandenen Installer ab. Laufzeit-Binaries und Standard-Firmware für Gabbro, Emery und Flint sind enthalten; diese Firmware enthält keine Health-Connect-Anbindung. Native Android-QEMU, vollständige Pebble-Health-Historie, vollständige Telefon-/Sensorfunktionen und die Kompatibilität aller Plattformen sind noch nicht fertig. Laufzeitpakete und genaue Grenzen beschreibt [docs/PEBBLE.md](docs/PEBBLE.md).

Nach den drei Einrichtungsseiten bietet eine optionale Seite **Gesundheitsdaten** getrennte Freigaben für Schritte, Puls und Hintergrundzugriff. Sie lässt sich überspringen und in den Pebble-Einstellungen erneut öffnen. Health Connect benötigt Android 14 oder neuer; Messwerte müssen aus einer anderen Quelle kommen. Gelesen werden die Schritte seit lokaler Mitternacht und der neueste frische Pulswert, ohne Daten zurückzuschreiben. Für die Übergabe an Pebble Health ist passende Firmware mit der enthaltenen Quellcode-Anbindung erforderlich; Standard-Firmware meldet die fehlende Anbindung. Das maximale Pulsalter ist einstellbar. Vollständige historische HealthService-Abfragen werden nicht bereitgestellt.

Internet ist standardmäßig deaktiviert. Bei Freigabe kann Watchface-Code externe Dienste erreichen und ihm verfügbare Daten übertragen. Der Emulator verwendet einen lokalen Server mit Sitzungstoken; native HTML-Designs blockieren weiterhin Netzwerkressourcen. Importierte Watchfaces/Laufzeiten und Drittkomponenten behalten ihre Lizenzen; siehe [pebble/THIRD-PARTY.md](pebble/THIRD-PARTY.md). PBWs, Laufzeitpakete und Gesundheitsfreigaben sind Geräteoptionen und werden nicht in Designprofilen exportiert.

### HTML-Designs

Eine eigenständige HTML-Datei oder ein ZIP mit `index.html` und lokalen Ressourcen importieren. HTML gestaltet die vollständige Anzeige und entscheidet, wie Uhr-, Datums-, Hintergrund- und Layout-Einstellungen verwendet werden. Daten werden über `window.miniScreen` und das Ereignis `miniscreen:update` bereitgestellt. Pixelshifting bewegt die WebView von außen; die Ladeanzeige bleibt eine native Ebene.

HTML-Designs laufen lokal mit gesperrtem Netzwerkzugriff und ohne JavaScript-Android-Bridge. Externe Navigation und Netzwerkressourcen sind gesperrt. Standardmäßig ist der Ticker eine native Ebene; HTML erhält keine Benachrichtigungsinhalte. **HTML-Design stellt Benachrichtigungen dar** übergibt datenschutzgefilterte aktuelle und wartende Meldungen zur eigenen Darstellung. Bereits übergebener Text kann im JavaScript-Speicher eines Designs verbleiben; deshalb nur Designs verwenden, denen die gewählte Inhaltsstufe anvertraut werden soll.

Die HTML-Schnittstelle steht in [docs/HTML-DESIGNS.md](docs/HTML-DESIGNS.md). Beispiele: [examples/minimal.html](examples/minimal.html), [examples/notification-clock.html](examples/notification-clock.html) und [examples/Classic-HTML-Design.zip](examples/Classic-HTML-Design.zip).

### Technische Steuerung des Rückdisplays

Absenderpaket: **`com.miniscreen.takeover`**. Alle drei Herstellerbefehle verwenden Androids **`Context.sendBroadcast()`** und **`Intent.setPackage("com.yft.miniscreendisplay")`**. Es sind ungeordnete, auf das Herstellerpaket begrenzte Broadcasts. Es wird keine konkrete Receiver-Komponente adressiert und keine Bestätigung über einen geordneten Broadcast empfangen.

| Exakte Action | Zielpaket | Extras | Zweck |
| --- | --- | --- | --- |
| `com.yft.miniscreen.action.GC_SCREEN_ON` | `com.yft.miniscreendisplay` | `HALL_CLOSE`: Boolean `false` | Einschalten des Rückdisplays anfordern. |
| `com.yft.miniscreen.action.START_ACTIVITY` | `com.yft.miniscreendisplay` | `activity`: Komponentenname als String | Die Hersteller-App zum Start der angegebenen Activity auf ihrem Rückdisplay auffordern. |
| `com.yft.miniscreen.action.GC_SCREEN_OFF` | `com.yft.miniscreendisplay` | `HALL_CLOSE`: Boolean `false` | Physisches Ausschalten anfordern. Der OFF-Zweig des Herstellers ignoriert dieses Extra. |

Für die Uhr lautet der Wert von `activity`:

```text
com.miniscreen.takeover/com.miniscreen.takeover.ScreenActivity
```

Bei einer anderen App wird ihre ausgewählte, exportierte Launcher-Komponente über `ComponentName.flattenToString()` als `Paket/vollqualifizierte.ActivityKlasse` übergeben.

Wird das Rückdisplay nicht als ON gemeldet, sendet die Wiederherstellung `GC_SCREEN_ON`, wartet ungefähr 700 ms und sendet danach `START_ACTIVITY`. Ein ON-Ereignis kann den Start vorziehen. Ein fehlender privater Displayeintrag verhindert den Start nicht. Der Erfolg wird anhand des Takeover-Activity-Lebenszyklus und des verfügbaren Displayzustands beurteilt, nicht allein anhand des Broadcast-Aufrufs. Für Fremd-Apps folgt die Activity-Anfrage 700 ms nach dem ON-Broadcast.

Beim Ausschalten werden Wiederherstellungsversuche abgebrochen, das `KEEP_SCREEN_ON`-Flag des Takeover-Fensters gelöst und seine Activity geschlossen. Nach **400 ms** wird `GC_SCREEN_OFF` gesendet; ungefähr eine Sekunde später wird der verfügbare Displayzustand geprüft. Ein kurzer CPU-Wakelock schützt den Ablauf auch bei gesperrtem Hauptbildschirm. Ein fehlendes/privates Display gilt als unbekannter Zustand, nicht als Beweis für OFF. Eine neue ausdrückliche Anzeigeanforderung macht noch ausstehende Ausschaltanforderungen ungültig.

Die privilegierte Displayoperation führt die Hersteller-App aus. Takeover sendet weder `GC_POWER`, `GC_POWER_ON`, `GC_POWER_OFF` noch künstliche Android-Screen-On/Off-Broadcasts. Reines Ausblenden sendet kein `GC_SCREEN_OFF`. Das Onboarding öffnet Einstellungen über Activity-Intents und verwendet dafür keine Hersteller-Power-Broadcasts.
