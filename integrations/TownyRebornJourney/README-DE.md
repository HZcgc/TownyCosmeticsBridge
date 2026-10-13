# Towny Reborn – eigenständige Reborn Journey

Version: 2.0.0-beta.1. Das ist das neue Testpaket zum vollständigen Ausbau aus Quests.

## Einfügen

1. Server vollständig stoppen und die bisherigen Plugin-/LuckPerms-Daten sichern.
2. Alle bisherigen `TownyRebornProgression*.jar` aus `plugins/` entfernen – auch die Pause-Version.
3. Den Inhalt dieses Pakets unter `plugins/` in deinen Server-Ordner kopieren.
4. **Den vorhandenen Ordner `plugins/TownyRebornProgression/` behalten.** Keine Spielerdateien ersetzen oder löschen.
5. Server vollständig starten. Das neue Plugin archiviert die alten Reborn-Quests/-Menüs selbst, bevor Quests/DeluxeMenus laden. Normale Quests bleiben bestehen.
6. Erst deinem Account Zugriff geben und `/reborn` testen. Für Spieler erst danach die Berechtigung freigeben.

Aus der Konsole:

```text
lp user HZcgc_No2 permission set townyreborn.progression.access true server=smp
lp user HZcgc_No2 permission set townyreborn.progression.admin true server=smp
```

Später für die öffentliche Freigabe:

```text
lp group default permission set townyreborn.progression.access true server=smp
```

Ohne `townyreborn.progression.access` gibt es keinen Command-/Menüzugriff und keine neuen Journey Points. Es gibt keinen globalen Pause-Schalter. Staff mit Wildcard-Permissions können die Berechtigung bereits besitzen.

## Inhalt

- Eigenes `/reborn` mit einem Schlangenmenü: zunächst **116 Level, vier Seiten**.
- **44 optionale permanente Perks**, die funktionalen Vorteile aller fünf bezahlten Ränge bis Level 86.
- `/tfly` ab Level 12; `/fly` ab Level 18 – beide auf der ersten Seite.
- Auf jedem Level eine **kostenlose** Belohnung: Minecraft-XP, Keys, Cosmetics oder zusätzliche Town-Claims.
- Level steigen über aktive Spielzeit, neue mcMMO-/Jobs-Level, Angeln, Erkundung und Advancements. Kein verpflichtender Quest-Grind.
- Geldkäufe und Belohnungsabholungen sind freiwillig und blockieren den weiteren Level-Aufstieg nicht.
- Frühere Level/Spielzeit/Questpunkte und dauerhaft gekaufte Perks werden übernommen. Monatsrang-Gruppen werden nicht als permanente Käufe übernommen.
- Reborn-Titel und -Level im Nametag; Reborn-Level anstelle der bisherigen mcMMO-Zahl im TAB.

## Was passiert mit den alten Dateien?

Die alten Reborn-Questdefinitionen, die Reborn-Kategorie und die alten `reborn_*.yml`-Menüs werden archiviert. Sie liegen danach unter:

```text
plugins/TownyRebornProgression/legacy-backup/<Zeitstempel>/
```

Die vorherige Progression-Config liegt unter `legacy-backup/config-pre-standalone.yml`. Alte Spielerdateien bleiben unter `playerdata/`; neue Profile/Belege liegen in `journeydata/`. Alte Profile werden einmal deterministisch eingelesen. Ein Placeholder-Aufruf allein schreibt keine neue Spielerdatei.

Die neue bearbeitbare Config entsteht automatisch unter `plugins/TownyRebornProgression/config.yml`. Preise, Titel, Punktkurve, Quellen und Belohnungen sind dort einstellbar. `REFERENCE-default-config.yml` ist nur eine Referenz und wird nicht automatisch über deine Einstellungen kopiert. Zum Verlängern `journey.steps` erhöhen und neue Belohnungen mit neuen IDs hinzufügen; vorhandene IDs beibehalten. Danach vollständig neu starten.

## Schutz vor doppelten Käufen und Belohnungen

Belege werden vor Geldabbuchung bzw. Belohnung gespeichert. Doppelklicks, parallele Aktionen und Rejoins lösen keine weitere Abbuchung aus. Sicher bezahlte Perks können nach einem Neustart ihre Permission-Zuteilung fertigstellen. Unklare Zahlungen und teilweise ausgelieferte Belohnungen werden **nicht automatisch erneut bezahlt/ausgegeben**; diese benötigen eine Prüfung durch den Owner.

```text
rebornadmin status <Spieler>
rebornadmin receipt <Spieler> grant
rebornadmin receipt <Spieler> cancel
rebornadmin gift <Spieler> delivered
```

`grant` nur nach bestätigter Zahlung, `delivered` nur nach geprüfter/nachgereichter Belohnung. `cancel` schließt den Beleg ohne weitere Abbuchung oder Erstattung. **Keine Journey-Dateien oder Reward-Marker löschen, um eine Ausgabe zu wiederholen.** Genauere Angaben stehen in `TECHNICAL-README.md`.

## Kurzer Test auf deinem Server

Mit deinem Account: `/reborn` öffnen, Seiten durchgehen, einen Perk ansehen/kaufen, eine kostenlose Belohnung holen und reloggen. Mit einem Alt ohne Berechtigung prüfen, dass der Command nicht zugänglich ist. Einmal die neuen Anzeigen und eine Town-/Cosmetic-Belohnung prüfen. Erst danach Default freigeben.

Lokal bestanden: **41 Java-Prüfungen + 12 Katalog-/Abhängigkeitsprüfungen**. Alle **36 vorhandenen Profile** und deren **48 alte Level-Claims** wurden geprüft; die alten Dateien bleiben bytegenau erhalten. Ein Live-Test auf deinem Server ist noch offen.

## Perks

Geld ist die vorhandene Ingame-Währung. Ein freigeschalteter Perk kann später gekauft werden; kein Kauf wird für den nächsten Level benötigt. Vorherige Upgrades sind nur innerhalb derselben Feature-Reihe erforderlich.

| Level | Permanenter Perk | Preis | Vorheriger Kauf |
| --- | --- | ---: | --- |
| 2 | 8 homes | 10,000 | – |
| 3 | 4 writable player vaults | 10,000 | – |
| 4 | +30 shop slots (50 base slots) | 10,000 | – |
| 6 | 2 active jobs | 25,000 | – |
| 7 | Trash menu | 10,000 | – |
| 8 | Seeds kit every 72 hours | 25,000 | – |
| 9 | Personal time | 10,000 | – |
| 10 | Personal weather | 10,000 | – |
| 11 | Portable crafting table | 25,000 | – |
| 12 | Town flight | 200,000 | – |
| 14 | Invisible item frames | 15,000 | – |
| 18 | Personal flight | 200,000 | – |
| 22 | Daily Supporter Crate Key | 75,000 | – |
| 24 | 12 homes | 25,000 | 8 homes |
| 26 | 6 writable player vaults | 25,000 | 4 writable player vaults |
| 28 | 100 base shop slots | 25,000 | +30 shop slots (50 base slots) |
| 30 | 2 player warps | 50,000 | – |
| 32 | Portable loom | 15,000 | – |
| 34 | Portable cartography table | 15,000 | – |
| 36 | Portable grindstone | 25,000 | – |
| 38 | Portable stonecutter | 25,000 | – |
| 40 | 16 homes | 50,000 | 12 homes |
| 42 | 8 writable player vaults | 50,000 | 6 writable player vaults |
| 44 | 150 base shop slots | 50,000 | 100 base shop slots |
| 46 | 3 active jobs | 50,000 | 2 active jobs |
| 48 | Lights kit every 72 hours | 50,000 | – |
| 50 | Nickname colours | 50,000 | – |
| 52 | Decorative Head Database | 50,000 | – |
| 54 | Glowing item frames | 25,000 | – |
| 58 | 22 homes | 75,000 | 16 homes |
| 60 | 12 writable player vaults | 75,000 | 8 writable player vaults |
| 62 | 200 base shop slots | 75,000 | 150 base shop slots |
| 64 | 3 player warps | 75,000 | 2 player warps |
| 66 | Nickname RGB gradients | 75,000 | Nickname colours |
| 68 | Portable anvil | 35,000 | – |
| 70 | Portable smithing table | 35,000 | – |
| 72 | Nickname text styles | 100,000 | Nickname RGB gradients |
| 74 | 30 homes | 100,000 | 22 homes |
| 76 | 16 writable player vaults | 100,000 | 12 writable player vaults |
| 78 | 240 base shop slots | 100,000 | 200 base shop slots |
| 80 | 4 active jobs | 75,000 | 3 active jobs |
| 82 | Redstone kit every 72 hours | 75,000 | – |
| 84 | Portable ender chest | 75,000 | – |
| 86 | Expanded town flight access | 150,000 | Town flight |

Shop-Stufen nutzen deine vorhandene SubscriptionBridge: Der erste Shop-Perk gibt +30 zum aktiven Grundlimit. Spätere Stufen sichern Mindest-Gesamtlimits von 100/150/200/240; Add-ons und das 300-Shop-Maximum laufen über die vorhandene Bridge. Die kostenlose Default-Ausstattung (ein Player Warp, einfache Nicknames sowie Schilder-/Ambossfarben) wird nicht erneut verkauft.

Die Keys nutzen die bereits vorhandenen digitalen Key-IDs `daily`, `vote`, `cosmetic` und `patron` (= Supporter). Halloween und die bestehenden Pet-Shops werden dabei nicht verändert. Town-Belohnungen geben der Town zum Zeitpunkt der Abholung mehr verfügbare Claim-Chunks und können später abgeholt werden, wenn man noch keine Town hat.
