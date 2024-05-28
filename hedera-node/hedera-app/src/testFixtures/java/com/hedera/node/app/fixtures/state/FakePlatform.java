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

package com.hedera.node.app.fixtures.state;

import static com.hedera.node.app.fixtures.AppTestBase.METRIC_EXECUTOR;

import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.utility.NoOpRecycleBin;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Random;

/**
 * A fake implementation of the {@link Platform} interface.
 */
public final class FakePlatform implements Platform {
    private final NodeId selfNodeId;
    private final AddressBook addressBook;
    private final PlatformContext context;
    private final Random random = new Random(12345L);

    public FakePlatform() {
        this.selfNodeId = new NodeId(0L);
        final var addressBuilder = RandomAddressBuilder.create(random);
        final var address =
                addressBuilder.withNodeId(selfNodeId).withWeight(500L).build();

        this.addressBook = new AddressBook(List.of(address));
        this.context = createPlatformContext();
    }

    public FakePlatform(final long nodeId, final AddressBook addresses) {
        this.selfNodeId = new NodeId(nodeId);
        this.addressBook = addresses;
        this.context = createPlatformContext();
    }

    private PlatformContext createPlatformContext() {
        final Configuration configuration = HederaTestConfigBuilder.createConfig();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        final var metrics = new DefaultMetrics(
                selfNodeId,
                new MetricKeyRegistry(),
                METRIC_EXECUTOR,
                new DefaultMetricsFactory(metricsConfig),
                metricsConfig);
        return PlatformContext.create(
                configuration,
                Time.getCurrent(),
                metrics,
                CryptographyHolder.get(),
                FileSystemManager.create(configuration),
                new NoOpRecycleBin());
    }

    @Override
    public PlatformContext getContext() {
        return context;
    }

    @Override
    public NotificationEngine getNotificationEngine() {
        return null;
    }

    @Override
    public Signature sign(byte[] bytes) {
        return null;
    }

    @Override
    public AddressBook getAddressBook() {
        return addressBook;
    }

    @Override
    public NodeId getSelfId() {
        return selfNodeId;
    }

    @Override
    public <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState(@NonNull String s) {
        return null;
    }

    @Override
    public boolean createTransaction(@NonNull byte[] bytes) {
        return false;
    }

    @Override
    public void start() {}
}
