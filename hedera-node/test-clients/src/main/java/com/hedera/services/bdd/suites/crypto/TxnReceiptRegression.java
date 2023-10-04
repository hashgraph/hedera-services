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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECEIPT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.meta.HapiGetReceipt;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class TxnReceiptRegression extends HapiSuite {
    static final Logger log = LogManager.getLogger(TxnReceiptRegression.class);

    public static void main(final String... args) {
        new TxnReceiptRegression().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            returnsInvalidForUnspecifiedTxnId(),
            returnsNotSupportedForMissingOp(),
            receiptAvailableWithinCacheTtl(),
            //						receiptUnavailableAfterCacheTtl(),
            receiptUnavailableIfRejectedInPrecheck(),
            receiptNotFoundOnUnknownTransactionID(),
            receiptUnknownBeforeConsensus(),
        });
    }

    @HapiTest
    private HapiSpec returnsInvalidForUnspecifiedTxnId() {
        return defaultHapiSpec("ReturnsInvalidForUnspecifiedTxnId")
                .given()
                .when()
                .then(getReceipt("").useDefaultTxnId().hasAnswerOnlyPrecheck(INVALID_TRANSACTION_ID));
    }

    private HapiSpec returnsNotSupportedForMissingOp() {
        return defaultHapiSpec("ReturnsNotSupportedForMissingOp")
                .given(cryptoCreate("misc").via("success").balance(1_000L))
                .when()
                .then(getReceipt("success").forgetOp().hasAnswerOnlyPrecheck(NOT_SUPPORTED));
    }

    private HapiSpec receiptUnavailableAfterCacheTtl() {
        return defaultHapiSpec("ReceiptUnavailableAfterCacheTtl")
                .given(cryptoCreate("misc").via("success").balance(1_000L))
                .when(sleepFor(200_000L))
                .then(getReceipt("success").hasAnswerOnlyPrecheck(RECEIPT_NOT_FOUND));
    }

    @HapiTest
    private HapiSpec receiptUnknownBeforeConsensus() {
        return defaultHapiSpec("ReceiptUnknownBeforeConsensus")
                .given()
                .when()
                .then(
                        cryptoCreate("misc").via("success").balance(1_000L).deferStatusResolution(),
                        getReceipt("success").hasPriorityStatus(UNKNOWN));
    }

    @HapiTest
    private HapiSpec receiptAvailableWithinCacheTtl() {
        return defaultHapiSpec("ReceiptAvailableWithinCacheTtl")
                .given(cryptoCreate("misc").via("success").balance(1_000L))
                .when()
                .then(getReceipt("success").hasPriorityStatus(SUCCESS));
    }

    private HapiSpec receiptUnavailableIfRejectedInPrecheck() {
        return defaultHapiSpec("ReceiptUnavailableIfRejectedInPrecheck")
                .given(usableTxnIdNamed("failingTxn"), cryptoCreate("misc").balance(1_000L))
                .when(cryptoCreate("nope")
                        .payingWith("misc")
                        .hasPrecheck(INSUFFICIENT_PAYER_BALANCE)
                        .txnId("failingTxn"))
                .then(getReceipt("failingTxn").hasAnswerOnlyPrecheck(RECEIPT_NOT_FOUND));
    }

    @HapiTest
    private HapiSpec receiptNotFoundOnUnknownTransactionID() {
        return defaultHapiSpec("receiptNotFoundOnUnknownTransactionID")
                .given()
                .when()
                .then(withOpContext((spec, ctxLog) -> {
                    final HapiGetReceipt op =
                            getReceipt(spec.txns().defaultTransactionID()).hasAnswerOnlyPrecheck(RECEIPT_NOT_FOUND);
                    CustomSpecAssert.allRunFor(spec, op);
                }));
    }
}
