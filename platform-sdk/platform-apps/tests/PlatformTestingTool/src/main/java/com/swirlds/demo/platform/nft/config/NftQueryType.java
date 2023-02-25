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

package com.swirlds.demo.platform.nft.config;

import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.demo.platform.PlatformTestingToolState;
import com.swirlds.demo.platform.nft.NftSimpleQuerier;
import java.util.function.BiConsumer;

/**
 * This enum describes the different types of NFT queries that can be performed.
 */
public enum NftQueryType {

    /**
     * Given an account, fetch the tokens owned by that account.
     */
    SIMPLE(NftSimpleQuerier::execute);

    private final BiConsumer<PlatformTestingToolState, SpeedometerMetric> executor;

    NftQueryType(final BiConsumer<PlatformTestingToolState, SpeedometerMetric> executor) {
        this.executor = executor;
    }

    /**
     * Execute a given query.
     *
     * @param state
     * 		the state against which the query should be executed
     * @param metric
     * 		performance data about the query should be written here.
     */
    public void execute(final PlatformTestingToolState state, final SpeedometerMetric metric) {
        executor.accept(state, metric);
    }
}
