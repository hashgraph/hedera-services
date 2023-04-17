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

package com.swirlds.common.context.internal;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.MetricsProvider;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.system.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import org.junit.jupiter.api.Test;

class DefaultPlatformContextTest {

    @Test
    void testNoNullServices() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final NodeId nodeId = new NodeId(false, 3256733545L);
        final MetricsProvider metricsProvider = new DefaultMetricsProvider(configuration);
        metricsProvider.createGlobalMetrics();

        // when
        final PlatformContext context = new DefaultPlatformContext(nodeId, metricsProvider, configuration);

        // then
        assertNotNull(context.getConfiguration(), "Configuration must not be null");
        assertNotNull(context.getMetrics(), "Metrics must not be null");
        assertNotNull(context.getCryptography(), "Cryptography must not be null");
    }
}
