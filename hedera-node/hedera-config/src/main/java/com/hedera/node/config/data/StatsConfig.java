/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.config.data;

import com.hedera.node.config.NodeProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.util.List;

@ConfigData("stats")
public record StatsConfig(
        @ConfigProperty(defaultValue = "<GAS>,ThroughputLimits,CreationLimits") @NodeProperty
                List<String> consThrottlesToSample,
        @ConfigProperty(defaultValue = "<GAS>,ThroughputLimits,OffHeapQueryLimits,CreationLimits,FreeQueryLimits,BalanceQueryLimits")
                @NodeProperty
                List<String> hapiThrottlesToSample,
        @ConfigProperty(defaultValue = "0") @NodeProperty int executionTimesToTrack,
        @ConfigProperty(value = "entityUtils.gaugeUpdateIntervalMs", defaultValue = "3000") @NodeProperty
                long entityUtilsGaugeUpdateIntervalMs,
        @ConfigProperty(value = "hapiOps.speedometerUpdateIntervalMs", defaultValue = "3000") @NodeProperty
                long hapiOpsSpeedometerUpdateIntervalMs,
        @ConfigProperty(value = "throttleUtils.gaugeUpdateIntervalMs", defaultValue = "1000") @NodeProperty
                long throttleUtilsGaugeUpdateIntervalMs,
        @ConfigProperty(defaultValue = "10.0") @NodeProperty double runningAvgHalfLifeSecs,
        @ConfigProperty(defaultValue = "10.0") @NodeProperty double speedometerHalfLifeSecs) {}
