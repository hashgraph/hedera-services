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

import com.hedera.hapi.node.base.HederaFunctionality;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.util.Set;

@ConfigData("scheduling")
public record SchedulingConfig(
        @ConfigProperty(defaultValue = "false") boolean longTermEnabled,
        @ConfigProperty(defaultValue = "100") long maxTxnPerSec,
        @ConfigProperty(defaultValue = "10000000") long maxNumber,
        @ConfigProperty(defaultValue = "5356800") long maxExpirationFutureSeconds,
        @ConfigProperty(
                        defaultValue =
                                "CONSENSUS_SUBMIT_MESSAGE,CRYPTO_TRANSFER,TOKEN_MINT,TOKEN_BURN,CRYPTO_APPROVE_ALLOWANCE")
                Set<HederaFunctionality> whitelist) {}
