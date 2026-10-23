// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.hashgraph.impl.consensus.flicker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.hiero.consensus.hashgraph.impl.test.fixtures.flicker.EventSpec;
import org.hiero.consensus.hashgraph.impl.test.fixtures.flicker.FlickerGraph;
import org.hiero.consensus.hashgraph.impl.test.fixtures.flicker.GraphFiles;
import org.hiero.consensus.hashgraph.impl.test.fixtures.flicker.GraphParser;
import org.hiero.consensus.hashgraph.impl.test.fixtures.graph.SimpleGraphs;
import org.hiero.consensus.hashgraph.impl.test.fixtures.graph.SimplePlatformEventGraph;
import org.hiero.consensus.model.event.EventDescriptorWrapper;
import org.hiero.consensus.model.event.PlatformEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The {@code .graph} files that ship with this module. */
class PortedGraphsTest {

    /** {@code SimpleGraphs.graph8e4n}'s events, in the order the port renamed them. */
    private static final Map<Integer, String> PORTED_NAMES =
            Map.of(0, "a0", 1, "b0", 2, "a1", 3, "a2", 4, "b1", 5, "c0", 6, "d0", 7, "c1");

    @ParameterizedTest
    @ValueSource(strings = {Scn001Graph.FILE, "simple-8e4n.graph"})
    @DisplayName("every shipped file parses and survives a round trip")
    void everyShippedFileRoundTrips(final String name) {
        final FlickerGraph graph = GraphFiles.load(name);

        assertThat(GraphParser.parse(graph.toText())).isEqualTo(graph);
    }

    @Test
    @DisplayName("simple-8e4n.graph is the graph SimpleGraphs.graph8e4n builds")
    void theSimpleGraphWasPortedFaithfully() {
        final List<PlatformEvent> original = new SimpleGraphs<>(SimplePlatformEventGraph::new)
                .graph8e4n(new Random(1))
                .events();
        final FlickerGraph ported = GraphFiles.load("simple-8e4n.graph");

        assertThat(ported.specs()).hasSize(original.size());
        for (int index = 0; index < original.size(); index++) {
            final PlatformEvent event = original.get(index);
            final EventSpec spec = spec(ported, PORTED_NAMES.get(index));

            assertThat(spec.creator())
                    .as("%s is created by node %s", spec.name(), event.getCreatorId())
                    .isEqualTo((int) event.getCreatorId().id() - 1);
            assertThat(spec.selfParent())
                    .as("%s's self-parent", spec.name())
                    .isEqualTo(name(event.getSelfParent(), original));
            assertThat(spec.otherParents())
                    .as("%s's other-parents", spec.name())
                    .isEqualTo(event.getOtherParents().stream()
                            .map(parent -> name(parent, original))
                            .toList());
        }
    }

    private static EventSpec spec(final FlickerGraph graph, final String name) {
        return graph.specs().stream()
                .filter(spec -> spec.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no event named '%s'".formatted(name)));
    }

    private static String name(final EventDescriptorWrapper parent, final List<PlatformEvent> original) {
        if (parent == null) {
            return null;
        }
        for (int index = 0; index < original.size(); index++) {
            if (original.get(index).getDescriptor().equals(parent)) {
                return PORTED_NAMES.get(index);
            }
        }
        throw new AssertionError("parent %s is not in the graph".formatted(parent));
    }
}
