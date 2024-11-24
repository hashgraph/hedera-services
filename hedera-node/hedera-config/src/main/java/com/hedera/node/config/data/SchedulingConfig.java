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

import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import com.hedera.node.config.NetworkProperty;
import com.hedera.node.config.types.HederaFunctionalitySet;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

// Spotless requires way too many newlines, and ends up breaking the string because it forces too many indents.
// spotless:off
@ConfigData("scheduling")
public record SchedulingConfig(
        @ConfigProperty(defaultValue = "1:10") ScaleFactor schedulableCapacityFraction,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean longTermEnabled,
        @ConfigProperty(defaultValue = "100") @NetworkProperty int maxExecutionsPerUserTxn,
        @ConfigProperty(defaultValue = "100") @NetworkProperty int maxTxnPerSec,
        @ConfigProperty(defaultValue = "1000") @NetworkProperty int consTimeSeparationNanos,
        @ConfigProperty(defaultValue = "10000000") @NetworkProperty long maxNumber,
        @ConfigProperty(defaultValue = "5356800") @NetworkProperty long maxExpirationFutureSeconds,
        @ConfigProperty(defaultValue =
                "CryptoTransfer,ConsensusSubmitMessage,TokenBurn,TokenMint,CryptoApproveAllowance")
                @NetworkProperty HederaFunctionalitySet whitelist) {}
// spotless:on
