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
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public class TestVirtualMapSettings implements VirtualMapSettings {
    private final VirtualMapSettings original;

    public TestVirtualMapSettings(VirtualMapSettings original) {
        this.original = original;
    }

    @Override
    public double getPercentHashThreads() {
        return original.getPercentHashThreads();
    }

    @Override
    public int getNumHashThreads() {
        return original.getNumHashThreads();
    }

    @Override
    public double getPercentCleanerThreads() {
        return original.getPercentCleanerThreads();
    }

    @Override
    public int getNumCleanerThreads() {
        return original.getNumCleanerThreads();
    }

    @Override
    public long getMaximumVirtualMapSize() {
        return original.getMaximumVirtualMapSize();
    }

    @Override
    public long getVirtualMapWarningThreshold() {
        return original.getVirtualMapWarningThreshold();
    }

    @Override
    public long getVirtualMapWarningInterval() {
        return original.getVirtualMapWarningInterval();
    }

    @Override
    public int getFlushInterval() {
        return original.getFlushInterval();
    }

    @Override
    public long getTotalFlushThreshold() {
        return original.getTotalFlushThreshold();
    }

    @Override
    public long getCopyFlushThreshold() {
        return original.getCopyFlushThreshold();
    }

    @Override
    public int getPreferredFlushQueueSize() {
        return original.getPreferredFlushQueueSize();
    }

    @Override
    public Duration getFlushThrottleStepSize() {
        return original.getFlushThrottleStepSize();
    }

    @Override
    public Duration getMaximumFlushThrottlePeriod() {
        return original.getMaximumFlushThrottlePeriod();
    }
}
