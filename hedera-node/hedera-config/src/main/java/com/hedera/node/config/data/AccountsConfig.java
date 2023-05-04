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

package com.hedera.node.config.data;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("accounts")
public record AccountsConfig(
        @ConfigProperty(defaultValue = "55") long addressBookAdmin,
        @ConfigProperty(defaultValue = "57") long exchangeRatesAdmin,
        @ConfigProperty(defaultValue = "56") long feeSchedulesAdmin,
        @ConfigProperty(defaultValue = "58") long freezeAdmin,
        @ConfigProperty(defaultValue = "100") long lastThrottleExempt,
        @ConfigProperty(defaultValue = "801") long nodeRewardAccount,
        @ConfigProperty(defaultValue = "800") long stakingRewardAccount,
        @ConfigProperty(defaultValue = "50") long systemAdmin,
        @ConfigProperty(defaultValue = "59") long systemDeleteAdmin,
        @ConfigProperty(defaultValue = "60") long systemUndeleteAdmin,
        @ConfigProperty(defaultValue = "2") long treasury,
        @ConfigProperty(defaultValue = "false") boolean storeOnDisk,
        @ConfigProperty(defaultValue = "5000000") long maxNumber,
        @ConfigProperty(value = "blocklist.enabled", defaultValue = "false") boolean blocklistEnabled,
        @ConfigProperty(value = "blocklist.resource", defaultValue = "") String blocklistResource) {
}
