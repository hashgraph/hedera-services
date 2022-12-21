/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.junit.TestBase.concurrentExecutionOf;

import com.hedera.services.bdd.suites.records.BalanceValidation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordBalanceValidator implements RecordStreamValidator {
    @Override
    @SuppressWarnings("java:S106")
    public void validate(final List<RecordWithSidecars> recordsWithSidecars) {
        final var expectedBalances = getExpectedBalanceFrom(recordsWithSidecars);
        System.out.println("Expected balances: " + expectedBalances);

        final var validationSpecs =
                TestBase.extractContextualizedSpecsFrom(
                        List.of(() -> new BalanceValidation(expectedBalances)),
                        TestBase::contextualizedSpecsFromConcurrent);

        concurrentExecutionOf(validationSpecs);
    }

    private Map<Long, Long> getExpectedBalanceFrom(
            final List<RecordWithSidecars> recordsWithSidecars) {
        final Map<Long, Long> expectedBalances = new HashMap<>();

        for (final var recordWithSidecars : recordsWithSidecars) {
            final var items = recordWithSidecars.recordFile().getRecordStreamItemsList();
            for (final var item : items) {
                final var grpcRecord = item.getRecord();
                grpcRecord
                        .getTransferList()
                        .getAccountAmountsList()
                        .forEach(
                                aa -> {
                                    final var accountNum = aa.getAccountID().getAccountNum();
                                    final var amount = aa.getAmount();
                                    expectedBalances.merge(accountNum, amount, Long::sum);
                                });
            }
        }

        return expectedBalances;
    }
}
