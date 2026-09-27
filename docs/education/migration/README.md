# Migration rehearsal and reconciliation

This is a **read-only** reconciliation tool, not an importer. No real customer data has been migrated. Use Python 3.10+; no third-party dependencies are needed.

## Inputs and stable mapping

Prepare two local JSON manifests: `expected.json` is the reviewed source-to-target mapping; `actual.json` is a consistent export from the isolated target database. Both contain arrays named `customers`, `contacts`, `students`, `guardians`, `enrollments`, `payments`, and `ledger`. Required fields are declared in `reconcile.py`. Use snake_case database field names, stable string IDs and decimal strings with the same scale in both exports. Do not include names, phone numbers or message content unless a private reconciliation explicitly needs them. Never commit manifests from real business data.

Map existing customers and contacts by their verified CRM IDs. Assign each legacy learner, course purchase and event a permanent mapping ID; review ambiguous records manually. Matching by name, phone suffix or order row position is forbidden. Orders must be created through native Cordys workflows; capture the persisted `bizId` for each product row. Do not turn unverified historical payments into receipts, or turn remaining balances into invented attendance.

```sh
python3 docs/education/migration/reconcile.py expected.json actual.json --report report.json
python3 -m unittest discover -s docs/education/migration -p 'test_*.py'
```

The report contains counts, input hashes, mismatch categories and additional-record counts. Exit code 0 means the supplied mapping is present and the checked relationships/balances are valid. Exit code 1 means reconciliation failed. The report deliberately excludes record values. Input manifests are never modified. Extra target rows are validated and reported, but never deleted: they may represent post-cutover activity. A passing report does **not** establish that omitted source records were migrated; compare source extraction counts and totals independently.

Checks cover duplicate stable IDs, missing/cross-organization family relationships, mode/units, payment allocation ceilings, immutable receipt totals, ledger signs, duplicate operation keys and negative balances. Every supplied expected field must match its actual counterpart. Amounts, product snapshots, seller attribution and source mapping IDs can be supplied as additional fields for exact comparison.

## Cutover runbook

1. Obtain a consistent backup and record schema version, source counts, amounts, balances and mapping version. Test restoration into an isolated instance before cutover.
2. Apply version 1.9.4 migrations in order with Flyway. Re-running Flyway must report no pending migrations; do not manually replay DDL or edit applied scripts.
3. Rehearse on synthetic or approved minimized data. Reconcile before and after, review every ambiguous relationship and compare receipt allocations/ledger balances per enrollment, not only organization totals.
4. For real cutover, suspend writes during extraction/import, apply the reviewed mapping through audited application operations, and compare a consistent target export. Preserve request keys and mapping IDs so retries cannot duplicate operations. Historical opening balances require a separately reviewed importer; the current application does not expose a bulk balance override.
5. Resume writes only after the business owner accepts the reconciliation. Record the cutover timestamp and retain immutable source manifests privately.
6. If an issue appears after writes resume, disable the affected entry point and fix forward. Reverse wrong attendance with the correction API, preserving ledger history. Never restore the old backup over new business activity, delete EDU tables, or disable foreign keys to force a rollback. A database restore requires a separate downtime and post-cutover replay plan.

## Scope of verified migration

The automated MySQL 8 integration suite applies the full baseline plus all seven new migrations to a fresh database and checks native mutation protections. Production-size migration timing, backup restoration, historical source mapping and real-data reconciliation still require the deployment owner's environment and approved inputs. Those are release gates, not implied by passing synthetic tests.
