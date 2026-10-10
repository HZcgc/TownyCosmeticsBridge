# BiggerBarrels adapter for QuickShop-Hikari 6.3.0.3

Source baseline: https://github.com/QuickShop-Community/QuickShop-Hikari/tree/6.3.0.3

Only `BukkitInventoryWrapper` is replaced. The wrapper keeps the physical inventory holder and location, and resolves `BiggerBarrels.getBarrel(Block)` for stock iteration, snapshots, item insertion, clearing and restoration. The compatibility marker is checked by TownyRebornBarrelShops. Regular inventories retain upstream behavior.

`tools/build.py` checks the original release SHA-256 and overlays the compiled replacement in the full upstream JAR. The output bundles the patch source and upstream license. Rebuild the adapter when changing QuickShop versions; the patch is pinned to 6.3.0.3.

Upstream applies GPL-3.0 to older code and AGPL-3.0 to additions from 2024 onward. See [LICENSE](LICENSE); this modified wrapper is distributed under AGPL-3.0. Corresponding patch source is in this directory and in the delivered JAR. Upstream source remains available at the release link above.
