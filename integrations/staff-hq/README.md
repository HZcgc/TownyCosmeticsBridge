# Towny Reborn Staff HQ 5.0.0

PHP/MySQL staff portal with Paper connector 1.4.0. The portal is in English. Keep the existing production configuration and storage; automatic schema 9 migrations add the new tables without removing cases, accounts or role rules.

Features:
- Player dossiers: notes, watch instructions, permanent message pins, profile attachments and source-linked cases.
- Owner-only account creation; granular profile/private-message/statistics permissions; readable role matrix and LuckPerms group snapshots.
- Durable, immediate dupe/exploit chat notifications through the configured Freddie Discord bot, with grouping and repeat-safe delivery.
- Permanent event statistics: crate batches/rewards/catalogs, voting, QuickShop transactions/catalogs, MCPets summons, pet shop purchases, Dragon results and supported PyroFishing/Piñata events.
- Dark, Cherry Blossom and Clear Light themes, larger text, mobile table cards and grouped diagnostics.
- Built-in staff field guide and repeat-safe historical crate import.

## Layout and build

`source/web` is the webspace update. `source/connector` builds with JDK 25 and Maven: `mvn -f source/connector/pom.xml clean verify`. Only plugin classes/resources enter the JAR; compile-only LiteBans signatures are excluded. The current Dragon implementation is maintained separately in [TownyDragonEvent](https://github.com/HZcgc/TownyDragonEvent).

The connector never modifies crate keys, balances, pet grants or LuckPerms nodes. Website permissions and Minecraft permissions remain separate. Unsupported optional event APIs are reported as missing coverage rather than invented activity.

Production credentials, Discord tokens, uploaded evidence, user database records and private logs are deliberately excluded from this repository. See [INSTALL-DE.md](docs/INSTALL-DE.md), [STAFF-GUIDE.md](docs/STAFF-GUIDE.md) and [VALIDATION.md](docs/VALIDATION.md).
