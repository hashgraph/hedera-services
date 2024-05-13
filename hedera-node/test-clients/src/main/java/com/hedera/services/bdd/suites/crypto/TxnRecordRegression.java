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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

/**
 * ! WARNING - Requires a RecordCache TTL of 3s to pass !
 *
 * <p>Even with a 3s TTL, a number of these tests fail. FUTURE: revisit
 * */
@HapiTestSuite
@Tag(CRYPTO)
public class TxnRecordRegression extends HapiSuite {
    static final Logger log = LogManager.getLogger(TxnRecordRegression.class);

    public static void main(final String... args) {
        new TxnRecordRegression().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(
            returnsInvalidForUnspecifiedTxnId(),
            recordNotFoundIfNotInPayerState(),
            recordUnavailableIfRejectedInPrecheck(),
            recordUnavailableBeforeConsensus(),
            recordsStillQueryableWithDeletedPayerId());
    }

    // FUTURE: revisit this test, which isn't passing in modular or mono code (even with a 3 second TTL)
    @HapiTest
    final Stream<DynamicTest> recordsStillQueryableWithDeletedPayerId() {
        return defaultHapiSpec("DeletedAccountRecordsUnavailableAfterTtl")
                .given(
                        cryptoCreate("toBeDeletedPayer"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .payingWith("toBeDeletedPayer")
                                .via("recordTxn"))
                .when(cryptoDelete("toBeDeletedPayer"))
                .then(getTxnRecord("recordTxn"));
    }

    @HapiTest
    final Stream<DynamicTest> returnsInvalidForUnspecifiedTxnId() {
        return defaultHapiSpec("ReturnsInvalidForUnspecifiedTxnId")
                .given()
                .when()
                .then(getTxnRecord("").useDefaultTxnId().hasCostAnswerPrecheck(INVALID_ACCOUNT_ID));
    }

    @HapiTest
    final Stream<DynamicTest> recordNotFoundIfNotInPayerState() {
        return defaultHapiSpec("RecordNotFoundIfNotInPayerState")
                .given(
                        cryptoCreate("misc").via("success"),
                        usableTxnIdNamed("rightAccountWrongId").payerId("misc"))
                .when()
                .then(getTxnRecord("rightAccountWrongId").hasAnswerOnlyPrecheck(RECORD_NOT_FOUND));
    }

    @HapiTest
    final Stream<DynamicTest> recordUnavailableBeforeConsensus() {
        return defaultHapiSpec("RecordUnavailableBeforeConsensus")
                .given()
                .when()
                .then(
                        cryptoCreate("misc").via("success").balance(1_000L).deferStatusResolution(),
                        getTxnRecord("success").hasAnswerOnlyPrecheck(RECORD_NOT_FOUND));
    }

    @HapiTest
    final Stream<DynamicTest> recordUnavailableIfRejectedInPrecheck() {
        return defaultHapiSpec("RecordUnavailableIfRejectedInPrecheck")
                .given(
                        cryptoCreate("misc").balance(1000L),
                        usableTxnIdNamed("failingTxn").payerId("misc"))
                .when(cryptoCreate("nope")
                        .payingWith("misc")
                        .hasPrecheck(INSUFFICIENT_PAYER_BALANCE)
                        .txnId("failingTxn"))
                .then(getTxnRecord("failingTxn").hasAnswerOnlyPrecheck(RECORD_NOT_FOUND));
    }
}
