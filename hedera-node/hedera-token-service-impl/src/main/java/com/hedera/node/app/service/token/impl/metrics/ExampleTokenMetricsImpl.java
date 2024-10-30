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

package com.hedera.node.app.service.token.impl.metrics;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.node.app.service.token.ExampleTokenMetrics;
import com.hedera.node.app.service.token.TokenService;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Metrics collection management for Token service
 */
public class ExampleTokenMetricsImpl implements ExampleTokenMetrics {
    private static final Logger log = LogManager.getLogger(ExampleTokenMetricsImpl.class);

    private static final String METRIC_CATEGORY = "app";
    private static final String METRIC_TXN_UNIT = "txs";

    private final Counter serialMintCounter;

    /**
     * Constructor
     *
     * @param metrics the instance to create this service's metrics with
     */
    public ExampleTokenMetricsImpl(@NonNull final Metrics metrics) {
        // Instantiate metric for counting serials minted
        final var serialsMintedMetricName = METRIC_CATEGORY + "_" + TokenService.NAME + "_mintedSerials_total";
        final var serialsMintedDescr = "Count of serials minted since node startup";
        final var serialsMintedConfig = new Counter.Config(METRIC_CATEGORY, serialsMintedMetricName)
                .withDescription(serialsMintedDescr)
                .withUnit(METRIC_TXN_UNIT);

        serialMintCounter = metrics.getOrCreate(serialsMintedConfig);
    }

    @Override
    public void incrementBySerialsCreated(@NonNull final TokenID tokenID, @NonNull final List<Long> serials) {
        serialMintCounter.add(serials.size());
        log.info(
                "Serials {} minted (token ID {}). Current serials minted count: {}",
                serials,
                tokenID,
                serialMintCounter.get());
    }
}
