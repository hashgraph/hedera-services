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

package com.hedera.services.bdd.junit;

import static com.hedera.services.bdd.junit.TestBase.concurrentExecutionOf;

import com.hedera.services.bdd.junit.utils.AccountClassifier;
import com.hedera.services.bdd.junit.validators.AccountNumTokenNum;
import com.hedera.services.bdd.suites.records.TokenBalanceValidation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This validator "reconciles" (compares) the HTS token balances of all accounts between the record
 * stream and the network state, comparing two sources of truth at the end of the CI test run:
 *
 * <ol>
 *   <li>The balances implied by the {@code TransferList} adjustments in the record stream.
 *   <li>The balances returned by {@code hasTokenBalance} queries.
 * </ol>
 *
 * <p>It uses the {@link com.hedera.services.bdd.suites.records.TokenBalanceValidation} suite to perform the queries.
 */
public class TokenReconciliationValidator implements RecordStreamValidator {
    private static final Logger log = LogManager.getLogger(TokenReconciliationValidator.class);

    private final Map<AccountNumTokenNum, Long> expectedTokenBalances = new HashMap<>();

    private final AccountClassifier accountClassifier = new AccountClassifier();

    @Override
    public void validateRecordsAndSidecars(final List<RecordWithSidecars> recordsWithSidecars) {
        getExpectedBalanceFrom(recordsWithSidecars);
        log.info("Expected token balances: {}", expectedTokenBalances);

        final var validationSpecs = TestBase.extractContextualizedSpecsFrom(
                List.of(() -> new TokenBalanceValidation(expectedTokenBalances, accountClassifier)),
                TestBase::contextualizedSpecsFromConcurrent);
        concurrentExecutionOf(validationSpecs);
    }

    private void getExpectedBalanceFrom(final List<RecordWithSidecars> recordsWithSidecars) {
        for (final var recordWithSidecars : recordsWithSidecars) {
            final var items = recordWithSidecars.recordFile().getRecordStreamItemsList();
            for (final var item : items) {
                accountClassifier.incorporate(item);
                final var grpcRecord = item.getRecord();
                grpcRecord.getTokenTransferListsList().forEach(tokenTransferList -> {
                    Long tokenNum = tokenTransferList.getToken().getTokenNum();
                    tokenTransferList.getTransfersList().forEach(tokenTransfers -> {
                        final long accountNum = tokenTransfers.getAccountID().getAccountNum();
                        final long amount = tokenTransfers.getAmount();
                        expectedTokenBalances.merge(new AccountNumTokenNum(accountNum, tokenNum), amount, Long::sum);
                    });
                });
            }
        }
    }
}
