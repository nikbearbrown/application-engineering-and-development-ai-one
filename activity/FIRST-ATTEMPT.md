# First attempt: model the repair-room desk

Do this before reading the worked chapter or opening the code. The desk is fictional and keeps one each of CLAMP, DRIVER and LEVEL.

1. Sketch the actor, physical IDs, booking ID, status and relevant dates. Write one rule that holds for a booking by itself, and one that has to look across all bookings.
2. Predict what the primary record and inventory view should show after each of these requests. “It failed” is not a state description.
   - Reserve LEVEL under R1 for May 12, 2026.
   - Try to reserve DRIVER and LEVEL together under R2 for the same day.
   - Try to reserve DRIVER alone under R3.
   - Check out R1. Attempt to cancel it. Return it on May 12.
   - Cancel R3. Record whether either booking should disappear.
3. Name a second request you would use to expose an invisible partial hold.
4. Name one race you would want to verify and one error whose filesystem outcome you could not assume.

Then compare your predictions with [Chapter 1](../chapters/01-model-the-work.md) and with the actual program. Preserve the original predictions before adding anything suggested by another learner or an optional assistant. The exercise doesn't require AI.
