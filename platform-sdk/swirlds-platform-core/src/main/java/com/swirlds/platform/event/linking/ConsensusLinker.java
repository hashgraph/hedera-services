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

package com.swirlds.platform.event.linking;

import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.event.EventDescriptorWrapper;
import com.swirlds.common.event.PlatformEvent;
import com.swirlds.common.platform.NodeId;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Links events to their parents. Expects events to be provided in topological order.
 * <p>
 * This implementation adds extra metrics to the base class.
 */
public class ConsensusLinker extends AbstractInOrderLinker {

    private final LongAccumulator missingParentAccumulator;
    private final LongAccumulator generationMismatchAccumulator;
    private final LongAccumulator birthRoundMismatchAccumulator;
    private final LongAccumulator timeCreatedMismatchAccumulator;

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     * @param selfId             the ID of this node
     */
    public ConsensusLinker(@NonNull final PlatformContext platformContext, @NonNull final NodeId selfId) {
        super(platformContext);

        missingParentAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "missingParents")
                        .withDescription("Parent child relationships where a parent was missing"));
        generationMismatchAccumulator = platformContext
                .getMetrics()
                .getOrCreate(
                        new LongAccumulator.Config(PLATFORM_CATEGORY, "parentGenerationMismatch")
                                .withDescription(
                                        "Parent child relationships where claimed parent generation did not match actual parent generation"));
        birthRoundMismatchAccumulator = platformContext
                .getMetrics()
                .getOrCreate(
                        new LongAccumulator.Config(PLATFORM_CATEGORY, "parentBirthRoundMismatch")
                                .withDescription(
                                        "Parent child relationships where claimed parent birth round did not match actual parent birth round"));
        timeCreatedMismatchAccumulator = platformContext
                .getMetrics()
                .getOrCreate(
                        new LongAccumulator.Config(PLATFORM_CATEGORY, "timeCreatedMismatch")
                                .withDescription(
                                        "Parent child relationships where child time created wasn't strictly after parent time created"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void eventHasBecomeAncient(@NonNull final EventImpl event) {
        event.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void childHasMissingParent(
            @NonNull final PlatformEvent child, @NonNull final EventDescriptorWrapper parentDescriptor) {
        super.childHasMissingParent(child, parentDescriptor);
        missingParentAccumulator.update(1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parentHasIncorrectGeneration(
            @NonNull final PlatformEvent child,
            @NonNull final EventDescriptorWrapper parentDescriptor,
            @NonNull final EventImpl candidateParent) {
        super.parentHasIncorrectGeneration(child, parentDescriptor, candidateParent);
        generationMismatchAccumulator.update(1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void parentHasIncorrectBirthRound(
            @NonNull final PlatformEvent child,
            @NonNull final EventDescriptorWrapper parentDescriptor,
            @NonNull final EventImpl candidateParent) {
        super.parentHasIncorrectBirthRound(child, parentDescriptor, candidateParent);
        birthRoundMismatchAccumulator.update(1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void childTimeIsNotAfterSelfParentTime(
            @NonNull final PlatformEvent child,
            @NonNull final EventImpl candidateParent,
            @NonNull final Instant parentTimeCreated,
            @NonNull final Instant childTimeCreated) {
        super.childTimeIsNotAfterSelfParentTime(child, candidateParent, parentTimeCreated, childTimeCreated);
        timeCreatedMismatchAccumulator.update(1);
    }
}
