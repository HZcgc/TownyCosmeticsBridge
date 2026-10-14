# Journey Points: Punkte, Grind und tägliche Limits

Alle sieben Quellen erhöhen denselben JP-Gesamtstand. Er wird nicht durch Geldkäufe oder Belohnungen verbraucht. Minecraft-XP ist ein eigener Wert.

| Aktivität | JP | Tageslimit |
|---|---:|---:|
| Aktive Spielminute | 1 | 360 |
| Neuer mcMMO-Powerlevel | 5 | 500 |
| Neuer persönlicher Höchstlevel in einem Job | 12 | 500 |
| Mit der Angel gefangener Fisch | 3 | 300 |
| Neu erfasstes Biom | 8 einmalig | 200 |
| Neuer Advancement, ohne Rezepte | 15 einmalig | 300 |
| Erfolgreich abgeschlossene Quest, auch Wiederholungen | 10 | 300 |

Neue Quest-Abschlüsse geben zusätzlich **10 JP**, maximal **300 JP pro UTC-Tag**. Auch jeder neue Abschluss einer regulär wiederholbaren Quest zählt. Der native Quests-Abschluss-Event wird verwendet; der schwankende Gesamtzähler ist nur eine Anzeige. Bereits früher abgeschlossene Quests werden nicht erneut als neue Abschlüsse importiert. Ihre eigenen Aufgaben und Belohnungen bleiben im Quests-Plugin. Die Verknüpfung zu `/progression` verbindet die Menüs.

## Beispiel

10 aktive Minuten + 5 Fische + 1 neuer mcMMO-Level = **30 JP**, sofern das jeweilige Tagesbudget frei ist. Damit bleiben auf Level 1 noch 120 JP bis Level 2.

| Journey-Level | Erforderliche Gesamt-JP |
|---|---:|
| 1 | 0 |
| 2 | 150 |
| 3 | 330 |
| 4 | 540 |
| 5 | 780 |
| 12 | 3.300 |
| 29 | 15.540 |
| 58 | 56.430 |
| 87 | 122.550 |
| 116 | 213.900 |

Der Preis des nächsten Aufstiegs steigt um 30 JP: 150, 180, 210 usw. Die Formel für Level L lautet `150 × (L−1) + 30 × (L−1) × (L−2) / 2`. Mit 250 Gesamt-JP ist man Level 2 und braucht noch 80 JP bis Level 3.

## Reset und Schutz vor wiederholten Punkten

Die Quell-Budgets erneuern sich um **00:00 UTC**. Der Gesamtstand bleibt dabei erhalten; ungenutzte Tagesbudgets werden nicht angespart. Die Punkte-/Menü-Aktualisierung läuft alle 30 Sekunden; Fische, Advancements und Quest-Abschlüsse lösen zusätzlich eigene Events aus.

Aktive Spielzeit verlangt Bewegung, Bauen oder Interaktion. Nach 90 Sekunden ohne Aktivität läuft keine Zeitvergütung weiter. Creative, Spectator, NPCs, Test-Bots und die ignorierten Welten `afk`/`hub` geben keine JP. Quellen-Limits gelten getrennt: Ist Fischen voll, kann man durch andere Quellen weiterkommen.

Die erste gültige mcMMO-/Job-Messung ist ein Ausgangspunkt. Nur neue persönliche Höchststände darüber werden belohnt. Ein Relog oder Job-/Skill-Reset gibt bereits verdiente Levels nicht erneut. Biome und Advancements zählen nur einmal pro Spieler. Bei ausgeschöpfter Tagesgrenze werden zusätzliche Punkte dieser Quelle nicht für morgen gesammelt.

Das theoretische Gesamtmaximum ist 2.460 JP pro UTC-Tag. 86 solche Tage liefern 211.560 JP, noch unter dem finalen Ziel von 213.900. Neue Spieler ab 0 JP benötigen deshalb mindestens 87 unterschiedliche UTC-Tage im normalen Spiel. Historisch übernommene JP zählen als bereits verdienter Vorsprung. Einmalige Quellen sind endlich; für die tatsächliche Dauer sind Spielweise und Spielzeit ausschlaggebend.

Quest-Abschlüsse werden mit Quest-ID und letztem Abschlusszeitpunkt im Profil gespeichert. Derselbe oder ein älterer Event gibt keine zweite Gutschrift. Das gilt auch nach Relog und Neustart; bei vollem Tagesbudget wird der Abschluss verbucht, ohne Punkte für morgen zu sammeln. Punkte und Abschlussbeleg werden gemeinsam atomar gespeichert. Bei einem Speicherfehler wird das Profil zur Prüfung angehalten.

## Früheren Fortschritt übernehmen

Beta.7 übernimmt **alte belegte JP + die seit dem Beta.4-Neustart verdienten JP**. Die heutige 150/30-Kurve berechnet daraus den neuen Level. Eine alte Levelnummer wird nicht einfach gleichgesetzt: 5.052 alte JP ergeben Level 15; mit 4 neuen JP sind es 5.056 JP, ebenfalls Level 15.

Die native Sicherung vor dem Neustart hat Vorrang. Für frühere Rank-Daten ohne native Sicherung wird die damalige Umrechnung mit der eingefrorenen 30/15-Kurve verwendet: Maximum aus dem alten Level-Mindeststand und aktiven Minuten plus 25 JP pro altem Quest-Punkt. Der damalige Rank-Level wird wie zuvor auf `(Level−1) × 12`, maximal 116, abgebildet. Beispiel: Rank-Level 4, 6.596 Minuten und 541 Quest-Punkte ergeben 20.121 JP / aktuellen Level 33. Quellen werden nicht doppelt addiert.

Die Übernahme setzt kein Tageslimit zurück und liest keine heutigen mcMMO-/Jobs-Level als rückwirkende neue Punkte ein. Bereits abgeholte Belohnungen und offene Belege bleiben bestehen. Neue Spieler beginnen weiterhin bei 0 JP.

Vor der Gutschrift werden der aktuelle native Stand gesichert und ein unabhängiger Beleg geschrieben. `history-credit-revision: pre-reset-points-2026-10-v1` und `progress-revision: long-journey-2026-10-v1` unverändert lassen. **`history-credits`, `migration-backups`, `journeydata` und `playerdata` behalten.** Relog, Neustart, erneute Installation oder ältere JARs, die Profilfelder entfernen, dürfen dieselben historischen JP nicht nochmals gutschreiben. Unklare abgebrochene Übernahmen werden vor einer weiteren Gutschrift zur Prüfung angehalten.

`/rebornadmin status <player>` zeigt den Gesamtstand und die historische Gutschrift mit Quelle. Der Spieler muss online sein. Die vollständige Installation und Belegregeln stehen in `README-DE.md`.

## Frühere Kauf-Freigaben

Zuvor erreichte Perk-Kauf-Freigaben bleiben unabhängig vom neu berechneten Level erhalten. Preis und bereits gekaufte Vorgänger bleiben erforderlich. Rangzugriffe wie Owner/Wither bedeuten keine individuell bezahlten permanenten Perks. Bekannte tatsächliche Käufe werden nicht nochmals berechnet, und alte Belohnungsbelege werden nicht erneut ausgezahlt.
