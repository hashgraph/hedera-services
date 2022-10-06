/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stats;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.utils.Pause;
import com.hedera.services.utils.SleepingPause;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.virtualmap.VirtualMap;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicesStatsManagerTest {
    private final long opsUpdateIntervalMs = 1_000;

    private final long throttleGaugesUpdateIntervalMs = 2_000;
    private final long entityUtilGaugesUpdateIntervalMs = 3_000;
    @Mock private Pause pause;
    @Mock private Platform platform;
    @Mock private Function<Runnable, Thread> threads;
    @Mock private HapiOpCounters counters;
    @Mock private MiscRunningAvgs runningAvgs;
    @Mock private MiscSpeedometers miscSpeedometers;
    @Mock private HapiOpSpeedometers speedometers;
    @Mock private NodeLocalProperties properties;
    @Mock private VirtualMap<ContractKey, IterableContractValue> storage;
    @Mock private VirtualMap<VirtualBlobKey, VirtualBlobValue> bytecode;
    @Mock private ThrottleGauges throttleGauges;
    @Mock private EntityUtilGauges entityUtilGauges;
    @Mock private ExpiryStats expiryStats;

    ServicesStatsManager subject;

    @BeforeEach
    void setup() {
        ServicesStatsManager.loopFactory = threads;
        ServicesStatsManager.pause = pause;

        given(platform.getSelfId()).willReturn(new NodeId(false, 123L));
        given(properties.hapiOpsStatsUpdateIntervalMs()).willReturn(opsUpdateIntervalMs);
        given(properties.entityUtilStatsUpdateIntervalMs())
                .willReturn(entityUtilGaugesUpdateIntervalMs);
        given(properties.throttleUtilStatsUpdateIntervalMs())
                .willReturn(throttleGaugesUpdateIntervalMs);

        subject =
                new ServicesStatsManager(
                        expiryStats,
                        counters,
                        throttleGauges,
                        runningAvgs,
                        entityUtilGauges,
                        miscSpeedometers,
                        speedometers,
                        properties,
                        () -> storage,
                        () -> bytecode);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ServicesStatsManager.pause = SleepingPause.SLEEPING_PAUSE;
        ServicesStatsManager.loopFactory =
                runnable ->
                        new Thread(
                                () -> {
                                    while (true) {
                                        runnable.run();
                                    }
                                });
    }

    @Test
    void initsAsExpected() {
        // setup:
        Thread thread = mock(Thread.class);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        given(pause.forMs(anyLong())).willReturn(true);
        given(threads.apply(captor.capture())).willReturn(thread);

        // when:
        subject.initializeFor(platform);

        // then:
        verify(counters).registerWith(platform);
        verify(speedometers).registerWith(platform);
        verify(expiryStats).registerWith(platform);
        verify(miscSpeedometers).registerWith(platform);
        verify(runningAvgs).registerWith(platform);
        verify(throttleGauges).registerWith(platform);
        verify(entityUtilGauges).registerWith(platform);
        verify(storage).registerMetrics(any());
        verify(bytecode).registerMetrics(any());
        // and:
        verify(thread).start();
        verify(thread)
                .setName(String.format(ServicesStatsManager.STATS_UPDATE_THREAD_NAME_TPL, 123L));
        // and when:
        for (int i = 0; i < 6; i++) {
            captor.getValue().run();
        }
        // then:
        verify(pause, times(6)).forMs(1_000L);
        verify(speedometers, times(6)).updateAll();
        verify(throttleGauges, times(3)).updateAll();
        verify(entityUtilGauges, times(2)).updateAll();
    }
}
