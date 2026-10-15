# Direct-upload verification

The merged direct web-root ZIP retains the tested API 9 and Journey modules, with the active installation's PDF import/viewer restored. The supplied installation had API 7 at the root and the exact API 9 files inside WEBSPACE-UPDATE/. No connector code, database schema, game data, permissions or credentials changed.

27 targeted checks passed using real PHP 8.3 and localhost HTTP responses. The checker client JavaScript ran with the real HTTP probes in a Node DOM fixture. Checks cover:

- Exact API/core equality outside the five documented PDF restore/cache files, root archive layout and exclusion of production files.
- Exact preservation of the active .htaccess, PDF styles and media.php. The active index differs only by the added Journey route; the active bootstrap differs only by asset cache revisions and the two new module includes. All merged PHP files pass syntax checks.
- A complete installation without loading an intentionally invalid private config.
- File completeness, stale API on disk, missing game validator and missing/invalid manifest.
- Matching CSP nonce and absence of absolute host paths/private file data.
- Executed API 7 despite uploaded API 9; mixed GET/POST versions; missing Journey capability; HTTP 503 and non-JSON replies.
- Wrong upload subfolder and wrong domain. Probes use the connector's root `/api.php`, omit credentials and never probe another domain.
- Expected HTTP 401 for the anonymous POST. No token or staff action is sent.
- Correct ready state and the immediate `/trstaff reload` instruction.

The check is independent of database credentials and does not invalidate OPcache. Live hosting installation remains necessary. The supplied installation confirms the incorrect nested upload; the checker also supports diagnosing routing/cache issues after the files have been placed correctly. Private configuration, uploaded evidence and other storage were never copied into source control or the release.
