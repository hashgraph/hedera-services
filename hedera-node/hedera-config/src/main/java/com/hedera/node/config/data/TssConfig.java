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
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Configuration for the TSS service.
 * @param maxSharesPerNode                The maximum number of shares that can be assigned to a node.
 * @param timesToTrySubmission            The number of times to retry a submission on getting an {@link IllegalStateException}
 * @param retryDelay                      The delay between retries
 * @param distinctTxnIdsToTry             The number of distinct transaction IDs to try in the event of a duplicate id
 * @param keyCandidateRoster              A feature flag for TSS; set this to true to enable the process that will key
 * @param keyActiveRoster                 A test-only configuration; set this to true to enable the process that will
 *                                        key the candidate roster with TSS key material, without waiting for upgrade
 *                                        boundary.
 * @param signatureLivenessPeriodMinutes  The amount of time a share signature is held in memory before being
 *                                        discarded in minutes
 * @param ledgerSignatureFailureThreshold The number of consecutive failures to produce a ledger signature before
 *                                        logging an error
 */
@ConfigData("tss")
public record TssConfig(
        @ConfigProperty(defaultValue = "3") @NetworkProperty long maxSharesPerNode,
        @ConfigProperty(defaultValue = "50") @NetworkProperty int timesToTrySubmission,
        @ConfigProperty(defaultValue = "5s") @NetworkProperty Duration retryDelay,
        @ConfigProperty(defaultValue = "10") @NetworkProperty int distinctTxnIdsToTry,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean keyCandidateRoster,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean signWithLedgerId,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean keyActiveRoster,
        @ConfigProperty(defaultValue = "5") @NetworkProperty int signatureLivenessPeriodMinutes,
        @ConfigProperty(defaultValue = "2") @NetworkProperty int ledgerSignatureFailureThreshold) {}
