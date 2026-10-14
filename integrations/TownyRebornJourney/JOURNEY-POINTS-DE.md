# Journey Points: Punkte, Grind und tägliche Limits

Alle sechs Quellen erhöhen denselben JP-Gesamtstand. Er wird nicht durch Geldkäufe oder Belohnungen verbraucht. Minecraft-XP ist ein eigener Wert.

| Aktivität | JP | Tageslimit |
|---|---:|---:|
| Aktive Spielminute | 1 | 360 |
| Neuer mcMMO-Powerlevel | 5 | 500 |
| Neuer persönlicher Höchstlevel in einem Job | 12 | 500 |
| Mit der Angel gefangener Fisch | 3 | 300 |
| Neu erfasstes Biom | 8 einmalig | 200 |
| Neuer Advancement, ohne Rezepte | 15 einmalig | 300 |

Normal abgeschlossene Quests geben aktuell keine JP. Ihre eigenen Aufgaben und Belohnungen bleiben im Quests-Plugin. Die Verknüpfung zu `/progression` verbindet die Menüs.

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

Die Quell-Budgets erneuern sich um **00:00 UTC**. Der Gesamtstand bleibt dabei erhalten; ungenutzte Tagesbudgets werden nicht angespart. Die Punkte-/Menü-Aktualisierung läuft alle 30 Sekunden; Fische und Advancements lösen zusätzlich eigene Events aus.

Aktive Spielzeit verlangt Bewegung, Bauen oder Interaktion. Nach 90 Sekunden ohne Aktivität läuft keine Zeitvergütung weiter. Creative, Spectator, NPCs, Test-Bots und die ignorierten Welten `afk`/`hub` geben keine JP. Quellen-Limits gelten getrennt: Ist Fischen voll, kann man durch andere Quellen weiterkommen.

Die erste gültige mcMMO-/Job-Messung ist ein Ausgangspunkt. Nur neue persönliche Höchststände darüber werden belohnt. Ein Relog oder Job-/Skill-Reset gibt bereits verdiente Levels nicht erneut. Biome und Advancements zählen nur einmal pro Spieler. Bei ausgeschöpfter Tagesgrenze werden zusätzliche Punkte dieser Quelle nicht für morgen gesammelt.

Das theoretische Gesamtmaximum ist 2.160 JP pro UTC-Tag. 99 solche Tage liefern 213.840 JP, noch unter dem finalen Ziel von 213.900. Der letzte Level benötigt deshalb mindestens 100 unterschiedliche UTC-Tage im normalen Spiel. Einmalige Quellen sind endlich; für die tatsächliche Dauer sind Spielweise und Spielzeit ausschlaggebend.

## Einmaliger neuer Start

Beta.4 beginnt für alle Profile bei 0 JP / Level 1. Der dauerhafte Marker `long-journey-2026-10-v1` stellt sicher, dass dieser Schritt nur einmal erfolgt. Die bisher gekauften permanenten Features und alle Belege werden übernommen. Alte Spielerdateien werden vor der Umstellung gesichert; niemand muss seinen Datenordner löschen.

## Kauf-Freigaben nach dem Neustart

Beta.5 bewahrt vorher erreichte Perk-Kauf-Freigaben getrennt von tatsächlich gekauften Features. Bereits freigeschaltete Käufe bleiben auch auf Journey-Level 1 möglich; Preis und gekaufte Vorgänger bleiben erforderlich. Rangzugriffe wie Owner/Wither bedeuten keine individuell bezahlten permanenten Perks. Bereits nach Beta.4 verdiente JP und tägliche Zähler werden nicht zurückgesetzt.
