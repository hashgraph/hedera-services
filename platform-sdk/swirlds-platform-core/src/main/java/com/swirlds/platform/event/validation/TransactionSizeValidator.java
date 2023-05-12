/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.validation;

import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;

import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.GossipEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Determines whether total size of all transactions in a given event is too large
 */
public class TransactionSizeValidator implements GossipEventValidator {
    private static final Logger logger = LogManager.getLogger(TransactionSizeValidator.class);
    private final int maxTransactionBytesPerEvent;

    public TransactionSizeValidator(final int maxTransactionBytesPerEvent) {
        this.maxTransactionBytesPerEvent = maxTransactionBytesPerEvent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventValid(final GossipEvent event) {
        if (event.getHashedData().getTransactions() == null) {
            return true;
        }

        // Sum the total size of all transactions about to be included in this event
        int tmpEventTransSize = 0;
        for (final Transaction t : event.getHashedData().getTransactions()) {
            tmpEventTransSize += t.getSerializedLength();
        }
        final int finalEventTransSize = tmpEventTransSize;

        // Ignore & log if we have encountered a transaction larger than the limit
        // This might be due to a malicious node in the network
        if (tmpEventTransSize > maxTransactionBytesPerEvent) {
            logger.error(
                    INVALID_EVENT_ERROR.getMarker(),
                    "maxTransactionBytesPerEvent exceeded by event {} with a total size of {} bytes",
                    () -> EventStrings.toShortString(event),
                    () -> finalEventTransSize);
            return false;
        }

        return true;
    }
}
