TOWNY REBORN STAFF HQ 5.0.0 — UPDATE
===================================

Additive schema 9; API version 7; Paper connector 1.4.0.
Existing API event types and configuration remain supported.

Player dossiers now support notes/watch instructions, permanent chat pins,
profile attachments without cases, and source-linked case creation.
Granular profile, private-message and statistics permissions are available.
Only the Owner can create staff accounts and change role permissions.

New native event statistics cover crate batches/rewards/catalogs, votes,
QuickShop, MCPets/pet shops, Dragon and available Fishing/Piñata APIs.
A missing hook is reported as unavailable coverage.

Fresh dupe/exploit matches enter the existing Freddie notification outbox
immediately; the shared one-message-per-minute limit still applies.
Historic chat replay does not create new emergency notifications.

Appearance offers Dark, Cherry Blossom, Clear Light and larger text.
Mobile tables become labelled cards; repeated diagnostics are expandable.
The built-in Staff field guide explains the workflow.

UPLOAD AS AN UPDATE: preserve config/config.php, config/notifications.php,
all storage/ content and the database. No live deployment was performed.
Full installation steps are in INSTALL-DE.md supplied with the update.
