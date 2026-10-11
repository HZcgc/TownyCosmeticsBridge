# Staff HQ 5.1.1 validation — 11 October 2026

185 PHP/MariaDB integration checks passed, including the browser-suite gate; the Chromium suite passed 39 checks. PHP syntax checks cover 24 files.

New regressions verify:

- Tooltips track the pointer horizontally and vertically, move within a chart, stay inside its bottom/right edge, and retain keyboard access.
- The Staff guide is a normal document, can be edited/renamed through the existing action, renders escaped content and survives repeated visits without duplication or overwriting.
- Helper can read the guide but cannot edit it; document edits retain CSRF protection.
- The schema-9 to 10 update applies every known permission for all three roles, keeps a rollback snapshot and preserves unknown custom permissions.
- Admin and moderator capabilities follow the requested hierarchy; private messages remain unavailable to Helper, and staff-account management remains Owner-only.
- Later Owner changes survive subsequent requests and migration retries.

Existing authentication, profile/evidence, case, native-statistics, replay, theme, phone-layout and notification regressions also pass. Tests use isolated synthetic records and a mocked Discord transport. No real Discord messages were sent, and the live webhost/Minecraft server was not changed. Connector 1.4.0 and Dragon Build 33 are unchanged.

Relevant test sources: tests/guide_role_checks.py, tests/browser_tests.cjs and the existing live/workspace/analytics/experience suites. GitHub Actions additionally checks PHP syntax, the history parser, connector tests/build and generation of the webspace ZIP.
