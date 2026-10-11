# Staff HQ 5.1 validation — 11 October 2026

| Check | Result |
| --- | --- |
| PHP 8.3 / MariaDB 10.11 integration checks | 172 passed, including the browser-suite gate |
| Chromium checks within that gate | 35 passed |
| PHP syntax | 23 PHP files passed |
| Desktop visual inspection | Cherry Dashboard, Dragon and Metric explorer; Clear Light Dashboard |
| Phone layout checks | 16 destinations at 375px without page-level horizontal overflow |

The new integration checks verify direct destinations and cards, active navigation, default Game overview, legacy statistics URLs, missing-data labels, guarded Helper access, escaped catalogue evidence, 24-entry pagination and native chart scopes. Dragon lifetime damage is shown separately and excluded from native period totals. The existing authentication, CSRF, profile, evidence, case, native replay and notification regressions still pass.

Browser checks cover saved theme/text preferences, plum Sakura colours, flower illustrations, toolbar and chart palettes, a primary-button contrast ratio of at least 4.5:1, mobile menu state and Escape dismissal, wrapped message cards and absence of JavaScript errors.

Validation used an isolated local database with synthetic records. Discord transport was mocked; no real Discord messages were sent. Live hosting and the Minecraft server were not changed. Connector 1.4.0 and Dragon Build 33 are unchanged by this update; their earlier validation is documented in VALIDATION.md.

Reproducible check sources: tests/live_tests.py, tests/experience_checks.py, tests/browser_tests.cjs and the existing workspace/analytics suites. The local integration harness requires the isolated runtime paths recorded in its configuration. GitHub Actions performs PHP syntax, history-parser and connector-build checks separately.
