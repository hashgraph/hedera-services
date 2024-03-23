/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.test.gui;

import static com.swirlds.platform.consensus.SyntheticSnapshot.GENESIS_SNAPSHOT;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.gui.GuiEventStorage;
import com.swirlds.platform.gui.hashgraph.HashgraphGuiSource;
import com.swirlds.platform.gui.hashgraph.internal.StandardGuiSource;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.consensus.TestIntake;
import com.swirlds.platform.test.fixtures.event.IndexedEvent;
import com.swirlds.platform.test.fixtures.event.generator.GraphGenerator;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.EventSource;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.awt.FlowLayout;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public class TestGuiSource {
    private final GraphGenerator<?> graphGenerator;
    private final TestIntake intake;
    private final HashgraphGuiSource guiSource;
    private ConsensusSnapshot savedSnapshot;
    private final GuiEventStorage eventStorage;

    /**
     * Construct a {@link TestGuiSource} with the given platform context, seed, and number of nodes.
     *
     * @param platformContext the platform context
     * @param seed            the seed
     * @param addressBook     the address book for this node
     */
    public TestGuiSource(
            @NonNull final PlatformContext platformContext, final long seed, @NonNull final AddressBook addressBook) {

        graphGenerator = new StandardGraphGenerator(platformContext, seed, generateSources(addressBook.getSize()));
        graphGenerator.reset();

        intake = new TestIntake(platformContext, graphGenerator.getAddressBook());

        eventStorage = new GuiEventStorage(platformContext.getConfiguration(), addressBook);
        guiSource = new StandardGuiSource(addressBook, eventStorage);
    }

    public TestGuiSource(
            @NonNull final PlatformContext platformContext,
            @NonNull final AddressBook addressBook,
            @NonNull final GraphGenerator<?> graphGenerator,
            @NonNull final TestIntake intake) {
        this.graphGenerator = graphGenerator;
        this.intake = intake;

        eventStorage = new GuiEventStorage(platformContext.getConfiguration(), addressBook);
        guiSource = new StandardGuiSource(addressBook, eventStorage);
    }

    public void runGui() {
        HashgraphGuiRunner.runHashgraphGui(guiSource, controls());
    }

    public void generateEvents(final int numEvents) {
        final List<IndexedEvent> events = graphGenerator.generateEvents(numEvents);
        intake.addEvents(events);
        for (final IndexedEvent event : events) {
            eventStorage.handlePreconsensusEvent(event.getBaseEvent());
        }
    }

    public @NonNull JPanel controls() {

        // Fame decided below
        final JLabel fameDecidedBelow = new JLabel("N/A");
        final Runnable updateFameDecidedBelow = () -> fameDecidedBelow.setText(
                "fame decided below: " + intake.getConsensus().getFameDecidedBelow());
        updateFameDecidedBelow.run();
        // Next events
        final JButton nextEvent = new JButton("Next events");
        final int defaultNumEvents = 10;
        final int numEventsMinimum = 1;
        final int numEventsStep = 1;
        final JSpinner numEvents = new JSpinner(new SpinnerNumberModel(
                Integer.valueOf(defaultNumEvents),
                Integer.valueOf(numEventsMinimum),
                Integer.valueOf(Integer.MAX_VALUE),
                Integer.valueOf(numEventsStep)));
        nextEvent.addActionListener(e -> {
            final List<IndexedEvent> events = graphGenerator.generateEvents(
                    numEvents.getValue() instanceof Integer value ? value : defaultNumEvents);
            intake.addEvents(events);
            for (final IndexedEvent event : events) {
                eventStorage.handlePreconsensusEvent(event.getBaseEvent());
            }

            updateFameDecidedBelow.run();
        });
        // Reset
        final JButton reset = new JButton("Reset");
        reset.addActionListener(e -> {
            graphGenerator.reset();
            intake.reset();
            eventStorage.handleSnapshotOverride(GENESIS_SNAPSHOT);
            updateFameDecidedBelow.run();
        });
        // snapshots
        final JButton printLastSnapshot = new JButton("Print last snapshot");
        printLastSnapshot.addActionListener(e -> {
            final ConsensusRound round = intake.getConsensusRounds().peekLast();
            if (round == null) {
                System.out.println("No consensus rounds");
            } else {
                System.out.println(round.getSnapshot().toString());
            }
        });
        final JButton saveLastSnapshot = new JButton("Save last snapshot");
        saveLastSnapshot.addActionListener(e -> {
            final ConsensusRound round = intake.getConsensusRounds().peekLast();
            if (round == null) {
                System.out.println("No consensus rounds");
            } else {
                savedSnapshot = round.getSnapshot();
            }
        });
        final JButton loadSavedSnapshot = new JButton("Load saved snapshot");
        loadSavedSnapshot.addActionListener(e -> {
            if (savedSnapshot == null) {
                System.out.println("No saved snapshot");
                return;
            }
            intake.reset();
            intake.loadSnapshot(savedSnapshot);
            eventStorage.handleSnapshotOverride(savedSnapshot);
        });

        // create JPanel
        final JPanel controls = new JPanel(new FlowLayout());
        controls.add(nextEvent);
        controls.add(numEvents);
        controls.add(reset);
        controls.add(fameDecidedBelow);
        controls.add(printLastSnapshot);
        controls.add(saveLastSnapshot);
        controls.add(loadSavedSnapshot);

        return controls;
    }

    public static @NonNull List<EventSource<?>> generateSources(final int numNetworkNodes) {
        final List<EventSource<?>> list = new LinkedList<>();
        for (long i = 0; i < numNetworkNodes; i++) {
            list.add(new StandardEventSource(true));
        }
        return list;
    }
}
