# mcMMO Standard migration

Offline ZIP converter for the reviewed Towny Reborn mcMMO 2.3.001 build.
It leaves the input and all live server files untouched and creates a separate
package containing installation files, exact rollback bytes, a full original
archive and a per-skill XP comparison. Requires Java 17 or newer.

```sh
./build.sh
java -jar build/TownyReborn-mcMMO-Standard-Converter.jar mcMMO.zip mcMMO.jar new-package.zip
java -jar build/TownyReborn-mcMMO-Standard-Converter.jar --verify new-package.zip fresh-mcMMO.zip
```

Without arguments the executable JAR opens desktop file selectors. Exports must
be taken after a clean server stop, and the server must remain stopped until the
four installation files are applied together. The converter cannot inspect a
remote server's process state. Read `resources/README-DE.txt` for the rollout,
verification, rollback and limits.

The native Standard formula sums ten terms starting at `10 * level + 1`.
Changing the linear base from 10200 to 10000 compensates that offset, so one
Standard level costs precisely the same XP as its ten original Retro levels.
Stored residual levels and XP are carried into the new ten-level block; total
reconstructed XP is unchanged. XP gain modifiers and diminishing returns are
unchanged. Whole-level skill displays and derived child levels become coarser.

The converter changes `config.yml`, `experience.yml`, `skillranks.yml` and
`flatfile/mcmmo.users`. Basic ranks that require level 1 in both modes start at
Standard 0 to retain existing initial unlocks, also making them available to new
players immediately. Higher requirements are unchanged. It generates no
level-up events, purchases or rewards. It does not alter other plugins or their
player data. Production rejoin/restart validation is still required.

Safety guards reject unsupported JAR hashes, another XP curve, SQL storage,
custom caps, enabled level-up command rewards, malformed or duplicate player
rows, unsafe ZIP paths, precision loss and previously migrated data. It also
checks every configured rank for every profile, including native child-skill
averages, and aborts if rounding would relock a previously unlocked rank.

Only synthetic test data belongs in this repository. Never commit exported
player databases, migration output, reports, plugin credentials or proprietary
plugin JARs.
