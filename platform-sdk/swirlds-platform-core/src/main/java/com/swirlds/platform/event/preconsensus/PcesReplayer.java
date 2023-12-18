/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.preconsensus;

import static com.swirlds.common.formatting.StringFormattingUtils.commaSeparatedNumber;
import static com.swirlds.common.units.TimeUnit.UNIT_MILLISECONDS;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.swirlds.base.time.Time;
import com.swirlds.common.formatting.UnitFormatter;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.wiring.DoneStreamingPcesTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class encapsulates the logic for replaying preconsensus events at boot up time.
 */
public class PcesReplayer {
    private static final Logger logger = LogManager.getLogger(PcesReplayer.class);

    private final Time time;

    private final StandardOutputWire<GossipEvent> eventOutputWire;

    private final Runnable flushSystem;

    private final StateManagementComponent stateManagementComponent;

    /**
     * Constructor
     *
     * @param time                     a source of time
     * @param eventOutputWire          the wire to put events on, to be replayed
     * @param flushSystem              a runnable that flushes the system
     * @param stateManagementComponent the state management component
     */
    public PcesReplayer(
            final @NonNull Time time,
            final @NonNull StandardOutputWire<GossipEvent> eventOutputWire,
            final @NonNull Runnable flushSystem,
            @NonNull final StateManagementComponent stateManagementComponent) {
        this.time = Objects.requireNonNull(time);
        this.eventOutputWire = Objects.requireNonNull(eventOutputWire);
        this.flushSystem = Objects.requireNonNull(flushSystem);
        this.stateManagementComponent = Objects.requireNonNull(stateManagementComponent);
    }

    /**
     * Write information about the replay to disk.
     */
    private void logReplayInfo(
            final long eventCount, final long transactionCount, @NonNull final Duration elapsedTime) {

        try (final ReservedSignedState latestConsensusRound =
                stateManagementComponent.getLatestImmutableState("SwirldsPlatform.replayPreconsensusEventStream()")) {

            if (latestConsensusRound.isNull()) {
                logger.info(
                        STARTUP.getMarker(),
                        "Replayed {} preconsensus events. No rounds reached consensus.",
                        commaSeparatedNumber(eventCount));
                return;
            }

            final Instant firstTimestamp = stateManagementComponent.getFirstStateTimestamp();
            final long firstRound = stateManagementComponent.getFirstStateRound();

            if (firstTimestamp == null) {
                // This should be impossible. If we have a state, we should have a timestamp.
                logger.error(
                        EXCEPTION.getMarker(),
                        "Replayed {} preconsensus events. "
                                + "First state timestamp is null, which should not be possible if a "
                                + "round has reached consensus",
                        commaSeparatedNumber(eventCount));
                return;
            }

            final long latestRound = latestConsensusRound.get().getRound();
            final long elapsedRounds = latestRound - firstRound;

            final Instant latestRoundTimestamp = latestConsensusRound.get().getConsensusTimestamp();
            final Duration elapsedConsensusTime = Duration.between(firstTimestamp, latestRoundTimestamp);

            logger.info(
                    STARTUP.getMarker(),
                    "replayed {} preconsensus events. These events contained {} transactions. "
                            + "{} rounds reached consensus spanning {} of consensus time. The latest "
                            + "round to reach consensus is round {}. Replay took {}.",
                    commaSeparatedNumber(eventCount),
                    commaSeparatedNumber(transactionCount),
                    commaSeparatedNumber(elapsedRounds),
                    new UnitFormatter(elapsedConsensusTime.toMillis(), UNIT_MILLISECONDS)
                            .setAbbreviate(false)
                            .render(),
                    commaSeparatedNumber(latestRound),
                    new UnitFormatter(elapsedTime.toMillis(), UNIT_MILLISECONDS)
                            .setAbbreviate(false)
                            .render());
        }
    }

    /**
     * Replays preconsensus events from disk.
     *
     * @param eventIterator an iterator over the events in the preconsensus stream
     * @return a trigger object indicating when the replay is complete
     */
    public DoneStreamingPcesTrigger replayPces(@NonNull final IOIterator<GossipEvent> eventIterator) {
        Objects.requireNonNull(eventIterator);

        final Instant start = time.now();

        int eventCount = 0;
        int transactionCount = 0;
        try {
            while (eventIterator.hasNext()) {
                final GossipEvent event = eventIterator.next();

                eventCount++;
                transactionCount += event.getHashedData().getTransactions().length;

                eventOutputWire.forward(event);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("error encountered while reading from the PCES", e);
        }

        flushSystem.run();

        final Duration elapsedTime = Duration.between(start, time.now());

        logReplayInfo(eventCount, transactionCount, elapsedTime);

        return new DoneStreamingPcesTrigger();
    }
}
