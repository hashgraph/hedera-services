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

package com.hedera.node.app.metrics;

import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.state.spi.metrics.StoreMetrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StoreMetricsServiceImpl implements StoreMetricsService {

    private final Map<StoreType, StoreMetricsImpl> storeMetricsMap;

    @Inject
    public StoreMetricsServiceImpl(@NonNull final Metrics metrics) {
        this.storeMetricsMap = new EnumMap<>(StoreType.class);
        storeMetricsMap.put(StoreType.TOPIC, new StoreMetricsImpl(metrics, "topics"));
        storeMetricsMap.put(StoreType.ACCOUNT, new StoreMetricsImpl(metrics, "accounts"));
        storeMetricsMap.put(StoreType.AIRDROP, new StoreMetricsImpl(metrics, "airdrops"));
        storeMetricsMap.put(StoreType.NFT, new StoreMetricsImpl(metrics, "nfts"));
        storeMetricsMap.put(StoreType.TOKEN, new StoreMetricsImpl(metrics, "tokens"));
        storeMetricsMap.put(
                StoreType.TOKEN_RELATION, new StoreMetricsImpl(metrics, "tokenAssociations", "token associations"));
        storeMetricsMap.put(StoreType.FILE, new StoreMetricsImpl(metrics, "files"));
        storeMetricsMap.put(StoreType.SLOT_STORAGE, new StoreMetricsImpl(metrics, "storageSlots", "storage slots"));
        storeMetricsMap.put(StoreType.CONTRACT, new StoreMetricsImpl(metrics, "contracts"));
        storeMetricsMap.put(StoreType.SCHEDULE, new StoreMetricsImpl(metrics, "schedules"));
        storeMetricsMap.put(StoreType.NODE, new StoreMetricsImpl(metrics, "nodes"));
    }

    @Override
    public StoreMetrics get(@NonNull final StoreType storeType, final long capacity) {
        final var metric = storeMetricsMap.get(storeType);
        metric.updateCapacity(capacity);
        return metric;
    }
}
