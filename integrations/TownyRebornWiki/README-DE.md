# Towny Reborn Wiki: Reborn Journey

Stand: 14. Oktober 2026, Progression **2.0.0-beta.7**. Die englische Spieleranleitung verwendet die mitgelieferte aktuelle Journey-Config: 116 Level, 150/30-JP-Kurve, sechs Quellen und 43 permanente Einzelperks.

## Einfügen

1. Die bestehenden Website-Dateien sichern.
2. Den Inhalt von `TownyReborn-Wiki-Journey-2026-10-14.zip` in den Website-Hauptordner mit `index.php` kopieren. Ordner zusammenführen und die mitgelieferten Dateien ersetzen.
3. Im NamelessMC-Panel den Website-/Template-Cache leeren und die Wiki-Seite mit Strg+F5 laden.
4. Im Menü **Reborn Journey & permanent perks** öffnen. Rechner mit 250 JP testen: Level 2, 55 % abgeschlossen, 80 JP bis Level 3.

Das Paket enthält die öffentlichen Wiki-/Theme-Dateien für den bestehenden Eligius-Aufbau und die vorhandene Root-Alternative. Die HTML-, CSS-, JS- und Versionskopien sind synchron; beide `template.php`-Dateien bekommen nur neue CSS-/JS-Cache-Versionen. Es werden keine Datenbanken, Zugangsdaten, Uploads, Playerprofile oder Minecraft-Plugin-Dateien mitgeliefert. Die vorhandenen Bilder bleiben über ihre bestehenden Pfade eingebunden. Ein Minecraft-Neustart ist für dieses Website-Update nicht erforderlich.

## Inhalte

- Eigenes Journey-Kapitel direkt hinter Quests, mit Sprunglinks zu Öffnen, JP, Rechner, Rewards, permanenten Perks, alter Fortschrittsübernahme und der kompletten Level-Liste.
- `/progression`, `/journey`, `/reborn`, Quests-Verknüpfung und `/reborn perks`; Erklärung, dass `/ranks` aktuell die Haupt-Journey öffnet.
- Durchgehende Snake-Seiten und unterschiedliche Farbskalen für Levelstatus und Perkbesitz.
- Alle sechs JP-Quellen, persönliche Höchststände, aktive Spielzeit, Tagesbudgets und Reset um 00:00 UTC.
- Formeln, konkrete Rechenbeispiele, interaktiver JP-/Ziellevel-Rechner und Tagesplanung mit getrennten Quell-Limits. Die Planung startet ausdrücklich mit unbenutzten Tagesbudgets und greift nicht auf Minecraft-Accounts zu.
- Alle 116 Rangnamen, kumulative JP, Größe des nächsten Schritts, einmalige Belohnungen und neue Kauf-Freigaben; vier aufklappbare Tabellen entsprechend den vier Spielmenüseiten.
- Alle 43 Einzelperks, nach Skeleton, Creeper, Blaze, Dragon und Wither sortiert, mit Level, Preis, vorherigem Besitz und Nutzung.
- Town flight in der eigenen Stadt, Erweiterung auf alle Städte und freies `/fly` am aktuellen Ende.
- Einmalige Übernahme belegter alter JP plus neuer JP; unveränderte tatsächliche Käufe, frühere Kauf-Freigaben und Belohnungsbelege.
- Hinweis, dass künftig weitere Level, Titel und Belohnungen hinzukommen können.

Die frühere Perk-Path-Anleitung sowie widersprüchliche Querverweise wurden ersetzt. Die alte Kategorieaufnahme mit den entfernten Perk-Path-Einträgen und die alte Perk-Path-Galerie werden nicht mehr als aktueller Journey-Ablauf angezeigt. Andere vorhandene Bilder und Galerien bleiben eingebunden. Aktuelle Journey-Screenshots können nach der Liste in `SCREENSHOTS-DE.md` ergänzt werden; die neue Anleitung enthält keine defekten Bild-Platzhalter.

## Datenstand und spätere Änderungen

Preise, Levelnamen, Perkvoraussetzungen, Belohnungen und Quellen wurden direkt aus `TownyRebornJourney/src/main/resources/config.yml` übernommen. Die Browser-Berechnungen werden gegen Ergebnisse der echten `Journey`-Klasse aus Beta.7 geprüft. Die Website liest die Minecraft-Config nicht live. Werden Level, Preise oder Raten geändert, müssen diese Tabellen und die Rechnerdaten entsprechend aktualisiert werden.

Die angehängte vollständige Website-ZIP ist unverändert als Ausgangsstand geblieben. Dieses Einfügepaket enthält die gezielten öffentlichen Änderungen und wird zusammen mit den Quellen auf GitHub gespeichert.
