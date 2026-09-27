# Existing workflow reuse and test mapping

The earlier teaching workbench is a behavioral reference, not a second database to operate in parallel. Only generalized rules and synthetic cases are reproduced here.

| Existing behavior | Cordys implementation requirement | Required new verification |
|---|---|---|
| Explicit student selection on conversion | CRM contact is never silently promoted to student | Parent name differs from child name; missing student rejected |
| Explicit fulfillment mode | Product specification snapshot controls enrollment mode | Unknown mode rejected; only HOURS/EVENT enabled in phase one |
| Positive hour package | Decimal entitlement quantity with defined teaching unit | Zero, negative, malformed and overprecision quantities rejected |
| Non-hour products omit hour balance | EVENT participation has its own state | Attendance on EVENT does not produce HOURS ledger entries |
| Stable class/student/enrollment IDs | Foreign keys and same-organization checks | Renames do not change associations; same names remain distinct |
| Canceled session rejected | Server-side session transition guard | Direct API submission to canceled session fails |
| Duplicate session submission detection | Transaction + business unique key + payload-bound request key | Parallel retries and different-payload key reuse |
| Teacher-confirmed feedback | Persist confirmed version and editor | Text correction does not create another charge |
| Separate copied/sent/delivered statuses | Delivery record references exact message/feedback version | Copy not counted as send; retry deduplicated |
| Renewal retains source enrollment | New order and entitlement batch linked to original | Prior amount, consumption and attribution remain unchanged |

## Rules to improve rather than copy

- Old conversion accepts a manually supplied paid amount. Cordys must require a validated receipt/allocation or explicitly show unverified payment; the UI cannot manufacture payment evidence.
- Old teaching submission has name-based fallback matching. New business operations must use stable IDs and reject ambiguous migration rows.
- Old aggregate balance presentation clamps negative values. New ledger rejects invalid consumption rather than hiding a deficit.
- Historical HOURS/SESSIONS/EVENT/PROJECT support in configuration does not prove every mode is implemented or tested. Only first-release modes are enabled.

## Full-chain synthetic scenario

Family F1 has guardians G1 and G2 and students S1 and S2. S1 books a trial, then purchases a HOURS package and an EVENT activity. S2 purchases the same EVENT activity in a separate order line. Existing CRM records retain consultant ownership and original order attribution.

1. Record consultation and trial attendance/outcome.
2. Persist the order using native order creation/approval flow and verify product-row IDs.
3. Create three enrollments and a teaching handoff exactly once.
4. Schedule the HOURS class and EVENT session; reject overlapping student/teacher/room bookings.
5. Confirm S1 HOURS attendance; repeat and concurrently resubmit without a second charge.
6. Confirm EVENT participation for S1/S2; no hour debit occurs.
7. Exercise approved leave/makeup and attendance correction without double charge.
8. Submit and confirm feedback, copy it and record manual sending separately.
9. Renew S1 through a new Cordys order/enrollment while preserving the old course history.
10. Reconcile totals and deny a teacher from another class and a user from another organization.

Status: acceptance specification only. This file is not evidence that the workflow currently executes.
