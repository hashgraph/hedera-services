/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss;
/**
 * An enum representing the status of the TSS keying process.
 * This status SHALL be used to determine the state of the TSS keying process.
 */
public enum TssKeyingStatus {

    /**
     * The TSS keying process has not yet reached the threshold for encryption
     * keys.
     */
    WAITING_FOR_ENCRYPTION_KEYS,

    /**
     * The TSS keying process has not yet reached the threshold for TSS messages.
     */
    WAITING_FOR_THRESHOLD_TSS_MESSAGES,

    /**
     * The TSS keying process has not yet reached the threshold for TSS votes.
     */
    WAITING_FOR_THRESHOLD_TSS_VOTES,

    /**
     * The TSS keying process has completed and the ledger id is set.
     */
    KEYING_COMPLETE
}
