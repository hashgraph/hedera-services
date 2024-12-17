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

package com.swirlds.platform.test.cli;

import static com.swirlds.platform.test.consensus.ConsensusTestArgs.DEFAULT_PLATFORM_CONTEXT;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.report.EventStreamReport;
import com.swirlds.platform.event.report.EventStreamScanner;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.recovery.internal.EventStreamRoundLowerBound;
import com.swirlds.platform.recovery.internal.EventStreamTimestampLowerBound;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.test.consensus.GenerateConsensus;
import com.swirlds.platform.test.fixtures.stream.StreamUtils;
import com.swirlds.platform.test.simulated.RandomSigner;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventStreamReportingToolTest {

    @TempDir
    Path tmpDir;

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        StaticSoftwareVersion.setSoftwareVersion(new BasicSoftwareVersion(1));
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    @AfterAll
    static void afterAll() {
        StaticSoftwareVersion.reset();
    }

    /**
     * Generates events, feeds them to consensus, then writes these consensus events to stream files. One the files a
     * written, it generates a report and checks the values.
     */
    @Test
    void createReportTest() throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();
        final int numNodes = 10;
        final int numEvents = 100_000;
        final Duration eventStreamWindowSize = Duration.ofSeconds(1);

        // generate consensus events
        final Deque<ConsensusRound> rounds = GenerateConsensus.generateConsensusRounds(
                DEFAULT_PLATFORM_CONTEXT, numNodes, numEvents, random.nextLong());
        if (rounds.isEmpty()) {
            Assertions.fail("events are excepted to reach consensus");
        }
        // get consensus info
        final long roundToReportFrom = rounds.size() / 2;
        final int numConsensusEvents = rounds.stream()
                .filter(r -> r.getRoundNum() >= roundToReportFrom)
                .mapToInt(ConsensusRound::getNumEvents)
                .sum();
        final List<PlatformEvent> lastRound =
                Optional.ofNullable(rounds.peekLast()).orElseThrow().getConsensusEvents();
        final Instant lastEventTime = lastRound.get(lastRound.size() - 1).getConsensusTimestamp();

        // write event stream
        StreamUtils.writeRoundsToStream(tmpDir, new RandomSigner(random), eventStreamWindowSize, rounds);

        // get report
        final EventStreamReport report = new EventStreamScanner(
                        tmpDir, new EventStreamRoundLowerBound(roundToReportFrom), Duration.ofSeconds(1), false)
                .createReport();

        // assert report has same info as expected
        Assertions.assertEquals(numConsensusEvents, report.summary().eventCount());
        Assertions.assertEquals(lastEventTime, report.summary().end());
        Assertions.assertEquals(
                lastEventTime, report.summary().lastEvent().getPlatformEvent().getConsensusTimestamp());
    }

    /**
     * Generates events, feeds them to consensus, then writes these consensus events to stream files. One the files a
     * written, it generates a report and checks the values.
     */
    @Test
    void createTimeBoundReportTest() throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();
        final int numNodes = 10;
        final int numEvents = 100_000;
        final Duration eventStreamWindowSize = Duration.ofSeconds(1);

        // generate consensus events
        final Deque<ConsensusRound> rounds = GenerateConsensus.generateConsensusRounds(
                DEFAULT_PLATFORM_CONTEXT, numNodes, numEvents, random.nextLong());
        if (rounds.isEmpty()) {
            Assertions.fail("events are excepted to reach consensus");
        }
        // get consensus info
        final long roundToReportFrom = rounds.size() / 2;
        final AtomicReference<Instant> timestampRef = new AtomicReference<>(Instant.MIN);
        final int numConsensusEvents = rounds.stream()
                .filter(r -> {
                    if (r.getRoundNum() >= roundToReportFrom) {
                        timestampRef.compareAndSet(
                                Instant.MIN, r.getConsensusEvents().get(0).getConsensusTimestamp());
                        return true;
                    }
                    return false;
                })
                .mapToInt(ConsensusRound::getNumEvents)
                .sum();
        final List<PlatformEvent> lastRound =
                Optional.ofNullable(rounds.peekLast()).orElseThrow().getConsensusEvents();
        final Instant lastEventTime = lastRound.get(lastRound.size() - 1).getConsensusTimestamp();

        // write event stream
        StreamUtils.writeRoundsToStream(tmpDir, new RandomSigner(random), eventStreamWindowSize, rounds);

        // get report
        final EventStreamReport report = new EventStreamScanner(
                        tmpDir, new EventStreamTimestampLowerBound(timestampRef.get()), Duration.ofSeconds(1), false)
                .createReport();

        // assert report has same info as expected
        Assertions.assertEquals(numConsensusEvents, report.summary().eventCount());
        Assertions.assertEquals(lastEventTime, report.summary().end());
        Assertions.assertEquals(
                lastEventTime, report.summary().lastEvent().getPlatformEvent().getConsensusTimestamp());
    }
}
