# Towny Reborn Journey 2.0.0-beta.5

## Hotfix für bereits installierte Beta.4

Server stoppen, die Beta.4-JAR entfernen und aus `TownyReborn-Journey-Hotfix-2.0.0-beta.5.zip` den `plugins/`-Baum einfügen. Er enthält ausschließlich die neue JAR und die aktualisierte Progression-Config für die günstigeren Preise. `playerdata`, `journeydata` und `migration-backups` bleiben im bestehenden Ordner. Vollständig starten. Der feste Fortschrittsmarker bleibt unverändert; bereits seit Beta.4 verdiente JP werden nicht nochmals zurückgesetzt. Die ausführliche ZIP enthält zusätzlich die bisherigen statischen TAB-, Nametag- und Quests-Configs.

Beta.5 trennt **zum Kauf freigeschaltet** von **dauerhaft gekauft**. Vor dem Level-Neustart erreichte Kauf-Freigaben werden aus der passenden Sicherung mit der bisherigen 30/15-JP-Kurve und den früheren Perk-Gates wiederhergestellt und als `unlocked-perks` separat gespeichert. Die Sicherung belegt auch frühere tatsächliche Käufe; diese bleiben ohne erneute Zahlung erhalten. Neue Spieler müssen `/fly` auf Level 116 erreichen. Eine bereits vor dem Neustart erreichte Fly-Kauf-Freigabe bleibt erhalten.

| Perk-Anzeige | Bedeutung |
|---|---|
| Grün | Individuell dauerhaft gekauft |
| Blau | Aktueller Rang bietet den Zugriff |
| Chest | Zum permanenten Kauf freigeschaltet |
| Rot | Journey-Level noch nicht erreicht |

Owner-/Supporter-Rechte werden nicht als individuelle Käufe gespeichert. Bestehende direkte permanente LuckPerms-Kaufmarker werden beim Laden übernommen, einschließlich der vorher gekauften Vorgänger. Abgeholte Belohnungen werden nicht neu ausgezahlt.

`rebornadmin status <player>` zeigt getrennt `owned` (gekauft) und `unlocked-for-purchase` (Kauf-Freigaben). Die Reparatur läuft auch bei bereits umgestellten Beta.4-Profilen und wiederholt keine Level-Rücksetzung.

## Günstigere Perks und Flug-Erklärung

Alle 43 optionalen Perk-Preise sind gesenkt: insgesamt rund 25 %. Die Journey-Anforderungen und Belohnungen bleiben gleich. Käufe werden nicht erneut berechnet; offene Belege behalten auch bei einem Speicherfehler ihren tatsächlich bezahlten alten Betrag. Die neue `config.yml` muss ausdrücklich eingefügt werden, damit die Preise gelten; das Plugin schreibt vorhandene Configs weiterhin nicht um.

| Bisher | Jetzt |
|---:|---:|
| $10.000 | $7.500 |
| $15.000 | $10.000 |
| $25.000 | $20.000 |
| $35.000 | $25.000 |
| $50.000 | $37.500 |
| $75.000 | $55.000 |
| $100.000 | $75.000 |
| $150.000 | $110.000 |
| $200.000 | $150.000 |

`Town flight` schaltet `/tfly` innerhalb der eigenen Town frei. Der bisher unklar bezeichnete `Expanded town flight access` heißt jetzt **Town flight anywhere** und gibt `townyflight.bypass`: TownyFlight entfernt den Flug beim Verlassen der eigenen Town nicht mehr, und `/tfly` lässt sich auch in fremden Towns und in der Wildnis benutzen. Weil dies bereits freies Fliegen erlaubt, erfordert auch dieser Perk für neue Freigaben **Level 116**. Frühere belegte Käufe und Kauf-Freigaben bleiben erhalten. `/fly` bleibt ebenfalls auf Level 116. Die beiden Perks werden einzeln gekauft; ein Spieler muss nicht beide kaufen. Andere Server-Plugins können unabhängig davon Flugbeschränkungen setzen.

Der Blaze-Tab verwendet nun die verifizierte Mojang-Identität und den Namen **MHF_Blaze** zusammen mit der festen Textur statt einer künstlichen UUID ohne Profilnamen. Die Textur wurde als PNG direkt von Mojangs Texturserver geprüft; die Darstellung im Minecraft-Client ist separat zu prüfen.

## Vollständiges Einfügepaket

1. Server vollständig stoppen und den bestehenden `TownyRebornProgression`-Ordner sichern.
2. Die bisherige Progression-JAR entfernen. Nur eine Version laden.
3. Den `plugins/`-Baum aus dieser ZIP über die bestehenden Plugin-Ordner kopieren und die mitgelieferten Configs ersetzen.
4. Alle vorhandenen `playerdata`, `journeydata`, Quest-Dateien und Datenbanken behalten.
5. Server vollständig starten. `/progression` öffnet die Journey; `/reborn perks` öffnet die Rank-Perks.

Zugriff bleibt `townyreborn.progression.access`, standardmäßig aus. Bestehende LuckPerms-Zuweisungen gelten weiter. Es gibt keine neue Pflicht-Permission.

## Einmaliger Neustart der Level

Diese Version setzt JP auf **0 / Level 1**, sobald ein bisheriges Profil erstmals geladen wird. Offline-Spieler werden beim nächsten Laden/Login umgestellt. Gekaufte permanente Perks, deren Vorgänger, direkte LP-Freischaltungen, bereits abgeholte Belohnungen, tägliche Zähler und offene Kauf-/Belohnungsbelege bleiben erhalten. Normale Quests, mcMMO, Jobs, Paid-Ränge und Geld werden durch den Level-Neustart nicht zurückgesetzt.

Vor dem Reset wird die komplette Originaldatei unter `migration-backups/long-journey-2026-10-v1/` gesichert. Ohne erfolgreiche Sicherung und Speicherung wird kein teilweise umgestelltes Profil übernommen. Neue Profile beginnen ebenfalls bei Level 1.

**`journey.progress-revision: long-journey-2026-10-v1` unverändert lassen.** Der identische gespeicherte Wert verhindert, dass Relog, Neustart oder das erneute Einfügen dieser Version nochmals zurücksetzen. Bestehende Skill-/Jobs-Höchstwerte bleiben als Schutz gegen erneutes Farmen erhalten. Die erste gültige Messung nach der Umstellung wird als neuer Ausgangspunkt erfasst und gibt keine rückwirkenden JP.

Eine bereits gekaufte Flugfreischaltung bleibt nutzbar. Neue Fly-Freigaben benötigen **Level 116**, den letzten Level der Journey; vor dem Neustart bereits erreichte Kauf-Freigaben bleiben erhalten. `/tfly` ist ein eigener Towny-Perk mit seiner bisherigen Freischaltung.

## Journey und Oberfläche

- 116 Level mit jeweils einem eigenen englischen Rangnamen; im TAB kompakt `J001` bis `J116`.
- Rangfarben verändern sich von Pink zu Lime, mit einem sanften Textgradienten. Der LP-Rang bleibt separat.
- Vier durchgehende Snake-Seiten: Endet die vorige Seite unten, startet die nächste unten; entsprechend oben.
- Grün: früherer abgeschlossener Level. Gelb: aktueller Level. Rot: zukünftiger gesperrter Level.
- Die Fortschrittsflasche zeigt Ziel, Prozent und fehlende JP. Der persönliche Kopf zeigt Balance, Journey-/Spielzeit, mcMMO, Jobs, Fische, Quests und gekaufte Perks.
- `How to progress` ordnet sechs Aktivitäten in zwei Reihen; darunter JP-Erklärung, persönliche Stats und Tageslimits. Kein Server-Snapshot.
- Bei einem neuen Level: Titel, Nachricht mit Rang und verfügbaren Belohnungen/Perks sowie `minecraft:ui.toast.challenge_complete`.
- Die fünf Köpfe im Perk-Menü sind klickbare Rank-Seiten. Blaze hat eine feste MHF-Blaze-Kopftextur; ein eigenes Buch erklärt das Blättern.
- Quests übernimmt das aktuelle Layout aus `Quests(8).zip`. Experience-Quests belegen Slot 40, den früheren Steinplatz; die Progression-Verknüpfung bleibt wie im Screenshot auf Slot 51.

Das Plugin schreibt keine anderen Plugin-Configs um. Die statischen TAB-, LyttleNametag- und Quests-Dateien liegen bereits passend im Paket.

## Grind und Belohnungen

Level 2: 150 JP; Level 3: 330 JP; Level 116: insgesamt **213.900 JP**. JP sind kumulativ und werden nicht ausgegeben. Jeder Aufstieg setzt den nächsten Rangnamen automatisch.

Alle Tageslimits resetten um **00:00 UTC**, unabhängig von der lokalen JVM-/Host-Zeitzone. Aktive Spielzeit hat jetzt ebenfalls ein Budget: 360 JP/Tag. Die übrigen Quell-Limits bleiben bestehen. Das theoretische Maximum ist 2.160 JP pro UTC-Tag: Im normalen Spiel ist der letzte Level frühestens am 100. verschiedenen UTC-Tag möglich. Biome und Advancements sind einmalig und dieses Maximum damit nicht dauerhaft erreichbar; die tatsächliche Dauer hängt von Spielzeit und Aktivitäten ab.

Die gesamte Journey enthält einmalig 525.500 $, Ressourcenpakete, einige Minecraft-XP-Belohnungen, einzelne Crate-Keys und insgesamt 20 Town-Claim-Chunks. Die letzte Belohnung enthält 100.000 $ und 5 Supporter-Keys. Es gibt keine UltraCosmetics-Belohnungen und keinen täglichen Supporter-Key-Perk.

Erhöhte Belohnungen ersetzen keinen alten Auszahlungsbeleg: Bereits gelieferte Geschenke werden nicht nochmals ausgezahlt. Offene alte Belege behalten ihre damaligen Geldbeträge, Befehle und Town-Ziele. Details: `REWARDS.md`, `PERKS.md`, `JOURNEY-POINTS-DE.md`.

## Nach dem Start prüfen

Mit einem Testspieler `/progression` öffnen und einen zuvor gekauften Perk testen. Bei erstmals umgestellten Profilen ist der Start Level 1 / 0 JP; bereits auf Beta.4 verdiente JP bleiben erhalten. Relog darf den Fortschritt nicht erneut löschen. Blaze-Kopf und günstigere Preise in `/reborn perks` prüfen. In `/quests` sollen Experience-Quests auf dem früheren Steinplatz und die Progression-Verknüpfung an ihrer Screenshot-Position stehen. TAB/Nametag sollten die neue Rangfarbe und die angeschlossene kleine mcMMO-Zahl zeigen.

Owner-Status: `rebornadmin status <player>`. `rebornadmin addpoints <player> <amount>` umgeht den normalen Grind und ist ausschließlich für Owner-Tests/administrative Korrekturen gedacht. Offene Zahlungsbelege erst anhand der tatsächlichen Zahlung/Auslieferung prüfen; kein Geld und keine Keys automatisch nachzahlen.

Die mitgelieferten Prüfberichte unterscheiden kontrollierte Tests von einem Live-Test auf Paper/Minecraft. Diese Version wurde nicht auf dem laufenden Server installiert.
