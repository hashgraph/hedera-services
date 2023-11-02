/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECEIPT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/* ! WARNING - Requires a RecordCache TTL of 3s to pass ! */
@HapiTestSuite
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
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            returnsInvalidForUnspecifiedTxnId(),
            recordNotFoundIfNotInPayerState(),
            recordUnavailableIfRejectedInPrecheck(),
            recordUnavailableBeforeConsensus(),
            recordAvailableInPayerState(),
            deletedAccountRecordsUnavailableAfterTtl(),
        });
    }

    private HapiSpec recordAvailableInPayerState() {
        return defaultHapiSpec("RecordAvailableInPayerState")
                .given(
                        cryptoCreate("stingyPayer").sendThreshold(1L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .payingWith("stingyPayer")
                                .via("recordTxn"))
                .when(sleepFor(5_000L))
                .then(
                        getReceipt("recordTxn").hasAnswerOnlyPrecheck(RECEIPT_NOT_FOUND),
                        getTxnRecord("recordTxn").hasPriority(recordWith().status(SUCCESS)));
    }

    private HapiSpec deletedAccountRecordsUnavailableAfterTtl() {
        return defaultHapiSpec("DeletedAccountRecordsUnavailableAfterTtl")
                .given(
                        cryptoCreate("lowThreshPayer").sendThreshold(1L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .payingWith("lowThreshPayer")
                                .via("recordTxn"),
                        cryptoDelete("lowThreshPayer"),
                        getTxnRecord("recordTxn"))
                .when(sleepFor(5_000L))
                .then(getTxnRecord("recordTxn").hasCostAnswerPrecheck(ACCOUNT_DELETED));
    }

    @HapiTest
    private HapiSpec returnsInvalidForUnspecifiedTxnId() {
        return defaultHapiSpec("ReturnsInvalidForUnspecifiedTxnId")
                .given()
                .when()
                .then(getTxnRecord("").useDefaultTxnId().hasCostAnswerPrecheck(INVALID_ACCOUNT_ID));
    }

    @HapiTest
    private HapiSpec recordNotFoundIfNotInPayerState() {
        return defaultHapiSpec("RecordNotFoundIfNotInPayerState")
                .given(
                        cryptoCreate("misc").via("success"),
                        usableTxnIdNamed("rightAccountWrongId").payerId("misc"))
                .when()
                .then(getTxnRecord("rightAccountWrongId").hasAnswerOnlyPrecheck(RECORD_NOT_FOUND));
    }

    @HapiTest
    private HapiSpec recordUnavailableBeforeConsensus() {
        return defaultHapiSpec("RecordUnavailableBeforeConsensus")
                .given()
                .when()
                .then(
                        cryptoCreate("misc").via("success").balance(1_000L).deferStatusResolution(),
                        getTxnRecord("success").hasAnswerOnlyPrecheck(RECORD_NOT_FOUND));
    }

    private HapiSpec recordUnavailableIfRejectedInPrecheck() {
        return defaultHapiSpec("RecordUnavailableIfRejectedInPrecheck")
                .given(usableTxnIdNamed("failingTxn"), cryptoCreate("misc").balance(1_000L))
                .when(cryptoCreate("nope")
                        .payingWith("misc")
                        .hasPrecheck(INSUFFICIENT_PAYER_BALANCE)
                        .txnId("failingTxn"))
                .then(getTxnRecord("failingTxn").hasCostAnswerPrecheck(RECORD_NOT_FOUND));
    }
}
