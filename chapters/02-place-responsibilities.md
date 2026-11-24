# Chapter 2. Place responsibilities before adding a screen

The repair room has three lendable items: CLAMP, DRIVER, and LEVEL. A visitor reserves CLAMP and LEVEL together. The next shift needs to know two things: the reservation is still waiting for pickup, and neither item is available for another reservation. Now unplug the command line. Can an application test still check out that reservation and see both items move into an active loan?

If the answer is no, the command line owns a rule it shouldn't. It doesn't matter that the program is organised into directories named `ui`, `domain`, and `storage`. The test tells you where the responsibility actually lives.

**After this chapter:** place input, lending rules, use-case sequencing, and storage on the right side of a dependency boundary; describe and implement checkout without a screen; replace storage with a stateful in-memory fake and rerun the important behaviour checks. The names and steps below illustrate the design. They are not an assertion that the final Java lab uses these exact signatures, error strings, or command spelling.

## Start with the decisions, not the classes

A reserve request identifies a loan, a due calendar day, and one or more inventory items. It creates a `RESERVED` loan if the request is valid and each item is available. Checking out changes `RESERVED` to `ACTIVE`. Only a reserved loan can be canceled, and only an active loan can be returned. Canceling or returning releases the held items, but the loan stays in history. Returning is late exactly when the return calendar day is strictly after the due calendar day. An unknown ID or an invalid operation cannot turn into an ordinary success.

Those rules hold when the request comes from a keyboard, a test, or a screen you haven't designed yet. Now make four decisions about who does what:

| Boundary | Receives | Owns | Does not get to decide |
| --- | --- | --- | --- |
| Command-line interaction | Text typed by a person | Splitting and parsing input, checking that supplied values can be represented, rendering a result | Whether a reserved loan can be returned |
| Domain: Loan and State | Typed loan, day and item values | Loan transitions, history and invariants spanning loans and inventory | What a CLI error looks like, or where a snapshot is stored |
| Application: DeskService | A use-case request, such as check out loan R17 | Load, request the appropriate domain operation, establish a proposed next state and save it before reporting success | How to recover from a corrupt storage format, or how to word a message |
| Persistence: Store and its implementations | Read request or whole state to save | Retrieving and storing state under a defined contract; structural conversion and failure reporting in a file adapter | Whether a late return should be accepted or an item should be released |

Parsing `dueDay` and judging lateness are not the same test. The CLI can reject text it can't convert into a date. Once it has a typed day, the domain applies the comparison. [Java's `LocalDate` documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/LocalDate.html) describes a calendar date without a time or time zone. That fits this chapter's explicit calendar-day contract. The caller supplies the return day; a rule shouldn't silently change because a test ran just after midnight. If a future interface obtains the return day from a clock, the application must decide which local calendar to use before constructing that request.

The distinction appears again when checking an item ID. The CLI can reject a missing argument. The domain or state must reject an unknown item when a test hands it a well-formed identifier directly. Otherwise, replacing the CLI creates a hole. The same reasoning applies to multiple items: the reservation is a single decision. If CLAMP is available and DRIVER is not, don't leave CLAMP reserved by half a request. Build a complete valid next state or leave the previous state unchanged.

## Draw the dependency before writing the adapter

A small design can live in one project. It doesn't need a separate server for each box.

```text
Command line  ->  DeskService  ->  Loan / State
                         |
                         v
                      Store contract
                      ^           ^
                      |           |
                In-memory fake   File adapter
```

The arrows point from code that uses a dependency toward the contract it uses. The service asks a `Store` to read or save state. It needn't know which implementation was supplied. The command-line startup code can choose one and give it to the service; a test can do the same without starting the CLI. The concrete adapters must satisfy the store contract. The domain doesn't call either adapter.

In Java, an interface can name a contract independently of the concrete classes implementing it; see the [Java Language Specification, Chapter 9](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html). Declaring a method signature is not enough to describe its behaviour. This store's contract must also answer: Does a later read observe what was saved? Can callers safely reason about one state without it changing underneath them? How is a failure reported? In a teaching implementation, immutable snapshots or copies at the boundary can preserve the previous state while the service prepares a change. A class name that says `Store` tells you none of that.

Consider the tempting alternative: put a call to a particular file reader inside checkout. That works for the first run. It also makes tests set up a filesystem to exercise a lending transition, and it makes a domain rejection harder to separate from a path or file-format problem. The interface is justified by a useful substitution: the same service can check a reservation through an in-memory fake or persist it with a file adapter. If a layer does nothing but forward calls to one fixed place, ask what capability its boundary buys before adding more of it.

## Worked checkout: run it without interaction

Start with a known snapshot. Loan R17 is `RESERVED`, due on May 8, and holds CLAMP and LEVEL. DRIVER is available. Here is one possible sequence for the operation. It is language-neutral pseudocode, not a code listing from the lab:

```text
checkOut(loanId):
    before = store.read()
    result = before.checkOutReservation(loanId)
    if result is rejected:
        report rejection without saving
        return
    store.save(result.nextState)
    report committed success only after save succeeds
```

First, the application loads the state. The domain asks whether R17 exists and is reserved, not which command produced the request. It stages a state with R17 `ACTIVE`, May 8 and both held item IDs unchanged. The inventory does not become free merely because the status changed. The application saves that complete proposed state. A later read must observe R17 as active for the reported success to be meaningful.

The service should not announce “checked out” between calculating and saving the result. It can establish that checkout would be valid without yet establishing that it is recorded. If persistence fails, report the storage failure rather than converting it into an invalid-loan message or a success. Designing for simultaneous writers or crashes partway through a write is a later issue; a single-session example does not by itself prove that all filesystem errors leave precisely the original bytes. The point here is to distinguish a known domain rejection, a successful save, and a failure whose storage outcome must be checked.

Try the following trace by first predicting the statuses and held inventory. After creating R17, inspect the state after each action rather than trusting printed output.

| Operation | Outcome under the rules | Items held after this step |
| --- | --- | --- |
| Check out R17 | Accept; R17 becomes `ACTIVE`. Its due day remains May 8. | CLAMP, LEVEL |
| Check out R17 again | Reject; only `RESERVED` can be checked out. | CLAMP, LEVEL |
| Reserve CLAMP and DRIVER on R18 | Reject the entire request; CLAMP is held. R18 doesn't acquire DRIVER. | CLAMP, LEVEL |
| Cancel R17 | Reject; an active loan isn't a reservation awaiting pickup. | CLAMP, LEVEL |
| Return R17 on May 8 | Accept; not late. R17 remains in history as returned. | None |

Repeat only the final case with a fresh active snapshot and May 9 as the return day. It should be late, and the items should still be released. Lateness is a fact about the return, not a reason to refuse to take an item back. Repeat with a fresh reserved R17 and cancel instead. Its items should be free and its canceled record should remain. A subsequent checkout of that historical record is invalid. An unknown loan ID is a different bad request; neither grants permission to search for a convenient substitute loan.

These checks show where the invariant belongs. The booking records its status. The lab’s DeskService inspects it and refuses checkout unless that status is reserved. The service uses the whole State to reject mixed available-and-held requests before saving; State also rejects snapshots with overlapping holds. DeskService coordinates the read and write. A CLI can make each rejection easy to understand without being trusted to enforce any of them.

## Put a real fake behind the same doorway

A useful in-memory fake stores a snapshot, replaces it on a successful save, and returns that new snapshot on the next read. It is fake because it does not persist to disk, not because it ignores the meaning of saving. If the snapshot is mutable, holding the same object by reference may let the state change without a save. That behaviour would invalidate tests that try to distinguish staging from committing. Use snapshots whose public behaviour preserves that distinction.

Compare three tests:

1. **Checkout response only:** Call checkout and check that it reports success. A no-op store which always returns the initial snapshot could pass.
2. **Checkout followed by a fresh observation:** Call checkout, then read the stored state or create a fresh service over the same fake. Require active status, unchanged due day and items still held. The no-op store would fail.
3. **Checkout followed by a competing use case:** In a fresh service over the same store, try checkout again and try reserving one of R17's held items. Require both attempts to fail; then return R17 and require the items to become reservable again. This tests consequences rather than only a flag.

Calling checkout twice by itself isn't enough if the service also maintains a private copy that masks a broken store. The fresh-service check makes that shortcut visible. Conversely, only checking that save was called once misses a save that received the wrong proposed state. A fixture with two items exposes implementations that change or release only the first.

Use the same decision-and-state trace for the file adapter where possible. Use a new test file, not a desk operator's data. Construct a new service and adapter for that same file to check that ordinary reopening reads the recorded state. The in-memory fake can be shared by two freshly constructed services during a test; constructing a *new fake* is not equivalent to reopening a file. The persistence-specific reopening check belongs to the file adapter, not to the abstract lending rule.

Troubleshooting gets simpler when each check answers one question:

- **Domain check:** Is an unknown or already active loan rejected, and is the proposed state correct without any storage?
- **Application contract check:** Does the next successfully saved state reflect the use case, and does a domain rejection avoid saving?
- **Adapter check:** Does saving and reopening preserve the inventory, due day, status, and history the application actually uses?
- **Interaction check:** Can a person enter a request and distinguish a parse problem from a domain rejection and a storage failure?

A stateful fake still cannot prove that the file format round-trips, the folder is writable, or a reboot preserves a partially written file. A full file test still doesn't prove that user-facing wording is useful. Substitute dependencies to make a specific question cheaper to answer. Don't advertise the resulting test as answering every question.

## Exercises

1. **Locate the decision.** Assign each of the following to one owner: malformed date text, a verified return day after due day, a failed save, a request to cancel an active loan, and a missing file header. For one pair, explain why the two failures must be distinguishable.
2. **Predict without a screen.** Starting with reserved R17, write the state before and after checkout. Circle the fields allowed to change. State what a second checkout should observe.
3. **Implement a seam.** In your exercise code, accept the store at service construction. Keep the request to check out independent of command tokens. Demonstrate one accepted and one rejected request without constructing a CLI or reading keyboard input.
4. **Catch a half-reservation.** Hold LEVEL, then attempt to reserve LEVEL and DRIVER together. Predict which items should be available after rejection. Add a check showing that the previously free DRIVER wasn't acquired.
5. **Compare dates.** Create equal-day and later-day returns from independently active snapshots. Check both the late-result field and that both releases leave retained history. If you check a date before the due day too, predict its classification from the same rule.
6. **Break the fake deliberately.** Substitute a store that always loads the initial snapshot and discards saves. Explain which thin test might still pass. Improve the check using a fresh service and persisted facts, then restore the stateful fake.
7. **Force a storage failure.** Substitute a fake that reports failure when asked to save. Compare that outcome with a known domain rejection. A failed write should not be described as a successful checkout; don't assert more about failure recovery than your store contract supports.
8. **Defend the boundary.** Sketch a future GUI that uses the same service. Which input and rendering choices move into the GUI? Demonstrate which tests stay unchanged. Propose one genuinely different requirement that would force a domain change rather than a UI edit.

The payoff is not four boxes on a diagram. It is being able to change how a request arrives, or where a result is stored, and still have the same observable lending behaviour.
