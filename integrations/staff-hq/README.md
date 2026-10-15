# Towny Reborn Staff HQ 5.2.1

English PHP/MySQL staff portal with Paper connector 1.5.1 and durable Journey beta.9 integration. HQ 5.2.1 uses API 9 and additive schema 11. Journey progress, retained unlocks, actual debits and reward evidence are visible directly in the dashboard and player profiles. A priority heartbeat separates server liveness from delayed HQ uploads. Keep existing production configuration, database, storage, migration journals and connector queues.

Earlier pointer-following charts, document-backed staff guidance, themes and once-only role presets remain available. Existing role customisations are preserved by the 5.2.0 migration.

Features:
- Player dossiers: notes, watch instructions, permanent message pins, profile attachments and source-linked cases.
- Owner-only account creation; granular profile/private-message/statistics permissions; readable role matrix and LuckPerms group snapshots.
- Durable, immediate dupe/exploit chat notifications through the configured Freddie Discord bot, with grouping and repeat-safe delivery.
- Permanent event statistics: crate batches/rewards/catalogs, voting, QuickShop transactions/catalogs, MCPets summons, pet shop purchases, Dragon results and supported PyroFishing/Piñata events.
- Direct sidebar destinations and dashboard cards for Reborn Journey, Crates, Dragon, Voting, Piñata, Fishing, QuickShop and MCPets.
- Compact staff priorities with player search, clickable cases and alerts; detailed rankings, catalogues and evidence expand within each system.
- Dark, a plum-and-rose Cherry Blossom theme with Sakura illustrations, and Clear Light; larger text, mobile table cards and grouped diagnostics.
- Built-in staff field guide and repeat-safe historical crate import.

## Layout and build

`source/web` is the webspace update. `source/connector` builds with JDK 25 and Maven: `mvn -f source/connector/pom.xml clean verify`. Only plugin classes/resources enter the JAR; compile-only LiteBans signatures are excluded. The current Dragon implementation is maintained separately in [TownyDragonEvent](https://github.com/HZcgc/TownyDragonEvent).

The connector never modifies crate keys, balances, pet grants or LuckPerms nodes. Website permissions and Minecraft permissions remain separate. Unsupported optional event APIs are reported as missing coverage rather than invented activity.

Production credentials, Discord tokens, uploaded evidence, user database records and private logs are deliberately excluded from this repository. See [the 5.1.1 update guide](docs/INSTALL-5.1.1-DE.md), [UI changes](docs/UI-CHANGES-5.1.1.md), [staff guide](docs/STAFF-GUIDE.md) and [5.1.1 validation](docs/VALIDATION-5.1.1.md). The earlier [5.0 installation guide](docs/INSTALL-DE.md) and [backend validation](docs/VALIDATION.md) document the existing connector and Dragon update.

Build the webspace-only ZIP with `python3 tools/package_web.py`. The package excludes production configuration, storage, JARs and test fixtures.

The requested Helper/Moderator/Admin defaults are applied once during upgrade; later Owner edits survive. Staff account management is always Owner-only. See [all permissions](docs/ROLE-PERMISSIONS.md).

## 5.2.0: Journey and API health

Adds direct Journey navigation, profile snapshots, source JP, historical credit, purchase eligibility, confirmed payments, reward command receipts and replay-safe aggregates. Connector 1.5.0 consumes Journey beta.9 durable audit receipts. Existing journals and permanent purchases remain authoritative. A small priority heartbeat distinguishes live game-server reports from delayed event delivery. HTTP failure warnings use a stability window rather than restarting on every short successful response. See docs/INSTALL-5.2.0-DE.md.


## 5.2.1: HTTP 422 compatibility and queue capacity

The executed PHP release and real Journey capability are included in JSON, even
when a host strips custom headers. Connector 1.5.1 identifies the exact rejected
event instead of treating any Journey entry in a mixed batch as an old API.
Unknown or unsupported Journey snapshots remain queued; actual invalid entries
are individually archived only after a matching validation response. Old config
limits of 5,000 are raised to an effective minimum of 50,000 without rewriting
tokens or configuration. Higher configured limits up to 500,000 are supported;
existing larger queues are never truncated. See docs/INSTALL-5.2.1-DE.md.
