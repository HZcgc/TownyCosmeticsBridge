# Standalone Reborn Journey

Current release: **TownyRebornProgression 2.0.0-beta.2**. Install [the beta.2 bundle](releases/TownyReborn-Journey-2.0.0-beta.2.zip). [German installation guide](README-DE.md), [all rewards](REWARDS.md), [permanent perks](PERKS.md).

The standalone plugin owns 116 levels, four snake pages, Journey Points, free milestone rewards and 43 optional permanent purchases. Access requires `townyreborn.progression.access`, default false. Level-ups depend only on points. Buying a perk or collecting a reward never blocks the next level.

## Current behavior

Each level states the required total and missing points. The progress bottle shows completed/remaining percentages, point totals and live personal stats. Its guide provides actions, configured point rates, daily limits and server TPS/online count. Green means reached, yellow the next goal, red a later locked milestone.

Alternate pages mirror the path's five rows. A bottom endpoint continues at the bottom of the next page; a top endpoint continues at the top. The default path contains 29 milestones per page and can be extended up to 10,000 levels.

Rewards are money through Vault, useful vanilla items, occasional one-shot keys and town bonus chunks. Minecraft XP appears only at levels 12, 52 and 104. There are no UltraCosmetics or cosmetic-crate rewards and no permanent daily Supporter-key perk. One-time Supporter-key rewards remain at four milestones.

QuickShop trading shops and Player Warps are explicitly different. Trading allowances use the existing SubscriptionBridge's 50/100/150/200/240 shop tiers, add-on stacking and 300-shop ceiling. Warp purchases grant two or three destinations. No 200-warp permission exists. `/fly` remains available at level 18 and `/tfly` at level 12.

## Displays

- Nametag: Town/Nation; LuckPerms rank and full Journey title; display name.
- TAB: LuckPerms rank, Journey abbreviation, display name, existing small mcMMO number.
- TownyChat: LuckPerms rank, full Journey title, display name, existing mcMMO number.

On full startup, `DisplaySetup` adjusts the installed TAB `groups.yml`, Towny `settings/ChatConfig.yml` and LyttleNametag `config.yml`. It preserves unrelated settings, AFK suffixes, RGB display names, chat channel tags, permission suffixes and per-world channel formats. Unsupported custom formatter lines are left unchanged. Existing small-number mcMMO formatting is retained where present; the known small-number format is the fallback. Set `display.auto-configure: false` after applying the layout if you want to manage those fields yourself. Display backups are under `legacy-backup/display-v3/`.

TownyChat formats its own existing channels; this plugin does not cancel/rebroadcast chat, change recipients, or install another chat formatter. If a separate plugin owns global chat, its format needs the same placeholders. The shipped plugin loads before TownyChat, TAB and LyttleNametag.

New placeholders: `%reborn_short_title%`, `%reborn_tab_prefix%`, `%reborn_chat_prefix%`, `%reborn_remaining_percent%`, `%reborn_remaining_points%`. Existing `level`, `title`, `points`, `percent`, `next_level`, `next_title`, `next_required`, `playtime_hours` and `access` placeholders remain. `trprogress` aliases remain compatible. Abbreviations are configured in `journey.short-titles`.

## Point sources

Defaults: one JP per active minute; new mcMMO power levels +5, 500/day; separate new personal levels per Job +12, 500/day; fishing +3, 300/day; new biomes +8, 200/day; non-recipe advancements +15, 300/day. Quests are optional and disabled. There is no required quest count or single-skill requirement. NPCs, ignored worlds/bots and Creative/Spectator players do not earn points.

The first valid mcMMO/Jobs counter establishes the baseline. Recorded high-water marks prevent re-awarding gains after a reset, job switch or relog. Gains above a daily cap are not banked. Limits reset at midnight Europe/Berlin. Progress and open menus refresh every 30 seconds.

The threshold for level L is `30 × (L−1) + 15 × (L−1) × (L−2) / 2`. Rates and the curve remain configurable. A level is not Minecraft XP; occasional XP gifts do not advance the Journey.

## Installation and migration

Stop fully, keep a data/permissions backup, replace every old Progression JAR with the single beta.2 JAR, keep the existing Progression data folder, then start fully. The bundle does not contain player records or an overriding live config. Its reference files are for comparison.

Beta.1 config is backed up to `legacy-backup/config-before-catalogue-v3.yml`. The new catalogue is installed once while preserving curve, source settings, permission context, titles and paid-perk prices/ownership IDs. The recurring-key offer is removed; shop/warp labels and help text are corrected. Later version-3 custom edits are not overwritten.

Each new reward has a stable `milestone_NNN` ID with aliases for its old beta.1 reward IDs. Existing claimed IDs and direct permanent LP markers import as claimed, without executing replacement rewards. Pending old receipts retain the original rendered commands, town bonus and amount. Never reuse an ID for a different claim or delete a player's claim ledger. Old earned permission grants are not broadly revoked; paid monthly subscriptions remain managed by their existing bridge.

Legacy Quests cleanup from beta.1 remains: it archives only the old Reborn quest definitions/category/menu registrations before Quests and DeluxeMenus load. Normal quests remain. Old `playerdata/` is read without overwriting it; new native profiles use `journeydata/`. Placeholder reads alone do not create a profile or award points.

## Transactions

Purchases save PREPARED before debit and DEBITED before LP changes. Duplicate/overlapping clicks are blocked. A saved DEBITED transaction may finish permissions after restart without another debit. Ambiguous PREPARED transactions need owner review.

Free reward delivery saves its original cash amount/commands/town in a receipt, then durably saves the LP marker before attempting money or item/key/town effects. Side effects are attempted once. A failed/ambiguous deposit, partial bundle, crash or final-save failure leaves a pending receipt; there is no automatic replay. Profile, economy, inventory and Towny databases do not share an atomic transaction, so a hard crash can require manual repair. Permission repair never replays commands or pays money.

Owner controls, player online:

```text
rebornadmin status <player>
rebornadmin receipt <player> grant
rebornadmin receipt <player> cancel
rebornadmin gift <player> delivered
rebornadmin addpoints <player> <1..1000000>
```

Confirm original payment/delivery before closing a receipt. In-flight saves cannot be manually closed. A rollback must restore matching config, profile, permission and economy records as well as the old binary.

## Dependencies, build and verification

Requires Paper/Java 21+, PlaceholderAPI, LuckPerms, Vault and a registered economy provider. Jobs/mcMMO are optional sources; Quests/DeluxeMenus are not dependencies.

```text
python build.py --jdk <jdk-bin> --deps <dependency-directory> --output <output-jar>
python verify.py --jdk <jdk-bin> --deps <dependency-directory> --jar <output-jar> --output <verification-directory>
```

`RecoveryChecks` runs 24 purchase/reward/profile recovery scenarios against the preserved beta.1 binary and this update. `JourneyUpdateChecks` covers the requested layout, configurable instructions, claim/config migration, uncertain/declined payouts, failed storage, preserved displays and native inventory actions. Paper inventory/registry, wallet and LP boundaries use controlled test doubles. A live server/client check remains necessary before public access.

Original beta.1 files and their earlier verification report remain under `releases/` and `verification/` for historical comparison. Current checks are in `verification/JOURNEY-UPDATE-VERIFICATION.txt`; current binaries and bundles have SHA-256 hashes in `releases/SHA256SUMS.txt`.

References: [TownyChat placeholder support](https://github.com/TownyAdvanced/TownyChat#papi-placeholder-support), [Towny API](https://townyadvanced.github.io/Towny/javadoc/release/com/palmergames/bukkit/towny/TownyAPI.html).
