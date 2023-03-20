/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap;

import java.time.Duration;

/**
 * {@link VirtualMapSettings} implementation with all defaults. Necessary for testing
 * {@link VirtualMapSettingsFactory} client code running in an environment without Browser-configured settings.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public final class DefaultVirtualMapSettings implements VirtualMapSettings {
    public static final int DEFAULT_NUM_HASH_THREADS = -1;
    public static final double DEFAULT_PERCENT_HASH_THREADS = 50.0;
    public static final int DEFAULT_NUM_CLEANER_THREADS = -1;
    public static final double DEFAULT_PERCENT_CLEANER_THREADS = 25.0;
    public static final long DEFAULT_MAXIMUM_VIRTUAL_MAP_SIZE = Integer.MAX_VALUE;
    public static final long DEFAULT_VIRTUAL_MAP_WARNING_THRESHOLD = 5_000_000;
    public static final long DEFAULT_VIRTUAL_MAP_WARNING_INTERVAL = 100_000;
    public static final int DEFAULT_FLUSH_INTERVAL = 20;
    public static final long DEFAULT_COPY_FLUSH_THRESHOLD = 500_000_000L;
    public static final long DEFAULT_FAMILY_THROTTLE_THRESHOLD = 2_500_000_000L;
    public static final int DEFAULT_PREFERRED_FLUSH_QUEUE_SIZE = 2;
    public static final Duration DEFAULT_FLUSH_THROTTLE_STEP_SIZE = Duration.ofMillis(200);
    public static final Duration DEFAULT_MAXIMUM_FLUSH_THROTTLE_PERIOD = Duration.ofSeconds(5);

    /**
     * {@inheritDoc}
     */
    @Override
    public double getPercentHashThreads() {
        return DEFAULT_PERCENT_HASH_THREADS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumHashThreads() {
        final int threads = Integer.getInteger("hashingThreadCount", (int)
                (Runtime.getRuntime().availableProcessors() * (getPercentHashThreads() / UNIT_FRACTION_PERCENT)));

        return Math.max(1, threads);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getPercentCleanerThreads() {
        return DEFAULT_PERCENT_CLEANER_THREADS;
    }

    @Override
    public int getNumCleanerThreads() {
        final int numProcessors = Runtime.getRuntime().availableProcessors();
        final int threads = Integer.getInteger(
                "cleanerThreadCount", (int) (numProcessors * (getPercentCleanerThreads() / UNIT_FRACTION_PERCENT)));

        return Math.max(1, threads);
    }

    @Override
    public long getMaximumVirtualMapSize() {
        return DEFAULT_MAXIMUM_VIRTUAL_MAP_SIZE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getVirtualMapWarningThreshold() {
        return DEFAULT_VIRTUAL_MAP_WARNING_THRESHOLD;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getVirtualMapWarningInterval() {
        return DEFAULT_VIRTUAL_MAP_WARNING_INTERVAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getFlushInterval() {
        return DEFAULT_FLUSH_INTERVAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFamilyThrottleThreshold() {
        return DEFAULT_FAMILY_THROTTLE_THRESHOLD;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCopyFlushThreshold() {
        return DEFAULT_COPY_FLUSH_THRESHOLD;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPreferredFlushQueueSize() {
        return DEFAULT_PREFERRED_FLUSH_QUEUE_SIZE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getFlushThrottleStepSize() {
        return DEFAULT_FLUSH_THROTTLE_STEP_SIZE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getMaximumFlushThrottlePeriod() {
        return DEFAULT_MAXIMUM_FLUSH_THROTTLE_PERIOD;
    }
}
