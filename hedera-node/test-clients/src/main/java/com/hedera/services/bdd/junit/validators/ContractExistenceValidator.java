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

package com.hedera.services.bdd.junit.validators;

import static com.hedera.services.bdd.junit.BalanceReconciliationValidator.streamOfItemsFrom;

import com.hedera.services.bdd.junit.RecordStreamValidator;
import com.hedera.services.bdd.junit.RecordWithSidecars;
import com.hedera.services.stream.proto.RecordStreamItem;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assertions;

/** A simple validator that asserts a contract that was created appears in the record stream */
public class ContractExistenceValidator implements RecordStreamValidator {
    private final String name;
    private final Instant consensusTimestamp;

    public ContractExistenceValidator(final String name, final Instant consensusTimestamp) {
        this.name = name;
        this.consensusTimestamp = consensusTimestamp;
    }

    @Override
    public void validateRecordsAndSidecars(final List<RecordWithSidecars> recordFiles) {
        final var contractExists = new AtomicBoolean();

        streamOfItemsFrom(recordFiles).filter(this::isAtConsensusTime).forEach(item -> {
            final var receipt = item.getRecord().getReceipt();
            if (receipt.hasContractID()) {
                contractExists.set(true);
            }
        });
        if (!contractExists.get()) {
            Assertions.fail("Expected '" + name + "' to be created at " + consensusTimestamp + ", but it was not");
        }
    }

    private boolean isAtConsensusTime(final RecordStreamItem item) {
        final var timestamp = item.getRecord().getConsensusTimestamp();
        final var timeHere = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        return timeHere.equals(consensusTimestamp);
    }
}
