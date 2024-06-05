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

import com.amh.config.NetworkProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("autorenew")
public record AutoRenew2Config(
        @ConfigProperty(defaultValue = "100") @NetworkProperty int numberOfEntitiesToScan,
        @ConfigProperty(defaultValue = "2") @NetworkProperty int maxNumberOfEntitiesToRenewOrDelete,
        @ConfigProperty(defaultValue = "604800") @NetworkProperty long gracePeriod,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean grantFreeRenewals) {}
