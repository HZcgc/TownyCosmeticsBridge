# Staff HQ 5.1: Webspace-Update

Dieses Paket baut die Oberfläche des vorhandenen HQ 5.0 um. Es wurde lokal geprüft und ist noch nicht auf eurem Webhost installiert. Connector 1.4.0, Dragon Build 33, Schema 9 und API 7 bleiben kompatibel. Für dieses Update ist kein Minecraft-Neustart nötig.

1. Den aktuellen Webspace und die HQ-Datenbank sichern.
2. Den **Inhalt von `WEBSPACE-UPDATE/`** in das bestehende Staff-HQ-Hauptverzeichnis hochladen. Gleichnamige Programmdateien ersetzen und Ordner zusammenführen.
3. **`config/config.php`, `config/notifications.php` und das komplette `storage/` behalten.** Diese Dateien sind nicht im Update enthalten. Keine Neuinstallation durchführen und keine Datenbank löschen.
4. HQ öffnen und mit **Strg+F5** neu laden. Auf dem Handy gegebenenfalls die Seite vollständig neu laden, damit die neuen Styles erscheinen.

**Crates, Dragon event, Voting, Piñata, Fishing, Player shops und MCPets** stehen jetzt direkt links im Menü und als Karten auf der Startseite. Auf dem Handy öffnet der Menüknopf dieselben direkten Links. Jede Karte führt zu Kennzahlen, Verlauf, Rankings und den aufklappbaren Details dieses Systems.

Unter **Appearance** am unteren Ende der Seitenleiste wählt jeder Staff sein Theme. **Cherry Blossom** ist jetzt dunkel in Pflaume und Rosa mit Kirschblüten. **Clear Light** bleibt die helle Variante. Die bisherige Auswahl und größere Schrift werden weiter gespeichert.

Nach dem Hochladen als Owner die Startseite sowie Crates und Dragon öffnen. Anschließend mit einem eingeschränkten Staff-Konto prüfen, ob die gewünschten Bereiche gemäß den bestehenden Rechten sichtbar sind. Die neue Navigation erweitert keine Gruppenrechte. Die bisherigen Notizen, Pins, Anhänge, Cases und Auswertungen bleiben verfügbar.

Neue Topic-Seiten verwenden die bereits gesammelten Daten. **No data yet** bedeutet, dass für den Zeitraum und Server noch keine erfassten Aktionen vorliegen. Fehlende optionale Hooks stehen weiterhin unter **Connections**. Ältere Dragon-Lifetime-Daten werden getrennt gezeigt und nicht nochmals in neue Ereignis-Summen addiert. Die ausführlichen bisherigen Messwerte und Exporte befinden sich unter **Metric explorer**.

Dieses ZIP enthält nur den Webspace und diese Anleitung. Bereits installierte Plugin-JARs, vorhandene Cronjobs und der private Crate-Import werden durch dieses UI-Update nicht ersetzt.
