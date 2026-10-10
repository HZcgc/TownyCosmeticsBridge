# Towny Reborn plugins — ExcellentCrates 7 update

This update restores the five supplied plugins on Paper 26.2 / Java 25 with ExcellentCrates 7.1.0, nightcore 2.16.7 and QuickShop-Hikari 6.3.0.3. See [INSTALLATION.md](INSTALLATION.md) for the server update, configuration migration and data preservation steps.

| Project | Version | Change |
| --- | --- | --- |
| TownyCosmeticsBridge | 1.2.2 | Native EC7 reward preview and virtual key service |
| TownyRebornCrateModels | 1.0.3 | EC7 registry, interaction context and pre-start event |
| TownyRebornHunt | 1.0.2 | Native virtual keys; transactional legacy SQLite migration |
| TownyRebornLoot | 1.0.1 | EC7 opening guards; historical test reset retired |
| TownyRebornBarrelShops | 1.0.1 | QuickShop 6.3.0.3 and BiggerBarrels runtime adapter |

The four recovered projects are under `integrations/`. Source was recovered from the exact supplied JARs using CFR 0.152; only API compatibility and compilation corrections were applied. Existing settings, model placements, hunt player files and loot profiles are retained. Hunt continues to respect the configured `max-finds` (the supplied JAR defaults to 44).

The [QuickShop patch](quickshop-patch/README.md) is required alongside BarrelShops. It routes stock operations to the expanded inventory and preserves normal inventories and physical shop locations.

## Build and test

Install JDK 25 and Python 3, then run:

```sh
python3 tools/build.py
python3 -m pip install PyYAML==6.0.2
python3 -m unittest discover -s tests -p 'test_*.py'
```

The build downloads pinned, SHA-256 verified dependencies using curl, compiles the five plugins and the wrapper patch, and runs ten Java regression tests against the real EC7 and QuickShop interfaces. Output JARs and their checksums are in `dist/`. GitHub Actions builds and publishes these as a downloadable workflow artifact. Maven project files are also provided; the QuickShop projects require the 6.3.0.3 release installed locally as `com.ghostchu.quickshop:quickshop-runtime:6.3.0.3`.

`tools/convert_ec7.py SOURCE DESTINATION` generates EC7 objects from the legacy configuration folder without reading or writing databases. EC6 rolls a rarity first and then a reward inside that rarity; EC7 uses a flat reward pool. The converter normalizes weights to preserve the original probabilities, and retains raw weights under `towny_migration.original_weight`. It preserves SNBT item payloads, commands, enchantments and one-per-player limits. The supplied Halloween rewards all use the same rarity, so normalization also preserves their probabilities as rewards become exhausted. Unsupported mixed-rarity player limits fail before any output is written.

Private server configurations and player databases are deliberately excluded from this repository. The separate configuration overlay accompanies the server update package.

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
