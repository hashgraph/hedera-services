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

package com.swirlds.platform.test.cli;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.event.report.EventStreamReport;
import com.swirlds.platform.event.report.EventStreamScanner;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.consensus.GenerateConsensus;
import com.swirlds.platform.test.simulated.RandomSigner;
import com.swirlds.platform.test.stream.StreamUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventStreamReportingToolTest {

    @TempDir
    Path tmpDir;

    /**
     * Generates events, feeds them to consensus, then writes these consensus events to stream files. One the files a
     * written, it generates a report and checks the values.
     */
    @Test
    void createReportTest() throws IOException, ConstructableRegistryException {
        final Random random = RandomUtils.getRandomPrintSeed();
        final int numNodes = 10;
        final int numEvents = 100_000;
        final Duration eventStreamWindowSize = Duration.ofSeconds(1);

        // setup
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        // generate consensus events
        final Deque<ConsensusRound> rounds =
                GenerateConsensus.generateConsensusRounds(numNodes, numEvents, random.nextLong());
        if (rounds.isEmpty()) {
            Assertions.fail("events are excepted to reach consensus");
        }
        // get consensus info
        final long roundToReportFrom = rounds.size() / 2;
        final int numConsensusEvents = rounds.stream()
                .filter(r -> r.getRoundNum() >= roundToReportFrom)
                .mapToInt(ConsensusRound::getNumEvents)
                .sum();
        final List<EventImpl> lastRound =
                Optional.ofNullable(rounds.peekLast()).orElseThrow().getConsensusEvents();
        final Instant lastEventTime = lastRound.get(lastRound.size() - 1).getConsensusTimestamp();

        // write event stream
        StreamUtils.writeRoundsToStream(tmpDir, new RandomSigner(random), eventStreamWindowSize, rounds);

        // get report
        final EventStreamReport report =
                new EventStreamScanner(tmpDir, roundToReportFrom, Duration.ofSeconds(1), false).createReport();

        // assert report has same info as expected
        Assertions.assertEquals(numConsensusEvents, report.summary().eventCount());
        Assertions.assertEquals(lastEventTime, report.summary().end());
        Assertions.assertEquals(
                lastEventTime, report.summary().lastEvent().getConsensusData().getConsensusTimestamp());
    }
}
