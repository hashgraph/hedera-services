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

import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import java.time.Duration;

/**
 * Instance-wide settings for {@code VirtualMap}.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public interface VirtualMapSettings {
    double UNIT_FRACTION_PERCENT = 100.0;

    /**
     * Gets the percentage (from 0.0 to 100.0) of available processors to devote to hashing threads.
     * Ignored if an explicit number of threads is given via {@code virtualMap.numHashThreads}.
     *
     * @return the configured percentage of processors to use for hashing
     */
    double getPercentHashThreads();

    /**
     * The number of threads to devote to hashing.
     *
     * If not set, defaults to the number of threads implied by {@code virtualMap.percentHashThreads}
     * and {@link Runtime#availableProcessors()}.
     *
     * @return the number of threads to use for hashing
     */
    int getNumHashThreads();

    /**
     * Gets the percentage (from 0.0 to 100.0) of available processors to devote to cache cleaner threads.
     * Ignored if an explicit number of threads is given via {@code virtualMap.numCleanerThreads}.
     *
     * @return the configured percentage of processors to use for cache cleaning
     */
    double getPercentCleanerThreads();

    /**
     * The number of threads to devote to cache cleaning.
     *
     * If not set, defaults to the number of threads implied by {@code virtualMap.percentCleanerThreads}
     * and {@link Runtime#availableProcessors()}.
     *
     * @return the number of threads to use for cache cleaning
     */
    int getNumCleanerThreads();

    /**
     * The maximum number of entries allowed in the {@link VirtualMap} instance.
     *
     * If not set, defaults to Integer.MAX_VALUE (2^31 - 1, or 2,147,483,647).
     *
     * @return the maximum number of entries allowed in a VirtualMap.
     */
    long getMaximumVirtualMapSize();

    /**
     * The threshold for the initial warning message to be logged, about the {@link VirtualMap} instance running
     * close to the maximum allowable size.  For example, if set to 5,000,000, then when we are trying to add a
     * new element to a VirtualMap, and there are only 5,000,000 spots left before reaching the limit
     * ({@code getMaximumVirtualMapSize()}), we would log a warning message that there are only 5,000,000 slots left.
     *
     * If not set, defaults to 5,000,000.
     *
     * @return the threshold for the initial warning message to be logged.
     */
    long getVirtualMapWarningThreshold();

    /**
     * The threshold for each subsequent warning message to be logged, after the initial one, about the
     * {@link VirtualMap} instance running close to the maximum allowable size.
     * For example, if set to 100,000, then for each 100,000 elements beyond {@code getVirtualMapWarningThreshold()}
     * that we are closer to 0, we will output an additional warning message that we are nearing the limit
     * ({@code getMaximumVirtualMapSize()}), we would log a warning message that there are only N slots left.
     *
     * If not set, defaults to 100,000.
     *
     * @return the threshold for each subsequent warning message to be logged.
     */
    long getVirtualMapWarningInterval();

    /**
     * The interval between flushing of copies. This value defines the value of N where every Nth copy is flushed.
     * The value must be positive and will typically be a fairly small number, such as 20. The first copy is not
     * flushed, but every Nth copy thereafter is.
     *
     * This setting is ignored, if {@link #getFamilyThrottleThreshold()} is set to a positive value.
     *
     * @return The number of copies between flushes
     */
    int getFlushInterval();

    /**
     * Virtual root flush threshold, in bytes. When virtual root size exceeds the threshold, it is
     * no longer merged with next copies, but marked to flush to disk, when it becomes the oldest
     * accessible version.
     *
     * If a virtual root is explicitly requested to flush with {@link VirtualRootNode#enableFlush()},
     * its size isn't checked against this threshold.
     *
     * @return Virtual root flush threshold
     */
    long getCopyFlushThreshold();

    /**
     * Virtual root family throttle threshold, in bytes. When sum of estimated sizes of all
     * virtual roots in a single family (virtual map) exceeds the threshold, virtual pipeline
     * starts throttling creating new virtual root copies.
     *
     * @return
     * 		Virtual root family throttle threshold
     */
    long getFamilyThrottleThreshold();

    /**
     * The preferred maximum number of virtual maps waiting to be flushed. If more maps than this number are awaiting
     * flushing then slow down fast copies of the virtual map so that flushing can catch up.
     *
     * @return the maximum number of copies awaiting flushing before backpressure is applied
     */
    int getPreferredFlushQueueSize();

    /**
     * For every map copy that is awaiting flushing in excess of {@link #getPreferredFlushQueueSize()}, artificially
     * increase the amount of time required to make a fast copy by this amount of time.
     *
     * @return the amount of time fast copies are forced to wait for each virtual map over the limit
     */
    Duration getFlushThrottleStepSize();

    /**
     * The maximum amount of time that any virtual map fast copy will be delayed due to a flush backlog.
     *
     * @return the maximum flush throttle for any individual fast copy operation
     */
    Duration getMaximumFlushThrottlePeriod();
}
