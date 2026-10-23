// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.hashgraph.impl.test.fixtures.flicker;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.hiero.consensus.model.roster.RosterWrapper;

/**
 * A parsed {@code .graph} file: the header, and the events in the order they must be fed.
 * <p>
 * The specs are topological — parents first — which is the reverse of the file's rows. A file is written the way a
 * hashgraph is drawn, with genesis at the bottom, so that it reads like the diagram it describes.
 *
 * @param kb          the knowledge-base entry this fixture reproduces, or null if it names none
 * @param config      consensus configuration keys the fixture needs, as written
 * @param defaults    the per-event fields a row does not override
 * @param anchorRound the round to take a snapshot at for a two-phase replay, or null for a single-phase fixture
 * @param rosters     every roster block, in declaration order; creator indices are positions in the first one
 * @param specs       the events, topologically ordered
 */
public record FlickerGraph(
        @Nullable String kb,
        @NonNull Map<String, String> config,
        @NonNull GraphDefaults defaults,
        @Nullable Long anchorRound,
        @NonNull List<NamedRoster> rosters,
        @NonNull List<EventSpec> specs) {

    public FlickerGraph {
        config = Collections.unmodifiableMap(new LinkedHashMap<>(requireNonNull(config, "config")));
        requireNonNull(defaults, "defaults must not be null");
        rosters = List.copyOf(requireNonNull(rosters, "rosters must not be null"));
        specs = List.copyOf(requireNonNull(specs, "specs must not be null"));
    }

    /**
     * @param name the name a roster block was declared with
     * @return that roster
     * @throws IllegalArgumentException if the file declares no roster by that name
     */
    @NonNull
    public NamedRoster roster(@NonNull final String name) {
        return rosters.stream()
                .filter(roster -> roster.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no roster named '%s'; this graph declares %s"
                        .formatted(name, rosters.stream().map(NamedRoster::name).toList())));
    }

    /**
     * @return the roster a creator index is a position in
     */
    @NonNull
    public NamedRoster firstRoster() {
        return rosters.getFirst();
    }

    /**
     * Build this graph's events.
     * <p>
     * The {@link Random} is fixed because it no longer reaches any hash — every hashed field is specified — so a seed
     * would be a knob that changes nothing a fixture can observe.
     *
     * @param roster the roster to build under, which must order its nodes the same way the graph's first one does
     * @return the events in feed order, each paired with its name
     */
    @NonNull
    public List<NamedEvent> build(@NonNull final RosterWrapper roster) {
        return GraphBuilder.build(specs, new Random(0), roster, defaults);
    }

    /**
     * Build this graph's events under one of its own roster blocks.
     *
     * @param rosterName the name a roster block was declared with
     * @return the events in feed order, each paired with its name
     */
    @NonNull
    public List<NamedEvent> build(@NonNull final String rosterName) {
        return build(roster(rosterName).toRosterWrapper());
    }
}
