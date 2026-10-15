TOWNY REBORN STAFF API
======================

Endpoint
--------
POST https://staff.townyreborn.com/api.php
Authorization: Bearer YOUR_API_TOKEN
Content-Type: application/json

The installer shows the initial API token once. Store it like a password. An
Owner or Administrator can rotate it later under System. Rotation invalidates
the old token immediately.

Health check
------------
GET https://staff.townyreborn.com/api.php

Supported events
----------------
- player.update
- player.join
- player.quit
- anticheat.alert
- punishment.created
- staff.action

Every event may include:
- event_id: unique ID for safe retry/deduplication
- occurred_at: ISO-8601 timestamp
- server: source server such as smp, hub or proxy

Batch mode (recommended for the SMP connector)
----------------------------------------------
Version 2 of the endpoint accepts up to 25 events in one request. This keeps
PHP/MySQL usage low on shared hosting. The TownyRebornStaffConnector 1.0.2 uses
this automatically; do not send one web request per Vulcan flag.

{
  "events": [
    {"type":"staff.action","event_id":"example-1","staff":"Console","action":"connector.test"},
    {"type":"player.quit","event_id":"example-2","uuid":"00000000-0000-0000-0000-000000000000","username":"ExamplePlayer"}
  ]
}

Example: Vulcan alert
---------------------
curl -X POST "https://staff.townyreborn.com/api.php" \
  -H "Authorization: Bearer YOUR_API_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
    "type": "anticheat.alert",
    "event_id": "smp-vulcan-10442",
    "occurred_at": "2026-09-28T14:30:00+02:00",
    "server": "smp",
    "uuid": "00000000-0000-0000-0000-000000000000",
    "username": "ExamplePlayer",
    "check": "Speed Type A",
    "vl": 8,
    "severity": "high",
    "message": "Vulcan alert received",
    "evidence": {"ping": 42, "tps": 20.0}
  }'

Example: player joins
---------------------
curl -X POST "https://staff.townyreborn.com/api.php" \
  -H "Authorization: Bearer YOUR_API_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
    "type": "player.join",
    "event_id": "smp-join-0001",
    "server": "smp",
    "uuid": "00000000-0000-0000-0000-000000000000",
    "username": "ExamplePlayer",
    "platform": "java",
    "online": true,
    "playtime_seconds": 1200,
    "balance": 3500.50,
    "town": "SpawnTown"
  }'

Example: punishment made by staff
---------------------------------
curl -X POST "https://staff.townyreborn.com/api.php" \
  -H "Authorization: Bearer YOUR_API_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
    "type": "punishment.created",
    "event_id": "smp-punishment-501",
    "server": "smp",
    "uuid": "00000000-0000-0000-0000-000000000000",
    "username": "ExamplePlayer",
    "punishment_type": "tempban",
    "reason": "Example reason",
    "duration_seconds": 86400,
    "source": "LiteBans",
    "external_id": "501",
    "issued_by": "StaffMinecraftName"
  }'

The Staff Team page can link that Minecraft name to a portal account. Matching
punishments and staff.action events then display the staff skin and identity.

Analytics session history
-------------------------
Each player.join opens a session and player.quit closes it. This powers unique
join charts and average-session statistics. Keep the SMP connector online so
session and retention data becomes more accurate over time.

Responses
---------
- 202: event accepted
- 202 with accepted_count: event batch accepted
- 200 with duplicate=true: event was already received
- 401: token missing or invalid
- 413: payload is larger than 1 MiB
- 422: event type or data is invalid
- 500/503: temporary processing or database problem

Do not put the token in a public repository, screenshot, Discord message or
client-side resource pack. It belongs only in protected server configuration.

EXTERNAL HEALTH SCHEDULER (HQ 4.2.1)
The optional notification-cron.php endpoint uses HTTPS POST and X-TR-Cron-Key.
Enable and manage the encrypted credential in Attention Centre as an administrator.
It is separate from connector API authentication and does not need a browser session.
An empty request body is sufficient. Successful calls return HTTP 200 with ok:true;
invalid/disabled credentials return 404, wrong method 405, and worker failure 503.
Do not put the credential in URL parameters. The CLI worker remains unchanged.

Staff HQ 5 additions (API 7)
---------------------------
game.event: stable event_id, plugin, metric, dimension, optional player UUID/name,
finite value, sample count, details and occurred_at. Permanent game-event receipts
prevent replay from duplicating rankings after raw activity expiry.
integration.snapshot: plugin, snapshot_key, data and occurred_at. Current catalog
chunks and manifests remove stale catalog entries; older snapshots do not replace
newer data. These events are read-only server observations, never grants.


API 8 / HQ 5.2.0: Journey and priority heartbeat
------------------------------------------------
POST connector.heartbeat as an operation, authenticated with the existing connector token. Fields: server, occurred_at (capture timestamp), online_players, optional tps/mspt/uptime_seconds and bounded metadata. This operation is independent of ordinary event batches. Old captures cannot overwrite newer liveness; an old capture remains stale even when delivered now. It does not acknowledge or discard event queues.

TownyRebornProgression is an accepted integration.snapshot source. Its catalog and player.<UUID> snapshots are current state. Stable game.event receipts distinguish source JP, historical restoration, level changes, purchase unlocks, ownership, confirmed debits, delivered purchases and reward claims. Do not add snapshot totals to new-event counters. The existing event ledger deduplicates replayed receipts permanently.

HTTP 500/502 means HQ/API delivery failed, not proof of an offline Minecraft server. Missing liveness is shown as Status unknown. Heartbeat/connector incidents have one notification per sustained incident and a recovery after two healthy minutes. New safe error references correlate API processing/storage faults with the private PHP log without disclosing SQL content or tokens.


API 9 / HQ 5.2.1: queue and deployment compatibility hotfix
Every JSON response now includes api_version, release and capabilities, including
HTTP 401/422/500. Header X-TR-API-Version remains available but is not required by
the connector. GET api.php returns version=9, release=5.2.1 and journey=true only
when the loaded gameSnapshotPlugins() whitelist actually supports Journey. A
partial or stale PHP deployment is reported as journey=false. This does not
change token validation, accepted event limits, stable IDs or game ownership.
Connector 1.5.1 retains unknown/unsupported Journey snapshots, checks the exact
identified failing event, and can use the real GET health response for API 8
hosts stripping custom headers. Existing retry queues are not cleared.
