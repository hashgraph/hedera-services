// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.assertions;

import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.hiero.consensus.model.status.ConsensusLayerStatus;

/**
 * Helper class that defines a step in the status progression of a node.
 *
 * <p>This class is used to define the expected status progression of a node during a test. It allows
 * to specify the target status, as well as any required or optional interim statuses that must/can be reached before
 * reaching the target status.
 */
public class StatusProgressionStep {

    private final EnumSet<ConsensusLayerStatus> targets;
    private final EnumSet<ConsensusLayerStatus> requiredInterim;
    private final EnumSet<ConsensusLayerStatus> optionalInterim;

    private StatusProgressionStep(
            @NonNull final EnumSet<ConsensusLayerStatus> targets,
            @NonNull final EnumSet<ConsensusLayerStatus> requiredInterim,
            @NonNull final EnumSet<ConsensusLayerStatus> optionalInterim) {
        this.targets = requireNonNull(targets);
        this.requiredInterim = requireNonNull(requiredInterim);
        this.optionalInterim = requireNonNull(optionalInterim);
    }

    /**
     * Returns the target status of this step.
     *
     * @return the target status
     */
    @NonNull
    public Set<ConsensusLayerStatus> targets() {
        return targets;
    }

    /**
     * Returns the required interim statuses of this step. The {@link Set} may be empty.
     *
     * @return the required interim statuses as an immutable {@link Set}
     */
    @NonNull
    public Set<ConsensusLayerStatus> requiredInterim() {
        return unmodifiableSet(requiredInterim);
    }

    /**
     * Returns the optional interim statuses of this step. The {@link Set} may be empty.
     *
     * @return the optional interim statuses as an immutable {@link Set}
     */
    @NonNull
    public Set<ConsensusLayerStatus> optionalInterim() {
        return unmodifiableSet(optionalInterim);
    }

    /**
     * Creates a new instance of {@link StatusProgressionStep} with the specified target status.
     *
     * @param target the target status
     * @return a new instance of {@link StatusProgressionStep}
     */
    public static StatusProgressionStep target(@NonNull final ConsensusLayerStatus target) {
        return new StatusProgressionStep(
                EnumSet.of(target), EnumSet.noneOf(ConsensusLayerStatus.class), EnumSet.noneOf(ConsensusLayerStatus.class));
    }

    /**
     * Creates a new instance of {@link StatusProgressionStep} with the specified target statuses. One of these targets
     * must be reached.
     *
     * @param first the first target status
     * @param rest the additional target statuses
     * @return a new instance of {@link StatusProgressionStep}
     */
    public static StatusProgressionStep targets(
            @NonNull final ConsensusLayerStatus first, @NonNull final ConsensusLayerStatus... rest) {
        return new StatusProgressionStep(
                EnumSet.of(first, rest), EnumSet.noneOf(ConsensusLayerStatus.class), EnumSet.noneOf(
                ConsensusLayerStatus.class));
    }

    /**
     * Creates a new instance of {@link StatusProgressionStep} that is a copy of this instance, but with the specified
     * required interim status added.
     *
     * @param first the first required interim status
     * @param rest optionally, the additional required interim statuses
     * @return a new instance of {@link StatusProgressionStep} with the specified required interim status
     */
    public StatusProgressionStep requiringInterim(
            @NonNull final ConsensusLayerStatus first, @Nullable final ConsensusLayerStatus... rest) {
        requireNonNull(first);

        final EnumSet<ConsensusLayerStatus> newRequiredInterim = EnumSet.copyOf(requiredInterim);
        newRequiredInterim.add(first);
        if (rest != null) {
            newRequiredInterim.addAll(Arrays.asList(rest));
        }

        return new StatusProgressionStep(targets, newRequiredInterim, optionalInterim);
    }

    /**
     * Creates a new instance of {@link StatusProgressionStep} that is a copy of this instance, but with the specified
     * optional interim status added.
     *
     * @param first the first optional interim status
     * @param rest optionally, the additional optional interim statuses
     * @return a new instance of {@link StatusProgressionStep} with the specified optional interim status
     */
    public StatusProgressionStep optionalInterim(
            @NonNull final ConsensusLayerStatus first, @Nullable final ConsensusLayerStatus... rest) {
        requireNonNull(first);

        final EnumSet<ConsensusLayerStatus> newOptionalInterim = EnumSet.copyOf(optionalInterim);
        newOptionalInterim.add(first);
        if (rest != null) {
            newOptionalInterim.addAll(Arrays.asList(rest));
        }

        return new StatusProgressionStep(targets, requiredInterim, newOptionalInterim);
    }
}
