# Rückdisplay-Überwachung

## Zustände und Zuständigkeiten

`ScreenActivity` rendert Uhr oder HTML und meldet Start, Stop und Zerstörung. `WatchService` überwacht den gewählten Displayzustand und den Lebenszyklus dieser Activity. Es werden keine fremden Apps abgefragt und keine Usage-Stats- oder Accessibility-Berechtigungen benötigt.

„Andere App wird respektiert“ ist eine konservative Schlussfolgerung: Unsere zuvor sichtbare Activity ist nicht mehr sichtbar, während das Rückdisplay weiterhin `ON` ist. Android kann denselben Zustand auch aus anderen Gründen erzeugen. Im Modus „Anzeige erhalten“ ist erneutes **Anzeigen** deshalb die ausdrückliche Übernahme. Nach einem Prozessneustart werden alte Display-IDs beziehungsweise Sichtbarkeitsmeldungen nicht als Beweis einer noch laufenden Activity verwendet; dafür gilt nur die aktuelle Activity-Instanz.

| Ereignis | Reaktion |
| --- | --- |
| Anzeigen | Gewünschten Zustand aktivieren, Pause und Fremd-App-Rücksicht zurücksetzen, Einschalten/Start anfordern |
| Display wird OFF oder DOZE | Bei erlaubtem Betriebszustand Einschalten versuchen; in „Anzeige erhalten“ nicht nach bereits erkanntem Verdrängen |
| Display ist ON, eigene Activity sichtbar | Erfolg bestätigen, Fehlerwartezeit zurücksetzen |
| Eigene Activity wird bei ON verdrängt | „Anzeige erhalten“ wartet; „Takeover erzwingen“ stellt sie wieder her |
| Hauptbildschirm entsperrt, Sperrbedingung aktiviert | Eigene Activity schließen und auf nächsten Sperrzyklus warten; Hersteller-Anzeige nicht generell ausschalten |
| Pause | Laufende Anzeige unverändert lassen, Wiederherstellungsversuch abbrechen |
| Fortsetzen | Erneut prüfen; bestehende Rücksicht auf andere Apps beibehalten |
| Ausblenden / Benachrichtigung „Beenden“ | Gewünschten Zustand deaktivieren, Activity schließen, Dienst stoppen; kein späteres automatisches Einschalten |
| Boot / Paket ersetzt | Bei aktivierter Wiederherstellung und zuvor gewünschter Anzeige nach freigegebenem Nutzer-Speicher den Dienst starten |

Die Geräteoptionen liegen getrennt von den exportierbaren Designprofilen. Beim Boot wird keine HTML-Datei aus noch gesperrtem Credential-Speicher gelesen. Es gibt bewusst keinen `LOCKED_BOOT_COMPLETED`-Empfänger.

## Wiederherstellungsablauf

1. Aktivierung, Pause und Sperrbedingung prüfen.
2. Fehlerwartezeit und gegebenenfalls Rücksicht auf andere Apps prüfen.
3. Wenn das Display noch nicht `ON` ist: `com.yft.miniscreen.action.GC_SCREEN_ON` an `com.yft.miniscreendisplay`, mit `HALL_CLOSE=false`, senden.
4. Nach dem Einschalt-Broadcast ungefähr 700 ms warten; ein gemeldetes `ON` kann den Start vorziehen. Ein fehlendes privates Display bedeutet unbekannter Zustand, nicht `OFF`. Ein kurzer `PARTIAL_WAKE_LOCK` hält nur diesen Einschaltversuch ausführbar, maximal zwölf Sekunden.
5. Auch bei unbekanntem oder noch nicht aktualisiertem Displayzustand unsere Komponente mit `com.yft.miniscreen.action.START_ACTIVITY` über den Hersteller starten. Der direkte Alternativstart nutzt stattdessen `ActivityOptions.setLaunchDisplayId` und kann an Android-Berechtigungen scheitern.
6. Die neue beziehungsweise wieder sichtbare `ScreenActivity` bestätigt den Start über ihren Lebenszyklus. Ein gesendeter Broadcast allein gilt nicht als Erfolg.

Fehlgeschlagene Versuche verwenden exponentiell ähnliche, begrenzte Wartezeiten: 5, 15, 30, 60, 120, dann 300 Sekunden. Ereignisse umgehen diese Wartezeit nicht. Ein explizites neues **Anzeigen** setzt sie zurück. Ein manueller Start wird nach ungefähr einer Minute ohne Erfolg aufgegeben; im überwachten Betrieb bleiben die Prüfungen mit begrenztem Wiederholungsrhythmus aktiv.

## Dienst und Energie

Der Vordergrunddienst verwendet den Typ `specialUse`, mit Beschreibung seines konkreten Rückdisplay-Anwendungsfalls im Manifest und den zugehörigen FGS-Berechtigungen. Die Benachrichtigung bietet Pausieren/Fortsetzen und Beenden. Sie verwendet einen separaten Kanal mit geringer Wichtigkeit.

Primär werden `DisplayManager.DisplayListener`, Bildschirm-/Entsperrereignisse und Activity-Lebenszyklusmeldungen verwendet. Die Kontrollprüfung läuft standardmäßig alle 60 Sekunden (einstellbar 15–300). Ein Handler ist kein Weckalarm: Bei schlafender CPU kann die Prüfung später stattfinden. Es gibt weder dauerhaften CPU-WakeLock noch exakte Alarme. Die sichtbare Rückdisplay-Activity verwendet weiterhin `FLAG_KEEP_SCREEN_ON`, damit Android diese Anzeige nicht durch normalen Activity-Timeout abschaltet.

Zeitlich begrenzte Pausen behalten ein absolutes Enddatum. Eine unbefristete Pause bleibt auch nach Neustart bestehen. Die Wiederherstellung nach Neustart ist standardmäßig deaktiviert. Android/OEM-Einschränkungen können Dienststarts und Hintergrund-Activity-Starts verhindern; Fehler erscheinen im Überwachungsstatus. `START_STICKY` wird im überwachten Betrieb verwendet, bietet aber keine Garantie gegen Systembeendigung und umgeht kein Force-Stop.

## Gerätetest für 0.1.2

Diese Version ist kompiliert; der korrigierte Startweg muss jetzt am Gerät geprüft werden. Der Hersteller-Broadcast zum Einschalten wurde zuvor in Second Screen Test 0.1.3 am Gerät bestätigt. Für diese App sind nach einem späteren Build folgende Fälle zu prüfen:

1. Rückdisplay aus: Anzeigen → `ON` → native Uhr und anschließend HTML-Design.
2. „Anzeige erhalten“: Hauptansicht schließen, Display ausschalten, Wiederherstellung beobachten.
3. Andere Rückdisplay-App öffnen: Sie bleibt im Modus „Anzeige erhalten“ stehen; erneutes Anzeigen übernimmt wieder.
4. „Takeover erzwingen“: Verdrängte Anzeige wird wiederhergestellt, keine eng getaktete Startschleife.
5. Sperrbedingung: Hauptbildschirm entsperren blendet unsere Activity aus; Sperren startet einen neuen Anzeigezyklus.
6. Unbefristete und zeitliche Pause: Keine Wiederherstellung während der Pause; Beenden stoppt auch den Dienst.
7. Hersteller-MiniScreen deaktivieren: Fehlermeldung und zunehmende Wartezeiten; Aktivieren und ausdrücklich Anzeigen setzt den Versuch zurück.
8. Neustart: Option aus → kein Autostart; Option an und gewünschte Anzeige aktiv → Wiederherstellung nach erstem Entsperren. Ausgeblendete Anzeige bleibt aus.
9. Prozessverlust, Benachrichtigungsaktionen und verweigerte Benachrichtigungsberechtigung prüfen.
