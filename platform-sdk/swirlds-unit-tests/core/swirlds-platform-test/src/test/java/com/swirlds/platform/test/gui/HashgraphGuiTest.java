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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.gui.hashgraph.HashgraphGuiSource;
import com.swirlds.platform.gui.hashgraph.internal.FinalShadowgraphGuiSource;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.MinGenInfo;
import com.swirlds.platform.test.consensus.TestIntake;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.event.source.EventSource;
import com.swirlds.platform.test.event.source.StandardEventSource;
import java.awt.FlowLayout;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class HashgraphGuiTest {
    @Test
    @Disabled("this test is useful for debugging consensus")
    void runGuiWithControls() {
        final long seed = 1;
        final int numNodes = 4;
        final int initialEvents = 100;

        final TestGuiSource guiSource = new TestGuiSource(seed, numNodes);
        guiSource.generateEvents(initialEvents);
        guiSource.runGui();
    }

    @Test
    @Disabled("this test is useful for debugging consensus restart/reconnect")
    void runGuiFromSnapshot() {
        final long seed = 1;
        final int numNodes = 4;
        // startup info
        final ConsensusSnapshot snapshot =
                new ConsensusSnapshot(
                        7,
                        Stream.of(
                                        "e5f7d07ed89a3d92e68f82f53f05dba23d3469a07d02c2ce6c51e6542134debd28da928c8b1b6226317509e1a8be3fb0",
                                        "d16b8fdb8b7548baaef655032dcc63366e4d7b02e61b5d858ac00b5b17b37d40d242f52cec072790d92a5c184538bebd",
                                        "c867d0f1d028e5007c950a9b7e69e26b9ad71b115b136e6715929bd0b67c0dd2292c99a6c83e395366d2575fb92c690d",
                                        "e0c74682d7c43d8ebec9625ea577305cfebfceaf4cb8ad24bf006ca5ae092a20155219d6215db1380d5d835a35f35127")
                                .map(CommonUtils::unhex)
                                .map(Hash::new)
                                .toList(),
                        List.of(
                                new MinGenInfo(1, 0),
                                new MinGenInfo(2, 10),
                                new MinGenInfo(3, 17),
                                new MinGenInfo(4, 27),
                                new MinGenInfo(5, 38),
                                new MinGenInfo(6, 51),
                                new MinGenInfo(7, 59)),
                        107,
                        Instant.parse("2020-05-06T13:21:56.689025Z"));

        final StandardGraphGenerator graphGenerator =
                new StandardGraphGenerator(seed, generateSources(numNodes));
        final TestIntake intake = new TestIntake(graphGenerator.getAddressBook());
        intake.loadSnapshot(snapshot);

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> e.printStackTrace());

        final HashgraphGuiSource guiSource =
                new FinalShadowgraphGuiSource(
                        intake.getShadowGraph(), graphGenerator.getAddressBook());

        HashgraphGuiRunner.runHashgraphGui(guiSource, controls(intake, graphGenerator));
    }

    public static List<EventSource<?>> generateSources(final int numNetworkNodes) {
        final List<EventSource<?>> list = new LinkedList<>();
        for (long i = 0; i < numNetworkNodes; i++) {
            list.add(new StandardEventSource(true));
        }
        return list;
    }

    @Deprecated(forRemoval = true)
    public static JPanel controls(
            final TestIntake intake, final StandardGraphGenerator graphGenerator) {

        // Fame decided below
        final JLabel fameDecidedBelow = new JLabel("N/A");
        final Runnable updateFameDecidedBelow =
                () ->
                        fameDecidedBelow.setText(
                                "fame decided below: "
                                        + intake.getConsensus().getFameDecidedBelow());
        updateFameDecidedBelow.run();
        // Next events
        final JButton nextEvent = new JButton("Next events");
        final int defaultNumEvents = 10;
        final int numEventsMinimum = 1;
        final int numEventsStep = 1;
        final JSpinner numEvents =
                new JSpinner(
                        new SpinnerNumberModel(
                                Integer.valueOf(defaultNumEvents),
                                Integer.valueOf(numEventsMinimum),
                                Integer.valueOf(Integer.MAX_VALUE),
                                Integer.valueOf(numEventsStep)));
        nextEvent.addActionListener(
                e -> {
                    intake.addEvents(
                            graphGenerator.generateEvents(
                                    numEvents.getValue() instanceof Integer value
                                            ? value
                                            : defaultNumEvents));
                    updateFameDecidedBelow.run();
                });
        // Reset
        final JButton reset = new JButton("Reset");
        reset.addActionListener(
                e -> {
                    graphGenerator.reset();
                    intake.reset();
                    updateFameDecidedBelow.run();
                });
        // snapshot
        final JButton snapshot = new JButton("Print last snapshot");
        snapshot.addActionListener(
                e -> {
                    final ConsensusRound round = intake.getConsensusRounds().peekLast();
                    if (round == null) {
                        System.out.println("No consensus rounds");
                    } else {
                        System.out.println(round.getSnapshot().toString());
                    }
                });

        // create JPanel
        final JPanel controls = new JPanel(new FlowLayout());
        controls.add(nextEvent);
        controls.add(numEvents);
        controls.add(reset);
        controls.add(fameDecidedBelow);
        controls.add(snapshot);

        return controls;
    }
}
