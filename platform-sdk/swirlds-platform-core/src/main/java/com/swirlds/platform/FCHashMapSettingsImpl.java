/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import com.swirlds.fchashmap.FCHashMapSettings;
import com.swirlds.platform.internal.SubSetting;
import java.time.Duration;

/**
 * An implementation of {@link FCHashMapSettings}.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public class FCHashMapSettingsImpl extends SubSetting implements FCHashMapSettings {

    public int maximumGCQueueSize = 200;
    public Duration gcQueueThresholdPeriod = Duration.ofMinutes(1);
    public boolean archiveEnabled = true;

    public int rebuildSplitFactor = 7;
    public int rebuildThreadCount = Runtime.getRuntime().availableProcessors();

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaximumGCQueueSize() {
        return maximumGCQueueSize;
    }

    public void setMaximumGCQueueSize(final int maximumGCQueueSize) {
        this.maximumGCQueueSize = maximumGCQueueSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getGCQueueThresholdPeriod() {
        return gcQueueThresholdPeriod;
    }

    public void setGCQueueThresholdPeriod(final Duration gcQueueThresholdPeriod) {
        this.gcQueueThresholdPeriod = gcQueueThresholdPeriod;
    }

    /**
     * Check if archival of the FCHashMap is enabled.
     */
    @Override
    public boolean isArchiveEnabled() {
        return archiveEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRebuildSplitFactor() {
        return rebuildSplitFactor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRebuildThreadCount() {
        return rebuildThreadCount;
    }

    /**
     * Set if archival of the FCHashMap is enabled.
     */
    public void setArchiveEnabled(final boolean archiveEnabled) {
        this.archiveEnabled = archiveEnabled;
    }
}
