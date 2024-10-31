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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import static java.util.Objects.requireNonNull;

import com.swirlds.base.time.Time;
import com.swirlds.common.concurrent.ExecutorFactory;
import com.swirlds.common.config.TransactionConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.merkle.crypto.MerkleCryptographyFactory;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultPlatformMetrics;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.metrics.platform.PlatformMetricsFactoryImpl;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.config.BasicConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ScheduledExecutorService;
import org.jetbrains.annotations.NotNull;

public class FakePlatformContext implements PlatformContext {
    private final Configuration platformConfig = ConfigurationBuilder.create()
            .withConfigDataType(MetricsConfig.class)
            .withConfigDataType(TransactionConfig.class)
            .withConfigDataType(CryptoConfig.class)
            .withConfigDataType(BasicConfig.class)
            .build();

    private final Metrics metrics;

    public FakePlatformContext(
            @NonNull final NodeId defaultNodeId, @NonNull final ScheduledExecutorService executorService) {
        requireNonNull(defaultNodeId);
        requireNonNull(executorService);
        final var metricsConfig = platformConfig.getConfigData(MetricsConfig.class);
        this.metrics = new DefaultPlatformMetrics(
                defaultNodeId,
                new MetricKeyRegistry(),
                executorService,
                new PlatformMetricsFactoryImpl(metricsConfig),
                metricsConfig);
    }

    @NonNull
    @Override
    public Configuration getConfiguration() {
        return platformConfig;
    }

    @NonNull
    @Override
    public Cryptography getCryptography() {
        return CryptographyHolder.get();
    }

    @NonNull
    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    @NonNull
    @Override
    public Time getTime() {
        throw new UnsupportedOperationException("Not used by Hedera");
    }

    @NonNull
    @Override
    public FileSystemManager getFileSystemManager() {
        throw new UnsupportedOperationException("Not used by Hedera");
    }

    @NonNull
    @Override
    public ExecutorFactory getExecutorFactory() {
        throw new UnsupportedOperationException("Not used by Hedera");
    }

    @NonNull
    @Override
    public RecycleBin getRecycleBin() {
        throw new UnsupportedOperationException("Not used by Hedera");
    }

    @NotNull
    @Override
    public MerkleCryptography getMerkleCryptography() {
        return MerkleCryptographyFactory.create(platformConfig, getCryptography());
    }
}
