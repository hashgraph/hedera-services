/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.platform.gui.hashgraph.HashgraphGuiSource;
import com.swirlds.platform.gui.hashgraph.internal.FinalShadowgraphGuiSource;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.platform.test.consensus.ConsensusUtils;
import com.swirlds.platform.test.consensus.TestIntake;
import com.swirlds.platform.test.event.EventUtils;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.test.framework.ResourceLoader;
import java.awt.FlowLayout;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Random;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

class TestGuiSource {
    final StandardGraphGenerator graphGenerator;
    final TestIntake intake;
    final HashgraphGuiSource guiSource;

    public TestGuiSource(final long seed, final int numNodes) {
        graphGenerator = new StandardGraphGenerator(seed, HashgraphGuiTest.generateSources(numNodes));

        intake = new TestIntake(graphGenerator.getAddressBook());

        guiSource = new FinalShadowgraphGuiSource(intake.getShadowGraph(), graphGenerator.getAddressBook());
    }

    public void runGui() {
        HashgraphGuiRunner.runHashgraphGui(guiSource, controls());
    }

    public void generateEvents(final int numEvents) {
        intake.addEvents(graphGenerator.generateEvents(numEvents));
    }

    public JPanel controls() {

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
            intake.addEvents(graphGenerator.generateEvents(
                    numEvents.getValue() instanceof Integer value ? value : defaultNumEvents));
            updateFameDecidedBelow.run();
        });
        // Reset
        final JButton reset = new JButton("Reset");
        reset.addActionListener(e -> {
            graphGenerator.reset();
            intake.reset();
            updateFameDecidedBelow.run();
        });
        // snapshot
        final JButton snapshot = new JButton("Print last snapshot");
        snapshot.addActionListener(e -> {
            final ConsensusRound round = intake.getConsensusRounds().peekLast();
            if (round == null) {
                System.out.println("No consensus rounds");
            } else {
                System.out.println(round.getSnapshot().toString());
            }
        });

        // load signed state
        final JButton loadSS = new JButton("Load version 5 state");
        loadSS.addActionListener(e -> {
            try {
                intake.reset();
                ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
                final Path ssPath = ResourceLoader.getFile("version-5-state.swh.bin");
                final SignedState state = SignedStateFileReader.readSignedStateOnly(ssPath);
                EventUtils.convertEvents(state);
                intake.loadFromSignedState(state);
                ConsensusUtils.loadEventsIntoGenerator(state, graphGenerator, new Random());
            } catch (final IOException | ConstructableRegistryException | URISyntaxException ex) {
                throw new RuntimeException(ex);
            }
        });

        // create JPanel
        final JPanel controls = new JPanel(new FlowLayout());
        controls.add(nextEvent);
        controls.add(numEvents);
        controls.add(reset);
        controls.add(fameDecidedBelow);
        controls.add(snapshot);
        controls.add(loadSS);

        return controls;
    }
}
