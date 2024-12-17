/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.deduplication;

import static com.swirlds.metrics.api.FloatFormats.FORMAT_10_2;
import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.sequence.map.SequenceMap;
import com.swirlds.platform.sequence.map.StandardSequenceMap;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * A standard implementation of an {@link EventDeduplicator}.
 */
public class StandardEventDeduplicator implements EventDeduplicator {
    /**
     * Avoid the creation of lambdas for Map.computeIfAbsent() by reusing this lambda.
     */
    private static final Function<EventDescriptorWrapper, Set<Bytes>> NEW_HASH_SET = ignored -> new HashSet<>();

    /**
     * Initial capacity of {@link #observedEvents}.
     */
    private static final int INITIAL_CAPACITY = 1024;

    /**
     * The current event window.
     */
    private EventWindow eventWindow;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * A map from event descriptor to a set of signatures that have been received for that event.
     */
    private final SequenceMap<EventDescriptorWrapper, Set<Bytes>> observedEvents;

    private static final LongAccumulator.Config DISPARATE_SIGNATURE_CONFIG = new LongAccumulator.Config(
                    PLATFORM_CATEGORY, "eventsWithDisparateSignature")
            .withDescription(
                    "Events received that match a descriptor of a previous event, but with a different signature")
            .withUnit("events");
    private final LongAccumulator disparateSignatureAccumulator;

    private final CountPerSecond duplicateEventsPerSecond;

    private static final RunningAverageMetric.Config AVG_DUPLICATE_PERCENT_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "dupEvPercent")
            .withDescription("percentage of events received that are already known")
            .withFormat(FORMAT_10_2);
    private final RunningAverageMetric avgDuplicatePercent;

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     */
    public StandardEventDeduplicator(
            @NonNull final PlatformContext platformContext, @NonNull final IntakeEventCounter intakeEventCounter) {

        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);

        final Metrics metrics = platformContext.getMetrics();

        this.disparateSignatureAccumulator = metrics.getOrCreate(DISPARATE_SIGNATURE_CONFIG);
        this.duplicateEventsPerSecond = new CountPerSecond(
                metrics,
                new CountPerSecond.Config(PLATFORM_CATEGORY, "dupEv_per_sec")
                        .withDescription("number of events received per second that are already known")
                        .withUnit("hz"));
        this.avgDuplicatePercent = metrics.getOrCreate(AVG_DUPLICATE_PERCENT_CONFIG);

        final AncientMode ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();
        this.eventWindow = EventWindow.getGenesisEventWindow(ancientMode);
        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            observedEvents = new StandardSequenceMap<>(
                    0, INITIAL_CAPACITY, true, ed -> ed.eventDescriptor().birthRound());
        } else {
            observedEvents = new StandardSequenceMap<>(
                    0, INITIAL_CAPACITY, true, ed -> ed.eventDescriptor().generation());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public PlatformEvent handleEvent(@NonNull final PlatformEvent event) {
        if (eventWindow.isAncient(event)) {
            // Ancient events can be safely ignored.
            intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());
            return null;
        }

        final Set<Bytes> signatures = observedEvents.computeIfAbsent(event.getDescriptor(), NEW_HASH_SET);
        if (signatures.add(event.getSignature())) {
            if (signatures.size() != 1) {
                // signature is unique, but descriptor is not
                disparateSignatureAccumulator.update(1);
            }

            // move toward 0%
            avgDuplicatePercent.update(0);

            return event;
        } else {
            // duplicate descriptor and signature
            duplicateEventsPerSecond.count(1);
            // move toward 100%
            avgDuplicatePercent.update(100);
            intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());

            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEventWindow(@NonNull final EventWindow eventWindow) {
        this.eventWindow = Objects.requireNonNull(eventWindow);

        observedEvents.shiftWindow(eventWindow.getAncientThreshold());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear(@NonNull final NoInput ignored) {
        observedEvents.clear();
    }
}
