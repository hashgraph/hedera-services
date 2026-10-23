// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.hashgraph.impl.test.fixtures.flicker;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.roster.RosterEntry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hiero.consensus.model.node.NodeId;
import org.hiero.consensus.model.roster.RosterWrapper;
import org.hiero.consensus.model.test.fixtures.roster.RosterWrapperFactory;

/**
 * One roster block from a {@code .graph} header: a name, and a weight per node letter.
 * <p>
 * Weights live beside the graph rather than in a separate roster file because the derivation that argues about them
 * is what makes a fixture reviewable, and a file reference puts the explanation and the data in different places.
 * Lifting a captured production roster is a different workflow — that belongs with incident replay, not here.
 *
 * @param name            the name the header gives this roster, such as {@code initial} or {@code after}
 * @param weightsByLetter each node's letter mapped to its weight, in declaration order
 */
public record NamedRoster(@NonNull String name, @NonNull Map<String, Long> weightsByLetter) {

    /**
     * Node ids advance irregularly from {@link NodeId#FIRST_NODE_ID} rather than being the letter's index.
     * <p>
     * A real roster assigns arbitrary ids, and a graph names creators by <i>index</i> precisely so that it can run
     * under any roster. If the ids here were {@code 0,1,2,3} they would equal the indices, and any code that confused
     * the two would pass every fixture in the suite.
     * <p>
     * The gaps mirror {@code RosterWrapperFactory.createRandomNodeIds}, which advances one to three per node, so the
     * values stay small enough to hold in your head while debugging — {@code a,b,c,d} are {@code 1,2,5,7}. Starting
     * one above the first node id is what guarantees a letter's id is always greater than its index, so the two can
     * never be confused for one another. The value is a function of the letter alone, so a node keeps its id across
     * every roster block in a file.
     */
    private static final long ID_ORIGIN = NodeId.FIRST_NODE_ID.id() + 1;

    public NamedRoster {
        requireNonNull(name, "name must not be null");
        weightsByLetter = Collections.unmodifiableMap(new LinkedHashMap<>(requireNonNull(weightsByLetter, "weights")));
    }

    /**
     * @return the node letters, in declaration order; a creator index is a position in this list
     */
    @NonNull
    public List<String> letters() {
        return List.copyOf(weightsByLetter.keySet());
    }

    /**
     * @param letter a node letter
     * @return the node id that letter always maps to
     */
    @NonNull
    public static NodeId nodeIdFor(@NonNull final String letter) {
        final int index = letter.charAt(0) - 'a';
        long id = ID_ORIGIN;
        for (int step = 1; step <= index; step++) {
            id += 1 + (2L * step + 1) % 3;
        }
        return NodeId.of(id);
    }

    /**
     * @return this block as a roster the algorithm can run under
     */
    @NonNull
    public RosterWrapper toRosterWrapper() {
        final List<RosterEntry> entries = weightsByLetter.entrySet().stream()
                .map(entry -> RosterEntry.newBuilder()
                        .nodeId(nodeIdFor(entry.getKey()).id())
                        .weight(entry.getValue())
                        .build())
                .toList();
        return RosterWrapperFactory.createRosterWrapper(entries);
    }
}
