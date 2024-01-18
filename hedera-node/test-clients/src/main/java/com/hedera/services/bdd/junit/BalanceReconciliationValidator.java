/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.TargetNetworkType;
import com.hedera.services.bdd.suites.records.BalanceValidation;
import com.hedera.services.stream.proto.RecordStreamItem;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.platform.commons.support.ReflectionSupport;

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

    @Override
    public void validateRecordsAndSidecarsHapi(
            final HapiTestEnv env, final List<RecordWithSidecars> recordsWithSidecars)
            throws InvocationTargetException, IllegalAccessException {
        getExpectedBalanceFrom(recordsWithSidecars);
        System.out.println("Expected balances: " + expectedBalances);

        // First, create an instance of the HapiSuite class (the class that owns this method).
        final var suite = new BalanceValidation(expectedBalances, accountClassifier);
        // Second, get the method
        final var testMethod = ReflectionSupport.findMethod(BalanceValidation.class, "validateBalances")
                .get();
        // Third, call the method to get the HapiSpec
        testMethod.setAccessible(true);
        final var spec = (HapiSpec) testMethod.invoke(suite);
        spec.setTargetNetworkType(TargetNetworkType.HAPI_TEST_NETWORK);
        final var result = suite.runSpecSync(spec, env.getNodes());
        // Fourth, report the result. YAY!!
        if (result == HapiSuite.FinalOutcome.SUITE_FAILED) {
            throw new AssertionError();
        }
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

            for (final var entry : expectedBalances.entrySet()) {
                if (entry.getValue() < 0) {
                    throw new IllegalStateException(
                            "Negative balance for account " + entry.getKey() + " with value " + entry.getValue());
                }
            }
        }
    }

    public static Stream<RecordStreamItem> streamOfItemsFrom(final List<RecordWithSidecars> recordsWithSidecars) {
        return recordsWithSidecars.stream()
                .flatMap(recordWithSidecars -> recordWithSidecars.recordFile().getRecordStreamItemsList().stream());
    }
}
