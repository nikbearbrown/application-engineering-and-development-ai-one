# Lab

Runnable Java lending desk. Run it with disposable local state. Start with [this file](LendingDesk.java).

[Book overview](../README.md).

## Run a disposable session

From the repository root with Java 17 or newer:

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
