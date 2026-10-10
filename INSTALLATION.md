# Towny Reborn Update — 10.10.2026

Für Paper 26.2, Java 25, ExcellentCrates 7.1.0, nightcore 2.16.7 und QuickShop-Hikari 6.3.0.3.

1. Server vollständig stoppen und den Ordner `plugins` sichern.
2. Die fünf bisherigen Towny-JARs entfernen und durch die fünf neuen JARs ersetzen: TownyCosmeticsBridge 1.2.2, TownyRebornCrateModels 1.0.3, TownyRebornHunt 1.0.2, TownyRebornLoot 1.0.1 und TownyRebornBarrelShops 1.0.1.
3. QuickShop-Hikari 6.3.0.3 durch `QuickShop-Hikari-6.3.0.3-townybarrels.2.jar` ersetzen. Nur eine QuickShop-JAR im Plugin-Ordner behalten. Der Patch stellt den Zugriff auf alle 54 BiggerBarrels-Fächer wieder her.
4. Die alte `ecrates-placeholderv2.1.jar` beziehungsweise eine ältere JAR mit Plugin-Name `ExcellentCratesAddon` durch `ecrates-placeholder-2.2-ec7.jar` ersetzen. Nur eine Addon-JAR behalten. Sie stellt die bestehenden `%ec_keyz_<key-id>%`-Anzeigen wieder her. Die TAB-Keys-Zeile kann mit `%ec_totalkeys_daily,vote,patron,cosmetic%` direkt die Summe der vier virtuellen Schlüsseltypen anzeigen.
5. Den Inhalt von `ExcellentCrates-7-Konfigurationen.zip` in `plugins` entpacken. Dadurch werden fünf Crates, fünf Schlüssel, 174 Belohnungen und drei Seltenheiten im EC7-Format ergänzt. EC7 verwendet jetzt `objects/crate`, `objects/key` und `objects/reward`; der alte Ordner `crates` wird nicht mehr gelesen.
6. Alle vorhandenen Plugin-Datenordner behalten, insbesondere `ExcellentCrates/data.db` und `TownyRebornHunt/players`. Keine Datenbank aus einem früheren Backup darüberkopieren.
7. Server normal starten. Kein `/reload` verwenden.

TownyRebornHunt übernimmt beim ersten Start die alten SQLite-Schlüsselbestände und Belohnungszähler aus der **aktuellen** `ExcellentCrates/data.db`. Vorher erstellt es `data-before-ec7-<timestamp>.db`. Die Übernahme läuft in einer Transaktion und wird dauerhaft markiert. Vorhandene EC7-Schlüsselbestände bleiben maßgeblich; Belohnungszähler werden niemals verringert. Weitere Starts vergeben keine alten Schlüssel erneut. Die alten Tabellen bleiben als Archiv erhalten. Eine MySQL-Datenbank wird durch diese Migration nicht verändert.

Der historische einmalige Halloween-Test-Reset ist deaktiviert. Fortschritt, bereits gewonnene Halloween-Belohnungen, Auszahlungen, Gegenstände und Verzauberungen bleiben erhalten. EC7 zieht Belohnungen anders als EC6: Die neuen Einzelgewichte sind deshalb normalisiert, damit die bisherigen Gewinnchancen erhalten bleiben. Die ursprünglichen Werte stehen unter `towny_migration.original_weight`. Die alte Animation `enclosing` wird durch die native EC7-Animation `csgo_animation:default` ersetzt; die Vorschau verwendet `inventory:default`. Bestehende ModelEngine-Platzierungen und Animationen bleiben im CrateModels-Datenordner. Das Hunt-Limit bleibt konfigurierbar; vorhandene Einstellungen werden nicht überschrieben.

Schlüssel werden jetzt mit `cratekey give SPIELER KEY ANZAHL` vergeben. Der alte Befehl `crates key give ... -sf` wird beim Konvertieren in den neuen Befehl ohne `-sf` übersetzt. Auch externe Belohnungslisten, etwa in PinataParty, müssen den neuen Befehl verwenden. Mit `base.virtual: true` in `ExcellentCrates/objects/key/<key>.yml` erhöht der Befehl das digitale Guthaben; der Spieler öffnet damit die aufgestellte Crate. `crate give` verteilt dagegen ein Crate-Item. Der Konverter übernimmt den bisherigen Hologramm-Schalter ausdrücklich. Für Halloween muss `hologram.enabled: false` in `ExcellentCrates/objects/crate/halloween2026.yml` stehen.

Nach dem Start prüfen: ExcellentCrates meldet **5 Crates, 5 Keys und 174 Rewards**, `/hunt check` bestätigt den virtuellen Schlüssel, `/trcrate status` zeigt die gespeicherten Modelle, `/trloot status` zeigt die Loot-Profile. Zum Fass-Test auch einen Artikel in die zweite Inventarhälfte legen und Kauf/Verkauf prüfen.

Die Builds und automatischen Tests ersetzen nicht den abschließenden Spieltest auf deinem Server: ItemsAdder und ModelEngine hängen zusätzlich von deinen dort installierten Assets ab.
