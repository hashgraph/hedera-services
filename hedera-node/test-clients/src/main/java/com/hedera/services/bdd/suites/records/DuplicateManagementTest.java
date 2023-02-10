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
package com.hedera.services.bdd.suites.records;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.includingDeduction;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.getNonFeeDeduction;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DuplicateManagementTest extends HapiSuite {
    private static final Logger log = LogManager.getLogger(DuplicateManagementTest.class);

    public static void main(String... args) {
        new DuplicateManagementTest().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    usesUnclassifiableIfNoClassifiableAvailable(),
                    hasExpectedDuplicates(),
                    classifiableTakesPriorityOverUnclassifiable(),
                });
    }

    private HapiSpec hasExpectedDuplicates() {
        return defaultHapiSpec("HasExpectedDuplicates")
                .given(
                        cryptoCreate("civilian").balance(ONE_HUNDRED_HBARS),
                        usableTxnIdNamed("txnId").payerId("civilian"))
                .when(
                        uncheckedSubmit(
                                        cryptoCreate("repeated")
                                                .payingWith("civilian")
                                                .txnId("txnId"))
                                .payingWith("civilian")
                                .fee(ONE_HBAR)
                                .hasPrecheck(NOT_SUPPORTED),
                        uncheckedSubmit(
                                cryptoCreate("repeated").payingWith("civilian").txnId("txnId")),
                        uncheckedSubmit(
                                cryptoCreate("repeated").payingWith("civilian").txnId("txnId")),
                        uncheckedSubmit(
                                cryptoCreate("repeated").payingWith("civilian").txnId("txnId")),
                        sleepFor(1_000L))
                .then(
                        getReceipt("txnId")
                                .andAnyDuplicates()
                                .payingWith("civilian")
                                .hasPriorityStatus(SUCCESS)
                                .hasDuplicateStatuses(DUPLICATE_TRANSACTION, DUPLICATE_TRANSACTION),
                        getTxnRecord("txnId")
                                .payingWith("civilian")
                                .via("cheapTxn")
                                .assertingNothingAboutHashes()
                                .hasPriority(recordWith().status(SUCCESS)),
                        getTxnRecord("txnId")
                                .andAnyDuplicates()
                                .payingWith("civilian")
                                .via("costlyTxn")
                                .assertingNothingAboutHashes()
                                .hasPriority(recordWith().status(SUCCESS))
                                .hasDuplicates(
                                        inOrder(
                                                recordWith().status(DUPLICATE_TRANSACTION),
                                                recordWith().status(DUPLICATE_TRANSACTION))),
                        sleepFor(1_000L),
                        withOpContext(
                                (spec, opLog) -> {
                                    var cheapGet =
                                            getTxnRecord("cheapTxn").assertingNothingAboutHashes();
                                    var costlyGet =
                                            getTxnRecord("costlyTxn").assertingNothingAboutHashes();
                                    allRunFor(spec, cheapGet, costlyGet);
                                    var payer = spec.registry().getAccountID("civilian");
                                    var cheapRecord = cheapGet.getResponseRecord();
                                    var costlyRecord = costlyGet.getResponseRecord();
                                    opLog.info("cheapRecord: {}", cheapRecord);
                                    opLog.info("costlyRecord: {}", costlyRecord);
                                    var cheapPrice = getNonFeeDeduction(cheapRecord).orElse(0);
                                    var costlyPrice = getNonFeeDeduction(costlyRecord).orElse(0);
                                    assertEquals(
                                            3 * cheapPrice,
                                            costlyPrice,
                                            String.format(
                                                    "Costly (%d) should be 3x more expensive than"
                                                            + " cheap (%d)!",
                                                    costlyPrice, cheapPrice));
                                }));
    }

    private HapiSpec usesUnclassifiableIfNoClassifiableAvailable() {
        return defaultHapiSpec("UsesUnclassifiableIfNoClassifiableAvailable")
                .given(
                        newKeyNamed("wrongKey"),
                        cryptoCreate("civilian"),
                        usableTxnIdNamed("txnId").payerId("civilian"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, "0.0.3", 100_000_000L)))
                .when(
                        uncheckedSubmit(
                                cryptoCreate("nope")
                                        .payingWith("civilian")
                                        .txnId("txnId")
                                        .signedBy("wrongKey")),
                        sleepFor(1_000L))
                .then(
                        getReceipt("txnId").hasPriorityStatus(INVALID_PAYER_SIGNATURE),
                        getTxnRecord("txnId")
                                .assertingNothingAboutHashes()
                                .hasPriority(
                                        recordWith()
                                                .status(INVALID_PAYER_SIGNATURE)
                                                .transfers(
                                                        includingDeduction(
                                                                "node payment", "0.0.3"))));
    }

    private HapiSpec classifiableTakesPriorityOverUnclassifiable() {
        return defaultHapiSpec("ClassifiableTakesPriorityOverUnclassifiable")
                .given(
                        cryptoCreate("civilian").balance(100 * 100_000_000L),
                        usableTxnIdNamed("txnId").payerId("civilian"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, "0.0.3", 100_000_000L)))
                .when(
                        uncheckedSubmit(
                                        cryptoCreate("nope")
                                                .txnId("txnId")
                                                .payingWith("civilian")
                                                .setNode("0.0.4"))
                                .logged(),
                        uncheckedSubmit(
                                cryptoCreate("sure")
                                        .txnId("txnId")
                                        .payingWith("civilian")
                                        .setNode("0.0.3")),
                        sleepFor(1_000L))
                .then(
                        getReceipt("txnId")
                                .andAnyDuplicates()
                                .logged()
                                .hasPriorityStatus(SUCCESS)
                                .hasDuplicateStatuses(INVALID_NODE_ACCOUNT),
                        getTxnRecord("txnId")
                                .assertingNothingAboutHashes()
                                .andAnyDuplicates()
                                .hasPriority(recordWith().status(SUCCESS))
                                .hasDuplicates(inOrder(recordWith().status(INVALID_NODE_ACCOUNT))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
