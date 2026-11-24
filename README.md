# Application Engineering and Development (AI Plus One)

**Compact working draft.** Design and verify one small application from a user need, not from a framework checklist. Read the first chapter with pencil and paper; then follow the same repair-room workflow through Java objects, a whole-state save, keyboard commands, and regression evidence. The examples and data are fictional. This draft does not claim classroom validation, expert review, or replacement of an existing course or book.

This is for readers who can read basic Java loops and classes. It isn't a Java language reference, a catalog of object-oriented patterns, a large web-service design guide, or a coding-agent manual. The small command interface makes the product behavior inspectable without requiring a GUI, account or external package.

| Chapter | Reader outcome |
| --- | --- |
| 1. [Turn a user need into a domain model](chapters/01-model-the-work.md) | Specify actors, identity, status and invariants |
| 2. [Place responsibilities](chapters/02-place-responsibilities.md) | Keep application rules independent of presentation and storage implementations |
| 3. [Check the whole transaction](chapters/03-check-the-whole-transaction.md) | Detect partial updates, distinguish errors and explain coordination limits |
| 4. [Design the user flow](chapters/04-design-the-user-flow.md) | Make empty, valid, canceled and wrong-state outcomes legible |
| 5. [Change with evidence](chapters/05-change-with-evidence.md) | Verify a compound feature and protect earlier behavior |

Do the [first-attempt handout](activity/FIRST-ATTEMPT.md) before opening the worked answers. The [capstone](activity/CAPSTONE.md) asks you to defend a small change and an unaided new case. Any AI assistance is optional and comes after your first attempt.

## Run the fictional desk and its checks

Install or use an existing JDK 17 or newer. No build tool, third-party packages, model account or network is needed once the JDK is available. From this repository root:

```sh
build=$(mktemp -d)
area=$(mktemp -d)
javac --release 17 -Xlint:all -Werror -d "$build" lab/LendingDesk.java tests/DeskTests.java
java -cp "$build" desk.DeskTests
java -cp "$build" desk.LendingDesk --state "$area/session.tsv"
```

Type one line per command. The app doesn't print a prompt before each line. Type `quit` to leave. Use a **new disposable local directory**, not a shared or real state file.

```text
list
inventory
reserve R1 2026-05-12 DRIVER LEVEL
show R1
checkout R1
cancel R1
return R1 2026-05-13
inventory
show R1
quit
```

The initial list is empty. The two named tools move together. Cancellation fails after checkout; the next recorded return succeeds and is one calendar day late. Inventory shows both tools free, with R1 retained in history. The lab won't make a real loan, send a message or infer permission from a command. To inspect ordinary reopen behavior, start another session with the same `--state` path after quitting the first one, and type `show R1`. Use a different empty directory for a new exercise.

## What the code does and doesn't claim

The domain model keeps immutable snapshots. The file adapter uses a stable lock for cooperating sessions on one tested local filesystem, and stages a complete replacement rather than falling back to a non-atomic move. Test the exact local execution before trusting it on another setup. Unsupported atomic replacement or a storage failure stops the session and asks you to inspect saved state. The lab does not demonstrate distributed locking, recovery from power loss, concurrent noncooperating programs, authentication, or assistive-technology compliance. Don't use it to manage real equipment.

The original text and lab do not import existing textbooks, course materials, proprietary illustrations, or student work. Public primary references are linked in the chapters.
