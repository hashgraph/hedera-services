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

import com.swirlds.platform.gui.hashgraph.HashgraphGuiSource;
import com.swirlds.platform.gui.hashgraph.internal.FinalShadowgraphGuiSource;
import com.swirlds.platform.test.consensus.TestIntake;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.event.source.EventSource;
import com.swirlds.platform.test.event.source.StandardEventSource;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class HashgraphGuiTest {
    @Test
    @Disabled("this test is useful for making changes to the GUI and inspecting them manually")
    void runGuiWithGeneratedEvents() {
        final long seed = 0;
        final int numNodes = 6;
        final int numEvents = 100;

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> e.printStackTrace());

        final List<? extends EventSource<?>> eventSources = Stream.generate(StandardEventSource::new)
                .map(es -> (EventSource<?>) es)
                .limit(numNodes)
                .toList();
        final StandardGraphGenerator graphGenerator =
                new StandardGraphGenerator(seed, (List<EventSource<?>>) eventSources);

        final TestIntake intake = new TestIntake(graphGenerator.getAddressBook());
        intake.addEvents(graphGenerator.generateEvents(numEvents));

        final HashgraphGuiSource guiSource =
                new FinalShadowgraphGuiSource(intake.getShadowGraph(), graphGenerator.getAddressBook());

        HashgraphGuiRunner.runHashgraphGui(guiSource);
    }
}
