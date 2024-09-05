/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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
import static org.mockito.Mockito.mock;

import com.swirlds.base.time.Time;
import com.swirlds.common.concurrent.ExecutorFactory;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.utility.NoOpRecycleBin;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.metrics.PlatformMetricsProvider;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.TestFileSystemManager;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DefaultPlatformContextTest {

    @Test
    void testNoNullServices() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final NodeId nodeId = new NodeId(3256733545L);
        final PlatformMetricsProvider metricsProvider = new DefaultMetricsProvider(configuration);
        metricsProvider.createGlobalMetrics();
        final MerkleCryptography merkleCryptography = mock(MerkleCryptography.class);

        // when
        final PlatformContext context = new DefaultPlatformContext(
                configuration,
                metricsProvider.createPlatformMetrics(nodeId),
                CryptographyHolder.get(),
                Time.getCurrent(),
                ExecutorFactory.create("test", new PlatformUncaughtExceptionHandler()),
                new TestFileSystemManager(Path.of("/tmp/test")),
                new NoOpRecycleBin(),
                merkleCryptography);

        // then
        assertNotNull(context.getConfiguration(), "Configuration must not be null");
        assertNotNull(context.getMetrics(), "Metrics must not be null");
        assertNotNull(context.getCryptography(), "Cryptography must not be null");
        assertNotNull(context.getTime(), "Time must not be null");
        assertNotNull(context.getFileSystemManager(), "FileSystemManager must not be null");
        assertNotNull(context.getExecutorFactory(), "ExecutorFactory must not be null");
        assertNotNull(context.getMerkleCryptography(), "MerkleCryptography must not be null");
    }
}
