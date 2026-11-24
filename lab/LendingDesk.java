package desk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

/** An original local repair-room lending example. No real equipment, identities or services. */
public final class LendingDesk {
    static final List<String> CATALOG = List.of("CLAMP", "DRIVER", "LEVEL");
    static final String HEADER = "tool-desk-v1";

    private LendingDesk() {}

    enum Status {
        RESERVED, ACTIVE, CANCELLED, RETURNED;
        boolean holdsTools() { return this == RESERVED || this == ACTIVE; }
    }

    record Loan(String id, List<String> items, LocalDate due, Status status, LocalDate returned) {
        Loan {
            if (!validId(id)) throw new IllegalArgumentException("invalid booking id");
            if (!validDay(due) || (returned != null && !validDay(returned))) {
                throw new IllegalArgumentException("day out of supported range");
            }
            Objects.requireNonNull(status, "status");
            if (items.isEmpty() || !CATALOG.containsAll(items)
                    || new HashSet<>(items).size() != items.size()) {
                throw new IllegalArgumentException("unknown, empty or duplicate item list");
            }
            items = List.copyOf(new TreeSet<>(items));
            if ((status == Status.RETURNED) != (returned != null)) {
                throw new IllegalArgumentException("a return day belongs only on a returned booking");
            }
        }

        Loan next(Status to, LocalDate on) { return new Loan(id, items, due, to, on); }
        boolean late() { return returned != null && returned.isAfter(due); }
    }

    record State(Map<String, Loan> loans) {
        State {
            loans = Map.copyOf(loans);
            var held = new HashSet<String>();
            for (var entry : loans.entrySet()) {
                var loan = entry.getValue();
                if (!entry.getKey().equals(loan.id())) throw new IllegalArgumentException("miskeyed loan");
                if (loan.status().holdsTools()) {
                    for (var item : loan.items()) {
                        if (!held.add(item)) throw new IllegalArgumentException("overlapping holds");
                    }
                }
            }
        }

        static State empty() { return new State(Map.of()); }
        State replace(Loan loan) {
            var next = new TreeMap<>(loans);
            next.put(loan.id(), loan);
            return new State(next);
        }
        List<Loan> orderedLoans() { return List.copyOf(new TreeMap<>(loans).values()); }

        String holder(String item) {
            for (var loan : loans.values()) {
                if (loan.status().holdsTools() && loan.items().contains(item)) return loan.id();
            }
            return null;
        }
    }

    interface Store {
        State read() throws IOException;
        void write(State replacement) throws IOException;
    }

    /** Stores immutable snapshots; use one DeskService per MemoryStore. */
    static final class MemoryStore implements Store {
        private State saved = State.empty();

        @Override public State read() { return saved; }
        @Override public void write(State replacement) { saved = replacement; }
    }

    enum ProblemCode { INVALID, UNKNOWN_ITEM, ITEM_BUSY, DUPLICATE_BOOKING, NOT_FOUND, WRONG_STATE }

    static final class Problem extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final ProblemCode code;
        Problem(ProblemCode code, String message) { super(message); this.code = code; }
    }

    /** The monitor makes check-and-write one unit for threads using this same service. */
    static final class DeskService {
        private final Store store;
        private State state;

        DeskService(Store store) throws IOException {
            this.store = Objects.requireNonNull(store);
            this.state = store.read();
        }

        synchronized State snapshot() { return state; }

        synchronized Loan show(String id) {
            if (!validId(id)) throw new Problem(ProblemCode.INVALID, "Use a simple uppercase booking ID.");
            var result = state.loans().get(id);
            if (result == null) throw new Problem(ProblemCode.NOT_FOUND, "No booking " + id + ". Use list.");
            return result;
        }

        synchronized Loan reserve(String id, LocalDate due, List<String> items) throws IOException {
            if (!validId(id) || !validDay(due) || items == null || items.isEmpty()) {
                throw new Problem(ProblemCode.INVALID, "Provide booking ID, due day and at least one item.");
            }
            if (state.loans().containsKey(id)) {
                throw new Problem(ProblemCode.DUPLICATE_BOOKING, "Booking IDs are retained. Use a new ID.");
            }
            var seen = new HashSet<String>();
            for (var item : items) {
                if (item == null || !CATALOG.contains(item)) {
                    throw new Problem(ProblemCode.UNKNOWN_ITEM, "Item is not in this catalog. Use inventory.");
                }
                if (!seen.add(item)) {
                    throw new Problem(ProblemCode.INVALID, "Do not repeat an item in one booking.");
                }
                if (state.holder(item) != null) {
                    throw new Problem(ProblemCode.ITEM_BUSY, item + " is held; no part of this booking was created.");
                }
            }
            return publish(new Loan(id, items, due, Status.RESERVED, null));
        }

        synchronized Loan checkout(String id) throws IOException {
            var loan = require(id, Status.RESERVED, "Check out a reserved booking.");
            return publish(loan.next(Status.ACTIVE, null));
        }

        synchronized Loan cancel(String id) throws IOException {
            var loan = require(id, Status.RESERVED, "Only a reserved booking can be cancelled.");
            return publish(loan.next(Status.CANCELLED, null));
        }

        synchronized Loan returnLoan(String id, LocalDate day) throws IOException {
            if (!validDay(day)) throw new Problem(ProblemCode.INVALID, "Provide a valid return day.");
            var loan = require(id, Status.ACTIVE, "Return an active booking.");
            return publish(loan.next(Status.RETURNED, day));
        }

        private Loan require(String id, Status status, String guidance) {
            var loan = show(id);
            if (loan.status() != status) {
                throw new Problem(ProblemCode.WRONG_STATE, guidance + " Current status: " + loan.status() + ".");
            }
            return loan;
        }

        private Loan publish(Loan loan) throws IOException {
            var replacement = state.replace(loan);
            store.write(replacement);
            state = replacement;
            return loan;
        }
    }

    static final class BusyStore extends IOException {
        private static final long serialVersionUID = 1L;
    }

    /** Demonstration for one local filesystem, not a distributed or crash-durable database. */
    static final class FileStore implements Store, AutoCloseable {
        private final Path path;
        private final FileChannel channel;
        private final FileLock lock;

        private FileStore(Path path, FileChannel channel, FileLock lock) {
            this.path = path; this.channel = channel; this.lock = lock;
        }

        static FileStore open(Path path) throws IOException {
            path = path.toAbsolutePath().normalize();
            if (path.getFileName() == null) throw new IOException("Specify a state filename.");
            var lockPath = path.resolveSibling(path.getFileName().toString() + ".lock");
            var channel = FileChannel.open(lockPath, CREATE, WRITE);
            try {
                FileLock lock;
                try {
                    lock = channel.tryLock();
                } catch (OverlappingFileLockException e) {
                    throw new BusyStore();
                }
                if (lock == null) throw new BusyStore();
                return new FileStore(path, channel, lock);
            } catch (IOException | RuntimeException e) {
                try { channel.close(); } catch (IOException close) { e.addSuppressed(close); }
                throw e;
            }
        }

        @Override public State read() throws IOException {
            if (Files.notExists(path)) return State.empty();
            var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(0).equals(HEADER)) throw new IOException("Unrecognized state file.");
            Map<String, Loan> rows = new TreeMap<>();
            try {
                for (var line : lines.subList(1, lines.size())) {
                    var f = line.split("\t", -1);
                    if (f.length != 5) throw new IllegalArgumentException("wrong field count");
                    var loan = new Loan(f[0], List.of(f[1].split(",", -1)), parseDay(f[2]),
                            Status.valueOf(f[3]), f[4].equals("-") ? null : parseDay(f[4]));
                    if (rows.putIfAbsent(loan.id(), loan) != null) throw new IllegalArgumentException("duplicate id");
                }
                return new State(rows);
            } catch (IllegalArgumentException | Problem e) {
                throw new IOException("Invalid state file.", e);
            }
        }

        @Override public void write(State replacement) throws IOException {
            var text = new ArrayList<String>();
            text.add(HEADER);
            for (var row : replacement.orderedLoans()) {
                text.add(String.join("\t", row.id(), String.join(",", row.items()), row.due().toString(),
                        row.status().name(), row.returned() == null ? "-" : row.returned().toString()));
            }
            var temp = Files.createTempFile(path.getParent(), "desk-stage-", ".tmp");
            try {
                Files.write(temp, text, StandardCharsets.UTF_8);
                Files.move(temp, path, ATOMIC_MOVE, REPLACE_EXISTING);
            } catch (IOException e) {
                try { Files.deleteIfExists(temp); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
                throw e;
            }
        }

        @Override public void close() throws IOException {
            try { lock.release(); } finally { channel.close(); }
        }
    }

    static boolean validId(String id) { return id != null && id.matches("[A-Z][A-Z0-9-]{0,15}"); }

    static boolean validDay(LocalDate day) { return day != null && day.getYear() >= 1 && day.getYear() <= 9999; }

    static LocalDate parseDay(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new Problem(ProblemCode.INVALID, "Use a calendar day formatted YYYY-MM-DD.");
        }
        try {
            var day = LocalDate.parse(value);
            if (day.getYear() < 1) throw new DateTimeParseException("invalid year", value, 0);
            return day;
        } catch (DateTimeParseException e) {
            throw new Problem(ProblemCode.INVALID, "That is not a valid calendar day.");
        }
    }

    static String describe(Loan loan) {
        var out = loan.status() + " " + loan.id() + " items=" + String.join(",", loan.items()) + " due=" + loan.due();
        if (loan.returned() != null) out += " returned=" + loan.returned() + " late=" + loan.late();
        return out;
    }

    static void need(String[] words, int length) {
        if (words.length != length) throw new Problem(ProblemCode.INVALID, "Check the command arguments with help.");
    }

    static boolean handle(DeskService desk, String line, PrintStream out) throws IOException {
        if (line.isBlank()) return true;
        String[] w = line.strip().split("\\s+");
        switch (w[0]) {
            case "list" -> {
                need(w, 1);
                var loans = desk.snapshot().orderedLoans();
                if (loans.isEmpty()) out.println("NO BOOKINGS. Use inventory to see tools, then reserve.");
                else for (var loan : loans) out.println(describe(loan));
            }
            case "inventory" -> {
                need(w, 1);
                var state = desk.snapshot();
                for (var item : CATALOG) {
                    var id = state.holder(item);
                    out.println(item + (id == null ? " FREE" : " HELD by " + id));
                }
            }
            case "reserve" -> {
                if (w.length < 4) throw new Problem(ProblemCode.INVALID, "Usage: reserve ID YYYY-MM-DD ITEM [ITEM ...].");
                out.println("OK " + describe(desk.reserve(w[1], parseDay(w[2]), Arrays.asList(w).subList(3, w.length))));
            }
            case "show" -> {
                need(w, 2);
                var loan = desk.show(w[1]);
                out.println(describe(loan));
                switch (loan.status()) {
                    case RESERVED -> out.println("Next: checkout " + loan.id() + " or cancel " + loan.id() + ".");
                    case ACTIVE -> out.println("Next: return " + loan.id() + " YYYY-MM-DD after receiving the tools.");
                    case RETURNED, CANCELLED -> out.println("History only. No current hold.");
                }
            }
            case "checkout" -> { need(w, 2); out.println("OK " + describe(desk.checkout(w[1]))); }
            case "cancel" -> { need(w, 2); out.println("OK " + describe(desk.cancel(w[1]))); }
            case "return" -> { need(w, 3); out.println("OK " + describe(desk.returnLoan(w[1], parseDay(w[2])))); }
            case "help" -> {
                need(w, 1);
                out.println("Commands: inventory; list; show ID; reserve ID YYYY-MM-DD ITEM [ITEM ...];");
                out.println("          checkout ID; cancel ID; return ID YYYY-MM-DD; help; quit.");
            }
            case "quit" -> { need(w, 1); return false; }
            default -> throw new Problem(ProblemCode.INVALID, "Unknown command. Use help.");
        }
        return true;
    }

    static int run(String[] args, InputStream input, PrintStream out) {
        if (args.length != 2 || !args[0].equals("--state") || args[1].isBlank()) {
            out.println("Usage: java -cp BUILD desk.LendingDesk --state FILE");
            return 2;
        }
        try (var durable = FileStore.open(Path.of(args[1]))) {
            var desk = new DeskService(durable);
            var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    if (!handle(desk, line, out)) break;
                } catch (Problem e) {
                    out.println("ERROR " + e.code + ": " + e.getMessage());
                }
            }
            return 0;
        } catch (BusyStore e) {
            out.println("ERROR STORE_BUSY: another session holds this state. Close it before retrying.");
            return 2;
        } catch (IOException | IllegalArgumentException e) {
            out.println("ERROR STORAGE: state or change not confirmed. Stop and inspect the saved file before retrying.");
            return 2;
        }
    }

    public static void main(String[] args) { System.exit(run(args, System.in, System.out)); }
}
