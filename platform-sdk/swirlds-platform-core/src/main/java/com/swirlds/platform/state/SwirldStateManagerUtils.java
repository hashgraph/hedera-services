// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.state;

import static com.swirlds.base.units.UnitConstants.NANOSECONDS_TO_MICROSECONDS;

import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.system.SoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Objects;

/**
 * A utility class with useful methods for implementations of {@link SwirldStateManager}.
 */
public final class SwirldStateManagerUtils {

    // prevent instantiation of a static utility class
    private SwirldStateManagerUtils() {}

    /**
     * Performs a fast copy on a {@link PlatformMerkleStateRoot}. The {@code state} must not be modified during execution of this method.
     *
     * @param state           the state object to fast copy
     * @param stats           object to record stats in
     * @param softwareVersion the current software version
     * @return the newly created state copy
     */
    public static PlatformMerkleStateRoot fastCopy(
            @NonNull final PlatformMerkleStateRoot state,
            @NonNull final SwirldStateMetrics stats,
            @NonNull final SoftwareVersion softwareVersion) {

        Objects.requireNonNull(softwareVersion);

        final long copyStart = System.nanoTime();

        // Create a fast copy
        final PlatformMerkleStateRoot copy = state.copy();
        final var platformState = copy.getWritablePlatformState();
        platformState.setCreationSoftwareVersion(softwareVersion);

        // Increment the reference count because this reference becomes the new value
        copy.reserve();

        final long copyEnd = System.nanoTime();

        stats.stateCopyMicros((copyEnd - copyStart) * NANOSECONDS_TO_MICROSECONDS);

        return copy;
    }

    /**
     * Determines if a {@code timestamp} is in a freeze period according to the provided timestamps.
     *
     * @param consensusTime  the consensus time to check
     * @param freezeTime     the freeze time
     * @param lastFrozenTime the last frozen time
     * @return true is the {@code timestamp} is in a freeze period
     */
    public static boolean isInFreezePeriod(
            @NonNull final Instant consensusTime,
            @Nullable final Instant freezeTime,
            @Nullable final Instant lastFrozenTime) {

        // if freezeTime is not set, or consensusTime is before freezeTime, we are not in a freeze period
        // if lastFrozenTime is equal to or after freezeTime, which means the nodes have been frozen once at/after the
        // freezeTime, we are not in a freeze period
        if (freezeTime == null || consensusTime.isBefore(freezeTime)) {
            return false;
        }
        // Now we should check whether the nodes have been frozen at the freezeTime.
        // when consensusTime is equal to or after freezeTime,
        // and lastFrozenTime is before freezeTime, we are in a freeze period.
        return lastFrozenTime == null || lastFrozenTime.isBefore(freezeTime);
    }
}
