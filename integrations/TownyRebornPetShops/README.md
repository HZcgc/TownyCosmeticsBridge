# Towny Reborn Petshops auf Podesten

Das auf einem Podest ausgestellte **3D-Petmodell** mit Hologramm öffnet beim Anklicken die Auswahl zwischen einem dauerhaften Kauf für **50.000 $** und dem **10,99-€-Store-Paket**. Der Ingame-Kauf benötigt eine zweite Bestätigung. Preis, Store-Link, ModelEngine-Modell, Icon und Hologrammhöhe sind pro Pet einstellbar; weitere Pets können über ihre vorhandene MCPets-ID hinzugefügt werden.

Neue MCPets-Definitionen in `plugins/MCPets/Pets` werden standardmäßig alle **15 Sekunden** im Hintergrund erkannt. `add`, `place` und `bind` lesen neue Definitionen auch sofort ein. Beim Anlegen eines Angebots entsteht automatisch `plugins/TownyRebornPetShops/shops/<Pet-ID>.yml`, einschließlich des fertigen Tebex-Konsolenbefehls. Für neue Pets ist keine Codeänderung und kein Petshop-Neustart nötig.

Nur MCPets-Berechtigungen werden freigeschaltet. UltraCosmetics und der Cosmetic-Crate werden nicht verändert. Shop-NPCs zeigen das Pet-Modell mit seiner Idle-Animation. MythicMobs-Kampffähigkeiten werden am Shop-NPC nicht ausgeführt. Die Modell- und Resourcepack-Dateien bleiben bestehen.

## Eigene Pets anklicken

Bei einem beschworenen eigenen Pet spielt **Rechtsklick** die vorhandene Animation. **Shift + Rechtsklick** öffnet das native MCPets-Interaktionsmenü. Dafür verwendet MCPets diese Einstellungen:

```yaml
RightClickToOpenMenu: true
LeftClickToOpenMenu: false
SneakMode: true
```

Die fünf Interaktions-Skills der vier gelieferten Pets bekommen zusätzlich `TriggerConditions: [crouching false]`. Dadurch startet beim Öffnen des Menüs keine Animation. Beim Bone Dragon wird die fehlerhafte Bedingung `onground{} true cancel` durch `(onground && offgcd) true` ersetzt. Animationen, Sounds und ihre vorhandenen Cooldowns bleiben erhalten. Auf dem Verkaufspodest öffnet der Klick weiterhin die Kauf-Auswahl.

Das vorbereitete Serverpaket enthält nur die geänderten `plugins/MCPets/config.yml` und `plugins/MythicMobs/skills/townyreborn_halloween_pets.yml` zusätzlich zum Shop-Addon. Bei gestopptem Server übernehmen und neu starten. Wer zwischenzeitlich seine Einstellungen geändert hat, kann aus aktuellen Kopien einen neuen Overlay erzeugen:

```bash
python3 tools/configure_pet_interactions.py \
  --mcpets-config /backup/plugins/MCPets/config.yml \
  --mythic-skills /backup/plugins/MythicMobs/skills/townyreborn_halloween_pets.yml \
  --output /tmp/pet-interactions
```

Der Helfer erhält Kommentare und andere Einstellungen. Für zukünftige Pet-Animations-Skills dieselbe Trigger-Bedingung am Interaktions-Skill ergänzen. Primärquellen: [MCPets-Klick-Einstellungen](https://mcpets.gitbook.io/mcpets/tutorials/config-templates/config-file), [MythicMobs-TriggerConditions](https://wiki.mythiccraft.io/mythicmobs/Skills/Metaskills), [Crouching](https://wiki.mythiccraft.io/mythicmobs/skills/conditions/crouching).

## Installation und NPCs

1. Paper 26.2 / Java 25, Citizens, LuckPerms und Vault verwenden. Für funktionierende Pets außerdem MCPets, MythicMobs, ModelEngine und das passende ItemsAdder-Resourcepack. Ein Vault-Economy-Anbieter wie EssentialsX wird für Ingame-Käufe benötigt.
2. `TownyRebornPetShops-1.1.0.jar` und bei der Erstinstallation den mitgelieferten Datenordner in `plugins` kopieren. Beim Update den Server stoppen, die alte Petshop-JAR ersetzen und den vorhandenen Datenordner behalten. Es darf nur eine Petshop-JAR installiert sein. Bestehende `config.yml`, `pets.yml`, `shops`, `npcs.yml` und `transactions.yml` bleiben erhalten.
3. Normal starten und `/trpetshop status` sowie `/trpetshop list` prüfen. Ohne aktives MCPets-Plugin oder Economy-Anbieter werden keine Ingame-Käufe ausgeführt.
4. Auf das vorbereitete Podest an die gewünschte Position stellen und `/trpetshop place <MCPets-ID>` ausführen. Das Addon übernimmt die MCPets-Definition, erstellt einen geschützten Citizens-NPC mit dem Pet-Modell und ergänzt das Hologramm. Im Chat werden die Pet-Datei und der konkrete Tebex-Befehl ausgegeben.
5. Einen HTTPS-Link zum passenden Store-Paket mit `/trpetshop store <MCPets-ID> <Link>` setzen. Bis dahin führt der voreingestellte Link zur Store-Startseite. `off` deaktiviert den Store-Button.

Es werden keine NPCs automatisch an unbekannten Positionen aufgestellt. Beispiel für die gelieferten Pets:

```text
/trpetshop place nm_bryborn_the_bone_dragon
```

Die aktuelle Spielerposition auf dem Podest wird verwendet. Das sichtbare Objekt ist das Pet selbst; der unsichtbare Citizens-Träger wird bereits vor dem Spawn ausgeblendet und ist unbeweglich. Das Addon baut oder ersetzt keine Podest-Blöcke. Die NPC-ID wird im Chat ausgegeben. Citizens speichert seine Position; nach `/npc select <ID>` kann der NPC mit den üblichen Citizens-Befehlen bewegt werden. Das Hologramm folgt ihm. Die Bindung speichert auch die NPC-UUID, damit wiederverwendete NPC-Nummern nicht versehentlich einen anderen NPC zum Shop machen.

## Preise, Links und neue Pets

```text
/trpetshop add <MCPets-ID>
/trpetshop price <MCPets-ID> 50000 10.99
/trpetshop store <MCPets-ID> https://shop.example.com/package/123456
/trpetshop height <MCPets-ID> 2.8
/trpetshop model <MCPets-ID> <ModelEngine-Modell-ID>
/trpetshop place <MCPets-ID>
```

`place` kann weitere NPCs für dasselbe Pet erstellen. Alternativ `/trpetshop bind <MCPets-ID> <NPC-ID>` für einen vorhandenen Citizens-NPC verwenden. Ohne NPC-ID wird der ausgewählte NPC verwendet. Sein bestehendes Modell bleibt erhalten; so lassen sich auch bereits mit ItemsAdder gestaltete Verkäufer einbinden. Bereits vorhandene Verkaufsbefehle des NPCs vorher entfernen.

`/trpetshop remove <NPC-ID>` entfernt die Shop-Verknüpfung. Vom Addon erstellte NPCs werden entfernt; zuvor vorhandene, lediglich gebundene NPCs bleiben stehen.

Grundeinstellungen stehen in `config.yml`, jedes Angebot in **`shops/<Pet-ID>.yml`**. Version 1.1 übernimmt bestehende Angebote aus `pets.yml` einmalig samt Preisen, Links, Modellen und Hologrammhöhen. Bereits vorhandene Einzeldateien haben Vorrang. Die alte Datei bleibt als Migrationshistorie bestehen; Änderungen an Angeboten danach in den Einzeldateien durchführen. Der Standardpreis gilt pro Pet und einmalig. Preise müssen positiv sein und höchstens zwei Nachkommastellen haben. Der im Addon angezeigte Store-Preis ändert **nicht** den echten Paketpreis im Store-Dashboard. Beispiel für die automatisch erzeugte Datei `shops/future_pet.yml`:

```yaml
schema-version: 1
pet-id: future_pet
enabled: true
name: 'Future Pet'
permission: 'pet.future'
model: 'future_pet'
icon-item-model: 'custom:heads/future_pet'
ingame-price: 50000.0
store-price: 10.99
store-url: 'https://shop.example.com/package/123456'
hologram-height: 2.5
hitbox-width: 1.8
hitbox-height: 2.0
idle-animation: idle
tebex:
  command: 'trpetshop grant {id} future_pet {transaction}'
  execution: SERVER_CONSOLE
  unlock: PERMANENT
```

Das Pet muss in `plugins/MCPets/Pets` existieren. Seine Berechtigung wird dort gelesen; Wildcards und UltraCosmetics-Berechtigungen werden abgelehnt. `permission`, `pet-id`, `schema-version` und der `tebex`-Block werden vom Addon erzeugt. Der Store-Befehl führt keine frei konfigurierbaren Befehle aus, sondern schaltet genau das native MCPets-Permission-Node dauerhaft frei. Bei einer abweichenden Modell-ID `model` entsprechend setzen. Das Icon wird aus modernen MCPets-Itemdaten übernommen; Käufer erhalten keine Pet-Items.

Für ein zukünftiges Pet dessen MCPets-Definition, MythicMobs-Mob, ModelEngine-Modell und Resourcepack installieren und in den jeweiligen Plugins laden. Danach reicht `/trpetshop place <neue-ID>`; ein vorheriges `add` oder Petshop-Reload ist nicht nötig. Der Petshop lädt keine Modelle oder neue Pets in fremden Plugins. Die Erkennung berücksichtigt auch Unterordner. Das Intervall steht in `discovery-interval-seconds` (5 bis 3600 Sekunden). Doppelte IDs oder ungültige Dateien ersetzen den letzten gültigen Katalog nicht. Neue Definitionen allein erstellen keine Angebote oder Podeste; die Platzierung bestimmt der Admin.

Nach manuellen Änderungen `/trpetshop reload` verwenden. Aktive Kaufmenüs werden geschlossen. Alte Bestätigungen gelten bei geändertem Preis nicht mehr. `/trpetshop open <MCPets-ID>` öffnet das Kaufmenü auch per Befehl. Admin-Berechtigung: `townyreborn.petshops.admin` (op); Spielerzugriff: `townyreborn.petshops.use` (standardmäßig erlaubt).

## Store-Anbindung

Pro Pet im Store ein einmaliges Paket für **10,99 €** anlegen, höchstens einen Kauf pro Spieler erlauben und den Paket-Link im Addon eintragen. Als bei bestätigter Zahlung ausgeführten Konsolenbefehl ohne führenden Schrägstrich verwenden:

In der automatisch angelegten `shops/<Pet-ID>.yml` steht der bereits auf dieses Pet zugeschnittene Befehl unter **`tebex.command`**. Diesen unverändert als Zahlungsbefehl für die Serverkonsole in dessen Tebex-Paket kopieren. Beispiel:

```text
trpetshop grant {id} <MCPets-ID> {transaction}
```

Bei Tebex ist `{id}` die Minecraft-UUID. Auch UUIDs ohne Bindestriche werden akzeptiert. Bekannte Spielernamen funktionieren für manuelle Tests; im Store die UUID verwenden. Der Befehl ist nur für Serverkonsole beziehungsweise RCON erlaubt. Er funktioniert offline, speichert die einzelne Pet-Berechtigung dauerhaft und belastet kein Ingame-Konto. Wiederholte Store-Befehle sind idempotent.

Eine manuelle Testzahlung im Store verwenden. Mit diesem Addon wurden keine Store-Pakete veröffentlicht: Paketpreis und endgültiger Paket-Link müssen im Store-Dashboard gesetzt werden. Ein HTTPS-Link darf `{player}` und `{uuid}` enthalten; Spielernamen werden URL-kodiert. Der Store-Button sendet einen klickbaren Link im Chat, den der Minecraft-Client nach dem Klick im Browser öffnet.

Primärquellen: [MCPets-Berechtigungen](https://mcpets.gitbook.io/mcpets/tutorials/config-templates/pet-config), [Tebex-Befehlsvariablen](https://docs.tebex.io/creators/command-management/an-introduction-to-commands), [ItemsAdder-Citizens-Modelle](https://wiki.itemsadder.com/adding-content/entities/citizens/).

## Kaufprüfung und Wiederherstellung

Besitz wird über die effektive MCPets-Berechtigung und abgeschlossene Addon-Transaktionen geprüft. Vorhandene Freischaltungen durch Gruppen oder andere Quellen werden anerkannt. Gleichzeitige Ingame- und Store-Vorgänge für denselben Spieler und dasselbe Pet sind gesperrt, bis der Vorgang abgeschlossen ist. Vault bestätigt den Abzug vor dem dauerhaften Speichern der LuckPerms-Berechtigung.

Ändert ein späteres Pet-Pack die native Berechtigung für dieselbe Pet-ID, erhalten Käufer mit einer abgeschlossenen Addon-Transaktion die neue Berechtigung ohne weitere Zahlung. Ursprünglicher Preis, Zahlungsquelle und Store-Transaktion bleiben erhalten. Dies wird beim Erkennen der Änderung, Start, Reload oder Login geprüft.

Ein bestätigter Abzug mit ausstehender Berechtigung bleibt als `CHARGED` gespeichert und wird beim Start oder nächsten Login ohne weitere Abbuchung fertiggestellt. Bei einem Absturz genau während der Abbuchung oder einer unklaren Economy-Antwort bleibt `PREPARED` gesperrt. Es gibt dann keine zweite automatische Abbuchung oder pauschale Rückzahlung.

`/trpetshop pending` zeigt offene Vorgänge. Nach Prüfung des Economy-Bankverlaufs kann ein Admin gezielt entscheiden:

```text
/trpetshop resolve <UUID> <MCPets-ID> paid
/trpetshop resolve <UUID> <MCPets-ID> unpaid
```

`paid` liefert das Pet ohne weiteren Abzug. `unpaid` ist nur für ungeklärte `PREPARED`-Vorgänge erlaubt und gibt einen neuen Kauf frei, nachdem bestätigt wurde, dass kein Geld abgezogen wurde. Transaktionsdateien und Economy-Daten beim Backup gemeinsam sichern.

Mit einem Spieler ohne op und ohne Pet-Berechtigung prüfen: Auswahl am NPC, Abbrechen ohne Abzug, zu wenig Guthaben, Kauf für 50.000 $, Pet im MCPets-Menü, erneuter Klick ohne zweite Abbuchung, Besitz nach Neustart und eine manuelle Store-Testzahlung. Operatoren besitzen durch ihre Berechtigungen häufig bereits alle Pets.

Die Tests prüfen Dateipersistenz, Doppelkäufe, fehlendes Guthaben, gesperrte Käufe, Save-Fehler, Neustart-Wiederherstellung, offline Store-Freischaltungen, automatische Erkennung neuer Pet-Definitionen, sofortiges Hinzufügen, sichere Migration, generierte Tebex-Befehle, Item-Modelle und den Erhalt individueller Preise/Links. Die endgültige 3D-Darstellung hängt von den auf dem Server geladenen Modellen und dem Resourcepack des Clients ab.
