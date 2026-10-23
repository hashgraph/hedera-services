// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.hashgraph.impl.test.fixtures.flicker;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes a {@link FlickerGraph} back out as {@code .graph} text. The inverse of {@link GraphParser}.
 * <p>
 * <b>Nothing in Phase 2 consumes this.</b> It is here for the round-trip test, which is the automatic detector for a
 * lossy format: if a field reaches an event's hash but has nowhere to live in the text, {@code parse → write → parse}
 * produces a different graph and the test says so. That check is worth more before fixtures are written against the
 * gap than after, which is why the writer precedes the exporter that will eventually use it.
 * <p>
 * <b>A value the reader can recompute is left out.</b> Three are:
 * <ul>
 *     <li>who created an event — the row has no column for it, because the name already says so: {@code b4} is
 *         created by node {@code b}.</li>
 *     <li>a birth round the event derives from its parents. Only a row that <i>raises</i> its birth round writes
 *         one.</li>
 *     <li>a default the graph never changed, which the header would otherwise restate from
 *         {@link GraphDefaults#STANDARD}.</li>
 * </ul>
 * Writing a derived value would let the text disagree with the derivation that produced it, and a file able to
 * contradict itself has two sources of truth.
 */
public final class GraphWriter {

    private static final String NONE = "-";
    private static final String GAP = "  ";
    private static final String INDENT = "   ";

    private GraphWriter() {}

    /**
     * Write a graph as {@code .graph} text.
     *
     * @param graph the graph
     * @return text that parses back to an equal graph
     * @throws IllegalArgumentException if an event's name does not start with its creator's letter, which the format
     *                                  cannot express
     */
    @NonNull
    public static String write(@NonNull final FlickerGraph graph) {
        final StringBuilder out = new StringBuilder();

        final int before = out.length();
        if (graph.kb() != null) {
            line(out, "kb " + graph.kb());
        }
        if (!graph.config().isEmpty()) {
            line(out, "config " + keyValues(graph.config()));
        }
        final String defaults = defaultsLine(graph.defaults());
        if (!defaults.isEmpty()) {
            line(out, "defaults " + defaults);
        }
        if (graph.anchorRound() != null) {
            line(out, "anchor " + graph.anchorRound());
        }
        if (out.length() > before) {
            out.append('\n');
        }

        final int rosterNameWidth =
                width(graph.rosters().stream().map(NamedRoster::name).toList(), 0);
        for (final NamedRoster roster : graph.rosters()) {
            line(out, "roster " + pad(roster.name(), rosterNameWidth) + GAP + weights(roster));
        }
        out.append('\n');

        writeRows(out, graph);
        return out.toString();
    }

    /**
     * Write the event rows, with the columns padded to line up and a comment naming them.
     * <p>
     * Rows come out in file order — the reverse of {@link FlickerGraph#specs()} — so that a parent is written below
     * its children and the text reads like the hashgraph it describes.
     */
    private static void writeRows(@NonNull final StringBuilder out, @NonNull final FlickerGraph graph) {
        final List<String> letters = graph.firstRoster().letters();
        final List<Row> rows = new ArrayList<>(graph.specs().size());
        for (final EventSpec spec : graph.specs().reversed()) {
            requireNameCarriesCreator(spec, letters, graph.firstRoster().name());
            rows.add(new Row(
                    spec.name(),
                    spec.selfParent() == null ? NONE : spec.selfParent(),
                    spec.otherParents().isEmpty() ? NONE : String.join(",", spec.otherParents()),
                    overrides(spec)));
        }

        final int nameWidth = width(rows.stream().map(Row::name).toList(), "name".length());
        final int selfWidth = width(rows.stream().map(Row::self).toList(), "self".length());
        final int othersWidth = width(rows.stream().map(Row::others).toList(), 0);
        line(out, "#" + GAP + pad("name", nameWidth) + GAP + pad("self", selfWidth) + GAP + "other-parents");
        for (final Row row : rows) {
            line(
                    out,
                    INDENT
                            + pad(row.name(), nameWidth)
                            + GAP
                            + pad(row.self(), selfWidth)
                            + GAP
                            + (row.overrides().isEmpty()
                                    ? row.others()
                                    : pad(row.others(), othersWidth) + GAP + row.overrides()));
        }
    }

    /**
     * The format carries an event's creator as the first letter of its name, so a spec whose creator index says
     * otherwise cannot be written. Only a spec built in code can be in that state; one that came from a file cannot.
     */
    private static void requireNameCarriesCreator(
            @NonNull final EventSpec spec, @NonNull final List<String> letters, @NonNull final String rosterName) {
        final int fromName = letters.indexOf(spec.name().substring(0, 1));
        if (fromName != spec.creator()) {
            throw new IllegalArgumentException(
                    ("'%s' is created by index %d, but the format reads the creator from the name's first letter, "
                                    + "which in roster '%s' (%s) is index %d. Rename the event after its creator.")
                            .formatted(spec.name(), spec.creator(), rosterName, letters, fromName));
        }
    }

    /**
     * Only the defaults a graph actually changed, so that the header states what is unusual rather than restating
     * {@link GraphDefaults#STANDARD}.
     */
    @NonNull
    private static String defaultsLine(@NonNull final GraphDefaults defaults) {
        final List<String> parts = new ArrayList<>(3);
        if (defaults.coin() != GraphDefaults.STANDARD.coin()) {
            parts.add("coin=" + defaults.coin());
        }
        if (defaults.birthRound() != GraphDefaults.STANDARD.birthRound()) {
            parts.add("br=" + defaults.birthRound());
        }
        if (defaults.transactionCount() != GraphDefaults.STANDARD.transactionCount()) {
            parts.add("txs=" + defaults.transactionCount());
        }
        return String.join(" ", parts);
    }

    @NonNull
    private static String overrides(@NonNull final EventSpec spec) {
        final List<String> parts = new ArrayList<>(3);
        if (spec.coin() != null) {
            parts.add("coin=" + spec.coin());
        }
        if (spec.birthRound() != null) {
            parts.add("br=" + spec.birthRound());
        }
        if (spec.transactionCount() != null) {
            parts.add("txs=" + (spec.transactionCount() == 0 ? "none" : spec.transactionCount()));
        }
        return String.join(" ", parts);
    }

    @NonNull
    private static String weights(@NonNull final NamedRoster roster) {
        final List<String> parts = new ArrayList<>(roster.weightsByLetter().size());
        roster.weightsByLetter().forEach((letter, weight) -> parts.add(letter + "=" + weight));
        return String.join(" ", parts);
    }

    @NonNull
    private static String keyValues(@NonNull final Map<String, String> values) {
        final List<String> parts = new ArrayList<>(values.size());
        values.forEach((key, value) -> parts.add(key + "=" + value));
        return String.join(" ", parts);
    }

    private static int width(@NonNull final List<String> values, final int atLeast) {
        return Math.max(atLeast, values.stream().mapToInt(String::length).max().orElse(0));
    }

    @NonNull
    private static String pad(@NonNull final String value, final int width) {
        return value + " ".repeat(Math.max(0, width - value.length()));
    }

    /** Appends a line with its trailing padding removed, so that alignment never leaves whitespace at line ends. */
    private static void line(@NonNull final StringBuilder out, @NonNull final String text) {
        out.append(text.stripTrailing()).append('\n');
    }

    /** One row's cells, already rendered. */
    private record Row(
            @NonNull String name,
            @NonNull String self,
            @NonNull String others,
            @NonNull String overrides) {}
}
