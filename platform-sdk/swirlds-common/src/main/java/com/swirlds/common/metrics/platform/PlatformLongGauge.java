/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.platform;

import com.swirlds.common.metrics.PlatformMetric;
import com.swirlds.metrics.impl.DefaultLongGauge;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A long gauge metric that is associated with the platform.
 */
public class PlatformLongGauge extends DefaultLongGauge implements PlatformMetric {

    /**
     * Constructs a new PlatformLongGauge with the given configuration.
     * @param config the configuration for this long gauge
     */
    public PlatformLongGauge(@NonNull final Config config) {
        super(config);
    }
}
