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

package com.swirlds.common.config;

import com.swirlds.common.config.validators.DefaultConfigViolation;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.annotation.ConstraintMethod;

/**
 * Configuration regarding transactions
 *
 * @param transactionMaxBytes
 * 		maximum number of bytes allowed in a transaction
 * @param maxAddressSizeAllowed
 * 		the maximum number of address allowed in a address book, the same as the maximum allowed network size
 * @param maxTransactionBytesPerEvent
 *      the maximum number of bytes that a single event may contain, not including the event headers. if a single
 *      transaction exceeds this limit, then the event will contain the single transaction only
 * @param maxTransactionCountPerEvent
 * 		the maximum number of transactions that a single event may contain
 * @param throttleTransactionQueueSize
 * 		Stop accepting new non-system transactions into the 4 transaction queues if any of them have more than this
 * 		many.
 */
@ConfigData("transaction")
public record TransactionConfig(
        @ConstraintMethod("validateTransactionBytes") @ConfigProperty(defaultValue = "6144") int transactionMaxBytes,
        @ConfigProperty(defaultValue = "1024") int maxAddressSizeAllowed,
        @ConfigProperty(defaultValue = "245760") int maxTransactionBytesPerEvent,
        @ConfigProperty(defaultValue = "245760") int maxTransactionCountPerEvent,
        @ConfigProperty(defaultValue = "100000") int throttleTransactionQueueSize) {
    public ConfigViolation validateTransactionBytes(final Configuration configuration) {
        final TransactionConfig transactionConfig = configuration.getConfigData(TransactionConfig.class);
        final int transactionMaxBytes = transactionConfig.transactionMaxBytes();
        final int maxTransactionBytesPerEvent = transactionConfig.maxTransactionBytesPerEvent();
        if (maxTransactionBytesPerEvent < transactionMaxBytes) {
            return new DefaultConfigViolation(
                    "transactionMaxBytes",
                    transactionMaxBytes + "",
                    true,
                    "Cannot configure transactionMaxBytes to " + transactionMaxBytes + ", it must be < "
                            + maxTransactionBytesPerEvent);
        }

        return null;
    }
}
