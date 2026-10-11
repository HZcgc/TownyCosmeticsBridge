# Installation: Staff HQ 5.0.0, Connector 1.4.0, Dragon Build 33

Das Paket ist ein Update für euren vorhandenen Stand. Es wurde lokal geprüft und ist noch nicht auf eurem Webhost oder Minecraft-Server installiert.

## 1. Backup

Vor dem Update die HQ-Datenbank und den gesamten Webspace sichern. Auf dem Minecraft-Server `plugins/TownyDragonEvent/` und `plugins/TownyRebornStaffConnector/` sowie die bisherigen JARs sichern. Bestehende `stats.yml`, Arena-Vorlagen und sonstige Welten behalten.

## 2. Webspace zuerst

Den Inhalt von `WEBSPACE-UPDATE/` in das vorhandene HQ-Hauptverzeichnis hochladen und gleichnamige Programmdateien ersetzen. Die Datei `config/config.php`, `config/notifications.php` und das gesamte `storage/` behalten. Das Paket enthält dafür keine Ersatzdateien. Keine Neuinstallation ausführen und keine Datenbank löschen.

PHP benötigt weiterhin PDO/MySQL, mbstring und fileinfo. Beim nächsten Aufruf läuft die additive Migration auf Schema 9 automatisch. Danach als Owner einloggen und unter **Team** beziehungsweise **Permission view** die neuen Rechte prüfen. Nur der Owner kann Staff-Konten anlegen und Gruppenrechte ändern.

Unter **Appearance** wählt jeder Staff selbst Dark, Cherry Blossom oder Clear Light sowie größere Schrift. Unter einem Spielerprofil stehen Notizen, Watch-Anweisungen, dauerhafte Pins, Anhänge und **Create case** bereit. Die englische Staff-Anleitung befindet sich unter **Staff field guide**.

Unter den Benachrichtigungen muss die bestehende Freddie-Konfiguration weiterhin aktiviert und verifiziert sein. **Dupe and exploit chat flags** ist standardmäßig aktiv. Ein neuer Dupe-Hinweis wird sofort zur Zustellung vorgemerkt; die bestehende Begrenzung auf eine Discord-Nachricht pro Minute bleibt bestehen. Bei vielen gleichzeitigen Alerts kann dadurch eine Meldung kurz warten. Historische Chat-Nachlieferungen erzeugen keinen neuen Alarm.

Den vorhandenen Cronjob nach dem behobenen Webhosting-Ausfall wieder aktivieren und prüfen. Token und URL aus der vorhandenen Konfiguration verwenden; das Update ändert sie nicht. Die API selbst versucht außerdem nach der Annahme neuer Events die Zustellung.

## 3. Paper-Plugins

Server vollständig stoppen. Alte JARs von **TownyRebornStaffConnector** und **TownyDragonEvent** aus dem aktiven Plugin-Verzeichnis herausnehmen und die zwei neuen JARs aus `PAPER-PLUGINS/` einsetzen. Pro Plugin nur eine JAR behalten. Anschließend normal starten; diese Welt-Plugins nicht über PlugMan nachladen.

Bestehende Connector-Konfiguration mit API-Token, Servernamen und Queue-Dateien behalten. **Keinen Token aus einer Beispieldatei übernehmen.** Das neue Tracking ist standardmäßig aktiv. Die Website zuerst aktualisieren, damit sie die neuen Event-Typen kennt.

Dragon ergänzt neue Konfigurationsschlüssel automatisch. Eure Portal-/Exit-Koordinaten, Rewards und vorhandenen Statistiken bleiben erhalten. Ein unmarkierter alter `dragonevent_end`-Ordner wird nicht gelöscht. Das nächste Event benutzt nötigenfalls `dragonevent_end_tr1` oder einen weiteren freien, verwalteten Klon. Alte unmarkierte Ordner nur nach eigener Prüfung und Backup manuell bereinigen.

Die vier Tafeln stehen um den konfigurierten Portal-Mittelpunkt:

```yaml
displays:
  lobby-countdown: true
  lobby-actionbar: true
  portal-leaderboards: true
  portal-radius: 3.0
  distance-from-portal: 5.5
  height: 4.5
  view-distance: 64.0
```

`distance-from-portal` ist der Abstand außerhalb des eingestellten Portalrands. Bei Radius 3 stehen die Tafeln also 8,5 Blöcke vom Mittelpunkt entfernt. `height` ist der Y-Offset über dem Portal-Mittelpunkt. Die Tafeln zeigen Live-Schaden, Saison-Schaden, Gesamt-Schaden und Gesamt-Siege. Für ein anders großes Portal den Radius anpassen. Änderungen mit `/dragon reload` im Leerlauf laden.

## 4. Vorhandene Crate-Historie importieren

`PRIVATE-IMPORT/crate-history.jsonl` wurde aus euren gelieferten Logs erzeugt: **1.095 Öffnungen in 937 Vorgängen**, einschließlich Sammelöffnungen. Sie enthält Spielernamen und gehört nicht in einen öffentlichen Ordner oder auf GitHub.

Die Datei außerhalb des Web-Hauptverzeichnisses ablegen. Mit der PHP-Version des HQ aus einer privaten Shell bzw. einem einmaligen Plesk-Auftrag ausführen:

```bash
php /pfad/zum/staffhq/cli/import-game-history.php /privater/pfad/crate-history.jsonl
```

Ein erneuter Lauf überspringt bereits importierte Datensätze. Der Import schreibt nur Auswertungen; er gibt keine Keys, Items, Pets oder Geld aus. Falls der Lauf unterbrochen wird, einfach erneut ausführen. Die Datei anschließend wieder aus dem erreichbaren Hosting-Bereich entfernen. Neue ExcellentCrates-7-Batches werden aus der bestätigten History mit ihrem echten `selectedAmount` erfasst; einzelne Reward-Grants werden zusätzlich getrennt protokolliert.

## 5. Kurzer Funktionstest auf eurem Server

`/trstaff status` und `/trstaff test` prüfen. Im HQ unter **Plugins & Connections** kontrollieren, welche nativen Hooks verfügbar sind. Eine Testnotiz und einen Pin auf einem Spielerprofil erstellen. Mit einem eingeschränkten Staff-Konto private Nachrichten und Anhänge auf ihre gewünschten Rechte prüfen.

Dragon zuerst mit `/dragon start 300` testen: fünf Minuten Lobby, Countdown und vier Tafeln. Danach `/dragon stop`, Rückkehr der Spieler und anschließend `/dragon status` mit `IDLE` prüfen. Noch einmal starten, damit auch der zweite Durchlauf kontrolliert ist. Einen Crate-Batch öffnen und die entsprechende Anzahl im HQ vergleichen.

Eine Dupe-Testnachricht in einem erfassten Kanal schreiben und im HQ sowie im vorgesehenen Discord-Kanal überprüfen. Keine echte Sanktion ist dafür nötig; der Filter erzeugt Hinweise zur manuellen Prüfung.

## Abdeckung

Neue Event-Rankings starten mit diesem Connector. Vorhandene Dragon-Lifetime-Statistiken werden getrennt angezeigt; alte Stimmen, Shop-Transaktionen und Piñata-/Fishing-Aktionen werden ohne entsprechende Historie nicht nachträglich erfunden. Pet-Purchases stammen aus dem vorhandenen DONE-Ledger; bestehender Besitz ist ein Snapshot, neue Abschlüsse zählen als neue Käufe/Grants. Optionale Piñata-/Fishing-Events werden nur erfasst, wenn die installierte Plugin-Version die betreffende API bereitstellt. Fehlende Hooks sind im HQ sichtbar.
