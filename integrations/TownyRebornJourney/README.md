# Standalone Reborn Journey

Current release: **2.0.0-beta.8**. Install [the folder overlay](releases/TownyReborn-Journey-2.0.0-beta.8.zip). [German installation guide](README-DE.md), [JP explanation](JOURNEY-POINTS-DE.md), [reward catalogue](REWARDS.md), [permanent features](PERKS.md).

116 levels, four continuous snake pages, a distinct title per level and 44 optional permanent features. Access requires `townyreborn.progression.access`, default false. Paid feature purchases never gate normal levelling. Fly is the final Wither feature and requires level 116 for new eligibility; previously reached purchase unlocks are retained.

## Beta.7 historical progress restoration

An already installed Beta.4–Beta.6 uses the small hotfix ZIP: back up the existing folder, remove the old JAR, copy its `plugins/` tree (**both the JAR and updated own config**), and restart fully. Keep `playerdata`, `journeydata`, `migration-backups` and the new `history-credits` directory. The config is an explicit overlay; no startup writer changes an existing config. `history-credit-revision: pre-reset-points-2026-10-v1` enables the new credit. The existing reset revision remains unchanged.

Purchase eligibility now has its own durable `unlocked-perks` set, independent of `owned-perks`. Native pre-reset records and the exact UUID-matching pre-reset backup restore old eligibility using the frozen Beta.3 catalogue in `legacy-perks-beta3.yml` (30/15 curve and original gates). Confirmed old `owned-perks` may be restored as ownership, but thresholds never grant a paid feature, permission, cash or key. Wrong-schema/UUID backups fail closed; backups from the new curve are not interpreted as old progress. Each repair saves before caching; `legacy-perks-imported` makes it once-only. Future reached gates are saved independently of points.

Already reached early Fly eligibility is retained; new players still need level 116. Prerequisite upgrades must still actually be owned. All 43 feature prices are reduced by around 25% in aggregate (total $2,350,000 → $1,755,000); paid receipts keep their original amount, balance and timestamp even if permission-delivery persistence fails. Permanent LP purchase markers continue to import actual ownership, including lower upgrades. The UI distinguishes permanent purchases (green), current rank/permission access (blue), available purchases (chest) and unreached gates (red). Rank/wildcard access never becomes a permanent purchase. Owner status reports both ownership and eligibility.

`Flight in all towns` replaces the Beta.5 unrestricted TownyFlight bypass. The stable `town_flight_bypass` feature ID now grants only `townyflight.alltowns`, unlocking at Level 86 for $110,000. It retains the own-town `tfly` prerequisite (Level 12). Flight ends on entering wilderness unless independently authorised by other rank/plugin permissions. Unrestricted `/fly` remains Level 116. Scope appears on the perk page, milestone and confirmation views. Reference: <https://github.com/TownyAdvanced/TownyFlight#permission-nodes>.

On activation, import and persist confirmed ownership before `Integrations.repairOwned` replaces the owner's old direct permanent `townyflight.bypass` grant with All-Town access in one LP save. The old bypass remains a legacy purchase marker, but is never a new grant. Only persistent user data is inspected for old ownership, excluding transient/inherited access. Removal is limited to true unexpired old bypass nodes without extra contexts, with global scope or exactly the configured server. Temporary/false/world/other-server/multi-server nodes, inherited group permissions and independent `/fly` grants are untouched. No negative override is written. Owner/Wither/Staff may still have their independent broader flight access, shown as current rank access rather than a purchase. LP save failures keep the recorded purchase and retry on activation, including when the loaded in-memory nodes already match. No re-debit or reward replay occurs.

The Blaze tab now sends the official `MHF_Blaze` UUID and nonempty name alongside its fixed canonical skin URL. Mojang's profile/session services confirm the identity and texture, and the texture PNG was fetched successfully. This avoids an incomplete nameless profile and permits a valid Mojang lookup if a client resolves it. No runtime HTTP lookup or new HeadDB dependency is introduced.

The released build passed 183 scenarios, including eight anonymous permission-only cases and 37 anonymous historical-profile cases derived from the supplied data. Private fixtures and original player/export records are not published. Without those two optional fixtures, 138 public scenarios run. Inventory/client/economy/LP boundaries use controlled doubles; profile and journal persistence uses real temporary files. Live Paper/client verification remains separate.

## Historical credit and installation

The default is now **proven pre-reset earned JP + post-reset earned JP** on the current 150/30 curve. This retains point units instead of inflating them to keep an old numerical level: 5052 previous points plus 4 current points become 5056 / Level 15. The priority is a UUID-exact schema-2 native backup under the original reset revision, then its legacy `playerdata` backup, then the still-present legacy file. Sources are never summed. Native proof must predate the new curve. Missing proof adds zero; malformed or ambiguous proof fails closed.

Legacy data is converted with the frozen old 30/15 curve and the exact earlier mapping: maximum of the old threshold at `(oldLevel−1)×12` (capped at 116; Level 1 stays 1) and `activeMinutes + 25×questPoints`. For example, old Rank Level 4, 6596 minutes and 541 quest points yield 20121 prior JP / current Level 33, plus new earnings. The expensive new threshold is not used as a historical rank floor. Prior purchase eligibility is recovered using the former gates, independent of the newly calculated level; point thresholds never grant paid ownership. Claimed gifts, pending effects, original paid amounts, daily budgets, high-water marks and achievements are retained.

`HistoryCredit` first backs up the current native profile under the credit revision, then writes a UUID-specific PREPARED journal under the fixed `history-credits/` directory. It records the exact before-file SHA256 (or absence), before/after JP and Journey minutes, original amount, source, revision and timestamp. Store atomically saves points and the profile credit marker, then finalises the journal as COMPLETED before caching. Save failure leaves the original profile intact and a prepared journal; only an exact unchanged before-file and counters can be automatically resumed. A committed native marker plus prepared journal can safely finalise without re-crediting. A changed unmarked profile with a prepared journal stops for review instead of guessing.

The separate completed journal survives older JARs stripping the new profile fields and prevents a second credit. It also protects against another zero-reset if the older JAR strips the old reset marker; a known new-curve counter is not interpreted using old purchase gates. An existing profile marker alone also prevents another grant if the journal is missing. Changing the credit revision cannot duplicate a completed grant because the UUID journal location is fixed. Deleting a credited native profile with an existing completed journal stops recovery rather than reconstructing another balance. Keep all data/receipt/backup folders and both revision values.

Historical active minutes are included once, limited to the gap between preserved total active minutes and current Journey minutes. New players still begin at 0; no historical data means no invented points. Positive restorations send a player notice after durable save when Journey access activates; they execute no money/item/key/reward command. New earned levels retain their normal title/sound feedback. Owner status reports the historical amount and source alongside total JP. Offline profiles migrate on their next load/login. The files in the install overlay contain no player data.

Beta.4's older reset mode remains available only when the history-credit setting is absent/empty. LongJourneyChecks explicitly exercises that prior mode, while HistoryCreditChecks covers the new enabled default and crash boundaries. Skill/Jobs baselines and all daily usage remain protected; current measurements are not retroactively converted into extra historical awards. Existing paid permissions and gift markers are repaired through the normal LP import without new payments or replacement payouts.

## Points and pacing

All activity sources share one cumulative point balance: active minute +1 (360/day); new mcMMO power level +5 (500/day); new per-job high-water level +12 (500/day); fish catch +3 (300/day); first biome +8 (200/day); first non-recipe advancement +15 (300/day). Normal Quests remain disabled as a JP source. Buying perks uses game money, not JP.

All awards and UI daily calculations use the UTC calendar, resetting at **00:00 UTC** regardless of the host/JVM timezone. Activity/poll refresh is 30 seconds, with a 90-second idle timeout. Creative/Spectator/NPCs/test-name prefixes/ignored worlds do not earn points.

Level L requires `150 × (L−1) + 30 × (L−1) × (L−2) / 2` total points. L2=150, L3=330, L116=213900. The theoretical daily ceiling is 2160 JP: 99 days reach only 213840, so a fresh player starting from zero needs at least the 100th distinct UTC day. Existing players receive their proven historical head start. One-time biome/advancement sources cannot sustain that ceiling; this is a strict optimistic lower bound, not a promise about actual completion time. Owner `addpoints` intentionally bypasses ordinary pacing.

## UI and feedback

Every level has a distinct English title. TAB uses J001-J116; the rank palette transitions pink to lime and uses a subtle text gradient. `title`/`short_title` stay plain for compatibility; `title_colored`/`short_title_colored` supply legacy RGB, and `title_mini` supplies MiniMessage. Existing `chat_prefix` automatically uses the coloured full title. Supplied TAB and nametag files explicitly use the renderer-specific placeholders, preserve LP order, display-name RGB and attached small mcMMO.

Green is an earlier completed level, yellow is the current level, red is a future locked level. The progress bottle remains focused on goal/percent/missing JP. The player head supplies personal money/playtime/skills/Jobs/fish/quests/perk stats. How to progress has six numbered sources in two rows plus the seventh Quest-completion source below, with explanation, personal stats and UTC limits. No server snapshot/TPS item remains.

Perk tiers are clickable Skeleton/Creeper/Blaze/Dragon/Wither head tabs, with a browsing-instruction book. The Blaze tab embeds the fixed skin returned by Mojang's MHF_Blaze profile, avoiding an asynchronous named-player lookup. Source: `https://sessionserver.mojang.com/session/minecraft/profile/4c38ed11596a4fd4ab1d26f386c1cbac`; skin hash `d06e342f90ec538aaa1552b224f264a040840902e126d91ece6139aa5b3c7cc3`.

A level increase is saved before a title/chat notification and `minecraft:ui.toast.challenge_complete` sound. The notification names the final reached level/title and counts new available rewards/perks. It also handles owner-added points; no false success feedback is sent after a failed save. Free rewards still need an explicit claim; the sound does not create an actual Minecraft advancement.

The Quests(8) screenshot layout retains all 17 category IDs and gameplay settings. Experience is the final category in YAML insertion order, placing it into free slot 40; slot 31 becomes filler. This also removes the malformed filler material that rendered as stone. The Journey shortcut stays at screenshot slot 51, with its access-gated next-tick `rebornadmin open {player}` bridge. Existing category slots other than Experience are retained.

## Receipts and owner review

Purchases save PREPARED before debit, DEBITED before permission writes. Overlapping actions are blocked; DEBITED recovery does not charge again. Ambiguous payments need owner review. Gifts persist original side effects and durable LP markers before delivery; uncertain effects/final-save failures are never automatically replayed. Economy, inventory and Towny do not share a transaction with the profile: a hard crash can require manual reconciliation.

Owner, player online: `rebornadmin status <player>`; `rebornadmin receipt <player> grant/cancel`; `rebornadmin gift <player> delivered`; `rebornadmin addpoints <player> <amount>`. Verify actual payment/delivery before resolving. Do not delete claims/receipts to retry. Rollback needs matching profile/permission/economy snapshots and historical-credit journals. Do not delete a completed journal to trigger another credit.

## Build and verification

Requires Java 21+, Paper, PlaceholderAPI, LuckPerms and Vault with an economy provider. Jobs, mcMMO and LMB Quests are optional. Quests uses a soft dependency for the completion-event hook; the Journey remains usable without it. No DeluxeMenus runtime dependency.

```text
python build.py --jdk <jdk-bin> --deps <dependency-directory> --output <output-jar>
python verify.py --jdk <jdk-bin> --deps <dependency-directory> --jar <output-jar> --output <verification-directory>
python package.py --jar <output-jar> --verification <verification-directory>
```

Core recovery scenarios run on both the preserved Beta.1 binary and this source build. Update checks cover actual menu inventory/actions, unchanged config files, old claims and failed/uncertain payments. Additional historical checks cover exact prior-point/legacy conversion, proof priority, real backup/journal files, failed writes, uncertain recovery, downgrade/revision replay protection and uploaded profile cases. Long-path checks cover one-time native/legacy migration, byte-identical backups, failed backup/save, paid recovery after reset, baseline persistence, UTC boundaries, pacing, unique renderable titles, notifications, Blaze skin and live-stat placement. Inventory registries, wallet/LP and client sound/title boundaries use controlled doubles. Live Paper/Minecraft verification is separate.

Report: `verification/JOURNEY-UPDATE-VERIFICATION.txt`. Historical release binaries/packages and their original checksums are retained. No player records or database exports are shipped.

## Beta.8 quest completion source and personal eGlow

The optional Quests hook reflects `PlayerFinishQuestEvent` and snapshots the public QuestProgress getters verified in [LMB Quests](https://github.com/LMBishop/Quests/tree/master/bukkit/src/main/java/com/leonardobishop/quests/bukkit/api/event). It awards actual successful completions rather than the falling `%quests_completed%` display count. There is no retrospective completion scan or aggregate award fallback. Quest IDs use stable SHA-256 keys to preserve YAML-safe per-quest completion timestamps. Points, daily cap and timestamp are saved atomically; duplicate/older events are skipped, repeat runs count, capped completions are acknowledged and storage errors block further profile writes until review. Async callbacks snapshot values before scheduling the award on the main thread.

Wither-page `eglow` unlocks at level 88 for $20,000. Its four grants are `eglow.user.*`, `eglow.color.*`, `eglow.blink.*`, `eglow.effect.*`; there are no eGlow admin grants or generic legacy purchase markers. Group permissions are not modified. Fly remains level 116 and the last Wither item.

## Durable Staff HQ audit (beta.9)

Store writes a sequence-bound audit receipt before the atomic profile commit and finalises it afterwards. Independent sequence counters survive profile writers that remove the new field. A failed profile commit leaves an uncommitted receipt; recovery finalises only the exact committed profile sequence. The connector acknowledges finalised audit files only after durably checkpointing every derived metric. Replayed metrics retain their IDs for the HQ permanent deduplication ledger. The collector does not change gameplay profiles, balances, keys, LP grants or migration journals.

All existing progress/credit revisions and the beta.8 catalogue remain unchanged. Current state snapshots include offline profiles; existing ownership, claim receipts and historical JP remain distinct from new activity. See STAFF-AUDIT-DE.md.
