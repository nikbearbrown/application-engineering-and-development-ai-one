# Chapter 5 — Change with evidence
*The happy path demonstrates a feature. The broken path tells you whether it is safe to keep.*

Imagine the repair desk originally lent one tool per reservation. The operator could reserve a Driver, then check it out or cancel it. Now a job needs a Driver and a Level together. “Accept a second item” sounds like a parser change. But the real change is a promise: hold the whole kit or hold nothing. If the Level is unavailable and the app holds the Driver anyway, the clerk gets neither the requested kit nor accurate availability for the next patron.

This chapter describes a proposed local development exercise, not the history of the supplied lab. The current lab is where you can observe the completed behavior. The suggested before, fail, fix and after receipts below are a method for designing and checking a similar change. Don't claim that a pre-kit implementation or a particular failure ever existed unless you actually built and observed one.

**After this chapter:** describe a behavior change in terms of invariants; preserve old behavior with regression checks; distinguish a useful failing receipt from a setup failure; use an independent prediction to review a bounded change, with or without AI.

## State the promise before suggesting a patch

The stock is exactly one `CLAMP`, one `DRIVER`, and one `LEVEL`. Reserving one tool remains valid. Reserving a kit is a single request. If all selected tools can be held, one `RESERVED` record contains the whole kit and all the named tools are held. If any tool cannot be held, the request creates no reservation and takes no additional holds. Successful transitions must respect the entire kit membership: checkout continues the hold for every tool, and cancellation of a reserved kit or return of an active kit releases every tool. Cancellation and return are not the same transition.

Here is the tempting patch: loop over the requested tools and reuse the “hold one” operation for each one. On a full shelf, the loop can look perfect. With `DRIVER LEVEL`, it holds Driver, notices that Level is unavailable, and returns a failure. The caller hears “no” after one of the effects has already occurred. Worse, walking the kit in the opposite order may seem to fix it. That only makes the first tool the unavailable one in this example. An invariant that depends on ordering shouldn't be sold as an all-or-none guarantee.

Instead, distinguish the check from the commit of the change. In the local, sequential exercise, check the ID and every requested item against one current state. Once the checks pass, create exactly one reservation with the complete requested membership. Availability can then be derived from that record. The failure path has nothing new to unwind. This isn't a claim that the sequence is safe when two processes can reserve concurrently. Checking and applying state under concurrency would need stronger coordination and independent tests. The teaching lab shows a domain invariant in its stated setting.

## Before: keep an observable baseline

Start by writing down what must remain true. In a clean exercise session, one-item reservations hold the named tool and leave the other two available. A checkout keeps the tool held. Canceling a reserved record releases it. Canceling an active record is a no-op. Returning the active record releases it and records the supplied calendar date. A due date and a return date on the same day don't trigger the late classification. Return after the due date does. These are rules a reader can recognize at the desk. They don't depend on a private collection class or the name of an internal method.

For a development assignment, choose and identify a real starting artifact before you edit. Run the one-item paths against it and write exactly what you verified. If you don't have a pre-change build, the suggested baseline is only a prediction or a proposed regression set. In the supplied exercise, run those requests against the **current** application to discover what it actually does. Label the result as a current regression check, not a historical “before.” A report is more useful when its limits are explicit than when every box has an invented checkmark.

Use controlled starting conditions. If a previous run left Driver held, a later “single-item reserve failed” says little about the change. Follow the lab's documented reset or start a fresh isolated session. Keep an exercise ID separate from any active example you need. Use small dates you can compare by eye. A surprising mismatch in May should not require a second tool to derive what your reference should have said.

## Fail: make the observation discriminate

Set up a state in which exactly the second requested tool is unavailable. This is a prediction exercise and a possible test sequence, not reported historical output:

```text
reserve R0 2026-05-12 LEVEL
inventory
reserve R1 2026-05-12 DRIVER LEVEL
list
inventory
show R1
```

After R0 succeeds, Level is held and the other tools are available. Predict what a bad sequential mutation would do: hold Driver, then fail on Level. Under the contract R1 must fail, must not exist as a reservation, and must leave Driver and Clamp available. Level stays held by R0. R0's due date, state and membership are unchanged. Compare the state before and after with `inventory` and the reservation views. A test that only looks for the word “failed” misses the problem. A test that only looks for an absent record can also miss it if a hold lives elsewhere. This is why the same negative case inspects both.

A useful red test fails for the reason it was built to reveal. When `reserve` rejects the entire command as an unknown format in a version that doesn't support multiple IDs, record that as an unsupported capability, not as evidence of a partial mutation. If possible in your own development copy, add the scenario to both the feature and the transition tests once the syntax can reach it. A mistake in setup, such as reversing which item is held, might allow a wrong implementation to pass. Verify the first state before trusting the second.

Try the mirror question as a design check: if the first requested tool is unavailable, a naive loop may already appear all-or-none because it hasn't mutated anything yet. Testing both orders helps expose this coincidence. Verification belongs to the behavior, not the string order. Keep the positive test too, so a program that rejects every kit cannot pass your entire suite.

## Fix: change the boundary once

Make one local change that can be reviewed against the promise. Convert the inputs into a list of tool IDs and hand the complete request to the operation that owns reservation state. Decide how to validate the complete set, then perform one creation only after it passes. The CLI can still render one reservation ID and its membership. It should not make multiple single-item reservations and then aggregate their output; that would keep the wrong unit of change and scatter check-out and return behavior across records.

If your design also changes storage, error wording, date format or command names, make those separate changes where practical. Otherwise a failing regression has too many possible causes. The useful draft diff should make it easy to point to where the single creation decision occurs and where the unchanged date comparison remains. Choosing a small boundary also makes reversal easier if the first fix protects the kit but breaks normal lends.

An optional AI assistant can suggest skeptical cases or review the local diff after you have committed your own prediction to paper. Provide only the local, authorized exercise contract and relevant synthetic snippets. Ask it to propose a counterexample, not to run unknown external commands or assert that the result is correct. If it recommends checking only the final message, reject that suggestion and retain a state check. If it proposes an unverified expired-date rule, distinguish that new product decision from the current contract. No model is needed for this exercise. A polished patch does not replace a test with an independent expected result.

## After: require both negative and familiar paths to work

Record the evidence in four parts. These are things to fill in after the corresponding work, not statements that the lab already produced them:

| Receipt | What to record | What the observation could support |
| --- | --- | --- |
| Before | Identified starting build or current reference, one-item scenarios and observed states | Which behavior must remain; if unavailable, label a predicted baseline. |
| Fail | Preconditions, exact command, actual unexpected state or reason, expected state | That a scenario found this specific defect, not that setup simply failed. |
| Fix | Small local change and how it enforces the kit invariant | The intended repair; motivation is not proof. |
| After | Same failure trigger, positive kit path and old single-item paths with resulting views | What was verified in the rerun under those conditions. |

A complete rerun should include a fully available two-tool kit, the unavailable-second-tool negative case, a cancel on a reserved kit, an invalid cancel on an active one, and a return that stores the given date and frees every member. Independently confirm the single-item route and both sides of the late boundary. Check the list and the exact detail when needed, not only internal state. End the example with a small report: what changed, which checks passed or failed, and what you did not test. Do not infer persistence, recovery after crashes, or concurrency safety from this local run unless those properties were actually examined.

## Practice

1. Predict inventory and list before running the unavailable-second-tool sequence. Compare the prediction with the current lab and classify any difference.
2. Build a minimal one-item regression set that covers a reserved cancellation and an active return. Explain why these are separate branches.
3. Create a deliberately weak acceptance check for a kit. Then strengthen it so it detects both a ghost hold and a program that refuses every kit.
4. Write a fail receipt for a missing multiitem parser. Contrast it with a fail receipt for a parser that accepts the input but mutates partially.
5. Give another learner your before and after cases without the patch. Ask them whether a different implementation could satisfy the same invariants and which observation would falsify it.

A change is ready for someone else to assess when they can see why it was made, what behavior survived, and which claim the evidence still cannot support.
