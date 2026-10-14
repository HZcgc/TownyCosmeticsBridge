# So funktionieren Journey Points

Journey Points, kurz **JP**, sind dein gemeinsamer Fortschrittszähler. Mehrere Aktivitäten geben Punkte für dasselbe Konto. Du kannst sie kombinieren oder nach deinen Vorlieben spielen.

| Aktivität | JP | Begrenzung |
| --- | ---: | --- |
| Aktiv spielen | 1 pro Minute | Stehen ohne Aktivität zählt nicht dauerhaft |
| mcMMO-Powerlevel erhöhen | 5 pro neuem Level | 500 JP pro Tag |
| Ein Job-Level erhöhen | 12 pro neuem persönlichem Höchststand | 500 JP pro Tag |
| Mit der Angel einen Fisch fangen | 3 pro Fang | 300 JP pro Tag |
| Ein neues Biom besuchen | 8 | 200 JP pro Tag; jedes Biom einmal |
| Ein neues Advancement erreichen | 15 | 300 JP pro Tag; Rezepte ausgeschlossen |
| Normale Quest abschließen | aktuell 0 | Quelle ist ausgeschaltet |

Die erste gültige mcMMO-/Jobs-Abfrage speichert deinen Startwert. Bereits vorhandene Levels werden nicht sofort in neue Punkte umgewandelt. Nur spätere neue Höchststände bringen JP. Jobwechsel, Levelresets oder Relogs zahlen alte Fortschritte nicht erneut aus.

## Ein Beispiel

Bei noch verfügbarem Tageslimit:

- 10 aktive Minuten = **10 JP**
- 5 gefangene Fische = **15 JP**
- 1 neues mcMMO-Powerlevel = **5 JP**

Zusammen sind das **30 JP**. Von einem frischen Profil aus erreichst du damit Level 2.

## Levelanforderungen

Die Anzeige verwendet deine **Gesamtpunkte**:

| Level | Insgesamt benötigte JP | Zusätzliche JP seit dem vorherigen Level |
| --- | ---: | ---: |
| 1 | 0 | – |
| 2 | 30 | 30 |
| 3 | 75 | 45 |
| 4 | 135 | 60 |
| 5 | 210 | 75 |
| 6 | 300 | 90 |

Jeder weitere Schritt kostet 15 JP mehr als der vorherige Schritt. Es wird nichts von deinem Konto abgezogen. Zum Beispiel hast du bei 50 JP Level 2 und brauchst noch **25 JP**, bis du insgesamt 75 JP für Level 3 hast.

Dein Titel hängt vom Journey-Level ab. Er ist unabhängig von deinem LP-Rang und der angezeigten mcMMO-Zahl. Historische Progression-Daten konnten beim Übergang bereits einen Startfortschritt ergeben; vorhandene native Punkte bleiben erhalten.

## Farben, Prozent und Belohnungen

**Grün** sind ältere abgeschlossene Level. **Gelb** ist dein aktuelles Journey-Level. **Rot** sind noch gesperrte zukünftige Level. Bei einem vollständig erreichten letzten Level ist der Pfad abgeschlossen.

Die Prozentanzeige misst den Fortschritt innerhalb des aktuellen Levels auf dem Weg zum nächsten. Bei 50 JP in Level 2 sind 20 von 45 benötigten zusätzlichen Punkten geschafft: ungefähr 44% erledigt und 56% offen.

Geld-/Item-/Key-Belohnungen können einmal beim erreichten Level abgeholt werden. Das Abholen ist freiwillig. Auch die separat freigeschalteten Perks müssen nicht gekauft werden, um weiterzuspielen.

Perks kosten **Ingame-Geld**, keine JP. Minecraft-XP-Belohnungen verändern deinen JP-Stand nicht. Normale Quest-Rewards bleiben bei Quests; der Button auf Slot 50 öffnet deinen eigenständigen Journey-Pfad.

## Einstellungen

Punktquellen, Tageslimits, Titel, Punktkurve und Perk-Preise stehen in `plugins/TownyRebornProgression/config.yml`. Die Hilfeseite im Menü liest die tatsächlich eingestellten Raten. Tageslimits setzen sich um Mitternacht deutscher Zeit zurück; die regelmäßige Anzeige aktualisiert sich alle 30 Sekunden.
