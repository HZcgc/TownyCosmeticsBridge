# Standalone Reborn Journey

Current version: **2.0.0-beta.3**. Install [the folder overlay](releases/TownyReborn-Journey-2.0.0-beta.3.zip). [German installation guide](README-DE.md), [JP explanation](JOURNEY-POINTS-DE.md), [rewards](REWARDS.md), [perks](PERKS.md).

The plugin owns 116 Journey levels, four continuous snake pages, free milestone rewards and 43 optional permanent features. Access requires `townyreborn.progression.access`, default false. Buying or collecting a reward never blocks level progression.

## Folder-based installation

Stop the server, remove the old Progression JAR, copy the supplied `plugins/` tree over existing plugin folders, replace only the supplied configs, and start fully. Keep all player records, quest definitions and databases.

The matching files are included for Progression, TAB groups, LyttleNametag and the Quests category UI. The plugin **does not rewrite existing configs, patch external plugin files, remove menus or archive quests**. The old `ConfigUpgrade`, `DisplaySetup` and `LegacyCleanup` classes are no longer shipped. Only a missing own config receives the normal initial default file. An incompatible old config causes an error without replacing it.

The installed configuration is explicit and reviewable before server startup. Existing reward IDs/aliases, progress, purchase receipts, permission context, prices and unlock requirements are preserved from Beta.2. Later config changes are made in files and applied with a full restart.

## Current layout

Permanent features have five tier tabs: Skeleton, Creeper, Blaze, Dragon and Wither. Each tier can extend to multiple pages. `/fly` is the final Wither entry, retaining its level-18 unlock and price. `/tfly` remains level 12. Tier labels describe the feature family; buying a feature never grants a paid parent group.

Path colours: **green = earlier completed level, yellow = current Journey level, red = future locked level**. The percentage bottle tracks progress within the current level towards the following level. A completed final level shows a completed path. Free gifts remain separately claimable at reached levels.

Nametag lines: Town/Nation; LP rank, spacing, Journey title and attached small mcMMO value; display name. TAB: LP rank, direct Journey abbreviation, display name and attached small mcMMO value. The supplied nametag template compensates the rank-image edge with two source spaces. No separator is added before the number. Existing display-name RGB, AFK suffixes, rank glyphs and unrelated TAB behavior are retained.

Quests retains all 17 existing categories in a regular grid. Slot 49 remains the Nether Star; slot 50 opens Journey progression. Its console action `rebornadmin open {player}` schedules the same player menu as `/progression` for the next tick and checks target access both before scheduling and before opening. It does not bypass the access gate. The six-active-quest limit and gameplay settings remain intact.

## Points

Default sources share one cumulative JP counter: active minute +1; new mcMMO power level +5 (500/day); new per-job high-water level +12 (500/day); fish catch +3 (300/day); first recorded biome +8 (200/day); first non-recipe advancement +15 (300/day). Quests as a JP source remain disabled. The Quest UI link does not turn on quest-based point rewards.

The first valid mcMMO/Jobs reading establishes the starting baseline. Previously recorded levels cannot be farmed again by resetting, job changes or relogging. AFK/ignored worlds, NPCs, test bots and Creative/Spectator players do not earn points. Daily limits reset at midnight Europe/Berlin. Polling and open-view refresh run every 30 seconds.

Level L requires `30 × (L−1) + 15 × (L−1) × (L−2) / 2` total points. L2=30, L3=75, L4=135. Example: 10 active minutes + 5 fish + 1 new mcMMO level = 30 JP, assuming available daily allowance. Buying perks uses game money without subtracting JP. Minecraft XP gifts do not affect the Journey counter.

## Compatibility and receipts

Legacy player profiles are read without replacement. Native profiles and pending receipts stay in `journeydata/`. Existing Beta.1 reward IDs/LP markers remain aliases of their canonical milestone, preventing replaced gifts from being claimed again. Pending old rewards retain their original commands/town/money amount. No player records are included in the installer.

Purchases persist PREPARED before debit, then DEBITED before LP changes. Double/overlapping actions are blocked. Saved DEBITED receipts may complete permissions after restart without another debit. Ambiguous payments need review.

Free payouts save original side effects and a durable LP marker before attempting cash/commands/town claims. A failed/ambiguous side effect or final-save failure is never automatically replayed. UI callback failure cannot reopen a delivered receipt. Economy/inventory/Towny persistence does not share an atomic transaction with the profile; a hard crash can require manual repair.

Owner review, player online:

```text
rebornadmin status <player>
rebornadmin receipt <player> grant
rebornadmin receipt <player> cancel
rebornadmin gift <player> delivered
rebornadmin addpoints <player> <amount>
```

Verify original payment/delivery before resolving. In-flight receipts cannot be manually closed. Do not delete claim markers, player data or receipts to retry rewards. Rollback requires matching permission/profile/economy snapshots.

## Build and validation

Requires Paper, Java 21+, PlaceholderAPI, LuckPerms, Vault and a registered economy provider. Jobs/mcMMO are optional sources. There is no Quests/DeluxeMenus dependency or runtime external config writer.

```text
python build.py --jdk <jdk-bin> --deps <dependency-directory> --output <output-jar>
python verify.py --jdk <jdk-bin> --deps <dependency-directory> --jar <output-jar> --output <verification-directory>
```

Checks cover tier pages/fly order, current-level colours, cumulative point examples, unchanged config files, absence of external writers, old claims and uncertain payments, the Quests next-tick access-gated bridge and actual inventory actions. Paper registry/inventory creation, wallet and LP boundaries use controlled doubles. The live server/client check is separate.

Current report: `verification/JOURNEY-UPDATE-VERIFICATION.txt`. Historical Beta.1/Beta.2 binaries and their recorded reports are retained. Release hashes are in `releases/SHA256SUMS.txt`.

Placeholders retain `level`, `title`, `short_title`, `points`, `percent`, `remaining_percent`, `remaining_points`, `next_required`, `playtime_hours`, `access` and the `trprogress` compatibility identifier. Commands: `/progression`, `/reborn`, `/reborn perks`.

[Quests custom GUI item documentation](https://quests.leonardobishop.com/configuration/custom-gui-items) describes zero-based slots, command actions and player substitution.
