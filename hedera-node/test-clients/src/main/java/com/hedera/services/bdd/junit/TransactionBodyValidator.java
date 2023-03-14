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

package com.hedera.services.bdd.junit;

import com.hedera.services.bdd.junit.utils.TransactionBodyClassifier;
import com.hedera.services.bdd.suites.records.TransactionBodyValidation;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * This validator checks all the transactions submitted have {@link com.hederahashgraph.api.proto.java.TransactionBody}
 * set, by comparing checking if there are any NONE functionality transactions at the end of the CI test run:
 *
 * <p>It uses the {@link TransactionBodyValidation} suite to perform the queries.
 */
public class TransactionBodyValidator implements RecordStreamValidator {
    private final Map<HederaFunctionality, Long> expectedTxnBodies = new EnumMap<>(HederaFunctionality.class);
    private final TransactionBodyClassifier transactionBodyClassifier = new TransactionBodyClassifier();

    @Override
    @SuppressWarnings("java:S106")
    public void validateRecordsAndSidecars(final List<RecordWithSidecars> recordsWithSidecars) {
        validateTransactionBody(recordsWithSidecars);
        System.out.println("Expected transaction body functions: " + expectedTxnBodies);
    }

    private void validateTransactionBody(final List<RecordWithSidecars> recordsWithSidecars) {
        for (final var recordWithSidecars : recordsWithSidecars) {
            final var items = recordWithSidecars.recordFile().getRecordStreamItemsList();
            for (final var item : items) {
                final var txnType = transactionBodyClassifier.incorporate(item);
                if (expectedTxnBodies.containsKey(txnType)) {
                    expectedTxnBodies.put(txnType, expectedTxnBodies.get(txnType) + 1);
                } else {
                    expectedTxnBodies.put(txnType, 1L);
                }
            }
        }

        if (transactionBodyClassifier.isInvalid()) {
            throw new IllegalStateException("Invalid TransactionBody type HederaFunctionality.NONE with value "
                    + expectedTxnBodies.get(HederaFunctionality.NONE));
        }
    }
}
