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

package com.hedera.node.app.utils;

import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultPlatformMetrics;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.metrics.platform.PlatformMetricsFactoryImpl;
import com.swirlds.common.platform.NodeId;
import com.swirlds.metrics.api.Metrics;
import java.util.Random;
import java.util.concurrent.Executors;

public class TestUtils {
    private static final Random RANDOM = new Random(9239992);
    private static final long DEFAULT_NODE_ID = 3;

    /**
     * Generates some random bytes
     *
     * @param length The number of bytes to generate.
     * @return Some random bytes.
     */
    public static byte[] randomBytes(final int length) {
        final byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) RANDOM.nextInt();
        }
        return data;
    }

    public static Metrics metrics() {
        final MetricsConfig metricsConfig =
                HederaTestConfigBuilder.createConfig().getConfigData(MetricsConfig.class);

        return new DefaultPlatformMetrics(
                NodeId.of(DEFAULT_NODE_ID),
                new MetricKeyRegistry(),
                Executors.newSingleThreadScheduledExecutor(),
                new PlatformMetricsFactoryImpl(metricsConfig),
                metricsConfig);
    }
}
