package com.swirlds.platform.event.stream;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.Gossip;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Utility methods for testing event stream validation.
 */
class EventStreamValidationTestUtils {

    /**
     * Describes a state that should be generated.
     *
     * @param timestamp                   the consensus timestamp of the state
     * @param runningEventHash            the running event hash of the state
     * @param minimumGenerationNonAncient the minimum generation non-ancient for the state
     * @param eventCount                  the number of events that have reached consensus so far
     */
    record RequestedState(
            @NonNull Instant timestamp,
            @NonNull Hash runningEventHash,
            long minimumGenerationNonAncient,
            long eventCount) {

    }

    /**
     * Generate a series of on-disk states.
     *
     * @param random          a random number generator
     * @param requestedStates describes the states to be generated
     * @return the generated states
     */
    @NonNull
    List<SignedState> generateStates(
            @NonNull final Random random,
            @NonNull List<RequestedState> requestedStates) {

        final List<SignedState> states = new ArrayList<>(requestedStates.size());

        for (final RequestedState requestedState : requestedStates) {
            // TODO
        }

        return states;
    }

    record PreconsensusEventFileToWrite(
            long minimumGeneration,
            long maximumGeneration,
            long timestamp,
            long origin,
            @NonNull List<GossipEvent> events) {

    }

    void generatePreconsensusEventStream(@NonNull final Random random) {

    }

    void generateConsensusEventStream(
            @NonNull final Random random,
            @NonNull final Path path) {

    }

}
