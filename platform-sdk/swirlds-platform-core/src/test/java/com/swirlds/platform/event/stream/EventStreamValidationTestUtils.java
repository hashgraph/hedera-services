package com.swirlds.platform.event.stream;

import com.swirlds.common.crypto.Hash;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.time.Instant;
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
     * @param path            the path to the directory where the states should be saved
     * @param requestedStates describes the states to be generated
     */
    void generateStates(
            @NonNull final Random random,
            @NonNull final Path path,
            @NonNull List<RequestedState> requestedStates) {

        for (final RequestedState requestedState : requestedStates) {

        }

    }

    void generatePreconsensusEventStream(
            @NonNull final Random random,
            @NonNull final Path path) {

    }

    void generateConsensusEventStream(
            @NonNull final Random random,
            @NonNull final Path path) {

    }

}
