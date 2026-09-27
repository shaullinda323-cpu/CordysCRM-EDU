# feat: add education enrollment and teaching workflow

## Problem

The CRM manages customers and sales, but a consumer education provider also needs to track multiple children per family, multiple enrollments per child, trial bookings, teaching handoff, attendance, entitlement consumption, feedback and renewal.

## Scope

Implement a complete first release for long-term courses (HOURS) and single-session activities (EVENT), reusing Cordys customers, pools, products, orders and permissions. The workflow is consultation → trial → sale → enrollment → scheduling → attendance/consumption → feedback → renewal.

Keep current contact/customer IDs and historical sales attribution. Use persistent order product-row identifiers and enrollment snapshots. Extend existing desktop/mobile applications and server-side scope checks rather than adding a disconnected application.

## Acceptance

See `docs/education/phase-one.md` for the complete gates and invariants. Required cases include multi-child families, multiple enrollments, concurrent/duplicate attendance, insufficient entitlement, leave/makeup, corrections, feedback retry, renewal history, cross-organization denial and repeatable migration.

## Validation plan

Targeted JUnit tests; MySQL integration and concurrency tests; frontend type/lint/style checks and builds; desktop/mobile browser walkthrough using synthetic records. Existing behavior must continue to work. Configuration or mock-only success is not end-to-end completion.

## Delivery boundaries

No production data migration, live external messaging or deployment in this development task without an explicit cutover decision. SESSIONS and PROJECT modes remain future extensions. No client-identifying data or credentials in source, screenshots, fixtures or this discussion.
