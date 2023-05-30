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

package com.swirlds.platform;

import static com.swirlds.common.formatting.StringFormattingUtils.commaSeparatedNumber;
import static com.swirlds.common.units.TimeUnit.UNIT_MILLISECONDS;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.formatting.UnitFormatter;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.platform.components.EventIntake;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PreConsensusEventFileManager;
import com.swirlds.platform.event.preconsensus.PreConsensusEventWriter;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class encapsulates the logic for replaying preconsensus events at boot up time.
 */
public final class PreconsensusEventReplay {

    private static final Logger logger = LogManager.getLogger(PreconsensusEventReplay.class);

    private PreconsensusEventReplay() {}

    /**
     * Replays preconsensus events from disk.
     *
     * @param platformContext                    the platform context for this node
     * @param preConsensusEventFileManager       manages the preconsensus event files on disk
     * @param preConsensusEventWriter            writes preconsensus events to disk
     * @param shadowGraph                        the shadow graph, used by gossip to track the current events
     * @param eventIntake                        the event intake component where events from the stream are fed
     * @param intakeQueue                        the queue thread for the event intake component
     * @param consensusRoundHandler              the object responsible for applying transactions to consensus rounds
     * @param stateManagementComponent           manages various copies of the state
     * @param currentPlatformStatus              a pointer to the current platform status
     * @param initialMinimumGenerationNonAncient the minimum generation of events to replay
     * @param diskStateRound                     the round number of the state on disk
     * @param diskStateTimestamp                 the timestamp of the state on disk
     */
    public static void replayPreconsensusEvents(
            @NonNull final PlatformContext platformContext,
            @NonNull final PreConsensusEventFileManager preConsensusEventFileManager,
            @NonNull final PreConsensusEventWriter preConsensusEventWriter,
            @NonNull final ShadowGraph shadowGraph,
            @NonNull final EventIntake eventIntake,
            @NonNull final QueueThread<EventIntakeTask> intakeQueue,
            @NonNull final ConsensusRoundHandler consensusRoundHandler,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final AtomicReference<PlatformStatus> currentPlatformStatus,
            final long initialMinimumGenerationNonAncient,
            final long diskStateRound,
            @Nullable final Instant diskStateTimestamp) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(preConsensusEventFileManager);
        Objects.requireNonNull(preConsensusEventWriter);
        Objects.requireNonNull(shadowGraph);
        Objects.requireNonNull(eventIntake);
        Objects.requireNonNull(intakeQueue);
        Objects.requireNonNull(consensusRoundHandler);
        Objects.requireNonNull(stateManagementComponent);
        Objects.requireNonNull(currentPlatformStatus);

        // Sanity check for platform status can be removed after we clean up platform status management
        if (currentPlatformStatus.get() != PlatformStatus.STARTING_UP) {
            throw new IllegalStateException(
                    "Platform status should be STARTING_UP, current status is " + currentPlatformStatus.get());
        }

        currentPlatformStatus.set(PlatformStatus.REPLAYING_EVENTS);

        // TODO use time
        final Instant start = Instant.now();

        logger.info(
                STARTUP.getMarker(),
                "replaying preconsensus event stream starting at generation {}",
                initialMinimumGenerationNonAncient);

        try {

            // TODO fix discontinuities
            final IOIterator<EventImpl> iterator =
                    preConsensusEventFileManager.getEventIterator(initialMinimumGenerationNonAncient);

            long eventCount = 0;
            long transactionCount = 0;

            while (iterator.hasNext()) {
                final EventImpl event = iterator.next();

                eventCount++;
                transactionCount += event.getTransactions().length;

                // TODO should we do this on a background thread?
                final Hash eventHash = platformContext.getCryptography().digestSync(event.getBaseEventHashedData());

                if (shadowGraph.isHashInGraph(eventHash)) {
                    // the shadowgraph doesn't deal with duplicate events well, filter them out here
                    continue;
                }

                final GossipEvent gossipEvent = new GossipEvent(event.getHashedData(), event.getUnhashedData());
                eventIntake.addUnlinkedEvent(gossipEvent);
            }

            // Wait until all events from the preconsensus event stream have been fully ingested.
            intakeQueue.waitUntilNotBusy();

            // Wait until all rounds from the preconsensus event stream have been fully processed.
            consensusRoundHandler.waitUntilNotBusy();

            // TODO are there other queues we need to wait on?

            // TODO use time
            final Instant finish = Instant.now();
            final Duration elapsed = Duration.between(start, finish);

            try (final ReservedSignedState latestConsensusRound = stateManagementComponent.getLatestImmutableState(
                    "SwirldsPlatform.replayPreconsensusEventStream()")) {

                if (latestConsensusRound.isNull()) {
                    logger.info(
                            STARTUP.getMarker(),
                            "Replayed {} preconsensus events. No rounds reached consensus.",
                            commaSeparatedNumber(eventCount));
                } else {
                    final long latestRound = latestConsensusRound.get().getRound();
                    final long elapsedRounds = latestRound - diskStateRound; // TODO this is wonky for genesis

                    // TODO it would be better to use the timestamp of the last transaction in this round
                    final Instant latestRoundTimestamp =
                            latestConsensusRound.get().getConsensusTimestamp();

                    final Duration elapsedConsensusTime;
                    if (diskStateTimestamp != null) {
                        elapsedConsensusTime = Duration.between(diskStateTimestamp, latestRoundTimestamp);
                    } else {
                        elapsedConsensusTime = Duration.ZERO;
                        // TODO we should compare time between first round and last round
                    }

                    logger.info(
                            STARTUP.getMarker(),
                            "replayed {} preconsensus events. These events contained {} transactions. "
                                    + "{} rounds reached consensus spanning {} of consensus time. The latest "
                                    + "round to reach consensus is round {}. Replay took {}.",
                            commaSeparatedNumber(eventCount),
                            commaSeparatedNumber(transactionCount),
                            commaSeparatedNumber(elapsedRounds),
                            new UnitFormatter(elapsedConsensusTime.toMillis(), UNIT_MILLISECONDS).setAbbreviate(false),
                            commaSeparatedNumber(latestRound),
                            new UnitFormatter(elapsed.toMillis(), UNIT_MILLISECONDS).setAbbreviate(false));
                }
            }

            preConsensusEventWriter.beginStreamingNewEvents();

        } catch (final IOException e) {
            throw new UncheckedIOException("unable to replay preconsensus event stream", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while replaying preconsensus event stream", e);
        }

        // Sanity check for platform status can be removed after we clean up platform status management
        if (currentPlatformStatus.get() != PlatformStatus.REPLAYING_EVENTS) {
            throw new IllegalStateException(
                    "Platform status should be REPLAYING_EVENTS, current status is " + currentPlatformStatus.get());
        }
        currentPlatformStatus.set(PlatformStatus.READY);

        // TODO when does "start up frozen" start measuring time?
    }
}
