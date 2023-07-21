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
import static com.swirlds.common.units.TimeUnit.UNIT_MILLISECONDS;
import static com.swirlds.common.units.TimeUnit.UNIT_NANOSECONDS;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Encapsulates metrics for {@link EventPreprocessor}.
 */
public class EventPreprocessorMetrics {

    private static final SpeedometerMetric.Config DUPLICATE_EVENTS_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    PLATFORM_CATEGORY, "duplicateEventsPerSecond")
            .withDescription("Number of duplicate events received per second.");
    private final SpeedometerMetric duplicateEventsPerSecond;

    private static final RunningAverageMetric.Config DUPLICATE_EVENT_PERCENT_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "duplicateEventPercent")
            .withDescription("Percentage of received events that are duplicates.");
    private final RunningAverageMetric duplicateEventPercent;

    private static final SpeedometerMetric.Config INVALID_EVENTS_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    PLATFORM_CATEGORY, "invalidEventsPerSecond")
            .withDescription("Number of events received per second that are invalid (after deduplication).");
    private final SpeedometerMetric invalidEventsPerSecond;

    private static final RunningAverageMetric.Config INVALID_EVENT_PERCENT_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "invalidEventPercent")
            .withDescription("Percentage of events received that are invalid (after deduplication).");
    private final RunningAverageMetric invalidEventPercent;

    private static final RunningAverageMetric.Config EVENT_HASH_TIME_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "eventHashTime")
            .withUnit("milliseconds")
            .withDescription("Average time to hash and deduplicate an event.");
    private final RunningAverageMetric eventHashTime;

    private static final RunningAverageMetric.Config EVENT_VALIDATION_TIME_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "eventValidationTime")
            .withUnit("milliseconds")
            .withDescription("Average time to validate an event.");
    private final RunningAverageMetric eventValidationTime;

    private static final RunningAverageMetric.Config EVENT_PREHANDLE_TIME_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "eventPrehandleTime")
            .withUnit("milliseconds")
            .withDescription("Average time to prehandle all application transactions in an event.");
    private final RunningAverageMetric eventPrehandleTime;

    private static final RunningAverageMetric.Config EVENT_PREPROCESS_TIME_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "eventPreprocessTime")
            .withUnit("milliseconds")
            .withDescription("Average time to perform all preprocessing on an event. This metric is only updated"
                    + "for events that are not discarded due to being invalid/duplicate.");
    private final RunningAverageMetric eventPreprocessTime;

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

        eventHashTime = platformContext.getMetrics().getOrCreate(EVENT_HASH_TIME_CONFIG);
        eventValidationTime = platformContext.getMetrics().getOrCreate(EVENT_VALIDATION_TIME_CONFIG);
        eventPrehandleTime = platformContext.getMetrics().getOrCreate(EVENT_PREHANDLE_TIME_CONFIG);
        eventPreprocessTime = platformContext.getMetrics().getOrCreate(EVENT_PREPROCESS_TIME_CONFIG);

        final FunctionGauge.Config<Integer> preprocessQueueSizeConfig = new FunctionGauge.Config<>(
                        PLATFORM_CATEGORY, "eventPreprocessQueueSize", Integer.class, preprocessQueueSizeSupplier)
                .withDescription("Number of events in the preprocess queue.");
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

    /**
     * Report the time taken to hash and deduplicate an event.
     */
    public void reportEventHashTime(@NonNull final Duration duration) {
        eventHashTime.update(UNIT_NANOSECONDS.convertTo(duration.toNanos(), UNIT_MILLISECONDS));
    }

    /**
     * Report the time taken to validate an event.
     */
    public void reportEventValidationTime(@NonNull final Duration duration) {
        eventValidationTime.update(UNIT_NANOSECONDS.convertTo(duration.toNanos(), UNIT_MILLISECONDS));
    }

    /**
     * Report the time taken to prehandle all application transactions in an event.
     */
    public void reportEventPrehandleTime(@NonNull final Duration duration) {
        eventPrehandleTime.update(UNIT_NANOSECONDS.convertTo(duration.toNanos(), UNIT_MILLISECONDS));
    }

    /**
     * Report the time taken to perform all preprocessing on an event.
     */
    public void reportEventPreprocessTime(@NonNull final Duration duration) {
        eventPreprocessTime.update(UNIT_NANOSECONDS.convertTo(duration.toNanos(), UNIT_MILLISECONDS));
    }
}
