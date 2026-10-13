# Towny Reborn Journey – Update 2.0.0-beta.2

## Einfügen

1. Server vollständig stoppen und die bisherigen Plugin-/LuckPerms-Daten sichern.
2. Die alte TownyRebornProgression-JAR aus `plugins/` entfernen. Es darf nur eine Version dort liegen.
3. Die neue JAR aus dem `plugins/`-Ordner dieses Pakets hineinkopieren.
4. **Den vorhandenen Ordner `TownyRebornProgression/` samt `config.yml`, `playerdata/` und `journeydata/` behalten.**
5. Vollständig starten. Das Plugin aktualisiert die Beta-Config und die vorhandenen TAB-, TownyChat- und LyttleNametag-Anzeigeformate automatisch und sichert die alten Dateien.
6. Mit deinem Account `/reborn` prüfen. Die öffentliche Zugriffsberechtigung bleibt weiterhin standardmäßig aus.

Die Referenzdateien im Paket sind zum Nachschauen. Sie werden nicht über deine Server-Config kopiert. Eine neue Quests-Config ist nicht nötig: Der bereits eingebaute Ausbau der alten Reborn-Menüpunkte läuft weiterhin automatisch; normale Quests bleiben erhalten.

## Was jetzt sichtbar ist

- Beim nächsten Level steht exakt, wie viele **Journey Points** fehlen und welche Aktivitäten sie bringen.
- Die XP-Flasche zeigt **erledigte und verbleibende Prozent**, fehlende Punkte, aktive Zeit, mcMMO-, Jobs- und Angelwerte. Anklicken öffnet die Anleitung mit tatsächlichen Punktraten, Tageslimits und einem Server-Snapshot.
- **Grün:** Level erreicht. **Gelb:** nächstes Levelziel. **Rot:** spätere, gesperrte Level.
- Die Schlange geht zwischen den Seiten auf derselben Höhe weiter: oben → oben, unten → unten.
- 116 kostenlose, einmalige Belohnungen: Geld, Baumaterialien, Essen, einzelne Keys und Town-Claims. Minecraft-XP nur bei Level 12, 52 und 104.
- Keine UltraCosmetics-Freischaltungen und kein täglicher Supporter-Key-Perk im Journey-Katalog. Supporter-Keys gibt es nur bei vier einzelnen Meilensteinen.
- 43 separat angebotene, optionale permanente Perks. Geldkäufe und Belohnungsabholungen blockieren keinen Levelaufstieg. `/fly` bleibt ab Level 18 und `/tfly` ab Level 12 erhältlich.

**200 QuickShop-Shops sind Handels-Shops an Kisten/Fässern.** Player Warps sind eigene Reiseziele und bleiben bei maximal **drei**. Der irreführende `/shops`-Verweis wurde bei QuickShop entfernt. Die höheren Trading-Shop-Stufen sichern die vorhandenen Bridge-Mindestlimits; Add-ons und das 300-Shop-Maximum berechnet weiterhin die SubscriptionBridge.

Die vollständigen Belohnungen stehen in `REWARDS.md`, alle optionalen Käufe mit Preis und Voraussetzungen in `PERKS.md`.

## Anzeigen

| Anzeige | Reihenfolge |
| --- | --- |
| Nametag, Zeile 1 | Town · Nation |
| Nametag, Zeile 2 | LP-Rang · voller Journey-Titel |
| Nametag, Zeile 3 | Displayname |
| TAB | LP-Rang · Journey-Kürzel · Displayname · bisherige kleine mcMMO-Zahl |
| Chat | LP-Rang · voller Journey-Titel · Displayname · mcMMO |

RGB-Displaynames, Kanal-Tags, Town-/Nation-Kanäle und AFK-Marker bleiben erhalten. Das Plugin passt vorhandene Formatfelder an, statt eine fremde komplette Chat-Config einzuspielen. Die Kürzel stehen in `journey.short-titles`.

## Punkte verdienen

| Aktivität | Journey Points | Tageslimit |
| --- | ---: | ---: |
| Aktiv spielen | 1 je aktiver Minute | – |
| Neues persönliches mcMMO-Powerlevel | 5 | 500 |
| Neues persönliches Level in einem Job | 12 | 500 |
| Fisch mit der Angel fangen | 3 | 300 |
| Neues Biom besuchen | 8 | 200 |
| Neues Advancement ohne Rezept | 15 | 300 |

Mehrere Aktivitäten kombinieren oder nach eigenen Vorlieben spielen. Der Aufstieg erfolgt automatisch, sobald die angezeigte Punktzahl erreicht ist. Vorhandene mcMMO-/Jobs-Level setzen den Startwert; Jobwechsel oder Relogs geben alte Punkte nicht erneut. Tageslimits setzen sich um Mitternacht deutscher Zeit zurück. Die Anleitung liest deine tatsächlichen Einstellungen, wenn du die Raten änderst.

## Zugriff

Aus der Konsole:

```text
lp user HZcgc_No2 permission set townyreborn.progression.access true server=smp
lp user HZcgc_No2 permission set townyreborn.progression.admin true server=smp
```

Erst bei öffentlicher Freigabe:

```text
lp group default permission set townyreborn.progression.access true server=smp
```

Ohne Access-Permission kein Command-/Menüzugriff und keine neuen Punkte. Wildcard-Staff-Permissions können Zugriff bereits enthalten.

## Übernahme und Belege

Punkte, aktive Zeit, dauerhaft gekaufte Perks und bereits abgeholte Beta-Belohnungen bleiben erhalten. Alte Belohnungs-IDs und LP-Marker werden dem jeweiligen Meilenstein zugeordnet: Eine frühere XP-/Cosmetic-Belohnung löst beim Austausch **keine zusätzliche neue Auszahlung** aus. Offene Belege behalten ihre ursprünglichen Commands, Town und Geldbeträge.

Die alte Beta-Config liegt anschließend unter `legacy-backup/config-before-catalogue-v3.yml`; frühere Anzeigeformate unter `legacy-backup/display-v3/`. Kurve, Punktquellen, bestehende Perk-Preise und Zugriffseinstellungen bleiben erhalten. Der bisherige Belohnungskatalog wird ersetzt. Bestehende Monatsränge verwaltet weiterhin ihre eigene Bridge.

Geld-/Key-Ausgaben werden vorab gespeichert. Bei einem unklaren oder teilweise ausgeführten Vorgang gibt es keinen automatischen zweiten Versuch. Nach Prüfung durch den Owner:

```text
rebornadmin status <Spieler>
rebornadmin receipt <Spieler> grant
rebornadmin receipt <Spieler> cancel
rebornadmin gift <Spieler> delivered
```

`delivered` nur nach geprüfter/nachgereichter Belohnung. Keine Spielerdateien, Claims oder LP-Marker löschen, um eine Belohnung erneut auszulösen.

## Teststand

Die 24 bestehenden Wiederherstellungstests laufen gegen alte und neue JAR. Zusätzlich prüft das Update Config-/Claim-Übernahme, Geldzahlungen bei Fehlern und Neustarts, durchgehende Seiten, Prozentwerte und die tatsächlichen Inventar-Anzeigen. Details in `VERIFICATION.txt`. Die Prüfung im laufenden Minecraft-Server steht noch aus.
