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

package com.swirlds.platform.wiring;

import com.swirlds.common.config.PlatformSchedulersConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * The {@link TaskScheduler}s used by the platform.
 *
 * @param internalEventValidatorScheduler  the scheduler for the internal event validator
 * @param eventDeduplicatorScheduler       the scheduler for the event deduplicator
 * @param eventSignatureValidatorScheduler the scheduler for the event signature validator
 * @param orphanBufferScheduler            the scheduler for the orphan buffer
 * @param inOrderLinkerScheduler           the scheduler for the in-order linker
 * @param linkedEventIntakeScheduler       the scheduler for the linked event intake
 */
public record PlatformSchedulers(
        @NonNull TaskScheduler<GossipEvent> internalEventValidatorScheduler,
        @NonNull TaskScheduler<GossipEvent> eventDeduplicatorScheduler,
        @NonNull TaskScheduler<GossipEvent> eventSignatureValidatorScheduler,
        @NonNull TaskScheduler<List<GossipEvent>> orphanBufferScheduler,
        @NonNull TaskScheduler<EventImpl> inOrderLinkerScheduler,
        @NonNull TaskScheduler<List<ConsensusRound>> linkedEventIntakeScheduler) {

    /**
     * Instantiate the schedulers for the platform, for the given wiring model
     *
     * @param context the platform context
     * @param model   the wiring model
     * @return the instantiated platform schedulers
     */
    public static PlatformSchedulers create(@NonNull final PlatformContext context, @NonNull final WiringModel model) {
        final PlatformSchedulersConfig config =
                context.getConfiguration().getConfigData(PlatformSchedulersConfig.class);

        return new PlatformSchedulers(
                model.schedulerBuilder("internalEventValidator")
                        .withType(config.getInternalEventValidatorSchedulerType())
                        .withUnhandledTaskCapacity(config.internalEventValidatorUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("eventDeduplicator")
                        .withType(config.getEventDeduplicatorSchedulerType())
                        .withUnhandledTaskCapacity(config.eventDeduplicatorUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("eventSignatureValidator")
                        .withType(config.getEventSignatureValidatorSchedulerType())
                        .withUnhandledTaskCapacity(config.eventSignatureValidatorUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("orphanBuffer")
                        .withType(config.getOrphanBufferSchedulerType())
                        .withUnhandledTaskCapacity(config.orphanBufferUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("inOrderLinker")
                        .withType(config.getInOrderLinkerSchedulerType())
                        .withUnhandledTaskCapacity(config.inOrderLinkerUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast(),
                model.schedulerBuilder("linkedEventIntake")
                        .withType(config.getLinkedEventIntakeSchedulerType())
                        .withUnhandledTaskCapacity(config.linkedEventIntakeUnhandledCapacity())
                        .withFlushingEnabled(true)
                        .withMetricsBuilder(model.metricsBuilder().withUnhandledTaskMetricEnabled(true))
                        .build()
                        .cast());
    }
}
