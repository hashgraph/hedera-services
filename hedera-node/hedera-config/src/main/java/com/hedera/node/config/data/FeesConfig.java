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

import com.hedera.node.config.NetworkProperty;
import com.hedera.node.config.types.CongestionMultipliers;
import com.hedera.node.config.types.EntityScaleFactors;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("fees")
public record FeesConfig(
        @ConfigProperty(defaultValue = "60") @NetworkProperty int minCongestionPeriod,
        @ConfigProperty(defaultValue = "90,10x,95,25x,99,100x") CongestionMultipliers percentCongestionMultipliers,
        @ConfigProperty(defaultValue = "DEFAULT(0,1:1)") EntityScaleFactors percentUtilizationScaleFactors,
        @ConfigProperty(defaultValue = "380") @NetworkProperty int tokenTransferUsageMultiplier) {}
