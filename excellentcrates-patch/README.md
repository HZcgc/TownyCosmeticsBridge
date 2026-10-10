# Crate-Key-Fix für Towny Reborn

Für Paper 26.2 / Java 25, ExcellentCrates 7.1.0 und nightcore 2.16.7.

## Installation

1. Server vollständig stoppen und `plugins` sichern.
2. Die bisherige ExcellentCrates-JAR durch `ExcellentCrates-7.1.0-townykeys.1.jar` ersetzen.
3. Die bisherige TownyRebornHunt-JAR durch `TownyRebornHunt-1.0.3.jar` ersetzen. Je Plugin nur eine JAR behalten.
4. Die vorhandenen Datenordner behalten, insbesondere `ExcellentCrates/data.db` und `TownyRebornHunt/players`.
5. Server normal starten. Kein `/reload` verwenden.

Das Paket enthält keine Spieler-Datenbank und keine Crate-Konfigurationen. Deine aktuellen Daten, Modelle, Animationseinstellungen, Belohnungen und Fundorte werden weiterverwendet.

## Behobener Fehler

EC7.1.0 speichert geänderte Schlüssel mit einem einfachen `INSERT`. Für einen bereits vorhandenen Eintrag `(owner_id, key_id)` scheitert das am Primärschlüssel. Neue Funde funktionieren zunächst im Speicher, werden aber nicht dauerhaft gespeichert; verbrauchte Keys können beim Rejoin zurückkommen. Der Patch verwendet den nativen NightCore-Builder mit `updateOnConflict()` für beide Werte, `virtual_balance` und `unclaimed_amount`. Das betrifft alle Key-Typen.

Beim Login lädt EC7 zusätzlich die Datenbank neu. Ein schneller Rejoin vor der nächsten automatischen Speicherung kann dabei aktuelle Werte überschreiben. Die zweite Änderung lädt fehlende Einträge nach und behält vorhandene Cache-Werte, einschließlich noch ungespeicherter Vergaben und bereits verbrauchter Keys. Die native Speicherung und die native Key-/Öffnungs-API bleiben zuständig.

## Einmalige Halloween-Reparatur

TownyRebornHunt 1.0.3 gleicht beim vollständigen Start bestätigte `FOUND`-Einträge mit aufgezeichnetem `halloween2026`-Key und den gespeicherten Halloween-Belohnungszählern ab. Ausstehende `PENDING`-Funde werden nicht ausgezahlt. Es ergänzt nur fehlende Restbestände: bestätigte Funde minus bereits erhaltene Belohnungen minus unclaimed Keys. Höhere vorhandene Bestände bleiben erhalten.

Diese Reparatur läuft ausschließlich für `halloween2026`, SQLite und die gepatchte EC-Version. Die Crate muss einen Key für eine Belohnung verlangen. Vorher entsteht `ExcellentCrates/data-before-key-recovery-<timestamp>.db`. Änderungen und ein dauerhafter Marker werden in einer Transaktion gespeichert. Spätere Logins, weitere Neustarts und neue Funde lösen keine erneute Nachzahlung aus. Die Tabelle `townyreborn_key_recovery` protokolliert die betroffenen UUIDs sowie Fund-, Verbrauchs- und Vorher-/Nachher-Zähler. Hunt-Fortschritt und Belohnungszähler werden nicht zurückgesetzt.

Im gemeldeten Beispiel stehen 16 Funde und 3 Öffnungen einem gespeicherten Restbestand von 1 gegenüber. Mit 16 bestätigten Zahlungs-Einträgen im aktuellen Hunt-Ordner korrigiert die Reparatur diesen Bestand einmalig auf 13.

Bei absichtlich entzogenen Halloween-Keys oder historischen manuellen Rücksetzungen können Funde und Öffnungen den gewünschten Bestand nicht vollständig erklären. Die Wiederherstellung setzt bestätigte, noch nicht durch Belohnungszähler verbrauchte Hunt-Zahlungen als Mindestbestand an; zusätzliche administrative Vergaben werden beibehalten.

## Prüfen

- Im Startlog: `Halloween key recovery restored ... confirmed unspent keys once`.
- `/hunt progress 6ft2Dreadhead` zeigt Funde und, für online Spieler, die verfügbaren virtuellen Keys getrennt.
- `/cratekey` beziehungsweise der bisherige Key-Befehl zeigt den nativen Restbestand.
- Eine Crate öffnen, ausloggen und sofort wieder einloggen. Der Restbestand muss vermindert bleiben. Auch neu gefundene Keys müssen einen Neustart überstehen.

`Ghosts found: 16/40` bleibt ein historischer Fortschrittszähler. Für eine zusätzliche Hologramm-Zeile mit verfügbaren Keys kann das bestehende Addon `%ec_keyz_halloween2026%` verwenden. Das Halloween-Hologramm wird von diesem Update nicht umgestaltet.

## Build und Quelltext

`JAVA_HOME=/path/to/jdk25 python3 tools/build.py` baut die zwei Ersatzklassen gegen die per SHA-256 festgelegten Original-JARs und erstellt eine vollständige gepatchte ExcellentCrates-JAR. Alle anderen Original-Einträge bleiben unverändert; die Versionsangabe lautet `7.1.0-townykeys.1`. Patch-Quelltext und GPL-3.0-Lizenz werden in der JAR mitgeliefert. Bei einem späteren offiziellen ExcellentCrates-Update muss der Patch neu geprüft werden.
