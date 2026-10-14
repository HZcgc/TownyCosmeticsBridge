# Standalone Reborn Journey

Current release: **2.0.0-beta.6**. Install [the folder overlay](releases/TownyReborn-Journey-2.0.0-beta.6.zip). [German installation guide](README-DE.md), [JP explanation](JOURNEY-POINTS-DE.md), [reward catalogue](REWARDS.md), [permanent features](PERKS.md).

116 levels, four continuous snake pages, a distinct title per level and 43 optional permanent features. Access requires `townyreborn.progression.access`, default false. Paid feature purchases never gate normal levelling. Fly is the final Wither feature and requires level 116 for new eligibility; previously reached purchase unlocks are retained.

## Beta.6 town-only flight and retained unlocks

An already installed Beta.4 or Beta.5 uses the small hotfix ZIP: remove the old JAR, copy its `plugins/` tree (new JAR and updated own config), and restart fully. Keep player records and migration backups. The config is an explicit file overlay; no startup writer changes an existing config. The progress revision remains `long-journey-2026-10-v1`: this hotfix does not reset the new Journey points again.

Purchase eligibility now has its own durable `unlocked-perks` set, independent of `owned-perks`. Native pre-reset records and the exact UUID-matching pre-reset backup restore old eligibility using the frozen Beta.3 catalogue in `legacy-perks-beta3.yml` (30/15 curve and original gates). Confirmed old `owned-perks` may be restored as ownership, but thresholds never grant a paid feature, permission, cash or key. Wrong-schema/UUID backups fail closed; backups from the new curve are not interpreted as old progress. Each repair saves before caching; `legacy-perks-imported` makes it once-only. Future reached gates are saved independently of points.

Already reached early Fly eligibility is retained; new players still need level 116. Prerequisite upgrades must still actually be owned. All 43 feature prices are reduced by around 25% in aggregate (total $2,350,000 → $1,755,000); paid receipts keep their original amount, balance and timestamp even if permission-delivery persistence fails. Permanent LP purchase markers continue to import actual ownership, including lower upgrades. The UI distinguishes permanent purchases (green), current rank/permission access (blue), available purchases (chest) and unreached gates (red). Rank/wildcard access never becomes a permanent purchase. Owner status reports both ownership and eligibility.

`Flight in all towns` replaces the Beta.5 unrestricted TownyFlight bypass. The stable `town_flight_bypass` feature ID now grants only `townyflight.alltowns`, unlocking at Level 86 for $110,000. It retains the own-town `tfly` prerequisite (Level 12). Flight ends on entering wilderness unless independently authorised by other rank/plugin permissions. Unrestricted `/fly` remains Level 116. Scope appears on the perk page, milestone and confirmation views. Reference: <https://github.com/TownyAdvanced/TownyFlight#permission-nodes>.

On activation, import and persist confirmed ownership before `Integrations.repairOwned` replaces the owner's old direct permanent `townyflight.bypass` grant with All-Town access in one LP save. The old bypass remains a legacy purchase marker, but is never a new grant. Only persistent user data is inspected for old ownership, excluding transient/inherited access. Removal is limited to true unexpired old bypass nodes without extra contexts, with global scope or exactly the configured server. Temporary/false/world/other-server/multi-server nodes, inherited group permissions and independent `/fly` grants are untouched. No negative override is written. Owner/Wither/Staff may still have their independent broader flight access, shown as current rank access rather than a purchase. LP save failures keep the recorded purchase and retry on activation, including when the loaded in-memory nodes already match. No re-debit or reward replay occurs.

The Blaze tab now sends the official `MHF_Blaze` UUID and nonempty name alongside its fixed canonical skin URL. Mojang's profile/session services confirm the identity and texture, and the texture PNG was fetched successfully. This avoids an incomplete nameless profile and permits a valid Mojang lookup if a client resolves it. No runtime HTTP lookup or new HeadDB dependency is introduced.

The released build passed 118 scenarios, including eight anonymous permission-only cases from the supplied export through the actual LP importer. The unprovided private fixture is not published; the nine general permission-import scenarios run without it (110 public scenarios total). The original user export and player files are not part of the repository or release package. Live Paper/client verification remains separate.

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
