# Direct-upload verification

The direct web-root ZIP contains byte-for-byte identical API/application/assets from the previously tested HQ 5.2.1/API 9 release. No connector code, database schema, game data, permissions or credentials changed.

21 additional isolated checks passed using real PHP 8.3 and localhost HTTP responses. The checker client JavaScript ran with the real HTTP probes in a Node DOM fixture. Checks cover:

- Exact core-file equality, root archive layout and exclusion of production files.
- A complete installation without loading an intentionally invalid private config.
- File completeness, stale API on disk, missing game validator and missing/invalid manifest.
- Matching CSP nonce and absence of absolute host paths/private file data.
- Executed API 7 despite uploaded API 9; mixed GET/POST versions; missing Journey capability; HTTP 503 and non-JSON replies.
- Wrong upload subfolder and wrong domain. Probes use the connector's root `/api.php`, omit credentials and never probe another domain.
- Expected HTTP 401 for the anonymous POST. No token or staff action is sent.
- Correct ready state and the immediate `/trstaff reload` instruction.

The check is independent of database credentials and does not invalidate OPcache. Live hosting installation remains necessary. The attached server log proves API 7 was served to connector 1.5.1; it does not reveal whether upload location, skipped files, routing or PHP cache caused it.
