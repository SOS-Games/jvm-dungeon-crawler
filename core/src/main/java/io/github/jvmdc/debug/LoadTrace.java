package io.github.jvmdc.debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds up how long each named step of a load takes.
 * Nested steps are counted on their own, so the lines do not overlap.
 */
public final class LoadTrace {
    private final Map<String, Row> rows = new LinkedHashMap<>();
    private final ArrayDeque<Frame> stack = new ArrayDeque<>();
    private final long started = System.nanoTime();
    public int done;
    public int total;
    private long slowestChunk;

    public void begin(String name) {
        stack.push(new Frame(name, System.nanoTime()));
    }

    public void end() {
        Frame frame = stack.pop();
        long elapsed = System.nanoTime() - frame.started;
        add(frame.name, elapsed - frame.child);
        if (!stack.isEmpty()) {
            stack.peek().child += elapsed;
        }
    }

    public void time(String name, Runnable work) {
        begin(name);
        try {
            work.run();
        } finally {
            end();
        }
    }

    public void add(String name, long nanos) {
        Row row = rows.get(name);
        if (row == null) {
            row = new Row(name);
            rows.put(name, row);
        }
        row.nanos += Math.max(0L, nanos);
        row.calls++;
    }

    public void noteChunk(long nanos) {
        if (nanos > slowestChunk) {
            slowestChunk = nanos;
        }
    }

    /** Slowest steps first, plus how far the chunk loop has got. */
    public String report() {
        StringBuilder out = new StringBuilder();
        if (total > 0) {
            out.append("chunk ").append(done).append(" of ").append(total).append('\n');
        }
        out.append("elapsed ").append(format(System.nanoTime() - started)).append('\n');
        if (slowestChunk > 0L) {
            out.append("slowest chunk ").append(format(slowestChunk)).append('\n');
        }
        List<Row> ordered = new ArrayList<>(rows.values());
        ordered.sort(Comparator.comparingLong((Row row) -> row.nanos).reversed());
        long summed = 0L;
        for (Row row : ordered) {
            out.append(pad(row.name)).append(format(row.nanos)).append("   x").append(row.calls).append('\n');
            summed += row.nanos;
        }
        long other = System.nanoTime() - started - summed;
        if (other > 1_000_000L) {
            out.append(pad("other")).append(format(other)).append('\n');
        }
        return out.toString().stripTrailing();
    }

    private static String pad(String name) {
        if (name.length() >= 14) {
            return name + "  ";
        }
        return name + " ".repeat(14 - name.length());
    }

    private static String format(long nanos) {
        double ms = nanos / 1_000_000.0;
        if (ms >= 100.0) {
            return String.format("%.0f ms", ms);
        }
        if (ms >= 10.0) {
            return String.format("%.1f ms", ms);
        }
        return String.format("%.2f ms", ms);
    }

    private static final class Frame {
        final String name;
        final long started;
        long child;

        Frame(String name, long started) {
            this.name = name;
            this.started = started;
        }
    }

    private static final class Row {
        final String name;
        long nanos;
        int calls;

        Row(String name) {
            this.name = name;
        }
    }
}
