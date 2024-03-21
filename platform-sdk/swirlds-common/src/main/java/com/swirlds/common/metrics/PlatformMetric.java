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

package com.swirlds.common.metrics;

import com.swirlds.common.metrics.statistics.StatsBuffered;
import com.swirlds.metrics.api.Metric;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * This class is only used to simplify the migration and will be removed afterwards.
 *
 * @deprecated This class is only temporary and will be removed during the Metric overhaul.
 */
@Deprecated(forRemoval = true)
public interface PlatformMetric extends Metric {

    /**
     * This method returns the {@link StatsBuffered} of this metric, if there is one.
     * <p>
     * This method is only used to simplify the migration and will be removed afterwards
     *
     * @return the {@code StatsBuffered}, if there is one, {@code null} otherwise
     * @deprecated This method is only temporary and will be removed during the Metric overhaul.
     */
    @Nullable
    @Deprecated(forRemoval = true)
    StatsBuffered getStatsBuffered();
}
