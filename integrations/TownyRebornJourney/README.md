# Standalone Reborn Journey

Current release: **2.0.0-beta.4**. Install [the folder overlay](releases/TownyReborn-Journey-2.0.0-beta.4.zip). [German installation guide](README-DE.md), [JP explanation](JOURNEY-POINTS-DE.md), [reward catalogue](REWARDS.md), [permanent features](PERKS.md).

116 levels, four continuous snake pages, a distinct title per level and 43 optional permanent features. Access requires `townyreborn.progression.access`, default false. Paid feature purchases never gate normal levelling. Fly is the final Wither feature and now requires the final level 116 for a new purchase.

## Folder installation and authorised progress restart

Stop the server, back up the existing Progression folder, remove the old JAR, copy the supplied `plugins/` files over the existing folders and restart fully. Keep player records, quest definitions and databases. The plugin does not rewrite external configs or existing own configs; only a missing own config receives the initial default.

This release contains an explicitly requested **one-time points restart**. `journey.progress-revision: long-journey-2026-10-v1` is compared with the profile marker. On first load of an older native/legacy profile, Store backs up its exact original contents, resets only the JP counter and current-Journey minutes, retains purchased perks/claim receipts/daily usage/high-water history, and commits the marker atomically before caching the record. Offline profiles migrate on first load. A failure to back up or save stops that load without replacing the old record or caching a partial migration. New profiles start with the same marker and zero points. Repeating installation, restart or relog does not repeat the reset. **Keep this revision value unchanged in future configs.**

All pending receipts retain their original amounts, commands and town targets. A debited old purchase can finish permission delivery at level 1 without another debit. Previously claimed gift IDs and LP markers remain claimed even when this release increases reward value. No refund/top-up/replayed gift is inferred from the reset. Existing permanent permissions are repaired from owned features and original explicit LP markers. Temporary/inherited supporter group access does not become a permanent purchase.

High-water values and achievements remain. Each existing skill/Jobs key gets a persisted baseline-due marker: its next valid reading establishes the new baseline without retrospective points, retaining the higher old record. Invalid readings cannot consume that marker. New increases and all daily usage continue across restart.

## Points and pacing

All activity sources share one cumulative point balance: active minute +1 (360/day); new mcMMO power level +5 (500/day); new per-job high-water level +12 (500/day); fish catch +3 (300/day); first biome +8 (200/day); first non-recipe advancement +15 (300/day). Normal Quests remain disabled as a JP source. Buying perks uses game money, not JP.

All awards and UI daily calculations use the UTC calendar, resetting at **00:00 UTC** regardless of the host/JVM timezone. Activity/poll refresh is 30 seconds, with a 90-second idle timeout. Creative/Spectator/NPCs/test-name prefixes/ignored worlds do not earn points.

Level L requires `150 × (L−1) + 30 × (L−1) × (L−2) / 2` total points. L2=150, L3=330, L116=213900. The theoretical daily ceiling is 2160 JP: 99 days reach only 213840, so normal progression needs at least the 100th distinct UTC day. One-time biome/advancement sources cannot sustain that ceiling; this is a strict optimistic lower bound, not a promise about actual completion time. Owner `addpoints` intentionally bypasses ordinary pacing.

## UI and feedback

Every level has a distinct English title. TAB uses J001-J116; the rank palette transitions pink to lime and uses a subtle text gradient. `title`/`short_title` stay plain for compatibility; `title_colored`/`short_title_colored` supply legacy RGB, and `title_mini` supplies MiniMessage. Existing `chat_prefix` automatically uses the coloured full title. Supplied TAB and nametag files explicitly use the renderer-specific placeholders, preserve LP order, display-name RGB and attached small mcMMO.

Green is an earlier completed level, yellow is the current level, red is a future locked level. The progress bottle remains focused on goal/percent/missing JP. The player head supplies personal money/playtime/skills/Jobs/fish/quests/perk stats. How to progress has six numbered sources in two rows, followed by explanation, personal stats and UTC limits. No server snapshot/TPS item remains.

Perk tiers are clickable Skeleton/Creeper/Blaze/Dragon/Wither head tabs, with a browsing-instruction book. The Blaze tab embeds the fixed skin returned by Mojang's MHF_Blaze profile, avoiding an asynchronous named-player lookup. Source: `https://sessionserver.mojang.com/session/minecraft/profile/4c38ed11596a4fd4ab1d26f386c1cbac`; skin hash `d06e342f90ec538aaa1552b224f264a040840902e126d91ece6139aa5b3c7cc3`.

A level increase is saved before a title/chat notification and `minecraft:ui.toast.challenge_complete` sound. The notification names the final reached level/title and counts new available rewards/perks. It also handles owner-added points; no false success feedback is sent after a failed save. Free rewards still need an explicit claim; the sound does not create an actual Minecraft advancement.

The Quests(8) screenshot layout retains all 17 category IDs and gameplay settings. Experience is the final category in YAML insertion order, placing it into free slot 40; slot 31 becomes filler. This also removes the malformed filler material that rendered as stone. The Journey shortcut stays at screenshot slot 51, with its access-gated next-tick `rebornadmin open {player}` bridge. Existing category slots other than Experience are retained.

## Receipts and owner review

Purchases save PREPARED before debit, DEBITED before permission writes. Overlapping actions are blocked; DEBITED recovery does not charge again. Ambiguous payments need owner review. Gifts persist original side effects and durable LP markers before delivery; uncertain effects/final-save failures are never automatically replayed. Economy, inventory and Towny do not share a transaction with the profile: a hard crash can require manual reconciliation.

Owner, player online: `rebornadmin status <player>`; `rebornadmin receipt <player> grant/cancel`; `rebornadmin gift <player> delivered`; `rebornadmin addpoints <player> <amount>`. Verify actual payment/delivery before resolving. Do not delete claims/receipts to retry. Rollback needs matching profile/permission/economy snapshots.

## Build and verification

Requires Java 21+, Paper, PlaceholderAPI, LuckPerms and Vault with an economy provider. Jobs/mcMMO are optional. No Quests or DeluxeMenus runtime dependency.

```text
python build.py --jdk <jdk-bin> --deps <dependency-directory> --output <output-jar>
python verify.py --jdk <jdk-bin> --deps <dependency-directory> --jar <output-jar> --output <verification-directory>
python package.py --jar <output-jar> --verification <verification-directory>
```

Core recovery scenarios run on both the preserved Beta.1 binary and this source build. Update checks cover actual menu inventory/actions, unchanged config files, old claims and failed/uncertain payments. Additional long-path checks cover one-time native/legacy migration, byte-identical backups, failed backup/save, paid recovery after reset, baseline persistence, UTC boundaries, pacing, unique renderable titles, notifications, Blaze skin and live-stat placement. Inventory registries, wallet/LP and client sound/title boundaries use controlled doubles. Live Paper/Minecraft verification is separate.

Report: `verification/JOURNEY-UPDATE-VERIFICATION.txt`. Historical release binaries/packages and their original checksums are retained. No player records or database exports are shipped.
