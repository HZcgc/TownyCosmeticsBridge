# Validation: Staff HQ 5.2.0 / Connector 1.5.0 / Journey beta.9

Journey: 210 controlled recovery, migration, historical-credit, ownership, GUI, quest/eGlow and durable-audit checks passed. The unchanged beta.1 reference also passes its independent 24 core checks.
Connector: 25 JUnit tests passed; no skipped native checks. Tests include the actual EC 7 API, persisted outbox recovery, exact payment receipt values, pending delivery, historical ownership/claim distinction, all 116 thresholds, future configured levels, HTTP 500 with successful priority liveness, and older-HQ compatibility retention.
HQ: 259 PHP/MariaDB integration checks passed. A real Journey Store produced 56 connector metric receipts accepted by the real API. Replaying every receipt leaves JP, purchase counts, spending and rewards unchanged. A deliberately broken ordinary ingest transaction returns a safe error reference while the priority heartbeat is still accepted.
Browser: 43 full-browser checks passed, including themes, pointer-following charts, keyboard access and mobile pages. Five further checks verify the final grouped Journey evidence: all 54 visible evidence entries preserved in six saved-change groups, no false historical evidence for a fresh profile, saved reward commands accessible, expanded evidence fitting 375px, no JavaScript errors.
PHP syntax checked with the bundled PHP 8.3 runtime. Java builds with JDK 25, connector target 17 and Journey target 21; JVM checks use -Xverify:all.

Controlled fixtures only; no production webhook, token, database or Minecraft server was contacted. This is not a live Minecraft/client deployment test. Existing production configs and data are excluded from release archives.

## CONNECTOR

```text

Test run finished after 3859 ms
[         6 containers found      ]
[         0 containers skipped    ]
[         6 containers started    ]
[         0 containers aborted    ]
[         6 containers successful ]
[         0 containers failed     ]
[        25 tests found           ]
[         0 tests skipped         ]
[        25 tests started         ]
[         0 tests aborted         ]
[        25 tests successful      ]
[         0 tests failed          ]

Oct 14, 2026 10:57:24 PM com.townyreborn.staffconnector.InsightsOutbox save
WARNING: Could not checkpoint game insights: FileSystemException
Oct 14, 2026 10:57:24 PM com.townyreborn.staffconnector.InsightsOutbox save
WARNING: Could not checkpoint game insights: FileSystemException
Oct 14, 2026 10:57:26 PM com.townyreborn.staffconnector.ConnectionWarnings failed
WARNING: Staff Portal delivery returned HTTP 500; portal error (HTTP 500); queued events retained, retrying in 60s. Game-server status is reported separately.
Oct 14, 2026 10:57:26 PM com.townyreborn.staffconnector.ConnectionWarnings failed
WARNING: Staff Portal delivery returned HTTP 422; Journey telemetry needs Staff HQ API 8; install the website update first; queue retained; queued events retained, retrying in 300s. Game-server status is reported separately.
Oct 14, 2026 10:57:26 PM com.townyreborn.staffconnector.InsightsOutbox save
WARNING: Could not checkpoint game insights: FileSystemException
Oct 14, 2026 10:57:26 PM com.townyreborn.staffconnector.InsightsOutbox save
WARNING: Could not checkpoint game insights: FileSystemException
```

## GROUPED UI

```text
PASS 54 evidence entries retained in 6 committed-change groups
PASS empty historical evidence is omitted
PASS saved reward command is accessible inside grouped evidence
PASS expanded Journey evidence fits a 375px screen
PASS no Journey browser JavaScript errors
```
