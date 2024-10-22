/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.issues;

import static com.hedera.services.bdd.junit.ContextRequirement.NO_CONCURRENT_CREATIONS;
import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.approxChangeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sendModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedQueryIds;
import static com.hedera.services.bdd.suites.HapiSuite.CIVILIAN_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(TOKEN)
public class IssueRegressionTests {
    private static final String TRANSFER = "transfer";
    private static final String PAYER = "payer";
    private static final String SNAPSHOT = "snapshot";
    private static final String DELETE_TXN = "deleteTxn";
    private static final String RECEIVER = "receiver";

    @HapiTest
    final Stream<DynamicTest> allowsCryptoCreatePayerToHaveLessThanTwiceFee() {
        return hapiTest(
                cryptoCreate(CIVILIAN_PAYER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate("payer")
                        .payingWith(CIVILIAN_PAYER)
                        .via("referenceTxn")
                        .balance(0L),
                withOpContext((spec, ctxLog) -> {
                    HapiGetTxnRecord subOp = getTxnRecord("referenceTxn");
                    allRunFor(spec, subOp);
                    TransactionRecord record = subOp.getResponseRecord();
                    long fee = record.getTransactionFee();
                    spec.registry().saveAmount("balance", fee * 2 - 1);
                }),
                cryptoTransfer(
                        tinyBarsFromTo(GENESIS, "payer", spec -> spec.registry().getAmount("balance"))),
                cryptoCreate("irrelevant").balance(0L).payingWith("payer"));
    }

    @LeakyHapiTest(requirement = NO_CONCURRENT_CREATIONS)
    final Stream<DynamicTest> createDeleteInSameRoundWorks() {
        final var key = "tbdKey";
        AtomicReference<String> nextFileId = new AtomicReference<>();
        return defaultHapiSpec("CreateDeleteInSameRoundWorks")
                .given(
                        newKeyNamed(key).type(KeyFactory.KeyType.LIST),
                        fileCreate("marker").via("markerTxn"))
                .when(withOpContext((spec, opLog) -> {
                    var lookup = getTxnRecord("markerTxn");
                    allRunFor(spec, lookup);
                    var markerFid = lookup.getResponseRecord().getReceipt().getFileID();
                    var nextFid = markerFid.toBuilder()
                            .setFileNum(markerFid.getFileNum() + 1)
                            .build();
                    nextFileId.set(HapiPropertySource.asFileString(nextFid));
                    opLog.info("Next file will be {}", nextFileId.get());
                }))
                .then(
                        fileCreate("tbd").key(key).deferStatusResolution(),
                        fileDelete(nextFileId::get).signedBy(GENESIS, key),
                        getFileInfo(nextFileId::get).hasDeleted(true));
    }

    @HapiTest
    final Stream<DynamicTest> recordStorageFeeIncreasesWithNumTransfers() {
        return defaultHapiSpec("RecordStorageFeeIncreasesWithNumTransfers")
                .given(
                        cryptoCreate("civilian").balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate("A"),
                        cryptoCreate("B"),
                        cryptoCreate("C"),
                        cryptoCreate("D"),
                        cryptoTransfer(tinyBarsFromTo("A", "B", 1L))
                                .payingWith("civilian")
                                .via("txn1"),
                        cryptoTransfer(tinyBarsFromTo("A", "B", 1L), tinyBarsFromTo("C", "D", 1L))
                                .payingWith("civilian")
                                .via("txn2"))
                .when(UtilVerbs.recordFeeAmount("txn1", "feeForOne"), UtilVerbs.recordFeeAmount("txn2", "feeForTwo"))
                .then(UtilVerbs.assertionsHold((spec, assertLog) -> {
                    long feeForOne = spec.registry().getAmount("feeForOne");
                    long feeForTwo = spec.registry().getAmount("feeForTwo");
                    assertLog.info("[Record storage] fee for one transfer : {}", feeForOne);
                    assertLog.info("[Record storage] fee for two transfers: {}", feeForTwo);
                    Assertions.assertEquals(-1, Long.compare(feeForOne, feeForTwo));
                }));
    }

    @HapiTest
    final Stream<DynamicTest> queryPaymentTxnMustHavePayerBalanceForBothTransferFeeAndNodePayment() {
        final long BALANCE = 1_000_000L;

        return HapiSpec.defaultHapiSpec("QueryPaymentTxnMustHavePayerBalanceForBothTransferFeeAndNodePayment")
                .given(cryptoCreate("payer").balance(BALANCE))
                .when()
                .then(getAccountInfo("payer")
                        .nodePayment(BALANCE)
                        .payingWith("payer")
                        .hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE));
    }

    @HapiTest
    final Stream<DynamicTest> cryptoTransferListShowsOnlyFeesAfterIAB() {
        final long PAYER_BALANCE = 1_000_000L;

        return defaultHapiSpec("CryptoTransferListShowsOnlyFeesAfterIAB")
                .given(cryptoCreate("payer").balance(PAYER_BALANCE))
                .when()
                .then(cryptoTransfer(tinyBarsFromTo("payer", GENESIS, PAYER_BALANCE))
                        .payingWith("payer")
                        .via("txn")
                        .hasPrecheck(INSUFFICIENT_PAYER_BALANCE));
    }

    @HapiTest
    final Stream<DynamicTest> duplicatedTxnsSameTypeDetected() {
        long initialBalance = 10_000L;

        return defaultHapiSpec("duplicatedTxnsSameTypeDetected")
                .given(
                        cryptoCreate("acct1").balance(initialBalance).logged().via("txnId1"),
                        sleepFor(2000),
                        cryptoCreate("acctWithDuplicateTxnId")
                                .balance(initialBalance)
                                .logged()
                                .txnId("txnId1")
                                .hasPrecheck(DUPLICATE_TRANSACTION))
                .when()
                .then(getTxnRecord("txnId1").logged());
    }

    @HapiTest
    final Stream<DynamicTest> duplicatedTxnsDifferentTypesDetected() {
        return defaultHapiSpec("duplicatedTxnsDifferentTypesDetected")
                .given(
                        cryptoCreate("acct2").via("txnId2"),
                        newKeyNamed("key1"),
                        createTopic("topic2").submitKeyName("key1"))
                .when(submitMessageTo("topic2")
                        .message("Hello world")
                        .payingWith("acct2")
                        .txnId("txnId2")
                        .hasPrecheck(DUPLICATE_TRANSACTION))
                .then(getTxnRecord("txnId2").logged());
    }

    @HapiTest
    final Stream<DynamicTest> duplicatedTxnsSameTypeDifferentNodesDetected() {
        return hapiTest(
                cryptoCreate("acct3").setNode("0.0.3").via("txnId1"),
                sleepFor(2000),
                cryptoCreate("acctWithDuplicateTxnId")
                        .setNode("0.0.5")
                        .txnId("txnId1")
                        .hasPrecheck(DUPLICATE_TRANSACTION),
                uncheckedSubmit(cryptoCreate("acctWithDuplicateTxnId")
                                .setNode("0.0.5")
                                .txnId("txnId1"))
                        .setNode("0.0.5"),
                sleepFor(2000),
                getTxnRecord("txnId1")
                        .andAnyDuplicates()
                        .assertingNothingAboutHashes()
                        .hasPriority(recordWith().status(SUCCESS))
                        .hasDuplicates(inOrder(recordWith().status(DUPLICATE_TRANSACTION))));
    }

    @HapiTest
    final Stream<DynamicTest> duplicatedTxnsDifferentTypesDifferentNodesDetected() {
        return defaultHapiSpec("duplicatedTxnsDifferentTypesDifferentNodesDetected")
                .given(
                        cryptoCreate("acct4").via("txnId4").setNode("0.0.3"),
                        newKeyNamed("key2"),
                        createTopic("topic2").setNode("0.0.5").submitKeyName("key2"))
                .when(submitMessageTo("topic2")
                        .message("Hello world")
                        .payingWith("acct4")
                        .txnId("txnId4")
                        .hasPrecheck(DUPLICATE_TRANSACTION))
                .then(getTxnRecord("txnId4").logged());
    }

    @HapiTest
    final Stream<DynamicTest> keepsRecordOfPayerIBE() {
        final var payer = "payer";
        return defaultHapiSpec("KeepsRecordOfPayerIBE")
                .given(
                        cryptoCreate(CIVILIAN_PAYER),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .payingWith(CIVILIAN_PAYER)
                                .via("referenceTxn"),
                        UtilVerbs.withOpContext((spec, ctxLog) -> {
                            HapiGetTxnRecord subOp = getTxnRecord("referenceTxn");
                            allRunFor(spec, subOp);
                            TransactionRecord record = subOp.getResponseRecord();
                            long fee = record.getTransactionFee();
                            spec.registry().saveAmount("fee", fee);
                            spec.registry().saveAmount("balance", fee * 2);
                        }))
                .when(cryptoCreate(payer).balance(spec -> spec.registry().getAmount("balance")))
                .then(
                        UtilVerbs.inParallel(
                                cryptoTransfer(tinyBarsFromTo(payer, FUNDING, spec -> spec.registry()
                                                .getAmount("fee")))
                                        .payingWith(payer)
                                        .via("txnA")
                                        .hasAnyKnownStatus(),
                                cryptoTransfer(tinyBarsFromTo(payer, FUNDING, spec -> spec.registry()
                                                .getAmount("fee")))
                                        .payingWith(payer)
                                        .via("txnB")
                                        .hasAnyKnownStatus()),
                        getTxnRecord("txnA").logged(),
                        getTxnRecord("txnB").logged());
    }

    @HapiTest
    final Stream<DynamicTest> tbdCanPayForItsOwnDeletion() {
        return defaultHapiSpec("TbdCanPayForItsOwnDeletion")
                .given(cryptoCreate("tbd"), cryptoCreate(TRANSFER))
                .when()
                .then(
                        cryptoDelete("tbd")
                                .via("selfFinanced")
                                .payingWith("tbd")
                                .transfer(TRANSFER),
                        getTxnRecord("selfFinanced").logged());
    }

    @HapiTest
    final Stream<DynamicTest> transferAccountCannotBeDeleted() {
        return defaultHapiSpec("TransferAccountCannotBeDeleted")
                .given(cryptoCreate(PAYER), cryptoCreate(TRANSFER), cryptoCreate("tbd"))
                .when(cryptoDelete(TRANSFER))
                .then(
                        balanceSnapshot(SNAPSHOT, PAYER),
                        cryptoDelete("tbd")
                                .via(DELETE_TXN)
                                .payingWith(PAYER)
                                .transfer(TRANSFER)
                                .hasKnownStatus(ACCOUNT_DELETED),
                        getTxnRecord(DELETE_TXN).logged(),
                        getAccountBalance(PAYER).hasTinyBars(approxChangeFromSnapshot(SNAPSHOT, -9384399, 10000)));
    }

    @HapiTest
    final Stream<DynamicTest> transferAccountCannotBeDeletedForContractTarget() {
        return defaultHapiSpec("TransferAccountCannotBeDeletedForContractTarget")
                .given(
                        uploadInitCode("CreateTrivial"),
                        uploadInitCode("PayReceivable"),
                        cryptoCreate(TRANSFER),
                        contractCreate("CreateTrivial"),
                        contractCreate("PayReceivable"))
                .when(cryptoDelete(TRANSFER), contractDelete("PayReceivable"))
                .then(
                        balanceSnapshot(SNAPSHOT, GENESIS),
                        contractDelete("CreateTrivial")
                                .via(DELETE_TXN)
                                .transferAccount(TRANSFER)
                                .hasKnownStatus(OBTAINER_DOES_NOT_EXIST),
                        contractDelete("CreateTrivial")
                                .via(DELETE_TXN)
                                .transferContract("PayReceivable")
                                .hasKnownStatus(INVALID_CONTRACT_ID));
    }

    @HapiTest
    final Stream<DynamicTest> multiKeyNonPayerEntityVerifiedAsync() {
        KeyShape LARGE_THRESH_SHAPE = KeyShape.threshOf(1, 10);
        SigControl firstOnly = LARGE_THRESH_SHAPE.signedWith(sigs(ON, OFF, OFF, OFF, OFF, OFF, OFF, OFF, OFF, OFF));

        return defaultHapiSpec("MultiKeyNonPayerEntityVerifiedAsync")
                .given(
                        newKeyNamed("payerKey").shape(LARGE_THRESH_SHAPE),
                        newKeyNamed("receiverKey").shape(LARGE_THRESH_SHAPE),
                        cryptoCreate(PAYER).keyShape(LARGE_THRESH_SHAPE),
                        cryptoCreate(RECEIVER).keyShape(LARGE_THRESH_SHAPE).receiverSigRequired(true))
                .when()
                .then(cryptoTransfer(tinyBarsFromTo(PAYER, RECEIVER, 1L))
                        .sigControl(forKey(PAYER, firstOnly), forKey(RECEIVER, firstOnly)));
    }

    @HapiTest
    final Stream<DynamicTest> discoversExpectedVersions() {
        return defaultHapiSpec("discoversExpectedVersions")
                .given()
                .when()
                .then(getVersionInfo().logged().hasNoDegenerateSemvers());
    }

    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given()
                .when()
                .then(sendModified(withSuccessivelyVariedQueryIds(), QueryVerbs::getVersionInfo));
    }
}
