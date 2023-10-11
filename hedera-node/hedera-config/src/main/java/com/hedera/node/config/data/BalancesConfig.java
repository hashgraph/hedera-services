/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

@ConfigData("balances")
public record BalancesConfig(
        @ConfigProperty(value = "exportDir.path", defaultValue = "/opt/hgcapp/accountBalances/") @NodeProperty
                String exportDirPath,
        @ConfigProperty(defaultValue = "false") @NodeProperty boolean exportEnabled,
        @ConfigProperty(defaultValue = "900") @NodeProperty int exportPeriodSecs,
        @ConfigProperty(defaultValue = "true") @NodeProperty boolean exportTokenBalances,
        @ConfigProperty(defaultValue = "0") @NodeProperty long nodeBalanceWarningThreshold,
        @ConfigProperty(defaultValue = "true") @NodeProperty boolean compressOnCreation) {}
