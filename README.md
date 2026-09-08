# TownyCosmeticsBridge

TownyReborn bridge between ExcellentCrates and UltraCosmetics. The console command
`townycosmeticsreward <player>` chooses a still-locked cosmetic for that
player, unlocks it through the UltraCosmetics profile API, and updates the
ExcellentCrates reward display. A completed collection awards the configured
Patron key; an internal error refunds the cosmetic key.

## Runtime

- Paper 26.2 / Java 25
- UltraCosmetics
- ExcellentCrates
- Plugin version represented here: `1.2.1`

## Source recovery note

The original source project was no longer present. This Java source was
recovered from the deployed `TownyCosmeticsBridge.jar` with Vineflower 1.12.0.
Control flow and integrations were preserved, then a reproducible Maven build
against Paper 26.2 was added for the TownyReborn 1.2.1 release.

Deployed JAR SHA-256:

```text
b767601bda2d91e1eeab0a904695e3c9890d2d83f82564b378ed0d1b97174c5a
```

The authoritative plugin metadata is in
`src/main/resources/plugin.yml`.
