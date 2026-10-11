# Validation — 11 October 2026

Locally verified against the supplied source/configuration/logs:

| Check | Result |
| --- | --- |
| Real PHP 8.3 / MariaDB 10.11 integration regressions | 142 passed |
| Chromium UI checks (themes, preference persistence, 375px layouts, JavaScript) | 12 passed |
| Connector Java/API/outbox/history tests | 7 passed, including the actual ExcellentCrates 7.1.0 JAR |
| Dragon filesystem, recovery and four-display tests | 14 passed |
| Historical log converter tests | 5 passed |
| PHP syntax | 22 files passed |
| Production Java compilation | Connector release 17; Dragon release 25 against real Paper 26.2 APIs |
| Supplied crate history | 937 batches, 1,095 openings/rewards; no unparsed records |

The PHP integration suite covers authentication, CSRF, explicit role denials, Owner-only staff creation, raw-log expiry, permanent profile pins, private evidence redaction, profile uploads/media authorization, repeat-safe case creation, native statistics replay, snapshot freshness/catalog shrink, historical CLI import and local notification transport. Discord was mocked; no real Discord message was sent during validation.

Dragon tests use actual Paper interfaces with isolated world, scheduler and display doubles. They verify four cardinal displays at 5.5 blocks beyond a 3-block portal edge, configurable height/distance, lobby countdown, marked-only deletion, unmarked legacy recovery to IDLE, template protection and symlink guards. This is not a completed live fight on the Minecraft server.

The server/client plugins and hosting were not deployed or modified live. The uploaded Paper 133 bootstrap requires a separate Mojang server download; a full Paper instance was not launched here. Verify a start/stop/start cycle and native optional-plugin coverage after installation. Public CI builds validate reproducibility separately.

Detailed synthetic checks: `tests/live_tests.py`, `tests/workspace_checks.py`, `tests/analytics_tests.py`, `tests/browser_tests.cjs`, `tests/history_tests.py` and each Java module's `src/test/java`. The local live harness expects the supplied isolated runtime paths; it never uses production credentials.
