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

import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION;
import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ifNotEmbeddedTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECEIPT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.RepeatableHapiTest;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

/**
 * ! WARNING - Requires a RecordCache TTL of 3s to pass !
 *
 * <p>Even with a 3s TTL, a number of these tests fail. FUTURE: revisit
 */
@Tag(CRYPTO)
public class TxnRecordRegression {
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
                        // Running with embedded mode the previous transaction will often already be handled
                        // and have a record available, so this is only interesting with a live network
                        ifNotEmbeddedTest(getTxnRecord("success").hasAnswerOnlyPrecheck(RECORD_NOT_FOUND)));
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

    @HapiTest
    final Stream<DynamicTest> getReceiptReturnsInvalidForUnspecifiedTxnId() {
        return hapiTest(getReceipt("").useDefaultTxnId().hasAnswerOnlyPrecheck(INVALID_TRANSACTION_ID));
    }

    @RepeatableHapiTest(NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION)
    final Stream<DynamicTest> receiptUnavailableAfterCacheTtl() {
        return hapiTest(
                // Every transaction in a repeatable spec reaches consensus one second apart,
                // and it uses a valid start offset of one second; hence this will reach
                // consensus at some time T with a valid start of T-1, and be purged after
                // any transaction that reaches consensus at T+180 or later
                cryptoCreate("misc").via("success").balance(1_000L),
                // Sleep until T+179
                sleepFor(179_000L),
                // Run a transaction that will reach consensus at T+180 to purge receipts
                cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                getReceipt("success").hasAnswerOnlyPrecheck(RECEIPT_NOT_FOUND));
    }

    @HapiTest
    final Stream<DynamicTest> receiptUnknownBeforeConsensus() {
        return defaultHapiSpec("ReceiptUnknownBeforeConsensus")
                .given()
                .when()
                .then(
                        cryptoCreate("misc").via("success").balance(1_000L).deferStatusResolution(),
                        getReceipt("success").hasPriorityStatus(UNKNOWN));
    }

    @HapiTest
    final Stream<DynamicTest> receiptAvailableWithinCacheTtl() {
        return defaultHapiSpec("ReceiptAvailableWithinCacheTtl")
                .given(cryptoCreate("misc").via("success").balance(1_000L))
                .when()
                .then(getReceipt("success").hasPriorityStatus(SUCCESS));
    }

    @HapiTest
    final Stream<DynamicTest> receiptUnavailableIfRejectedInPrecheck() {
        return defaultHapiSpec("ReceiptUnavailableIfRejectedInPrecheck")
                .given(cryptoCreate("misc").balance(1_000L))
                .when(cryptoCreate("nope")
                        .payingWith("misc")
                        .hasPrecheck(INSUFFICIENT_PAYER_BALANCE)
                        .via("failingTxn"))
                .then(getReceipt("failingTxn").hasAnswerOnlyPrecheck(RECEIPT_NOT_FOUND));
    }

    @HapiTest
    final Stream<DynamicTest> receiptNotFoundOnUnknownTransactionID() {
        return defaultHapiSpec("receiptNotFoundOnUnknownTransactionID")
                .given()
                .when()
                .then(withOpContext((spec, ctxLog) -> {
                    allRunFor(
                            spec,
                            getReceipt(TransactionID.newBuilder()
                                            .setAccountID(AccountID.newBuilder()
                                                    .setAccountNum(Long.MAX_VALUE)
                                                    .build())
                                            .build())
                                    .hasAnswerOnlyPrecheck(INVALID_TRANSACTION_ID),
                            getReceipt(TransactionID.newBuilder()
                                            .setTransactionValidStart(Timestamp.newBuilder()
                                                    .setSeconds(Long.MAX_VALUE)
                                                    .setNanos(Integer.MAX_VALUE)
                                                    .build())
                                            .build())
                                    .hasAnswerOnlyPrecheck(INVALID_TRANSACTION_ID),
                            getReceipt(TransactionID.newBuilder()
                                            .setAccountID(AccountID.newBuilder()
                                                    .setAccountNum(Long.MAX_VALUE)
                                                    .build())
                                            .setTransactionValidStart(Timestamp.newBuilder()
                                                    .setSeconds(Long.MAX_VALUE)
                                                    .setNanos(Integer.MAX_VALUE)
                                                    .build())
                                            .build())
                                    .hasAnswerOnlyPrecheck(RECEIPT_NOT_FOUND));
                }));
    }
}
