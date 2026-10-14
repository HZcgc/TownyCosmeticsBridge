# Staff HQ 5.2.0 · Journey-Audit und Verbindungsstatus

Enthalten: Staff Connector 1.5.0, Journey 2.0.0-beta.9 sowie HQ 5.2.0 / API 8 / Schema 11. Bestehende JP, Perk-Käufe, Kauf-Freigaben, Reward-Belege und historische Gutschriften bleiben erhalten. Es gibt keinen neuen Journey-Reset und keine geänderten Preise oder Tageslimits.

1. HQ-Dateien und Datenbank sowie die betroffenen Minecraft-Pluginordner sichern.
2. Zuerst den Inhalt von `WEBSPACE-UPDATE/` über das bestehende Staff-HQ-Hauptverzeichnis laden. `config/config.php`, `config/notifications.php` und `storage/` behalten. Keine Neuinstallation. Das HQ öffnen und mit Strg+F5 neu laden. Der additive Schritt auf Schema 11 verändert keine bereits eingerichteten Gruppenrechte.
3. SMP stoppen. Die bisherige StaffConnector-JAR und Progression-JAR entfernen; je Plugin genau eine JAR behalten. `SERVER-UPDATE/plugins/` über den SMP-Pluginordner kopieren. Die mitgelieferte Progression-Config ausdrücklich ersetzen; deren bisherige Revisionsmarker bleiben gleich. Alle Datenordner und Queues behalten.
4. Auf dem Hub ausschließlich die neue StaffConnector-JAR einsetzen. Er benötigt dort kein Journey-Plugin. Proxy-/Velocity-JARs bleiben separat.
5. Der Server-Update-Ordner enthält außerdem `DiscordSRV/messages.yml`. Diese Datei ergänzen/ersetzen; die vorhandene DiscordSRV-Config mit Bot-Token und Kanalzuordnungen behalten.
6. Vollständig starten. Kein PlugMan-/Bukkit-Reload. Der vorhandene HQ-Cronjob bleibt erforderlich und unverändert. Für ausschließlich die DiscordSRV-Textänderung ist alternativ `/discord reload` möglich.

## Kontrolle

Im HQ steht Reborn Journey direkt im Menü, auf dem Dashboard und im Spielerprofil. Vorhandene Offline-Profile werden als Bestand eingelesen; neue Ereignisse kommen aus bestätigten Journey-Speicherbelegen. Ein Leveltitel oder alter Besitz wird nicht als neuer Geldkauf gerechnet. Die ersten Profil-Snapshots erscheinen normalerweise innerhalb einer Minute. Bereits umgestellte Spielerprofile erscheinen auch offline; noch nicht umgestellte alte Profile nach ihrem nächsten Login und der regulären Journey-Übernahme.

Auf dem SMP `/trstaff status` prüfen. Die Verbindung verwendet denselben Token und Endpoint wie zuvor. Es sind keine neuen Minecraft-Permissions erforderlich. Die HQ-Auswertung respektiert `analytics.view`; Staff- und Owner-Verwaltungsrechte bleiben getrennt.

Ein HTTP-500/502-Fehler beschreibt eine fehlerhafte HQ/API-Antwort. Der kleine bevorzugte Heartbeat wird unabhängig vom normalen Datenrückstau gesendet. Wenn kein frischer Status ankommt, zeigt das HQ **Status unknown**, keine bestätigte SMP-Offlinemeldung. Ein kurzer Erfolg beendet einen Vorfall nicht sofort: Die Verbindung muss zwei Minuten stabil sein. Für Heartbeat-/Connector-Störungen gibt es eine Erstmeldung und eine bestätigte Entwarnung, keine wiederholten Discord-Erinnerungen. Konsolenwarnungen werden bei einer schwankenden Störung auf eine pro 15 Minuten begrenzt.

Echte Webhost-/Datenbankstörungen können weiterhin HTTP-Fehler erzeugen. Neue API-Fehlerreferenzen können mit dem privaten PHP-Fehlerlog abgeglichen werden; Token oder SQL-Inhalte werden nicht an den Connector ausgegeben. Nicht blind Queues oder Datenbanken löschen.

## Gefundener Statistikfehler

Ein maximaler Float-Schadenswert aus Todes-/Kill-Ereignissen konnte ein ganzes Statistikpaket ungültig machen. Neue unmögliche Werte werden vor dem Versand abgefangen. Gültige Einträge aus früher vollständig abgewiesenen `invalid_numeric_metric`-Paketen werden einmalig mit stabilen IDs neu übertragen. Originalarchive bleiben erhalten; Geld, Items, Keys oder Spielerdaten werden dadurch nicht vergeben.

## Unbedingt behalten

Progression: `journeydata`, `playerdata`, `migration-backups`, `history-credits` und `staff-audit`.

Connector: die vorhandene `config.yml`, alle Pending-/Outbox-Dateien, Cursor, `executed-remote-actions.txt`, `rejected-events.jsonl` und den neuen Repair-Checkpoint.

DiscordSRV: vorhandene `config.yml`, Account-Verknüpfungen und Bot-Token. Die Änderung betrifft nur Nachrichtenformate: **DISCORD [#Kanalname]**, Name, Antwortbezug und Text. Sie verändert nicht, wer Staff-/Adminnachrichten sehen darf.
