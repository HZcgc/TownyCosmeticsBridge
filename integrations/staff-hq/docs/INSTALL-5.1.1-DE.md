# Staff HQ 5.1.1: Graph-Tooltips, Guide und Gruppenrechte

Das Update enthält die komplette neue HQ-Oberfläche und die drei anschließenden Anpassungen. Es funktioniert als Webspace-Update für HQ 5.0 oder 5.1. Die Minecraft-Plugins und API 7 bleiben kompatibel; kein Minecraft-Neustart ist erforderlich. Es ist noch nicht auf eurem Webhost installiert.

1. Den aktuellen HQ-Webspace und die Datenbank sichern.
2. Den **Inhalt von `WEBSPACE-UPDATE/`** in das bestehende HQ-Hauptverzeichnis hochladen, Ordner zusammenführen und gleichnamige Programmdateien ersetzen.
3. **`config/config.php`, `config/notifications.php` und das gesamte `storage/` behalten.** Keine Neuinstallation ausführen und keine Datenbank löschen.
4. Das HQ öffnen und mit **Strg+F5** neu laden. Der nächste Aufruf führt die einmalige Aktualisierung auf Schema 10 aus.

Die Graph-Tooltips folgen jetzt dem Mauszeiger. Am rechten oder unteren Rand wechseln sie die Seite, damit sie im sichtbaren Graphen bleiben. Touch und Tastatur sind weiterhin verfügbar; das Ziehen eines Bereichs zum Zoomen bleibt erhalten.

Unter **Documents → Staff field guide → Edit** kann ein Admin oder der Owner den Guide bearbeiten und Dateien anhängen. Der Menüpunkt **Staff guide** zeigt dasselbe gespeicherte Dokument. Titeländerungen und spätere Webupdates überschreiben eure Inhalte nicht. Ein bereits vorhandenes Dokument mit genau diesem Titel wird übernommen. Der bisherige Guide wird andernfalls einmalig als Dokument angelegt.

## Die gewünschten Gruppenrechte werden einmal gesetzt

Beim Update erhalten **Helper, Moderator und Admin** die Rechte aus `ROLE-PERMISSIONS.md`. Vorherige Regeln dieser Gruppen werden in der Datenbank unter `staff.roles.before_5_1_1` für eine mögliche Wiederherstellung gespeichert. Die neue Einstellung ersetzt beim ersten Update die bisherigen Regeln für die bekannten HQ-Rechte. Unbekannte eigene Rechte bleiben erhalten. **Spätere Änderungen durch den Owner bleiben bestehen**; weitere Seitenaufrufe setzen sie nicht erneut zurück.

- Helper: Spielerprofile, Notizen und Watch-Anweisungen; Pins und Anhänge hinzufügen; Cases bearbeiten; öffentliche Nachrichten, Spielstatistiken und Diagnostik lesen. Keine privaten Nachrichten, Sanktionen ausführen oder Dokumente ändern.
- Moderator: zusätzlich Moderationshinweise bearbeiten, private Nachrichten und Commands lesen, Sanktionen erfassen und ausführen sowie Profilevidence verwalten.
- Admin: zusätzlich Dokumente und den Staff Guide bearbeiten sowie Systemeinstellungen und Connector-Tokens verwalten.
- **Nur Owner:** Staff-Konten verwalten/anlegen und Gruppenrechte ändern.

Nach dem Upload unter **Permissions** die Matrix prüfen und je ein Konto der drei Gruppen testen. Die Einstellung betrifft ausschließlich das Staff HQ, nicht LuckPerms oder Minecraft-Ränge. Der bestehende Cronjob, alle Plugin-JARs und eure bisherigen Daten bleiben erhalten.
