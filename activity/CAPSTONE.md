# Capstone: defend the next desk change

Start unaided. Choose a small extension for the fictional repair room, such as a one-step replacement of a tool in a reserved kit, or a printable handover summary. Do not touch any real inventory or user data. Use a separate local copy and create a disposable state path. No external services are needed.

## Before you implement

- **Define the need.** Name the worker's question, the operation and the stable identity. Write the old invariants and one new rule. Draw an accepted and rejected path.
- **Predict failures.** Pick a concrete setup where a plausible partial fix could pass the happy path yet leave a tool held incorrectly. Record expected list, detail and inventory before running. Identify whether the operation is a read, a deliberate new state, or a no-op.
- **Set boundaries.** Say what belongs in presentation, what belongs in the domain/service, and what observations justify claims about persistence. If your change doesn't alter persistence, don't replace the store just to show a new framework.

## Implement and verify

Use a bounded change. Record your original reference version, exact input, output and observed state. Run the supplied Java tests, then add focused checks for your accepted, blocked and re-opened paths. If you add a read-only summary, prove that producing it doesn't write the underlying state. If you add a compound operation, preserve the prior snapshot on a known pre-write failure and verify the unaffected tool through another request. Do not call it crash-safe merely because a planned exception worked.

Optional AI may suggest a counterexample or compare one small synthetic diff **after** your own first attempt. Keep the original design in your notes. Record what suggestion you accepted or rejected, why, and an independent check. Do not put credentials, records belonging to anyone else, or actual desk data in a prompt.

## What to hand to a reviewer

A one-page specification, domain/interaction boundary, runnable reproducible test, receipts for the negative and familiar paths, and a short note naming what you did not verify. Let the reviewer introduce a fresh case after you freeze the change. Independently explain the state that should result, which property would make the patch wrong, and what trace lets them reproduce your response. A complete program is useful. A small program whose behavior someone else can defend is a better submission.

| Assessment dimension | What a reviewer can check |
| --- | --- |
| Model and scope | Stable IDs, correct status and derived state, unknowns not invented |
| Transaction | New state happens only under declared conditions; known failure doesn't leak partial effects |
| Flow | A person can reach valid and recovery paths with readable receipts |
| Regression | A plausible wrong implementation fails independently set checks |
| Transfer and honesty | Fresh case explained unaided; evidence and limits named separately |
