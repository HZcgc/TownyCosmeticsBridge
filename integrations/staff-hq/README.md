# Towny Reborn Staff HQ 5.1.0

PHP/MySQL staff portal with Paper connector 1.4.0. The portal is in English. Version 5.1 is a webspace-only interface update for HQ 5.0: schema 9, API 7 and the connector stay compatible. Keep the existing production configuration, database and storage.

Features:
- Player dossiers: notes, watch instructions, permanent message pins, profile attachments and source-linked cases.
- Owner-only account creation; granular profile/private-message/statistics permissions; readable role matrix and LuckPerms group snapshots.
- Durable, immediate dupe/exploit chat notifications through the configured Freddie Discord bot, with grouping and repeat-safe delivery.
- Permanent event statistics: crate batches/rewards/catalogs, voting, QuickShop transactions/catalogs, MCPets summons, pet shop purchases, Dragon results and supported PyroFishing/Piñata events.
- Direct sidebar destinations and dashboard cards for Crates, Dragon, Voting, Piñata, Fishing, QuickShop and MCPets.
- Compact staff priorities with player search, clickable cases and alerts; detailed rankings, catalogues and evidence expand within each system.
- Dark, a plum-and-rose Cherry Blossom theme with Sakura illustrations, and Clear Light; larger text, mobile table cards and grouped diagnostics.
- Built-in staff field guide and repeat-safe historical crate import.

## Layout and build

`source/web` is the webspace update. `source/connector` builds with JDK 25 and Maven: `mvn -f source/connector/pom.xml clean verify`. Only plugin classes/resources enter the JAR; compile-only LiteBans signatures are excluded. The current Dragon implementation is maintained separately in [TownyDragonEvent](https://github.com/HZcgc/TownyDragonEvent).

The connector never modifies crate keys, balances, pet grants or LuckPerms nodes. Website permissions and Minecraft permissions remain separate. Unsupported optional event APIs are reported as missing coverage rather than invented activity.

Production credentials, Discord tokens, uploaded evidence, user database records and private logs are deliberately excluded from this repository. See [the 5.1 update guide](docs/INSTALL-5.1-DE.md), [UI changes](docs/UI-CHANGES-5.1.md), [staff guide](docs/STAFF-GUIDE.md) and [5.1 validation](docs/VALIDATION-5.1.md). The earlier [5.0 installation guide](docs/INSTALL-DE.md) and [backend validation](docs/VALIDATION.md) document the existing connector and Dragon update.

Build the webspace-only ZIP with `python3 tools/package_web.py`. The package excludes production configuration, storage, JARs and test fixtures.
