// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.hashgraph.impl.consensus.flicker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.hiero.base.crypto.Hash;
import org.hiero.consensus.event.NoOpIntakeEventCounter;
import org.hiero.consensus.fakes.noop.NoOpMetrics;
import org.hiero.consensus.gui.api.TestGuiSource;
import org.hiero.consensus.hashgraph.impl.test.fixtures.flicker.FlickerGraph;
import org.hiero.consensus.hashgraph.impl.test.fixtures.flicker.NamedEvent;
import org.hiero.consensus.model.event.NonDeterministicGeneration;
import org.hiero.consensus.model.event.PlatformEvent;
import org.hiero.consensus.orphan.DefaultOrphanBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * G10: the viewer computes consensus from the same hashes the test asserts on.
 * <p>
 * The GUI never hashes an event. If a graph reached it unhashed it would still draw, and still decide rounds — from
 * placeholder hashes, whose whitening breaks ties differently from the test's. The two would disagree only in
 * intra-round order, which is the hardest kind of disagreement to notice while debugging the thing it is supposed to
 * explain.
 */
class FlickerGuiTest {

    @Test
    @DisplayName("the viewer is fed the test's events, hashes and order included")
    void theViewerSeesWhatTheTestSees() {
        final FlickerGraph graph = Scn001Graph.graph();

        final List<PlatformEvent> viewed = FlickerGui.events(graph, "initial");
        final List<NamedEvent> tested = graph.build("initial");

        assertThat(viewed).extracting(PlatformEvent::getHash).doesNotContainNull();
        assertThat(viewed.stream().map(PlatformEvent::getHash).toList())
                .as("same hashes, in the same order — so the same whitening and the same consensus order")
                .isEqualTo(tested.stream().map(named -> named.event().getHash()).toList());
    }

    @Test
    @DisplayName("the viewer's events are copies, so viewing a graph cannot disturb a test holding it")
    void theViewerGetsItsOwnCopies() {
        final FlickerGraph graph = Scn001Graph.graph();

        final List<PlatformEvent> first = FlickerGui.events(graph, "initial");
        final List<PlatformEvent> second = FlickerGui.events(graph, "initial");

        assertThat(first.getFirst()).isNotSameAs(second.getFirst());
        assertThat(first.getFirst().getHash()).isEqualTo(second.getFirst().getHash());
    }

    @Test
    @DisplayName("the roster the viewer runs under is the one named")
    void theNamedRosterIsUsed() {
        final FlickerGraph graph = Scn001Graph.graph();

        final List<Hash> underInitial = FlickerGui.events(graph, "initial").stream()
                .map(PlatformEvent::getHash)
                .toList();
        final List<Hash> underDefault = FlickerGui.events(graph, null).stream()
                .map(PlatformEvent::getHash)
                .toList();

        assertThat(underDefault)
                .as("no roster named means the first block, which is 'initial'")
                .isEqualTo(underInitial);
    }

    @Test
    @DisplayName("every event reaches the viewer's consensus instance, carrying an nGen")
    void theWholeGraphGetsPastTheOrphanBuffer() {
        final FlickerGraph graph = Scn001Graph.graph();
        final DefaultOrphanBuffer buffer = new DefaultOrphanBuffer(new NoOpMetrics(), new NoOpIntakeEventCounter());

        final List<PlatformEvent> released = FlickerGui.events(graph, "initial").stream()
                .map(buffer::handleEvent)
                .flatMap(List::stream)
                .toList();

        // The viewer feeds events through this same buffer, and draws nothing until one comes out with an nGen above
        // GENERATION_UNDEFINED. An event held back as an orphan - a parent hash that does not resolve, say - would
        // leave the window blank rather than visibly wrong.
        assertThat(released)
                .as("every event comes out of the orphan buffer")
                .hasSize(graph.specs().size());
        assertThat(released).allSatisfy(event -> assertThat(event.getNGen())
                .isGreaterThan(NonDeterministicGeneration.GENERATION_UNDEFINED));
    }

    @Test
    @DisplayName("the viewer opens on one event, and steps through the rest")
    void theViewerOpensOnOneEventAndSteps() {
        // viewer() feeds the first event, which is what makes the picture draw the roster's columns at all. The rest
        // arrive the way "Next events" delivers them. runGui is what opens a window, and is not called here.
        final TestGuiSource viewer = FlickerGui.viewer(Scn001Graph.graph(), "initial");

        assertThatCode(() -> viewer.generateEvents(10)).doesNotThrowAnyException();
    }
}
