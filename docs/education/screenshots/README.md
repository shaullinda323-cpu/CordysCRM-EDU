# Synthetic browser acceptance screenshots

Both images were captured from the isolated MySQL 8 TestApplication with synthetic records. The Cordys CRM default layout/navigation remains visible in the desktop capture; mobile uses the existing mobile shell. No real customer, learner, or guardian data appears.

- `desktop-workbench.png`: queue and class scheduling in the desktop workspace.
- `mobile-workbench.png`: the same queue at a phone viewport, with a synthetic class awaiting teacher actions.
- `mobile-feedback-delivery-gate.png`: mobile queue distinguishes pending attendance and pending teacher-reported send; no fake delivery receipt is present.

Backend integration tests exercise attendance corrections, leave/makeup, feedback revision and role scope. The browser rehearsal visibly completed guardian linking, approved/enough-paid enrollment handoff, class membership, scheduling, attendance consumption and feedback confirmation. It did not submit a fake send receipt; the interface intentionally remains at “pending send” until a human records an actual event.
