// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.hashgraph.impl.consensus.flicker;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import org.hiero.consensus.fakes.noop.NoOpMetrics;
import org.hiero.consensus.gui.api.ListEventProvider;
import org.hiero.consensus.gui.api.TestGuiSource;
import org.hiero.consensus.hashgraph.impl.test.fixtures.flicker.FlickerGraph;
import org.hiero.consensus.hashgraph.impl.test.fixtures.flicker.GraphFiles;
import org.hiero.consensus.hashgraph.impl.test.fixtures.flicker.NamedEvent;
import org.hiero.consensus.hashgraph.impl.test.fixtures.flicker.NamedRoster;
import org.hiero.consensus.model.event.PlatformEvent;
import org.hiero.consensus.model.roster.RosterWrapper;

/**
 * Opens a {@code .graph} file in the hashgraph viewer. This is the tool to reach for when a fixture fails and the
 * rows do not explain why.
 * <p>
 * Run it as {@code FlickerGui scn-001.graph [roster]}, or call {@link #show} from a scratch test. The viewer feeds
 * events in the file's own order, so stepping through it walks the graph bottom-up exactly as the harness does.
 * <p>
 * <b>The viewer and the test agree because they are given the same hashes.</b> The GUI's path — provider, orphan
 * buffer, then its own {@code ConsensusImpl} — contains no hasher, so an event arriving unhashed would be given an
 * arbitrary one, and the whitening that breaks ties in consensus order would differ from the test's. Events come
 * from {@link FlickerGraph#build}, which hashes as it builds, so both sides see identical content hashes and
 * therefore identical rounds. {@link FlickerGuiTest} asserts this rather than trusting it.
 * <p>
 * A graph's anchor round is ignored: the viewer replays from genesis, where a two-phase fixture restarts from a
 * snapshot. What it shows is the graph, not the restart.
 */
public final class FlickerGui {

    private FlickerGui() {}

    /**
     * @param args the file name, and optionally the name of the roster block to run under
     */
    public static void main(@NonNull final String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("usage: FlickerGui <file.graph> [roster]");
        }
        show("scn-001.graph", "initial"); // args.length > 1 ? args[1] : null);
    }

    /**
     * Open a graph in the viewer. Blocks until the window is closed.
     *
     * @param fileName   the file to open, such as {@code scn-001.graph}
     * @param rosterName the roster block to run under, or null for the first one the file declares
     */
    public static void show(@NonNull final String fileName, @Nullable final String rosterName) {
        viewer(GraphFiles.load(fileName), rosterName).runGui();
    }

    /**
     * The viewer, holding the graph's first event and nothing more. "Next events" adds the rest, as many at a time
     * as the spinner says.
     * <p>
     * <b>The one event is what makes the roster visible.</b> Nothing is drawn at all until the viewer has seen an
     * event — not even the columns and the node labels under them — because the picture's readiness check is
     * {@code maxGeneration != GENERATION_UNDEFINED}, and {@code maxGeneration} starts at
     * {@code EventConstants.FIRST_GENERATION}, which is the same number. Opening on a genesis event costs nothing
     * to step past and means the window shows who is in the roster from the start.
     *
     * @param graph      the graph to view
     * @param rosterName the roster block to run under, or null for the first one the file declares
     * @return the source the viewer runs on
     */
    @NonNull
    public static TestGuiSource viewer(@NonNull final FlickerGraph graph, @Nullable final String rosterName) {
        final RosterWrapper roster = roster(graph, rosterName).toRosterWrapper();
        final TestGuiSource source = new TestGuiSource(
                new NoOpMetrics(), configuration(graph), roster, new ListEventProvider(events(graph, rosterName)));
        source.generateEvents(1);
        return source;
    }

    /**
     * The events the viewer is fed, in the order it is fed them.
     * <p>
     * They are gossip copies, as the harness also uses: the viewer links events into its own {@code EventImpl}s and
     * writes consensus metadata onto them, which must not reach a graph a test is also holding.
     *
     * @param graph      the graph to build
     * @param rosterName the roster block to build under, or null for the first one the file declares
     * @return the events, in feed order
     */
    @NonNull
    public static List<PlatformEvent> events(@NonNull final FlickerGraph graph, @Nullable final String rosterName) {
        return graph.build(roster(graph, rosterName).toRosterWrapper()).stream()
                .map(NamedEvent::event)
                .map(PlatformEvent::copyGossipedData)
                .toList();
    }

    @NonNull
    private static NamedRoster roster(@NonNull final FlickerGraph graph, @Nullable final String rosterName) {
        return rosterName == null ? graph.firstRoster() : graph.roster(rosterName);
    }

    /** The file's {@code config} line, as a {@link Configuration} the viewer's consensus instance can read. */
    @NonNull
    private static Configuration configuration(@NonNull final FlickerGraph graph) {
        final TestConfigBuilder builder = new TestConfigBuilder();
        graph.config().forEach(builder::withValue);
        return builder.getOrCreateConfig();
    }
}
