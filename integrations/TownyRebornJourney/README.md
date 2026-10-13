# Standalone Reborn Journey

TownyRebornProgression 2.0.0-beta.1 replaces the previous Quests-bound Reborn progression. It owns its inventory, levels, active time, rewards and permanent perk purchases. Public access is controlled by `townyreborn.progression.access`, which defaults to false. There is no global pause flag.

The default journey has 116 levels across four 29-node snake pages, 44 optional permanent perks and a free reward at every level. Functional Skeleton through Wither permissions are available by level 86. `/fly` becomes purchasable at level 18, `/tfly` at level 12. The existing monthly rank prefixes/groups remain separate from earned titles.

A level is derived only from Journey Points. Optional purchases use Vault money without spending points. Buying perks or claiming rewards never becomes a prerequisite for the next level. Upgrade chains apply only to a purchase within the same feature family, such as homes or vaults.

## Dependencies and optional sources

Required: Paper, PlaceholderAPI, LuckPerms, Vault and a registered economy provider. Jobs and mcMMO are optional level-gain sources. Quests and DeluxeMenus are not dependencies. The plugin loads before them to archive its old integration before their files are read.

Default point sources: one point per active minute; new mcMMO high-water levels (+5, 500/day); separate per-job high-water levels (+12, 500/day); catches (+3, 300/day); previously unvisited biomes (+8, 200/day); non-recipe advancements (+15, 300/day). AFK worlds, NPCs, test bots and Creative/Spectator players do not earn points. A first valid mcMMO/Jobs counter establishes a baseline. Leaving/rejoining a job or restoring a lower level cannot award previously recorded gains again. Gains beyond a daily cap are not banked for future days.

Quests points are an optional source and are disabled by default. The new path has no mandatory quest count, category requirements or mcMMO-only requirement. Existing Jobs payouts and mcMMO leveling settings are outside this plugin.

The curve for level L is `30 × (L−1) + 15 × (L−1) × (L−2) / 2` points. Titles, curve, source rates, prices, levels and rewards are editable in `plugins/TownyRebornProgression/config.yml`. Increase `journey.steps` and add stable reward/perk IDs to extend the path, then restart. Current validation supports up to 10,000 levels. Preserve IDs and existing receipts when editing the catalogue.

## Installation and controlled release

1. Stop the server fully. Keep a snapshot of the existing Progression, Quests, DeluxeMenus and LuckPerms data for rollback.
2. Remove all old TownyRebornProgression JAR copies, including the paused build. Install the single 2.0.0-beta.1 JAR in `plugins/`.
3. Keep `plugins/TownyRebornProgression/` in place. The new plugin reads old `playerdata/` and writes new `journeydata/`; it does not replace an existing player database with uploaded snapshots.
4. Copy the supplied LyttleNametag `config.yml` and TAB `groups.yml` to their matching plugin folders if you want the new displays. Other TAB settings, mcMMO statistics, display names, AFK suffixes and Town/nation lines retain their existing behavior.
5. Start the server. Original/paused progression settings are backed up to `legacy-backup/config-pre-standalone.yml` before installing the native configuration. The old `progression.paused` option has no effect on the standalone release.
6. The startup cleanup archives `Quests/quests/reborn`, removes the Reborn category and its old shortcut, and archives/removes the old `reborn_*.yml` DeluxeMenus registrations. Originals are in `TownyRebornProgression/legacy-backup/<timestamp>/`. Normal quests and all legacy player records remain intact.
7. Grant access to a test account. Once the owner is satisfied, grant it to Default. A full restart is required for the migration; use a full restart for later config edits too.

Console commands (omit the leading slash in the console):

```text
lp user HZcgc_No2 permission set townyreborn.progression.access true server=smp
lp user HZcgc_No2 permission set townyreborn.progression.admin true server=smp
```

Owner test: open `/reborn`, browse the four pages, open `/reborn perks`, review a purchase, earn a few points, claim one free reward and relog. `/rebornadmin status HZcgc_No2` shows level and pending receipts. Use an alt without access to verify the release gate.

Public release:

```text
lp group default permission set townyreborn.progression.access true server=smp
```

Public access can be removed with `lp group default permission unset townyreborn.progression.access server=smp`. Explicit per-user permissions and wildcard staff permissions still apply. The configuration's permission context is `smp`, matching the supplied export; adjust it if your LuckPerms server context changes.

## Migration and permanent permissions

Legacy levels, active minutes and quest points seed the new journey once, deterministically: the larger of the mapped old level threshold and `active minutes + 25 × quest points`. Level 1 maps to 1; later old levels map to `(old level−1)×12`, capped at the new path length. Reading a placeholder alone does not create a player file or award points. Players without access do not earn new points.

Owned perks are imported from explicit permanent user nodes with matching/global server context, not from paid group inheritance, wildcard access or expiring/world/region-only grants. Higher owned tiers include lower purchase tiers. Permanent profile records also repair missing companion LP permissions without charging money or replaying reward commands.

Homes/vaults retain the existing SubscriptionBridge counter permissions. Shop upgrades retain its exact legacy markers (`shops.30/50/80/120/160`): the first perk adds 30 slots to the current base allowance; later perks provide minimum total allowances of 100/150/200/240. The existing paid group, permanent add-on and 300-shop ceiling are still calculated by SubscriptionBridge. The new plugin never changes a LuckPerms parent group or grants paid rank prefixes.

Default already provides one Player Warp, plain nicknames and basic sign/anvil colours, so these are not sold as new perks. Existing crate bonuses from old perk purchases are not replayed on import. The new milestones have their own one-shot IDs.

## Purchases, rewards and receipt review

Purchases save a PREPARED receipt before asking Vault to withdraw money, then a DEBITED receipt before granting permissions. Double clicks and overlapping purchases/rewards are blocked per player. A saved DEBITED receipt can safely finish its permission grant after restart without another debit. A PREPARED receipt with an ambiguous wallet result requires owner review: a changed balance is not treated as proof of a payment.

Free key/item/town rewards persist a pending receipt and a durable LP marker before issuing a non-idempotent effect. They are never automatically replayed after restart. If a plugin call fails, a bundle partially executes, or final storage fails, staff review the saved receipt and deliver only the missing portion. Receipts contain rendered commands and the original reward town. This favors preventing duplicated rewards; unrelated plugins' inventory/economy/Towny databases cannot participate in one atomic transaction. A hard crash can therefore leave a reward needing manual repair. Towny saves its bonus blocks through its own persistence queue.

Owner commands, with the player online:

```text
rebornadmin status <player>
rebornadmin receipt <player> grant
rebornadmin receipt <player> cancel
rebornadmin gift <player> delivered
rebornadmin addpoints <player> <1..1000000>
```

Use `receipt ... grant` only after confirming that the recorded payment occurred; `cancel` closes a receipt without debit or refund. Use `gift ... delivered` only after verifying/delivering the reward. In-flight saves cannot be manually closed. These commands require `townyreborn.progression.admin`.

Never delete `journeydata/`, claimed IDs, LP reward markers or transaction receipts to retry a delivery. A rollback must restore the matching data/permissions snapshot as well as the old JAR and archived Quests/DeluxeMenus configurations; mixing old/new reward ledgers can duplicate rewards.

## Displays and placeholders

`/reborn` opens the native journey. Aliases: `/ranks`, `/perks`, `/progress`, `/journey`, `/fortschritt`. `/reborn perks` opens all permanent upgrades. Nametags show the existing rank prefix, the Reborn title and a small Reborn level; TAB replaces the former mcMMO number with the Reborn level. mcMMO remains available as its own gameplay statistic.

Placeholders: `%reborn_level%`, `%reborn_title%`, `%reborn_points%`, `%reborn_percent%`, `%reborn_next_required%`, `%reborn_playtime_hours%`, `%reborn_access%`. Corresponding `trprogress` placeholders are retained for older integrations. Retired `ready_*` meta-quest placeholders always return 0.

Keys use the current `cratekey give <player> <key> <amount>` command and existing virtual IDs: `daily`, `vote`, `cosmetic`, `patron` (Supporter). Halloween balances/rewards are not part of the journey migration. The added UltraCosmetics rewards use installed hats, emotes and particles; pet catalogues remain managed by their current plugins.

Town rewards give extra available claim chunks to the town at claim time. They can be claimed later if the player has no town. Joining another town cannot re-claim a milestone already delivered.

## Verification and build

The supplied release passes 41 named Java checks against real Paper YAML and API classes, plus 12 dependency/catalogue checks. Tests use all 36 uploaded progression profiles, preserving all 48 old claimed-level records and original bytes; cover native inventory actions, permission gates/revocation, LP import filters, cleanup rollback, deterministic migration, concurrent clicks, restart recovery, storage/payment failures and partial reward delivery. Wallet/LP/GUI side effects use controlled test backends or mocks. A live server/client integration test is still required before public release.

The original local checkout was no longer present when publishing to GitHub. Sources were recovered from the checksum-verified delivered release and their erased local collection types restored for compilation. The exact previously tested binary and install bundle are retained under `releases/`, with SHA-256 hashes. The original 53-check report is retained under `verification/ORIGINAL-RELEASE-VERIFICATION.txt`; its original test source/input checkout is not included here.

The recovered source compiles and passes 24 additional core scenarios against both the original binary and the source rebuild, including level boundaries, tier ownership, profile storage, high-water gains, duplicate purchases, ambiguous/debited payment recovery, free reward receipts and town-claim deferral. These recovery checks are separate from the recorded original 53-check run. Install the checksum-verified binary from `releases/` for the current beta.

Build and verify recovered source with the repository's dependency directory and JDK path:

```text
python build.py --jdk <jdk-bin> --deps <dependency-directory> --output <output-jar>
python verify.py --jdk <jdk-bin> --deps <dependency-directory> --jar <output-jar> --output <verification-directory>
```

The shipped JAR contains only the new source-built classes and its own resources. It contains no Quests API, old progression classes or player data.

Towny API references used for optional integration: [TownyAPI](https://townyadvanced.github.io/Towny/javadoc/release/com/palmergames/bukkit/towny/TownyAPI.html) and [Town bonus blocks/persistence](https://townyadvanced.github.io/Towny/javadoc/release/com/palmergames/bukkit/towny/object/Town.html).
