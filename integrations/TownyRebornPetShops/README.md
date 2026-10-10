# Towny Reborn pedestal pet shops — 1.2.0

Place the animated **3D pet model** on a pedestal with an English hologram. **Right-click** plays its interaction animation; **left-click** opens the choice between a permanent unlock for **75,000 $** and a **10.99 € Tebex package**. The in-game option asks for a second confirmation before payment. Models use a configurable **64-block render distance** and turn toward the nearest visible player within **8 blocks**.

Only native MCPets permissions are unlocked. UltraCosmetics and cosmetic crate rewards are unchanged. The shop plays ModelEngine animation clips without running MythicMobs combat skills. Prices, package links, models, icons, hologram heights and interaction animations are configurable per pet.

## Install or update

1. Use Paper 26.2 / Java 25, Citizens, LuckPerms and Vault. Models also require MCPets, MythicMobs, ModelEngine and the matching ItemsAdder resource pack. A Vault economy provider such as EssentialsX is needed for in-game payments. The model adapter is verified against ModelEngine R4.1.0.
2. Stop the server and replace the old pet shop JAR with `plugins/TownyRebornPetShops-1.2.0.jar` from this ZIP. Keep only one pet shop JAR. **Keep the existing `plugins/TownyRebornPetShops` data folder**, especially `shops`, `npcs.yml` and `transactions.yml`. The `examples` folder is for reference; do not copy it over live data.
3. Start normally and check `/trpetshop status` and `/trpetshop list`. Existing pedestal bindings are reused. Legacy 50,000-dollar offers and the old default price migrate once to 75,000. Other custom prices, links, store prices, models, heights and payment records are retained. The shipped German click instruction becomes English; custom hologram lines remain available for editing.
4. For a new pedestal, stand where its model should appear and run `/trpetshop place <MCPets-ID>`. The plugin creates a protected invisible Citizens carrier, adds the pet model and hologram, and prints the NPC ID, pet file path and Tebex console command.
5. Set the matching package URL with `/trpetshop store <MCPets-ID> https://shop.example.com/package/123456`. The default link goes to the store homepage until a package link is configured. `off` disables the link.

The addon creates no pedestal blocks and places no models at unknown locations. Citizens stores the position. Use `/npc select <NPC-ID>` and normal Citizens movement commands to move a model; the hologram follows. Both the NPC ID and UUID are checked so reused NPC numbers cannot acquire unrelated shop bindings.

## Commands and settings

```text
/trpetshop add <MCPets-ID>
/trpetshop place <MCPets-ID>
/trpetshop price <MCPets-ID> 75000 10.99
/trpetshop store <MCPets-ID> https://shop.example.com/package/123456
/trpetshop height <MCPets-ID> 2.8
/trpetshop model <MCPets-ID> <ModelEngine-model-ID>
/trpetshop animation <MCPets-ID> <auto|animation-name|off>
/trpetshop range 64
/trpetshop open <MCPets-ID>
/trpetshop reload
```

`place` can create several pedestals for the same pet. `/trpetshop bind <MCPets-ID> [NPC-ID]` links an existing Citizens NPC, or the currently selected one, and keeps its existing model. Remove previous sales commands on that NPC first. `/trpetshop remove <NPC-ID>` removes the binding and deletes addon-created NPCs; previously existing NPCs remain.

`config.yml` controls default prices and English hologram lines. `npc.render-distance` accepts 16–256 blocks and sets Citizens tracking and the individual ModelEngine model's radius. Shop models get per-model occlusion overrides so pedestal blocks cannot hide the invisible carrier. Client visibility is also bounded by loaded chunks and client settings/resource packs. `npc.look-at-player-range` defaults to 8 blocks; `npc.update-interval-ticks` defaults to 4. Set the look range to 0 for a fixed orientation. `interaction.cooldown-seconds` defaults to 3; longer clips cannot restart before they finish.

`auto` selects a native interaction clip. The supplied pets use Bone Dragon `flap`, Clown `honk`, Mummy `crush` and Witch Cat `cauldron`. Use `animation` to choose another loaded clip, such as the Clown's `honkshot`, or disable it with `off`. The same setting works for future pets.

Admin permission: `townyreborn.petshops.admin` (op by default). Player access: `townyreborn.petshops.use` (allowed by default). `/trpetshop help` lists the English commands. Reload closes purchase menus; a confirmation cannot charge an outdated price.

## Individual pet files and future pets

New MCPets definitions in `plugins/MCPets/Pets`, including subfolders, are discovered asynchronously every 15 seconds. `add`, `place` and `bind` read them immediately. Install and load a future pet's MCPets definition, MythicMobs mob, ModelEngine model and resource pack, then run `/trpetshop place <new-ID>`. No addon code change or restart is needed. Discovery alone does not create an offer or pedestal.

Each offer creates **`plugins/TownyRebornPetShops/shops/<Pet-ID>.yml`**. The package URL and exact Tebex grant command live in the same file:

```yaml
schema-version: 1
offer-version: 2
pet-id: future_pet
enabled: true
name: 'Future Pet'
permission: 'pet.future'
model: 'future_pet'
icon-item-model: 'custom:heads/future_pet'
ingame-price: 75000.0
store-price: 10.99
store-url: 'https://shop.example.com/package/123456'
hologram-height: 2.5
hitbox-width: 1.8
hitbox-height: 2.0
idle-animation: idle
interaction-animation: auto
tebex:
  command: 'trpetshop grant {id} future_pet {transaction}'
  execution: SERVER_CONSOLE
  unlock: PERMANENT
```

Edit prices, `store-url` or `interaction-animation` here and run `/trpetshop reload`. Positive prices must have at most two decimal places. The displayed store price does not change Tebex's actual price. Retain both version fields so later manual prices are not re-migrated.

The pet ID, native permission and Tebex block are generated from MCPets. Edited grant commands cannot execute arbitrary console commands. Wildcards and UltraCosmetics permissions are rejected. Icons are read from modern MCPets item components; purchases grant permanent access rather than an item. Invalid or duplicate definitions retain the last valid catalog. `discovery-interval-seconds` accepts 5–3600 seconds.

Legacy `pets.yml` offers migrate to individual files once; existing individual files take precedence. The old file remains as history. Edit offers afterward in `shops/<Pet-ID>.yml`.

## Tebex delivery

Create a one-time **10.99 €** package per pet in Tebex, limit repeat purchases as appropriate, and copy its URL into `store-url`. Copy **`tebex.command`** from its generated pet file into the package as a payment command for the **server console**, without a leading slash:

```text
trpetshop grant {id} <MCPets-ID> {transaction}
```

`{id}` is the Minecraft UUID; UUIDs without hyphens also work. Known player names are accepted for manual console tests. Delivery works offline, grants only that pet's permanent LuckPerms permission and never withdraws in-game money. Repeated delivery is idempotent. Configure the actual package and a test payment in the Tebex dashboard; the addon does not publish products.

HTTPS links may contain `{player}` and `{uuid}`; names are URL-encoded. The Tebex option sends a clickable chat link, which the Minecraft client opens in a browser after the player clicks it.

## Purchases and recovery

Effective MCPets permissions and completed addon transactions both count as ownership, including permissions from groups or previous unlocks. Simultaneous in-game/store operations for one player and pet are locked until completion; repeated clicks cannot charge twice.

If a future pack changes the native permission for an existing pet ID, completed addon purchases receive the new permission without another payment. Original prices, payment sources and Tebex receipts remain intact. This is checked on discovery, startup, reload and login.

A confirmed charge awaiting permission storage stays `CHARGED` and completes after restart/login without another withdrawal. An uncertain withdrawal stays `PREPARED`, blocking another payment until bank history is checked. `/trpetshop pending` lists these purchases. After verification, an admin can run:

```text
/trpetshop resolve <UUID> <MCPets-ID> paid
/trpetshop resolve <UUID> <MCPets-ID> unpaid
```

`paid` completes delivery without another withdrawal. `unpaid` is only valid for a `PREPARED` purchase verified as not charged. Back up transactions together with economy data.

## Owned pets

This update preserves the earlier summoned-pet setup: right-click runs that pet's native interaction; **sneak + right-click** opens its MCPets menu. It does not overwrite MCPets/MythicMobs settings. That setup uses `RightClickToOpenMenu: true`, `LeftClickToOpenMenu: false`, `SneakMode: true`, and `TriggerConditions: [crouching false]` on interaction skills so opening the menu does not also trigger an animation.

If needed, prepare that separate overlay from current configuration copies:

```sh
python3 tools/configure_pet_interactions.py \
  --mcpets-config /backup/plugins/MCPets/config.yml \
  --mythic-skills /backup/plugins/MythicMobs/skills/townyreborn_halloween_pets.yml \
  --output /tmp/pet-interactions
```

The helper preserves comments, existing mechanics and unrelated settings. Use the same crouching condition for future pet skills. This 1.2.0 ZIP contains the shop addon and examples; it does not replace live MCPets/MythicMobs files.

Tests cover native ModelEngine events, render/culling setters, body/head rotation, one-shot clips, one-time 75k migration, custom links, generated commands, discovery, permanent purchases, duplicate charges and restart recovery. On the live server, also check model visibility at a distance, both clicks, a non-op player's 75k purchase, ownership after restart and Tebex test delivery.

API references: [ModelEngine animations](https://wiki.mythiccraft.io/modelengine/API/Basic/Play-Stop-Animation), [MCPets click settings](https://mcpets.gitbook.io/mcpets/tutorials/config-templates/config-file), [MCPets permissions](https://mcpets.gitbook.io/mcpets/tutorials/config-templates/pet-config), [Tebex command variables](https://docs.tebex.io/creators/command-management/an-introduction-to-commands).
