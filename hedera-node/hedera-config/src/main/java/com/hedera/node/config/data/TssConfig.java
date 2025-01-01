/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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
import com.hedera.node.config.NodeProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Configuration for the TSS service.
 * @param hintsEnabled whether hinTS signatures are enabled
 * @param historyEnabled whether address book proofs are enabled
 * @param urgentProofKeysWaitPeriod the wait period for urgently collection of proof keys
 * @param relaxedProofKeysWaitPeriod the wait period for background collection of proof keys
 * @param maxSharesPerNode the maximum number of shares that can be assigned to a node.
 * @param timesToTrySubmission the number of times to retry a submission on getting an {@link IllegalStateException}
 * @param retryDelay the delay between retries
 * @param distinctTxnIdsToTry the number of distinct transaction IDs to try in the event of a duplicate id
 * @param keyCandidateRoster a feature flag for TSS; set this to true to enable the process that will key
 */
@ConfigData("tss")
public record TssConfig(
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean hintsEnabled,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean historyEnabled,
        @ConfigProperty(defaultValue = "60s") @NodeProperty Duration urgentProofKeysWaitPeriod,
        @ConfigProperty(defaultValue = "300s") @NodeProperty Duration relaxedProofKeysWaitPeriod,
        @ConfigProperty(defaultValue = "3") @NetworkProperty int maxSharesPerNode,
        @ConfigProperty(defaultValue = "50") @NetworkProperty int timesToTrySubmission,
        @ConfigProperty(defaultValue = "5s") @NetworkProperty Duration retryDelay,
        @ConfigProperty(defaultValue = "10") @NetworkProperty int distinctTxnIdsToTry,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean keyCandidateRoster,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean signWithLedgerId) {}
