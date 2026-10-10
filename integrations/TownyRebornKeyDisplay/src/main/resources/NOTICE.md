Compatibility replacement for ExcellentCratesAddon / ecrates-placeholder v2.1
by Cyvertray_. The upstream project is licensed MIT:
https://modrinth.com/plugin/excellentcrates-virtual-key-placeholder

This release replaces EC6 reflection with the public EC7 service API. The
existing ec_keyz_ and ec_physicalkeyz_ placeholders retain their meanings.
The new ec_totalkeys_<comma-separated-key-ids> placeholder sums virtual keys
without number formatting or a Math expansion. No key balance is changed.
