# Towny Reborn Journey 2.0.0

- Stable release based on the deployed Beta 9 source and gameplay catalogue.
- All 116 TAB labels now use the level number and a distinct three-letter title code, for example `[1NEW]`, `[15GAR]`, `[88RAD]` and `[116RLG]`. Full chat and overhead Journey titles keep their names. The pink-to-lime gradient, LP order, display name and attached mcMMO remain intact.
- Menu clicks cancel item movement immediately. Navigation, rewards and purchase actions execute on the next server tick, at most once per player per tick. Closing the menu, disconnecting, changing its actions or losing access invalidates an old click. Number keys, drop, middle-click and offhand swaps do not activate buttons.
- Failed activity, polling or owner point-save operations block subsequent awards and purchases until storage succeeds; success feedback is emitted only after a committed save.
- A failed owner receipt review restores the original payment phase. It cannot turn an unsaved PREPARED decision into automatic DEBITED recovery.
- A UI feedback exception cannot reopen a successfully saved purchase. Actual payments and original receipt amounts remain authoritative.
- Missing curve settings now default to the current 150/30 curve rather than the obsolete Beta 1 curve. Explicit configured values remain supported.

No JP reset, additional historical credit, new payment or replay of delivered rewards is part of this update. Both migration revisions, all gates, prices, source budgets, Quests JP and personal eGlow are unchanged. Existing Staff Connector 1.5.1 and Staff HQ API 9 continue to consume the same durable audit format.

`TownyReborn-Journey-2.0.0-Update.zip` is the small overlay for an existing Beta 9 installation. It contains only the new Progression JAR, its explicit config and documentation. The existing TAB format already reads the same `%reborn_short_title_colored%` placeholder, so it picks up the new codes without replacing TAB, Quests or nametag configs.

`LEVEL-CODES.md` lists every label. `verification/JOURNEY-UPDATE-VERIFICATION.txt` records the completed checks and their controlled-test scope. Actual player data and uploaded archives are excluded from the release.

Menu scheduling follows the [Bukkit InventoryClickEvent contract](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/inventory/InventoryClickEvent.html).
