/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import static com.swirlds.demo.merkle.map.FCMConfig.FCMQueryConfig;
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.config.api.Configuration;
import com.swirlds.demo.merkle.map.MapValueData;
import com.swirlds.demo.merkle.map.internal.DummyExpectedFCMFamily;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.map.test.lifecycle.ExpectedValue;
import com.swirlds.merkle.map.test.pta.MapKey;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class FCMQueryControllerTest {

    private static final Random RANDOM = new Random();

    @Test
    @Tag(TIME_CONSUMING)
    @SuppressWarnings("unchecked")
    void executeTest() throws InterruptedException {
        final TransactionCounter transactionCounter = new TransactionCounter();
        transactionCounter.fcmFCQCreateAmount = 100;
        final List<TransactionCounter> transactionCounters = new ArrayList<>();
        transactionCounters.add(transactionCounter);

        final MapValueData value = new MapValueData(0, 0, 0, true, RANDOM.nextLong());

        final ExpectedValue expectedValue = new ExpectedValue();
        expectedValue.setUid(value.getUid());

        final MerkleMap<MapKey, MapValueData> map = new MerkleMap<>() {
            @Override
            public MapValueData get(final Object key) {
                return value;
            }
        };

        final Map<MapKey, ExpectedValue> expectedMap = mock(Map.class);
        when(expectedMap.get(any(MapKey.class))).thenReturn(expectedValue);

        final DummyExpectedFCMFamily expectedFCMFamily = new DummyExpectedFCMFamily(0);
        expectedFCMFamily.setExpectedMap(expectedMap);

        final AddressBook addressBook =
                new RandomAddressBookGenerator().setSize(1).build();
        final PlatformTestingToolState state = new PlatformTestingToolState() {
            @Override
            public List<TransactionCounter> getTransactionCounter() {
                return transactionCounters;
            }
        };

        state.initChildren();

        state.getStateMap().setMap(map);
        final AutoCloseableWrapper<? extends SwirldState> wrapper = new AutoCloseableWrapper<>(state, this::close);

        final MetricKeyRegistry registry = new MetricKeyRegistry();
        when(registry.register(any(), any(), any())).thenReturn(true);

        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);

        final Metrics metrics = new DefaultMetrics(
                null, registry, mock(ScheduledExecutorService.class), new DefaultMetricsFactory(), metricsConfig);

        final MockPlatform platform = MockPlatform.newBuilder()
                .setAddressBook(addressBook)
                .setNodeId(new NodeId(false, 0))
                .setLastCompleteSwirldState(wrapper)
                .setMetrics(metrics)
                .build();

        state.init(platform, new DualStateImpl(), InitTrigger.GENESIS, SoftwareVersion.NO_VERSION);
        state.setExpectedFCMFamily(expectedFCMFamily);

        final FCMQueryConfig config = new FCMQueryConfig();
        config.setNumberOfThreads(1);
        config.setQps(10);

        final FCMQueryController controller = new FCMQueryController(config, platform);
        controller.launch();
        Thread.sleep(1_000);

        verify(expectedMap, atLeast(10)).get(any(MapKey.class));
    }

    private void close() {}
}
