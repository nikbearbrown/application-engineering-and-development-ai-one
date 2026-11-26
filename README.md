# Application Engineering and Development (AI Plus One)

## About the book

This five-chapter rough draft teaches readers with basic programming experience to turn a repair-room lending need into a small, testable application. You will model reservations, place rules and persistence deliberately, test a transaction, and defend a change in a user flow. The optional lab uses Java 17 or newer.

## Start reading

Start with [Turn a user need into a domain model](chapters/01-model-the-work.md). Use the [complete chapter guide](#chapter-guide) to find every chapter in reading order.

## Chapter guide

### Part 1 · Model and protect the transaction

1. [Turn a user need into a domain model](chapters/01-model-the-work.md): Translate a real user need into objects, states and a rule you can test.
2. [Place responsibilities before adding a screen](chapters/02-place-responsibilities.md): Place the domain rule, application action and storage responsibilities deliberately.
3. [Check the whole transaction](chapters/03-check-the-whole-transaction.md): Check both success and failure without allowing half a transaction to persist.

### Part 2 · Design and revise the interaction

4. [Design the user flow](chapters/04-design-the-user-flow.md): Trace a person’s journey through common and failed interactions.
5. [Change with evidence](chapters/05-change-with-evidence.md): Change one rule or feature and use reproducible checks to show what remains true.

## How to teach or study it

Use the [first-attempt activity](activity/FIRST-ATTEMPT.md) before reading the implementation. The model gives the rules a home before any screen exists; transactions and user flow then show how the same rules survive failure. Demonstrate learning with a tested change and a defense in the [capstone](activity/CAPSTONE.md). The lab is disposable; it is not a real inventory system.

## What’s in this repo

| Resource | Link | Coverage | Status |
| --- | --- | --- | --- |
| Manuscript | [README.md](chapters/README.md) | 5 written chapters | Compact rough draft |
| Exercises and assessment | [README.md](activity/README.md) | 2 documents: first attempt and capstone | Draft |
| Code | [README.md](lab/README.md), [README.md](tests/README.md) | 1 Java application and 1 test source | Java 17+ |
| Datasets | [README.md](lab/README.md) | Synthetic example session and local state produced by the lab | No standalone dataset found |
| Blueprint or syllabus | — | — | Not found |
| Slides | — | — | Not found |
| Standalone quizzes | — | — | Not found |
| Separate answer-key file | — | — | Not found |
| Figure assets | — | — | Not found |
| Videos | — | — | Not found |
| Web or download edition | — | — | Not found |

## Credits, license, and corrections

No license file was found; public access does not establish reuse rights. Report errors in the [repository issues](https://github.com/nikbearbrown/application-engineering-and-development-ai-one/issues), with the chapter and a precise location.
