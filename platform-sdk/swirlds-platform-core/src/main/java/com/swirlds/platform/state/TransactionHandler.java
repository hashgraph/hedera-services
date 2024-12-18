// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.state;

import static com.swirlds.base.units.UnitConstants.NANOSECONDS_TO_SECONDS;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.eventhandling.DefaultTransactionPrehandler.NO_OP_CONSUMER;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TransactionHandler {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(TransactionHandler.class);

    /** The id of this node. */
    private final NodeId selfId;

    /** Stats relevant to SwirldState operations. */
    private final SwirldStateMetrics stats;

    public TransactionHandler(final NodeId selfId, final SwirldStateMetrics stats) {
        this.selfId = selfId;
        this.stats = stats;
    }

    /**
     * Applies a consensus round to SwirldState, handles any exceptions gracefully, and updates relevant statistics.
     *
     * @param round
     * 		the round to apply
     * @param state
     * 		the state to apply {@code round} to
     */
    public void handleRound(final ConsensusRound round, final PlatformMerkleStateRoot state) {
        try {
            final Instant timeOfHandle = Instant.now();
            final long startTime = System.nanoTime();

            state.handleConsensusRound(round, state.getWritablePlatformState(), NO_OP_CONSUMER);

            final double secondsElapsed = (System.nanoTime() - startTime) * NANOSECONDS_TO_SECONDS;

            // Avoid dividing by zero
            if (round.getNumAppTransactions() == 0) {
                stats.consensusTransHandleTime(secondsElapsed);
            } else {
                stats.consensusTransHandleTime(secondsElapsed / round.getNumAppTransactions());
            }
            stats.consensusTransHandled(round.getNumAppTransactions());
            stats.consensusToHandleTime(
                    round.getReachedConsTimestamp().until(timeOfHandle, ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
        } catch (final Throwable t) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "error invoking SwirldState.handleConsensusRound() [ nodeId = {} ] with round {}",
                    selfId,
                    round.getRoundNum(),
                    t);
        }
    }
}
