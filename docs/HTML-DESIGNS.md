# Vollständige HTML-Designs

MiniScreen Takeover zeigt dein Design als komplette Anzeige. Es gibt keine zusätzliche native Uhr darüber. Eine aktivierte Ladeanzeige wird unabhängig vom HTML als native Ebene darüber gezeichnet. Verwende relative CSS-Einheiten oder die tatsächlichen CSS-Abmessungen; die WebView-Fläche wird für Pixelshifting verkleinert und von Android verschoben.

## Dateien importieren

Importiere eine eigenständige `.html`-Datei oder ein ZIP. Für ZIPs empfiehlt sich `index.html` im Stamm, daneben etwa `style.css`, `clock.js`, `images/` und `fonts/`. Relative URLs funktionieren innerhalb des Designordners. Ressourcen außerhalb dieses Ordners und Netzwerkressourcen werden blockiert. Eine einzige `index.html` in einem Unterordner wird ebenfalls erkannt, falls im ZIP-Stamm keine liegt.

Das mitgelieferte `examples/Classic-HTML-Design.zip` ist direkt importierbar. `examples/minimal.html` ist ein kleines eigenständiges Beispiel.

## Daten lesen

```js
function render(d) {
  document.querySelector('#clock').textContent = d.time;
  document.querySelector('#clock').hidden = d.showClock === false;
  document.querySelector('#date').textContent = d.showDate ? d.date : '';
}
window.addEventListener('miniscreen:update', event => render(event.detail));
if (window.miniScreen) render(window.miniScreen);
```

Der erste Zustand wird nach Laden der Seite übertragen. Danach erfolgen Updates bei Einstellungen, Zeit-/Zeitzonenänderungen und jedem Pixelshift, sonst an der nächsten vollen Minute oder Sekunde entsprechend der Sekundenoption. `window.miniScreen` bleibt der zuletzt übertragene Zustand. Bei laufenden eigenen Animationen kann JavaScript zusätzlich `Date.now()` verwenden; häufige Animationen erhöhen den Energiebedarf.

| Feld | Bedeutung |
| --- | --- |
| `epochMs` | Unixzeit in Millisekunden zum Zeitpunkt des Updates |
| `time` | Bereits formatierte Uhrzeit mit 12/24 Stunden, Sekundenoption und führender Null |
| `date` | Formatiertes Datum; leer, wenn deaktiviert |
| `showClock` | Uhranzeige gewünscht; unabhängig vom Datum. Eigene Designs sollen bei `false` ihre Uhr ausblenden. |
| `showDate` | Datumsanzeige gewünscht |
| `width`, `height` | Äußere Renderfläche in physischen Pixeln; auf dem Rückdisplay normalerweise 340 × 340 |
| `cssWidth`, `cssHeight` | Innerer WebView-Bereich in CSS-Pixeln; zusätzlich `innerWidth`, `innerHeight` beziehungsweise `clientWidth` verwenden |
| `timeZone`, `locale` | Android-Zeitzone und Sprachkennung |
| `running` | `true`, solange diese Renderfläche aktiv ist; eigene Animationen bei `false` pausieren |
| `batteryPlugged`, `batteryLevel` | Stromversorgung angeschlossen und Akkustand 0–100; in der simulierten Vorschau `true` und 65 |
| `preview` | `true` in der Hauptbildschirm-Vorschau |
| `shiftX`, `shiftY` | Aktuelle Verschiebung in Referenzpixeln bezogen auf 340 Pixel |
| `settings` | Vollständige Profileinstellungen, siehe folgende Tabelle |
| `backgroundUrl` | Lokale URL für das gewählte Hintergrundbild oder leer |
| `fontUrl` | Lokale URL für die gewählte Schrift oder leer |

Die CSS-Variablen `--time-color`, `--date-color` und `--background-color` werden auf `document.documentElement` gesetzt.

| `settings`-Felder | Bedeutung |
| --- | --- |
| `schema`, `name`, `mode` | Profilformat-Version (1), Profilname, `native` oder `html` |
| `twentyFour`, `seconds`, `leadingZero`, `showClock`, `showDate`, `datePattern` | Zeit-/Datumseinstellungen |
| `timeColor`, `dateColor`, `backgroundColor` | HEX-Farben `#RRGGBB` |
| `timeSize`, `dateSize` | Referenzschriftgrößen bezogen auf eine 340-Pixel-Fläche |
| `timeX`, `timeY`, `dateX`, `dateY` | Gewünschte Mittelpunkte in Prozent; eigene Designs dürfen sie anders interpretieren |
| `font`, `bold` | Systemschriftname und Fettoption |
| `imageFit`, `dim`, `shiftBackground` | `cover` oder `contain`, Abdunklung 0–100 %, Hintergrundbewegung |
| `shifting`, `shiftRange`, `shiftInterval` | Pixelshifting aktiv, Bereich in Referenzpixeln, Intervall in Sekunden |
| `brightness` | Angeforderte Displayhelligkeit 1–100 %; Android stellt sie außerhalb des HTML ein |
| `image`, `fontFile`, `design` | Interne relative Assetpfade; für Bilder/Schriften die bereitgestellten URLs verwenden |

## Hintergrund und Schrift

Setze ein gewähltes Bild über `backgroundUrl` als CSS-Hintergrund. Lade `fontUrl` bei Bedarf mit `FontFace`. Diese URLs bleiben lokal, obwohl sie mit `https://miniscreen.local/` beginnen; die App beantwortet sie aus dem Profilverzeichnis. Im Beispiel werden Fonts nur bei Änderung neu geladen.

Das HTML entscheidet, ob es ein Hintergrundbild, die Datumseinstellung oder Positionsregler berücksichtigt. Die App zwingt diese Gestaltungsoptionen nicht auf. Helligkeit, Touchsperre und Pixelshifting gelten dagegen außerhalb des HTML.

Die ganze HTML-Fläche bewegt sich mit Pixelshifting. Für eine optisch unbewegte Hintergrundebene kann das Design die Verschiebung entgegenrechnen. Um Referenzpixel in CSS-Pixel umzurechnen:

```js
const factor = Math.min(d.width, d.height) / 340 / (window.devicePixelRatio || 1);
const dx = d.shiftX * factor, dy = d.shiftY * factor;
background.style.transform = `translate(${-dx}px, ${-dy}px)`;
```

Kein zusätzliches Pixelshifting auf Texte anwenden, wenn die App es bereits ausführt. Achte auf ausreichenden Platz für lange Datumsangaben und 12-Stunden-Uhrzeiten. Der automatische Außenrand verhindert Abschneiden durch die Bewegung; Layoutüberläufe innerhalb deiner Seite musst du selbst vermeiden.

## WebView-Regeln

JavaScript ist erlaubt, es gibt aber **keine** `addJavascriptInterface`-Bridge. Die App überträgt lediglich JSON-Daten an die Seite. Offline-Ressourcen aus dem Design, `data:`-Bilder/Fonts und unterstützte lokale Medien sind möglich. Externe Verbindungen, eingebettete Webseiten, Popups, Datei-/Content-URLs, Formulare nach außen und Webworker werden nicht freigegeben. Lokale Skripte können keine Android-Aktionen auslösen. DOM-Speicherung ist deaktiviert; Designs sollen beim Laden aus den gelieferten Daten neu aufgebaut werden.

Berührungen werden bereits durch die Android-Anzeige abgefangen. Das gilt auch für die Vorschau; HTML-Buttons sind daher keine Steuerelemente für diese App.

Limits: 20 MiB pro entpacktem Design, 200 ZIP-Einträge, 1 MiB pro HTML-Datei, 240 Zeichen pro ZIP-Pfad und maximal 12 Pfadebenen. Profile exportieren die aktuell referenzierten Dateien und das aktuelle Design, nicht frühere unbenutzte Importe.

`locale` folgt der gewählten App-Sprache, bei „Systemsprache“ der Android-Locale. Die App übersetzt keine frei formulierten Texte eines importierten HTML-Designs.

## Schaufenster und Ladeanzeige

`settings.imageFit` ist `cover`, `contain` oder `window`. Bei `window` füllt das Bild zunächst die Fläche; `windowZoom` (100–500 %) vergrößert es darüber hinaus. `windowSeconds` (10–180) gibt die Dauer eines Bewegungsabschnitts an und `windowMotion` aktiviert die Bewegung. `dim` bleibt die Abdunklung in Prozent. Das aktuelle Beispiel in `app/src/main/assets/designs/classic/index.html` enthält die Bildberechnung und eine gedrosselte `requestAnimationFrame`-Schleife. Zufallsziele sind deterministisch aus Zeitsegment und Achse berechnet; eine Smoothstep-Interpolation vermeidet sprunghafte Richtungswechsel.

Die Ladeanzeige wird automatisch von Android über dem HTML gezeichnet. Dafür muss das Design selbst nichts rendern. Die Einstellungen stehen zusätzlich in `settings`: `chargeEnabled`, `chargeCircle`, `chargeAnimated`, `chargeBold`, `chargeColor`, `chargeSymbolColor`, `chargeRingColor`, `chargeFont`, `chargeFontFile`, `chargeSize`, `chargeX`, `chargeY`, `chargeRingWidth`, `chargeAnimationSeconds`.

`chargeFontFile` ist ein interner Profilpfad für die native Ebene, keine zugängliche HTML-URL. Eigene HTML-Inhalte werden beim Import nicht automatisch umgeschrieben. Das mitgelieferte Design zeichnet keine zweite Ladeanzeige. Updates erfolgen auch bei Akkuänderungen und beim Start/Pausieren der Renderfläche. Die tatsächliche Animate-Rate ist unabhängig vom Minuten-/Sekundentakt der Uhr.

## Benachrichtigungsticker

Standardmäßig zeichnet Android den konfigurierbaren Ticker über dem Design. Die HTML-Seite bekommt in diesem Modus keine Benachrichtigungsinhalte. Mit **„HTML-Design stellt Benachrichtigungen dar“** entfällt diese native Ebene. Die Seite erhält dann im bestehenden `miniscreen:update`-Event:

- `notification`: aktuell gezeigte Meldung oder `null` während einer Pause.
- `notifications`: gefilterte Warteschlange, maximal 50 Einträge.
- `tickerSettings`: Schriftgröße, Schrift, Fett, Farben, Deckkraft, Position, Breite, Laufschrift, Geschwindigkeit, Zeilenzahl, Symbolgröße, Anzeigedauer und `clockMode` (0 = sichtbar, 1 = abdunkeln, 2 = ausblenden).

Eine Meldung enthält `app` (Paketname), `label`, `text`, `count` und `iconUrl` (lokales App-Symbol). `label` ist im diskreten Modus leer; `text` enthält dann nur die Anzahl. In den weiteren Stufen enthält `text` bereits die erlaubte Kombination aus App, Titel und Nachricht. Titel und Nachricht stehen nicht zusätzlich als ungefilterte Felder bereit. `count` zählt aktive erfasste Benachrichtigungen dieser App. `iconUrl` funktioniert nur für erlaubte Apps im aktivierten HTML-Modus, in der Vorschau nur für das Beispielsymbol.

Die Daten werden **vor** dem JavaScript-Aufruf nach dem aktuellen Sperrzustand gefiltert. Bei Entsperren, Entfernung, Datenschutzänderung und Stop werden sie aktualisiert bzw. geleert. Ein HTML-Design muss alte Texte daraufhin selbst entfernen. Nur Designs aktivieren, denen man die gewählte Inhaltsstufe anvertrauen möchte: bereits übergebene Inhalte kann die App nicht aus beliebigen JavaScript-Variablen eines Designs zurückholen. Keine Benachrichtigungen in eigenes HTML oder Dateien einbetten; `textContent` statt `innerHTML` verwenden.

```js
window.addEventListener('miniscreen:update', ({detail: d}) => {
  const n = d.notification;
  const box = document.getElementById('notification');
  box.hidden = !n;
  box.textContent = n ? n.text : '';
});
```

Das vollständige Beispiel `examples/notification-clock.html` zeigt Uhr und gefilterte Meldung. Native Schriftgrößen verwenden die 340px-Referenz; für HTML mit `Math.min(d.cssWidth, d.cssHeight) / 340` skalieren. Schrift `profile` verwendet die Profilschrift, einschließlich `fontUrl` für die importierte Schrift. Pixelshifting erfolgt außerhalb der WebView. Im HTML-Modus steuert das Design selbst das Abdunkeln/Ausblenden der Uhr. Die native Ladeanzeige bleibt unabhängig davon aktiv.

## Native Zusatzmodule

Widgets und Notification Dot werden als native Ebenen über dem HTML-Design angezeigt. Der Punkt verwendet eine eigene App-/Farbauswahl und erhält keine Nachrichtentexte zur Anzeige. Widget-Bindings und Punkt-Einstellungen sind Geräteoptionen und nicht Teil des Profil-Exports. Der Punkt wird über Ticker und Ladeanzeige gelegt, damit sein Status sichtbar bleibt. Die Uhr und das Datum können unabhängig deaktiviert werden; das eingebaute Design berücksichtigt beide Schalter.
