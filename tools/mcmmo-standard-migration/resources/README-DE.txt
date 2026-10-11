TOWNY REBORN - mcMMO Retro -> Standard, Version 2.3.001
=====================================================

Dieses Paket ist eine Offline-Umstellung eines bestimmten Datenstands.
Es wird nichts automatisch auf deinem Server ausgefuehrt.
Das Werkzeug ist KEIN Bukkit-Plugin: nicht in plugins legen.

WICHTIG: Wurde der Server seit dem Export weiter bespielt, darfst du die
enthaltene mcmmo.users NICHT darueberkopieren. Sonst fehlen spaetere Fortschritte.
Der ZIP-Export muss NACH einem sauberen, vollstaendigen Server-Stopp erfolgen.
Der Server bleibt bis zur Installation gestoppt.

VOR DER INSTALLATION
-------------------
1. Wartung aktivieren; Spieler vom SMP nehmen und weitere Logins verhindern.
2. Im Server-Panel sauber stoppen und warten, bis der Prozess beendet ist.
   Kein /reload und kein PlugMan. Auto-Neustart fuer die Wartung pausieren.
3. Den kompletten aktuellen plugins/mcMMO-Ordner separat sichern und als ZIP
   herunterladen. Auch die Server-Sicherung des Hosters verwenden.
4. Falls dieses Paket aus genau diesem gestoppten Export erstellt wurde:
   weiter mit INSTALLATION. Andernfalls zuerst NEUES PAKET ERSTELLEN.

NEUES PAKET ERSTELLEN (bei einem spaeteren Datenstand)
---------------------------------------------------
Im Ordner Werkzeug liegt eine ausfuehrbare JAR fuer Java 17 oder neuer.
Unter Windows Start-Windows.cmd oeffnen. Die beiden Dateiauswahlen fragen
nach dem frischen mcMMO-ZIP und der auf dem Server installierten mcMMO.jar.
Das Programm erzeugt daneben ein neues Paket; die Eingaben bleiben unveraendert.
Eine GUI braucht eine normale Desktop-Java-Installation.

Alternativ im Terminal:
  java -jar TownyReborn-mcMMO-Standard-Converter.jar mcMMO-frisch.zip mcMMO.jar neu.zip

Falls du Java nicht starten kannst: den frischen gestoppten Export erneut
bereitstellen. Den SMP bis zur abgeschlossenen Umstellung gestoppt lassen.

Die Konvertierung ist auf die gepruefte mcMMO-JAR beschraenkt und prueft ihren
SHA-256. Andere Builds, MySQL, geaenderte XP-Kurven, eigene Level-Caps,
aktivierte Level-Up-Rewards und unbekannte Datenzeilen brechen die Umstellung ab.
Falls die Rundung eines abgeleiteten Skills eine bisher freigeschaltete hoehere
Faehigkeitsstufe sperren wuerde, bricht das Werkzeug ebenfalls ab. Dann braucht
dieser konkrete Grenzfall eine Pruefung statt einer automatischen Anpassung.
Bereits konvertierte Daten werden an RetroMode=false bzw. dem Marker erkannt.

Optionaler Vergleich mit einem zweiten Export vom weiterhin gestoppten Server:
  java -jar TownyReborn-mcMMO-Standard-Converter.jar --verify paket.zip mcMMO-frisch.zip
MATCH bedeutet: alle vier betroffenen Dateien entsprechen dem Originalstand.
Das Programm kann NICHT feststellen, ob dein entfernter Server wirklich aus ist.

INSTALLATION
------------
1. Bei weiterhin gestopptem Server alle vier Dateien aus INSTALL/plugins/mcMMO
   an die entsprechenden Stellen unter plugins/mcMMO hochladen:
     config.yml
     experience.yml
     skillranks.yml
     flatfile/mcmmo.users
   Zusaetzlich den Marker towny-standard-migration.properties mit hochladen.
   Diese Dateien gehoeren zusammen. Keine halbe Installation starten.
2. Die vorhandenen mcMMO- und McmmoGuiV2-JARs bleiben auf dem Server.
   TownyRebornProgression, dessen playerdata, LuckPerms und Quests bleiben erhalten.
   Keine playerdata-Ordner durch alte Kopien ersetzen.
3. Server mit weiter aktivierter Wartung starten. Die Konsole auf Fehler pruefen.
4. Als Owner mindestens pruefen:
     /mcstats, /mctop, /mcrank und /skills
     XP-Anzeige und Grundfaehigkeiten mehrerer unterschiedlicher Spieler
     /ranks und bereits gekaufte Perks
     TAB/Scoreboard und aktive mcMMO-Boosts
   Auch Logout/Login pruefen. Keine Migration ein zweites Mal ausfuehren.
5. Wieder sauber stoppen/starten und die Speicherung erneut pruefen.
   Erst danach Wartung beenden und den automatischen Neustart wieder aktivieren.

WAS GEANDERT WIRD
----------------
* RetroMode: true -> false.
* Einzelne gespeicherte Skill-Level: floor(Retro-Level / 10).
* Angefangene Zehnergruppen werden mit den bisherigen Rest-XP zusammengezaehlt.
  Die gesamte rekonstruierte XP-Summe pro Skill bleibt EXAKT erhalten.
* Die LINEAR-Basis geht von 10200 auf 10000, der Faktor bleibt 200. Das gleicht
  den Indexversatz der nativen Standard-Formel aus. Der Bedarf fuer ein neues
  Standard-Level entspricht dadurch exakt den zehn bisherigen Retro-Leveln.
  Skill-Multiplikatoren, Global-Rate, Diminished Returns und Boosts bleiben gleich.
* Chat-Meilensteine gehen von 100 auf 10; das entspricht der neuen Levelskala.
* Die 19 Grundfaehigkeiten mit Rank_1=1 in BEIDEN Skalen bekommen im
  Standard-Modus Rank_1=0. Dadurch bleiben bisherige Start-Freischaltungen
  bei der groberen Skala erhalten. Diese Grundfaehigkeiten sind damit auch
  fuer neue Spieler sofort verfuegbar; hoehere Anforderungen bleiben gleich.

Die Powerlevel-Anzeige addiert die GANZEN neuen Einzel-Level. Sie ist deshalb
nicht einfach die alte Gesamtsumme geteilt durch zehn. Reste liegen weiter
in den XP der einzelnen Skills. Auch niedrige Skills mit Level 0 verlieren
ihre bisherige XP nicht. Salvage/Smelting werden nativ aus ihren Elternskills
abgeleitet; ihre Anzeige und Chancen koennen sich durch Rundung leicht aendern.
Das ist die groebere Standard-Skala. Das Werkzeug verschenkt keine Skill-Level.

REPORT enthaelt den Vergleich je Spieler und je Skill sowie Pruefsummen.
Die Spielerdaten und diese Berichte gehoeren nicht in ein oeffentliches GitHub.
Die vorbereitete Probe wurde offline geprueft. Der Test mit Spielern auf deinem
Server, einschliesslich Rejoin und Neustart, muss noch in der Wartung erfolgen.

RUECKWEG, solange die Wartung noch aktiv ist
-----------------------------------------
1. Server sauber stoppen.
2. Alle vier Dateien aus ROLLBACK/plugins/mcMMO gemeinsam zurueckspielen.
3. Den Marker plugins/mcMMO/towny-standard-migration.properties entfernen.
4. Server in der Wartung starten und den alten Stand pruefen.

BACKUP/mcMMO-before-migration.zip ist zusaetzlich das UNVERAENDERTE Eingabe-ZIP.
Nach Wiedereroeffnung und neu erspielten Fortschritten ist ein alter Rollback
nicht verlustfrei. Dann nicht blind die Sicherung einspielen; zuerst den neuen
Stand sichern und eine gezielte Rueckumrechnung pruefen.
