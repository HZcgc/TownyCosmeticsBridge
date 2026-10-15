# Connector 1.5.1 / Staff HQ 5.2.1 verification

- Native Java compilation with JDK 25, connector class files targeting Java 17, JVM verification enabled.
- 35 connector tests passed, including 11 new compatibility/queue regressions. One existing optional ExcellentCrates/nightcore linkage test was not run because those third-party JARs were not supplied to this build. The unchanged GameInsights, CrateHistoryReader, TelemetryBuffer and JourneyInsights class bytes match release 1.5.0.
- A real 50,000-entry retry queue was saved and reopened. Original event IDs/order survived; an existing 50,001-entry queue was not truncated. Legacy 5,000 limits become 50,000; higher configuration values remain effective within the documented bounds.
- Real localhost HTTP tests cover stripped/stale version headers, JSON API versions, legacy API 8 health detection, mixed Journey/non-Journey batches, actual old APIs, partial website capability reports, unidentified rejections and valid Journey snapshots rejected by a stale validator.
- 265 real PHP 8.3/MariaDB integration checks passed. All PHP source files also passed syntax checks. Tests include the new API 9 response fields, real missing Journey capability during a partial deployment, restoration of the complete deployment, token rejection, exact validation identities and transactional rollback.
- The unchanged beta.9 Journey Store generated 56 audit metric receipts, parsed through the actual connector and ingested twice each. JP, debits, ownership and rewards remained deduplicated.
- 44 browser checks passed, including existing navigation/themes, charts, 375px layouts, keyboard navigation and no JavaScript errors.
- ZIP contents are explicit overlays with SHA-256 manifests. Production configuration, tokens, accounts, uploaded evidence, databases, queues and player data are excluded.

These are isolated development checks. The screenshot does not expose the live server's actual validation response or executed PHP files. Live installation and the public API 9 check described in INSTALL-DE.md must be performed on the existing webspace/server. No production deployment, token rotation, queue clearing or game progress reset was performed.
