# Towny Reborn plugins — ExcellentCrates 7 update

This update restores the five supplied Towny plugins and the key display addon on Paper 26.2 / Java 25 with ExcellentCrates 7.1.0, nightcore 2.16.7 and QuickShop-Hikari 6.3.0.3. See [INSTALLATION.md](INSTALLATION.md) for the server update, configuration migration and data preservation steps.

| Project | Version | Change |
| --- | --- | --- |
| TownyCosmeticsBridge | 1.2.2 | Native EC7 reward preview and virtual key service |
| TownyRebornCrateModels | 1.0.3 | EC7 registry, interaction context and pre-start event |
| TownyRebornHunt | 1.0.4 | Native virtual keys; legacy import and one-time lost Halloween balance recovery |
| ExcellentCrates key patch | 7.1.0-townykeys.2 | Persist updated balances; keep current keys across rapid rejoins; report the patch version on Paper |
| TownyRebornLoot | 1.0.1 | EC7 opening guards; historical test reset retired |
| TownyRebornBarrelShops | 1.0.1 | QuickShop 6.3.0.3 and BiggerBarrels runtime adapter |
| ExcellentCratesAddon | 2.2-ec7 | EC7 key counters; legacy hologram placeholders and an unformatted virtual-key total |
| TownyRebornCrateOffsets | 1.0.0 | Per-crate X/Y/Z displacement of the native opening animation |
| TownyRebornPetShops | 1.2.1 | English 75k pet shops, player-facing models, extended visibility and native interaction sounds |

The four recovered projects are under `integrations/`. Source was recovered from the exact supplied JARs using CFR 0.152; only API compatibility and compilation corrections were applied. Existing settings, model placements, hunt player files and loot profiles are retained. Hunt continues to respect the configured `max-finds` (the supplied JAR defaults to 44).

The [QuickShop patch](quickshop-patch/README.md) is required alongside BarrelShops. It routes stock operations to the expanded inventory and preserves normal inventories and physical shop locations.

## Build and test

Install JDK 25 and Python 3, then run:

```sh
python3 tools/build.py
python3 -m pip install PyYAML==6.0.2
python3 -m unittest discover -s tests -p 'test_*.py'
```

The build downloads pinned, SHA-256 verified dependencies using curl, compiles the Towny plugins, the key display addon and the wrapper patch, and runs Java regression tests covering the real EC7, QuickShop and ModelEngine interfaces, native animation and batch classes, permanent pet purchases, automatic discovery and shop-file migration. Output JARs and their checksums are in `dist/`. GitHub Actions builds and publishes these as a downloadable workflow artifact. Maven project files are also provided; the QuickShop projects require the 6.3.0.3 release installed locally as `com.ghostchu.quickshop:quickshop-runtime:6.3.0.3`.

`integrations/TownyRebornKeyDisplay` replaces Cyvertray_'s MIT-licensed ExcellentCratesAddon 2.1. Its old reflection calls silently returned zero after the EC7 API change. The replacement uses the public EC7 service, retains `%ec_keyz_<key-id>%` and `%ec_physicalkeyz_<key-id>%`, and survives PlaceholderAPI reloads. `%ec_totalkeys_daily,vote,patron,cosmetic%` sums the four virtual balances directly as a plain integer, including amounts over 999; it does not require the Math expansion. Counters read each viewer's current balance and never grant or consume keys. Replace the old addon JAR rather than installing both versions.

`integrations/TownyRebornCrateOffsets` shifts the native CSGO and Simple Roll opening displays independently for each crate. Install its JAR alongside the existing plugins, then use `/trcrateoffset set vote 0 0.5 0` to move the Vote animation half a block up. `/trcrateoffset reset vote` restores its original position. File settings are in `plugins/TownyRebornCrateOffsets/config.yml`; `/trcrateoffset reload` applies edits to subsequent openings. Coordinates accept decimals from -32 to 32. The admin permission is `townyreborn.crateoffsets.admin` (op by default).

ExcellentCrates 7.1.0 does not expose a public animation-position setter or its animation registry. The offset addon checks three native display fields and the animation-stage registry field at startup, then attaches profile delegates through the registry's public API before opening. It moves the display location after native block centering, preserving fractional and negative offsets. EC continues to own the original animation instances, reward selection, key charges, concurrent-opening guards, timing and entity cleanup. It reattaches after EC configuration reload and restores original profiles when disabled. Other animation types are left alone. Future EC releases that change those internal fields require a compatibility update.

`tools/convert_ec7.py SOURCE DESTINATION` generates EC7 objects from the legacy configuration folder without reading or writing databases. EC6 rolls a rarity first and then a reward inside that rarity; EC7 uses a flat reward pool. The converter normalizes weights to preserve the original probabilities, and retains raw weights under `towny_migration.original_weight`. It preserves SNBT item payloads, enchantments and one-per-player limits. Legacy `crates key give PLAYER KEY AMOUNT -sf` payouts become `cratekey give PLAYER KEY AMOUNT`, keeping each recipient, key and amount. Other commands are preserved. Hologram enablement and virtual-key settings are carried over explicitly. The supplied Halloween rewards all use the same rarity, so normalization also preserves their probabilities as rewards become exhausted. Unsupported mixed-rarity player limits fail before any output is written.

Private server configurations and player databases are deliberately excluded from this repository. The separate configuration overlay accompanies the server update package.

`python3 tools/configure_ec7.py EXCELLENTCRATES DELUXEMENUS` updates copies of the two configuration folders for EC7.1. It enables native batches up to 20 for every crate, disables opening cooldowns that otherwise cap a batch at one, and provides all twenty amount buttons. Existing interaction cooldowns, reward quotas, weights, payouts, item payloads, locations and database settings are retained. Native EC skips the amount menu when only one opening is available. The four standard crates, their key/reward labels and a separate preview profile use Towny Reborn's pink/lime palette. Halloween keeps its own key/reward labels, hologram settings and original preview profile, and stays absent from the public DeluxeMenus crate menu. Menu preview/open commands use `crate preview ID PLAYER` and `crate open ID PLAYER`; balances use the existing key display addon. The tool also updates legacy key payouts elsewhere in DeluxeMenus while retaining their recipients and amounts. Apply the generated files with the server stopped; this tool never reads or writes player databases.

## Crate key persistence

[Crate key fix and installation](excellentcrates-patch/README.md): EC7.1.0's key repository attempts a plain INSERT when saving an existing owner/key record. The patch enables native SQL updates on conflict, and merges login data without replacing current cached balances with a stale periodic save. All key types keep their remaining balances after rejoin and restart. Hunt 1.0.4 can recover confirmed unspent Halloween keys once from its payment records and the current reward usage table, with a backup, transaction, audit and durable marker. Patch revision 2 updates both plugin descriptors so Paper reports the patch version and Hunt runs the startup recovery for offline players. Install both JARs from `TownyReborn-Crate-Key-Fix.zip` while the server is stopped; keep the live player data folders. The build tests the original failure, the fixed native persistence/cost services against SQLite, and startup recovery using the Paper descriptor from the final JAR. Release tests verify both descriptors and the delivered ZIP.

## MCPets shops

[Pet shop installation and commands](integrations/TownyRebornPetShops/README.md): place the animated model with `/trpetshop place <MCPets-ID>`. Right-click plays an interaction animation; left-click opens the English purchase menu. Defaults are 75,000 $ in-game and 10.99 € in Tebex. Customize them with `/trpetshop price <ID> 75000 10.99` and set its package link with `/trpetshop store <ID> https://shop.example.com/package/123`. Models use a configurable 64-block render distance and face nearby players. In-game purchases use Vault, ask for confirmation and save a single permanent MCPets permission through LuckPerms. Console-only `trpetshop grant {id} <MCPets-ID> {transaction}` supports offline Tebex delivery. UltraCosmetics and crate rewards are unchanged. Generic builds begin with an empty catalog; add or place the MCPets you wish to sell.

New definitions are discovered asynchronously every 15 seconds and immediately on `add`, `place` or `bind`. Each offer gets `shops/<pet-id>.yml` with prices, link, model, interaction animation and exact `tebex.command`. Version 1.2 upgrades legacy 50k offers once to 75k while keeping other custom prices, links and purchase/NPC records. Replace only the addon JAR; example configs are outside `plugins` to preserve live data. Packages must still be created in Tebex; generated setup files do not publish products.

Version 1.2.1 synchronizes right-click animations with their native MythicMobs sound sequences. Automatic sound lookup follows the MCPets `MythicMob` ID and its `~onInteract` skills, including the selected Clown animation's separate sound profile. `interaction-sounds` defaults to `auto`; custom animation maps or `off` can be set in each pet file. Delayed sounds stop when the pedestal is removed/reloaded or a newer interaction replaces the old one. Hologram height remains adjustable per pet with `/trpetshop height <ID> 2.8`.

`tools/configure_pet_interactions.py` prepares a two-file overlay from current MCPets/MythicMobs config copies: right-click plays the existing animation; sneak-right-click opens the native MCPets interaction menu. It retains the other settings and animation mechanics and validates both inputs before writing. The pet shop README includes the command and the settings for future pet skills.

## Cosmetics bridge

TownyReborn bridge between ExcellentCrates and UltraCosmetics. The console command
`townycosmeticsreward <player>` chooses a still-locked cosmetic for that
player, unlocks it through the UltraCosmetics profile API, and updates the
ExcellentCrates reward display. A completed collection awards the configured
Patron key; an internal error refunds the cosmetic key.

## Runtime

- Paper 26.2 / Java 25
- UltraCosmetics
- ExcellentCrates 7.1.0 / nightcore 2.16.7
- Plugin version represented here: `1.2.2`

## Source recovery note

The original source project was no longer present. This Java source was
recovered from the deployed `TownyCosmeticsBridge.jar` with Vineflower 1.12.0.
Control flow and integrations were preserved, then a reproducible Maven build
against Paper 26.2 was added for the TownyReborn 1.2.1 release.

Deployed JAR SHA-256:

```text
b767601bda2d91e1eeab0a904695e3c9890d2d83f82564b378ed0d1b97174c5a
```

The authoritative plugin metadata is in
`src/main/resources/plugin.yml`.
