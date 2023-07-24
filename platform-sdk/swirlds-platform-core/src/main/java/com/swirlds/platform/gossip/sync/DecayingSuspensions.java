package com.swirlds.platform.gossip.sync;

import static com.swirlds.common.units.TimeUnit.UNIT_NANOSECONDS;
import static com.swirlds.common.units.TimeUnit.UNIT_SECONDS;

import com.swirlds.base.time.Time;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

// TODO port this forward

/**
 * Sync permit suspensions that gradually lessen over time. Encapsulates logic for the sync permit provider to make sure
 * permit suspensions are not instantaneously reversed when the intake queue briefly dips to an acceptable level.
 */
public class DecayingSuspensions {

    private final Time time;
    private final double decayRate;

    private double previousSuspensionCount;
    private Instant previousUpdateTime;

    /**
     * Constructor.
     *
     * @param time      provides wall clock time
     * @param decayRate the number of suspensions that decay per second, fractional values are permitted
     */
    public DecayingSuspensions(@NonNull final Time time, final double decayRate) {
        this.time = Objects.requireNonNull(time);
        if (decayRate <= 0) {
            throw new IllegalArgumentException("decayRate must be nonzero positive, provided value: " + decayRate);
        }
        this.decayRate = decayRate;
        this.previousSuspensionCount = 0;
        this.previousUpdateTime = time.now();
    }

    /**
     * Get the current suspension count as reckoned by the decaying suspension algorithm.
     *
     * @param instantaneousSuspensionCount the current suspension count as reckoned by the sync permit provider, this is
     *                                     computed using the current intake queue size and can change very quickly as
     *                                     the size of the queue changes
     * @return the current suspension count as reckoned by the decaying suspension algorithm
     */
    public double getCurrentSuspensionCount(final double instantaneousSuspensionCount) {

        if (instantaneousSuspensionCount > previousSuspensionCount) {
            // The current count is greater than the previous count.
            // Suspensions increases immediately and decay gradually.

            previousSuspensionCount = instantaneousSuspensionCount;
            previousUpdateTime = time.now();
            return instantaneousSuspensionCount;
        }

        // The current suspension count is less than what is permitted by the decaying suspension algorithm.
        // Allow the value to decay according to the time that has passed since it was checked last.
        final Instant now = time.now();
        final Duration elapsedDuration = Duration.between(previousUpdateTime, now);
        final double elapsedSeconds = UNIT_NANOSECONDS.convertTo(elapsedDuration.toNanos(), UNIT_SECONDS);
        final double decayedSuspensions = elapsedSeconds * decayRate;
        previousSuspensionCount = Math.max(0, previousSuspensionCount - decayedSuspensions);
        previousUpdateTime = now;
        return previousSuspensionCount;
    }
}
