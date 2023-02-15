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

import static com.hedera.services.bdd.junit.TestBase.concurrentExecutionOf;

import com.hedera.services.bdd.junit.utils.AccountClassifier;
import com.hedera.services.bdd.suites.records.BalanceValidation;
import com.hedera.services.stream.proto.RecordStreamItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * This validator "reconciles" the hbar balances of all accounts and contract between the record
 * stream and the network state, comparing two sources of truth at the end of the CI test run:
 *
 * <ol>
 *   <li>The balances implied by the {@code TransferList} adjustments in the record stream.
 *   <li>The balances returned by {@code getAccountBalance} and {@code getContractInfo} queries.
 * </ol>
 *
 * <p>It uses the {@link BalanceValidation} suite to perform the queries.
 */
public class BalanceReconciliationValidator implements RecordStreamValidator {
    private final Map<Long, Long> expectedBalances = new HashMap<>();

    private final AccountClassifier accountClassifier = new AccountClassifier();

    @Override
    @SuppressWarnings("java:S106")
    public void validateRecordsAndSidecars(final List<RecordWithSidecars> recordsWithSidecars) {
        getExpectedBalanceFrom(recordsWithSidecars);
        System.out.println("Expected balances: " + expectedBalances);

        final var validationSpecs = TestBase.extractContextualizedSpecsFrom(
                List.of(() -> new BalanceValidation(expectedBalances, accountClassifier)),
                TestBase::contextualizedSpecsFromConcurrent);
        concurrentExecutionOf(validationSpecs);
    }

    private void getExpectedBalanceFrom(final List<RecordWithSidecars> recordsWithSidecars) {
        for (final var recordWithSidecars : recordsWithSidecars) {
            final var items = recordWithSidecars.recordFile().getRecordStreamItemsList();
            for (final var item : items) {
                accountClassifier.incorporate(item);
                final var grpcRecord = item.getRecord();
                grpcRecord.getTransferList().getAccountAmountsList().forEach(aa -> {
                    final var accountNum = aa.getAccountID().getAccountNum();
                    final var amount = aa.getAmount();
                    expectedBalances.merge(accountNum, amount, Long::sum);
                });
            }
        }
    }

    public static Stream<RecordStreamItem> streamOfItemsFrom(final List<RecordWithSidecars> recordsWithSidecars) {
        return recordsWithSidecars.stream()
                .flatMap(recordWithSidecars -> recordWithSidecars.recordFile().getRecordStreamItemsList().stream());
    }
}
