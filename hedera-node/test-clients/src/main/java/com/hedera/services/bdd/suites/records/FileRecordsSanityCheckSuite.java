/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.records;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.takeBalanceSnapshots;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateRecordTransactionFees;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateTransferListForBalances;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withStrictCostAnswerValidation;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.EXCHANGE_RATE_CONTROL;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.NODE;
import static com.hedera.services.bdd.suites.HapiSuite.NODE_REWARD;
import static com.hedera.services.bdd.suites.HapiSuite.STAKING_REWARD;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class FileRecordsSanityCheckSuite {
    @HapiTest
    final Stream<DynamicTest> fileAppendRecordSanityChecks() {
        return defaultHapiSpec("FileAppendRecordSanityChecks")
                .given(flattened(
                        fileCreate("test"),
                        takeBalanceSnapshots(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER)))
                .when(fileAppend("test").via("txn").fee(95_000_000L))
                .then(
                        validateTransferListForBalances(
                                "txn", List.of(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER)),
                        validateRecordTransactionFees("txn"));
    }

    @HapiTest
    final Stream<DynamicTest> fileCreateRecordSanityChecks() {
        return defaultHapiSpec("FileCreateRecordSanityChecks")
                .given(takeBalanceSnapshots(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER))
                .when(fileCreate("test").via("txn"))
                .then(
                        validateTransferListForBalances(
                                "txn", List.of(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER)),
                        validateRecordTransactionFees("txn"));
    }

    @HapiTest
    final Stream<DynamicTest> fileDeleteRecordSanityChecks() {
        return defaultHapiSpec("FileDeleteRecordSanityChecks")
                .given(flattened(
                        fileCreate("test"),
                        takeBalanceSnapshots(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER)))
                .when(fileDelete("test").via("txn"))
                .then(
                        validateTransferListForBalances(
                                "txn", List.of(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER)),
                        validateRecordTransactionFees("txn"));
    }

    @HapiTest
    final Stream<DynamicTest> fileUpdateRecordSanityChecks() {
        return defaultHapiSpec("FileUpdateRecordSanityChecks")
                .given(flattened(
                        fileCreate("test"),
                        takeBalanceSnapshots(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER)))
                .when(fileUpdate("test")
                        .contents("Here are some new contents!")
                        .via("txn")
                        .fee(95_000_000L))
                .then(
                        withStrictCostAnswerValidation(() -> getFileInfo("test").payingWith(EXCHANGE_RATE_CONTROL)),
                        validateTransferListForBalances(
                                "txn", List.of(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER)),
                        validateRecordTransactionFees("txn"));
    }
}
