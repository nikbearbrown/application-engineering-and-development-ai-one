# Chapter 1 — Turn a user need into a domain model
*Draw what can change before drawing where to click.*

Imagine a repair room has one clamp, one driver and one level. A worker makes a form called “Book equipment.” It has three checkboxes. Two people tick the driver at the same time. Both screens look finished. Which booking is wrong?

A shape problem has a button or list that looks wrong. A domain problem has two representations of a fact the real operation can't make true. Only one person can hold the room's driver at a time. To test that promise, first decide what “hold” means, then decide where the app will refuse a transition. The next five chapters build a small application from that decision. No graphical toolkit is needed to find the bug.

**After this chapter, you can:** separate actors from records; identify objects by a stable key; express transitions and invariants in testable terms; and derive inventory and lateness without maintaining duplicated facts.

The room, IDs, dates and policies are invented. The teaching lab is an offline Java program, not an inventory or reservation service.

## Ask what someone needs to happen

Begin with a request, not a noun list. A counter worker needs to know whether a set of named tools can be held together, confirm that the set was handed out, and make the tools available when the set is returned. Two distinct things are happening. The keeper has physically limited inventory, and the booking records its changing commitments. Do not make a new driver object every time someone chooses “driver” on the form: a tool identity represents the same physical resource across multiple operations.

The teaching case deliberately uses a fixed catalog. CLAMP, DRIVER and LEVEL are IDs for three individual tools. There is no separate “three drivers” stock quantity to decrement. If the room later buys a second driver, decide whether the request names one physical unit or any unit of that type. These are different promises. The current contract knows exactly which units it puts on hold.

There are also two meanings of “who.” In the fictional story a patron wants a bundle and a desk worker records the decision. In the lab no one authenticates, and no patron name or personal data exists. Those people are **actors** because their jobs matter to the requirement. They are not mandatory rows in the app's data. Before adding an identity system or admin role, specify which decision requires it. A toy command that can perform a transition is not evidence of real permission.

| Concept | Stable identity here | Changes over the flow |
| --- | --- | --- |
| Tool | Physical tool code such as DRIVER | The booking, if any, that currently holds it |
| Booking | Unique ID such as R1 | Status and explicit return day |
| Requested kit | The set of item codes carried by one booking | No membership edit in this lesson; cancel the reserved booking and submit a new one |
| Availability view | No separate owned record | Derived from reserved and active bookings |
| Operator and patron | Roles in the story, not identities in the lab | Facts they need to inspect and decisions they make elsewhere |

A booking does not become a different object because its status changes. Nor does a new reservation for the same driver reuse the old booking ID. Those distinctions let the room keep history without confusing an old receipt with an active hold.

## Work the state machine, not the screen sequence

A **state** says what is true now about an entity relevant to a decision. A **transition** is a permitted move caused by an operation. It is not a claim that something physical happened because the software printed a receipt. The lab's worker decides when to record checkout or return. The simulation records the command, not a verified physical handoff.

The lab keeps four status values:

| Operation | Starts in | Ends in | What happens to the named tools |
| --- | --- | --- | --- |
| Reserve | No record with this ID; all requested tools are free | RESERVED | Hold every requested tool together |
| Check out | RESERVED | ACTIVE | Keep every hold |
| Cancel | RESERVED | CANCELLED | Release every hold; keep the historical record |
| Return | ACTIVE | RETURNED | Record return day and release every hold |

An unknown ID, duplicate ID or invalid starting state is rejected. “Cancel” is not interchangeable with “return” merely because both can free tools. Before a handoff, cancellation releases a commitment. After a handoff, the return records a date. A loader that restores only “free” flags could show the same shelf with no way to tell what happened. History and availability answer different questions.

The app does not infer expiry just because a due date has passed. A due day is one input to classifying a recorded return. The due day does not automatically cancel a reservation or return a tool someone still has. That would be a different requirement needing a clock, a rule for expiring holds, and a recovery policy. Do not invent it in a convenience function.

### An invariant has to survive disappointing input

An **invariant** is a rule that must hold in every accepted state, not just after a useful demonstration. In our case:

1. A booking ID names one record, even after cancellation or return.
2. Every booking has a nonempty set of distinct catalog tool IDs.
3. Reserved and active bookings hold all their named tools; canceled and returned ones hold none.
4. A tool is held by at most one reserved or active booking.
5. Only a returned booking has a return day. It is late if, and only if, that day is after its due day.

Rules 2 and 5 can be checked while looking at a single booking. Rule 4 compares bookings across a whole snapshot. A check located at only one row cannot see a second row that also holds CLAMP. A screen may warn the worker that a tool looks held, but the application still needs a guard at the point where it can commit the new state. In Chapter 3 you will make the check and successful change share a transaction boundary.

The rule about late return is narrow on purpose. The date with no time is known as a calendar day, not as a timestamp. May 12 <= May 12 for the on-time condition. A return on May 13 is late for a due day of May 12. An agreed close-of-day policy would be a different model with time and a local zone. The exercise does not calculate fines, infer actual handoff times or diagnose missing tools. We leave those blank rather than making the code guess.

## Work a table strong enough to catch a partial update

Begin with no bookings. Predict all outcomes unaided.

| Step | Command | Required result | Holds after command |
| --- | --- | --- | --- |
| 1 | reserve R1 2026-05-12 LEVEL | Create R1 reserved with LEVEL | LEVEL by R1 |
| 2 | reserve R2 2026-05-12 DRIVER LEVEL | Reject the whole kit because LEVEL is held | LEVEL by R1 only |
| 3 | reserve R3 2026-05-12 DRIVER | Create R3 reserved with DRIVER | DRIVER by R3; LEVEL by R1 |
| 4 | checkout R1 | R1 active; do not release LEVEL | DRIVER by R3; LEVEL by R1 |
| 5 | cancel R1 | Reject; R1 is no longer a reservation awaiting pickup | DRIVER by R3; LEVEL by R1 |
| 6 | return R1 2026-05-12 | R1 returned on the due day, not late | DRIVER by R3 only |
| 7 | cancel R3 | R3 cancelled; keep its record | None |

Step 2 is the smallest evidence that the kit was treated as one request, especially when followed by step 3. A wrong implementation might hold DRIVER first and return an error after discovering that LEVEL is busy. It could even remove R2 from the visible booking list while retaining a separate “held” flag for DRIVER. You need to check the list and the next use of the tool. “The error was red” cannot prove either property.

At the last step, neither R1 nor R3 goes away. Their history does not participate in holding the tools. The learner can now ask “Was level returned on time?” and “Which tools can be reserved?” independently. If the code stores a separate editable “available” list, both paths have to write it correctly forever. The lab uses the booking status and its membership to derive whether the item is held. With three tools and small histories, this keeps one authoritative state. A larger application may need an index, but that index needs a consistency rule and tests of its own.

When the same person wants a different list next time, change the display, not the fact. Sort data by the booking ID in one view and partition it by current status in another. Neither act writes a booking. The design gives the next programmer a simple first question: where is the truth, and is this code supposed to change it?

### Decide the cost of keeping the model small

This lab intentionally forbids editing a kit after reservation. Adding a new item with an existing hold may turn a simple guard into a combined change: release some holds, take others and keep the old kit if any new item is unavailable. You can support that, but the change is not “let the screen send a second selection.” Specify identity, old and new membership, whether a quoted reservation can change, and what is observable after failure.

The same discipline applies to a new actor request, such as a late-return exception. Lateness is recorded as a comparison, not a punishment. A proposed exception might record a waived administrative consequence without falsifying the dates. If changing a business rule requires the program to lie about the past, you're storing too many decisions in one flag. The exercise doesn't add a waiver, but it gives you enough structure to ask what a new record would need to mean.

## Practice before you implement

1. **Warm-up:** Draw a status diagram. Mark which states hold tools and which allow a return day. Give one forbidden transition leading to a readable rejection.
2. **Warm-up:** Explain why an old booking can mention DRIVER while a second reservation legitimately holds DRIVER. Then identify a pair of states where the same two rows are impossible.
3. **Application:** Change the first table so that CLAMP is held instead of LEVEL. Design a kit request that fails only on its second tool. Predict the exact available subset afterward before checking.
4. **Application:** Write distinct outcomes for on-time return, late return, unknown booking, and known booking in the wrong state. Name what each decision needs to see.
5. **Synthesis:** A worker asks for a queue of unfilled requests. Add it on paper without counting queue entries as held tools. Show what will happen when the first request wants two items and only one became free.
6. **Challenge:** Design the smallest extension for replacing one tool in a reserved kit. Specify which states may attempt it, which identity and due day stay fixed, and what it would mean for the whole replacement to fail without changing the old kit. Keep the change local and imaginary.

Work the [first-attempt handout](../activity/FIRST-ATTEMPT.md) before running the app. In Chapter 2, put these decisions into a program without asking the interface or a file format to decide which booking is valid.
