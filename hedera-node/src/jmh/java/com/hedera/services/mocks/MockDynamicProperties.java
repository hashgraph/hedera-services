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
package com.hedera.services.mocks;

import com.hedera.services.context.properties.GlobalDynamicProperties;

public class MockDynamicProperties extends GlobalDynamicProperties {
    private final int maxContractKvPairs;
    private final long maxAggregateKvPairs;

    public static GlobalDynamicProperties mockPropertiesWith(
            final int maxContractKvPairs, final int maxAggregateKvPairs) {
        return new MockDynamicProperties(maxContractKvPairs, maxAggregateKvPairs);
    }

    private MockDynamicProperties(final int maxContractKvPairs, final int maxAggregateKvPairs) {
        super(null, null);
        this.maxContractKvPairs = maxContractKvPairs;
        this.maxAggregateKvPairs = maxAggregateKvPairs;
    }

    @Override
    public void reload() {
        /* No-op */
    }

    @Override
    public long maxDailyStakeRewardThPerH() {
        return 17_808L;
    }

    @Override
    public long maxAggregateContractKvPairs() {
        return maxAggregateKvPairs;
    }

    @Override
    public int maxIndividualContractKvPairs() {
        return maxContractKvPairs;
    }

    @Override
    public boolean isStakingEnabled() {
        return true;
    }
}
