/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.spi.config.data;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.util.List;

@ConfigData("stats")
public record StatsConfig(@ConfigProperty List<String> consThrottlesToSample,
                          @ConfigProperty List<String> hapiThrottlesToSample,
                          @ConfigProperty int executionTimesToTrack,
                          @ConfigProperty("entityUtils.gaugeUpdateIntervalMs") long entityUtilsGaugeUpdateIntervalMs,
                          @ConfigProperty("hapiOps.speedometerUpdateIntervalMs") long hapiOpsSpeedometerUpdateIntervalMs,
                          @ConfigProperty("throttleUtils.gaugeUpdateIntervalMs") long throttleUtilsGaugeUpdateIntervalMs,
                          @ConfigProperty double runningAvgHalfLifeSecs,
                          @ConfigProperty double speedometerHalfLifeSecs) {
}
