# EDU phase one: long-term AI courses and single-session activities

Status: implementation delivered for review; real-data cutover and external delivery integration remain release gates.
Baseline: 5d30ee74d92c50baf7e1ff64fc553923323fb508.

## Objective

Run consultation → trial → sale → enrollment → scheduling → attendance/entitlement consumption → teacher feedback → renewal inside Cordys. Reuse customer ownership/pools, products, orders, authentication and permissions. All fixtures must be synthetic.

## Current evidence (2026-09-27)

- Fresh MySQL 8 baseline migration plus all seven 1.9.4 migrations completed in Testcontainers. Full backend suite ran 774 tests; all EDU tests passed, with three existing mail-settings/person-center failures because test SMTP settings are rejected by the current SSRF validation (one follow-on null dereference after the rejected write).
- Web and mobile type checks and production builds passed. ESLint and Stylelint passed for new code (existing mobile-store warnings are baseline warnings).
- Synthetic browser rehearsal used the existing customer detail UI on desktop/mobile and reached enrollment, receipt allocation, handoff, class membership, scheduling, attendance, entitlement debit and teacher-confirmed feedback. Screenshots are in `screenshots/`.
- The migration manifest reconciler passed five unit cases. It does not import production records. A deployment backup/restore rehearsal and owner-approved real-data reconciliation remain pending.
- Provider-backed parent-message delivery is not configured. Manual delivery evidence records `UNVERIFIED`; copying and teacher confirmation do not imply sending or receipt.

## Acceptance gates

- [x] Consultation uses existing customers, contacts, ownership, pools and follow-up records.
- [x] A family supports multiple guardians and children; a child supports multiple enrollments.
- [x] Trial booking records attendance and outcome without inventing a paid enrollment.
- [x] Sale creates or references a real Cordys order and immutable product-row snapshot.
- [x] Enrollment and teaching handoff are transactional and idempotent.
- [x] Scheduling detects teacher, room and student conflicts, including concurrent requests.
- [x] HOURS consumption and EVENT participation have separate completion semantics.
- [x] Duplicate attendance submission does not double-consume; corrections use reversal entries.
- [x] Leave/makeup handling does not charge the same entitlement twice.
- [x] Feedback revisions require human confirmation and manual send evidence; unverified delivery is shown explicitly. Provider callbacks are outside this initial integration.
- [x] Renewal creates a new order/enrollment and preserves original history and attribution.
- [x] Desktop and mobile workflows enforce organization, resource, field and export scope server-side; permission scope is covered by backend integration tests.
- [x] Role work queues and management exceptions link to actionable records.
- [x] Synthetic manifest reconciliation detects missing/cross-organization links and preserves post-cutover rows; production migration rehearsal is still required.
- [x] EDU backend integration/concurrency tests and both production builds pass. Browser walkthrough reaches attendance and feedback; full suite also includes three unrelated existing SMTP validation failures. Manual provider sending and production migration still need deployment-specific acceptance.

## Integration findings

1. `Customer` already owns `owner`, `poolId`, `inSharedPool`, `followTime`, freeze fields and organization. Do not create a competing ownership model.
2. `CustomerContact` links to a customer. Add guardian/student association metadata rather than making the child the customer contact.
3. `sales_order_field` uses `BaseResourceSubField` with `refSubId`, `rowId` and `bizId`. `BaseResourceFieldService.saveSubFieldValue` preserves incoming row `id` as `bizId`, generating it only for new rows. Enrollment must reference a verified persisted business row identifier, never its display position.
4. `OrderService.add` requires a module form configuration; it saves a snapshot and runs approval hooks. Do not bypass it with a raw insert to simulate a sale. EDU fulfillment eligibility must account for approval and payment separately.
5. Payment records currently reference contracts. Verify linking/allocation before presenting an order as paid; never infer payment from the order stage.
6. `ResourcePermissionService` delegates resource scope to providers. EDU validates organization boundaries explicitly, and the order resource provider now compares organization IDs before granting access.
7. The baseline frontend lockfile is ignored and absent; workspace package policy was left unchanged apart from enabling existing required dependency build scripts for local builds.

## UI scope

The Cordys shell, navigation and customer detail structure remain intact. EDU workflows appear in the existing customer overview drawer and mobile detail tabs, with one workbench entry using the existing layout.

## Implementation packages

1. Customer-linked family/student/guardian domain, API, authorization and migration.
2. Trial, product specification snapshots, order-row adapter, enrollment and handoff.
3. Classes, dated membership, sessions, attendance, entitlement ledger and leave/makeup.
4. Feedback templates, confirmation/delivery records, renewal and role work queues.
5. Desktop/mobile workflows, migration preview/reconciliation, full-chain verification.

## Non-negotiable invariants

- Every linked object belongs to the authenticated organization; request parameters cannot select another organization or actor.
- Customer reassignment cannot rewrite historical sales attribution.
- An order row cannot silently change a previously confirmed enrollment snapshot.
- Enrollment, receipt, learning and feedback statuses are independent.
- Entitlement balance is derived from ledger entries; insufficient balance is not clamped to zero.
- Attendance, ledger effects and critical audit records share a transaction; external delivery is retriable outside that transaction.
- Idempotency keys bind an operation to its original payload; reusing a key for different content is a conflict.
- A copied message is not a delivered message.
- Naming, phone matching and array order are not stable relational identifiers.

## Discussion prerequisite

The user authorized enabling Issues. Tracking issue: https://github.com/shaullinda323-cpu/CordysCRM-EDU/issues/1. Scope and acceptance are recorded there before feature implementation.
