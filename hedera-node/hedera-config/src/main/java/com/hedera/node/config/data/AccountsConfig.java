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

import com.hedera.node.config.NetworkProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("accounts")
public record AccountsConfig(
        @ConfigProperty(defaultValue = "55") @NetworkProperty long addressBookAdmin,
        @ConfigProperty(defaultValue = "57") @NetworkProperty long exchangeRatesAdmin,
        @ConfigProperty(defaultValue = "56") @NetworkProperty long feeSchedulesAdmin,
        @ConfigProperty(defaultValue = "58") @NetworkProperty long freezeAdmin,
        @ConfigProperty(defaultValue = "100") @NetworkProperty long lastThrottleExempt,
        @ConfigProperty(defaultValue = "801") @NetworkProperty long nodeRewardAccount,
        @ConfigProperty(defaultValue = "800") @NetworkProperty long stakingRewardAccount,
        @ConfigProperty(defaultValue = "50") @NetworkProperty long systemAdmin,
        @ConfigProperty(defaultValue = "59") @NetworkProperty long systemDeleteAdmin,
        @ConfigProperty(defaultValue = "60") @NetworkProperty long systemUndeleteAdmin,
        @ConfigProperty(defaultValue = "2") @NetworkProperty long treasury,
        @ConfigProperty(defaultValue = "true") @NetworkProperty boolean storeOnDisk,
        @ConfigProperty(defaultValue = "20000000") @NetworkProperty long maxNumber,
        @ConfigProperty(value = "blocklist.enabled", defaultValue = "false") @NetworkProperty boolean blocklistEnabled,
        @ConfigProperty(value = "blocklist.path", defaultValue = "") @NetworkProperty String blocklistResource) {}
