# Chapter 3 — Check the whole transaction
*One failed reservation should not leave a tool half-borrowed.*

Suppose a volunteer at a fictional repair room needs a clamp and a level for the same job. The room has one of each. Someone else already holds the clamp. The program tries to allocate the level first, then notices the clamp is unavailable and prints “reservation rejected.” A second volunteer now asks for the level. The program says it's taken.

The first error message was accurate about the clamp and false about the operation as a whole. The program didn't reject the reservation. It rejected part of it, after making part of it stick. If you only test the text on the screen, the application looks done. If you look at who can use the level, it's broken.

This is why the unit of work matters. The volunteer asked for one reservation containing two tools, not two unrelated attempts to reserve one tool each. A **transaction** is the unit of change you want to make as a whole. In this exercise, the reservation is either accepted for every requested tool or rejected without holding any of them. The same rule applies when a group is checked out, cancelled, or returned. None of the cases refers to a real service or makes a real reservation.

**After this chapter:** predict a whole state change from a lending rule; test that one rejected item leaves the other items available; explain how a guarded update differs from a durable update; and diagnose what to tell the user when storage cannot confirm what happened.

## Start with what must stay true

The room has exactly three individual tools: `CLAMP`, `DRIVER`, and `LEVEL`. A reservation has its own ID, a due day on the room's calendar, a nonempty set of those tools, and a status. A set means that an item appears once in the reservation, not that requesting it twice can create two copies. The application still needs a clear rule for validating user input; do not depend on a set to make an ambiguous entry acceptable.

The room tracks the reservation instead of pretending each tool has a separate borrower within the group. At any moment, a tool may belong to **at most one holding reservation**. Two statuses hold tools: `RESERVED` and `ACTIVE`. A reserved tool is being kept for a future pickup. An active tool is checked out. Either way, the next visitor cannot reserve it. The cancelled and returned records may remain in the history, but they don't hold stock.

| Request | Requires | What changes as a whole |
| --- | --- | --- |
| Reserve | Valid new reservation; none of its requested tools are held | Store one `RESERVED` record with the ID, due day, and complete item set |
| Check out | The reservation is `RESERVED` | Move it to `ACTIVE`; every listed item remains held |
| Cancel | The reservation is `RESERVED` | Mark it cancelled; release every listed item |
| Return | The reservation is `ACTIVE` | Mark it returned, store the return date, and release every listed item |

A volunteer should not have to remember that cancelling an active reservation isn't the same as taking back the tools. The application should reject it. The active loan stays active. The appropriate operation is a return, with its own calendar date.

The return is late only when its date is **after** the due date. For a reservation due on April 10, a return on April 10 is on time; a return on April 11 is late. An earlier return is on time too. Those dates are synthetic. Store the date of the return, not just a computed label. That lets a later reader check the decision against the due day. In this date-only exercise there is no cutoff hour to infer. A rule such as “before closing time” would need a different contract.

### Work the rejected request all the way through

Start with an existing reservation A for `CLAMP`. It is `RESERVED`, so the clamp is held. Now try reservation B for `CLAMP` and `LEVEL`. The clamp alone is enough to reject the request. That doesn't give permission to modify the level.

| Point in the exercise | Holding reservations | Remaining available tools |
| --- | --- | --- |
| Before B arrives | A holds `CLAMP` | `DRIVER` and `LEVEL` |
| After rejecting B | A still holds `CLAMP`; B was not stored | `DRIVER` and `LEVEL` |
| After accepting C for `LEVEL` | A holds `CLAMP`; C holds `LEVEL` | `DRIVER` |

The third line proves something the rejection message did not. A separate valid request can immediately claim the free part of the failed bundle. Ask for `LEVEL` first in the test input if the interface takes an ordered list. Also try the opposite order. You don't want correctness to depend on which tool the program happened to examine first.

Now check out A. It moves from `RESERVED` to `ACTIVE` without freeing the clamp. A cancellation attempt at this point leaves A active. Return A on its due day. The fixture should contain a returned A with the correct date, and a new request for `CLAMP` can now succeed. That sequence checks both the visible status and its consequence for a different request. If the status changes but the old item is still unavailable, the transaction isn't correct yet.

<!-- → [INFOGRAPHIC: one request enters as a bundle; check every item before the entire reservation becomes visible; rejection keeps the old holdings] -->

## Put the check and the change under the same guard

Even a program that checks all the tools before making a change can be wrong. Imagine two people request the only `DRIVER` at almost the same time. Both read “available.” Both form a valid next state. Both then write after their checks. Two approvals can describe ownership of one physical driver, or one write can overwrite the other. The problem isn't the arithmetic. It's the gap between asking whether a change is permitted and making the change.

For the single Java service in this exercise, use the same synchronization guard for the entire decision: read the current state, check eligibility, construct the whole replacement snapshot, attempt the commit, and publish the replacement to the service only after the commit is confirmed. A Java synchronized operation can keep callers using that same guard from entering the protected operation at the same time. Every path that makes or depends on the protected update must honor the guard. Synchronizing the final write while doing the availability check outside it leaves the gap in place.

Play the driver example under this design. Suppose D1 enters first. It sees the driver free, its request passes, and its commit succeeds. D2 then enters and reads the state containing D1's reservation. It is rejected for the held driver. If D2 enters first, the names switch. The test should require **one success and one domain rejection** for the two valid requests with functioning storage, not a specific winner. In either ordering, an independent final check should find just the winning hold. A result claiming the winner must be the first thread you started would assert something the rule never promised.

The busy interval is a cost. The second caller waits while the first checks and saves, even if it wanted a different tool. That is acceptable for the small, local room we are studying. It makes the order of decisions easy to explain. If a later design permits multiple workers to change different pieces of a larger system, it needs its own plan for transactions that touch multiple pieces. This exercise doesn't show that plan, and a guard in one object does not protect another object or a different process that never uses it.

## Don't publish a replacement you couldn't save

There are two distinct objects in this design: the **confirmed state**, which is still in effect, and a **candidate state**, which would be in effect if the change was saved. Make the candidate without mutating the confirmed one. If you copy only the top of a structure but then edit an item that both versions still share, the old state can change by accident. Whether the implementation uses immutable records or makes suitable copies, test that a rejected candidate leaves both prior records and availability unchanged.

For a clean failure test, deliberately inject an error **before the storage adapter writes anything**. Begin with A holding `CLAMP`. Try to reserve `DRIVER` and `LEVEL` as B, which passes the policy checks. Cause the pre-write failure. The result is a storage failure, not a conflict. The old snapshot still has A alone. Under the test's controlled failure, the saved snapshot and the service state should both remain unchanged. Restore the working test adapter, then demand that a new request can still hold `LEVEL`. That probes the same no-half-update rule in a different failure branch.

The phrase “before the adapter writes anything” does real work. An arbitrary error from an actual save doesn't prove that the previous snapshot survived or that the candidate was never published. If a real storage error leaves the outcome uncertain, the CLI should stop without reporting success, guessing which state survived, bootstrapping an empty state, or automatically retrying against uncertain records. A read of the authoritative saved state needs verification before normal operation resumes. The injected case makes a specific claim about a specific timing, not a promise about all I/O errors.

### Atomically visible is not the same as durable after a crash

One local snapshot design prepares the replacement away from the current snapshot, then attempts a final atomic move into place. In [Java SE 17 `Files.move`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#move(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...)), `ATOMIC_MOVE` requests an atomic filesystem operation. If that operation cannot be performed atomically, the method throws `AtomicMoveNotSupportedException`. Other move options are ignored with `ATOMIC_MOVE`, and whether an existing target can be replaced atomically depends on the implementation. Passing `REPLACE_EXISTING` as well is not a portability guarantee. If the final operation can't be performed as required, stop. Falling back to an ordinary move abandons the property under discussion.

That storage step is separate from the synchronization guard. The guard orders callers that cooperate. The move is about whether the snapshot changes as one filesystem operation on that provider. Neither on its own answers what survives abrupt power loss. A successful return from the reservation method is not evidence that a later crash test was run or that the newest snapshot always survives one. Crash durability needs independent verification under the declared storage assumptions. This lesson makes no such promise.

If cooperating CLI processes share one local folder, they can use one stable lock file as their common guard so only one of them at a time reads, checks, and attempts the snapshot update. The lock path is not the changing data snapshot. Holding a lock on a file and separately replacing that same file is not the design. In [Java SE 17 `FileLock`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileLock.html), file locks are associated with the JVM, and whether a native lock blocks other programs is system-dependent. Use in-service synchronization for the two threads in the driver test. Use the stable lock only for the cooperating local CLI scenario, not as a promise about other programs, shared network filesystems, separate machines, or distributed transactions.

## Diagnose what happened before writing the receipt

Three failures can produce three different next steps. Don't make the reader infer them from the same word “failed.”

| Result category | Example | What the user should learn |
| --- | --- | --- |
| Syntax or input | A malformed calendar date doesn't parse | Correct the request; the application did not reach a state change |
| Domain | A valid request includes a tool that is already held | Choose another tool or wait; the request as a whole was rejected |
| Storage | The valid candidate could not be confirmed as saved | No success claim; stop for verification if persisted state is uncertain |

A valid return date landing on the due day is not a syntax error, domain rejection, or storage error. It is a successful on-time return. A cancellation of an active loan may be perfectly well-formed as input, but it fails a domain rule. A rejection label cannot tell you whether the validation, the decision, or the commit was at fault. An honest CLI describes the layer that stopped the operation and limits its claims about the state accordingly.

## Exercises

1. **Trace the group.** Reserve `DRIVER` and `LEVEL` together. Check them out, attempt cancellation, then return them the day after the due date. After each step, list the holding reservations, the available tools, and whether the latest request succeeded. Explain why the failed cancellation doesn't free anything.
2. **Catch a half-update.** Begin with `LEVEL` held. Attempt a bundle containing `CLAMP` and `LEVEL`. Your test must distinguish a display-only rejection from a real rejection. Use a fresh reservation for the other item as well as a check of the records.
3. **Exercise the calendar boundary.** Design fixtures for a return before, on, and after the due day, plus a malformed date. Use an active reservation in every valid case. State what information should be stored and what observation distinguishes date parsing from the lateness rule.
4. **Test the race without choosing a winner.** Arrange for two simultaneous valid requests for `DRIVER` on one service with working storage. Write the required joint outcomes and final snapshot property. Name an outcome that would still be wrong if both callers got perfectly formatted responses.
5. **Find the limit of a failure test.** Compare an injected failure before any write with an uncertain I/O error during a real save. State which old-state claim you can make in the injected case, which claim you must withhold in the real case, and why the CLI shouldn't retry automatically.
6. **Separate the guarantees.** Sketch one check for ordering threads, one for ordering cooperating local CLI processes, and one observation that would be needed for a claim about a crash. Explain why none, alone, establishes a distributed transaction.

A whole transaction is not a long command. It is one change whose conditions, effects, and failure boundary agree. Check the requested bundle, check what another caller can observe, and don't turn an unconfirmed save into a success story.
