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

import com.swirlds.base.time.Time;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.formatting.UnitFormatter;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

import static com.swirlds.common.formatting.StringFormattingUtils.commaSeparatedNumber;
import static com.swirlds.common.units.TimeUnit.UNIT_MILLISECONDS;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

/**
 * This class encapsulates the logic for replaying preconsensus events at boot up time.
 */
public final class PreconsensusReplayIterator {

    private static final Logger logger = LogManager.getLogger(PreconsensusReplayIterator.class);

    private int eventCount = 0;
    private int transactionCount = 0;

    private PreconsensusReplayIterator() {}

    /**
     * Replays preconsensus events from disk.
     *
     * @param platformContext          the platform context for this node
     * @param time                     a source of time
     * @param eventIterator            an iterator over the events in the preconsensus stream
     * @param eventConsumer            a consumer that accepts events as they are read
     * @param intakeQueue              the event intake queue
     * @param consensusRoundHandler    the object responsible for applying transactions to consensus rounds
     * @param stateHashSignQueue       the queue thread for hashing and signing states
     * @param stateManagementComponent manages various copies of the state
     * @param flushIntakePipeline      flushes the intake pipeline. only used if the new intake pipeline is
     *                                 enabled
     *
     * @throws InterruptedException if the thread is interrupted while waiting for the replay to complete
     */
    public void replayPreconsensusEvents(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final IOIterator<GossipEvent> eventIterator,
            @NonNull final Consumer<GossipEvent> eventConsumer,
            @NonNull final QueueThread<GossipEvent> intakeQueue,
            @NonNull final ConsensusRoundHandler consensusRoundHandler,
            @NonNull final QueueThread<ReservedSignedState> stateHashSignQueue,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final Runnable flushIntakePipeline) throws InterruptedException {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(time);
        Objects.requireNonNull(eventIterator);
        Objects.requireNonNull(stateManagementComponent);

        // todo measure this in a better way
        final Instant start = time.now();

        try {
            while (eventIterator.hasNext()) {
                final GossipEvent event = eventIterator.next();

                eventCount++;
                transactionCount += event.getHashedData().getTransactions().length;

                eventConsumer.accept(event);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("error encountered while reading from the PCES", e);
        }

        final boolean useLegacyIntake = platformContext.getConfiguration()
                .getConfigData(EventConfig.class)
                .useLegacyIntake();

        waitForReplayToComplete(
                intakeQueue,
                consensusRoundHandler,
                stateHashSignQueue,
                useLegacyIntake,
                flushIntakePipeline);

        final Instant finish = time.now();
        final Duration elapsed = Duration.between(start, finish);

        logReplayInfo(stateManagementComponent, eventCount, transactionCount, elapsed);
    }

    /**
     * Wait for all events to be replayed. Some of this work happens on asynchronous threads, so we need to wait for them
     * to complete even after we exhaust all available events from the stream.
     */
    private static void waitForReplayToComplete(
            @NonNull final QueueThread<GossipEvent> intakeQueue,
            @NonNull final ConsensusRoundHandler consensusRoundHandler,
            @NonNull final QueueThread<ReservedSignedState> stateHashSignQueue,
            final boolean useLegacyIntake,
            @NonNull final Runnable flushIntakePipeline)
            throws InterruptedException {

        // Wait until all events from the preconsensus event stream have been fully ingested.
        intakeQueue.waitUntilNotBusy();

        if (!useLegacyIntake) {
            // The old intake has an empty intake pipeline as soon as the intake queue is empty.
            // The new intake has more steps to the intake pipeline, so we need to flush it before certifying that
            // the replay is complete.
            flushIntakePipeline.run();
        }

        // Wait until all rounds from the preconsensus event stream have been fully processed.
        consensusRoundHandler.waitUntilNotBusy();

        // Wait until we have hashed/signed all rounds
        stateHashSignQueue.waitUntilNotBusy();
    }

    /**
     * Write information about the replay to disk.
     */
    private static void logReplayInfo(
            @NonNull final StateManagementComponent stateManagementComponent,
            final long eventCount,
            final long transactionCount,
            @NonNull final Duration elapsedTime) {

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
}
