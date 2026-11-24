# Chapter 4 — Design the user flow
*The rules decide what can happen. The screen tells you what to do next.*

Suppose you are covering a repair-room lending desk. The previous shift left a note: “R1, Driver and Level, due May 12.” Can you cancel it? Should you return the tools first? Are both tools on the shelf? The note contains real information, but it doesn't answer the question your hand is about to act on. You need a view that names the state and tells you what follows from it.

The fictional desk has one identifiable `CLAMP`, one `DRIVER`, and one `LEVEL`. A reservation holds each listed tool while it is `RESERVED` or `ACTIVE`. The first state means the kit has been set aside. The second means it was checked out. A screen that reduces both states to “unavailable” may be right about inventory and still be unhelpful about the next action. Canceling a held kit and recording the return of a checked-out kit are different jobs.

**After this chapter:** separate a command handler from a read model; design a legible route from empty list to reservation detail; explain why rejection leaves state intact; test the flow from the keyboard without claiming formal accessibility compliance.

## Start with the question a screen answers

There are three useful questions at the counter: “What can I reserve?”, “What reservations exist?”, and “What can I do with this reservation?” Give each a home. Inventory starts from the tools. The list starts from the reservations. Detail starts from one reservation ID. All three can refer to the same underlying facts, but the arrangement matters. A desk worker scanning shelf stock shouldn't have to open every reservation to learn which tool is held. Someone with a receipt ID shouldn't have to scroll through inventory to find the matching reservation.

A **read model** is a presentation of existing state for a particular question. It can be as small as a few lines built from the current state at the time of a command. It does not have to be another data store or an architecture with a name. The inventory view can show that the Driver is held without copying a second “held” flag into a separate permanent field. If both copies were writable, a successful cancellation might free the reservation but leave the inventory view looking blocked. Derive availability from the current reservation state and render it. Here there's little reason to introduce another fact to keep in sync.

| View | Information that earns its place | Useful next step |
| --- | --- | --- |
| Inventory | Stable tool ID and available or held status | Choose one or more available IDs for a reservation. |
| Reservation list | Reservation ID and state, with due date or tool list where useful | Pick one ID and open detail. |
| Reservation detail | Exact ID, state, tool IDs and due date; return date and late status when recorded | Show the action that matches this state or make clear the record is closed. |
| Recovery message | Which command did not apply and why; say what did not change | Inspect the current record or use the correct action. |

Design the empty list before designing the full one. “No reservations yet” distinguishes a successful query with nothing to display from output that got lost. A hint like “Use inventory to see tool IDs” gives a real next step. Don't print a fake reservation or show a cancel instruction that needs an ID not yet in the system. The inventory command can still show all three tools. Empty reservations and empty inventory are not the same condition.

## Trace a route someone can follow without guessing

Try this sequence in an exercise session that starts without reservations. It is a sequence of inputs, followed by what to inspect, not a transcript captured from a running program. Compare actual output with the documented lab rather than expecting these descriptions to be exact strings.

```text
list
inventory
reserve R1 2026-05-12 DRIVER LEVEL
list
show R1
```

The first list tells you there are no reservations. Inventory identifies the three available tools. Reserving succeeds if both requested IDs are available. Read the next list and verify that it contains R1 with the `RESERVED` state. Open its detail with `show R1`: the same R1 should show Driver and Level and the requested due date. Then inspect inventory again. Driver and Level are held; the Clamp is still available. A tiny consistency test has now crossed two views and the one operation that matters.

The reserved detail is also where cancellation should be visible. Display an instruction such as `cancel R1` with the actual ID. No need to force the worker to reconstruct it from a generic reference elsewhere. The command should remain discoverable through `help` as well. A hint is presentation, not permission: even a worker who types a cancel command without opening detail must face the same state rule. Showing a shortcut for one state does not make it legal in every state.

Take two branches from the reserved state in separate clean runs. In one, type `cancel R1` and inspect the resulting detail and inventory. The reservation is canceled, both tools are freed, and no return date appears. In the other, type `checkout R1`, then inspect detail. The state is now `ACTIVE` and both tools remain held. Use `return R1 2026-05-13` on that branch. The return date is May 13, one calendar day after the May 12 due date, so the return is late. Again, both tool holds end. If you want to test on-time behavior, repeat with an independently prepared active reservation and a return on May 12. Equal to the due date is not after it.

The two branches answer different real questions, even though they both free tools. Cancellation undoes a hold before lending. Return records what happened after lending, including the date. Don't collapse them into one button just because inventory reaches the same result. You would lose the meaning that the person watching the counter needs.

## Let the handler turn intent into a decision

A **command handler** receives a command, checks its shape, and mixes as little policy into the interaction layer as possible. For `cancel R1`, the interaction layer should recognize the verb and a single reservation ID. Then the domain operation checks the real record and decides whether it can move out of `RESERVED`. Once the result comes back, the interface presents it. A well-shaped command isn't the same as an allowed action. Even an existing ID with perfect formatting may be in the wrong state.

Take the active branch above and try `cancel R1` before returning. The command names known data but requests the wrong transition. Do not make an opportunistic return. Do not release one tool on the theory that one item could be canceled. Do not set a return date or silently discard the reservation. The operation is a no-op: verify active status and the same holds using `show R1` and `inventory`. A recovery message can explain that an active reservation must be returned, and that nothing changed. If the tool hasn't come back yet, the right next step may be to stop, not to type another command.

A wrong state belongs to a different recovery category from a wrong command shape or an unknown ID. If a return command omits the date, show the required form; don't choose a date for the worker. If an ID doesn't exist, allow the worker to inspect `list` and confirm the receipt. If an operation cannot apply, name the state that blocked it. None of these paths should quietly move the reservation anyway. A malformed request can be rejected before the domain operation; a parsed but invalid request should arrive at an explicit domain refusal.

Make the visible reason match what the application knows. “Can't cancel an active reservation” is more informative than “Something went wrong.” “No reservation R9” is different from a failure to display the page. If your interface can't establish a cause, don't invent one. State what was observed and let the operator inspect the current view. Treat a success message that appears without a matching state change as a defect, just like a state change with no usable message.

## Readability is part of the flow

A command-line lab is workable when ordinary keyboard interaction reaches every step. Use consistent verbs and visible examples. If you print a command prompt, keep it short. Make section names and status words readable without depending on color or a symbol. Show IDs as text that can be copied, type the commands without a pointer, and put the reason near the failed command. Prefer a short list that still makes sense when a terminal wraps. A transcript the learner can read later is more useful than a visual flourish that disappears.

Test those properties rather than declaring compliance. In a narrow terminal, can you still find the command and the exact ID? With no color, is a held tool still labeled? Does `help` cover the exit path through `quit`? After an invalid ID, does the CLI still accept the next command so you can find the correct detail? These checks make the example easier to operate. They are not an accessibility audit, a screen-reader test, or evidence of compliance with any formal standard.

## Practice

1. Turn an empty `list` result into one explicit statement and one valid next step. Explain why cancellation isn't that step.
2. Run the reserve branch and compare inventory with detail. Name a bug that a success message alone could miss.
3. On fresh reserved and active records, try `cancel`. Record the actual change or lack of change from two views before crediting the message.
4. Design a narrow-terminal or noncolor check. Name one accessibility question your check doesn't answer.
5. Pick a recovery reason, such as a missing return date. Draft a message that helps the worker correct it without making an unstated decision on their behalf.

A helpful flow makes the rules easy to inspect, not easy to bypass. The next chapter shows how to change one rule-facing behavior without breaking the routes that the desk already relies on.
