/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.AssertUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoGetRecordsRegression extends HapiSuite {
    static final Logger log = LogManager.getLogger(CryptoGetRecordsRegression.class);

    public static void main(String... args) {
        new CryptoGetRecordsRegression().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    //						failsForDeletedAccount(),
                    //						failsForMissingAccount(),
                    //						failsForMissingPayment(),
                    //						failsForInsufficientPayment(),
                    //						failsForMalformedPayment(),
                    //						failsForUnfundablePayment(),
                    //						succeedsNormally(),
                    getAccountRecords_testForDuplicates()
                });
    }

    private HapiSpec succeedsNormally() {
        String memo = "Dim galleries, dusky corridors got past...";

        return defaultHapiSpec("SucceedsNormally")
                .given(cryptoCreate("misc"), cryptoCreate("lowThreshPayer").sendThreshold(1L))
                .when(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, "misc", 1))
                                .payingWith("lowThreshPayer")
                                .memo(memo)
                                .via("txn"))
                .then(
                        getAccountRecords("lowThreshPayer")
                                .has(
                                        AssertUtils.inOrder(
                                                recordWith()
                                                        .txnId("txn")
                                                        .memo(memo)
                                                        .transfers(
                                                                including(
                                                                        tinyBarsFromTo(
                                                                                GENESIS, "misc",
                                                                                1L)))
                                                        .status(SUCCESS)
                                                        .payer("lowThreshPayer"))));
    }

    private HapiSpec failsForMissingAccount() {
        return defaultHapiSpec("FailsForMissingAccount")
                .given()
                .when()
                .then(
                        getAccountRecords("1.2.3").hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
                        getAccountRecords("1.2.3")
                                .nodePayment(123L)
                                .hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID));
    }

    private HapiSpec failsForMalformedPayment() {
        return defaultHapiSpec("FailsForMalformedPayment")
                .given(newKeyNamed("wrong").shape(SIMPLE))
                .when()
                .then(
                        getAccountRecords(GENESIS)
                                .signedBy("wrong")
                                .hasAnswerOnlyPrecheck(INVALID_SIGNATURE));
    }

    private HapiSpec failsForUnfundablePayment() {
        long everything = 1_234L;
        return defaultHapiSpec("FailsForUnfundablePayment")
                .given(cryptoCreate("brokePayer").balance(everything))
                .when()
                .then(
                        getAccountRecords(GENESIS)
                                .payingWith("brokePayer")
                                .nodePayment(everything)
                                .hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE));
    }

    private HapiSpec failsForInsufficientPayment() {
        return defaultHapiSpec("FailsForInsufficientPayment")
                .given()
                .when()
                .then(
                        getAccountRecords(GENESIS)
                                .nodePayment(1L)
                                .hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE));
    }

    private HapiSpec failsForMissingPayment() {
        return defaultHapiSpec("FailsForMissingPayment")
                .given()
                .when()
                .then(
                        getAccountRecords(GENESIS)
                                .useEmptyTxnAsAnswerPayment()
                                .hasAnswerOnlyPrecheck(NOT_SUPPORTED));
    }

    private HapiSpec failsForDeletedAccount() {
        return defaultHapiSpec("FailsForDeletedAccount")
                .given(cryptoCreate("toBeDeleted"))
                .when(cryptoDelete("toBeDeleted").transfer(GENESIS))
                .then(
                        getAccountRecords("toBeDeleted").hasCostAnswerPrecheck(ACCOUNT_DELETED),
                        getAccountRecords("toBeDeleted")
                                .nodePayment(123L)
                                .hasAnswerOnlyPrecheck(ACCOUNT_DELETED));
    }

    private HapiSpec getAccountRecords_testForDuplicates() {
        return defaultHapiSpec("testForDuplicateAccountRecords")
                .given(
                        cryptoCreate("account1").balance(5000000000000L).sendThreshold(1L),
                        cryptoCreate("account2").balance(5000000000000L).sendThreshold(1L))
                .when(
                        cryptoTransfer(tinyBarsFromTo("account1", "account2", 10L))
                                .payingWith("account1")
                                .via("thresholdTxn"))
                .then(getAccountRecords("account1").logged());
    }
}
