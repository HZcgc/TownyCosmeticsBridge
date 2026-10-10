# Towny Reborn pedestal pet shops — 1.2.1

Place the animated **3D pet model** on a pedestal with an English hologram. **Right-click** plays its interaction animation and matching native sounds; **left-click** opens the choice between a permanent unlock for **75,000 $** and a **10.99 € Tebex package**. The in-game option asks for a second confirmation before payment. Models use a configurable **64-block render distance** and turn toward the nearest visible player within **8 blocks**.

Only native MCPets permissions are unlocked. UltraCosmetics and cosmetic crate rewards are unchanged. The shop plays ModelEngine animation clips without running MythicMobs combat skills. Prices, package links, models, icons, hologram heights and interaction animations are configurable per pet.

## Install or update

1. Use Paper 26.2 / Java 25, Citizens, LuckPerms and Vault. Models also require MCPets, MythicMobs, ModelEngine and the matching ItemsAdder resource pack. A Vault economy provider such as EssentialsX is needed for in-game payments. The model adapter is verified against ModelEngine R4.1.0.
2. Stop the server and replace the old pet shop JAR with `plugins/TownyRebornPetShops-1.2.1.jar` from this ZIP. Keep only one pet shop JAR. **Keep the existing `plugins/TownyRebornPetShops` data folder**, especially `shops`, `npcs.yml` and `transactions.yml`. The `examples` folder is for reference; do not copy it over live data. Native sound lookup reads the already installed MythicMobs mob/skill files; no extra sound assets are needed for the supplied pets.
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

Hologram height is measured in blocks above the model's base. `/trpetshop height nm_widge_the_witch_cat 2.8` immediately moves the Witch Cat's hologram to that height. The setting applies to all shop pedestals displaying that pet and is also stored as `hologram-height` in its pet file.

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
interaction-sounds: auto
tebex:
  command: 'trpetshop grant {id} future_pet {transaction}'
  execution: SERVER_CONSOLE
  unlock: PERMANENT
```

Edit prices, `store-url` or `interaction-animation` here and run `/trpetshop reload`. Positive prices must have at most two decimal places. The displayed store price does not change Tebex's actual price. Retain both version fields so later manual prices are not re-migrated.

`interaction-sounds: auto` reads the native mob identified by MCPets' `MythicMob`, follows its `~onInteract` skill references and matches their ModelEngine `state` to the animation that actually plays. Literal sound names, cumulative `delay` ticks, pitch, volume and sound category are preserved. Mob/skill YAML subfolders and both capitalized/lowercase `Mobs`/`Skills` directory names are supported. Spawn/timer skills do not become interaction sounds. Native file changes are picked up by discovery or reload. Existing shop files get the `auto` setting without resetting prices, links or hologram heights.

Set `interaction-sounds: off` to mute one pet's interactions. For future packs using dynamic expressions or other complex skill mechanics, configure an explicit profile in the same pet file:

```yaml
interaction-sounds:
  honk:
    - sound: minecraft:block.note_block.trumpet_exposed
      delay-ticks: 8
      volume: 0.7
      pitch: 1.85
      category: MASTER
```

The profile key is the played animation name. Custom resource-pack sound keys are supported. `delay-ticks` is measured from the animation start (20 ticks per second at normal tick rate). Explicit sound profiles survive reloads; they are not replaced by automatic lookup. Only sound cues are copied: the addon never casts native combat/console skills. Sounds play at the pedestal for nearby players, and queued cues are suppressed after removal, reload or replacement by a newer animation. Click cooldowns also cover the sound sequence.

The pet ID, native permission and Tebex block are generated from MCPets. Edited grant commands cannot execute arbitrary console commands. Wildcards and UltraCosmetics permissions are rejected. Icons are read from modern MCPets item components; purchases grant permanent access rather than an item. Invalid or duplicate definitions retain the last valid catalog. `discovery-interval-seconds` accepts 5–3600 seconds.

Legacy `pets.yml` offers migrate to individual files once; existing individual files take precedence. The old file remains as history. Edit offers afterward in `shops/<Pet-ID>.yml`.

## Tebex delivery

Create a one-time **10.99 €** package per pet in Tebex, limit repeat purchases as appropriate, and copy its URL into `store-url`. Copy **`tebex.command`** from its generated pet file into the package as a payment command for the **server console**, without a leading slash:

```text
trpetshop grant {id} <MCPets-ID> {transaction}
```

`{id}` is the Minecraft UUID; UUIDs without hyphens also work. Known player names are accepted for manual console tests. Delivery works offline, grants only that pet's permanent LuckPerms permission and never withdraws in-game money. Repeated delivery is idempotent. Configure the actual package and a test payment in the Tebex dashboard; the addon does not publish products.

HTTPS links may contain `{player}` and `{uuid}`; names are URL-encoded. The Tebex option sends a clickable chat link, which the Minecraft client opens in a browser after the player clicks it.

## Purchase announcements

After the first permanent unlock has been delivered and saved, all online players receive a pink/lime Towny Reborn chat announcement: **`TOWNYREBORN » Player has purchased Pet!`**. This covers both in-game purchases and offline Tebex deliveries. Simply opening the store link, a rejected payment, a pending unlock, repeated Tebex commands, joins and permission repairs do not produce another announcement.

These settings are added automatically to an existing `config.yml`, preserving prices, pet files and custom texts:

```yaml
announcements:
  enabled: true
  message: '&a{player} &7has purchased &d{pet}&7!'
```

The normal `prefix` is prepended. Placeholders are `{player}`, `{pet}`, `{pet_id}` and `{source}` (`in-game` or `Tebex`). Offline delivery uses the known player name; if no name is available, it uses `A player`. A durable announcement claim is saved before broadcasting to prevent duplicate announcements after recovery. A process crash between that save and the broadcast may omit that message; it never repeats it or takes another payment. Completed purchases from older versions are not announced retroactively.

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

Right-click still runs a summoned pet's native interaction. In **1.3.0**, **shift + right-click your own pet** opens an English naming dialog with **Save name**, **MCPets menu** and **Cancel**. The native MCPets menu retains its mount, inventory, skins and other actions. Other players' pets and shop pedestal models cannot be renamed. `townyreborn.petshops.rename` is allowed for players by default; explicitly denying it, or setting `naming.enabled: false`, retains the native interaction flow.

In **1.3.1**, clicking **Rename** in the native MCPets interaction menu also opens this dialog, even when you are no longer crouching. MCPets 4.1.5's chat naming path leaves literal `&` characters from `&#rrggbb` output and strips colors for players without `mcpets.color`. The addon consumes the native chat prompt before opening the dialog and saves the correctly converted name through MCPets. No extra native color permission is needed. Other native menu actions remain available. Names already reduced to `&H&Z&c&g&c` have lost their original color values; paste the original RGBirdflop output again to repair them.

Paste the name and color codes from [RGBirdflop](https://www.birdflop.com/resources/rgb/) into the name field, without a `/nick` command. Supported output formats include:

```text
&aBuddy
&#ff55ffH&#aaff55i
&x&f&f&5&5&f&fH&x&a&a&f&f&5&5i
<gradient:#ff55ff:#aaff55>Buddy</gradient>
```

Expanded `§x` colors, `<#rrggbb>` colors, bold/italic/underline and rainbow tags also work. The dialog accepts up to 4096 input characters so long gradients fit; only visible characters count toward `naming.max-length` (default 16). MCPets' own configured limit and blacklist are also enforced. Names use the native MCPets player database and ModelEngine name tag, so colors persist after respawn/rejoin/restart. The adapter supports MCPets 4.1.5's legacy name setter and misspelled `MaxNameLenght` getter, as well as newer versions. Ordinary players do not need MCPets' separate native color permission for this addon dialog.

No live MCPets/MythicMobs configuration is overwritten. Keep the earlier summoned-pet setup: `RightClickToOpenMenu: true`, `LeftClickToOpenMenu: false`, `SneakMode: true`, and `TriggerConditions: [crouching false]` on interaction skills so opening the menu does not also trigger an animation.

If needed, prepare that separate overlay from current configuration copies:

```sh
python3 tools/configure_pet_interactions.py \
  --mcpets-config /backup/plugins/MCPets/config.yml \
  --mythic-skills /backup/plugins/MythicMobs/skills/townyreborn_halloween_pets.yml \
  --output /tmp/pet-interactions
```

The helper preserves comments, existing mechanics and unrelated settings. Use the same crouching condition for future pet skills. This 1.3.1 ZIP contains the shop addon and examples; it does not replace live MCPets/MythicMobs files.

Tests cover native ModelEngine events, render/culling setters, body/head rotation, one-shot clips, sound names/timing/category, nested sound helpers, duplicate interactions, queued sound cleanup, one-time 75k migration, custom links, generated commands, discovery, permanent purchases, announcement claims/retries and restart recovery. Naming tests cover exact RGB colors, MiniMessage gradients, long formatted names, native limits/blacklists, owner checks, duplicate events, stale dialogs, native menu access and the native Rename button for a player without `mcpets.color`. On the live server, also check the four models' sounds/visibility, both clicks, a non-op player's 75k purchase and announcement, ownership after restart, offline Tebex test delivery and a colored pet name through both rename entry points after respawn/rejoin.

API references: [ModelEngine animations](https://wiki.mythiccraft.io/modelengine/API/Basic/Play-Stop-Animation), [MythicMobs sounds](https://wiki.mythiccraft.io/mythicmobs/skills/mechanics/Sound), [MCPets click settings](https://mcpets.gitbook.io/mcpets/tutorials/config-templates/config-file), [MCPets permissions](https://mcpets.gitbook.io/mcpets/tutorials/config-templates/pet-config), [Tebex command variables](https://docs.tebex.io/creators/command-management/an-introduction-to-commands).
