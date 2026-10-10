# Towny Reborn Update — 10.10.2026

Für Paper 26.2, Java 25, ExcellentCrates 7.1.0, nightcore 2.16.7 und QuickShop-Hikari 6.3.0.3.

1. Server vollständig stoppen und den Ordner `plugins` sichern.
2. Die fünf bisherigen Towny-JARs entfernen und durch die fünf neuen JARs ersetzen: TownyCosmeticsBridge 1.2.2, TownyRebornCrateModels 1.0.3, TownyRebornHunt 1.0.2, TownyRebornLoot 1.0.1 und TownyRebornBarrelShops 1.0.1.
3. QuickShop-Hikari 6.3.0.3 durch `QuickShop-Hikari-6.3.0.3-townybarrels.2.jar` ersetzen. Nur eine QuickShop-JAR im Plugin-Ordner behalten. Der Patch stellt den Zugriff auf alle 54 BiggerBarrels-Fächer wieder her.
4. Den Inhalt von `ExcellentCrates-7-Konfigurationen.zip` in `plugins` entpacken. Dadurch werden fünf Crates, fünf Schlüssel, 174 Belohnungen und drei Seltenheiten im EC7-Format ergänzt. EC7 verwendet jetzt `objects/crate`, `objects/key` und `objects/reward`; der alte Ordner `crates` wird nicht mehr gelesen.
5. Alle vorhandenen Plugin-Datenordner behalten, insbesondere `ExcellentCrates/data.db` und `TownyRebornHunt/players`. Keine Datenbank aus einem früheren Backup darüberkopieren.
6. Server normal starten. Kein `/reload` verwenden.

TownyRebornHunt übernimmt beim ersten Start die alten SQLite-Schlüsselbestände und Belohnungszähler aus der **aktuellen** `ExcellentCrates/data.db`. Vorher erstellt es `data-before-ec7-<timestamp>.db`. Die Übernahme läuft in einer Transaktion und wird dauerhaft markiert. Vorhandene EC7-Schlüsselbestände bleiben maßgeblich; Belohnungszähler werden niemals verringert. Weitere Starts vergeben keine alten Schlüssel erneut. Die alten Tabellen bleiben als Archiv erhalten. Eine MySQL-Datenbank wird durch diese Migration nicht verändert.

Der historische einmalige Halloween-Test-Reset ist deaktiviert. Fortschritt, bereits gewonnene Halloween-Belohnungen, Auszahlungen, Gegenstände, Verzauberungen und Befehle bleiben erhalten. EC7 zieht Belohnungen anders als EC6: Die neuen Einzelgewichte sind deshalb normalisiert, damit die bisherigen Gewinnchancen erhalten bleiben. Die ursprünglichen Werte stehen unter `towny_migration.original_weight`. Die alte Animation `enclosing` wird durch die native EC7-Animation `csgo_animation:default` ersetzt; die Vorschau verwendet `inventory:default`. Bestehende ModelEngine-Platzierungen und Animationen bleiben im CrateModels-Datenordner. Das Hunt-Limit bleibt konfigurierbar; vorhandene Einstellungen werden nicht überschrieben.

Nach dem Start prüfen: ExcellentCrates meldet **5 Crates, 5 Keys und 174 Rewards**, `/hunt check` bestätigt den virtuellen Schlüssel, `/trcrate status` zeigt die gespeicherten Modelle, `/trloot status` zeigt die Loot-Profile. Zum Fass-Test auch einen Artikel in die zweite Inventarhälfte legen und Kauf/Verkauf prüfen.

Die Builds und automatischen Tests ersetzen nicht den abschließenden Spieltest auf deinem Server: ItemsAdder und ModelEngine hängen zusätzlich von deinen dort installierten Assets ab.
