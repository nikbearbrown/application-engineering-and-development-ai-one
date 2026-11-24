package desk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static desk.LendingDesk.ProblemCode.*;
import static desk.LendingDesk.Status.*;

public final class DeskTests {
    private static final LocalDate DUE = LocalDate.of(2026, 5, 12);
    private static int passed;

    interface Check { void run() throws Exception; }

    private static void test(String name, Check c) throws Exception {
        c.run();
        passed++;
        System.out.println("PASS " + name);
    }

    private static void eq(Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError("Expected " + expected + " but saw " + actual);
        }
    }

    private static void must(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void expect(LendingDesk.ProblemCode code, Check step) throws Exception {
        try { step.run(); }
        catch (LendingDesk.Problem e) { eq(e.code, code); return; }
        throw new AssertionError("Expected domain rejection: " + code);
    }

    private static LendingDesk.DeskService fresh() throws IOException {
        return new LendingDesk.DeskService(new LendingDesk.MemoryStore());
    }

    private static void emptyAndValidation() throws Exception {
        var desk = fresh();
        eq(desk.snapshot().loans().size(), 0);
        expect(INVALID, () -> desk.reserve("invalid id", DUE, List.of("CLAMP")));
        expect(UNKNOWN_ITEM, () -> desk.reserve("R1", DUE, List.of("DRIVER", "UNKNOWN")));
        expect(INVALID, () -> desk.reserve("R1", DUE, List.of("CLAMP", "CLAMP")));
        expect(NOT_FOUND, () -> desk.cancel("R3"));
        expect(INVALID, () -> desk.reserve("R1", LocalDate.of(0, 1, 1), List.of("CLAMP")));
        eq(desk.snapshot().loans().size(), 0);
    }

    private static void cancellationReleasesButRetainsHistory() throws Exception {
        var desk = fresh();
        desk.reserve("R1", DUE, List.of("CLAMP", "DRIVER"));
        eq(desk.snapshot().holder("CLAMP"), "R1");
        eq(desk.cancel("R1").status(), CANCELLED);
        eq(desk.snapshot().holder("CLAMP"), null);
        expect(WRONG_STATE, () -> desk.checkout("R1"));
        expect(DUPLICATE_BOOKING, () -> desk.reserve("R1", DUE, List.of("CLAMP")));
        desk.reserve("R2", DUE, List.of("CLAMP"));
        eq(desk.snapshot().orderedLoans().size(), 2);
        eq(desk.show("R1").status(), CANCELLED);
    }

    private static void checkoutAndLateBoundary() throws Exception {
        var desk = fresh();
        desk.reserve("R1", DUE, List.of("LEVEL"));
        expect(WRONG_STATE, () -> desk.returnLoan("R1", DUE));
        desk.checkout("R1");
        expect(WRONG_STATE, () -> desk.cancel("R1"));
        eq(desk.snapshot().holder("LEVEL"), "R1");
        eq(desk.returnLoan("R1", DUE).late(), false);
        expect(WRONG_STATE, () -> desk.returnLoan("R1", DUE.plusDays(1)));
        desk.reserve("R2", DUE, List.of("LEVEL"));
        desk.checkout("R2");
        eq(desk.returnLoan("R2", DUE.plusDays(1)).late(), true);
        eq(desk.snapshot().holder("LEVEL"), null);
    }

    private static void kitConflictDoesNotLeakFirstHold() throws Exception {
        var desk = fresh();
        desk.reserve("R1", DUE, List.of("LEVEL"));
        var before = desk.snapshot();
        expect(ITEM_BUSY, () -> desk.reserve("R2", DUE, List.of("DRIVER", "LEVEL")));
        eq(desk.snapshot(), before);
        eq(desk.snapshot().holder("DRIVER"), null);
        eq(desk.reserve("R3", DUE, List.of("DRIVER")).status(), RESERVED);
        eq(desk.snapshot().holder("LEVEL"), "R1");
    }

    private static final class FaultStore implements LendingDesk.Store {
        private LendingDesk.State saved = LendingDesk.State.empty();
        private boolean fail;
        @Override public LendingDesk.State read() { return saved; }
        @Override public void write(LendingDesk.State next) throws IOException {
            if (fail) throw new IOException("controlled failure before store write");
            saved = next;
        }
    }

    private static void knownPreWriteFailureStaysOld() throws Exception {
        var store = new FaultStore();
        var desk = new LendingDesk.DeskService(store);
        desk.reserve("R1", DUE, List.of("CLAMP", "LEVEL"));
        var before = desk.snapshot();
        store.fail = true;
        try { desk.cancel("R1"); throw new AssertionError("expected injected failure"); }
        catch (IOException expected) {
            eq(desk.snapshot(), before);
            eq(store.read(), before);
            eq(desk.snapshot().holder("LEVEL"), "R1");
        }
        store.fail = false;
        eq(desk.cancel("R1").status(), CANCELLED);
        eq(desk.snapshot().holder("LEVEL"), null);
    }

    private static void singleServiceRace() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 15; round++) {
                var desk = fresh();
                var ready = new CountDownLatch(2);
                var gate = new CountDownLatch(1);
                var f1 = pool.submit(() -> contest(desk, "R1", ready, gate));
                var f2 = pool.submit(() -> contest(desk, "R2", ready, gate));
                must(ready.await(4, TimeUnit.SECONDS), "workers failed to align");
                gate.countDown();
                var outcomes = List.of(f1.get(4, TimeUnit.SECONDS), f2.get(4, TimeUnit.SECONDS));
                eq(outcomes.stream().filter(s -> s.equals("won")).count(), 1L);
                eq(outcomes.stream().filter(s -> s.equals("ITEM_BUSY")).count(), 1L);
                eq(desk.snapshot().loans().size(), 1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static String contest(LendingDesk.DeskService desk, String id,
                                  CountDownLatch ready, CountDownLatch gate) throws Exception {
        ready.countDown();
        must(gate.await(4, TimeUnit.SECONDS), "race start timeout");
        try { desk.reserve(id, DUE, List.of("DRIVER")); return "won"; }
        catch (LendingDesk.Problem e) { return e.code.name(); }
    }

    private static void exerciseStore(LendingDesk.Store store) throws Exception {
        var desk = new LendingDesk.DeskService(store);
        desk.reserve("R1", DUE, List.of("DRIVER", "LEVEL"));
        desk.checkout("R1");
        desk.returnLoan("R1", DUE.plusDays(1));
        // Handoff: the first service is not used after the next service loads its saved state.
        var reopened = new LendingDesk.DeskService(store);
        eq(reopened.show("R1").items(), List.of("DRIVER", "LEVEL"));
        eq(reopened.show("R1").status(), RETURNED);
        eq(reopened.show("R1").late(), true);
        eq(reopened.snapshot().holder("DRIVER"), null);
    }

    private static void bothAdaptersStoreContract(Path dir) throws Exception {
        exerciseStore(new LendingDesk.MemoryStore());
        var state = dir.resolve("adapter.tsv");
        try (var file = LendingDesk.FileStore.open(state)) { exerciseStore(file); }
        try (var reopened = LendingDesk.FileStore.open(state)) {
            eq(reopened.read().loans().get("R1").status(), RETURNED);
        }
    }

    record RunResult(int status, String text) {}

    private static Process cliProcess(Path state) throws IOException {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "desk.LendingDesk",
                "--state", state.toString()).redirectErrorStream(true).start();
    }

    private static RunResult cli(Path state, String command) throws Exception {
        var proc = cliProcess(state);
        try {
            try (var in = proc.getOutputStream()) { in.write(command.getBytes(StandardCharsets.UTF_8)); }
            must(proc.waitFor(8, TimeUnit.SECONDS), "CLI timed out");
            return new RunResult(proc.exitValue(), new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } finally { proc.destroyForcibly(); }
    }

    private static void cliRestartAndWrongState(Path dir) throws Exception {
        var file = dir.resolve("session.tsv");
        var a = cli(file, "list\nreserve R1 2026-02-30 CLAMP\nlist\nreserve R1 2026-05-12 DRIVER LEVEL\n"
                + "show R1\ncheckout R1\nshow R1\ncancel R1\nreturn R1 2026-05-13\nlist\nquit\n");
        eq(a.status(), 0);
        must(a.text().lines().filter(s -> s.startsWith("NO BOOKINGS")).count() == 2, a.text());
        must(a.text().contains("ERROR INVALID: That is not a valid calendar day."), a.text());
        must(a.text().contains("ERROR WRONG_STATE:"), a.text());
        must(a.text().contains("Next: checkout R1 or cancel R1."), a.text());
        must(a.text().contains("Next: return R1 YYYY-MM-DD"), a.text());
        must(a.text().contains("RETURNED R1 items=DRIVER,LEVEL due=2026-05-12 returned=2026-05-13 late=true"), a.text());
        var b = cli(file, "show R1\ninventory\nquit\n");
        eq(b.status(), 0);
        must(b.text().contains("RETURNED R1") && b.text().contains("DRIVER FREE"), b.text());
    }

    private static void corruptFileFailClosed(Path dir) throws Exception {
        var path = dir.resolve("bad.tsv");
        var text = "tool-desk-v1\nR1\tDRIVER\t2026-05-12\tRESERVED\t-\n"
                + "R2\tDRIVER\t2026-05-12\tACTIVE\t-\n";
        Files.writeString(path, text);
        var bad = cli(path, "reserve R3 2026-05-12 CLAMP\n");
        eq(bad.status(), 2);
        must(bad.text().startsWith("ERROR STORAGE:"), bad.text());
        eq(Files.readString(path), text);
    }

    private static void concurrentSessionsStaySeparate(Path dir) throws Exception {
        var path = dir.resolve("locked.tsv");
        var owner = cliProcess(path);
        var waiters = Executors.newSingleThreadExecutor();
        try {
            var in = owner.getOutputStream();
            in.write("reserve R1 2026-05-12 CLAMP\n".getBytes(StandardCharsets.UTF_8));
            in.flush();
            var reader = owner.inputReader(StandardCharsets.UTF_8);
            String first = waiters.submit(reader::readLine).get(6, TimeUnit.SECONDS);
            must(first.startsWith("OK RESERVED R1"), first);
            // First process retains the lock while waiting for the next input line.
            var other = cli(path, "reserve R2 2026-05-12 CLAMP\nquit\n");
            eq(other.status(), 2);
            must(other.text().startsWith("ERROR STORE_BUSY:"), other.text());
            in.write("quit\n".getBytes(StandardCharsets.UTF_8));
            in.close();
            must(owner.waitFor(8, TimeUnit.SECONDS), "first process did not close");
            var after = cli(path, "list\nquit\n");
            eq(after.status(), 0);
            must(after.text().contains("RESERVED R1") && !after.text().contains("R2"), after.text());
        } finally {
            owner.destroyForcibly();
            waiters.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("lending-desk-tests-");
        try {
            test("empty state, validation and rejected commands", DeskTests::emptyAndValidation);
            test("cancellation releases but retains identity/history", DeskTests::cancellationReleasesButRetainsHistory);
            test("checkout, wrong-state guards and exact due-day", DeskTests::checkoutAndLateBoundary);
            test("failed kit must not partially hold candidate items", DeskTests::kitConflictDoesNotLeakFirstHold);
            test("forced pre-write failure preserves memory and saved data", DeskTests::knownPreWriteFailureStaysOld);
            test("two synchronized requests have exactly one winner", DeskTests::singleServiceRace);
            test("memory and file adapters pass the same saved-state contract", () -> bothAdaptersStoreContract(dir));
            test("documented CLI entry point rejects input and persists across restart", () -> cliRestartAndWrongState(dir));
            test("invalid persisted records fail without overwrite", () -> corruptFileFailClosed(dir));
            test("second CLI process does not bypass the stable local lock", () -> concurrentSessionsStaySeparate(dir));
            System.out.println(passed + " tests passed.");
        } finally {
            try (var paths = Files.walk(dir)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
