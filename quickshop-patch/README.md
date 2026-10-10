# Towny Reborn patch for QuickShop-Hikari 6.3.0.3

Source baseline: https://github.com/QuickShop-Community/QuickShop-Hikari/tree/6.3.0.3

## Installieren

Server stoppen, die vorhandene QuickShop-JAR durch QuickShop-Hikari-6.3.0.3-townybarrels.3.jar ersetzen und den Server starten. Es darf nur eine QuickShop-JAR im Plugin-Ordner liegen. Den Ordner QuickShop-Hikari mit Config, Datenbank und vorhandenen Shops behalten. CatchBall und BiggerBarrels bleiben installiert; für diesen Fix benötigen sie kein Update. Bestehende Shops funktionieren nach dem Neustart mit dem bisherigen Musteritem.

Der Tiervergleich ist bei matcher.work-type: 1 automatisch aktiv. Dieser Modus ist in der mitgelieferten Serverconfig bereits eingestellt. Keine globale NBT-Ausnahme und kein Config-Reset sind nötig. Optional lässt sich die neue Regel mit matcher.towny-animals-enabled: false abschalten. Andere Vergleichsmodi bleiben unverändert.

## Was zusammen gehandelt werden kann

| Item | Ignorierte individuelle Daten | Weiterhin getrennte Eigenschaften |
| --- | --- | --- |
| Axolotl-Eimer | UUID, Gesundheit, Jagd-Cooldown, genaue Alters-/Zucht-Cooldown-Ticks und sonstige bekannte Laufzeitwerte | Farbe einschließlich seltenem Blau, Jungtier/erwachsen, Name, Altersperre, andere Itemkomponenten und Plugin-Daten |
| CatchBall-Tier | CatchBall-Fang-UUID, Kopfprofil-UUID, bekannte Laufzeitwerte und konfigurierte Fanghistorie mit Spieler, Zeit und Ort | Tierart, Variante, Jungtier/erwachsen, Name, Besitzer/Zähmung, Eigenschaften, Inventar, Villager-Beruf und Angebote, Textur, andere Plugin-Daten und verbleibende Ball-Nutzungen |

CatchBall-Fanghistorie wird anhand der tatsächlich geladenen DropSkullLore-Vorlage erkannt. Andere Beschreibungen und zusätzliche Lore-Zeilen bleiben Teil des strengen Vergleichs. Unbekannte CatchBall-Versionen behalten den strengen Lore-Vergleich. Die kompatible Schnittstelle wurde an CatchBall 2.1.2-TownyReborn-3Uses-r4-VillagerPlacement geprüft.

Nur ausdrücklich bekannte Laufzeitfelder werden aus einer Kopie der Daten für den Vergleich entfernt. UUIDs in Besitzerdaten, Attributen, Angeboten oder Plugin-Daten werden nicht pauschal entfernt. Unbekannte und fehlerhafte Daten werden nicht als Wildcard akzeptiert. NBT-/SNBT-Auswertung ist in Größe, Verschachtelung und Tag-Anzahl begrenzt.

## Tatsächliche Tiere übertragen

Upstream bereitet bei einem Warenübergang die tatsächlich entfernten Stacks vor, fügt aber das Shop-Muster hinzu. Der Patch führt die vorbereitete Operation aus. Käufer erhalten dadurch die gelagerten Tiere mit ihrer tatsächlichen UUID/Fanghistorie und vollständigen gespeicherten Daten; Verkäufer geben ihre tatsächlichen Items ins Lager. Unterschiedliche Tiere werden beim Einfügen weiterhin nur nach dem nativen strengen Itemvergleich gestapelt. Bei einem fehlgeschlagenen Transfer setzt QuickShop beide Inventare aus ihren Snapshots zurück.

Absichtlich unbegrenzte Adminshops behalten ihr natives Verhalten: Ohne Quellinventar erzeugen sie das Musteritem. Für Handel mit individuellen Tieren einen normalen Shop mit tatsächlichem Lagerbestand verwenden.

## BiggerBarrels und Build

Der bisherige BiggerBarrels-Fix bleibt enthalten. BukkitInventoryWrapper behält Inventarhalter und Ort des physischen Fasses, nutzt aber BiggerBarrels.getBarrel(Block) für Bestand, Snapshots, Einfügen, Leeren und Wiederherstellung. Der Kompatibilitätsmarker für TownyRebornBarrelShops bleibt erhalten. Normale Inventare behalten ihr natives Verhalten.

Ersetzte Upstream-Klassen: BukkitInventoryWrapper, BukkitItemMatcherImpl und SimpleInventoryTransaction. Zwei neue, interne Helfer lesen und vergleichen die gespeicherten Tierdaten.

tools/build.py prüft die SHA-256 der unveränderten Originalversion, kompiliert den Patch und ergänzt die vollständige originale JAR. Version, Lizenz, Patchquellen und diese Anleitung sind im Ergebnis enthalten. Der Patch ist an QuickShop 6.3.0.3 gebunden; für neue QuickShop-Versionen muss er neu geprüft werden.

Die Regressionstests nutzen den realen Bukkit-Vergleicher, native QuickShop-Entnahme-/Einfügeoperationen und den Warenübergang: mehrere verschiedene Tiere, Kauf/Verkauf, Teilmengen, volles Zielinventar, fehlender Bestand, wiederholte Versuche, exakte Metadaten und Rollback. Die vorhandenen BiggerBarrels-Tests laufen weiter. Zusätzlich werden Descriptor, erlaubte Klassenänderungen und gebündelte Quellen geprüft. Ein Test auf dem laufenden Minecraft-Server ist nach dem Austausch noch erforderlich.

Upstream applies GPL-3.0 to older code and AGPL-3.0 to additions from 2024 onward. See [LICENSE](LICENSE); modified and added patch classes are distributed under AGPL-3.0. Corresponding patch source is in this directory and in the delivered JAR. Upstream source remains available at the release link above.
