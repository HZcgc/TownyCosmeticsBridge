# Towny Reborn Journey – 2.0.0-beta.3

## Einfügen

1. Server vollständig stoppen.
2. Die bisherige `TownyRebornProgression*.jar` aus `plugins/` entfernen. Nur eine Progression-Version dort liegen lassen.
3. Den kompletten `plugins/`-Inhalt dieses Pakets über die vorhandenen Ordner kopieren und die mitgelieferten Configs ersetzen.
4. **Die vorhandenen Plugin-Ordner behalten.** Spielerdateien, Quests-Definitionen und Datenbanken bleiben auf dem Server.
5. Vollständig starten. Mit `/progression` oder `/reborn` öffnen und den neuen Button in `/quests` testen.

Das Paket enthält die fertigen Dateien:

```text
plugins/TownyRebornProgression-2.0.0-beta.3.jar
plugins/TownyRebornProgression/config.yml
plugins/TAB/groups.yml
plugins/LyttleNametag/config.yml
plugins/Quests/config.yml
plugins/Quests/categories.yml
```

Das Plugin schreibt keine bestehenden Configs mehr um und verändert keine TAB-, Nametag-, Quests-, TownyChat- oder DeluxeMenus-Dateien. Automatische Config-Updater und Menüentfernung sind vollständig ausgebaut. Änderungen werden künftig über die fertigen Dateien im Paket eingespielt. Beim ersten Installieren ohne eigene Config wird wie bei normalen Plugins die eigene Standard-Config angelegt.

## Perks und Farben

Die permanenten Features sind nach **Skeleton → Creeper → Blaze → Dragon → Wither** gruppiert. Oben im Perk-Menü kann man direkt einen Rangbereich auswählen. `/fly` steht ganz zuletzt im Wither-Bereich. **Sein bisheriges Freischalt-Level 18 und der Preis bleiben unverändert**; die Änderung betrifft die Sortierung. `/tfly` bleibt bei Level 12.

Jeder Kauf ist ein einzelnes permanentes Feature. Der nächste Journey-Level braucht keinen vorherigen Kauf. Preise, Berechtigungen, Kaufketten, Reward-IDs und Punktekurve bleiben gegenüber Beta.2 erhalten.

- **Grün:** früheres, abgeschlossenes Level.
- **Gelb:** dein aktuelles Journey-Level.
- **Rot:** zukünftiges, gesperrtes Level.

Die Prozentanzeige zeigt, wie weit du innerhalb des aktuellen Levels auf dem Weg zum nächsten bist. Belohnungen beim erreichten Level können weiterhin später abgeholt werden. Die Schlange bleibt über alle Seiten verbunden.

## Anzeigen

| Anzeige | Layout |
| --- | --- |
| Nametag, Zeile 1 | Town · Nation |
| Nametag, Zeile 2 | LP-Rang · Journey-Titel mit direkt angehängter kleiner mcMMO-Zahl |
| Nametag, Zeile 3 | Displayname |
| TAB | LP-Rang · Journey-Kürzel · Displayname mit direkt angehängter kleiner mcMMO-Zahl |

Zwischen dem LP-Rangbild und dem Journey-Titel wird der Abstand vergrößert. Die mcMMO-Zahl hat keinen zusätzlichen Abstand. TAB nutzt den direkten `%reborn_short_title%`-Placeholder, zum Beispiel `CIT` für Citizen. RGB-Namen, AFK-Marker und Rangbilder bleiben erhalten. Andere TAB-Einstellungen einschließlich Header, Sortierung, Scoreboard und Stresstest werden nicht ersetzt.

## Quests

Das Kategorienfenster zeigt alle bisherigen 17 Kategorien in einem gleichmäßigen Raster, mit einer kleinen Bedienhilfe und einer Zustandslegende. Der Nether Star bleibt auf Slot 49. Direkt rechts davon liegt auf Slot 50 der Button **Reborn Progression**.

Dieser Button öffnet dasselbe Menü wie `/progression`. Die Öffnung läuft im nächsten Tick, damit das Schließen des Quests-Fensters sie nicht überschreibt. Der Spieler braucht weiterhin seine Journey-Access-Permission. Das normale Quest-Limit bleibt bei sechs aktiven Quests; IDs, Aufgaben, Anforderungen und Spielerfortschritte bleiben erhalten.

## Journey Points

Alle aktiven Quellen zahlen auf **ein gemeinsames JP-Konto** ein. Sie werden nicht ausgegeben. Mehr dazu mit Beispielen in `JOURNEY-POINTS-DE.md` und im Hilfemenü bei der XP-Flasche.

| Aktivität | JP | Tageslimit |
| --- | ---: | ---: |
| Aktiv spielen | 1 je Minute | – |
| Neues mcMMO-Powerlevel | 5 | 500 |
| Neues persönliches Level in einem Job | 12 | 500 |
| Fisch mit der Angel fangen | 3 | 300 |
| Neues Biom besuchen | 8 | 200 |
| Neues Advancement ohne Rezept | 15 | 300 |

Normale Questabschlüsse sind aktuell als JP-Quelle ausgeschaltet. Der neue Quests-Button verbindet die Menüs. Minecraft-XP-Belohnungen und Ingame-Geld werden getrennt von JP verwaltet. Perks kosten Geld; das Level steigt über die gesammelten Punkte.

## Zugriff und Daten

Die Access-Permission bleibt standardmäßig aus. Bereits gesetzte Berechtigungen bleiben bestehen.

```text
lp user HZcgc_No2 permission set townyreborn.progression.access true server=smp
lp user HZcgc_No2 permission set townyreborn.progression.admin true server=smp
```

Erst bei öffentlicher Freigabe:

```text
lp group default permission set townyreborn.progression.access true server=smp
```

Vorhandene `journeydata/`, `playerdata/` und LP-Reward-Marker behalten. Geld-/Key-Claims können nach einem Neustart nicht automatisch wiederholt werden. Bei einem unklaren Beleg nach Prüfung durch den Owner:

```text
rebornadmin status <Spieler>
rebornadmin receipt <Spieler> grant
rebornadmin receipt <Spieler> cancel
rebornadmin gift <Spieler> delivered
```

Das Paket enthält keine hochgeladenen Spielerdateien. Alle Belohnungen stehen in `REWARDS.md`, die nach Rang sortierten Käufe in `PERKS.md`. Lokale Prüfungen stehen in `VERIFICATION.txt`; der Test im laufenden Minecraft-Server bleibt separat.
