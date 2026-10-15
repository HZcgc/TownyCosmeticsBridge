# Towny Reborn Journey 2.0.0 – stabile Version

## Update von Beta 9 auf 2.0.0

1. Server vollständig stoppen und den bestehenden Progression-Ordner sichern.
2. Die bisherige Progression-JAR entfernen; nur **TownyRebornProgression-2.0.0.jar** behalten.
3. Aus **TownyReborn-Journey-2.0.0-Update.zip** den `plugins/`-Baum einfügen. **JAR und Progression-Config ersetzen.**
4. Alle Datenordner behalten: `journeydata`, `playerdata`, `migration-backups`, `history-credits`, `staff-audit` sowie vorhandene Legacy-Sicherungen.
5. Server vollständig starten und `/progression` sowie `/rebornadmin status <player>` prüfen.

TAB zeigt jetzt den Level und drei zum Rangnamen passende Buchstaben: **[1NEW] Newcomer**, **[15GAR] Gardener**, **[88RAD] Radiant**, **[116RLG] Reborn Legend**. Jede der 116 Kombinationen ist unterschiedlich; die vollständige Liste steht in `LEVEL-CODES.md`. Der Farbverlauf bleibt Pink → Lime. Der LP-Rang bleibt davor, der Spielername dahinter, mcMMO weiterhin direkt am Namen. Chat und schwebender Journey-Titel bleiben ausgeschrieben.

Die kleine Update-ZIP ersetzt **nur** die Progression-JAR und deren eigene Config. Die bestehenden TAB-Platzhalter erhalten automatisch die neuen Kürzel. Quests-, TAB- und LyttleNametag-Dateien müssen für dieses Update nicht ersetzt werden. Das vollständige Paket ist zusätzlich für die Referenzinstallation verfügbar.

**Kein neuer Reset und keine zweite Gutschrift:** JP, Level, Käufe, frühere Kauf-Freigaben, Tageslimits und Belohnungsbelege bleiben erhalten. Beide Revisionsmarker bleiben unverändert. Staff Connector 1.5.1 und Staff HQ API 9 funktionieren mit demselben Auditformat weiter.

Behoben wurden direkte Fensterwechsel im Klick-Event, verspätete und mehrfache Menüaktionen sowie Fehlerpfade bei Speicherfehlern, Zahlungsbelegen und Erfolgsmeldungen. Details: `CHANGELOG-2.0.0.md`. Die Tests laufen gegen echte temporäre Profildateien mit kontrollierten Plugin-/Client-Schnittstellen; die Darstellung im laufenden Minecraft-Client ist separat zu prüfen.

## Bestehende Staff-HQ-Anbindung (seit Beta.9)

Die Journey schreibt bestätigte Profiländerungen als dauerhafte Auditbelege unter `staff-audit/`. Staff Connector 1.5.0 übernimmt sie in sein eigenes dauerhaftes Ausgangsprotokoll und überträgt sie mit stabilen Ereignis-IDs ins Staff HQ 5.2.0. JP und Quellen, Levelwechsel, historische Gutschriften, Kauf-Freigaben, gekaufte Perks, tatsächliche Zahlungsbeträge, Belohnungsbelege, Reward-Befehle und Town-Boni sind damit getrennt nachvollziehbar. Früherer Besitz wird nicht als neuer Kauf gerechnet.

**Keine Revisionsmarker ändern und keine Datenordner ersetzen.** Die stabile Version 2.0.0 bleibt mit dem installierten Staff Connector 1.5.1 und HQ 5.2.1 / API 9 kompatibel. Die Journey-Auditbelege verwenden dasselbe Format; Connector und Website müssen für dieses Update nicht erneut ersetzt werden. Details: `STAFF-AUDIT-DE.md`.

## Update: Quests und eGlow (Beta.8)

Neue Quests-Abschlüsse geben 10 JP, bis 300 JP pro UTC-Tag. Wiederholbare Quests zählen bei jedem tatsächlichen neuen Abschluss. Alte Abschlüsse werden nicht nachträglich erneut bezahlt; die historischen JP bleiben erhalten. Quests ist eine optionale Integration und der eigenständige Journey-Pfad bleibt getrennt. Die neue Quest-Kachel steht unter den übrigen Aktivitäten in `How to progress`.

Auf der Wither-Perk-Seite ist **Glow colours and effects** für **$20.000 ab Level 88** hinzugekommen, direkt vor dem letzten Fly-Eintrag. Der permanente Kauf gewährt persönliche eGlow-Menüs, Farben, Blinken, Rainbow und eigene Effekte. Verwaltung anderer Spieler, Debug und Reload bleiben separate Staff-Rechte. Nutzung: `/eglow`, ausschalten: `/eglow off`. Der Eintrag kauft keinen monatlichen Wither-Rang und ändert keine LuckPerms-Gruppe.

Quests-JP und der persönliche eGlow-Perk bleiben in 2.0.0 unverändert enthalten. Für die Installation gelten die Schritte im Abschnitt „Update von Beta 9 auf 2.0.0“.

## Fortschritt übernehmen (Beta.4 bis Beta.7)

Server stoppen, den bestehenden Progression-Ordner sichern, die bisherige JAR entfernen und aus `TownyReborn-Journey-2.0.0-Update.zip` den `plugins/`-Baum einfügen. Er enthält die neue JAR und die neue Progression-Config. **Beide ersetzen**, damit die Fortschrittsübernahme aktiviert ist. `playerdata`, `journeydata`, `migration-backups` und der neue Ordner `history-credits` bleiben im bestehenden Ordner. Vollständig starten. Offline-Spieler werden beim nächsten Laden/Login übernommen. Die ausführliche ZIP enthält zusätzlich die bisherigen statischen TAB-, Nametag- und Quests-Configs.

Beta.7 rechnet den früher verdienten Fortschritt auf die längere Journey um: **alte belegte JP + seit Beta.4 verdiente JP**. Der aktuelle Stand wird nicht durch eine alte Spielerdatei ersetzt. Bereits gezahlte Perks, offene Transaktionen, abgeholte Belohnungen, Tageszähler, Skill-Höchststände und mcMMO-/Jobs-Daten bleiben erhalten.

Diese Version trennt **zum Kauf freigeschaltet** von **dauerhaft gekauft**. Vor dem Level-Neustart erreichte Kauf-Freigaben werden aus der passenden Sicherung mit der bisherigen 30/15-JP-Kurve und den früheren Perk-Gates wiederhergestellt und als `unlocked-perks` separat gespeichert. Die Sicherung belegt auch frühere tatsächliche Käufe; diese bleiben ohne erneute Zahlung erhalten. Neue Spieler müssen `/fly` auf Level 116 erreichen. Eine bereits vor dem Neustart erreichte Fly-Kauf-Freigabe bleibt erhalten.

| Perk-Anzeige | Bedeutung |
|---|---|
| Grün | Individuell dauerhaft gekauft |
| Blau | Aktueller Rang bietet den Zugriff |
| Chest | Zum permanenten Kauf freigeschaltet |
| Rot | Journey-Level noch nicht erreicht |

Owner-/Supporter-Rechte werden nicht als individuelle Käufe gespeichert. Bestehende direkte permanente LuckPerms-Kaufmarker werden beim Laden übernommen, einschließlich der vorher gekauften Vorgänger. Abgeholte Belohnungen werden nicht neu ausgezahlt.

`rebornadmin status <player>` zeigt getrennt `owned` (gekauft) und `unlocked-for-purchase` (Kauf-Freigaben). Die Reparatur läuft auch bei bereits umgestellten Beta.4-Profilen und wiederholt keine Level-Rücksetzung.

## Günstigere Perks und Flug-Erklärung

Die bestehenden 43 optionalen Perk-Preise bleiben gegenüber Beta.4 gesenkt: insgesamt rund 25 %. Die JP-Kurve und Belohnungen bleiben gleich. Käufe werden nicht erneut berechnet; offene Belege behalten auch bei einem Speicherfehler ihren tatsächlich bezahlten alten Betrag. Die neue `config.yml` muss ausdrücklich eingefügt werden, damit die Town-Flug-Erweiterung und Preise gelten; das Plugin schreibt vorhandene Configs weiterhin nicht um.

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

**Town flight** schaltet `/tfly` innerhalb der eigenen Town auf Level 12 frei. **Flight in all towns** erweitert dies auf sämtliche Towns auf Level 86 und gibt ausschließlich `townyflight.alltowns`. Beim Übergang in die Wildnis endet dieser Town-Flug. Der Perk gibt weder `townyflight.bypass` noch `townyflight.wilderness` oder `/fly`. Freies `/fly` bleibt der letzte Perk auf Level 116. Preis der Town-Erweiterung: **$110.000**. Frühere belegte Käufe und Kauf-Freigaben behalten ihren stabilen internen Perk-Schlüssel `town_flight_bypass`; es wird nicht erneut bezahlt.

Beim Laden eines Besitzers ersetzt das Plugin seine alte direkte permanente Perk-Berechtigung `townyflight.bypass` durch All-Town-Zugriff in einem LuckPerms-Speichervorgang. Die native Besitzmarkierung wird vorher gesichert. Ein fehlgeschlagener LP-Speichervorgang wird beim nächsten Laden/Login erneut gespeichert, ohne erneut zu zahlen. Globale oder zum konfigurierten Server passende alte direkte Grants werden ersetzt; Welt-Kontexte, andere oder kombinierte Server-Kontexte, befristete Rechte, negative Nodes, Gruppen und separate `/fly`-Rechte bleiben unangetastet. Nur persistente direkte LP-Nodes belegen einen alten Kauf; transiente oder geerbte Zugriffe werden nicht zu Käufen.

**Zum Flug-Test einen normalen Spieler verwenden**, der nur diese beiden Town-Perks besitzt. Owner-/Staff-Rechte und der aktive Wither-Rang können separat weiterhin freies Fliegen erlauben. Die Journey verändert diese Rang-Gruppen nicht.

Der Blaze-Tab verwendet nun die verifizierte Mojang-Identität und den Namen **MHF_Blaze** zusammen mit der festen Textur statt einer künstlichen UUID ohne Profilnamen. Die Textur wurde als PNG direkt von Mojangs Texturserver geprüft; die Darstellung im Minecraft-Client ist separat zu prüfen.

## Vollständiges Einfügepaket

1. Server vollständig stoppen und den bestehenden `TownyRebornProgression`-Ordner sichern.
2. Die bisherige Progression-JAR entfernen. Nur eine Version laden.
3. Den `plugins/`-Baum aus dieser ZIP über die bestehenden Plugin-Ordner kopieren und die mitgelieferten Configs ersetzen.
4. Alle vorhandenen `playerdata`, `journeydata`, `migration-backups`, `history-credits`, Quest-Dateien und Datenbanken behalten.
5. Server vollständig starten. `/progression` öffnet die Journey; `/reborn perks` öffnet die Rank-Perks.

Zugriff bleibt `townyreborn.progression.access`, standardmäßig aus. Bestehende LuckPerms-Zuweisungen gelten weiter. Es gibt keine neue Pflicht-Permission.

## Einmalige Übernahme des alten Fortschritts

Die Übernahme erhält die verdienten JP, nicht die alte numerische Journey-Levelnummer. Die aktuelle Kurve berechnet daraus den heutigen Rang. Beispielsweise ergeben 5.052 alte JP und 4 neue JP zusammen **5.056 JP / Level 15**.

| Vorhandener Nachweis | Berechnung |
|---|---|
| Native Journey-Sicherung vor Beta.4 | Gespeicherte alte JP unverändert übernehmen |
| Nur frühere Rank-Daten | Bisherige Umrechnung aus Level, aktiven Minuten und Quest-Punkten mit der eingefrorenen 30/15-Kurve rekonstruieren |
| Kein alter Nachweis | Aktuelle JP behalten, keine historischen Punkte erfinden |

Die native Sicherung unter `migration-backups/long-journey-2026-10-v1/journeydata/` hat Vorrang. Danach folgt die alte `playerdata`-Sicherung; nur wenn diese fehlt, wird die noch vorhandene Legacy-Datei verwendet. Diese Quellen werden **nicht zusammengerechnet**. Bei Legacy-Daten gilt die frühere Formel: Maximum aus dem alten Level-Mindeststand und `aktive Minuten + 25 × Quest-Punkte`. Das alte Rank-Level wird wie zuvor auf `(Level−1) × 12`, maximal 116, abgebildet. Die Mindest-JP stammen aus der früheren 30/15-Kurve, nicht aus der teureren aktuellen Kurve. Beispiel: Rank-Level 4, 6.596 aktive Minuten und 541 Quest-Punkte ergeben **20.121 alte JP / aktueller Level 33**, plus neue JP.

Gespeicherte alte Perk-Kauf-Freigaben bleiben erhalten, auch wenn die längere Kurve eine andere Levelnummer ergibt. Tatsächliche Käufe bleiben getrennt. Historisch aufgezeichnete aktive Minuten werden einmalig in der persönlichen Journey-Spielzeit berücksichtigt; die gesamten aktiven Minuten und Tagesbudgets werden nicht neu erzeugt. Neue Spieler beginnen weiterhin bei 0 JP / Level 1.

Vor der Gutschrift wird der aktuelle native Stand unter `migration-backups/pre-reset-points-2026-10-v1/journeydata/` gesichert. Das Plugin schreibt danach einen unabhängigen Beleg unter `history-credits/<UUID>.yml`, speichert den neuen JP-Stand und den Profilmarker atomar und schließt den Beleg ab. Nach einem Speicherfehler wird nur ein exakt unveränderter Ausgangsstand automatisch fortgesetzt. Unklare oder beschädigte Belege werden zur Prüfung angehalten; es wird keine zweite Gutschrift geraten.

Der separate abgeschlossene Beleg schützt auch dann, wenn eine ältere JAR die neuen Profilfelder entfernt. Relog, Neustart, erneutes Einfügen oder eine geänderte Credit-Revisionsbezeichnung ergeben keine zweite Gutschrift. **`history-credits` und `migration-backups` behalten.** Beide Config-Marker unverändert lassen:

```yaml
journey:
  progress-revision: long-journey-2026-10-v1
  history-credit-revision: pre-reset-points-2026-10-v1
```

Die Gutschrift zahlt keine Items, Keys, Geld oder bereits gelieferten Belohnungen aus. Noch nie abgeholte, nun erreichte Meilensteine können regulär einmalig beansprucht werden. Bei aktiviertem Journey-Zugriff erhält der Spieler eine Nachricht mit Gutschrift und aktuellem Level. `/rebornadmin status <player>` zeigt `historical-credit=<JP>/<Quelle>` neben dem Gesamtstand; der Spieler muss online sein.

## Journey und Oberfläche

- 116 Level mit jeweils einem eigenen englischen Rangnamen; im TAB Levelnummer plus drei Rangbuchstaben, von `[1NEW]` bis `[116RLG]`.
- Rangfarben verändern sich von Pink zu Lime, mit einem sanften Textgradienten. Der LP-Rang bleibt separat.
- Vier durchgehende Snake-Seiten: Endet die vorige Seite unten, startet die nächste unten; entsprechend oben.
- Grün: früherer abgeschlossener Level. Gelb: aktueller Level. Rot: zukünftiger gesperrter Level.
- Die Fortschrittsflasche zeigt Ziel, Prozent und fehlende JP. Der persönliche Kopf zeigt Balance, Journey-/Spielzeit, mcMMO, Jobs, Fische, Quests und gekaufte Perks.
- `How to progress` ordnet sieben Aktivitäten in zwei Reihen; darunter JP-Erklärung, persönliche Stats und Tageslimits. Kein Server-Snapshot.
- Bei einem neuen Level: Titel, Nachricht mit Rang und verfügbaren Belohnungen/Perks sowie `minecraft:ui.toast.challenge_complete`.
- Die fünf Köpfe im Perk-Menü sind klickbare Rank-Seiten. Blaze hat eine feste MHF-Blaze-Kopftextur; ein eigenes Buch erklärt das Blättern.
- Quests übernimmt das aktuelle Layout aus `Quests(8).zip`. Experience-Quests belegen Slot 40, den früheren Steinplatz; die Progression-Verknüpfung bleibt wie im Screenshot auf Slot 51.

Das Plugin schreibt keine anderen Plugin-Configs um. Die statischen TAB-, LyttleNametag- und Quests-Dateien liegen bereits passend im Paket.

## Grind und Belohnungen

Level 2: 150 JP; Level 3: 330 JP; Level 116: insgesamt **213.900 JP**. JP sind kumulativ und werden nicht ausgegeben. Jeder Aufstieg setzt den nächsten Rangnamen automatisch.

Alle Tageslimits resetten um **00:00 UTC**, unabhängig von der lokalen JVM-/Host-Zeitzone. Aktive Spielzeit hat jetzt ebenfalls ein Budget: 360 JP/Tag. Die übrigen Quell-Limits bleiben bestehen. Das theoretische Maximum ist 2.460 JP pro UTC-Tag: Für neue Spieler ab 0 JP ist der letzte Level frühestens am 87. verschiedenen UTC-Tag möglich; alte Spieler bekommen ihren belegten Vorsprung zurück. Biome und Advancements sind einmalig und dieses Maximum damit nicht dauerhaft erreichbar; die tatsächliche Dauer hängt von Spielzeit und Aktivitäten ab.

Die gesamte Journey enthält einmalig 525.500 $, Ressourcenpakete, einige Minecraft-XP-Belohnungen, einzelne Crate-Keys und insgesamt 20 Town-Claim-Chunks. Die letzte Belohnung enthält 100.000 $ und 5 Supporter-Keys. Es gibt keine UltraCosmetics-Belohnungen und keinen täglichen Supporter-Key-Perk.

Erhöhte Belohnungen ersetzen keinen alten Auszahlungsbeleg: Bereits gelieferte Geschenke werden nicht nochmals ausgezahlt. Offene alte Belege behalten ihre damaligen Geldbeträge, Befehle und Town-Ziele. Details: `REWARDS.md`, `PERKS.md`, `JOURNEY-POINTS-DE.md`.

## Nach dem Start prüfen

Mit einem Testspieler `/progression` öffnen und einen zuvor gekauften Perk testen. Mit vorhandenen alten Daten muss die einmalige historische Gutschrift im Status erscheinen; bereits seit Beta.4 verdiente JP bleiben zusätzlich erhalten. Relog darf den Fortschritt nicht erneut löschen. Blaze-Kopf und günstigere Preise in `/reborn perks` prüfen. In `/quests` sollen Experience-Quests auf dem früheren Steinplatz und die Progression-Verknüpfung an ihrer Screenshot-Position stehen. TAB/Nametag sollten die neue Rangfarbe und die angeschlossene kleine mcMMO-Zahl zeigen.

Owner-Status: `rebornadmin status <player>`. `rebornadmin addpoints <player> <amount>` umgeht den normalen Grind und ist ausschließlich für Owner-Tests/administrative Korrekturen gedacht. Offene Zahlungsbelege erst anhand der tatsächlichen Zahlung/Auslieferung prüfen; kein Geld und keine Keys automatisch nachzahlen.

Die mitgelieferten Prüfberichte unterscheiden kontrollierte Tests von einem Live-Test auf Paper/Minecraft. Diese Version wurde nicht auf dem laufenden Server installiert.
