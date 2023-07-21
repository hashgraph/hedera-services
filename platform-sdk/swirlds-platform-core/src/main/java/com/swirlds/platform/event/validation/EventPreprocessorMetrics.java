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

package com.swirlds.platform.event.validation;

import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Supplier;

/**
 * Encapsulates metrics for {@link EventPreprocessor}.
 */
public class EventPreprocessorMetrics {

    private static final SpeedometerMetric.Config DUPLICATE_EVENTS_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    PLATFORM_CATEGORY, "duplicateEventsPerSecond")
            .withDescription("number of events received per second that are already known");
    private final SpeedometerMetric duplicateEventsPerSecond;

    private static final RunningAverageMetric.Config DUPLICATE_EVENT_PERCENT_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "duplicateEventPercent")
            .withDescription("percentage of events received that are already known");
    private final RunningAverageMetric duplicateEventPercent;

    private static final SpeedometerMetric.Config INVALID_EVENTS_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    PLATFORM_CATEGORY, "invalidEventsPerSecond")
            .withDescription("number of events received per second that are invalid (after deduplication)");
    private final SpeedometerMetric invalidEventsPerSecond;

    private static final RunningAverageMetric.Config INVALID_EVENT_PERCENT_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "invalidEventPercent")
            .withDescription("percentage of events received that are invalid (after deduplication)");
    private final RunningAverageMetric invalidEventPercent;

    /**
     * Constructor.
     *
     * @param platformContext this platform's context
     */
    public EventPreprocessorMetrics(
            @NonNull final PlatformContext platformContext,
            @NonNull final Supplier<Integer> preprocessQueueSizeSupplier) {

        duplicateEventsPerSecond = platformContext.getMetrics().getOrCreate(DUPLICATE_EVENTS_PER_SECOND_CONFIG);
        duplicateEventPercent = platformContext.getMetrics().getOrCreate(DUPLICATE_EVENT_PERCENT_CONFIG);

        invalidEventsPerSecond = platformContext.getMetrics().getOrCreate(INVALID_EVENTS_PER_SECOND_CONFIG);
        invalidEventPercent = platformContext.getMetrics().getOrCreate(INVALID_EVENT_PERCENT_CONFIG);

        final FunctionGauge.Config<Integer> preprocessQueueSizeConfig = new FunctionGauge.Config<>(
                        PLATFORM_CATEGORY, "preprocessQueueSize", Integer.class, preprocessQueueSizeSupplier)
                .withDescription("number of events in the preprocess queue");
        platformContext.getMetrics().getOrCreate(preprocessQueueSizeConfig);
    }

    /**
     * Register the receipt of a duplicate event.
     */
    public void registerDuplicateEvent() {
        duplicateEventsPerSecond.cycle();

        // Move the running average towards 100%
        duplicateEventPercent.update(100);
    }

    /**
     * Register the receipt of a unique event.
     */
    public void registerUniqueEvent() {

        // Move the running average towards 0%
        duplicateEventPercent.update(0);
    }

    /**
     * Register the receipt of an invalid event.
     */
    public void registerInvalidEvent() {
        invalidEventsPerSecond.cycle();

        // Move the running average towards 100%
        invalidEventPercent.update(100);
    }

    /**
     * Register the receipt of a valid event.
     */
    public void registerValidEvent() {

        // Move the running average towards 0%
        invalidEventPercent.update(0);
    }
}
