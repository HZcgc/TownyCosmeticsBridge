# Journey und Staff HQ

Die stabile TownyRebornProgression 2.0.0 verwendet dasselbe Auditformat wie Beta 9. Der zuletzt installierte TownyRebornStaffConnector 1.5.1 und Staff HQ 5.2.1 / API 9 bleiben kompatibel. Für dieses Journey-Update müssen Connector und Website nicht erneut ersetzt werden. Ursprüngliche Mindestversionen des Auditformats: Journey Beta 9, Connector 1.5.0 und HQ 5.2.0 / API 8.

## Erfasste Daten

- Neue JP je Aktivität, manuelle Korrekturen und historische Gutschriften getrennt.
- Journey-Level und Rangtitel, erreichte Kauf-Freigaben und früher behaltene Freigaben.
- Permanent gekaufte Perks, vorhandener früherer Besitz und Änderungen am Besitz getrennt.
- Gespeicherte Zahlungen mit dem tatsächlich belasteten Belegbetrag, auch nach Preisänderungen.
- Offene PREPARED-/DEBITED-Kaufbelege und abgeschlossene Auslieferung.
- Abgeholte Belohnungen samt damaliger Befehlsliste, Geld und Town-Claim-Boni. Alte Belohnungsmarker werden nicht erneut als neue Auszahlungen gezählt.
- Aktuelle Profile einschließlich Offline-Spielern: JP, Level, Titel, Besitz, Kauf-Freigaben, Reward-Belege, Tagesbudgets und Übernahme-Nachweis.

Im HQ gibt es direkt im Menü und auf dem Dashboard **Reborn Journey**. Im Spielerprofil gibt es ebenfalls einen Journey-Abschnitt. Zugriff verwendet die vorhandene HQ-Berechtigung `analytics.view`; es gibt keine neue Minecraft-Pflicht-Permission. Explizite Rollenverbote gelten weiter.

## Dauerhafte Übergabe

Ein Auditbeleg wird vor dem Profilwechsel vorbereitet und erst nach dessen erfolgreicher Speicherung bestätigt. Sequenz und Beleg schützen vor erfundenen Änderungen nach einem fehlgeschlagenen Schreibvorgang. Ein unabhängiger Zähler verhindert die Wiederverwendung alter Ereignis-IDs. Der Connector liest diese Belege im Hintergrund und quittiert sie erst nach dauerhafter Übergabe in seinen Ausgangspuffer. HQ erkennt Wiederholungen an stabilen IDs.

`staff-audit/`, `journeydata/`, `playerdata/`, `migration-backups/` und `history-credits/` behalten. Der Connector räumt nur erfolgreich übernommene Auditdateien selbst auf. Seine bisherigen Queue- und Cursor-Dateien behalten. Netzwerkfehler führen zu Wiederholungen, nicht zu neuen Spielerauszahlungen.

Bestehende Profile werden als aktueller Bestand übernommen. Alte Käufe werden nicht anhand heutiger Listenpreise zu neuen Umsätzen erklärt. Ereignisdetails unterliegen dem vorhandenen 30-Tage-Fenster; dauerhafte Summen und der aktuelle Profilstand bleiben getrennt davon verfügbar. Unklare/defekte Belege bleiben zur Prüfung erhalten.
